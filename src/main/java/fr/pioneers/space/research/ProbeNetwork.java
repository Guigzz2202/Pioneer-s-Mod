package fr.pioneers.space.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Les sondes en orbite (commun a tout le monde, sauvegarde avec le monde).
 * <p>
 * A chaque tick, les sondes de chaque destination remplissent un tampon de donnees (sauf echec de transmission),
 * que les antennes accordees sur cette destination vident. Le tampon est limite : sans antenne, les donnees se perdent.
 */
public class ProbeNetwork extends SavedData {

    private static final String NAME = "pioneers_probes";
    private static final int N = ProbeDestination.values().length;

    private final int[] probes = new int[N];
    private final double[] buffer = new double[N];
    private final long[] lost = new long[N];

    public static ProbeNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ProbeNetwork::load, ProbeNetwork::new, NAME);
    }

    public void tick(RandomSource random) {
        for (ProbeDestination destination : ProbeDestination.values()) {
            int i = destination.ordinal();
            if (probes[i] == 0) {
                continue;
            }
            if (random.nextFloat() < destination.dataFailure * ResearchEffects.transmissionLossMultiplier()) {
                lost[i]++;
                continue;
            }
            double produced = probes[i] * ProbeDestination.DATA_PER_PROBE_PER_TICK * ResearchEffects.probeDataMultiplier();
            //Le tampon garde 10 secondes de donnees au plus
            buffer[i] = Math.min(buffer[i] + produced, Math.max(1_000, produced * 200));
        }
    }

    /** Une antenne recupere jusqu'a {@code max} mB de donnees de cette destination. */
    public long extract(ProbeDestination destination, long max) {
        int i = destination.ordinal();
        long taken = Math.min(max, (long) buffer[i]);
        buffer[i] -= taken;
        return taken;
    }

    public void setProbes(ProbeDestination destination, int count) {
        probes[destination.ordinal()] = Math.max(0, count);
        setDirty();
    }

    public void addProbe(ProbeDestination destination) {
        probes[destination.ordinal()]++;
        setDirty();
    }

    public int getProbes(ProbeDestination destination) {
        return probes[destination.ordinal()];
    }

    private static ProbeNetwork load(CompoundTag tag) {
        ProbeNetwork network = new ProbeNetwork();
        for (ProbeDestination destination : ProbeDestination.values()) {
            network.probes[destination.ordinal()] = tag.getInt(destination.name().toLowerCase());
        }
        return network;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        for (ProbeDestination destination : ProbeDestination.values()) {
            tag.putInt(destination.name().toLowerCase(), probes[destination.ordinal()]);
        }
        return tag;
    }
}
