package fr.pioneers.client.minimap;

import fr.pioneers.naval.HarborTile;
import fr.pioneers.naval.SeaBeaconBlock;
import fr.pioneers.pneumatic.network.TubeStationTile;
import fr.pioneers.rail.block.ItemLoaderTile;
import fr.pioneers.rail.block.RailPostTile;
import fr.pioneers.rail.block.StationTile;
import fr.pioneers.rail.block.SubstationTile;
import fr.pioneers.rail.track.TrackBlock;
import fr.pioneers.robotics.hub.BlockRoboticHubPart;
import fr.pioneers.robotics.logistics.BlockLogisticMarker;
import fr.pioneers.robotics.logistics.BlockRoboticRoad;
import fr.pioneers.robotics.logistics.MarkerRole;
import fr.pioneers.robotics.logistics.TileEntityLogisticChest;
import fr.pioneers.robotics.logistics.TileEntityLogisticTerminal;
import fr.pioneers.robotics.logistics.TileEntityRoboticRelay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * 1.10.3 : releve d'un chunk CONNU DU CLIENT (aucune donnee du serveur) : dessus de chaque colonne (carte de hauteur WORLD_SURFACE recue
 * avec le chunk ; dans une dimension a plafond, premiere surface ouverte sous le plafond), couleur de carte du bloc teintee par le biome,
 * eau et profondeur, voies ferrees et routes robotiques (calques), lieux remarquables (tuiles du chunk : gares, ports, postes,
 * sous-stations, hubs, relais, terminaux, points logistiques, stations d'hypertube ; balises maritimes posees sur l'eau). Couleurs finales
 * par {@link SurfaceShade}. Fil de rendu, tableaux de travail gardes : aucune allocation hors des lieux trouves.
 */
final class ChunkScanner {

    private final int[] base = new int[256], height = new int[256], depth = new int[256], floor = new int[256];
    private final int[] north = new int[16], west = new int[16];
    final int[] pixels = new int[256], rail = new int[256], road = new int[256];
    boolean anyRail, anyRoad;
    final List<Poi> found = new ArrayList<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Reglages lus au debut du releve. */
    private boolean relief, tint, waterDepth;
    private int railColor, roadColor;

    void settings(boolean relief, boolean tint, boolean waterDepth, int railRgb, int roadRgb) {
        this.relief = relief;
        this.tint = tint;
        this.waterDepth = waterDepth;
        railColor = SurfaceShade.abgr(railRgb);
        roadColor = SurfaceShade.abgr(roadRgb);
    }

