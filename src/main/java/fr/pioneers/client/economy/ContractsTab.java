package fr.pioneers.client.economy;

import fr.pioneers.client.rp.NotebookScreen;
import fr.pioneers.client.rp.ParchmentScreen;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.0.5-beta (lot 26) : n'est plus un onglet : c'est la vue des CONTRATS AU TEMPS REEL emboitee dans l'onglet Documents ({@link DocumentsTab} :
 * detail d'un contrat au temps reel, formulaire « Contrat au temps reel »), inchangee ; ses retours a la liste ramenent a celle des documents.
 * <p>
 * 1.0.4-beta (lot 7) : onglet Contrats du Carnet (touche J), juste apres la Bourse : contrats entre joueurs au taux horaire, en temps reel du
 * serveur. Trois vues : la LISTE (filtres « A accepter », « En cours », « Termines », « Brouillons » ; bouton « + Nouveau »), le FORMULAIRE
 * (client, intitule, description, debut et fin jour / mois / annee heure : minute avec « Maintenant », « +1 h », « +1 jour », taux horaire ;
 * duree et montant prevu calcules ; « Enregistrer le brouillon », « Proposer au client ») et le DETAIL (termes, facture : facture, paye,
 * reste du, a ce jour ; historique ; actions selon le role et l'etat ; « Imprimer la facture »).
 * <p>
 * Le serveur fait tout ({@code economy/contract/ContractService}) : l'onglet n'envoie que des demandes (numero, termes d'un brouillon) et
 * affiche la vue recue (aussi poussee quand l'autre partie agit ou qu'une heure est prelevee). Dates : fuseau du SERVEUR (recu dans la vue),
 * format de la langue ({@code contract.pioneers.format.*}). Maquette : {@code tools/gc/economy/contracts_ui_preview.py} (memes constantes).
 */
public final class ContractsTab implements NotebookScreen.Tab {

    public enum Mode {
        LIST, EDIT, DETAIL
    }

    // ------------------------------------------------------------------ mise en page (coordonnees de la page, maquette alignee)
    public static final int X = 10, W = ParchmentScreen.PAGE_W - 20, TOP_Y = 24, CHIP_W = 72, CHIP_H = 13, CHIP_GAP = 3, NEW_X = 312, NEW_W = 62;
    public static final int LIST_Y = 42, ROW_H = 24, LIST_H = 176, FOOT_Y = 224, STATE_W = 76, AMOUNT_W = 90;
    public static final int BACK_X = 314, BACK_W = 60, LEFT_X = 14, FIELD_H = 14;
    public static final int MSG_Y = 200, ACT_Y = 214, ACT_H = 16, HINT_LINES = 4;
    /**
     * 1.0.5-beta (lot 16, retours de Guillaume : « page de contrat : rediger une vraie lettre ») : le formulaire est une LETTRE a gauche
     * (destinataire au selecteur de joueur, objet, corps multiligne, signature, compte des caracteres et des lignes) et ses CONDITIONS a
     * droite (formule, montant, debut, fin, duree, montant prevu) ; le detail se lit comme une lettre (pastilles Lettre / Historique a
     * gauche, conditions et facture a droite). Rangees du formulaire :
     */
    public static final int LETTER_W = 184, TO_LABEL_W = 44, F_TO = 47, F_SUBJECT = 65, F_BODY = 82, BODY_H = 108, F_SIGN = 193, RIGHT_X2 = 208,
          RCOL_W = 162, F_PAY = 46, F_AMOUNT = 61, F_START = 80, F_END = 124, F_SUM = 168, VIEW_CHIP_W = 74, LETTER_Y = 60, BODY_SCROLL = 6;
    /** Champs de date : largeurs (jour, mois, annee, heure, minute) et ecart. */
    public static final int DW = 20, YW = 34, DGAP = 5, SHORT_Y = 18, SHORT_H = 12, RATE_W = 70, SAVE_W = 128, CHIP_PAD = 12, CHIP_MIN = 56;
    /** Detail : colonnes des libelles. */
    public static final int HIST_ROW = 18, LABEL_W = 58, D_Y = 46, D_STEP = 11, ACT_W = 92, PRINT_W = 112;
    private static final int LIST_SCROLL = 3, HIST_SCROLL = 4;

    private static final String[] FILTERS = {"pending", "running", "closed", "drafts"};

    // ------------------------------------------------------------------ etat (garde entre les ouvertures du Carnet)
    @Nullable
    private static CompoundTag view;
    private static long receivedAt;
    private static Mode mode = Mode.LIST;
    private static String filter = "";
    private static int selected, editing;
    private static boolean confirmTerminate;
    @Nullable
    private static String message;
    private static long messageAt;
    /** Formulaire : client, intitule, description, taux, debut et fin (jour, mois, annee, heure, minute). */
    private static String fClient = "", fTitle = "", fNote = "", fRate = "";
    /** 1.0.5-beta (lot 16) : detail : lettre ou historique (pastilles), editeur de la lettre en cours. */
    private static boolean showHistory;
    @Nullable
    private static fr.pioneers.client.gui.LetterEditor editor;
    /** 1.0.4-beta (lot 29) : formule choisie dans le formulaire (vrai = a l'heure ; {@code fRate} est alors le taux, sinon le total). */
    private static boolean fHourly;
    /** 1.0.4-beta (lot 29) : pastilles de la formule (« Au total », « A l'heure ») apres le libelle, sur la ligne de {@code F_RATE}. */
    public static final int PAY_LABEL_W = 48, PAY_W = 54, PAY_GAP = 3;
    private static final String[] fStart = new String[5], fEnd = new String[5];

    @Override
    public String id() {
        return "contracts";
    }

    @Override
    public boolean available() {
        return PioneerApi.hasFeature("economy");
    }

    // ------------------------------------------------------------------ 1.0.5-beta (lot 26) : vue emboitee dans l'onglet Documents
    /** Vue en cours (l'onglet Documents revient a sa liste quand celle-ci revient a la sienne). */
    public static Mode mode() {
        return mode;
    }

    /** Detail d'un contrat au temps reel (ouvert depuis la liste des documents). */
    public static void showDetail(int id) {
        selected = id;
        mode = Mode.DETAIL;
        confirmTerminate = false;
        message = null;
        showHistory = false;
    }

    /** Nouveau contrat au temps reel (formulaire d'avant : au total ou a l'heure, periode reelle). */
    public static void startNew() {
        newForm();
        mode = Mode.EDIT;
        confirmTerminate = false;
        message = null;
    }

    /** Ouverture emboitee (meme travail que {@link #opened}). */
    public static void embedded(NotebookScreen s) {
        INSTANCE.opened(s);
    }

    /** Dessin emboite (meme travail que {@link #draw}). */
    public static void drawEmbedded(NotebookScreen s, GuiGraphics g, int mx, int my, float pt) {
        INSTANCE.draw(s, g, mx, my, pt);
    }

    private static final ContractsTab INSTANCE = new ContractsTab();

    // ------------------------------------------------------------------ reseau
    public static void onServer(CompoundTag t) {
        boolean push = t.getBoolean("push");
        if (push) {
            DocumentsTab.contractsChanged(); //1.0.5-beta (lot 26) : la liste des documents suit les contrats au temps reel
        }
        view = t;
        receivedAt = Util.getMillis();
        if (!push && t.contains("msg")) {
            message = t.getString("msg");
            messageAt = receivedAt;
            int focus = t.getInt("focus");
            if (message.startsWith("ok_") && focus > 0 && !"ok_delete".equals(message)) {
                selected = focus;
                mode = Mode.DETAIL;
                reopen();
            } else if ("ok_delete".equals(message)) {
                mode = Mode.LIST;
                reopen();
            } else if (mode == Mode.EDIT && editing == 0 && focus > 0) {
                editing = focus; //brouillon cree mais pas propose (fin passee...) : la prochaine demande le modifie, sans en creer un autre
            }
        }
    }

    private static void request(String op, int id) {
        CompoundTag t = new CompoundTag();
        t.putString("op", op);
        t.putInt("id", id);
        RpNet.toServer(RpActions.CONTRACT_REQUEST, t);
    }

    /** Le Carnet ouvert sur cet onglet reconstruit ses champs (changement de vue). */
    private static void reopen() {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof NotebookScreen s) {
            s.open("contracts");
        }
    }

    private static void go(NotebookScreen s, Mode m) {
        mode = m;
        confirmTerminate = false;
        message = null;
        s.open("contracts");
    }

    // ------------------------------------------------------------------ outils
    static long serverNow() {
        return view == null ? System.currentTimeMillis() : view.getLong("now") + (Util.getMillis() - receivedAt);
    }

    static ZoneId zone() {
        try {
            return view == null ? ZoneId.systemDefault() : ZoneId.of(view.getString("zone"));
        } catch (DateTimeException e) {
            return ZoneId.systemDefault();
        }
    }

    @Nullable
    private static UUID me() {
        return view != null && view.hasUUID("me") ? view.getUUID("me") : null;
    }

    private static List<CompoundTag> contracts() {
        List<CompoundTag> out = new ArrayList<>();
        if (view != null) {
            for (Tag t : view.getList("list", Tag.TAG_COMPOUND)) {
                out.add((CompoundTag) t);
            }
        }
        return out;
    }

    @Nullable
    private static CompoundTag find(int id) {
        for (CompoundTag c : contracts()) {
            if (c.getInt("id") == id) {
                return c;
            }
        }
        return null;
    }

    static String filterOf(CompoundTag c) {
        String st = c.getString("state");
        return switch (st) {
            case "DRAFT" -> "drafts";
            case "PROPOSED" -> "pending";
            case "ACCEPTED", "RUNNING", "SUSPENDED" -> "running";
            case "FINISHED", "TERMINATED" -> c.getLong("owed") > 0 || c.getLong("held") > 0 || c.getLong("refund") > 0 ? "running" : "closed";
            default -> "closed";
        };
    }

    public static String group(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder(v < 0 ? "−" : "");
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    public static Component tokens(long v) {
        return Component.translatable("economy.pioneers.tokens", group(v));
    }

    public static Component duration(long minutes) {
        long h = minutes / 60, m = minutes % 60;
        return h == 0 ? Component.translatable("contract.pioneers.duration_min", m)
              : Component.translatable("contract.pioneers.duration", group(h), String.format(Locale.ROOT, "%02d", m));
    }

    public static String date(long ms, ZoneId zone, boolean shortForm) {
        String pattern = I18n.get(shortForm ? "contract.pioneers.format.short" : "contract.pioneers.format.date");
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.US).format(Instant.ofEpochMilli(ms).atZone(zone));
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).format(Instant.ofEpochMilli(ms).atZone(zone));
        }
    }

    /** Couleur de l'etat affiche. */
    public static int stateColor(String shown) {
        return switch (shown) {
            case "proposed" -> ParchmentScreen.GOLD;
            case "accepted" -> ParchmentScreen.INK_LIGHT;
            case "running" -> ParchmentScreen.INK;
            case "finished" -> ParchmentScreen.PLUS;
            case "unpaid", "refused", "suspended" -> ParchmentScreen.MINUS;
            default -> ParchmentScreen.DIM;
        };
    }

    public static Component stateLabel(String shown) {
        return Component.translatable("contract.pioneers.state." + shown);
    }

    /** Fuseau du serveur : « Europe/Paris (UTC+01:00) ». */
    static Component zoneText() {
        String off = view == null ? "" : view.getString("offset");
        return Component.translatable("contract.pioneers.zone", zone().getId(), off.isEmpty() ? "+00:00" : off);
    }

    // ------------------------------------------------------------------ ouverture
    @Override
    public void opened(NotebookScreen s) {
        //vue fraiche a l'ouverture (pas a chaque changement de vue de l'onglet : au plus une demande toutes les 3 s)
        if (view == null || Util.getMillis() - receivedAt > 3000) {
            request("list", 0);
        }
        if (mode == Mode.EDIT) {
            openForm(s);
        }
    }

    @Override
    public void draw(NotebookScreen s, GuiGraphics g, int mx, int my, float pt) {
        if (view == null) {
            s.wrap(g, Component.translatable("contract.pioneers.loading"), X + 4, LIST_Y + 4, W - 8, ParchmentScreen.DIM, 1F, 2);
            return;
        }
        if (mode == Mode.DETAIL && find(selected) == null) {
            mode = Mode.LIST;
        }
        switch (mode) {
            case EDIT -> drawForm(s, g, mx, my);
            case DETAIL -> drawDetail(s, g, mx, my, find(selected));
            default -> drawList(s, g, mx, my);
        }
    }

    // ------------------------------------------------------------------ LISTE
    private static void drawList(NotebookScreen s, GuiGraphics g, int mx, int my) {
        List<CompoundTag> all = contracts();
        int[] counts = new int[FILTERS.length];
        for (CompoundTag c : all) {
            for (int i = 0; i < FILTERS.length; i++) {
                if (FILTERS[i].equals(filterOf(c))) {
                    counts[i]++;
                }
            }
        }
        if (filter.isEmpty()) {
            filter = FILTERS[1];
            for (int i = 0; i < FILTERS.length; i++) {
                if (counts[i] > 0) {
                    filter = FILTERS[i];
                    break;
                }
            }
        }
        Component[] labels = new Component[FILTERS.length];
        for (int i = 0; i < FILTERS.length; i++) {
            labels[i] = Component.translatable("contract.pioneers.filter." + FILTERS[i], counts[i]);
        }
        int[] cw = chipWidths(s.font(), labels);
        int x = X;
        for (int i = 0; i < FILTERS.length; i++) {
            String f = FILTERS[i];
            s.chip(g, mx, my, x, TOP_Y, cw[i], CHIP_H, labels[i], f.equals(filter), true, () -> {
                filter = f;
                s.scrollTo(LIST_SCROLL, 0);
            });
            x += cw[i] + CHIP_GAP;
        }
        s.button(g, mx, my, NEW_X, TOP_Y, NEW_W, CHIP_H, Component.translatable("contract.pioneers.new"), true, () -> {
            newForm();
            go(s, Mode.EDIT);
        });
        List<CompoundTag> shown = new ArrayList<>();
        for (CompoundTag c : all) {
            if (filter.equals(filterOf(c))) {
                shown.add(c);
            }
        }
        UUID me = me();
        ZoneId z = zone();
        s.scrollArea(LIST_SCROLL, X, LIST_Y, W, LIST_H, shown.size() * ROW_H);
        s.beginClip(g, X, LIST_Y, W, LIST_H);
        int y = LIST_Y - s.scrollOf(LIST_SCROLL);
        for (CompoundTag c : shown) {
            if (y + ROW_H > LIST_Y && y < LIST_Y + LIST_H) {
                row(s, g, mx, my, c, y, me, z);
            }
            y += ROW_H;
        }
        s.endClip(g);
        if (shown.isEmpty()) {
            s.wrap(g, Component.translatable("contract.pioneers.empty." + filter), X + 4, LIST_Y + 6, W - 8, ParchmentScreen.DIM, 1F, 3);
        }
        footer(s, g);
    }

    /**
     * Largeur des pastilles de filtre : libelle + {@link #CHIP_PAD} (au moins {@link #CHIP_MIN}), toutes ramenees a la meme echelle si elles
     * depassent la place avant le bouton « + Nouveau » (le libelle se resserre alors dans sa pastille).
     */
    static int[] chipWidths(net.minecraft.client.gui.Font font, Component[] labels) {
        int n = labels.length, room = NEW_X - 6 - X - (n - 1) * CHIP_GAP, sum = 0;
        int[] w = new int[n];
        for (int i = 0; i < n; i++) {
            w[i] = Math.max(CHIP_MIN, font.width(labels[i]) + CHIP_PAD);
            sum += w[i];
        }
        if (sum > room) {
            for (int i = 0; i < n; i++) {
                w[i] = w[i] * room / sum;
            }
        }
        return w;
    }

    private static void row(NotebookScreen s, GuiGraphics g, int mx, int my, CompoundTag c, int y, @Nullable UUID me, ZoneId z) {
        int id = c.getInt("id");
        boolean on = s.over(mx, my, X, y, W, ROW_H - 1) && my >= LIST_Y && my < LIST_Y + LIST_H;
        if (on) {
            g.fill(X, y, X + W, y + ROW_H - 1, 0x18243660);
        }
        String shown = c.getString("shown");
        int sw = s.fitRight(g, stateLabel(shown), X + W - 4, y + 3, STATE_W, stateColor(shown), 1F);
        Component head = Component.translatable("contract.pioneers.number", id).append("  ").append(Component.literal(c.getString("title")));
        s.fit(g, head, X + 4, y + 3, W - 8 - sw - 8, ParchmentScreen.INK);
        boolean provider = me != null && c.hasUUID("pv") && me.equals(c.getUUID("pv"));
        Component who = Component.translatable(provider ? "contract.pioneers.row.client" : "contract.pioneers.row.provider",
              provider ? c.getString("cln") : c.getString("pvn"));
        Component line = c.getBoolean("hourly") ? Component.translatable("contract.pioneers.row.line", who, date(c.getLong("start"), z, true),
              date(c.getLong("end"), z, true), group(c.getLong("rate")))
              : Component.translatable("contract.pioneers.row.line_total", who, date(c.getLong("start"), z, true), date(c.getLong("end"), z, true));
        //en cours : facture / prevu ; sinon le facture (ou le prevu tant que rien n'est facture) ; un reste du passe devant
        Component money = c.getLong("owed") > 0 ? Component.translatable("contract.pioneers.row.owed", group(c.getLong("owed")))
              : "RUNNING".equals(c.getString("state")) ? Component.translatable("contract.pioneers.row.progress", group(c.getLong("billed")),
              group(c.getLong("planned"))) : tokens(c.getLong("billed") > 0 ? c.getLong("billed") : c.getLong("planned"));
        int aw = s.fitRight(g, money, X + W - 4, y + 13, AMOUNT_W, c.getLong("owed") > 0 ? ParchmentScreen.MINUS : ParchmentScreen.TEXT, 0.75F);
        s.fit(g, line, X + 4, y + 13, W - 8 - aw - 8, ParchmentScreen.DIM, 0.75F);
        s.separator(g, X, y + ROW_H - 1, W);
        if (on) {
            s.click(X, Math.max(y, LIST_Y), W, ROW_H - 1, () -> {
                selected = id;
                go(s, Mode.DETAIL);
            });
        }
    }

    private static void footer(NotebookScreen s, GuiGraphics g) {
        Component bal = Component.translatable("contract.pioneers.balance", group(view == null ? 0 : view.getLong("balance")));
        int bw = s.fitRight(g, bal, X + W - 4, FOOT_Y, 120, ParchmentScreen.TEXT, 0.75F);
        if (message != null && Util.getMillis() - messageAt < 8000) {
            s.fit(g, result(message), X + 4, FOOT_Y, W - 16 - bw, message.startsWith("ok_") ? ParchmentScreen.PLUS : ParchmentScreen.MINUS, 0.75F);
        } else {
            s.fit(g, Component.translatable("contract.pioneers.server_time", date(serverNow(), zone(), false), zoneText()), X + 4, FOOT_Y,
                  W - 16 - bw, ParchmentScreen.DIM, 0.75F);
        }
    }

    static Component result(String msg) {
        return Component.translatable(msg.startsWith("ok_") ? "contract.pioneers.result." + msg : "contract.pioneers.error." + msg);
    }

    // ------------------------------------------------------------------ FORMULAIRE
    private static void newForm() {
        editing = 0;
        fClient = "";
        fTitle = "";
        fNote = "";
        fRate = "";
        fHourly = false;
        long now = serverNow() / 60_000L * 60_000L;
        setFields(fStart, now);
        setFields(fEnd, now + 3_600_000L);
    }

    private static void loadForm(CompoundTag c) {
        editing = c.getInt("id");
        fClient = c.getString("cla").isEmpty() ? c.getString("cln") : c.getString("cla");
        fTitle = c.getString("title");
        fNote = c.getString("body").isEmpty() ? c.getString("note") : c.getString("body");
        fHourly = c.getBoolean("hourly");
        fRate = Long.toString(fHourly ? c.getLong("rate") : c.getLong("total") > 0 ? c.getLong("total") : c.getLong("planned"));
        setFields(fStart, c.getLong("start"));
        setFields(fEnd, c.getLong("end"));
    }

    private static void setFields(String[] f, long ms) {
        ZonedDateTime d = Instant.ofEpochMilli(ms).atZone(zone());
        f[0] = String.format(Locale.ROOT, "%02d", d.getDayOfMonth());
        f[1] = String.format(Locale.ROOT, "%02d", d.getMonthValue());
        f[2] = Integer.toString(d.getYear());
        f[3] = String.format(Locale.ROOT, "%02d", d.getHour());
        f[4] = String.format(Locale.ROOT, "%02d", d.getMinute());
    }

    /** Champs -> {annee, mois, jour, heure, minute} (null si incomplet). */
    @Nullable
    static int[] fields(String[] f) {
        try {
            return new int[]{Integer.parseInt(f[2]), Integer.parseInt(f[1]), Integer.parseInt(f[0]), Integer.parseInt(f[3]), Integer.parseInt(f[4])};
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** Champs -> ms dans le fuseau du serveur (-1 si la date n'existe pas). */
    static long epoch(String[] f) {
        int[] v = fields(f);
        if (v == null) {
            return -1;
        }
        try {
            return LocalDateTime.of(v[0], v[1], v[2], v[3], v[4]).atZone(zone()).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            return -1;
        }
    }

    static long rate() {
        try {
            return fRate.isEmpty() ? 0 : Long.parseLong(fRate);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Ordre des champs de date dans la langue (« dmy » ou « mdy ») : indice du champ a la position i. */
    static int[] order() {
        return "mdy".equals(I18n.get("contract.pioneers.format.input")) ? new int[]{1, 0, 2} : new int[]{0, 1, 2};
    }

    private static void openForm(NotebookScreen s) {
        //1.0.5-beta (lot 16) : destinataire au selecteur de joueur deja vu (saisie libre gardee), corps multiligne
        fr.pioneers.client.gui.PlayerPicker to = new fr.pioneers.client.gui.PlayerPicker(s.font(), LEFT_X + TO_LABEL_W, F_TO - 3, LETTER_W - TO_LABEL_W,
              FIELD_H, Component.translatable("contract.pioneers.field.client"), "contract", fr.pioneers.client.gui.PlayerPicker.Style.PAPER);
        to.setValue(fClient);
        to.setResponder(v -> fClient = v);
        s.tabWidget(to);
        s.tabWidget(box(s, LEFT_X + TO_LABEL_W, F_SUBJECT - 3, LETTER_W - TO_LABEL_W, 40, fTitle, "contract.pioneers.field.title", v -> fTitle = v,
              false));
        editor = new fr.pioneers.client.gui.LetterEditor(s.font(), LEFT_X, F_BODY, LETTER_W, BODY_H, Component.translatable("contract.pioneers.letter.body_hint"),
              fr.pioneers.economy.contract.ContractRules.BODY_MAX, fr.pioneers.economy.contract.ContractRules.BODY_LINES);
        editor.setValue(fNote);
        editor.setValueListener(v -> fNote = v);
        s.tabWidget(editor);
        s.tabWidget(box(s, RIGHT_X2, F_AMOUNT, RATE_W, 10, fRate, "contract.pioneers.field.rate_hint", v -> fRate = v, true));
        dateBoxes(s, fStart, F_START + 10);
        dateBoxes(s, fEnd, F_END + 10);
    }

    private static void dateBoxes(NotebookScreen s, String[] f, int y) {
        int[] ord = order();
        int x = RIGHT_X2;
        for (int i = 0; i < 3; i++) {
            int k = ord[i];
            int w = k == 2 ? YW : DW;
            s.tabWidget(box(s, x, y, w, k == 2 ? 4 : 2, f[k], null, v -> f[k] = v, true));
            x += w + DGAP + (i < 2 ? 2 : 6);
        }
        s.tabWidget(box(s, x, y, DW, 2, f[3], null, v -> f[3] = v, true));
        x += DW + DGAP + 2;
        s.tabWidget(box(s, x, y, DW, 2, f[4], null, v -> f[4] = v, true));
    }

    private static EditBox box(NotebookScreen s, int x, int y, int w, int max, String value, @Nullable String hint,
          java.util.function.Consumer<String> sink, boolean digits) {
        EditBox b = new EditBox(s.font(), x, y, w, FIELD_H, hint == null ? Component.empty() : Component.translatable(hint));
        b.setMaxLength(max);
        if (digits) {
            b.setFilter(v -> v.chars().allMatch(Character::isDigit));
        }
        b.setValue(value == null ? "" : value);
        if (hint != null) {
            b.setHint(Component.translatable(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        b.setResponder(sink);
        return b;
    }

    /** Nom de pionnier du joueur (signature de la lettre). */
    private static String myName() {
        return fr.pioneers.client.rp.RpClient.profile().name;
    }

    private static void drawForm(NotebookScreen s, GuiGraphics g, int mx, int my) {
        Component head = editing > 0 ? Component.translatable("contract.pioneers.form.draft", editing) : Component.translatable("contract.pioneers.form.new");
        s.icon(g, fr.pioneers.client.rp.RpGui.stat("contract"), LEFT_X, TOP_Y + 1, 10);
        s.fit(g, head, LEFT_X + 14, TOP_Y + 3, BACK_X - LEFT_X - 22, ParchmentScreen.INK);
        s.button(g, mx, my, BACK_X, TOP_Y, BACK_W, FIELD_H, Component.translatable("contract.pioneers.back"), true,
              () -> go(s, editing > 0 ? Mode.DETAIL : Mode.LIST));
        s.separator(g, LEFT_X, TOP_Y + 17, W - 8);
        int dim = ParchmentScreen.DIM;
        //la lettre : destinataire, objet, corps, signature et compte
        s.icon(g, fr.pioneers.client.rp.RpGui.stat("player"), LEFT_X, F_TO, 8);
        s.fit(g, Component.translatable("contract.pioneers.letter.to"), LEFT_X + 11, F_TO, TO_LABEL_W - 13, dim, 0.75F);
        s.tooltip(mx, my, LEFT_X, F_TO - 2, TO_LABEL_W, 11, Component.translatable("contract.pioneers.letter.to.tip"));
        s.icon(g, fr.pioneers.client.rp.RpGui.stat("letter"), LEFT_X, F_SUBJECT, 8);
        s.fit(g, Component.translatable("contract.pioneers.letter.subject"), LEFT_X + 11, F_SUBJECT, TO_LABEL_W - 13, dim, 0.75F);
        int len = editor == null ? fNote.length() : editor.length(), lines = editor == null ? 1 : editor.lines();
        int max = fr.pioneers.economy.contract.ContractRules.BODY_MAX, maxLines = fr.pioneers.economy.contract.ContractRules.BODY_LINES;
        Component count = Component.translatable("contract.pioneers.letter.count", group(len), group(max));
        int cw = s.fitRight(g, count, LEFT_X + LETTER_W, F_SIGN, 100, len >= max || lines >= maxLines ? ParchmentScreen.MINUS : dim, 0.75F);
        s.tooltip(mx, my, LEFT_X + LETTER_W - cw, F_SIGN - 1, cw, 8, Component.translatable("contract.pioneers.letter.count.tip", lines, maxLines));
        s.fit(g, Component.literal("— " + myName()).withStyle(st -> st.withItalic(true)), LEFT_X, F_SIGN, LETTER_W - cw - 6, ParchmentScreen.INK_LIGHT,
              0.75F);
        //les conditions
        s.icon(g, fr.pioneers.client.rp.RpGui.stat("shining"), RIGHT_X2, F_PAY - 1, 10);
        s.fit(g, Component.translatable("contract.pioneers.field.pay_label"), RIGHT_X2 + 13, F_PAY, PAY_LABEL_W - 15, dim, 0.75F);
        s.tooltip(mx, my, RIGHT_X2, F_PAY - 2, PAY_LABEL_W, 12,
              Component.translatable(fHourly ? "contract.pioneers.form.hint_hourly" : "contract.pioneers.form.hint_total"));
        int px = RIGHT_X2 + PAY_LABEL_W;
        for (int i = 0; i < 2; i++) {
            boolean hourly = i == 1;
            s.chip(g, mx, my, px, F_PAY - 2, PAY_W, SHORT_H, Component.translatable(hourly ? "contract.pioneers.pay.hourly" : "contract.pioneers.pay.total"),
                  fHourly == hourly, true, 0.75F, () -> {
                      fHourly = hourly;
                      message = null;
                  });
            px += PAY_W + PAY_GAP;
        }
        s.fit(g, Component.translatable(fHourly ? "contract.pioneers.per_hour" : "contract.pioneers.in_total"), RIGHT_X2 + RATE_W + 6, F_AMOUNT + 3,
              RCOL_W - RATE_W - 6, ParchmentScreen.TEXT);
        dateRow(s, g, mx, my, "contract.pioneers.field.start", fStart, F_START, true);
        dateRow(s, g, mx, my, "contract.pioneers.field.end", fEnd, F_END, false);
        //duree, montant prevu
        long a = epoch(fStart), b = epoch(fEnd), r = rate();
        int y = F_SUM;
        if (a < 0 || b < 0) {
            s.wrap(g, Component.translatable("contract.pioneers.error.date"), RIGHT_X2, y, RCOL_W, ParchmentScreen.MINUS, 0.75F, 2);
        } else {
            long minutes = b > a ? (b - a) / 60_000L : 0;
            s.icon(g, fr.pioneers.client.rp.RpGui.stat("date"), RIGHT_X2, y, 8);
            s.fit(g, Component.translatable("contract.pioneers.form.duration", duration(minutes)), RIGHT_X2 + 11, y + 1, RCOL_W - 11, ParchmentScreen.TEXT,
                  0.75F);
            s.fit(g, Component.translatable("contract.pioneers.form.planned_value", tokens(fHourly ? Math.floorDiv(minutes * r, 60L) : r)), RIGHT_X2,
                  y + 10, RCOL_W, ParchmentScreen.INK);
        }
        s.fit(g, zoneText(), RIGHT_X2, y + 21, RCOL_W, dim, 0.75F);
        if (message != null && !message.startsWith("ok_")) {
            s.fit(g, result(message), LEFT_X, MSG_Y + 2, W - 8, ParchmentScreen.MINUS, 0.75F);
        }
        boolean ready = !fClient.isBlank() && !fTitle.isBlank() && r > 0 && a >= 0 && b > a;
        s.button(g, mx, my, LEFT_X, ACT_Y, SAVE_W, ACT_H, Component.translatable("contract.pioneers.action.save"), ready, () -> submit(false));
        s.button(g, mx, my, LEFT_X + SAVE_W + 4, ACT_Y, SAVE_W, ACT_H, Component.translatable("contract.pioneers.action.propose"), ready, () -> submit(true));
    }

    private static void dateRow(NotebookScreen s, GuiGraphics g, int mx, int my, String label, String[] f, int y, boolean start) {
        s.fit(g, Component.translatable(label), RIGHT_X2, y, RCOL_W, ParchmentScreen.DIM, 0.75F);
        //separateurs entre les champs (/ / et :)
        int[] ord = order();
        int x = RIGHT_X2;
        for (int i = 0; i < 3; i++) {
            int w = ord[i] == 2 ? YW : DW;
            x += w;
            if (i < 2) {
                s.fit(g, Component.literal("/"), x + 1, y + 13, 6, ParchmentScreen.DIM);
            }
            x += DGAP + (i < 2 ? 2 : 6);
        }
        x += DW;
        s.fit(g, Component.literal(":"), x + 2, y + 13, 4, ParchmentScreen.DIM);
        //raccourcis
        int cy = y + 10 + FIELD_H + 4;
        int[] ws = {58, 40, 52};
        String[] keys = {"contract.pioneers.now", "contract.pioneers.plus_hour", "contract.pioneers.plus_day"};
        int cx = RIGHT_X2;
        for (int i = 0; i < 3; i++) {
            int k = i;
            s.chip(g, mx, my, cx, cy, ws[i], SHORT_H, Component.translatable(keys[i]), false, true, () -> shortcut(s, f, start, k));
            cx += ws[i] + 3;
        }
    }

    /** Raccourci de date : « Maintenant » (minute en cours), « +1 h », « +1 jour » (sur la valeur du champ) ; la fin suit un debut qui la depasse. */
    private static void shortcut(NotebookScreen s, String[] f, boolean start, int k) {
        long base = epoch(f);
        long now = serverNow() / 60_000L * 60_000L;
        long v = k == 0 ? now : (base < 0 ? now : base) + (k == 1 ? 3_600_000L : 86_400_000L);
        setFields(f, v);
        if (start && epoch(fEnd) <= v) {
            setFields(fEnd, v + 3_600_000L);
        }
        message = null;
        s.open("contracts");
    }

    private static void submit(boolean propose) {
        int[] a = fields(fStart), b = fields(fEnd);
        if (a == null || b == null) {
            message = "date";
            messageAt = Util.getMillis();
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putString("op", "save");
        t.putInt("id", editing);
        t.putString("client", fClient.trim());
        t.putString("title", fTitle.trim());
        //1.0.5-beta (lot 16) : corps de la lettre (le serveur le borne et le refuse s'il depasse) ; description courte = son debut
        t.putString("body", fNote);
        t.putString("note", fNote.replace('\n', ' ').trim());
        t.putIntArray("s", a);
        t.putIntArray("e", b);
        t.putString("pay", fHourly ? "hourly" : "total");
        t.putLong("amount", rate());
        t.putBoolean("propose", propose);
        RpNet.toServer(RpActions.CONTRACT_REQUEST, t);
    }

    // ------------------------------------------------------------------ DETAIL
    private static void drawDetail(NotebookScreen s, GuiGraphics g, int mx, int my, @Nullable CompoundTag c) {
        if (c == null) {
            return;
        }
        ZoneId z = zone();
        int id = c.getInt("id");
        String shown = c.getString("shown");
        UUID me = me();
        boolean provider = me != null && c.hasUUID("pv") && me.equals(c.getUUID("pv"));
        boolean client = me != null && c.hasUUID("cl") && me.equals(c.getUUID("cl"));
        s.button(g, mx, my, BACK_X, TOP_Y, BACK_W, FIELD_H, Component.translatable("contract.pioneers.back"), true, () -> go(s, Mode.LIST));
        int sw = s.fitRight(g, stateLabel(shown), BACK_X - 8, TOP_Y + 3, STATE_W, stateColor(shown), 1F);
        s.icon(g, fr.pioneers.client.rp.RpGui.stat("contract"), LEFT_X, TOP_Y + 1, 10);
        Component head = Component.translatable("contract.pioneers.number", id).append("  ").append(Component.literal(c.getString("title")));
        s.fit(g, head, LEFT_X + 14, TOP_Y + 3, BACK_X - 8 - sw - 8 - LEFT_X - 14, ParchmentScreen.INK);
        s.separator(g, LEFT_X, TOP_Y + 17, W - 8);
        //a gauche : la lettre ou l'historique (pastilles)
        ListTag h = c.getList("hist", Tag.TAG_COMPOUND);
        Component[] views = {Component.translatable("contract.pioneers.view.letter"), Component.translatable("contract.pioneers.view.history", h.size())};
        for (int i = 0; i < 2; i++) {
            boolean hist = i == 1;
            s.chip(g, mx, my, LEFT_X + i * (VIEW_CHIP_W + 3), D_Y - 2, VIEW_CHIP_W, SHORT_H, views[i], showHistory == hist, true, 0.75F, () -> {
                showHistory = hist;
                s.scrollTo(BODY_SCROLL, 0);
            });
        }
        if (showHistory) {
            drawHistory(s, g, h, z);
        } else {
            drawLetter(s, g, mx, my, c, z);
        }
        //a droite : les conditions, puis la facture (ou ce qui va se passer)
        int ry = D_Y;
        s.icon(g, fr.pioneers.client.rp.RpGui.stat("date"), RIGHT_X2, ry - 1, 10);
        s.fit(g, Component.translatable("contract.pioneers.terms"), RIGHT_X2 + 13, ry, RCOL_W - 13, ParchmentScreen.INK);
        ry += D_STEP + 1;
        ry = kv(s, g, "contract.pioneers.label.start", Component.literal(date(c.getLong("start"), z, false)), RIGHT_X2, ry);
        ry = kv(s, g, "contract.pioneers.label.end", Component.literal(date(c.getLong("end"), z, false)), RIGHT_X2, ry);
        boolean hourly = c.getBoolean("hourly");
        ry = hourly ? kv(s, g, "contract.pioneers.label.rate_hourly", Component.translatable("contract.pioneers.rate_value", group(c.getLong("rate"))), RIGHT_X2,
              ry) : kv(s, g, "contract.pioneers.label.total", tokens(c.getLong("planned")), RIGHT_X2, ry);
        ry = kv(s, g, "contract.pioneers.label.planned", Component.translatable("contract.pioneers.planned_value", duration(c.getLong("plannedMin")),
              tokens(c.getLong("planned"))), RIGHT_X2, ry);
        ry += 4;
        boolean billing = "RUNNING".equals(c.getString("state")) || "FINISHED".equals(c.getString("state")) || "TERMINATED".equals(c.getString("state"))
                          || "SUSPENDED".equals(c.getString("state"));
        s.icon(g, fr.pioneers.client.rp.RpGui.stat(billing ? "shining" : "history"), RIGHT_X2, ry - 1, 10);
        s.fit(g, Component.translatable(billing ? "contract.pioneers.invoice" : "contract.pioneers.next_title"), RIGHT_X2 + 13, ry, RCOL_W - 13,
              ParchmentScreen.INK);
        ry += D_STEP + 1;
        if (!billing) {
            //rien n'est encore facture : ce qui va se passer, selon l'etat et le role (pas de facture vide)
            ZonedDateTime from = Instant.ofEpochMilli(Math.max(c.getLong("eff"), c.getLong("start"))).atZone(z);
            String st = c.getString("state").toLowerCase(Locale.ROOT);
            String other = provider ? c.getString("cln") : c.getString("pvn"), when = date(from.toInstant().toEpochMilli(), z, false);
            boolean open = st.equals("draft") || st.equals("proposed") || st.equals("accepted");
            Component next = !open ? Component.translatable("contract.pioneers.next." + st + (provider ? "_provider" : "_client"))
                  : hourly ? Component.translatable("contract.pioneers.next." + st + (provider ? "_provider" : "_client") + "_hourly", group(c.getLong("rate")),
                  group(c.getLong("planned")), when, other)
                  : Component.translatable("contract.pioneers.next." + st + (provider ? "_provider" : "_client") + "_total", group(c.getLong("planned")), when,
                  other);
            s.wrap(g, next, RIGHT_X2, ry, RCOL_W, ParchmentScreen.TEXT, 0.75F, (MSG_Y - ry) / ParchmentScreen.lineStep(0.75F));
        } else {
            drawBilling(s, g, mx, my, c, ry);
        }
        //resultat de la derniere demande ; avertissement avant la resiliation
        if (confirmTerminate) {
            s.fit(g, Component.translatable("contract.pioneers.confirm_terminate"), LEFT_X, MSG_Y + 2, W - 8, ParchmentScreen.MINUS, 0.75F);
        } else if (message != null && Util.getMillis() - messageAt < 8000) {
            s.fit(g, result(message), LEFT_X, MSG_Y + 2, W - 8, message.startsWith("ok_") ? ParchmentScreen.PLUS : ParchmentScreen.MINUS, 0.75F);
        }
        actions(s, g, mx, my, c, provider, client);
    }

    /**
     * 1.0.5-beta (lot 16) : la lettre telle que le destinataire la lit : expediteur, destinataire, date, objet, corps (qui defile), signature ;
     * un contrat d'avant sans texte : une phrase qui renvoie aux conditions.
     */
    private static void drawLetter(NotebookScreen s, GuiGraphics g, int mx, int my, CompoundTag c, ZoneId z) {
        int x = LEFT_X, w = LETTER_W, top = LETTER_Y, bottom = MSG_Y - 2;
        g.fill(x - 2, top - 2, x + w + 2, bottom, 0x22FFFFFF);
        s.frame(g, x - 2, top - 2, w + 4, bottom - top + 2, ParchmentScreen.FAINT);
        long when = c.getLong("proposed") > 0 ? c.getLong("proposed") : c.getLong("created");
        int dw = s.fitRight(g, Component.literal(date(when, z, true)), x + w - 2, top, 64, ParchmentScreen.DIM, 0.75F);
        s.fit(g, Component.translatable("contract.pioneers.letter.from_line", c.getString("pvn")), x + 2, top, w - 4 - dw - 4, ParchmentScreen.TEXT, 0.75F);
        s.fit(g, Component.translatable("contract.pioneers.letter.to_line", c.getString("cln")), x + 2, top + 9, w - 4, ParchmentScreen.TEXT, 0.75F);
        int sh = s.wrap(g, Component.translatable("contract.pioneers.letter.subject_line", c.getString("title")), x + 2, top + 19, w - 4,
              ParchmentScreen.INK, 1F, 2);
        s.separator(g, x + 2, top + 20 + sh, w - 4);
        String body = c.getString("body").isEmpty() ? c.getString("note") : c.getString("body");
        int bt = top + 23 + sh, bh = bottom - 12 - bt;
        if (body.isEmpty()) {
            s.wrap(g, Component.translatable("contract.pioneers.letter.empty"), x + 2, bt, w - 4, ParchmentScreen.DIM, 0.75F, 4);
        } else {
            Component text = Component.literal(body);
            int content = s.wrapHeight(text, w - 6, 1F);
            s.scrollArea(BODY_SCROLL, x, bt, w, bh, content);
            s.beginClip(g, x, bt, w, bh);
            s.wrap(g, text, x + 2, bt - s.scrollOf(BODY_SCROLL), w - 6, ParchmentScreen.TEXT, 1F, 99);
            s.endClip(g);
            if (content > bh) {
                s.fitRight(g, Component.literal(s.scrollOf(BODY_SCROLL) + bh >= content ? "▲" : "▼"), x + w - 2, bottom - 10, 8, ParchmentScreen.FAINT, 0.75F);
            }
        }
        s.fit(g, Component.literal("— " + c.getString("pvn")).withStyle(st -> st.withItalic(true)), x + 2, bottom - 10, w - 14, ParchmentScreen.INK_LIGHT,
              0.75F);
    }

    /** Historique en colonne de gauche (le plus recent en haut). */
    private static void drawHistory(NotebookScreen s, GuiGraphics g, ListTag h, ZoneId z) {
        List<CompoundTag> lines = new ArrayList<>();
        for (int i = h.size() - 1; i >= 0; i--) {
            lines.add(h.getCompound(i));
        }
        int step = HIST_ROW, top = LETTER_Y, hh = MSG_Y - 2 - top;
        s.scrollArea(HIST_SCROLL, LEFT_X, top, LETTER_W, hh, lines.size() * step);
        s.beginClip(g, LEFT_X, top, LETTER_W, hh);
        int hy = top - s.scrollOf(HIST_SCROLL);
        for (CompoundTag l : lines) {
            if (hy + step > top && hy < top + hh) {
                histLine(s, g, l, hy, z);
            }
            hy += step;
        }
        s.endClip(g);
    }

    /** Bloc « Facture » d'un contrat qui facture : temps facture, facture, paye, reste du, a ce jour, barre facture / prevu. */
    private static void drawBilling(NotebookScreen s, GuiGraphics g, int mx, int my, CompoundTag c, int ry) {
        ry = kv(s, g, "contract.pioneers.label.hours", duration(c.getLong("bm")), RIGHT_X2, ry);
        ry = kv(s, g, "contract.pioneers.label.billed", tokens(c.getLong("billed")), RIGHT_X2, ry);
        ry = kvc(s, g, "contract.pioneers.label.paid", tokens(c.getLong("paid")), RIGHT_X2, ry, ParchmentScreen.PLUS);
        long owed = c.getLong("owed");
        ry = kvc(s, g, "contract.pioneers.label.owed", tokens(owed), RIGHT_X2, ry, owed > 0 ? ParchmentScreen.MINUS : ParchmentScreen.TEXT);
        if (c.getBoolean("hourly") && ("RUNNING".equals(c.getString("state")) || "SUSPENDED".equals(c.getString("state")))) {
            //1.0.4-beta (lot 29) : reserve du client / reserve visee
            ry = kvc(s, g, "contract.pioneers.label.reserve", Component.translatable("contract.pioneers.reserve_value", group(c.getLong("reserve")),
                  group(c.getLong("reserveTarget"))), RIGHT_X2, ry, c.getLong("reserve") < c.getLong("rate") ? ParchmentScreen.MINUS : ParchmentScreen.TEXT);
        } else if ("RUNNING".equals(c.getString("state"))) {
            ry = kv(s, g, "contract.pioneers.label.accrued", tokens(c.getLong("accrued")), RIGHT_X2, ry);
        }
        long planned = Math.max(1, c.getLong("planned"));
        s.bar(g, RIGHT_X2, ry + 3, RCOL_W, 5, c.getLong("billed") / (double) planned, ParchmentScreen.INK_LIGHT);
        s.tooltip(mx, my, RIGHT_X2, ry + 1, RCOL_W, 9, Component.translatable("contract.pioneers.bar", group(c.getLong("billed")), group(c.getLong("planned"))));
    }

    private static void histLine(NotebookScreen s, GuiGraphics g, CompoundTag l, int y, ZoneId z) {
        String k = l.getString("k");
        long amount = l.getLong("a"), minutes = l.getLong("m");
        //1.0.5-beta (lot 16) : deux lignes par entree (date et montant, puis ce qui s'est passe) dans la colonne de la lettre
        s.fit(g, Component.literal(date(l.getLong("t"), z, true)), LEFT_X, y, 90, ParchmentScreen.DIM, 0.75F);
        if (amount > 0 && ("billed".equals(k) || "paid".equals(k) || "short".equals(k) || "hour".equals(k) || "suspended".equals(k)
                           || "refund".equals(k))) {
            s.fitRight(g, tokens(amount), LEFT_X + LETTER_W, y, 90, "short".equals(k) || "suspended".equals(k) ? ParchmentScreen.MINUS
                  : "paid".equals(k) ? ParchmentScreen.PLUS : ParchmentScreen.TEXT, 0.75F);
        }
        Component what = "billed".equals(k) || "terminated".equals(k) || "hour".equals(k)
              ? Component.translatable("contract.pioneers.hist." + k, duration(minutes)) : Component.translatable("contract.pioneers.hist." + k);
        s.fit(g, what, LEFT_X + 6, y + 8, LETTER_W - 6, ParchmentScreen.TEXT, 0.75F);
        s.separator(g, LEFT_X, y + HIST_ROW - 1, LETTER_W);
    }

    private static int kv(NotebookScreen s, GuiGraphics g, String label, Component value, int x, int y) {
        return kvc(s, g, label, value, x, y, ParchmentScreen.TEXT);
    }

    private static int kvc(NotebookScreen s, GuiGraphics g, String label, Component value, int x, int y, int color) {
        s.fit(g, Component.translatable(label), x, y, LABEL_W - 2, ParchmentScreen.DIM, 0.75F);
        s.fit(g, value, x + LABEL_W, y, RCOL_W - LABEL_W, color, 0.75F);
        return y + D_STEP;
    }

    private static void actions(NotebookScreen s, GuiGraphics g, int mx, int my, CompoundTag c, boolean provider, boolean client) {
        int id = c.getInt("id");
        String st = c.getString("state");
        List<Object[]> acts = new ArrayList<>();
        if (provider && "DRAFT".equals(st)) {
            acts.add(new Object[]{"edit", (Runnable) () -> {
                loadForm(c);
                go(s, Mode.EDIT);
            }});
            acts.add(new Object[]{"propose", (Runnable) () -> request("propose", id)});
            acts.add(new Object[]{"delete", (Runnable) () -> request("delete", id)});
        }
        if (provider && "PROPOSED".equals(st)) {
            acts.add(new Object[]{"withdraw", (Runnable) () -> request("withdraw", id)});
        }
        if (client && "PROPOSED".equals(st)) {
            acts.add(new Object[]{"accept", (Runnable) () -> request("accept", id)});
            acts.add(new Object[]{"refuse", (Runnable) () -> request("refuse", id)});
        }
        if (client && "SUSPENDED".equals(st)) {
            acts.add(new Object[]{"resume", (Runnable) () -> request("resume", id)});        //1.0.4-beta (lot 29) : regler et reprendre
        }
        if ((provider || client) && ("ACCEPTED".equals(st) || "RUNNING".equals(st) || "SUSPENDED".equals(st))) {
            if (confirmTerminate) {
                acts.add(new Object[]{"confirm_terminate", (Runnable) () -> {
                    confirmTerminate = false;
                    request("terminate", id);
                }});
            } else {
                acts.add(new Object[]{"terminate", (Runnable) () -> confirmTerminate = true});
            }
        }
        if (client && c.getLong("owed") > 0 && !"SUSPENDED".equals(st)) {
            acts.add(new Object[]{"pay", (Runnable) () -> request("pay", id)});
        }
        int x = LEFT_X;
        for (Object[] a : acts) {
            s.button(g, mx, my, x, ACT_Y, ACT_W, ACT_H, Component.translatable("contract.pioneers.action." + a[0]), true, (Runnable) a[1]);
            x += ACT_W + 4;
        }
        if ("RUNNING".equals(st) || "FINISHED".equals(st) || "TERMINATED".equals(st) || "SUSPENDED".equals(st)) {
            s.button(g, mx, my, LEFT_X + W - 8 - PRINT_W, ACT_Y, PRINT_W, ACT_H, Component.translatable("contract.pioneers.action.print"), true,
                  () -> request("print", id));
            s.tooltip(mx, my, LEFT_X + W - 8 - PRINT_W, ACT_Y, PRINT_W, ACT_H, Component.translatable("contract.pioneers.action.print.tip"));
        }
    }
}
