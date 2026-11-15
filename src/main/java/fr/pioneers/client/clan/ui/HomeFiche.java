package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanTab.H;
import static fr.pioneers.client.clan.ui.ClanTab.W;
import static fr.pioneers.client.clan.ui.ClanTab.X;
import static fr.pioneers.client.clan.ui.ClanTab.Y;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : accueil du joueur sans clan : fonder par la Charte, tenir seul (le premier claim cree le clan solo),
 * invitations recues (accepter, refuser), et les annuaires. 1.0.5-beta (lot 20, retour 10 : « plus lisible, moins verbeux, icones
 * explicites ») : trois blocs a icone (titre + une ligne courte, l'explication entiere en infobulle), invitations en rangees (clan, qui
 * invite ; l'echeance en infobulle), annuaires en boutons a icone.
 */
final class HomeFiche extends ClanTab.Fiche {

    static final int BLOCK = 26, INVITES_Y = Y + 92, ROW = 22;

    @Override
    String id() {
        return "home";
    }

    @Override
    void enter() {
        ClanUiClient.request("sheet");
    }

    /** Bloc : icone 16 px (embleme d'appartenance), titre, une ligne courte ; tout le texte en infobulle. */
    private static void block(Ui u, String icon, String title, String line, String detail, int y, int w) {
        u.image(Ui.gui(icon), X, y + 1, 16, 16, 32, 32);
        u.fit(Component.translatable(title), X + 20, y, w - 20, Ui.INK, 1F);
        u.fit(Component.translatable(line), X + 20, y + 11, w - 20, Ui.TEXT, Ui.SMALL);
        u.tip(X, y, w, 18, Component.translatable(title), Component.translatable(detail));
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("sheet");
        title(u, Component.translatable("clan.pioneers.ui.home.title"), Y);
        int y = Y + 16;
        block(u, "member_clan", "clan.pioneers.ui.home.found_short", "clan.pioneers.ui.home.found_line", "clan.pioneers.ui.home.found.text", y, W);
        y += BLOCK;
        block(u, "member_solo", "clan.pioneers.ui.home.solo_short", "clan.pioneers.ui.home.solo_line", "clan.pioneers.ui.home.solo.text", y, W - 100);
        Minecraft mc = Minecraft.getInstance();
        u.iconButton(X + W - 96, y + 2, 96, 14, Ui.gui("perm_claim"), Component.translatable("clan.pioneers.ui.home.claim_here"), mc.player != null, () -> {
            if (mc.player != null) {
                ClanUiClient.mapAct("claim", mc.player.chunkPosition().x, mc.player.chunkPosition().z, null);
                ClanUiClient.request("sheet");
            }
        });
        y += BLOCK + 6;
        //invitations
        ListTag inv = t == null ? new ListTag() : t.getList("invites", Tag.TAG_COMPOUND);
        u.section(Ui.gui("ico_invite"), Component.translatable("clan.pioneers.ui.home.invites_short"), X, y, W);
        if (!inv.isEmpty()) {
            u.right(Component.literal(Integer.toString(inv.size())), X + W, y + 3, 30, Ui.INK, Ui.SMALL);
        }
        long now = ClanUiClient.now();
        if (inv.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.home.no_invite"), X, INVITES_Y, W, Ui.DIM, Ui.SMALL);
        }
        int top = INVITES_Y, h = (Y + H - 20 - top) / ROW * ROW;
        u.s.scrollArea(2, X, top, W, h, inv.size() * ROW);
        u.s.beginClip(u.g, X, top, W, h);
        int ry = top - u.s.scrollOf(2);
        for (int i = 0; i < inv.size(); i++, ry += ROW) {
            if (ry + ROW <= top || ry >= top + h) {
                continue;
            }
            CompoundTag e = inv.getCompound(i);
            String name = e.getString("clan");
            u.fill(X, ry, X + W, ry + ROW - 2, 0x0C243660);
            int x = X + 2;
            if (!e.getString("tag").isBlank()) {
                Component tag = Component.literal("[" + e.getString("tag") + "] ");
                u.text(tag, x, ry + 2, Ui.dyeInk(e.getInt("color")));
                x += u.width(tag, 1F);
            }
            u.fit(Component.literal(name), x, ry + 2, X + W - 134 - x, Ui.INK, 1F);
            u.fit(Component.translatable("clan.pioneers.ui.home.from", e.getString("by")), X + 2, ry + 12, W - 136, Ui.DIM, Ui.SMALL);
            u.tip(X, ry, W - 132, ROW - 2, Component.literal(name), Component.translatable("clan.pioneers.ui.home.expires", Ui.duration(e.getLong("expires")
                                                                                                                                      - now)));
            CompoundTag a = new CompoundTag();
            a.putString("clan", name);
            if (ry >= top && ry + ROW <= top + h) {
                u.button(X + W - 128, ry + 3, 62, 14, Component.translatable("clan.pioneers.ui.accept"), true, () -> ClanUiClient.act("accept", "sheet", a));
                u.button(X + W - 64, ry + 3, 62, 14, Component.translatable("clan.pioneers.ui.decline"), true,
                      () -> ClanUiClient.act("decline", "sheet", new CompoundTag()));
            }
        }
        u.s.endClip(u.g);
        //annuaires
        int by = Y + H - 14;
        u.iconButton(X, by, 138, 14, Ui.gui("nav_clans"), Component.translatable("clan.pioneers.ui.nav.clans"), true, () -> ClanTab.select("clans"));
        u.iconButton(X + W - 138, by, 138, 14, Ui.gui("nav_peoples"), Component.translatable("clan.pioneers.ui.nav.peoples"), true,
              () -> ClanTab.select("peoples"));
    }
}
