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
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Journal : les 200 dernieres lignes du journal du clan, par pages de 50, les plus recentes d'abord (les
 * mouvements du tresor seulement avec la permission de le voir). Chaque ligne est traduite chez le joueur. 1.0.5-beta (lot 20) : icone du
 * titre, lignes repliees au lieu d'etre coupees ({@link TreasuryFiche#journal}).
 */
final class JournalFiche extends ClanTab.Fiche {

    private static int page;

    @Override
    String id() {
        return "journal";
    }

    @Override
    void enter() {
        page = 0;
        ask();
    }

    private static void ask() {
        CompoundTag t = new CompoundTag();
        t.putInt("page", page);
        ClanUiClient.request("journal", t);
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("journal");
        if (waiting(u, t)) {
            return;
        }
        int pages = Math.max(1, t.getInt("pages"));
        int pg = t.getInt("page");
        u.icon12(Ui.gui("nav_journal"), X, Y - 2);
        u.fit(Component.translatable("clan.pioneers.ui.journal.title"), X + Ui.GAP, Y, W - 90 - Ui.GAP, Ui.INK, 1F);
        u.button(X + W - 86, Y - 1, 14, 12, Component.literal("<"), pg > 0, () -> {
            page = pg - 1;
            ask();
        });
        u.center(Component.translatable("clan.pioneers.ui.page", pg + 1, pages), X + W - 43, Y + 1, 54, Ui.TEXT, Ui.SMALL);
        u.button(X + W - 14, Y - 1, 14, 12, Component.literal(">"), pg + 1 < pages, () -> {
            page = pg + 1;
            ask();
        });
        u.separator(X, Y + 12, W);
        ListTag lines = t.getList("lines", Tag.TAG_COMPOUND);
        if (lines.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.journal.empty"), X, Y + 16, W, Ui.DIM, Ui.SMALL);
            return;
        }
        TreasuryFiche.journal(u, lines, X, Y + 16, W, H - 16, 5);
    }
}
