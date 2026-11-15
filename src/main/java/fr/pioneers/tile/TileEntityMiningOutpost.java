package fr.pioneers.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.registries.PiBaseMachines;
import fr.pioneers.registries.PiItems;
import fr.pioneers.registries.PiRobotics;
import fr.pioneers.robotics.ItemRobotUnit;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.robotics.UnitTier;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.base.BaseStructure;
import fr.pioneers.space.farm.SpacePlant.Planet;
import fr.pioneers.space.mining.Deposit;
import fr.pioneers.space.mining.DrillHead;
import fr.pioneers.space.mining.ItemDrillHead;
import fr.pioneers.space.mining.ItemRoverModule;
import fr.pioneers.space.mining.MiningOutpostStructure;
import fr.pioneers.space.mining.MiningPortTile;
import fr.pioneers.space.mining.MiningVein;
import fr.pioneers.space.mining.RoverModule;
import fr.pioneers.space.mining.RoverRoad;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controleur de la base d'extraction planetaire.
 * <p>
 * Le radar du toit repere des gisements autour de la base. Le rover sort du hangar, roule jusqu'au gisement choisi,
 * fore unite par unite jusqu'a remplir sa benne (ou vider le filon, ou manquer de batterie), revient au dock, decharge
 * dans la soute et se recharge. Quand tous les gisements sont epuises, le radar relance une prospection.
 * La tete de forage installee fixe les filons accessibles (durete) ; les modules et les recherches ameliorent le rover.
 */
