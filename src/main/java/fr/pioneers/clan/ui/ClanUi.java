package fr.pioneers.clan.ui;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.core.ClanViews;
import fr.pioneers.clan.seal.ClanSeals;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : cote serveur de l'onglet Clan du Carnet. Branche le crochet de la Mairie (clic droit : le Carnet s'ouvre sur
 * l'onglet Clan), la carte strategique ({@code CLAN_MAP_REQUEST} -&gt; {@code CLAN_MAP}, envoi initial puis deltas) et le releve des
 * regions explorees. Les fiches hors fondations sont servies par {@link ClanUiViews}, les intentions par {@link ClanUiActions}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanUi {

    private static int ticks;

    private ClanUi() {
    }

    /** Appele par {@code ClanBootstrap.init}. */
    public static void init() {
        RpNet.onServer(RpActions.CLAN_MAP_REQUEST, ClanMapNet::request);
        //Mairie : la fiche du clan, avec l'ordre d'ouvrir le Carnet sur l'onglet Clan
        ClanSeals.clanScreen = p -> {
            CompoundTag t = ClanViews.sheet(p, "sheet");
            t.putBoolean("open", true);
            RpNet.send(p, RpActions.CLAN_VIEW, t);
        };
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ClanApi.enabled()) {
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            ClanMapNet.tick(event.getServer());
        }
        if (ticks % 40 == 0) {
            ClanExplored.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClanMapNet.forget(event.getEntity().getUUID());
        ClanExplored.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ClanMapNet.clear();
        ClanExplored.clear();
        ticks = 0;
    }
}
