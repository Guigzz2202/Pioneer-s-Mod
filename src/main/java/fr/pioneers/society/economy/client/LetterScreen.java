package fr.pioneers.society.economy.client;

import fr.pioneers.society.rp.client.ParchmentScreen;
import fr.pioneers.society.economy.mail.Letters;
import java.time.DateTimeException;
import java.time.ZoneId;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 1.0.4-beta (lot 29) : lecture d'une LETTRE de la boite aux lettres ({@code economy/mail/LetterItem}, clic droit) : en-tete « Lettre »,
 * date d'envoi (au format de la langue, fuseau du serveur), expediteur, objet, texte, montant, contrat. Rien n'est demande au serveur.
 * Maquette : {@code tools/gc/economy/mail_ui_preview.py} (memes constantes).
 */
public class LetterScreen extends ParchmentScreen {

    public static final int MARGIN = 24, RIGHT = PAGE_W - 24, HEAD_Y = 18, SEP_Y = 44, FROM_Y = 54, SUBJECT_Y = 80, BODY_Y = 98, BODY_LINES = 6,
          AMOUNT_Y = 168, FOOT_Y = 218, SEAL_X = 330, SEAL_Y = 172;
    /** 1.0.5-beta (lot 16) : lettre de contrat (corps du prestataire) : haut et bas du corps (4 lignes pleines), montant descendu sous la
     * signature, defilement. */
    public static final int WRITTEN_Y = 118, WRITTEN_BOTTOM = 158, WRITTEN_AMOUNT_Y = 176, SCROLL = 0;
    private final CompoundTag t;
    @Nullable
    private final net.minecraft.client.gui.screens.Screen parent;

    public LetterScreen(CompoundTag t) {
        this(t, null);
    }

    /** 1.0.5-beta (lot 16) : lue depuis le bac a lettres : Echap y ramene. */
    public LetterScreen(CompoundTag t, @Nullable net.minecraft.client.gui.screens.Screen parent) {
        super(Component.translatable("item.pioneers.letter"));
        this.t = t;
        this.parent = parent;
    }

    public static void open(CompoundTag t) {
        Minecraft.getInstance().setScreen(new LetterScreen(t));
    }

    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(t.getString("z"));
        } catch (DateTimeException e) {
            return ZoneId.systemDefault();
        }
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        String kind = t.getString("k");
        boolean known = Letters.known(kind);
        fit(g, Component.translatable("mail.pioneers.letter.title"), MARGIN, HEAD_Y, 160, INK, 2F);
        fitRight(g, Component.literal(ContractsTab.date(t.getLong("d"), zone(), false)), RIGHT, HEAD_Y + 4, 180, DIM, 1F);
        separator(g, MARGIN, SEP_Y, RIGHT - MARGIN);
        if (!t.getString("f").isEmpty()) {
            fit(g, Component.translatable("mail.pioneers.label.from"), MARGIN, FROM_Y, 70, DIM, 0.75F);
            fit(g, Component.literal(t.getString("f")), MARGIN + 72, FROM_Y, RIGHT - MARGIN - 72, TEXT, 1F);
        }
        //1.0.5-beta (lot 26) : lettre d'un document du Carnet (type, numero, objet)
        boolean document = kind.startsWith("document_");
        if (t.getInt("n") > 0) {
            fit(g, Component.translatable(document ? "mail.pioneers.label.document" : "mail.pioneers.label.contract"), MARGIN, FROM_Y + 11, 70, DIM, 0.75F);
            fit(g, document ? Component.translatable("mail.pioneers.document_ref", Letters.docType(t), t.getInt("n"), t.getString("t"))
                  : Component.translatable("mail.pioneers.contract_ref", t.getInt("n"), t.getString("t")), MARGIN + 72, FROM_Y + 11,
                  RIGHT - MARGIN - 72, TEXT, 1F);
        }
        fit(g, known ? Component.translatable("mail.pioneers.kind." + kind) : Component.translatable("item.pioneers.letter"), MARGIN, SUBJECT_Y,
              RIGHT - MARGIN, INK, 1.5F);
        String written = t.getString("w");
        if (known && !written.isEmpty()) {
            //1.0.5-beta (lot 16) : la lettre du prestataire : annonce en une ligne, periode, corps qui defile, signature
            fit(g, Component.translatable(document ? "mail.pioneers.letter.answer_document" : "mail.pioneers.letter.answer"), MARGIN, BODY_Y,
                  RIGHT - MARGIN, DIM, 0.75F);
            tooltip(pageMx, pageMy, MARGIN, BODY_Y, RIGHT - MARGIN, 8, Component.translatable("mail.pioneers.body." + kind, Letters.bodyArgs(t)));
            if (!document) {
                fit(g, Component.translatable("mail.pioneers.letter.period", ContractsTab.date(t.getLong("s"), zone(), false),
                      ContractsTab.date(t.getLong("e"), zone(), false)), MARGIN, BODY_Y + 8, RIGHT - MARGIN, DIM, 0.75F);
            } else {
                fit(g, Component.literal(t.getString("t")), MARGIN, BODY_Y + 8, RIGHT - MARGIN, INK, 1F);
            }
            Component text = Component.literal(written);
            int h = WRITTEN_BOTTOM - WRITTEN_Y, content = wrapHeight(text, RIGHT - MARGIN - 6, 1F);
            scrollArea(SCROLL, MARGIN, WRITTEN_Y, RIGHT - MARGIN, h, content);
            beginClip(g, MARGIN, WRITTEN_Y, RIGHT - MARGIN, h);
            wrap(g, text, MARGIN, WRITTEN_Y - scroll[SCROLL], RIGHT - MARGIN - 6, TEXT, 1F, 99);
            endClip(g);
            if (content > h) {
                fitRight(g, Component.literal(scroll[SCROLL] + h >= content ? "▲" : "▼"), RIGHT, WRITTEN_BOTTOM - 8, 8, FAINT, 0.75F);
            }
            fitRight(g, Component.translatable("mail.pioneers.letter.signed", t.getString("sg")).withStyle(st -> st.withItalic(true)), RIGHT - 60,
                  WRITTEN_BOTTOM + 1, 200, INK_LIGHT, 0.75F);
        } else if (known) {
            wrap(g, Component.translatable("mail.pioneers.body." + kind, Letters.bodyArgs(t)), MARGIN, BODY_Y, RIGHT - MARGIN, TEXT, 1F,
                  BODY_LINES);
        }
        long a = t.getLong("a");
        int ay = known && !written.isEmpty() ? WRITTEN_AMOUNT_Y : AMOUNT_Y;
        if (a > 0) {
            separator(g, MARGIN, ay - 6, 200);
            fit(g, Component.translatable("mail.pioneers.label.amount"), MARGIN, ay, 90, DIM, 0.75F);
            fit(g, "contract_proposed".equals(kind) ? Letters.terms(t.getString("m"), a) : ContractsTab.tokens(a), MARGIN, ay + 10, 200, INK,
                  1.5F);
        }
        //cachet de cire (le sens est dans les mots : simple ornement)
        frame(g, SEAL_X - 14, SEAL_Y - 14, 28, 28, MINUS);
        fitCentered(g, Component.literal("P"), SEAL_X, SEAL_Y - 6, 20, MINUS, 1.5F);
        fit(g, Component.translatable("mail.pioneers.letter.foot", t.getString("z")), MARGIN, FOOT_Y, RIGHT - MARGIN, DIM, 0.75F);
    }
}
