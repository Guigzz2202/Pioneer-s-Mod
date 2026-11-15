package fr.pioneers.bronze.steam;

import java.util.Locale;

/**
 * Pieces de la chaudiere modulaire : corps (eau + reserve de vapeur du metal), dome (sortie de vapeur, reserve), cheminee (tirage).
 * 1.0.2-beta (lot 3, plus de pression) : chaque piece apporte sa RESERVE de vapeur (mB, {@link SteamRules}) ; l'acier en tient plus que le cuivre.
 */
public enum BoilerPart {
    COPPER_SHELL(SteamRules.COPPER_SHELL_STEAM),
    STEEL_SHELL(SteamRules.STEEL_SHELL_STEAM),
    STEAM_DOME(SteamRules.DOME_STEAM),
    CHIMNEY(0);

    /** 4 000 mB d'eau par corps. */
    public static final int WATER = 4_000, MAX_PARTS = 12, MAX_CHIMNEY = 8;
    /** Reserve de vapeur de la piece (mB). */
    public final int steam;

    BoilerPart(int steam) {
        this.steam = steam;
    }

    public boolean shell() {
        return this == COPPER_SHELL || this == STEEL_SHELL;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace("_shell", "_boiler_shell");
    }
}
