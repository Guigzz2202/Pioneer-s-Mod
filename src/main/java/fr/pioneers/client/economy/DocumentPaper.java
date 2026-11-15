package fr.pioneers.client.economy;

import fr.pioneers.client.rp.ParchmentScreen;
import fr.pioneers.client.rp.RpGui;
import fr.pioneers.economy.document.DocStatus;
import fr.pioneers.economy.document.DocType;
import fr.pioneers.economy.document.Document;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 1.0.5-beta (lot 26, retour 14) : un DOCUMENT dessine comme une feuille de papier (apercu de l'onglet Documents du Carnet et lecture du
 * document imprime, {@link DocumentScreen}) : type en capitales, numero et jour, de / a, objet, corps, lignes de montants et total,
 * echeancier (jour, montant, reglee / en retard / a venir), signatures, tampon du statut. La feuille DEFILE si elle est plus haute que sa place
 * (barre du Carnet). Maquette : {@code tools/gc/rp/documents_preview.py} (memes constantes).
 */
public final class DocumentPaper {

    /** Marges et pas de la feuille (px de la page). */
    public static final int PAD = 8, ROW = 10, SMALL = 8, LABEL_W = 40, STAMP_W = 64, STAMP_H = 14, SIGN_H = 22, STATE_W = 70;
    public static final int PAPER = 0xFFFBF5E6, PAPER_EDGE = 0xFFC9B48C, PAPER_SHADOW = 0x30402A10;
    private static final int[] HEIGHTS = new int[16];

    private DocumentPaper() {
    }

    /** Jour de jeu lisible : « jour 132 (dans 5 j) ». */
    public static Component day(long day, long today) {
        if (day < 0) {
            return Component.translatable("document.pioneers.day_unknown");
        }
        if (today < 0) {
            return Component.translatable("document.pioneers.day", day); //document imprime lu hors du Carnet : jour seul
        }
        long dd = day - today;
        Component rel = dd == 0 ? Component.translatable("document.pioneers.today") : dd > 0 ? Component.translatable("document.pioneers.in_days", dd)
              : Component.translatable("document.pioneers.days_ago", -dd);
        return Component.translatable("document.pioneers.day_rel", day, rel);
    }

    public static Component typeName(String type) {
        return Component.translatable("document.pioneers.type." + type);
    }

    public static Component statusName(String status) {
        return Component.translatable("document.pioneers.status." + status);
    }

    /** Couleur d'un statut. */
    public static int statusColor(String status) {
        return switch (status) {
            case "sent" -> ParchmentScreen.GOLD;
            case "accepted" -> ParchmentScreen.INK;
            case "paid" -> ParchmentScreen.PLUS;
            case "overdue", "refused" -> ParchmentScreen.MINUS;
            default -> ParchmentScreen.DIM;
        };
    }

    /** Icone d'un type (tuile {@code stat/doc_<type>}). */
    public static net.minecraft.resources.ResourceLocation icon(String type) {
        return RpGui.stat("doc_" + type);
    }

