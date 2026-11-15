package fr.pioneers.logistics.robotics;

import java.util.Locale;

/**
 * Les trois familles d'unites robotiques et leur caractere.
 * <ul>
 *     <li><b>Drone</b> : le plus rapide, vole au-dessus des obstacles, mais emporte peu et travaille lentement.</li>
 *     <li><b>Robot</b> : l'ouvrier : recolte, abat, mine et creuse bien plus vite que les autres.</li>
 *     <li><b>Rover</b> : lent, mais une soute colossale (54 piles, 512 000 mB) : le transport de masse.</li>
 *     <li><b>Tortue</b> : lente, mais tres econe en energie, bonne ouvriere et bonne soute (18 piles) ; elle dort sur place entre deux
 *     travaux (mode repos), sans consommer.</li>
 * </ul>
 */
public enum UnitKind {
    //            vitesse  piles  fluide(mB)  travail  FE/t   vol    cote du hub  batterie  FE/bloc  FE/travail  couverture
    //Drone : le plus rapide ; robot : le meilleur ouvrier (travaille trois fois plus vite) ; rover : une soute colossale
    DRONE(0.95, 1, 2_000, 0.6, 40, true, 3, 80_000, 50, 300, 24),
    ROBOT(0.75, 4, 8_000, 3.0, 70, false, 5, 200_000, 120, 500, 32),
    ROVER(0.60, 54, 512_000, 0.7, 120, false, 7, 600_000, 360, 900, 40),
    //Tortue : lente, mais tres econe en energie, bonne ouvriere et bonne soute ; seule a savoir dormir sur place (mode repos)
    TURTLE(0.5, 18, 64_000, 2.2, 30, false, 3, 400_000, 12, 90, 28);

    /** Multiplicateur de vitesse de deplacement (navigation). */
    public final double speed;
    /** Piles d'objets transportees. */
    public final int stacks;
    /** Fluide, gaz ou donnees transportes (mB / unites). */
    public final int fluid;
    /** Vitesse de travail (plus grand = plus rapide). */
    public final double work;
    /** Consommation de base d'une unite en mission (FE/t). */
    public final int energy;
    public final boolean flying;
    /** Cote du hub (cube) qui accueille ce genre d'unites. */
    public final int hubSize;
    /** Batterie (FE), consommation par bloc parcouru et par action de travail. */
    public final long battery;
    public final int drainPerBlock, drainPerWork;
    /** Couverture (blocs autour du centre) d'un hub de ce genre. */
    public final int coverage;

    UnitKind(double speed, int stacks, int fluid, double work, int energy, boolean flying, int hubSize, long battery, int drainPerBlock,
          int drainPerWork, int coverage) {
        this.speed = speed;
        this.stacks = stacks;
        this.fluid = fluid;
        this.work = work;
        this.energy = energy;
        this.flying = flying;
        this.hubSize = hubSize;
        this.battery = battery;
        this.drainPerBlock = drainPerBlock;
        this.drainPerWork = drainPerWork;
        this.coverage = coverage;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Aptitude a une tache : multiplie la vitesse de travail (1 = normal). */
    public double affinity(RobotTask task) {
        return switch (this) {
            case DRONE -> switch (task) {
                case COLLECTOR, FARMER, APIARIST -> 1.35;
                case LOGISTICS, HUNTER -> 1.15;
                case MINER, EXCAVATOR, LUMBERJACK -> 0.6;
                default -> 1.0;
            };
            case ROBOT -> switch (task) {
                case BREEDER, FISHER -> 1.25;
                default -> 1.1;
            };
            case ROVER -> switch (task) {
                case MINER, EXCAVATOR -> 1.6;
                case LUMBERJACK, LOGISTICS -> 1.3;
                case FISHER, BREEDER -> 0.7;
                default -> 0.9;
            };
            case TURTLE -> switch (task) {
                case FARMER, BREEDER, FISHER, APIARIST -> 1.3;
                case LUMBERJACK, COLLECTOR -> 1.1;
                default -> 1.0;
            };
        };
    }
}
