package fr.pioneers.kinetics.kitchen;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.ui.FluidPair;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.kinetics.KineticEvents;
import fr.pioneers.kinetics.KineticsConfig;
import fr.pioneers.recipe.PiRecipes;
import fr.pioneers.transport.CableTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 8b) : UNITE DE PROCEDE des postes mecaniques de la cuisine ({@link KitchenMachineType}), code de l'ancienne
 * {@code KitchenMachineTile} (qui ne fait plus que la porter par l'adaptateur cinetique : noeud de rotation, faces des objets, seau et
 * bouteille, rendu). Energie motrice : la rotation ({@link PowerSource.Kinetic}) : la machine demande son couple tant qu'une recette est
 * engagee (5 % a vide), n'avance qu'au-dessus de sa vitesse minimale, a la cadence de sa vitesse (plafonnee a 1,5 x la nominale) ; le
 * travail se compte en tours ({@code max(1, gestes) x tours par geste}).
 * <p>
 * Recettes : les ingredients sont CONTENUS dans les cases d'entree (un objet par case) ; sans verrou la recette faisable la plus complete
 * l'emporte (puis l'ordre des identifiants) ; le verrou = la recette FIXEE d'avant (choisie maintenant dans le carre du selecteur standard,
 * source {@code kitchen:<poste>} ; les cases acceptent toujours tout ingredient des stations, comme avant). Restes (seau, bouteille) et
 * tirages sans place : trop-plein vide d'abord dans les sorties.
 * <p>
 * Sauvegarde au format d'avant : {@code slots}, {@code progress} (tours), {@code in} / {@code out} (reservoirs), {@code recipe},
 * {@code locked} (recette fixee), {@code overflow} ; client : {@code status}, {@code progress} (part), {@code need}, {@code slots}, reservoirs,
 * {@code locked}. Cases : entrees puis sorties (ordre d'avant), aux places de l'interface v2.
 * <p>
 * 1.0.5-beta (lot 32, retour 41) : l'extracteur de miel ({@link KitchenMachineType#fills}) range chaque produit dans la sortie de son RANG
 * (cire, rayon de miel, cadre vide ; trop-plein ailleurs) et a la paire de cases « Remplir » ({@link #containerIn} : fiole ou seau vide,
 * {@link #containerOut} : bouteille ou seau de miel), a droite de la jauge, sauvees a la suite des sorties ; le remplissage
 * ({@link HoneyFill#fillOne}) se fait au changement du contenu seulement.
 */
public class KitchenProcess extends ProcessUnit {

    public static final int STATUS_IDLE = 0, STATUS_WORKING = 1, STATUS_SLOW = 2, STATUS_FULL = 3, STATUS_STOPPED = 4;

    private final KitchenMachineTile tile;
    public final KitchenMachineType type;
    private final PowerSource.Kinetic power;
    private final List<ItemSlot> all = new ArrayList<>();
    /** 1.0.5-beta (lot 32) : cases « Remplir » de l'extracteur de miel (null pour les autres machines). */
    @Nullable
    private final ItemSlot containerIn, containerOut;
    /** 1.0.5-beta (lot 41) : paire « Vider » (contenant plein -> cuve d'entree, contenant vide rendu) ; null sans cuve d'entree. */
    @Nullable
    private final ItemSlot drainIn, drainOut;
    private boolean fillPending = true;
    @Nullable
    private final FluidTank tankIn, tankOut;
    private float work;
    @Nullable
    private ResourceLocation recipeId;
    @Nullable
    private KitchenRecipe recipe;
    private int sentStatus = -1;
    private boolean engaged, dirty = true, room;
    private int scanClock;
    private final List<ItemStack> overflow = new ArrayList<>();
    // Client : avancement recu et instant de reception (extrapole a la vitesse du reseau), contenu affiche
    private float clientProgress, sentProgress;
    private double clientProgressTime;
    private float clientNeed = 1;
    private int sentHash;

    public KitchenProcess(UnitHost machine, KitchenMachineTile tile, KitchenMachineType type) {
        super(machine, CableTier.BASIC, new StandardLayout.Counts(type.inputs(), type.outputs(), type.hasTankIn() ? 1 : 0, 0, type.hasTankOut() ? 1 : 0, 0));
        this.tile = tile;
        this.type = type;
        this.power = (PowerSource.Kinetic) machine.power();
        for (int i = 0; i < type.inputs(); i++) {
            int index = i;
            int[] p = layout.itemIn.get(i);
            inputs.add(machine.slot(new ItemSlot(() -> 64, s -> acceptsInput(index, s), (s, a) -> true, (s, a) -> !a.external(), this::changed, p[0], p[1])));
        }
        for (int i = 0; i < type.outputs(); i++) {
            int[] p = layout.itemOut.get(i);
            outputs.add(machine.slot(new ItemSlot(() -> 64, s -> false, (s, a) -> false, (s, a) -> true, this::changed, p[0], p[1])));
        }
        all.addAll(inputs);
        all.addAll(outputs);
        //1.0.5-beta (lot 41, retour 60 : plus de seau ni de bouteille au clic) : paire « Remplir » de toute machine a cuve de sortie (lot 32 :
        //l'extracteur de miel seul), paire « Vider » de toute machine a cuve d'entree ; sauvees a la suite des sorties (mondes d'avant relus)
        if (type.hasTankOut()) {
            //A droite de la jauge de sortie : contenant vide en haut, plein en bas (cotes de la jauge)
            int x = layout.fluidOut.get(0).x() + StandardLayout.GAUGE_W + 5;
            containerIn = machine.slot(new ItemSlot(() -> 16, FluidPair::fillable, (s, a) -> true, (s, a) -> !a.external(), this::changed,
                  x, StandardLayout.TOP + 1).ghost(Items.GLASS_BOTTLE));
            containerOut = machine.slot(new ItemSlot(() -> 64, s -> false, (s, a) -> false, (s, a) -> true, this::changed,
                  x, StandardLayout.TOP + StandardLayout.GAUGE_H - 17));
            if (type.fills()) {
                //Objets attendus des cases (fantomes) : cadre plein ; cire, rayon, cadre vide (rang des produits de la recette) ; miel
                containerOut.ghost(Items.HONEY_BOTTLE);
                inputs.get(0).ghost(PiFarmDevices.HONEY_FRAME.get());
                outputs.get(0).ghost(PiFarmDevices.BEESWAX.get());
                outputs.get(1).ghost(Items.HONEYCOMB);
                outputs.get(2).ghost(PiFarmDevices.HIVE_FRAME.get());
            }
            all.add(containerIn);
            all.add(containerOut);
        } else {
            containerIn = null;
            containerOut = null;
        }
        if (type.hasTankIn()) {
            //A droite de la derniere sortie : seau plein en haut, seau vide rendu en bas
            int[] last = layout.itemOut.get(layout.itemOut.size() - 1);
            int x = last[0] + StandardLayout.SLOT + 5;
            drainIn = machine.slot(new ItemSlot(() -> 16, FluidPair::drainable, (s, a) -> true, (s, a) -> !a.external(), this::changed,
                  x, StandardLayout.TOP + 1).ghost(type == KitchenMachineType.CHURN ? Items.MILK_BUCKET : Items.WATER_BUCKET));
            drainOut = machine.slot(new ItemSlot(() -> 64, s -> false, (s, a) -> false, (s, a) -> true, this::changed,
                  x, StandardLayout.TOP + StandardLayout.GAUGE_H - 17));
            all.add(drainIn);
            all.add(drainOut);
        } else {
            drainIn = null;
            drainOut = null;
        }
        tankIn = type.hasTankIn() ? new FluidTank(KitchenMachineType.TANK, this::canFill) {
            @Override
            protected void onContentsChanged() {
                changed();
            }
        } : null;
        tankOut = type.hasTankOut() ? new FluidTank(KitchenMachineType.TANK) {
            @Override
            protected void onContentsChanged() {
                changed();
            }
        } : null;
        if (tankIn != null) {
            fluidIn.add(tankIn);
        }
        if (tankOut != null) {
            fluidOut.add(tankOut);
        }
        //Charge a vide (rien d'engage) : 5 % du couple de travail, comme avant
        power.declareLoad(type.torque * 0.05);
    }

    public List<ItemSlot> all() {
        return all;
    }

    @Nullable
    public FluidTank tankIn() {
        return tankIn;
    }

    @Nullable
    public FluidTank tankOut() {
        return tankOut;
    }

    @Nullable
    public ResourceLocation locked() {
        return lock.id();
    }

    private void changed() {
        dirty = true;
        fillPending = true;
        machine.changed();
    }

    /** 1.0.5-beta (lot 32) : case du contenant vide (extracteur de miel), ou null. */
    @Nullable
    public ItemSlot containerIn() {
        return containerIn;
    }

    /** 1.0.5-beta (lot 32) : case du contenant plein (extracteur de miel), ou null. */
    @Nullable
    public ItemSlot containerOut() {
        return containerOut;
    }

    /** 1.0.5-beta (lot 41) : case du contenant plein a vider dans la cuve d'entree, ou null. */
    @Nullable
    public ItemSlot drainIn() {
        return drainIn;
    }

    /** 1.0.5-beta (lot 41) : case du contenant vide rendu, ou null. */
    @Nullable
    public ItemSlot drainOut() {
        return drainOut;
    }

    /** Cases ouvertes par le haut et les cotes : entrees (+ contenant vide a remplir, contenant plein a vider). */
    public List<ItemSlot> upSlots() {
        List<ItemSlot> out = new ArrayList<>(inputs);
        if (containerIn != null) {
            out.add(containerIn);
        }
        if (drainIn != null) {
            out.add(drainIn);
        }
        return out;
    }

    /** Cases ouvertes par le bas et les cotes : sorties (+ contenant rempli, contenant vide rendu). */
    public List<ItemSlot> downSlots() {
        List<ItemSlot> out = new ArrayList<>(outputs);
        if (containerOut != null) {
            out.add(containerOut);
        }
        if (drainOut != null) {
            out.add(drainOut);
        }
        return out;
    }

    /**
     * 1.0.5-beta (lot 32) : remplit les contenants de la case « Remplir » depuis le reservoir de sortie (fiole 250 mB, seau 1 000 mB) tant que
     * c'est possible ; vrai si au moins un a ete rempli. Appele au tick seulement apres un changement du contenu ({@link #fillPending}).
     */
    boolean fillContainers() {
        if (containerIn == null || containerOut == null || tankOut == null) {
            return false;
        }
        boolean any = false;
        for (int k = 0; k < 64; k++) {
            ItemStack[] r = FluidPair.fillOne(tankOut, containerIn.getStack(), containerOut.getStack());
            if (r == null) {
                break;
            }
            containerIn.setStack(r[0]);
            containerOut.setStack(r[1]);
            any = true;
        }
        return any;
    }

    /** 1.0.5-beta (lot 41) : vide les contenants pleins de la paire « Vider » dans la cuve d'entree (au changement du contenu seulement). */
    boolean drainContainers() {
        if (drainIn == null || drainOut == null || tankIn == null) {
            return false;
        }
        boolean any = false;
        for (int k = 0; k < 64; k++) {
            ItemStack[] r = FluidPair.drainOne(tankIn, drainIn.getStack(), drainOut.getStack());
            if (r == null) {
                break;
            }
            drainIn.setStack(r[0]);
            drainOut.setStack(r[1]);
            any = true;
        }
        return any;
    }

    // ------------------------------------------------------------------
    // Recettes
    // ------------------------------------------------------------------

    /** Recettes des stations de la machine, dans l'ordre des identifiants (meme ordre client et serveur). */
    public static List<KitchenRecipe> recipes(Level level, KitchenMachineType type) {
        List<KitchenRecipe> out = new ArrayList<>();
        for (Station s : type.stations) {
            var t = PiKitchen.TYPES.get(s);
            if (t != null) {
                out.addAll(PiRecipes.sorted(level, t.get()));
            }
        }
        out.sort(Comparator.comparing(r -> r.getId().toString()));
        return out;
    }

    @Nullable
    static KitchenRecipe byId(Level level, KitchenMachineType type, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        for (KitchenRecipe r : recipes(level, type)) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** Cache par cote : objets acceptes et fluides acceptes par type de machine, jusqu'au prochain rechargement des recettes. */
    private static final class AcceptSide {

        RecipeManager manager;
        int generation = -1;
        final Map<KitchenMachineType, Map<Item, Boolean>> items = new EnumMap<>(KitchenMachineType.class);
        final Map<KitchenMachineType, Map<Fluid, Boolean>> fluids = new EnumMap<>(KitchenMachineType.class);

        void check(Level level) {
            RecipeManager m = level.getRecipeManager();
            int g = PiRecipes.generation();
            if (m != manager || g != generation) {
                manager = m;
                generation = g;
                items.clear();
                fluids.clear();
            }
        }
    }

    private static final AcceptSide[] ACCEPTS = {new AcceptSide(), new AcceptSide()};

    static synchronized boolean ingredientOf(Level level, KitchenMachineType type, ItemStack stack) {
        AcceptSide side = ACCEPTS[level.isClientSide ? 1 : 0];
        side.check(level);
        return side.items.computeIfAbsent(type, k -> new HashMap<>()).computeIfAbsent(stack.getItem(), item -> {
            for (KitchenRecipe r : recipes(level, type)) {
                if (r.accepts(stack)) {
                    return true;
                }
            }
            return false;
        });
    }

    static synchronized boolean fluidOf(Level level, KitchenMachineType type, Fluid fluid) {
        AcceptSide side = ACCEPTS[level.isClientSide ? 1 : 0];
        side.check(level);
        return side.fluids.computeIfAbsent(type, k -> new HashMap<>()).computeIfAbsent(fluid, f -> {
            for (KitchenRecipe r : recipes(level, type)) {
                if (r.fluid() != null && r.fluid().test(new FluidStack(f, r.fluid().amount()))) {
                    return true;
                }
            }
            return false;
        });
    }

    /** Case d'entree : ingredient d'une recette, et pas deja dans une AUTRE case (un objet par case : les entonnoirs ne noient pas la cuve). */
    private boolean acceptsInput(int index, ItemStack stack) {
        Level level = level();
        if (level == null || stack.isEmpty() || !ingredientOf(level, type, stack)) {
            return false;
        }
        for (int i = 0; i < inputs.size(); i++) {
            if (i != index && ItemStack.isSameItemSameTags(inputs.get(i).getStack(), stack)) {
                return false;
            }
        }
        return true;
    }

    boolean canFill(FluidStack stack) {
        Level level = level();
        return level == null || !stack.isEmpty() && fluidOf(level, type, stack.getFluid());
    }

    /** Recette dont les ingredients (et le liquide d'entree) sont la, ou null : la recette fixee si elle l'est, sinon la plus complete. */
    @Nullable
    private KitchenRecipe findRecipe(Level level) {
        if (lock.isSet()) {
            KitchenRecipe r = byId(level, type, lock.id());
            return r != null && ingredientsOk(r) ? r : null;
        }
        if (recipe != null && ingredientsOk(recipe)) {
            return recipe;
        }
        KitchenRecipe best = null;
        int bestScore = -1;
        for (KitchenRecipe r : recipes(level, type)) {
            int score = 0;
            for (KitchenRecipe.Counted c : r.ingredients()) {
                score += c.count();
            }
            if (r.fluid() != null) {
                score++;
            }
            if (score > bestScore && ingredientsOk(r)) {
                best = r;
                bestScore = score;
            }
        }
        return best;
    }

    /** Ingredients contenus dans les cases d'entree et liquide d'entree suffisant (une recette sans rien a prendre est ignoree). */
    private boolean ingredientsOk(KitchenRecipe r) {
        if (r.ingredients().isEmpty() && r.fluid() == null) {
            return false;
        }
        if (r.fluid() != null && (tankIn == null || !r.fluid().test(tankIn.getFluid()))) {
            return false;
        }
        if (!r.fluidResult().isEmpty() && tankOut == null) {
            return false;
        }
        List<ItemStack> copies = new ArrayList<>();
        for (ItemSlot slot : inputs) {
            copies.add(slot.getStack().copy());
        }
        return take(r, copies, null);
    }

    /** Place pour les produits : liquide dans le reservoir de sortie, objets dans les sorties. */
    private boolean roomOk(KitchenRecipe r) {
        if (!r.fluidResult().isEmpty() && (tankOut == null || tankOut.fill(r.fluidResult(), IFluidHandler.FluidAction.SIMULATE) < r.fluidResult().getAmount())) {
            return false;
        }
        return hasRoom(r);
    }

    /**
     * Preleve les ingredients dans les piles donnees (copies pour l'essai, cases reelles pour la recette) : ingredients les plus precis
     * d'abord (le moins de piles qui conviennent). {@code taken} recoit les piles prises (restes, nom d'origine).
     */
    private static boolean take(KitchenRecipe r, List<ItemStack> stacks, @Nullable List<ItemStack> taken) {
        List<KitchenRecipe.Counted> order = new ArrayList<>(r.ingredients());
        order.sort(Comparator.comparingInt(c -> {
            int n = 0;
            for (ItemStack s : stacks) {
                if (!s.isEmpty() && c.ingredient().test(s)) {
                    n++;
                }
            }
            return n;
        }));
        for (KitchenRecipe.Counted c : order) {
            int need = c.count();
            for (ItemStack s : stacks) {
                if (need <= 0) {
                    break;
                }
                if (!s.isEmpty() && c.ingredient().test(s)) {
                    int n = Math.min(need, s.getCount());
                    if (taken != null) {
                        taken.add(s.copyWithCount(n));
                    }
                    s.shrink(n);
                    need -= n;
                }
            }
            if (need > 0) {
                return false;
            }
        }
        return true;
    }

    /** 1.0.5-beta (lot 32) : sortie preferee d'un produit (extracteur de miel : son rang dans la recette ; sinon -1). */
    private int preferred(KitchenRecipe r, ItemStack stack) {
        if (!type.fills()) {
            return -1;
        }
        List<KitchenRecipe.Result> results = r.results();
        for (int i = 0; i < results.size() && i < outputs.size(); i++) {
            if (ItemStack.isSameItemSameTags(results.get(i).stack(), stack)) {
                return i;
            }
        }
        return -1;
    }

    /** Place pour les produits surs (chance 1) et une sortie libre pour les tirages ; le reste part au trop-plein. */
    private boolean hasRoom(KitchenRecipe r) {
        List<ItemStack> sim = new ArrayList<>();
        for (ItemSlot slot : outputs) {
            sim.add(slot.getStack().copy());
        }
        boolean chancy = false;
        for (KitchenRecipe.Result res : r.results()) {
            if (res.chance() >= 1) {
                if (!mergeRest(sim, res.stack(), false, preferred(r, res.stack())).isEmpty()) {
                    return false;
                }
            } else {
                chancy = true;
            }
        }
        if (chancy) {
            for (ItemStack s : sim) {
                if (s.isEmpty()) {
                    return true;
                }
            }
            for (KitchenRecipe.Result res : r.results()) {
                for (ItemStack s : sim) {
                    if (ItemStack.isSameItemSameTags(s, res.stack()) && s.getCount() + res.stack().getCount() <= s.getMaxStackSize()) {
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }

    /** Ajoute une pile aux sorties (copies ou reelles) ; renvoie ce qui n'a pas trouve de place. */
    private ItemStack mergeRest(List<ItemStack> slots, ItemStack stack, boolean real) {
        return mergeRest(slots, stack, real, -1);
    }

    /**
     * 1.0.5-beta (lot 32) : idem, la sortie {@code prefer} d'abord (extracteur de miel : la sortie du rang du produit dans la recette), puis
     * les autres ; {@code prefer} < 0 : comme avant.
     */
    private ItemStack mergeRest(List<ItemStack> slots, ItemStack stack, boolean real, int prefer) {
        ItemStack rest = stack.copy();
        if (prefer >= 0 && prefer < slots.size()) {
            ItemStack in = slots.get(prefer);
            if (in.isEmpty()) {
                slots.set(prefer, rest.copy());
                if (real) {
                    outputs.get(prefer).setStack(rest.copy());
                }
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameTags(in, rest)) {
                int n = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(n);
                rest.shrink(n);
                if (real && n > 0) {
                    outputs.get(prefer).setStack(in.copy());
                }
                if (rest.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
            ItemStack in = slots.get(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, rest)) {
                int n = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(n);
                rest.shrink(n);
                if (real && n > 0) {
                    outputs.get(i).setStack(in.copy());
                }
            }
        }
        for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, rest.copy());
                if (real) {
                    outputs.get(i).setStack(rest.copy());
                }
                rest = ItemStack.EMPTY;
            }
        }
        return rest;
    }

    /** Vide le trop-plein dans les sorties ; vrai s'il n'en reste rien. */
    private boolean flushOverflow() {
        if (overflow.isEmpty()) {
            return true;
        }
        List<ItemStack> live = new ArrayList<>();
        for (ItemSlot slot : outputs) {
            live.add(slot.getStack().copy());
        }
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : overflow) {
            ItemStack rest = mergeRest(live, stack, true);
            if (!rest.isEmpty()) {
                left.add(rest);
            }
        }
        overflow.clear();
        overflow.addAll(left);
        machine.changed();
        return overflow.isEmpty();
    }

    /** Tours de l'arbre pour la recette. */
    public float need(@Nullable KitchenRecipe r) {
        return r == null ? 1 : Math.max(1, r.strokes()) * type.turnsPerStroke;
    }

    // ------------------------------------------------------------------
    // Travail
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        double omega = power.omega();
        double rate = Math.min(omega, type.nominalSpeed * 1.5) / 60.0 / 20.0 * KineticsConfig.machineSpeed();
        process(level, omega, rate);
        //1.0.5-beta (lot 32) : cases « Remplir » de l'extracteur de miel, seulement apres un changement (pas de travail a chaque tick)
        if (fillPending) {
            fillPending = false;
            if (fillContainers()) {
                level.playSound(null, pos(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
            if (drainContainers()) {
                level.playSound(null, pos(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.1F);
            }
        }
        power.declareLoad(engaged ? type.torque : type.torque * 0.05);
        if (status != sentStatus) {
            sentStatus = status;
            machine.syncNow();
        }
    }

    private void process(ServerLevel level, double omega, double rate) {
        //Recette et place revues quand le contenu change (cases, reservoirs), et toutes les secondes (rechargement des recettes)
        if (dirty || ++scanClock >= 20) {
            dirty = false;
            scanClock = 0;
            KitchenRecipe found = findRecipe(level);
            if (found != recipe) {
                if (found == null || recipeId == null || !found.getId().equals(recipeId)) {
                    work = 0;
                }
                recipe = found;
                recipeId = found == null ? null : found.getId();
            }
            room = recipe != null && roomOk(recipe);
        }
        if (!flushOverflow()) {
            engaged = false;
            status = STATUS_FULL;
            return;
        }
        if (recipe == null) {
            engaged = false;
            status = STATUS_IDLE;
            return;
        }
        if (!room) {
            engaged = false;
            status = STATUS_FULL;
            return;
        }
        engaged = true;
        if (omega < type.minSpeed) {
            status = omega < 0.05 ? STATUS_STOPPED : STATUS_SLOW;
            return;
        }
        status = STATUS_WORKING;
        float before = work;
        work += (float) rate;
        //Un evenement par geste (tour de vis, coup de couteau, de batteur...)
        if ((int) (before / type.turnsPerStroke) != (int) (work / type.turnsPerStroke)) {
            effects(level);
        }
        float need = need(recipe);
        if (work >= need) {
            KitchenRecipe r = recipe;
            if (ingredientsOk(r) && roomOk(r)) {
                work -= need;
                craft(level, r);
            } else {
                work = need;
                dirty = true;
            }
        }
    }

    private void craft(ServerLevel level, KitchenRecipe r) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemSlot slot : inputs) {
            stacks.add(slot.getStack().copy());
        }
        List<ItemStack> taken = new ArrayList<>();
        if (!take(r, stacks, taken)) {
            return;
        }
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).setStack(stacks.get(i));
        }
        if (r.fluid() != null && tankIn != null) {
            tankIn.drain(r.fluid().amount(), IFluidHandler.FluidAction.EXECUTE);
        }
        ItemStack source = ItemStack.EMPTY;
        if (!r.ingredients().isEmpty()) {
            for (ItemStack s : taken) {
                if (r.ingredients().get(0).ingredient().test(s)) {
                    source = s;
                    break;
                }
            }
        }
        List<ItemStack> live = new ArrayList<>();
        for (ItemSlot slot : outputs) {
            live.add(slot.getStack().copy());
        }
        for (ItemStack stack : r.roll(level.random, source)) {
            ItemStack rest = mergeRest(live, stack, true, preferred(r, stack));
            if (!rest.isEmpty()) {
                overflow.add(rest);
            }
        }
        //Restes de recette (seau, bouteille) : dans les sorties aussi
        for (ItemStack s : taken) {
            if (s.hasCraftingRemainingItem()) {
                ItemStack rem = s.getCraftingRemainingItem();
                for (int i = 0; i < s.getCount(); i++) {
                    ItemStack rest = mergeRest(live, rem, true);
                    if (!rest.isEmpty()) {
                        overflow.add(rest);
                    }
                }
            }
        }
        if (!r.fluidResult().isEmpty() && tankOut != null) {
            tankOut.fill(r.fluidResult().copy(), IFluidHandler.FluidAction.EXECUTE);
        }
        finish(level);
        KineticEvents.award(level, pos(), "kitchen_" + type.key());
        dirty = true;
        machine.syncNow();
    }

    /** Sons et particules d'un geste. */
    private void effects(ServerLevel level) {
        BlockPos pos = pos();
        SoundEvent sound = switch (type) {
            case PRESS -> SoundEvents.WOODEN_BUTTON_CLICK_ON;
            case CHURN -> SoundEvents.HONEY_BLOCK_SLIDE;
            case CHOPPER -> SoundEvents.AXE_STRIP;
            case KNEADER -> SoundEvents.SLIME_BLOCK_STEP;
            case HONEY_EXTRACTOR -> SoundEvents.CHAIN_STEP;
        };
        float pitch = switch (type) {
            case PRESS -> 0.7F;
            case CHOPPER -> 1.4F;
            case HONEY_EXTRACTOR -> 1.4F;
            default -> 1.1F;
        };
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.45F, pitch + level.random.nextFloat() * 0.2F);
        ItemStack work = workpiece();
        if (!work.isEmpty() && (type == KitchenMachineType.PRESS || type == KitchenMachineType.CHOPPER)) {
            double fx = tile.facing().getStepX() * 0.25, fz = tile.facing().getStepZ() * 0.25;
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, work.copyWithCount(1)), pos.getX() + 0.5 + fx,
                  pos.getY() + (type == KitchenMachineType.PRESS ? 0.5 : 0.75), pos.getZ() + 0.5 + fz, 3, 0.12, 0.05, 0.12, 0.04);
        }
    }

    private void finish(ServerLevel level) {
        SoundEvent sound = switch (type) {
            case PRESS -> SoundEvents.BUCKET_FILL;
            case CHURN, KNEADER -> SoundEvents.SLIME_SQUISH_SMALL;
            case CHOPPER -> SoundEvents.WOOD_HIT;
            case HONEY_EXTRACTOR -> SoundEvents.HONEY_DRINK;
        };
        level.playSound(null, pos(), sound, SoundSource.BLOCKS, 0.6F, 1.0F + level.random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------
    // Recette fixee (selecteur standard ; fleches d'avant gardees pour les paquets)
    // ------------------------------------------------------------------

    /** Fleches d'avant : recette precedente / suivante (automatique compris). */
    void cycle(boolean next) {
        Level level = level();
        if (level == null) {
            return;
        }
        List<KitchenRecipe> list = recipes(level, type);
        int at = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(lock.id())) {
                at = i;
            }
        }
        //Cycle : automatique (-1), puis chaque recette
        int n = list.size() + 1;
        int to = Math.floorMod(at + 1 + (next ? 1 : -1), n) - 1;
        lock.set(to < 0 ? null : list.get(to).getId());
        recipeLockChanged();
    }

    @Override
    public String recipeSource() {
        return RecipeSources.kitchen(type);
    }

    @Override
    public void recipeLockChanged() {
        work = 0;
        recipe = null;
        recipeId = null;
        dirty = true;
        machine.syncNow();
    }

    @Override
    public int recipeTier() {
        return 0;
    }

    void dropContents(Consumer<ItemStack> drop) {
        overflow.forEach(stack -> drop.accept(stack.copy()));
        overflow.clear();
        for (ItemSlot slot : all) {
            if (!slot.isEmpty()) {
                drop.accept(slot.getStack().copy());
                slot.setStack(ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------
    // Rendu (client)
    // ------------------------------------------------------------------

    /** Premier objet d'entree (ce qui est travaille : fruits dans la maie, legume sur la planche, cadre dans la cage). */
    public ItemStack workpiece() {
        for (ItemSlot slot : inputs) {
            if (!slot.isEmpty()) {
                return slot.getStack();
            }
        }
        return ItemStack.EMPTY;
    }

    /** Nombre d'objets d'entree (hauteur de la pate, du marc). */
    public int inputCount() {
        int n = 0;
        for (ItemSlot slot : inputs) {
            n += slot.getCount();
        }
        return n;
    }

    /** Avancement de la recette (0..1) ; cote client, extrapole a la vitesse du reseau depuis le dernier envoi. */
    public float progress(float partial) {
        Level level = level();
        if (level != null && level.isClientSide) {
            if (status != STATUS_WORKING) {
                return clientProgress;
            }
            double omega = Math.min(power.omega(), type.nominalSpeed * 1.5);
            double turns = omega / 60.0 / 20.0 * Math.max(0, RenderClock.time(partial) - clientProgressTime); //lot 29 : heure serveur estimee
            return (float) Math.min(1, clientProgress + turns / Math.max(0.25, clientNeed));
        }
        return recipe == null ? 0 : Math.min(1, work / need(recipe));
    }

    @Nullable
    public KitchenRecipe recipe() {
        return recipe;
    }

    /** Recette fixee (client : interface) ; null = automatique. */
    @Nullable
    public KitchenRecipe fixedRecipe() {
        Level level = level();
        return level == null ? null : byId(level, type, lock.id());
    }

    // ------------------------------------------------------------------
    // Interface : 0 vitesse x100, 1 couple x10, 2 avancement x1000, 3 etat, 4 cadence en % de la nominale, 5 type, 6 puissance du reseau (W),
    // 7 tours de la recette x100, 8 liquide d'entree (mB), 9 liquide de sortie (mB) : memes indices qu'avant
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.SPEED, () -> Math.round(power.speed() * 100)).add(UiValues.TORQUE, () -> Math.round(power.torque() * 10))
              .add(UiValues.PROGRESS_X1000, () -> Math.round(progress(0) * 1000)).add(UiValues.STATUS, () -> status)
              .add(UiValues.RATE, () -> Math.round(Math.min(power.omega(), type.nominalSpeed * 1.5) / type.nominalSpeed * 100))
              .add(UiValues.MACHINE_TYPE, type::ordinal).add(UiValues.NET_POWER, () -> Math.round(power.networkPower()))
              .add(UiValues.TURNS, () -> Math.round(need(recipe) * 100))
              .add(UiValues.FLUID, () -> tankIn == null ? 0 : tankIn.getFluidAmount())
              .add(UiValues.FLUID_OUT, () -> tankOut == null ? 0 : tankOut.getFluidAmount());
    }

    // ------------------------------------------------------------------
    // Synchronisation, sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putInt("status", status);
        sentProgress = progress(0);
        tag.putFloat("progress", sentProgress);
        tag.putFloat("need", need(recipe));
        tag.put("slots", ItemSlots.save(all));
        if (tankIn != null) {
            tag.put("in", tankIn.writeToNBT(new CompoundTag()));
        }
        if (tankOut != null) {
            tag.put("out", tankOut.writeToNBT(new CompoundTag()));
        }
        if (lock.isSet()) {
            tag.putString("locked", lock.id().toString());
        }
        sentHash = contentHash();
    }

    @Override
    public void readClient(CompoundTag tag) {
        status = tag.getInt("status");
        clientProgress = tag.getFloat("progress");
        clientNeed = tag.getFloat("need");
        Level level = level();
        clientProgressTime = RenderClock.now();
        ItemSlots.load(all, tag.getList("slots", Tag.TAG_COMPOUND));
        if (tankIn != null) {
            tankIn.setFluid(FluidStack.loadFluidStackFromNBT(tag.getCompound("in")));
        }
        if (tankOut != null) {
            tankOut.setFluid(FluidStack.loadFluidStackFromNBT(tag.getCompound("out")));
        }
        lock.set(tag.contains("locked") ? ResourceLocation.tryParse(tag.getString("locked")) : null);
    }

    /** Empreinte de ce que le rendu montre : objets, niveaux des reservoirs par 1/16, recette fixee. */
    private int contentHash() {
        int h = 1;
        for (ItemSlot slot : all) {
            ItemStack s = slot.getStack();
            h = h * 31 + (s.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(s.getItem()) * 97 + s.getCount());
        }
        if (tankIn != null) {
            h = h * 31 + tankIn.getFluidAmount() * 16 / KitchenMachineType.TANK + tankIn.getFluid().getFluid().hashCode();
        }
        if (tankOut != null) {
            h = h * 31 + tankOut.getFluidAmount() * 16 / KitchenMachineType.TANK + tankOut.getFluid().getFluid().hashCode();
        }
        return h * 31 + (lock.id() == null ? 0 : lock.id().hashCode());
    }

    @Override
    public boolean clientDirty() {
        if (contentHash() != sentHash) {
            return true;
        }
        float p = progress(0);
        //Recette achevee (l'avancement retombe) ou derive de l'extrapolation du client
        return p < sentProgress - 0.05F || Math.abs(p - sentProgress) >= 0.25F;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("slots", ItemSlots.save(all));
        tag.putFloat("progress", work);
        if (tankIn != null) {
            tag.put("in", tankIn.writeToNBT(new CompoundTag()));
        }
        if (tankOut != null) {
            tag.put("out", tankOut.writeToNBT(new CompoundTag()));
        }
        if (recipeId != null) {
            tag.putString("recipe", recipeId.toString());
        }
        if (lock.isSet()) {
            tag.putString("locked", lock.id().toString());
        }
        if (!overflow.isEmpty()) {
            ListTag list = new ListTag();
            overflow.forEach(stack -> list.add(stack.save(new CompoundTag())));
            tag.put("overflow", list);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        ItemSlots.load(all, tag.getList("slots", Tag.TAG_COMPOUND));
        work = tag.getFloat("progress");
        if (tankIn != null) {
            tankIn.readFromNBT(tag.getCompound("in"));
        }
        if (tankOut != null) {
            tankOut.readFromNBT(tag.getCompound("out"));
        }
        recipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        lock.set(tag.contains("locked") ? ResourceLocation.tryParse(tag.getString("locked")) : null);
        overflow.clear();
        for (Tag t : tag.getList("overflow", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (!stack.isEmpty()) {
                overflow.add(stack);
            }
        }
        dirty = true;
    }
}
