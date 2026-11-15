package fr.pioneers.society.clan.territory;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.tech.kinetics.KineticNetworks;
import fr.pioneers.tech.kinetics.KineticNode;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 1.9 (MAJ RP, bloc 4) : une frontiere de claim a bouge (claim, abandon, declin, liberation) : les reseaux de cables (objets, fluides, gaz,
 * energie), de donnees et d'arbres qui passent par ce chunk sont dissous et refaits en fin de tick ; leur parcours ne franchit plus une frontiere
 * refusee par {@code ClanApi.canTransfer}. Un reseau qui traverse la frontiere a forcement un cable dans le chunk : il suffit de le lire.
 */
public final class BorderSplits {

    private BorderSplits() {
    }

    /**
     * Les relations d'un clan ont change (alliance signee ou rompue, faction rejointe ou quittee, guerre) : tous ses chunks charges refont
     * leurs reseaux. A appeler par la diplomatie et les factions.
     */
    public static void clanChanged(MinecraftServer server, Clan clan) {
        for (var e : clan.claims.entrySet()) {
            ServerLevel level = ClanTerritory.level(server, e.getKey());
            if (level != null) {
                for (long chunk : e.getValue().keySet()) {
                    changed(level, chunk);
                }
            }
        }
        TerritoryNet.bump();
    }

    public static void changed(ServerLevel level, long chunk) {
        //1.11 : reseaux logistiques qui passent par ce chunk (cables nus compris : ils n'ont pas de tile)
        LogisticNetworks.chunkChanged(level, chunk);
        LevelChunk c = level.getChunkSource().getChunkNow(TerritoryRules.cx(chunk), TerritoryRules.cz(chunk));
        if (c == null) {
            //chunk decharge : ses cables refont leur reseau en se chargeant
            return;
        }
        for (BlockEntity be : new ArrayList<>(c.getBlockEntities().values())) {
            BlockPos pos = be.getBlockPos();
            if (be instanceof KineticNode) {
                KineticNetworks.invalidate(level, pos);
            }
        }
    }
}
