package fr.pioneers.robotics;

import java.util.Locale;

/**
 * Ameliorations robotiques : chacune a sa contrepartie. Celles des hubs s'appliquent a toutes leurs unites ; celles des relais au relais.
 * <ul>
 *     <li>vitesse : +20 % de vitesse, mais +25 % de consommation de la batterie (par carte) ;</li>
 *     <li>soute : +40 % de soute, mais -8 % de vitesse ;</li>
 *     <li>autonomie : +40 % de batterie, mais -5 % de vitesse ;</li>
 *     <li>efficacite : -15 % de consommation, mais -8 % de vitesse de travail ;</li>
 *     <li>toucher de soie : recoltes et minage en toucher de soie, mais travail 30 % plus lent et deux fois plus gourmand ;</li>
 *     <li>fortune (1 a 3) : fortune au minage et a la recolte, mais -15 % de vitesse de travail et +50 % d'energie par niveau ;</li>
 *     <li>portee (relais) : +8 blocs de couverture, mais +50 % d'entretien ;</li>
 *     <li>charge rapide (relais) : +60 % de vitesse de charge, mais 10 % de pertes en plus ;</li>
 *     <li>IA (0.17.2, une par hub, faite d'un reseau de neurones entraine) : +25 % de vitesse, +25 % de batterie, -20 % de consommation et
 *     +15 % de vitesse de travail ; sa contrepartie est son prix.</li>
 * </ul>
 */
public enum RoboticUpgrade {
    SPEED(4, true),
    CARGO(4, true),
    BATTERY(4, true),
    EFFICIENCY(4, true),
    SILK_TOUCH(1, true),
    FORTUNE(3, true),
    RANGE(4, false),
    CHARGING(4, false),
    AI(1, true);

    public final int max;
    /** Pour les hubs (sinon pour les relais). */
    public final boolean forHub;

    RoboticUpgrade(int max, boolean forHub) {
        this.max = max;
        this.forHub = forHub;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String itemId() {
        return this == AI ? "ai_upgrade" : "robotic_upgrade_" + id();
    }
}
