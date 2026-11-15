package fr.pioneers.wild;

import fr.pioneers.farm.animal.breed.BreedAnimal;
import fr.pioneers.pet.Pet;
import fr.pioneers.season.BiomeClimates;
import fr.pioneers.season.SeasonClimate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 7, specification §4, §11 n° 9) : le VRAI LOUP, distinct du chien sauvage (le loup vanilla). Meutes de 3 a 5 ; la nuit il
 * chasse le BETAIL (tag {@code pioneers:wolf_prey} et races du mod) et le JOUEUR ISOLE ({@link Wild7Rules#wolfAttacksPlayer}) ; le jour il
 * se repose et n'attaque qu'un joueur isole trop proche. Toute la meute a 16 blocs suit la proie de l'un des siens. Il CRAINT LE FEU
 * ({@link #fear}) : un joueur qui tient une torche ou une lanterne a 10 blocs (tag {@code pioneers:wolf_repellents}), un feu ou un feu de
 * camp allume a 5 blocs le font fuir et lacher sa proie. Ne s'apprivoise pas. Entre dans le tag {@code pioneers:livestock_predators} : le
 * chien autonome defend le betail contre lui (decision 9). Robes : gris, blanc (biomes enneiges), noir ; louveteau.
 */
public class GrayWolf extends WildAnimal {

    @Nullable
    Vec3 fear;
    int fearLeft;

    public GrayWolf(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild7Rules.WOLF);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Wild7Goals.FearFire(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.45, true));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new Wild7Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false, e -> !isBaby() && livestock(e)
              && Wild7Rules.wolfHuntsLivestock(level().isNight(), distanceTo(e), fear != null)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, e -> !isBaby() && scary(e)
              && Wild7Rules.wolfAttacksPlayer(level().isNight(), distanceTo(e), isolated(e), fear != null)));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        boolean snowy = BiomeClimates.of(level.getBiome(blockPosition())).family() == SeasonClimate.Family.SNOWY;
        return Wild7Rules.wolfCoat(snowy, random.nextDouble());
    }

    /** Betail que le loup chasse : tag {@code pioneers:wolf_prey}, races du mod ; jamais une bete apprivoisee, nommee ou un animal de compagnie. */
    public static boolean livestock(LivingEntity e) {
        if (!e.isAlive() || e instanceof Pet || e instanceof WildAnimal || e instanceof TamableAnimal t && t.isTame()) {
            return false;
        }
        return e.getType().is(PiWild7.WOLF_PREY) || e instanceof BreedAnimal;
    }

    /** Joueur isole : aucun autre joueur (vivant, pas spectateur) a 24 blocs. */
    public static boolean isolated(LivingEntity p) {
        List<Player> others = p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(Wild7Rules.ISOLATED_RADIUS),
              o -> o != p && o.isAlive() && !o.isSpectator() && o.distanceTo(p) <= Wild7Rules.ISOLATED_RADIUS);
        return Wild7Rules.isolated(others.size());
    }

    /** D'ou vient la peur (feu tenu ou feu au sol), ou null. */
    @Nullable
    public Vec3 fear() {
        return fear;
    }

    /** Ce joueur tient-il une torche, une lanterne (dans l'une ou l'autre main) ? */
    public static boolean holdsFire(Player p) {
        return p.getMainHandItem().is(PiWild7.WOLF_REPELLENTS) || p.getOffhandItem().is(PiWild7.WOLF_REPELLENTS);
    }

    /** Releve la peur : joueur a 10 blocs avec une torche ou une lanterne en main, feu ou feu de camp allume a 5 blocs. */
    @Nullable
    Vec3 scan() {
        Player p = level().getNearestPlayer(getX(), getY(), getZ(), Wild7Rules.FEAR_TORCH_RANGE, e -> e instanceof Player pl && !pl.isSpectator()
              && holdsFire(pl));
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
            if (fire(level().getBlockState(b))) {
                return Vec3.atCenterOf(b);
            }
        }
        return null;
    }

    /** Feu ou feu de camp allume (ce qui fait peur au loup et au tigre). */
    public static boolean fire(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
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
        if (t != null) {
            if (fear != null) {
                setTarget(null);
            } else if (!level().isNight() && livestock(t)) {
                setTarget(null);                                  //a l'aube, la meute laisse le betail
            } else if (tickCount % 20 == 0) {
                setAction(WildRules.CHARGE);
                for (WildAnimal w : kin(Wild7Rules.PACK_RADIUS)) {
                    if (w != this && w.getTarget() == null && !w.isBaby() && ((GrayWolf) w).fear == null) {
                        w.setTarget(t);                           //la meute suit
                    }
                }
            }
        } else if (action() == WildRules.CHARGE) {
            setAction(WildRules.IDLE);
        }
    }

    @Override
    public boolean doHurtTarget(@NotNull net.minecraft.world.entity.Entity e) {
        boolean hit = super.doHurtTarget(e);
        if (hit && random.nextInt(3) == 0) {
            playOwn("growl");
        }
        return hit;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.15F, 0.9F);
    }

    @Override
    public boolean isFood(@NotNull net.minecraft.world.item.ItemStack stack) {
        return false;
    }
}
