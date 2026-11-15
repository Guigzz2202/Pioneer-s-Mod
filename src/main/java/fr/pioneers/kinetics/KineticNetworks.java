package fr.pioneers.kinetics;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Reseaux de force rotative de chaque monde (serveur). Un noeud qui se charge, change ou disparait dissout son reseau ; les reseaux sont
 * reconstruits en fin de tick par un parcours des liens, puis chacun calcule sa vitesse. Un reseau reconstruit repart de la derniere vitesse
 * de ses noeuds (ajouter un arbre ne fait pas caler la machine).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KineticNetworks {

    private KineticNetworks() {
    }

    /** Ecart de rapport tolere quand un noeud est atteint par deux chemins (sinon le reseau est bloque). */
    private static final double TOLERANCE = 1e-3;
    private static final int MAX_NODES = 4096;

    private static final class Networks {

        final Map<BlockPos, KineticNetwork> byPos = new HashMap<>();
        final Set<BlockPos> dirty = new LinkedHashSet<>();
        final List<KineticNetwork> all = new ArrayList<>();
        KineticNetwork[] snapshot = new KineticNetwork[16];
    }

    private static final Map<Level, Networks> LEVELS = new WeakHashMap<>();

    private static Networks networks(Level level) {
        return LEVELS.computeIfAbsent(level, l -> new Networks());
    }

    /** Noeud charge, pose ou modifie (liens, orientation, embrayage...) : son reseau sera reconstruit. */
    public static void invalidate(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        Networks networks = networks(level);
        BlockPos key = pos.immutable();
        dissolve(networks, networks.byPos.get(key));
        networks.dirty.add(key);
    }

    /** Noeud decharge ou casse. */
    public static void remove(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        Networks networks = networks(level);
        BlockPos key = pos.immutable();
        dissolve(networks, networks.byPos.get(key));
        networks.dirty.remove(key);
    }

    @Nullable
    public static KineticNetwork network(Level level, BlockPos pos) {
        Networks networks = LEVELS.get(level);
        return networks == null ? null : networks.byPos.get(pos);
    }

    private static void dissolve(Networks networks, @Nullable KineticNetwork network) {
        if (network == null) {
            return;
        }
        for (KineticNode node : network.nodes) {
            BlockPos pos = node.nodePos();
            networks.byPos.remove(pos);
            networks.dirty.add(pos);
            node.leaveNetwork();
        }
        networks.all.remove(network);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Networks networks = LEVELS.get(level);
        if (networks == null) {
            return;
        }
        if (!networks.dirty.isEmpty()) {
            rebuild(level, networks);                              //1.0.5-beta (lot 29) : rien a allouer quand rien n'a change
        }
        //1.0.5-beta (lot 29) : copie dans un tableau garde d'un tick a l'autre (une piece cassee pendant le tick dissout son reseau)
        int count = networks.all.size();
        if (networks.snapshot.length < count) {
            networks.snapshot = new KineticNetwork[Math.max(16, count * 2)];
        }
        KineticNetwork[] snapshot = networks.all.toArray(networks.snapshot);
        for (int i = 0; i < count; i++) {
            KineticNetwork network = snapshot[i];
            snapshot[i] = null;
            //Reseau en bordure de la zone chargee (tronçons « paresseux ») : en pause
            if (!network.nodes.isEmpty() && !level.shouldTickBlocksAt(network.nodes.get(0).nodePos())) {
                continue;
            }
            network.tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            LEVELS.remove(level);
        }
    }

    @Nullable
    private static KineticNode node(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity tile = level.getBlockEntity(pos);
        return tile instanceof KineticNode node && !tile.isRemoved() ? node : null;
    }

    private static void rebuild(Level level, Networks networks) {
        //Noeuds deja ranges dans un reseau pendant ce passage : jamais redissous (sinon un reseau tronque a MAX_NODES boucle sans fin)
        Set<BlockPos> built = new HashSet<>();
        while (!networks.dirty.isEmpty()) {
            BlockPos start = networks.dirty.iterator().next();
            networks.dirty.remove(start);
            KineticNode first = node(level, start);
            if (first == null || built.contains(start)) {
                continue;
            }
            dissolve(networks, networks.byPos.get(start));
            networks.dirty.remove(start);
            KineticNetwork network = new KineticNetwork();
            Map<BlockPos, Double> seen = new HashMap<>();
            //1.12.4 (lot F4) : variateurs traverses depuis le depart (absent = aucun)
            Map<BlockPos, KineticNetwork.Chain> chainOf = new HashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            seen.put(start, 1.0);
            queue.add(start);
            double speedSum = 0;
            int speedCount = 0;
            while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
                BlockPos pos = queue.poll();
                KineticNode node = node(level, pos);
                if (node == null || built.contains(pos)) {
                    continue;
                }
                dissolve(networks, networks.byPos.get(pos));
                networks.dirty.remove(pos);
                double ratio = seen.get(pos);
                KineticNetwork.Chain chain = chainOf.get(pos);
                network.add(node, ratio, chain);
                int variator = node.isVariator() ? network.variatorIndex(node) : -1;
                if (node.lastSpeed() != 0) {
                    speedSum += node.lastSpeed() / ratio;
                    speedCount++;
                }
                long[] signature = {KineticNode.LINKS_SEED};
                node.collectLinks((other, r) -> {
                    signature[0] = KineticNode.linkHash(signature[0], other, r);
                    if (!Double.isFinite(r) || r == 0) {
                        return;
                    }
                    BlockPos key = other.immutable();
                    //1.9 (MAJ RP, bloc 4) : un arbre ne transmet rien a travers une frontiere de claim refusee
                    if (!ClanApi.canTransfer(level, pos, key)) {
                        return;
                    }
                    if (built.contains(key)) {
                        network.jammed = true;                   //Touche un reseau tronque : trop grand, bloque
                        return;
                    }
                    double expected = ratio * r;
                    KineticNode target = node(level, key);
                    //1.12.4 (lot F4) : passage par un variateur (regulateur) : entree -> sortie = x k, sortie -> entree = / k
                    KineticNetwork.Chain c = chain;
                    if (variator >= 0 && node.variatorOutput(key)) {
                        c = KineticNetwork.Chain.with(c, variator, 1);
                    }
                    if (target != null && target.isVariator() && target.variatorOutput(pos)) {
                        c = KineticNetwork.Chain.with(c, network.variatorIndex(target), -1);
                    }
                    Double known = seen.get(key);
                    if (known != null) {
                        if (Math.abs(known - expected) > TOLERANCE * Math.max(1, Math.abs(known)) || !KineticNetwork.Chain.same(c, chainOf.get(key))) {
                            network.jammed = true;               //Deux chemins, deux rapports : montage impossible
                        }
                        return;
                    }
                    if (target == null) {
                        return;
                    }
                    if (Math.abs(expected) > 1e5 || Math.abs(expected) < 1e-5) {
                        network.jammed = true;
                        return;
                    }
                    seen.put(key, expected);
                    if (c != null) {
                        chainOf.put(key, c);
                    }
                    queue.add(key);
                });
                node.linksBuilt(signature[0]);
            }
            if (!queue.isEmpty()) {
                network.jammed = true;                           //Plus de MAX_NODES pieces : le reseau est bloque
                for (BlockPos rest : queue) {
                    networks.dirty.remove(rest);
                }
            }
            network.start(speedCount == 0 ? 0 : speedSum / speedCount, level.getGameTime());
            for (KineticNode node : network.nodes) {
                networks.byPos.put(node.nodePos(), network);
                built.add(node.nodePos());
            }
            networks.all.add(network);
        }
    }
}
