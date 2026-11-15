package fr.pioneers.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 8, specification §4 « Savane ») : le RHINOCEROS, gibier des savanes. Il CHARGE A VUE tout joueur a moins de 8 blocs
 * (ligne de vue : il ne charge pas a travers un mur ; {@link Wild8Rules#rhinoCharges}), tete basse, corne en avant, jusqu'a 8 s ou 16 blocs ;
 * son coup PROJETTE au loin (recul fort) ; apres un coup, 4 s de calme. TRES RESISTANT : cuirasse (armure 14, durete 6), presque aucun
 * recul. Il ne casse aucun bloc. Beaucoup d'os, viande de gros gibier. Robes : blanc, noir. Le petit ne charge pas.
 */
public class Rhino extends WildAnimal {

    int chargeLeft, calm;

    public Rhino(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild8Rules.RHINO);
        setMaxUpStep(GeneratedWild8.STEP[kind - GeneratedWild8.FIRST]);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.9, true));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.0, Ingredient.of(food()), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(7, new Wild8Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return Wild8Rules.coat(kind, random.nextDouble());
    }

    /** Charge un joueur visible a moins de 8 blocs ; rend la cible. */
    @Nullable
    public LivingEntity provoke() {
        if (getTarget() == null && !isBaby()) {
            Player p = level().getNearestPlayer(getX(), getY(), getZ(), Wild8Rules.RHINO_SIGHT, WildAnimal::scary);
            if (p != null && Wild8Rules.rhinoCharges(distanceTo(p), hasLineOfSight(p), calm)) {
                setTarget(p);
            }
        }
        return getTarget();
    }

    public boolean charging() {
        return chargeLeft > 0 && getTarget() != null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (calm > 0) {
            calm--;
        }
        if (isBaby()) {
            setTarget(null);
            return;
        }
        LivingEntity t = provoke();
        if (t != null) {
            if (chargeLeft == 0) {
                chargeLeft = Wild8Rules.RHINO_CHARGE_TICKS;
                playOwn("charge");
            }
            setAction(WildRules.CHARGE);
            if (--chargeLeft <= 0 || !t.isAlive() || distanceTo(t) > Wild8Rules.RHINO_GIVE_UP
                || t instanceof Player p && (p.isCreative() || p.isSpectator())) {
                stopCharge();
            }
        } else if (action() == WildRules.CHARGE) {
            setAction(WildRules.IDLE);
        }
    }

    void stopCharge() {
        setTarget(null);
        chargeLeft = 0;
        calm = Wild8Rules.RHINO_CALM;
        setAction(WildRules.IDLE);
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity e) {
        boolean hit = super.doHurtTarget(e);
        if (hit && e instanceof LivingEntity l) {
            //la corne projette : en plus du recul de l'attribut, une poussee vers le haut
            Vec3 v = l.getDeltaMovement();
            l.setDeltaMovement(v.x, Math.max(v.y, 0.45), v.z);
            l.hurtMarked = true;
            stopCharge();                                         //apres le coup, le calme (il repart plus loin)
        }
        return hit;
    }

    @Override
    protected boolean fleesWhenHurt() {
        return isBaby();
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return super.hurt(source, amount);
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.25F, 1.1F);
    }
}
