package fr.pioneers.client.clan.ui;

import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.client.rp.NotebookScreen;
import fr.pioneers.client.rp.ParchmentScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : outils de dessin de l'onglet Clan, en coordonnees de la page du Carnet (384 x 240), au-dessus des outils
 * publics de {@link ParchmentScreen}. Tout texte trop long est borne : resserre jusqu'a 75 %, puis coupe avec « … » et son texte entier en
 * infobulle (rien ne deborde de sa case). Les couleurs portent toujours un signe en plus (texte, icone ou pastille).
 */
final class Ui {

    static final int INK = ParchmentScreen.INK, INK_LIGHT = ParchmentScreen.INK_LIGHT, TEXT = ParchmentScreen.TEXT, DIM = ParchmentScreen.DIM,
          FAINT = ParchmentScreen.FAINT, PLUS = ParchmentScreen.PLUS, MINUS = ParchmentScreen.MINUS, GOLD = ParchmentScreen.GOLD,
          PAPER = ParchmentScreen.PAPER, PAPER_DARK = ParchmentScreen.PAPER_DARK;
    static final float SMALL = 0.75F;

    final NotebookScreen s;
    final GuiGraphics g;
    final Font font;
    final int mx, my;

    Ui(NotebookScreen s, GuiGraphics g, int mx, int my) {
        this.s = s;
        this.g = g;
        this.font = s.font();
        this.mx = mx;
        this.my = my;
    }

    static ResourceLocation gui(String name) {
        return Pioneers.rl("textures/gui/clan/" + name + ".png");
    }

    // ------------------------------------------------------------------ texte
    void text(Component c, int x, int y, int color) {
        s.text(g, c, x, y, color);
    }

