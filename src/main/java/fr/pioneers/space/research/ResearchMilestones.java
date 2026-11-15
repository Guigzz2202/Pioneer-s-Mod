package fr.pioneers.space.research;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Le programme spatial entremele aux ages (0.18.0).
 * <ul>
 *     <li><b>Jalons industriels</b> : certaines recherches demandent qu'un joueur du monde ait obtenu un progres des ages (titane, ergol,
 *     ordinateur de vol, fission, centre de donnees, fusion, IA...). Les jalons atteints sont notes une fois pour toutes dans
 *     {@link ResearchState} (commun au monde, comme la recherche).</li>
 *     <li><b>Plancher de calcul</b> : selon l'ere, une table de recherche ne traite une unite que si le reseau de calcul qu'elle touche lui
 *     accorde assez de GOPS (et de QOPS a partir de Pluton).</li>
 * </ul>
 * Decision de Guillaume : « la recherche spatiale se debloque au niveau du titane, de la gestion du carburant de fusee et de l'informatique
 * moyenne pour programmer les actions des sondes ».
 */
public final class ResearchMilestones {

    private static final Map<ResearchNode, List<ResourceLocation>> MILESTONES = new EnumMap<>(ResearchNode.class);
    private static final Set<ResourceLocation> ALL = new LinkedHashSet<>();

    static {
        put(ResearchNode.TELEMETRY, "industry/titanium", "space/propellant", "space/flight_computer");
        put(ResearchNode.PLANETARY_PROSPECTING, "industry/drill");
        put(ResearchNode.ROBOTICS, "chemistry/processor");
        put(ResearchNode.CRYO_TANKS, "chemistry/asu");
        put(ResearchNode.ION_PROPULSION, "nuclear/rtg");
        put(ResearchNode.DATA_COMPRESSION, "nuclear/computer");
        put(ResearchNode.INNER_SYSTEM, "nuclear/criticality");
        put(ResearchNode.BLUEPRINT_T3, "space/cryogenics");
        put(ResearchNode.AUTONOMOUS_ROVERS, "nuclear/ai_expert");
        put(ResearchNode.EXOTIC_METALLURGY, "industry/superconductor");
        put(ResearchNode.QUANTUM_LABS, "nuclear/data_center");
        put(ResearchNode.HELIOPHYSICS, "nuclear/dosimeter");
        put(ResearchNode.PHOTONIC_COMPUTING, "nuclear/laser");
        put(ResearchNode.RADIATION_HARDENING, "nuclear/hazmat");
        put(ResearchNode.JOVIAN_SYSTEM, "space/ultimate");
        put(ResearchNode.BLUEPRINT_T8, "space/ntr_core");
        put(ResearchNode.FUSION_DRIVES, "nuclear/stellarator");
        put(ResearchNode.DEEP_SPACE_RELAYS, "nuclear/quantum");
        put(ResearchNode.SINGULARITY_THEORY, "nuclear/ai_foundation");
        put(ResearchNode.BLUEPRINT_T11, "space/fusion_drive");
        put(ResearchNode.SOLAR_SIMULATION, "nuclear/factory_ai");
    }

    private ResearchMilestones() {
    }

    private static void put(ResearchNode node, String... ids) {
        List<ResourceLocation> list = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation rl = Pioneers.rl(id);
            list.add(rl);
            ALL.add(rl);
        }
        MILESTONES.put(node, List.copyOf(list));
    }

    /** Jalons (progres) demandes par une recherche. */
    public static List<ResourceLocation> of(ResearchNode node) {
        return MILESTONES.getOrDefault(node, Collections.emptyList());
    }

    /** Tous les progres qui servent de jalon. */
    public static Set<ResourceLocation> all() {
        return Collections.unmodifiableSet(ALL);
    }

    public static boolean met(ResearchNode node, Predicate<ResourceLocation> reached) {
        for (ResourceLocation id : of(node)) {
            if (!reached.test(id)) {
                return false;
            }
        }
        return true;
    }

    /** GOPS que le reseau de calcul doit accorder a une table de recherche pour cette recherche (0 = aucun). */
    public static double gopsFloor(ResearchNode node) {
        if (node.era >= 11) {
            return 16_384;
        } else if (node.era >= 8) {
            return 4_096;
        } else if (node.era >= 5) {
            return 1_024;
        } else if (node.era >= 2) {
            return 128;
        }
        return 0;
    }

    /** QOPS demandes (calcul quantique, a partir de l'ere de Pluton). */
    public static double qopsFloor(ResearchNode node) {
        if (node.era >= 11) {
            return 8;
        } else if (node.era >= 8) {
            return 1;
        }
        return 0;
    }
}
