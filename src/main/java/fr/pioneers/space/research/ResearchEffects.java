package fr.pioneers.space.research;

import fr.pioneers.space.SolarSystem.Body;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Effets des recherches terminees, appliques par les machines (cote serveur).
 * Tenu a jour par {@link ResearchState} ; le client a sa propre copie dans ClientResearch.
 */
public final class ResearchEffects {

    private ResearchEffects() {
    }

    private static Set<ResearchNode> completed = EnumSet.noneOf(ResearchNode.class);

    /** Appele par l'etat serveur, et cote client a chaque synchronisation (les interfaces affichent ainsi les vraies valeurs). */
    public static void update(Set<ResearchNode> nodes) {
        completed = nodes.isEmpty() ? EnumSet.noneOf(ResearchNode.class) : EnumSet.copyOf(nodes);
    }

    public static boolean has(ResearchNode node) {
        return completed.contains(node);
    }

    /** Donnees produites par les sondes : telemetrie +15 %, compression quantique +35 %, durcissement anti-radiations +25 %. */
    public static double probeDataMultiplier() {
        return 1 + (has(ResearchNode.TELEMETRY) ? 0.15 : 0) + (has(ResearchNode.DATA_COMPRESSION) ? 0.35 : 0)
               + (has(ResearchNode.RADIATION_HARDENING) ? 0.25 : 0);
    }

    /** Pertes de transmission : /2 (relais lunaires), encore /2 (relais de l'espace profond). */
    public static float transmissionLossMultiplier() {
        return (has(ResearchNode.LUNAR_RELAYS) ? 0.5F : 1) * (has(ResearchNode.DEEP_SPACE_RELAYS) ? 0.5F : 1);
    }

    /** Debit des antennes : x2 (relais lunaires), encore x2 (relais de l'espace profond). */
    public static int antennaRateMultiplier() {
        return (has(ResearchNode.LUNAR_RELAYS) ? 2 : 1) * (has(ResearchNode.DEEP_SPACE_RELAYS) ? 2 : 1);
    }

    /** Echecs de mise en orbite des sondes : -40 % (fiabilite), puis -25 % (boucliers thermiques). */
    public static float launchFailureMultiplier() {
        return (has(ResearchNode.LAUNCH_RELIABILITY) ? 0.6F : 1) * (has(ResearchNode.THERMAL_SHIELDING) ? 0.75F : 1);
    }

    /** Hydrogene des lancements automatiques et des cargos : -25 % (cryogenie), puis -20 % (aerocapture). */
    public static double hydrogenMultiplier() {
        return (has(ResearchNode.CRYO_TANKS) ? 0.75 : 1) * (has(ResearchNode.AEROCAPTURE) ? 0.8 : 1);
    }

    /** Duree des vols cargo : -30 % (ions), -20 % (voiles photoniques), -25 % (fusion). */
    public static double cargoTransitMultiplier() {
        return (has(ResearchNode.ION_PROPULSION) ? 0.7 : 1) * (has(ResearchNode.PHOTONIC_SAILS) ? 0.8 : 1) * (has(ResearchNode.FUSION_DRIVES) ? 0.75 : 1);
    }

    /** Croissance des plantes +50 %, batterie du robot deux fois plus endurante. */
    // --- Minage planetaire ---

    /** Rayon de prospection supplementaire (blocs). */
    public static int surveyRadiusBonus() {
        return has(ResearchNode.PLANETARY_PROSPECTING) ? 8 : 0;
    }

    /** Gisements supplementaires par prospection. */
    public static int extraDeposits() {
        return has(ResearchNode.PLANETARY_PROSPECTING) ? 2 : 0;
    }

    public static double drillSpeedMultiplier() {
        return has(ResearchNode.DEEP_CORE_DRILLING) ? 1.4 : 1;
    }

    public static boolean drillWearHalved() {
        return has(ResearchNode.DEEP_CORE_DRILLING);
    }

    public static double roverSpeedMultiplier() {
        return has(ResearchNode.AUTONOMOUS_ROVERS) ? 1.5 : 1;
    }

    public static double roverDrainMultiplier() {
        return has(ResearchNode.AUTONOMOUS_ROVERS) ? 0.6 : 1;
    }

    public static float byproductMultiplier() {
        return has(ResearchNode.EXOTIC_METALLURGY) ? 2 : 1;
    }

    public static float extraPrimaryChance() {
        return has(ResearchNode.EXOTIC_METALLURGY) ? 0.25F : 0;
    }

    public static boolean agronomy() {
        return has(ResearchNode.SPACE_AGRONOMY);
    }

    /** Vitesse des laboratoires : +50 % (laboratoires quantiques), +25 % (calcul photonique). */
    public static double labSpeedMultiplier() {
        return (has(ResearchNode.QUANTUM_LABS) ? 2 : 1) * (has(ResearchNode.PHOTONIC_COMPUTING) ? 1.25 : 1);
    }

    /** Production des chambres (singularite, plasma stellaire) +25 %. */
    public static double singularityBonus() {
        return has(ResearchNode.SINGULARITY_THEORY) ? 0.25 : 0;
    }

