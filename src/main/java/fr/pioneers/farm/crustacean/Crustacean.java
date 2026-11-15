package fr.pioneers.farm.crustacean;

import fr.pioneers.farm.aquarium.PiAquarium;
import fr.pioneers.farm.fish.FishSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.4 : crustace du mod (une classe pour les six especes de {@link GeneratedCrustaceans}, deux types d'entite : {@link PiCrustaceans#AQUATIC}
 * et {@link PiCrustaceans#SHORE}). Espece et taille synchronisees (modele, carapace, boite), tirees a l'apparition selon le milieu
 * ({@link CrustaceanSpawns}). Marche (au fond de l'eau pour les aquatiques, qui ne nagent pas et suffoquent hors de l'eau) ; les crabes de
 * rivage respirent partout et fuient le joueur. Disparait loin des joueurs, sauf s'il est nomme.
 */
public class Crustacean extends PathfinderMob {

    private static final EntityDataAccessor<Integer> SPECIES = SynchedEntityData.defineId(Crustacean.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(Crustacean.class, EntityDataSerializers.FLOAT);
    /** Crustaces au plus dans 16 blocs pour qu'un autre naisse. */
    public static final int CROWD = 4;
    private boolean chosen;

    public Crustacean(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPathfindingMalus(BlockPathTypes.WATER, 0F);
        setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0F);
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ARMOR, 2);
    }

    public boolean aquatic() {
        return getType() == PiCrustaceans.AQUATIC.get();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SPECIES, 0);
        entityData.define(SIZE, 1F);
    }

    public CrustaceanDef def() {
        CrustaceanDef[] all = GeneratedCrustaceans.SPECIES;
        return all[Mth.clamp(entityData.get(SPECIES), 0, all.length - 1)];
    }

    public int species() {
        return entityData.get(SPECIES);
    }

    /** Taille de l'individu (0.8..1.2 de celle de l'espece). */
    public float individual() {
        return entityData.get(SIZE);
    }

    public float scale() {
        return def().size() * individual();
    }

    public void set(int species, float size) {
        entityData.set(SPECIES, species);
        entityData.set(SIZE, size);
        chosen = true;
        CrustaceanDef d = def();
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(d.health());
            setHealth(d.health());
        }
        refreshDimensions();
    }

    /** Taille reelle en cm. */
    public int centimetres() {
        CrustaceanDef d = def();
        float t = Mth.clamp((individual() - 0.8F) / 0.4F, 0, 1);
        return Math.round(d.cmMin() + (d.cmMax() - d.cmMin()) * t);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SPECIES.equals(key) || SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    @NotNull
    @Override
    public EntityDimensions getDimensions(@NotNull Pose pose) {
        return super.getDimensions(pose).scale(Math.min(2F, scale()));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 5F, 1.0, 1.3));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.9, 60));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    /** Lieux de promenade : l'eau pour les aquatiques (jamais la terre), le sable et le bord de l'eau pour ceux du rivage. */
    @Override
    public float getWalkTargetValue(@NotNull BlockPos pos, @NotNull LevelReader level) {
        boolean water = level.getFluidState(pos).is(FluidTags.WATER);
        if (aquatic()) {
            return water ? 10F : -100F;
        }
        return water ? 4F : level.getBlockState(pos.below()).is(BlockTags.SAND) ? 8F : 0F;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return !aquatic();
    }

    @Override
    public void baseTick() {
        int air = getAirSupply();
        super.baseTick();
        if (aquatic() && isAlive() && !level().isClientSide) {
            //Comme les animaux aquatiques : hors de l'eau, l'air s'epuise puis il suffoque
            if (isInWaterOrBubble()) {
                setAirSupply(300);
            } else {
                setAirSupply(air - 1);
                if (getAirSupply() <= -20) {
                    setAirSupply(0);
                    hurt(damageSources().drown(), 2F);
                }
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !hasCustomName();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 3;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty, @NotNull MobSpawnType reason,
          @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (!chosen) {
            int s = CrustaceanSpawns.pick(level.getLevel(), blockPosition(), random, aquatic());
            if (s < 0) {
                s = CrustaceanSpawns.any(random, aquatic());
            }
            set(s, 0.8F + random.nextFloat() * 0.4F);
        }
        if (aquatic()) {
            //Au fond de l'eau
            BlockPos.MutableBlockPos p = blockPosition().mutable();
            for (int i = 0; i < 24 && level.getFluidState(p.below()).is(FluidTags.WATER); i++) {
                p.move(0, -1, 0);
            }
            moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), 0);
        }
        return out;
    }

    @NotNull
    @Override
    public Component getTypeName() {
        return Component.translatable("farm.pioneers.crustacean." + def().id());
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        ItemStack drop = CrustaceanSpawns.catchOf(species(), individual(), random);
        if (!drop.isEmpty()) {
            spawnAtLocation(drop);
        }
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.TURTLE_HURT_BABY;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_DEATH_BABY;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(SoundEvents.TURTLE_SHAMBLE_BABY, 0.08F, 1.6F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.3F;
    }

    /**
     * 1.0.4-beta (lot 28) : un seau d'eau sur un crustace vivant le met au SEAU (espece et taille gardees : seau de crustace, pour l'aquarium
     * ou pour le relacher ailleurs).
     */
    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player,
          @NotNull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.WATER_BUCKET) && isAlive()) {
            playSound(SoundEvents.BUCKET_FILL_FISH, 1F, 1F);
            ItemStack bucket = new ItemStack(PiAquarium.CRUSTACEAN_BUCKET.get());
            CompoundTag t = bucket.getOrCreateTag();
            t.putInt("Species", species());
            t.putFloat("Size", individual());
            if (hasCustomName()) {
                bucket.setHoverName(getCustomName());
            }
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, bucket, false));
            if (!level().isClientSide) {
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Species", species());
        tag.putFloat("Size", individual());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Species")) {
            set(tag.getInt("Species"), tag.getFloat("Size"));
        }
    }

    public static boolean crowded(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(Crustacean.class, new AABB(pos).inflate(16), c -> true).size() >= CROWD;
    }

    /** Regle d'apparition des aquatiques : dans l'eau, une espece pour ce milieu, coin pas surpeuple. */
    public static boolean checkAquatic(EntityType<Crustacean> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        ServerLevel sl = level.getLevel();
        return reason == MobSpawnType.SPAWNER || CrustaceanSpawns.pick(sl, pos, random, true) >= 0 && !crowded(sl, pos);
    }

    /** Regle d'apparition du rivage : sur le sable, au jour, une espece pour cette plage, coin pas surpeuple. */
    public static boolean checkShore(EntityType<Crustacean> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.below()).is(BlockTags.SAND) || level.getRawBrightness(pos, 0) < 8) {
            return false;
        }
        ServerLevel sl = level.getLevel();
        return reason == MobSpawnType.SPAWNER || FishSpawns.habitat(sl, pos) >= 0 && CrustaceanSpawns.pick(sl, pos, random, false) >= 0
                                                 && !crowded(sl, pos);
    }
}