    /** Releve {@code chunk} ; resultats dans {@link #pixels}, {@link #rail}, {@link #road}, {@link #found}. */
    void scan(ClientLevel level, LevelChunk chunk) {
        found.clear();
        anyRail = anyRoad = false;
        int cx = chunk.getPos().x, cz = chunk.getPos().z, minY = level.getMinBuildHeight();
        boolean ceiling = level.dimensionType().hasCeiling();
        int ceilingTop = Math.min(level.getMaxBuildHeight() - 1, minY + level.dimensionType().logicalHeight() - 1);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                column(level, chunk, cx, cz, x, z, minY, ceiling, ceilingTop);
            }
        }
        LevelChunk n = neighbour(level, cx, cz - 1), w = neighbour(level, cx - 1, cz);
        for (int i = 0; i < 16; i++) {
            north[i] = n == null ? height[i] : top(n, i, 15, minY, ceiling, ceilingTop);
            west[i] = w == null ? height[i * 16] : top(w, 15, i, minY, ceiling, ceilingTop);
        }
        SurfaceShade.shade(base, height, depth, floor, north, west, relief, waterDepth, pixels);
        blockEntities(chunk);
    }

    @Nullable
    private static LevelChunk neighbour(ClientLevel level, int cx, int cz) {
        return level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
    }

    /** Dessus d'une colonne : bloc le plus haut ; a plafond : premier sol sous le plafond (MIN_VALUE : rien). */
    private int top(LevelChunk chunk, int x, int z, int minY, boolean ceiling, int ceilingTop) {
        if (!ceiling) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            return y < minY ? Integer.MIN_VALUE : y;
        }
        int wx = chunk.getPos().getMinBlockX() + x, wz = chunk.getPos().getMinBlockZ() + z;
        int y = ceilingTop;
        //plafond (au plus 40 blocs pleins), puis l'air de la grotte, puis le sol
        int steps = 0;
        while (y > minY && !chunk.getBlockState(pos.set(wx, y, wz)).isAir() && steps++ < 40) {
            y--;
        }
        if (steps >= 40) {
            return Integer.MIN_VALUE;
        }
        while (y > minY && chunk.getBlockState(pos.set(wx, y, wz)).isAir()) {
            y--;
        }
        return y <= minY ? Integer.MIN_VALUE : y;
    }

    private void column(ClientLevel level, LevelChunk chunk, int cx, int cz, int x, int z, int minY, boolean ceiling, int ceilingTop) {
        int i = z * 16 + x;
        int wx = (cx << 4) + x, wz = (cz << 4) + z;
        rail[i] = 0;
        road[i] = 0;
        depth[i] = 0;
        int y = top(chunk, x, z, minY, ceiling, ceilingTop);
        height[i] = y;
        if (y == Integer.MIN_VALUE) {
            base[i] = SurfaceShade.VOID_RGB;
            return;
        }
        BlockState state = chunk.getBlockState(pos.set(wx, y, wz));
        //calques : la voie ou la route se note a part, la couleur est celle du dessous
        if (state.getBlock() instanceof TrackBlock) {
            rail[i] = railColor;
            anyRail = true;
            y--;
            state = chunk.getBlockState(pos.set(wx, y, wz));
        } else if (state.getBlock() instanceof BlockRoboticRoad) {
            road[i] = roadColor;
            anyRoad = true;
        } else if (state.getBlock() instanceof SeaBeaconBlock) {
            found.add(new Poi(Poi.Kind.SEA_BEACON, wx, y, wz, Poi.Kind.SEA_BEACON.color, ""));
            y--;
            state = chunk.getBlockState(pos.set(wx, y, wz));
        }
        MapColor color = state.getMapColor(chunk, pos);
        int guard = 0;
        while (color == MapColor.NONE && y > minY && guard++ < 16) {
            y--;
            state = chunk.getBlockState(pos.set(wx, y, wz));
            color = state.getMapColor(chunk, pos);
        }
        if (!state.getFluidState().isEmpty() && color == MapColor.WATER) {
            //eau : profondeur jusqu'au fond (16 au plus), teinte de l'eau du biome
            int d = 0;
            int fy = y;
            BlockState below = state;
            while (fy > minY && d < 16 && !below.getFluidState().isEmpty()) {
                fy--;
                d++;
                below = chunk.getBlockState(pos.set(wx, fy, wz));
            }
            depth[i] = Math.max(1, d);
            MapColor fc = below.getMapColor(chunk, pos);
            floor[i] = fc == MapColor.NONE ? MapColor.SAND.col : fc.col;
            base[i] = tint ? waterColor(chunk, wx, y, wz) : MapColor.WATER.col;
            pos.set(wx, y, wz);
            return;
        }
        int rgb = color == MapColor.NONE ? MapColor.STONE.col : color.col;
        if (tint) {
            if (color == MapColor.GRASS) {
                rgb = SurfaceShade.scale(biome(chunk, wx, y, wz).getGrassColor(wx, wz), 0.86F);
            } else if (state.is(BlockTags.LEAVES) && color == MapColor.PLANT) {
                rgb = SurfaceShade.scale(biome(chunk, wx, y, wz).getFoliageColor(), 0.78F);
            }
        }
        base[i] = rgb;
    }

    private static Biome biome(LevelChunk chunk, int wx, int y, int wz) {
        return chunk.getNoiseBiome(wx >> 2, y >> 2, wz >> 2).value();
    }

    private static int waterColor(LevelChunk chunk, int wx, int y, int wz) {
        //teinte de l'eau du biome, rapprochee de la couleur de carte pour rester lisible
        return SurfaceShade.mix(biome(chunk, wx, y, wz).getWaterColor(), MapColor.WATER.col, 0.35F);
    }

    /** Lieux remarquables des tuiles du chunk. */
    private void blockEntities(LevelChunk chunk) {
        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            BlockEntity be = e.getValue();
            BlockPos p = e.getKey();
            BlockState state = be.getBlockState();
            Poi.Kind kind = null;
            int color = -1;
            String name = "";
            if (be instanceof StationTile st) {
                kind = Poi.Kind.STATION;
                name = st.name();
            } else if (be instanceof HarborTile) {
                kind = Poi.Kind.HARBOR;
            } else if (be instanceof ItemLoaderTile || be instanceof RailPostTile) {
                kind = Poi.Kind.RAIL_POST;
            } else if (be instanceof SubstationTile) {
                kind = Poi.Kind.SUBSTATION;
            } else if (be instanceof TileEntityRoboticRelay) {
                kind = Poi.Kind.RELAY;
            } else if (be instanceof TileEntityLogisticTerminal) {
                kind = Poi.Kind.TERMINAL;
            } else if (be instanceof TileEntityLogisticChest) {
                kind = Poi.Kind.CHEST;
            } else if (be instanceof TubeStationTile) {
                kind = Poi.Kind.TUBE_STATION;
            } else if (state.getBlock() instanceof BlockLogisticMarker) {
                kind = Poi.Kind.POINT;
                MarkerRole role = state.getValue(BlockLogisticMarker.ROLE);
                color = role.color;
            } else if (state.getBlock() instanceof BlockRoboticHubPart && hubCorner(chunk, p)) {
                //un hub = un lieu : le coin bas nord-ouest le porte, au centre du cube 3 x 3
                found.add(new Poi(Poi.Kind.HUB, p.getX() + 1, p.getY() + 1, p.getZ() + 1, Poi.Kind.HUB.color, ""));
                continue;
            }
            if (kind != null) {
                found.add(new Poi(kind, p.getX(), p.getY(), p.getZ(), color < 0 ? kind.color : color, name == null ? "" : name));
            }
        }
    }

    private boolean hubCorner(LevelChunk chunk, BlockPos p) {
        return !hub(chunk, p.west()) && !hub(chunk, p.north()) && !hub(chunk, p.below());
    }

    private static boolean hub(LevelChunk chunk, BlockPos p) {
        //hors du chunk : la case voisine est lue dans le monde client (chunk voisin charge ou non : air)
        if (p.getX() >> 4 != chunk.getPos().x || p.getZ() >> 4 != chunk.getPos().z) {
            return chunk.getLevel().getBlockState(p).getBlock() instanceof BlockRoboticHubPart;
        }
        return chunk.getBlockState(p).getBlock() instanceof BlockRoboticHubPart;
    }
}
