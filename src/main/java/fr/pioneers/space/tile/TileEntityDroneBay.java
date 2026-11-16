package fr.pioneers.space.tile;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.Ejector;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideType;
import fr.pioneers.space.base.PiBaseMachines;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.logistics.robotics.ItemRobotUnit;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.base.BaseLayout;
import fr.pioneers.space.base.BaseStructure;
import fr.pioneers.space.giant.GasPocketEntity;
import fr.pioneers.space.giant.GiantEvents;
import fr.pioneers.space.giant.HarvesterDroneEntity;
import fr.pioneers.space.giant.PocketKind;
import fr.pioneers.logistics.transport.Transfer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Baie de drones (socle) : posee sur un aerostat, elle lance jusqu'a quatre drones collecteurs vers les poches de gaz alentour.
 * <ul>
 *     <li>Mode automatique : les drones choisissent les poches (designees d'abord, puis riches, puis proches) ;
 *     mode designe : seulement celles marquees au scanner de gaz.</li>
 *     <li>Filtre par contenu (hydrogene, helium 3, methane, ammoniac, hydrogene metallique, diamants de pluie).</li>
 *     <li>Le blindage (un par drone) ouvre la couche des orages.</li>
 * </ul>
 * Quatre reservoirs de gaz et deux cases de sortie pour les cristaux (faces configurables, ejection). Anciennes baies (Mekanism) reprises
 * dans le meme ordre : drones, blindages, cristaux, diamants, batterie ; reservoirs hydrogene, helium 3, methane, ammoniac.
 * <p>
 * Interface (212 de large) : 4 mode, 5 filtre, 6 en vol, 7 geante gazeuse, 8 perdus, 9 gaz recolte, 10 objets recoltes, 11 drones,
 * 12 blindes.
 */
public class TileEntityDroneBay extends MachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    /** Drones en vol au plus (configuration). */
    public static int maxFlying() {
        return PiCommonConfig.get(PiCommonConfig.DRONE_BAY_MAX_DRONES, 4);
    }

    /** Portee des drones autour de la baie (configuration). */
    public static int range() {
        return PiCommonConfig.get(PiCommonConfig.DRONE_RANGE, 80);
    }

    public static final long GAS_CAPACITY = 64_000;
    public static final long ENERGY_STORAGE_FE = 400_000;
    public static final long LAUNCH_FE = 5_000, FLIGHT_FE_PER_TICK = 15;
    public static final int ACTION_MODE = CUSTOM, ACTION_RECALL = CUSTOM + 1, ACTION_FILTER = CUSTOM + 10;
    public static final int GUI_WIDTH = 212, GUI_HEIGHT = StandardLayout.ZONE;
    /**
     * Lot 8c : interface en VRAIE v2 : energie et batterie a la place commune, 4 reservoirs de gaz, radar, carte d'etat ; rangee des filtres,
     * du mode et du rappel ; cases (drones, blindage, cristaux, diamants) puis ecran des recoltes (ORDRE des cases inchange). Geometrie
     * partagee avec la vue {@code BaseViews} et le banc.
     */
    public static final int SLOT_Y = 104, BATTERY_X = StandardLayout.BATTERY_X, GAS_X = 26, GAS_STEP = 14;
    public static final StandardLayout.Rect ENERGY = new StandardLayout.Rect(8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H),
          RADAR = new StandardLayout.Rect(GAS_X + 4 * GAS_STEP + 2, StandardLayout.TOP, 60, 60),
          CARD = new StandardLayout.Rect(RADAR.x() + RADAR.w() + 4, StandardLayout.TOP, GUI_WIDTH - 8 - (RADAR.x() + RADAR.w() + 4), 60),
          FILTERS = new StandardLayout.Rect(8, StandardLayout.TOP + 64, 6 * 13 - 1, 12),
          MODE_BUTTON = new StandardLayout.Rect(90, FILTERS.y(), 56, 12),
          RECALL = new StandardLayout.Rect(GUI_WIDTH - 8 - 52, FILTERS.y(), 52, 12),
          STATS = new StandardLayout.Rect(104, SLOT_Y - 2, GUI_WIDTH - 8 - 104, 46);
    private static final PocketKind[] GAS_ORDER = {PocketKind.HYDROGEN, PocketKind.HELIUM_3, PocketKind.METHANE, PocketKind.AMMONIA};

    public final BasicGasTank hydrogenTank, helium3Tank, methaneTank, ammoniaTank;
    private final ItemSlot droneSlot, platingSlot, crystalSlot, diamondSlot, energySlot;

    /** 0 : automatique, 1 : poches designees seulement. */
    private int mode;
    private int filter = (1 << PocketKind.values().length) - 1;
    private int flying;
    private boolean gasGiant;
    private int lost;
    private long gasHarvested;
    private int itemsHarvested;
    private boolean recalling;
    private final List<Integer> drones = new ArrayList<>();
    /** 1.0 (lot 4) : baie construite (pont, aires, reservoirs, cabine) : formee, aires (drones en vol au plus), tier, centres des aires. */
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
            BaseStructure.Result r = BaseStructure.find(level, worldPosition, facing(), BaseLayout.Kind.DRONE_BAY);
            return r == null ? MultiblockCore.House.Survey.fail(null, null, null) : MultiblockCore.House.Survey.ok(r.members(), r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            BaseStructure.Result result = (BaseStructure.Result) survey.data();
            int oldTier = tier, oldPads = pads;
            padCenters.clear();
            tier = result.tier();
            pads = result.functional().size() / 4;
            //Centre de chaque aire (2 x 2 dalles) : moyenne de ses 4 dalles, dans l'ordre du plan
            List<BlockPos> tiles = result.functional();
            for (int i = 0; i + 3 < tiles.size(); i += 4) {
                double x = 0, y = 0, z = 0;
                for (int j = 0; j < 4; j++) {
                    x += tiles.get(i + j).getX() + 0.5;
                    y += tiles.get(i + j).getY();
                    z += tiles.get(i + j).getZ() + 0.5;
                }
                padCenters.add(new Vec3(x / 4, y / 4 + 0.25, z / 4));
            }
            if (first || oldTier != tier || oldPads != pads) {
                BaseStructure.paint(level, result);
                setChanged();
                markSync();
            }
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            padCenters.clear();
            if (pads != 0) {
                pads = 0;
                setChanged();
                markSync();
            }
        }

        @Override
        public void houseChanged() {
            setChanged();
            markSync();
        }
    }, 20);
    private int pads, tier;
    private final List<Vec3> padCenters = new ArrayList<>();
    /** Cout du vol et du decollage selon le tier de la baie (MKI a MKIV). */
    public static final double[] TIER_COST = {1.0, 0.7, 0.5, 0.35};

    public TileEntityDroneBay(BlockPos pos, BlockState state) {
        super(PiBaseMachines.DRONE_BAY_TILE.get(), pos, state, ENERGY_STORAGE_FE, (int) (LAUNCH_FE / 4));
        droneSlot = slot(ItemSlot.of(stack -> stack.getItem() instanceof ItemRobotUnit unit && unit.kind == UnitKind.DRONE, this::setChanged, 8, SLOT_Y).ghost(() -> new ItemStack(PiRobotics.UNITS.get(UnitKind.DRONE).get(UnitTier.T1).get())));
        platingSlot = slot(ItemSlot.of(stack -> stack.is(PiItems.DRONE_ARMOR_PLATING.get()), this::setChanged, 26, SLOT_Y).ghost(PiItems.DRONE_ARMOR_PLATING.get()));
        crystalSlot = slot(output(62).ghost(PiItems.METALLIC_HYDROGEN.get()));
        diamondSlot = slot(output(80).ghost(PiItems.RAIN_DIAMOND.get()));
        energySlot = batterySlot(StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y);
        hydrogenTank = gasTank(BasicGasTank.output(GAS_CAPACITY, this::tanksChanged));
        helium3Tank = gasTank(BasicGasTank.output(GAS_CAPACITY, this::tanksChanged));
        methaneTank = gasTank(BasicGasTank.output(GAS_CAPACITY, this::tanksChanged));
        ammoniaTank = gasTank(BasicGasTank.output(GAS_CAPACITY, this::tanksChanged));
    }

    private ItemSlot output(int x) {
        return new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL, (stack, automation) -> true,
              this::setChanged, x, SLOT_Y);
    }

    private void tanksChanged() {
        setChanged();
        markSync();
    }

    @Override
    protected SideType[] ejectTypes() {
        return new SideType[]{SideType.ITEMS, SideType.GASES};
    }

    @Override
    protected boolean canEject() {
        return true;
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
        java.util.List<StandardLayout.Part> parts = new java.util.ArrayList<>();
        parts.add(StandardLayout.framed("energy", ENERGY));
        for (int i = 0; i < 4; i++) {
            parts.add(StandardLayout.framed("gauge", new StandardLayout.Rect(GAS_X + i * GAS_STEP, StandardLayout.TOP, StandardLayout.GAUGE_W, 60)));
        }
        parts.add(new StandardLayout.Part("card", RADAR));
        parts.add(new StandardLayout.Part("card", CARD));
        parts.add(new StandardLayout.Part("setting", FILTERS));
        parts.add(new StandardLayout.Part("setting", MODE_BUTTON));
        parts.add(new StandardLayout.Part("setting", RECALL));
        parts.add(new StandardLayout.Part("screen", STATS));
        return parts;
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

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

    /** Lot 8a : etat de la structure au client (onglet Structure, voile, erreur commune). */
    @Override
    protected void writeClient(net.minecraft.nbt.CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
    }

    @Override
    protected void readClient(net.minecraft.nbt.CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
    }

    public int getTier() {
        return tier;
    }

    @Override
    protected void tick() {
        discharge(energySlot, LAUNCH_FE);
        house.tick();
        gasGiant = PiDimensions.isGasGiant(level);
        drones.removeIf(id -> !(level.getEntity(id) instanceof HarvesterDroneEntity drone) || !drone.isAlive());
        //Drones disparus sans rentrer (dechargement du monde...) : ils sont consideres comme rentres
        flying = drones.size();
        if (flying > 0) {
            use(Math.min(energy.getStored(), Math.round(FLIGHT_FE_PER_TICK * flying * TIER_COST[tier])));
        }
        if (recalling && flying == 0) {
            recalling = false;
        }
        boolean active = flying > 0;
        if (gasGiant && level.getGameTime() % 100 == 0 && level instanceof ServerLevel server) {
            //Une baie chargee sans joueur a proximite entretient aussi ses poches
            GiantEvents.populate(server, Vec3.atCenterOf(worldPosition), 10, 16, range());
        }
        if (isFormed() && gasGiant && !recalling && canWork() && level.getGameTime() % 20 == Math.floorMod(worldPosition.asLong(), 20)) {
            //1.0 : une aire d'atterrissage par drone en vol
            int available = Math.min(Math.min(maxFlying(), pads), droneSlot.getCount()) - flying;
            long launchCost = Math.round(LAUNCH_FE * TIER_COST[tier]);
            while (available > 0 && energy.getStored() >= launchCost) {
                boolean armored = drones.size() < platingSlot.getCount();
                GasPocketEntity target = findTarget(null, armored);
                if (target == null || !launch(target, armored)) {
                    break;
                }
                use(launchCost);
                available--;
                active = true;
            }
        }
        if (level.getGameTime() % 10 == 0) {
            Ejector.eject(level, worldPosition, sides, Transfer.GASES, internalGases(), 4_000);
            Ejector.eject(level, worldPosition, sides, Transfer.ITEMS, new ItemSlots(List.of(crystalSlot, diamondSlot), Automation.INTERNAL), 64);
        }
        markWorking(active);
    }

    private boolean launch(GasPocketEntity target, boolean armored) {
        HarvesterDroneEntity drone = PiEntityTypes.HARVESTER_DRONE.get().create(level);
        if (drone == null) {
            return false;
        }
        //Decollage depuis l'aire du drone (le controleur reste son port d'attache)
        Vec3 from = drones.size() < padCenters.size() ? padCenters.get(drones.size())
              : new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5);
        drone.moveTo(from.x, from.y, from.z, 0, 0);
        drone.launch(worldPosition, drones.size(), armored, target, getDroneTier());
        level.addFreshEntity(drone);
        drones.add(drone.getId());
        flying = drones.size();
        return true;
    }

    /** Prochaine poche pour un drone (en vol, ou au decollage si {@code drone} est null). */
    @Nullable
    public GasPocketEntity findTarget(@Nullable HarvesterDroneEntity drone) {
        return recalling ? null : findTarget(drone, drone != null && drone.isArmored());
    }

    @Nullable
    private GasPocketEntity findTarget(@Nullable HarvesterDroneEntity drone, boolean armored) {
        Vec3 center = Vec3.atCenterOf(worldPosition);
        AABB box = new AABB(worldPosition).inflate(range(), level.getHeight(), range());
        GasPocketEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (GasPocketEntity pocket : level.getEntitiesOfClass(GasPocketEntity.class, box)) {
            PocketKind kind = pocket.getKind();
            if ((filter & kind.bit()) == 0 || pocket.isClaimed() || mode == 1 && !pocket.isMarked() || pocket.isInStorm() && !armored
                || !hasRoomFor(kind, kind.tripCapacity(armored))) {
                continue;
            }
            if (drone != null && drone.getCargo() > 0 && drone.getCargoKind() != kind) {
                continue;
            }
            double distance = pocket.position().distanceTo(drone == null ? center : drone.position());
            double score = distance - (pocket.isMarked() ? 1000 : 0) - (pocket.isRich() ? 40 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = pocket;
            }
        }
        return best;
    }

    @Nullable
    public BasicGasTank tankFor(PocketKind kind) {
        return switch (kind) {
            case HYDROGEN -> hydrogenTank;
            case HELIUM_3 -> helium3Tank;
            case METHANE -> methaneTank;
            case AMMONIA -> ammoniaTank;
            default -> null;
        };
    }

    private ItemSlot crystalSlotFor(PocketKind kind) {
        return kind == PocketKind.METALLIC_HYDROGEN ? crystalSlot : diamondSlot;
    }

    private boolean hasRoomFor(PocketKind kind, int amount) {
        BasicGasTank tank = tankFor(kind);
        if (tank != null) {
            return tank.getCapacity() - tank.getStack().getAmount() >= Math.min(amount, GAS_CAPACITY / 4);
        }
        ItemSlot slot = crystalSlotFor(kind);
        return slot.getCount() < slot.getLimit(slot.getStack());
    }

    /** Un drone rentre : il depose sa cargaison. */
    public void dock(HarvesterDroneEntity drone) {
        drones.remove((Integer) drone.getId());
        flying = drones.size();
        int amount = drone.getCargo();
        if (amount <= 0) {
            return;
        }
        PocketKind kind = drone.getCargoKind();
        BasicGasTank tank = tankFor(kind);
        Gas gas = kind.gasId() == null ? null : PioneersAPI.gas(kind.gasId());
        if (tank != null && gas != null) {
            tank.insert(GasStack.of(gas, amount), Action.EXECUTE, Automation.INTERNAL);
            gasHarvested += amount;
        } else if (kind.item() != null) {
            crystalSlotFor(kind).insert(new ItemStack(kind.item(), amount), Action.EXECUTE, Automation.INTERNAL);
            itemsHarvested += amount;
        }
        setChanged();
    }

    /** Tier des drones installes : vol, debit d'aspiration et emport augmentent avec lui. */
    public UnitTier getDroneTier() {
        return droneSlot.getStack().getItem() instanceof ItemRobotUnit unit ? unit.tier : UnitTier.T1;
    }

    /** Un drone est perdu (foudre) : il disparait de la reserve. */
    public void lost(HarvesterDroneEntity drone) {
        drones.remove((Integer) drone.getId());
        flying = drones.size();
        if (!droneSlot.isEmpty()) {
            droneSlot.extract(1, Action.EXECUTE, Automation.INTERNAL);
        }
        if (drone.isArmored() && !platingSlot.isEmpty()) {
            platingSlot.extract(1, Action.EXECUTE, Automation.INTERNAL);
        }
        lost++;
        setChanged();
    }

    public boolean isRecalling() {
        return recalling;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == ACTION_MODE) {
            mode = mode == 0 ? 1 : 0;
        } else if (action == ACTION_RECALL) {
            recalling = true;
        } else if (action >= ACTION_FILTER && action < ACTION_FILTER + PocketKind.values().length) {
            filter ^= PocketKind.values()[action - ACTION_FILTER].bit();
        }
        setChanged();
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    public static long getMaxEnergyUsage() {
        return LAUNCH_FE;
    }

    /** API Machine (lot 8b) : valeurs NOMMEES aux indices d'avant (corps propre de la maison dans la vue {@code house}). */
    public static final UiValues.Key MODE = UiValues.Key.of("mode"), FILTER = UiValues.Key.of("filter"), FLYING = UiValues.Key.of("flying"), GAS_GIANT = UiValues.Key.of("gas_giant"), LOST = UiValues.Key.of("lost"), GAS_HARVESTED = UiValues.Key.of("gas_harvested"), ITEMS_HARVESTED = UiValues.Key.of("items_harvested"), DRONES = UiValues.Key.of("drones"), PLATED = UiValues.Key.of("plated"), FORMED = UiValues.Key.of("formed"), PADS = UiValues.Key.of("pads"), TIER = UiValues.Key.of("tier");

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        UiValues.Key[] keys = {MODE, FILTER, FLYING, GAS_GIANT, LOST, GAS_HARVESTED, ITEMS_HARVESTED, DRONES, PLATED, FORMED, PADS, TIER};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            values.add(keys[i], () -> machineValue(index));
        }
    }

    @Override
    protected int machineValueCount() {
        return 12;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> mode;
            case 1 -> filter;
            case 2 -> flying;
            case 3 -> gasGiant ? 1 : 0;
            case 4 -> lost;
            case 5 -> gasHarvested;
            case 6 -> itemsHarvested;
            case 7 -> droneSlot.getCount();
            case 8 -> Math.min(droneSlot.getCount(), platingSlot.getCount());
            case 9 -> isFormed() ? 1 : 0;
            case 10 -> pads;
            default -> tier;
        };
    }

    /** Filtre (cote client : valeur d'interface). */
    public static boolean accepts(long filter, PocketKind kind) {
        return (filter & kind.bit()) != 0;
    }

    public boolean accepts(PocketKind kind) {
        return accepts(filter, kind);
    }

    public static PocketKind[] gasOrder() {
        return GAS_ORDER;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);
        mode = nbt.getInt("mode");
        if (nbt.contains("filter")) {
            filter = nbt.getInt("filter");
        }
        lost = nbt.getInt("lost");
        gasHarvested = nbt.getLong("gasHarvested");
        itemsHarvested = nbt.getInt("itemsHarvested");
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("mode", mode);
        nbt.putInt("filter", filter);
        nbt.putInt("lost", lost);
        nbt.putLong("gasHarvested", gasHarvested);
        nbt.putInt("itemsHarvested", itemsHarvested);
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
