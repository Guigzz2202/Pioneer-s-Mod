package fr.pioneers.space.tile;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.compat.mekanism.MekanismCompat;
import fr.pioneers.core.compat.mekanism.MekanismGasBridge;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.PiTags;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.core.registries.PiBaseMachines;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.RoboticUpgrade;
import fr.pioneers.space.ItemRocket;
import fr.pioneers.space.LaunchPadStructure.Size;
import fr.pioneers.space.LaunchPadStructure;
import fr.pioneers.space.LaunchTowerBlock;
import fr.pioneers.space.LaunchVisualEntity;
import fr.pioneers.space.Propellant;
import fr.pioneers.space.RocketEntity;
import fr.pioneers.space.RocketTier;
import fr.pioneers.space.cargo.CargoFlight;
import fr.pioneers.space.cargo.CargoNetwork.Station;
import fr.pioneers.space.cargo.CargoNetwork;
import fr.pioneers.space.cargo.CargoRoutes;
import fr.pioneers.space.cargo.CargoType;
import fr.pioneers.space.module.RocketCargo;
import fr.pioneers.space.module.RocketModule;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ProbeNetwork;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cœur du complexe de lancement : il planifie la mission et automatise le plein et le chargement ; les modules et les soutes
 * appartiennent a la fusee (accroupi + clic droit sur la fusee).
 * <ul>
 *     <li><b>Vol habite</b> (module habitacle) : le joueur monte dans la fusee et decolle avec ses modules et sa cargaison.</li>
 *     <li><b>Sonde</b> (modules sonde) : lancement automatique ; chaque module met une sonde en orbite de la destination.
 *     Le tier des sondes suit celui de la fusee ; la fusee et ses sondes sont consommees, les autres modules et la cargaison
 *     reviennent dans le quai du complexe.</li>
 *     <li><b>Logistique</b> (modules de soute) : le quai remplit les soutes de la fusee, qui part vers un complexe en reception,
 *     y est dechargee, refait le plein, puis revient se poser ici, vide, avec ses modules.</li>
 *     <li><b>Reception</b> : accueille les fusees logistiques, les decharge dans le quai (qui se vide de lui-meme dans les
 *     tuyaux, cables, ports et le reseau de donnees branches), puis leur refait le plein pour le retour.</li>
 * </ul>
 * Le quai (18 cases, fluide, gaz, donnees) est l'interface d'automatisation : on l'alimente par les ports du complexe.
 * <p>
 * Socle (sans Mekanism) : carburant = fluides du tag {@code pioneers:rocket_fuel} ; ameliorations de vitesse = case d'ameliorations
 * de vitesse robotiques. Anciens coeurs (Mekanism) repris dans le meme ordre : fusee, 18 cases du quai, seau plein, seau vide ;
 * reservoirs carburant et soute a fluides ; soute a gaz ; soute a donnees.
 * <p>
 * Interface (236 de large) : 4 mission, 5 taille, 6 destination, 7 regle, 8 minuterie (s), 9 etat, 10 compte a rebours, 11 lancements,
 * 12 reussites, 13 echecs, 14 envoyes, 15 recus, 16 sondes a destination, 17 tier fusee, 18 carburant, 19 capacite, 20 carburant
 * demande, 21 duree (s), 22 escale (s), 23 remplissage (%).
 */
public class TileEntityLaunchPad extends DataMachineTile implements MultiblockPort, MultiblockCore.House.Holder {

    public static final int TANK_CAPACITY = 250_000;
    public static final int STATION_NAME_LENGTH = 24;
    public static final int COUNTDOWN_TICKS = 60;
    public static final int CARGO_SLOTS = 18;
    public static final long BUFFER_FLUID = 64_000, BUFFER_GAS = 64_000, BUFFER_DATA = 200_000;
    /** Escale minimale apres chaque atterrissage (inspection, refroidissement) avant de repartir. */
    public static final int TURNAROUND_TICKS = 200;
    public static final int[] TIMER_SECONDS = {30, 60, 120, 300};
    public static final int ACTION_MISSION = CUSTOM, ACTION_PREVIOUS = CUSTOM + 1, ACTION_NEXT = CUSTOM + 2, ACTION_RULE = CUSTOM + 3,
          ACTION_TIMER = CUSTOM + 4;
    public static final int GUI_WIDTH = 236, GUI_HEIGHT = 164;
    /**
     * Lot 8c : interface en VRAIE v2 (tout sous la bande du titre, elements declares) : nom de la station et mission, carburant, fusee et
     * ameliorations, ecran d'etat, destination, ligne de la mission, modules de la fusee, seaux, quai (18 cases et 3 soutes). ORDRE des cases
     * inchange. Geometrie partagee avec la vue {@code BaseViews} et le banc.
     */
    public static final int PAD_TOP = StandardLayout.TOP;
    public static final int ROCKET_X = 30, ROCKET_Y = PAD_TOP + 16, UPGRADE_X = 30, UPGRADE_Y = PAD_TOP + 40, BUCKET_X = 8, BUCKET_IN_Y = PAD_TOP + 80,
          BUCKET_OUT_Y = PAD_TOP + 100;
    public static final int MODULES_X = 30, MODULES_Y = PAD_TOP + 80, DOCK_X = 30, DOCK_Y = PAD_TOP + 102, MAX_UPGRADES = 8;
    public static final StandardLayout.Rect NAME = new StandardLayout.Rect(8, PAD_TOP, 118, 13), MISSION_BUTTON = new StandardLayout.Rect(130, PAD_TOP, 98, 13),
          FUEL = new StandardLayout.Rect(8, PAD_TOP + 16, 16, 58), STATUS_SCREEN = new StandardLayout.Rect(52, PAD_TOP + 16, 176, 30),
          TARGET_ROW = new StandardLayout.Rect(52, PAD_TOP + 49, 176, 12), INFO_ROW = new StandardLayout.Rect(52, PAD_TOP + 64, 176, 14),
          MODULES = new StandardLayout.Rect(MODULES_X - 1, MODULES_Y - 1, GUI_WIDTH - 8 - (MODULES_X - 1), 18),
          DOCK = new StandardLayout.Rect(DOCK_X + 9 * 18 + 4, DOCK_Y, 34, 36);

    public enum Mission {MANNED, PROBE, LOGISTICS, RECEIVE}

    public enum Rule {ASAP, HALF, FULL, TIMER, REDSTONE}

    public enum Status {
        NOT_FORMED, NO_ROCKET, PAD_TOO_SMALL, NO_PASSENGER, READY_TO_BOARD, BOARDED, NO_PROBE, RESEARCH_LOCKED, PROBE_LOCKED, TIER_TOO_LOW,
        FUELING, DISABLED, SKY_BLOCKED, COUNTDOWN, NO_CARGO_MODULE, NO_TARGET, WAITING_CARGO, IN_FLIGHT, MAINTENANCE, READY, LANDING, UNLOADING,
        BUFFER_FULL, REFUELING, PAD_OCCUPIED,
        /** 0.18.0 : l'ergol du reservoir ne convient pas au tier de la fusee (voir {@link Propellant}). */
        WRONG_FUEL
    }

    public final FluidTank fuelTank, fluidCargo;
    /** 0.18.0 : ergol du reservoir inadapte a la fusee posee. */
    private boolean wrongFuel;
    public final BasicGasTank gasCargo;
    private final ItemSlot rocketSlot, bucketInput, bucketOutput, upgradeSlot;
    private final List<ItemSlot> cargoSlots = new ArrayList<>();
    /** Quai a donnees (comme le quai a fluide et a gaz). */
    private final BasicDataTank dataCargo;

