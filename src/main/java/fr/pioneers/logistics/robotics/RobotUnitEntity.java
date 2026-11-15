package fr.pioneers.logistics.robotics;

import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.territory.ClanAgents;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.FishItems;
import fr.pioneers.farm.fish.FishSpawns;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.core.network.PacketRobotUnit;
import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.track.TrackBlock;
import fr.pioneers.logistics.rail.track.TrackPlacement;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.hub.RoboticHubData;
import fr.pioneers.logistics.robotics.hub.TileEntityRoboticBay;
import fr.pioneers.logistics.robotics.logistics.LogisticNetwork;
import fr.pioneers.logistics.robotics.logistics.LogisticPoint;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import fr.pioneers.logistics.robotics.logistics.NetworkNode;
import fr.pioneers.logistics.robotics.logistics.TileEntityLogisticTerminal;
import fr.pioneers.logistics.robotics.works.WorksData;
import fr.pioneers.logistics.robotics.works.WorksPlan;
import fr.pioneers.space.farm.HydroponicSubstrateBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unite robotique en mission (drone, robot ou rover). Elle sort d'un hub, fait son travail (tournee de livraisons, ou tache de zone avec
 * ses fournitures), enchaine directement le travail suivant du reseau, depose sa cargaison dans un stockage, se recharge a un relais
 * quand sa batterie baisse, et attend pres d'un relais entre deux missions avant de rentrer.
 * <p>
 * Deplacements sans blocage :
 * <ul>
 *     <li>les drones volent en ligne droite au-dessus du relief (altitude de croisiere d'apres la carte des hauteurs) et traversent ce
 *     qui depasse ;</li>
 *     <li>les unites au sol avancent par etapes courtes (chemins courts, fiables), contournent un obstacle par des etapes de cote,
 *     sautent, puis, coincees, s'extraient par un bond propulse ; un point qu'elles n'atteignent pas est confie aux drones ; pour
 *     rentrer ou se recharger, elles finissent au besoin en vol propulse.</li>
 * </ul>
 * L'unite reste un objet du hub pendant sa sortie : si le hub disparait, elle se dissout simplement. Les unites ne se poussent pas
 * entre elles. Invulnerables ; un joueur en creatif peut en renvoyer une a son hub d'un coup. Non sauvegardees : une unite dechargee
 * rentre aussitot.
 */
public class RobotUnitEntity extends PathfinderMob {

    /** RESTING : mode repos des tortues (elles dorment sur leur zone de travail, sans consommer). */
    public enum Phase {GOING, WORKING, DEPOSIT, CHARGING, IDLE, RETURNING, RESTING}

    private enum Move {MOVING, ARRIVED, FAILED}

    private static final EntityDataAccessor<Byte> TIER = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> TASK = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> WORKING = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> BEAM = SynchedEntityData.defineId(RobotUnitEntity.class,
          EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<ItemStack> SHOWN = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> FILL = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.FLOAT);
    /** 0 : rien, 1 : en charge, 2 : bond ou vol propulse. */
    private static final EntityDataAccessor<Byte> FX = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(RobotUnitEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double IDLE_FE = 1, LOW_BATTERY = 0.2, FULL_BATTERY = 0.95;
    private static final int IDLE_TIMEOUT = 1_200, WAYPOINT = 32;

    private final UnitKind kind;
    @Nullable
    private BlockPos home;
    private int slot = -1;
    private UnitCargo cargo;
    private UnitStats stats = UnitStats.BASE;
    private long battery, maxBattery = 1;
    @Nullable
    private Job job;
    private Phase phase = Phase.GOING;
    /** Phase a reprendre apres une recharge. */
    @Nullable
    private Phase resume;
    /** Arret en cours d'une tournee (ou des fournitures). */
    private int stop;
    private int workTimer, treeIndex, idleTicks;
    /** A-t-elle fini au moins un travail (compteur du hub) ? */
    private boolean completed;
    @Nullable
    private Endpoint deposit;
    private int depositTries;
    @Nullable
    private NetworkNode charger;
    private int chargerWait;
    private double baseSpeed = 0.25;
    private Vec3 lastPos = Vec3.ZERO;

    //Deplacement
    @Nullable
    private Vec3 moveTarget;
    private double cruiseY = Double.NaN;
    private int cruiseAge;
    /** Vol propulse (unites au sol) : pour rentrer ou se recharger quand le chemin est introuvable. */
    private boolean jets;

    /** Mode repos (tortues) : la zone ou elle campe (-1 : aucune) et sa place pour dormir. */
    private int restOrder = -1;
    @Nullable
    private Vec3 restSpot;

    public RobotUnitEntity(EntityType<? extends RobotUnitEntity> type, Level level, UnitKind kind) {
        super(type, level);
        this.kind = kind;
        this.cargo = new UnitCargo(kind.stacks, kind.fluid);
        setPersistenceRequired();
        setMaxUpStep(1.0F);
        if (kind.flying) {
            setNoGravity(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FLYING_SPEED, 0.5)
              .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        //Portillons et portes : ouvrables (l'unite les ouvre en passant et les referme derriere elle)
        GroundPathNavigation navigation = new GroundPathNavigation(this, level) {
            @NotNull
            @Override
            protected PathFinder createPathFinder(int maxVisitedNodes) {
                nodeEvaluator = new GateNodeEvaluator();
                nodeEvaluator.setCanPassDoors(true);
                nodeEvaluator.setCanOpenDoors(true);
                return new PathFinder(nodeEvaluator, maxVisitedNodes);
            }
        };
        navigation.setCanOpenDoors(true);
        navigation.setCanFloat(true);
        navigation.setMaxVisitedNodesMultiplier(4);
        return navigation;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TIER, (byte) 0);
        entityData.define(TASK, (byte) 0);
        entityData.define(WORKING, false);
        entityData.define(BEAM, Optional.empty());
        entityData.define(SHOWN, ItemStack.EMPTY);
        entityData.define(FILL, 0F);
        entityData.define(BATTERY, 1F);
        entityData.define(FX, (byte) 0);
        entityData.define(SLEEPING, false);
    }

    /** Sortie du hub : porte d'attache, emplacement de l'unite, tier, tache, ameliorations du hub et batterie. */
    public void setup(BlockPos bay, int slot, UnitTier tier, RobotTask task, UnitStats stats, long maxBattery, long battery) {
        this.home = bay.immutable();
        this.slot = slot;
        this.stats = stats;
        this.maxBattery = Math.max(1, maxBattery);
        this.battery = Math.min(maxBattery, battery);
        entityData.set(TIER, (byte) tier.ordinal());
        setTask(task);
        double factor = tier.capacity * stats.cargo();
        int stacks = (int) Math.max(1, Math.round(kind.stacks * factor));
        int tank = (int) Math.round(kind.fluid * factor);
        cargo = new UnitCargo(stacks, tank, Math.round(kind.fluid * 100L * factor));
        baseSpeed = 0.32 * kind.speed * tier.speed * stats.speed();
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed);
        setDropChance(EquipmentSlot.MAINHAND, 0);
        lastPos = position();
    }

    private void setTask(RobotTask task) {
        entityData.set(TASK, (byte) task.ordinal());
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(task.tool));
    }

    /** Nouveau travail (au depart, ou en enchainant). */
    public void assign(Job job) {
        if (level() instanceof ServerLevel server) {
            RoboticsScheduler.setIdle(server, this, false);
        }
        leaveCharger();
        setSleeping(false);
        if (job.group != restOrder) {
            //Un autre travail : elle quitte sa zone de repos
            restOrder = -1;
            restSpot = null;
        }
        this.job = job;
        this.stop = 0;
        //Fournitures deja a bord (graines, nourriture, pousses) : inutile de passer les prendre
        if (job.type != Job.Type.TRIP) {
            while (stop < job.stops.size() && job.stops.get(stop).load() && cargoCount(job.stops.get(stop).res()) >= Math.min(2, job.stops.get(stop).amount())) {
                stop++;
            }
        }
        this.dockDeposit = false;
        this.treeIndex = 0;
        this.lootAt = null;
        this.phase = Phase.GOING;
        this.resume = null;
        resetMove();
        setTask(job.task);
    }

    // ------------------------------------------------------------------
    // Acces
    // ------------------------------------------------------------------

    public UnitKind getKind() {
        return kind;
    }

    public int getSlot() {
        return slot;
    }

    public UnitTier getTier() {
        return UnitTier.byIndex(entityData.get(TIER));
    }

    public RobotTask getTask() {
        return RobotTask.byIndex(entityData.get(TASK));
    }

    public UnitStats getStats() {
        return stats;
    }

    public long getBattery() {
        return battery;
    }

    public long getMaxBattery() {
        return maxBattery;
    }

    public double batteryRatio() {
        return battery / (double) maxBattery;
    }

    public int getEffect() {
        return entityData.get(FX);
    }

    public Phase getPhase() {
        return phase;
    }

    /** Disponible pour un travail : en attente, ou endormie sur sa zone (mode repos). */
    public boolean isIdle() {
        return phase == Phase.IDLE || phase == Phase.RESTING;
    }

    /** Zone de repos (mode repos des tortues), ou -1 : une tortue endormie n'accepte que les travaux de sa zone. */
    public int getRestOrder() {
        return phase == Phase.RESTING ? restOrder : -1;
    }

    /** Endormie (rendu : pose de sommeil, « z »). */
    public boolean isSleeping() {
        return entityData.get(SLEEPING);
    }

    private void setSleeping(boolean sleeping) {
        if (entityData.get(SLEEPING) != sleeping) {
            entityData.set(SLEEPING, sleeping);
        }
    }

    public boolean isWorking() {
        return entityData.get(WORKING);
    }

    public Optional<BlockPos> getBeam() {
        return entityData.get(BEAM);
    }

    /** Objet montre dans la soute (rendu). */
    public ItemStack getShownCargo() {
        return entityData.get(SHOWN);
    }

    public float getCargoFill() {
        return entityData.get(FILL);
    }

    public UnitCargo getCargo() {
        return cargo;
    }

    /** Ou va l'unite (carte), ou null. */
    @Nullable
    public Vec3 getMoveTarget() {
        return moveTarget;
    }

    @Nullable
    public Job getJob() {
        return job;
    }

    /** Le hub de l'unite, s'il est forme et la compte bien dehors. */
    @Nullable
    public RoboticHubData getHubData() {
        if (home == null || !level().isLoaded(home)) {
            return null;
        }
        if (level().getBlockEntity(home) instanceof TileEntityRoboticBay bay) {
            RoboticHubData data = bay.getHub();
            if (data != null && data.isOut(this)) {
                return data;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Comportement
    // ------------------------------------------------------------------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (!kind.flying) {
            passGates(server);
            swim();
        }
        RoboticHubData hub = getHubData();
        if (hub == null) {
            //Hub defait, detruit ou decharge : l'unite se dissout (elle est toujours dans le hub), sa soute tombe
            dissolve(server);
            return;
        }
        if (!isSleeping()) {
            //Une tortue endormie ne consomme rien
            drain(hub);
        }
        if (hub.isRecalling() && phase != Phase.RETURNING) {
            restOrder = -1;
            setSleeping(false);
            dropJob(server, false);
            leaveCharger();
            setIdleState(server, false);
            phase = Phase.RETURNING;
        }
        //Batterie basse : on va se recharger, puis on reprend
        //Une unite en attente sous 35 % ne peut plus etre choisie : elle va se recharger tout de suite plutot que d'occuper une place
        boolean waitingLow = phase == Phase.IDLE && batteryRatio() < RoboticsScheduler.MIN_BATTERY;
        if ((batteryRatio() < LOW_BATTERY || waitingLow) && (phase == Phase.GOING || phase == Phase.DEPOSIT || phase == Phase.IDLE || phase == Phase.RESTING)) {
            setIdleState(server, false);
            setSleeping(false);
            resume = phase == Phase.IDLE || phase == Phase.RESTING ? null : phase;
            if (job == null) {
                //Sans travail en cours : sa place sur l'ordre est rendue pendant la recharge
                RoboticsScheduler.leave(server, this);
            }
            phase = Phase.CHARGING;
            resetMove();
        }
        updateSpeed(server);
        switch (phase) {
            case GOING -> going(server, hub);
            case WORKING -> working(server, hub);
            case DEPOSIT -> depositing(server, hub);
            case CHARGING -> charging(server, hub);
            case IDLE -> idling(server, hub);
            case RETURNING -> returning(server, hub);
            case RESTING -> resting(server, hub);
        }
        entityData.set(FILL, cargo.fill());
        if (tickCount % 10 == 0) {
            entityData.set(SHOWN, cargo.displayStack().copyWithCount(1));
            entityData.set(BATTERY, (float) batteryRatio());
        }
    }

    /** Consommation : deplacement, attente ; plus d'unites dehors, plus cher. */
    private void drain(RoboticHubData hub) {
        Vec3 pos = position();
        double moved = Math.min(4, pos.distanceTo(lastPos));
        lastPos = pos;
        double tierFactor = 1 + (getTier().energy - 1) * 0.5;
        double cost = (moved * kind.drainPerBlock + IDLE_FE) * tierFactor * stats.drain() * hub.crowdFactor();
        battery = Math.max(0, battery - Math.round(cost));
    }

    private void drainWork() {
        double tierFactor = 1 + (getTier().energy - 1) * 0.5;
        battery = Math.max(0, battery - Math.round(kind.drainPerWork * tierFactor * stats.workEnergy()));
    }

    /** Vitesse au sol : routes robotiques, batterie a plat. */
    private void updateSpeed(ServerLevel server) {
        if (kind.flying) {
            return;
        }
        double speed = baseSpeed;
        BlockState below = server.getBlockState(blockPosition().below());
        if (below.is(PiRobotics.ROAD.get())) {
            speed *= kind == UnitKind.ROVER ? 1.8 : 1.5;
        }
        if (battery <= 0) {
            speed *= 0.3;
        }
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
    }

    private void going(ServerLevel server, RoboticHubData hub) {
        setWorking(false, null);
        if (job == null) {
            afterJob(server, hub, null);
            return;
        }
        Vec3 target = workPoint(server);
        if (target == null) {
            RoboticsScheduler.abandon(server, job);
            job = null;
            afterJob(server, hub, null);
            return;
        }
        //Tir : des que la proie est a portee (et pas trop pres) et en vue, on tire sans finir le trajet
        if (shooting() && lootAt == null && server.getEntity(job.entityId) instanceof LivingEntity prey && prey.isAlive()) {
            double d2 = distanceToSqr(prey);
            if (d2 < (LASER_RANGE - 1) * (LASER_RANGE - 1) && d2 > TOO_CLOSE * TOO_CLOSE && hasLineOfSight(prey)) {
                getNavigation().stop();
                resetMove();
                phase = Phase.WORKING;
                workTimer = workDuration(server);
                return;
            }
        }
        Move move = travel(server, target, reach(), false);
        if (move == Move.ARRIVED) {
            getNavigation().stop();
            phase = Phase.WORKING;
            workTimer = workDuration(server);
        } else if (move == Move.FAILED) {
            //Inaccessible au sol : on le laisse aux drones
            RoboticsScheduler.groundUnreachable(server, BlockPos.containing(target));
            Job failed = job;
            job = null;
            if (failed.type == Job.Type.TRIP) {
                RoboticsScheduler.release(server, failed);
            } else {
                RoboticsScheduler.abandon(server, failed);
            }
            afterJob(server, hub, null);
        }
    }

    private boolean atStop() {
        return job != null && stop < job.stops.size();
    }

    /** Tir au laser a distance (chasse, elimination du surplus) : on reste loin des monstres (creepers...). */
    private boolean shooting() {
        return job != null && !atStop() && job.type == Job.Type.ENTITY && (getTask() == RobotTask.HUNTER || job.cull);
    }

    private static final double LASER_RANGE = 18;
    /** Distance de tir : assez loin des coups (et des explosions de creeper). */
    private static final double SHOOT_DISTANCE = 13, TOO_CLOSE = 8;
    /** Portee du rayon de travail (minage, travaux) : jusqu'a 4 blocs du bloc vise. */
    private static final double BEAM_REACH = 4.0;

    private double reach() {
        if (shooting()) {
            //Le point de tir est deja a bonne distance de la proie
            return lootAt != null ? 2.0 : 2.5;
        }
        if (job != null && !atStop() && job.type == Job.Type.BLOCK && getTask().usesBeam()) {
            return BEAM_REACH;
        }
        //Bras des unites au sol : un coffre au-dessus d'une machine reste accessible
        return kind.flying ? 1.6 : 2.6;
    }

    /** Ou se placer pour travailler, ou null si le travail n'existe plus. */
    @Nullable
    private Vec3 workPoint(ServerLevel server) {
        if (job == null) {
            return null;
        }
        if (atStop()) {
            return lift(job.stops.get(stop).at().accessPoint());
        }
        switch (job.type) {
            case ENTITY -> {
                Entity entity = server.getEntity(job.entityId);
                if ((entity == null || !entity.isAlive()) && lootAt != null) {
                    //Proie abattue : on va ramasser son butin
                    return lootAt.add(0, kind.flying ? 1.2 : 0, 0);
                }
                if (entity == null || !entity.isAlive()) {
                    return null;
                }
                if (shooting()) {
                    //Tir : on se poste a distance de la proie, jamais au contact
                    Vec3 away = position().subtract(entity.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 0.01) {
                        away = new Vec3(1, 0, 0);
                    }
                    return entity.position().add(away.normalize().scale(SHOOT_DISTANCE)).add(0, kind.flying ? 2.5 : 0, 0);
                }
                return entity.position().add(0, kind.flying ? 1.2 : 0, 0);
            }
            case BLOCK -> {
                BlockPos pos = job.pos;
                if (pos == null) {
                    return null;
                }
                if (getTask().usesBeam()) {
                    //Rayon : n'importe ou a portee du bloc ; le chemin choisit la place (au sol comme en vol)
                    return Vec3.atCenterOf(pos);
                }
                return Vec3.atBottomCenterOf(pos).add(0, kind.flying ? 1.4 : 0, 0);
            }
            default -> {
                return null;
            }
        }
    }

    private Vec3 lift(Vec3 point) {
        return kind.flying ? point.add(0, 0.5, 0) : point;
    }

    private int workDuration(ServerLevel server) {
        double speed = kind.work * getTier().work * stats.work() * kind.affinity(getTask());
        //1.9 (MAJ RP) : chantier plus rapide quand le proprietaire de la baie (Construction) est connecte
        if (getTask() == RobotTask.BUILDER && home != null && server.getBlockEntity(home) instanceof fr.pioneers.core.block.PiBlockEntity pbe
            && pbe.owner() != null) {
            net.minecraft.server.level.ServerPlayer owner = server.getServer().getPlayerList().getPlayer(pbe.owner());
            if (owner != null) {
                speed *= fr.pioneers.core.profile.PioneerApi.factor(owner, "building.queue_speed");
            }
        }
        double ticks;
        if (atStop()) {
            ticks = 12;
        } else {
            ticks = switch (getTask()) {
                case LOGISTICS -> 12;
                case COLLECTOR -> 6;
                case FARMER -> 24;
                case BREEDER, APIARIST -> 30;
                case HUNTER -> 12;
                case FISHER -> 140 + server.random.nextInt(200);
                case BUILDER -> {
                    if (job != null && job.place != null) {
                        yield 6;
                    }
                    BlockPos target = currentBlock();
                    float hardness = target == null ? 1 : Math.max(0.3F, server.getBlockState(target).getDestroySpeed(server, target));
                    yield 8 + hardness * 14;
                }
                case LUMBERJACK, MINER, EXCAVATOR -> {
                    BlockPos target = currentBlock();
                    float hardness = target == null ? 1 : Math.max(0.3F, server.getBlockState(target).getDestroySpeed(server, target));
                    yield 8 + hardness * 14;
                }
            };
        }
        return Math.max(2, (int) Math.round(ticks / Math.max(0.2, speed)));
    }

    @Nullable
    private BlockPos currentBlock() {
        if (job == null) {
            return null;
        }
        if (getTask() == RobotTask.LUMBERJACK && treeIndex < job.extra.size()) {
            return job.extra.get(treeIndex);
        }
        if (getTask() == RobotTask.BUILDER && job.place != null && treeIndex > 0 && treeIndex <= job.extra.size()) {
            return job.extra.get(treeIndex - 1);
        }
        return job.pos;
    }

    private void working(ServerLevel server, RoboticHubData hub) {
        if (job == null) {
            afterJob(server, hub, null);
            return;
        }
        boolean stopping = atStop();
        BlockPos block = stopping ? null : currentBlock();
        BlockPos beam = !stopping && getTask().usesBeam() ? block : null;
        if (shooting() && server.getEntity(job.entityId) instanceof LivingEntity prey) {
            beam = BlockPos.containing(prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ());
        }
        setWorking(true, beam);
        lookAtWork(server);
        if (tickCount % 6 == 0) {
            swing(InteractionHand.MAIN_HAND);
        }
        if (--workTimer > 0) {
            return;
        }
        if (stopping) {
            performStop(server);
            stop++;
            if (atStop() || job.type != Job.Type.TRIP) {
                phase = Phase.GOING;
                resetMove();
                return;
            }
            finish(server, hub);
            return;
        }
        //1.9 (MAJ RP, bloc 4) : une unite n'agit dans un claim que si sa baie est du meme territoire (ou d'un allie)
        BlockPos at = block != null ? block : job.pos;
        if (at != null && !ClanAgents.canAct(server, home, at)) {
            finish(server, hub);
            return;
        }
        boolean done = ClanAgents.act(home, () -> perform(server));
        drainWork();
        if (done) {
            finish(server, hub);
        } else if (job != null && phase == Phase.WORKING) {
            //Travail en plusieurs etapes (arbre, combat) : on continue
            workTimer = workDuration(server);
        }
    }

    /** Travail termine : on libere, puis on enchaine le suivant s'il y en a un pres d'ici. */
    private void finish(ServerLevel server, RoboticHubData hub) {
        Job finished = job;
        job = null;
        completed = true;
        if (finished != null) {
            RoboticsScheduler.release(server, finished);
        }
        afterJob(server, hub, finished);
    }

    /** Arret d'une tournee : prendre ou livrer. */
    private void performStop(ServerLevel server) {
        if (job == null) {
            return;
        }
        Job.Stop s = job.stops.get(stop);
        //1.9 (MAJ RP, bloc 4) : ni prise ni livraison a travers une frontiere de claim refusee (depuis sa baie)
        boolean allowed = home == null || ClanApi.canTransfer(server, home, s.at().pos());
        if (s.load()) {
            long amount = allowed ? RoboticsWorld.take(server, s.at(), s.res(), s.amount(), cargo, s.accept(), false) : 0;
            if (amount > 0) {
                playSound(SoundEvents.ITEM_PICKUP, 0.6F, 0.8F);
            } else if (job.type == Job.Type.TRIP && stop == 0) {
                //Rien a prendre : la tournee n'a plus d'objet
                stop = job.stops.size();
            }
        } else {
            long delivered = allowed ? RoboticsWorld.give(server, s.at(), cargo, s.res(), s.amount()) : 0;
            if (delivered > 0) {
                RoboticsScheduler.delivered(server, job, delivered);
                //Livraison faite : la demande n'est plus en souffrance
                RoboticsScheduler.resolve(server, s.at().pos(), RoboticsAlert.Type.NO_SOURCE, RoboticsAlert.Type.NO_CARRIER);
                if (server.getBlockEntity(s.at().pos()) instanceof TileEntityLogisticTerminal terminal) {
                    //Commande d'un joueur : elle avance
                    terminal.delivered(s.res(), delivered);
                }
                playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6F, 1.0F);
            }
        }
    }

    private void lookAtWork(ServerLevel server) {
        Vec3 at = null;
        Entity entity = job != null && job.type == Job.Type.ENTITY && !atStop() ? server.getEntity(job.entityId) : null;
        if (entity != null) {
            at = entity.position();
        } else if (atStop()) {
            at = Vec3.atCenterOf(job.stops.get(stop).at().pos());
        } else if (currentBlock() != null) {
            at = Vec3.atCenterOf(currentBlock());
        }
        if (at != null) {
            getLookControl().setLookAt(at.x, at.y, at.z, 30, 30);
            double dx = at.x - getX(), dz = at.z - getZ();
            if (dx * dx + dz * dz > 0.01) {
                setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90);
                yBodyRot = getYRot();
            }
        }
    }

    /** Outil du travail (toucher de soie, fortune selon les cartes du hub). */
    private ItemStack tool(Item base) {
        ItemStack tool = new ItemStack(base);
        if (stats.silk()) {
            tool.enchant(Enchantments.SILK_TOUCH, 1);
        } else if (stats.fortune() > 0) {
            tool.enchant(Enchantments.BLOCK_FORTUNE, stats.fortune());
        }
        return tool;
    }

    /** Fait le travail de zone ; true s'il est termine. */
    private boolean perform(ServerLevel server) {
        RobotTask task = getTask();
        boolean griefing = PiCommonConfig.get(PiCommonConfig.ROBOTICS_GRIEFING, true);
        switch (task) {
            case FARMER -> {
                if (job != null && job.pos != null && griefing && job.farm > 0) {
                    tend(server, job);
                    return true;
                }
                if (job != null && job.pos != null && griefing && !harvest(server, job.pos)) {
                    noRoom(server);
                    return false;
                }
                return true;
            }
            case LUMBERJACK -> {
                if (job == null || job.pos == null || !griefing) {
                    return true;
                }
                //Feuillage d'abord (par poignees : il donne les pousses), puis le tronc du haut jusqu'au pied
                int leaves = 0;
                while (treeIndex < job.extra.size()) {
                    BlockPos pos = job.extra.get(treeIndex);
                    BlockState state = server.getBlockState(pos);
                    boolean leaf = state.is(BlockTags.LEAVES);
                    if (!leaf && !state.is(BlockTags.LOGS)) {
                        treeIndex++;
                        continue;
                    }
                    List<ItemStack> drops = RoboticsWorld.breakBlock(server, pos, this, leaf ? leafTool() : tool(Items.IRON_AXE), cargo::canFit);
                    if (drops == RoboticsWorld.NO_ROOM) {
                        noRoom(server);
                        return false;
                    }
                    treeIndex++;
                    collect(drops);
                    if (!leaf || ++leaves >= 6) {
                        break;
                    }
                }
                if (treeIndex < job.extra.size()) {
                    return false;
                }
                replant(server, job.pos);
                return true;
            }
            case BUILDER -> {
                return build(server, griefing);
            }
            case MINER, EXCAVATOR -> {
                if (job != null && job.pos != null && griefing) {
                    List<ItemStack> drops = RoboticsWorld.breakBlock(server, job.pos, this, tool(Items.DIAMOND_PICKAXE), cargo::canFit);
                    if (drops == RoboticsWorld.NO_ROOM) {
                        noRoom(server);
                        return false;
                    }
                    collect(drops);
                }
                return true;
            }
            case FISHER -> {
                if (cargo.items.size() >= cargo.stacks) {
                    noRoom(server);
                    return false;
                }
                fish(server);
                //Peche : la prise part au reseau par petits lots (environ 15 poissons), sans attendre une soute pleine
                if (fished >= 12 + server.random.nextInt(7)) {
                    fished = 0;
                    noRoom(server);
                    return false;
                }
                return true;
            }
            case COLLECTOR -> {
                if (job != null && server.getEntity(job.entityId) instanceof ItemEntity item && item.isAlive()) {
                    if (cargo.itemRoom(item.getItem()) <= 0) {
                        noRoom(server);
                        return false;
                    }
                    ItemStack rest = cargo.insert(item.getItem());
                    if (rest.isEmpty()) {
                        item.discard();
                    } else {
                        item.setItem(rest);
                    }
                    playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
                }
                return true;
            }
            case APIARIST -> {
                if (job == null) {
                    return true;
                }
                if (job.type == Job.Type.BLOCK && job.pos != null) {
                    if (griefing && !harvestHoney(server, job.pos)) {
                        noRoom(server);
                        return false;
                    }
                    return true;
                }
                feed(server);
                return true;
            }
            case BREEDER -> {
                if (job == null) {
                    return true;
                }
                if (job.cull) {
                    return laser(server);
                }
                feed(server);
                return true;
            }
            case HUNTER -> {
                return laser(server);
            }
            default -> {
                return true;
            }
        }
    }

    /** Nourrit le couple vise (elevage, rucher) avec la nourriture de la soute. */
    private void feed(ServerLevel server) {
        if (job == null) {
            return;
        }
        Entity first = server.getEntity(job.entityId), second = server.getEntity(job.partner);
        for (Entity entity : new Entity[]{first, second}) {
            if (entity instanceof Animal animal && animal.getAge() == 0 && !animal.isInLove()) {
                //1.3.0 : aliments du mod (graines, orge, aliment compose...) acceptes ; l'animal nourri devient domestique
                AnimalSpecies sp = AnimalSpecies.of(animal);
                ItemStack food = takeFromCargo(st -> animal.isFood(st) || sp != null && sp.feedValue(st) > 0);
                if (!food.isEmpty()) {
                    AnimalData data = sp == null ? null : PiAnimals.get(animal);
                    if (data != null) {
                        data.domestic = true;
                        data.hunger = Math.min(100, data.hunger + Math.max(10, sp.feedValue(food)));
                    }
                    animal.setInLove(null);
                    //Reproduction par un robot : pas d'experience (voir RobotBreeding)
                    animal.getPersistentData().putLong(RobotBreeding.TAG, server.getGameTime());
                    server.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(), 3, 0.3, 0.2, 0.3, 0);
                }
            }
        }
    }

    /** Peche : prises depuis le dernier dechargement. */
    private int fished;

    /** Apiculture : alternance rayons / fioles selon le reglage (compteur par unite). */
    private int honeyCount;

    /**
     * Recolte une ruche pleine, sans enerver les abeilles : des rayons (comme a la cisaille) ou une fiole de miel (une fiole vide de la
     * soute), selon la part reglee sur la zone. false si la soute est pleine.
     */
    private boolean harvestHoney(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        if (!ZoneScanner.honeyReady(state)) {
            return true;
        }
        RoboticOrder order = job == null ? null : RoboticsOrders.get(server).get(job.group);
        int comb = order == null ? 100 : order.honeycomb;
        //Repartition reguliere : sur 100 recoltes, « comb » en rayons
        boolean wantsComb = (honeyCount++ * comb) % 100 < comb;
        ItemStack loot;
        if (!wantsComb && !takeFromCargo(stack -> stack.is(Items.GLASS_BOTTLE)).isEmpty()) {
            loot = new ItemStack(Items.HONEY_BOTTLE);
            playSound(SoundEvents.BOTTLE_FILL, 0.6F, 1);
        } else {
            loot = new ItemStack(Items.HONEYCOMB, 3);
            playSound(SoundEvents.BEEHIVE_SHEAR, 0.6F, 1);
        }
        if (cargo.itemRoom(loot) < loot.getCount()) {
            return false;
        }
        cargo.insert(loot);
        server.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, 0), 3);
        server.sendParticles(ParticleTypes.FALLING_HONEY, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0);
        return true;
    }

    /**
     * Tir au laser a distance (jusqu'a 9 blocs) : chasse et elimination du surplus. Le butin est ramasse ensuite. true quand la cible
     * est abattue (ou partie).
     */
    private boolean laser(ServerLevel server) {
        if (job == null || !(server.getEntity(job.entityId) instanceof LivingEntity target) || !target.isAlive()) {
            return collectLoot(server);
        }
        if (distanceToSqr(target) > LASER_RANGE * LASER_RANGE || distanceToSqr(target) < TOO_CLOSE * TOO_CLOSE) {
            //Trop loin, ou la proie s'approche : on se replace a distance de tir
            phase = Phase.GOING;
            resetMove();
            return false;
        }
        //Degats « physiques » (les sorcieres resistent a la magie), qui ignorent l'invulnerabilite apres un coup
        target.invulnerableTime = 0;
        target.hurt(damageSources().mobAttack(this), (float) (22 * getTier().work * stats.work()));
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.2, 0.2,
              0.2, 0.05);
        playSound(SoundEvents.BEACON_POWER_SELECT, 0.4F, 1.8F);
        if (!target.isAlive()) {
            lootAt = target.position();
            return false;
        }
        return false;
    }

    /** Ou la derniere proie est tombee (on va ramasser son butin). */
    @Nullable
    private Vec3 lootAt;

    /** Ramasse le butin de la proie : on s'en approche d'abord. */
    private boolean collectLoot(ServerLevel server) {
        if (lootAt == null) {
            return true;
        }
        if (position().distanceToSqr(lootAt) > 9) {
            //Le butin est loin : on y va (le travail continue a l'arrivee)
            phase = Phase.GOING;
            resetMove();
            return false;
        }
        pickUpAround(server);
        lootAt = null;
        return true;
    }

    /** Range le butin dans la soute ; rien ne tombe jamais au sol (le butin est verifie avant de casser). */
    private void collect(@Nullable List<ItemStack> drops) {
        if (drops == null) {
            return;
        }
        for (ItemStack drop : drops) {
            cargo.insert(drop);
        }
    }

    /** Soute trop pleine pour la suite : on rend le travail (il reste a faire) et on va se vider. */
    private void noRoom(ServerLevel server) {
        if (job != null) {
            RoboticsScheduler.release(server, job);
            job = null;
        }
        setWorking(false, null);
        resetMove();
        deposit = null;
        depositTries = 0;
        phase = Phase.DEPOSIT;
    }

    /** Outil pour le feuillage : jamais de toucher de soie (il donnerait les feuilles au lieu des pousses). */
    private ItemStack leafTool() {
        ItemStack tool = new ItemStack(Items.IRON_HOE);
        if (stats.fortune() > 0) {
            tool.enchant(Enchantments.BLOCK_FORTUNE, stats.fortune());
        }
        return tool;
    }

    /** Objets d'une ressource dans la soute. */
    private long cargoCount(LogisticResource res) {
        long count = 0;
        for (ItemStack stack : cargo.items) {
            if (!stack.isEmpty() && res.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Entretien du champ : rebecher la terre pietinee (houe), puis semer (graine de la soute). */
    private void tend(ServerLevel server, Job job) {
        BlockPos crop = job.pos;
        if (job.farm == 1) {
            BlockPos soil = job.pos;
            BlockState above = server.getBlockState(soil.above());
            if (!above.isAir() && above.canBeReplaced()) {
                server.destroyBlock(soil.above(), false);
            }
            //1.12.3 (lot F3) : le substrat hydroponique tasse se beche sur place (il reste du substrat)
            BlockState ground = server.getBlockState(soil);
            server.setBlockAndUpdate(soil, HydroponicSubstrateBlock.isPacked(ground) ? ground.setValue(HydroponicSubstrateBlock.TILLED, true)
                                                                                      : Blocks.FARMLAND.defaultBlockState());
            server.playSound(null, soil, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1, 1);
            crop = soil.above();
        }
        RoboticOrder order = RoboticsOrders.get(server).get(job.group);
        ItemStack wanted = order == null ? job.material : ZoneScanner.seedFor(server, order, crop);
        plant(server, crop, wanted);
    }

    /** Seme une graine de la soute (celle voulue de preference) sur la terre labouree. */
    private boolean plant(ServerLevel server, BlockPos crop, ItemStack wanted) {
        if (!server.getBlockState(crop).isAir() || !HydroponicSubstrateBlock.isFarmSoil(server.getBlockState(crop.below()))) {
            return false;
        }
        ItemStack seed = wanted.isEmpty() ? ItemStack.EMPTY : takeFromCargo(stack -> ItemStack.isSameItem(stack, wanted));
        if (seed.isEmpty() || !(seed.getItem() instanceof BlockItem item)) {
            if (!seed.isEmpty()) {
                cargo.insert(seed);
            }
            return false;
        }
        BlockState state = item.getBlock().defaultBlockState();
        if (!state.canSurvive(server, crop)) {
            cargo.insert(seed);
            return false;
        }
        server.setBlockAndUpdate(crop, state);
        if (server.getBlockEntity(crop) instanceof CropTile tile) {
            tile.setGenes(Genes.of(seed));
        }
        server.playSound(null, crop, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1, 1);
        return true;
    }

    /** Recolte et replante (graine prise dans la recolte ou dans la soute) ; false si la soute est trop pleine. */
    private boolean harvest(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        if (!ZoneScanner.isRipe(server, pos, state)) {
            return true;
        }
        Block block = state.getBlock();
        if (ZoneScanner.isColumnPlant(state)) {
            return harvestColumn(server, pos, state);
        }
        if (block instanceof CaveVines) {
            List<ItemStack> berries = List.of(new ItemStack(Items.GLOW_BERRIES));
            if (!cargo.canFit(berries)) {
                return false;
            }
            collect(berries);
            server.setBlockAndUpdate(pos, state.setValue(CaveVines.BERRIES, false));
            server.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1, 1);
            return true;
        }
        if (block instanceof SweetBerryBushBlock) {
            List<ItemStack> berries = List.of(new ItemStack(Items.SWEET_BERRIES, 2 + server.random.nextInt(2)));
            if (!cargo.canFit(berries)) {
                return false;
            }
            collect(berries);
            server.setBlockAndUpdate(pos, state.setValue(SweetBerryBushBlock.AGE, 1));
            server.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1, 1);
            return true;
        }
        if (block instanceof FarmCropBlock farm) {
            //1.3 : culture genetique : recolte sur place, replantee avec la meilleure graine fille (ou repousse)
            RoboticOrder farmOrder = job == null ? null : RoboticsOrders.get(server).get(job.group);
            List<ItemStack> crops = farm.harvest(server, pos, state, farmOrder == null || farmOrder.replant, true);
            server.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1, 1);
            for (ItemStack stack : crops) {
                if (cargo.canFit(List.of(stack))) {
                    collect(List.of(stack));
                } else {
                    Block.popResource(server, pos, stack);
                }
            }
            return true;
        }
        ItemStack seed = block.getCloneItemStack(server, pos, state);
        List<ItemStack> drops = RoboticsWorld.breakBlock(server, pos, this, stats.fortune() > 0 ? tool(Items.IRON_HOE) : ItemStack.EMPTY, cargo::canFit);
        if (drops == RoboticsWorld.NO_ROOM) {
            return false;
        }
        if (drops == null) {
            return true;
        }
        RoboticOrder order = job == null ? null : RoboticsOrders.get(server).get(job.group);
        boolean replantOn = order == null || order.replant;
        ItemStack chosen = order == null ? ItemStack.EMPTY : order.plantItem();
        if (replantOn && !chosen.isEmpty() && !ItemStack.isSameItem(chosen, seed) && HydroponicSubstrateBlock.isFarmSoil(server.getBlockState(pos.below()))) {
            //Une autre graine est demandee pour ce champ : la recolte va en soute, on seme celle-la
            collect(drops);
            plant(server, pos, chosen);
            return true;
        }
        BlockState replant = null;
        if (!replantOn) {
            collect(drops);
            return true;
        }
        if (block instanceof CropBlock crop) {
            replant = crop.getStateForAge(0);
        } else if (block instanceof NetherWartBlock) {
            replant = state.setValue(NetherWartBlock.AGE, 0);
        } else if (block instanceof CocoaBlock) {
            replant = state.setValue(CocoaBlock.AGE, 0);
        }
        if (replant != null) {
            boolean planted = false;
            for (ItemStack drop : drops) {
                if (ItemStack.isSameItem(drop, seed)) {
                    drop.shrink(1);
                    planted = true;
                    break;
                }
            }
            if (!planted) {
                planted = !takeFromCargo(stack -> ItemStack.isSameItem(stack, seed)).isEmpty();
            }
            if (planted && replant.canSurvive(server, pos)) {
                server.setBlockAndUpdate(pos, replant);
            }
        }
        collect(drops);
        return true;
    }

    /** Plante en colonne : tout au-dessus du pied, du haut vers le bas (rien ne tombe au sol), le pied repousse. */
    private boolean harvestColumn(ServerLevel server, BlockPos pos, BlockState state) {
        BlockPos base = pos;
        while (ZoneScanner.sameColumn(state, server.getBlockState(base.below())) && pos.getY() - base.getY() < 32) {
            base = base.below();
        }
        BlockPos top = pos;
        while (ZoneScanner.sameColumn(state, server.getBlockState(top.above())) && top.getY() - pos.getY() < 32) {
            top = top.above();
        }
        ItemStack tool = stats.fortune() > 0 ? tool(Items.IRON_HOE) : ItemStack.EMPTY;
        for (BlockPos at = top; at.getY() > base.getY(); at = at.below()) {
            List<ItemStack> drops = RoboticsWorld.breakBlock(server, at, this, tool, cargo::canFit);
            if (drops == RoboticsWorld.NO_ROOM) {
                return false;
            }
            if (drops != null) {
                collect(drops);
            }
        }
        return true;
    }

    /**
     * Travaux : retire le bloc, ou pose le prochain bloc du groupe (un par cycle, objet pris dans la soute). True quand c'est fini (ou qu'il
     * n'y a plus de quoi poser : le reste sera repris par un prochain travail).
     */
    private boolean build(ServerLevel server, boolean griefing) {
        if (job == null || job.pos == null || !griefing) {
            return true;
        }
        if (job.place == null) {
            BlockState state = server.getBlockState(job.pos);
            if (state.isAir()) {
                return true;
            }
            //0.20.7 : jamais sous un train (la voie sera reprise plus tard)
            if (state.getBlock() instanceof TrackBlock && RailWorld.get(server).trainNear(job.pos, 0)) {
                return true;
            }
            List<ItemStack> drops = RoboticsWorld.breakBlock(server, job.pos, this, tool(Items.DIAMOND_PICKAXE), cargo::canFit);
            if (drops == RoboticsWorld.NO_ROOM) {
                noRoom(server);
                return false;
            }
            collect(drops);
            if (state.getBlock() instanceof TrackBlock) {
                //0.20.7 : les voies voisines hors chantier se simplifient
                WorksPlan removed = WorksData.get(server).plan(job.group);
                TrackPlacement.worksChanged(server, job.pos, true, p -> removed != null && removed.target(p) != null);
            }
            return true;
        }
        List<BlockPos> positions = ZoneScanner.placements(job);
        while (treeIndex < positions.size()) {
            BlockPos pos = positions.get(treeIndex);
            BlockState target = job.states.get(treeIndex);
            treeIndex++;
            BlockState current = server.getBlockState(pos);
            if (WorksPlan.matches(current, target) || !ZoneScanner.needsPlacement(current, target)) {
                continue;
            }
            //Jamais de bloc sur quelqu'un (unite, joueur, animal) : la case sera reprise plus tard
            if (!server.getEntities((Entity) null, new AABB(pos), entity -> entity instanceof LivingEntity && entity.isAlive()).isEmpty()) {
                continue;
            }
            if (target.getBlock() instanceof TrackBlock && current.getBlock() instanceof TrackBlock && RailWorld.get(server).trainNear(pos, 0)) {
                continue;
            }
            ItemStack need = WorksPlan.requiredItem(server, pos, target, current);
            if (!need.isEmpty() && takeFromCargo(stack -> ItemStack.isSameItem(stack, need)).isEmpty()) {
                //Plus de materiau : fin du groupe
                return true;
            }
            BlockState placed = WorksPlan.placedState(target);
            server.setBlock(pos, placed, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
            WorksPlan plan = WorksData.get(server).plan(job.group);
            if (placed.getBlock() instanceof TrackBlock) {
                //0.20.7 : les voies du monde hors chantier se raccordent a la nouvelle (aiguillage, courbe, pente, diagonale)
                TrackPlacement.worksChanged(server, pos, false, p -> plan != null && plan.target(p) != null);
            }
            CompoundTag config = plan == null ? null : plan.configAt(pos);
            if (config != null) {
                //Point logistique copie : ses reglages suivent
                RoboticsScheduler.applyConfig(server, pos, config);
            }
            SoundType sound = placed.getSoundType(server, pos, this);
            server.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2F, sound.getPitch() * 0.8F);
            return treeIndex >= positions.size();
        }
        return true;
    }

    /** Replante une pousse au pied de l'arbre abattu (pousse de la soute : recolte ou fournitures). */
    private void replant(ServerLevel server, BlockPos root) {
        if (!server.getBlockState(root).isAir() || !server.getBlockState(root.below()).is(BlockTags.DIRT)) {
            return;
        }
        RoboticOrder order = job == null ? null : RoboticsOrders.get(server).get(job.group);
        if (order != null && !order.replant) {
            return;
        }
        ItemStack chosen = order == null ? ItemStack.EMPTY : order.plantItem();
        if (!chosen.isEmpty()) {
            ItemStack own = takeFromCargo(stack -> ItemStack.isSameItem(stack, chosen));
            if (!own.isEmpty() && own.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().canSurvive(server, root)) {
                server.setBlockAndUpdate(root, item.getBlock().defaultBlockState());
                return;
            }
            if (!own.isEmpty()) {
                cargo.insert(own);
            }
        }
        //La pousse de la meme essence si on l'a (chene -> pousse de chene), sinon n'importe laquelle
        ItemStack sapling = takeFromCargo(stack -> stack.is(ItemTags.SAPLINGS) && job != null && sameSpecies(stack, job));
        if (sapling.isEmpty()) {
            sapling = takeFromCargo(stack -> stack.is(ItemTags.SAPLINGS));
        }
        if (!sapling.isEmpty() && sapling.getItem() instanceof BlockItem item) {
            BlockState state = item.getBlock().defaultBlockState();
            if (state.canSurvive(server, root)) {
                server.setBlockAndUpdate(root, state);
                return;
            }
        }
        if (!sapling.isEmpty()) {
            cargo.insert(sapling);
        }
    }

    /** La pousse vient-elle de la meme essence que l'arbre abattu (meme prefixe d'identifiant : oak_log / oak_sapling) ? */
    private static boolean sameSpecies(ItemStack sapling, Job job) {
        if (job.extra.isEmpty()) {
            return false;
        }
        String id = ForgeRegistries.ITEMS.getKey(sapling.getItem()).getPath();
        String species = id.replace("_sapling", "").replace("_propagule", "");
        return job.woodName != null && job.woodName.startsWith(species);
    }

    private void fish(ServerLevel server) {
        if (job == null || job.pos == null) {
            return;
        }
        LootTable table = server.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        LootParams params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(job.pos))
              .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD)).withParameter(LootContextParams.THIS_ENTITY, this)
              .withLuck(getTier().ordinal() + stats.fortune()).create(LootContextParamSets.FISHING);
        var catches = table.getRandomItems(params);
        //1.3.0 : les poissons sont ceux du milieu (especes du mod), la morue et le saumon vanilla restant des especes parmi d'autres
        for (int i = 0; i < catches.size(); i++) {
            ItemStack stack = catches.get(i);
            if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH) || stack.is(Items.PUFFERFISH)) {
                int sp = FishSpawns.pick(server, job.pos, server.random, false);
                if (sp >= 0) {
                    FishDef d = GeneratedFish.SPECIES[sp];
                    catches.set(i, FishItems.of(sp, d.cmMin() + server.random.nextInt(Math.max(1, d.cmMax() - d.cmMin()))));
                }
            }
        }
        for (ItemStack stack : catches) {
            fished += stack.getCount();
        }
        collect(catches);
        server.sendParticles(ParticleTypes.SPLASH, job.pos.getX() + 0.5, job.pos.getY() + 1, job.pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
        server.playSound(null, job.pos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
    }

    /** Ramasse le butin tombe autour (chasse). */
    private void pickUpAround(ServerLevel server) {
        for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(4))) {
            ItemStack rest = cargo.insert(item.getItem());
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setItem(rest);
            }
        }
    }

    private ItemStack takeFromCargo(Predicate<ItemStack> predicate) {
        for (ItemStack stack : cargo.items) {
            if (!stack.isEmpty() && predicate.test(stack)) {
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                cargo.clearItems();
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------
    // Apres le travail : enchainer, deposer, attendre, rentrer
    // ------------------------------------------------------------------

    private void afterJob(ServerLevel server, RoboticHubData hub, @Nullable Job finished) {
        setWorking(false, null);
        resetMove();
        if (kind == UnitKind.TURTLE && finished != null && finished.group >= 0 && finished.type != Job.Type.TRIP) {
            //Mode repos : une tortue reste sur la zone qu'elle vient de travailler
            RoboticOrder order = RoboticsOrders.get(server).get(finished.group);
            if (order != null && order.type == RoboticOrder.Type.ZONE && order.rest) {
                restOrder = order.id;
                restSpot = restSpot(server, order);
            }
        }
        Job next = RoboticsScheduler.next(server, this, finished, hub);
        if (next != null) {
            assign(next);
            return;
        }
        depositTries = 0;
        deposit = null;
        if (finished != null && finished.group >= 0 && finished.type != Job.Type.TRIP && cargo.slotFill() < 0.75) {
            RoboticOrder order = RoboticsOrders.get(server).get(finished.group);
            if (order != null && !order.paused && order.type == RoboticOrder.Type.ZONE) {
                //Chantier en cours (une couche se termine, un travail se libere) : on attend sur place sans aller se vider
                lingerOrder = order.id;
                lingerUntil = tickCount + LINGER_TICKS;
                idleTicks = 0;
                phase = Phase.IDLE;
                setIdleState(server, true);
                return;
            }
        }
        lingerOrder = -1;
        if (cargo.isEmpty()) {
            enterIdle(server);
        } else {
            phase = Phase.DEPOSIT;
        }
    }

    /** Attente sur un chantier entre deux travaux : ordre, et fin de l'attente (ticks). */
    private int lingerOrder = -1, lingerUntil;
    private static final int LINGER_TICKS = 240;

    /** Attente sur place (chantier) : l'unite garde sa soute et reprend des qu'un travail de la zone se libere. */
    private boolean lingering(ServerLevel server) {
        if (lingerOrder < 0) {
            return false;
        }
        RoboticOrder order = RoboticsOrders.get(server).get(lingerOrder);
        if (tickCount > lingerUntil || order == null || order.paused || cargo.slotFill() >= 0.75) {
            lingerOrder = -1;
            return false;
        }
        return true;
    }

    /** Depose la cargaison : objets et autres ressources vont au stockage le plus proche qui les accepte. */
    private void depositing(ServerLevel server, RoboticHubData hub) {
        setWorking(false, null);
        if (cargo.isEmpty() || depositTries > 4) {
            if (!cargo.tankEmpty() || cargo.energy > 0 || cargo.air > 0) {
                RoboticsScheduler.alert(server, RoboticsAlert.Type.STORAGE_FULL, blockPosition());
            }
            if (cargo.hasItems()) {
                phase = Phase.RETURNING;
                resetMove();
            } else {
                enterIdle(server);
            }
            return;
        }
        LogisticResource res = cargoResource();
        if (deposit == null) {
            LogisticsRegistry registry = LogisticsRegistry.get(server);
            LogisticNetwork network = registry.networkOf(hub, server.getGameTime());
            LogisticPoint storage = network == null || res == null ? null : registry.findStorage(server, network, res, 1, position(), true);
            depositTries++;
            if (storage == null) {
                //Aucun stockage : les objets iront dans le tampon du hub
                if (res != null && res.type == Resource.ITEMS) {
                    RoboticsScheduler.alert(server, RoboticsAlert.Type.STORAGE_FULL, blockPosition());
                }
                phase = Phase.RETURNING;
                resetMove();
                return;
            }
            deposit = storage.endpointFor(res);
            resetMove();
        }
        Move move = travel(server, lift(deposit.accessPoint()), reach(), false);
        if (move == Move.ARRIVED) {
            getNavigation().stop();
            //1.9 (MAJ RP, bloc 4) : pas de depot a travers une frontiere de claim refusee
            if (res != null && (home == null || ClanApi.canTransfer(server, home, deposit.pos()))
                && RoboticsWorld.give(server, deposit, cargo, res.type == Resource.ITEMS ? LogisticResource.any(Resource.ITEMS) : res, 0) > 0) {
                //Depot reussi : il y a de nouveau de la place
                RoboticsScheduler.resolve(server, null, RoboticsAlert.Type.STORAGE_FULL);
            }
            playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6F, 1.0F);
            deposit = null;
        } else if (move == Move.FAILED) {
            RoboticsScheduler.groundUnreachable(server, deposit.standPos());
            deposit = null;
            depositTries++;
        }
    }

    /** Ce qu'il reste a deposer (precis). */
    @Nullable
    private LogisticResource cargoResource() {
        for (ItemStack stack : cargo.items) {
            if (!stack.isEmpty()) {
                return LogisticResource.of(stack);
            }
        }
        if (!cargo.fluid.isEmpty()) {
            return LogisticResource.of(cargo.fluid);
        }
        if (!cargo.gas.isEmpty()) {
            return LogisticResource.of(cargo.gas.getGas());
        }
        if (!cargo.data.isEmpty()) {
            return LogisticResource.of(cargo.data.getType());
        }
        if (cargo.air > 0) {
            return LogisticResource.PRESSURE;
        }
        return cargo.energy > 0 ? LogisticResource.ENERGY : null;
    }

    /** Noeud du reseau le plus interessant (proche, et peu encombre pour une recharge). */
    @Nullable
    private NetworkNode nearestNode(ServerLevel server, RoboticHubData hub, boolean forCharge) {
        LogisticNetwork network = LogisticsRegistry.get(server).networkOf(hub, server.getGameTime());
        if (network == null) {
            return hub;
        }
        NetworkNode best = null;
        double bestScore = Double.MAX_VALUE;
        for (NetworkNode node : network.nodes) {
            double score = Math.sqrt(Vec3.atCenterOf(node.nodeCenter()).distanceToSqr(position())) + (forCharge ? node.crowd() * 48 : 0);
            if (score < bestScore) {
                best = node;
                bestScore = score;
            }
        }
        return best;
    }

    private void charging(ServerLevel server, RoboticHubData hub) {
        setWorking(false, null);
        if (charger == null || !charger.nodeValid()) {
            charger = nearestNode(server, hub, true);
            chargerWait = 0;
            if (charger == null) {
                phase = Phase.RETURNING;
                return;
            }
        }
        Vec3 spot = charger.chargeSpot(this);
        boolean reserved = spot != null;
        if (spot == null) {
            //Toutes les places sont prises : on attend a cote
            spot = charger.idleSpot(this);
            if (++chargerWait > 600) {
                //Trop d'attente : on tente un autre chargeur
                charger = null;
                return;
            }
        }
        Move move = travel(server, spot, kind.flying ? 0.6 : 1.2, true);
        if (move != Move.ARRIVED || !reserved) {
            if (!jets) {
                entityData.set(FX, (byte) 0);
            }
            return;
        }
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        long given = charger.charge(this, maxBattery - battery);
        battery = Math.min(maxBattery, battery + given);
        entityData.set(FX, (byte) (given > 0 ? 1 : 0));
        if (batteryRatio() >= FULL_BATTERY) {
            leaveCharger();
            Phase next = resume;
            resume = null;
            resetMove();
            if (next != null && (job != null || next == Phase.DEPOSIT)) {
                phase = next;
            } else {
                enterIdle(server);
            }
        }
    }

    private void leaveCharger() {
        if (charger != null) {
            charger.leave(this);
            charger = null;
        }
        if (!level().isClientSide) {
            entityData.set(FX, (byte) 0);
        }
    }

    /**
     * Plus rien a faire : l'unite rentre au hub (elle en repartira des qu'un travail se presente) ; une tortue en mode repos retourne
     * plutot dormir sur sa zone.
     */
    private void enterIdle(ServerLevel server) {
        resetMove();
        if (restOrder >= 0 && restSpot != null && restValid(server)) {
            phase = Phase.RESTING;
            idleTicks = 0;
            setIdleState(server, true);
            return;
        }
        restOrder = -1;
        restSpot = null;
        setIdleState(server, false);
        phase = Phase.RETURNING;
    }

    /** La zone de repos existe-t-elle encore, active, et pour son hub ? */
    private boolean restValid(ServerLevel server) {
        RoboticOrder order = RoboticsOrders.get(server).get(restOrder);
        RoboticHubData hub = getHubData();
        return order != null && order.rest && !order.paused && order.isValid() && order.type == RoboticOrder.Type.ZONE && hub != null && !hub.isRecalling()
               && order.assignable(hub);
    }

    /** Place pour dormir : ou elle se trouve si c'est dans la zone (ou tout pres), sinon au centre de la zone, au sol. */
    private Vec3 restSpot(ServerLevel server, RoboticOrder order) {
        Vec3 pos = position();
        if (order.min != null && order.max != null) {
            boolean inside = pos.x >= order.min.getX() - 2 && pos.x <= order.max.getX() + 3 && pos.z >= order.min.getZ() - 2 && pos.z <= order.max.getZ() + 3;
            if (inside) {
                return onGround() ? pos : groundBelow(server, pos);
            }
            int x = (order.min.getX() + order.max.getX()) / 2, z = (order.min.getZ() + order.max.getZ()) / 2;
            int y = server.hasChunk(x >> 4, z >> 4) ? server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : order.max.getY() + 1;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return pos;
    }

    /** Le sol sous un point (le premier bloc plein en dessous), pour s'y poser. */
    private static Vec3 groundBelow(ServerLevel server, Vec3 pos) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(pos).mutable();
        for (int i = 0; i < 48 && cursor.getY() > server.getMinBuildHeight(); i++) {
            BlockPos below = cursor.below();
            if (!server.getBlockState(below).getCollisionShape(server, below).isEmpty()) {
                return new Vec3(pos.x, cursor.getY(), pos.z);
            }
            cursor.move(0, -1, 0);
        }
        return pos;
    }

    /** Mode repos : la tortue rejoint sa place et s'endort ; elle se reveille des qu'un travail de sa zone se presente. */
    private void resting(ServerLevel server, RoboticHubData hub) {
        setWorking(false, null);
        idleTicks++;
        if (idleTicks % 40 == 0) {
            if (!restValid(server)) {
                setSleeping(false);
                restOrder = -1;
                restSpot = null;
                enterIdle(server);
                return;
            }
            if (cargo.isFull() || !cargo.isEmpty() && !dockDeposit) {
                //Rien a faire : on va d'abord vider sa soute
                dockDeposit = true;
                setSleeping(false);
                setIdleState(server, false);
                deposit = null;
                depositTries = 0;
                phase = Phase.DEPOSIT;
                return;
            }
            Job next = RoboticsScheduler.wake(server, this, hub, restOrder);
            if (next != null) {
                assign(next);
                return;
            }
        }
        if (restSpot == null) {
            enterIdle(server);
            return;
        }
        if (isSleeping() && !onGround()) {
            //Plus de sol sous elle (bloc casse...) : elle se reveille et retrouve le sol
            setSleeping(false);
            restSpot = groundBelow(server, position());
        }
        if (!isSleeping()) {
            Move move = travel(server, restSpot, 1.2, true);
            if (move == Move.ARRIVED) {
                getNavigation().stop();
                if (onGround() && !jets) {
                    //Jamais en l'air : elle ne s'endort que posee au sol
                    setDeltaMovement(Vec3.ZERO);
                    setSleeping(true);
                    playSound(SoundEvents.TURTLE_SHAMBLE, 0.4F, 0.7F);
                } else if (idleTicks % 60 == 59) {
                    restSpot = groundBelow(server, position());
                }
            }
        } else {
            getNavigation().stop();
        }
    }

    private void setIdleState(ServerLevel server, boolean idle) {
        RoboticsScheduler.setIdle(server, this, idle);
    }

    /** Deja passee deposer sa cargaison avant de rentrer (on ne repasse pas en boucle si aucun stockage n'en veut). */
    private boolean dockDeposit;

    /** Avant de rentrer ou d'attendre : s'il reste de la cargaison, on la depose d'abord au stockage. True si on part deposer. */
    private boolean depositFirst(RoboticHubData hub) {
        if (cargo.isEmpty() || dockDeposit || hub.isRecalling()) {
            return false;
        }
        dockDeposit = true;
        deposit = null;
        depositTries = 0;
        resetMove();
        phase = Phase.DEPOSIT;
        return true;
    }

    /** Attente devant son hub, prete a repartir ; au-dela d'une minute (ou avec des objets a bord), elle rentre. */
    private void idling(ServerLevel server, RoboticHubData hub) {
        setWorking(false, null);
        if (lingering(server)) {
            if (++idleTicks % 10 == 0) {
                Job next = RoboticsScheduler.next(server, this, null, hub, lingerOrder);
                if (next != null) {
                    lingerOrder = -1;
                    assign(next);
                    return;
                }
            }
            setDeltaMovement(getDeltaMovement().multiply(0.6, 1, 0.6));
            return;
        }
        if (depositFirst(hub)) {
            setIdleState(server, false);
            return;
        }
        if (++idleTicks > IDLE_TIMEOUT || cargo.hasItems()) {
            setIdleState(server, false);
            phase = Phase.RETURNING;
            resetMove();
            return;
        }
        if (idleTicks % 20 == 0) {
            Job next = RoboticsScheduler.next(server, this, null, hub);
            if (next != null) {
                assign(next);
                return;
            }
        }
        //Les relais etendent le reseau et rechargent, mais ne servent jamais de lieu d'attente : on attend devant son hub
        travel(server, hub.idleSpot(this), 1.5, true);
    }

    private void returning(ServerLevel server, RoboticHubData hub) {
        setWorking(false, null);
        //Jamais de cargaison ramenee au hub s'il y a un stockage pour elle
        if (depositFirst(hub)) {
            return;
        }
        RoboticHubData.Bay bay = hub.nearestBay(position());
        if (bay == null) {
            dissolve(server);
            return;
        }
        Vec3 dock = bay.exit(kind);
        Move move = travel(server, dock, kind.flying ? 1.1 : 1.6, true);
        if (move == Move.ARRIVED) {
            hub.dock(server, this, completed);
            discard();
        }
    }

    /** Lache le travail en cours (rappel) ; {@code blacklist} : il est impossible. */
    private void dropJob(ServerLevel server, boolean blacklist) {
        if (job != null) {
            if (blacklist) {
                RoboticsScheduler.abandon(server, job);
            } else {
                RoboticsScheduler.release(server, job);
            }
            job = null;
        }
    }

    /** L'unite disparait sans rentrer : sa soute tombe au sol. */
    private void dissolve(ServerLevel server) {
        dropJob(server, false);
        leaveCharger();
        setIdleState(server, false);
        for (ItemStack stack : cargo.items) {
            spawnAtLocation(stack);
        }
        cargo.items.clear();
        server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.4, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        discard();
    }

    private void setWorking(boolean working, @Nullable BlockPos beam) {
        if (entityData.get(WORKING) != working) {
            entityData.set(WORKING, working);
        }
        Optional<BlockPos> wanted = Optional.ofNullable(beam);
        if (!entityData.get(BEAM).equals(wanted)) {
            entityData.set(BEAM, wanted);
        }
    }

    // ------------------------------------------------------------------
    // Deplacement
    // ------------------------------------------------------------------

    private void resetMove() {
        moveTarget = null;
        cruiseY = Double.NaN;
        route = null;
        routeFails = 0;
        routeStall = 0;
        if (!kind.flying) {
            jets = false;
            noPhysics = false;
            setNoGravity(false);
        }
    }

    // ------------------------------------------------------------------
    // Deplacement : chemins calcules (RobotPathfinder), au sol ou en vol
    // ------------------------------------------------------------------

    /** Chemin suivi (points), son but et sa portee, s'il est complet, en vol ou non. */
    @Nullable
    private List<Vec3> route;
    private int routeIndex, routeAge, routeStall, routeFails, routeCheck;
    @Nullable
    private Vec3 routeGoal;
    private boolean routeComplete, routeAir;
    private double routeBest;

    /**
     * Avance vers un point, jusqu'a portee {@code reach}. {@code mustArrive} : il faut y arriver (retour, recharge). Toutes les unites
     * au sol finissent en vol propulse en dernier recours quand aucun chemin ne mene au but.
     */
    private Move travel(ServerLevel server, Vec3 target, double reach, boolean mustArrive) {
        if (moveTarget == null || moveTarget.distanceToSqr(target) > 2.25) {
            moveTarget = target;
            flyBest = Double.MAX_VALUE;
            flyStuck = 0;
            cruiseY = Double.NaN;
            route = null;
            routeFails = 0;
            routeStall = 0;
        }
        if (position().distanceToSqr(target) <= reach * reach) {
            setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
            if (jets) {
                jets = false;
                noPhysics = false;
                setNoGravity(false);
                entityData.set(FX, (byte) 0);
            }
            route = null;
            return Move.ARRIVED;
        }
        if (jets && !kind.flying) {
            jetsTicks++;
            if (++jetsCheck >= 30 && jetsTicks > 80 && tickCount > noLandingUntil) {
                jetsCheck = 0;
                landIfWalkable(server, target, reach);
            }
        } else {
            if (jetsTicks > 0 && landedAt > 0 && tickCount - landedAt < 400) {
                //Les propulseurs reprennent juste apres un atterrissage : l'unite n'etait pas sortie d'affaire, on la laisse voler
                noLandingUntil = tickCount + 1200;
            }
            jetsTicks = 0;
        }
        if (kind.flying || jets) {
            fly(server, target, reach);
            return Move.MOVING;
        }
        return walk(server, target, reach, mustArrive);
    }

    private int jetsCheck, jetsTicks, landedAt, noLandingUntil;

    /**
     * Unite au sol sur ses propulseurs : des qu'un chemin a pied mene au but depuis le sol sous elle (a quelques blocs), elle coupe les
     * propulseurs et reprend la marche.
     */
    private void landIfWalkable(ServerLevel server, Vec3 target, double reach) {
        BlockPos.MutableBlockPos at = BlockPos.containing(position()).mutable();
        int drop = 0;
        while (drop < 5 && at.getY() > server.getMinBuildHeight() && server.getBlockState(at.below()).getCollisionShape(server, at.below()).isEmpty()) {
            at.move(0, -1, 0);
            drop++;
        }
        if (drop >= 5 || !server.getFluidState(at).isEmpty() || !RobotPathfinder.permit(server, this)) {
            return;
        }
        //Sortie d'affaire : rien autour du corps a l'arrivee au sol, et la place libre au-dessus
        AABB body = getBoundingBox().move(0, at.getY() - getY(), 0).inflate(0.2, 0, 0.2);
        if (!server.noCollision(this, body) || !server.noCollision(this, body.move(0, 1, 0))) {
            return;
        }
        Vec3 ground = new Vec3(getX(), at.getY(), getZ());
        RobotPathfinder.Route found = RobotPathfinder.ground(server, ground, target, Math.max(0.6, reach - 0.25), bodyBlocks(), 6000, true);
        //Un chemin complet qui part bien d'ici (pas d'une case voisine inaccessible)
        if (found != null && found.complete() && !found.isEmpty() && horizontal(found.points().get(0), ground) < 1.2
            && Math.abs(found.points().get(0).y - ground.y) < 1.1) {
            landedAt = tickCount;
            jets = false;
            noPhysics = false;
            setNoGravity(false);
            entityData.set(FX, (byte) 0);
            route = null;
            routeFails = 0;
        }
    }

    /** Vitesse de vol (blocs par tick). */
    private double flySpeed() {
        double speed = 0.34 * kind.speed * getTier().speed * stats.speed();
        if (!kind.flying) {
            speed *= 0.8;
        }
        return battery <= 0 ? speed * 0.3 : speed;
    }

    /** Hauteur du corps en blocs entiers (pour les chemins). */
    private int bodyBlocks() {
        return Math.max(1, Mth.ceil(getBbHeight() - 0.05));
    }

    /**
     * Marche : chemin A* au bloc pres (routes preferees, portee d'action), recalcule si le but bouge, s'il est partiel, ou si l'unite est
     * genee. Sans aucun chemin : vol propulse (dernier recours, pour toutes les unites).
     */
    private Move walk(ServerLevel server, Vec3 target, double reach, boolean mustArrive) {
        noPhysics = false;
        setNoGravity(false);
        Vec3 pos = position();
        routeAge++;
        boolean stale = route == null || routeAir || routeGoal == null || routeGoal.distanceToSqr(target) > 2.25 || routeAge > 400;
        if (stale) {
            if (routeAge < 6 && route == null && routeFails > 0) {
                //Pas plus d'un calcul tous les quelques ticks quand ca echoue
                return Move.MOVING;
            }
            if (!RobotPathfinder.permit(server, this)) {
                return Move.MOVING;
            }
            route = null;
            routeAge = 0;
            routeAir = false;
            RobotPathfinder.Route found = RobotPathfinder.ground(server, pos, target, Math.max(0.6, reach - 0.25), bodyBlocks(), mustArrive ? 9000 : 5000, true);
            if (found == null || found.isEmpty()) {
                routeFails++;
                if (routeFails >= 2) {
                    //Dernier recours : propulseurs
                    if (!mustArrive) {
                        RoboticsScheduler.groundUnreachable(server, BlockPos.containing(target));
                    }
                    jets = true;
                    routeFails = 0;
                    playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5F, 1.4F);
                }
                return Move.MOVING;
            }
            route = found.points();
            routeComplete = found.complete();
            routeGoal = target;
            routeIndex = 0;
            routeStall = 0;
            routeBest = Double.MAX_VALUE;
            getNavigation().stop();
        }
        //Suivre le chemin
        List<Vec3> points = route;
        while (routeIndex < points.size() - 1) {
            Vec3 node = points.get(routeIndex);
            Vec3 next = points.get(routeIndex + 1);
            boolean reached = horizontal(pos, node) < 0.45 && Math.abs(pos.y - node.y) < 1.2;
            //Raccourci : le point suivant est deja plus proche (virage pris en diagonale)
            boolean passed = horizontal(pos, next) < horizontal(node, next) && Math.abs(next.y - pos.y) < 0.6 && Math.abs(node.y - pos.y) < 0.6;
            if (reached || passed) {
                routeIndex++;
                routeBest = Double.MAX_VALUE;
            } else {
                break;
            }
        }
        Vec3 node = points.get(Math.min(routeIndex, points.size() - 1));
        if (routeIndex >= points.size() - 1 && horizontal(pos, node) < 0.45 && Math.abs(pos.y - node.y) < 1.2) {
            if (routeComplete && routeGoal != null && routeGoal.distanceToSqr(target) < 0.01) {
                //Chemin complet : sa derniere case est a portee du but, on y est
                route = null;
                getNavigation().stop();
                return Move.ARRIVED;
            }
            //Bout d'un chemin partiel (ou but deplace) : on recalcule d'ici
            route = null;
            return Move.MOVING;
        }
        getMoveControl().setWantedPosition(node.x, node.y, node.z, 1.0);
        //Eau : on nage en surface
        if (isInWater() && getFluidHeight(FluidTags.WATER) > getFluidJumpThreshold()) {
            getJumpControl().jump();
        }
        //Gene : pas de progres vers le point du chemin
        if (++routeCheck >= 15) {
            routeCheck = 0;
            double distance = pos.distanceTo(node);
            if (distance < routeBest - 0.2) {
                routeBest = distance;
                routeStall = 0;
            } else if (++routeStall == 2) {
                getJumpControl().jump();
            } else if (routeStall >= 4) {
                //Le monde a change ou l'unite est coincee : nouveau calcul ; si ca se repete, propulseurs
                route = null;
                routeFails++;
                routeStall = 0;
                if (routeFails >= 3) {
                    if (!mustArrive) {
                        RoboticsScheduler.groundUnreachable(server, BlockPos.containing(target));
                    }
                    jets = true;
                    routeFails = 0;
                }
            }
        }
        return Move.MOVING;
    }

    /** Ticks passes sous l'eau d'affilee (coincee au fond : propulseurs). */
    private int underwater;

    /**
     * Unites au sol dans l'eau : elles remontent toujours a la surface (les flotteurs ne coulent pas) ; restees trop longtemps sous l'eau,
     * elles sortent aux propulseurs.
     */
    private void swim() {
        if (jets || !isInWater()) {
            underwater = 0;
            return;
        }
        double depth = getFluidHeight(FluidTags.WATER);
        if (depth > 0.35) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x, Math.max(motion.y, depth > 0.9 ? 0.14 : 0.06), motion.z);
        }
        if (isUnderWater()) {
            if (++underwater > 100) {
                underwater = 0;
                jets = true;
                route = null;
                playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5F, 1.4F);
            }
        } else {
            underwater = 0;
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /**
     * Vol (drones, et propulseurs des unites au sol en dernier recours) : chemin en vol (ligne droite, par-dessus, ou A* en 3D) suivi avec
     * inertie, freinage seulement a l'approche du but. Sans chemin : on monte au-dessus du relief ; vraiment bloquee plus de 10 s, un
     * instant en vol fantome pour se degager.
     */
    private void fly(ServerLevel server, Vec3 target, double reach) {
        boolean ghost = ghostTicks > 0;
        if (ghostTicks > 0) {
            ghostTicks--;
        }
        noPhysics = ghost;
        setNoGravity(true);
        Vec3 pos = position();
        routeAge++;
        if ((route == null || !routeAir || routeGoal == null || routeGoal.distanceToSqr(target) > 2.25 || routeAge > (routeComplete ? 300 : 60)
            || horizontalCollision && routeAge > 20) && RobotPathfinder.permit(server, this)) {
            routeAge = 0;
            routeAir = true;
            routeGoal = target;
            routeIndex = 0;
            RobotPathfinder.Route found = RobotPathfinder.air(server, pos, target, Math.max(0.5, reach - 0.3), getBbWidth(), getBbHeight() + 0.1, 5000);
            route = found == null || found.isEmpty() ? null : found.points();
            routeComplete = found != null && found.complete();
            if (route == null) {
                //Aucun chemin : on montera au-dessus du relief
                routeAir = true;
            }
        }
        Vec3 goal;
        boolean last;
        if (route != null) {
            while (routeIndex < route.size() - 1 && route.get(routeIndex).distanceToSqr(pos) < 0.5 * 0.5) {
                routeIndex++;
            }
            goal = route.get(routeIndex);
            last = routeIndex >= route.size() - 1;
            if (last && !routeComplete && goal.distanceToSqr(pos) < 0.6 * 0.6) {
                route = null;
            }
        } else {
            //Repli : monter puis filer au-dessus du relief
            double dx = target.x - pos.x, dz = target.z - pos.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (Double.isNaN(cruiseY) || ++cruiseAge > 40) {
                cruiseAge = 0;
                cruiseY = cruiseAltitude(server, pos, target);
            }
            if (horizontalCollision) {
                cruiseY = Math.max(cruiseY, pos.y + 2);
            }
            goal = horizontal > 1 ? (pos.y < cruiseY - 1 && horizontal > 3 ? new Vec3(pos.x, cruiseY, pos.z) : new Vec3(target.x, Math.max(cruiseY, target.y), target.z))
                                  : target;
            last = horizontal <= 1;
        }
        Vec3 dir = goal.subtract(pos);
        double length = dir.length();
        double max = flySpeed(), accel = max * 0.16;
        //Freinage seulement au dernier point (sinon on enchaine les virages a vitesse de croisiere)
        double allowed = last ? Math.min(max, Math.sqrt(2 * accel * Math.max(0, length - 0.1))) : max;
        Vec3 wanted = length < 1e-4 ? Vec3.ZERO : dir.scale(allowed / length);
        Vec3 change = wanted.subtract(getDeltaMovement());
        double changeLength = change.length();
        if (changeLength > accel) {
            change = change.scale(accel / changeLength);
        }
        Vec3 velocity = getDeltaMovement().add(change);
        setDeltaMovement(velocity);
        //Vraiment bloquee (10 s sans progres) : degagement en vol fantome
        if (++flyCheck >= 20) {
            flyCheck = 0;
            double distance = pos.distanceTo(target);
            if (distance < flyBest - 0.5) {
                flyBest = distance;
                flyStuck = 0;
            } else if (++flyStuck >= 10) {
                flyStuck = 0;
                ghostTicks = 40;
                flyBest = Double.MAX_VALUE;
                route = null;
            }
        }
        if (velocity.horizontalDistanceSqr() > 1e-4) {
            float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        if (jets) {
            entityData.set(FX, (byte) 2);
        }
    }

    private int ghostTicks, flyCheck, flyStuck;
    private double flyBest = Double.MAX_VALUE;

    /** Altitude de croisiere : le plus haut relief sous la trajectoire, plus une marge. */
    private double cruiseAltitude(ServerLevel server, Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        int samples = (int) Math.min(48, Math.max(2, length / 2));
        int top = Integer.MIN_VALUE;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            int x = Mth.floor(from.x + dx * t), z = Mth.floor(from.z + dz * t);
            if (server.hasChunk(x >> 4, z >> 4)) {
                top = Math.max(top, server.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
            }
        }
        if (top == Integer.MIN_VALUE) {
            top = (int) Math.max(from.y, to.y);
        }
        return Math.max(top + 2.5, to.y);
    }

    // ------------------------------------------------------------------
    // Portillons et portes
    // ------------------------------------------------------------------

    /** Portillons et portes ouverts par l'unite, a refermer derriere elle. */
    private final Map<BlockPos, Integer> opened = new HashMap<>();

    /** Ouvre les portillons et portes en bois du chemin juste devant, et referme ceux qu'elle a passes. */
    private void passGates(ServerLevel server) {
        if (route != null && !routeAir) {
            int end = Math.min(route.size(), routeIndex + 3);
            for (int i = Math.max(0, routeIndex - 1); i < end; i++) {
                Vec3 point = route.get(i);
                if (point.distanceToSqr(position()) < 2.6 * 2.6) {
                    BlockPos node = BlockPos.containing(point);
                    open(server, node);
                    open(server, node.above());
                }
            }
        }
        //Refermer derriere soi (sans enfermer un animal dans le portillon)
        if (!opened.isEmpty()) {
            var iterator = opened.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                BlockPos pos = entry.getKey();
                boolean far = Vec3.atBottomCenterOf(pos).distanceToSqr(position()) > 2.2 * 2.2;
                if (far && server.getEntities((Entity) null, new AABB(pos), e -> e instanceof LivingEntity).isEmpty() || entry.getValue() + 400 < tickCount) {
                    setGate(server, pos, false);
                    iterator.remove();
                }
            }
        }
    }

    private void open(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        boolean gate = state.getBlock() instanceof FenceGateBlock, door = state.getBlock() instanceof DoorBlock door1 && door1.type().canOpenByHand();
        //1.9 (MAJ RP, bloc 4) : une unite d'un autre territoire n'ouvre pas les portes d'un claim
        if ((gate || door) && !state.getValue(BlockStateProperties.OPEN) && RoboticsWorld.canBreak(server, pos, state) && ClanAgents.canAct(server, home, pos)) {
            setGate(server, pos, true);
            opened.put(pos.immutable(), tickCount);
        }
    }

    private static void setGate(ServerLevel server, BlockPos pos, boolean open) {
        BlockState state = server.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(null, server, state, pos, open);
        } else if (state.getBlock() instanceof FenceGateBlock && state.getValue(BlockStateProperties.OPEN) != open) {
            server.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            server.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 0.8F, 1);
        }
    }

    /** Vol (drones, vol propulse, bond) : on applique directement la vitesse voulue ; sinon marche normale. */
    @Override
    public void travel(@NotNull Vec3 input) {
        if (isSleeping()) {
            //Endormie : immobile (ni glissade, ni rotation), seule la gravite s'applique
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y - 0.08) * 0.98, 0);
            move(MoverType.SELF, getDeltaMovement());
            float yaw = getYRot();
            yBodyRot = yBodyRotO = yHeadRot = yHeadRotO = yaw;
            setXRot(0);
            calculateEntityAnimation(false);
            return;
        }
        if ((kind.flying || jets) && isControlledByLocalInstance()) {
            move(MoverType.SELF, getDeltaMovement());
            calculateEntityAnimation(false);
        } else {
            super.travel(input);
        }
    }

    // ------------------------------------------------------------------
    // Effets client
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            return;
        }
        if (isWorking()) {
            Optional<BlockPos> beam = getBeam();
            if (beam.isPresent() && random.nextInt(2) == 0) {
                BlockPos pos = beam.get();
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                      pos.getZ() + random.nextDouble(), 0, 0.05, 0);
            }
        }
        int fx = getEffect();
        if (fx == 1 && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * getBbHeight(),
                  getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
        } else if (fx == 2) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), 0, -0.08, 0);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, -0.04, 0);
        }
        if (kind.flying && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.CLOUD, getX(), getY() - 0.1, getZ(), 0, -0.04, 0);
        }
    }

    // ------------------------------------------------------------------
    // Interactions et regles
    // ------------------------------------------------------------------

    @NotNull
    @Override
    protected InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            //Maj + clic droit : fiche de l'unite (nom, soute, travail, rappel)
            if (player instanceof ServerPlayer serverPlayer && level() instanceof ServerLevel server) {
                PacketRobotUnit.sendInfo(serverPlayer, this, server);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) {
            player.displayClientMessage(Component.translatable("robotics.pioneers.unit.status",
                  Component.translatable(getTask().getTranslationKey()), Math.round(cargo.fill() * 100),
                  Component.translatable("robotics.pioneers.unit.phase." + phase.name().toLowerCase(Locale.ROOT)),
                  Math.round(batteryRatio() * 100)).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Fiche de l'unite pour son interface (Maj + clic droit). */
    public CompoundTag infoTag(ServerLevel server) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", getId());
        tag.putByte("kind", (byte) kind.ordinal());
        tag.putByte("tier", (byte) getTier().ordinal());
        tag.putString("name", hasCustomName() ? getCustomName().getString() : "");
        tag.putByte("task", (byte) getTask().ordinal());
        tag.putByte("phase", (byte) phase.ordinal());
        tag.putFloat("battery", (float) batteryRatio());
        tag.putBoolean("sleeping", isSleeping());
        tag.put("cargo", cargo.save());
        tag.putInt("stacks", cargo.stacks);
        tag.putString("job", Component.Serializer.toJson(describeJob(server)));
        tag.putInt("order", job != null && job.group >= 0 ? job.group : restOrder);
        if (moveTarget != null) {
            tag.putLong("target", BlockPos.containing(moveTarget).asLong());
        }
        if (home != null) {
            tag.putLong("home", home.asLong());
        }
        return tag;
    }

    private Component describeJob(ServerLevel server) {
        Component phaseName = Component.translatable("robotics.pioneers.unit.phase." + phase.name().toLowerCase(Locale.ROOT));
        if (job == null) {
            return phaseName;
        }
        Component task = Component.translatable(job.task.getTranslationKey());
        return switch (job.type) {
            case TRIP -> {
                Job.Stop load = job.stops.stream().filter(Job.Stop::load).findFirst().orElse(null);
                Job.Stop drop = job.stops.stream().filter(stop -> !stop.load()).findFirst().orElse(null);
                if (load == null || drop == null) {
                    yield task;
                }
                yield Component.translatable("robotics.pioneers.unit.job.trip", load.res().name(), positionText(load.at().pos()),
                      positionText(drop.at().pos()));
            }
            case BLOCK -> job.pos == null ? task : Component.translatable("robotics.pioneers.unit.job.block", task,
                  server.getBlockState(job.pos).getBlock().getName(), positionText(job.pos));
            case ENTITY -> {
                Entity entity = server.getEntity(job.entityId);
                yield Component.translatable("robotics.pioneers.unit.job.entity", task, entity == null ? Component.literal("?") : entity.getDisplayName());
            }
        };
    }

    private static Component positionText(BlockPos pos) {
        return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** Nouveau nom (vide : retire le nom), garde aussi par l'objet de l'unite dans son hub. */
    public void rename(ServerLevel server, String name) {
        Component component = name.isBlank() ? null : Component.literal(name);
        setCustomName(component);
        setCustomNameVisible(component != null);
        RoboticHubData hub = getHubData();
        if (hub != null) {
            hub.renameSlot(getSlot(), component);
        }
    }

    /** Rappel de cette unite seule : elle lache son travail et rentre au hub. */
    public void recall(ServerLevel server) {
        restOrder = -1;
        restSpot = null;
        setSleeping(false);
        dropJob(server, false);
        leaveCharger();
        setIdleState(server, false);
        RoboticsScheduler.leave(server, this);
        resetMove();
        phase = Phase.RETURNING;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && player.isCreative() && level() instanceof ServerLevel server) {
            //Le joueur en creatif renvoie l'unite a son hub
            RoboticHubData hub = getHubData();
            dropJob(server, false);
            leaveCharger();
            setIdleState(server, false);
            if (hub != null) {
                hub.dock(server, this, false);
                discard();
            } else {
                dissolve(server);
            }
            return false;
        }
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            setIdleState(server, false);
            leaveCharger();
            RoboticsScheduler.leave(server, this);
            if (reason != RemovalReason.DISCARDED && reason != RemovalReason.KILLED) {
                //Dechargement du monde : l'unite rentre directement
                dropJob(server, false);
                RoboticHubData hub = getHubData();
                if (hub != null) {
                    hub.dock(server, this, completed);
                }
            }
        }
        super.remove(reason);
    }

    /** Les unites ne se poussent pas entre elles (pas d'embouteillage aux portes et aux relais). */
    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return !(entity instanceof RobotUnitEntity) && super.canCollideWith(entity);
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        if (!(entity instanceof RobotUnitEntity)) {
            super.doPush(entity);
        }
    }

    @Override
    public void push(@NotNull Entity entity) {
        if (!(entity instanceof RobotUnitEntity)) {
            super.push(entity);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, @NotNull DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    /** Objet de l'unite (si elle redevient un objet). */
    public ItemStack asItem() {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(PiRobotics.unitItemId(kind, getTier())));
    }

    /** Le chemin passe par les portillons fermes comme par des portes (l'unite les ouvre). */
    private static final class GateNodeEvaluator extends WalkNodeEvaluator {

        @NotNull
        @Override
        public BlockPathTypes getBlockPathType(@NotNull BlockGetter level, int x, int y, int z) {
            BlockPathTypes type = super.getBlockPathType(level, x, y, z);
            if (type == BlockPathTypes.FENCE && level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof FenceGateBlock) {
                return BlockPathTypes.DOOR_WOOD_CLOSED;
            }
            return type;
        }
    }
}