    /**
     * Dessine la feuille dans ({@code x}, {@code y}, {@code w}, {@code h}) ; {@code slot} = barre de defilement du Carnet ; {@code today} :
     * jour de jeu (-1 : inconnu, document imprime lu hors du Carnet).
     */
    public static void draw(ParchmentScreen s, GuiGraphics g, int mx, int my, CompoundTag t, int x, int y, int w, int h, int slot, long today) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, PAPER_SHADOW);
        g.fill(x, y, x + w, y + h, PAPER);
        s.frame(g, x, y, w, h, PAPER_EDGE);
        int content = HEIGHTS[slot & 15];
        s.scrollArea(slot, x, y + 1, w, h - 2, content);
        s.beginClip(g, x + 1, y + 1, w - 2, h - 2);
        int top = y + PAD - s.scrollOf(slot);
        int end = body(s, g, mx, my, t, x + PAD, top, w - 2 * PAD - 4, today);
        s.endClip(g);
        HEIGHTS[slot & 15] = end - top + PAD;
    }

    /** Contenu de la feuille a partir de ({@code x}, {@code y}) sur {@code w} px ; rend le bas. */
    static int body(ParchmentScreen s, GuiGraphics g, int mx, int my, CompoundTag t, int x, int y, int w, long today) {
        Document d = Document.load(t);
        String type = d.type.key();
        //en-tete : TYPE, numero, jour ; tampon du statut
        s.icon(g, icon(type), x, y + 1, 12);
        s.fit(g, Component.literal(typeName(type).getString().toUpperCase(Locale.ROOT)), x + 16, y, w - STAMP_W - 20, ParchmentScreen.INK, 1.5F);
        int stampX = x + w - STAMP_W;
        int sc = statusColor(d.status.key());
        s.frame(g, stampX, y, STAMP_W, STAMP_H, sc);
        s.fitCentered(g, Component.literal(statusName(d.status.key()).getString().toUpperCase(Locale.ROOT)), stampX + STAMP_W / 2, y + 3, STAMP_W - 4,
              sc, 0.75F);
        y += 15;
        long when = d.sent >= 0 ? d.sent : d.created;
        Component ref = Component.translatable("document.pioneers.ref", d.id, d.timed > 0 ? Component.translatable("document.pioneers.realtime")
              : day(when, today));
        s.fit(g, ref, x, y, w, ParchmentScreen.DIM, 0.75F);
        y += SMALL + 2;
        s.separator(g, x, y, w);
        y += 4;
        y = row(s, g, Component.translatable("document.pioneers.label.from"), Component.literal(d.author.name()), x, y, w);
        Component to = d.recipient.clan() ? Component.translatable("document.pioneers.to_clan", d.recipient.name()) : Component.literal(d.recipient.name());
        y = row(s, g, Component.translatable("document.pioneers.label.to"), to, x, y, w);
        y = row(s, g, Component.translatable("document.pioneers.label.subject"), Component.literal(d.subject), x, y, w);
        y += 2;
        if (!d.body.isEmpty()) {
            y += s.wrap(g, Component.literal(d.body), x, y, w, ParchmentScreen.TEXT, 0.75F, 99) + 4;
        }
        //lignes de montants et total
        if (!d.lines.isEmpty() || d.type.amounts) {
            s.fit(g, Component.translatable("document.pioneers.label.amounts"), x, y, w, ParchmentScreen.INK_LIGHT, 0.75F);
            y += SMALL + 1;
            for (Document.Line l : d.lines) {
                int aw = s.fitRight(g, ContractsTab.tokens(l.amount()), x + w, y, 90, ParchmentScreen.TEXT, 0.75F);
                s.fit(g, Component.literal(l.label()), x + 4, y, w - aw - 10, ParchmentScreen.TEXT, 0.75F);
                y += SMALL + 1;
            }
            g.fill(x + w / 2, y, x + w, y + 1, 0x60402A10);
            y += 2;
            int tw = s.fitRight(g, ContractsTab.tokens(d.total()), x + w, y, 110, ParchmentScreen.INK, 1F);
            s.fitRight(g, Component.translatable("document.pioneers.label.total"), x + w - tw - 6, y + 1, 80, ParchmentScreen.DIM, 0.75F);
            y += ROW + 2;
        }
        //echeancier
        if (!d.dues.isEmpty()) {
            s.fit(g, Component.translatable("document.pioneers.label.schedule"), x, y, w, ParchmentScreen.INK_LIGHT, 0.75F);
            y += SMALL + 1;
            int i = 1;
            for (Document.Due due : d.dues) {
                boolean late = !due.paid() && today >= 0 && due.day < today;
                Component state = due.paid() ? Component.translatable("document.pioneers.due.paid", due.paidDay)
                      : late ? Component.translatable("document.pioneers.due.late") : Component.translatable("document.pioneers.due.coming");
                int col = due.paid() ? ParchmentScreen.PLUS : late ? ParchmentScreen.MINUS : ParchmentScreen.DIM;
                s.fit(g, Component.literal(i + ". "), x + 2, y, 14, ParchmentScreen.DIM, 0.75F);
                int dw = s.fit(g, d.legacy != null ? Component.translatable("document.pioneers.realtime") : day(due.day, today), x + 14, y, w / 2 - 14,
                      ParchmentScreen.TEXT, 0.75F);
                int sw = s.fitRight(g, state, x + w, y, STATE_W, col, 0.75F);
                s.fitRight(g, ContractsTab.tokens(due.amount), x + w - sw - 6, y, w - sw - 6 - 14 - dw - 6, ParchmentScreen.TEXT, 0.75F);
                y += SMALL + 1;
                i++;
            }
            y += 3;
        }
        //signatures (types a signer, ou signes a l'envoi)
        if (d.type.signed || d.type.authorPays() || !d.authorSign.isEmpty()) {
            int half = (w - 6) / 2;
            sign(s, g, x, y, half, Component.translatable("document.pioneers.sign.author"), d.authorSign, d.authorSignDay);
            if (d.type.signed) {
                sign(s, g, x + half + 6, y, half, Component.translatable("document.pioneers.sign.recipient"), d.recipientSign, d.recipientSignDay);
            }
            y += SIGN_H + 2;
        }
        if (d.legacy != null) {
            y += s.wrap(g, Component.translatable("document.pioneers.migrated_invoice"), x, y, w, ParchmentScreen.FAINT, 0.75F, 3) + 2;
        }
        if (t.contains("printed") && t.getLong("printed") >= 0) {
            s.fit(g, Component.translatable("document.pioneers.printed", t.getLong("printed")), x, y, w, ParchmentScreen.FAINT, 0.75F);
            y += SMALL;
        }
        return y;
    }

    private static int row(ParchmentScreen s, GuiGraphics g, Component label, Component value, int x, int y, int w) {
        s.fit(g, label, x, y + 1, LABEL_W - 2, ParchmentScreen.DIM, 0.75F);
        s.fit(g, value, x + LABEL_W, y, w - LABEL_W, ParchmentScreen.TEXT, 1F);
        return y + ROW + 1;
    }

    private static void sign(ParchmentScreen s, GuiGraphics g, int x, int y, int w, Component label, String who, long day) {
        g.fill(x, y + SIGN_H - 1, x + w, y + SIGN_H, 0x80402A10);
        s.fit(g, label, x, y, w, ParchmentScreen.DIM, 0.75F);
        if (who == null || who.isEmpty()) {
            s.fit(g, Component.translatable("document.pioneers.sign.waiting"), x + 2, y + 10, w - 4, ParchmentScreen.FAINT, 0.75F);
        } else {
            Component sig = Component.literal(who).withStyle(st -> st.withItalic(true));
            s.fit(g, day >= 0 ? Component.translatable("document.pioneers.sign.done", sig, day) : sig, x + 2, y + 10, w - 4, ParchmentScreen.INK_LIGHT,
                  1F);
        }
    }

    /** Texte court d'un statut pour le joueur (cote) : « A signer », « A payer »... ou le statut. */
    public static Component todo(CompoundTag sum) {
        String st = sum.getString("status");
        boolean recipient = "r".equals(sum.getString("side")), payer = sum.getBoolean("payer");
        if ("sent".equals(st) && recipient && DocType.of(sum.getString("type")).signed && sum.getInt("timed") == 0) {
            return Component.translatable("document.pioneers.todo.sign");
        }
        if (payer && ("overdue".equals(st) || sum.getLong("owed") > 0 && !DocStatus.of(st).closed() && !"draft".equals(st))) {
            return Component.translatable("document.pioneers.todo.pay");
        }
        return statusName(st);
    }
}
