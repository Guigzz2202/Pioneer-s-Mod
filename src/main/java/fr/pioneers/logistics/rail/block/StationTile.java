package fr.pioneers.logistics.rail.block;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.net.RailNetwork;
import fr.pioneers.core.registries.PiRail;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 : panneau de gare : garde le nom pour l'affichage (le reseau garde le sien, qui fait foi) et le renvoie aux clients.
 */
public class StationTile extends PiBlockEntity {

    private String name = "";
    private int limit = -1;
    /** 0.20.7 : reglages logistiques de la gare, recopies pour les plans des robots (copier / coller). */
    private CompoundTag logi = new CompoundTag();

    public StationTile(BlockPos pos, BlockState state) {
        super(PiRail.STATION_TILE.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public int limit() {
        return limit;
    }

    public CompoundTag logistics() {
        return logi;
    }

    /** Recopie le nom, la limite et la logistique du reseau (qui fait foi). */
    public void refresh(ServerLevel level) {
        RailNetwork.Station st = RailWorld.get(level).net.station(worldPosition);
        if (st != null) {
            name = st.name;
            limit = st.limit;
            logi = st.logistics.save();
            sync();
        }
    }

    public void rename(ServerLevel level, String newName) {
        String n = newName.trim();
        if (n.isEmpty() || n.length() > 64) {
            return;
        }
        RailNetwork.Station st = RailWorld.get(level).net.station(worldPosition);
        if (st == null) {
            st = StationBlock.register(level, worldPosition, getBlockState(), n);
        }
        st.name = n;
        name = n;
        RailWorld.get(level).setDirty();
        sync();
    }

    public void setLimit(ServerLevel level, int newLimit) {
        RailNetwork.Station st = RailWorld.get(level).net.station(worldPosition);
        if (st != null) {
            st.limit = Math.max(-1, Math.min(64, newLimit));
            limit = st.limit;
            RailWorld.get(level).setDirty();
            sync();
        }
    }

    /** Recopie le nom du reseau (chargement du chunk). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) {
            RailNetwork.Station st = RailWorld.get(sl).net.station(worldPosition);
            if (st != null && (!st.name.equals(name) || st.limit != limit || !st.logistics.save().equals(logi))) {
                name = st.name;
                limit = st.limit;
                logi = st.logistics.save();
                sync();
            }
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("name", name);
        tag.putInt("limit", limit);
        tag.put("logi", logi);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        name = tag.getString("name");
        limit = tag.contains("limit") ? tag.getInt("limit") : -1;
        logi = tag.getCompound("logi");
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
