package fr.pioneers.core;

import java.text.NumberFormat;
import java.util.Locale;

/** Mise en forme des nombres (separateurs de milliers selon la langue de la JVM, comme Mekanism). */
public final class Texts {

    private static final ThreadLocal<NumberFormat> INTEGER = ThreadLocal.withInitial(NumberFormat::getIntegerInstance);

    private Texts() {
    }

    public static String format(long value) {
        return INTEGER.get().format(value);
    }

    /**
     * 0.20.46 (audit) : entier avec espaces entre milliers (« 12 500 »), identique quelle que soit la langue du systeme. Remplace les
     * {@code String.format("%,d", n).replace(',', ' ')} recopies (qui ne marchaient pas sous un systeme en francais : le separateur
     * produit n'etait pas une virgule).
     */
    public static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    /** Energie lisible : 950 FE, 12,5 kFE, 3,20 MFE, 1,05 GFE. */
    public static String energy(long fe) {
        if (fe < 1_000) {
            return fe + " FE";
        } else if (fe < 1_000_000) {
            return String.format("%.1f kFE", fe / 1_000.0);
        } else if (fe < 1_000_000_000) {
            return String.format("%.2f MFE", fe / 1_000_000.0);
        }
        return String.format("%.2f GFE", fe / 1_000_000_000.0);
    }
}
