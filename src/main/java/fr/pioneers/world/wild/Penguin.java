package fr.pioneers.world.wild;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 9) : MANCHOT et PINGOUIN (une classe, la sorte dans la bete). Vivent en COLONIES sur la banquise et les cotes froides
 * (la colonie se tient ensemble : {@link WildGoals.Herd}), se dandinent, glissent sur le ventre, appellent ; PLONGENT PECHER
 * ({@link Wild9Goals.FishDive}) et reviennent a la cote un POISSON AU BEC (synchronise, visible), qu'ils avalent apres un moment ; plongent
 * aussi a l'approche d'un joueur. Proies de l'orque.
 */
public class Penguin extends MarineAnimal {

    static final EntityDataAccessor<Boolean> FISH = SynchedEntityData.defineId(Penguin.class, EntityDataSerializers.BOOLEAN);
    /** Delai avant la prochaine peche ; ticks depuis que le poisson est au bec ; poissons peches (GameTests). */
    public int fishCooldown, carried, caught, callLeft;

    public Penguin(EntityType<? extends Animal> type, Level level, int kind) {
        super(type, level, kind);
        fishCooldown = 200 + random.nextInt(Wild9Rules.FISH_MIN);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FISH, false);
    }

    public boolean hasFish() {
        return entityData.get(FISH);
    }

    public void setFish(boolean f) {
        if (f && !hasFish()) {
            caught++;
            carried = 0;
        }
        entityData.set(FISH, f);
    }

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new Wild9Goals.Breathe(this));
        goalSelector.addGoal(1, new Wild9Goals.Dive(this));
        goalSelector.addGoal(2, new Wild9Goals.Shore(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1, Ingredient.of(food()), false));
        goalSelector.addGoal(4, new Wild9Goals.FishDive(this));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new WildGoals.Act(this));
        goalSelector.addGoal(8, new Wild9Goals.Cruise(this, 1.0));
        goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.7, 0.002F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(11, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (fishCooldown > 0) {
            fishCooldown--;
        }
        //appel de la colonie : tete levee, ailerons ecartes, le temps du cri
        if (callLeft > 0 && --callLeft == 0 && action() == WildRules.DISPLAY) {
            setAction(WildRules.IDLE);
        }
        //poisson au bec : tenu a terre un moment, puis avale (animation de repas)
        if (hasFish() && !isInWater()) {
            carried++;
            if (carried == Wild9Rules.CARRY_TICKS) {
                setAction(Wild8Rules.EAT);
            } else if (carried >= Wild9Rules.CARRY_TICKS + Wild9Rules.EAT_TICKS) {
                setFish(false);
                setAction(WildRules.IDLE);
                playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.5F, 1.4F);
            }
        }
    }

    @Override
    public void playAmbientSound() {
        super.playAmbientSound();
        if (!level().isClientSide && onGround() && !isInWater() && action() == WildRules.IDLE && !hasFish()) {
            setAction(WildRules.DISPLAY);
            callLeft = 40;
        }
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        if (hasFish()) {
            spawnAtLocation(Items.COD);
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("penguin_fish", hasFish());
        tag.putInt("penguin_fish_cooldown", fishCooldown);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(FISH, tag.getBoolean("penguin_fish"));
        fishCooldown = tag.getInt("penguin_fish_cooldown");
    }
}
