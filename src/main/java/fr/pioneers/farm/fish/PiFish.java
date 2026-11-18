package fr.pioneers.farm.fish;

import fr.pioneers.farm.PiFishing;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Poisson du mod (une entite pour toutes les especes de {@link GeneratedFish}) : espece et taille synchronisees (modele, robe, boite), espece
 * tiree a l'apparition selon le milieu ({@link FishSpawns}). Ne disparait pas au loin (pisciculture) ; pond tres rarement dans les herbiers
 * ({@link FishRoeBlock}) si le coin n'est pas surpeuple. Se met au seau (espece et taille gardees).
 * <p>
 * 1.0.3-beta (Guillaume : « reduire la reproduction et le repeuplement dans une meme etendue d'eau ») : deux plafonds au lieu d'un.
 * Avant : 9 poissons au plus dans 16 blocs ({@code CROWD}) pour l'apparition, la ponte et l'eclosion, ponte 1 fois sur 24 000 ticks.
 * Maintenant : plafond de ZONE ({@link #ZONE} 16 blocs) de {@link #SPAWN_CAP} 10 (6 avant la 1.0.5-beta lot 13) pour l'apparition et
 * de {@link #BREED_CAP} 4 pour la ponte et
 * l'eclosion ; plafond par ETENDUE D'EAU : au plus 1 poisson par {@link #WATER_PER_FISH} 32 blocs d'eau reliee au point (au moins 1), une
 * etendue de {@link #BODY_SCAN} 256 blocs ou plus ne comptant que pour le plafond de zone (lac, riviere, mer) ; ponte 1 fois sur
 * {@link #ROE_CHANCE} 48 000 ticks. La peche ne depend pas des poissons presents (tirage par le milieu, {@link FishSpawns}) : inchangee.
 * <p>
 * 1.0.5-beta (lot 4, spec §3 : faune persistante) : les poissons ne disparaissent plus et n'apparaissent plus au hasard ; ils sont poses a
 * la generation des troncons et par le repeuplement de la zone (groupe des poissons, {@link fr.pioneers.world.fauna.FaunaRegistry}). PLAFONDS
 * UNIFIES : le repeuplement passe par {@link #checkSpawn} (plafond de zone {@link #SPAWN_CAP} et d'etendue d'eau, comme l'ancienne
 * apparition naturelle), puis par le plafond du groupe des poissons de la zone de 128 blocs (reglage {@code fauna.capFish}) ; la ponte et
 * l'eclosion gardent {@link #BREED_CAP} (pisciculture, frai et aquariums inchanges ; un bassin dans un claim n'est pas compte dans la
 * faune sauvage).
 * <p>
 * 1.0.5-beta (lot 13, Guillaume : « trop peu de poissons apparaissent ») : plafond d'apparition de zone 6 -> 10 (bancs de 3 a 6 qui
 * tiennent cote a cote) ; bancs plus gros et plus nombreux (T:farm2/data_fish.py FISH_SPAWNS, J:world/fauna/FaunaRules.WATER_CHANCE 0,25) ;
 * le plafond du groupe des poissons par zone de 128 blocs (48, budget du lot 4) est inchange.
 */
public class PiFish extends AbstractFish {

    private static final EntityDataAccessor<Integer> SPECIES = SynchedEntityData.defineId(PiFish.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(PiFish.class, EntityDataSerializers.FLOAT);
    /** Plafonds (1.0.3-beta, voir la classe) : apparition, ponte et eclosion dans {@link #ZONE} blocs ; eau par poisson d'une petite etendue. */
    public static final int ZONE = 16, SPAWN_CAP = 10, BREED_CAP = 4, WATER_PER_FISH = 32, BODY_SCAN = 256, ROE_CHANCE = 48000;
    private boolean chosen;

    /** 1.0.3-beta lot 8 : espece fixee par le type ({@code pioneers:fish_<id>}) ; -1 pour le type generique {@code pioneers:fish}. */
    private final int fixed;

    public PiFish(EntityType<? extends AbstractFish> type, Level level) {
        super(type, level);
        fixed = PiFishing.speciesOf(type);
        if (fixed >= 0) {
            entityData.set(SPECIES, fixed);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SPECIES, 0);
        entityData.define(SIZE, 1F);
    }

    public FishDef def() {
        FishDef[] all = GeneratedFish.SPECIES;
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
        entityData.set(SPECIES, fixed >= 0 ? fixed : species);
        entityData.set(SIZE, size);
        chosen = true;
        refreshDimensions();
    }

    /** Taille reelle en cm. */
    public int centimetres() {
        FishDef d = def();
        float t = Mth.clamp((individual() - 0.8F) / 0.4F, 0, 1);
        return Math.round(d.cmMin() + (d.cmMax() - d.cmMin()) * t);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SPECIES.equals(key) || SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    /**
     * Boite : celle de l'espece (type {@code pioneers:fish_<id>}, mesuree sur son modele) a la taille de l'individu ; type generique (avant la
     * conversion) : l'ancienne boite a l'echelle du modele.
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (fixed >= 0) {
            return super.getDimensions(pose).scale(individual());
        }
        return super.getDimensions(pose).scale(Math.min(3F, scale()));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data,
          @Nullable CompoundTag tag) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (!chosen) {
            //1.0.5-beta (lot 25) : jamais une espece de peche seulement (requin gris)
            int s = FishSpawns.pickWild(level.getLevel(), blockPosition(), random);
            set(Math.max(0, s), 0.8F + random.nextFloat() * 0.4F);
        }
        return out;
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("farm.pioneers.fish." + def().id());
    }

    // ------------------------------------------------------------------ vie

    /**
     * 1.4 : comme les poissons vanilla, un poisson sauvage disparait loin des joueurs (sinon il ne comptait pas dans le plafond d'apparition et
     * les eaux se remplissaient sans fin) ; seul un poisson sorti d'un seau ou nomme reste.
     * 1.0.5-beta (lot 4) : persistant (plus d'apparition au hasard : les eaux ne se remplissent plus) ; regle d'avant si {@code fauna.enabled}
     * est faux.
     */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return !fr.pioneers.world.fauna.FaunaSettings.current().enabled() && !fromBucket() && !hasCustomName();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && isInWater() && random.nextInt(ROE_CHANCE) == 0) {
            spawnRoe();
        }
    }

    /** Ponte : un herbier voisin devient un amas d'oeufs de l'espece (si le coin et l'etendue d'eau ne sont pas surpeuples). */
    private void spawnRoe() {
        if (FishSpawns.catchOnly(def()) || crowded(level(), blockPosition(), BREED_CAP)) {
            return;
        }
        BlockPos at = blockPosition();
        for (int i = 0; i < 12; i++) {
            BlockPos p = at.offset(random.nextInt(7) - 3, random.nextInt(5) - 3, random.nextInt(7) - 3);
            BlockState s = level().getBlockState(p);
            if (s.is(Blocks.SEAGRASS)) {
                level().setBlock(p, PiFishing.ROE_BLOCK.get().defaultBlockState().setValue(FishRoeBlock.SPECIES, species()), 3);
                return;
            }
        }
    }

    /**
     * Surpeuple : {@code zoneCap} poissons ou plus dans {@link #ZONE} blocs, ou, dans une petite etendue d'eau (moins de {@link #BODY_SCAN}
     * blocs relies au point), autant de poissons que {@link #bodyCap} dans l'etendue elle-meme.
     */
    public static boolean crowded(Level level, BlockPos pos, int zoneCap) {
        List<PiFish> near = level.getEntitiesOfClass(PiFish.class, new AABB(pos).inflate(ZONE), f -> true);
        if (near.size() >= zoneCap) {
            return true;
        }
        Set<BlockPos> body = waterBody(level, pos, BODY_SCAN);
        if (body.size() >= BODY_SCAN) {
            return false;
        }
        int cap = bodyCap(body.size(), zoneCap), n = 0;
        for (PiFish f : near) {
            if (body.contains(f.blockPosition()) && ++n >= cap) {
                return true;
            }
        }
        return false;
    }

    /** Poissons admis dans une etendue de {@code blocks} blocs d'eau : 1 par {@link #WATER_PER_FISH}, au moins 1, au plus {@code zoneCap}. */
    public static int bodyCap(int blocks, int zoneCap) {
        return blocks >= BODY_SCAN ? zoneCap : Mth.clamp(blocks / WATER_PER_FISH, 1, zoneCap);
    }

    /** Eau reliee a {@code start} par les faces (blocs dont le fluide est de l'eau, chunks charges), au plus {@code limit} blocs. */
    public static Set<BlockPos> waterBody(Level level, BlockPos start, int limit) {
        Set<BlockPos> seen = new HashSet<>();
        if (!level.getFluidState(start).is(FluidTags.WATER)) {
            return seen;
        }
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(start.immutable());
        todo.add(start.immutable());
        while (!todo.isEmpty() && seen.size() < limit) {
            BlockPos p = todo.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.size() >= limit) {
                    break;
                }
                if (!seen.contains(q) && level.isLoaded(q) && level.getFluidState(q).is(FluidTags.WATER)) {
                    seen.add(q);
                    todo.add(q);
                }
            }
        }
        return seen;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        spawnAtLocation(FishItems.catchOf(this));
    }

    // ------------------------------------------------------------------ seau

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(PiFishing.FISH_BUCKET.get());
    }

    @Override
    public void saveToBucketTag(ItemStack bucket) {
        super.saveToBucketTag(bucket);
        CompoundTag t = bucket.getOrCreateTag();
        t.putInt("Species", species());
        t.putFloat("Size", individual());
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        if (tag.contains("Species")) {
            set(tag.getInt("Species"), tag.contains("Size") ? tag.getFloat("Size") : 1F);
        }
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Species", species());
        tag.putFloat("Size", individual());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Species")) {
            set(tag.getInt("Species"), tag.getFloat("Size"));
        }
    }

    public static boolean checkSpawn(EntityType<PiFish> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
          RandomSource random) {
        if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getBlockState(pos.above()).is(Blocks.WATER)
            && !level.getFluidState(pos.above()).is(FluidTags.WATER) && !level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        ServerLevel sl = level.getLevel();
        return !crowded(sl, pos, SPAWN_CAP) && !level.getBlockState(pos.below()).is(BlockTags.ICE);
    }
}
