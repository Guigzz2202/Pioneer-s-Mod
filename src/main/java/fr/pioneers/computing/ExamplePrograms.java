package fr.pioneers.computing;

import fr.pioneers.Pioneers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Programmes d'exemple de la disquette « exemples » (0.17.1) : lus dans les ressources du mod
 * ({@code /data/pioneers/pioscript_examples/<nom>}, ecrits par {@code tools/gc/computing/assets.py}).
 */
public final class ExamplePrograms {

    public static final String[] NAMES = {"lisez-moi.txt", "bonjour.py", "clignotant.py", "reacteur.py", "tableau.py", "compteur.py", "vol.py", "rail.py", "journal.py", "fonctions.txt"};
    public static final Map<String, String> ALL = load();

    private ExamplePrograms() {
    }

    private static Map<String, String> load() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : NAMES) {
            try (InputStream in = ExamplePrograms.class.getResourceAsStream("/data/pioneers/pioscript_examples/" + name)) {
                if (in != null) {
                    out.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                Pioneers.LOGGER.warn("Pioneer's : exemple PioScript illisible : {}", name, e);
            }
        }
        return out;
    }
}
