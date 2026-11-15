package fr.pioneers.core.tooltip;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * 1.11.2 (lot E2, demande de Guillaume : « Unifier la presentation des tooltips sur tous les objets du mod ») : SEULE facon d'ajouter des
 * lignes d'infobulle a un objet du mod (methodes {@code appendHoverText} des objets et des blocs, {@code DescribedBlockItem}, evenements
 * {@code ItemTooltipEvent} du client). Chaque ligne a un ROLE, qui fixe sa couleur et sa place, dans cet ordre :
 * <ol>
 *     <li>{@link Section#DESC} : description courte, gris ; une description longue ne montre que sa premiere phrase, le reste sous Maj ;</li>
 *     <li>{@link Section#STATS} : caracteristiques « cle : valeur » (cle grise, valeur blanche ; energie jaune) ;</li>
 *     <li>{@link Section#TIER} : tier MK, dans la couleur du tier (vert, bleu, rouge, bordeaux ; creatif violet) ;</li>
 *     <li>{@link Section#STATE} : ce que porte cette pile (contenu, chargement, programme, verrou), aqua ;</li>
 *     <li>{@link Section#NOTES} : remarques : particularite (or), danger ou manque (rouge), atout (vert) ;</li>
 *     <li>{@link Section#HINTS} : aide d'usage (clic droit...), gris fonce, puis le rappel « Maj : details » quand il y a plus a lire.</li>
 * </ol>
 * Les lignes sont inserees a la fin de leur section (l'ordre d'appel ne compte pas : une remarque ajoutee avant une caracteristique
 * s'affiche apres elle). Code commun, sans classe du client : l'etat de la touche Maj est fourni par le client ({@link #shiftKey}).
 */
public final class Tooltips {

    /** Sections, dans l'ordre d'affichage. */
    public enum Section { DESC, STATS, TIER, STATE, NOTES, HINTS }

    // ------------------------------------------------------------------ couleurs fixes (une par role)
    public static final ChatFormatting DESC = ChatFormatting.GRAY, LABEL = ChatFormatting.GRAY, VALUE = ChatFormatting.WHITE,
          ENERGY = ChatFormatting.YELLOW, STATE = ChatFormatting.AQUA, NOTE = ChatFormatting.GOLD, WARN = ChatFormatting.RED,
          GOOD = ChatFormatting.GREEN, HINT = ChatFormatting.DARK_GRAY;
    /** Couleurs des tiers lisibles sur le fond d'une infobulle (MKI vert, MKII bleu, MKIII rouge, MKIV bordeaux, creatif violet). */
    private static final int[] TIER_RGB = {0x5FD068, 0x5C9BFF, 0xFF5A50, 0xD0507A, 0xB040FF};
    private static final String[] TIER_IDS = {"basic", "advanced", "elite", "ultimate"};
    /** Au-dela, une description est « longue » : premiere phrase seule, le reste sous Maj. */
    public static final int SHORT = 110;
    public static final String HOLD_SHIFT = "tooltip.pioneers.hold_shift";

    private static BooleanSupplier shift = () -> false;
    /** Derniere infobulle en cours de construction (par fil) : un bloc et son objet ecrivent dans la meme, chacun a sa place. */
    private static final ThreadLocal<Tooltips> LAST = new ThreadLocal<>();

    private final List<Component> lines;
    private final int base;
    private final int[] counts = new int[Section.values().length];
    /** Taille de la liste apres notre derniere ecriture (une autre ecriture entre-temps : nouvelle infobulle en dessous). */
    private int size;

    private Tooltips(List<Component> lines) {
        this.lines = lines;
        this.base = lines.size();
        this.size = base;
    }

    /** Client (mise en place) : etat de la touche Maj. */
    public static void shiftKey(BooleanSupplier supplier) {
        shift = supplier;
    }

    /** Maj enfoncee (toujours faux sur un serveur). */
    public static boolean shift() {
        return shift.getAsBoolean();
    }

    /**
     * Infobulle de cette liste : la meme que celle en cours si personne n'y a ecrit depuis (objet puis bloc : {@code DescribedBlockItem}
     * appelle {@code Block.appendHoverText}, les deux rangent leurs lignes dans les memes sections), sinon une nouvelle sous les lignes
     * deja presentes.
     */
    public static Tooltips of(List<Component> lines) {
        Tooltips t = LAST.get();
        if (t != null && t.lines == lines && t.size == lines.size()) {
            return t;
        }
        t = new Tooltips(lines);
        LAST.set(t);
        return t;
    }

    /**
     * Infobulle d'un {@code ItemTooltipEvent} (client) : celle que l'objet a construite dans cette liste s'il y en a une (les lignes de
     * l'evenement prennent leur place dans ses sections, au-dessus des lignes vanilla : attributs, identifiant...), sinon une nouvelle sous
     * le nom de l'objet.
     */
    public static Tooltips event(List<Component> lines) {
        Tooltips t = LAST.get();
        if (t != null && t.lines == lines && t.base + t.written() <= lines.size()) {
            return t;
        }
        t = new Tooltips(lines, Math.min(1, lines.size()));
        LAST.set(t);
        return t;
    }

    private Tooltips(List<Component> lines, int base) {
        this.lines = lines;
        this.base = base;
        this.size = lines.size();
    }

    private int written() {
        int n = 0;
        for (int c : counts) {
            n += c;
        }
        return n;
    }

    // ------------------------------------------------------------------ insertion par section

    public Tooltips add(Section section, Component line) {
        int at = base;
        for (int i = 0; i <= section.ordinal(); i++) {
            at += counts[i];
        }
        lines.add(Math.min(at, lines.size()), line);
        counts[section.ordinal()]++;
        size = lines.size();
        return this;
    }

    // ------------------------------------------------------------------ 1. description

    /** Description (cle de langue) : courte en entier ; longue : premiere phrase, le reste sous Maj. */
    public Tooltips desc(String key, Object... args) {
        return desc(Component.translatable(key, args));
    }

    public Tooltips desc(Component text) {
        String s = text.getString();
        if (s.length() <= SHORT || shift()) {
            return add(Section.DESC, plain(text, DESC));
        }
        String first = firstSentence(s);
        if (first.length() >= s.length()) {
            return add(Section.DESC, plain(text, DESC));
        }
        add(Section.DESC, Component.literal(first).withStyle(DESC));
        return more();
    }

    /** Description qui ne se lit que sous Maj (rappel sinon). */
    public Tooltips descOnShift(String key, Object... args) {
        if (shift()) {
            return add(Section.DESC, Component.translatable(key, args).withStyle(DESC));
        }
        return more();
    }

    /** Premiere phrase d'un texte (jusqu'au premier point suivi d'une espace, ou au premier « ; »). */
    static String firstSentence(String s) {
        int best = -1;
        for (String end : new String[]{". ", " ; ", "; ", " : ", "! ", "? "}) {
            int i = s.indexOf(end);
            if (i > 20 && (best < 0 || i < best)) {
                best = i + (end.startsWith(".") || end.startsWith("!") || end.startsWith("?") ? 1 : 0);
            }
        }
        return best < 0 ? s : s.substring(0, best).trim();
    }

    // ------------------------------------------------------------------ 2. caracteristiques

    /** « Cle : valeur » (libelle en cle de langue {@code tooltip.pioneers.stat.*} ou autre, valeur deja ecrite). */
    public Tooltips stat(String labelKey, Object value) {
        return stat(labelKey, value, VALUE);
    }

    public Tooltips stat(String labelKey, Object value, ChatFormatting valueColor) {
        Component v = value instanceof Component c ? c.copy().withStyle(valueColor) : Component.literal(String.valueOf(value)).withStyle(valueColor);
        return add(Section.STATS, Component.translatable("tooltip.pioneers.stat", Component.translatable(labelKey).withStyle(LABEL), v)
              .withStyle(LABEL));
    }

    /** Energie (FE, FE/t) : valeur en jaune. */
    public Tooltips energy(String labelKey, Component value) {
        return stat(labelKey, value, ENERGY);
    }

    /**
     * Caracteristique deja redigee dans une cle de langue (« Capacite : %s mB ») : la partie avant le premier deux-points reste grise, la
     * suite passe en blanc. Sans deux-points : toute la ligne en gris.
     */
    public Tooltips spec(String key, Object... args) {
        return spec(Component.translatable(key, args));
    }

    public Tooltips spec(Component text) {
        return add(Section.STATS, keyValue(text.getString()));
    }

    /** Ligne de caracteristique dont la couleur porte une information (temperature du metal, couleur d'un clan) : gardee telle quelle. */
    public Tooltips custom(Component line) {
        return add(Section.STATS, line);
    }

    /** Puce d'une liste de caracteristiques (modules d'une fusee, fichiers d'une disquette). */
    public Tooltips item(Component text) {
        return add(Section.STATS, Component.literal("  • ").withStyle(HINT).append(text.copy().withStyle(LABEL)));
    }

    /** « Libelle : valeur » colore a partir d'un texte deja traduit. */
    public static MutableComponent keyValue(String s) {
        int i = s.indexOf(" : ");
        int skip = 3;
        if (i < 0) {
            i = s.indexOf(": ");
            skip = 2;
        }
        if (i <= 0 || i > 48) {
            return Component.literal(s).withStyle(LABEL);
        }
        return Component.literal(s.substring(0, i + skip)).withStyle(LABEL).append(Component.literal(s.substring(i + skip)).withStyle(VALUE));
    }

    // ------------------------------------------------------------------ 3. tier

    /** Tier MK (0 = MKI ... 3 = MKIV, 4 = creatif) : « Tier : MKII » dans la couleur du tier. */
    public Tooltips tier(int tier) {
        return add(Section.TIER, Component.translatable("tooltip.pioneers.stat", Component.translatable("tooltip.pioneers.tier").withStyle(LABEL),
              tierName(tier)).withStyle(LABEL));
    }

    /** Nom du tier (« MKII ») dans sa couleur. */
    public static MutableComponent tierName(int tier) {
        int t = Math.max(0, Math.min(4, tier));
        MutableComponent name = t == 4 ? Component.translatable("tooltip.pioneers.tier.creative")
                                       : Component.translatable("industry.pioneers.tier." + TIER_IDS[t]);
        return name.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(TIER_RGB[t])));
    }

    public static int tierColor(int tier) {
        return TIER_RGB[Math.max(0, Math.min(4, tier))];
    }

    /** Tier d'un identifiant (basic_..., advanced_..., elite_..., ultimate_..., creative_...) ; -1 sinon. */
    public static int tierOf(String id) {
        String p = id.toLowerCase(Locale.ROOT);
        for (int i = 0; i < TIER_IDS.length; i++) {
            if (p.startsWith(TIER_IDS[i] + "_")) {
                return i;
            }
        }
        return p.startsWith("creative_") ? 4 : -1;
    }

    // ------------------------------------------------------------------ 4. etat de la pile

    public Tooltips state(String key, Object... args) {
        return state(Component.translatable(key, args));
    }

    public Tooltips state(Component text) {
        return add(Section.STATE, plain(text, STATE));
    }

    // ------------------------------------------------------------------ 5. remarques

    /** Particularite (or). */
    public Tooltips note(String key, Object... args) {
        return add(Section.NOTES, Component.translatable(key, args).withStyle(NOTE));
    }

    public Tooltips note(Component text) {
        return add(Section.NOTES, plain(text, NOTE));
    }

    /** Danger, manque, refus (rouge). */
    public Tooltips warn(String key, Object... args) {
        return add(Section.NOTES, Component.translatable(key, args).withStyle(WARN));
    }

    public Tooltips warn(Component text) {
        return add(Section.NOTES, plain(text, WARN));
    }

    /** Atout (vert). */
    public Tooltips good(String key, Object... args) {
        return add(Section.NOTES, Component.translatable(key, args).withStyle(GOOD));
    }

    public Tooltips good(Component text) {
        return add(Section.NOTES, plain(text, GOOD));
    }

    // ------------------------------------------------------------------ 6. aide

    /** Aide d'usage (gris fonce). */
    public Tooltips hint(String key, Object... args) {
        return add(Section.HINTS, Component.translatable(key, args).withStyle(HINT));
    }

    public Tooltips hint(Component text) {
        return add(Section.HINTS, plain(text, HINT));
    }

    /** Lignes de detail : montrees sous Maj ; sinon le rappel « Maj : details ». */
    public Tooltips details(Consumer<Tooltips> more) {
        if (shift()) {
            more.accept(this);
            return this;
        }
        return more();
    }

    /** Rappel « Maj : details » (une seule fois par infobulle, toujours en dernier). */
    public Tooltips more() {
        for (Component c : lines) {
            if (c.getContents() instanceof TranslatableContents t && HOLD_SHIFT.equals(t.getKey())) {
                return this;
            }
        }
        lines.add(Component.translatable(HOLD_SHIFT).withStyle(HINT));
        size = lines.size();
        return this;
    }

    private static MutableComponent plain(Component text, ChatFormatting color) {
        return text.copy().withStyle(color);
    }
}
