package fr.pioneers.space;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * Fusee "spectacle" : uniquement visuelle, sans passager, sans logique de voyage et jamais sauvegardee.
 * Elle sert aux lancements automatiques (sondes, cargos) : montee jusqu'a l'orbite, descente retropropulsee avec
 * deploiement des jambes, ou explosion en vol. Toute la logique (cargaison, sonde, resultat) reste dans les machines.
 */
public class LaunchVisualEntity extends Entity {

    public static final int ASCENT_TICKS = 160, DESCENT_TICKS = 110;
    /** Hauteur a laquelle une fusee en descente apparait au-dessus de son pas de tir. */
    public static final double DESCENT_HEIGHT = 90;
    /** Modele : index du tier de la fusee. */

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(LaunchVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DESCENDING = SynchedEntityData.defineId(LaunchVisualEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> EXPLODE_AT = SynchedEntityData.defineId(LaunchVisualEntity.class, EntityDataSerializers.INT);

    private double startY;
    private double speed;

    public LaunchVisualEntity(EntityType<? extends LaunchVisualEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Montee (lancement) d'un modele donne, eventuellement avec explosion au tick {@code explodeAt}. */
    public void setup(int kind, boolean descending, int explodeAt) {
        entityData.set(KIND, kind);
        entityData.set(DESCENDING, descending);
        entityData.set(EXPLODE_AT, explodeAt);
        startY = getY();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
        entityData.define(DESCENDING, false);
        entityData.define(EXPLODE_AT, -1);
    }

    public int getKind() {
        return entityData.get(KIND);
    }

    public boolean isDescending() {
        return entityData.get(DESCENDING);
    }

    /** Progression de la phase (0..1). */
    public float getProgress(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / (isDescending() ? DESCENT_TICKS : ASCENT_TICKS), 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount == 1) {
            startY = getY();
        }
        if (isDescending()) {
            //Descente freinee : vitesse nulle au contact du sol
            float t = Mth.clamp(tickCount / (float) DESCENT_TICKS, 0, 1);
            double target = startY - DESCENT_HEIGHT * (1 - (1 - t) * (1 - t));
            setPos(getX(), target, getZ());
            if (level().isClientSide) {
                exhaust(t > 0.55F ? 1.2F : 0.5F, t > 0.85F);
            } else if (tickCount >= DESCENT_TICKS) {
                discard();
            }
        } else {
            speed = Math.min(speed + 0.02, 2.0);
            setPos(getX(), getY() + speed, getZ());
            int explodeAt = entityData.get(EXPLODE_AT);
            if (level().isClientSide) {
                exhaust(1.0F, tickCount < 40);
            } else if (explodeAt >= 0 && tickCount >= explodeAt) {
                if (level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 3, getZ(), 2, 0.5, 1, 0.5, 0);
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 3, getZ(), 60, 1.5, 3, 1.5, 0.05);
                    serverLevel.sendParticles(ParticleTypes.LAVA, getX(), getY() + 3, getZ(), 30, 1, 1, 1, 0.2);
                    serverLevel.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 8.0F, 0.7F);
                }
                discard();
            } else if (tickCount >= ASCENT_TICKS) {
                discard();
            } else if (tickCount % 10 == 0) {
                level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 3.0F, 0.4F);
            }
        }
    }

    private void exhaust(float intensity, boolean groundCloud) {
        for (int i = 0; i < 6 * intensity + 1; i++) {
            double ox = (random.nextDouble() - 0.5) * 0.6, oz = (random.nextDouble() - 0.5) * 0.6;
            level().addParticle(ParticleTypes.FLAME, getX() + ox, getY() - 0.1, getZ() + oz, ox * 0.2, -0.5 - random.nextDouble() * 0.4, oz * 0.2);
            if (random.nextFloat() < intensity * 0.7F) {
                level().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX() + ox * 2, getY() - 0.6, getZ() + oz * 2, ox * 0.1, -0.05, oz * 0.1);
            }
        }
        if (groundCloud) {
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                level().addParticle(ParticleTypes.CLOUD, getX(), getY() - (isDescending() ? 0.2 : 0) + 0.1, getZ(), Math.cos(angle) * 0.4, 0.02,
                      Math.sin(angle) * 0.4);
            }
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    /** Les grandes fusees (jusqu'a 18 blocs) restent visibles meme quand leur base sort de l'ecran. */
    @NotNull
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().expandTowards(0, 12, 0).inflate(1.5, 0, 1.5);
    }
}
