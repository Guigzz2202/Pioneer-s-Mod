package fr.pioneers.bronze.machines;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.api.machine.tier.TierRecipes.TierRecipe;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.api.machine.unit.ToolBay;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.bronze.BronzeEvents;
import fr.pioneers.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.forge.MoltenLoad;
import fr.pioneers.industry.recipe.Remanents;
import fr.pioneers.machine.AcceptCache;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 7) : UNITE DE PROCEDE des machines de traitement a vapeur, portee par {@link SteamProcessTile} (noeud de vapeur : volume,
 * faces, echanges). Energie motrice : la vapeur ({@link PowerSource.Steam}) : {@link #STEAM_PER_TICK} mB par tick de travail ; reserve vide =
 * « pas de vapeur ».
 * <p>
 * 1.0.5-beta (lot 38, retours 49, 52, 53 ; specification age du bronze §2.2, §3.2) : la machine est le TIER 1 de sa famille
 * ({@link SteamMachine#family}) : elle fait TOUTES les recettes de sa famille de tier minimal <= 1 (moteur commun {@link TierRecipes} : recettes
 * mecaniques, recettes a vapeur, cuissons pour le four), deux fois plus vite qu'une machine mecanique et a x1,5 sur les recettes a rendement ;
 * selecteur de recette standard (verrou, source {@code family:<famille>}) ; sans verrou : la plus haute recette, puis la plus complete ;
 * sorties en vrac dans ses cases (sous-produits compris), le surplus d'un tirage attend dans un trop-plein (cle {@code overflow}) : rien ne se
 * perd. Sauvegarde : {@code progress}, {@code water}, {@code overflow}, verrou {@code lockedRecipe} (cles facultatives : une machine d'avant le
 * lot 38 se relit sans perte, voir {@link SteamProcessTile#load}).
 */
public class SteamProcess extends ProcessUnit {

    /** Vapeur prise par tick de travail (mB) : {@link fr.pioneers.bronze.steam.SteamRules#PROCESS_USE}. */
    public static final double STEAM_PER_TICK = fr.pioneers.bronze.steam.SteamRules.PROCESS_USE;
    public static final int IDLE = 0, WORKING = 1, NO_STEAM = 2, FULL = 3, NO_RECIPE = 4;
    /** Tier global des machines a vapeur. */
    public static final int TIER = 1;
    /** Cuisson du four a vapeur (ticks) : duree de base de la famille « cuisson ». */
    public static final int FURNACE_TIME = TierRecipes.SMELT_TIME;
    /** 1.0.3-beta (lot 11b) : reservoir d'eau du melangeur a vapeur (mB ; 8 seaux = 8 gachees de 8 betons). */
    public static final int WATER_TANK = 8_000;

    private final SteamProcessTile tile;
    public final SteamMachine machineKind;
    /** Famille de tiers de la machine (cle de tools/gc/api/tier_families.py). */
    public final String family;
    private final PowerSource.Steam steam;
    /** 1.0.5-beta (lot 39) : vapeur prise par tick de travail (mB) : {@link SteamMachine#steamUse} (6 pour le four a induction). */
    public final double steamUse;
    /** Melangeur : eau de gachage (null pour les autres machines). */
    @Nullable
    private final FluidTank water;
    /** Sorties vues de l'exterieur pour l'ejection (extraction seulement). */
    private final ItemSlots ejectable;
    /** Surplus d'un tirage qui n'a pas trouve de place : remis dans les sorties des qu'il y en a ; la machine attend. */
    private final List<ItemStack> overflow = new ArrayList<>();
    /** Objets a poser au sol au premier tick serveur (ancien atelier relu, bain d'un ancien fondeur). */
    private final List<ItemStack> pending = new ArrayList<>();
    private double work;
    private int time = 200, linger;
    private boolean dirty = true;
    @Nullable
    private TierRecipe current;
    /** Client : identifiant de la recette en cours (ecran). */
    @Nullable
    private ResourceLocation shownRecipe;
    /**
     * 1.0.5-beta (lot 44, retour 62) : case « Outil » des remanents (presse a vapeur : matrice a plaque), DERNIERE case ; null sans remanent
     * ({@link ToolBay}). Cle de la machine pour {@link Remanents} : {@code steam:<machine>}.
     */
    @Nullable
    private final ItemSlot tool;
    private final String toolKey;
    /** Outil de la derniere recette examinee (entree, sortie qui le rend ; -1 : aucun) : sans allocation a chaque tick. */
    @Nullable
    private TierRecipe toolFor;
    private int toolIn = -1, toolOut = -1;

    public SteamProcess(UnitHost machine, SteamProcessTile tile, SteamMachine kind) {
        super(machine, CableTier.BASIC, new StandardLayout.Counts(kind.inputs, kind.outputs, kind == SteamMachine.MIXER ? 1 : 0, 0, 0, 0));
        this.tile = tile;
        this.machineKind = kind;
        this.family = kind.family();
        this.steamUse = kind.steamUse();
        this.steam = (PowerSource.Steam) machine.power();
        if (kind == SteamMachine.MIXER) {
            water = machine.fluidTank(new FluidTank(WATER_TANK, f -> f.getFluid().isSame(Fluids.WATER)) {
                @Override
                protected void onContentsChanged() {
                    dirty = true;
                    machine.markSync();
                    machine.changed();
                }
            });
            fluidIn.add(water);
        } else {
            water = null;
        }
        toolKey = Remanents.STEAM + kind.key();
        boolean tools = Remanents.hasToolSlot(toolKey);
        for (int i = 0; i < kind.inputs; i++) {
            int index = i;
            int[] p = layout.itemIn.get(i);
            //1.0.5-beta (lot 44) : un remanent de la machine ne va jamais dans une case d'entree (sa place : la case Outil)
            inputs.add(machine.slot(new ItemSlot(() -> 64, s -> !tools || !Remanents.isTool(toolKey, s), (s, a) -> !a.external() || acceptsExternal(index, s),
                  (s, a) -> !a.external(), this::inputChanged, p[0], p[1])));
        }
        for (int i = 0; i < kind.outputs; i++) {
            int[] o = layout.itemOut.get(i);
            outputs.add(machine.slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::outputChanged, o[0], o[1])));
        }
        //1.0.5-beta (lot 44) : case Outil en DERNIER (les index des cases d'avant ne bougent pas)
        tool = tools ? machine.slot(ToolBay.slot(toolKey, this, this::inputChanged, layout.toolX, layout.toolY)) : null;
        ejectable = new ItemSlots(outputs, Automation.INTERNAL);
    }

    /** 1.0.5-beta (lot 44) : la case Outil (null : machine sans remanent). */
    @Nullable
    @Override
    public ItemSlot toolSlot() {
        return tool;
    }

    /** 1.0.5-beta (lot 44) : outil de la recette ({@link #toolIn} : son entree, {@link #toolOut} : la sortie qui le rend), garde par recette. */
    private void toolOf(TierRecipe r) {
        if (r == toolFor) {
            return;
        }
        toolFor = r;
        toolIn = tool == null ? -1 : Remanents.toolInput(toolKey, r.inputs());
        toolOut = -1;
        if (toolIn >= 0) {
            net.minecraft.world.item.Item item = Remanents.toolOf(toolKey, r.inputs().get(toolIn).ingredient());
            for (int i = 0; i < r.outputs().size(); i++) {
                if (r.outputs().get(i).stack().is(item)) {
                    toolOut = i;
                    break;
                }
            }
        }
    }

    /**
     * 1.0.5-beta (lot 44) : MONDE EXISTANT (appele par la tile apres la lecture des cases) : un remanent d'avant dans une case d'entree passe
     * dans la case Outil ; ce qui n'y tient pas est pose au sol au premier tick (comme les objets d'un ancien format).
     */
    void moveLegacyTools() {
        List<ItemStack> rest = new ArrayList<>();
        if (ToolBay.move(toolKey, inputs, tool, rest)) {
            rest.forEach(this::addPending);
            dirty = true;
        }
    }

    /**
     * Les tremies et cables n'apportent que ce que la machine sait traiter (recette verrouillee : ses seuls ingredients), et jamais dans une
     * deuxieme case un objet deja present dans une autre (sinon une case se remplit d'un seul ingredient et le second ne passe plus).
     */
    private boolean acceptsExternal(int index, ItemStack stack) {
        for (int j = 0; j < inputs.size(); j++) {
            if (j != index && ItemStack.isSameItemSameTags(inputs.get(j).getStack(), stack)) {
                return false;
            }
        }
        Level level = level();
        if (level == null) {
            return true;
        }
        return lockAllows(stack, () -> AcceptCache.test(level, machineKind, stack, s -> TierRecipes.accepts(level, family, s)));
    }

    private void inputChanged() {
        dirty = true;
        machine.changed();
    }

    private void outputChanged() {
        dirty = true;
        machine.changed();
    }

    /** Reservoir d'eau du melangeur a vapeur (null pour les autres machines). */
    @Nullable
    public FluidTank water() {
        return water;
    }

    public double work() {
        return work;
    }

    /** Sorties vues pour l'ejection automatique (faces de sortie). */
    public ItemSlots ejectable() {
        return ejectable;
    }

    /** Recettes de la famille de cette machine (tier minimal <= 1). */
    public List<TierRecipe> recipes() {
        Level level = level();
        return level == null ? List.of() : TierRecipes.of(level, family);
    }

    /** Client : recette en cours (ecran), ou null. */
    @Nullable
    public ResourceLocation shownRecipe() {
        return shownRecipe;
    }

    /** Ancien contenu a poser au sol (lecture d'un ancien format) : ajoute par la tile pendant la lecture. */
    void addPending(ItemStack stack) {
        if (!stack.isEmpty()) {
            pending.add(stack.copy());
        }
    }

    List<ItemStack> takePending() {
        List<ItemStack> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    // ------------------------------------------------------------------
    // Travail
    // ------------------------------------------------------------------

    @Nullable
    private TierRecipe find(Level level) {
        List<ItemStack> stacks = presentItems();
        int have = water == null ? 0 : water.getFluidAmount();
        TierRecipe locked = null;
        if (lock.isSet()) {
            locked = TierRecipes.byId(level, family, lock.id());
            if (locked == null) {
                return null;                                              // verrou inconnu : la machine attend (dropMissingLock l'efface)
            }
        }
        return TierRecipes.find(TierRecipes.of(level, family), stacks, have, locked);
    }

    /** Place pour les produits SURS (rendement compris, arrondi au-dessus) et au moins une place pour chaque tirage ; trop-plein vide. */
    private boolean fits(TierRecipe r) {
        if (!overflow.isEmpty()) {
            return false;
        }
        double f = r.yieldFactor(TIER);
        List<ItemStack> sim = new ArrayList<>(outputs.size());
        for (ItemSlot slot : outputs) {
            sim.add(slot.getStack().copy());
        }
        toolOf(r);
        for (int k = 0; k < r.outputs().size(); k++) {
            TierRecipes.Out o = r.outputs().get(k);
            if (k == toolOut) {
                continue;                                                 // lot 44 : l'outil revient dans la case Outil d'ou il vient
            }
            if (o.chance() >= 1F) {
                int n = (int) Math.ceil(o.stack().getCount() * f - 1e-9);
                if (!merge(sim, o.stack().copyWithCount(n), false).isEmpty()) {
                    return false;
                }
            } else if (!room(sim, o.stack())) {
                return false;
            }
        }
        return true;
    }

    /** Une case libre, ou une pile du meme objet qui prend encore un exemplaire. */
    private static boolean room(List<ItemStack> sim, ItemStack stack) {
        for (ItemStack in : sim) {
            if (in.isEmpty() || ItemStack.isSameItemSameTags(in, stack) && in.getCount() < in.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Ajoute une pile aux sorties (simulees ou reelles) ; renvoie ce qui n'a pas trouve de place. */
    private ItemStack merge(List<ItemStack> slots, ItemStack stack, boolean real) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
            ItemStack in = slots.get(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, rest)) {
                int n = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                if (n > 0) {
                    in.grow(n);
                    rest.shrink(n);
                    if (real) {
                        outputs.get(i).setStack(in.copy());
                    }
                }
            }
        }
        for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                int n = Math.min(rest.getCount(), rest.getMaxStackSize());
                ItemStack put = rest.copyWithCount(n);
                slots.set(i, put);
                rest.shrink(n);
                if (real) {
                    outputs.get(i).setStack(put.copy());
                }
            }
        }
        return rest;
    }

    /** Quantite tiree pour une sortie au rendement {@code f} : partie entiere sure, plus un de plus avec la probabilite du reste (§2.2 point 8). */
    static int roll(TierRecipes.Out o, double f, RandomSource random) {
        double expected = o.stack().getCount() * (double) Math.min(1F, o.chance()) * f;
        int whole = (int) Math.floor(expected + 1e-9);
        double frac = expected - whole;
        return whole + (frac > 1e-9 && random.nextDouble() < frac ? 1 : 0);
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now % 100 == 17) {
            dropMissingLock();                                            // 1.11.1 : verrou sur une recette partie ailleurs
        }
        if (!overflow.isEmpty()) {
            flushOverflow();
        }
        if (dirty || now % 20 == 0) {
            dirty = false;
            TierRecipe found = find(level);
            if (found == null || current == null || !found.id().equals(current.id())) {
                work = 0;
                if (found != current) {
                    machine.markSync();
                }
            }
            current = found;
        }
        int was = status;
        if (current == null) {
            status = inputsEmpty() ? IDLE : NO_RECIPE;
        } else if (!fits(current)) {
            status = FULL;
        } else if (!steam.ready(steamUse)) {
            status = NO_STEAM;
        } else {
            status = WORKING;
            steam.take(steamUse);
            time = current.duration(TIER);
            work += 1.0 / time;
            if (work >= 1) {
                work = 0;
                finish(level, current);
                dirty = true;
            }
        }
        //Le voyant reste allume un instant : pas de clignotement (ni de mises a jour de lumiere) quand la vapeur arrive au compte-gouttes
        linger = status == WORKING ? 20 : Math.max(0, linger - 1);
        boolean active = linger > 0;
        BlockState state = machine.state();
        if (state.hasProperty(SteamProcessBlock.ACTIVE) && state.getValue(SteamProcessBlock.ACTIVE) != active) {
            level.setBlock(pos(), state.setValue(SteamProcessBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        if (was != status) {
            machine.changed();
        }
    }

    private void flushOverflow() {
        List<ItemStack> sim = new ArrayList<>(outputs.size());
        for (ItemSlot slot : outputs) {
            sim.add(slot.getStack().copy());
        }
        for (int i = 0; i < overflow.size(); i++) {
            ItemStack rest = merge(sim, overflow.get(i), true);
            overflow.set(i, rest);
        }
        overflow.removeIf(ItemStack::isEmpty);
        machine.changed();
    }

    private void finish(ServerLevel level, TierRecipe r) {
        int[] used = r.match(presentItems());
        if (used == null) {
            return;
        }
        //1.0.5-beta (lot 21 puis 38) : restes de fabrication rendus (seau vide d'un seau d'eau...), comme a l'etabli : vers les sorties ou le
        //trop-plein, jamais perdus
        List<ItemStack> rests = new ArrayList<>(0);
        toolOf(r);
        for (int i = 0; i < used.length; i++) {
            //1.0.5-beta (lot 44) : l'index qui suit les entrees est la case Outil (presentItems)
            ItemSlot slot = used[i] >= inputs.size() && tool != null ? tool : inputs.get(used[i]);
            ItemStack stack = slot.getStack().copy();
            int count = r.inputs().get(i).count();
            ItemStack rest = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
            if (!rest.isEmpty()) {
                rests.add(rest.copyWithCount(Math.min(rest.getMaxStackSize(), rest.getCount() * count)));
            }
            stack.shrink(count);
            slot.setStack(stack);
        }
        if (water != null && r.water() > 0) {
            water.drain(r.water(), IFluidHandler.FluidAction.EXECUTE);
        }
        double f = r.yieldFactor(TIER);
        List<ItemStack> sim = new ArrayList<>(outputs.size());
        for (ItemSlot slot : outputs) {
            sim.add(slot.getStack().copy());
        }
        for (int k = 0; k < r.outputs().size(); k++) {
            TierRecipes.Out o = r.outputs().get(k);
            if (k == toolOut && tool != null) {
                //1.0.5-beta (lot 44) : l'outil revient dans la case Outil (chance de la recette = usure ; sans rendement : le meme outil)
                int n = Math.min(roll(o, 1, level.random), r.inputs().get(toolIn).count());
                ItemStack held = tool.getStack();
                if (n > 0 && (held.isEmpty() || ItemStack.isSameItemSameTags(held, o.stack()))) {
                    int put = Math.min(n, tool.getLimit(o.stack()) - held.getCount());
                    tool.setStack(o.stack().copyWithCount(held.getCount() + put));
                    n -= put;
                }
                if (n > 0) {
                    ItemStack rest = merge(sim, o.stack().copyWithCount(n), true);
                    if (!rest.isEmpty()) {
                        overflow.add(rest);
                    }
                }
                continue;
            }
            int n = roll(o, f, level.random);
            while (n > 0) {
                int part = Math.min(n, o.stack().getMaxStackSize());
                ItemStack rest = merge(sim, o.stack().copyWithCount(part), true);
                if (!rest.isEmpty()) {
                    overflow.add(rest);
                }
                n -= part;
            }
        }
        for (ItemStack back : rests) {
            ItemStack rest = merge(sim, back, true);
            if (!rest.isEmpty()) {
                overflow.add(rest);
            }
        }
        // 1.0.5-beta (lot 38) : la presse frappe (son de l'ancien marteau-pilon), les autres soufflent
        if (machineKind == SteamMachine.PRESS) {
            level.playSound(null, pos(), PiBronze.SOUND_HAMMER.get(), SoundSource.BLOCKS, 0.5F, 0.95F + level.random.nextFloat() * 0.1F);
        } else {
            level.playSound(null, pos(), PiBronze.SOUND_CHUFF.get(), SoundSource.BLOCKS, 0.35F, 1.1F + level.random.nextFloat() * 0.2F);
        }
        BronzeEvents.award(level, pos(), "steam_" + machineKind.key());
        machine.changed();
    }

    // ------------------------------------------------------------------
    // Interface (memes indices qu'avant : 0 avancement x1000, 1 vapeur en reserve (mB), 2 etat, 3 duree d'une piece ; 4 contenance de la
    // reserve ; lot 38 : 5 rendement x1000, 6 trop-plein), client, sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.PROGRESS_X1000, () -> Math.round(work * 1000)).add(UiValues.STEAM, () -> Math.round(steam.stored()))
              .add(UiValues.STATUS, () -> status).add(UiValues.DURATION, () -> time).add(UiValues.STEAM_MAX, () -> Math.round(steam.capacity()))
              .add(UiValues.YIELD_X1000, () -> current == null ? 1000 : Math.round(current.yieldFactor(TIER) * 1000))
              .add(UiValues.OVERFLOW, () -> {
                  int n = 0;
                  for (ItemStack s : overflow) {
                      n += s.getCount();
                  }
                  return n;
              });
    }

    @Override
    public void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        if (water != null) {
            tag.put("water", water.writeToNBT(new CompoundTag()));
        }
        tag.putString("cur", current == null ? "" : current.id().toString());
    }

    @Override
    public void readClient(CompoundTag tag) {
        super.readClient(tag);
        if (water != null) {
            water.setFluid(FluidStack.loadFluidStackFromNBT(tag.getCompound("water")));
        }
        if (tag.contains("cur", Tag.TAG_STRING)) {
            String s = tag.getString("cur");
            shownRecipe = s.isEmpty() ? null : ResourceLocation.tryParse(s);
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putDouble("progress", work);
        if (water != null && !water.isEmpty()) {
            tag.put("water", water.writeToNBT(new CompoundTag()));
        }
        if (!overflow.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack s : overflow) {
                list.add(s.save(new CompoundTag()));
            }
            tag.put("overflow", list);
        }
        if (!pending.isEmpty()) {
            //Objets d'un ancien format pas encore poses au sol (tile sauvee avant son premier tick) : gardes, poses au sol a la relecture
            ListTag list = new ListTag();
            for (ItemStack s : pending) {
                list.add(s.save(new CompoundTag()));
            }
            tag.put("pending", list);
        }
        lock.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        work = tag.getDouble("progress");
        if (water != null) {
            water.setFluid(FluidStack.loadFluidStackFromNBT(tag.getCompound("water")));
        }
        overflow.clear();
        for (Tag t : tag.getList("overflow", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.of((CompoundTag) t);
            if (!s.isEmpty()) {
                overflow.add(s);
            }
        }
        pending.clear();
        for (Tag t : tag.getList("pending", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.of((CompoundTag) t);
            if (!s.isEmpty()) {
                pending.add(s);
            }
        }
        //1.0.3-beta (lot 7b) : bain d'un ancien fondeur d'alliages (retire au lot 11 de la 1.0.4) : rendu en lingots, pepites et scorie
        if (tag.contains("tank", Tag.TAG_COMPOUND)) {
            MoltenLoad bath = MoltenLoad.load(tag.getCompound("tank"));
            if (!bath.isEmpty()) {
                bath.spill().forEach(this::addPending);
            }
        }
        lock.load(tag);
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Selecteur de recette (lot 38 : verrou standard, recettes de la famille de tier minimal <= 1)
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return TierRecipes.SOURCE + family;
    }

    @Override
    public void recipeLockChanged() {
        dirty = true;
        work = 0;
        machine.markSync();
        machine.changed();
    }

    /** Tier MK de la machine pour le selecteur : T1 = -1 (les recettes de tier minimal m y sont au tier m - 2). */
    @Override
    public int recipeTier() {
        return TIER - 2;
    }

    @Override
    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>(inputs.size() + 1);
        for (ItemSlot slot : inputs) {
            out.add(slot.getStack());
        }
        return ToolBay.present(out, tool);                                 // lot 44 : la case Outil en dernier
    }

    @Override
    public List<FluidStack> presentFluids() {
        return water == null ? List.of() : List.of(water.getFluid());
    }

    /** Pour les GameTests et le banc : une operation entiere tout de suite (vapeur non comptee). */
    public boolean craftNow() {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        TierRecipe r = find(level);
        if (r == null || !fits(r)) {
            return false;
        }
        finish(level, r);
        current = r;
        return true;
    }

    /** Pour l'interface : le compte d'une entree de la recette en cours (null sans recette). */
    @Nullable
    public List<Counted> currentInputs() {
        return current == null ? null : current.inputs();
    }

    /** Toutes les piles des cases (pour les cases d'un ancien format relues). */
    NonNullList<ItemStack> stacks() {
        NonNullList<ItemStack> out = NonNullList.create();
        inputs.forEach(s -> out.add(s.getStack()));
        outputs.forEach(s -> out.add(s.getStack()));
        return out;
    }
}
