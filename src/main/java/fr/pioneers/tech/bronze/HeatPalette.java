package fr.pioneers.tech.bronze;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 23, Guillaume : « l'ensemble de la structure des multi primitifs doit avoir la meme couleur quand il chauffe, de maniere
 * homogene ») : CHAUFFE HOMOGENE des multiblocs primitifs (haut fourneau, four a coke, fonderie automatique, four de potier). Vraies causes
 * de l'heterogeneite d'avant : palier calcule par role (socle du haut fourneau h-2, couronne h-1, voute et cheminee du four a coke h-1),
 * foyers en briques refractaires bornes a 2 et peints au feu (fonderie : au soufflet) et non a la chaleur, controleur forme toujours au palier
 * 2, tuyere jamais chauffee, et briques des foyers / du controleur rendues lumineuses (forge_data) quand celles des parois ne l'etaient pas.
 * <p>Regle : le MAITRE (la tuile du controleur) calcule UN palier {@code 0..}{@link #MAX} pour toute la structure et le peint, au meme tick,
 * sur chaque bloc qui a une propriete {@code heat} (briques, foyers, cuves, tuyere, controleur, maconnerie du four de potier) ; chaque modele
 * au palier h a la MEME texture de briques ({@code T:core/p105_lot15.py HEAT_BRICK}) et la MEME lueur ({@code T:core/p105_lot23.py
 * HEAT_GLOW}). Synchro client : l'etat du bloc (UPDATE_CLIENTS).
 */
public final class HeatPalette {

    /** Dernier palier (0 froid, 1 tiede, 2 chaud, 3 au rouge vif). */
    public static final int MAX = 3;
    /** Propriete commune ajoutee aux controleurs et a la tuyere (meme nom et memes valeurs que {@code FireBricksBlock.HEAT}). */
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, MAX);
    /** Lumiere emise par un bloc de la structure au palier h (la meme pour tous : faces voisines eclairees pareil). */
    public static final int LIGHT_PER_STEP = 4;

    private HeatPalette() {
    }

    /** La propriete {@code heat} (entiere) d'un etat, ou null. */
    @Nullable
    public static IntegerProperty heatOf(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntegerProperty ip && p.getName().equals("heat")) {
                return ip;
            }
        }
        return null;
    }

    /** L'etat au palier {@code h} (borne a la propriete du bloc), ou l'etat lui-meme s'il n'a pas de palier. */
    public static BlockState heated(BlockState state, int h) {
        IntegerProperty p = heatOf(state);
        if (p == null) {
            return state;
        }
        int max = p.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
        return state.setValue(p, Math.max(0, Math.min(max, h)));
    }

    /** Palier peint d'un etat (-1 sans propriete). */
    public static int shown(BlockState state) {
        IntegerProperty p = heatOf(state);
        return p == null ? -1 : state.getValue(p);
    }

    /** Peint le palier {@code h} sur un bloc charge (clients prevenus, voisins non : pur affichage). */
    public static void paint(Level level, BlockPos pos, int h) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState s = level.getBlockState(pos);
        BlockState want = heated(s, h);
        if (want != s) {
            level.setBlock(pos, want, Block.UPDATE_CLIENTS);
        }
    }

    /** Peint le meme palier sur toutes les cases (meme tick). */
    public static void paintAll(Level level, Collection<BlockPos> positions, int h) {
        for (BlockPos p : positions) {
            paint(level, p, h);
        }
    }
}