    void text(Component c, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    int width(Component c, float scale) {
        return (int) Math.ceil(font.width(c) * scale);
    }

    /**
     * Texte borne a {@code w} : resserre jusqu'a 75 % de {@code scale}, puis coupe (infobulle du texte entier). Renvoie la largeur dessinee.
     * 1.9.1 (retours de Guillaume) : delegue a l'outil commun {@link TextFit}.
     */
    int fit(Component c, int x, int y, int w, int color, float scale) {
        return TextFit.fit(g, font, c, x, y, w, color, scale, this::tips);
    }

    private void tips(int x, int y, int w, int h, List<Component> lines) {
        s.tooltip(mx, my, x, y, w, h, lines);
    }

    /** 1.9.1 (retours de Guillaume) : texte aligne a droite sur {@code rx}, borne a {@code w} ; renvoie la largeur dessinee. */
    int right(Component c, int rx, int y, int w, int color, float scale) {
        return TextFit.right(g, font, c, rx - w, y, w, color, scale, this::tips);
    }

    void center(Component c, int cx, int y, int w, int color, float scale) {
        TextFit.center(g, font, c, cx - w / 2F, y, w, color, scale, this::tips);
    }

    /** Texte coupe en lignes (au plus {@code max}, la derniere coupee avec « … ») ; renvoie la hauteur. 1.9.1 : via {@link TextFit}. */
    int wrap(Component c, int x, int y, int w, int color, float scale, int max) {
        return TextFit.wrap(g, font, c, x, y, w, color, scale, max, this::tips);
    }

    int wrapHeight(Component c, int w, float scale, int max) {
        return TextFit.wrapHeight(font, c, w, scale, max);
    }

    /** Libelle a gauche, valeur a droite (ligne d'une fiche). */
    void row(Component label, Component value, int x, int y, int w, int valueColor) {
        //1.0.5-beta (lot 20) : la valeur (courte) est mesuree d'abord, le libelle prend le reste (avant : la moitie au plus)
        int vw = Math.min(width(value, SMALL), w / 2);
        fit(value, x + w - vw, y, vw, valueColor, SMALL);
        fit(label, x, y, w - vw - 4, DIM, SMALL);
    }

    // ------------------------------------------------------------------ cadres, boutons
    void fill(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    void frame(int x, int y, int w, int h, int color) {
        s.frame(g, x, y, w, h, color);
    }

    void separator(int x, int y, int w) {
        s.separator(g, x, y, w);
    }

    boolean over(int x, int y, int w, int h) {
        return s.over(mx, my, x, y, w, h);
    }

    void click(int x, int y, int w, int h, Runnable r) {
        s.click(x, y, w, h, r);
    }

    void tip(int x, int y, int w, int h, Component... lines) {
        s.tooltip(mx, my, x, y, w, h, lines);
    }

    void tip(int x, int y, int w, int h, List<Component> lines) {
        s.tooltip(mx, my, x, y, w, h, lines);
    }

    /** Bouton a l'encre ; le libelle se resserre pour tenir. */
    void button(int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean on = enabled && over(x, y, w, h);
        fill(x, y, x + w, y + h, on ? 0x40243660 : 0x18243660);
        frame(x, y, w, h, enabled ? INK : FAINT);
        float sc = width(label, 1F) <= w - 4 ? 1F : SMALL;
        center(label, x + w / 2, y + (h - (int) (8 * sc)) / 2, w - 4, enabled ? INK : FAINT, sc);
        if (enabled) {
            click(x, y, w, h, action);
        }
    }

    /** Pastille selectionnable (onglets, choix). */
    void chip(int x, int y, int w, int h, Component label, boolean selected, boolean enabled, Runnable action) {
        boolean on = enabled && over(x, y, w, h);
        fill(x, y, x + w, y + h, selected ? 0x50243660 : on ? 0x28243660 : 0x10243660);
        frame(x, y, w, h, selected ? INK : enabled ? INK_LIGHT : FAINT);
        float sc = width(label, 1F) <= w - 4 ? 1F : SMALL;
        center(label, x + w / 2, y + (h - (int) (8 * sc)) / 2, w - 4, selected ? INK : enabled ? INK_LIGHT : FAINT, sc);
        if (enabled) {
            click(x, y, w, h, action);
        }
    }

    // ------------------------------------------------------------------ 1.0.5-beta (lot 20, retour 10 : onglet Clan lisible, icones explicites)
    /** Hauteur d'une ligne de fait (icone 12 px + valeur) et ecart a la valeur. */
    static final int FACT = 13, ICON = 12, GAP = 15;
    /** Delai de la confirmation d'une action irreversible (deuxieme clic). */
    static final long CONFIRM_MS = 4000;
    private static final java.util.Map<String, Long> ARMED = new java.util.HashMap<>();

    /** Icone de 16 px du mod (dessin 12 x 12 dans la toile 16 x 16) montree en 12 x 12 : le dessin sans le lisere, au pixel pres. */
    void icon12(ResourceLocation tex, int x, int y) {
        icon12(tex, x, y, 0xFFFFFF, 1F);
    }

    void icon12(ResourceLocation tex, int x, int y, int rgb, float alpha) {
        g.setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(tex, x, y, 2F, 2F, ICON, ICON, 16, 16);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /**
     * Fait d'une fiche : icone 12 px puis la valeur (pleine taille, bornee a {@code w}) ; le libelle et le detail sont dans l'infobulle de
     * toute la ligne (moins de texte a lire, rien de perdu). Renvoie la largeur dessinee.
     */
    int fact(ResourceLocation icon, Component value, int x, int y, int w, int color, Component... tip) {
        icon12(icon, x, y);
        int vw = fit(value, x + GAP, y + 2, w - GAP, color, 1F);
        if (tip.length > 0) {
            tip(x, y, Math.min(w, GAP + vw), ICON, tip);
        }
        return GAP + vw;
    }

    /** Titre de section : icone 12 px, titre a l'encre (petit), filet dessous ; renvoie la hauteur (14). */
    int section(ResourceLocation icon, Component title, int x, int y, int w) {
        icon12(icon, x, y);
        fit(title, x + GAP, y + 3, w - GAP, INK, SMALL);
        separator(x + GAP, y + 12, w - GAP);
        return 14;
    }

    /** Pastille selectionnable a icone ({@code null} : libelle centre seul) (colonne des fiches, vues) : icone 12 px a gauche, libelle borne, pastille de compte a droite. */
    void navChip(int x, int y, int w, int h, @javax.annotation.Nullable ResourceLocation icon, Component label, boolean selected, int badge, Runnable action) {
        boolean on = over(x, y, w, h);
        fill(x, y, x + w, y + h, selected ? 0x50243660 : on ? 0x28243660 : 0x10243660);
        frame(x, y, w, h, selected ? INK : INK_LIGHT);
        int lx = icon == null ? x + 3 : x + 17;
        if (icon != null) {
            icon12(icon, x + 2, y + (h - ICON) / 2);
        }
        int bw = 0;
        if (badge > 0) {
            Component b = Component.literal(Integer.toString(badge));
            bw = width(b, SMALL) + 4;
            int bx = x + w - 2 - bw, by = y + (h - 8) / 2;
            fill(bx, by, bx + bw, by + 8, 0xFF9A2E25);
            text(b, bx + 2, by + 1.5F, PAPER, SMALL);
            bw += 2;
        }
        int lw = x + w - lx - bw - 2;
        float sc = width(label, 1F) <= lw ? 1F : SMALL;
        if (icon == null) {
            center(label, lx + lw / 2, y + (h - (int) (8 * sc)) / 2 + (sc < 1F ? 1 : 0), lw, selected ? INK : INK_LIGHT, sc);
        } else {
            fit(label, lx, y + (h - (int) (8 * sc)) / 2 + (sc < 1F ? 1 : 0), lw, selected ? INK : INK_LIGHT, sc);
        }
        click(x, y, w, h, action);
    }

    /** Bouton a icone ({@code null} : sans icone, libelle centre) : icone 12 px puis le libelle ; desactive : encre pale et, s'il y en a une, la raison en infobulle. */
    void iconButton(int x, int y, int w, int h, @javax.annotation.Nullable ResourceLocation icon, Component label, boolean enabled, Runnable action, Component... why) {
        boolean on = enabled && over(x, y, w, h);
        fill(x, y, x + w, y + h, on ? 0x40243660 : 0x18243660);
        frame(x, y, w, h, enabled ? INK : FAINT);
        if (icon == null) {
            float sc = width(label, 1F) <= w - 4 ? 1F : SMALL;
            center(label, x + w / 2, y + (h - (int) (8 * sc)) / 2, w - 4, enabled ? INK : FAINT, sc);
        } else {
            icon12(icon, x + 2, y + (h - ICON) / 2, 0xFFFFFF, enabled ? 1F : 0.35F);
            int lw = w - 17 - 2;
            float sc = width(label, 1F) <= lw ? 1F : SMALL;
            fit(label, x + 16, y + (h - (int) (8 * sc)) / 2 + (sc < 1F ? 1 : 0), lw, enabled ? INK : FAINT, sc);
        }
        if (enabled) {
            click(x, y, w, h, action);
        } else if (why.length > 0) {
            tip(x, y, w, h, why);
        }
    }

    /**
     * Action IRREVERSIBLE : le premier clic arme le bouton ({@link #CONFIRM_MS} ms : fond rouge, libelle « Sur ? », infobulle), le second
     * l'execute. {@code key} distingue les boutons (membre vise, traite…).
     */
    void confirm(String key, int x, int y, int w, int h, @javax.annotation.Nullable ResourceLocation icon, Component label, boolean enabled, Runnable action, Component... why) {
        Long at = ARMED.get(key);
        boolean armed = enabled && at != null && System.currentTimeMillis() - at < CONFIRM_MS;
        if (!armed) {
            iconButton(x, y, w, h, icon, label, enabled, () -> ARMED.put(key, System.currentTimeMillis()), why);
            return;
        }
        fill(x, y, x + w, y + h, over(x, y, w, h) ? 0x609A2E25 : 0x409A2E25);
        frame(x, y, w, h, MINUS);
        if (icon == null) {
            center(Component.translatable("clan.pioneers.ui.confirm"), x + w / 2, y + (h - 8) / 2, w - 4, MINUS, 1F);
        } else {
            icon12(Ui.gui("ico_warning"), x + 2, y + (h - ICON) / 2);
            fit(Component.translatable("clan.pioneers.ui.confirm"), x + 16, y + (h - 8) / 2, w - 19, MINUS, 1F);
        }
        tip(x, y, w, h, Component.translatable("clan.pioneers.ui.confirm_tip"));
        click(x, y, w, h, () -> {
            ARMED.remove(key);
            action.run();
        });
    }

    /** Pastille de presence 5 x 5 (en ligne : vert, sinon gris) ; toujours suivie d'un mot. */
    void dot(boolean online, int x, int y) {
        fill(x, y, x + 5, y + 5, online ? PLUS : 0xFFB9A889);
        frame(x, y, 5, 5, online ? 0xFF1E4A20 : DIM);
    }

    /** Hauteur de {@code c} replie dans {@code w} a l'echelle {@code scale} (au plus {@code max} lignes). */
    int wrapH(Component c, int w, float scale, int max) {
        return wrapHeight(c, w, scale, max);
    }

    void bar(int x, int y, int w, int h, double frac, int color) {
        s.bar(g, x, y, w, h, frac, color);
    }

    // ------------------------------------------------------------------ images
    void icon(ResourceLocation tex, int x, int y, int size) {
        s.icon(g, tex, x, y, size);
    }

    /** Image entiere dessinee a {@code w} x {@code h} (texture de {@code tw} x {@code th}). */
    void image(ResourceLocation tex, int x, int y, int w, int h, int tw, int th) {
        g.blit(tex, x, y, w, h, 0, 0, tw, th, tw, th);
    }

    /** Image teintee (multipliee par la couleur RGB), opacite {@code alpha}. */
    void tinted(ResourceLocation tex, int x, int y, int w, int h, int tw, int th, int rgb, float alpha) {
        g.setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        image(tex, x, y, w, h, tw, th);
        g.setColor(1F, 1F, 1F, 1F);
    }

    void item(ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** Embleme d'un clan : sa banniere (couleur de fond et motifs de la banniere posee sur la Mairie), ou vide. */
    static ItemStack banner(CompoundTag b) {
        if (b == null || !b.contains("Base")) {
            return ItemStack.EMPTY;
        }
        ItemStack st = new ItemStack(BannerBlock.byColor(DyeColor.byId(b.getInt("Base") & 15)).asItem());
        ListTag pats = b.getList("Patterns", Tag.TAG_COMPOUND);
        if (!pats.isEmpty()) {
            CompoundTag bet = new CompoundTag();
            bet.put("Patterns", pats.copy());
            st.getOrCreateTag().put("BlockEntityTag", bet);
        }
        return st;
    }

    /** Embleme : banniere du clan, sinon l'embleme d'appartenance « clan ». */
    void emblem(CompoundTag banner, int x, int y, int size) {
        ItemStack st = banner(banner);
        if (st.isEmpty()) {
            image(gui("member_clan"), x, y, size, size, 32, 32);
        } else {
            item(st, x, y, size / 16F);
        }
    }

    // ------------------------------------------------------------------ couleurs et nombres
    /** Couleur d'une teinture (0..15) lisible sur le papier : les teintes claires sont foncees. */
    static int dyeInk(int dye) {
        int c = DyeColor.byId(dye & 15).getFireworkColor();
        int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        double lum = (0.299 * r + 0.587 * gg + 0.114 * b) / 255.0;
        if (lum > 0.55) {
            double k = 0.55 / lum;
            r = (int) (r * k);
            gg = (int) (gg * k);
            b = (int) (b * k);
        }
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    /**
     * Teinte d'une tuile grise de la carte (manifeste des visuels, tint « clan ») : les teintures tres sombres (noir, gris) sont eclaircies
     * avant de teindre, sinon le motif de la tuile disparait.
     */
    static int tint(int dye) {
        int c = dye(dye);
        int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        double lum = (0.299 * r + 0.587 * gg + 0.114 * b) / 255.0;
        if (lum < 0.45) {
            double k = (0.45 - lum) / (1 - lum);
            r += (int) ((255 - r) * k);
            gg += (int) ((255 - gg) * k);
            b += (int) ((255 - b) * k);
        }
        return r << 16 | gg << 8 | b;
    }

    /** Couleur pleine d'une teinture (carte, pastilles). */
    static int dye(int dye) {
        return DyeColor.byId(dye & 15).getFireworkColor() & 0xFFFFFF;
    }

    /** 12 345 678 (espaces fines entre les milliers). */
    static String group(long v) {
        String raw = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder(v < 0 ? "−" : "");
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (raw.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(raw.charAt(i));
        }
        return b.toString();
    }

    /** Duree lisible (jours, heures ou minutes). */
    static Component duration(long ms) {
        long m = Math.max(0, ms) / 60_000L;
        if (m >= 2 * 24 * 60) {
            return Component.translatable("clan.pioneers.ui.time.days", m / (24 * 60));
        }
        if (m >= 120) {
            return Component.translatable("clan.pioneers.ui.time.hours", m / 60);
        }
        return Component.translatable("clan.pioneers.ui.time.minutes", Math.max(1, m));
    }

    /**
     * Argument de journal : texte, ou cle de langue si prefixe par « # ». 1.0.3-beta (lot 16) : « #cle|a|b » = cle avec ses propres
     * arguments (meme lecture que {@code ClanMessages.arg} cote serveur).
     */
    static Object arg(String a) {
        if (!a.startsWith("#")) {
            return a;
        }
        String[] parts = a.substring(1).split("\\|");
        return parts.length == 1 ? Component.translatable(parts[0]) : Component.translatable(parts[0], (Object[]) java.util.Arrays.copyOfRange(parts, 1,
              parts.length));
    }

    /** Ligne de journal traduite (cle {@code k}, arguments {@code a}). */
    static Component line(CompoundTag l) {
        ListTag a = l.getList("a", Tag.TAG_STRING);
        Object[] args = new Object[a.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = arg(a.getString(i));
        }
        return Component.translatable(l.getString("k"), args);
    }

    static List<String> strings(ListTag l) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    /** Texte venu du serveur : « #cle » = cle de langue a traduire chez le client (noms des roles par defaut), sinon texte tel quel. */
    static Component txt(String s) {
        return s != null && s.startsWith("#") ? Component.translatable(s.substring(1)) : Component.literal(s == null ? "" : s);
    }

    /** Nom affiche d'un clan (nom, ou « X (Solitaire) » pour un clan solo sans nom). */
    static Component clanName(String name, String soloOf) {
        return name == null || name.isBlank() ? Component.translatable("clan.pioneers.solo_name", soloOf == null || soloOf.isBlank() ? "?" : soloOf)
              : Component.literal(name);
    }

    static FormattedText plain(String s) {
        return FormattedText.of(s);
    }
}
