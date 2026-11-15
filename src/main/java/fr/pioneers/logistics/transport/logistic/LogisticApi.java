package fr.pioneers.logistics.transport.logistic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 : points d'accroche du reseau logistique pour le diagramme (D2b) et la migration (D2c). Serveur seulement. Aucun controle de droits
 * ici : la session du diagramme verifie {@code ClanApi.canModify} (spec §5.1) avant d'appeler ces methodes.
 * <p>
 * Lecture : {@link #summary} (copie : niveau, cables, maillon faible, canaux et etats, points, raccords, graphe compresse, cout). Edition :
 * chaque methode materialise d'abord le mode auto (spec §2), recompile les canaux et ecrit la configuration dans toutes les tiles a fiche
 * chargees du reseau (au tick suivant). Migration : {@link #seed} ecrit un canal et des points directement dans une tile (avant que le
 * reseau existe : la reconstruction les reunit par uid et revision).
 */
public final class LogisticApi {

    private LogisticApi() {
    }

    @Nullable
    public static LogisticNetwork network(Level level, BlockPos cable) {
        return LogisticNetworks.network(level, cable);
    }

    /** Resume du reseau d'un cable (null : pas de reseau, ex. aucune fiche). */
    @Nullable
    public static LogisticNetwork.Summary summary(Level level, BlockPos cable) {
        LogisticNetwork network = network(level, cable);
        return network == null ? null : network.summary();
    }

    /**
     * Cree (uid 0 : nouveau, uid tire au hasard et rendu dans {@code def.uid}) ou remplace un canal.
     *
     * @return faux s'il n'y a pas de reseau
     */
    public static boolean putChannel(ServerLevel level, BlockPos cable, ChannelDef def) {
        LogisticNetwork network = network(level, cable);
        if (network == null) {
            return false;
        }
        network.putChannel(def);
        LogisticNetworks.pushConfig(level, network);
        return true;
    }

    public static boolean deleteChannel(ServerLevel level, BlockPos cable, int uid) {
        LogisticNetwork network = network(level, cable);
        if (network == null) {
            return false;
        }
        network.deleteChannel(uid);
        LogisticNetworks.pushConfig(level, network);
        return true;
    }

    /** Reglages d'un point : raccord {@code machineKey} (position du bloc touche, {@code BlockPos.asLong}) dans le canal {@code uid}. */
    public static boolean putPoint(ServerLevel level, BlockPos cable, long machineKey, int uid, PointConfig config) {
        LogisticNetwork network = network(level, cable);
        if (network == null || network.attach(machineKey) == null) {
            return false;
        }
        network.putPoint(machineKey, uid, config);
        LogisticNetworks.pushConfig(level, network);
        return true;
    }

    /** 1.11 (D2b) : memes reglages pour plusieurs raccords (groupe du diagramme). Rend le nombre de points ecrits. */
    public static int putPoints(ServerLevel level, BlockPos cable, long[] keys, int uid, PointConfig config) {
        LogisticNetwork network = network(level, cable);
        if (network == null) {
            return 0;
        }
        int done = network.putPoints(keys, uid, config);
        LogisticNetworks.pushConfig(level, network);
        return done;
    }

    /** 1.11 (D2b) : deplace un canal d'un rang ({@code delta} -1 : vers le haut). */
    public static boolean moveChannel(ServerLevel level, BlockPos cable, int uid, int delta) {
        LogisticNetwork network = network(level, cable);
        if (network == null || !network.moveChannel(uid, delta)) {
            return false;
        }
        LogisticNetworks.pushConfig(level, network);
        return true;
    }

    /** 1.11 (D2b) : duplique un canal et ses points ; rend le nouvel uid (0 : rien). */
    public static int duplicateChannel(ServerLevel level, BlockPos cable, int uid) {
        LogisticNetwork network = network(level, cable);
        int copy = network == null ? 0 : network.duplicateChannel(uid);
        if (copy != 0) {
            LogisticNetworks.pushConfig(level, network);
        }
        return copy;
    }

    /** « Repasser en auto » (avec confirmation dans l'ecran). */
    public static boolean resetAuto(ServerLevel level, BlockPos cable) {
        LogisticNetwork network = network(level, cable);
        if (network == null) {
            return false;
        }
        network.resetAuto();
        LogisticNetworks.pushConfig(level, network);
        return true;
    }

    /** Disposition du diagramme (noeud deplace), partagee par tous les joueurs. */
    public static boolean moveNode(ServerLevel level, BlockPos cable, long node, int x, int y) {
        LogisticNetwork network = network(level, cable);
        if (network == null) {
            return false;
        }
        network.moveNode(node, x, y);
        return true;
    }

    /** Coller un reseau entier (D2b : canaux recrees et points appliques par type de machine, calcules par l'ecran). */
    public static boolean replaceConfig(ServerLevel level, BlockPos cable, NetworkConfig config) {
        LogisticNetwork network = network(level, cable);
        if (network == null) {
            return false;
        }
        network.replaceConfig(config);
        LogisticNetworks.pushConfig(level, network);
        return true;
    }

    /** Ce que chaque face d'une machine offre pour un type (patron du cube) : 2 bits par face relative ; 0 si la machine n'est pas raccordee. */
    public static int faces(Level level, BlockPos cable, long machineKey, ChannelKind kind) {
        LogisticNetwork network = network(level, cable);
        LogisticNetwork.Attach attach = network == null ? null : network.attach(machineKey);
        return attach == null ? 0 : attach.endpoint.faces(kind);
    }

    // ------------------------------------------------------------------
    // Migration (D2c)
    // ------------------------------------------------------------------

    /** Nouvel uid de canal (non nul). */
    public static int newUid() {
        return LogisticNetwork.UIDS.getAsInt();
    }

    /**
     * Migration : ecrit un canal (et, si {@code point} n'est pas nul, le point du raccord {@code machineKey}) dans la configuration d'une tile
     * de cable a fiche, sans reseau (la tile est marquee a sauver ; la reconstruction suivante reunit tout). Le drapeau auto est retire.
     */
    public static void seed(LogisticCableTile tile, ChannelDef def, long machineKey, @Nullable PointConfig point, long rev) {
        NetworkConfig config = tile.config() == null ? new NetworkConfig() : tile.config();
        config.seed(def, machineKey, point, rev);
        tile.setConfig(config, true);
        if (tile.getLevel() != null) {
            LogisticNetworks.invalidate(tile.getLevel(), tile.getBlockPos());
        }
    }
}
