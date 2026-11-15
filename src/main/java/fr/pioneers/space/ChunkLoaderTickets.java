package fr.pioneers.space;

import fr.pioneers.Pioneers;
import fr.pioneers.registries.PiSpaceMachines;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * Au chargement du monde, Forge restaure les chunks forces : on retire ceux dont le chunkloader n'existe plus.
 */
public final class ChunkLoaderTickets {

    private ChunkLoaderTickets() {
    }

    public static void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(Pioneers.MODID, (level, helper) -> {
            for (BlockPos pos : new ArrayList<>(helper.getBlockTickets().keySet())) {
                if (!PiSpaceMachines.CHUNK_LOADERS.stream().anyMatch(b -> b.getBlock() == level.getBlockState(pos).getBlock())) {
                    helper.removeAllTickets(pos);
                }
            }
            //1.9 (MAJ RP, bloc 4) : tickets des clans (au nom de l'identifiant du clan), valides contre ClanData
            fr.pioneers.clan.territory.ClanChunkloading.validate(level, helper);
        });
    }
}
