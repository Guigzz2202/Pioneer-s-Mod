package fr.pioneers.client.gui;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 16, retours de Guillaume : « un selecteur de joueur deja vu au lieu de taper les pseudos ») : champ de nom avec une LISTE
 * DEROULANTE filtrable des joueurs connus du serveur (connectes + deja venus : {@code core/profile/KnownPlayers}, paquet
 * {@link RpActions#PLAYERS_REQUEST} → {@link RpActions#PLAYERS_VIEW}) : tete du skin (vraie pour un joueur connecte, celle par defaut sinon),
 * nom de pionnier, compte, point vert / gris en ligne / hors ligne. La saisie libre reste possible (secours) ; le serveur reste juge du nom
 * (un nom inconnu est refuse par la demande finale). Clavier : fleches, Entree, Echap ; souris : clic, molette.
 * <p>
 * La liste se dessine au-dessus des autres champs ({@link #Z}) et a la priorite des clics de l'ecran ({@link #captures}, appele par
 * {@code ParchmentScreen.mouseClicked} / {@code mouseScrolled} et par la Partie du guide). Mise en page pure : {@link #rows}, {@link #ROW_H}.
 */
public class PlayerPicker extends EditBox {

    public static final int ROW_H = 12, ROWS = 6, HEAD = 8, Z = 300, REFRESH_MS = 1500;
    /** Palette : papier du Carnet (encre sur papier) ou interface sombre (Partie du guide). */
    public enum Style {
        PAPER(0xFFF3E7CC, 0xFF24365E, 0xFF3A2E22, 0xFF7A6A55, 0x30243660),
        DARK(0xF0141A20, 0xFF8FA6C0, 0xFFE8EEF4, 0xFF9AA6B2, 0x405A7A9A);

        final int bg, frame, text, dim, hover;

        Style(int bg, int frame, int text, int dim, int hover) {
            this.bg = bg;
            this.frame = frame;
            this.text = text;
            this.dim = dim;
            this.hover = hover;
        }
    }

    /** Un joueur de la liste du serveur. */
    public record Entry(UUID id, String account, String pioneer, boolean online, long seen) {
    }

    private static final Map<String, List<Entry>> LISTS = new HashMap<>();
    private static final Map<String, Long> ASKED = new HashMap<>();

    private final Font font;
    private final String purpose;
    private final Style style;
    private boolean open;
    private int scroll, hovered = -1, keyed = -1;
    private String lastQuery = "";
    @Nullable
    private Consumer<String> onPick;

    public PlayerPicker(Font font, int x, int y, int w, int h, Component hint, String purpose, Style style) {
        super(font, x, y, w, h, hint);
        this.font = font;
        this.purpose = purpose;
        this.style = style;
        setMaxLength(16);
        setHint(Component.translatable("picker.pioneers.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    public PlayerPicker onPick(Consumer<String> c) {
        this.onPick = c;
        return this;
    }

    // ------------------------------------------------------------------ reseau
    public static void onServer(CompoundTag t) {
        List<Entry> out = new ArrayList<>();
        for (Tag x : t.getList("list", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            if (c.hasUUID("u") && out.size() < 200) {
                out.add(new Entry(c.getUUID("u"), c.getString("n"), c.getString("p"), c.getBoolean("o"), c.getLong("s")));
            }
        }
        LISTS.put(t.getString("purpose"), out);
    }

    private void ask() {
        long now = Util.getMillis();
        Long last = ASKED.get(purpose);
        if (last == null || now - last > REFRESH_MS) {
            ASKED.put(purpose, now);
            CompoundTag t = new CompoundTag();
            t.putString("purpose", purpose);
            t.putString("q", "");
            RpNet.toServer(RpActions.PLAYERS_REQUEST, t);
        }
    }

    /** Joueurs de la liste du serveur qui contiennent le texte tape (compte ou nom de pionnier) : PUR, aussi pour la maquette. */
    public static List<Entry> filter(List<Entry> all, String typed) {
        String q = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (q.isEmpty() || e.account().toLowerCase(Locale.ROOT).contains(q) || e.pioneer().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    private List<Entry> shown() {
        return filter(LISTS.getOrDefault(purpose, List.of()), getValue());
    }

    /** Rangees visibles de la liste (au plus {@link #ROWS} ; une rangee « aucun joueur » si vide). */
    public static int rows(int count) {
        return Math.max(1, Math.min(ROWS, count));
    }

    private int listY() {
        return getY() + height + 1;
    }

    private int listH() {
        return rows(shown().size()) * ROW_H + 2;
    }

    /** La liste ouverte couvre ce point (priorite sur les zones de l'ecran). */
    public boolean captures(double mx, double my) {
        return open && visible && mx >= getX() && mx < getX() + width && my >= listY() && my < listY() + listH();
    }

    /** Entree choisira la ligne surlignee (et pas la saisie libre). */
    public boolean takesEnter() {
        return open && isFocused() && keyed >= 0;
    }

    // ------------------------------------------------------------------ evenements
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            open = true;
            ask();
        } else {
            open = false;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (captures(mx, my)) {
            int i = scroll + (int) ((my - listY() - 1) / ROW_H);
            List<Entry> l = shown();
            if (button == 0 && i >= 0 && i < l.size()) {
                pick(l.get(i));
            }
            return true;
        }
        boolean in = super.mouseClicked(mx, my, button);
        if (in) {
            open = true;
            ask();
        }
        return in;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (captures(mx, my)) {
            scroll = Math.max(0, Math.min(Math.max(0, shown().size() - ROWS), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return super.isMouseOver(mx, my) || captures(mx, my);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (open && isFocused()) {
            List<Entry> l = shown();
            if (key == 264 && !l.isEmpty()) { //bas
                keyed = Math.min(l.size() - 1, keyed + 1);
                scroll = Math.max(scroll, keyed - ROWS + 1);
                return true;
            }
            if (key == 265 && !l.isEmpty()) { //haut
                keyed = Math.max(0, keyed - 1);
                scroll = Math.min(scroll, keyed);
                return true;
            }
            if ((key == 257 || key == 335) && keyed >= 0 && keyed < l.size()) {
                pick(l.get(keyed));
                return true;
            }
            if (key == 256) { //Echap : ferme la liste, pas l'ecran
                open = false;
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        boolean r = super.charTyped(c, mods);
        if (r) {
            open = true;
            keyed = -1;
            scroll = 0;
        }
        return r;
    }

    private void pick(Entry e) {
        setValue(e.account());
        moveCursorToEnd();
        open = false;
        keyed = -1;
        if (onPick != null) {
            onPick.accept(e.account());
        }
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void renderWidget(@NotNull GuiGraphics g, int mx, int my, float pt) {
        super.renderWidget(g, mx, my, pt);
        if (!visible) {
            return;
        }
        if (!getValue().equals(lastQuery)) {
            lastQuery = getValue();
            scroll = 0;
        }
        if (!open || !isFocused()) {
            return;
        }
        List<Entry> l = shown();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - ROWS)));
        int x = getX(), y = listY(), w = width, h = listH();
        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        g.fill(x, y, x + w, y + h, style.frame);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, style.bg);
        hovered = captures(mx, my) ? scroll + (my - y - 1) / ROW_H : -1;
        if (l.isEmpty()) {
            Component none = Component.translatable(LISTS.containsKey(purpose) ? ("pay".equals(purpose) ? "picker.pioneers.none_online"
                  : "picker.pioneers.none") : "picker.pioneers.loading");
            g.drawString(font, fitted(font, none.getString(), w - 8), x + 4, y + 3, style.dim, false);
        }
        for (int r = 0; r < ROWS && scroll + r < l.size(); r++) {
            int i = scroll + r;
            Entry e = l.get(i);
            int ry = y + 1 + r * ROW_H;
            if (i == hovered || i == keyed) {
                g.fill(x + 1, ry, x + w - 1, ry + ROW_H, style.hover);
            }
            PlayerFaceRenderer.draw(g, skin(e.id()), x + 3, ry + 2, HEAD);
            int dot = x + w - 9;
            g.blit(Pioneers.rl("textures/gui/pioneer/stat/" + (e.online() ? "online" : "offline") + ".png"), dot, ry + 2, 0, 0, 8, 8, 8, 8);
            label(g, e, x + 14, ry + 2, dot - 3 - (x + 14), e.online() ? style.text : style.dim);
        }
        if (l.size() > ROWS) {
            int track = h - 2, bar = Math.max(6, track * ROWS / l.size());
            int by = y + 1 + (track - bar) * scroll / Math.max(1, l.size() - ROWS);
            g.fill(x + w - 2, by, x + w - 1, by + bar, style.frame);
        }
        g.pose().popPose();
    }

    /**
     * Nom d'une ligne, jamais coupe s'il peut tenir : « Pionnier (compte) » si la place le permet, sinon le nom de pionnier seul (le compte
     * va dans le champ une fois choisi), resserre jusqu'a 0,75 ; « … » seulement au-dela.
     */
    void label(GuiGraphics g, Entry e, int x, int y, int room, int color) {
        //1.0.5-beta (lot 19) : sans nom de pionnier (identite privee : le serveur ne l'envoie qu'a un membre du clan qui peut la voir) : le compte
        String both = e.pioneer() + " (" + e.account() + ")";
        String name = e.pioneer().isEmpty() ? e.account()
              : e.pioneer().equalsIgnoreCase(e.account()) || font.width(both) > room ? e.pioneer() : both;
        int w = font.width(name);
        if (w <= room) {
            g.drawString(font, name, x, y, color, false);
            return;
        }
        float k = Math.max(0.75F, room / (float) w);
        g.pose().pushPose();
        g.pose().translate(x, y + (1 - k) * 4, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, fitted(font, name, (int) (room / k)), 0, 0, color, false);
        g.pose().popPose();
    }

    /** Texte entier s'il tient, sinon coupe par « … » (le nom complet reste dans le champ une fois choisi). */
    static String fitted(Font f, String s, int w) {
        return f.width(s) <= w ? s : TextFit.cut(f, s, w);
    }

    /** Tete du skin : la vraie pour un joueur connecte (liste des joueurs du client), celle par defaut de son identifiant sinon. */
    static ResourceLocation skin(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }
}
