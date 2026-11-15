package fr.pioneers.bronze.autofoundry;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.registries.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4) : tile du conduit de coulee : seulement ce que le rendu montre (le metal qui coule en ce moment : metal, ticks restants,
 * bec qui verse dans le moule du dessous). La fonderie la REPREND a chaque coulee le long du chemin choisi ({@link #flow}) ; le client
 * decompte seul entre deux envois. Rien n'est sauve (une coulee interrompue repart de la fonderie).
 */
public class CastingChannelTile extends PiBlockEntity {

    private String metal = "";
    private int ticks;
    private boolean spout;
    /** Serveur : heure de fin de la coulee deja annoncee au client (pas de renvoi tant qu'elle court). */
    private long announced;

    public CastingChannelTile(BlockPos pos, BlockState state) {
        super(PiBronze.CASTING_CHANNEL_TILE.get(), pos, state);
    }

    public String metal() {
        return metal;
    }

    /** Le metal coule-t-il en ce moment ? */
    public boolean flowing() {
        return ticks > 0 && !metal.isEmpty();
    }

    /** Le bec verse-t-il dans le moule du dessous ? */
    public boolean spout() {
        return spout && flowing();
    }

    /**
     * Serveur : du metal coule ici pendant {@code duration} ticks (prolonge si la coulee continue) ; {@code pouring} : ce conduit est le
     * dernier du chemin, son bec verse dans le moule du dessous.
     */
    public void flow(String pouredMetal, int duration, boolean pouring) {
        if (level == null || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        boolean news = !pouredMetal.equals(metal) || pouring != spout || announced - now < 4;
        metal = pouredMetal;
        spout = pouring;
        ticks = duration;
        if (news) {
            announced = now + ticks;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Serveur et client : le flot s'epuise. */
    void tick() {
        if (ticks > 0) {
            ticks--;
        }
    }

    void clientTick() {
        tick();
        if (level != null && spout() && level.random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, worldPosition.getX() + 0.4 + level.random.nextDouble() * 0.2, worldPosition.getY() - 0.3,
                  worldPosition.getZ() + 0.4 + level.random.nextDouble() * 0.2, 0, 0.03, 0);
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("metal", metal);
        tag.putInt("ticks", ticks);
        tag.putBoolean("spout", spout);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        metal = tag.getString("metal");
        ticks = tag.getInt("ticks");
        spout = tag.getBoolean("spout");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, -1, 0);
    }
}
