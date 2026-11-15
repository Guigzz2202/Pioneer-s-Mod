package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.core.profile.RestorerDeskBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.society.economy.EconomyBlocks;
import fr.pioneers.society.economy.stall.TallStallBlock;
import fr.pioneers.farm.crustacean.FishingNetBlock;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import fr.pioneers.farm.food.DishShelfBlock;
import fr.pioneers.farm.leisure.PiLeisure;
import fr.pioneers.tech.forge.smithing.ToolRackBlock;
import fr.pioneers.building.home.HomeWoods;
import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.line.LineShape;
import fr.pioneers.tech.kinetics.rope.RopeMotion;
import fr.pioneers.world.nature.LeafDecay;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiForge;
import fr.pioneers.society.village.VillageBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Patch 1.0.3-beta, lot 11 (GameTests) : retours de Guillaume du 05/11 soir.
 * <ul>
 *   <li>fabrications : chaque bloc de village, de mobilier et de decor du mod a une recette d'etabli CHARGEE par le serveur ;</li>
 *   <li>etageres : montants avant seulement sous une etagere ou un bloc ({@code topped}) ; porte-outils : un par bois, une tuile ;</li>
 *   <li>etal de 2 blocs : haut pose, toit teint (les deux moities), haut retire avec le bas, haut pose sur un etal d'avant ;</li>
 *   <li>filet de peche : voisins du meme plan relies, axe croise separe ;</li>
 *   <li>corde a linge : creux des pieces ; cordes des poulies : sens de chaque brin = sens de la jante (memes rotations que le dessin) ;</li>
 *   <li>feuilles : apres l'abattage, les feuilles naturelles orphelines tombent en quelques secondes, jamais les feuilles posees.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot11Tests {

    private PatchP103Lot11Tests() {
    }

    // ================================================================== fabrications
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11_recipes", timeoutTicks = 20)
    public static void p103_11_fabrications_du_village_et_du_mobilier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Set<Item> made = new HashSet<>();
        for (BenchRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiBronze.BENCH.get())) {
            made.add(r.result().getItem());
        }
        List<BlockEntry<?>> all = new ArrayList<>(List.of(EconomyBlocks.MARKET_STALL, EconomyBlocks.MARKET_COUNTER, EconomyBlocks.SALE_BIN,
              EconomyBlocks.PEDDLER_CRATE, VillageBlocks.NEEDS_BOARD, VillageBlocks.VILLAGE_FOUNTAIN, VillageBlocks.VILLAGE_LAMPPOST, PiLeisure.ASHTRAY,
              RestorerDeskBlock.ENTRY, PiHome.ROPE_HOOK));
        all.addAll(PiHome.TABLES.values());
        all.addAll(PiHome.CHAIRS.values());
        all.addAll(PiHome.CABINETS.values());
        all.addAll(DishShelfBlock.VARIANTS.values());
        all.addAll(PiForge.TOOL_RACKS.values());
        for (BlockEntry<?> e : all) {
            Gt.check(made.contains(e.getBlock().asItem()), "recette d'etabli chargee : " + e.getBlock());
        }
        s.done();
    }

    // ================================================================== etageres et porte-outils
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11_shelves", timeoutTicks = 20)
    public static void p103_11_etageres_et_porte_outils(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState shelf = DishShelfBlock.VARIANTS.get("birch").getBlock().defaultBlockState();
        BlockPos a = new BlockPos(2, 2, 2);
        h.setBlock(a.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(a, shelf);
        //1.0.3-beta (lot 12) : plus de « topped » (montants avant) : etagere au sol a 4 montants, planche haute seulement au sommet
        h.setBlock(a, DishShelfBlock.connect(h.getBlockState(a), s.level, h.absolutePos(a)));
        Gt.check(!h.getBlockState(a).getValue(DishShelfBlock.UP), "seule : planche haute (pas up)");
        h.setBlock(a.above(), shelf);
        h.setBlock(a.above(), h.getBlockState(a.above()).updateShape(Direction.DOWN, h.getBlockState(a), s.level, h.absolutePos(a.above()),
              h.absolutePos(a)));
        Gt.check(h.getBlockState(a).getValue(DishShelfBlock.UP) && h.getBlockState(a.above()).getValue(DishShelfBlock.DOWN),
              "etagere empilee : up / down");
        // porte-outils : un par bois, le chene garde tool_rack, une seule tuile
        Gt.equal(HomeWoods.ALL.size(), (long) PiForge.TOOL_RACKS.size(), "un porte-outils par bois");
        Gt.check(PiForge.TOOL_RACKS.get("oak") == PiForge.TOOL_RACK, "porte-outils de chene = tool_rack d'avant (mondes existants)");
        for (BlockEntry<ToolRackBlock> e : PiForge.TOOL_RACKS.values()) {
            Gt.check(PiForge.TOOL_RACK_TILE.get().isValid(e.getBlock().defaultBlockState()), "tuile commune : " + e.getBlock());
        }
        s.done();
    }

    // ================================================================== etal
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11_stall", timeoutTicks = 20)
    public static void p103_11_etal_deux_blocs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        TallStallBlock block = EconomyBlocks.MARKET_STALL.getBlock();
        BlockPos low = new BlockPos(2, 2, 2), abs = h.absolutePos(low);
        BlockState st = block.defaultBlockState();
        Gt.equal(TallStallBlock.DEFAULT_COLOR, st.getValue(TallStallBlock.COLOR), "toit d'avant : rouge par defaut");
        h.setBlock(low, st);
        block.setPlacedBy(s.level, abs, st, null, new ItemStack(block));
        BlockState up = h.getBlockState(low.above());
        Gt.check(up.is(block) && up.getValue(TallStallBlock.HALF) == DoubleBlockHalf.UPPER, "le haut de l'etal est pose");
        Gt.check(TallStallBlock.dye(s.level, abs, DyeColor.BLUE), "teinture du toit");
        Gt.equal(DyeColor.BLUE, h.getBlockState(low).getValue(TallStallBlock.COLOR), "bas teint");
        Gt.equal(DyeColor.BLUE, h.getBlockState(low.above()).getValue(TallStallBlock.COLOR), "haut teint");
        h.setBlock(low, Blocks.AIR.defaultBlockState());
        Gt.check(h.getBlockState(low.above()).isAir(), "le haut disparait avec le bas");
        // etal d'avant (un seul bloc) : son haut est pose au tick programme par StallTile.onLoad
        BlockPos old = new BlockPos(5, 2, 5);
        h.setBlock(old, st);
        block.tick(h.getBlockState(old), s.level, h.absolutePos(old), RandomSource.create(1));
        Gt.check(h.getBlockState(old.above()).is(block), "etal d'avant : haut pose si la place est libre");
        s.done();
    }

    // ================================================================== filet
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11_net", timeoutTicks = 20)
    public static void p103_11_filets_relies(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState net = PiCrustaceans.FISHING_NET.getBlock().defaultBlockState().setValue(FishingNetBlock.FACING, Direction.NORTH);
        BlockPos a = new BlockPos(2, 2, 2), b = a.east(), c = a.above();
        h.setBlock(a, net);
        h.setBlock(b, net.setValue(FishingNetBlock.FACING, Direction.SOUTH));
        h.setBlock(c, net);
        BlockState sa = FishingNetBlock.connect(h.getBlockState(a), s.level, h.absolutePos(a));
        Gt.check(sa.getValue(FishingNetBlock.RIGHT) && !sa.getValue(FishingNetBlock.LEFT), "voisin a l'est (meme axe, autre sens) : relie a droite");
        Gt.check(sa.getValue(FishingNetBlock.UP) && !sa.getValue(FishingNetBlock.DOWN), "filet au-dessus : relie en haut");
        h.setBlock(b, net.setValue(FishingNetBlock.FACING, Direction.EAST));
        sa = FishingNetBlock.connect(h.getBlockState(a), s.level, h.absolutePos(a));
        Gt.check(!sa.getValue(FishingNetBlock.RIGHT), "axe croise : pas relie");
        s.done();
    }

    // ================================================================== corde a linge et poulies (regles pures)
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11_ropes", timeoutTicks = 20)
    public static void p103_11_cordes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        double[] one = {0.5};
        Gt.check(LineShape.dip(0, one) == 0 && LineShape.dip(1, one) == 0, "creux nul aux crochets");
        Gt.check(Math.abs(LineShape.dip(0.5, one) - LineShape.DIP) < 1e-9, "creux d'une piece au milieu");
        Gt.check(LineShape.dip(0.5, one) > LineShape.dip(0.3, one), "le creux est le plus fort sous la piece");
        Gt.check(LineShape.dip(0.5, new double[]{0.2, 0.4, 0.5, 0.6, 0.8, 0.5, 0.5}) <= LineShape.DIP_MAX + 1e-9, "creux total borne");
        // poulies : la jante (rotation +axe comme le dessin : align puis rotation en y) va dans le sens du brin, sur les 3 axes
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (float[] c : new float[][]{{8, 8, 48, 0, 0}, {8, 4, 0, 48, 0}, {12, 8, 40, 16, 0}, {4, 12, 0, 0, 40}}) {
                for (boolean crossed : new boolean[]{false, true}) {
                    check(axis, c[0] - 1.2F, c[1] - 1.2F, c[2], c[3], c[4], crossed);
                }
            }
        }
        Gt.check(Math.abs(RopeMotion.travel(360, 6.8) - 2 * Math.PI * 6.8) < 1e-9, "un tour = la circonference de la gorge");
        s.done();
    }

    /** Point (u, v) du plan de la corde -> monde (memes conventions que MachineVisuals.rope). */
    private static Vector3f world(Direction.Axis axis, double u, double v) {
        return switch (axis) {
            case X -> new Vector3f(0, (float) v, (float) u);
            case Y -> new Vector3f((float) u, 0, (float) v);
            case Z -> new Vector3f((float) u, (float) v, 0);
        };
    }

    private static void check(Direction.Axis axis, float ra, float rb, float ox, float oy, float oz, boolean crossed) {
        float du = (axis == Direction.Axis.X ? oz : ox), dv = (axis == Direction.Axis.Y ? oz : oy);
        double dist = Math.hypot(du, dv);
        if (dist < 1) {
            return;
        }
        double base = Math.atan2(dv, du);
        //rotation de la poulie comme KDraw.align(axis) puis rotate(Y, angle)
        Quaternionf align = switch (axis) {
            case X -> new Quaternionf().rotationZ((float) Math.toRadians(-90));
            case Z -> new Quaternionf().rotationX((float) Math.toRadians(90));
            default -> new Quaternionf();
        };
        Quaternionf spin = new Quaternionf(align).mul(new Quaternionf().rotationY((float) Math.toRadians(1))).mul(new Quaternionf(align).invert());
        for (int side = -1; side <= 1; side += 2) {
            double phi = Math.asin(Math.max(-1, Math.min(1, (crossed ? ra + rb : ra - rb) / dist)));
            double na = base + side * (Math.PI / 2 - phi), nb = crossed ? na + Math.PI : na;
            Vector3f pa = world(axis, Math.cos(na) * ra, Math.sin(na) * ra);
            Vector3f pb = world(axis, du + Math.cos(nb) * rb, dv + Math.sin(nb) * rb);
            Vector3f moved = spin.transform(new Vector3f(pa));
            Vector3f vel = moved.sub(pa);
            Vector3f strand = new Vector3f(pb).sub(pa);
            int real = vel.dot(strand) > 0 ? 1 : -1;
            Gt.equal(real, RopeMotion.strandSign(axis, side), "sens du brin " + side + " axe " + axis + (crossed ? " croise" : ""));
        }
    }

    // ================================================================== feuilles
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11_leaves", timeoutTicks = 120)
    public static void p103_11_feuilles_qui_tombent(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, false);
        List<BlockPos> logs = new ArrayList<>(), leaves = new ArrayList<>();
        for (int y = 1; y <= 3; y++) {
            BlockPos p = new BlockPos(4, y, 4);
            h.setBlock(p, Blocks.OAK_LOG.defaultBlockState());
            logs.add(h.absolutePos(p));
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 3; y <= 4; y++) {
                    if (dx == 0 && dz == 0 && y == 3) {
                        continue;
                    }
                    BlockPos p = new BlockPos(4 + dx, y, 4 + dz);
                    h.setBlock(p, leaf);
                    leaves.add(p);
                }
            }
        }
        BlockPos placed = new BlockPos(4, 5, 4);
        h.setBlock(placed, leaf.setValue(LeavesBlock.PERSISTENT, true));
        h.runAtTickTime(5, () -> {
            for (BlockPos p : logs) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            LeafDecay.schedule(level, logs);
        });
        h.runAtTickTime(110, () -> {
            for (BlockPos p : leaves) {
                Gt.check(!h.getBlockState(p).is(Blocks.OAK_LEAVES), "feuille naturelle orpheline tombee : " + p);
            }
            Gt.check(h.getBlockState(placed).is(Blocks.OAK_LEAVES), "feuille posee par un joueur (persistent) : jamais");
            s.done();
        });
    }
}
