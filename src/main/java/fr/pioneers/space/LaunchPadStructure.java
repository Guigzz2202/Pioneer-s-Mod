package fr.pioneers.space;

import fr.pioneers.industry.multi.Structure;
import fr.pioneers.registries.PiBaseMachines;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Complexe de lancement : un disque de plaques autour du cœur (des ports peuvent remplacer des plaques), et une tour de lancement
 * posee sur le bord, au milieu d'un cote.
 * Quatre tailles, pour suivre la taille des fusees :
 * <pre>
 *  Taille      disque           tour       fusees      remplissage
 *  Petit       5x5 (rayon 2)    5 blocs    T1 a T3     100 mB/t
 *  Moyen       7x7 (rayon 3)    9 blocs    T4 a T7     250 mB/t
 *  Grand       9x9 (rayon 4)   13 blocs    T8 a T10    500 mB/t
 *  Lourd     11x11 (rayon 5)   17 blocs    T11 a T13  1000 mB/t
 * </pre>
 * Le disque est arrondi (cases au-dela du cercle exclues). La plus grande taille valide est retenue ;
 * une tour trop courte limite le complexe a la taille correspondante (on agrandit la tour pour monter de gamme).
 * <p>
 * 0.20.34 (Guillaume : « les tailles de launch pad associees a des tiers, avec les couleurs du tier ») : petit = MKI (vert), moyen = MKII
 * (bleu), grand = MKIII (rouge), lourd = MKIV (bordeaux), les couleurs des cables et des buffers ({@link CableTier}). Une fois forme, le
 * complexe peint ses plaques et sa tour a la couleur de son tier (propriete {@link #TIER}, 0 = hors complexe).
 */
public final class LaunchPadStructure {

    private LaunchPadStructure() {
    }

    public enum Size {
        SMALL(2, 5, 3, 100),
        MEDIUM(3, 9, 7, 250),
        LARGE(4, 13, 10, 500),
        /** 0.10 : lanceurs lourds (geantes gazeuses, etoiles voisines). */
        HEAVY(5, 17, 13, 1000);

        public final int radius, towerHeight, maxTier, fillRate;

        Size(int radius, int towerHeight, int maxTier, int fillRate) {
            this.radius = radius;
            this.towerHeight = towerHeight;
            this.maxTier = maxTier;
            this.fillRate = fillRate;
        }

        public String getTranslationKey() {
            return "launch_pad.pioneers.size." + name().toLowerCase();
        }

        /** 0.20.34 : tier du complexe (petit = MKI ... lourd = MKIV). */
        public CableTier tier() {
            return CableTier.values()[ordinal()];
        }

        /** Valeur de {@link #TIER} des plaques et de la tour de ce complexe (1 a 4). */
        public int paint() {
            return ordinal() + 1;
        }

        /** Couleur du tier eclaircie, lisible sur les interfaces sombres (RGB). */
        public int textColor() {
            int c = tier().color, r = Math.min(255, (c >> 16 & 255) * 3 / 2 + 30), g = Math.min(255, (c >> 8 & 255) * 3 / 2 + 30);
            return r << 16 | g << 8 | Math.min(255, (c & 255) * 3 / 2 + 30);
        }

        @Nullable
        public static Size byIndex(int index) {
            return index < 0 || index >= values().length ? null : values()[index];
        }
    }

    /** 0.20.34 : couleur de tier des plaques et de la tour (0 = hors d'un complexe forme, 1..4 = MKI..MKIV). */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 4);

    /** Rayon maximal (pour retrouver le cœur depuis un bloc quelconque). */
    public static final int MAX_RADIUS = 5;
    public static final int MAX_TOWER = 24;
    /** Ancien rayon (petit pas de tir), utilise par le spatioport cargo. */
    public static final int RADIUS = 2;

    /** Valide le complexe autour du cœur (et peint la tour et le disque) ; null si aucune taille n'est complete. */
    @Nullable
    public static Size formation(Level level, BlockPos core) {
        Found found = measure(Structure.View.of(level), core);
        if (found == null) {
            return null;
        }
        configureTower(level, found.base(), found.side().getOpposite(), found.height(), found.size().paint());
        paintDisc(level, core, 0, found.size().radius, found.size().paint());
        return found.size();
    }

    /** Lot 8a : peinture d'un complexe mesure (tour configuree, disque au tier de la taille) : crochet du cycle commun. */
    public static void paint(Level level, BlockPos core, Found found) {
        configureTower(level, found.base(), found.side().getOpposite(), found.height(), found.size().paint());
        paintDisc(level, core, 0, found.size().radius, found.size().paint());
    }

    /** Lot 8a : cases d'un complexe mesure (disque et tour) : registre des hotes. */
    public static java.util.List<BlockPos> cells(BlockPos core, Found found) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        int r = found.size().radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!isOutside(dx, dz, r)) {
                    out.add(core.offset(dx, 0, dz));
                }
            }
        }
        for (int i = 0; i < found.height(); i++) {
            out.add(found.base().above(i));
        }
        return out;
    }

    /** API Multibloc (lot 6) : complexe mesure (taille, pied et cote de la tour, hauteur), sans rien peindre (monde reel ou banc). */
    public record Found(Size size, BlockPos base, Direction side, int height) {}

    /** API Multibloc (lot 6) : la plus grande taille complete autour du cœur (tour trop courte : declassee), ou null. */
    @Nullable
    public static Found measure(Structure.View level, BlockPos core) {
        for (int i = Size.values().length - 1; i >= 0; i--) {
            Size size = Size.values()[i];
            if (!discComplete(level, core, size.radius)) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos base = core.relative(side, size.radius).above();
                int height = towerHeight(level, base);
                if (height >= Size.SMALL.towerHeight) {
                    //Tour trop courte : le complexe est declasse
                    Size result = size;
                    while (result.ordinal() > 0 && height < result.towerHeight) {
                        result = Size.values()[result.ordinal() - 1];
                    }
                    return new Found(result, base, side, height);
                }
            }
        }
        return null;
    }

    /** Petit pas de tir valide (spatioport cargo). */
    public static boolean validate(Level level, BlockPos core) {
        return formation(level, core) != null;
    }

    private static boolean discComplete(Structure.View level, BlockPos core, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx != 0 || dz != 0) && !isOutside(dx, dz, radius) && !isPlate(level.getBlockState(core.offset(dx, 0, dz)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void configureTower(Level level, BlockPos base, Direction facing, int height, int tier) {
        int arm = Math.max(1, height - 3);
        for (int i = 0; i < height; i++) {
            BlockPos pos = base.above(i);
            BlockState state = level.getBlockState(pos);
            BlockState wanted = LaunchTowerBlock.configure(state, facing, i == arm, i == height - 1).setValue(TIER, tier);
            if (wanted != state) {
                level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * 0.20.34 : peint les plaques du disque dont la distance au cœur est entre {@code from} (exclu, 0 = depuis le centre) et {@code to}
     * (inclus) ; avec {@code tier} = 0, seulement celles de ce cœur (le plus proche) ou orphelines, pour ne pas effacer un complexe voisin.
     */
    public static void paintDisc(Level level, BlockPos core, int from, int to, int tier) {
        for (int dx = -to; dx <= to; dx++) {
            for (int dz = -to; dz <= to; dz++) {
                if (dx == 0 && dz == 0 || isOutside(dx, dz, to) || from > 0 && !isOutside(dx, dz, from)) {
                    continue;
                }
                BlockPos pos = core.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(TIER) && isPlate(state) && state.getValue(TIER) != tier && (tier > 0 || ownedBy(level, pos, core))) {
                    level.setBlock(pos, state.setValue(TIER, tier), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Plaque ou tour de ce cœur (le plus proche), ou orpheline (cœur casse). */
    private static boolean ownedBy(Level level, BlockPos pos, BlockPos core) {
        BlockPos owner = findCore(level, pos);
        return owner == null || owner.equals(core);
    }

    /** 0.20.34 : complexe defait : plaques et tours de ce cœur reviennent a la couleur neutre. */
    public static void unpaint(Level level, BlockPos core) {
        paintDisc(level, core, 0, MAX_RADIUS, 0);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int r = Size.SMALL.radius; r <= MAX_RADIUS; r++) {
                BlockPos base = core.relative(side, r).above();
                for (int i = 0; i < MAX_TOWER; i++) {
                    BlockState state = level.getBlockState(base.above(i));
                    if (!LaunchTowerBlock.is(state)) {
                        break;
                    }
                    if (state.getValue(TIER) != 0 && ownedBy(level, base, core)) {
                        level.setBlock(base.above(i), state.setValue(TIER, 0), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** Cases hors du disque arrondi (au-dela du cercle de rayon r). */
    public static boolean isOutside(int dx, int dz, int radius) {
        return dx * dx + dz * dz > radius * radius + 1;
    }

    private static int towerHeight(Structure.View level, BlockPos base) {
        int height = 0;
        while (height < MAX_TOWER && LaunchTowerBlock.is(level.getBlockState(base.above(height)))) {
            height++;
        }
        return height;
    }

    /** Trouve le cœur depuis n'importe quel bloc du pas de tir ou de la tour (pour ouvrir l'interface). */
    @Nullable
    public static BlockPos findCore(Level level, BlockPos pos) {
        BlockPos padLevel = pos;
        int guard = 0;
        while (LaunchTowerBlock.is(level.getBlockState(padLevel)) && guard++ < MAX_TOWER + 1) {
            padLevel = padLevel.below();
        }
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
            for (int dz = -MAX_RADIUS; dz <= MAX_RADIUS; dz++) {
                BlockPos candidate = padLevel.offset(dx, 0, dz);
                if (isCore(level.getBlockState(candidate)) && dx * dx + dz * dz < bestDistance) {
                    best = candidate;
                    bestDistance = dx * dx + dz * dz;
                }
            }
        }
        return best;
    }

    /** Plaque du disque : plaque de pas de tir ou port du complexe. */
    public static boolean isPlate(BlockState state) {
        return state.getBlock() == PiBlocks.LAUNCH_PAD.getBlock() || state.getBlock() == PiBlocks.LAUNCH_PORT.getBlock();
    }

    /** Cœur de pas de tir ou de spatioport cargo. */
    public static boolean isCore(BlockState state) {
        return state.getBlock() == PiBaseMachines.LAUNCH_PAD_CORE.getBlock();
    }
}
