package fr.pioneers.fauna;

import java.util.Locale;

/**
 * 1.0.5-beta (lot 4, specification « Le monde vivant » §3) : les cinq groupes de la faune. Chaque zone de 128 x 128 blocs garde, par groupe,
 * sa densite de depart, son compte et son plafond ; une espece appartient a un seul groupe ({@link FaunaSpecies}).
 * <p>
 * PUR (aucune classe du jeu) : le banc {@code tools/fauna/FaunaBench.java} s'en sert hors jeu.
 */
public enum FaunaGroup {
    /** Gibier : betes de pature et petite faune terrestre (vaches, moutons, cochons, poules, lapins, chevaux, lamas, chevres, grenouilles...). */
    GAME(48),
    /** Predateurs : loups, renards, ours polaires, ocelots (et les vrais loups, ours, lions... des lots 7 et 8). */
    PREDATOR(8),
    /** Oiseaux : corbeaux, perroquets (et pics-verts, pigeons, mouettes... des lots 7 et 9). */
    BIRD(24),
    /** Faune marine : calmars, dauphins, tortues, axolotls, crustaces (et baleines, requins... du lot 9). */
    MARINE(16),
    /** Poissons : les poissons du mod (et les poissons vanilla deja presents, persistants mais jamais remis). */
    FISH(48);

    public static final FaunaGroup[] ALL = values();
    public static final int COUNT = ALL.length;

    /** Plafond par zone et par groupe par defaut (reglage serveur {@code fauna.cap<Groupe>}). */
    public final int defaultCap;

    FaunaGroup(int defaultCap) {
        this.defaultCap = defaultCap;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Cle de langue {@code fauna.pioneers.group.<id>}. */
    public String key() {
        return "fauna.pioneers.group." + id();
    }
}