    @Nullable
    private UUID stationId;
    @Nullable
    private UUID targetId;
    private String name = "";

    private int mission;
    private int size = -1;
    /** 0.20.34 : rayon des plaques peintes a la couleur du tier (-1 = inconnu apres chargement, 0 = rien de peint). */
    private int paintedRadius = -1;
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
            return net.minecraft.core.Direction.NORTH;
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            LaunchPadStructure.Found found = LaunchPadStructure.measure(fr.pioneers.tech.industry.multi.Structure.View.of(level), worldPosition);
            return found == null ? MultiblockCore.House.Survey.fail("gui.pioneers.multiblock.house.launch_pad", null, null)
                  : MultiblockCore.House.Survey.ok(LaunchPadStructure.cells(worldPosition, found), found);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            LaunchPadStructure.Found found = (LaunchPadStructure.Found) survey.data();
            LaunchPadStructure.paint(level, worldPosition, found);
            size = found.size().ordinal();
            //0.20.34 : couleur du tier : le complexe retreci rend leur couleur neutre aux plaques et tours qui ne servent plus
            int radius = found.size().radius;
            if (paintedRadius > radius) {
                LaunchPadStructure.paintDisc(level, worldPosition, radius, paintedRadius, 0);
            }
            paintedRadius = radius;
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            size = -1;
            if (!survey.removal() && paintedRadius != 0) {
                LaunchPadStructure.unpaint(level, worldPosition);
            }
            paintedRadius = 0;
        }

        @Override
        public void houseChanged() {
            setChanged();
            markSync();
        }
    }, 20);
    private int destination;
    private int rule;
    private int timerIndex = 1;
    private int status;
    private int countdown;
    private int launches;
    private int successes;
    private int failures;
    private int sent;
    private int received;
    private int probesAtDestination;
    /** Fusee sur le pas de tir : tier (-1 = aucune), carburant, capacite. */
    private int rocketTier = -1;
    private int rocketFuel;
    private int rocketCapacity;
    private int fuelNeeded;
    private int etaSeconds;
    private int turnaround;
    /** Remplissage des soutes de la fusee posee (pour cent). */
    private int rocketFill;

    /** Sondes en vol : {destination, sondes mises en orbite (0 = echec), tick de fin}. */
    private final List<long[]> inFlight = new ArrayList<>();
    private long lastDeparture;
    private long dockedAt = -TURNAROUND_TICKS;
    /** Fusee en visite (reception) : tier, modules, cargaison restante et complexe d'attache. */
    @Nullable
    private CompoundTag visitorCargo;
    @Nullable
    private UUID visitorHome;
    private int visitorTier;
    private ListTag visitorModules = new ListTag();
    /** Une fusee est en train de se poser ici. */
    private boolean landing;
    private boolean unloading;
    /** Modules de la fusee posee (serveur : relus chaque tick ; client : synchronises). */
    private List<ItemStack> rocketModules = List.of();

    //Cote client (rendu de la fusee en visite)
    private String targetLabel = "";
    private int clientVisitor = -1;
    private boolean clientCountdown, clientBusy;
    private int clientCargoColor = CargoType.ITEMS.color;

    public TileEntityLaunchPad(BlockPos pos, BlockState state) {
        super(PiBaseMachines.LAUNCH_PAD_CORE_TILE.get(), pos, state, 0, 0);
        //Ordre des anciennes cases (Mekanism) : fusee, 18 cases du quai, seau plein, seau vide
        rocketSlot = slot(new ItemSlot(() -> 1, stack -> stack.getItem() instanceof ItemRocket, (stack, automation) -> true,
              (stack, automation) -> automation != Automation.EXTERNAL, this::setChanged, ROCKET_X, ROCKET_Y).ghost(PiItems.ROCKET_TIER_1.get()));
        for (int i = 0; i < CARGO_SLOTS; i++) {
            //Quai : rempli de l'exterieur en expedition, vide vers l'exterieur en reception
            cargoSlots.add(slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL || isLoadingMission(),
                  (stack, automation) -> automation != Automation.EXTERNAL || isReceiver(), this::setChanged, DOCK_X + (i % 9) * 18, DOCK_Y + (i / 9) * 18)));
        }
        bucketInput = slot(new ItemSlot(() -> 1, TileEntityLaunchPad::holdsFuel, (stack, automation) -> true, (stack, automation) -> true,
              this::setChanged, BUCKET_X, BUCKET_IN_Y).ghost(() -> fuelBucket()));
        bucketOutput = slot(new ItemSlot(() -> 16, stack -> true, (stack, automation) -> automation == Automation.INTERNAL, (stack, automation) -> true,
              this::setChanged, BUCKET_X, BUCKET_OUT_Y).ghost(Items.BUCKET));
        upgradeSlot = slot(new ItemSlot(() -> MAX_UPGRADES, stack -> stack.is(PiRobotics.UPGRADES.get(RoboticUpgrade.SPEED).get()),
              (stack, automation) -> automation != Automation.EXTERNAL, (stack, automation) -> automation != Automation.EXTERNAL, this::setChanged,
              UPGRADE_X, UPGRADE_Y).ghost(() -> new ItemStack(PiRobotics.UPGRADES.get(RoboticUpgrade.SPEED).get())));
        fuelTank = fluidTank(new FluidTank(TANK_CAPACITY, TileEntityLaunchPad::isFuel) {
            @Override
            protected void onContentsChanged() {
                tanksChanged();
            }
        });
        fluidCargo = fluidTank(new FluidTank((int) BUFFER_FLUID) {
            @Override
            protected void onContentsChanged() {
                tanksChanged();
            }
        });
        gasCargo = gasTank(new BasicGasTank(BUFFER_GAS, gas -> true, automation -> automation == Automation.INTERNAL || isLoadingMission(),
              automation -> automation != Automation.EXTERNAL || isReceiver(), this::tanksChanged));
        dataCargo = dataTank(BasicDataTank.of(BUFFER_DATA, type -> true, (type, automation) -> automation != Automation.EXTERNAL || isLoadingMission(),
              (type, automation) -> automation != Automation.EXTERNAL || isReceiver(), this::tanksChanged));
        //Tout passe par les ports du complexe : pas de faces a regler sur le coeur
        dataDirections(false, false);
    }

    private void tanksChanged() {
        setChanged();
        markSync();
    }

    /** Fluide accepte comme carburant (tag {@code pioneers:rocket_fuel}). */
    public static boolean isFuel(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().is(PiTags.Fluids.ROCKET_FUEL);
    }

    /** Seau du premier carburant connu (aide de l'interface), sinon un seau vide. */
    private static ItemStack fuelBucket() {
        return ForgeRegistries.FLUIDS.tags().getTag(PiTags.Fluids.ROCKET_FUEL).stream().findFirst()
              .map(fluid -> new ItemStack(fluid.getBucket())).filter(stack -> !stack.isEmpty())
              .orElseGet(() -> new ItemStack(Items.BUCKET));
    }

    private static boolean holdsFuel(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).map(handler -> isFuel(handler.getFluidInTank(0))).orElse(false);
    }

    /** Vide un seau (ou tout contenant) de carburant dans le reservoir ; le contenant vide va dans la case du dessous. */
    private void drainBucket() {
        ItemStack stack = bucketInput.getStack();
        if (stack.isEmpty() || !bucketOutput.getStack().isEmpty() && bucketOutput.getCount() >= 16) {
            return;
        }
        ItemStack single = stack.copyWithCount(1);
        single.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).ifPresent(handler -> {
            FluidStack inside = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (!isFuel(inside) || fuelTank.fill(inside, IFluidHandler.FluidAction.SIMULATE) < inside.getAmount()) {
                return;
            }
            FluidStack drained = handler.drain(inside.getAmount(), IFluidHandler.FluidAction.EXECUTE);
            ItemStack empty = handler.getContainer();
            if (!bucketOutput.insert(empty.copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                return;
            }
            fuelTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            bucketInput.setStack(ItemStack.EMPTY);
            bucketOutput.insert(empty, Action.EXECUTE, Automation.INTERNAL);
        });
    }

    private int speedUpgrades() {
        return upgradeSlot.getCount();
    }

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
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
        return java.util.List.of(new StandardLayout.Part("setting", NAME), new StandardLayout.Part("setting", MISSION_BUTTON),
              StandardLayout.framed("gauge", FUEL), new StandardLayout.Part("screen", STATUS_SCREEN), new StandardLayout.Part("setting", TARGET_ROW),
              new StandardLayout.Part("card", INFO_ROW), new StandardLayout.Part("card", MODULES), StandardLayout.framed("gauge", DOCK));
    }

    @Override
    public String guiTier() {
        return "launch_pad";
    }

    /** Le coeur ne s'ouvre pas par ses faces : objets, fluides et gaz passent par les ports du complexe. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (side != null && (cap == ForgeCapabilities.ITEM_HANDLER || cap == ForgeCapabilities.FLUID_HANDLER || cap == ForgeCapabilities.ENERGY
                             || cap == PioneersAPI.GAS_HANDLER || MekanismCompat.LOADED
                                                                                 && MekanismGasBridge.isMekanismGasCapability(cap))) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    // ------------------------------------------------------------------
    // Boucle
    // ------------------------------------------------------------------

    @Override
    protected void tick() {
        drainBucket();
        ServerLevel serverLevel = (ServerLevel) level;
        CargoNetwork network = CargoNetwork.get(serverLevel.getServer());
        long now = level.getGameTime();
        resolveProbeFlights();
        house.tick();
        if (now % 20 == 0) {
            if (stationId == null) {
                stationId = UUID.randomUUID();
                var body = CargoRoutes.body(level.dimension());
                int index = network.countOn(level.dimension()) + 1;
                name = (body == null ? "Station" : defaultName(body.name())) + " " + index;
                setChanged();
            }
            network.updateStation(stationId, level.dimension(), worldPosition, name, isReceiver());
            probesAtDestination = ProbeNetwork.get(serverLevel.getServer()).getProbes(getDestination());
            refreshRoute(network);
        }
        Size padSize = getSize();
        RocketEntity rocket = findRocket();
        if (rocket == null && padSize != null && !landing && visitorCargo == null && !isReceiver()) {
            rocket = deployFromSlot(padSize);
        }
        updateRocketModules(rocket);
        if (rocket != null && rocket.getState() == RocketEntity.State.IDLE && !rocket.isVehicle() && isLoadingMission() && now % 5 == 0) {
            //Le quai charge les soutes de la fusee (selon ses modules)
            loadRocket(rocket);
        }
        rocketFill = rocket == null ? 0 : (int) Math.round(rocket.getCargo().fillRatio(rocket.getModules()) * 100);
        rocketTier = rocket == null ? -1 : rocket.getTier().ordinal();
        //0.18.0 : seul un ergol adapte au tier de la fusee passe dans ses reservoirs
        wrongFuel = rocket != null && !fuelTank.isEmpty() && !Propellant.suits(fuelTank.getFluid(), rocket.getTier());
        if (rocket != null && padSize != null && !wrongFuel && rocket.getTier().level <= padSize.maxTier && rocket.getState() == RocketEntity.State.IDLE) {
            int rate = (int) (padSize.fillRate * (1 + speedUpgrades()));
            int amount = Math.min(rate, fuelTank.getFluidAmount());
            if (amount > 0) {
                int accepted = rocket.fill(amount);
                if (accepted > 0) {
                    fuelTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
        rocketFuel = rocket == null ? 0 : rocket.getFuel();
        rocketCapacity = rocket == null ? 0 : rocket.getFuelCapacity();

        Status current = switch (getMission()) {
            case MANNED -> tickManned(rocket, padSize);
            case PROBE -> tickProbe(rocket, padSize);
            case LOGISTICS -> tickLogistics(network, rocket, padSize, now);
            case RECEIVE -> tickReceiver(network, now);
        };
        status = current.ordinal();
        if (isReceiver() && now % 5 == 0) {
            pushOutputs();
        }
        markWorking(padSize != null);
        //Rendu : fusee en visite, compte a rebours, activite
        int visitor = visitorCargo != null && !landing ? visitorTier : -1;
        boolean busy = current == Status.UNLOADING;
        if (visitor != clientVisitor || (countdown > 0) != clientCountdown || busy != clientBusy) {
            clientVisitor = visitor;
            clientCountdown = countdown > 0;
            clientBusy = busy;
            syncNow();
        }
    }

    private static String defaultName(String body) {
        return switch (body) {
            case "EARTH" -> "Terre";
            case "MOON" -> "Lune";
            case "VENUS" -> "Venus";
            case "MERCURY" -> "Mercure";
            case "DEIMOS" -> "Deimos";
            case "CERES" -> "Ceres";
            case "HYGIEA" -> "Hygie";
            case "EUROPA" -> "Europe";
            case "GANYMEDE" -> "Ganymede";
            case "ENCELADUS" -> "Encelade";
            case "PLUTO" -> "Pluton";
            default -> body.charAt(0) + body.substring(1).toLowerCase();
        };
    }

    @Nullable
    private RocketEntity findRocket() {
        List<RocketEntity> rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(worldPosition.above()).inflate(0.4, 0, 0.4));
        return rockets.isEmpty() ? null : rockets.get(0);
    }

    /** Une fusee arrivee par tuyau (ou rentree d'une route logistique) est dressee sur le pas de tir. */
    @Nullable
    private RocketEntity deployFromSlot(Size padSize) {
        if (!(rocketSlot.getStack().getItem() instanceof ItemRocket item) || item.getTier().level > padSize.maxTier || countdown > 0) {
            return null;
        }
        if (skyBusy()) {
            return null;
        }
        RocketEntity rocket = item.getTier().getEntityType().create(level);
        if (rocket == null) {
            return null;
        }
        BlockPos pos = worldPosition.above();
        rocket.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing().toYRot(), 0);
        if (!level.noCollision(rocket)) {
            return null;
        }
        ItemStack stack = rocketSlot.extract(1, Action.EXECUTE, Automation.INTERNAL);
        rocket.setModules(ItemRocket.getModules(stack));
        level.addFreshEntity(rocket);
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 0.6F);
        return rocket;
    }

    private boolean skyBusy() {
        return !level.getEntitiesOfClass(LaunchVisualEntity.class, new AABB(worldPosition.above()).inflate(1.5, 20, 1.5)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Modules
    // ------------------------------------------------------------------

    /** Missions pendant lesquelles le quai se charge de l'exterieur et remplit la fusee. */
    public boolean isLoadingMission() {
        Mission current = getMission();
        return current == Mission.LOGISTICS || current == Mission.MANNED;
    }

    /** Emplacements de modules de la fusee posee (0 sans fusee). */
    public int getModuleCapacity() {
        RocketTier tier = getRocketTier();
        return tier == null ? 0 : RocketModule.slots(tier);
    }

    /** Modules de la fusee posee sur le pas de tir (cote serveur comme client). */
    public List<ItemStack> getActiveModules() {
        return rocketModules;
    }

    public int countModule(RocketModule module) {
        return RocketModule.count(getActiveModules(), module);
    }

    private void updateRocketModules(@Nullable RocketEntity rocket) {
        List<ItemStack> modules = rocket == null ? List.of() : rocket.getModules();
        if (!sameStacks(modules, rocketModules)) {
            rocketModules = modules;
            syncNow();
        }
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameTags(a.get(i), b.get(i)) || a.get(i).getCount() != b.get(i).getCount()) {
                return false;
            }
        }
        return true;
    }

    /** Range des objets dans le quai ; ce qui ne rentre pas tombe au sol. */
    private void stash(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemSlot slot : cargoSlots) {
            rest = slot.insert(rest, Action.EXECUTE, Automation.INTERNAL);
            if (rest.isEmpty()) {
                return;
            }
        }
        drop(rest);
    }

    /** Rend au quai la cargaison d'une fusee consommee (sonde) ; les fluides, gaz et donnees qui ne rentrent pas sont perdus. */
    private void stashCargo(RocketCargo cargo) {
        cargo.drainItems(this::stash);
        if (!cargo.getFluid().isEmpty()) {
            fluidCargo.fill(cargo.getFluid(), IFluidHandler.FluidAction.EXECUTE);
        }
        if (!cargo.getGas().isEmpty()) {
            gasCargo.insert(cargo.getGas(), Action.EXECUTE, Automation.INTERNAL);
        }
        if (!cargo.getData().isEmpty()) {
            dataCargo.insert(cargo.getData(), Action.EXECUTE, Automation.INTERNAL);
        }
        cargo.clear();
    }

    /** Remplit les soutes de la fusee depuis le quai (objets, fluide, gaz, donnees), dans la limite de ses modules. */
    private void loadRocket(RocketEntity rocket) {
        List<ItemStack> modules = rocket.getModules();
        RocketCargo cargo = rocket.getCargo();
        int itemSlots = RocketCargo.itemSlots(modules);
        for (ItemSlot slot : cargoSlots) {
            if (slot.isEmpty()) {
                continue;
            }
            ItemStack moving = slot.extract(slot.getCount(), Action.SIMULATE, Automation.INTERNAL);
            int before = moving.getCount();
            for (int i = 0; i < itemSlots && !moving.isEmpty(); i++) {
                ItemStack target = cargo.getItems().get(i);
                if (target.isEmpty()) {
                    cargo.getItems().set(i, moving.copy());
                    moving = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameTags(target, moving) && target.getCount() < target.getMaxStackSize()) {
                    int added = Math.min(moving.getCount(), target.getMaxStackSize() - target.getCount());
                    target.grow(added);
                    moving.shrink(added);
                }
            }
            if (moving.getCount() != before) {
                slot.extract(before - moving.getCount(), Action.EXECUTE, Automation.INTERNAL);
            }
        }
        long fluidRoom = RocketCargo.fluidCapacity(modules) - cargo.getFluid().getAmount();
        if (fluidRoom > 0 && !fluidCargo.isEmpty() && (cargo.getFluid().isEmpty() || cargo.getFluid().isFluidEqual(fluidCargo.getFluid()))) {
            FluidStack moved = fluidCargo.drain((int) Math.min(fluidRoom, 4_000), IFluidHandler.FluidAction.EXECUTE);
            if (!moved.isEmpty()) {
                FluidStack merged = cargo.getFluid().isEmpty() ? moved : new FluidStack(cargo.getFluid(), cargo.getFluid().getAmount() + moved.getAmount());
                cargo.setFluid(merged);
            }
        }
        long gasRoom = RocketCargo.gasCapacity(modules) - cargo.getGas().getAmount();
        if (gasRoom > 0 && !gasCargo.getStack().isEmpty() && (cargo.getGas().isEmpty() || cargo.getGas().isSameGas(gasCargo.getStack()))) {
            GasStack moved = gasCargo.extract(Math.min(gasRoom, 4_000), Action.EXECUTE, Automation.INTERNAL);
            if (!moved.isEmpty()) {
                cargo.setGas(cargo.getGas().isEmpty() ? moved : cargo.getGas().copyWithAmount(cargo.getGas().getAmount() + moved.getAmount()));
            }
        }
        long dataRoom = RocketCargo.dataCapacity(modules) - cargo.getData().getAmount();
        if (dataRoom > 0 && !dataCargo.isEmpty() && (cargo.getData().isEmpty() || cargo.getData().isTypeEqual(dataCargo.getStack()))) {
            DataStack moved = dataCargo.extract(Math.min(dataRoom, 10_000), Action.EXECUTE, Automation.INTERNAL);
            if (!moved.isEmpty()) {
                cargo.setData(cargo.getData().isEmpty() ? moved : moved.copyWithAmount(cargo.getData().getAmount() + moved.getAmount()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Vol habite
    // ------------------------------------------------------------------

    /** Hydrogene demande pour un vol habite : le plein, moins les modules d'efficacite. */
    public static int mannedFuel(RocketTier tier, List<ItemStack> modules) {
        return (int) Math.ceil(tier.fuelCapacity * RocketModule.fuelMultiplier(modules) * PiCommonConfig.get(PiCommonConfig.ROCKET_FUEL, 1.0));
    }

    private Status tickManned(@Nullable RocketEntity rocket, @Nullable Size padSize) {
        countdown = 0;
        Status common = commonChecks(rocket, padSize);
        if (common != null) {
            return common;
        }
        fuelNeeded = mannedFuel(rocket.getTier(), rocket.getModules());
        if (rocket.isVehicle() || rocket.getState() != RocketEntity.State.IDLE) {
            return Status.BOARDED;
        } else if (countModule(RocketModule.PASSENGER) == 0) {
            return Status.NO_PASSENGER;
        } else if (rocket.getFuel() < fuelNeeded) {
            return wrongFuel ? Status.WRONG_FUEL : Status.FUELING;
        }
        return Status.READY_TO_BOARD;
    }

    @Nullable
    private Status commonChecks(@Nullable RocketEntity rocket, @Nullable Size padSize) {
        if (padSize == null) {
            return Status.NOT_FORMED;
        } else if (rocket == null) {
            return Status.NO_ROCKET;
        } else if (rocket.getTier().level > padSize.maxTier) {
            return Status.PAD_TOO_SMALL;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Sondes
    // ------------------------------------------------------------------

    /** Hydrogene necessaire pour mettre des sondes en orbite (reservoirs cryogeniques et modules compris). */
    public static int hydrogenFor(ProbeDestination target, List<ItemStack> modules) {
        return (int) Math.ceil(target.hydrogen * ResearchEffects.hydrogenMultiplier() * RocketModule.fuelMultiplier(modules));
    }

    /** Taux d'echec de mise en orbite, recherches et boucliers compris. */
    public static float failureFor(RocketTier tier, List<ItemStack> modules) {
        return (float) (tier.probeLaunchFailure() * ResearchEffects.launchFailureMultiplier() * RocketModule.failureMultiplier(modules));
    }

    /** Recherche d'instruments qu'il faut pour un tier de sonde (null pour le tier 1). */
    @Nullable
    public static ResearchNode probeResearch(int probeTier) {
        return switch (probeTier) {
            case 2 -> ResearchNode.PROBE_T2;
            case 3 -> ResearchNode.PROBE_T3;
            case 4 -> ResearchNode.PROBE_T4;
            case 5 -> ResearchNode.PROBE_T5;
            default -> null;
        };
    }

    private Status tickProbe(@Nullable RocketEntity rocket, @Nullable Size padSize) {
        Status current = probeStatus(rocket, padSize);
        if (countdown > 0) {
            if (current != Status.COUNTDOWN) {
                countdown = 0;
            } else if (--countdown == 0) {
                launchProbes(rocket);
                return Status.NO_ROCKET;
            } else {
                countdownEffects();
            }
        } else if (current == Status.COUNTDOWN) {
            //Les ameliorations de vitesse raccourcissent le compte a rebours (1 seconde au minimum)
            countdown = Math.max(20, COUNTDOWN_TICKS / (1 + speedUpgrades()));
        }
        return current;
    }

    private Status probeStatus(@Nullable RocketEntity rocket, @Nullable Size padSize) {
        ProbeDestination target = getDestination();
        Status common = commonChecks(rocket, padSize);
        if (common != null) {
            return common;
        }
        List<ItemStack> modules = rocket.getModules();
        fuelNeeded = hydrogenFor(target, modules);
        ResearchNode instruments = probeResearch(target.probeTier());
        if (rocket.isVehicle() || rocket.getState() != RocketEntity.State.IDLE) {
            return Status.BOARDED;
        } else if (RocketModule.count(modules, RocketModule.PROBE) == 0) {
            return Status.NO_PROBE;
        } else if (!ResearchEffects.isDestinationUnlocked(target)) {
            return Status.RESEARCH_LOCKED;
        } else if (instruments != null && !ResearchEffects.has(instruments)) {
            return Status.PROBE_LOCKED;
        } else if (!target.canLaunchWith(rocket.getTier())) {
            return Status.TIER_TOO_LOW;
        } else if (rocket.getFuel() < Math.min(rocket.getFuelCapacity(), fuelNeeded)) {
            return wrongFuel ? Status.WRONG_FUEL : Status.FUELING;
        } else if (!canWork()) {
            return Status.DISABLED;
        } else if (skyBusy()) {
            return Status.SKY_BLOCKED;
        }
        return Status.COUNTDOWN;
    }

    /** Lancement sans pilote : la fusee et les modules sonde partent ; l'issue est tiree des maintenant. */
    private void launchProbes(@Nullable RocketEntity rocket) {
        if (rocket == null) {
            return;
        }
        RocketTier tier = rocket.getTier();
        ProbeDestination target = getDestination();
        List<ItemStack> modules = rocket.getModules();
        int probes = RocketModule.count(modules, RocketModule.PROBE);
        //Les modules sonde partent avec la fusee ; les autres modules et la cargaison reviennent au quai
        for (ItemStack module : rocket.takeModules()) {
            if (RocketModule.of(module) != RocketModule.PROBE) {
                stash(module);
            }
        }
        stashCargo(rocket.getCargo());
        boolean success = level.random.nextFloat() >= failureFor(tier, modules);
        //0.18.0 : progres « premiere sonde » pour les joueurs proches
        IndustryEvents.award(level, worldPosition, "space_probe_launch");
        int explodeAt = success ? -1 : 40 + level.random.nextInt(LaunchVisualEntity.ASCENT_TICKS - 60);
        spawnVisual(tier, false, explodeAt, rocket.getYRot());
        rocket.discard();
        long done = level.getGameTime() + (success ? LaunchVisualEntity.ASCENT_TICKS : explodeAt);
        inFlight.add(new long[]{target.ordinal(), success ? probes : 0, done});
        launches++;
        setChanged();
    }

    private void resolveProbeFlights() {
        long now = level.getGameTime();
        Iterator<long[]> iterator = inFlight.iterator();
        while (iterator.hasNext()) {
            long[] flight = iterator.next();
            if (now >= flight[2]) {
                iterator.remove();
                ProbeDestination target = ProbeDestination.byIndex((int) flight[0]);
                if (flight[1] > 0) {
                    for (int i = 0; i < flight[1]; i++) {
                        ProbeNetwork.get(level.getServer()).addProbe(target);
                    }
                    successes++;
                } else {
                    failures++;
                }
                probesAtDestination = ProbeNetwork.get(level.getServer()).getProbes(getDestination());
                setChanged();
            }
        }
    }

    // ------------------------------------------------------------------
    // Logistique (expedition)
    // ------------------------------------------------------------------

    private Status tickLogistics(CargoNetwork network, @Nullable RocketEntity rocket, @Nullable Size padSize, long now) {
        if (padSize == null) {
            countdown = 0;
            return Status.NOT_FORMED;
        }
        if (landing) {
            return Status.LANDING;
        }
        if (rocket == null) {
            countdown = 0;
            return stationId != null && network.countFlightsOf(stationId) > 0 ? Status.IN_FLIGHT : Status.NO_ROCKET;
        }
        if (rocket.getTier().level > padSize.maxTier) {
            countdown = 0;
            return Status.PAD_TOO_SMALL;
        }
        if (rocket.isVehicle() || rocket.getState() != RocketEntity.State.IDLE) {
            countdown = 0;
            return Status.BOARDED;
        }
        List<ItemStack> modules = rocket.getModules();
        if (!RocketModule.hasCargo(modules)) {
            countdown = 0;
            return Status.NO_CARGO_MODULE;
        }
        Station target = network.getStation(targetId);
        if (target == null || !target.receiver) {
            countdown = 0;
            return Status.NO_TARGET;
        }
        //Escale technique apres le retour de la fusee
        turnaround = (int) Math.max(0, (dockedAt + TURNAROUND_TICKS - now + 19) / 20);
        if (turnaround > 0) {
            countdown = 0;
            return Status.MAINTENANCE;
        }
        int fuel = routeFuel(level.dimension(), target.dimension, modules);
        Status ready;
        if (!canWork()) {
            ready = Status.DISABLED;
        } else if (!ruleSatisfied(now, rocket)) {
            ready = Status.WAITING_CARGO;
        } else if (rocket.getFuel() < Math.min(rocket.getFuelCapacity(), fuel)) {
            ready = wrongFuel ? Status.WRONG_FUEL : Status.FUELING;
        } else if (skyBusy()) {
            ready = Status.SKY_BLOCKED;
        } else {
            ready = Status.COUNTDOWN;
        }
        if (ready != Status.COUNTDOWN) {
            countdown = 0;
            return ready;
        }
        if (countdown == 0) {
            countdown = COUNTDOWN_TICKS;
        } else if (--countdown == 0) {
            launchLogistics(network, rocket, target, now);
            return Status.IN_FLIGHT;
        }
        countdownEffects();
        return Status.COUNTDOWN;
    }

    /** Hydrogene d'un trajet logistique (modules d'efficacite compris). */
    public static int routeFuel(ResourceKey<Level> from, ResourceKey<Level> to, List<ItemStack> modules) {
        return (int) Math.ceil(CargoRoutes.fuel(from, to) * RocketModule.fuelMultiplier(modules));
    }

    /** Duree d'un trajet logistique (modules de vitesse compris). */
    public static int routeTicks(ResourceKey<Level> from, ResourceKey<Level> to, List<ItemStack> modules) {
        return LaunchVisualEntity.ASCENT_TICKS + (int) Math.round(CargoRoutes.transitTicks(from, to) * RocketModule.timeMultiplier(modules))
               + LaunchVisualEntity.DESCENT_TICKS;
    }

    private boolean ruleSatisfied(long now, RocketEntity rocket) {
        double ratio = rocket.getCargo().fillRatio(rocket.getModules());
        return switch (getRule()) {
            case ASAP -> ratio > 0;
            case HALF -> ratio >= 0.5;
            case FULL -> ratio >= 0.999;
            case TIMER -> ratio > 0 && now - lastDeparture >= getTimerSeconds() * 20L;
            case REDSTONE -> ratio > 0 && level.hasNeighborSignal(worldPosition);
        };
    }

    private void launchLogistics(CargoNetwork network, RocketEntity rocket, Station target, long now) {
        List<ItemStack> modules = rocket.getModules();
        CompoundTag cargo = rocket.getCargo().save();
        CargoFlight flight = new CargoFlight(UUID.randomUUID(), stationId, stationId, target.id, RocketModule.dominantCargo(modules), cargo, now,
              now + routeTicks(level.dimension(), target.dimension, modules) - LaunchVisualEntity.DESCENT_TICKS, false);
        flight.tier = rocket.getTier().ordinal();
        flight.modules = saveStacks(rocket.takeModules());
        rocket.getCargo().clear();
        network.addFlight(flight);
        spawnVisual(rocket.getTier(), false, -1, rocket.getYRot());
        rocket.discard();
        lastDeparture = now;
        sent++;
        setChanged();
    }

    // ------------------------------------------------------------------
    // Reception
    // ------------------------------------------------------------------

    private Status tickReceiver(CargoNetwork network, long now) {
        if (getSize() == null) {
            countdown = 0;
            return Status.NOT_FORMED;
        }
        if (landing) {
            return Status.LANDING;
        }
        if (visitorCargo == null) {
            countdown = 0;
            return findRocket() != null || !rocketSlot.isEmpty() ? Status.PAD_OCCUPIED : Status.READY;
        }
        //Dechargement progressif
        if (!isCargoEmpty(visitorCargo)) {
            boolean moved = unloadStep(now);
            if (now % 4 == 0) {
                unloadEffects();
            }
            return moved ? Status.UNLOADING : Status.BUFFER_FULL;
        }
        //Plein pour le retour
        Station home = network.getStation(visitorHome);
        List<ItemStack> modules = loadStacks(visitorModules);
        if (home == null) {
            //Complexe d'attache detruit : la fusee et ses modules restent ici, rendus en objets
            dropVisitor();
            return Status.READY;
        }
        turnaround = (int) Math.max(0, (dockedAt + TURNAROUND_TICKS - now + 19) / 20);
        if (turnaround > 0) {
            countdown = 0;
            return Status.MAINTENANCE;
        }
        int fuel = routeFuel(level.dimension(), home.dimension, modules);
        fuelNeeded = fuel;
        if (fuelTank.getFluidAmount() < fuel) {
            countdown = 0;
            return Status.REFUELING;
        }
        if (countdown == 0) {
            countdown = COUNTDOWN_TICKS;
        } else if (--countdown == 0) {
            fuelTank.drain(fuel, IFluidHandler.FluidAction.EXECUTE);
            spawnVisual(RocketTier.byIndex(visitorTier), false, -1, 0);
            CargoFlight flight = new CargoFlight(UUID.randomUUID(), visitorHome, stationId, visitorHome, RocketModule.dominantCargo(modules), new CompoundTag(),
                  now, now + routeTicks(level.dimension(), home.dimension, modules) - LaunchVisualEntity.DESCENT_TICKS, true);
            flight.tier = visitorTier;
            flight.modules = visitorModules;
            network.addFlight(flight);
            clearVisitor();
            return Status.READY;
        }
        countdownEffects();
        return Status.COUNTDOWN;
    }

    private void clearVisitor() {
        visitorCargo = null;
        visitorHome = null;
        visitorModules = new ListTag();
        setChanged();
        syncNow();
    }

    private void dropVisitor() {
        drop(ItemRocket.withContents(RocketTier.byIndex(visitorTier), loadStacks(visitorModules), RocketCargo.read(visitorCargo)));
        clearVisitor();
    }

    private void drop(ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, stack));
    }

    private static boolean isCargoEmpty(CompoundTag cargo) {
        return RocketCargo.read(cargo).isEmpty();
    }

    /** Decharge un peu de la fusee en visite (objets, puis fluide, gaz et donnees). Retourne faux si le quai est plein. */
    private boolean unloadStep(long now) {
        RocketCargo cargo = RocketCargo.read(visitorCargo);
        boolean moved;
        int itemIndex = -1;
        for (int i = 0; i < cargo.getItems().size(); i++) {
            if (!cargo.getItems().get(i).isEmpty()) {
                itemIndex = i;
                break;
            }
        }
        if (itemIndex >= 0) {
            if (now % 4 != 0) {
                return true;
            }
            ItemStack stack = cargo.getItems().get(itemIndex);
            int before = stack.getCount();
            for (ItemSlot slot : cargoSlots) {
                stack = slot.insert(stack, Action.EXECUTE, Automation.INTERNAL);
                if (stack.isEmpty()) {
                    break;
                }
            }
            cargo.getItems().set(itemIndex, stack);
            moved = stack.isEmpty() || stack.getCount() != before;
        } else if (!cargo.getFluid().isEmpty()) {
            FluidStack fluid = cargo.getFluid();
            FluidStack part = new FluidStack(fluid, Math.min(1_000, fluid.getAmount()));
            int count = fluidCargo.fill(part, IFluidHandler.FluidAction.EXECUTE);
            cargo.setFluid(new FluidStack(fluid, fluid.getAmount() - count));
            moved = count > 0;
        } else if (!cargo.getGas().isEmpty()) {
            GasStack gas = cargo.getGas();
            GasStack part = gas.copyWithAmount(Math.min(1_000, gas.getAmount()));
            GasStack rest = gasCargo.insert(part, Action.EXECUTE, Automation.INTERNAL);
            long count = part.getAmount() - rest.getAmount();
            cargo.setGas(gas.copyWithAmount(gas.getAmount() - count));
            moved = count > 0;
        } else {
            DataStack data = cargo.getData();
            DataStack part = data.copyWithAmount(Math.min(5_000, data.getAmount()));
            DataStack rest = dataCargo.insert(part, Action.EXECUTE, Automation.INTERNAL);
            long count = part.getAmount() - rest.getAmount();
            cargo.setData(data.copyWithAmount(data.getAmount() - count));
            moved = count > 0;
        }
        visitorCargo = cargo.save();
        setChanged();
        return moved;
    }

    /** Reception : la soute se vide d'elle-meme dans les tuyaux et inventaires voisins. */
    private void pushOutputs() {
        for (Direction direction : Directions.ALL) {
            BlockPos target = worldPosition.relative(direction);
            BlockState state = level.getBlockState(target);
            if (LaunchPadStructure.isPlate(state) || LaunchTowerBlock.is(state) || direction == Direction.UP) {
                continue;
            }
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            BlockEntity tile = ClanApi.canTransfer(level, worldPosition, target) ? level.getBlockEntity(target) : null;
            if (tile == null) {
                continue;
            }
            Direction side = direction.getOpposite();
            tile.getCapability(ForgeCapabilities.ITEM_HANDLER, side).ifPresent(handler -> {
                for (ItemSlot slot : cargoSlots) {
                    if (!slot.isEmpty()) {
                        ItemStack stack = slot.extract(64, Action.SIMULATE, Automation.INTERNAL);
                        ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                        int moved = stack.getCount() - rest.getCount();
                        if (moved > 0) {
                            slot.extract(moved, Action.EXECUTE, Automation.INTERNAL);
                        }
                    }
                }
            });
            tile.getCapability(ForgeCapabilities.FLUID_HANDLER, side).ifPresent(handler -> {
                if (!fluidCargo.isEmpty()) {
                    int filled = handler.fill(new FluidStack(fluidCargo.getFluid(), Math.min(4_000, fluidCargo.getFluidAmount())), IFluidHandler.FluidAction.EXECUTE);
                    if (filled > 0) {
                        fluidCargo.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            });
            IGasHandler gases = GasAccess.find(tile, side);
            if (gases != null && !gasCargo.getStack().isEmpty()) {
                GasStack offer = gasCargo.getStack().copyWithAmount(Math.min(4_000, gasCargo.getStack().getAmount()));
                GasStack rest = gases.insertGas(offer, Action.EXECUTE);
                long moved = offer.getAmount() - rest.getAmount();
                if (moved > 0) {
                    gasCargo.extract(moved, Action.EXECUTE, Automation.INTERNAL);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Arrivees (appelees par le reseau logistique)
    // ------------------------------------------------------------------

    /** Peut-on accueillir cette fusee maintenant ? (une seule a la fois, pas de tir libre) */
    public boolean canAcceptLanding(CargoFlight flight) {
        if (getSize() == null || landing || findRocket() != null || skyBusy()) {
            return false;
        }
        if (flight.returning) {
            return flight.home.equals(stationId) && rocketSlot.isEmpty();
        }
        return isReceiver() && visitorCargo == null && rocketSlot.isEmpty();
    }

    public void beginLanding(CargoFlight flight) {
        landing = true;
        spawnVisual(RocketTier.byIndex(flight.tier), true, -1, 0);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 3.0F, 0.5F);
        setChanged();
    }

    public void completeLanding(CargoFlight flight) {
        landing = false;
        dockedAt = level.getGameTime();
        if (flight.returning) {
            //Retour a la maison : la fusee (avec ses modules, soutes vides) reprend sa case et sera redressee sur le pas de tir
            ItemStack rocket = ItemRocket.withModules(RocketTier.byIndex(flight.tier), loadStacks(flight.modules));
            if (!rocketSlot.insert(rocket, Action.EXECUTE, Automation.INTERNAL).isEmpty()) {
                drop(rocket);
            }
        } else {
            visitorCargo = flight.cargo;
            visitorHome = flight.home;
            visitorTier = flight.tier;
            visitorModules = flight.modules;
            clientCargoColor = flight.type.color;
            received++;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, 30, 1.2, 0.1, 1.2, 0.05);
        }
        setChanged();
        syncNow();
    }

    // ------------------------------------------------------------------
    // Effets
    // ------------------------------------------------------------------

    private void spawnVisual(RocketTier tier, boolean descending, int explodeAt, float yaw) {
        LaunchVisualEntity visual = PiEntityTypes.LAUNCH_VISUAL.get().create(level);
        if (visual == null) {
            return;
        }
        double y = worldPosition.getY() + 1 + (descending ? LaunchVisualEntity.DESCENT_HEIGHT : 0);
        visual.moveTo(worldPosition.getX() + 0.5, y, worldPosition.getZ() + 0.5, yaw, 0);
        visual.setup(tier.ordinal(), descending, explodeAt);
        level.addFreshEntity(visual);
        if (!descending) {
            level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.5F);
        }
    }

    private void countdownEffects() {
        if (level instanceof ServerLevel serverLevel && countdown % 4 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, 8, 0.8, 0.05, 0.8, 0.08);
            serverLevel.sendParticles(ParticleTypes.FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5, 4, 0.2, 0, 0.2, 0.01);
        }
    }

    private void unloadEffects() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, worldPosition.getX() + 0.5, worldPosition.getY() + 3.5, worldPosition.getZ() + 0.5, 4, 0.5, 1.2, 0.5, 0.02);
        }
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    /** Destination courante, carburant et duree du trajet (pour l'interface). */
    private void refreshRoute(CargoNetwork network) {
        String label = "";
        if (getMission() == Mission.LOGISTICS) {
            Station target = network.getStation(targetId);
            if (target != null && target.receiver) {
                List<ItemStack> modules = getActiveModules();
                fuelNeeded = routeFuel(level.dimension(), target.dimension, modules);
                etaSeconds = routeTicks(level.dimension(), target.dimension, modules) / 20;
                var body = CargoRoutes.body(target.dimension);
                label = target.name + (body == null ? "" : "|" + body.getTranslationKey());
            } else {
                etaSeconds = 0;
            }
        } else if (getMission() == Mission.RECEIVE && visitorHome != null) {
            Station home = network.getStation(visitorHome);
            if (home != null) {
                label = home.name;
            }
        }
        if (!label.equals(targetLabel)) {
            targetLabel = label;
            syncNow();
        }
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        CargoNetwork network = CargoNetwork.get(player.server);
        switch (action) {
            case ACTION_MISSION -> {
                //On ne change de mission que si aucune fusee n'est en route ni en visite
                boolean busy = landing || visitorCargo != null || stationId != null && network.countFlightsOf(stationId) > 0;
                if (!busy) {
                    mission = (mission + 1) % Mission.values().length;
                    countdown = 0;
                }
            }
            case ACTION_PREVIOUS, ACTION_NEXT -> {
                if (countdown > 0) {
                    break;
                }
                int step = action == ACTION_NEXT ? 1 : -1;
                if (getMission() == Mission.PROBE) {
                    destination = Math.floorMod(destination + step, ProbeDestination.values().length);
                    probesAtDestination = ProbeNetwork.get(player.server).getProbes(getDestination());
                } else if (getMission() == Mission.LOGISTICS && stationId != null) {
                    List<Station> targets = network.receiversFor(stationId);
                    if (targets.isEmpty()) {
                        targetId = null;
                    } else {
                        int index = -1;
                        for (int i = 0; i < targets.size(); i++) {
                            if (targets.get(i).id.equals(targetId)) {
                                index = i;
                            }
                        }
                        targetId = targets.get(Math.floorMod(index + step, targets.size())).id;
                    }
                }
            }
            case ACTION_RULE -> rule = (rule + 1) % Rule.values().length;
            case ACTION_TIMER -> timerIndex = (timerIndex + 1) % TIMER_SECONDS.length;
            default -> {
            }
        }
        refreshRoute(network);
        setChanged();
        syncNow();
    }

    // ------------------------------------------------------------------
    // Valeurs d'interface
    // ------------------------------------------------------------------

    /** API Machine (lot 8b) : valeurs NOMMEES aux indices d'avant (corps propre de la maison dans la vue {@code house}). */
    public static final UiValues.Key MISSION = UiValues.Key.of("mission"), SIZE = UiValues.Key.of("size"), DESTINATION = UiValues.Key.of("destination"), RULE = UiValues.Key.of("rule"), TIMER = UiValues.Key.of("timer"), STATUS = UiValues.Key.of("status"), COUNTDOWN = UiValues.Key.of("countdown"), LAUNCHES = UiValues.Key.of("launches"), SUCCESSES = UiValues.Key.of("successes"), FAILURES = UiValues.Key.of("failures"), SENT = UiValues.Key.of("sent"), RECEIVED = UiValues.Key.of("received"), PROBES_AT = UiValues.Key.of("probes_at"), ROCKET_TIER = UiValues.Key.of("rocket_tier"), ROCKET_FUEL = UiValues.Key.of("rocket_fuel"), ROCKET_CAPACITY = UiValues.Key.of("rocket_capacity"), FUEL_NEEDED = UiValues.Key.of("fuel_needed"), ETA = UiValues.Key.of("eta"), TURNAROUND = UiValues.Key.of("turnaround"), FILL = UiValues.Key.of("fill");

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        UiValues.Key[] keys = {MISSION, SIZE, DESTINATION, RULE, TIMER, STATUS, COUNTDOWN, LAUNCHES, SUCCESSES, FAILURES, SENT, RECEIVED, PROBES_AT, ROCKET_TIER, ROCKET_FUEL, ROCKET_CAPACITY, FUEL_NEEDED, ETA, TURNAROUND, FILL};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            values.add(keys[i], () -> machineValue(index));
        }
    }

    @Override
    protected int machineValueCount() {
        return 20;
    }

    @Override
    protected long machineValue(int index) {
        return switch (index) {
            case 0 -> mission;
            case 1 -> size;
            case 2 -> destination;
            case 3 -> rule;
            case 4 -> getTimerSeconds();
            case 5 -> status;
            case 6 -> countdown;
            case 7 -> launches;
            case 8 -> successes;
            case 9 -> failures;
            case 10 -> sent;
            case 11 -> received;
            case 12 -> probesAtDestination;
            case 13 -> rocketTier;
            case 14 -> rocketFuel;
            case 15 -> rocketCapacity;
            case 16 -> fuelNeeded;
            case 17 -> etaSeconds;
            case 18 -> turnaround;
            default -> rocketFill;
        };
    }

    public void setStationName(String newName) {
        String trimmed = newName.trim();
        if (!trimmed.isEmpty() && trimmed.length() <= STATION_NAME_LENGTH) {
            name = trimmed;
            setChanged();
            syncNow();
            if (level instanceof ServerLevel serverLevel && stationId != null) {
                CargoNetwork.get(serverLevel.getServer()).updateStation(stationId, level.dimension(), worldPosition, name, isReceiver());
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        //Dechargement normal du monde : le complexe reste dans le reseau
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        house.removed(true);
        //Bloc casse : le complexe quitte le reseau (les fusees en route vers lui font demi-tour)
        if (!unloading && level instanceof ServerLevel serverLevel && stationId != null) {
            CargoNetwork.get(serverLevel.getServer()).removeStation(stationId);
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------
    // Accesseurs
    // ------------------------------------------------------------------

    public Mission getMission() {
        return Mission.values()[Math.floorMod(mission, Mission.values().length)];
    }

    public boolean isReceiver() {
        return getMission() == Mission.RECEIVE;
    }

    @Nullable
    public Size getSize() {
        return Size.byIndex(size);
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

    public ProbeDestination getDestination() {
        return ProbeDestination.byIndex(destination);
    }

    public Status getStatus() {
        return Status.values()[Math.max(0, Math.min(status, Status.values().length - 1))];
    }

    public Rule getRule() {
        return Rule.values()[Math.floorMod(rule, Rule.values().length)];
    }

    public int getTimerSeconds() {
        return TIMER_SECONDS[Math.floorMod(timerIndex, TIMER_SECONDS.length)];
    }

    @Nullable
    public RocketTier getRocketTier() {
        return rocketTier < 0 ? null : RocketTier.byIndex(rocketTier);
    }

    public BasicDataTank getDataCargo() {
        return dataCargo;
    }

    public List<ItemSlot> getDockSlots() {
        return cargoSlots;
    }

    public ItemSlot getRocketSlot() {
        return rocketSlot;
    }

    public String getStationName() {
        return name;
    }

    /** "Nom|cle de l'astre" ou "" (cote client). */
    public String getTargetLabel() {
        return targetLabel;
    }

    public int getClientVisitor() {
        return clientVisitor;
    }

    public boolean isClientCountdown() {
        return clientCountdown;
    }

    public boolean isClientBusy() {
        return clientBusy;
    }

    public int getClientCargoColor() {
        return clientCargoColor;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-4, 0, -4), worldPosition.offset(5, 20, 5));
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    private static ListTag saveStacks(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                list.add(stack.save(new CompoundTag()));
            }
        }
        return list;
    }

    private static List<ItemStack> loadStacks(ListTag list) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Tag tag : list) {
            ItemStack stack = ItemStack.of((CompoundTag) tag);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        tag.putString("name", name);
        tag.putString("target", targetLabel);
        tag.putInt("visitor", visitorCargo != null && !landing ? visitorTier : -1);
        tag.putBoolean("countdown", countdown > 0);
        tag.putBoolean("busy", clientBusy);
        tag.putInt("cargoColor", clientCargoColor);
        tag.putInt("mission", mission);
        tag.putInt("rocketTier", rocketTier);
        tag.put("rocketModules", saveStacks(rocketModules));
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        name = tag.getString("name");
        targetLabel = tag.getString("target");
        clientVisitor = tag.getInt("visitor");
        clientCountdown = tag.getBoolean("countdown");
        clientBusy = tag.getBoolean("busy");
        clientCargoColor = tag.getInt("cargoColor");
        mission = tag.getInt("mission");
        rocketTier = tag.getInt("rocketTier");
        rocketModules = loadStacks(tag.getList("rocketModules", Tag.TAG_COMPOUND));
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);
        stationId = nbt.hasUUID("station") ? nbt.getUUID("station") : null;
        targetId = nbt.hasUUID("target") ? nbt.getUUID("target") : null;
        name = nbt.getString("name");
        mission = nbt.getInt("mission");
        destination = nbt.getInt("destination");
        rule = nbt.getInt("rule");
        timerIndex = nbt.contains("timer") ? nbt.getInt("timer") : 1;
        launches = nbt.getInt("launches");
        successes = nbt.getInt("successes");
        failures = nbt.getInt("failures");
        sent = nbt.getInt("sent");
        received = nbt.getInt("received");
        lastDeparture = nbt.getLong("lastDeparture");
        dockedAt = nbt.contains("dockedAt") ? nbt.getLong("dockedAt") : -TURNAROUND_TICKS;
        visitorCargo = nbt.contains("visitorCargo") ? nbt.getCompound("visitorCargo") : null;
        visitorHome = nbt.hasUUID("visitorHome") ? nbt.getUUID("visitorHome") : null;
        visitorTier = nbt.getInt("visitorTier");
        visitorModules = nbt.getList("visitorModules", Tag.TAG_COMPOUND);
        clientCargoColor = nbt.contains("cargoColor") ? nbt.getInt("cargoColor") : CargoType.ITEMS.color;
        landing = nbt.getBoolean("landing");
        inFlight.clear();
        long[] flights = nbt.getLongArray("inFlight");
        for (int i = 0; i + 2 < flights.length; i += 3) {
            //Recharge : les vols en cours se terminent au prochain tick
            inFlight.add(new long[]{flights[i], flights[i + 1], 0});
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag nbtTags) {
        super.saveAdditional(nbtTags);
        if (stationId != null) {
            nbtTags.putUUID("station", stationId);
        }
        if (targetId != null) {
            nbtTags.putUUID("target", targetId);
        }
        nbtTags.putString("name", name);
        nbtTags.putInt("mission", mission);
        nbtTags.putInt("destination", destination);
        nbtTags.putInt("rule", rule);
        nbtTags.putInt("timer", timerIndex);
        nbtTags.putInt("launches", launches);
        nbtTags.putInt("successes", successes);
        nbtTags.putInt("failures", failures);
        nbtTags.putInt("sent", sent);
        nbtTags.putInt("received", received);
        nbtTags.putLong("lastDeparture", lastDeparture);
        nbtTags.putLong("dockedAt", dockedAt);
        if (visitorCargo != null) {
            nbtTags.put("visitorCargo", visitorCargo);
        }
        if (visitorHome != null) {
            nbtTags.putUUID("visitorHome", visitorHome);
        }
        nbtTags.putInt("visitorTier", visitorTier);
        nbtTags.put("visitorModules", visitorModules);
        nbtTags.putInt("cargoColor", clientCargoColor);
        nbtTags.putBoolean("landing", landing);
        long[] flights = new long[inFlight.size() * 3];
        for (int i = 0; i < inFlight.size(); i++) {
            System.arraycopy(inFlight.get(i), 0, flights, i * 3, 3);
        }
        nbtTags.putLongArray("inFlight", flights);
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
