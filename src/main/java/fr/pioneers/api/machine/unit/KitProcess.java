package fr.pioneers.api.machine.unit;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.PiTags;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.GasTankHandler;
import fr.pioneers.core.machine.Ejector;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.industry.Impurity;
import fr.pioneers.industry.IndustryEvents;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.IoMigration;
import fr.pioneers.industry.IoTank;
import fr.pioneers.industry.TankView;
import fr.pioneers.industry.Tiers;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import fr.pioneers.industry.recipe.RecipeMatch;
import fr.pioneers.machine.AcceptCache;
import fr.pioneers.machine.MachineKind;
import fr.pioneers.machine.ProcessingRecipe;
import fr.pioneers.recipe.PiRecipes;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;
import fr.pioneers.transport.Transfer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : procede du KIT de base (broyeur, four electrique, compacteur), fondu dans l'atelier : meme tile
 * ({@code AtelierTile}), meme interface standard, meme selecteur ; type de tile {@code kit_machine} garde (alias : les machines posees le
 * gardent), memes cles de sauvegarde ({@code energy}, {@code config}, {@code items}, {@code progress}, verrou : format « kit » de
 * {@link MachineTile}), meme ordre des cases (entree, sortie, batterie, puis le liant du compacteur en DERNIER).
 * <p>
 * Une entree, une sortie, une batterie. Chaque tick de travail coute {@link MachineKind#power} FE ; l'operation dure
 * {@link MachineKind#ticks}. Sans energie, la progression s'arrete (elle n'est pas perdue) ; elle repart de zero si l'entree change ou
 * disparait. Ejection automatique de la sortie par les faces de sortie. MAJ Recettes (lot 8a) : le compacteur a une case de plus, le LIANT.
 * <p>
 * 1.0.3-beta (lot 5, decision de Guillaume : la dechiqueteuse est fondue dans le broyeur) : le broyeur lit AUSSI les recettes industrielles de
 * sa machine ({@code pioneers:industrial}, machine {@code crusher} : une entree, une ou deux sorties, ni fluide ni gaz : copeaux, fibres) ; leur
 * sous-produit (avec sa chance) va dans une 2e case de sortie, declaree en DERNIER (apres la batterie : les broyeurs poses gardent leurs cases).
 * <p>
 * 1.0.4-beta (lot 11, fusion des machines, arbitrage de Guillaume du 07/11) : le broyeur a absorbe le broyeur a boulets, le four (Four
 * industriel) le four de grillage, de reduction, de frittage, de traitement thermique et de diffusion. Le mecanisme du kit est GARDE (voie A :
 * ses cases, ses recettes de broyage / cuisson / compactage, ses recettes propres {@code industry/<cle>/}, sa duree fixe) ; les recettes
 * RECUES (pioneers:industrial de la machine hors dossier propre : {@link IndustryRecipes#kitReceived}) passent par la voie B, celle d'un
 * atelier ({@link RecipeMatch} sur toutes les cases, duree {@link Tiers#ticks}, tier exige, rendement du minerai). L'io de la machine du
 * catalogue s'est elargie a l'union des io (broyeur 1/2/1 fluide, four 3/2, fluide e/s, gaz e/s) : les cases et reservoirs en plus sont
 * declares APRES les cases d'avant (entree, sortie, batterie, liant ou 2e sortie) : les machines posees gardent leur contenu.
 */
public class KitProcess extends ProcessUnit implements RecipeMatch.Buffers {

    /** Recette trouvee : quantite d'entree, resultat et liant (0 : aucun) ; lot 5 : sous-produit (vide : aucun) et sa chance. */
    public record Found(int count, ItemStack result, int binderCount, ItemStack extra, float chance) {

        Found(int count, ItemStack result, int binderCount) {
            this(count, result, binderCount, ItemStack.EMPTY, 0F);
        }
    }

    public final MachineKind kind;
    private final ItemSlot input, output, battery;
    @Nullable
    private final ItemSlot binder;
    /** 1.0.3-beta (lot 5) : 2e sortie du broyeur (sous-produits des recettes de l'ancienne dechiqueteuse), null pour les autres. */
    @Nullable
    private final ItemSlot output2;
    private final ItemSlots ejectable;
    /** 1.0.4-beta (lot 11) : machine du catalogue (io elargie) et cases en plus (entrees, sorties) declarees apres celles du kit. */
    public final IndustryMachine catalog;
    private final List<ItemSlot> extraIn = new ArrayList<>(), extraOut = new ArrayList<>();
    /** Voie B : recette recue en cours, a revoir (entrees changees), etat d'attente, voie du dernier tick, recette a reprendre (chargement). */
    @Nullable
    private IndustrialRecipe received;
    private boolean receivedDirty = true, laneB;
    private int receivedWait = IDLE;
    @Nullable
    private ResourceLocation savedReceived;
    @Nullable
    private Found cached;
    private ItemStack cachedFor = ItemStack.EMPTY;
    private ItemStack cachedBinder = ItemStack.EMPTY;

    public KitProcess(MachineTile.Access machine, MachineKind kind, CableTier tier) {
        super(machine, tier, counts(kind));
        this.kind = kind;
        this.catalog = IndustryMachine.byKey(kind.name);
        int[] in = layout.itemIn.get(0), out = layout.itemOut.get(0);
        input = machine.slot(new ItemSlot(() -> 64, this::accepts, (stack, automation) -> true,
              (stack, automation) -> automation != Automation.EXTERNAL, this::inputChanged, in[0], in[1]));
        output = machine.slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL,
              (stack, automation) -> true, machine::changed, out[0], out[1]));
        battery = machine.batterySlot(layout.batteryX, layout.batteryY);
        //MAJ Recettes (lot 8a) : la case du liant est la DERNIERE (sauvegarde par indice : les machines posees avant gardent leurs cases)
        if (kind == MachineKind.COMPRESSOR) {
            int[] b = layout.itemIn.get(1);
            binder = machine.slot(new ItemSlot(() -> 64, this::acceptsBinder, (stack, automation) -> true,
                  (stack, automation) -> automation != Automation.EXTERNAL, this::inputChanged, b[0], b[1]));
            inputs.add(input);
            inputs.add(binder);
        } else {
            binder = null;
            inputs.add(input);
        }
        outputs.add(output);
        //1.0.3-beta (lot 5) : 2e sortie du broyeur, en DERNIER (sauvegarde par indice)
        if (kind == MachineKind.CRUSHER) {
            int[] o2 = layout.itemOut.get(1);
            output2 = machine.slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL,
                  (stack, automation) -> true, machine::changed, o2[0], o2[1]));
            outputs.add(output2);
            ejectable = new ItemSlots(List.of(output, output2), Automation.INTERNAL);
        } else {
            output2 = null;
            ejectable = new ItemSlots(List.of(output), Automation.INTERNAL);
        }
        //1.0.4-beta (lot 11) : cases et reservoirs de l'io elargie (machines absorbees), APRES ceux du kit (sauvegarde par rang)
        for (int i = kitIn(kind); i < catalog.itemsIn; i++) {
            int[] p = layout.itemIn.get(i);
            ItemSlot slot = machine.slot(new ItemSlot(() -> 64, this::acceptsReceived, (stack, automation) -> true,
                  (stack, automation) -> automation != Automation.EXTERNAL, this::receivedChanged, p[0], p[1]));
            inputs.add(slot);
            extraIn.add(slot);
        }
        for (int i = kitOut(kind); i < catalog.itemsOut; i++) {
            int[] p = layout.itemOut.get(i);
            ItemSlot slot = machine.slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL,
                  (stack, automation) -> true, this::outputsChanged, p[0], p[1]));
            outputs.add(slot);
            extraOut.add(slot);
        }
        int tank = 16_000 * (tier.ordinal() + 1);
        for (int i = 0; i < catalog.fluidsIn; i++) {
            fluidIn.add(machine.fluidTank(new IoTank(tank, false, this::acceptsFluid, this::receivedChanged)));
        }
        for (int i = 0; i < catalog.fluidsOut; i++) {
            fluidOut.add(machine.fluidTank(new IoTank(tank, true, f -> true, this::outputsChanged)));
        }
        for (int i = 0; i < catalog.gasesIn; i++) {
            gasIn.add(machine.gasTank(BasicGasTank.input(tank * 2L, g -> level() == null
                  || lockAllowsGas(g, () -> IndustryRecipes.acceptsGas(level(), catalog, g)), this::receivedChanged)));
        }
        for (int i = 0; i < catalog.gasesOut; i++) {
            gasOut.add(machine.gasTank(BasicGasTank.output(tank * 2L, this::outputsChanged)));
        }
        List<ItemSlot> eject = new ArrayList<>(output2 != null ? List.of(output, output2) : List.of(output));
        eject.addAll(extraOut);
        ejectAll = new ItemSlots(eject, Automation.INTERNAL);
        //Faces des objets fermees a la pose (le kit declarait ses objets avant l'energie : meme reglage par defaut qu'avant le lot 2)
        machine.sides().fill(SideType.ITEMS, SideMode.NONE);
        duration = kind.ticks(tier);
    }

    private final ItemSlots ejectAll;

    /** Cases d'entree du kit d'avant le lot 11 (le liant du compacteur en plus). */
    private static int kitIn(MachineKind kind) {
        return kind == MachineKind.COMPRESSOR ? 2 : 1;
    }

    /** Cases de sortie du kit d'avant le lot 11 (la 2e sortie du broyeur en plus). */
    private static int kitOut(MachineKind kind) {
        return kind == MachineKind.CRUSHER ? 2 : 1;
    }

    /**
     * Cases du kit : celles de la machine du catalogue (1.0.4-beta lot 11 : io elargie a celle des machines absorbees ; avant : une entree,
     * deux pour le compacteur, une sortie, deux pour le broyeur).
     */
    public static StandardLayout.Counts counts(MachineKind kind) {
        IndustryMachine m = IndustryMachine.byKey(kind.name);
        return new StandardLayout.Counts(Math.max(kitIn(kind), m.itemsIn), Math.max(kitOut(kind), m.itemsOut), m.fluidsIn, m.gasesIn, m.fluidsOut,
              m.gasesOut);
    }

    /**
     * 1.0.4-beta (lot 11) : noms des cases et reservoirs du kit dans l'ordre de declaration ({@link IoMigration} : NBT d'une machine absorbee
     * remis par noms) : objets entree 0, sortie 0, batterie, liant (entree 1) ou 2e sortie (sortie 1), puis les entrees et sorties en plus ;
     * fluides et gaz : entrees puis sorties.
     */
    public static IoMigration.Layout migrationLayout(MachineKind kind) {
        IndustryMachine m = IndustryMachine.byKey(kind.name);
        List<String> items = new ArrayList<>(List.of("in0", "out0", "battery0"));
        if (kind == MachineKind.COMPRESSOR) {
            items.add("in1");
        } else if (kind == MachineKind.CRUSHER) {
            items.add("out1");
        }
        for (int i = kitIn(kind); i < m.itemsIn; i++) {
            items.add("in" + i);
        }
        for (int i = kitOut(kind); i < m.itemsOut; i++) {
            items.add("out" + i);
        }
        List<String> fluids = new ArrayList<>(), gases = new ArrayList<>();
        for (int i = 0; i < m.fluidsIn; i++) {
            fluids.add("in" + i);
        }
        for (int i = 0; i < m.fluidsOut; i++) {
            fluids.add("out" + i);
        }
        for (int i = 0; i < m.gasesIn; i++) {
            gases.add("in" + i);
        }
        for (int i = 0; i < m.gasesOut; i++) {
            gases.add("out" + i);
        }
        return new IoMigration.Layout(items, fluids, gases);
    }

    /** Ressources ejectables : objets ; 1.0.4-beta (lot 11) : fluides et gaz si la machine a des reservoirs (io elargie). */
    public static SideType[] ejectTypes(MachineKind kind) {
        IndustryMachine m = IndustryMachine.byKey(kind.name);
        List<SideType> out = new ArrayList<>(List.of(SideType.ITEMS));
        if (m.fluidsIn + m.fluidsOut > 0) {
            out.add(SideType.FLUIDS);
        }
        if (m.gasesIn + m.gasesOut > 0) {
            out.add(SideType.GASES);
        }
        return out.toArray(SideType[]::new);
    }

    @Override
    public SideType[] ejectTypes() {
        return ejectTypes(kind);
    }

    /**
     * Cases vues de l'exterieur (tuyaux, entonnoirs) : entree, liant, sortie (pas la batterie) ; 1.0.4-beta (lot 11) : puis les entrees et
     * sorties de l'io elargie (en dernier : les rangs d'avant ne changent pas).
     */
    public List<ItemSlot> automationSlots() {
        List<ItemSlot> out = new ArrayList<>();
        if (output2 != null) {
            out.addAll(List.of(input, output, output2));
        } else {
            out.addAll(binder != null ? List.of(input, binder, output) : List.of(input, output));
        }
        out.addAll(extraIn);
        out.addAll(extraOut);
        return out;
    }

    /** Une case d'entree de la voie B ou un reservoir d'entree a change : recette recue a revoir. */
    private void receivedChanged() {
        receivedDirty = true;
        machine.changed();
    }

    private void outputsChanged() {
        receivedDirty = true;
        machine.changed();
    }

    /** 1.0.4-beta (lot 11) : objet d'une recette recue (cases d'entree en plus ; verrouillee : seulement les ingredients de sa recette). */
    private boolean acceptsReceived(ItemStack stack) {
        Level level = level();
        return level == null || lockAllows(stack, () -> receivedTakes(level, stack));
    }

    private boolean receivedTakes(Level level, ItemStack stack) {
        for (IndustrialRecipe r : IndustryRecipes.kitReceived(level, catalog)) {
            for (fr.pioneers.bronze.bench.BenchRecipe.Counted c : r.items()) {
                if (c.ingredient().test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean acceptsFluid(FluidStack stack) {
        Level level = level();
        if (level == null) {
            return true;
        }
        if (lock.isSet() && lockedChoice() != null) {
            return lockedChoice().takesFluid(stack);
        }
        for (IndustrialRecipe r : IndustryRecipes.kitReceived(level, catalog)) {
            for (IndustrialRecipe.FluidIn in : r.fluids()) {
                if (in.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void inputChanged() {
        receivedDirty = true;
        machine.changed();
        if (!ItemStack.isSameItemSameTags(input.getStack(), cachedFor) || binder != null && !ItemStack.isSameItemSameTags(binder.getStack(), cachedBinder)) {
            progress = 0;
        }
    }

    /** MAJ Recettes (lot 8a) : liant d'au moins une recette de compactage (verrouillee : seulement celui de sa recette). */
    private boolean acceptsBinder(ItemStack stack) {
        Level level = level();
        if (level == null) {
            return true;
        }
        return lockAllows(stack, () -> PiRecipes.sorted(level, type()).stream().anyMatch(r -> r.acceptsBinder(stack)));
    }

    // ------------------------------------------------------------------
    // Recettes
    // ------------------------------------------------------------------

    private boolean accepts(ItemStack stack) {
        Level level = level();
        if (level == null) {
            return true;
        }
        //0.20.46 (audit) : resultat garde par objet jusqu'au prochain rechargement des recettes
        //MAJ Recettes (lot 7) : verrouillee, la machine ne prend que l'ingredient de sa recette
        return lockAllows(stack, () -> AcceptCache.test(level, kind, stack, this::scan));
    }

    private boolean scan(ItemStack stack) {
        boolean kit = switch (kind) {
            case FURNACE -> smelt(stack.copyWithCount(1)) != null;
            case CRUSHER -> level().getRecipeManager().getAllRecipesFor(type()).stream().anyMatch(recipe -> recipe.accepts(stack))
                            || industrial(level()).stream().anyMatch(r -> r.items().get(0).ingredient().test(stack));
            case COMPRESSOR -> level().getRecipeManager().getAllRecipesFor(type()).stream().anyMatch(recipe -> recipe.accepts(stack));
        };
        //1.0.4-beta (lot 11) : ou un ingredient d'une recette recue (voie B)
        return kit || receivedTakes(level(), stack);
    }

    /**
     * 1.0.3-beta (lot 5) : recettes industrielles que le broyeur compact sait faire (machine {@code crusher} : une entree d'objet, une ou deux
     * sorties d'objets, ni fluide ni gaz) ; vide pour les autres machines du kit. 1.0.4-beta (lot 11) : seulement celles de son dossier propre
     * ({@code industry/crusher/} : ex-dechiqueteuse, voie A) ; les recettes recues (ex-broyeur a boulets) passent par la voie B.
     */
    public static List<IndustrialRecipe> industrial(Level level) {
        List<IndustrialRecipe> out = new java.util.ArrayList<>();
        for (IndustrialRecipe r : PiRecipes.sorted(level, PiIndustry.INDUSTRIAL.get())) {
            //1.0.5-beta (lot 38) : sauf celles qu'une recette basse convertie de la famille rend inutiles (voie B)
            if (r.machine() == IndustryMachine.CRUSHER && kitFits(r) && r.getId().getPath().startsWith("industry/crusher/")
                && !IndustryRecipes.dominated(level, IndustryMachine.CRUSHER).contains(r.getId())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Recette industrielle a la mesure du broyeur compact (une entree, une ou deux sorties, ni fluide ni gaz). */
    public static boolean kitFits(IndustrialRecipe r) {
        return r.items().size() == 1 && !r.results().isEmpty() && r.results().size() <= 2 && r.fluids().isEmpty() && r.gases().isEmpty()
               && r.fluidResults().isEmpty() && r.gasResults().isEmpty() && r.data().isEmpty();
    }

    private static Found found(IndustrialRecipe r) {
        IndustrialRecipe.Result main = r.results().get(0);
        if (r.results().size() > 1) {
            IndustrialRecipe.Result extra = r.results().get(1);
            return new Found(r.items().get(0).count(), main.stack().copy(), 0, extra.stack().copy(), extra.chance());
        }
        return new Found(r.items().get(0).count(), main.stack().copy(), 0);
    }

    private RecipeType<ProcessingRecipe> type() {
        return kind == MachineKind.CRUSHER ? PiRecipes.CRUSHING.get() : PiRecipes.COMPRESSING.get();
    }

    @Nullable
    private Found smelt(ItemStack stack) {
        Level level = level();
        if (level == null) {
            return null;
        }
        //1.0.3-beta (lot 3) : recettes PROPRES au four electrique (pioneers:industrial, machine electric_furnace : minerai brut, concasse et poudre
        //des metaux de tier 1 -> 1 lingot) ; elles passent avant les cuissons vanilla (le four vanilla, lui, ne fond toujours pas le minerai)
        for (fr.pioneers.industry.recipe.IndustrialRecipe r : fr.pioneers.industry.recipe.IndustryRecipes.of(level, fr.pioneers.industry.IndustryMachine.FURNACE)) {
            if (r.items().size() == 1 && !r.results().isEmpty() && r.fluids().isEmpty() && r.gases().isEmpty() && r.fluidResults().isEmpty()
                && r.getId().getPath().startsWith("industry/furnace/") && r.items().get(0).ingredient().test(stack)) {
                return new Found(r.items().get(0).count(), r.results().get(0).stack().copy(), 0);
            }
        }
        Optional<SmeltingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level);
        //MAJ Recettes (lot 1) : plus de four joker, seulement la nourriture et l'age du bronze (tag pioneers:furnace_denied)
        return recipe.map(r -> new Found(1, r.getResultItem(level.registryAccess()).copy(), 0)).filter(f -> PiTags.Items.electricFurnaceAllows(f.result))
              .orElse(null);
    }

    /** Recette de l'objet d'entree (sans regarder s'il y en a assez), gardee tant que l'entree ne change pas. */
    @Nullable
    public Found recipe() {
        ItemStack stack = input.getStack();
        Level level = level();
        if (stack.isEmpty() || level == null) {
            return null;
        }
        ItemStack bind = binder == null ? ItemStack.EMPTY : binder.getStack();
        if (!ItemStack.isSameItemSameTags(stack, cachedFor) || !ItemStack.isSameItemSameTags(bind, cachedBinder)) {
            cachedFor = stack.copyWithCount(1);
            cachedBinder = bind.isEmpty() ? ItemStack.EMPTY : bind.copyWithCount(1);
            RecipeChoice locked = lockedChoice();
            //1.0.4-beta (lot 11) : recette recue verrouillee : voie B (la voie A ne fait rien)
            if (locked != null && lockedReceived(level) != null) {
                cached = null;
                return cached;
            }
            //1.0.3-beta (lot 5) : recette industrielle verrouillee (broyeur : ex-dechiqueteuse) : lue telle quelle (sous-produit et chance)
            IndustrialRecipe lockedIndustrial = locked == null || kind != MachineKind.CRUSHER ? null
                  : industrial(level).stream().filter(r -> r.getId().equals(locked.id())).findFirst().orElse(null);
            if (lockedIndustrial != null) {
                cached = lockedIndustrial.items().get(0).ingredient().test(cachedFor) ? found(lockedIndustrial) : null;
                return cached;
            }
            if (locked != null) {
                //MAJ Recettes (lot 7) : seule la recette verrouillee (une entree, une sortie ; lot 8a : et son liant, 2e entree)
                cached = locked.items().get(0).ingredient().test(cachedFor) && !locked.outputs().isEmpty()
                         ? new Found(locked.items().get(0).count(), locked.outputs().get(0).copy(),
                         locked.items().size() > 1 ? locked.items().get(1).count() : 0) : null;
                return cached;
            }
            //MAJ Recettes (lot 7) : sans verrou, la premiere recette par identifiant (l'ordre du selecteur) ; lot 8a : d'abord celle dont le
            //liant est dans sa case, puis une recette sans liant, puis la premiere (le liant manque : la machine attend)
            cached = switch (kind) {
                case FURNACE -> smelt(cachedFor);
                case CRUSHER, COMPRESSOR -> {
                    //1.0.5-beta (lot 38) : sans les recettes du kit qu'une recette basse convertie de la famille rend inutiles (minerai brut ->
                    //concasses : 3 par la recette du concasseur a machoires, voie B, au lieu de 2)
                    java.util.Set<net.minecraft.resources.ResourceLocation> gone = IndustryRecipes.dominated(level, catalog);
                    List<ProcessingRecipe> all = PiRecipes.sorted(level, type()).stream().filter(r -> r.accepts(cachedFor) && !gone.contains(r.getId())).toList();
                    ProcessingRecipe best = all.stream().filter(r -> r.acceptsBinder(cachedBinder)).findFirst()
                          .orElse(all.stream().filter(r -> !r.hasBinder()).findFirst().orElse(all.isEmpty() ? null : all.get(0)));
                    if (best == null && kind == MachineKind.CRUSHER) {
                        //1.0.3-beta (lot 5) : puis les recettes de l'ancienne dechiqueteuse (premiere par identifiant)
                        IndustrialRecipe r = industrial(level).stream().filter(x -> x.items().get(0).ingredient().test(cachedFor)).findFirst().orElse(null);
                        yield r == null ? null : found(r);
                    }
                    yield best == null ? null : new Found(best.getCount(), best.getResult().copy(), best.getBinderCount());
                }
            };
        }
        return cached;
    }

    /** Le liant de la recette est la (aucun liant demande : toujours vrai). */
    private boolean binderReady(Found found) {
        return found.binderCount <= 0 || binder != null && binder.getCount() >= found.binderCount;
    }

    // ------------------------------------------------------------------
    // Travail
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        Level level = level();
        if (level.getGameTime() % 100 == 17) {
            dropMissingLock();
        }
        machine.discharge(battery, kind.power(tier) * 4);
        if (level.getGameTime() % 10 == 0) {
            ejectTanks(level);
        }
        Found found = recipe();
        //1.0.4-beta (lot 11) : rien pour la voie A (ou recette recue verrouillee) : voie B, les recettes recues des machines absorbees
        if (found == null && (lock.isSet() && lockedReceived(level) != null || !IndustryRecipes.kitReceived(level, catalog).isEmpty() && !lock.isSet())) {
            if (!laneB) {
                laneB = true;
                receivedDirty = true;
                resetProgress();
            }
            tickReceived(level);
            if (level.getGameTime() % 10 == 0) {
                Ejector.eject(level, machine.pos(), machine.sides(), Transfer.ITEMS, ejectAll, 16);
            }
            return;
        }
        if (laneB) {
            laneB = false;
            received = null;
            resetProgress();
        }
        boolean working = false;
        int was = status;
        duration = kind.ticks(tier);
        if (found == null) {
            status = input.isEmpty() ? IDLE : NO_RECIPE;
            resetProgress();
        } else if (input.getCount() < found.count || !binderReady(found)) {
            status = NO_RECIPE;
            resetProgress();
        } else if (!output.insert(found.result.copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty()
                   || !found.extra.isEmpty() && (output2 == null || !output2.insert(found.extra.copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty())) {
            status = FULL;
            resetProgress();
        } else if (!machine.canWork()) {
            status = IDLE;
        } else if (!machine.use(kind.power(tier))) {
            status = NO_ENERGY;
        } else {
            status = WORKING;
            working = true;
            if (++progress >= duration) {
                progress = 0;
                input.extract(found.count, Action.EXECUTE, Automation.INTERNAL);
                if (found.binderCount > 0 && binder != null) {
                    binder.extract(found.binderCount, Action.EXECUTE, Automation.INTERNAL);
                }
                output.insert(found.result.copy(), Action.EXECUTE, Automation.INTERNAL);
                if (!found.extra.isEmpty() && output2 != null && (found.chance >= 1F || level.random.nextFloat() < found.chance)) {
                    output2.insert(found.extra.copy(), Action.EXECUTE, Automation.INTERNAL);
                }
            }
            machine.changed();
        }
        if (was != status) {
            machine.changed();
        }
        machine.markWorking(working);
        if (level.getGameTime() % 10 == 0 && (!output.isEmpty() || output2 != null && !output2.isEmpty() || !extraOut.isEmpty())) {
            Ejector.eject(level, machine.pos(), machine.sides(), Transfer.ITEMS, extraOut.isEmpty() ? ejectable : ejectAll, 16);
        }
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 11) : voie B, recettes recues (comme a l'atelier de la machine absorbee)
    // ------------------------------------------------------------------

    /** La recette recue verrouillee (null : aucun verrou, verrou de la voie A, ou recette disparue). */
    @Nullable
    private IndustrialRecipe lockedReceived(Level level) {
        if (!lock.isSet() || level == null) {
            return null;
        }
        for (IndustrialRecipe r : IndustryRecipes.kitReceived(level, catalog)) {
            if (r.getId().equals(lock.id())) {
                return r;
            }
        }
        return null;
    }

    private int impurity(IndustrialRecipe r) {
        return Impurity.result(r.impurity(), tier.ordinal(), r.tier(), false);
    }

    private double bonus(IndustrialRecipe r) {
        return r.ore() ? Tiers.oreBonus(tier.ordinal()) * machine.tile().ownerFactor("miner.ore_yield") : 1;
    }

    @Override
    public int extraRolls() {
        return tier.ordinal();
    }

    private void tickReceived(Level level) {
        List<IndustrialRecipe> list = IndustryRecipes.kitReceived(level, catalog);
        if (receivedDirty || level.getGameTime() % 20 == 0) {
            receivedDirty = false;
            IndustrialRecipe locked = lockedReceived(level);
            IndustrialRecipe found = null;
            if (savedReceived != null) {
                //Reprise apres chargement : la recette en cours d'abord (avancement garde)
                for (IndustrialRecipe r : RecipeMatch.candidates(list, locked)) {
                    if (r.getId().equals(savedReceived) && RecipeMatch.supply(r, this, 1) > 0) {
                        found = r;
                    }
                }
            }
            boolean resuming = found != null;
            savedReceived = null;
            if (found == null) {
                found = lock.isSet() && locked == null ? null : RecipeMatch.find(list, this, tier.ordinal(), false, locked);
            }
            if (found != received) {
                if (!resuming) {
                    progress = 0;
                }
                received = found;
            }
            if (received != null) {
                duration = Tiers.ticks(received.time(), tier.ordinal(), received.tier(), false);
                tierNeeded = -1;
            } else {
                tierNeeded = RecipeMatch.tierNeeded(list, this, tier.ordinal(), false, locked);
                receivedWait = tierNeeded >= 0 ? TIER : inputsEmpty() ? IDLE : NO_RECIPE;
            }
        }
        boolean working = false;
        int was = status;
        if (!machine.canWork()) {
            status = IDLE;
        } else if (received == null) {
            status = receivedWait;
        } else if (!RecipeMatch.fits(received, this, 1, impurity(received), bonus(received))) {
            status = FULL;
        } else if (!machine.use(Tiers.power(tier))) {
            status = NO_ENERGY;
        } else {
            status = WORKING;
            working = true;
            if (++progress >= duration) {
                progress = 0;
                if (RecipeMatch.supply(received, this, 1) > 0) {
                    RecipeMatch.consume(received, this, 1);
                    RecipeMatch.produce(received, this, 1, level.random, impurity(received), bonus(received));
                    IndustryEvents.award(level, machine.pos(), catalog, received);
                    if (level instanceof ServerLevel sl) {
                        fr.pioneers.core.profile.SkillHooks.autoRecipe(sl, machine.pos(), machine.tile().owner(), catalog, received, false);
                    }
                }
                receivedDirty = true;
            }
            machine.changed();
        }
        if (was != status) {
            machine.changed();
        }
        machine.markWorking(working);
    }

    /** Fluides et gaz produits : ejectes par les faces de sortie (comme a l'atelier). */
    private void ejectTanks(Level level) {
        if (!fluidOut.isEmpty()) {
            Ejector.eject(level, machine.pos(), machine.sides(), Transfer.FLUIDS, new TankView(fluidOut), 16_000);
        }
        if (!gasOut.isEmpty()) {
            Ejector.eject(level, machine.pos(), machine.sides(), Transfer.GASES, new GasTankHandler(gasOut, Automation.INTERNAL), 32_000);
        }
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            machine.changed();
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("progress", progress);
        lock.save(tag);
        //1.0.4-beta (lot 11) : recette recue en cours (voie B), comme la cle « recipe » d'un atelier
        if (laneB && received != null) {
            tag.putString("recipe", received.getId().toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        progress = tag.getInt("progress");
        lock.load(tag);
        cachedFor = ItemStack.EMPTY;
        cachedBinder = ItemStack.EMPTY;
        savedReceived = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        receivedDirty = true;
    }

    // ------------------------------------------------------------------
    // Selecteur de recette
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return RecipeSources.kit(kind);
    }

    @Override
    public void recipeLockChanged() {
        cachedFor = ItemStack.EMPTY;
        cachedBinder = ItemStack.EMPTY;
        receivedDirty = true;
        progress = 0;
        machine.changed();
        machine.syncNow();
    }
}
