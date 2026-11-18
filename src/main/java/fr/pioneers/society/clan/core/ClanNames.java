package fr.pioneers.society.clan.core;

import java.text.Normalizer;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * 1.9 (MAJ RP, bloc 4) : identite d'un clan : nom (3 a 24 caracteres, lettres, chiffres, espaces, apostrophes et traits d'union, filtre de
 * mots refuses), etiquette (2 a 4 lettres, en capitales), devise (60 caracteres au plus), couleur (une des 16 teintures). Classe pure : chaque
 * methode renvoie la cle de langue du refus, ou {@code null} si la valeur est bonne.
 */
public final class ClanNames {

    private ClanNames() {
    }

    /** Nom nettoye : espaces en trop retires. */
    public static String cleanName(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /** Etiquette nettoyee : capitales, sans espace. */
    public static String cleanTag(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static String cleanMotto(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    @Nullable
    public static String checkName(String name, ClanDefs.Rules r) {
        int n = name.codePointCount(0, name.length());
        if (n < r.nameMin() || n > r.nameMax()) {
            return "clan.pioneers.error.name_length";
        }
        for (int i = 0; i < name.length(); ) {
            int c = name.codePointAt(i);
            if (!(Character.isLetterOrDigit(c) || c == ' ' || c == '\'' || c == '’' || c == '-')) {
                return "clan.pioneers.error.name_chars";
            }
            i += Character.charCount(c);
        }
        if (!Character.isLetter(name.codePointAt(0))) {
            return "clan.pioneers.error.name_chars";
        }
        return banned(name, r) ? "clan.pioneers.error.name_banned" : null;
    }

    @Nullable
    public static String checkTag(String tag, ClanDefs.Rules r) {
        int n = tag.codePointCount(0, tag.length());
        if (n < r.tagMin() || n > r.tagMax()) {
            return "clan.pioneers.error.tag_length";
        }
        for (int i = 0; i < tag.length(); ) {
            int c = tag.codePointAt(i);
            if (!Character.isLetter(c)) {
                return "clan.pioneers.error.tag_chars";
            }
            i += Character.charCount(c);
        }
        return banned(tag, r) ? "clan.pioneers.error.name_banned" : null;
    }

    @Nullable
    public static String checkMotto(String motto, ClanDefs.Rules r) {
        if (motto.codePointCount(0, motto.length()) > r.mottoMax()) {
            return "clan.pioneers.error.motto_length";
        }
        for (int i = 0; i < motto.length(); ) {
            int c = motto.codePointAt(i);
            if (Character.isISOControl(c) || c == '§') {
                return "clan.pioneers.error.motto_chars";
            }
            i += Character.charCount(c);
        }
        return banned(motto, r) ? "clan.pioneers.error.name_banned" : null;
    }

    @Nullable
    public static String checkColor(int color) {
        return color < 0 || color > 15 ? "clan.pioneers.error.color" : null;
    }

    /** Filtre : texte replie (sans accents, minuscules, chiffres « leet » courants) puis recherche de chaque mot refuse. */
    public static boolean banned(String text, ClanDefs.Rules r) {
        String folded = fold(text);
        String compact = folded.replace(" ", "").replace("-", "").replace("'", "");
        for (String w : r.bannedWords()) {
            String f = fold(w);
            if (!f.isEmpty() && (folded.contains(f) || compact.contains(f.replace(" ", "")))) {
                return true;
            }
        }
        return false;
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return n.replace('0', 'o').replace('1', 'i').replace('3', 'e').replace('4', 'a').replace('5', 's').replace('7', 't').replace('@', 'a')
              .replace('$', 's').replace('’', '\'');
    }
}
