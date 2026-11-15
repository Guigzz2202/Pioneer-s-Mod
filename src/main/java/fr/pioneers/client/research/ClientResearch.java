package fr.pioneers.client.research;

import fr.pioneers.network.PacketResearch;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchMilestones;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Copie cote client de l'etat de la recherche (arbre technologique, astres decouverts sur la carte).
 */
public final class ClientResearch {

    private ClientResearch() {
    }

    private static final Set<ResearchNode> completed = EnumSet.noneOf(ResearchNode.class);
    @Nullable
    private static ResearchNode active;
    private static int[] progress = new int[0];
    private static int[] samples = new int[0];
    private static final List<ResearchNode> queue = new ArrayList<>();
    private static int labs;
    private static long updatedAt;
    /** 0.18.0 : jalons industriels atteints dans le monde. */
    private static final Set<String> milestones = new HashSet<>();

    public static void update(PacketResearch.Sync sync) {
        completed.clear();
        for (int index : sync.completed()) {
            if (index >= 0 && index < ResearchNode.values().length) {
                completed.add(ResearchNode.values()[index]);
            }
        }
        samples = sync.samples();
        active = sync.active() < 0 ? null : ResearchNode.byIndex(sync.active());
        progress = sync.progress();
        queue.clear();
        for (int index : sync.queue()) {
            queue.add(ResearchNode.byIndex(index));
        }
        labs = sync.labs();
        milestones.clear();
        milestones.addAll(Arrays.asList(sync.milestones()));
        ResearchEffects.update(completed);
        updatedAt = System.currentTimeMillis();
    }

    public static void onCompleted(ResearchNode node) {
        Minecraft.getInstance().getToasts().addToast(new ResearchToast(node));
    }

    public static boolean isCompleted(ResearchNode node) {
        return completed.contains(node);
    }

    public static boolean isAvailable(ResearchNode node) {
        return !completed.contains(node) && node.prerequisitesMet(completed::contains) && milestonesMet(node);
    }

    public static boolean hasMilestone(ResourceLocation id) {
        return milestones.contains(id.toString());
    }

    public static boolean milestonesMet(ResearchNode node) {
        return ResearchMilestones.met(node, ClientResearch::hasMilestone);
    }

    /** Echantillons fournis (ou biomes analyses pour la surface) pour un type. */
    public static int getSamples(ResearchNode node, int type) {
        int index = node.ordinal() * 4 + type;
        return index < samples.length ? samples[index] : 0;
    }

    /** Avancement 0..1 d'une recherche (unites de laboratoire ou echantillons). */
    public static double getFraction(ResearchNode node) {
        if (completed.contains(node)) {
            return 1;
        }
        if (node.kind.isSample()) {
            long total = 0;
            for (int type = 0; type < node.sampleTypes(); type++) {
                total += Math.min(node.units, getSamples(node, type));
            }
            return total / (double) (node.units * node.sampleTypes());
        }
        return getProgress(node) / (double) node.units;
    }

    @Nullable
    public static ResearchNode getActive() {
        return active;
    }

    public static int getProgress(ResearchNode node) {
        return node.ordinal() < progress.length ? progress[node.ordinal()] : 0;
    }

    public static List<ResearchNode> getQueue() {
        return queue;
    }

    public static int getLabs() {
        return labs;
    }

    public static int completedCount() {
        return completed.size();
    }

    public static boolean isDiscovered(Body body) {
        ResearchNode node = ResearchEffects.discoveryOf(body);
        return node == null || completed.contains(node);
    }

    /** On peut s'y poser (orbite etudiee). */
    public static boolean canLand(Body body) {
        return ResearchEffects.canLand(body);
    }

    public static int mastery(Body body) {
        return BodyResearch.mastery(body, completed::contains);
    }
}
