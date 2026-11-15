package fr.pioneers.content.spacesim;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.compat.mekanism.MekanismCompat;
import fr.pioneers.compat.mekanism.MekanismGasBridge;
import fr.pioneers.content.universe.GalaxyType;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.recipe.PiRecipes;
import fr.pioneers.recipe.SolarSimulationRecipe;
import fr.pioneers.registries.PiSpaceSim;
import fr.pioneers.simulation.ItemSimulatedPlanet;
import fr.pioneers.simulation.SimulatedPlanet;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controleur du Simulateur spatial (fusion des simulateurs du systeme solaire et d'univers). Pose dans la coque (en bas au centre d'une face laterale), il porte
 * la logique ; le mode se choisit a l'interface (aucun coeur) ({@link SpaceSimMode}) :
 * <ul>
 *     <li>systeme solaire (Stellar Core, garde tant que le soleil brille) : {@link #SOLAR_IGNITION_FE} FE et {@link #SOLAR_IGNITION_PLASMA} mB de
 *     plasma pour l'allumage, puis {@link #SOLAR_UPKEEP_FE} FE et 1 mB par tick ; chaque planete installee produit sa recette
 *     {@link SolarSimulationRecipe} a chaque cycle ;</li>
 *     <li>galaxie (Void Singularity Cell, consommee au Big Bang) : energie et plasma du Big Bang, puis la consommation de la galaxie choisie
 *     ({@link GalaxyType}), qui depose ses produits a chaque cycle.</li>
 * </ul>
 * Energie et plasma entrent par les ports de la coque ; les produits en sortent par les ports en sortie. S'il manque de l'energie ou du
 * plasma, ou si la structure casse, la simulation s'effondre. En marche, l'anneau de bobines supremes devient un torrent d'energie.
 * <p>
 * Interface : 4 mode, 5 etat, 6 en marche, 7 forme, 8 galaxie, 9 avancement, 10 duree du cycle, 11 cycles, 12 energie par tick, 13 plasma par
 * tick, 14 energie d'allumage, 15 plasma d'allumage, 16 planetes, 17 energie consommee, 18 coeur conforme au mode.
 */
public class SpaceSimControllerTile extends MachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final long ENERGY_CAPACITY_FE = 1_000_000_000_000L;
    public static final long PLASMA_CAPACITY = 128_000;
    public static final long SOLAR_IGNITION_FE = 50_000_000_000L, SOLAR_UPKEEP_FE = 40_000_000L;
    public static final long SOLAR_IGNITION_PLASMA = 4_000, SOLAR_UPKEEP_PLASMA = 1;
    public static final long GALAXY_IGNITION_FE = 250_000_000_000L, GALAXY_IGNITION_PLASMA = 16_000;
    public static final int PLANETS = SimulatedPlanet.values().length, OUTPUTS = 12;
    public static final int ACTION_IGNITE = CUSTOM, ACTION_SHUTDOWN = CUSTOM + 1, ACTION_GALAXY_PREV = CUSTOM + 2, ACTION_GALAXY_NEXT = CUSTOM + 3,
          ACTION_MODE_SOLAR = CUSTOM + 4, ACTION_MODE_GALAXY = CUSTOM + 5;
    public static final int GUI_HEIGHT = 164;
    // --- Interface ---
    /**
     * Lot 8c : interface en VRAIE v2 (196 de large) : energie et plasma a gauche, carte d'etat, choix du mode a droite ; rangee allumage /
     * arret / galaxie ; puis les huit cases des planetes et les douze cases de sortie, centrees (ORDRE des cases inchange). Geometrie
     * partagee avec la vue {@code CosmicViews.SIMULATOR} et le banc.
     */
    public static final int PLANETS_X = (StandardLayout.WIDTH - 8 * 18) / 2 + 1, PLANETS_Y = 105, OUTPUT_COLUMNS = 6,
          OUTPUTS_X = (StandardLayout.WIDTH - OUTPUT_COLUMNS * 18) / 2 + 1, OUTPUTS_Y = 126;
    public static final StandardLayout.Rect ENERGY = new StandardLayout.Rect(8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          PLASMA = new StandardLayout.Rect(24, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H),
          MODES = new StandardLayout.Rect(StandardLayout.WIDTH - 8 - 24, StandardLayout.TOP, 24, 26),
          CARD = new StandardLayout.Rect(40, StandardLayout.TOP, StandardLayout.WIDTH - 8 - 24 - 4 - 40, StandardLayout.GAUGE_H),
          ROW = new StandardLayout.Rect(8, StandardLayout.TOP + StandardLayout.GAUGE_H + 4, StandardLayout.WIDTH - 16, StandardLayout.STEP_H);

    /** Pourquoi la simulation est arretee (affichage). */
    public enum Status {
        OFF, RUNNING, NO_RESEARCH, NO_CORE, NO_ENERGY, NO_PLASMA, COLLAPSED_ENERGY, COLLAPSED_PLASMA, COLLAPSED_STRUCTURE, OUTPUT_FULL;

        public static Status byIndex(int index) {
            return index >= 0 && index < values().length ? values()[index] : OFF;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final BasicGasTank plasmaTank;
    public final List<ItemSlot> planetSlots = new ArrayList<>();
    public final List<ItemSlot> outputSlots = new ArrayList<>();
    private final ItemSlots outputHandler;

    /** Lot 8a : cycle de vie commun des multiblocs maison (verification toutes les 2 s ou a la demande, revalidee au chargement). */
    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public net.minecraft.world.level.Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public Direction front() {
            return getBlockState().getValue(MachineBlock.FACING);
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            return level instanceof ServerLevel server ? SpaceSimStructure.survey(server, SpaceSimControllerTile.this) : null;
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            SpaceSimStructure.attach((ServerLevel) level, SpaceSimControllerTile.this, (SpaceSimStructure.Result) survey.data(), first);
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            SpaceSimStructure.release((ServerLevel) level, SpaceSimControllerTile.this);
            if (!survey.removal()) {
                setStructure(false, List.of());
            }
        }

        @Override
        public void houseChanged() {
            setChanged();
            syncNow();
        }
    }, 40);
    private final List<BlockPos> coils = new ArrayList<>();
    private boolean coilsLit;
    private boolean running;
    private SpaceSimMode runMode = SpaceSimMode.NONE;
    /** Mode choisi au controleur (le coeur doit lui correspondre a l'allumage). */
    private SpaceSimMode selectedMode = SpaceSimMode.SOLAR;
    @Nullable
    private BlockPos center;
    private Status status = Status.OFF;
    private int galaxy;
    private int galaxyProgress;
    private int cycles;
    private final int[] planetProgress = new int[PLANETS];
    private long lastEnergyUsed;
    /** Temps de jeu du dernier allumage (animation d'expansion cote client). */
    private long ignitedAt;
    private final SolarSimulationRecipe[] recipes = new SolarSimulationRecipe[PLANETS];
    private final ItemStack[] recipeKeys = new ItemStack[PLANETS];

    public SpaceSimControllerTile(BlockPos pos, BlockState state) {
        super(PiSpaceSim.CONTROLLER_TILE.get(), pos, state, ENERGY_CAPACITY_FE, Integer.MAX_VALUE);
        plasmaTank = gasTank(BasicGasTank.input(PLASMA_CAPACITY, SpaceSimControllerTile::isPlasma, this::changed));
        for (SimulatedPlanet planet : SimulatedPlanet.values()) {
            planetSlots.add(slot(new ItemSlot(() -> 1, stack -> stack.getItem() instanceof ItemSimulatedPlanet item && item.isFilled() && item.getPlanet() == planet,
                  (stack, automation) -> true, (stack, automation) -> automation != Automation.EXTERNAL, this::changed, PLANETS_X + 18 * planet.ordinal(), PLANETS_Y)
                  .ghostIcon("ghost_planet")));
            recipeKeys[planet.ordinal()] = ItemStack.EMPTY;
        }
        for (int i = 0; i < OUTPUTS; i++) {
            outputSlots.add(slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL, (stack, automation) -> true,
                  this::changed, OUTPUTS_X + 18 * (i % OUTPUT_COLUMNS), OUTPUTS_Y + 18 * (i / OUTPUT_COLUMNS))));
        }
        outputHandler = new ItemSlots(outputSlots, Automation.EXTERNAL);
    }

    public static boolean isPlasma(GasStack stack) {
        ResourceLocation id = stack.getGas().getRegistryName();
        return id != null && id.getPath().equals("stellar_plasma");
    }

    private void changed() {
        setChanged();
        markSync();
    }

    /** Mode choisi au controleur (ou ce qui tourne). */
    public SpaceSimMode getMode() {
        return running ? runMode : selectedMode;
    }

    /** Plus de coeur : le mode choisi suffit. */
    public boolean coreMatches() {
        return true;
    }

    public GalaxyType getGalaxy() {
        return GalaxyType.byIndex(galaxy);
    }

    /** Centre du globe (calcule a la verification, synchronise au client). */
    public BlockPos getCenter() {
        if (center == null) {
            center = level == null ? worldPosition : SpaceSimStructure.center(level, worldPosition, getBlockState().getValue(MachineBlock.FACING));
        }
        return center;
    }

    BlockPos resolveCenter() {
        BlockPos found = SpaceSimStructure.center(level, worldPosition, getBlockState().getValue(MachineBlock.FACING));
        if (!found.equals(center)) {
            center = found;
            syncNow();
        }
        return found;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /** Lot 8a : crochets du cycle commun (bobines de l'anneau ; structure defaite : la simulation s'effondre). */
    void setStructure(boolean formed, List<BlockPos> coils) {
        this.coils.clear();
        this.coils.addAll(coils);
        if (!formed) {
            coilsLit = false;
            if (running) {
                collapse(Status.COLLAPSED_STRUCTURE);
            }
        }
    }

    public boolean isFormed() {
        return house.formed();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    /** Lot 8a : un bloc a change dans le globe (registre des hotes ou file des pieces) : revalider au prochain tick. */
    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    /** Erreur au format d'avant ({@code cle|x y z}) : messages au joueur et ecran. */
    public String getError() {
        return house.legacyError();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            SpaceSimStructure.register(server, worldPosition);
            house.load();
        }
    }

    /** Dechargement du chunk (ou arret du serveur) : ne rien relire du monde, sinon le serveur se bloque en rechargeant le chunk. */
    private boolean unloading;

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            SpaceSimStructure.unregister(server, worldPosition);
            boolean broken = !unloading && level.isLoaded(worldPosition) && !level.getBlockState(worldPosition).is(getBlockState().getBlock());
            house.removed(!broken);
        }
    }

    // ------------------------------------------------------------------
    // Logique
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        house.tick();
        lastEnergyUsed = 0;
        if (running && isFormed()) {
            long energyUse = upkeepEnergy(), plasmaUse = upkeepPlasma();
            if (energy.getStored() < energyUse) {
                collapse(Status.COLLAPSED_ENERGY);
            } else if (plasmaTank.getStack().getAmount() < plasmaUse) {
                collapse(Status.COLLAPSED_PLASMA);
            } else {
                use(energyUse);
                plasmaTank.extract(plasmaUse, Action.EXECUTE, Automation.INTERNAL);
                lastEnergyUsed = energyUse;
                if (runMode == SpaceSimMode.SOLAR) {
                    simulatePlanets();
                } else {
                    simulateGalaxy();
                }
                if (level.getGameTime() % 4 == 0) {
                    torrentKills(server);
                }
                if (level.getGameTime() % 70 == 0) {
                    level.playSound(null, getCenter(), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3.0F, runMode == SpaceSimMode.SOLAR ? 0.75F : 0.65F);
                }
                setChanged();
            }
        }
        //L'anneau : torrent d'energie en marche, bobines visibles a l'arret
        boolean lit = running && isFormed();
        if (lit != coilsLit) {
            coilsLit = lit;
            for (BlockPos coil : coils) {
                SpaceSimStructure.light(level, coil, lit);
            }
            syncNow();
        }
        markWorking(running);
    }

    /** Le torrent d'energie de l'anneau tue sur le coup tout etre vivant qui le touche, quelle que soit son armure. */
    private void torrentKills(ServerLevel server) {
        BlockPos c = getCenter();
        double reach = SpaceSimShape.RING_OUTER + 1.5;
        AABB box = new AABB(c.getX() - reach, c.getY() - 1.5, c.getZ() - reach, c.getX() + 1 + reach, c.getY() + 2.5, c.getZ() + 1 + reach);
        for (LivingEntity entity : server.getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = entity.getX() - (c.getX() + 0.5), dz = entity.getZ() - (c.getZ() + 0.5);
            double r = Math.sqrt(dx * dx + dz * dz);
            boolean ring = r >= SpaceSimShape.RING_INNER - 1.2 && r <= SpaceSimShape.RING_OUTER + 1.2;
            boolean spoke = (Math.abs(dx) <= 1.2 || Math.abs(dz) <= 1.2) && r > SpaceSimShape.RADIUS + 0.5 && r < SpaceSimShape.RING_INNER;
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if ((ring || spoke) && entity.isAlive()) {
                entity.hurt(server.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                if (entity.isAlive()) {
                    entity.kill();
                }
            }
        }
    }

    public long upkeepEnergy() {
        return getMode() == SpaceSimMode.GALAXY ? getGalaxy().energyPerTick() : SOLAR_UPKEEP_FE;
    }

    public long upkeepPlasma() {
        return getMode() == SpaceSimMode.GALAXY ? getGalaxy().plasmaPerTick : SOLAR_UPKEEP_PLASMA;
    }

    public long ignitionEnergy() {
        return getMode() == SpaceSimMode.GALAXY ? Math.max(1, Math.round(GALAXY_IGNITION_FE * PiCommonConfig.get(PiCommonConfig.UNIVERSE_ENERGY, 1.0)))
                                                : SOLAR_IGNITION_FE;
    }

    public long ignitionPlasma() {
        return getMode() == SpaceSimMode.GALAXY ? GALAXY_IGNITION_PLASMA : SOLAR_IGNITION_PLASMA;
    }

    private void ignite() {
        if (running || !isFormed() || level == null) {
            return;
        }
        SpaceSimMode mode = getMode();
        if (mode == SpaceSimMode.NONE || !coreMatches()) {
            status = Status.NO_CORE;
        } else if (!ResearchEffects.has(mode == SpaceSimMode.SOLAR ? ResearchNode.SOLAR_SIMULATION : ResearchNode.UNIVERSE_SIMULATION)) {
            status = Status.NO_RESEARCH;
        } else if (energy.getStored() < ignitionEnergy() + upkeepEnergy()) {
            status = Status.NO_ENERGY;
        } else if (plasmaTank.getStack().getAmount() < ignitionPlasma() + upkeepPlasma()) {
            status = Status.NO_PLASMA;
        } else {
            use(ignitionEnergy());
            plasmaTank.extract(ignitionPlasma(), Action.EXECUTE, Automation.INTERNAL);
            if (mode == SpaceSimMode.GALAXY) {
                galaxyProgress = 0;
            }
            runMode = mode;
            running = true;
            status = Status.RUNNING;
            ignitedAt = level.getGameTime();
            level.playSound(null, getCenter(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3.0F, 0.9F);
        }
        setChanged();
        syncNow();
    }

    private void shutdown() {
        if (running) {
            running = false;
            status = Status.OFF;
            if (level != null) {
                level.playSound(null, getCenter(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0F, 0.9F);
            }
            setChanged();
            syncNow();
        }
    }

    private void collapse(Status reason) {
        running = false;
        status = reason;
        if (level != null) {
            level.playSound(null, getCenter(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0F, 0.7F);
        }
        setChanged();
        syncNow();
    }

    private void simulatePlanets() {
        for (int i = 0; i < PLANETS; i++) {
            SolarSimulationRecipe recipe = recipe(i);
            if (recipe == null) {
                planetProgress[i] = 0;
                continue;
            }
            if (planetProgress[i] < recipe.getDuration()) {
                planetProgress[i]++;
            }
            if (planetProgress[i] >= recipe.getDuration() && fits(recipe.getOutputs())) {
                insert(recipe.getOutputs());
                planetProgress[i] = 0;
            }
        }
    }

    private void simulateGalaxy() {
        GalaxyType type = getGalaxy();
        if (galaxyProgress < type.cycleTicks()) {
            galaxyProgress++;
            status = Status.RUNNING;
        }
        if (galaxyProgress >= type.cycleTicks()) {
            List<ItemStack> outputs = new ArrayList<>();
            type.outputs().forEach(output -> outputs.add(output.copy()));
            if (level != null && level.random.nextFloat() < type.bonusChance()) {
                outputs.add(type.bonus().copy());
            }
            if (fits(outputs)) {
                insert(outputs);
                galaxyProgress = 0;
                cycles++;
                status = Status.RUNNING;
            } else {
                status = Status.OUTPUT_FULL;
            }
        }
    }

    @Nullable
    private SolarSimulationRecipe recipe(int index) {
        ItemStack stack = planetSlots.get(index).getStack();
        if (stack.isEmpty() || level == null) {
            recipes[index] = null;
            recipeKeys[index] = ItemStack.EMPTY;
            return null;
        }
        if (recipes[index] == null || !ItemStack.isSameItemSameTags(recipeKeys[index], stack)) {
            recipes[index] = null;
            recipeKeys[index] = stack.copyWithCount(1);
            for (SolarSimulationRecipe recipe : PiRecipes.sorted(level, PiRecipes.SOLAR_SIMULATION.get())) {
                if (recipe.getPlanet().test(stack)) {
                    recipes[index] = recipe;
                    break;
                }
            }
        }
        return recipes[index];
    }

    /** Toutes les sorties d'un cycle trouvent-elles leur place (essai sur une copie) ? */
    private boolean fits(List<ItemStack> outputs) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemSlot slot : outputSlots) {
            copy.add(slot.getStack().copy());
        }
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (int i = 0; i < copy.size() && !remaining.isEmpty(); i++) {
                ItemStack existing = copy.get(i);
                int limit = remaining.getMaxStackSize();
                if (existing.isEmpty()) {
                    int moved = Math.min(limit, remaining.getCount());
                    copy.set(i, remaining.copyWithCount(moved));
                    remaining.shrink(moved);
                } else if (ItemStack.isSameItemSameTags(existing, remaining)) {
                    int moved = Math.min(limit - existing.getCount(), remaining.getCount());
                    if (moved > 0) {
                        existing.grow(moved);
                        remaining.shrink(moved);
                    }
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void insert(List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (ItemSlot slot : outputSlots) {
                remaining = slot.insert(remaining, Action.EXECUTE, Automation.INTERNAL);
                if (remaining.isEmpty()) {
                    break;
                }
            }
        }
    }

    /** Les produits sortent par un port en sortie vers un inventaire voisin. */
    void ejectInto(IItemHandler target) {
        for (ItemSlot slot : outputSlots) {
            if (slot.isEmpty()) {
                continue;
            }
            ItemStack offer = slot.getStack().copy();
            ItemStack rest = ItemHandlerHelper.insertItemStacked(target, offer, false);
            int moved = offer.getCount() - rest.getCount();
            if (moved > 0) {
                slot.extract(moved, Action.EXECUTE, Automation.INTERNAL);
            }
        }
    }

    IItemHandler outputHandler() {
        return outputHandler;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        switch (action) {
            case ACTION_IGNITE -> ignite();
            case ACTION_SHUTDOWN -> shutdown();
            case ACTION_MODE_SOLAR, ACTION_MODE_GALAXY -> {
                if (!running) {
                    selectedMode = action == ACTION_MODE_SOLAR ? SpaceSimMode.SOLAR : SpaceSimMode.GALAXY;
                    status = Status.OFF;
                    setChanged();
                    syncNow();
                }
            }
            case ACTION_GALAXY_PREV, ACTION_GALAXY_NEXT -> {
                int count = GalaxyType.values().length;
                galaxy = Math.floorMod(galaxy + (action == ACTION_GALAXY_NEXT ? 1 : -1), count);
                galaxyProgress = 0;
                setChanged();
                syncNow();
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Client (rendu)
    // ------------------------------------------------------------------

    public boolean isRunning() {
        return running;
    }

    public SpaceSimMode getRunMode() {
        return runMode;
    }

    public long getIgnitedAt() {
        return ignitedAt;
    }

    public boolean hasPlanet(int index) {
        return !planetSlots.get(index).isEmpty();
    }

    public int countPlanets() {
        int count = 0;
        for (ItemSlot slot : planetSlots) {
            if (!slot.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

    @Override
    public int guiHeight() {
        //Lot 8c : zone de 164 (cases des planetes et des sorties sous la rangee de reglage)
        return Math.max(GUI_HEIGHT, StandardLayout.ZONE);
    }

    /** Lot 8a : interface standard v2 (inventaire dans le cadre, onglets a droite, Infos et Structure) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return java.util.List.of(StandardLayout.framed("energy", ENERGY), StandardLayout.framed("gauge", PLASMA), new StandardLayout.Part("card", CARD),
              new StandardLayout.Part("setting", MODES), new StandardLayout.Part("setting", ROW));
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("space_simulator.pioneers.title");
    }

    /** Habillage a la couleur de ce qui est simule (ambre pour le systeme solaire, indigo pour la galaxie). */
    @Override
    public String guiTier() {
        return getMode() == SpaceSimMode.GALAXY ? "space_simulator_galaxy" : "space_simulator_solar";
    }

    /** L'interface s'ouvre depuis la coque : le joueur peut etre loin du controleur. */
    @Override
    public double guiReach() {
        return 40;
    }

    /** Lot 8a : valeurs d'interface NOMMEES (memes indices qu'avant : 4 et suivants, apres les 4 communes). */
    public static final UiValues.Key V_MODE = UiValues.Key.of("mode"), V_STATUS = UiValues.Key.of("status"),
          V_RUNNING = UiValues.Key.of("running"), V_FORMED = UiValues.Key.of("formed"), V_GALAXY = UiValues.Key.of("galaxy"),
          V_PROGRESS = UiValues.Key.of("progress"), V_CYCLE = UiValues.Key.of("cycle"), V_CYCLES = UiValues.Key.of("cycles"),
          V_UPKEEP_ENERGY = UiValues.Key.of("upkeep_energy"), V_UPKEEP_ITEMS = UiValues.Key.of("upkeep_items"),
          V_IGNITION_ENERGY = UiValues.Key.of("ignition_energy"), V_IGNITION_ITEMS = UiValues.Key.of("ignition_items"),
          V_PLANETS = UiValues.Key.of("planets"), V_USED = UiValues.Key.of("used"), V_CAN_IGNITE = UiValues.Key.of("can_ignite");
    private static final UiValues.Key[] VALUES = {V_MODE, V_STATUS, V_RUNNING, V_FORMED, V_GALAXY, V_PROGRESS, V_CYCLE, V_CYCLES, V_UPKEEP_ENERGY,
          V_UPKEEP_ITEMS, V_IGNITION_ENERGY, V_IGNITION_ITEMS, V_PLANETS, V_USED, V_CAN_IGNITE};

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        for (int i = 0; i < VALUES.length; i++) {
            int k = i;
            values.add(VALUES[i], () -> machineValue(k));
        }
    }

    @Override
    protected int machineValueCount() {
        return 15;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> getMode().ordinal();
            case 1 -> status.ordinal();
            case 2 -> running ? 1 : 0;
            case 3 -> isFormed() ? 1 : 0;
            case 4 -> galaxy;
            case 5 -> getMode() == SpaceSimMode.GALAXY ? galaxyProgress : bestPlanetProgress();
            case 6 -> getMode() == SpaceSimMode.GALAXY ? getGalaxy().cycleTicks() : 1000;
            case 7 -> cycles;
            case 8 -> upkeepEnergy();
            case 9 -> upkeepPlasma();
            case 10 -> ignitionEnergy();
            case 11 -> ignitionPlasma();
            case 12 -> countPlanets();
            case 13 -> lastEnergyUsed;
            default -> coreMatches() ? 1 : 0;
        };
    }

    /** Avancement (x1000) de la planete la plus proche de son cycle. */
    private long bestPlanetProgress() {
        long best = 0;
        for (int i = 0; i < PLANETS; i++) {
            SolarSimulationRecipe recipe = recipes[i];
            if (recipe != null && recipe.getDuration() > 0) {
                best = Math.max(best, planetProgress[i] * 1000L / recipe.getDuration());
            }
        }
        return best;
    }

    /** Le controleur ne prend rien par ses faces : tout passe par les ports de la coque. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (side != null && (cap == ForgeCapabilities.ENERGY || cap == PioneersAPI.GAS_HANDLER
                             || cap == ForgeCapabilities.ITEM_HANDLER
                             || MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(cap))) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos c = getCenter();
        double r = SpaceSimShape.RING_OUTER + 4;
        return new AABB(c.getX() - r, c.getY() - r, c.getZ() - r, c.getX() + r + 1, c.getY() + r + 1, c.getZ() + r + 1);
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("running", running);
        tag.putInt("runMode", runMode.ordinal());
        tag.putInt("selectedMode", selectedMode.ordinal());
        tag.putInt("status", status.ordinal());
        tag.putInt("galaxy", galaxy);
        tag.putInt("galaxyProgress", galaxyProgress);
        tag.putInt("cycles", cycles);
        tag.putIntArray("planetProgress", planetProgress);
        tag.putLong("ignitedAt", ignitedAt);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        running = tag.getBoolean("running");
        runMode = SpaceSimMode.byIndex(tag.getInt("runMode"));
        selectedMode = tag.contains("selectedMode") ? SpaceSimMode.byIndex(tag.getInt("selectedMode")) : SpaceSimMode.SOLAR;
        if (selectedMode == SpaceSimMode.NONE) {
            selectedMode = SpaceSimMode.SOLAR;
        }
        status = Status.byIndex(tag.getInt("status"));
        galaxy = tag.getInt("galaxy");
        galaxyProgress = tag.getInt("galaxyProgress");
        cycles = tag.getInt("cycles");
        int[] saved = tag.getIntArray("planetProgress");
        System.arraycopy(saved, 0, planetProgress, 0, Math.min(saved.length, PLANETS));
        ignitedAt = tag.getLong("ignitedAt");
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        tag.putBoolean("running", running);
        tag.putInt("runMode", runMode.ordinal());
        tag.putInt("galaxy", galaxy);
        tag.putInt("selectedMode", selectedMode.ordinal());
        tag.putLong("center", getCenter().asLong());
        tag.putLong("ignitedAt", ignitedAt);
        int mask = 0;
        for (int i = 0; i < PLANETS; i++) {
            if (hasPlanet(i)) {
                mask |= 1 << i;
            }
        }
        tag.putInt("planets", mask);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        running = tag.getBoolean("running");
        runMode = SpaceSimMode.byIndex(tag.getInt("runMode"));
        galaxy = tag.getInt("galaxy");
        selectedMode = SpaceSimMode.byIndex(tag.getInt("selectedMode"));
        if (tag.contains("center")) {
            center = BlockPos.of(tag.getLong("center"));
        }
        ignitedAt = tag.getLong("ignitedAt");
        clientPlanets = tag.getInt("planets");
    }

    private int clientPlanets;

    /** Planetes installees, vues par le client (rendu). */
    public boolean hasClientPlanet(int index) {
        return (clientPlanets & 1 << index) != 0;
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite
     * interne de la maison ({@link MultiblockPort#capabilityOf}) ; les ports propres de la maison gardent leurs gestionnaires.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }
}
