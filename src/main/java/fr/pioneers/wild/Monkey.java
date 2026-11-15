package fr.pioneers.wild;

import fr.pioneers.common.PiCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 8, specification §4 « Jungle et foret tropicale ») : le SINGE des jungles (jungle, jungle clairsemee, bambouseraie). Il
 * GRIMPE AUX ARBRES (comme l'araignee : il monte le long des troncs, {@link WallClimberNavigation}) et CUEILLE LES BANANES : celles des
 * bananiers du mod (feuilles mures a 12 blocs, la branche revient au repos, {@link Wild8Goals.Forage}) ; la jungle vanilla n'en a pas : au
 * repos dans la canopee, il finit par en trouver une, et il nait souvent une banane a la main (choix du lot 8). Son REPAS est anime
 * ({@link Wild8Goals.Eat}, 5 s) : il s'assoit, EPLUCHE la banane, la MANGE et JETTE LA PEAU (une vraie peau visible qui tombe et disparait,
 * {@link BananaPeel} : pas un objet). Il VOLE parfois la banane qu'un joueur tient en main a 3 blocs, UNE seule, jamais autre chose
 * ({@link #trySteal}) ; reglage serveur propre {@code fauna.monkeyTheft} (actif par defaut, comme les rats et les mouettes de la decision
 * 14). Gibier du socle pour le budget seulement : le chien de chasse ne le piste pas (tag {@code pioneers:not_game}). Bananes au butin.
 */
public class Monkey extends WildAnimal {

    static final EntityDataAccessor<Boolean> BANANA = SynchedEntityData.defineId(Monkey.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(Monkey.class, EntityDataSerializers.BOOLEAN);

    int stealCool, canopy;
    /** Debut du repas (ticks de la bete) : relu par le rendu pour les etapes du repas. */
    int eatStart;

    public Monkey(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild8Rules.MONKEY);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BANANA, false);
        entityData.define(CLIMBING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WildGoals.Flee(this));
        goalSelector.addGoal(3, new Wild8Goals.Eat(this));
        goalSelector.addGoal(4, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.15, Ingredient.of(food()), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new Wild8Goals.Forage(this));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new Wild8Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public boolean onClimbable() {
        return climbing();
    }

    public boolean climbing() {
        return entityData.get(CLIMBING);
    }

    public boolean hasBanana() {
        return entityData.get(BANANA);
    }

    public void setBanana(boolean b) {
        entityData.set(BANANA, b);
    }

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty, @NotNull MobSpawnType reason,
          @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, reason, data, tag);
        setBanana(random.nextFloat() < 0.5F);                     //la jungle vanilla n'a pas de bananier : il nait souvent une banane a la main
        return d;
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return Wild8Rules.coat(kind, random.nextDouble());
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ACTION.equals(key) && action() == Wild8Rules.EAT) {
            eatStart = tickCount;
        }
    }

    /** Ticks ecoules depuis le debut du repas (rendu, sur le client comme sur le serveur). */
    public float eatTicks(float partial) {
        return tickCount - eatStart + partial;
    }

    /** Pour le banc du rendu : place le repas a l'instant {@code t}. */
    public void eatAt(int t) {
        eatStart = tickCount - t;
    }

    /** Tient-il une banane a l'instant du rendu ? (le repas la fait disparaitre une fois la peau jetee) */
    public boolean bananaVisible(float partial) {
        return action() == Wild8Rules.EAT ? Wild8Rules.eatStage((int) eatTicks(partial)) < 2 : hasBanana();
    }

    public static boolean theftEnabled() {
        return PiCommonConfig.get(PiCommonConfig.FAUNA_MONKEY_THEFT, true);
    }

    /** La main qui tient une banane du mod, ou null : rien d'autre ne l'interesse. */
    @Nullable
    public static InteractionHand bananaHand(Player p) {
        if (p.getMainHandItem().is(PiWild8.banana()) && !p.getMainHandItem().isEmpty()) {
            return InteractionHand.MAIN_HAND;
        }
        if (p.getOffhandItem().is(PiWild8.banana()) && !p.getOffhandItem().isEmpty()) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** Vole la banane tenue par {@code p} (une seule, jamais un autre objet) ; rend vrai si le vol a eu lieu. */
    public boolean trySteal(Player p) {
        InteractionHand hand = bananaHand(p);
        if (isBaby() || !Wild8Rules.monkeySteals(theftEnabled(), hand != null, hasBanana(), stealCool, distanceTo(p))
            || p.isCreative() || p.isSpectator()) {
            return false;
        }
        p.getItemInHand(hand).shrink(1);
        setBanana(true);
        stealCool = Wild8Rules.STEAL_COOLDOWN_MIN + random.nextInt(Wild8Rules.STEAL_COOLDOWN_MAX - Wild8Rules.STEAL_COOLDOWN_MIN);
        playOwn("steal");
        swing(InteractionHand.MAIN_HAND);
        p.displayClientMessage(Component.translatable("wild.pioneers.monkey.stole"), true);
        alarm(p.position(), WildRules.ALARM_TICKS, false);        //il file avec son butin
        return true;
    }

    /** Mange la banane : la peau part de la main, vers le cote. */
    void tossPeel() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 hand = position().add(-Mth.sin(yaw) * 0.25 - Mth.cos(yaw) * 0.15, getBbHeight() * 0.7, Mth.cos(yaw) * 0.25 - Mth.sin(yaw) * 0.15);
        Vec3 side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(0.18 + random.nextDouble() * 0.1).add(0, 0.22, 0);
        BananaPeel.toss(level(), hand, side);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            entityData.set(CLIMBING, horizontalCollision);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (stealCool > 0) {
            stealCool--;
        }
        if (tickCount % 10 == 0 && !hasBanana() && stealCool <= 0 && random.nextInt(4) == 0) {
            Player p = level().getNearestPlayer(getX(), getY(), getZ(), Wild8Rules.STEAL_RANGE, WildAnimal::scary);
            if (p != null) {
                trySteal(p);
            }
        }
        //dans la canopee : il finit par trouver une banane (la jungle vanilla n'a pas de bananier)
        if (!hasBanana() && tickCount % 100 == 0 && level().getBlockState(blockPosition().below()).is(net.minecraft.tags.BlockTags.LEAVES)) {
            if (++canopy >= Wild8Rules.CANOPY_FIND) {
                canopy = 0;
                setBanana(true);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        if (hasBanana() && level() instanceof ServerLevel sl) {
            sl.addFreshEntity(new ItemEntity(sl, getX(), getY() + 0.3, getZ(), new ItemStack(PiWild8.banana())));
            setBanana(false);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, @NotNull DamageSource source) {
        return false;                                             //il saute de branche en branche
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("monkey_banana", hasBanana());
        tag.putInt("monkey_steal", stealCool);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setBanana(tag.getBoolean("monkey_banana"));
        stealCool = tag.getInt("monkey_steal");
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.WOOL_STEP, 0.15F, 1.3F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }
}
