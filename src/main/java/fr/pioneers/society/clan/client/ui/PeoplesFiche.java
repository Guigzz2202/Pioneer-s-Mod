package fr.pioneers.society.clan.client.ui;

import static fr.pioneers.society.clan.client.ui.ClanTab.H;
import static fr.pioneers.society.clan.client.ui.ClanTab.W;
import static fr.pioneers.society.clan.client.ui.ClanTab.X;
import static fr.pioneers.society.clan.client.ui.ClanTab.Y;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : annuaire des peuples (registre dynamique {@code PeopleApi}) : les trois peuples de villageois et les peuples
 * de joueurs nes d'une secession. Pour chacun : couleur, nom, doctrine (icone et nom), villages ; peuple de villageois, ou faction qui le
 * porte (etiquette, nombre de clans). 1.0.5-beta (lot 20) : « villageois / joueurs » et le nombre de villages en faits a icone, la faction en
 * infobulle.
 */
final class PeoplesFiche extends ClanTab.Fiche {

    static final int ROW = 36;

    @Override
    String id() {
        return "peoples";
    }

    @Override
    void enter() {
        ClanUiClient.request("peoples");
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("peoples");
        if (waiting(u, t)) {
            return;
        }
        ListTag l = t.getList("peoples", Tag.TAG_COMPOUND);
        u.fit(Component.translatable("clan.pioneers.ui.peoples.title", l.size()), X, Y, W, Ui.INK, 1F);
        u.separator(X, Y + 10, W);
        int top = Y + 14, h = Y + H - top;
        u.s.scrollArea(7, X, top, W, h, l.size() * ROW);
        u.s.beginClip(u.g, X, top, W, h);
        int y = top - u.s.scrollOf(7);
        for (int i = 0; i < l.size(); i++, y += ROW) {
            if (y + ROW <= top || y >= top + h) {
                continue;
            }
            CompoundTag p = l.getCompound(i);
            u.fill(X, y, X + W, y + ROW - 2, 0x0C243660);
            u.fill(X, y, X + 3, y + ROW - 2, 0xFF000000 | p.getInt("color"));
            String doctrine = p.getString("doctrine");
            u.image(Ui.gui("doctrine_" + doctrine), X + 6, y + 1, 32, 32, 32, 32);
            u.tip(X + 6, y + 1, 32, 32, Component.translatable("clan.pioneers.doctrine." + doctrine),
                  Component.translatable("clan.pioneers.doctrine." + doctrine + ".desc"));
            Component name;
            try {
                Component parsed = Component.Serializer.fromJson(p.getString("name"));
                name = parsed == null ? Component.translatable("people.pioneers." + p.getString("id")) : parsed;
            } catch (RuntimeException e) {
                name = Component.translatable("people.pioneers." + p.getString("id"));
            }
            u.fit(name, X + 44, y + 2, W - 50, Ui.INK, 1F);
            u.fit(Component.translatable("clan.pioneers.ui.peoples.doctrine", Component.translatable("clan.pioneers.doctrine." + doctrine)), X + 44,
                  y + 13, W - 50, Ui.TEXT, Ui.SMALL);
            //1.0.5-beta (lot 20) : qui le porte et combien de villages, en deux faits a icone ; la faction en infobulle
            boolean player = p.getBoolean("player");
            int fx = X + 44, fy = y + 21;
            u.icon12(Ui.gui(player ? "nav_members" : "nav_peoples"), fx, fy);
            int tw = u.fit(Component.translatable(player ? "clan.pioneers.ui.peoples.player_short" : "clan.pioneers.ui.peoples.villagers_short"), fx + Ui.GAP,
                  fy + 3, 100, Ui.DIM, Ui.SMALL);
            if (player && !p.getString("faction").isEmpty()) {
                String tag = p.getString("tag");
                u.tip(fx, fy, Ui.GAP + tw, 12, Component.translatable("clan.pioneers.ui.peoples.player"), Component.translatable(
                      "clan.pioneers.ui.peoples.faction", tag.isBlank() ? p.getString("faction") : "[" + tag + "] " + p.getString("faction"),
                      p.getInt("clans")));
            }
            int vx = fx + Ui.GAP + tw + 10;
            u.icon12(Ui.gui("nav_home"), vx, fy);
            u.fit(Component.translatable("clan.pioneers.ui.peoples.villages_short", p.getInt("villages")), vx + Ui.GAP, fy + 3, X + W - vx - Ui.GAP - 4,
                  Ui.DIM, Ui.SMALL);
        }
        u.s.endClip(u.g);
    }
}
