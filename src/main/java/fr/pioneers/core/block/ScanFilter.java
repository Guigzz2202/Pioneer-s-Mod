package fr.pioneers.core.block;

import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 1.0.5-beta (lot 29, reprise du §2 « IA » et « block entities », bancs {@code tools/perf/ai_cost.sh} et {@code tickers.sh}) : FILTRE AVANT
 * UN BALAYAGE DE BLOCS EN CUBE d'un but ou d'un capteur de la faune (feu autour du tigre et du loup, cultures du sanglier, eau des betes de la
 * ferme, feuilles de la girafe, bananes du singe, coffres du rat) ou d'un bloc (fleurs du rucher). Le balayage d'origine lit chaque case (729 a 6 727 lectures) ; le filtre regarde d'abord la PALETTE de chaque
 * section de 16 x 16 x 16 touchee par la boite ({@link LevelChunkSection#maybeHas}) ou la liste des tiles de chaque troncon : si aucune ne
 * peut contenir ce qu'on cherche, le balayage n'aurait rien trouve, il est saute.
 * <p>
 * EXACT, jamais plus permissif : la reponse est « peut-etre » (vrai) des qu'un troncon touche n'est pas charge, que la boite sort de la
 * hauteur du monde, qu'une tile attend encore d'etre creee, ou que la palette contient un etat qui convient ; le balayage d'origine decide
 * alors, inchange. Les predicats ne regardent que l'etat de bloc (ou la tile), comme le balayage.
 */
public final class ScanFilter {

    private ScanFilter() {
    }

    /** Faux seulement si AUCUNE case de la boite [a, b] ne peut avoir un etat qui verifie {@code p}. */
    public static boolean mayContain(Level level, BlockPos a, BlockPos b, Predicate<BlockState> p) {
        ChunkSource source = level.getChunkSource();
        if (source == null) {
            return true;                                                  //monde sans source de troncons (bancs hors du jeu)
        }
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int cx0 = Math.min(a.getX(), b.getX()) >> 4, cx1 = Math.max(a.getX(), b.getX()) >> 4;
        int cz0 = Math.min(a.getZ(), b.getZ()) >> 4, cz1 = Math.max(a.getZ(), b.getZ()) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk chunk = source.getChunkNow(cx, cz);
                if (chunk == null || y0 < chunk.getMinBuildHeight() || y1 >= chunk.getMaxBuildHeight()) {
                    return true;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = chunk.getSectionIndex(y0), last = chunk.getSectionIndex(y1); i <= last; i++) {
                    if (i < 0 || i >= sections.length || sections[i].maybeHas(p)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Faux seulement si AUCUNE tile de la boite [a, b] ne verifie {@code p} (tiles en attente de creation : vrai). */
    public static boolean mayHaveTile(Level level, BlockPos a, BlockPos b, Predicate<BlockEntity> p) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        ChunkSource source = level.getChunkSource();
        if (source == null) {
            return true;
        }
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk chunk = source.getChunkNow(cx, cz);
                if (chunk == null) {
                    return true;
                }
                Map<BlockPos, BlockEntity> tiles = chunk.getBlockEntities();
                for (BlockPos q : chunk.getBlockEntitiesPos()) {
                    if (q.getX() < x0 || q.getX() > x1 || q.getY() < y0 || q.getY() > y1 || q.getZ() < z0 || q.getZ() > z1) {
                        continue;
                    }
                    BlockEntity be = tiles.get(q);
                    if (be == null || p.test(be)) {
                        return true;                                       //en attente de creation, ou candidate : le balayage decide
                    }
                }
            }
        }
        return false;
    }
}
