package fr.pioneers.society.economy.client;

import fr.pioneers.society.rp.client.NotebookScreen;
import fr.pioneers.society.rp.client.ParchmentScreen;
import fr.pioneers.society.rp.client.RpGui;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.society.economy.document.DocStatus;
import fr.pioneers.society.economy.document.DocType;
import fr.pioneers.society.economy.document.Document;
import fr.pioneers.society.economy.document.DocumentRules;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.0.5-beta (lot 26, retour 14 de Guillaume : « Renomme la section contrat en "Documents" ; produire une multitude de documents ; le joueur
 * choisit le type, les montants en jeu, les echeances des montants ») : onglet DOCUMENTS du Carnet (identifiant interne inchange
 * {@code contracts} : meme place, meme icone, meme touche). Quatre vues :
 * <ul>
 *     <li>LISTE : filtres par statut (A traiter, En cours, Brouillons, Clos, Tous) et par type (pastilles a icone), lignes « Facture n° 12 ·
 *     objet », partie, prochaine echeance, reste du ; « + Nouveau » ;</li>
 *     <li>FORMULAIRE : type (9 pastilles), deux pages : « Lettre » (destinataire joueur au selecteur ou clan, objet, corps multiligne, signature)
 *     et « Montants » (lignes libelle / montant, echeancier jour / montant, « Repartir », total verifie) ; « Enregistrer » / « Envoyer » ; un
 *     contrat peut aussi etre fait AU TEMPS REEL (formulaire des contrats d'avant, {@link ContractsTab}) ;</li>
 *     <li>DETAIL : la feuille mise en forme ({@link DocumentPaper}) et les actions selon le cote et le statut (envoyer, modifier, supprimer,
 *     reprendre, signer et accepter, refuser, payer l'echeance, annuler / faire grace, imprimer) ;</li>
 *     <li>contrat au temps reel : le detail et le formulaire d'avant ({@link ContractsTab}), sans changement.</li>
 * </ul>
 * Le serveur fait tout ({@code economy/document/DocumentService}) : l'onglet n'envoie que des demandes et affiche la vue recue (poussee aussi
 * quand l'autre partie agit ou qu'une echeance passe). Maquette : {@code tools/gc/rp/documents_preview.py} (memes constantes).
 */
public final class DocumentsTab implements NotebookScreen.Tab {

    public static final String ID = "contracts";

    public enum Mode {
        LIST, EDIT, DETAIL, LEGACY
    }

    // ------------------------------------------------------------------ mise en page (coordonnees de la page ; maquette alignee)
    public static final int X = 10, W = ParchmentScreen.PAGE_W - 20, TOP_Y = 24, CHIP_H = 13, CHIP_GAP = 3, NEW_X = 312, NEW_W = 62;
    public static final int TYPE_Y = 40, TYPE_W = 16, TYPE_ALL_W = 34, LIST_Y = 58, ROW_H = 24, LIST_H = 160, FOOT_Y = 224;
    public static final int LEFT_X = 14, LETTER_W = 184, RIGHT_X = 206, RCOL_W = 164, BACK_X = 314, BACK_W = 60, FIELD_H = 14;
    public static final int PAGE_CHIP_X = 222, PAGE_CHIP_W = 74, SEP_Y = 56, F_KIND = 62, KIND_W = 38, KIND_LABEL_W = 40, SCHED_LABEL_W = 62, ADD_DUE_W = 54, F_TO = 76, F_SUBJECT = 96, SUBJECT_LABEL_W = 44,
          F_BODY = 112, BODY_H = 78, F_SIGN = 195;
    public static final int ROWS_Y = 74, ROW_STEP = 14, BOX_H = 12, LABEL_BOX_W = 118, AMOUNT_BOX_W = 50, DAYS_BOX_W = 26, DEL_W = 10,
          TOTALS_Y = 188, MSG_Y = 202, ACT_Y = 214, ACT_H = 16, SAVE_W = 128;
    public static final int PAPER_X = 14, PAPER_Y = 42, PAPER_W = 262, PAPER_H = 170, SIDE_X = 284, SIDE_W = 86, SIDE_Y = 44, ACTION_Y = 100,
          ACTION_STEP = 17, ACTION_H = 14;
    public static final int LIST_SCROLL = 5, PAPER_SCROLL = 6;
    public static final long CONFIRM_MS = 4000;

    /** Filtres de statut, dans l'ordre des pastilles. */
    public static final String[] FILTERS = {"todo", "open", "drafts", "closed", "all"};

    // ------------------------------------------------------------------ etat (garde entre les ouvertures du Carnet)
    @Nullable
    private static CompoundTag view;
    @Nullable
    private static CompoundTag doc;
    private static long receivedAt, askedAt;
    private static Mode mode = Mode.LIST;
    private static String filter = "", typeFilter = "";
    private static int selected, editing;
    @Nullable
    private static String message, confirm;
    private static long messageAt, confirmAt;
    /** Formulaire. */
    private static DocType fType = DocType.INVOICE;
    private static boolean fClan, amountsPage;
    private static String fTo = "", fSubject = "", fBody = "";
    private static final List<String[]> fLines = new ArrayList<>(), fDues = new ArrayList<>();
    private static long prevTotal;
    private static final List<EditBox> dueAmountBoxes = new ArrayList<>();
    @Nullable
    private static fr.pioneers.client.gui.LetterEditor editor;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return PioneerApi.hasFeature("economy");
    }

    // ------------------------------------------------------------------ reseau
    public static void onServer(CompoundTag t) {
        boolean push = t.getBoolean("push");
        view = t;
        receivedAt = Util.getMillis();
        if (t.contains("doc")) {
            doc = t.getCompound("doc");
        }
        if (!push && t.contains("msg")) {
            message = t.getString("msg");
            messageAt = receivedAt;
            int focus = t.getInt("focus");
            if ("ok_delete".equals(message)) {
                mode = Mode.LIST;
                doc = null;
                reopen();
            } else if (message.startsWith("ok_") && focus > 0) {
                selected = focus;
                mode = Mode.DETAIL;
                reopen();
            } else if (mode == Mode.EDIT && editing == 0 && focus > 0) {
                editing = focus; //brouillon cree mais pas envoye : la demande suivante le modifie, sans en creer un autre
            }
        }
        //document ouvert pousse : on redemande sa feuille a jour
        if (push && mode == Mode.DETAIL && selected > 0) {
            request("open", selected);
        }
    }

    /** Un contrat au temps reel a change (vue des contrats poussee) : la liste des documents se rafraichit (au plus toutes les 2 s). */
    public static void contractsChanged() {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof NotebookScreen && Util.getMillis() - askedAt > 2000) {
            request("list", 0);
        }
    }

    static void request(String op, int id) {
        CompoundTag t = new CompoundTag();
        t.putString("op", op);
        t.putInt("id", id);
        askedAt = Util.getMillis();
        RpNet.toServer(RpActions.DOCUMENT_REQUEST, t);
    }

    private static void reopen() {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof NotebookScreen s) {
            s.open(ID);
        }
    }

    private static void go(NotebookScreen s, Mode m) {
        mode = m;
        confirm = null;
        message = null;
        s.open(ID);
    }

    // ------------------------------------------------------------------ outils
    static long today() {
        return view == null ? 0 : view.getLong("today");
    }

    static List<CompoundTag> docs() {
        List<CompoundTag> out = new ArrayList<>();
        if (view != null) {
            for (Tag t : view.getList("list", Tag.TAG_COMPOUND)) {
                out.add((CompoundTag) t);
            }
        }
        return out;
    }

    @Nullable
    static CompoundTag find(int id) {
        for (CompoundTag c : docs()) {
            if (c.getInt("id") == id) {
                return c;
            }
        }
        return null;
    }

    /** Groupe de statut d'une ligne : a traiter (signer ou payer), en cours, brouillon, clos. */
    public static String groupOf(CompoundTag c) {
        String st = c.getString("status");
        if ("draft".equals(st)) {
            return "drafts";
        }
        if (DocStatus.of(st).closed()) {
            return "closed";
        }
        Component todo = DocumentPaper.todo(c);
        return todo.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getKey().startsWith("document.pioneers.todo.")
              ? "todo" : "open";
    }

    static boolean matches(CompoundTag c, String f) {
        String g = groupOf(c);
        boolean st = "all".equals(f) || f.equals(g) || "open".equals(f) && "todo".equals(g);
        return st && (typeFilter.isEmpty() || typeFilter.equals(c.getString("type")));
    }

    static Component title(CompoundTag c) {
        return Component.translatable("document.pioneers.numbered", DocumentPaper.typeName(c.getString("type")), c.getInt("id"));
    }

    static Component result(String msg) {
        return Component.translatable(msg.startsWith("ok_") ? "document.pioneers.result." + msg : "document.pioneers.error." + msg);
    }

    // ------------------------------------------------------------------ ouverture
    @Override
    public void opened(NotebookScreen s) {
        if (view == null || Util.getMillis() - receivedAt > 3000) {
            request("list", 0);
        }
        if (mode == Mode.EDIT) {
            openForm(s);
        } else if (mode == Mode.LEGACY) {
            ContractsTab.embedded(s);
        } else if (mode == Mode.DETAIL && (doc == null || doc.getInt("id") != selected)) {
            request("open", selected);
        }
    }

    @Override
    public void draw(NotebookScreen s, GuiGraphics g, int mx, int my, float pt) {
        if (mode == Mode.LEGACY) {
            if (ContractsTab.mode() == ContractsTab.Mode.LIST) {
                mode = Mode.LIST;
                request("list", 0);
            } else {
                ContractsTab.drawEmbedded(s, g, mx, my, pt);
                return;
            }
        }
        if (view == null) {
            s.wrap(g, Component.translatable("document.pioneers.loading"), X + 4, LIST_Y + 4, W - 8, ParchmentScreen.DIM, 1F, 2);
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
        List<CompoundTag> all = docs();
        int[] counts = new int[FILTERS.length];
        for (CompoundTag c : all) {
            for (int i = 0; i < FILTERS.length; i++) {
                if (matches(c, FILTERS[i])) {
                    counts[i]++;
                }
            }
        }
        if (filter.isEmpty()) {
            filter = counts[0] > 0 ? "todo" : counts[1] > 0 ? "open" : "all";
        }
        Component[] labels = new Component[FILTERS.length];
        for (int i = 0; i < FILTERS.length; i++) {
            labels[i] = Component.translatable("document.pioneers.filter." + FILTERS[i], counts[i]);
        }
        int[] cw = ContractsTab.chipWidths(s.font(), labels);
        int x = X;
        for (int i = 0; i < FILTERS.length; i++) {
            String f = FILTERS[i];
            s.chip(g, mx, my, x, TOP_Y, cw[i], CHIP_H, labels[i], f.equals(filter), true, () -> {
                filter = f;
                s.scrollTo(LIST_SCROLL, 0);
            });
            x += cw[i] + CHIP_GAP;
        }
        s.button(g, mx, my, NEW_X, TOP_Y, NEW_W, CHIP_H, Component.translatable("document.pioneers.new"), true, () -> {
            newForm(fType);
            go(s, Mode.EDIT);
        });
        //filtre par type : « Tous » puis une pastille a icone par type (nom en infobulle)
        s.chip(g, mx, my, X, TYPE_Y, TYPE_ALL_W, CHIP_H, Component.translatable("document.pioneers.type_all"), typeFilter.isEmpty(), true, 0.75F,
              () -> typeFilter = "");
        int tx = X + TYPE_ALL_W + CHIP_GAP;
        for (DocType t : DocType.values()) {
            String k = t.key();
            s.chip(g, mx, my, tx, TYPE_Y, TYPE_W + 2, CHIP_H, Component.empty(), k.equals(typeFilter), true,
                  () -> typeFilter = k.equals(typeFilter) ? "" : k);
            s.icon(g, DocumentPaper.icon(k), tx + 3, TYPE_Y + 1, 12);
            s.tooltip(mx, my, tx, TYPE_Y, TYPE_W + 2, CHIP_H, DocumentPaper.typeName(k), Component.translatable("document.pioneers.type_tip." + k));
            tx += TYPE_W + 2 + 2;
        }
        List<CompoundTag> shown = new ArrayList<>();
        for (CompoundTag c : all) {
            if (matches(c, filter)) {
                shown.add(c);
            }
        }
        s.fitRight(g, Component.translatable("document.pioneers.count", shown.size()), X + W - 4, TYPE_Y + 3, X + W - tx - 8, ParchmentScreen.DIM, 0.75F);
        s.scrollArea(LIST_SCROLL, X, LIST_Y, W, LIST_H, shown.size() * ROW_H);
        s.beginClip(g, X, LIST_Y, W, LIST_H);
        int y = LIST_Y - s.scrollOf(LIST_SCROLL);
        for (CompoundTag c : shown) {
            if (y + ROW_H > LIST_Y && y < LIST_Y + LIST_H) {
                row(s, g, mx, my, c, y);
            }
            y += ROW_H;
        }
        s.endClip(g);
        if (shown.isEmpty()) {
            s.wrap(g, Component.translatable("document.pioneers.empty." + filter), X + 4, LIST_Y + 6, W - 8, ParchmentScreen.DIM, 1F, 3);
        }
        footer(s, g);
    }

    private static void row(NotebookScreen s, GuiGraphics g, int mx, int my, CompoundTag c, int y) {
        int id = c.getInt("id");
        boolean on = s.over(mx, my, X, y, W, ROW_H - 1) && my >= LIST_Y && my < LIST_Y + LIST_H;
        if (on) {
            g.fill(X, y, X + W, y + ROW_H - 1, 0x18243660);
        }
        s.icon(g, DocumentPaper.icon(c.getString("type")), X + 3, y + 5, 12);
        Component state = DocumentPaper.todo(c);
        String st = c.getString("status");
        boolean todo = "todo".equals(groupOf(c));
        int sw = s.fitRight(g, state, X + W - 4, y + 3, 92, todo ? ParchmentScreen.MINUS : DocumentPaper.statusColor(st), 1F);
        Component head = title(c).copy().append("  ").append(Component.literal(c.getString("sub")));
        s.fit(g, head, X + 19, y + 3, W - 23 - sw - 8, ParchmentScreen.INK);
        boolean mine = c.getBoolean("mine");
        Component who = Component.translatable(mine ? "document.pioneers.row.to" : "document.pioneers.row.from",
              mine ? (c.getString("tk").equals("clan") ? Component.translatable("document.pioneers.to_clan", c.getString("to"))
                    : Component.literal(c.getString("to"))) : Component.literal(c.getString("au")));
        Component line = who;
        if (c.getInt("timed") > 0) {
            line = Component.translatable("document.pioneers.row.line", who, Component.translatable("document.pioneers.realtime"));
        } else if (c.getLong("nd") >= 0 && !DocStatus.of(st).closed()) {
            line = Component.translatable("document.pioneers.row.line", who, Component.translatable("document.pioneers.row.next",
                  DocumentPaper.day(c.getLong("nd"), today()), ContractsTab.group(c.getLong("na"))));
        }
        long owed = c.getLong("owed");
        Component money = owed > 0 && !DocStatus.of(st).closed() ? Component.translatable("document.pioneers.row.owed", ContractsTab.group(owed))
              : c.getLong("total") > 0 ? ContractsTab.tokens(c.getLong("total")) : Component.empty();
        int aw = s.fitRight(g, money, X + W - 4, y + 13, 100, owed > 0 && "overdue".equals(st) ? ParchmentScreen.MINUS : ParchmentScreen.TEXT, 0.75F);
        s.fit(g, line, X + 19, y + 13, W - 23 - aw - 8, ParchmentScreen.DIM, 0.75F);
        s.separator(g, X, y + ROW_H - 1, W);
        if (on) {
            s.click(X, Math.max(y, LIST_Y), W, ROW_H - 1, () -> {
                if (c.getInt("timed") > 0) {
                    ContractsTab.showDetail(c.getInt("timed"));
                    go(s, Mode.LEGACY);
                } else {
                    selected = id;
                    doc = null;
                    request("open", id);
                    go(s, Mode.DETAIL);
                }
            });
        }
    }

    private static void footer(NotebookScreen s, GuiGraphics g) {
        Component bal = Component.translatable("contract.pioneers.balance", ContractsTab.group(view == null ? 0 : view.getLong("balance")));
        int bw = s.fitRight(g, bal, X + W - 4, FOOT_Y, 120, ParchmentScreen.TEXT, 0.75F);
        if (message != null && Util.getMillis() - messageAt < 8000) {
            s.fit(g, result(message), X + 4, FOOT_Y, W - 16 - bw, message.startsWith("ok_") ? ParchmentScreen.PLUS : ParchmentScreen.MINUS, 0.75F);
        } else {
            s.fit(g, Component.translatable("document.pioneers.today_is", today()), X + 4, FOOT_Y, W - 16 - bw, ParchmentScreen.DIM, 0.75F);
        }
    }

    // ------------------------------------------------------------------ FORMULAIRE
    static void newForm(DocType type) {
        editing = 0;
        fType = type;
        fClan = false;
        fTo = "";
        fSubject = "";
        fBody = "";
        fLines.clear();
        fDues.clear();
        amountsPage = false;
        if (type.amounts) {
            fLines.add(new String[]{"", ""});
        }
        prevTotal = 0;
    }

    static void loadForm(CompoundTag t) {
        Document d = Document.load(t);
        editing = d.id;
        fType = d.type;
        fClan = d.recipient.clan();
        fTo = d.recipientAccount.isEmpty() ? d.recipient.name() : d.recipientAccount;
        fSubject = d.subject;
        fBody = d.body;
        fLines.clear();
        for (Document.Line l : d.lines) {
            fLines.add(new String[]{l.label(), Long.toString(l.amount())});
        }
        fDues.clear();
        for (Document.Due due : d.dues) {
            fDues.add(new String[]{Long.toString(Math.max(0, due.day - today())), Long.toString(due.amount)});
        }
        prevTotal = d.total();
        amountsPage = false;
    }

    static long num(String v) {
        try {
            return v == null || v.isEmpty() ? 0 : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static long total() {
        long t = 0;
        for (String[] l : fLines) {
            t += Math.max(0, num(l[1]));
        }
        return t;
    }

    static long scheduled() {
        long t = 0;
        for (String[] d : fDues) {
            t += Math.max(0, num(d[1]));
        }
        return t;
    }

    private static void openForm(NotebookScreen s) {
        dueAmountBoxes.clear();
        editor = null;
        if (!amountsPage) {
            if (fClan) {
                s.tabWidget(box(s, LEFT_X, F_TO - 3, LETTER_W, DocumentRules.NAME_MAX, fTo, "document.pioneers.field.clan", v -> fTo = v, false, FIELD_H));
            } else {
                fr.pioneers.client.gui.PlayerPicker to = new fr.pioneers.client.gui.PlayerPicker(s.font(), LEFT_X, F_TO - 3, LETTER_W, FIELD_H,
                      Component.translatable("document.pioneers.field.player"), "contract", fr.pioneers.client.gui.PlayerPicker.Style.PAPER);
                to.setValue(fTo);
                to.setResponder(v -> fTo = v);
                s.tabWidget(to);
            }
            s.tabWidget(box(s, LEFT_X + SUBJECT_LABEL_W, F_SUBJECT - 3, LETTER_W - SUBJECT_LABEL_W, DocumentRules.SUBJECT_MAX, fSubject,
                  "document.pioneers.field.subject", v -> fSubject = v, false, FIELD_H));
            editor = new fr.pioneers.client.gui.LetterEditor(s.font(), LEFT_X, F_BODY, LETTER_W, BODY_H, Component.translatable("document.pioneers.field.body"),
                  DocumentRules.BODY_MAX, DocumentRules.BODY_LINES);
            editor.setValue(fBody);
            editor.setValueListener(v -> fBody = v);
            s.tabWidget(editor);
            return;
        }
        for (int i = 0; i < fLines.size(); i++) {
            String[] l = fLines.get(i);
            int y = ROWS_Y + i * ROW_STEP;
            s.tabWidget(box(s, LEFT_X, y, LABEL_BOX_W, DocumentRules.LABEL_MAX, l[0], "document.pioneers.field.label", v -> l[0] = v, false, BOX_H));
            s.tabWidget(box(s, LEFT_X + LABEL_BOX_W + 4, y, AMOUNT_BOX_W, 10, l[1], "document.pioneers.field.amount", v -> l[1] = v, true, BOX_H));
        }
        for (int i = 0; i < fDues.size(); i++) {
            String[] d = fDues.get(i);
            int y = ROWS_Y + i * ROW_STEP;
            s.tabWidget(box(s, RIGHT_X, y, DAYS_BOX_W, 4, d[0], null, v -> d[0] = v, true, BOX_H));
            dueAmountBoxes.add(s.tabWidget(box(s, RIGHT_X + 90, y, AMOUNT_BOX_W, 10, d[1], "document.pioneers.field.amount", v -> d[1] = v, true, BOX_H)));
        }
    }

    private static EditBox box(NotebookScreen s, int x, int y, int w, int max, String value, @Nullable String hint, java.util.function.Consumer<String> sink,
          boolean digits, int h) {
        EditBox b = new EditBox(s.font(), x, y, w, h, hint == null ? Component.empty() : Component.translatable(hint));
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

    private static String myName() {
        return view != null && !view.getString("myName").isEmpty() ? view.getString("myName") : fr.pioneers.society.rp.client.RpClient.profile().name;
    }

    private static void drawForm(NotebookScreen s, GuiGraphics g, int mx, int my) {
        int dim = ParchmentScreen.DIM;
        Component head = editing > 0 ? Component.translatable("document.pioneers.form.draft", DocumentPaper.typeName(fType.key()), editing)
              : Component.translatable("document.pioneers.form.new", DocumentPaper.typeName(fType.key()));
        s.icon(g, DocumentPaper.icon(fType.key()), LEFT_X, TOP_Y + 1, 12);
        s.fit(g, head, LEFT_X + 16, TOP_Y + 3, BACK_X - LEFT_X - 24, ParchmentScreen.INK);
        s.button(g, mx, my, BACK_X, TOP_Y, BACK_W, FIELD_H, Component.translatable("contract.pioneers.back"), true,
              () -> go(s, editing > 0 ? Mode.DETAIL : Mode.LIST));
        //type : une pastille a icone par type (un brouillon change de type tant qu'il n'est pas envoye)
        int tx = LEFT_X;
        for (DocType t : DocType.values()) {
            s.chip(g, mx, my, tx, TYPE_Y, TYPE_W + 2, CHIP_H, Component.empty(), t == fType, true, () -> setType(s, t));
            s.icon(g, DocumentPaper.icon(t.key()), tx + 3, TYPE_Y + 1, 12);
            s.tooltip(mx, my, tx, TYPE_Y, TYPE_W + 2, CHIP_H, DocumentPaper.typeName(t.key()), Component.translatable("document.pioneers.type_tip." + t.key()));
            tx += TYPE_W + 4;
        }
        //pages : Lettre / Montants
        s.chip(g, mx, my, PAGE_CHIP_X, TYPE_Y, PAGE_CHIP_W, CHIP_H, Component.translatable("document.pioneers.page.letter"), !amountsPage, true, 0.75F,
              () -> page(s, false));
        s.chip(g, mx, my, PAGE_CHIP_X + PAGE_CHIP_W + 3, TYPE_Y, PAGE_CHIP_W, CHIP_H,
              Component.translatable("document.pioneers.page.amounts", fLines.size(), fDues.size()), amountsPage, fType.amounts, 0.75F, () -> page(s, true));
        s.separator(g, LEFT_X, SEP_Y, W - 8);
        if (amountsPage && fType.amounts) {
            drawAmounts(s, g, mx, my);
        } else {
            drawLetter(s, g, mx, my, dim);
        }
        if (message != null && !message.startsWith("ok_")) {
            s.fit(g, result(message), LEFT_X, MSG_Y, W - 8, ParchmentScreen.MINUS, 0.75F);
        }
        boolean ready = !fTo.isBlank() && !fSubject.isBlank();
        s.button(g, mx, my, LEFT_X, ACT_Y, SAVE_W, ACT_H, Component.translatable("document.pioneers.action.save"), ready, () -> submit(false));
        s.button(g, mx, my, LEFT_X + SAVE_W + 4, ACT_Y, SAVE_W, ACT_H, Component.translatable("document.pioneers.action.send"), ready, () -> submit(true));
    }

    private static void drawLetter(NotebookScreen s, GuiGraphics g, int mx, int my, int dim) {
        //destinataire : joueur ou clan
        s.icon(g, RpGui.stat(fClan ? "clan" : "player"), LEFT_X, F_KIND, 8);
        s.fit(g, Component.translatable("document.pioneers.label.to"), LEFT_X + 11, F_KIND + 1, KIND_LABEL_W - 13, dim, 0.75F);
        int kx = LEFT_X + KIND_LABEL_W;
        for (int i = 0; i < 2; i++) {
            boolean clan = i == 1;
            s.chip(g, mx, my, kx, F_KIND - 2, KIND_W, 12, Component.translatable(clan ? "document.pioneers.kind.clan" : "document.pioneers.kind.player"),
                  fClan == clan, true, 0.75F, () -> {
                      if (fClan != clan) {
                          fClan = clan;
                          fTo = "";
                          s.open(ID);
                      }
                  });
            kx += KIND_W + 3;
        }
        if (fClan) {
            ListTag clans = view == null ? new ListTag() : view.getList("clans", Tag.TAG_COMPOUND);
            if (!clans.isEmpty()) {
                s.chip(g, mx, my, kx, F_KIND - 2, LEFT_X + LETTER_W - kx, 12, Component.translatable("document.pioneers.kind.next_clan"), false, true, 0.75F,
                      () -> {
                          int at = 0;
                          for (int i = 0; i < clans.size(); i++) {
                              if (clans.getCompound(i).getString("n").equalsIgnoreCase(fTo)) {
                                  at = i + 1;
                              }
                          }
                          fTo = clans.getCompound(at % clans.size()).getString("n");
                          s.open(ID);
                      });
            }
        }
        s.icon(g, RpGui.stat("letter"), LEFT_X, F_SUBJECT, 8);
        s.fit(g, Component.translatable("document.pioneers.label.subject"), LEFT_X + 11, F_SUBJECT, SUBJECT_LABEL_W - 13, dim, 0.75F);
        int len = editor == null ? fBody.length() : editor.length(), lines = editor == null ? 1 : editor.lines();
        Component count = Component.translatable("contract.pioneers.letter.count", ContractsTab.group(len), ContractsTab.group(DocumentRules.BODY_MAX));
        int cw = s.fitRight(g, count, LEFT_X + LETTER_W, F_SIGN, 100, len >= DocumentRules.BODY_MAX || lines >= DocumentRules.BODY_LINES
              ? ParchmentScreen.MINUS : dim, 0.75F);
        s.fit(g, Component.literal("— " + myName()).withStyle(st -> st.withItalic(true)), LEFT_X, F_SIGN, LETTER_W - cw - 6, ParchmentScreen.INK_LIGHT, 0.75F);
        //colonne de droite : le type, ses regles, le resume des montants
        int y = F_KIND - 2;
        s.fit(g, DocumentPaper.typeName(fType.key()), RIGHT_X, y, RCOL_W, ParchmentScreen.INK, 1F);
        y += 11;
        y += s.wrap(g, Component.translatable("document.pioneers.type_tip." + fType.key()), RIGHT_X, y, RCOL_W, ParchmentScreen.TEXT, 0.75F, 6) + 4;
        y = rule(s, g, y, "document.pioneers.rule.sign." + (fType.signed ? "yes" : "no"));
        y = rule(s, g, y, "document.pioneers.rule.payer." + fType.payer.name().toLowerCase(java.util.Locale.ROOT));
        if (fType.payable()) {
            y = rule(s, g, y, "document.pioneers.rule.debit." + (fType.autoDebit ? "yes" : "no"));
        }
        if (fType.amounts) {
            y += 3;
            s.icon(g, RpGui.stat("shining"), RIGHT_X, y, 8);
            s.fit(g, Component.translatable("document.pioneers.form.summary", ContractsTab.tokens(total()), fDues.size()), RIGHT_X + 11, y + 1, RCOL_W - 11,
                  ParchmentScreen.INK, 0.75F);
        }
        if (fType == DocType.CONTRACT && editing == 0) {
            s.button(g, mx, my, RIGHT_X, F_SIGN - 15, RCOL_W, 13, Component.translatable("document.pioneers.realtime_new"), true, () -> {
                ContractsTab.startNew();
                go(s, Mode.LEGACY);
            });
            s.tooltip(mx, my, RIGHT_X, F_SIGN - 15, RCOL_W, 13, Component.translatable("document.pioneers.realtime_new.tip"));
        }
    }

    private static int rule(NotebookScreen s, GuiGraphics g, int y, String key) {
        return y + s.wrap(g, Component.literal("• ").append(Component.translatable(key)), RIGHT_X, y, RCOL_W, ParchmentScreen.DIM, 0.75F, 2) + 1;
    }

    private static void drawAmounts(NotebookScreen s, GuiGraphics g, int mx, int my) {
        int dim = ParchmentScreen.DIM;
        //lignes de montants
        s.fit(g, Component.translatable("document.pioneers.label.amounts"), LEFT_X, F_KIND, 90, ParchmentScreen.INK, 1F);
        if (fLines.size() < DocumentRules.LINES_MAX) {
            s.chip(g, mx, my, LEFT_X + LETTER_W - 70, F_KIND - 2, 70, 12, Component.translatable("document.pioneers.add_line"), false, true, 0.75F, () -> {
                fLines.add(new String[]{"", ""});
                s.open(ID);
            });
        }
        for (int i = 0; i < fLines.size(); i++) {
            int k = i, y = ROWS_Y + i * ROW_STEP;
            s.chip(g, mx, my, LEFT_X + LABEL_BOX_W + 4 + AMOUNT_BOX_W + 3, y, DEL_W, BOX_H, Component.literal("×"), false, true, () -> {
                fLines.remove(k);
                s.open(ID);
            });
        }
        //echeancier
        if (fType.schedule) {
            s.fit(g, Component.translatable("document.pioneers.label.schedule"), RIGHT_X, F_KIND, SCHED_LABEL_W - 2, ParchmentScreen.INK, 1F);
            int cx = RIGHT_X + SCHED_LABEL_W;
            if (fDues.size() < DocumentRules.DUES_MAX) {
                s.chip(g, mx, my, cx, F_KIND - 2, ADD_DUE_W, 12, Component.translatable("document.pioneers.add_due"), false, true, 0.75F, () -> {
                    long last = fDues.isEmpty() ? DocumentRules.DEFAULT_DUE_IN - 7 : num(fDues.get(fDues.size() - 1)[0]);
                    fDues.add(new String[]{Long.toString(Math.max(0, last) + 7), ""});
                    s.open(ID);
                });
            }
            s.chip(g, mx, my, cx + ADD_DUE_W + 3, F_KIND - 2, RIGHT_X + RCOL_W - cx - ADD_DUE_W - 3, 12, Component.translatable("document.pioneers.split"), false, total() > 0,
                  0.75F, () -> {
                      int n = Math.max(1, fDues.size());
                      long first = fDues.isEmpty() ? DocumentRules.DEFAULT_DUE_IN : Math.max(0, num(fDues.get(0)[0]));
                      fDues.clear();
                      for (Document.Due d : DocumentRules.split(total(), n, first, 7)) {
                          fDues.add(new String[]{Long.toString(d.day), Long.toString(d.amount)});
                      }
                      prevTotal = total();
                      s.open(ID);
                  });
            s.tooltip(mx, my, cx + ADD_DUE_W + 3, F_KIND - 2, RIGHT_X + RCOL_W - cx - ADD_DUE_W - 3, 12, Component.translatable("document.pioneers.split.tip"));
            for (int i = 0; i < fDues.size(); i++) {
                int k = i, y = ROWS_Y + i * ROW_STEP;
                long in = Math.max(0, num(fDues.get(i)[0]));
                s.fit(g, Component.translatable("document.pioneers.due_in", today() + in), RIGHT_X + DAYS_BOX_W + 3, y + 3, 90 - DAYS_BOX_W - 5, dim, 0.75F);
                s.chip(g, mx, my, RIGHT_X + 90 + AMOUNT_BOX_W + 3, y, DEL_W, BOX_H, Component.literal("×"), false, true, () -> {
                    fDues.remove(k);
                    s.open(ID);
                });
            }
            if (fDues.isEmpty()) {
                s.wrap(g, Component.translatable("document.pioneers.schedule_empty"), RIGHT_X, ROWS_Y, RCOL_W, dim, 0.75F, 4);
            }
            //une seule echeance qui valait le total suit le total
            long tot = total();
            if (fDues.size() == 1 && tot != prevTotal && (num(fDues.get(0)[1]) == prevTotal || fDues.get(0)[1].isEmpty())) {
                fDues.get(0)[1] = Long.toString(tot);
                if (dueAmountBoxes.size() == 1) {
                    dueAmountBoxes.get(0).setValue(fDues.get(0)[1]);
                }
            }
            prevTotal = tot;
        } else {
            s.wrap(g, Component.translatable("document.pioneers.no_schedule." + fType.key()), RIGHT_X, F_KIND, RCOL_W, dim, 0.75F, 5);
        }
        //total verifie
        long tot = total(), sch = scheduled();
        s.fit(g, Component.translatable("document.pioneers.form.total", ContractsTab.tokens(tot)), LEFT_X, TOTALS_Y, LETTER_W, ParchmentScreen.INK, 1F);
        if (fType.schedule && !fDues.isEmpty()) {
            boolean ok = sch == tot;
            s.fit(g, Component.translatable(ok ? "document.pioneers.form.schedule_ok" : "document.pioneers.form.schedule_bad", ContractsTab.tokens(sch)),
                  RIGHT_X, TOTALS_Y + 1, RCOL_W, ok ? ParchmentScreen.PLUS : ParchmentScreen.MINUS, 0.75F);
        }
    }

    private static void page(NotebookScreen s, boolean amounts) {
        if (amountsPage != amounts && (!amounts || fType.amounts)) {
            amountsPage = amounts;
            s.open(ID);
        }
    }

    private static void setType(NotebookScreen s, DocType t) {
        if (t == fType) {
            return;
        }
        fType = t;
        if (!t.amounts) {
            fLines.clear();
            amountsPage = false;
        } else if (fLines.isEmpty()) {
            fLines.add(new String[]{"", ""});
        }
        if (!t.schedule) {
            fDues.clear();
        }
        message = null;
        s.open(ID);
    }

    private static void submit(boolean send) {
        CompoundTag t = new CompoundTag();
        t.putString("op", "save");
        t.putInt("id", editing);
        t.putString("type", fType.key());
        t.putString("toKind", fClan ? "clan" : "player");
        t.putString("to", fTo.trim());
        t.putString("subject", fSubject.trim());
        t.putString("body", fBody);
        ListTag ls = new ListTag();
        for (String[] l : fLines) {
            if (l[0].isBlank() && l[1].isBlank()) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putString("l", l[0].trim());
            c.putLong("a", num(l[1]));
            ls.add(c);
        }
        t.put("lines", ls);
        ListTag ds = new ListTag();
        for (String[] d : fDues) {
            CompoundTag c = new CompoundTag();
            c.putLong("a", num(d[1]));
            c.putLong("d", today() + Math.max(0, num(d[0])));
            ds.add(c);
        }
        t.put("dues", ds);
        t.putBoolean("send", send);
        message = null;
        askedAt = Util.getMillis();
        RpNet.toServer(RpActions.DOCUMENT_REQUEST, t);
    }

    // ------------------------------------------------------------------ DETAIL
    private static void drawDetail(NotebookScreen s, GuiGraphics g, int mx, int my, @Nullable CompoundTag c) {
        if (c == null) {
            return;
        }
        s.icon(g, DocumentPaper.icon(c.getString("type")), LEFT_X, TOP_Y + 1, 12);
        int sw = s.fitRight(g, DocumentPaper.statusName(c.getString("status")), BACK_X - 8, TOP_Y + 3, 90, DocumentPaper.statusColor(c.getString("status")), 1F);
        s.fit(g, title(c), LEFT_X + 16, TOP_Y + 3, BACK_X - LEFT_X - 30 - sw, ParchmentScreen.INK);
        s.button(g, mx, my, BACK_X, TOP_Y, BACK_W, FIELD_H, Component.translatable("contract.pioneers.back"), true, () -> go(s, Mode.LIST));
        if (doc == null || doc.getInt("id") != c.getInt("id")) {
            s.wrap(g, Component.translatable("document.pioneers.loading"), PAPER_X, PAPER_Y + 4, PAPER_W, ParchmentScreen.DIM, 1F, 2);
        } else {
            DocumentPaper.draw(s, g, mx, my, doc, PAPER_X, PAPER_Y, PAPER_W, PAPER_H, PAPER_SCROLL, today());
        }
        //colonne des chiffres et des actions
        int y = SIDE_Y;
        if (c.getLong("total") > 0) {
            y = fact(s, g, y, "document.pioneers.label.total", ContractsTab.tokens(c.getLong("total")), ParchmentScreen.INK);
            if (c.getInt("dues") > 0) {
                y = fact(s, g, y, "document.pioneers.label.paid", ContractsTab.tokens(c.getLong("paid")), ParchmentScreen.PLUS);
                y = fact(s, g, y, "document.pioneers.label.owed", ContractsTab.tokens(c.getLong("owed")),
                      c.getLong("owed") > 0 ? ParchmentScreen.MINUS : ParchmentScreen.DIM);
            }
        }
        if (c.getLong("nd") >= 0 && !DocStatus.of(c.getString("status")).closed()) {
            s.fit(g, Component.translatable("document.pioneers.label.next"), SIDE_X, y, SIDE_W, ParchmentScreen.DIM, 0.75F);
            s.fit(g, DocumentPaper.day(c.getLong("nd"), today()), SIDE_X, y + 8, SIDE_W, ParchmentScreen.TEXT, 0.75F);
            y += 18;
        }
        int ay = Math.max(ACTION_Y, y + 4);
        for (Object[] a : actions(c)) {
            String op = (String) a[0];
            Component label = (Component) a[1];
            boolean irreversible = (Boolean) a[2];
            boolean asking = op.equals(confirm) && Util.getMillis() - confirmAt < CONFIRM_MS;
            s.button(g, mx, my, SIDE_X, ay, SIDE_W, ACTION_H, asking ? Component.translatable("document.pioneers.sure") : label, true, () -> {
                if (irreversible && !asking) {
                    confirm = op;
                    confirmAt = Util.getMillis();
                    return;
                }
                confirm = null;
                if ("edit".equals(op)) {
                    if (doc != null) {
                        loadForm(doc);
                        go(s, Mode.EDIT);
                    }
                    return;
                }
                request(op, c.getInt("id"));
            });
            ay += ACTION_STEP;
        }
        if (message != null && Util.getMillis() - messageAt < 8000) {
            s.wrap(g, result(message), SIDE_X, Math.min(ay + 2, FOOT_Y - 16), SIDE_W, message.startsWith("ok_") ? ParchmentScreen.PLUS : ParchmentScreen.MINUS,
                  0.75F, 3);
        }
        s.fit(g, Component.translatable("document.pioneers.today_is", today()), PAPER_X, FOOT_Y, PAPER_W, ParchmentScreen.DIM, 0.75F);
    }

    private static int fact(NotebookScreen s, GuiGraphics g, int y, String key, Component value, int color) {
        int lw = s.fit(g, Component.translatable(key), SIDE_X, y, 40, ParchmentScreen.DIM, 0.75F);
        s.fitRight(g, value, SIDE_X + SIDE_W, y, SIDE_W - lw - 4, color, 0.75F);
        return y + 10;
    }

    /** Actions proposees : {op, libelle, irreversible} (le serveur revoit tout). */
    static List<Object[]> actions(CompoundTag c) {
        List<Object[]> out = new ArrayList<>();
        DocType type = DocType.of(c.getString("type"));
        DocStatus st = DocStatus.of(c.getString("status"));
        boolean author = "a".equals(c.getString("side")), recipient = "r".equals(c.getString("side")), payer = c.getBoolean("payer");
        boolean running = type.payable() && !st.closed() && st != DocStatus.DRAFT && (!type.signed || st != DocStatus.SENT);
        if (st == DocStatus.DRAFT && author) {
            out.add(new Object[]{"edit", Component.translatable("document.pioneers.action.edit"), false});
            out.add(new Object[]{"send", Component.translatable("document.pioneers.action.send"), false});
            out.add(new Object[]{"delete", Component.translatable("document.pioneers.action.delete"), true});
            return out;
        }
        if (st == DocStatus.SENT && recipient && type.signed) {
            out.add(new Object[]{"accept", Component.translatable("document.pioneers.action.accept"), false});
            out.add(new Object[]{"refuse", Component.translatable("document.pioneers.action.refuse"), true});
        }
        if (running && payer && c.getLong("owed") > 0) {
            out.add(new Object[]{"pay", Component.translatable("document.pioneers.action.pay", ContractsTab.group(c.getLong("na"))), true});
        }
        if (st == DocStatus.SENT && author && c.getLong("paid") == 0 && type != DocType.LETTER && type != DocType.RECEIPT && (type.signed || running)) {
            out.add(new Object[]{"withdraw", Component.translatable("document.pioneers.action.withdraw"), false});
        }
        boolean payeeSide = type.payable() && (type.authorPays() ? recipient : author);
        if (!st.closed() && st != DocStatus.DRAFT && type != DocType.LETTER && (running ? payeeSide : author)) {
            out.add(new Object[]{"cancel", Component.translatable(running ? "document.pioneers.action.forgive" : "document.pioneers.action.cancel"), true});
        }
        if (st != DocStatus.DRAFT) {
            out.add(new Object[]{"print", Component.translatable("document.pioneers.action.print"), false});
        }
        return out;
    }
}
