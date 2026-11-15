package fr.pioneers.society.clan.client.faction;

import fr.pioneers.society.clan.client.ClanPaper;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.society.rp.client.ParchmentScreen;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * 1.9 (MAJ RP, bloc 4) : ecran du Pacte federal, sur la page du Carnet (papier, encre bleue), comme la Charte. Vierge : le Representant
 * choisit le nom, l'etiquette (3 a 5 lettres), la couleur (16 teintures), la devise et le regime federal (quatre fiches : accroche et plafond
 * de contribution), puis « Ouvrir le Pacte ». Ouvert : clans signataires, temps restant ; le fondateur le scelle des qu'un clan a signe, un
 * autre Representant le signe au nom de son clan (vote selon son regime). Le client n'envoie que l'intention ; le serveur verifie tout.
 * <p>
 * 1.9.1 (retours de Guillaume : textes qui se superposaient) : disposition calculee avec la vraie police. Le preambule est replie au-dessus
 * du filet (le filet descend s'il le faut), chaque libelle de champ a sa ligne, la palette sa rangee sous « Couleur », les descriptions des
 * regimes sont repliees dans leur fiche (hauteur et taille du texte adaptees), les messages du bas ont chacun leur ligne. Tout texte passe
 * par {@link TextFit} : rien ne sort de sa case, en francais comme en anglais. Maquettes : {@code tools/gc/rp/screens_ui_preview.py}.
 */
public class PactScreen extends ParchmentScreen {

    private record RegimeCard(String id, int contribution) {
    }

    /** Colonnes : gauche (champs) 20..186, droite (regimes) 200..364 ; rangee des boutons ; bas utile de la page. */
    static final int X = 20, CW = PAGE_W - 40, LW = 166, RX = 200, RW = 164, BTN_Y = 212, BTN_W = 124, BOTTOM = 232, GAP = 3;
    /** Echelles : preambule, libelles ; tailles essayees pour les descriptions des regimes (la plus grande qui tient). */
    static final float HEAD = 0.8F, LABEL = 0.8F, NAME = 0.85F;
    static final float[] CARD_SCALES = {0.75F, 0.7F, 0.6F};

    private CompoundTag view;
    private long received;
    private final List<RegimeCard> regimes = new ArrayList<>();
    private EditBox name, tag, motto;
    private int color = 11;
    private String regime = "council";
    private String error = "";
    /** Filet sous le preambule et haut des colonnes (calcules d'apres la police dans {@link #init()}). */
    private int sepY = 40, y0 = 45;

    public PactScreen(CompoundTag view) {
        super(Component.translatable("clan.pioneers.pact.title"));
        read(view);
    }

    /** Nouvel etat envoye par le serveur. */
    public void read(CompoundTag t) {
        if (t.contains("error")) {
            error = t.getString("error");
            return;
        }
        view = t;
        received = System.currentTimeMillis();
        error = "";
        regimes.clear();
        for (Tag x : t.getList("regimes", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) x;
            regimes.add(new RegimeCard(r.getString("id"), r.getInt("contribution")));
        }
        if (!regimes.isEmpty() && regimes.stream().noneMatch(r -> r.id.equals(regime))) {
            regime = regimes.get(0).id;
        }
        if (name != null) {
            boxes();
        }
    }

    private boolean open() {
        return view.contains("draft");
    }

    private static Component header() {
        return Component.translatable("clan.pioneers.pact.text").withStyle(s -> s.withItalic(true));
    }

    /** 1.9.1 : ligne de chaque libelle de champ (le champ est 10 px plus bas). */
    private int nameY() {
        return y0;
    }

    private int tagY() {
        return y0 + 29;
    }

    private int colorY() {
        return y0 + 58;
    }

    private int mottoY() {
        return y0 + 84;
    }

    @Override
    protected void init() {
        super.init();
        //1.9.1 (retours de Guillaume) : le filet descend sous le preambule replie
        sepY = 22 + TextFit.wrapHeight(font, header(), CW, HEAD, 3) + 1;
        y0 = sepY + 5;
        String n = name == null ? "" : name.getValue(), tg = tag == null ? "" : tag.getValue(), m = motto == null ? "" : motto.getValue();
        name = new EditBox(font, left + X + 1, top + nameY() + 10, LW - 2, 14, Component.translatable("clan.pioneers.charter.name", 0, 0));
        name.setMaxLength(Math.max(1, view.getInt("nameMax")));
        name.setValue(n);
        tag = new EditBox(font, left + X + 1, top + tagY() + 10, 50, 14, Component.translatable("clan.pioneers.pact.tag", 0, 0));
        tag.setMaxLength(Math.max(1, view.getInt("tagMax")));
        tag.setFilter(s -> s.codePoints().allMatch(Character::isLetter));
        tag.setValue(tg);
        motto = new EditBox(font, left + X + 1, top + mottoY() + 10, LW - 2, 14, Component.translatable("clan.pioneers.charter.motto", 0));
        motto.setMaxLength(Math.max(1, view.getInt("mottoMax")));
        motto.setValue(m);
        addRenderableWidget(name);
        addRenderableWidget(tag);
        addRenderableWidget(motto);
        boxes();
    }

    private void boxes() {
        boolean edit = !open();
        name.visible = edit;
        tag.visible = edit;
        motto.visible = edit;
    }

    private void send(String op) {
        CompoundTag t = new CompoundTag();
        t.putString("op", op);
        t.putInt("hand", view.getInt("hand"));
        if ("pact_open".equals(op)) {
            t.putString("name", name.getValue());
            t.putString("tag", tag.getValue());
            t.putInt("color", color);
            t.putString("motto", motto.getValue());
            t.putString("regime", regime);
        }
        RpNet.toServer(RpActions.FACTION_ACTION, t);
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        TextFit.Tips tips = ClanPaper.tips(this, mx, my);
        TextFit.center(g, font, Component.translatable("clan.pioneers.pact.title"), X, 10, CW, INK, 1F, tips);
        TextFit.wrap(g, font, header(), X, 22, CW, DIM, HEAD, 3, tips, true);
        separator(g, X, sepY, CW);
        if (open()) {
            drawOpen(g, mx, my, tips);
        } else {
            drawDraft(g, mx, my, tips);
        }
    }

    // ------------------------------------------------------------------ redaction
    private void drawDraft(GuiGraphics g, int mx, int my, TextFit.Tips tips) {
        //colonne de gauche : chaque libelle sur sa ligne, le champ dessous (1.9.1)
        TextFit.fit(g, font, Component.translatable("clan.pioneers.charter.name", view.getInt("nameMin"), view.getInt("nameMax")), X, nameY(), LW,
              TEXT, LABEL, tips);
        TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.tag", view.getInt("tagMin"), view.getInt("tagMax")), X, tagY(), LW, TEXT,
              LABEL, tips);
        TextFit.fit(g, font, Component.translatable("clan.pioneers.charter.color"), X, colorY(), LW, TEXT, LABEL, tips);
        ClanPaper.palette(this, g, mx, my, X, colorY() + 10, color, i -> color = i);
        TextFit.fit(g, font, Component.translatable("clan.pioneers.charter.motto", view.getInt("mottoMax")), X, mottoY(), LW, TEXT, LABEL, tips);
        //cout, regles et erreur sous les champs, chacun sur ses lignes
        long cost = view.getLong("cost");
        Component costLine = cost > 0 ? Component.translatable("clan.pioneers.pact.cost", cost, view.getLong("treasury"))
              : Component.translatable("clan.pioneers.pact.free");
        int by = mottoY() + 30;
        by += TextFit.wrap(g, font, costLine, X, by, LW, cost > view.getLong("treasury") ? MINUS : TEXT, 0.75F, 2, tips) + 2;
        by += TextFit.wrap(g, font, Component.translatable("clan.pioneers.pact.rules", view.getInt("minLevel"), view.getInt("maxClans"),
              view.getLong("expireDays")), X, by, LW, DIM, 0.7F, 3, tips) + 2;
        if (!error.isEmpty()) {
            TextFit.wrap(g, font, Component.translatable(error), X, by, LW, MINUS, 0.75F, Math.max(1, (BOTTOM - by) / 8), tips);
        }
        //colonne de droite : regimes federaux
        TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.regime"), RX, y0, RW, TEXT, LABEL, tips);
        List<Component> descs = new ArrayList<>();
        for (RegimeCard r : regimes) {
            descs.add(Component.translatable("clan.pioneers.pact.regime." + r.id));
        }
        int top = y0 + 10;
        float sc = cardScale(descs, BTN_Y - 4 - top);
        int max = fits(descs, sc, Integer.MAX_VALUE, BTN_Y - 4 - top) ? Integer.MAX_VALUE : 2;
        int y = top;
        for (int i = 0; i < regimes.size(); i++) {
            RegimeCard r = regimes.get(i);
            int h = cardHeight(descs.get(i), sc, max);
            boolean sel = r.id.equals(regime);
            g.fill(RX, y, RX + RW, y + h, sel ? 0x40243660 : over(mx, my, RX, y, RW, h) ? 0x22243660 : 0x10243660);
            frame(g, RX, y, RW, h, sel ? INK : FAINT);
            TextFit.fit(g, font, Component.translatable("clan.pioneers.regime." + r.id), RX + 4, y + 3, RW - 8, sel ? INK : TEXT, NAME, tips);
            int dh = TextFit.wrap(g, font, descs.get(i), RX + 4, y + 12, RW - 8, DIM, sc, max, tips);
            TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.contribution", r.contribution), RX + 4, y + 13 + dh, RW - 8, INK_LIGHT,
                  sc, tips);
            String id = r.id;
            click(RX, y, RW, h, () -> regime = id);
            y += h + GAP;
        }
        //bouton, ou la raison du refus a sa place
        if (view.contains("blocked")) {
            TextFit.wrap(g, font, Component.translatable(view.getString("blocked")), RX, BTN_Y, RW, MINUS, 0.75F, 2, tips);
        } else {
            ClanPaper.button(this, font, g, mx, my, RX + RW - BTN_W, BTN_Y, BTN_W, 16, Component.translatable("clan.pioneers.pact.open"), true,
                  () -> send("pact_open"));
        }
    }

    /** Hauteur d'une fiche de regime : nom, description repliee, ligne de contribution. */
    private int cardHeight(Component desc, float sc, int max) {
        return 13 + TextFit.wrapHeight(font, desc, RW - 8, sc, max) + TextFit.step(sc) + 2;
    }

    private boolean fits(List<Component> descs, float sc, int max, int room) {
        int h = -GAP;
        for (Component d : descs) {
            h += cardHeight(d, sc, max) + GAP;
        }
        return h <= room;
    }

    /** Plus grande taille de texte a laquelle les quatre fiches tiennent entieres dans {@code room} ; la plus petite sinon. */
    private float cardScale(List<Component> descs, int room) {
        for (float sc : CARD_SCALES) {
            if (fits(descs, sc, Integer.MAX_VALUE, room)) {
                return sc;
            }
        }
        return CARD_SCALES[CARD_SCALES.length - 1];
    }

    // ------------------------------------------------------------------ signatures
    private void drawOpen(GuiGraphics g, int mx, int my, TextFit.Tips tips) {
        CompoundTag d = view.getCompound("draft");
        //gauche : le futur nom, son regime, sa devise, le fondateur
        int y = y0;
        int rgb = DyeColor.byId(d.getInt("color") & 15).getFireworkColor();
        g.fill(X, y + 1, X + 12, y + 13, 0xFF000000 | rgb);
        frame(g, X - 1, y, 14, 14, INK);
        TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.tooltip_name", d.getString("name"), d.getString("tag")), X + 18, y + 3,
              LW - 18, INK, 1F, tips);
        y += 20;
        TextFit.fit(g, font, Component.translatable("clan.pioneers.regime." + d.getString("regime")), X, y, LW, TEXT, LABEL, tips);
        y += 11;
        if (!d.getString("motto").isEmpty()) {
            y += TextFit.wrap(g, font, Component.translatable("clan.pioneers.quoted", d.getString("motto")).withStyle(s -> s.withItalic(true)), X, y,
                  LW, DIM, 0.75F, 3, tips) + 3;
        }
        TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.tooltip_founder", d.getString("founderName")), X, y, LW, TEXT, LABEL, tips);
        int leftEnd = y + 10;
        //droite : signatures, temps restant, clans signataires
        ListTag sigs = d.getList("signerNames", Tag.TAG_COMPOUND);
        int ry = y0;
        TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.waiting", sigs.size()), RX, ry + 3, RW, INK, 1F, tips);
        ry += 15;
        long left = Math.max(0, view.getLong("left") - (System.currentTimeMillis() - received));
        TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.left", left / 86_400_000L, (left / 3_600_000L) % 24), RX, ry, RW, DIM, 0.75F,
              tips);
        ry += 12;
        TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.signers"), RX, ry, RW, TEXT, LABEL, tips);
        ry += 10;
        if (sigs.isEmpty()) {
            TextFit.fit(g, font, Component.translatable("clan.pioneers.pact.nobody"), RX + 6, ry, RW - 6, DIM, 0.75F, tips);
            ry += 10;
        }
        for (int i = 0; i < Math.min(7, sigs.size()); i++) {
            TextFit.fit(g, font, Component.literal("✒ " + sigs.getCompound(i).getString("n")), RX + 6, ry, RW - 6, INK, LABEL, tips);
            ry += 10;
        }
        //bas : le mode d'emploi, puis l'erreur ou le refus, puis les boutons
        Component msg = !error.isEmpty() ? Component.translatable(error)
              : !view.getBoolean("founder") && view.contains("blocked") ? Component.translatable(view.getString("blocked")) : null;
        int msgH = msg == null ? 0 : TextFit.wrapHeight(font, msg, CW, 0.75F, 2);
        int msgY = BTN_Y - 3 - msgH;
        int howY = Math.max(leftEnd, ry) + 4;
        int lines = Math.max(1, (msgY - 2 - howY) / TextFit.step(0.75F));
        TextFit.wrap(g, font, Component.translatable("clan.pioneers.pact.how"), X, howY, CW, DIM, 0.75F, lines, tips);
        if (msg != null) {
            TextFit.wrap(g, font, msg, X, msgY, CW, MINUS, 0.75F, 2, tips);
        }
        if (view.getBoolean("founder")) {
            ClanPaper.button(this, font, g, mx, my, RX + RW - 2 * BTN_W - 10, BTN_Y, BTN_W, 16, Component.translatable("clan.pioneers.pact.seal"),
                  view.getBoolean("canSeal"), () -> send("pact_seal"));
            ClanPaper.button(this, font, g, mx, my, RX + RW - BTN_W, BTN_Y, BTN_W, 16, Component.translatable("clan.pioneers.pact.cancel"), true,
                  () -> send("pact_cancel"));
        } else if (!view.contains("blocked")) {
            ClanPaper.button(this, font, g, mx, my, RX + RW - BTN_W, BTN_Y, BTN_W, 16, Component.translatable("clan.pioneers.pact.sign"), true,
                  () -> send("pact_sign"));
        }
    }
}
