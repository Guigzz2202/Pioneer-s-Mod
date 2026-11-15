package fr.pioneers.client.guide;

import fr.pioneers.client.guide.GuideBook.Chapter;
import fr.pioneers.client.guide.GuideBook.Page;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * 1.4.2 : recherche plein texte du guide. Les mots de la requete (2 lettres au moins) sont compares sans accents ni majuscules ; une page
 * repond si TOUS les mots y figurent (titre, sous-partie, chapitre, noms des objets montres ou texte). Score : titre &gt; chapitre et
 * sous-partie &gt; objets &gt; nombre d'occurrences dans le texte. Les textes sont prepares une fois par contenu du guide.
 */
public final class GuideSearch {

    /** Une page trouvee : position dans le guide, score, extrait du texte (mots trouves surlignes). */
    public record Hit(int chapter, int page, int score, String snippet) {}

    /** Texte d'une page prepare pour la recherche : texte brut (sans codes de mise en forme) et sa version repliee (meme longueur). */
    private record Prepared(String title, String context, String items, String plain, String folded) {}

    private static final Map<List<Chapter>, List<List<Prepared>>> CACHE = new IdentityHashMap<>();
    /** Code de surlignage des mots trouves (jaune). */
    public static final String MARK = "§e";

    private GuideSearch() {
    }

    /** Minuscules sans accents, caractere par caractere (la longueur ne change pas : les positions restent valables). */
    public static String fold(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) {
                String n = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                c = n.isEmpty() ? c : n.charAt(0);
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    /** Retire les codes de mise en forme (§x). */
    public static String strip(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Mots de la requete, replies, d'au moins 2 caracteres. */
    public static List<String> terms(String query) {
        List<String> out = new ArrayList<>();
        for (String word : fold(query).split("[\\s,;:.!?'’\"()«»]+")) {
            if (word.length() >= 2 && !out.contains(word)) {
                out.add(word);
            }
        }
        return out;
    }

    private static List<List<Prepared>> prepared(List<Chapter> chapters) {
        return CACHE.computeIfAbsent(chapters, list -> {
            CACHE.clear();
            List<List<Prepared>> out = new ArrayList<>();
            for (Chapter chapter : list) {
                List<Prepared> pages = new ArrayList<>();
                for (Page page : chapter.pages()) {
                    StringBuilder items = new StringBuilder();
                    for (ItemStack stack : page.items()) {
                        items.append(stack.getHoverName().getString()).append(' ');
                    }
                    for (List<GuideBook.Node> row : page.flow()) {
                        for (GuideBook.Node node : row) {
                            items.append(node.label()).append(' ');
                            if (!node.stack().isEmpty()) {
                                items.append(node.stack().getHoverName().getString()).append(' ');
                            }
                        }
                    }
                    String plain = strip(page.text()).replace('\n', ' ');
                    pages.add(new Prepared(fold(strip(page.title())), fold(chapter.title() + " " + page.section()), fold(items.toString()), plain,
                          fold(plain)));
                }
                out.add(pages);
            }
            return out;
        });
    }

    /** Pages qui contiennent tous les mots, de la plus pertinente a la moins pertinente (au plus {@code max}). */
    public static List<Hit> search(List<Chapter> chapters, String query, int max) {
        List<String> terms = terms(query);
        List<Hit> hits = new ArrayList<>();
        if (terms.isEmpty()) {
            return hits;
        }
        List<List<Prepared>> all = prepared(chapters);
        //Expression entiere (« haut fourneau ») : bonus quand les mots se suivent
        String phrase = terms.size() > 1 ? String.join(" ", terms) : null;
        for (int c = 0; c < all.size(); c++) {
            for (int p = 0; p < all.get(c).size(); p++) {
                Prepared page = all.get(c).get(p);
                int score = 0;
                boolean every = true;
                for (String term : terms) {
                    int s = 0;
                    if (page.title().contains(term)) {
                        s += 12;
                    }
                    if (page.context().contains(term)) {
                        s += 5;
                    }
                    if (page.items().contains(term)) {
                        s += 4;
                    }
                    s += Math.min(6, count(page.folded(), term));
                    if (s == 0) {
                        every = false;
                        break;
                    }
                    score += s;
                }
                if (every) {
                    if (phrase != null) {
                        score += (page.title().contains(phrase) ? 20 : 0) + Math.min(3, count(page.folded(), phrase)) * 6;
                    }
                    hits.add(new Hit(c, p, score, snippet(page, terms, phrase)));
                }
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed().thenComparingInt(Hit::chapter).thenComparingInt(Hit::page));
        return hits.size() > max ? new ArrayList<>(hits.subList(0, max)) : hits;
    }

    private static int count(String text, String term) {
        int n = 0;
        for (int i = text.indexOf(term); i >= 0; i = text.indexOf(term, i + term.length())) {
            n++;
        }
        return n;
    }

    /** Extrait d'environ 150 caracteres autour de l'expression entiere, sinon du premier mot trouve dans le texte, mots surlignes. */
    private static String snippet(Prepared page, List<String> terms, String phrase) {
        int at = phrase == null ? -1 : page.folded().indexOf(phrase);
        for (String term : at >= 0 ? List.<String>of() : terms) {
            int i = page.folded().indexOf(term);
            if (i >= 0 && (at < 0 || i < at)) {
                at = i;
            }
        }
        String plain = page.plain();
        if (at < 0) {
            at = 0;
        }
        int start = Math.max(0, at - 45);
        while (start > 0 && start < at && plain.charAt(start - 1) != ' ') {
            start++;
        }
        int end = Math.min(plain.length(), start + 150);
        while (end < plain.length() && end > start + 100 && plain.charAt(end) != ' ') {
            end--;
        }
        String cut = plain.substring(start, end).trim();
        return (start > 0 ? "… " : "") + highlight(cut, terms) + (end < plain.length() ? " …" : "");
    }

    /**
     * Surligne les mots dans un texte qui peut contenir des codes de mise en forme : chaque occurrence est encadree de {@link #MARK} et
     * d'un retour au style en cours (codes rejoues depuis le dernier §r, une couleur annulant ce qui precede comme dans Minecraft).
     */
    public static String highlight(String raw, List<String> terms) {
        if (terms.isEmpty()) {
            return raw;
        }
        String folded = fold(raw);
        boolean[] marked = new boolean[raw.length()];
        boolean any = false;
        for (String term : terms) {
            for (int i = folded.indexOf(term); i >= 0; i = folded.indexOf(term, i + 1)) {
                if (i > 0 && raw.charAt(i - 1) == '§') {
                    continue;
                }
                for (int k = i; k < i + term.length(); k++) {
                    marked[k] = true;
                }
                any = true;
            }
        }
        if (!any) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length() + 32);
        StringBuilder active = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '§' && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                if (code == 'r') {
                    active.setLength(0);
                } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    active.setLength(0);
                    active.append('§').append(code);
                } else {
                    active.append('§').append(code);
                }
                out.append(c).append(raw.charAt(i + 1));
                i += 2;
                continue;
            }
            if (marked[i]) {
                int j = i;
                while (j < raw.length() && marked[j] && raw.charAt(j) != '§') {
                    j++;
                }
                out.append(MARK).append(raw, i, j).append("§r").append(active);
                i = j;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Le texte (code compris) contient-il un des mots ? */
    public static boolean contains(String raw, List<String> terms) {
        String folded = fold(strip(raw));
        for (String term : terms) {
            if (folded.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
