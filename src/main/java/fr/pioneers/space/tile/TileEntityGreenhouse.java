package fr.pioneers.space.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.space.base.PiBaseMachines;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.logistics.robotics.ItemRobotUnit;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.base.BaseStructure;
import fr.pioneers.space.farm.GreenhousePortTile;
import fr.pioneers.space.farm.GreenhouseStructure;
import fr.pioneers.space.farm.HydroponicSubstrateBlock;
import fr.pioneers.space.farm.ItemSpaceSeeds;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.research.ResearchEffects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controleur de la serre spatiale.
 * <p>
 * Serre construite (1.0) : voir {@link GreenhouseStructure}. Le robot agricole (objet a installer) roule entre les bacs : il plante les graines disponibles, recolte les plantes mures
 * et retourne a sa station de recharge quand sa batterie faiblit. Les plantes ne poussent que si la serre est construite
 * sur leur astre d'origine, et que la serre est alimentee (eclairage et support de vie).
 * <p>
 * 1.12.3 (lot F3) : chaque case garde les genes de sa graine ({@link Genes}, comme les cultures de la Terre). Croissance : vitesse
 * (genes moyens 5 = vitesse d'avant) ; Rendement : recolte (5 = recolte d'avant, 1,7 en moyenne) ; Fertilite : graines rendues et mutations
 * des graines filles ; Resistance : marge de temperature autour de l'astre d'origine ({@link SpacePlant#climate} : ailleurs, pousse
 * ralentie x0,45 si l'astre est assez proche en temperature, sinon rien).
 */
public class TileEntityGreenhouse extends MachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final int CELLS = GreenhouseStructure.GRID * GreenhouseStructure.GRID;
    /** 1.0 : croissance selon le tier de la serre (MKI a MKIV). */
    public static final double[] TIER_GROWTH = {1.0, 1.5, 2.0, 3.0};
    public static final int GROW_TICKS = 2_400;
    public static final int BATTERY_MAX = 1_000;
    public static final long LIFE_SUPPORT_FE = 200, CHARGE_FE = 500;
    public static final long ENERGY_STORAGE_FE = 400_000;
    /** Duree d'une action (plantation ou recolte). */
    public static final int WORK_TICKS = 24;
    /** Ticks pour parcourir une case. */
    public static final int TICKS_PER_CELL = 7;

    public enum Mode {IDLE, MOVING, WORKING, CHARGING}

    public static final int GUI_HEIGHT = StandardLayout.ZONE;
    /** Lot 8b (vraie v2) : reserve et batterie a la place commune, tortue, carte de l'etat, rangee des graines, deux rangees de recoltes. */
    public static final int ROBOT_X = 30, ROBOT_Y = StandardLayout.TOP + 4, ROWS_X = 28, SEEDS_Y = StandardLayout.TOP + 50, OUT_Y = StandardLayout.TOP + 78;
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(52, StandardLayout.TOP, StandardLayout.WIDTH - 8 - 52, 40);
    /** Lot 8b : valeurs nommees aux indices d'avant. */
    public static final UiValues.Key FORMED = UiValues.Key.of("formed"), PLANET_OK = UiValues.Key.of("planet_ok"), ROBOT = UiValues.Key.of("robot"),
          BATTERY = UiValues.Key.of("battery"), MODE = UiValues.Key.of("mode"), PLANTED = UiValues.Key.of("planted"), MATURE = UiValues.Key.of("mature"),
          HARVESTS = UiValues.Key.of("harvests"), BEDS = UiValues.Key.of("beds"), TIER = UiValues.Key.of("tier");
    private final ItemSlot robotSlot, energySlot;
    private final List<ItemSlot> seedSlots = new ArrayList<>();
    private final List<ItemSlot> outputSlots = new ArrayList<>();

    /** Plante de chaque case (-1 = vide), sa croissance et (1.12.3) ses genes ({@link Genes#packed()}, 0 = genes de base). */
    private final int[] plants = new int[CELLS];
    private final int[] growth = new int[CELLS];
    private final int[] genes = new int[CELLS];
    /** Recolte moyenne d'une plante spatiale au Rendement 5 (avant la 1.12.3 : 1 a 3 objets, 1,7 en moyenne). */
    public static final float AVERAGE_HARVEST = 1.7F;
    /** Cases qui sont des bacs dans la serre construite. */
    private boolean[] beds = new boolean[CELLS];
    private int tier, size;

    /** Lot 8a : cycle de vie commun des multiblocs maison (verification periodique ou a la demande, revalidee au chargement). */
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
        public net.minecraft.core.Direction front() {
            return facing();
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            GreenhouseStructure.Result r = GreenhouseStructure.find(level, worldPosition, facing());
            return r == null ? MultiblockCore.House.Survey.fail(null, null, null) : MultiblockCore.House.Survey.ok(r.base().members(), r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            GreenhouseStructure.Result result = (GreenhouseStructure.Result) survey.data();
            center = result.center();
            beds = result.beds();
            if (result.tier() != tier || result.size() != size || first) {
                tier = result.tier();
                size = result.size();
                BaseStructure.paint(level, result.base());
                dirtyClient = true;
            }
            for (BlockPos port : result.ports()) {
                if (level.getBlockEntity(port) instanceof GreenhousePortTile portTile) {
                    portTile.link(worldPosition);
                }
            }
            if (result.padCell() != padCell) {
                padCell = result.padCell();
                robotCell = targetCell = padCell;
                setMode(Mode.IDLE);
            }
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            center = null;
        }

        @Override
        public void houseChanged() {
            dirtyClient = true;
        }
    }, 20);
    private boolean planetOk;
    private int battery = BATTERY_MAX;
    private int mode;
    private int planted;
    private int mature;
    private int harvests;

    @Nullable
    private BlockPos center;
    private int padCell = -1;
    private int robotCell = -1, targetCell = -1;
    private long moveStart;
    private int moveDuration, workTicks;
    private boolean dirtyClient;

    //Cote client
    private int[] clientPlants = new int[0];
    private int[] clientStages = new int[0];

    public TileEntityGreenhouse(BlockPos pos, BlockState state) {
        super(PiBaseMachines.GREENHOUSE_CONTROLLER_TILE.get(), pos, state, ENERGY_STORAGE_FE, 0);
        Arrays.fill(plants, -1);
        //Ancien ordre des cases (Mekanism) : tortue, 6 graines, 12 recoltes, batterie. Le controleur refuse l'automatisation : les ports la font
        robotSlot = slot(new ItemSlot(() -> 1, stack -> stack.getItem() instanceof ItemRobotUnit unit && unit.kind == UnitKind.TURTLE,
              (stack, automation) -> !automation.external(), (stack, automation) -> !automation.external(), this::setChanged, ROBOT_X, ROBOT_Y).ghost(() -> new ItemStack(PiRobotics.UNITS.get(UnitKind.TURTLE).get(UnitTier.T1).get())));
        for (int i = 0; i < 6; i++) {
            seedSlots.add(slot(new ItemSlot(() -> 64, stack -> stack.getItem() instanceof ItemSpaceSeeds, (stack, automation) -> !automation.external(),
                  (stack, automation) -> !automation.external(), this::setChanged, ROWS_X + i * 18, SEEDS_Y)
                  .ghost(() -> new ItemStack(PiItems.SEEDS.values().iterator().next().get()))));
        }
        for (int i = 0; i < 12; i++) {
            outputSlots.add(slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL,
                  (stack, automation) -> !automation.external(), this::setChanged, ROWS_X + (i % 6) * 18, OUT_Y + (i / 6) * 18)));
        }
        energySlot = batterySlot(StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y);
    }

    /** Energie et objets seulement par les ports de serre ; pas de reglage de faces sur le controleur. */
    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

    @Override
    protected List<ItemSlot> automationSlots() {
        return List.of();
    }

    @Override
    public int guiHeight() {
        //Lot 8a : zone v2 d'au moins StandardLayout.ZONE (le corps d'avant garde sa place en haut ; dessous : erreur commune)
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
        return java.util.List.of(new StandardLayout.Part("energy", new StandardLayout.Rect(7, StandardLayout.TOP - 1, StandardLayout.GAUGE_W + 2,
              StandardLayout.ENERGY_H + 2)), new StandardLayout.Part("card", CARD));
    }

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        UiValues.Key[] keys = {FORMED, PLANET_OK, ROBOT, BATTERY, MODE, PLANTED, MATURE, HARVESTS, BEDS, TIER};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            values.add(keys[i], () -> machineValue(index));
        }
    }

    @Override
    public String guiTier() {
        return "greenhouse";
    }

    /** Recoit l'energie d'un port de serre (FE). */
    public int receiveFromPort(int amount, boolean simulate) {
        return (int) energy.insert(amount, simulate);
    }

    // ------------------------------------------------------------------
    // Logique
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        discharge(energySlot, 10_000);
        long time = level.getGameTime();
        house.tick();
        if (time % 20 == 0) {
            countPlants();
        }
        boolean powered = isFormed() && canWork() && use(LIFE_SUPPORT_FE);
        //Croissance : seulement sur le bon astre, et avec le support de vie
        for (int cell = 0; cell < CELLS; cell++) {
            if (plants[cell] >= 0 && growth[cell] < GROW_TICKS) {
                SpacePlant plant = SpacePlant.byIndex(plants[cell]);
                Genes cellGenes = Genes.unpack(genes[cell]);
                Climate.Status climate = plant.climate(level, cellGenes);
                if (powered && climate.grows()) {
                    int before = stage(cell);
                    //Agronomie spatiale : +50 % (une croissance double un tick sur deux)
                    //Biologie de l'astre maitrisee : +25 % (un tick de croissance en plus, une fois sur quatre)
                    int step = (ResearchEffects.agronomy() && (level.getGameTime() & 1) == 0 ? 2 : 1)
                               + ((level.getGameTime() & 3) == 0 && ResearchEffects.biologyGrowthBonus(
                          SolarSystem.fromDimension(level.dimension())) > 0 ? 1 : 0);
                    //Reglage de la configuration : multiplicateur, la partie fractionnaire jouee au hasard
                    double scaled = step * PiCommonConfig.get(PiCommonConfig.GREENHOUSE_GROWTH, 1.0) * TIER_GROWTH[Math.max(0, Math.min(3, tier))];
                    //1.12.3 : gene de Croissance (5 = vitesse d'avant, comme FarmCropBlock.growthChance) et climat (supportable : x0,45)
                    scaled *= cellGenes.growthFactor() / 1.05F * climate.factor;
                    step = (int) scaled + (level.random.nextDouble() < scaled - (int) scaled ? 1 : 0);
                    growth[cell] = Math.min(GROW_TICKS, growth[cell] + step);
                    if (stage(cell) != before) {
                        dirtyClient = true;
                    }
                }
            }
        }
        planetOk = anyPlantGrowsHere();
        if (isFormed() && powered && hasRobot()) {
            tickRobot(time);
        }
        markWorking(isFormed() && powered && hasRobot());
        if (dirtyClient) {
            dirtyClient = false;
            setChanged();
            syncNow();
        }
    }

    private void tickRobot(long time) {
        Mode current = getMode();
        switch (current) {
            case MOVING -> {
                if (time >= moveStart + moveDuration) {
                    robotCell = targetCell;
                    if (robotCell == padCell && battery < BATTERY_MAX && !jobPending) {
                        setMode(Mode.CHARGING);
                    } else if (jobPending) {
                        workTicks = workDuration();
                        setMode(Mode.WORKING);
                    } else {
                        setMode(Mode.IDLE);
                    }
                }
            }
            case WORKING -> {
                if (--workTicks <= 0) {
                    doJob(robotCell);
                    jobPending = false;
                    battery = Math.max(0, battery - drain(25));
                    setMode(Mode.IDLE);
                }
            }
            case CHARGING -> {
                if (use(CHARGE_FE)) {
                    battery = Math.min(BATTERY_MAX, battery + 8);
                    if (battery >= BATTERY_MAX) {
                        setMode(Mode.IDLE);
                    }
                }
            }
            case IDLE -> {
                if (battery < BATTERY_MAX / 5) {
                    if (robotCell == padCell) {
                        setMode(Mode.CHARGING);
                    } else {
                        moveTo(padCell, false, time);
                    }
                    return;
                }
                int job = findJob();
                if (job >= 0) {
                    moveTo(job, true, time);
                } else if (robotCell != padCell) {
                    moveTo(padCell, false, time);
                } else if (battery < BATTERY_MAX) {
                    setMode(Mode.CHARGING);
                }
            }
        }
    }

    private boolean jobPending;

    private void moveTo(int cell, boolean job, long time) {
        jobPending = job;
        if (cell == robotCell) {
            if (job) {
                workTicks = workDuration();
                setMode(Mode.WORKING);
            } else {
                setMode(Mode.IDLE);
            }
            return;
        }
        int distance = Math.max(Math.abs(GreenhouseStructure.cellX(cell) - GreenhouseStructure.cellX(robotCell)),
              Math.abs(GreenhouseStructure.cellZ(cell) - GreenhouseStructure.cellZ(robotCell)));
        targetCell = cell;
        moveStart = time;
        int perCell = Math.max(2, (int) Math.round(TICKS_PER_CELL / getRobotTier().speed));
        moveDuration = Math.max(perCell, distance * perCell);
        battery = Math.max(0, battery - drain(distance * 3));
        setMode(Mode.MOVING);
    }

    /** Case la plus proche a traiter : plante mure (recolte) ou bac vide (plantation, s'il reste des graines). */
    private int findJob() {
        boolean canPlant = hasSeeds();
        boolean canHarvest = hasOutputRoom();
        int best = -1, bestDistance = Integer.MAX_VALUE;
        for (int cell = 0; cell < CELLS; cell++) {
            if (cell == padCell || !beds[cell]) {
                continue;
            }
            boolean todo = plants[cell] < 0 ? canPlant : canHarvest && growth[cell] >= GROW_TICKS;
            if (todo) {
                int distance = Math.abs(GreenhouseStructure.cellX(cell) - GreenhouseStructure.cellX(robotCell))
                               + Math.abs(GreenhouseStructure.cellZ(cell) - GreenhouseStructure.cellZ(robotCell));
                if (distance < bestDistance) {
                    best = cell;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private void doJob(int cell) {
        if (plants[cell] >= 0 && growth[cell] >= GROW_TICKS) {
            SpacePlant plant = SpacePlant.byIndex(plants[cell]);
            //1.12.3 : recolte selon le Rendement (5 = 1,7 en moyenne comme avant), graines filles selon la Fertilite (heritage et mutations)
            Genes parent = Genes.unpack(genes[cell]);
            float amount = AVERAGE_HARVEST * parent.yieldFactor() / 1.2F;
            int produce = Math.max(1, (int) amount + (level.random.nextFloat() < amount - (int) amount ? 1 : 0));
            output(new ItemStack(PiItems.PRODUCE.get(plant).get(), produce));
            ItemSpaceSeeds seedItem = PiItems.SEEDS.get(plant).get();
            int seeds = parent.seedCount(level.random);
            for (int i = 0; i < seeds; i++) {
                output(seedItem.stack(parent.child(level.random), 1));
            }
            plants[cell] = -1;
            growth[cell] = 0;
            genes[cell] = 0;
            harvests++;
            //Apres la recolte, le substrat se tasse parfois : il faudra le becher de nouveau
            if (level.random.nextFloat() < 0.35F) {
                setTilled(cell, false);
            }
            particles(cell, true);
        } else if (plants[cell] < 0 && !isTilled(cell)) {
            //Bechage du substrat (premiere visite)
            setTilled(cell, true);
            if (center != null && level instanceof ServerLevel serverLevel) {
                BlockPos soil = center.offset(GreenhouseStructure.cellX(cell), 0, GreenhouseStructure.cellZ(cell));
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(soil)),
                      soil.getX() + 0.5, soil.getY() + 1.05, soil.getZ() + 0.5, 14, 0.3, 0.05, 0.3, 0.05);
                serverLevel.playSound(null, soil, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
        } else if (plants[cell] < 0) {
            //Plantation : les graines sont prises a tour de role dans les 6 cases
            for (int i = 0; i < seedSlots.size(); i++) {
                ItemSlot slot = seedSlots.get((cell + i) % seedSlots.size());
                if (!slot.isEmpty() && slot.getStack().getItem() instanceof ItemSpaceSeeds seeds) {
                    ItemStack taken = slot.extract(1, Action.EXECUTE, Automation.INTERNAL);
                    plants[cell] = seeds.getPlant().ordinal();
                    growth[cell] = 0;
                    genes[cell] = Genes.has(taken) ? Genes.of(taken).packed() : 0;
                    particles(cell, false);
                    break;
                }
            }
        }
        dirtyClient = true;
        countPlants();
    }

    private boolean isTilled(int cell) {
        return center != null && HydroponicSubstrateBlock.isTilled(level,
              center.offset(GreenhouseStructure.cellX(cell), 0, GreenhouseStructure.cellZ(cell)));
    }

    private void setTilled(int cell, boolean tilled) {
        if (center == null) {
            return;
        }
        BlockPos soil = center.offset(GreenhouseStructure.cellX(cell), 0, GreenhouseStructure.cellZ(cell));
        BlockState state = level.getBlockState(soil);
        if (state.getBlock() instanceof HydroponicSubstrateBlock) {
            level.setBlock(soil, state.setValue(HydroponicSubstrateBlock.TILLED, tilled), 3);
        }
    }

    private void particles(int cell, boolean harvest) {
        if (center != null && level instanceof ServerLevel serverLevel) {
            double x = center.getX() + 0.5 + GreenhouseStructure.cellX(cell), z = center.getZ() + 0.5 + GreenhouseStructure.cellZ(cell);
            serverLevel.sendParticles(harvest ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.COMPOSTER, x, center.getY() + 1.4, z, 6, 0.3, 0.2, 0.3, 0);
        }
    }

    private void output(ItemStack stack) {
        for (ItemSlot slot : outputSlots) {
            stack = slot.insert(stack, Action.EXECUTE, Automation.INTERNAL);
            if (stack.isEmpty()) {
                return;
            }
        }
    }

    /** 1.12.3 : la recolte et jusqu'a 3 graines aux genes differents (piles distinctes) doivent trouver place. */
    private boolean hasOutputRoom() {
        int free = 0;
        for (ItemSlot slot : outputSlots) {
            if (slot.isEmpty()) {
                free++;
            }
        }
        return free >= 4;
    }

    private boolean hasSeeds() {
        for (ItemSlot slot : seedSlots) {
            if (!slot.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean anyPlantGrowsHere() {
        for (int cell = 0; cell < CELLS; cell++) {
            if (plants[cell] >= 0 && SpacePlant.byIndex(plants[cell]).climate(level, Genes.unpack(genes[cell])).grows()) {
                return true;
            }
        }
        for (ItemSlot slot : seedSlots) {
            if (slot.getStack().getItem() instanceof ItemSpaceSeeds seeds && seeds.getPlant().climate(level, Genes.of(slot.getStack())).grows()) {
                return true;
            }
        }
        return false;
    }

    private void countPlants() {
        planted = 0;
        mature = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            if (plants[cell] >= 0) {
                planted++;
                if (growth[cell] >= GROW_TICKS) {
                    mature++;
                }
            }
        }
    }

    private int stage(int cell) {
        return Math.min(4, growth[cell] * 4 / GROW_TICKS);
    }

    private void setMode(Mode newMode) {
        if (mode != newMode.ordinal()) {
            mode = newMode.ordinal();
            dirtyClient = true;
        }
    }

    // ------------------------------------------------------------------
    // Acces
    // ------------------------------------------------------------------

    public List<ItemSlot> getSeedSlots() {
        return seedSlots;
    }

    public List<ItemSlot> getOutputSlots() {
        return outputSlots;
    }

    public boolean hasRobot() {
        return !robotSlot.isEmpty();
    }

    private int clientRobotTier;

    /** Tier de la tortue installee : elle plante et recolte plus vite, et se deplace un peu plus vite. */
    public UnitTier getRobotTier() {
        if (level != null && level.isClientSide) {
            return UnitTier.byIndex(clientRobotTier);
        }
        return robotSlot.getStack().getItem() instanceof ItemRobotUnit unit ? unit.tier : UnitTier.T1;
    }

    private int workDuration() {
        return Math.max(4, (int) Math.round(WORK_TICKS / getRobotTier().work));
    }

    public Mode getMode() {
        return Mode.values()[Math.max(0, Math.min(mode, Mode.values().length - 1))];
    }

    public boolean isFormed() {
        return house.formed();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        house.removed(true);
    }

    /** Tier de la serre construite (0 = MKI). */
    public int getTier() {
        return tier;
    }

    /** Nombre de bacs de la serre. */
    public int bedCount() {
        int n = 0;
        for (boolean bed : beds) {
            n += bed ? 1 : 0;
        }
        return n;
    }

    public int getBattery() {
        return battery;
    }

    @Override
    protected int machineValueCount() {
        return 10;
    }

    /** Interface : 4 forme, 5 astre favorable, 6 tortue, 7 batterie, 8 mode, 9 plantes, 10 mures, 11 recoltes, 12 bacs, 13 tier. */
    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> isFormed() ? 1 : 0;
            case 1 -> planetOk ? 1 : 0;
            case 2 -> hasRobot() ? 1 : 0;
            case 3 -> battery;
            case 4 -> mode;
            case 5 -> planted;
            case 6 -> mature;
            case 7 -> harvests;
            case 8 -> bedCount();
            default -> tier;
        };
    }

    // ------------------------------------------------------------------
    // Rendu (client)
    // ------------------------------------------------------------------

    private BlockPos clientCenter;
    private boolean clientRobot;
    private int clientFrom, clientTo, clientDuration, clientMode;
    private long clientMoveStart;

    @Nullable
    public BlockPos getClientCenter() {
        return clientCenter;
    }

    public boolean hasClientRobot() {
        return clientRobot;
    }

    public int[] getClientPlants() {
        return clientPlants;
    }

    public int[] getClientStages() {
        return clientStages;
    }

    /** Position du robot (cases, flottant) a un instant donne. */
    public float[] getRobotPosition(float time) {
        float t = clientDuration <= 0 ? 1 : Math.min(1, Math.max(0, (time - clientMoveStart) / clientDuration));
        if (getClientMode() != Mode.MOVING) {
            t = 1;
        }
        float fx = GreenhouseStructure.cellX(clientFrom), fz = GreenhouseStructure.cellZ(clientFrom);
        float tx = GreenhouseStructure.cellX(clientTo), tz = GreenhouseStructure.cellZ(clientTo);
        //Acceleration / freinage doux
        float e = t * t * (3 - 2 * t);
        return new float[]{fx + (tx - fx) * e, fz + (tz - fz) * e, tx - fx, tz - fz};
    }

    public Mode getClientMode() {
        return Mode.values()[Math.max(0, Math.min(clientMode, Mode.values().length - 1))];
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        if (isFormed() && center != null) {
            tag.putLong("center", center.asLong());
        }
        tag.putBoolean("robot", hasRobot() && isFormed());
        tag.putInt("robotTier", getRobotTier().ordinal());
        int[] stages = new int[CELLS];
        for (int cell = 0; cell < CELLS; cell++) {
            stages[cell] = plants[cell] < 0 ? 0 : stage(cell);
        }
        tag.putIntArray("plants", plants.clone());
        tag.putIntArray("stages", stages);
        boolean moving = getMode() == Mode.MOVING;
        tag.putInt("from", moving ? robotCell : (robotCell < 0 ? Math.max(0, padCell) : robotCell));
        tag.putInt("to", moving ? targetCell : (robotCell < 0 ? Math.max(0, padCell) : robotCell));
        tag.putLong("moveStart", moveStart);
        tag.putInt("duration", moveDuration);
        tag.putInt("mode", mode);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        clientCenter = tag.contains("center") ? BlockPos.of(tag.getLong("center")) : null;
        clientRobot = tag.getBoolean("robot");
        clientRobotTier = tag.getInt("robotTier");
        clientPlants = tag.getIntArray("plants");
        clientStages = tag.getIntArray("stages");
        clientFrom = tag.getInt("from");
        clientTo = tag.getInt("to");
        clientMoveStart = tag.getLong("moveStart");
        clientDuration = tag.getInt("duration");
        clientMode = tag.getInt("mode");
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-16, -4, -16), worldPosition.offset(17, 10, 17));
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);
        int[] savedPlants = nbt.getIntArray("plants");
        int[] savedGrowth = nbt.getIntArray("growth");
        int[] savedGenes = nbt.getIntArray(Genes.TAG);
        //1.0 : grille agrandie (serres construites) ; les plantes des anciennes serres (5 x 5) sont perdues
        boolean sameGrid = savedPlants.length == CELLS;
        for (int cell = 0; cell < CELLS; cell++) {
            plants[cell] = sameGrid && cell < savedPlants.length ? savedPlants[cell] : -1;
            growth[cell] = sameGrid && cell < savedGrowth.length ? savedGrowth[cell] : 0;
            //1.12.3 : serres d'avant sans genes = genes de base
            genes[cell] = sameGrid && cell < savedGenes.length ? savedGenes[cell] : 0;
        }
        battery = nbt.contains("battery") ? nbt.getInt("battery") : BATTERY_MAX;
        harvests = nbt.getInt("harvests");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbtTags) {
        super.saveAdditional(nbtTags);
        nbtTags.putIntArray("plants", plants);
        nbtTags.putIntArray("growth", growth);
        nbtTags.putIntArray(Genes.TAG, genes);
        nbtTags.putInt("battery", battery);
        nbtTags.putInt("harvests", harvests);
    }

    /** Usure de la batterie du robot (divisee par deux avec l'agronomie spatiale). */
    private static int drain(int amount) {
        return ResearchEffects.agronomy() ? (amount + 1) / 2 : amount;
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : un port commun pose dans la structure formee prete la capacite
     * interne de la maison ({@link MultiblockPort#capabilityOf}) ; les ports propres de la maison gardent leurs gestionnaires.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.tech.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() ? MultiblockPort.capabilityOf(this, type) : null;
    }
}
