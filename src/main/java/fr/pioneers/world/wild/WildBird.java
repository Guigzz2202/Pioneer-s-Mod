package fr.pioneers.world.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 7) : base des OISEAUX sauvages (pic-vert, pigeon) : vol (commande de vol et navigation aerienne du perroquet vanilla,
 * descente planee, jamais de degats de chute), action synchronisee « en vol » ({@link Wild7Rules#FLY}) des qu'il quitte le sol, fuite a
 * tire-d'aile ({@link Wild7Goals.BirdFlee} : loin et en hauteur), et POSE ({@link Wild7Rules#PERCH}) : accroche a un tronc (pic-vert) ou
 * juche sur un toit (pigeon) ; accroche, l'oiseau ne tombe pas et ne bouge plus ({@link #cling}).
 */
public abstract class WildBird extends WildAnimal implements FlyingAnimal {

    /** Accroche a un tronc : position tenue et face (vers le tronc), ou null. */
    @Nullable
    Vec3 clingAt;
    @Nullable
    Direction clingFace;
    /** Nichoir qui l'a appele (il y vole puis se pose a cote), et patience restante (ticks). */
    @Nullable
    public BlockPos home;
    int homeTicks;

    protected WildBird(EntityType<? extends Animal> type, Level level, int kind) {
        super(type, level, kind);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Wild7Goals.BirdFlee(this));
        goalSelector.addGoal(2, new Wild7Goals.GoHome(this));
        addOwnGoals();
        goalSelector.addGoal(8, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    /** Buts propres (pose sur un tronc, sur un toit ; picorage, parade). */
    protected abstract void addOwnGoals();

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public boolean isFlying() {
        return !onGround() && clingAt == null;
    }

    /** Appel d'un nichoir : l'oiseau vole vers {@code house} (une minute de patience au plus). */
    public void summon(BlockPos house) {
        home = house.immutable();
        homeTicks = 1200;
    }

    public boolean clinging() {
        return clingAt != null;
    }

    /** Accroche l'oiseau a {@code at}, tourne vers {@code face} (null : decroche). */
    public void cling(@Nullable Vec3 at, @Nullable Direction face) {
        clingAt = at;
        clingFace = face;
        if (at != null) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            setPos(at);
            if (face != null) {
                float yaw = face.toYRot();
                setYRot(yaw);
                yBodyRot = yHeadRot = yaw;
            }
        }
        setNoGravity(at != null);
    }

    @Override
    public void travel(@NotNull Vec3 input) {
        if (clingAt != null) {
            setDeltaMovement(Vec3.ZERO);
            setPos(clingAt);
            if (clingFace != null) {
                setYRot(clingFace.toYRot());
                yBodyRot = getYRot();
            }
            return;
        }
        super.travel(input);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        Vec3 v = getDeltaMovement();
        if (clingAt == null && !onGround() && v.y < 0) {
            setDeltaMovement(v.multiply(1, 0.6, 1));                //descente planee
        }
        if (!level().isClientSide) {
            int a = action();
            if (clingAt == null && !onGround() && a != Wild7Rules.FLY && a != Wild7Rules.PERCH && !isInWater()) {
                setAction(Wild7Rules.FLY);
            } else if ((onGround() || isInWater()) && a == Wild7Rules.FLY) {
                setAction(WildRules.IDLE);
            }
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && clingAt != null) {
            cling(null, null);                                     //blesse, il lache prise et s'envole
        }
        return hit;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, @NotNull DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, @NotNull BlockState state, @NotNull BlockPos pos) {
    }

    @Override
    protected boolean isFlapping() {
        return isFlying() && tickCount % 4 == 0;
    }

    @Override
    protected void onFlap() {
        if (tickCount % 40 == 0) {
            playOwnQuiet();
        }
    }

    /** Bruit d'ailes discret en vol (pigeon) ; rien par defaut. */
    protected void playOwnQuiet() {
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.PARROT_STEP, 0.1F, 1.2F);
    }
}