    /** Eruptions stellaires : degats de radiation divises par deux (blindage anti-eruptions). */
    public static float flareDamageMultiplier() {
        return has(ResearchNode.FLARE_SHIELDING) ? 0.5F : 1;
    }

    public static boolean isDestinationUnlocked(ProbeDestination destination) {
        ResearchNode node = unlockOf(destination);
        return node == null || has(node);
    }

    /**
     * On peut s'y poser : la recherche d'orbite de l'astre est terminee (la Terre est toujours accessible).
     * Les astres sans surface (Soleil, geantes) ne sont jamais des destinations.
     */
    public static boolean canLand(Body body) {
        if (body == Body.EARTH) {
            return true;
        }
        if (body.isGasGiant()) {
            //Geantes : la descente dans les nuages s'apprend une fois pour toutes
            return has(ResearchNode.ATMOSPHERIC_ENTRY);
        }
        if (body.sector().far()) {
            //1.0.7 (lot 11) : on s'y pose des qu'on y arrive (propulsion a matiere noire, carte du halo)
            return true;
        }
        ResearchNode orbit = BodyResearch.node(body, ResearchNode.Kind.LAB);
        return orbit != null && has(orbit);
    }

    /** Geologie de l'astre terminee : forage +25 % sur cet astre. */
    public static double geologyDrillMultiplier(@Nullable Body body) {
        ResearchNode node = body == null ? null : BodyResearch.node(body, ResearchNode.Kind.GEOLOGY);
        return node != null && has(node) ? 1.25 : 1;
    }

    /** Biologie de l'astre terminee : croissance des serres +25 % sur cet astre. */
    public static double biologyGrowthBonus(@Nullable Body body) {
        ResearchNode node = body == null ? null : BodyResearch.node(body, ResearchNode.Kind.BIOLOGY);
        return node != null && has(node) ? 0.25 : 0;
    }

    public static boolean isDiscovered(Body body) {
        ResearchNode node = discoveryOf(body);
        return node == null || has(node);
    }

    /** Recherche qui fait apparaitre un astre sur la carte (null = connu des le depart). */
    @Nullable
    public static ResearchNode discoveryOf(Body body) {
        return switch (body) {
            case SUN, EARTH, MOON -> null;
            case MARS, PHOBOS, DEIMOS -> ResearchNode.MARS_CARTOGRAPHY;
            case VENUS, MERCURY -> ResearchNode.INNER_SYSTEM;
            case ASTEROID_BELT, CERES, VESTA, PALLAS, HYGIEA -> ResearchNode.BELT_SURVEY;
            case JUPITER, IO, EUROPA, GANYMEDE, CALLISTO -> ResearchNode.JOVIAN_SYSTEM;
            case SATURN, TITAN, ENCELADUS -> ResearchNode.SATURN_SYSTEM;
            case URANUS, MIRANDA, TITANIA -> ResearchNode.URANUS_SYSTEM;
            case NEPTUNE, TRITON -> ResearchNode.NEPTUNE_SYSTEM;
            case PLUTO, CHARON -> ResearchNode.DEEP_TELESCOPE;
            case CENTAURI_A, CENTAURI_B, PROXIMA, TOLIMAN_B, PROXIMA_B, PROXIMA_D -> ResearchNode.CENTAURI_SURVEY;
            case TRAPPIST_1, TRAPPIST_1B, TRAPPIST_1C, TRAPPIST_1D, TRAPPIST_1E, TRAPPIST_1F, TRAPPIST_1G, TRAPPIST_1H -> ResearchNode.TRAPPIST_SURVEY;
            //1.0.7 (lot 11) : systemes lointains : reveles par l'Observatoire du Halo (megastructure), pas par l'arbre de recherche
            case LICH, DRAUGR, POLTERGEIST, PHOBETOR, VAN_MAANEN, VAN_MAANEN_B, VAN_MAANEN_C, VAN_MAANEN_D, GAIA_BH1, GAIA_BH1_B, GAIA_BH1_C,
                 GAIA_BH1_D, FYLGJA, VAN_MAANEN_E, GAIA_BH1_E -> null;
        };
    }

    /** Destination de sonde verrouillee : recherche necessaire (null = ouverte des le depart). */
    @Nullable
    public static ResearchNode unlockOf(ProbeDestination destination) {
        return switch (destination) {
            case EARTH_ORBIT, MOON -> null;
            case DEEP_SPACE -> ResearchNode.RADIO_ASTRONOMY;
            case MARS -> ResearchNode.MARS_CARTOGRAPHY;
            case VENUS, MERCURY -> ResearchNode.INNER_SYSTEM;
            case SUN -> ResearchNode.HELIOPHYSICS;
            case JUPITER -> ResearchNode.JOVIAN_SYSTEM;
            case SATURN -> ResearchNode.SATURN_SYSTEM;
            case URANUS -> ResearchNode.URANUS_SYSTEM;
            case NEPTUNE -> ResearchNode.NEPTUNE_SYSTEM;
            case PLUTO -> ResearchNode.DEEP_TELESCOPE;
            case CENTAURI -> ResearchNode.CENTAURI_SURVEY;
            case TRAPPIST -> ResearchNode.TRAPPIST_SURVEY;
        };
    }
}