public class TileEntityMiningOutpost extends MachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final long ENERGY_STORAGE_FE = 800_000;
    public static final long LIFE_SUPPORT_FE = 80, SURVEY_FE = 600, CHARGE_FE = 300;
    public static final int BATTERY = 2_400, CARGO = 64;
    public static final int SURVEY_TICKS = 160, UNLOAD_TICKS = 30, DRILL_TICKS = 50;
    public static final float DRIVE_COST = 1.0F, DRILL_COST = 6.0F;
    public static final int BASE_RADIUS = 24, OUTPUT_SLOTS = 18;
    public static final int ACTION_FILTER_NEXT = CUSTOM, ACTION_FILTER_PREV = CUSTOM + 1, ACTION_RESCAN = CUSTOM + 2, ACTION_RANGE_DOWN = CUSTOM + 3,
          ACTION_RANGE_UP = CUSTOM + 4;
    public static final int GUI_WIDTH = 212, GUI_HEIGHT = StandardLayout.ZONE;
    /**
     * Lot 8c : interface en VRAIE v2 : energie et batterie a la place commune, rover et tete de forage puis les 3 modules a droite de
     * l'energie, carte de prospection, ecran (nom du rover, etat), rangee des reglages (nouvelle prospection, filtre de minerai, portee),
     * 18 sorties dessous (ORDRE des cases inchange : rover, tete, 3 modules, batterie, 18 sorties). Geometrie partagee avec la vue
     * {@code BaseViews} et le banc.
     */
    public static final int ROVER_X = 27, ROVER_Y = StandardLayout.TOP + 1, MODULES_X = 45, OUTPUT_X = (GUI_WIDTH - 9 * 18) / 2 + 1, OUTPUT_Y = 112;
    public static final StandardLayout.Rect ENERGY = new StandardLayout.Rect(8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H),
          MAP = new StandardLayout.Rect(66, StandardLayout.TOP, 66, 66),
          SCREEN = new StandardLayout.Rect(136, StandardLayout.TOP, GUI_WIDTH - 8 - 136, 66),
          RESCAN = new StandardLayout.Rect(8, StandardLayout.TOP + 70, 54, 12),
          FILTER = new StandardLayout.Rect(MAP.x(), RESCAN.y(), MAP.w(), 12),
          RANGE = new StandardLayout.Rect(SCREEN.x(), RESCAN.y(), SCREEN.w(), 12);
    public static final int RANGE_STEP = 4;

    public enum Mode {IDLE, SURVEYING, OUTBOUND, DRILLING, RETURNING, UNLOADING, CHARGING}

    public enum Status {WORKING, NOT_FORMED, WRONG_BODY, NO_ROVER, NO_DRILL, DRILL_TOO_WEAK, FILTERED, OUTPUT_FULL, NO_ENERGY, NOTHING_FOUND, LOW_BATTERY, PATH_BLOCKED}

    private final ItemSlot roverSlot, drillSlot, energySlot;
    private final List<ItemSlot> moduleSlots = new ArrayList<>();
    private final List<ItemSlot> outputSlots = new ArrayList<>();

    private final List<Deposit> deposits = new ArrayList<>();
    private final List<ItemStack> cargo = new ArrayList<>();

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
            MiningOutpostStructure.Result r = MiningOutpostStructure.find(level, worldPosition, facing());
            return r == null ? MultiblockCore.House.Survey.fail(null, null, null) : MultiblockCore.House.Survey.ok(r.base().members(), r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            MiningOutpostStructure.Result result = (MiningOutpostStructure.Result) survey.data();
            if (!result.center().equals(center) || result.door() != door || result.docks() != docks || result.tier() != tier || first) {
                center = result.center();
                door = result.door();
                docks = Math.max(1, result.docks());
                tier = result.tier();
                half = result.half();
                BaseStructure.paint(level, result.base());
                dirtyClient = true;
            }
            for (BlockPos port : result.ports()) {
                if (level.getBlockEntity(port) instanceof MiningPortTile portTile) {
                    portTile.link(worldPosition);
                }
            }
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            //Rien a liberer : les ports se delient seuls (lien date)
        }

        @Override
        public void houseChanged() {
            dirtyClient = true;
        }
    }, 20);
    private int mode;
    private int status;
    private int battery = BATTERY;
    private int cargoCount;
    private int filter;
    /** Portee reglee par le joueur (0 = maximum). */
    private int range;
    /** Portee maximale (config + recherches + sondeur), calculee cote serveur et synchronisee. */
    private int maxRange = BASE_RADIUS;
    private long unitsMined;
    private int trips;
    private int targetIndex = -1;

    @Nullable
    private BlockPos center;
    @Nullable
    private Direction door;
    /** 1.0 : garage construit : nombre de docks (rovers en convoi), tier, demi-longueur du hangar. */
    private int docks = 1, tier;
    private double half = 2.5;
    private long phaseStart;
    private int phaseDuration;
    private float drillProgress;
    private boolean rescanRequested;
    private long nextSurvey;
    /** Route du trajet en cours : profil d'altitude et blocs restant a creuser (avec la distance ou les creuser). */
    private int[] road = new int[0];
    private final List<BlockPos> pendingDigs = new ArrayList<>();
    private final List<Double> pendingDigAt = new ArrayList<>();
    private boolean dirtyClient;

    public TileEntityMiningOutpost(BlockPos pos, BlockState state) {
        super(PiBaseMachines.MINING_OUTPOST_CONTROLLER_TILE.get(), pos, state, ENERGY_STORAGE_FE, 0);
        //Ancien ordre des cases (Mekanism) : rover, tete de forage, 3 modules, batterie, 18 sorties ; l'automatisation passe par les ports
        roverSlot = slot(new ItemSlot(() -> Math.max(1, docks), stack -> stack.getItem() instanceof ItemRobotUnit unit && unit.kind == UnitKind.ROVER,
              (stack, automation) -> !automation.external(), (stack, automation) -> !automation.external(), this::setChanged, ROVER_X, ROVER_Y).ghost(() -> new ItemStack(PiRobotics.UNITS.get(UnitKind.ROVER).get(UnitTier.T1).get())));
        drillSlot = slot(new ItemSlot(() -> 64, stack -> stack.getItem() instanceof ItemDrillHead, (stack, automation) -> !automation.external(),
              (stack, automation) -> !automation.external(), this::setChanged, ROVER_X, ROVER_Y + 20)
              .ghost(() -> new ItemStack(PiItems.DRILL_HEADS.get(DrillHead.STEEL).get())));
        for (int i = 0; i < 3; i++) {
            moduleSlots.add(slot(new ItemSlot(() -> 64, stack -> stack.getItem() instanceof ItemRoverModule, (stack, automation) -> !automation.external(),
                  (stack, automation) -> !automation.external(), this::setChanged, MODULES_X, ROVER_Y + i * 20)
                  .ghost(() -> new ItemStack(PiItems.ROVER_MODULES.values().iterator().next().get()))));
        }
        energySlot = batterySlot(StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y);
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            outputSlots.add(slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL,
                  (stack, automation) -> !automation.external(), this::setChanged, OUTPUT_X + (i % 9) * 18, OUTPUT_Y + (i / 9) * 18)));
        }
    }

    /** Energie et objets seulement par les ports de la base ; pas de reglage de faces sur le controleur. */
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
    public int guiWidth() {
        return GUI_WIDTH;
    }

    @Override
    public int guiHeight() {
        return GUI_HEIGHT;
    }

    /** Lot 8a : interface standard v2 (inventaire dans le cadre, onglets a droite, Infos et Structure) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public java.util.List<StandardLayout.Part> guiParts() {
        return java.util.List.of(StandardLayout.framed("energy", ENERGY), new StandardLayout.Part("card", MAP), new StandardLayout.Part("screen", SCREEN),
              new StandardLayout.Part("setting", RESCAN), new StandardLayout.Part("setting", FILTER), new StandardLayout.Part("setting", RANGE));
    }

    @Override
    public String guiTier() {
        return "mining_outpost";
    }

    /** Recoit l'energie d'un port de la base (FE). */
    public int receiveFromPort(int amount, boolean simulate) {
        return (int) energy.insert(amount, simulate);
    }

    // ------------------------------------------------------------------
    // Logique serveur
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        discharge(energySlot, 10_000);
        long time = level.getGameTime();
        house.tick();
        if (time % 20 == 0) {
            getMaxRange();
        }
        Planet body = bodyOf(level);
        if (!isFormed()) {
            setStatus(Status.NOT_FORMED);
            recallRover();
        } else if (body == null) {
            setStatus(Status.WRONG_BODY);
            recallRover();
        } else if (!hasRover()) {
            setStatus(Status.NO_ROVER);
            recallRover();
        } else if (!canWork() || !consume(LIFE_SUPPORT_FE)) {
            setStatus(Status.NO_ENERGY);
        } else {
            tickRover(time, body);
        }
        markWorking(isFormed() && body != null && hasRover() && getMode() != Mode.IDLE);
        if (dirtyClient) {
            dirtyClient = false;
            setChanged();
            syncNow();
        }
    }

    /** Sans structure ou sans rover : le rover est considere de retour au dock (sa benne est conservee). */
    private void recallRover() {
        Mode current = getMode();
        if (current == Mode.OUTBOUND || current == Mode.DRILLING || current == Mode.RETURNING || current == Mode.SURVEYING) {
            setMode(Mode.IDLE);
        }
    }

    private boolean consume(long fe) {
        return use(fe);
    }

    private void tickRover(long time, Planet body) {
        switch (getMode()) {
            case IDLE -> tickIdle(time, body);
            case SURVEYING -> {
                if (!consume(SURVEY_FE)) {
                    setStatus(Status.NO_ENERGY);
                    phaseStart++;
                    return;
                }
                setStatus(Status.WORKING);
                if (time >= phaseStart + phaseDuration) {
                    survey(body);
                    setMode(Mode.IDLE);
                }
            }
            case OUTBOUND -> {
                setStatus(Status.WORKING);
                digAlong(time);
                if (time >= phaseStart + phaseDuration) {
                    drillProgress = 0;
                    startPhase(Mode.DRILLING, time, 0);
                }
            }
            case DRILLING -> tickDrilling(time);
            case RETURNING -> {
                setStatus(Status.WORKING);
                if (time >= phaseStart + phaseDuration) {
                    trips++;
                    startPhase(Mode.UNLOADING, time, UNLOAD_TICKS);
                }
            }
            case UNLOADING -> {
                if (time >= phaseStart + phaseDuration) {
                    unload();
                    if (cargo.isEmpty()) {
                        setStatus(Status.WORKING);
                        setMode(Mode.IDLE);
                    } else {
                        setStatus(Status.OUTPUT_FULL);
                        phaseStart = time;
                        phaseDuration = 20;
                    }
                }
            }
            case CHARGING -> {
                if (consume(CHARGE_FE * crew())) {
                    setStatus(Status.WORKING);
                    battery = Math.min(getBatteryMax(), battery + 15 * crew());
                    if (battery >= getBatteryMax()) {
                        setMode(Mode.IDLE);
                    }
                } else {
                    setStatus(Status.NO_ENERGY);
                }
            }
        }
    }

    private void tickIdle(long time, Planet body) {
        //Au dock : d'abord vider la benne
        if (!cargo.isEmpty()) {
            startPhase(Mode.UNLOADING, time, UNLOAD_TICKS);
            return;
        }
        if (rescanRequested) {
            rescanRequested = false;
            nextSurvey = 0;
            deposits.clear();
            targetIndex = -1;
            dirtyClient = true;
        }
        deposits.removeIf(Deposit::isDepleted);
        if (deposits.isEmpty()) {
            if (time < nextSurvey) {
                setStatus(Status.NOTHING_FOUND);
                return;
            }
            startPhase(Mode.SURVEYING, time, SURVEY_TICKS);
            return;
        }
        DrillHead head = getDrillHead();
        if (head == null) {
            setStatus(Status.NO_DRILL);
            targetIndex = -1;
            return;
        }
        int target = chooseTarget(head);
        if (target < 0) {
            boolean anyMineable = false, anyBlocked = false;
            for (Deposit deposit : deposits) {
                anyMineable |= deposit.vein.hardness <= head.tier && !deposit.blocked;
                anyBlocked |= deposit.blocked;
            }
            setStatus(anyMineable ? Status.FILTERED : anyBlocked ? Status.PATH_BLOCKED : Status.DRILL_TOO_WEAK);
            if (targetIndex != -1) {
                targetIndex = -1;
                dirtyClient = true;
            }
            if (battery < getBatteryMax()) {
                setMode(Mode.CHARGING);
            }
            return;
        }
        if (!hasFreeOutput()) {
            setStatus(Status.OUTPUT_FULL);
            return;
        }
        Deposit deposit = deposits.get(target);
        double length = MiningOutpostStructure.length(MiningOutpostStructure.path(door, center, deposit.pos, half));
        float driveCost = (float) (length * DRIVE_COST * crew() * ResearchEffects.roverDrainMultiplier());
        //Assez de batterie pour l'aller-retour et une bonne serie de forages
        if (battery < driveCost * 2 + DRILL_COST * crew() * 12) {
            setStatus(Status.LOW_BATTERY);
            setMode(Mode.CHARGING);
            return;
        }
        boolean digging = PiCommonConfig.ROVER_DIGGING.get();
        var plan = digging ? RoverRoad.plan(level, center, MiningOutpostStructure.path(door, center, deposit.pos, half), door, half) : null;
        if (plan != null && plan.blocked()) {
            //Chemin barre par des blocs non naturels : gisement ignore
            deposit.blocked = true;
            dirtyClient = true;
            return;
        }
        //Sans creusage (option de configuration), le rover suit le relief sans route calculee
        road = plan == null ? new int[0] : plan.heights();
        pendingDigs.clear();
        pendingDigAt.clear();
        if (plan != null) {
            pendingDigs.addAll(plan.digs());
            pendingDigAt.addAll(plan.digAt());
        }
        targetIndex = target;
        battery -= Mth.ceil(driveCost);
        setStatus(Status.WORKING);
        startPhase(Mode.OUTBOUND, time, Math.max(10, Mth.ceil(length / getDriveSpeed())));
    }

    /** Creuse les blocs de la route que le rover atteint (un bloc devant lui). */
    private void digAlong(long time) {
        if (pendingDigs.isEmpty() || targetIndex < 0 || targetIndex >= deposits.size()) {
            return;
        }
        double length = MiningOutpostStructure.length(MiningOutpostStructure.path(door, center, deposits.get(targetIndex).pos, half));
        double reached = length * Mth.clamp((time - phaseStart) / (double) Math.max(1, phaseDuration), 0, 1) + 1.0;
        while (!pendingDigs.isEmpty() && pendingDigAt.get(0) <= reached) {
            BlockPos pos = pendingDigs.remove(0);
            pendingDigAt.remove(0);
            if (RoverRoad.diggable(level, pos)) {
                level.destroyBlock(pos, false);
            }
        }
    }

    private void tickDrilling(long time) {
        Deposit deposit = targetIndex >= 0 && targetIndex < deposits.size() ? deposits.get(targetIndex) : null;
        DrillHead head = getDrillHead();
        double length = deposit == null ? 0 : MiningOutpostStructure.length(MiningOutpostStructure.path(door, center, deposit.pos, half));
        float returnCost = (float) (length * DRIVE_COST * crew() * ResearchEffects.roverDrainMultiplier());
        boolean stop = deposit == null || deposit.isDepleted() || head == null || head.tier < deposit.vein.hardness
                       || cargoCount >= getCargoMax() || battery < returnCost + DRILL_COST * crew();
        if (stop) {
            if (head == null) {
                setStatus(Status.NO_DRILL);
            }
            battery = Math.max(0, battery - Mth.ceil(returnCost));
            startPhase(Mode.RETURNING, time, Math.max(10, Mth.ceil(length / getDriveSpeed())));
            return;
        }
        setStatus(Status.WORKING);
        drillProgress += getDrillRate(head);
        if (drillProgress >= DRILL_TICKS) {
            drillProgress -= DRILL_TICKS;
            mineUnit(deposit);
        }
    }

    private void mineUnit(Deposit deposit) {
        MiningVein vein = deposit.vein;
        var random = level.random;
        int count = vein.minCount + random.nextInt(vein.maxCount - vein.minCount + 1);
        if (hasModule(RoverModule.REFINERY) && random.nextFloat() < 0.3F) {
            count++;
        }
        if (random.nextFloat() < ResearchEffects.extraPrimaryChance()) {
            count++;
        }
        addCargo(new ItemStack(vein.getPrimary(), count));
        if (random.nextFloat() < vein.byproductChance * ResearchEffects.byproductMultiplier()) {
            addCargo(new ItemStack(vein.getByproduct()));
        }
        deposit.remaining--;
        unitsMined++;
        battery = Math.max(0, battery - Mth.ceil(DRILL_COST * crew() * ResearchEffects.roverDrainMultiplier()));
        wearDrill();
        //Effets au gisement
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = deposit.pos;
            BlockState ground = level.getBlockState(pos);
            if (!ground.isAir()) {
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                      10, 0.3, 0.05, 0.3, 0.08);
            }
            serverLevel.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 0.7F + random.nextFloat() * 0.3F);
        }
        dirtyClient = true;
    }

    private void wearDrill() {
        if (ResearchEffects.drillWearHalved() && level.random.nextBoolean()) {
            return;
        }
        ItemStack stack = drillSlot.getStack().copy();
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return;
        }
        stack.setDamageValue(stack.getDamageValue() + 1);
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            drillSlot.setStack(ItemStack.EMPTY);
            level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8F, 1);
        } else {
            drillSlot.setStack(stack);
        }
    }

    /** Ajoute a la benne, par piles normales (jamais plus que la taille de pile maximale). */
    private void addCargo(ItemStack stack) {
        ItemStack rest = stack.copy();
        cargoCount += rest.getCount();
        for (ItemStack held : cargo) {
            if (rest.isEmpty()) {
                return;
            }
            if (ItemStack.isSameItemSameTags(held, rest) && held.getCount() < held.getMaxStackSize()) {
                int moved = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                rest.shrink(moved);
            }
        }
        while (!rest.isEmpty()) {
            cargo.add(rest.split(rest.getMaxStackSize()));
        }
    }

    /** Dechargement de la benne dans la soute (ce qui ne rentre pas reste dans la benne). */
    private void unload() {
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack stack : cargo) {
            ItemStack left = stack;
            for (ItemSlot slot : outputSlots) {
                left = slot.insert(left, Action.EXECUTE, Automation.INTERNAL);
                if (left.isEmpty()) {
                    break;
                }
            }
            if (!left.isEmpty()) {
                rest.add(left);
            }
        }
        cargo.clear();
        cargo.addAll(rest);
        cargoCount = 0;
        for (ItemStack stack : cargo) {
            cargoCount += stack.getCount();
        }
        dirtyClient = true;
    }

    private boolean hasFreeOutput() {
        for (ItemSlot slot : outputSlots) {
            if (slot.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean inRange(Deposit deposit) {
        double dx = deposit.pos.getX() - center.getX(), dz = deposit.pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= (getSurveyRadius() + 0.5) * (getSurveyRadius() + 0.5);
    }

    /** Gisement le plus proche que la tete peut forer et que le filtre accepte (dans la portee reglee). */
    private int chooseTarget(DrillHead head) {
        MiningVein wanted = getFilterVein();
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < deposits.size(); i++) {
            Deposit deposit = deposits.get(i);
            if (deposit.isDepleted() || deposit.blocked || deposit.vein.hardness > head.tier || wanted != null && deposit.vein != wanted
                || !inRange(deposit)) {
                continue;
            }
            double distance = deposit.pos.distSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Prospection : de nouveaux gisements autour de la base, selon l'astre et le biome de chaque point. */
    private void survey(Planet body) {
        var random = level.random;
        List<MiningVein> veins = veinsOf(body);
        int wanted = 5 + ResearchEffects.extraDeposits() + (hasModule(RoverModule.SCANNER) ? 2 : 0);
        int radius = getSurveyRadius();
        float richness = (ResearchEffects.has(ResearchNode.PLANETARY_PROSPECTING) ? 1.25F : 1)
                         * (hasModule(RoverModule.SCANNER) ? 1.2F : 1);
        int found = 0;
        for (int attempt = 0; attempt < 80 && deposits.size() < 9 && found < wanted; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 7 + random.nextDouble() * (radius - 7);
            int x = center.getX() + Mth.floor(Math.cos(angle) * distance), z = center.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos pos = new BlockPos(x, y, z);
            boolean tooClose = false;
            for (Deposit other : deposits) {
                tooClose |= other.pos.distSqr(pos) < 25;
            }
            if (tooClose || level.getBlockState(pos).isAir()) {
                continue;
            }
            String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse(null);
            int total = 0;
            for (MiningVein vein : veins) {
                total += vein.weightIn(biome);
            }
            if (total <= 0) {
                continue;
            }
            int roll = random.nextInt(total);
            MiningVein chosen = veins.get(0);
            for (MiningVein vein : veins) {
                roll -= vein.weightIn(biome);
                if (roll < 0) {
                    chosen = vein;
                    break;
                }
            }
            int units = Math.max(12, Math.round((40 + random.nextInt(90)) * chosen.size * richness));
            deposits.add(new Deposit(pos, chosen, units, units));
            found++;
        }
        setStatus(found == 0 && deposits.isEmpty() ? Status.NOTHING_FOUND : Status.WORKING);
        if (deposits.isEmpty()) {
            //Rien autour : nouvel essai dans 30 secondes
            nextSurvey = level.getGameTime() + 600;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, center.above(MiningOutpostStructure.HEIGHT + 3).relative(door == null ? Direction.NORTH : door.getCounterClockWise(), (int) (half - 2.5)), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        dirtyClient = true;
    }

    private void startPhase(Mode newMode, long time, int duration) {
        phaseStart = time;
        phaseDuration = duration;
        setMode(newMode);
        dirtyClient = true;
    }

    private void setMode(Mode newMode) {
        if (mode != newMode.ordinal()) {
            mode = newMode.ordinal();
            dirtyClient = true;
        }
    }

    private void setStatus(Status newStatus) {
        status = newStatus.ordinal();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        Planet body = bodyOf(level);
        int count = body == null ? 0 : veinsOf(body).size();
        switch (action) {
            case ACTION_FILTER_NEXT -> filter = (filter + 1) % (count + 1);
            case ACTION_FILTER_PREV -> filter = (filter + count) % (count + 1);
            case ACTION_RANGE_DOWN -> {
                int current = getSurveyRadius();
                range = Math.max(minRange(), current - RANGE_STEP);
            }
            case ACTION_RANGE_UP -> {
                int next = getSurveyRadius() + RANGE_STEP;
                range = next >= getMaxRange() ? 0 : next;
            }
            case ACTION_RESCAN -> {
                Mode current = getMode();
                if (current == Mode.IDLE || current == Mode.CHARGING) {
                    rescanRequested = true;
                    setMode(Mode.IDLE);
                }
            }
            default -> {
            }
        }
        setChanged();
        syncNow();
    }

    // ------------------------------------------------------------------
    // Caracteristiques
    // ------------------------------------------------------------------

    @Nullable
    public static Planet bodyOf(@Nullable Level level) {
        if (level == null) {
            return null;
        }
        for (Planet planet : Planet.values()) {
            if (level.dimension() == planet.dimension()) {
                return planet;
            }
        }
        return null;
    }

    public static List<MiningVein> veinsOf(Planet body) {
        List<MiningVein> list = new ArrayList<>();
        for (MiningVein vein : MiningVein.values()) {
            if (vein.body == body) {
                list.add(vein);
            }
        }
        return list;
    }

    @Nullable
    public MiningVein getFilterVein() {
        Planet body = bodyOf(level);
        if (body == null || filter <= 0) {
            return null;
        }
        List<MiningVein> veins = veinsOf(body);
        return filter - 1 < veins.size() ? veins.get(filter - 1) : null;
    }

    public boolean hasModule(RoverModule module) {
        for (ItemSlot slot : moduleSlots) {
            if (slot.getStack().getItem() instanceof ItemRoverModule item && item.getModule() == module) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public DrillHead getDrillHead() {
        return drillSlot.getStack().getItem() instanceof ItemDrillHead item ? item.getHead() : null;
    }

    /** Tier du rover installe (Mk I a Mk IV) : il rend la base plus ou moins efficace. */
    public UnitTier getRoverTier() {
        if (level != null && level.isClientSide) {
            return UnitTier.byIndex(clientRoverTier);
        }
        return roverSlot.getStack().getItem() instanceof ItemRobotUnit unit ? unit.tier : UnitTier.T1;
    }

    public int getBatteryMax() {
        return (int) Math.round((hasModule(RoverModule.BATTERY) ? BATTERY * 2 : BATTERY) * getRoverTier().energy) * crew();
    }

    public int getCargoMax() {
        return (int) Math.round((hasModule(RoverModule.CARGO) ? CARGO * 2 : CARGO) * getRoverTier().capacity) * crew();
    }

    /** 1.0 : forage selon le tier du garage (MKI a MKIV). */
    public static final double[] TIER_DRILL = {1.0, 1.25, 1.5, 2.0};

    /** Rovers du convoi : ceux du hangar, dans la limite des docks du garage. */
    public int crew() {
        if (level != null && level.isClientSide) {
            return Math.max(1, clientCrew);
        }
        return Math.max(1, Math.min(roverSlot.getStack().getCount(), docks));
    }

    /** Tier du garage construit (0 = MKI). */
    public int getTier() {
        return tier;
    }

    /** Portee maximale de la base (cote serveur : configuration, recherches et module sondeur ; cote client : valeur synchronisee). */
    public int getMaxRange() {
        if (level != null && !level.isClientSide) {
            maxRange = PiCommonConfig.MINING_BASE_RANGE.get() + ResearchEffects.surveyRadiusBonus()
                       + (hasModule(RoverModule.SCANNER) ? 8 : 0);
        }
        return maxRange;
    }

    private static int minRange() {
        return PiCommonConfig.MINING_MIN_RANGE.get();
    }

    /** Portee effective : le reglage du joueur, borne par le minimum et le maximum. */
    public int getSurveyRadius() {
        int max = getMaxRange();
        return range <= 0 ? max : Math.max(Math.min(minRange(), max), Math.min(range, max));
    }

    public boolean isRangeMax() {
        return range <= 0 || range >= getMaxRange();
    }

    /** Plus d'ameliorations de Mekanism sur le socle : la vitesse vient du tier du rover, des modules et des recherches. */
    private int speedUpgrades() {
        return 0;
    }

    /** Vitesse du rover (blocs par tick). */
    public double getDriveSpeed() {
        return 0.14 * (1 + 0.25 * speedUpgrades()) * ResearchEffects.roverSpeedMultiplier() * getRoverTier().speed;
    }

    /** Progression du forage par tick (une unite = {@link #DRILL_TICKS}). */
    public float getDrillRate(@Nullable DrillHead head) {
        if (head == null) {
            return 0;
        }
        return (float) (head.speed * getRoverTier().work * (1 + 0.5 * speedUpgrades()) * (hasModule(RoverModule.OVERCLOCK) ? 1.6 : 1)
                        * crew() * TIER_DRILL[Math.max(0, Math.min(3, tier))]
                        * ResearchEffects.drillSpeedMultiplier()
                       * ResearchEffects.geologyDrillMultiplier(level == null ? null : SolarSystem.fromDimension(level.dimension())));
    }

    // ------------------------------------------------------------------
    // Acces
    // ------------------------------------------------------------------

    public List<ItemSlot> getOutputSlots() {
        return outputSlots;
    }

    public ItemSlot getDrillSlot() {
        return drillSlot;
    }

    public boolean hasRover() {
        return !roverSlot.isEmpty();
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

    public Mode getMode() {
        return Mode.values()[Mth.clamp(mode, 0, Mode.values().length - 1)];
    }

    public Status getStatus() {
        return Status.values()[Mth.clamp(status, 0, Status.values().length - 1)];
    }

    public int getBattery() {
        return battery;
    }

    /** Interface : 4 forme, 5 mode, 6 etat, 7 batterie, 8 benne, 9 filtre, 10 portee, 11 portee max, 12 extrait, 13 trajets, 14 cible. */
    /** API Machine (lot 8b) : valeurs NOMMEES aux indices d'avant (corps propre de la maison dans la vue {@code house}). */
    public static final UiValues.Key V_FORMED = UiValues.Key.of("formed"), V_MODE = UiValues.Key.of("mode"), V_STATUS = UiValues.Key.of("status"), V_BATTERY = UiValues.Key.of("battery"), V_CARGO = UiValues.Key.of("cargo"), V_FILTER = UiValues.Key.of("filter"), V_RANGE = UiValues.Key.of("range"), V_RANGE_MAX = UiValues.Key.of("range_max"), V_MINED = UiValues.Key.of("mined"), V_TRIPS = UiValues.Key.of("trips"), V_TARGET_INDEX = UiValues.Key.of("target_index");

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        UiValues.Key[] keys = {V_FORMED, V_MODE, V_STATUS, V_BATTERY, V_CARGO, V_FILTER, V_RANGE, V_RANGE_MAX, V_MINED, V_TRIPS, V_TARGET_INDEX};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            values.add(keys[i], () -> machineValue(index));
        }
    }

    @Override
    protected int machineValueCount() {
        return 11;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> isFormed() ? 1 : 0;
            case 1 -> mode;
            case 2 -> status;
            case 3 -> battery;
            case 4 -> cargoCount;
            case 5 -> filter;
            case 6 -> getSurveyRadius();
            case 7 -> getMaxRange();
            case 8 -> unitsMined;
            case 9 -> trips;
            default -> targetIndex;
        };
    }

    // ------------------------------------------------------------------
    // Synchronisation et rendu (client)
    // ------------------------------------------------------------------

    private BlockPos clientCenter;
    private Direction clientDoor = Direction.NORTH;
    private boolean clientRover;
    private String clientRoverName = "";
    private int clientRoverTier;
    private int clientMode, clientDuration, clientTarget = -1, clientCargoColor = 0x888888;
    private long clientPhaseStart;
    private float clientCargoFill;
    private int[] clientRoad = new int[0];
    private final List<Deposit> clientDeposits = new ArrayList<>();

    @Nullable
    public BlockPos getClientCenter() {
        return clientCenter;
    }

    public Direction getClientDoor() {
        return clientDoor;
    }

    public boolean hasClientRover() {
        return clientRover;
    }

    /** Nom donne au rover (vide = pas de nom). */
    public String getClientRoverName() {
        return clientRoverName;
    }

    public static final int ROVER_NAME_LENGTH = 24;

    /** Renomme le rover (l'objet dans le hangar garde ce nom). */
    public void setRoverName(String name) {
        ItemStack rover = roverSlot.getStack();
        if (rover.isEmpty()) {
            return;
        }
        ItemStack renamed = rover.copy();
        if (name.isEmpty()) {
            renamed.resetHoverName();
        } else {
            renamed.setHoverName(Component.literal(name));
        }
        roverSlot.setStack(renamed);
        setChanged();
        syncNow();
    }

    private String roverName() {
        ItemStack rover = roverSlot.getStack();
        return !rover.isEmpty() && rover.hasCustomHoverName() ? rover.getHoverName().getString() : "";
    }

    public Mode getClientMode() {
        return Mode.values()[Mth.clamp(clientMode, 0, Mode.values().length - 1)];
    }

    public List<Deposit> getClientDeposits() {
        return clientDeposits;
    }

    public int getClientTarget() {
        return clientTarget;
    }

    public float getClientCargoFill() {
        return clientCargoFill;
    }

    public int getClientCargoColor() {
        return clientCargoColor;
    }

    /** Altitude du rover (relative au centre du sol) a une distance donnee de son trajet. */
    public double getRoadHeight(double distance) {
        return RoverRoad.heightAt(clientRoad, distance);
    }

    public boolean hasClientRoad() {
        return clientRoad.length > 0;
    }

    public long getClientPhaseStart() {
        return clientPhaseStart;
    }

    /**
     * Pose du rover a un instant donne, relative au centre du sol de la base :
     * {x, z, direction x, direction z, en mouvement (0/1), distance parcourue}.
     */
    public double[] getRoverPose(float time) {
        return getRoverPose(time, 0);
    }

    private double clientHalf = 2.5;
    private int clientCrew = 1;

    /** Case (relative au centre du sol) dans l'emprise du hangar (cote client). */
    public boolean insideHangar(int dx, int dz) {
        return clientDoor != null && RoverRoad.insideHangar(clientDoor, clientHalf, dx, dz);
    }

    /** Position du radar (relative au centre du sol) : sur le toit, au bout du hangar (cote client). */
    public double[] radarOffset() {
        Direction door = clientDoor == null ? Direction.NORTH : clientDoor;
        double along = clientHalf - 0.5 - 2;
        return new double[]{door.getStepZ() * along, MiningOutpostStructure.HEIGHT + 3, -door.getStepX() * along};
    }

    /** Rovers dessines (le convoi). */
    public int getClientCrew() {
        return Math.max(1, clientCrew);
    }

    /**
     * Pose du rover n du convoi : chacun part de son dock (travee n) et rejoint la file a la sortie du garage ; au gisement, ils forent
     * cote a cote (decalage lateral).
     */
    public double[] getRoverPose(float time, int index) {
        double[] pose = getRoverPose0(time);
        int crew = getClientCrew();
        if (clientDoor == null) {
            return pose;
        }
        //Axe des travees (le long des portes) : x local du plan (la porte regarde -z local)
        double sx = clientDoor.getStepZ(), sz = -clientDoor.getStepX();
        double dock = 4 * index + 2 - (clientHalf - 0.5);                   //position du dock de la travee le long du hangar
        double lane = (index - (crew - 1) / 2.0) * 1.3;                      //file cote a cote au gisement
        double fade = Math.max(0, 1 - pose[5] / 6.0);                         //quitte son dock sur les 6 premiers blocs
        double nx = -pose[3], nz = pose[2];                                  //normale a la marche
        if (fade >= 1) {
            pose[0] += sx * dock;
            pose[1] += sz * dock;
        } else {
            pose[0] += sx * dock * fade + nx * lane * (1 - fade);
            pose[1] += sz * dock * fade + nz * lane * (1 - fade);
        }
        return pose;
    }

    private double[] getRoverPose0(float time) {
        Mode current = getClientMode();
        Deposit target = clientTarget >= 0 && clientTarget < clientDeposits.size() ? clientDeposits.get(clientTarget) : null;
        if (clientCenter == null || target == null || current != Mode.OUTBOUND && current != Mode.DRILLING && current != Mode.RETURNING) {
            //Au dock, tourne vers la porte
            return new double[]{0, 0, clientDoor.getStepX(), clientDoor.getStepZ(), 0, 0};
        }
        double[][] path = MiningOutpostStructure.path(clientDoor, clientCenter, target.pos, clientHalf);
        double length = MiningOutpostStructure.length(path);
        double t = clientDuration <= 0 ? 1 : Mth.clamp((time - clientPhaseStart) / clientDuration, 0, 1);
        double fraction = switch (current) {
            case OUTBOUND -> t;
            case RETURNING -> 1 - t;
            default -> 1;
        };
        double[] at = MiningOutpostStructure.along(path, fraction);
        double dirX = at[2], dirZ = at[3];
        if (current == Mode.RETURNING) {
            //Marche avant au retour aussi : le rover fait demi-tour au gisement
            dirX = -dirX;
            dirZ = -dirZ;
        }
        if (current == Mode.DRILLING) {
            double[] end = MiningOutpostStructure.along(path, 1);
            dirX = end[2];
            dirZ = end[3];
        }
        boolean moving = current != Mode.DRILLING && t < 1;
        return new double[]{at[0], at[1], dirX, dirZ, moving ? 1 : 0, fraction * length};
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        if (isFormed() && center != null && door != null) {
            tag.putLong("center", center.asLong());
            tag.putInt("door", door.get3DDataValue());
            tag.putDouble("half", half);
        }
        tag.putInt("crew", hasRover() ? crew() : 0);
        tag.putInt("tier", tier);
        tag.putBoolean("rover", hasRover() && isFormed());
        tag.putString("roverName", roverName());
        tag.putByte("roverTier", (byte) getRoverTier().ordinal());
        tag.putInt("mode", mode);
        tag.putLong("phaseStart", phaseStart);
        tag.putInt("duration", phaseDuration);
        tag.putInt("target", targetIndex);
        tag.putIntArray("road", road);
        ListTag list = new ListTag();
        for (Deposit deposit : deposits) {
            list.add(deposit.save());
        }
        tag.put("deposits", list);
        tag.putFloat("cargoFill", cargoCount / (float) getCargoMax());
        tag.putInt("cargoColor", targetIndex >= 0 && targetIndex < deposits.size() ? deposits.get(targetIndex).vein.color : 0x888888);
        tag.putInt("filter", filter);
        tag.putInt("range", range);
        tag.putInt("maxRange", getMaxRange());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        clientCenter = tag.contains("center") ? BlockPos.of(tag.getLong("center")) : null;
        clientDoor = Direction.from3DDataValue(tag.getInt("door"));
        clientHalf = tag.contains("half") ? tag.getDouble("half") : 2.5;
        clientCrew = tag.getInt("crew");
        tier = tag.getInt("tier");
        if (clientDoor.getAxis().isVertical()) {
            clientDoor = Direction.NORTH;
        }
        clientRover = tag.getBoolean("rover");
        clientRoverName = tag.getString("roverName");
        clientRoverTier = tag.getByte("roverTier");
        clientMode = tag.getInt("mode");
        clientPhaseStart = tag.getLong("phaseStart");
        clientDuration = tag.getInt("duration");
        clientTarget = tag.getInt("target");
        clientRoad = tag.getIntArray("road");
        clientDeposits.clear();
        for (Tag entry : tag.getList("deposits", Tag.TAG_COMPOUND)) {
            clientDeposits.add(Deposit.load((CompoundTag) entry));
        }
        clientCargoFill = tag.getFloat("cargoFill");
        clientCargoColor = tag.getInt("cargoColor");
        filter = tag.getInt("filter");
        range = tag.getInt("range");
        maxRange = tag.contains("maxRange") ? tag.getInt("maxRange") : maxRange;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(48, 24, 48);
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);
        deposits.clear();
        for (Tag entry : nbt.getList("deposits", Tag.TAG_COMPOUND)) {
            deposits.add(Deposit.load((CompoundTag) entry));
        }
        cargo.clear();
        cargoCount = 0;
        for (Tag entry : nbt.getList("cargo", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) {
                cargo.add(stack);
                cargoCount += stack.getCount();
            }
        }
        battery = nbt.contains("battery") ? nbt.getInt("battery") : BATTERY;
        mode = nbt.getInt("roverMode");
        phaseStart = nbt.getLong("phaseStart");
        phaseDuration = nbt.getInt("phaseDuration");
        targetIndex = nbt.contains("target") ? nbt.getInt("target") : -1;
        drillProgress = nbt.getFloat("drillProgress");
        filter = nbt.getInt("filter");
        range = nbt.getInt("range");
        unitsMined = nbt.getLong("unitsMined");
        trips = nbt.getInt("trips");
        road = nbt.getIntArray("road");
        pendingDigs.clear();
        pendingDigAt.clear();
        long[] digs = nbt.getLongArray("digs"), digAt = nbt.getLongArray("digAt");
        for (int i = 0; i < Math.min(digs.length, digAt.length); i++) {
            pendingDigs.add(BlockPos.of(digs[i]));
            pendingDigAt.add(Double.longBitsToDouble(digAt[i]));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbtTags) {
        super.saveAdditional(nbtTags);
        ListTag list = new ListTag();
        for (Deposit deposit : deposits) {
            list.add(deposit.save());
        }
        nbtTags.put("deposits", list);
        ListTag cargoList = new ListTag();
        for (ItemStack stack : cargo) {
            cargoList.add(stack.save(new CompoundTag()));
        }
        nbtTags.put("cargo", cargoList);
        nbtTags.putInt("battery", battery);
        nbtTags.putInt("roverMode", mode);
        nbtTags.putLong("phaseStart", phaseStart);
        nbtTags.putInt("phaseDuration", phaseDuration);
        nbtTags.putInt("target", targetIndex);
        nbtTags.putFloat("drillProgress", drillProgress);
        nbtTags.putInt("filter", filter);
        nbtTags.putInt("range", range);
        nbtTags.putLong("unitsMined", unitsMined);
        nbtTags.putInt("trips", trips);
        nbtTags.putIntArray("road", road);
        nbtTags.putLongArray("digs", pendingDigs.stream().mapToLong(BlockPos::asLong).toArray());
        nbtTags.putLongArray("digAt", pendingDigAt.stream().mapToLong(Double::doubleToLongBits).toArray());
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
