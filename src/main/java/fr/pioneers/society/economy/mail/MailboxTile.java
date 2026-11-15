package fr.pioneers.society.economy.mail;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.society.economy.EconomyApi;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 29) : tuile de la boite aux lettres : seulement son proprietaire ({@link PiBlockEntity#owner()}) et son nom, envoyes au
 * client (le « ! » n'est dessine que chez le proprietaire). Le contenu est dans {@link MailData} (une boite par joueur).
 */
public class MailboxTile extends PiBlockEntity {

    private String ownerName = "";

    public MailboxTile(BlockPos pos, BlockState state) {
        super(PiMail.MAILBOX_TILE.get(), pos, state);
    }

    /** La boite devient celle de ce joueur. */
    public void claim(ServerPlayer player) {
        setOwner(player.getUUID());
        ownerName = EconomyApi.name(player);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public String ownerName() {
        return ownerName;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("owner_name", ownerName);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        ownerName = tag.getString("owner_name");
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Boite de rendu jusqu'au « ! » au-dessus du bloc. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 1.2, 0);
    }
}
