package fr.pioneers.world.wild;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import fr.pioneers.world.pet.Pet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 8, specification §4 « Savane ») : le LION et la LIONNE (une entite, le sexe dans la bete : le lion porte la criniere).
 * FAINEANTS : couches la plupart du temps ({@link Wild8Goals.Idle}). Un joueur qui RESTE a moins de 6 blocs les fait gronder (avertissement
 * apres 1 s, message), puis attaquer apres 3 s ({@link Wild8Rules#lionAttacks}) ; ils lachent prise a 16 blocs. Blesses, ils ripostent et la
 * troupe suit. La LIONNE CHASSE le gibier de la savane (tag {@code pioneers:lion_prey} : chevaux, anes et lamas sauvages, bisons ;
 * girafons) de la fin d'apres-midi a l'aube, une fois par jour au plus ({@link Wild8Rules#lionessHunts}) ; les autres lionnes de la troupe
 * a 16 blocs la rejoignent. Predateur du socle (le chien autonome defend le betail contre lui s'il s'en prend a un animal). Lionceaux.
 */
public class Lion extends WildAnimal {

    int near, rage, fedLeft;
    boolean warned;

    public Lion(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild8Rules.LION);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.5, true));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new Wild8Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.7, 0.002F));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false, e -> !isBaby() && !isMale() && prey(e)
              && Wild8Rules.lionessHunts(level().getDayTime(), distanceTo(e), fedLeft)));
    }

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty, @NotNull MobSpawnType reason,
          @Nullable SpawnGroupData data, @Nullable net.minecraft.nbt.CompoundTag tag) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, reason, data, tag);
        //une troupe : surtout des lionnes (un lion pour trois lionnes environ)
        setSex(random.nextFloat() < 0.3F ? WildRules.MALE : WildRules.FEMALE);
        return d;
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return 0;
    }

    /** Gibier de la savane que chasse la lionne : tag {@code pioneers:lion_prey}, girafons ; jamais une bete apprivoisee, nommee ou en laisse. */
    public static boolean prey(LivingEntity e) {
        if (!e.isAlive() || e instanceof Pet || e.hasCustomName() || e instanceof TamableAnimal t && t.isTame()
            || e instanceof AbstractHorse h && h.isTamed() || e instanceof net.minecraft.world.entity.Mob m && m.isLeashed()) {
            return false;
        }
        return e.getType().is(PiWild8.LION_PREY) || e instanceof Giraffe g && g.isBaby();
    }

    /** Compteur de presence du joueur le plus proche (ticks passes a moins de 6 blocs). */
    public int near() {
        return near;
    }

    public boolean angry() {
        return rage > 0 && getTarget() != null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (fedLeft > 0) {
            fedLeft--;
        }
        if (isBaby()) {
            setTarget(null);
            return;
        }
        Player p = level().getNearestPlayer(getX(), getY(), getZ(), Wild8Rules.LION_RANGE + 2, WildAnimal::scary);
        double d = p == null ? 99 : distanceTo(p);
        near = Wild8Rules.lionNear(near, d);
        if (p != null && getTarget() == null) {
            if (Wild8Rules.lionWarns(d, near) && !warned) {
                warned = true;
                setAction(WildRules.ALERT);                        //il se redresse et gronde
                getLookControl().setLookAt(p);
                playOwn("growl");
                p.displayClientMessage(Component.translatable("wild.pioneers.lion.warning"), true);
            } else if (Wild8Rules.lionAttacks(d, near)) {
                setTarget(p);
                rage = Wild8Rules.LION_RAGE;
                playOwn("roar");
                for (WildAnimal w : kin(Wild8Rules.PRIDE_RADIUS)) {
                    if (w != this && !w.isBaby() && w.getTarget() == null && w instanceof Lion l && l.near > Wild8Rules.LION_WARN) {
                        w.setTarget(p);                            //la troupe deja reveillee suit
                    }
                }
            }
        }
        if (near == 0) {
            warned = false;
        }
        LivingEntity t = getTarget();
        if (t != null) {
            setAction(WildRules.CHARGE);
            if (rage > 0) {
                rage--;
            }
            if (!t.isAlive() || distanceTo(t) > Wild8Rules.LION_GIVE_UP || t instanceof Player pl && (pl.isCreative() || pl.isSpectator())
                || t instanceof Player && rage <= 0) {
                setTarget(null);
                near = 0;
                setAction(WildRules.IDLE);
            } else if (tickCount % 20 == 0 && !(t instanceof Player) && !isMale()) {
                for (WildAnimal w : kin(Wild8Rules.PRIDE_RADIUS)) {
                    if (w != this && !w.isBaby() && !w.isMale() && w.getTarget() == null) {
                        w.setTarget(t);                            //les lionnes chassent ensemble
                    }
                }
            }
        } else if (action() == WildRules.CHARGE) {
            setAction(WildRules.IDLE);
        }
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity e) {
        boolean hit = super.doHurtTarget(e);
        if (hit && e instanceof LivingEntity l && !l.isAlive() && !(l instanceof Player)) {
            fedLeft = Wild8Rules.LIONESS_FED;                     //repue pour la journee
            for (WildAnimal w : kin(Wild8Rules.PRIDE_RADIUS)) {
                if (w instanceof Lion o) {
                    o.fedLeft = Wild8Rules.LIONESS_FED;
                }
            }
        }
        return hit;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && source.getEntity() instanceof LivingEntity && !isBaby()) {
            rage = Wild8Rules.LION_RAGE;
        }
        return hit;
    }

    @Override
    protected boolean fleesWhenHurt() {
        return isBaby();
    }

    @Override
    public void readAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fedLeft = tag.getInt("lion_fed");
    }

    @Override
    public void addAdditionalSaveData(@NotNull net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("lion_fed", fedLeft);
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.2F, 0.7F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    /** Lions de la troupe a 16 blocs (adultes). */
    public List<WildAnimal> pride() {
        return kin(Wild8Rules.PRIDE_RADIUS);
    }
}
