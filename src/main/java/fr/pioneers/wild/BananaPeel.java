package fr.pioneers.wild;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 8) : la PEAU DE BANANE que le singe jette apres son repas : une petite entite visible qui vole, tombe, tournoie, se pose et
 * disparait au bout de {@link GeneratedWild8#PEEL_LIFE} ticks. Ephemere : jamais sauvee (type {@code noSave}), aucune collision, rien a
 * ramasser (ce n'est pas un objet : elle n'encombre ni le sol ni les inventaires). Rendu : {@code client/wild/BananaPeelRenderer}.
 */
public class BananaPeel extends Entity {

    public BananaPeel(EntityType<? extends BananaPeel> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    /** Jetee depuis {@code from} avec la vitesse {@code v}. */
    public static BananaPeel toss(Level level, Vec3 from, Vec3 v) {
        BananaPeel p = PiWild8.BANANA_PEEL.get().create(level);
        if (p != null) {
            p.moveTo(from.x, from.y, from.z, level.random.nextFloat() * 360F, 0F);
            p.setDeltaMovement(v);
            level.addFreshEntity(p);
        }
        return p;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (!isNoGravity()) {
            v = v.add(0, -0.04, 0);
        }
        move(MoverType.SELF, v);
        v = getDeltaMovement().scale(onGround() ? 0.5 : 0.98);
        setDeltaMovement(onGround() ? new Vec3(v.x, 0, v.z) : v);
        if (!level().isClientSide && tickCount >= GeneratedWild8.PEEL_LIFE) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @NotNull
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
