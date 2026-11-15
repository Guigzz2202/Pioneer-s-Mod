package fr.pioneers.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
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
 * 1.0.5-beta (lot 8, specification §4 « Savane ») : la GIRAFE, gibier paisible des savanes et des plateaux de savane. Elle BROUTE LA CIME
 * DES ACACIAS ({@link Wild8Goals.Browse} : feuilles du tag {@code pioneers:giraffe_browse} a 3 ou 4 blocs au-dessus de ses sabots, cou tendu,
 * rien n'est casse) ; elle fuit le joueur qui s'approche a 10 blocs (moitie accroupi) ; frappee de pres, elle RUE ({@link #kick} : les
 * pattes arriere detendues, le coup part a mi-ruade et repousse l'agresseur), puis s'enfuit avec la harde. Grande bete : pas d'un bloc sans
 * sauter, boite de 1,3 x 3,2 blocs (elle passe sous les couronnes des acacias). Robes : reticulee, masai.
 */
public class Giraffe extends WildAnimal {

    int kickLeft, kickCool;
    /** Ruades qui ont porte (GameTests). */
    public int kicksLanded;
    @Nullable
    LivingEntity kicked;

    public Giraffe(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild8Rules.GIRAFFE);
        setMaxUpStep(GeneratedWild8.STEP[kind - GeneratedWild8.FIRST]);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WildGoals.Flee(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1, Ingredient.of(food()), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(6, new Wild8Goals.Browse(this));
        goalSelector.addGoal(7, new Wild8Goals.Idle(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        boolean plateau = level.getBiome(blockPosition()).is(net.minecraft.world.level.biome.Biomes.SAVANNA_PLATEAU);
        return Wild8Rules.giraffeCoat(plateau && level.getBiome(blockPosition()).is(BiomeTags.IS_SAVANNA), random.nextDouble());
    }

    /** Ruade contre l'agresseur (assez pres, pas deja en train de ruer) ; le coup part a mi-ruade ({@link Wild8Rules#KICK_HIT}). */
    public boolean kick(LivingEntity attacker) {
        if (!Wild8Rules.giraffeKicks(distanceTo(attacker), kickCool, isBaby())) {
            return false;
        }
        kicked = attacker;
        kickLeft = Wild8Rules.KICK_TICKS;
        kickCool = Wild8Rules.KICK_COOLDOWN;
        //elle presente la croupe : la ruade part vers l'arriere
        float yaw = (float) Math.toDegrees(Math.atan2(attacker.getZ() - getZ(), attacker.getX() - getX())) + 90F;
        setYRot(yaw);
        yBodyRot = yaw;
        getNavigation().stop();
        setAction(Wild8Rules.KICK);
        playOwn("kick");
        return true;
    }

    public boolean kicking() {
        return kickLeft > 0;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Giraffe)
            && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            kick(attacker);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (kickCool > 0) {
            kickCool--;
        }
        if (kickLeft > 0) {
            kickLeft--;
            getNavigation().stop();
            setAction(Wild8Rules.KICK);
            if (kickLeft == Wild8Rules.KICK_TICKS - Wild8Rules.KICK_HIT && kicked != null && kicked.isAlive()
                && distanceTo(kicked) <= Wild8Rules.KICK_RANGE + 0.8 && level() instanceof ServerLevel sl) {
                kicked.hurt(damageSources().mobAttack(this), Wild8Rules.KICK_DAMAGE);
                kicksLanded++;
                Vec3 away = kicked.position().subtract(position()).multiply(1, 0, 1);
                if (away.lengthSqr() > 1E-4) {
                    away = away.normalize();
                    kicked.knockback(Wild8Rules.KICK_KNOCK, -away.x, -away.z);
                }
                sl.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, getSoundSource(), 1F, 0.7F);
            }
            if (kickLeft == 0) {
                kicked = null;
                setAction(WildRules.FLEE);
            }
        } else if (action() == Wild8Rules.KICK) {
            setAction(WildRules.IDLE);
        }
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.HORSE_STEP, 0.3F, 0.7F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 480;
    }
}
