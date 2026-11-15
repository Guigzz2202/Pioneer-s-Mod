package fr.pioneers.world.wild;

import fr.pioneers.farm.device.ApiaryTile;
import fr.pioneers.farm.device.DeviceSites;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 7, specification §4, §11 n° 13) : l'OURS, gibier dangereux (cible du mode Chasse du chien : groupe GIBIER). Il charge qui
 * s'approche a moins de 6 blocs ou le blesse ; l'ourse suivie d'oursons charge a 12 blocs ({@link Wild7Rules#bearCharges}) ; la charge dure
 * 10 s au plus. Attire par les RUCHES DU MOD ({@link ApiaryTile}, registre {@link DeviceSites.Kind#APIARY} : jamais les ruches vanilla) a
 * 24 blocs : il y va, et la CASSE (le contenu tombe, il se regale) — sauf dans un claim de clan ou si les degats des creatures sont coupes :
 * il se dresse, rugit pour MENACER, previent les joueurs proches et s'en va ({@link Wild7Rules#bearBreaks}). Actif en hiver (pas
 * d'hibernation dans la 1.0.5). Robes : brun, noir (forets de feuillus) ; ourson.
 */
public class Bear extends WildAnimal {

    int rageLeft, calm;

    public Bear(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild7Rules.BEAR);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.35, true));
        goalSelector.addGoal(3, new HiveRaid(this));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(7, new Wild7Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        boolean broadleaf = level.getBiome(blockPosition()).is(BiomeTags.IS_FOREST) && !level.getBiome(blockPosition()).is(BiomeTags.IS_TAIGA);
        return Wild7Rules.bearCoat(broadleaf, random.nextDouble());
    }

    /** Ourse qui garde ses petits (un ourson a 12 blocs) ? */
    public boolean guardingCub() {
        if (isBaby() || isMale()) {
            return false;
        }
        for (WildAnimal w : kin(Wild7Rules.CUB_GUARD)) {
            if (w.isBaby()) {
                return true;
            }
        }
        return false;
    }

    /** Charge un joueur trop proche (6 blocs ; 12 pour l'ourse qui garde ses petits) ; rend la cible. */
    @Nullable
    public LivingEntity provoke() {
        if (getTarget() == null && calm == 0 && !isBaby()) {
            boolean guard = guardingCub();
            Player p = level().getNearestPlayer(getX(), getY(), getZ(), guard ? Wild7Rules.CUB_GUARD : Wild7Rules.BEAR_CHARGE, WildAnimal::scary);
            if (p != null && Wild7Rules.bearCharges(distanceTo(p), false, guard)) {
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
        if (isBaby() && getTarget() != null) {
            setTarget(null);
        }
        LivingEntity t = provoke();
        if (t != null) {
            if (rageLeft == 0) {
                rageLeft = Wild7Rules.BEAR_RAGE_TICKS;
                playOwn("roar");
            }
            setAction(WildRules.CHARGE);
            if (--rageLeft <= 0 || !t.isAlive() || distanceTo(t) > 20 || t instanceof Player p && (p.isCreative() || p.isSpectator())) {
                setTarget(null);
                rageLeft = 0;
                calm = 100;
                setAction(WildRules.IDLE);
            }
        } else if (action() == WildRules.CHARGE) {
            setAction(WildRules.IDLE);
        }
    }

    @Override
    protected boolean fleesWhenHurt() {
        return isBaby();
    }

    @Override
    public boolean isFood(@NotNull net.minecraft.world.item.ItemStack stack) {
        return stack.is(food());
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.POLAR_BEAR_STEP, 0.3F, 0.9F);
    }

    @Override
    protected float getSoundVolume() {
        return 1.1F;
    }

    // ------------------------------------------------------------------ ruches

    /** Casse ou menace la ruche du mod en {@code pos} (appele quand l'ours y est arrive). Rend vrai s'il l'a cassee. */
    public boolean raid(BlockPos pos) {
        if (!(level() instanceof ServerLevel sl) || !(sl.getBlockEntity(pos) instanceof ApiaryTile)) {
            return false;
        }
        boolean claimed = PiWild7.claimed(sl, pos);
        boolean grief = ForgeEventFactory.getMobGriefingEvent(sl, this);
        setAction(WildRules.DISPLAY);
        playOwn("roar");
        if (Wild7Rules.bearBreaks(claimed, grief)) {
            sl.destroyBlock(pos, true, this);
            heal(8F);
            return true;
        }
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32))) {
            p.displayClientMessage(Component.translatable("wild.pioneers.bear.threat"), true);
        }
        return false;
    }

    /** Va vers la ruche du mod la plus proche (24 blocs), la casse ou menace, puis attend 1 a 2 minutes avant d'en chercher une autre. */
    static final class HiveRaid extends Goal {

        final Bear b;
        @Nullable
        BlockPos hive;
        int cool, left, menace;

        HiveRaid(Bear b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (b.isBaby() || b.getTarget() != null || --cool > 0) {
                return false;
            }
            cool = 100 + b.getRandom().nextInt(100);
            List<BlockPos> near = DeviceSites.near(b.level(), b.getX(), b.getY(), b.getZ(), Wild7Rules.HIVE_RANGE, DeviceSites.Kind.APIARY);
            hive = null;
            double best = Double.MAX_VALUE;
            for (BlockPos p : near) {
                double d = p.distSqr(b.blockPosition());
                if (d < best && b.level().getBlockEntity(p) instanceof ApiaryTile) {
                    best = d;
                    hive = p;
                }
            }
            return hive != null;
        }

        @Override
        public void start() {
            left = 600;
            menace = 0;
            b.getNavigation().moveTo(hive.getX() + 0.5, hive.getY(), hive.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return hive != null && left > 0 && b.getTarget() == null && b.level().isLoaded(hive)
                   && (menace > 0 || b.level().getBlockEntity(hive) instanceof ApiaryTile);
        }

        @Override
        public void tick() {
            left--;
            if (hive == null) {
                return;                                                    //1.0.5-beta (lot 21)
            }
            if (menace > 0) {
                b.getLookControl().setLookAt(hive.getX() + 0.5, hive.getY() + 0.5, hive.getZ() + 0.5);
                if (--menace == 0) {
                    left = 0;
                }
                return;
            }
            if (b.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(hive)) < 2.2) {
                b.getNavigation().stop();
                boolean broke = b.raid(hive);
                menace = broke ? 20 : Wild7Rules.MENACE_TICKS;
            } else if (b.getNavigation().isDone()) {
                b.getNavigation().moveTo(hive.getX() + 0.5, hive.getY(), hive.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            hive = null;
            if (b.action() == WildRules.DISPLAY) {
                b.setAction(WildRules.IDLE);
            }
            cool = Wild7Rules.HIVE_COOLDOWN_MIN + b.getRandom().nextInt(Wild7Rules.HIVE_COOLDOWN_MAX - Wild7Rules.HIVE_COOLDOWN_MIN);
        }
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity e) {
        boolean hit = super.doHurtTarget(e);
        if (hit) {
            playOwn("roar");
        }
        return hit;
    }
}
