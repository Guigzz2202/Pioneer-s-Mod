package fr.pioneers.core.common.projector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11.3 (lot E3, demande de Guillaume : « une page de multibloc dans JEI : prevoir le nombre de blocs necessaires et une preview ») : NOMENCLATURE
 * d'un plan de multibloc, calculee depuis la SEULE source des plans ({@link Blueprint#layout} : {@code IndustrialBlueprint} / {@code ShapeTemplate.of},
 * {@code BlastLayout}, {@code HarborLayout}, {@code BaseLayout}, {@link ProjectedStructure}...) : rien n'est recopie.
 * <p>
 * Code commun sans classe client (utilise par la categorie JEI {@code integration/jei/MultiblockCategory} et par le banc hors jeu
 * {@code tools/multiblock/BlueprintBench.java}) : dimensions, nombre total de blocs, lignes (bloc conseille, quantite, variantes du meme bloc a
 * d'autres tiers, autres blocs acceptes), cases relatives (pour l'apercu 3D).
 */
public record BlueprintBill(Blueprint structure, int variant, int sizeX, int sizeY, int sizeZ, int total, List<Line> lines, @Nullable Block controller,
                            Map<BlockPos, BlockState> cells, int free) {

    /**
     * Une ligne : le bloc conseille par le plan, sa quantite, ses variantes « de famille » (le meme bloc a un autre tier : carcasses, verres,
     * toles, cadres... ; le conseille en tete) et les autres blocs acceptes a ces places (verre ou port a la place d'une carcasse... ; jamais
     * l'air). 1.0.5-beta (lot 27, retour 33) : {@code optional} = cases que la validation n'exige pas ({@link ProjectedStructure.Ghost#optional} :
     * tube de niveau de la chaudiere, cheminee au-dessus du premier bloc, modules facultatifs) : une ligne a part, apres les lignes exigees.
     */
    public record Line(Block block, int count, List<Block> family, List<Block> others, boolean optional) {

        /** Ligne exigee (avant le lot 27). */
        public Line(Block block, int count, List<Block> family, List<Block> others) {
            this(block, count, family, others, false);
        }
    }

    /** Mots de tier retires d'un identifiant pour reconnaitre une famille de blocs (industrial_casing_basic et _elite : meme famille). */
    private static final Pattern TIER_WORDS = Pattern.compile("(^|_)(basic|advanced|elite|ultimate|steel|aluminium|titanium|tungsten|copper|bronze|iron)(?=_|$)");

    /** Famille d'un identifiant de bloc (chemin sans ses mots de tier). */
    public static String family(String path) {
        String f = TIER_WORDS.matcher(path).replaceAll("$1").replaceAll("__+", "_").replaceAll("^_|_$", "");
        return f.isEmpty() ? path : f;
    }

    /**
     * Nomenclature d'un plan (variante {@code variant}), dans le repere du plan (ancre en 0, avant au nord), cases ramenees au coin le plus bas.
     * {@code ids} donne le chemin de registre d'un bloc (en jeu : le registre de Forge ; au banc : les objets de registre resolus a la main).
     */
    public static BlueprintBill of(Blueprint structure, int variant, Function<Block, String> ids) {
        Map<BlockPos, ProjectedStructure.Ghost> layout = structure.layout(variant, BlockPos.ZERO, Direction.NORTH);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : layout.entrySet()) {
            if (e.getValue().state() == null) {
                continue;
            }
            BlockPos p = e.getKey();
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        Block controller = structure.controller();
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        //Cle d'une ligne : le bloc et son caractere facultatif (un meme bloc peut avoir des cases exigees et d'autres facultatives)
        Map<Map.Entry<Block, Boolean>, int[]> counts = new LinkedHashMap<>();
        Map<Map.Entry<Block, Boolean>, Set<Block>> accepted = new LinkedHashMap<>();
        int free = 0, total = 0;
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : layout.entrySet()) {
            ProjectedStructure.Ghost g = e.getValue();
            if (g.state() == null) {
                free++;
                continue;
            }
            BlockPos p = e.getKey();
            cells.put(new BlockPos(p.getX() - minX, p.getY() - minY, p.getZ() - minZ), g.state());
            total++;
            Block b = g.state().getBlock();
            if (b == controller) {
                continue;
            }
            Map.Entry<Block, Boolean> key = Map.entry(b, g.optional());
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            accepted.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(g.accepted());
        }
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<Map.Entry<Block, Boolean>, int[]> e : counts.entrySet()) {
            Block b = e.getKey().getKey();
            String fam = family(name(ids, b));
            List<Block> family = new ArrayList<>();
            List<Block> others = new ArrayList<>();
            family.add(b);
            List<Block> sorted = new ArrayList<>(accepted.get(e.getKey()));
            sorted.sort(Comparator.comparing(x -> name(ids, x)));
            for (Block a : sorted) {
                //1.0.5-beta (lot 27) : l'air n'est pas un bloc « accepte aussi » (« Accepte aussi : Air » sous le tube de niveau)
                if (a == b || a == Blocks.AIR) {
                    continue;
                }
                (family(name(ids, a)).equals(fam) ? family : others).add(a);
            }
            lines.add(new Line(b, e.getValue()[0], List.copyOf(family), List.copyOf(others), e.getKey().getValue()));
        }
        //Les exigees d'abord, puis les plus nombreux (la carcasse avant les ports), a egalite dans l'ordre du plan
        lines.sort(Comparator.comparing(Line::optional).thenComparingInt((Line l) -> -l.count()));
        boolean any = !cells.isEmpty();
        return new BlueprintBill(structure, variant, any ? maxX - minX + 1 : 0, any ? maxY - minY + 1 : 0, any ? maxZ - minZ + 1 : 0, total, List.copyOf(lines),
              controller, cells, free);
    }

    private static String name(Function<Block, String> ids, Block b) {
        String n = ids.apply(b);
        return n == null ? b.getClass().getSimpleName() : n;
    }

    /** 1.0.5-beta (lot 27) : nombre de blocs facultatifs (compris dans {@link #total}). */
    public int optionalCount() {
        int n = 0;
        for (Line l : lines) {
            if (l.optional()) {
                n += l.count();
            }
        }
        return n;
    }

    /** 1.0.5-beta (lot 27) : nombre de blocs EXIGES (controleur compris) : {@link #total} moins les facultatifs. */
    public int required() {
        return total - optionalCount();
    }

    /** Nombre de couches (hauteur). */
    public int layers() {
        return sizeY;
    }
}
