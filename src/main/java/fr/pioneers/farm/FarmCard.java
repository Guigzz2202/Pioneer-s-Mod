package fr.pioneers.farm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fiche au regard (culture, poste, animal, poisson, pieces de l'age mecanique...) : icone, titre, couleur du liseré, lignes. Donnees
 * communes ; le dessin est cote client ({@code client/farm/FarmHud}).
 * <p>
 * 1.6 (Guillaume : « beaucoup trop d'informations, il faut etre synthetique, et les informations debordent ») : une fiche = un titre et
 * 2 a 4 lignes courtes (etat, avancement, ce qui manque), plus au plus une indication courte ({@link Line#hint}). Les lignes a libelle
 * ({@link Line#pair}, {@link Line#gauge}) alignent leurs valeurs en colonne ; le dessin calcule la largeur sur le texte reel (bornee) et
 * coupe proprement ce qui depasse.
 */
public record FarmCard(ItemStack icon, Component title, int accent, List<Line> lines) {

    /** Sorte de ligne (ordre = codage reseau : ajouter en fin). */
    public enum Kind {
        /** Texte seul, colore par son style (deux rangs au plus). */
        TEXT,
        /** Indication courte, grisee (un rang). */
        HINT,
        /** Libelle a gauche, valeur alignee en colonne. */
        PAIR,
        /** Libelle (facultatif), jauge 0..1, valeur. */
        GAUGE,
        /** Icones d'objets avec leur nombre et, au besoin, une petite jauge d'avancement sous chacune. */
        ITEMS
    }

    /**
     * Une ligne. {@code text} = texte (TEXT, HINT) ou libelle (PAIR, GAUGE ; null = jauge sans libelle) ; {@code value} = valeur ;
     * {@code bar} = jauge 0..1 (-1 : aucune) ; {@code colour} = couleur de la jauge, ou de la valeur d'une PAIR ; {@code items} et
     * {@code fill} (0..1 par objet, -1 : sans jauge) pour ITEMS.
     */
    public record Line(Kind kind, @Nullable Component text, @Nullable Component value, float bar, int colour, List<ItemStack> items, float[] fill) {

        /** Ancienne forme (texte, ou jauge suivie de son texte). */
        public Line(Component text, float bar, int colour) {
            this(bar >= 0 ? Kind.GAUGE : Kind.TEXT, bar >= 0 ? null : text, bar >= 0 ? text : null, bar, colour, List.of(), new float[0]);
        }

        public static Line text(Component text) {
            return new Line(Kind.TEXT, text, null, -1, 0, List.of(), new float[0]);
        }

        public static Line text(Component text, int colour) {
            return text(text.copy().withStyle(s -> s.withColor(colour)));
        }

        /** Ancienne jauge : barre puis texte (sans colonne de libelles). */
        public static Line bar(Component text, float value, int colour) {
            return new Line(Kind.GAUGE, null, text, clamp(value), colour, List.of(), new float[0]);
        }

        /** Indication courte en gris (au plus une par fiche, et seulement si elle aide vraiment). */
        public static Line hint(Component text) {
            return new Line(Kind.HINT, text, null, -1, 0, List.of(), new float[0]);
        }

        /** Libelle et valeur (valeur dans la couleur donnee, sauf si elle porte deja la sienne). */
        public static Line pair(Component label, Component value, int colour) {
            return new Line(Kind.PAIR, label, value, -1, colour, List.of(), new float[0]);
        }

        /** Libelle, jauge (couleur {@code colour}) et valeur. */
        public static Line gauge(Component label, float ratio, Component value, int colour) {
            return new Line(Kind.GAUGE, label, value, clamp(ratio), colour, List.of(), new float[0]);
        }

        /** Rangee d'icones (contenu d'un poste...). */
        public static Line items(List<ItemStack> items) {
            float[] none = new float[items.size()];
            Arrays.fill(none, -1);
            return items(items, none);
        }

        /** Rangee d'icones avec l'avancement de chacune (0..1 ; -1 : pas de jauge). */
        public static Line items(List<ItemStack> items, float[] fill) {
            return new Line(Kind.ITEMS, null, null, -1, 0, List.copyOf(items), fill.clone());
        }

        private static float clamp(float v) {
            return Float.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
        }
    }

    /** Bloc ou tuile qui a une fiche au regard (blocs de la ferme animee). */
    public interface Source {

        @Nullable
        FarmCard card(boolean sneaking);
    }

    public static FarmCard of(ItemStack icon, Component title, int accent) {
        return new FarmCard(icon, title, accent, new ArrayList<>());
    }

    public FarmCard add(Line line) {
        lines.add(line);
        return this;
    }

    // ------------------------------------------------------------------ vocabulaire commun (cles card.pioneers.*)

    public static final int OK = 0x6EDC5A, WARN = 0xF0B040, BAD = 0xF05050, INFO = 0xD8DCE0, DIM = 0xA0A0A0, WATER = 0x4A8AF0;

    /** Libelle commun {@code card.pioneers.<cle>}. */
    public static MutableComponent label(String key) {
        return Component.translatable("card.pioneers." + key);
    }

    /** Valeur commune {@code card.pioneers.<cle>} (avec arguments). */
    public static MutableComponent word(String key, Object... args) {
        return Component.translatable("card.pioneers." + key, args);
    }

    /** « 62 % ». */
    public static MutableComponent percent(float ratio) {
        return Component.translatable("card.pioneers.percent", Math.round(Math.max(0, Math.min(1, ratio)) * 100));
    }

    /** « 3/8 ». */
    public static MutableComponent ratio(long a, long b) {
        return Component.translatable("card.pioneers.ratio", a, b);
    }

    /** « 250 mB ». */
    public static MutableComponent millibuckets(int mb) {
        return Component.translatable("card.pioneers.mb", mb);
    }

    /** Duree courte : « 45 s », « 3 min 05 », « 1 h 12 ». */
    public static MutableComponent duration(long seconds) {
        long s = Math.max(0, seconds);
        if (s < 60) {
            return Component.translatable("card.pioneers.seconds", s);
        }
        if (s < 3600) {
            return Component.translatable("card.pioneers.minutes", s / 60, String.format(Locale.ROOT, "%02d", s % 60));
        }
        return Component.translatable("card.pioneers.hours", s / 3600, String.format(Locale.ROOT, "%02d", s / 60 % 60));
    }

    /** Pile courte : « 3 × Charbon » (ou le nom seul). */
    public static MutableComponent stack(ItemStack s) {
        return s.getCount() > 1 ? Component.literal(s.getCount() + " × ").append(s.getHoverName()) : s.getHoverName().copy();
    }

    /** Couleur d'une reserve qui se vide : verte, orange sous 50 %, rouge sous 25 %. */
    public static int level(float ratio) {
        return ratio >= 0.5F ? OK : ratio >= 0.25F ? WARN : BAD;
    }
}
