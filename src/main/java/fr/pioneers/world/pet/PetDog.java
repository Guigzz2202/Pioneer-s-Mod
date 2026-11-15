package fr.pioneers.world.pet;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5) : CHIEN de race (type {@code pioneers:<race>_dog}) : le loup vanilla (apprivoisement aux os, collier, assis au clic
 * droit, defense du maitre) + mode, niche, repos synchronises, buts du territoire, de la garde et de la chasse ({@link PetGoals}),
 * stats de la race ({@link PetCore#applyStats}), petits de race ({@link PetRules#childBreed}). Le loup vanilla reste le CHIEN SAUVAGE :
 * apprivoise, il devient l'un de ces chiens, race tiree au hasard ({@link PetConversion}).
 */
public class PetDog extends Wolf implements Pet {

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(PetDog.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_REST = SynchedEntityData.defineId(PetDog.class, EntityDataSerializers.BYTE);
    @Nullable
    private GlobalPos home;
    @Nullable
    private PetBrain brain;

    public PetDog(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MODE, (byte) PetMode.FOLLOW.ordinal());
        entityData.define(DATA_REST, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        PetGoals.install(this, this, false);
    }

    @Override
    public TamableAnimal self() {
        return this;
    }

    @Override
    public boolean isCat() {
        return false;
    }

    @Override
    public int breed() {
        return PiPets.dogBreed(getType());
    }

    @Override
    public PetMode mode() {
        return PetMode.of(entityData.get(DATA_MODE));
    }

    @Override
    public void setMode(PetMode mode) {
        PetMode old = mode();
        entityData.set(DATA_MODE, (byte) mode.ordinal());
        if (!level().isClientSide && old != mode) {
            setOrderedToSit(mode == PetMode.SIT);
            setInSittingPose(mode == PetMode.SIT);
            getNavigation().stop();
            if (mode == PetMode.HUNT) {
                HuntEvents.started(this);
            }
        }
    }

    @Nullable
    @Override
    public GlobalPos home() {
        return home;
    }

    @Override
    public void setHome(@Nullable GlobalPos home) {
        this.home = home;
    }

    @Override
    public int rest() {
        return entityData.get(DATA_REST);
    }

    @Override
    public void setRest(int rest) {
        entityData.set(DATA_REST, (byte) rest);
    }

    @Override
    public PetBrain brain() {
        if (brain == null) {
            brain = new PetBrain();
        }
        return brain;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        PetCore.save(this, tag);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        PetCore.load(this, tag);
    }

    @Override
    public void setTame(boolean tame) {
        super.setTame(tame);
        PetCore.applyStats(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            PetCore.tick(this);
        }
    }

    @Override
    public boolean canMate(@NotNull Animal other) {
        return other instanceof PetDog && super.canMate(other);
    }

    @Nullable
    @Override
    public Wolf getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob mate) {
        EntityType<PetDog> t = PiPets.dogType(PetCore.childBreed(this, mate, random.nextDouble()));
        PetDog child = t == null ? null : t.create(level);
        if (child != null) {
            PetCore.adopt(this, child);
        }
        return child;
    }
}
