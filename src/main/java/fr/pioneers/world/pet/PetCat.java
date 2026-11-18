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
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5) : CHAT de race (type {@code pioneers:<race>_cat}) : le chat vanilla (apprivoisement au poisson, collier, assis,
 * coucher sur le lit du maitre, chasse des lapins errants) + mode, panier, repos synchronises, buts du territoire, de la chasse aux rats et
 * de la peche ({@link PetGoals}), stats de la race. Les chats errants des villages (vanilla) deviennent le chat de la race de leur variante
 * ({@link PetConversion}).
 */
public class PetCat extends Cat implements Pet {

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(PetCat.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_REST = SynchedEntityData.defineId(PetCat.class, EntityDataSerializers.BYTE);
    @Nullable
    private GlobalPos home;
    @Nullable
    private PetBrain brain;

    public PetCat(EntityType<? extends Cat> type, Level level) {
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
        PetGoals.install(this, this, true);
    }

    @Override
    public TamableAnimal self() {
        return this;
    }

    @Override
    public boolean isCat() {
        return true;
    }

    @Override
    public int breed() {
        return PiPets.catBreed(getType());
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
        return other instanceof PetCat && super.canMate(other);
    }

    @Nullable
    @Override
    public Cat getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob mate) {
        EntityType<PetCat> t = PiPets.catType(PetCore.childBreed(this, mate, random.nextDouble()));
        PetCat child = t == null ? null : t.create(level);
        if (child != null) {
            PetCore.adopt(this, child);
        }
        return child;
    }
}
