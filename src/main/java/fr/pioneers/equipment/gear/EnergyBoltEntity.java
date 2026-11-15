package fr.pioneers.equipment.gear;

import fr.pioneers.core.registries.PiGear;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * 0.20.15 : tir du fusil a plasma : orbe lumineux (objet {@code energy_bolt}, texture du tier par custom_model_data), droit et sans chute,
 * trainee de la couleur du tier ; s'eteint apres 3 secondes ou au premier obstacle. Les tirs d'une salve touchent tous (l'invulnerabilite
 * apres un coup est levee).
 */
public class EnergyBoltEntity extends ThrowableItemProjectile {

    private static final int LIFE = 60;
    private float damage = 6;
    private int tier;

    public EnergyBoltEntity(EntityType<? extends EnergyBoltEntity> type, Level level) {
        super(type, level);
    }

    public EnergyBoltEntity(Level level, LivingEntity owner, ModularTier tier, float damage) {
        super(PiGear.ENERGY_BOLT.get(), owner, level);
        this.damage = damage;
        this.tier = tier.ordinal();
        ItemStack look = new ItemStack(getDefaultItem());
        look.getOrCreateTag().putInt("CustomModelData", tier.ordinal() + 1);
        setItem(look);
    }

    @NotNull
    @Override
    protected Item getDefaultItem() {
        return PiGear.ENERGY_BOLT_ITEM.get();
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int color = ModularTier.values()[Math.max(0, Math.min(3, colorTier()))].color;
            Vector3f c = new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
            Vec3 v = getDeltaMovement();
            for (int k = 0; k < 2; k++) {
                double f = k / 2.0;
                level().addParticle(new DustParticleOptions(c, 0.9F), getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0, 0);
            }
        } else if (tickCount > LIFE) {
            discard();
        }
    }

    /** Tier lu sur l'objet affiche (synchronise), pour la trainee cote client. */
    private int colorTier() {
        ItemStack stack = getItem();
        return stack.hasTag() ? stack.getTag().getInt("CustomModelData") - 1 : tier;
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (target == getOwner()) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurt(damageSources().thrown(this, getOwner()), damage);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);
    }

    @Override
    protected void onHit(@NotNull HitResult result) {
        super.onHit(result);
        if (result.getType() == HitResult.Type.ENTITY && ((EntityHitResult) result).getEntity() == getOwner()) {
            return;
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.2);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putByte("Tier", (byte) tier);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        tier = tag.getByte("Tier");
    }
}
