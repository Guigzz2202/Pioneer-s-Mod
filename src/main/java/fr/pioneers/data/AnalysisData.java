package fr.pioneers.data;

import fr.pioneers.api.data.DataType;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.tile.TileEntityAsteroidCrusher;
import org.jetbrains.annotations.Nullable;

/**
 * Donnees d'analyse : ce que produisent le broyeur d'asteroides, le spectrometre geologique et le sequenceur biologique,
 * et la recherche qu'elles nourrissent une fois arrivees dans un laboratoire.
 */
public final class AnalysisData {

    /** Donnees que vaut un echantillon (Mo). */
    public static final long PER_SAMPLE = 1_000;

    private AnalysisData() {
    }

    /** Recherche et type d'echantillon nourris par des donnees d'analyse. */
    public record Target(ResearchNode node, int type) {
    }

    /** Recherche nourrie par ce type de donnees (null si ce ne sont pas des donnees d'analyse). */
    @Nullable
    public static Target targetOf(DataType type) {
        Body body = PiDataTypes.geologicalBody(type);
        if (body != null) {
            ResearchNode node = BodyResearch.node(body, ResearchNode.Kind.GEOLOGY);
            return node == null ? null : new Target(node, 0);
        }
        body = PiDataTypes.biologicalBody(type);
        if (body != null) {
            ResearchNode node = BodyResearch.node(body, ResearchNode.Kind.BIOLOGY);
            return node == null ? null : new Target(node, 0);
        }
        int asteroid = PiDataTypes.asteroidType(type);
        return asteroid < 0 ? null : new Target(TileEntityAsteroidCrusher.currentTier(), asteroid);
    }
}
