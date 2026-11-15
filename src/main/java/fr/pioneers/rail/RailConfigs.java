package fr.pioneers.rail;

import fr.pioneers.rail.block.ItemLoaderTile;
import fr.pioneers.rail.block.RailPostTile;
import fr.pioneers.rail.block.StationTile;
import fr.pioneers.rail.logistics.StationLogistics;
import fr.pioneers.rail.net.RailNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.7 : reglages des blocs du chemin de fer copies dans les plans des robots (comme les reglages des points logistiques) : gare (nom,
 * limite, logistique), postes de quai (sens). Cle {@code rail} du reglage. 1.9.2 (retours de Guillaume) : plus de plaque tournante ni de
 * transbordeur ; un ancien reglage « table » d'un plan est ignore.
 */
public final class RailConfigs {

    public static final String KEY = "rail";

    private RailConfigs() {
    }

    public static boolean isRail(CompoundTag tag) {
        return tag.contains(KEY);
    }

    /** Reglage du bloc a cette position (vue du client : ce que les tiles synchronisent), ou null. */
    @Nullable
    public static CompoundTag capture(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag t = new CompoundTag();
        if (be instanceof StationTile st) {
            t.putString(KEY, "station");
            t.putString("name", st.name());
            t.putInt("limit", st.limit());
            t.put("logi", st.logistics().copy());
        } else if (be instanceof ItemLoaderTile il) {
            t.putString(KEY, "post");
            t.putBoolean("unload", il.unloads());
        } else if (be instanceof RailPostTile rp) {
            t.putString(KEY, "post");
            t.putBoolean("unload", rp.unloads());
        } else {
            return null;
        }
        return t;
    }

    /** Reglage tourne avec le collage (1.9.2 : plus aucun reglage oriente depuis le retrait des tables). */
    public static CompoundTag rotate(CompoundTag tag, Rotation rot) {
        return tag;
    }

    /** Applique le reglage au bloc pose (serveur). */
    public static void apply(ServerLevel level, BlockPos pos, CompoundTag tag) {
        if (!level.isLoaded(pos)) {
            return;
        }
        RailWorld w = RailWorld.get(level);
        BlockEntity be = level.getBlockEntity(pos);
        switch (tag.getString(KEY)) {
            case "station" -> {
                RailNetwork.Station st = w.net.station(pos);
                if (st == null) {
                    return;
                }
                String name = tag.getString("name").trim();
                if (!name.isEmpty() && name.length() <= 64) {
                    st.name = name;
                }
                st.limit = Math.max(-1, Math.min(64, tag.getInt("limit")));
                if (!tag.getCompound("logi").isEmpty()) {
                    st.logistics = StationLogistics.load(tag.getCompound("logi"));
                }
                w.setDirty();
                if (be instanceof StationTile tile) {
                    tile.refresh(level);
                }
            }
            case "post" -> {
                if (be instanceof ItemLoaderTile il) {
                    il.setUnloads(tag.getBoolean("unload"));
                } else if (be instanceof RailPostTile rp) {
                    rp.setUnloads(tag.getBoolean("unload"));
                }
            }
            default -> {
            }
        }
    }
}
