package fr.pioneers.space.event;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.registries.PiSounds;
import fr.pioneers.space.environment.PiWorldBlocks;
import fr.pioneers.space.tile.TileEntityForceField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Meteore d'une pluie de meteores : il traverse le ciel en flammes et s'ecrase en creusant un cratere ; il se desintegre, ou reste
 * enfoui au fond (meteorite a recuperer). Un champ de force l'arrete.
 */
public class MeteorEntity extends Entity {

    public static final String[] KINDS = {"rock_meteorite", "iron_meteorite", "ice_meteorite", "volcanic_meteorite"};
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, (byte) 0);
        entityData.define(SIZE, 1F);
    }

    public void setup(int kind, float size) {
        entityData.set(KIND, (byte) Mth.clamp(kind, 0, KINDS.length - 1));
        entityData.set(SIZE, size);
    }

    public BlockState getBlock() {
        var block = PiWorldBlocks.METEORITES.get(KINDS[Mth.clamp(entityData.get(KIND), 0, KINDS.length - 1)]);
        return block == null ? Blocks.MAGMA_BLOCK.defaultBlockState() : block.getBlock().defaultBlockState();
    }

    public float getSize() {
        return entityData.get(SIZE);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        Vec3 from = position(), to = from.add(motion);
        if (level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                double t = random.nextDouble();
                level().addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, getX() - motion.x * t, getY() - motion.y * t,
                      getZ() - motion.z * t, (random.nextDouble() - 0.5) * 0.05, 0.02, (random.nextDouble() - 0.5) * 0.05);
            }
            level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
            setPos(to.x, to.y, to.z);
            return;
        }
        if (tickCount == 1) {
            level().playSound(null, blockPosition(), PiSounds.METEOR_FALL.get(), SoundSource.AMBIENT, 6.0F, 0.8F + random.nextFloat() * 0.4F);
        }
        //Champ de force : le meteore se pulverise sur la bulle
        if (level() instanceof ServerLevel server && TileEntityForceField.intercept(server, to, getSize())) {
            server.sendParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 12, 0.8, 0.8, 0.8, 0.2);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, to.x, to.y, to.z, 16, 1.0, 1.0, 1.0, 0.05);
            discard();
            return;
        }
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() != HitResult.Type.MISS) {
            impact(hit);
            return;
        }
        setPos(to.x, to.y, to.z);
        if (getY() < level().getMinBuildHeight() || tickCount > 600) {
            discard();
        }
    }

    private void impact(BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        boolean terrain = PiCommonConfig.get(PiCommonConfig.METEOR_TERRAIN, true);
        float power = terrain ? 1.1F + getSize() * 0.8F : 1.0F + getSize() * 0.6F;
        level().explode(this, at.x, at.y, at.z, power, terrain ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        if (!(level() instanceof ServerLevel server)) {
            discard();
            return;
        }
        server.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 20, 0.6, 0.4, 0.6, 0.1);
        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 12, 0.8, 0.3, 0.8, 0.02);
        if (random.nextFloat() < PiCommonConfig.get(PiCommonConfig.METEOR_EMBED_CHANCE, 0.35)) {
            embed(server, BlockPos.containing(at));
        } else {
            //Desintegre a l'impact : seulement des eclats
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getBlock()), at.x, at.y + 0.5, at.z, 40, 0.8, 0.5, 0.8, 0.2);
        }
        discard();
    }

    /** Le meteore reste enfoui au fond du cratere : un bloc de meteorite, plus quelques fragments pour les plus gros. */
    private void embed(ServerLevel server, BlockPos from) {
        BlockPos.MutableBlockPos pos = from.mutable();
        //Descend jusqu'au fond du cratere
        for (int i = 0; i < 8 && pos.getY() > server.getMinBuildHeight() + 1; i++) {
            BlockState below = server.getBlockState(pos.below());
            if (!below.canBeReplaced()) {
                break;
            }
            pos.move(0, -1, 0);
        }
        //Enfonce dans le sol
        BlockPos core = pos.below();
        BlockState state = getBlock();
        if (server.getBlockState(core).getDestroySpeed(server, core) < 0) {
            core = pos.immutable();
        }
        server.setBlock(core, state, Block.UPDATE_ALL);
        int fragments = Math.round((getSize() - 0.8F) * 3);
        for (int i = 0; i < fragments; i++) {
            BlockPos frag = core.offset(random.nextInt(3) - 1, -random.nextInt(2), random.nextInt(3) - 1);
            if (!frag.equals(core) && !server.getBlockState(frag).canBeReplaced() && server.getBlockState(frag).getDestroySpeed(server, frag) >= 0 && !server.getBlockState(frag).hasBlockEntity()) {
                server.setBlock(frag, state, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }
}
