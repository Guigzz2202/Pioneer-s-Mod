package fr.pioneers.logistics.robotics.logistics;

import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.hub.RoboticHubData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Un reseau robotique : des hubs et des relais dont les couvertures se touchent. Les points logistiques couverts en font partie ; les
 * unites de ses hubs y travaillent et s'y rechargent.
 */
public final class LogisticNetwork {

    /** Identifiant stable tant que le noeud le plus « petit » ne change pas. */
    public final long id;
    public final List<NetworkNode> nodes = new ArrayList<>();
    public final List<RoboticHubData> hubs = new ArrayList<>();
    public final List<LogisticPoint> points = new ArrayList<>();
    /** Stocks du reseau (fournisseurs, a vider, stockages, tampons), calcules regulierement. */
    public Map<LogisticResource, Long> totals = new HashMap<>();

    LogisticNetwork(long id) {
        this.id = id;
    }

    public boolean covers(BlockPos pos) {
        for (NetworkNode node : nodes) {
            if (node.covers(pos)) {
                return true;
            }
        }
        return false;
    }

    public long stock(LogisticResource res) {
        if (!res.isAny()) {
            return totals.getOrDefault(res, 0L);
        }
        long sum = 0;
        for (Map.Entry<LogisticResource, Long> entry : totals.entrySet()) {
            if (res.covers(entry.getKey())) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    /** Nom court affiche (« Reseau 3F2A »). */
    public String label() {
        return Integer.toHexString((int) (id ^ (id >>> 32)) & 0xFFFF).toUpperCase(Locale.ROOT);
    }
}
