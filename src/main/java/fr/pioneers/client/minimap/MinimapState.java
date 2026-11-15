package fr.pioneers.client.minimap;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * 1.10.3 : etat de la minimap pour le monde et la dimension en cours : carte exploree ({@link MapStore}), chunks a relever, releveur.
 */
final class MinimapState {

    @Nullable
    MapStore store;
    String dim = "";
    /** Dossier du monde ou du serveur ({@code <jeu>/pioneers/minimap/<monde>}), null hors partie. */
    @Nullable
    Path worldDir;
    @Nullable
    ClientLevel level;
    final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
    final ChunkScanner scanner = new ChunkScanner();
    int near;
    boolean deathSeen;
    /** Mesure du releve (banc en jeu : F3 n'en dit rien) : nanosecondes cumulees et chunks releves. */
    long scanNanos, scanned;
}
