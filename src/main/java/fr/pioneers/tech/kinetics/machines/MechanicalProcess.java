package fr.pioneers.tech.kinetics.machines;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.tech.bronze.AirBlastReceiver;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.tech.forge.ForgeHeat;
import fr.pioneers.tech.kinetics.KineticEvents;
import fr.pioneers.tech.kinetics.KineticsConfig;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 7) : UNITE DE PROCEDE des machines mecaniques (meule, tamis, concasseur, scie, tour, marteau-pilon, perceuse, noria,
 * soufflet), code de l'ancienne {@code MechanicalMachineTile} qui ne fait plus que la porter (noeud du reseau : arbre, inertie, limites,
 * rendu). Energie motrice : la rotation ({@link PowerSource.Kinetic}) : l'unite LIT la vitesse et DECLARE son couple de travail (plein tant
 * qu'une recette est engagee, 5 % a vide) ; travail compte en tours de l'arbre ({@link MechanicalRecipe}), cadence plafonnee a 1,5 x la
 * nominale. Le soufflet et la noria agissent sur le bloc d'en face / au-dessus ; la perceuse tire de la pierre de la roche dessous.
 * 1.0.2-beta (lot 2) : le martinet est retire (forge des tetes d'outils a la main ; son cintrage passe au tour) ; le tamis n'a plus de case
 * de maille (rendement de base = celui de l'ancienne maille de fibres, la toile est dans le modele).
 * <p>
 * Sauvegarde au format d'avant le lot 7 : {@code slots} (entrees, sorties), {@code progress} (tours, flottant), {@code placedWater},
 * {@code water}, {@code overflow}, {@code recipe}, {@code lockedRecipe}. Cases aux places de l'interface standard v2 ({@link StandardLayout}).
 * 1.0.2-beta : un tamis d'avant (cle {@link #MESHLESS} absente) avait sa maille en case 1 : elle part au trop-plein, les sorties reculent
 * d'une case ; la loupe {@code bloom} d'un martinet (remappe en tour) part au trop-plein.
 */
public class MechanicalProcess extends ProcessUnit {

    public static final int STATUS_IDLE = 0, STATUS_WORKING = 1, STATUS_SLOW = 2, STATUS_FULL = 3, STATUS_STOPPED = 4;
    /** 1.0.2-beta : marque des tamis sans case de maille (sauvegarde ecrite apres le lot 2). */
    private static final String MESHLESS = "meshless";
    private static final int WATER_BUFFER = 2_000;

    private final MechanicalMachineTile tile;
    public final MachineType type;
    private final PowerSource.Kinetic power;
    private final List<ItemSlot> all = new ArrayList<>();
    private float turns;
    @Nullable
    private ResourceLocation recipeId;
    @Nullable
    private MechanicalRecipe recipe;
    private boolean engaged;
    private double boost;
    private boolean placedWater;
    /** Noria : eau montee en attente (mB), offerte aux conduites et reservoirs voisins. */
    private int water;
    private int sentStatus = -1;
    /** Tirages qui n'ont pas trouve de place : vides d'abord dans les sorties, la machine attend tant qu'il en reste. */
    private final List<ItemStack> overflow = new ArrayList<>();
    private float clientProgress, sentProgress;
    /**
     * 1.0.5-beta (lot 29) : avancement par tick (0..1) au dernier tick de travail, envoye avec l'avancement : le client l'extrapole (la buche de
     * la scie avance en continu au lieu de sauter de 10 % en 10 %) et le serveur ne renvoie plus que si le vrai avancement s'ecarte de 10 % de
     * cette extrapolation (fin d'une piece, vitesse changee) : un paquet par piece au lieu de dix.
     */
    private float step, sentStep, clientStep;
    private double predicted, clientAt;
    @Nullable
    private MechanicalRecipe lockedRecipe;
    @Nullable
    private ResourceLocation lockedFor;
    private int lockedGeneration = -1;
    private ItemStack lastWork = ItemStack.EMPTY;

    public MechanicalProcess(UnitHost machine, MechanicalMachineTile tile, MachineType type) {
        super(machine, CableTier.BASIC, new StandardLayout.Counts(type.inputs, type.outputs, 0, 0, 0, 0));
        this.tile = tile;
        this.type = type;
        this.power = (PowerSource.Kinetic) machine.power();
        for (int i = 0; i < type.inputs; i++) {
            int[] p = layout.itemIn.get(i);
            inputs.add(machine.slot(new ItemSlot(() -> 64, this::acceptsInput, (s, a) -> true, (s, a) -> !a.external(), machine::changed, p[0], p[1])));
        }
        for (int i = 0; i < type.outputs; i++) {
            int[] p = layout.itemOut.get(i);
            outputs.add(machine.slot(new ItemSlot(() -> 64, s -> false, (s, a) -> false, (s, a) -> true, machine::changed, p[0], p[1])));
        }
        all.addAll(inputs);
        all.addAll(outputs);
        power.declareLoad(type.torque * 0.05);
    }

    // ------------------------------------------------------------------
    // Etat (rendu, interface)
    // ------------------------------------------------------------------

    public float progressRatio() {
        Level level = level();
        if (level != null && level.isClientSide) {
            if (clientStep > 0 && status == STATUS_WORKING) {
                return (float) Math.min(1, clientProgress + clientStep * Math.max(0, RenderClock.now() - clientAt));
            }
            return clientProgress;
        }
        if (type == MachineType.DRILL) {
            return Math.min(1, turns / DrillProducts.TURNS);
        }
        return recipe == null ? 0 : Math.min(1, turns / recipe.turns());
    }

    public List<ItemSlot> all() {
        return all;
    }

    public ItemStack workpiece() {
        return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0).getStack();
    }

    public boolean engaged() {
        return engaged;
    }

    /** Noria : eau en reserve (mB). */
    public int water() {
        return water;
    }

    /** Noria : eau prise par une conduite voisine. */
    public int drainWater(int max, boolean execute) {
        int n = Math.min(max, water);
        if (execute && n > 0) {
            water -= n;
            machine.changed();
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Entrees
    // ------------------------------------------------------------------

    private boolean acceptsInput(ItemStack stack) {
        Level level = level();
        if (type.recipe == null || level == null) {
            return false;
        }
        //MAJ Recettes (lot 7) : verrouillee, la machine ne prend que l'ingredient de sa recette (entonnoirs, goulottes, robots)
        return lockAllows(stack, () -> accepts(level, type.recipe, stack));
    }

    /**
     * 0.20.46 (audit) : objets acceptes par sorte de recette, gardes par objet (piles sans NBT) jusqu'au prochain rechargement des recettes ;
     * caches separes client / serveur.
     */
    private static final class AcceptSide {

        RecipeManager manager;
        int generation = -1;
        final Map<MechanicalRecipe.Kind, Map<Item, Boolean>> known = new EnumMap<>(MechanicalRecipe.Kind.class);
    }

    private static final AcceptSide[] ACCEPTS = {new AcceptSide(), new AcceptSide()};

    static synchronized boolean accepts(Level level, MechanicalRecipe.Kind kind, ItemStack stack) {
        if (stack.isEmpty() || stack.hasTag()) {
            return scanAccepts(level, kind, stack);
        }
        AcceptSide side = ACCEPTS[level.isClientSide ? 1 : 0];
        RecipeManager manager = level.getRecipeManager();
        int generation = PiRecipes.generation();
        if (manager != side.manager || side.generation != generation) {
            side.manager = manager;
            side.generation = generation;
            side.known.clear();
        }
        Map<Item, Boolean> known = side.known.computeIfAbsent(kind, k -> new HashMap<>());
        Boolean result = known.get(stack.getItem());
        if (result == null) {
            result = scanAccepts(level, kind, stack);
            known.put(stack.getItem(), result);
        }
        return result;
    }

    private static boolean scanAccepts(Level level, MechanicalRecipe.Kind kind, ItemStack stack) {
        for (MechanicalRecipe r : PiRecipes.sorted(level, PiRecipes.MECHANICAL.get(kind).get())) {
            if (r.accepts(stack)) {
                return true;
            }
        }
        return false;
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
        if (sentStatus == STATUS_WORKING) {
            predicted += sentStep;                               //lot 29 : ce que le client affiche (extrapolation)
        }
        step = 0;
        switch (type) {
            case BELLOWS -> bellows(level, omega);
            case NORIA -> noria(level, omega);
            case DRILL -> drill(level, omega, rate);
            default -> process(level, omega, rate);
        }
        //Charge declaree au reseau : couple de travail engage, 5 % a vide
        power.declareLoad(engaged ? type.torque : type.torque * 0.05);
        if (status != sentStatus) {
            sentStatus = status;
            machine.syncNow();
        }
    }

    private void process(ServerLevel level, double omega, double rate) {
        MechanicalRecipe found = findRecipe(level);
        if (found != recipe) {
            if (found == null || recipeId == null || !found.getId().equals(recipeId)) {
                turns = 0;
            }
            recipe = found;
            recipeId = found == null ? null : found.getId();
        }
        if (recipe == null) {
            engaged = false;
            status = STATUS_IDLE;
            return;
        }
        if (!flushOverflow() || !hasRoom(recipe)) {
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
        step = (float) (rate / recipe.turns());
        float before = turns;
        turns += rate;
        //Un evenement sonore par tour (coup de marteau, passe du tamis...)
        if ((int) before != (int) turns || type == MachineType.SAW && level.getGameTime() % 6 == 0) {
            effects(level);
        }
        if (turns >= recipe.turns()) {
            turns -= recipe.turns();
            craft(level, recipe);
        }
    }

    @Nullable
    private MechanicalRecipe findRecipe(ServerLevel level) {
        if (type.recipe == null || inputs.isEmpty()) {
            return null;
        }
        ItemStack in = inputs.get(0).getStack();
        if (in.isEmpty()) {
            return null;
        }
        MechanicalRecipe locked = locked(level);
        if (locked != null) {
            //MAJ Recettes (lot 7) : seule la recette verrouillee (sans recherche : resolue une fois)
            return locked.accepts(in) && in.getCount() >= locked.count() ? locked : null;
        }
        if (recipe != null && recipe.accepts(in) && in.getCount() >= recipe.count()) {
            return recipe;
        }
        for (MechanicalRecipe r : PiRecipes.sorted(level, PiRecipes.MECHANICAL.get(type.recipe).get())) {
            if (r.accepts(in) && in.getCount() >= r.count()) {
                return r;
            }
        }
        return null;
    }

    /** La recette verrouillee (null : aucun verrou, ou recette disparue : comme sans verrou). */
    @Nullable
    private MechanicalRecipe locked(Level level) {
        if (!lock.isSet() || type.recipe == null || recipeSource().isEmpty()) {
            return null;
        }
        if (!lock.id().equals(lockedFor) || lockedGeneration != PiRecipes.generation()) {
            lockedFor = lock.id();
            lockedGeneration = PiRecipes.generation();
            lockedRecipe = null;
            for (MechanicalRecipe r : PiRecipes.sorted(level, PiRecipes.MECHANICAL.get(type.recipe).get())) {
                if (r.getId().equals(lockedFor)) {
                    lockedRecipe = r;
                    break;
                }
            }
        }
        return lockedRecipe;
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

    // 0.20.46 (audit) : place en sortie recalculee seulement quand les sorties (objet, nombre, NBT) ou ce qu'on veut y mettre changent
    @Nullable
    private Object roomFor;
    private boolean roomOk;
    private Item[] roomItems = new Item[0];
    private int[] roomCounts = new int[0];
    private CompoundTag[] roomTags = new CompoundTag[0];

    private boolean roomKnown(Object key) {
        if (roomFor != key || roomItems.length != outputs.size()) {
            return false;
        }
        for (int i = 0; i < roomItems.length; i++) {
            ItemStack stack = outputs.get(i).getStack();
            if (stack.getItem() != roomItems[i] || stack.getCount() != roomCounts[i] || stack.getTag() != roomTags[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean rememberRoom(Object key, boolean ok) {
        int n = outputs.size();
        if (roomItems.length != n) {
            roomItems = new Item[n];
            roomCounts = new int[n];
            roomTags = new CompoundTag[n];
        }
        for (int i = 0; i < n; i++) {
            ItemStack stack = outputs.get(i).getStack();
            roomItems[i] = stack.getItem();
            roomCounts[i] = stack.getCount();
            roomTags[i] = stack.getTag();
        }
        roomFor = key;
        roomOk = ok;
        return ok;
    }

    /** Place pour les resultats surs (chance 1) et au moins une sortie libre pour les tirages ; le reste part au trop-plein. */
    private boolean hasRoom(MechanicalRecipe r) {
        if (roomKnown(r)) {
            return roomOk;
        }
        return rememberRoom(r, computeRoom(r));
    }

    private boolean computeRoom(MechanicalRecipe r) {
        List<ItemStack> worst = new ArrayList<>();
        boolean chancy = false;
        for (MechanicalRecipe.Result result : r.results()) {
            if (result.chance() >= 1) {
                worst.add(result.stack().copy());
            } else {
                chancy = true;
            }
        }
        if (chancy && worst.isEmpty()) {
            for (ItemSlot slot : outputs) {
                if (slot.isEmpty()) {
                    return true;
                }
            }
            //Aucune case vide : on accepte encore si un resultat possible peut s'empiler quelque part
            for (MechanicalRecipe.Result result : r.results()) {
                for (ItemSlot slot : outputs) {
                    ItemStack in = slot.getStack();
                    if (ItemStack.isSameItemSameTags(in, result.stack()) && in.getCount() + result.stack().getCount() <= in.getMaxStackSize()) {
                        return true;
                    }
                }
            }
            return false;
        }
        List<ItemStack> sim = new ArrayList<>();
        for (ItemSlot slot : outputs) {
            sim.add(slot.getStack().copy());
        }
        for (ItemStack stack : worst) {
            if (!merge(sim, stack, false)) {
                return false;
            }
        }
        return true;
    }

    private boolean merge(List<ItemStack> slots, ItemStack stack, boolean real) {
        return mergeRest(slots, stack, real).isEmpty();
    }

    /** Ajoute une pile aux sorties (simulees ou reelles) ; renvoie ce qui n'a pas trouve de place. */
    private ItemStack mergeRest(List<ItemStack> slots, ItemStack stack, boolean real) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < slots.size() && !rest.isEmpty(); i++) {
            ItemStack in = slots.get(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, rest)) {
                int n = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(n);
                rest.shrink(n);
                if (real) {
                    outputs.get(i).setStack(in);
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

    private void craft(ServerLevel level, MechanicalRecipe r) {
        //1.0.2-beta (Guillaume : « le tamis ne demande plus de filtre pour etre efficace ») : rendement de base = celui de l'ancienne maille
        //de fibres (tier 1, chances x1, sans second tirage) ; la toile fait partie du tamis
        int mesh = 1;
        float bonus = 1, second = 0;
        List<ItemStack> live = new ArrayList<>();
        for (ItemSlot slot : outputs) {
            live.add(slot.getStack().copy());
        }
        for (ItemStack stack : r.roll(level.random, mesh, bonus, second)) {
            ItemStack rest = mergeRest(live, stack, true);
            if (!rest.isEmpty()) {
                overflow.add(rest);
            }
        }
        inputs.get(0).extract(r.count(), Action.EXECUTE, Automation.INTERNAL);
        KineticEvents.award(level, pos(), "machine_" + type.key());
        machine.changed();
    }

    private void effects(ServerLevel level) {
        SoundEvent sound = switch (type) {
            case MILLSTONE -> SoundEvents.GRINDSTONE_USE;
            case SIFTER -> SoundEvents.GRAVEL_HIT;
            case JAW_CRUSHER -> SoundEvents.STONE_BREAK;
            case SAW -> SoundEvents.AXE_STRIP;
            case LATHE -> SoundEvents.WOOD_HIT;
            default -> SoundEvents.WOOD_STEP;
        };
        BlockPos at = pos();
        float volume = 0.5F;
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, 0.8F + level.random.nextFloat() * 0.3F);
        ItemStack work = workpiece();
        if (!work.isEmpty() && work.getItem() instanceof BlockItem block) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.getBlock().defaultBlockState()), at.getX() + 0.5, at.getY() + 1.0,
                  at.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.05);
        }
    }

    // ------------------------------------------------------------------
    // Soufflet et noria
    // ------------------------------------------------------------------

    private void bellows(ServerLevel level, double omega) {
        BlockPos target = pos().relative(tile.facing());
        BlockEntity be = level.getBlockEntity(target);
        //Fours de l'age du bronze (creuset, haut fourneau...) : l'air leur est livre directement ; forge primaire : par un feu de camp, au
        //creuset pose dessus (ForgeHeat.airReceiver)
        AirBlastReceiver receiver = ForgeHeat.airReceiver(level, target);
        if (receiver != null) {
            engaged = true;
            status = omega >= type.minSpeed ? STATUS_WORKING : STATUS_SLOW;
            if (omega >= type.minSpeed) {
                receiver.blast(Math.min(1.0, omega / type.nominalSpeed));
                if (level.getGameTime() % 100 == 0) {
                    KineticEvents.award(level, pos(), "machine_bellows");
                }
            }
            return;
        }
        engaged = be instanceof AbstractFurnaceBlockEntity && omega >= type.minSpeed;
        status = engaged ? STATUS_WORKING : STATUS_IDLE;
        if (!(be instanceof AbstractFurnaceBlockEntity furnace) || omega < type.minSpeed) {
            return;
        }
        BlockState state = level.getBlockState(target);
        if (!state.hasProperty(AbstractFurnaceBlock.LIT) || !state.getValue(AbstractFurnaceBlock.LIT)) {
            return;
        }
        //Le soufflet active le feu : la cuisson (et la combustion) vont plus vite, jusqu'a x2
        boost += Math.min(1.0, omega / type.nominalSpeed);
        while (boost >= 1) {
            boost -= 1;
            AbstractFurnaceBlockEntity.serverTick(level, target, state, furnace);
        }
        if (level.getGameTime() % 100 == 0) {
            KineticEvents.award(level, pos(), "machine_bellows");
        }
        if (level.getGameTime() % 8 == 0) {
            level.sendParticles(ParticleTypes.FLAME, target.getX() + 0.5, target.getY() + 0.6, target.getZ() + 0.5, 2, 0.25, 0.1, 0.25, 0.01);
        }
    }

    private void noria(ServerLevel level, double omega) {
        BlockPos at = pos(), below = at.below(), above = at.above();
        FluidState pool = level.getFluidState(below);
        boolean running = omega >= type.minSpeed && pool.is(FluidTags.WATER);
        engaged = pool.is(FluidTags.WATER);
        status = running ? STATUS_WORKING : STATUS_IDLE;
        if (running && level.getGameTime() % 100 == 0) {
            KineticEvents.award(level, at, "machine_noria");
        }
        if (running) {
            //L'eau montee va dans la reserve, puis vers le haut ou une conduite voisine (goulotte, conduite en bois, cable a fluides)
            water = Math.min(WATER_BUFFER, water + (int) Math.round(30 * Math.min(1.5, omega / type.nominalSpeed)));
            boolean piped = false;
            for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                //1.9 (MAJ RP, bloc 4) : l'eau ne passe pas une frontiere de claim refusee
                BlockEntity next = ClanApi.canTransfer(level, at, at.relative(d)) ? level.getBlockEntity(at.relative(d)) : null;
                IFluidHandler handler = next == null ? null : next.getCapability(ForgeCapabilities.FLUID_HANDLER, d.getOpposite()).orElse(null);
                if (handler != null) {
                    piped = true;
                    if (water > 0) {
                        water -= handler.fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
            if (piped) {
                machine.changed();
            } else if (level.getBlockState(above).isAir()) {
                level.setBlock(above, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                placedWater = true;
                machine.changed();
            }
        } else if (placedWater) {
            if (level.getFluidState(above).isSource() && level.getFluidState(above).is(FluidTags.WATER)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            placedWater = false;
            machine.changed();
        }
    }

    // ------------------------------------------------------------------
    // Perceuse
    // ------------------------------------------------------------------

    /** Roche sous la perceuse : un bloc tous les {@link DrillProducts#TURNS} tours, dans la case de sortie. */
    private void drill(ServerLevel level, double omega, double rate) {
        BlockPos at = pos();
        if (level.getGameTime() % 10 == (at.asLong() & 7)) {
            pushOut(level);
        }
        BlockState rock = level.getBlockState(at.below());
        ItemStack product = DrillProducts.of(rock);
        if (product.isEmpty()) {
            engaged = false;
            status = STATUS_IDLE;
            return;
        }
        boolean fits;
        if (roomKnown(product.getItem())) {
            fits = roomOk;
        } else {
            List<ItemStack> sim = new ArrayList<>();
            for (ItemSlot slot : outputs) {
                sim.add(slot.getStack().copy());
            }
            fits = rememberRoom(product.getItem(), merge(sim, product, false));
        }
        if (!fits) {
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
        step = (float) (rate / DrillProducts.TURNS);
        float before = turns;
        turns += rate;
        if ((int) (before * 2) != (int) (turns * 2)) {
            level.playSound(null, at, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.45F, 0.7F + level.random.nextFloat() * 0.3F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock), at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.78, 5, 0.12, 0.02,
                  0.12, 0.06);
        }
        if (turns >= DrillProducts.TURNS) {
            turns -= DrillProducts.TURNS;
            List<ItemStack> live = new ArrayList<>();
            for (ItemSlot slot : outputs) {
                live.add(slot.getStack().copy());
            }
            mergeRest(live, product, true);
            KineticEvents.award(level, at, "machine_" + type.key());
            machine.changed();
        }
    }

    /** Perceuse : pousse sa case de sortie (4 objets au plus) dans un conteneur voisin : devant, cotes, arriere, dessus. */
    private void pushOut(ServerLevel level) {
        if (outputs.isEmpty() || outputs.get(0).isEmpty()) {
            return;
        }
        BlockPos at = pos();
        Direction front = tile.facing();
        for (Direction d : new Direction[]{front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite(), Direction.UP}) {
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            BlockEntity next = ClanApi.canTransfer(level, at, at.relative(d)) ? level.getBlockEntity(at.relative(d)) : null;
            if (next == null || next instanceof MechanicalMachineTile) {
                continue;
            }
            IItemHandler handler = next.getCapability(ForgeCapabilities.ITEM_HANDLER, d.getOpposite()).orElse(null);
            if (handler == null) {
                continue;
            }
            ItemSlot slot = outputs.get(0);
            ItemStack offer = slot.getStack().copyWithCount(Math.min(4, slot.getStack().getCount()));
            ItemStack rest = offer;
            for (int i = 0; i < handler.getSlots() && !rest.isEmpty(); i++) {
                rest = handler.insertItem(i, rest, false);
            }
            int moved = offer.getCount() - rest.getCount();
            if (moved > 0) {
                ItemStack left = slot.getStack().copy();
                left.shrink(moved);
                slot.setStack(left);
                machine.changed();
                return;
            }
        }
    }

    void onBroken() {
        Level level = level();
        if (placedWater && level != null && level.getFluidState(pos().above()).isSource()) {
            level.setBlock(pos().above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
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
    // Valeurs d'interface (memes indices qu'avant le lot 7), client, sauvegarde
    // ------------------------------------------------------------------

    /** 0 vitesse x100, 1 couple x10, 2 avancement x1000, 3 etat, 4 cadence en % de la nominale, 5 type, 6 puissance du reseau (W), 7 tours x100. */
    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.SPEED, () -> Math.round(power.speed() * 100)).add(UiValues.TORQUE, () -> Math.round(power.torque() * 10))
              .add(UiValues.PROGRESS_X1000, () -> Math.round(progressRatio() * 1000)).add(UiValues.STATUS, () -> status)
              .add(UiValues.RATE, () -> Math.round(Math.min(power.omega(), type.nominalSpeed * 1.5) / type.nominalSpeed * 100))
              .add(UiValues.MACHINE_TYPE, type::ordinal).add(UiValues.NET_POWER, () -> Math.round(power.networkPower()))
              .add(UiValues.TURNS, () -> type == MachineType.DRILL ? Math.round(DrillProducts.TURNS * 100) : recipe == null ? 0
                    : Math.round(recipe.turns() * 100));
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putInt("status", status);
        sentProgress = progressRatio();
        tag.putFloat("progress", sentProgress);
        sentStep = status == STATUS_WORKING ? step : 0;
        predicted = sentProgress;
        tag.putFloat("step", sentStep);
        if (!inputs.isEmpty()) {
            tag.put("work", inputs.get(0).getStack().save(new CompoundTag()));
        }
        lock.writeClient(tag);
    }

    @Override
    public void readClient(CompoundTag tag) {
        status = tag.getInt("status");
        clientProgress = tag.getFloat("progress");
        clientStep = tag.getFloat("step");                        //absent (avant le lot 29) : 0, pas d'extrapolation
        clientAt = RenderClock.now();
        if (!inputs.isEmpty() && tag.contains("work")) {
            inputs.get(0).setStack(ItemStack.of(tag.getCompound("work")));
        }
        lock.readClient(tag);
    }

    @Override
    public boolean clientDirty() {
        if (inputs.isEmpty()) {
            return false;
        }
        ItemStack now = inputs.get(0).getStack();
        if (!ItemStack.isSameItem(now, lastWork) || now.isEmpty() != lastWork.isEmpty()) {
            lastWork = now.copy();
            return true;
        }
        //Avancement : la buche de la scie, les pierres du concasseur ; 1.0.5-beta (lot 29) : le client extrapole, renvoi a 10 % d'ecart a son
        //extrapolation (avant : a chaque pas de 10 % de l'avancement)
        return Math.abs(progressRatio() - Math.min(1, predicted)) >= 0.1F;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("slots", ItemSlots.save(all));
        tag.putFloat("progress", turns);
        tag.putBoolean("placedWater", placedWater);
        tag.putInt("water", water);
        if (!overflow.isEmpty()) {
            ListTag list = new ListTag();
            overflow.forEach(stack -> list.add(stack.save(new CompoundTag())));
            tag.put("overflow", list);
        }
        if (recipeId != null) {
            tag.putString("recipe", recipeId.toString());
        }
        if (type == MachineType.SIFTER) {
            tag.putBoolean(MESHLESS, true);
        }
        lock.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        ListTag slots = tag.getList("slots", 10);
        List<ItemStack> legacy = new ArrayList<>();
        if (type == MachineType.SIFTER && !tag.getBoolean(MESHLESS)) {
            //1.0.2-beta : tamis d'avant le lot 2 : case 1 = la maille (rendue au trop-plein), les sorties reculent d'une case
            ListTag moved = new ListTag();
            for (Tag t : slots) {
                CompoundTag entry = ((CompoundTag) t).copy();
                int index = entry.getShort("Slot");
                if (index == 1) {
                    legacy.add(ItemStack.of(entry));
                } else {
                    if (index > 1) {
                        entry.putShort("Slot", (short) (index - 1));
                    }
                    moved.add(entry);
                }
            }
            slots = moved;
        }
        ItemSlots.load(all, slots);
        turns = tag.getFloat("progress");
        recipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        placedWater = tag.getBoolean("placedWater");
        water = tag.getInt("water");
        if (tag.contains("bloom")) {
            //1.0.2-beta : loupe d'un martinet retire (bloc remappe en tour a bois) : rendue au trop-plein
            legacy.add(ItemStack.of(tag.getCompound("bloom")));
        }
        lock.load(tag);
        overflow.clear();
        for (Tag t : tag.getList("overflow", 10)) {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (!stack.isEmpty()) {
                overflow.add(stack);
            }
        }
        for (ItemStack stack : legacy) {
            if (!stack.isEmpty()) {
                overflow.add(stack);
            }
        }
    }

    // ------------------------------------------------------------------
    // Selecteur de recette (toutes les machines a recettes sauf le tamis : sa maille choisit deja)
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return type.recipe == null || type == MachineType.SIFTER ? "" : RecipeSources.mechanical(type.recipe);
    }

    @Override
    public void recipeLockChanged() {
        lockedFor = null;
        machine.syncNow();
    }

    @Override
    public int recipeTier() {
        return 0;
    }

    @Override
    public List<ItemStack> presentItems() {
        return inputs.isEmpty() ? List.of() : List.of(inputs.get(0).getStack());
    }
}
