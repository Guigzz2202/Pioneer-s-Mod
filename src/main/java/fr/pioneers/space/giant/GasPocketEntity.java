package fr.pioneers.space.giant;

import fr.pioneers.core.registries.PiSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * Poche de gaz flottant dans l'atmosphere d'une geante gazeuse : une bulle lumineuse qui derive lentement et se dissipe au bout de
 * quelques minutes. Les drones collecteurs la vident ; le scanner de gaz la revele et la designe aux drones (clic droit).
 * Les poches ne sont pas sauvegardees : elles apparaissent autour des joueurs et des baies de drones actives.
 */
public class GasPocketEntity extends Entity {

    /** Duree de vie (ticks). */
    public static final int LIFETIME = 12_000;

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(GasPocketEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> AMOUNT = SynchedEntityData.defineId(GasPocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAPACITY = SynchedEntityData.defineId(GasPocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RICH = SynchedEntityData.defineId(GasPocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MARKED = SynchedEntityData.defineId(GasPocketEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(GasPocketEntity.class, EntityDataSerializers.INT);

    private int revealedUntil;
    /** Drone qui vise cette poche (id d'entite, -1 aucun). */
    private int claimedBy = -1;
    private Vec3 drift = Vec3.ZERO;

    public GasPocketEntity(EntityType<? extends GasPocketEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, (byte) 0);
        entityData.define(AMOUNT, 1000);
        entityData.define(CAPACITY, 1000);
        entityData.define(RICH, false);
        entityData.define(MARKED, false);
        entityData.define(AGE, 0);
    }

    public void setup(PocketKind kind, boolean rich) {
        entityData.set(KIND, (byte) kind.ordinal());
        int amount = kind.rollAmount(random, rich);
        entityData.set(AMOUNT, amount);
        entityData.set(CAPACITY, amount);
        entityData.set(RICH, rich);
        double angle = random.nextDouble() * Math.PI * 2;
        drift = new Vec3(Math.cos(angle) * 0.01, 0, Math.sin(angle) * 0.01);
        refreshDimensions();
    }

    public PocketKind getKind() {
        return PocketKind.values()[Mth.clamp(entityData.get(KIND), 0, PocketKind.values().length - 1)];
    }

    public int getAmount() {
        return entityData.get(AMOUNT);
    }

    public int getCapacity() {
        return Math.max(1, entityData.get(CAPACITY));
    }

    public boolean isRich() {
        return entityData.get(RICH);
    }

    public boolean isMarked() {
        return entityData.get(MARKED);
    }

    public int getAge() {
        return entityData.get(AGE);
    }

    /** Rayon visible de la bulle (selon ce qu'il reste). */
    public float getRadius() {
        float fill = Mth.sqrt(getAmount() / (float) getCapacity());
        return (isRich() ? 1.1F : 0.7F) + 1.5F * fill;
    }

    public boolean isClaimed() {
        return claimedBy >= 0 && level().getEntity(claimedBy) instanceof HarvesterDroneEntity drone && drone.isAlive();
    }

    public void claim(int droneId) {
        claimedBy = droneId;
    }

    public void release(int droneId) {
        if (claimedBy == droneId) {
            claimedBy = -1;
        }
    }

    /** Couche des orages : reservee aux drones blindes. */
    public boolean isInStorm() {
        return PocketKind.Layer.at(getY()) == PocketKind.Layer.STORM;
    }

    /** Prelevement par un drone ; renvoie la quantite prise. */
    public int drain(int amount) {
        int taken = Math.min(amount, getAmount());
        entityData.set(AMOUNT, getAmount() - taken);
        if (getAmount() <= 0) {
            discard();
        }
        return taken;
    }

    /** Revelee par un scanner : contour lumineux pendant quelques secondes. */
    public void reveal(int ticks) {
        revealedUntil = tickCount + ticks;
        setGlowingTag(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            entityData.set(AGE, getAge() + 1);
            if (getAge() > LIFETIME) {
                discard();
                return;
            }
            if (revealedUntil > 0 && tickCount > revealedUntil) {
                revealedUntil = 0;
                setGlowingTag(false);
            }
            if (tickCount % 20 == 0) {
                refreshDimensions();
            }
        } else {
            //Volutes et etincelles (poches riches)
            float radius = getRadius();
            if (random.nextFloat() < 0.35F) {
                int c = getKind().color;
                Vector3f color = new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
                double angle = random.nextDouble() * Math.PI * 2, pitch = (random.nextDouble() - 0.5) * Math.PI;
                level().addParticle(new DustParticleOptions(color, 1.5F), getX() + Math.cos(angle) * Math.cos(pitch) * radius,
                      getY() + radius + Math.sin(pitch) * radius, getZ() + Math.sin(angle) * Math.cos(pitch) * radius, 0, 0.01, 0);
            }
            if (isRich() && random.nextFloat() < 0.2F) {
                level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * radius * 2,
                      getY() + radius + (random.nextDouble() - 0.5) * radius * 2, getZ() + (random.nextDouble() - 0.5) * radius * 2, 0, 0.005, 0);
            }
        }
        //Derive lente et balancement
        double bob = Math.sin((tickCount + getId() * 13) * 0.03) * 0.004;
        setPos(getX() + drift.x, getY() + bob, getZ() + drift.z);
    }

    @NotNull
    @Override
    public EntityDimensions getDimensions(@NotNull Pose pose) {
        float size = getRadius() * 2;
        return EntityDimensions.scalable(size, size);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof ItemGasScanner)) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            boolean marked = !isMarked();
            entityData.set(MARKED, marked);
            reveal(200);
            player.displayClientMessage(Component.translatable(marked ? "pocket.pioneers.marked" : "pocket.pioneers.unmarked",
                  Component.translatable(getKind().getTranslationKey()), describeAmount()).withStyle(marked ? ChatFormatting.AQUA : ChatFormatting.GRAY),
                  true);
            level().playSound(null, blockPosition(), PiSounds.SCANNER_PING.get(), SoundSource.PLAYERS, 1.0F, marked ? 1.4F : 0.8F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Quantite restante, avec son unite. */
    public Component describeAmount() {
        return getKind().isGas() ? Component.translatable("pocket.pioneers.amount_gas", getAmount())
                                 : Component.translatable("pocket.pioneers.amount_items", getAmount());
    }

    @Override
    public boolean isPickable() {
        return true;
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
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }
}
