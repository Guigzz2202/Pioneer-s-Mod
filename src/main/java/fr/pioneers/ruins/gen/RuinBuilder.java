package fr.pioneers.ruins.gen;

import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.common.projector.ProjectedStructure;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.industry.multi.ControllerBlock;
import fr.pioneers.industry.multi.IndustrialBlueprint;
import fr.pioneers.industry.multi.IndustrialGlassBlock;
import fr.pioneers.industry.multi.ModuleBlock;
import fr.pioneers.industry.multi.PartBlock;
import fr.pioneers.industry.multi.PortBlock;
import fr.pioneers.industry.multi.SheetMetalBlock;
import fr.pioneers.industry.multi.StructureFrameBlock;
import fr.pioneers.ruins.ArchiveCabinetBlock;
import fr.pioneers.ruins.ArchiveCabinetTile;
import fr.pioneers.ruins.DerelictBlock;
import fr.pioneers.ruins.DerelictControllerTile;
import fr.pioneers.ruins.PerimeterConsoleBlock;
import fr.pioneers.ruins.RuinBlocks;
import fr.pioneers.ruins.ScrapCategory;
import fr.pioneers.ruins.SiteTile;
import fr.pioneers.ruins.sentinel.Sentinels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9 (MAJ RP, bloc 1) : pose d'une ruine dans le monde (chunk du site, ecritures bornees aux 3 x 3 chunks voisins). Plan reel du multibloc
 * ({@link IndustrialBlueprint}) tourne et mis en miroir comme le dit le {@link RuinPlan}, degrade par {@link RuinDesign}, puis : terrain
 * (remblai et deblai sous l'emprise, ou salle creusee pour le MKIII souterrain, puits d'acces), blocs delabres, tuiles (Controleur, Console,
 * Armoires), vegetation selon le biome, Sentinelles endormies. Aucune donnee sauvee : le Controleur cree son site au premier chargement.
 */
final class RuinBuilder {

    private static final long SALT_BUILD = 0x4255494CL;
    private final GenWorld w;
    private final RuinPlan plan;
    private final GenSettings settings;
    private final GenSettings.Tier ts;
    private final int tier;
    private final Rng rng;

    RuinBuilder(WorldGenLevel level, ChunkPos center, RuinPlan plan, GenSettings settings) {
        this.w = new GenWorld(level, center);
        this.plan = plan;
        this.settings = settings;
        this.tier = plan.site.tier();
        this.ts = settings.tier(tier);
        this.rng = new Rng(Rng.hash(plan.site.seed(), SALT_BUILD));
    }

