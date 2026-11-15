package fr.pioneers.wild;

import fr.pioneers.season.BiomeClimates;
import fr.pioneers.season.SeasonClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 7, specification §4) : le SERPENT (plaines, forets, savane, jungle, desert). Il CHASSE LES RATS (12 blocs) ; il MORD qui
 * s'approche a moins de 2 blocs ou l'attaque ({@link Wild7Rules#snakeBites}) : POISON LEGER (niveau I, 4 s), puis il se calme 3 s. Il passe
 * beaucoup de temps enroule au soleil, la tete dressee de temps en temps, langue dardee. Corps articule (tete, corps et 6 anneaux en
 * chaine). Robes selon le biome ({@link Wild7Rules#snakeCoat}) : couleuvre a collier (plaines, forets), vipere des sables (desert),
 * couleuvre verte (jungle), serpent annele (savane). Boule de slime.
 */
public class Snake extends WildAnimal {

    int calm;

    public Snake(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild7Rules.SNAKE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, false));
        goalSelector.addGoal(7, new Wild7Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Rat.class, 10, true, false, e -> calm == 0
              && distanceTo(e) < Wild7Rules.SNAKE_PREY_RANGE));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        Holder<Biome> b = level.getBiome(blockPosition());
        SeasonClimate.Family f = BiomeClimates.of(b).family();
        return Wild7Rules.snakeCoat(f == SeasonClimate.Family.DRY, b.is(BiomeTags.IS_JUNGLE), b.is(BiomeTags.IS_SAVANNA));
    }

    /** Mord un joueur trop proche (2 blocs) ; rend la cible. */
    @Nullable
    public LivingEntity provoke() {
        if (getTarget() == null && calm == 0 && !isBaby()) {
            Player p = level().getNearestPlayer(getX(), getY(), getZ(), Wild7Rules.SNAKE_BITE + 1, WildAnimal::scary);
            if (p != null && Wild7Rules.snakeBites(distanceTo(p), false)) {
                setTarget(p);
            }
        }
        return getTarget();
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
        LivingEntity t = provoke();
        if (t != null) {
            setAction(WildRules.CHARGE);
            if (!t.isAlive() || distanceTo(t) > (t instanceof Player ? 6 : 16)) {
                setTarget(null);
            }
        } else if (action() == WildRules.CHARGE) {
            setAction(WildRules.IDLE);
        }
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity e) {
        boolean hit = super.doHurtTarget(e);
        if (hit) {
            playOwn("bite");
            if (e instanceof LivingEntity l && !(e instanceof Rat)) {
                l.addEffect(new MobEffectInstance(MobEffects.POISON, GeneratedWild7.SNAKE_POISON_TICKS, 0), this);
            }
            if (e instanceof Player) {
                setTarget(null);
                calm = Wild7Rules.SNAKE_CALM;
                alarm(e.position(), 40, false);
            }
        }
        return hit;
    }

    @Override
    protected boolean fleesWhenHurt() {
        return false;
    }

    @Override
    public boolean isFood(@NotNull net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.SILVERFISH_STEP, 0.05F, 0.6F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }
}
