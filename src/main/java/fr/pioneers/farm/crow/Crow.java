package fr.pioneers.farm.crow;

import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.device.DeviceSites;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.device.ScarecrowBlock;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Corbeau (1.3.1) : vient en bande ravager les champs ({@link CrowSpawner}). Il vise une culture avancee que nul epouvantail ne protege
 * ({@link ScarecrowBlock#SCARE_RADIUS}), s'y pose, picore {@link #PECK} ticks puis la ravage ({@link FarmCropBlock#ravage}) ; apres
 * {@link #appetite} cultures, il s'en va et disparait loin des joueurs. Il fuit le joueur (7 blocs, 4 accroupi) et les epouvantails (14 blocs, des la pose ; 1.4.2),
 * entrainant la bande. Vol a la maniere du perroquet ; {@link #PECKING} anime le bec.
 * <p>
 * 1.0.5-beta (lot 4, spec §3 « population persistante ») : le corbeau ne disparait plus. Repu (ou las de chercher), il s'eloigne des
 * joueurs puis SE REPOSE un jour de jeu ({@link #REST}) sans piller, et reprend sa vie de corbeau de la zone ; il est compte dans le groupe
 * des oiseaux du repeuplement ({@link fr.pioneers.world.fauna.FaunaRegistry}). {@link CrowSpawner} appelle d'abord les corbeaux de la zone vers
 * un champ ({@link #summon}, {@link CallGoal}). Reglage {@code fauna.enabled} faux : comportement d'avant (il disparait loin des joueurs).
 * <p>
 * 1.0.2-beta (Guillaume : « ameliorer la fluidite de deplacement des corbeaux, eviter la chute libre ; ils ne sont pas attires par les champs :
 * qu'ils aillent en limite de zone de l'epouvantail, comme des charognards ») : VOL PLANE ({@link #travel} : vitesse de descente bornee a
 * {@link #GLIDE_SINK}, {@link #LANDING_SINK} pres du sol, elan horizontal garde en planant), battements quand il monte ou avance, ailes
 * tendues quand il plane (client : {@link #glide}) ; l'epouvantail ne les chasse plus au loin : un corbeau ENTRE dans sa zone en ressort
 * jusqu'au bord ({@link #shoo}), et des que des cultures sont gardees il vient roder AU BORD de la zone ({@link EdgeGoal} : tourne en
 * cercle a quelques blocs de haut, ou se pose sur le bord, picore le sol et croasse), a l'affut d'une culture laissee sans garde.
 */
public class Crow extends PathfinderMob implements FlyingAnimal {

    public static final int PECK = 60, FLEE = 120;
    /** 1.0.5-beta (lot 4) : repos apres un repas ou une longue errance (un jour de jeu) ; duree d'un appel vers un champ. */
    public static final int REST = 24000, CALL = 900;
    /** 1.0.2-beta : vitesse de descente maximale en vol plane (blocs/tick), et pres du sol (atterrissage doux). */
    public static final double GLIDE_SINK = 0.11, LANDING_SINK = 0.05;
    /** 1.0.2-beta : distance au bord de la zone de l'epouvantail ou les corbeaux rodent (blocs au-dela du rayon). */
    public static final double EDGE_MARGIN = 1.5;
    private static final EntityDataAccessor<Boolean> PECKING = SynchedEntityData.defineId(Crow.class, EntityDataSerializers.BOOLEAN);
    private int raided, spooked, appetite = 3, life, resting, calling;
    @Nullable
    private BlockPos called;
    private boolean leaving;
    /** Cote client : battements (comme le perroquet). */
    public float flap, flapSpeed, oFlap, oFlapSpeed;
    private float flapping = 1, nextFlap = 1;
    /** Cote client (1.0.2-beta) : part « vol plane » (0 battements .. 1 ailes tendues), lissee. */
    public float glide, oGlide;
    /** Cote client (1.0.5-beta lot 4) : ticks depuis que le corbeau a quitte le sol (animation de l'envol). */
    public int airTicks;

    public Crow(EntityType<? extends Crow> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 10, false);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1);
        setPathfindingMalus(BlockPathTypes.WATER, -1);
        appetite = 3 + level.random.nextInt(3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.FLYING_SPEED, 0.55).add(Attributes.MOVEMENT_SPEED, 0.25)
              .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PECKING, false);
    }

    public boolean pecking() {
        return entityData.get(PECKING);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        goalSelector.addGoal(2, new FleeGoal(this));
        goalSelector.addGoal(3, new LeaveGoal(this));
        goalSelector.addGoal(4, new RaidGoal(this));
        goalSelector.addGoal(5, new CallGoal(this));
        goalSelector.addGoal(6, new EdgeGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
    }

    @Override
    public boolean isFlying() {
        return !onGround();
    }

    /**
     * 1.0.2-beta : vol plane. Le deplacement vanilla (pesanteur 0,08 quand le controle de vol est au repos) faisait tomber le corbeau comme une
     * pierre entre deux ordres : apres lui, la vitesse de descente est bornee ({@link #GLIDE_SINK}, {@link #LANDING_SINK} a moins de deux blocs
     * du sol) et l'elan horizontal d'un corbeau qui plane s'use moins vite (il glisse au lieu de s'arreter net).
     */
    @Override
    public void travel(Vec3 input) {
        super.travel(input);
        if (onGround() || isInWater() || isInLava() || isPassenger()) {
            return;
        }
        Vec3 v = getDeltaMovement();
        boolean near = !level().noCollision(this, getBoundingBox().move(0, -2, 0));
        double sink = near ? LANDING_SINK : GLIDE_SINK;
        if (v.y < -sink) {
            double h = Math.sqrt(v.x * v.x + v.z * v.z);
            //Planer : l'altitude perdue en moins garde un peu d'elan vers l'avant (sans depasser la vitesse de vol)
            double keep = h > 1e-3 && h < 0.3 ? Math.min(1.08, 0.3 / h) : 1;
            setDeltaMovement(v.x * keep, -sink, v.z * keep);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    /** 1.0.5-beta (lot 4) : persistant (faune) ; sans la faune persistante, il disparait loin des joueurs comme avant. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return !fr.pioneers.world.fauna.FaunaSettings.current().enabled();
    }

    /** Au repos (repu) : ni pillage ni ronde. */
    public boolean resting() {
        return resting > 0;
    }

    /**
     * 1.0.5-beta (lot 4) : appel vers un champ ({@link CrowSpawner}) : le corbeau y vole et cherche a piller ({@link CallGoal}, puis
     * {@link RaidGoal} a 16 blocs). Refuse s'il se repose, s'en va ou a peur.
     */
    public boolean summon(BlockPos field) {
        if (resting > 0 || leaving || spooked > 0) {
            return false;
        }
        called = field.immutable();
        calling = CALL;
        return true;
    }

    /** Repu ou las : repos d'un jour (sans la faune persistante : il disparait, comme avant). */
    void rest() {
        if (!fr.pioneers.world.fauna.FaunaSettings.current().enabled()) {
            discard();
            return;
        }
        leaving = false;
        resting = REST;
        raided = 0;
        life = 0;
        called = null;
        calling = 0;
        appetite = 3 + random.nextInt(3);
        getNavigation().stop();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            //Battements d'ailes (repris du perroquet)
            oFlap = flap;
            oFlapSpeed = flapSpeed;
            flapSpeed += (!onGround() && !isPassenger() ? 4 : -1) * 0.3F;
            flapSpeed = Mth.clamp(flapSpeed, 0, 1);
            if (!onGround() && flapping < 1) {
                flapping = 1;
            }
            flapping *= 0.9F;
            //1.0.2-beta : vitesse verticale lue sur la position (le client ne simule pas le corbeau) : il descend = il plane, ailes tendues ;
            //il monte ou file a l'horizontale = il bat des ailes (le vieux freinage du client, sans effet sur la position, est retire)
            oGlide = glide;
            double vy = getY() - yo;
            float want = onGround() ? 0 : vy < -0.025 ? 1 : vy > 0.02 ? 0 : glide;
            glide += (want - glide) * 0.15F;
            flap += flapping * 2 * (1 - 0.75F * glide);
            airTicks = onGround() ? 0 : Math.min(1000, airTicks + 1);
        } else {
            life++;
            if (spooked > 0) {
                spooked--;
            }
            if (resting > 0) {
                resting--;
                life = 0;
            }
            if (calling > 0 && --calling == 0) {
                called = null;
            }
            //Trop longtemps sans joueur a proximite, ou repu depuis longtemps : il s'en va
            if (life > 6000 && !leaving) {
                leaving = true;
            }
        }
    }

    // ------------------------------------------------------------------ sons

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return PiFarmDevices.CROW_CAW.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.PARROT_STEP, 0.15F, 1F);
    }

    @Override
    protected void onFlap() {
        playSound(SoundEvents.PARROT_FLY, 0.15F, 0.9F);
        nextFlap = flyDist + flapSpeed / 2;
    }

    @Override
    protected boolean isFlapping() {
        return flyDist > nextFlap;
    }

    @Override
    public float getVoicePitch() {
        return 0.9F + random.nextFloat() * 0.2F;
    }

    void caw() {
        level().playSound(null, getX(), getY(), getZ(), PiFarmDevices.CROW_CAW.get(), SoundSource.NEUTRAL, 1F, getVoicePitch());
    }

    // ------------------------------------------------------------------ peur

    /** Prend peur : fuit loin de {@code from} et entraine la bande (12 blocs). */
    public void scare(Vec3 from, boolean flock) {
        if (spooked > FLEE / 2) {
            return;
        }
        spooked = FLEE;
        entityData.set(PECKING, false);
        Vec3 away = position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        away = away.normalize().scale(14 + random.nextInt(8));
        getNavigation().moveTo(getX() + away.x, getY() + 6 + random.nextInt(5), getZ() + away.z, 1.7);
        caw();
        if (flock) {
            for (Crow c : level().getEntitiesOfClass(Crow.class, getBoundingBox().inflate(12))) {
                if (c != this) {
                    c.scare(from, false);
                }
            }
        }
    }

    public boolean spooked() {
        return spooked > 0;
    }

    /**
     * 1.0.2-beta : chasse par un epouvantail : s'envole jusqu'au BORD de sa zone (rayon + {@link #EDGE_MARGIN} + 1), en croassant ; ne
     * fuit pas plus loin (il reviendra roder au bord).
     */
    public void shoo(Vec3 scarecrow) {
        if (spooked > FLEE / 2) {
            return;
        }
        spooked = FLEE / 2;
        entityData.set(PECKING, false);
        Vec3 away = position().subtract(scarecrow).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        away = away.normalize().scale(ScarecrowBlock.SCARE_RADIUS + EDGE_MARGIN + 1);
        getNavigation().moveTo(scarecrow.x + away.x, scarecrow.y + 4 + random.nextInt(3), scarecrow.z + away.z, 1.4);
        caw();
    }

    // ------------------------------------------------------------------ sauvegarde

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("raided", raided);
        tag.putInt("appetite", appetite);
        tag.putBoolean("leaving", leaving);
        tag.putInt("life", life);
        tag.putInt("resting", resting);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        raided = tag.getInt("raided");
        appetite = Math.max(1, tag.getInt("appetite"));
        leaving = tag.getBoolean("leaving");
        life = tag.getInt("life");
        resting = tag.getInt("resting");
    }

    // ------------------------------------------------------------------ buts

    /** Fuite devant un joueur proche ou un epouvantail. */
    static final class FleeGoal extends Goal {

        private final Crow crow;

        FleeGoal(Crow crow) {
            this.crow = crow;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (crow.spooked > 0) {
                return true;
            }
            //1.4.2 : plus de filtre « tickCount % 10 » : le selecteur de buts n'evalue canUse qu'un tick sur deux selon la parite de
            //l'identifiant, si bien que la moitie des corbeaux ne voyaient jamais ni joueur ni epouvantail. L'epouvantail effraie un peu
            //au-dela de la zone qu'il protege.
            Player p = crow.level().getNearestPlayer(crow, 7);
            if (p != null && !p.isSpectator() && (!p.isShiftKeyDown() || crow.distanceToSqr(p) < 16)) {
                crow.scare(p.position(), true);
                return true;
            }
            //1.0.2-beta : l'epouvantail ne chasse plus le corbeau au loin : entre dans la zone, il en ressort jusqu'au bord (ou il rode)
            List<BlockPos> sc = DeviceSites.near(crow.level(), crow.getX(), crow.getY(), crow.getZ(), ScarecrowBlock.SCARE_RADIUS - 0.5,
                  DeviceSites.Kind.SCARECROW);
            if (!sc.isEmpty()) {
                BlockPos s = sc.get(0);
                crow.shoo(Vec3.atCenterOf(s));
                PiFarmDevices.award(crow.level(), s, "scare");
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return crow.spooked > 0 && !crow.getNavigation().isDone();
        }
    }

    /** Repu : s'eloigne des joueurs et disparait. */
    static final class LeaveGoal extends Goal {

        private final Crow crow;
        private int time;

        LeaveGoal(Crow crow) {
            this.crow = crow;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return crow.leaving && crow.spooked == 0;
        }

        @Override
        public void start() {
            time = 0;
            go();
        }

        private void go() {
            Player p = crow.level().getNearestPlayer(crow, 64);
            Vec3 away = p == null ? new Vec3(crow.random.nextDouble() - 0.5, 0, crow.random.nextDouble() - 0.5)
                                  : crow.position().subtract(p.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) {
                away = new Vec3(1, 0, 0);
            }
            away = away.normalize().scale(24);
            crow.getNavigation().moveTo(crow.getX() + away.x, crow.getY() + 10, crow.getZ() + away.z, 1.3);
        }

        @Override
        public void tick() {
            time++;
            Player p = crow.level().getNearestPlayer(crow, 48);
            if (p == null || time > 1200) {
                //1.0.5-beta (lot 4) : plus de disparition : il se pose quelque part et se repose un jour
                crow.rest();
                return;
            }
            if (crow.getNavigation().isDone()) {
                go();
            }
        }
    }

    /** Pillage : vole vers une culture avancee non protegee, picore, ravage. */
    static final class RaidGoal extends Goal {

        private final Crow crow;
        @Nullable
        private BlockPos target;
        private int peck, cooldown, moving;

        RaidGoal(Crow crow) {
            this.crow = crow;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (crow.spooked > 0 || crow.leaving || crow.resting > 0 || --cooldown > 0) {
                return false;
            }
            cooldown = 30 + crow.random.nextInt(40);
            target = CrowSpawner.findCrop(crow.level(), crow.blockPosition(), 16, crow.random);
            return target != null;
        }

        @Override
        public void start() {
            peck = 0;
            moving = 0;
            fly();
        }

        private void fly() {
            crow.getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.1, target.getZ() + 0.5, 1.1);
        }

        private boolean valid() {
            if (target == null) {
                return false;
            }
            BlockState s = crow.level().getBlockState(target);
            return s.getBlock() instanceof FarmCropBlock crop && crop.getAge(s) >= 3
                   && !DeviceSites.scared(crow.level(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5, ScarecrowBlock.SCARE_RADIUS);
        }

        @Override
        public boolean canContinueToUse() {
            return valid() && crow.spooked == 0 && moving < 400;
        }

        @Override
        public void tick() {
            if (target == null) {
                return;
            }
            double dx = crow.getX() - (target.getX() + 0.5), dz = crow.getZ() - (target.getZ() + 0.5), dy = crow.getY() - target.getY();
            boolean there = dx * dx + dz * dz < 0.5 && dy > -0.6 && dy < 1.4;
            crow.getLookControl().setLookAt(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            if (!there) {
                moving++;
                crow.entityData.set(PECKING, false);
                if (crow.getNavigation().isDone() || moving % 40 == 0) {
                    fly();
                }
                return;
            }
            crow.getNavigation().stop();
            crow.entityData.set(PECKING, true);
            peck++;
            if (crow.level() instanceof ServerLevel level) {
                BlockState s = level.getBlockState(target);
                if (peck % 8 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), target.getX() + 0.5, target.getY() + 0.3, target.getZ() + 0.5, 3,
                          0.15, 0.1, 0.15, 0.02);
                    level.playSound(null, target, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.35F, 1.4F + crow.random.nextFloat() * 0.3F);
                }
                if (peck >= PECK && s.getBlock() instanceof FarmCropBlock crop) {
                    crop.ravage(level, target, s, 3);
                    crow.raided++;
                    if (crow.raided >= crow.appetite) {
                        crow.leaving = true;
                    }
                    if (crow.random.nextInt(2) == 0) {
                        crow.caw();
                    }
                    target = null;
                }
            }
        }

        @Override
        public void stop() {
            crow.entityData.set(PECKING, false);
            target = null;
        }
    }

    /**
     * 1.0.5-beta (lot 4) : appele vers un champ ({@link #summon}) : vole a 8..12 blocs au-dessus des cultures ; arrive a 12 blocs, l'appel
     * s'arrete et {@link RaidGoal} (priorite plus haute) prend la main s'il trouve une culture sans garde.
     */
    static final class CallGoal extends Goal {

        private final Crow crow;

        CallGoal(Crow crow) {
            this.crow = crow;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return crow.called != null && crow.spooked == 0 && !crow.leaving && crow.resting == 0
                   && crow.distanceToSqr(Vec3.atCenterOf(crow.called)) > 144;
        }

        @Override
        public void start() {
            fly();
        }

        private void fly() {
            BlockPos f = crow.called;
            if (f != null) {
                crow.getNavigation().moveTo(f.getX() + 0.5, f.getY() + 8 + crow.random.nextInt(5), f.getZ() + 0.5, 1.2);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (crow.getNavigation().isDone() || crow.tickCount % 60 == 0) {
                fly();
            }
        }

        @Override
        public void stop() {
            if (crow.called != null && crow.distanceToSqr(Vec3.atCenterOf(crow.called)) <= 144) {
                crow.called = null;
                crow.calling = 0;
            }
        }
    }

    /**
     * 1.0.2-beta : rodeur AU BORD de la zone d'un epouvantail qui garde des cultures (charognard) : un point du bord tourne vers le corbeau
     * (± 40 degres), puis, au hasard, il y tourne en cercle a 4..7 blocs du sol, ou s'y pose (bord libre et solide), picore le sol et croasse ;
     * 6 a 12 s, puis il cherche ailleurs (une culture sans garde passe avant : {@link RaidGoal}, priorite plus haute).
     */
    static final class EdgeGoal extends Goal {

        private final Crow crow;
        private BlockPos scarecrow;
        private Vec3 spot;
        private boolean landing;
        private int time, duration, cooldown;
        private double angle;

        EdgeGoal(Crow crow) {
            this.crow = crow;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /** Rayon ou le corbeau rode (bord de la zone + marge). */
        static double ring() {
            return ScarecrowBlock.SCARE_RADIUS + EDGE_MARGIN;
        }

        @Override
        public boolean canUse() {
            if (crow.spooked > 0 || crow.leaving || crow.resting > 0 || --cooldown > 0) {
                return false;
            }
            cooldown = 40 + crow.random.nextInt(40);
            List<BlockPos> sc = DeviceSites.near(crow.level(), crow.getX(), crow.getY(), crow.getZ(), 40, DeviceSites.Kind.SCARECROW);
            if (sc.isEmpty()) {
                return false;
            }
            //Le plus proche, s'il garde des cultures avancees
            BlockPos best = null;
            double d = Double.MAX_VALUE;
            for (BlockPos p : sc) {
                double q = p.distToCenterSqr(crow.position());
                if (q < d) {
                    d = q;
                    best = p;
                }
            }
            java.util.List<BlockPos> crops = new java.util.ArrayList<>();
            CrowSpawner.scan(crow.level(), best, ScarecrowBlock.SCARE_RADIUS, crops, null);
            if (crops.isEmpty()) {
                return false;
            }
            scarecrow = best;
            Vec3 c = Vec3.atBottomCenterOf(best);
            Vec3 to = crow.position().subtract(c);
            angle = Math.atan2(to.z, to.x) + (crow.random.nextDouble() - 0.5) * Math.toRadians(80);
            landing = crow.random.nextInt(3) > 0;
            spot = edgePoint(angle, landing);
            if (spot == null) {
                landing = false;
                spot = edgePoint(angle, false);
            }
            return spot != null;
        }

        /** Point du bord a l'angle {@code a} : au sol (dessus d'un bloc plein, air au-dessus) ou en l'air, 4..7 blocs au-dessus du sol. */
        private Vec3 edgePoint(double a, boolean ground) {
            double r = ring();
            int x = net.minecraft.util.Mth.floor(scarecrow.getX() + 0.5 + Math.cos(a) * r), z = net.minecraft.util.Mth.floor(scarecrow.getZ() + 0.5 + Math.sin(a) * r);
            if (!crow.level().isLoaded(new BlockPos(x, scarecrow.getY(), z))) {
                return null;
            }
            int top = crow.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            if (ground) {
                BlockPos under = new BlockPos(x, top - 1, z);
                BlockState s = crow.level().getBlockState(under);
                if (!s.isFaceSturdy(crow.level(), under, net.minecraft.core.Direction.UP) || !crow.level().getFluidState(under).isEmpty()) {
                    return null;
                }
                return new Vec3(x + 0.5, top, z + 0.5);
            }
            return new Vec3(x + 0.5, top + 4 + crow.random.nextInt(4), z + 0.5);
        }

        @Override
        public void start() {
            time = 0;
            duration = 120 + crow.random.nextInt(120);
            crow.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return crow.spooked == 0 && !crow.leaving && time < duration + 400;
        }

        @Override
        public void tick() {
            time++;
            double dx = crow.getX() - spot.x, dz = crow.getZ() - spot.z;
            boolean there = dx * dx + dz * dz < (landing ? 1.0 : 4.0);
            if (!there) {
                if (crow.getNavigation().isDone() || time % 40 == 0) {
                    crow.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
                }
                return;
            }
            if (time > duration) {
                time = duration + 400;            //fin : il repartira chercher (RaidGoal, ou un autre point du bord)
                return;
            }
            if (landing) {
                //Pose au bord : il picore le sol et croasse de temps en temps
                crow.getNavigation().stop();
                crow.entityData.set(PECKING, crow.onGround() && (time / 20) % 3 != 2);
                crow.getLookControl().setLookAt(scarecrow.getX() + 0.5, scarecrow.getY() + 1, scarecrow.getZ() + 0.5);
                if (crow.random.nextInt(140) == 0) {
                    crow.caw();
                }
                return;
            }
            //Ronde : il tourne autour de l'epouvantail sur le bord de sa zone (environ un tour en 20 s)
            angle += 2 * Math.PI / 400;
            Vec3 next = edgePoint(angle, false);
            if (next != null) {
                spot = new Vec3(next.x, spot.y, next.z);
                crow.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
            }
        }

        @Override
        public void stop() {
            crow.entityData.set(PECKING, false);
            cooldown = 60 + crow.random.nextInt(60);
        }
    }
}
