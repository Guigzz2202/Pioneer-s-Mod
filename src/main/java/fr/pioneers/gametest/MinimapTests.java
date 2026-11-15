package fr.pioneers.gametest;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ui.ClanMapNet;
import fr.pioneers.society.clan.ui.ClanMates;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.10.3 : seule partie serveur de la minimap : la position des membres de son clan ({@code ClanMates}, paquet {@code CLAN_MATES}). Un
 * membre voit les autres membres EN LIGNE de son clan dans la meme dimension, jamais un joueur d'un autre clan, jamais lui-meme ; un
 * Solitaire ne voit personne ; le drapeau « mates » de l'abonnement a la carte est garde quand la fiche Territoire se reabonne.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MinimapTests {

    private static final BlockPos AT = new BlockPos(2, 2, 2);

    private MinimapTests() {
    }

    private static String sfx(GameTestHelper h) {
        return Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_minimap_membres", timeoutTicks = 40)
    public static void minimap_membres_du_clan_seulement(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer a = s.player("gtmma", "Albane", AT);
        ServerPlayer b = s.player("gtmmb", "Basile", new BlockPos(6, 2, 6));
        ServerPlayer c = s.player("gtmmc", "Corentin", AT);
        ServerPlayer d = s.player("gtmmd", "Delphine", AT);
        Clan mine = s.clan("GtCarte" + sfx(h), a, b);
        s.clan("GtAutre" + sfx(h), c);
        List<ServerPlayer> seen = ClanMates.of(s.server, a);
        Gt.check(seen.contains(b), "le membre du clan n'est pas montre");
        Gt.check(!seen.contains(a), "le joueur se voit lui-meme");
        Gt.check(!seen.contains(c), "un joueur d'un autre clan est montre");
        Gt.check(ClanMates.of(s.server, c).isEmpty(), "clan d'un seul membre : personne a montrer");
        Gt.check(ClanMates.of(s.server, d).isEmpty(), "un Solitaire voit des joueurs");
        CompoundTag t = ClanMates.tag(s.server, a);
        ListTag list = t.getList("mates", Tag.TAG_COMPOUND);
        Gt.equal(1, list.size(), "membres dans le paquet");
        Gt.equal(b.getGameProfile().getName(), list.getCompound(0).getString("name"), "pseudo du membre");
        Gt.equal(b.getBlockX(), list.getCompound(0).getInt("x"), "position du membre");
        Gt.equal(mine.color & 15, t.getInt("dye"), "teinture du clan");
        Gt.equal(s.level.dimension().location().toString(), t.getString("dim"), "dimension");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_minimap_abonnement", timeoutTicks = 40)
    public static void minimap_abonnement_garde_les_membres(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer a = s.player("gtmab", "Armand", AT);
        s.clan("GtAbo" + sfx(h), a);
        CompoundTag open = new CompoundTag();
        open.putString("op", "open");
        open.putBoolean("mates", true);
        RpNet.dispatch(a, RpActions.CLAN_MAP_REQUEST, open);
        Gt.check(ClanMapNet.watchesMates(a.getUUID()), "la minimap n'est pas abonnee aux membres");
        //la fiche Territoire se reabonne (« fresh », sans drapeau) : le drapeau de la minimap reste
        CompoundTag fiche = new CompoundTag();
        fiche.putString("op", "open");
        fiche.putBoolean("fresh", true);
        h.runAfterDelay(6, () -> {
            RpNet.dispatch(a, RpActions.CLAN_MAP_REQUEST, fiche);
            Gt.check(ClanMapNet.watchesMates(a.getUUID()), "la fiche Territoire a coupe les membres de la minimap");
            CompoundTag close = new CompoundTag();
            close.putString("op", "close");
            h.runAfterDelay(6, () -> {
                RpNet.dispatch(a, RpActions.CLAN_MAP_REQUEST, close);
                Gt.check(!ClanMapNet.watchesMates(a.getUUID()), "abonnement ferme mais membres encore envoyes");
                s.done();
            });
        });
    }
}
