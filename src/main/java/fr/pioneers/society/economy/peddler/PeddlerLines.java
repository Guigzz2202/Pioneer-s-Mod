package fr.pioneers.society.economy.peddler;

/**
 * GENERE par tools/gc/economy/peddlers.py (ne pas modifier a la main) : nombre de surnoms ({@code peddler.pioneers.name.<n>}) et de
 * repliques par moment ({@code peddler.pioneers.voice.<moment>.<n>}) des Colporteurs. Les textes sont dans la langue.
 */
public final class PeddlerLines {

    public static final int NAMES = 65;

    private PeddlerLines() {
    }

    /** Nombre de repliques du moment (0 : moment inconnu). */
    public static int voices(String moment) {
        return switch (moment) {
            case "greet" -> 5;
            case "open" -> 4;
            case "broke" -> 4;
            case "quota" -> 4;
            case "refuse" -> 4;
            default -> 0;
        };
    }
}
