package fr.pioneers.logistics.warehouse;

import fr.pioneers.tech.kinetics.rope.PulleyTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 2, Guillaume : « il faut ameliorer le moyen de connexion entre le comptoir et les cordes ») : regles communes des lignes
 * de l'entrepot, les memes au serveur (reseau, colis), au client (dessin, apercu de pose) et aux GameTests.
 * <ul>
 *     <li>STATION PAR DESCENTE : une PETITE poulie a axe vertical (1.0.4-beta lot 27 : elle remplace la poulie d'entrepot) dessert la
 *     trappe d'une caisse ou le comptoir situe SOUS elle, posee dessus (0 bloc) ou plus haut, jusqu'a {@link #MAX_DROP} blocs de vide entre
 *     les deux (cases sans collision ni fluide) : le colis quitte la corde a la jante et descend a la verticale au bout de son filin, puis
 *     remonte. Le comptoir se pose AU SOL sous la ligne.</li>
 *     <li>Les poulies normale et grande ne font que RELAYER (leur jante passe hors de l'ouverture de la trappe et du bac du comptoir).</li>
 *     <li>DEGAGEMENT des colis : sous une poulie de base (ils passent jusqu'a 2 px sous son bloc) et le long des brins des cordes qui touchent une
 *     poulie de base : aucun point du colis ne doit entrer dans une collision.</li>
 * </ul>
 */
public final class WarehouseLines {

    /** Descente maximale (blocs de vide entre la poulie et la station). */
    public static final int MAX_DROP = 8;
    /** Ticks de montee ou de descente par bloc de vide (1.0.5-beta lot 13 : 5, avant 6 ; cadence {@link LineNode#CADENCE}). */
    public static final int TICKS_PER_BLOCK = 5;
    /** Le colis sous la corde (px sous la gorge) : bas et haut de la caisse, demi-largeur avec les coins (memes que le dessin). */
    public static final float HANG_BOTTOM = 8.9F, HANG_TOP = 3.9F, HALF = 2.95F;

    private WarehouseLines() {
    }

    /** Station desservie par une petite poulie : le bloc (trappe ou comptoir), sa tuile, les blocs de vide entre les deux. */
    public record Station(BlockPos block, BlockEntity tile, int gap) {

        public boolean counter() {
            return tile instanceof CounterTile;
        }
    }

    /** Case que le colis peut traverser en descendant : ni collision ni fluide. */
    public static boolean open(BlockGetter level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return st.getCollisionShape(level, p).isEmpty() && st.getFluidState().isEmpty();
    }

    private static boolean stationTile(@Nullable BlockEntity be) {
        return be instanceof CrateHatchTile || be instanceof CounterTile;
    }

    /** Station sous la roue {@code pulley} (dessous, a {@link #MAX_DROP} blocs de vide au plus), ou null (le role se lit sur la roue). */
    @Nullable
    public static Station station(@Nullable BlockGetter level, BlockPos pulley) {
        if (level == null) {
            return null;
        }
        for (int k = 1; k <= MAX_DROP + 1; k++) {
            BlockPos p = pulley.below(k);
            BlockEntity be = level.getBlockEntity(p);
            if (stationTile(be)) {
                return new Station(p, be, k - 1);
            }
            if (!open(level, p)) {
                return null;
            }
        }
        return null;
    }

    /**
     * Petite poulie a axe vertical qui dessert le comptoir (ou la trappe) {@code station} : au-dessus, a {@link #MAX_DROP} blocs de vide au
     * plus ; null si la premiere roue de la colonne n'est pas une station (poulie normale ou grande, axe horizontal).
     */
    @Nullable
    public static PulleyTile pulleyAbove(@Nullable BlockGetter level, BlockPos station) {
        if (level == null) {
            return null;
        }
        for (int k = 1; k <= MAX_DROP + 1; k++) {
            BlockPos p = station.above(k);
            if (level.getBlockEntity(p) instanceof PulleyTile w) {
                return w.stationWheel() ? w : null;
            }
            if (!open(level, p)) {
                return null;
            }
        }
        return null;
    }

    /** Blocs de vide sous la poulie {@code pulley} jusqu'a sa station (0 : posee dessus ; -1 : pas de station). */
    public static int gap(@Nullable BlockGetter level, BlockPos pulley) {
        Station st = station(level, pulley);
        return st == null ? -1 : st.gap();
    }

    /**
     * Pourquoi une roue au-dessus d'une trappe ou d'un comptoir n'en est pas la station (message clair), ou null si elle l'est ou s'il n'y a
     * rien dessous : bloc dans la colonne, trop haut, poulie trop grande (1.0.4-beta lot 27 : seule la petite poulie dessert une station).
     */
    @Nullable
    public static Component stationProblem(@Nullable BlockGetter level, BlockPos pulley) {
        if (level == null) {
            return null;
        }
        boolean base = !(level.getBlockEntity(pulley) instanceof LineNode n && n.stationWheel());
        BlockPos blocked = null;
        for (int k = 1; k <= MAX_DROP + 17; k++) {
            BlockPos p = pulley.below(k);
            BlockEntity be = level.getBlockEntity(p);
            if (stationTile(be)) {
                boolean counter = be instanceof CounterTile;
                if (base) {
                    return k - 1 <= MAX_DROP ? Component.translatable(counter ? "warehouse.pioneers.station.size_counter" : "warehouse.pioneers.station.size_crate")
                                             : null;
                }
                if (blocked != null) {
                    //Un bloc dans la colonne : on le signale si la station est a portee (sinon ce n'est pas pour cette poulie)
                    return k - 1 <= MAX_DROP ? Component.translatable(counter ? "warehouse.pioneers.station.blocked_counter"
                          : "warehouse.pioneers.station.blocked_crate", pulley.getY() - blocked.getY()) : null;
                }
                if (k - 1 > MAX_DROP) {
                    return Component.translatable(counter ? "warehouse.pioneers.station.far_counter" : "warehouse.pioneers.station.far_crate", k - 1, MAX_DROP);
                }
                return null;
            }
            if (blocked == null && !open(level, p)) {
                blocked = p;
            }
        }
        return null;
    }

    /** Angle de depose (plan x, z, atan2) de la station de {@code pulley} : cote +u de la trappe, facade du comptoir ; sinon +X. */
    public static double dropAngle(@Nullable BlockGetter level, BlockPos pulley) {
        Station st = station(level, pulley);
        if (st == null) {
            return 0;
        }
        BlockState below = level.getBlockState(st.block());
        if (below.getBlock() instanceof CrateHatchBlock) {
            return below.getValue(CrateBlock.AXIS) == Direction.Axis.X ? Math.PI / 2 : 0;
        }
        if (below.getBlock() instanceof CounterBlock) {
            Direction f = below.getValue(CounterBlock.FACING);
            return Math.atan2(f.getStepZ(), f.getStepX());
        }
        return 0;
    }

    // ------------------------------------------------------------------ degagement des colis

    /** Vrai si le point (monde) entre dans la collision d'un bloc autre que ceux de {@code skip}. */
    private static boolean solidAt(BlockGetter level, double x, double y, double z, BlockPos... skip) {
        BlockPos cell = BlockPos.containing(x, y, z);
        for (BlockPos s : skip) {
            if (cell.equals(s)) {
                return false;
            }
        }
        BlockState st = level.getBlockState(cell);
        VoxelShape shape = st.getCollisionShape(level, cell);
        if (shape.isEmpty()) {
            return false;
        }
        double lx = x - cell.getX(), ly = y - cell.getY(), lz = z - cell.getZ();
        for (AABB b : shape.toAabbs()) {
            if (lx > b.minX && lx < b.maxX && ly > b.minY && ly < b.maxY && lz > b.minZ && lz < b.maxZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Case touchee par un colis pendu sous la corde au point (x, yRope, z), marche (dx, dz), ou null : 5 points (centre et coins a 85 %)
     * sur le bas et le haut de la caisse.
     */
    @Nullable
    public static BlockPos parcelHits(BlockGetter level, double x, double yRope, double z, double dx, double dz, BlockPos... skip) {
        double h = HALF * 0.85 / 16.0, nx = -dz, nz = dx;
        for (double dy : new double[]{-(HANG_BOTTOM - 0.3) / 16.0, -(HANG_TOP + 0.3) / 16.0}) {
            for (int k = 0; k < 5; k++) {
                double a = k == 0 ? 0 : (k == 1 || k == 2 ? h : -h), b = k == 0 ? 0 : (k == 1 || k == 3 ? h : -h);
                double px = x + dx * a + nx * b, pz = z + dz * a + nz * b, py = yRope + dy;
                if (solidAt(level, px, py, pz, skip)) {
                    return BlockPos.containing(px, py, pz);
                }
            }
        }
        return null;
    }

    /**
     * Sous une poulie a axe vertical : le tour complet des colis autour de la roue ; case touchee ou null. 1.0.4-beta (lot 27) : la station
     * posee juste dessous (trappe, comptoir) n'est pas un obstacle : le colis y descend.
     */
    @Nullable
    public static BlockPos underWheel(BlockGetter level, BlockPos pos) {
        LineNode node = LineNode.at(level, pos);
        if (!(node instanceof PulleyTile)) {
            return null;
        }
        double r = node.pitchPx() / 16.0, y = pos.getY() + node.ropeYPx() / 16.0;
        BlockPos below = node.stationWheel() && stationTile(level.getBlockEntity(pos.below())) ? pos.below() : pos;
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24;
            BlockPos hit = parcelHits(level, pos.getX() + 0.5 + Math.cos(a) * r, y, pos.getZ() + 0.5 + Math.sin(a) * r, -Math.sin(a), Math.cos(a), pos,
                  below);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** Le long des deux brins de la corde a -> b (fleche comprise) : case touchee par un colis, ou null. */
    @Nullable
    public static BlockPos alongRope(BlockGetter level, BlockPos a, BlockPos b, fr.pioneers.tech.kinetics.rope.LinkType type) {
        LineNode na = LineNode.at(level, a), nb = LineNode.at(level, b);
        if (na == null || nb == null) {
            return null;
        }
        double ax = a.getX() + 0.5, az = a.getZ() + 0.5, bx = b.getX() + 0.5, bz = b.getZ() + 0.5;
        double ra = na.pitchPx() / 16.0, rb = nb.pitchPx() / 16.0, ya = a.getY() + na.ropeYPx(b) / 16.0, yb = b.getY() + nb.ropeYPx(a) / 16.0;
        double d = Math.hypot(bx - ax, bz - az);
        if (d < 1e-3) {
            return null;
        }
        double base = Math.atan2(bz - az, bx - ax), phi = Math.asin(Math.max(-1, Math.min(1, (ra - rb) / d)));
        int steps = Math.max(4, (int) Math.ceil(d * 8));
        for (int side = -1; side <= 1; side += 2) {
            double n = base + side * (Math.PI / 2 - phi), c = Math.cos(n), s = Math.sin(n);
            double x0 = ax + c * ra, z0 = az + s * ra, x1 = bx + c * rb, z1 = bz + s * rb;
            double len = Math.max(1e-6, Math.hypot(x1 - x0, z1 - z0)), dx = (x1 - x0) / len, dz = (z1 - z0) / len;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double sag = sag(d, t, type) / 16.0;
                BlockPos hit = parcelHits(level, x0 + (x1 - x0) * t, ya + (yb - ya) * t - sag, z0 + (z1 - z0) * t, dx, dz, a, b);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** Fleche (px) de la corde au point {@code t} (0..1) d'un troncon de {@code blocks} blocs : celle du dessin de la corde du mod. */
    public static float sag(double blocks, double t, fr.pioneers.tech.kinetics.rope.LinkType type) {
        float sag = type == fr.pioneers.tech.kinetics.rope.LinkType.LEATHER_BELT ? 0.4F : (float) (blocks * 16 * 0.03);
        return (float) (sag * 4 * t * (1 - t));
    }

    /** Corde a -> b utilisable par les colis (degagement le long des deux brins). */
    public static boolean usable(BlockGetter level, BlockPos a, BlockPos b, fr.pioneers.tech.kinetics.rope.LinkType type) {
        return alongRope(level, a, b, type) == null;
    }

    /** Ce qui empeche une poulie a axe vertical de bien relayer les colis (message), ou null. */
    @Nullable
    public static Component relayProblem(BlockGetter level, BlockPos pos) {
        BlockPos hit = underWheel(level, pos);
        if (hit != null) {
            return Component.translatable("warehouse.pioneers.base.under_wheel", hit.getX(), hit.getY(), hit.getZ());
        }
        return stationProblem(level, pos);
    }

    // ------------------------------------------------------------------ aide a la pose (lot 2)

    /**
     * Ou poser une petite poulie pour desservir la station {@code station} (comptoir ou trappe) quand on clique son dessus : dans sa
     * colonne, a la hauteur de la roue de ligne la plus proche ({@link LineNode}, a portee de corde, de 1 a {@link #MAX_DROP} + 1 blocs
     * au-dessus), si la colonne est libre jusque-la ; sinon null (la poulie se pose dessus, comme au lot 1).
     */
    @Nullable
    public static BlockPos placeAbove(net.minecraft.world.level.Level level, BlockPos station) {
        double range = fr.pioneers.tech.kinetics.KineticsConfig.ropeRange();
        int r = (int) Math.ceil(range / 16.0);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int cx = station.getX() >> 4, cz = station.getZ() >> 4;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!level.hasChunk(x, z)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
                    if (!(be instanceof LineNode n) || !n.lineNode()) {
                        continue;
                    }
                    BlockPos p = be.getBlockPos();
                    int dy = p.getY() - station.getY();
                    double d = Math.hypot(p.getX() - station.getX(), p.getZ() - station.getZ());
                    if (dy < 1 || dy > MAX_DROP + 1 || d < 0.5 || d > range || d >= bestD) {
                        continue;
                    }
                    best = p;
                    bestD = d;
                }
            }
        }
        if (best == null || best.getY() == station.getY() + 1) {
            return null;
        }
        BlockPos target = new BlockPos(station.getX(), best.getY(), station.getZ());
        for (BlockPos p = station.above(); p.getY() < target.getY(); p = p.above()) {
            if (!open(level, p)) {
                return null;
            }
        }
        return level.getBlockState(target).canBeReplaced() ? target : null;
    }
}
