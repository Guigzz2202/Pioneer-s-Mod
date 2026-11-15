package fr.pioneers.client.rp;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerDefs;
import fr.pioneers.core.profile.PioneerProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * 1.9 (MAJ RP) : textes et ressources communs aux ecrans du pionnier (noms, effets d'un skill, lignes d'une carte de vocation).
 * <p>
 * 1.9.1 (retours de Guillaume : textes superposes) : mesure des textes avec la vraie police ({@link #fitScale}, {@link #ellipsize},
 * {@link #splitWords}) ; les ecrans de papier s'en servent par {@code ParchmentScreen.fit/wrap}. Un texte n'est jamais coupe sans
 * infobulle, jamais reduit sous {@link #MIN_SCALE}. Portrait en pied du pionnier ({@link #pioneer}).
 */
public final class RpGui {

    /** 1.9.1 : plus petite echelle d'un texte reduit pour tenir (en dessous, il est coupe par « … » avec une infobulle). */
    public static final float MIN_SCALE = 0.75F;
    /** 1.9.1 : hauteur d'un personnage pour le cadrer (1,8 bloc + coiffure et marge), largeur bras compris. */
    public static final float PIONEER_H = 1.92F, PIONEER_W = 1.12F;

    private RpGui() {
    }

    public static ResourceLocation skillIcon(String skill) {
        return Pioneers.rl("textures/gui/pioneer/skill/" + skill + ".png");
    }

    public static ResourceLocation emblem(String vocation) {
        return Pioneers.rl("textures/gui/pioneer/vocation/" + vocation + ".png");
    }

    /** 1.0.4-beta (lot 8) : icone d'une tuile du Carnet (jours, niveaux, Shining, Maitrises, ecrits ; T:rp/lot8_identity_clans.py). */
    public static ResourceLocation stat(String id) {
        return Pioneers.rl("textures/gui/pioneer/stat/" + id + ".png");
    }

    public static ResourceLocation clasp(String skill) {
        return Pioneers.rl("textures/gui/pioneer/clasp/" + skill + ".png");
    }

    public static Component skillName(String skill) {
        return Component.translatable("skill.pioneers." + skill);
    }

    public static Component vocationName(String vocation) {
        return vocation == null || vocation.isEmpty() ? Component.translatable("pioneer.pioneers.unknown_vocation")
              : Component.translatable("vocation.pioneers." + vocation);
    }

    /** Valeur lisible d'un effet : « +12 % », « −8 % », « +0,4 bloc »... */
    public static String effect(PioneerDefs.Mod m, double value) {
        if (m.abs()) {
            double v = Math.round(value * 10) / 10.0;
            return (v >= 0 ? "+" : "−") + trim(Math.abs(v));
        }
        double pct = Math.round(value * 1000) / 10.0;
        return (pct >= 0 ? "+" : "−") + trim(Math.abs(pct)) + " %";
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Effets d'un skill a un niveau donne (lignes « libelle : valeur »). */
    public static List<Component> skillEffects(PioneerDefs.Skill s, int level) {
        List<Component> out = new ArrayList<>();
        for (PioneerDefs.Mod m : s.mods()) {
            out.add(Component.translatable("modifier.pioneers." + m.key()).append(" : " + effect(m, m.value() * level / PioneerApi.MAX_LEVEL)));
        }
        return out;
    }

    /** Infobulle d'une carte de vocation : phrase, affinites, avantage (+) et defaut (−). */
    public static List<Component> vocationTooltip(PioneerDefs.Vocation v) {
        List<Component> out = new ArrayList<>();
        out.add(vocationName(v.id()).copy().withStyle(s -> s.withColor(0x9FB4E0)));
        out.add(Component.translatable("vocation.pioneers." + v.id() + ".phrase").withStyle(s -> s.withItalic(true).withColor(0xC8C0B0)));
        if (v.neutral()) { //1.9.1 (retours de Guillaume) : le Polyvalent n'a ni affinite, ni avantage, ni defaut
            out.add(Component.translatable("pioneer.pioneers.vocation.neutral").withStyle(s -> s.withColor(0xC8C0B0)));
            return out;
        }
        MutableComponent aff = Component.translatable("pioneer.pioneers.affinity").copy();
        out.add(aff.withStyle(s -> s.withColor(0xC8C0B0)));
        for (String a : v.affinities()) {
            out.add(Component.literal("  · ").append(skillName(a)).withStyle(s -> s.withColor(0xC8C0B0)));
        }
        out.add(Component.translatable("pioneer.pioneers.advantage", Component.translatable("trait.pioneers." + v.advantage().id()))
              .withStyle(s -> s.withColor(0x7FCF7A)));
        out.add(Component.translatable("trait.pioneers." + v.advantage().id() + ".desc").withStyle(s -> s.withColor(0xA9D8A2)));
        out.add(Component.translatable("pioneer.pioneers.defect", Component.translatable("trait.pioneers." + v.defect().id()))
              .withStyle(s -> s.withColor(0xE48A7E)));
        out.add(Component.translatable("trait.pioneers." + v.defect().id() + ".desc").withStyle(s -> s.withColor(0xE8B3AA)));
        return out;
    }

    public static int level(PioneerProfile p, String skill) {
        return PioneerApi.levelOf(p.xp(skill));
    }

    // ------------------------------------------------------------------ 1.9.1 (retours de Guillaume) : mesure des textes
    /**
     * Echelle a laquelle {@code text} tient dans {@code w} : {@code scale} s'il tient, sinon reduite jusqu'a {@link #minScale} ;
     * renvoie {@code -1} si meme a cette echelle il deborde (il faut alors le couper : {@link #ellipsize}).
     */
    public static float fitScale(Font font, FormattedText text, float w, float scale) {
        int full = font.width(text);
        if (full * scale <= w + 0.01F) {
            return scale;
        }
        float k = w / Math.max(1, full);
        return k >= minScale(scale) ? k : -1;
    }

    /** Plus petite echelle permise pour un texte prevu a {@code scale} : jamais sous 0,75 (un texte deja a 0,75 n'est que coupe). */
    public static float minScale(float scale) {
        return scale <= MIN_SCALE ? scale : Math.max(MIN_SCALE, scale * MIN_SCALE);
    }

    /** Texte coupe a la largeur {@code w} (en unites de police) avec « … » a la fin. */
    public static String ellipsize(Font font, String s, float w) {
        int room = (int) Math.floor(w) - font.width("…");
        return font.plainSubstrByWidth(s, Math.max(0, room)).stripTrailing() + "…";
    }

    /** Texte brut d'une ligne deja coupee par la police. */
    public static String plain(FormattedCharSequence seq) {
        StringBuilder b = new StringBuilder();
        seq.accept((i, style, cp) -> {
            b.appendCodePoint(cp);
            return true;
        });
        return b.toString();
    }

    /**
     * Lignes d'un nom court (noms de vocation des cartes) coupe apres les espaces ET les traits d'union (« Mineur- » / « Fondeur »), en
     * unites de police ; un mot plus long que {@code w} reste seul sur sa ligne (l'appelant le reduit ou le coupe).
     */
    public static List<String> splitWords(Font font, String s, float w) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            cur.append(c);
            if (c == ' ' || c == '-') {
                tokens.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String t : tokens) {
            if (line.length() > 0 && font.width((line + t).stripTrailing()) > w) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            line.append(t);
        }
        if (line.length() > 0) {
            lines.add(line.toString().stripTrailing());
        }
        return lines;
    }

    // ------------------------------------------------------------------ 1.9.1 (retours de Guillaume) : portrait en pied
    /**
     * Le pionnier en pied, pieds en ({@code cx}, {@code footY}) dans le repere courant de {@code g}, {@code scale} px par bloc, tourne de
     * {@code yaw} degres (0 = de face), sans inclinaison (les pieds restent sur la ligne). Remplace
     * {@code InventoryScreen.renderEntityInInventoryFollowsMouse}, dont les « coordonnees de souris » sont des ecarts (on lui passait des
     * positions : personnage penche et decale) et qu'on decalait deux fois du coin de la page.
     */
    public static void pioneer(GuiGraphics g, int cx, int footY, int scale, float yaw, LivingEntity e) {
        float body = e.yBodyRot, bodyO = e.yBodyRotO, yRot = e.getYRot(), xRot = e.getXRot(), headO = e.yHeadRotO, head = e.yHeadRot;
        e.yBodyRot = 180F + yaw;
        e.yBodyRotO = e.yBodyRot;
        e.setYRot(180F + yaw);
        e.setXRot(0F);
        e.yHeadRot = e.getYRot();
        e.yHeadRotO = e.getYRot();
        InventoryScreen.renderEntityInInventory(g, cx, footY, scale, new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), e);
        e.yBodyRot = body;
        e.yBodyRotO = bodyO;
        e.setYRot(yRot);
        e.setXRot(xRot);
        e.yHeadRotO = headO;
        e.yHeadRot = head;
    }

    /** Echelle (px par bloc) d'un personnage cadre en entier dans {@code w x h} avec une marge de {@code pad}. */
    public static int pioneerScale(int w, int h, int pad) {
        return (int) Math.max(8, Math.min((h - 2 * pad) / PIONEER_H, (w - 2 * pad) / PIONEER_W));
    }

    /** Icone d'interface avec melange alpha (sans lui, un pixel translucide etait dessine opaque : emblemes d'avant 1.9.1). */
    public static void blitBlended(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y, 0, 0, size, size, size, size);
        RenderSystem.disableBlend();
    }

    /** Vocations choisissables (le Commercant attend l'interrupteur de l'economie). */
    public static List<PioneerDefs.Vocation> vocations() {
        List<PioneerDefs.Vocation> out = new ArrayList<>();
        for (PioneerDefs.Vocation v : PioneerDefs.get().orderedVocations()) {
            if (PioneerApi.hasFeature(v.feature())) {
                out.add(v);
            }
        }
        return out;
    }
}
