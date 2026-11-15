package fr.pioneers.society.clan.client.ui;

import fr.pioneers.society.rp.client.NotebookScreen;
import fr.pioneers.core.profile.PioneerApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : onglet Clan du Carnet (visible si l'interrupteur des clans est vrai). A gauche, l'identite du clan, les
 * fiches et le bandeau des reponses du serveur ; a droite, la fiche choisie. Ce que l'on voit depend du statut : sans clan (accueil, carte,
 * statistiques personnelles, annuaires), Solitaire (son clan solo, sans vote ni diplomatie), membre (les huit fiches et les annuaires). Le
 * client n'envoie que des intentions ; le serveur revoit tout.
 */
final class ClanTab implements NotebookScreen.Tab {

    static final String ID = "clan";
    /** Colonne de gauche et zone de la fiche (coordonnees de la page). */
    static final int NAV_X = 8, NAV_W = 80, NAV_Y = 45, NAV_H = 13, NAV_STEP = 14, NOTICE_Y = 202;
    static final int X = 94, Y = 26, W = 282, H = 208;

    enum Status {
        LOADING, DISABLED, NONE, SOLO, MEMBER
    }

    private static final Map<String, Fiche> FICHES = new LinkedHashMap<>();
    private static String current = "sheet";
    private static long lastDraw;
    @Nullable
    private static EditBox text, number;
    private static String textOwner = "", numberOwner = "";
    private static boolean textUsed, numberUsed;

