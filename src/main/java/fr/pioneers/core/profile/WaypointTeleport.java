package fr.pioneers.core.profile;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 29, Guillaume : « Si on est admin on doit pouvoir se tp a n'importe quel waypoint, peu importe le mode ») : teleportation
 * d'un ADMINISTRATEUR a un waypoint de sa minimap ({@link RpActions#WAYPOINT_TP}, demande du client {@code client/minimap/WaypointEditScreen}).
 * <p>1.0.4-beta (lot 30, Guillaume : « la tp sur un waypoint ne doit pas etre dispo en survie. Il faut d'ailleurs fix la tp pour se faire tp
 * correctement et non en couche -64 ») :
 * <ul>
 *     <li>QUI : un administrateur (operateur de niveau 2 ou plus, {@link #admin} : regle du lot 29 ; avant le lot 29, personne ne se
 *     teleportait) ; QUAND : seulement en CREATIF ou en SPECTATEUR ({@link #modeAllows}) ; jamais en survie ni en aventure, meme pour un
 *     operateur (le serveur refuse ; l'interface grise le bouton avec l'explication) ;</li>
 *     <li>OU : une place sure ({@link #find}) dans la colonne du waypoint, sinon dans les colonnes voisines (jusqu'a {@link #SPIRAL} blocs),
 *     le chunk etant CHARGE (genere au besoin) avant toute lecture de hauteur : la hauteur du waypoint si elle est sure et pas sous le sol ;
 *     waypoint DANS un bloc : la premiere place sure juste au-dessus, a {@link #ABOVE} blocs au plus (1.0.6, reponse de Guillaume du
 *     16/11/2026) ; sinon la surface (carte
 *     des hauteurs MOTION_BLOCKING) ; dans une dimension a plafond (Nether), la place sure la plus proche de la
 *     hauteur du waypoint, toujours sous le plafond de bedrock ; jamais dans un liquide, la lave, le feu ; sur l'eau en dernier recours (on y
 *     flotte) ; aucune place : refus (« aucune place sure »), on ne part pas.</li>
 * </ul>
 * Cause du bug du lot 29 : la recherche partait de la hauteur du waypoint (souvent fausse quand il est pose sur la carte d'un chunk que le
 * client n'avait pas : hauteur -64) et prenait la premiere poche d'air a +/- 48 blocs : on arrivait dans les grottes du fond, pres de -64.
 */
public final class WaypointTeleport {

    /** Colonnes voisines essayees (rayon en blocs) quand celle du waypoint n'a pas de place sure (ocean, vide de l'End, lave). */
    public static final int SPIRAL = 8;

    /** 1.0.6 : waypoint dans un bloc : place sure cherchee jusqu'a ce nombre de blocs au-dessus (« juste au-dessus ») ; au-dela, la surface. */
    public static final int ABOVE = 2;

    private WaypointTeleport() {
    }

    public static void init() {
        RpNet.onServer(RpActions.WAYPOINT_TP, WaypointTeleport::request);
    }

    /** Administrateur : operateur de niveau 2 au moins (lot 29). */
    public static boolean admin(Player player) {
        return player.hasPermissions(2);
    }

    /** 1.0.4-beta (lot 30) : mode de jeu qui permet la teleportation : creatif ou spectateur ; jamais survie ni aventure. */
    public static boolean modeAllows(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    /** 1.0.4-beta (lot 30) : le joueur peut-il se teleporter a un waypoint maintenant ? Administrateur ET creatif / spectateur. */
    public static boolean allowed(Player player) {
        return admin(player) && modeAllows(player);
    }

    private static void request(ServerPlayer player, CompoundTag t) {
        String name = t.getString("name");
        if (!admin(player)) {
            player.displayClientMessage(Component.translatable("minimap.pioneers.tp.denied").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!modeAllows(player)) {
            player.displayClientMessage(Component.translatable("minimap.pioneers.tp.survival").withStyle(ChatFormatting.RED), true);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
        ServerLevel level = id == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            player.displayClientMessage(Component.translatable("minimap.pioneers.tp.no_dim", t.getString("dim")).withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockPos spot = safeSpot(level, t.getInt("x"), t.getInt("y"), t.getInt("z"));
        if (spot == null) {
            player.displayClientMessage(Component.translatable("minimap.pioneers.tp.no_spot").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("minimap.pioneers.tp.done", name.isEmpty() ? spot.toShortString() : name)
              .withStyle(ChatFormatting.GOLD), true);
    }

    // ------------------------------------------------------------------ recherche de la place (pure : banc tools/rptest/WaypointTpBench)

    /** Sol sous les pieds : rien (air, lave, feu, magma, cactus...), de l'eau (on y flotte : dernier recours), un sol plein. */
    public static final int FLOOR_NONE = 0, FLOOR_WATER = 1, FLOOR_SOLID = 2;

    /** Une colonne (x, z) du monde, vue par la recherche ; le monde reel ({@link #column}) ou un banc. */
    public interface Column {

        /** Plus basse hauteur construite (le sol de bedrock est a cette hauteur). */
        int minY();

        /** Premiere hauteur utilisable au-dessus de tout : plafond de bedrock (Nether) ou haut du monde. */
        int ceiling();

        boolean hasCeiling();

        /** Rien qui arrete un joueur ni aucun liquide a cette hauteur. */
        boolean passable(int y);

        /** Nature du bloc a cette hauteur comme sol ({@link #FLOOR_SOLID}...). */
        int floor(int y);

        /** Carte des hauteurs MOTION_BLOCKING : premiere hauteur libre au-dessus du plus haut bloc qui arrete (feuilles, eau comprises). */
        int surface();

        /** Meme carte sans les feuilles : sous elle, on est « sous le sol » (grotte, sous un toit). */
        int ground();
    }

    /** Pieds a {@code y} : sol plein dessous, pieds et tete libres, sous le plafond, au-dessus du bedrock. */
    public static boolean safe(Column c, int y) {
        return y >= c.minY() + 1 && y + 1 < c.ceiling() && c.floor(y - 1) == FLOOR_SOLID && c.passable(y) && c.passable(y + 1);
    }

    /**
     * Hauteur des pieds dans la colonne, ou null : la hauteur du waypoint si elle est sure (et, sans plafond, pas sous le sol) ; un waypoint
     * DANS un bloc : la premiere place sure au-dessus de lui (1.0.6) ; sinon la surface si elle est sure ; dans une dimension a plafond, la place
     * sure la plus proche du waypoint, sous le plafond.
     */
    @Nullable
    public static Integer find(Column c, int wanted) {
        if (!c.hasCeiling()) {
            if (wanted >= c.ground() && safe(c, wanted)) {
                return wanted;
            }
            //1.0.6 (reponse de Guillaume du 16/11/2026) : waypoint pose dans un bloc : on arrive juste au-dessus (au plus ABOVE blocs plus
            //haut), pas a la surface de la colonne. Un waypoint enfoui plus profond dans le terrain (ou au ras du fond : waypoint pose sur la
            //carte d'un chunk que le client n'avait pas, bug du lot 29) garde la regle du lot 30 : la surface, jamais sous le sol.
            if (wanted > c.minY() + 1 && wanted < c.ceiling() && !c.passable(wanted)) {
                for (int y = wanted + 1; y <= wanted + ABOVE && y + 1 < c.ceiling(); y++) {
                    //jamais sous le sol (regle du lot 30) : une grotte juste au-dessus d'un waypoint enfoui ne compte pas
                    if (y >= c.ground() && safe(c, y)) {
                        return y;
                    }
                }
            }
            int s = c.surface();
            return safe(c, s) ? s : null;
        }
        int lo = c.minY() + 1, hi = c.ceiling() - 2;
        int y0 = Math.max(lo, Math.min(hi, wanted));
        for (int d = 0; d <= hi - lo; d++) {
            if (y0 - d >= lo && safe(c, y0 - d)) {
                return y0 - d;
            }
            if (d > 0 && y0 + d <= hi && safe(c, y0 + d)) {
                return y0 + d;
            }
            if (y0 - d < lo && y0 + d > hi) {
                break;
            }
        }
        return null;
    }

    /** Dernier recours sans plafond : flotter a la surface de l'eau (jamais dans la lave) ; null sinon. */
    @Nullable
    public static Integer onWater(Column c) {
        int s = c.surface();
        return !c.hasCeiling() && s >= c.minY() + 1 && s + 1 < c.ceiling() && c.floor(s - 1) == FLOOR_WATER && c.passable(s) && c.passable(s + 1) ? s
                                                                                                                                                 : null;
    }

    /** Place sure pour un waypoint en (x, y, z) de {@code level} (colonne du waypoint puis voisines), ou null. */
    @Nullable
    public static BlockPos safeSpot(ServerLevel level, int x, int y, int z) {
        for (int r = 0; r <= SPIRAL; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    Integer h = find(column(level, x + dx, z + dz), y);
                    if (h != null) {
                        return new BlockPos(x + dx, h, z + dz);
                    }
                }
            }
        }
        Integer w = onWater(column(level, x, z));
        return w == null ? null : new BlockPos(x, w, z);
    }

    /** Colonne du monde reel ; le chunk est charge (genere au besoin) AVANT de lire la carte des hauteurs. */
    public static Column column(ServerLevel level, int x, int z) {
        level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        boolean roof = level.dimensionType().hasCeiling();
        int min = level.getMinBuildHeight(), top = roof ? Math.min(level.getMaxBuildHeight(), min + level.dimensionType().logicalHeight())
                                                       : level.getMaxBuildHeight();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        return new Column() {
            @Override
            public int minY() {
                return min;
            }

            @Override
            public int ceiling() {
                return top;
            }

            @Override
            public boolean hasCeiling() {
                return roof;
            }

            @Override
            public boolean passable(int y) {
                p.set(x, y, z);
                BlockState s = level.getBlockState(p);
                return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty() && !s.is(BlockTags.FIRE) && !s.is(Blocks.POWDER_SNOW)
                       && !s.is(Blocks.SWEET_BERRY_BUSH) && !s.is(Blocks.COBWEB);
            }

            @Override
            public int floor(int y) {
                p.set(x, y, z);
                BlockState s = level.getBlockState(p);
                FluidState f = s.getFluidState();
                if (f.is(FluidTags.LAVA)) {
                    return FLOOR_NONE;
                }
                if (!f.isEmpty() && s.getCollisionShape(level, p).isEmpty()) {
                    return f.is(FluidTags.WATER) ? FLOOR_WATER : FLOOR_NONE;
                }
                if (s.getCollisionShape(level, p).isEmpty() || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(BlockTags.CAMPFIRES)
                    || s.is(Blocks.BEDROCK) && roof && y >= top - 6) {
                    return FLOOR_NONE;
                }
                return FLOOR_SOLID;
            }

            @Override
            public int surface() {
                return surface;
            }

            @Override
            public int ground() {
                return ground;
            }
        };
    }
}