    boolean build() {
        //Plan reel, tel que la restauration le reconstruira : layout(0, controleur, forward), controleur a l'ancre
        Map<BlockPos, ProjectedStructure.Ghost> layout = IndustrialBlueprint.of(plan.industry).layout(0, BlockPos.ZERO, plan.forward);
        if (layout.isEmpty()) {
            return false;
        }
        //1. Cases du plan, relatives au controleur
        int n = layout.size(), k = 0;
        int[][] rel = new int[n][];
        byte[] role = new byte[n], hint = new byte[n];
        int rx0 = Integer.MAX_VALUE, rx1 = Integer.MIN_VALUE, ry0 = Integer.MAX_VALUE, ry1 = Integer.MIN_VALUE, rz0 = Integer.MAX_VALUE,
              rz1 = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : layout.entrySet()) {
            BlockPos p = e.getKey();
            rel[k] = new int[]{p.getX(), p.getY(), p.getZ()};
            hint[k] = -1;
            role[k] = role(e.getValue(), hint, k);
            rx0 = Math.min(rx0, p.getX());
            rx1 = Math.max(rx1, p.getX());
            ry0 = Math.min(ry0, p.getY());
            ry1 = Math.max(ry1, p.getY());
            rz0 = Math.min(rz0, p.getZ());
            rz1 = Math.max(rz1, p.getZ());
            k++;
        }
        int m = settings.margin();
        int wx0 = Math.max(w.minX, plan.ctrlX + rx0 - m), wx1 = Math.min(w.maxX, plan.ctrlX + rx1 + m);
        int wz0 = Math.max(w.minZ, plan.ctrlZ + rz0 - m), wz1 = Math.min(w.maxZ, plan.ctrlZ + rz1 + m);
        int gw = wx1 - wx0 + 1, gd = wz1 - wz0 + 1;
        //2. Sol : hauteurs des colonnes, mode de pose
        int[] top = new int[gw * gd];
        BlockState[] topState = new BlockState[gw * gd];
        List<Integer> footTops = new ArrayList<>();
        for (int z = 0; z < gd; z++) {
            for (int x = 0; x < gw; x++) {
                int g = w.ground(wx0 + x, wz0 + z);
                top[z * gw + x] = g;
                topState[z * gw + x] = w.get(wx0 + x, g, wz0 + z);
                int ax = wx0 + x - plan.ctrlX, az = wz0 + z - plan.ctrlZ;
                if (ax >= rx0 && ax <= rx1 && az >= rz0 && az <= rz1) {
                    footTops.add(g);
                }
            }
        }
        if (footTops.isEmpty()) {
            return false;
        }
        footTops.sort(Integer::compare);
        int median = footTops.get(footTops.size() / 2), low = footTops.get(0), high = footTops.get(footTops.size() - 1);
        int centerTop = w.ground(plan.site.centerX(), plan.site.centerZ());
        int lift = ry0 == 0 ? 1 : -ry0;                                                        //controleur au-dessus de la rangee du sol
        int planTop = lift + ry1;                                                             //rangee du haut du plan (rangee du sol = 0)
        int gh = planTop + 3;
        boolean wet = w.water(plan.site.centerX(), centerTop, plan.site.centerZ()) > 0;
        String mode = tier <= 1 ? "surface" : wet && tier >= 3 ? "underground" : ts.mode(rng);
        int yG;
        switch (mode) {
            case "sunken" -> yG = median - rng.range(2, 4);
            case "flank" -> {
                if (high - low >= 8) {
                    yG = low;
                } else {
                    mode = tier >= 3 ? "underground" : "surface";
                    yG = median;
                }
            }
            default -> yG = median;
        }
        if ("underground".equals(mode)) {
            yG = centerTop - rng.range(ts.depthMin(), ts.depthMax()) - gh;
            if (yG < w.minY + 6 || yG + gh > centerTop - 4) {
                if (wet) {
                    return false;                                                         //ni salle sous la mer, ni ruine noyee
                }
                mode = high - low >= 8 ? "flank" : "surface";
                yG = "flank".equals(mode) ? low : median;
            }
        }
        boolean underground = "underground".equals(mode);
        if (yG + gh + 4 > w.maxY) {
            return false;
        }
        //3. Terrain
        if (underground) {
            excavate(wx0, wx1, wz0, wz1, yG, yG + gh);
        } else {
            for (int z = 0; z < gd; z++) {
                for (int x = 0; x < gw; x++) {
                    int c = z * gw + x, t = top[c], X = wx0 + x, Z = wz0 + z;
                    for (int y = Math.max(t + 1, yG - 24); y <= yG; y++) {
                        w.set(X, y, Z, GenWorld.fill(topState[c], yG - y, y));
                    }
                    if (t > yG && topState[c].is(BlockTags.DIRT) && w.get(X, yG, Z).is(BlockTags.DIRT)) {
                        w.set(X, yG, Z, topState[c]);                                       //deblai : la terre mise a nu reprend son herbe
                    }
                    int clearTo = Math.min(Math.max(t, yG + gh), yG + gh + 6);
                    for (int y = yG + 1; y <= clearTo; y++) {
                        w.clear(X, y, Z);
                    }
                }
            }
        }
        //4. Ruine
        RuinDesign.Input in = new RuinDesign.Input(gw, gh + 1, gd);
        in.ground = 0;
        int interior = 0;
        for (int i = 0; i < n; i++) {
            int x = plan.ctrlX + rel[i][0] - wx0, y = rel[i][1] + lift, z = plan.ctrlZ + rel[i][2] - wz0;
            if (!in.in(x, y, z)) {
                continue;
            }
            int idx = in.index(x, y, z);
            in.role[idx] = role[i];
            in.hint[idx] = hint[i];
            if (role[i] == RuinDesign.FREE || role[i] == RuinDesign.MODULE) {
                interior++;
            }
        }
        in.ctrlX = plan.ctrlX - wx0;
        in.ctrlY = lift;
        in.ctrlZ = plan.ctrlZ - wz0;
        in.facade = plan.forward.getOpposite().get2DDataValue();
        in.tier = tier;
        in.dominant = plan.category.hasTradeBlock() ? plan.category.ordinal() : ScrapCategory.MOTION.ordinal();
        in.wear = rng.range(ts.wearMin(), ts.wearMax()) * PiCommonConfig.get(RpConfig.RUIN_WEAR, 1.0);
        in.worn = ts.worn();
        in.rubble = ts.rubble();
        in.cabinets = interior >= settings.secondCabinet() ? 2 : 1;
        RuinDesign.Output out = RuinDesign.design(in, rng);
        BlockPos ctrl = new BlockPos(plan.ctrlX, yG + lift, plan.ctrlZ);
        long siteId = ctrl.asLong();
        RandomSource random = RandomSource.create(rng.nextLong());
        List<BlockPos> panes = new ArrayList<>();
        for (int y = 0; y < in.h; y++) {
            for (int z = 0; z < in.d; z++) {
                for (int x = 0; x < in.w; x++) {
                    int code = out.block()[in.index(x, y, z)];
                    BlockState s = state(code);
                    if (s == null) {
                        continue;
                    }
                    BlockPos p = new BlockPos(wx0 + x, yG + y, wz0 + z);
                    w.set(p, s);
                    if (RuinDesign.kind(code) == RuinDesign.O_PANE) {
                        panes.add(p);
                    }
                }
            }
        }
        BlockPos min = new BlockPos(wx0, yG, wz0), max = new BlockPos(wx1, yG + gh, wz1);
        if (w.level.getBlockEntity(ctrl) instanceof DerelictControllerTile tile) {
            tile.setup(plan.industry.key, min, max, plan.forward.get2DDataValue());
        }
        if (out.console() >= 0 && w.level.getBlockEntity(at(in, out.console(), wx0, yG, wz0)) instanceof SiteTile tile) {
            tile.setSite(siteId);
        }
        for (int c = 0; c < out.cabinets().size(); c++) {
            if (w.level.getBlockEntity(at(in, out.cabinets().get(c), wx0, yG, wz0)) instanceof ArchiveCabinetTile tile) {
                tile.setLootTable(new ResourceLocation("pioneers", "archives/mk" + tier + (c == 0 ? "_code" : "")), random.nextLong());
            }
        }
        if (underground) {
            shaft(in, out, wx0, wz0, yG, gh);
        }
        //5. Vegetation, toiles, lichens, neige
        decor(in, out, wx0, wz0, yG, underground);
        for (BlockPos p : panes) {
            BlockState s = w.level.getBlockState(p);
            BlockState u = Block.updateFromNeighbourShapes(s, w.level, p);
            if (u != s) {
                w.set(p, u);
            }
        }
        //6. Sentinelles endormies sur les cases libres
        if (PiCommonConfig.get(RpConfig.SENTINELS, true)) {
            List<BlockPos> posts = new ArrayList<>();
            for (int i : out.posts()) {
                BlockPos p = at(in, i, wx0, yG, wz0);
                if (w.can(p) && !w.level.getBlockState(p).blocksMotion() && !w.level.getBlockState(p.above()).blocksMotion()) {
                    posts.add(p);
                }
            }
            rng.shuffle(posts);
            if (posts.size() > 64) {
                posts = new ArrayList<>(posts.subList(0, 64));
            }
            Sentinels.spawnForSite(w.level, siteId, tier, posts, random);
        }
        return true;
    }

    private static BlockPos at(RuinDesign.Input in, int index, int x0, int y0, int z0) {
        return new BlockPos(x0 + index % in.w, y0 + index / (in.w * in.d), z0 + index / in.w % in.d);
    }

    /** Role d'une case du plan reel. */
    private static byte role(ProjectedStructure.Ghost g, byte[] hint, int k) {
        BlockState s = g.state();
        if (s == null) {
            return RuinDesign.FREE;
        }
        Block b = s.getBlock();
        if (b instanceof ControllerBlock) {
            return RuinDesign.CONTROLLER;
        }
        if (b instanceof PortBlock) {
            return RuinDesign.PORT;
        }
        if (b instanceof PartBlock part) {
            //1.0.4-beta (lot 15) : seul reste le refrigerateur a dilution
            hint[k] = (byte) ScrapCategory.FLUID.ordinal();
            return RuinDesign.PART;
        }
        if (b instanceof ModuleBlock) {
            return RuinDesign.MODULE;
        }
        if (b instanceof IndustrialGlassBlock || b == Blocks.GLASS || b instanceof fr.pioneers.industry.construction.CleanroomGlassBlock) {
            return RuinDesign.GLASS;
        }
        if (b instanceof StructureFrameBlock) {
            return RuinDesign.FRAME;
        }
        if (b instanceof SheetMetalBlock) {
            return RuinDesign.SHEET;
        }
        //Carcasses, voile de beton, et tout le reste : casing delabre (jamais un bloc industriel)
        return RuinDesign.CASING;
    }

    /** Bloc d'un code de {@link RuinDesign} (null : rien a poser). */
    private BlockState state(int code) {
        boolean worn = RuinDesign.worn(code);
        Direction face = Direction.from2DDataValue(RuinDesign.facing(code));
        return switch (RuinDesign.kind(code)) {
            case RuinDesign.O_CASING -> derelict("casing", worn);
            case RuinDesign.O_PORT -> derelict("port", worn);
            case RuinDesign.O_TRADE -> derelict(ScrapCategory.values()[RuinDesign.category(code)].id(), worn);
            case RuinDesign.O_CONTROLLER -> RuinBlocks.get("controller", tier).defaultBlockState();
            case RuinDesign.O_PANE -> Blocks.GLASS_PANE.defaultBlockState();
            case RuinDesign.O_TINTED -> Blocks.TINTED_GLASS.defaultBlockState();
            case RuinDesign.O_RUBBLE -> derelict("rubble", worn);
            case RuinDesign.O_CHAIN -> Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
            case RuinDesign.O_CABINET -> RuinBlocks.get("cabinet", tier).defaultBlockState().setValue(ArchiveCabinetBlock.FACING, face);
            case RuinDesign.O_CONSOLE -> RuinBlocks.get("console", tier).defaultBlockState().setValue(PerimeterConsoleBlock.FACING, face);
            default -> null;
        };
    }

    private BlockState derelict(String role, boolean worn) {
        return RuinBlocks.get(role, tier).defaultBlockState().setValue(DerelictBlock.WORN, worn);
    }

    /** MKIII souterrain : salle creusee (boite + marge), sol plein, parois etanches (aucun fluide ne touche la salle). */
    private void excavate(int x0, int x1, int z0, int z1, int y0, int y1) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean wall = x < x0 || x > x1 || z < z0 || z > z1;
                for (int y = y0; y <= y1 + 1; y++) {
                    BlockState s = w.get(x, y, z);
                    if (wall || y == y0 || y == y1 + 1) {
                        if (!s.getFluidState().isEmpty() || y == y0 && !s.isSolidRender(w.level, new BlockPos(x, y, z))) {
                            w.set(x, y, z, GenWorld.rock(y));
                        }
                    } else if (!s.isAir()) {
                        w.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** MKIII souterrain : puits d'acces (echelle contre un pilier de pierre taillee) d'un coin de la salle jusqu'a la surface. */
    private void shaft(RuinDesign.Input in, RuinDesign.Output out, int x0, int z0, int yG, int gh) {
        int[][] corners = {{1, 1, -1, 0}, {in.w - 2, 1, 1, 0}, {1, in.d - 2, -1, 0}, {in.w - 2, in.d - 2, 1, 0}};
        List<int[]> order = new ArrayList<>(Arrays.asList(corners));
        rng.shuffle(order);
        for (int[] c : order) {
            int cx = c[0], cz = c[1];
            boolean free = true;
            for (int y = 1; y < in.h && free; y++) {
                free = in.in(cx, y, cz) && out.block()[in.index(cx, y, cz)] == RuinDesign.AIR && in.role[in.index(cx, y, cz)] == RuinDesign.NONE;
            }
            int X = x0 + cx, Z = z0 + cz;
            int surface = w.ground(X, Z);
            if (!free || w.water(X, surface, Z) > 0 || surface - yG > 96 || surface <= yG + gh) {
                continue;
            }
            for (int y = surface + 1; y <= surface + 12 && !w.get(X, y, Z).isAir(); y++) {
                w.clear(X, y, Z);                                                         //feuillage ou herbe au-dessus de l'ouverture
            }
            Direction dir = c[2] < 0 ? Direction.WEST : Direction.EAST;                     //pilier vers le bord exterieur de la salle
            int px = X + dir.getStepX(), pz = Z + dir.getStepZ();
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, dir.getOpposite());
            for (int y = yG + 1; y <= surface; y++) {
                w.set(px, y, pz, lining());
                w.set(X, y, Z, ladder);
                if (y > yG + gh) {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        for (int s = -1; s <= 1; s++) {
                            boolean alongX = d.getAxis() == Direction.Axis.X;
                            int rx = X + d.getStepX() + (alongX ? 0 : s), rz = Z + d.getStepZ() + (alongX ? s : 0);
                            if (rx != px || rz != pz) {
                                w.set(rx, y, rz, lining());
                            }
                        }
                    }
                }
            }
            //Margelle a la surface
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && w.get(X + dx, surface + 1, Z + dz).canBeReplaced()) {
                        w.set(X + dx, surface + 1, Z + dz, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(X + dx, surface + 1, Z + dz);
                    BlockState s = w.level.getBlockState(p);
                    if (s.is(Blocks.MOSSY_COBBLESTONE_WALL)) {
                        w.set(p, Block.updateFromNeighbourShapes(s, w.level, p));
                    }
                }
            }
            return;
        }
    }

    private BlockState lining() {
        double r = rng.nextDouble();
        return (r < 0.45 ? Blocks.STONE_BRICKS : r < 0.7 ? Blocks.CRACKED_STONE_BRICKS : r < 0.9 ? Blocks.MOSSY_STONE_BRICKS : Blocks.COBBLESTONE)
              .defaultBlockState();
    }

    /** Vegetation selon le biome (lianes, mousse, herbes ; neige au froid ; buissons morts au sec), toiles, lichen lumineux sous terre. */
    private void decor(RuinDesign.Input in, RuinDesign.Output out, int x0, int z0, int yG, boolean underground) {
        BlockPos center = new BlockPos(plan.site.centerX(), yG + 1, plan.site.centerZ());
        Holder<Biome> biome = w.level.getBiome(center);
        boolean cold = !underground && biome.value().coldEnoughToSnow(center);
        boolean dry = !cold && !biome.value().hasPrecipitation();
        boolean lush = biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.LUSH_CAVES);
        boolean taiga = biome.is(BiomeTags.IS_TAIGA);
        int[] roof = new int[in.w * in.d];
        Arrays.fill(roof, -1);
        for (int z = 0; z < in.d; z++) {
            for (int x = 0; x < in.w; x++) {
                for (int y = in.h - 1; y > 0; y--) {
                    if (RuinDesign.solid(out.block()[in.index(x, y, z)])) {
                        roof[z * in.w + x] = y;
                        break;
                    }
                }
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 1; y < in.h; y++) {
            for (int z = 0; z < in.d; z++) {
                for (int x = 0; x < in.w; x++) {
                    int X = x0 + x, Y = yG + y, Z = z0 + z;
                    if (!w.can(X, Y, Z) || !w.get(X, Y, Z).isAir()) {
                        continue;
                    }
                    p.set(X, Y, Z);
                    BlockState below = w.get(X, Y - 1, Z), above = w.get(X, Y + 1, Z);
                    boolean floor = below.isFaceSturdy(w.level, p.below(), Direction.UP);
                    boolean ceiling = above.isFaceSturdy(w.level, p.above(), Direction.DOWN);
                    boolean sky = !underground && y > roof[z * in.w + x];
                    boolean foot = in.role[in.index(x, Math.min(in.h - 1, in.ctrlY), z)] != RuinDesign.NONE || roof[z * in.w + x] > 0;
                    double r = rng.nextDouble();
                    if (ceiling && foot && r < (underground ? 0.05 : 0.025)) {
                        w.set(X, Y, Z, Blocks.COBWEB.defaultBlockState());
                        continue;
                    }
                    if (floor) {
                        if (cold && sky && r < 0.65) {
                            w.set(X, Y, Z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + rng.nextInt(2)));
                        } else if (!cold && !dry && r < (lush ? 0.22 : underground ? 0.06 : 0.12)) {
                            w.set(X, Y, Z, Blocks.MOSS_CARPET.defaultBlockState());
                        } else if (dry && sky && y == 1 && r < 0.05
                                   && (below.is(BlockTags.SAND) || below.is(BlockTags.DIRT) || below.is(BlockTags.TERRACOTTA))) {
                            w.set(X, Y, Z, Blocks.DEAD_BUSH.defaultBlockState());
                        } else if (!underground && !cold && !dry && y == 1 && !foot && below.is(Blocks.GRASS_BLOCK) && r < 0.3) {
                            w.set(X, Y, Z, (taiga && rng.nextDouble() < 0.6 ? Blocks.FERN : Blocks.GRASS).defaultBlockState());
                        }
                        continue;
                    }
                    if (underground && r < 0.035) {
                        for (Direction d : Direction.values()) {
                            BlockPos q = p.relative(d);
                            if (w.level.getBlockState(q).isFaceSturdy(w.level, q, d.getOpposite())) {
                                w.set(X, Y, Z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true));
                                break;
                            }
                        }
                    }
                }
            }
        }
        //Lianes sur les parois exterieures
        if (underground || cold || dry) {
            return;
        }
        double vine = lush ? 0.10 : 0.035;
        for (int y = 2; y < in.h; y++) {
            for (int z = 0; z < in.d; z++) {
                for (int x = 0; x < in.w; x++) {
                    if (!RuinDesign.solid(out.block()[in.index(x, y, z)])) {
                        continue;
                    }
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        int X = x0 + x + d.getStepX(), Z = z0 + z + d.getStepZ();
                        if (rng.nextDouble() >= vine || !w.can(X, yG + y, Z) || !w.get(X, yG + y, Z).isAir()) {
                            continue;
                        }
                        BlockState v = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d.getOpposite()), true);
                        int len = rng.range(1, 4);
                        for (int s = 0; s < len && yG + y - s > yG && w.get(X, yG + y - s, Z).isAir(); s++) {
                            w.set(X, yG + y - s, Z, v);
                        }
                    }
                }
            }
        }
    }
}