    static {
        for (Fiche f : List.of(new HomeFiche(), new SheetFiche(), new MembersFiche(), new StatsFiche(), new TerritoryFiche(), new TreasuryFiche(),
              new GovernmentFiche(), new DiplomacyFiche(), new FactionFiche(), new JournalFiche(), new DirectoryFiche(), new PeoplesFiche())) {
            FICHES.put(f.id(), f);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return PioneerApi.hasFeature("clans");
    }

    @Override
    public void opened(NotebookScreen s) {
        text = s.tabWidget(new EditBox(s.font(), 0, 0, 100, 14, Component.empty()));
        text.setMaxLength(100);
        text.visible = false;
        number = s.tabWidget(new EditBox(s.font(), 0, 0, 60, 14, Component.empty()));
        number.setMaxLength(9);
        number.setFilter(x -> x.chars().allMatch(Character::isDigit));
        number.visible = false;
        textOwner = numberOwner = "";
        ClanUiClient.request("sheet");
        Fiche f = FICHES.get(current);
        if (f != null && !"sheet".equals(current)) {
            f.enter();
        }
    }

    /** Choisit la fiche affichee (au prochain dessin, elle fait ses demandes). */
    static void select(String id) {
        if (id.equals(current)) {
            return;
        }
        Fiche old = FICHES.get(current);
        if (old != null) {
            old.leave();
        }
        current = id;
        Fiche f = FICHES.get(id);
        if (f != null) {
            f.enter();
        }
    }

    /** L'onglet Clan a-t-il ete dessine a l'instant (Carnet ouvert sur cet onglet) ? */
    static boolean visible() {
        return System.currentTimeMillis() - lastDraw < 500;
    }

    static Status status() {
        CompoundTag t = ClanUiClient.sheet("sheet");
        if (t == null) {
            return Status.LOADING;
        }
        String err = t.getString("error");
        if (err.endsWith(".disabled")) {
            return Status.DISABLED;
        }
        if (!err.isEmpty()) {
            return Status.NONE;
        }
        return t.getBoolean("solo") ? Status.SOLO : Status.MEMBER;
    }

    /** Permission du joueur dans son clan (fiche « sheet »). */
    static boolean may(String perm) {
        CompoundTag t = ClanUiClient.sheet("sheet");
        if (t == null) {
            return false;
        }
        for (int i = 0; i < t.getList("perms", 8).size(); i++) {
            if (perm.equals(t.getList("perms", 8).getString(i))) {
                return true;
            }
        }
        return false;
    }

    static boolean isRep() {
        CompoundTag t = ClanUiClient.sheet("sheet");
        return t != null && t.getBoolean("isRep");
    }

    static List<String> nav(Status st) {
        return switch (st) {
            case NONE -> List.of("home", "territory", "stats", "clans", "peoples");
            case SOLO -> List.of("sheet", "members", "stats", "territory", "treasury", "journal", "clans", "peoples");
            case MEMBER -> inFaction() ? List.of("sheet", "members", "stats", "territory", "treasury", "government", "diplomacy", "faction", "journal", "clans",
                  "peoples") : List.of("sheet", "members", "stats", "territory", "treasury", "government", "diplomacy", "journal", "clans", "peoples");
            default -> List.of();
        };
    }

    /** Le clan du joueur est-il dans une faction (fiche « sheet ») ? */
    static boolean inFaction() {
        CompoundTag t = ClanUiClient.sheet("sheet");
        return t != null && !t.getString("faction").isEmpty();
    }

    // ------------------------------------------------------------------ champs de saisie (deux, deplaces d'une fiche a l'autre)
    /** Champ de texte a cette place pour cette image ; {@code owner} change = champ vide. */
    static EditBox textBox(Ui u, String owner, int x, int y, int w, Component hint, int max) {
        EditBox b = text;
        if (b == null) {
            throw new IllegalStateException("onglet Clan ferme");
        }
        if (!owner.equals(textOwner)) {
            textOwner = owner;
            b.setValue("");
            b.setFocused(false);
        }
        b.setMaxLength(max);
        place(u, b, x, y, w, hint);
        textUsed = true;
        return b;
    }

    /** Champ numerique a cette place pour cette image. */
    static EditBox numberBox(Ui u, String owner, int x, int y, int w, Component hint) {
        EditBox b = number;
        if (b == null) {
            throw new IllegalStateException("onglet Clan ferme");
        }
        if (!owner.equals(numberOwner)) {
            numberOwner = owner;
            b.setValue("");
            b.setFocused(false);
        }
        place(u, b, x, y, w, hint);
        numberUsed = true;
        return b;
    }

    private static void place(Ui u, EditBox b, int x, int y, int w, Component hint) {
        b.setX(u.s.pageLeft() + x);
        b.setY(u.s.pageTop() + y);
        b.setWidth(w);
        b.setHint(hint);
        b.visible = true;
    }

    static long parse(@Nullable EditBox b) {
        try {
            return b == null || b.getValue().isEmpty() ? 0 : Long.parseLong(b.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void draw(NotebookScreen s, GuiGraphics g, int mx, int my, float pt) {
        lastDraw = System.currentTimeMillis();
        textUsed = numberUsed = false;
        Ui u = new Ui(s, g, mx, my);
        Status st = status();
        List<String> nav = nav(st);
        if (st == Status.LOADING || st == Status.DISABLED) {
            u.wrap(Component.translatable(st == Status.LOADING ? "clan.pioneers.ui.loading" : "clan.pioneers.error.disabled"), X, Y + 8, W, Ui.DIM, 1F,
                  3);
            hideUnused();
            return;
        }
        if (!nav.contains(current)) {
            select(nav.get(0));
        }
        header(u, st);
        List<String> shown = new ArrayList<>(nav);
        //1.0.5-beta (lot 20, retour 10) : une icone et un libelle court par fiche, le role de la fiche en infobulle ; pastille de compte sur ce
        //qui attend le joueur (votes ouverts, invitations recues)
        for (int i = 0; i < shown.size(); i++) {
            String id = shown.get(i);
            int y = NAV_Y + i * NAV_STEP;
            int badge = badge(id);
            u.navChip(NAV_X, y, NAV_W, NAV_H, Ui.gui("nav_" + id), Component.translatable("clan.pioneers.ui.nav_short." + id), id.equals(current), badge,
                  () -> select(id));
            if (badge > 0) {
                u.tip(NAV_X, y, NAV_W, NAV_H, Component.translatable("clan.pioneers.ui.nav." + id), Component.translatable("clan.pioneers.ui.nav_tip." + id),
                      Component.translatable("clan.pioneers.ui.nav_badge." + id, badge));
            } else {
                u.tip(NAV_X, y, NAV_W, NAV_H, Component.translatable("clan.pioneers.ui.nav." + id), Component.translatable("clan.pioneers.ui.nav_tip." + id));
            }
        }
        notices(u);
        Fiche f = FICHES.get(current);
        if (f != null) {
            f.draw(u);
        }
        hideUnused();
    }

    private static void hideUnused() {
        if (text != null && !textUsed) {
            text.visible = false;
            text.setFocused(false);
        }
        if (number != null && !numberUsed) {
            number.visible = false;
            number.setFocused(false);
        }
    }

    /** 1.0.5-beta (lot 20) : ce qui attend le joueur dans une fiche (votes ouverts, invitations recues), 0 sinon. */
    static int badge(String id) {
        CompoundTag t = ClanUiClient.sheet("sheet");
        if (t == null) {
            return 0;
        }
        return switch (id) {
            case "government" -> t.getInt("openVotes");
            case "home" -> t.getList("invites", 10).size();
            default -> 0;
        };
    }

    /** Identite en tete de la colonne : le nom, puis [ETQ] (couleur du clan) et le role. */
    private static void header(Ui u, Status st) {
        CompoundTag t = ClanUiClient.sheet("sheet");
        if (st == Status.NONE || t == null) {
            u.fit(Component.translatable("clan.pioneers.ui.independent"), NAV_X, Y, NAV_W, Ui.INK, 1F);
            u.fit(Component.translatable("clan.pioneers.ui.independent.sub"), NAV_X, Y + 10, NAV_W, Ui.DIM, Ui.SMALL);
            return;
        }
        Component name = st == Status.SOLO && t.getString("name").isBlank() ? Component.literal(t.getString("solo_of"))
              : Ui.clanName(t.getString("name"), t.getString("solo_of"));
        u.fit(name, NAV_X, Y, NAV_W, Ui.INK, 1F);
        int x = NAV_X;
        String tag = t.getString("tag");
        if (!tag.isBlank()) {
            Component tc = Component.literal("[" + tag + "] ");
            u.text(tc, x, Y + 10, Ui.dyeInk(t.getInt("color")), Ui.SMALL);
            x += u.width(tc, Ui.SMALL);
        }
        Component role = st == Status.SOLO ? Component.translatable("clan.pioneers.regime.solo") : Ui.txt(t.getString("roleName"));
        u.fit(role, x, Y + 10, NAV_X + NAV_W - x, Ui.DIM, Ui.SMALL);
    }

    /** Bandeau des reponses du serveur, sous les fiches (8 secondes). */
    private static void notices(Ui u) {
        long now = System.currentTimeMillis();
        int y = NOTICE_Y;
        int bottom = Y + H;
        List<ClanUiClient.Notice> recent = new ArrayList<>();
        for (ClanUiClient.Notice n : ClanUiClient.NOTICES) {
            if (now - n.at() < 8000) {
                recent.add(n);
            }
        }
        if (recent.isEmpty()) {
            return;
        }
        u.fill(NAV_X, y - 2, NAV_X + NAV_W, bottom, 0x30243660);
        u.frame(NAV_X, y - 2, NAV_W, bottom - y + 2, Ui.FAINT);
        for (int i = recent.size() - 1; i >= 0 && y < bottom - 6; i--) {
            ClanUiClient.Notice n = recent.get(i);
            Component c = n.error() ? Component.literal("− ").append(n.text()) : n.text();
            int lines = Math.max(1, (bottom - 2 - y) / 8);
            y += u.wrap(c, NAV_X + 2, y, NAV_W - 4, n.error() ? Ui.MINUS : Ui.TEXT, Ui.SMALL, lines) + 2;
        }
    }

    // ------------------------------------------------------------------ une fiche
    /** Une fiche de l'onglet : ses demandes a l'entree, son dessin dans la zone {@link #X}, {@link #Y}, {@link #W}, {@link #H}. */
    abstract static class Fiche {

        abstract String id();

        void enter() {
        }

        void leave() {
        }

        abstract void draw(Ui u);

        /** Message d'attente ou d'erreur de la fiche ; vrai si la fiche n'a rien d'autre a montrer. */
        static boolean waiting(Ui u, @Nullable CompoundTag t) {
            if (t == null) {
                u.wrap(Component.translatable("clan.pioneers.ui.loading"), X, Y + 4, W, Ui.DIM, 1F, 3);
                return true;
            }
            if (!t.getString("error").isEmpty()) {
                u.wrap(Component.translatable(t.getString("error")), X, Y + 4, W, Ui.DIM, 1F, 4);
                return true;
            }
            return false;
        }

        /** Titre de fiche (encre, pleine taille) et filet dessous. */
        static void title(Ui u, Component c, int y) {
            u.fit(c, X, y, W, Ui.INK, 1F);
            u.separator(X, y + 10, W);
        }
    }
}
