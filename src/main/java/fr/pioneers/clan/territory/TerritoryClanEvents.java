package fr.pioneers.clan.territory;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.core.ClanEvent;
import fr.pioneers.clan.seal.ClanSeals;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : le territoire suit la vie des clans (evenements des fondations). Clan solo absorbe : ses tickets de chunkloading
 * passent au clan d'accueil (dans ses emplacements, le reste est libere) et les reseaux de ses chunks sont refaits ; clan vide : le
 * chunkloading s'arrete (le tresor est detruit) ; membre exclu : ses designations tombent, les Sceaux reviennent au clan ; tout changement
 * de membres ou de niveau renvoie les bordures.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class TerritoryClanEvents {

    private TerritoryClanEvents() {
    }

    @SubscribeEvent
    public static void onMerged(ClanEvent.SoloMerged e) {
        MinecraftServer server = e.server;
        Clan into = e.clan;
        TerritorySaved saved = TerritorySaved.get(server);
        //les demandes en attente du clan solo passent au clan d'accueil
        List<TerritorySaved.Queued> moved = new ArrayList<>();
        saved.queue.removeIf(q -> {
            if (q.clan().equals(e.from)) {
                moved.add(new TerritorySaved.Queued(into.id, q.dim(), q.chunk(), q.at()));
                return true;
            }
            return false;
        });
        saved.queue.addAll(moved);
        saved.changed();
        int slots = ClanChunkloading.slots(into);
        for (String key : new ArrayList<>(into.chunkload)) {
            ClanChunkloading.force(server, e.from, key, false);
        }
        while (into.chunkload.size() > slots) {
            String key = into.chunkload.remove(into.chunkload.size() - 1);
            ClanChunkloading.force(server, into.id, key, false);
        }
        if (!ClanChunkloading.suspended(into)) {
            for (String key : into.chunkload) {
                ClanChunkloading.force(server, into.id, key, true);
            }
        }
        BorderSplits.clanChanged(server, into);
        ClanSeals.rebuild(server);
        ClanChunkloading.serveQueue(server);
    }

    @SubscribeEvent
    public static void onEmptied(ClanEvent.Emptied e) {
        ClanChunkloading.stopAll(e.server, e.clan);
        TerritoryNet.bump();
    }

    @SubscribeEvent
    public static void onDissolved(ClanEvent.Dissolved e) {
        ClanChunkloading.stopAll(e.server, e.clan);
        ClanSeals.rebuild(e.server);
        TerritoryNet.bump();
    }

    @SubscribeEvent
    public static void onLeft(ClanEvent.MemberLeft e) {
        if ("kick".equals(e.reason)) {
            ClanSeals.dropBy(e.server, e.clan, e.player);
        }
        TerritoryNet.bump();
    }

    @SubscribeEvent
    public static void onJoined(ClanEvent.MemberJoined e) {
        TerritoryNet.bump();
    }
}
