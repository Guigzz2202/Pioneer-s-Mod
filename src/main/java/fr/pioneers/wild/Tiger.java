package fr.pioneers.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 8, specification §4 « Jungle et foret tropicale ») : le TIGRE, predateur solitaire des jungles. Il CHASSE LE JOUEUR A VUE
 * (24 blocs, ligne de vue : {@link Wild8Rules#tigerHunts}) : A L'AFFUT, tapi, il approche lentement (cache dans les hautes herbes, il
 * attend tapi quand il se repose), puis BONDIT a 5 blocs ({@link Wild8Goals.Stalk}) et termine au corps a corps. Il CRAINT LE FEU, comme le
 * vrai loup (choix du lot 8 : un joueur qui tient une torche ou une lanterne a 6 blocs, un feu ou un feu de camp allume a 5 blocs le font
 * fuir et lacher sa proie). Predateur du socle. Robes : du Bengale, blanc (rare).
 */
public class Tiger extends WildAnimal implements Wild8Goals.Fearful {

    @Nullable
    Vec3 fear;
    int fearLeft, pounceCool, pounceLeft;
    /** Coups portes depuis la naissance (GameTests). */
    public int strikes;

    public Tiger(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild8Rules.TIGER);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Wild8Goals.FearFire(this));
        goalSelector.addGoal(2, new Wild8Goals.Stalk(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(7, new Wild8Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, e -> !isBaby() && scary(e)
              && Wild8Rules.tigerHunts(distanceTo(e), hasLineOfSight(e), fear != null)));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return Wild8Rules.coat(kind, random.nextDouble());
    }

    @Nullable
    @Override
    public Vec3 fear() {
        return fear;
    }

    /** Hautes herbes, fougeres ou bambous a l'endroit du tigre : il s'y cache a l'affut. */
    public boolean inTallGrass() {
        BlockState s = level().getBlockState(blockPosition());
        return s.is(Blocks.TALL_GRASS) || s.is(Blocks.GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN) || s.is(Blocks.BAMBOO);
    }

    /** Releve la peur : torche ou lanterne en main a 6 blocs, feu ou feu de camp allume a 5 blocs. */
    @Nullable
    Vec3 scan() {
        Player p = level().getNearestPlayer(getX(), getY(), getZ(), Wild8Rules.TIGER_FEAR_RANGE, e -> e instanceof Player pl && !pl.isSpectator()
              && GrayWolf.holdsFire(pl));
        if (p != null) {
            return p.position();
        }
        BlockPos c = blockPosition();
        int r = Wild7Rules.FEAR_FIRE_RANGE;
        //1.0.5-beta (lot 29) : palettes des sections d'abord (exact) : sans feu possible, pas de lecture des 605 cases
        if (!fr.pioneers.core.block.ScanFilter.mayContain(level(), c.offset(-r, -2, -r), c.offset(r, 2, r), GrayWolf::fire)) {
            return null;
        }
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
            if (GrayWolf.fire(level().getBlockState(b))) {
                return Vec3.atCenterOf(b);
            }
        }
        return null;
    }

    /** Bond vers la proie : elan horizontal et vertical ; le coup part a l'arrivee. */
    public void pounce(LivingEntity t) {
        Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1E-4) {
            return;
        }
        d = d.normalize();
        setDeltaMovement(d.x * 0.95, 0.42, d.z * 0.95);
        hasImpulse = true;
        pounceCool = Wild8Rules.POUNCE_COOLDOWN;
        pounceLeft = Wild8Rules.POUNCE_TICKS;
        setAction(Wild8Rules.POUNCE);
        playOwn("roar");
    }

    public boolean pouncing() {
        return pounceLeft > 0;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (pounceCool > 0) {
            pounceCool--;
        }
        if (pounceLeft > 0 && --pounceLeft == 0 && action() == Wild8Rules.POUNCE) {
            setAction(getTarget() != null ? WildRules.CHARGE : WildRules.IDLE);
        }
        if ((tickCount + getId()) % 10 == 0) {                         //1.0.5-beta (lot 29) : releve etale selon la bete
            Vec3 f = scan();
            if (f != null) {
                fear = f;
                fearLeft = Wild7Rules.FEAR_TICKS;
            }
        }
        if (fearLeft > 0 && --fearLeft == 0) {
            fear = null;
        }
        LivingEntity t = getTarget();
        if (t != null && (fear != null || isBaby() || !t.isAlive() || distanceTo(t) > Wild8Rules.TIGER_SIGHT + 8
                          || t instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            setTarget(null);
        }
        if (getTarget() == null && (action() == Wild8Rules.STALK && !inTallGrass() || action() == WildRules.CHARGE)) {
            setAction(WildRules.IDLE);
        }
    }

    @Override
    protected boolean fleesWhenHurt() {
        return isBaby();
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.12F, 0.6F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 500;
    }
}
