package fr.pioneers.gametest;

import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.parts.HandCrankBlock;
import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.logistics.warehouse.CounterBlock;
import fr.pioneers.logistics.warehouse.CounterTile;
import fr.pioneers.logistics.warehouse.CrateHatchTile;
import fr.pioneers.logistics.warehouse.CrateLayout;
import fr.pioneers.logistics.warehouse.PiWarehouse;
import fr.pioneers.logistics.warehouse.WarehouseLines;
import fr.pioneers.logistics.warehouse.WarehouseRopes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 2 (GameTests) : les poulies de base dans l'entrepot et le comptoir pose au sol. Reseau avec relais en poulies de
 * base (petite et grande, deux cordes chacune) jusqu'a un comptoir AU SOL desservi par descente (poulie d'entrepot 2 blocs au-dessus) :
 * le colis est livre ; poulie d'entrepot <-> poulie de base : la rotation passe dans les deux sens au rapport des rayons primitifs ; poulie
 * de base d'axe horizontal refusee ; poulie de base au-dessus d'un comptoir : pas de station, message clair ; colonne bouchee et comptoir
 * trop bas : messages ; l'ancienne facon (poulie posee sur le comptoir) et une caisse desservie par une ligne plus haute ; rien ne bouge
 * sans rotation.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot2Tests {

    private PatchP104Lot2Tests() {
    }

    /** Caisse courte selon Z (origine relative {@code rel}) ; renvoie la trappe formee. */
    private static CrateHatchTile crate(GameTestHelper h, BlockPos rel) {
        BlockPos origin = h.absolutePos(rel);
        BlockPos hatch = null;
        for (int w = 0; w < 3; w++) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    CrateLayout.Kind k = CrateLayout.kind(u, v, w, 3);
                    BlockPos p = CrateLayout.at(origin, Direction.Axis.Z, u, v, w);
                    if (u == 1 && v == 2 && w == 1) {
                        k = CrateLayout.Kind.HATCH;
                        hatch = p;
                    }
                    h.getLevel().setBlock(p, CrateLayout.block(k).defaultBlockState(), 3);
                }
            }
        }
        CrateHatchTile t = (CrateHatchTile) h.getLevel().getBlockEntity(hatch);
        Gt.check(t.house().checkNow(), "caisse non formee : " + t.house().error());
        return t;
    }

    /** 1.0.4-beta (lot 27) : la poulie d'entrepot retiree : une PETITE poulie a axe vertical (la station) a sa place. */
    private static BlockPos warehousePulley(GameTestHelper h, BlockPos abs) {
        h.getLevel().setBlock(abs, PiKinetics.SMALL_PULLEY.getBlock().defaultBlockState(), 3);
        return abs;
    }

    private static BlockPos basePulley(GameTestHelper h, BlockPos abs, Block block, Direction.Axis axis) {
        h.getLevel().setBlock(abs, block.defaultBlockState().setValue(AxisKineticBlock.AXIS, axis), 3);
        return abs;
    }

    private static BlockPos counter(GameTestHelper h, BlockPos abs) {
        h.getLevel().setBlock(abs, PiWarehouse.COUNTER.getBlock().defaultBlockState().setValue(CounterBlock.FACING, Direction.WEST), 3);
        return abs;
    }

    private static void rope(GameTestHelper h, BlockPos a, BlockPos b) {
        Gt.check(WarehouseRopes.link(h.getLevel(), a, b, LinkType.FIBER_ROPE), "corde " + a + " -> " + b + " refusee : " + WarehouseRopes.check(h.getLevel(), a, b));
    }

    private static HandCrankBlock.Tile crank(GameTestHelper h, BlockPos abs, Direction facing) {
        h.getLevel().setBlock(abs, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, facing), 3);
        return (HandCrankBlock.Tile) h.getLevel().getBlockEntity(abs);
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    private static int out(CounterTile counter, net.minecraft.world.item.Item item) {
        int got = 0;
        for (var slot : counter.outputs()) {
            if (slot.getStack().is(item)) {
                got += slot.getCount();
            }
        }
        return got;
    }

    /**
     * Caisse (station sur la trappe) -> relais d'entrepot -> petite poulie de base -> grande poulie de base -> poulie d'entrepot 2 blocs
     * au-dessus d'un comptoir pose au sol ; manivelle sur la poulie du comptoir si {@code turn}.
     */
    private static CounterTile relayNetwork(GameTestHelper h, boolean turn, CrateHatchTile[] crate) {
        crate[0] = crate(h, new BlockPos(2, 1, 2));
        BlockPos w1 = warehousePulley(h, crate[0].getBlockPos().above());
        BlockPos wr = warehousePulley(h, h.absolutePos(new BlockPos(8, 4, 3)));
        BlockPos small = basePulley(h, h.absolutePos(new BlockPos(13, 4, 3)), PiKinetics.SMALL_PULLEY.getBlock(), Direction.Axis.Y);
        BlockPos large = basePulley(h, h.absolutePos(new BlockPos(13, 4, 10)), PiKinetics.LARGE_PULLEY.getBlock(), Direction.Axis.Y);
        BlockPos c = counter(h, h.absolutePos(new BlockPos(17, 1, 10)));
        BlockPos w2 = warehousePulley(h, h.absolutePos(new BlockPos(17, 4, 10)));
        Gt.equal(w1.getY(), wr.getY(), "ligne a la hauteur des caisses");
        rope(h, w1, wr);
        rope(h, wr, small);
        rope(h, small, large);
        rope(h, large, w2);
        PulleyTile s = (PulleyTile) h.getLevel().getBlockEntity(small), l = (PulleyTile) h.getLevel().getBlockEntity(large);
        Gt.equal(2, s.ropeCount(), "petite poulie : deux cordes (relais)");
        Gt.equal(2, l.ropeCount(), "grande poulie : deux cordes (relais)");
        CounterTile counter = (CounterTile) h.getLevel().getBlockEntity(c);
        Gt.check(counter.station() != null && counter.station().getBlockPos().equals(w2), "le comptoir au sol n'a pas sa station au-dessus");
        WarehouseLines.Station st = WarehouseLines.station(h.getLevel(), w2);
        Gt.check(st != null && st.counter() && st.gap() == 2, "descente de 2 blocs attendue");
        if (turn) {
            //Deux manivelles (cinq roues, six cordes a entrainer)
            HandCrankBlock.Tile a = crank(h, w2.above(), Direction.DOWN), b = crank(h, wr.above(), Direction.DOWN);
            h.onEachTick(() -> {
                a.crank(10);
                b.crank(10);
            });
        }
        return counter;
    }

    // ================================================================== relais en poulies de base, comptoir au sol : colis livre
    @GameTest(template = Gt.TALL, batch = "pioneers_p104b_relay", timeoutTicks = 2000)
    public static void p104b_relais_et_comptoir_au_sol(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = relayNetwork(h, true, crate);
        Gt.equal(5, counter.counter().graph().links().size(), "reseau : 3 petites poulies (stations) et 2 poulies de relais");
        Gt.equal(1, counter.counter().graph().hatches(h.getLevel()).size(), "une caisse reliee");
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 200, false);
        Gt.check(counter.counter().request(new ItemStack(Items.IRON_INGOT), 100), "demande refusee");
        boolean[] departed = {false};
        h.onEachTick(() -> departed[0] |= !counter.counter().parcels().isEmpty());
        h.succeedWhen(() -> {
            Gt.check(departed[0], "aucun colis n'est parti");
            Gt.equal(100, out(counter, Items.IRON_INGOT), "lingots en sortie du comptoir au sol");
            Gt.equal(100, crate[0].storage().count(new ItemStack(Items.IRON_INGOT)), "reste dans la caisse");
        });
    }

    // ================================================================== sans rotation : rien ne bouge (relais compris)
    @GameTest(template = Gt.TALL, batch = "pioneers_p104b_still", timeoutTicks = 140)
    public static void p104b_sans_rotation(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = relayNetwork(h, false, crate);
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 64, false);
        counter.counter().request(new ItemStack(Items.IRON_INGOT), 64);
        h.runAtTickTime(120, () -> {
            Gt.check(counter.counter().parcels().isEmpty(), "un colis est parti sans rotation");
            Gt.equal(64, crate[0].storage().count(new ItemStack(Items.IRON_INGOT)), "la caisse a perdu des objets sans rotation");
            Gt.equal(0, out(counter, Items.IRON_INGOT), "rien en sortie");
            h.succeed();
        });
    }

    // ================================================================== poulie d'entrepot <-> poulie de base : rotation dans les deux sens
    @GameTest(template = Gt.TALL, batch = "pioneers_p104b_ratio", timeoutTicks = 200)
    public static void p104b_rotation_mixte(GameTestHelper h) {
        //A : la manivelle tourne la poulie d'entrepot, la poulie de base (moyenne) suit
        BlockPos wa = warehousePulley(h, h.absolutePos(new BlockPos(3, 3, 3)));
        BlockPos ba = basePulley(h, h.absolutePos(new BlockPos(9, 3, 3)), PiKinetics.PULLEY.getBlock(), Direction.Axis.Y);
        rope(h, wa, ba);
        HandCrankBlock.Tile ca = crank(h, wa.above(), Direction.DOWN);
        //B : la manivelle tourne la poulie de base (grande), la poulie d'entrepot suit
        BlockPos wb = warehousePulley(h, h.absolutePos(new BlockPos(3, 3, 14)));
        BlockPos bb = basePulley(h, h.absolutePos(new BlockPos(9, 3, 14)), PiKinetics.LARGE_PULLEY.getBlock(), Direction.Axis.Y);
        rope(h, bb, wb);
        HandCrankBlock.Tile cb = crank(h, bb.above(), Direction.DOWN);
        h.onEachTick(() -> {
            ca.crank(10);
            cb.crank(10);
        });
        //Une poulie de base d'axe horizontal ne porte pas de colis : refus clair ; elle garde une seule corde (regle d'avant)
        BlockPos bx = basePulley(h, h.absolutePos(new BlockPos(9, 3, 24)), PiKinetics.PULLEY.getBlock(), Direction.Axis.X);
        BlockPos wx = warehousePulley(h, h.absolutePos(new BlockPos(3, 3, 24)));
        Gt.equal("kinetics.pioneers.rope.axis", key(WarehouseRopes.check(h.getLevel(), wx, bx)), "refus de l'axe horizontal");
        Gt.equal(PulleyTile.MAX_ROPES, ((PulleyTile) h.getLevel().getBlockEntity(bx)).capacity(), "axe horizontal : 4 cordes aussi (lot 27)");
        h.runAtTickTime(160, () -> {
            PulleyTile w1 = (PulleyTile) h.getLevel().getBlockEntity(wa), w2 = (PulleyTile) h.getLevel().getBlockEntity(wb);
            PulleyTile p1 = (PulleyTile) h.getLevel().getBlockEntity(ba), p2 = (PulleyTile) h.getLevel().getBlockEntity(bb);
            Gt.check(Math.abs(w1.getSpeed()) > 1 && Math.abs(p1.getSpeed()) > 1, "A : rien ne tourne");
            Gt.check(Math.abs(w2.getSpeed()) > 1 && Math.abs(p2.getSpeed()) > 1, "B : rien ne tourne");
            //Meme vitesse lineaire de corde : w_base = w_petite x 4 / (4 x taille)
            double ra = p1.getSpeed() / w1.getSpeed(), rb = p2.getSpeed() / w2.getSpeed();
            Gt.check(Math.abs(ra - 4 / 8.0) < 0.02, "A : rapport " + ra);
            Gt.check(Math.abs(rb - 4 / 12.0) < 0.02, "B : rapport " + rb);
            Gt.check(Math.abs(w1.ropeSpeed() - p1.ropeSpeed()) < 1e-3, "A : vitesses de corde differentes");
            h.succeed();
        });
    }

    // ================================================================== poulie de base au-dessus d'un comptoir : pas de station, message
    @GameTest(template = Gt.TALL, batch = "pioneers_p104b_refusals", timeoutTicks = 40)
    public static void p104b_refus_clairs(GameTestHelper h) {
        //Poulie de base 3 blocs au-dessus d'un comptoir : elle ne fait que relayer
        BlockPos c1 = counter(h, h.absolutePos(new BlockPos(3, 1, 3)));
        BlockPos b1 = basePulley(h, h.absolutePos(new BlockPos(3, 4, 3)), PiKinetics.PULLEY.getBlock(), Direction.Axis.Y);
        Gt.check(((CounterTile) h.getLevel().getBlockEntity(c1)).station() == null, "une poulie de base dessert le comptoir");
        Gt.equal("warehouse.pioneers.station.size_counter", key(WarehouseLines.stationProblem(h.getLevel(), b1)), "message de la poulie trop grande");
        //Colonne bouchee : un bloc entre la poulie d'entrepot et le comptoir
        BlockPos c2 = counter(h, h.absolutePos(new BlockPos(8, 1, 3)));
        BlockPos w2 = warehousePulley(h, h.absolutePos(new BlockPos(8, 4, 3)));
        h.getLevel().setBlock(c2.above(), Blocks.STONE.defaultBlockState(), 3);
        Gt.check(((CounterTile) h.getLevel().getBlockEntity(c2)).station() == null, "station malgre le bloc");
        Gt.equal("warehouse.pioneers.station.blocked_counter", key(WarehouseLines.stationProblem(h.getLevel(), w2)), "message de la colonne bouchee");
        //Trop haut : 10 blocs de vide (8 au plus)
        BlockPos c3 = counter(h, h.absolutePos(new BlockPos(13, 1, 3)));
        BlockPos w3 = warehousePulley(h, h.absolutePos(new BlockPos(13, 12, 3)));
        Gt.check(((CounterTile) h.getLevel().getBlockEntity(c3)).station() == null, "station a 10 blocs");
        Gt.equal("warehouse.pioneers.station.far_counter", key(WarehouseLines.stationProblem(h.getLevel(), w3)), "message trop haut");
        //A 8 blocs : station
        BlockPos c4 = counter(h, h.absolutePos(new BlockPos(18, 1, 3)));
        warehousePulley(h, h.absolutePos(new BlockPos(18, 10, 3)));
        CounterTile t4 = (CounterTile) h.getLevel().getBlockEntity(c4);
        Gt.check(t4.station() != null && WarehouseLines.gap(h.getLevel(), t4.station().getBlockPos()) == WarehouseLines.MAX_DROP, "station a 8 blocs");
        //1.0.4-beta (lot 27) : une poulie pleine (4 cordes) refuse une cinquieme
        BlockPos p = basePulley(h, h.absolutePos(new BlockPos(3, 4, 12)), PiKinetics.PULLEY.getBlock(), Direction.Axis.Y);
        BlockPos q1 = warehousePulley(h, h.absolutePos(new BlockPos(3, 4, 18))), q2 = warehousePulley(h, h.absolutePos(new BlockPos(9, 4, 12)));
        BlockPos q3 = warehousePulley(h, h.absolutePos(new BlockPos(3, 4, 6))), q4 = warehousePulley(h, h.absolutePos(new BlockPos(9, 4, 18)));
        BlockPos q5 = warehousePulley(h, h.absolutePos(new BlockPos(9, 4, 6)));
        rope(h, p, q1);
        rope(h, p, q2);
        rope(h, p, q3);
        rope(h, p, q4);
        Gt.equal("kinetics.pioneers.rope.busy", key(WarehouseRopes.check(h.getLevel(), q5, p)), "cinquieme corde refusee");
        h.succeed();
    }

    // ================================================================== ancienne facon (poulie sur le comptoir) et caisse sous une ligne plus haute
    @GameTest(template = Gt.TALL, batch = "pioneers_p104b_old_way", timeoutTicks = 1400)
    public static void p104b_ancienne_facon_et_caisse_basse(GameTestHelper h) {
        //Caisse posee au sol ; la ligne passe 2 blocs au-dessus de sa trappe (descente vers la caisse)
        CrateHatchTile hatch = crate(h, new BlockPos(2, 1, 2));
        BlockPos w1 = warehousePulley(h, hatch.getBlockPos().above(3));
        Gt.equal(hatch.getBlockPos(), WarehouseLines.station(h.getLevel(), w1).block(), "la poulie haute dessert la trappe");
        Gt.check(hatch.station() != null && hatch.station().getBlockPos().equals(w1), "la trappe ne voit pas sa poulie");
        //Comptoir monte comme au lot 1 : la poulie posee dessus, a la hauteur de la ligne
        BlockPos c = counter(h, h.absolutePos(new BlockPos(9, 5, 3)));
        h.getLevel().setBlock(c.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockPos w2 = warehousePulley(h, c.above());
        Gt.equal(w1.getY(), w2.getY(), "meme hauteur");
        rope(h, w1, w2);
        CounterTile counter = (CounterTile) h.getLevel().getBlockEntity(c);
        Gt.equal(0, WarehouseLines.gap(h.getLevel(), w2), "poulie posee sur le comptoir");
        HandCrankBlock.Tile tile = crank(h, w2.above(), Direction.DOWN);
        h.onEachTick(() -> tile.crank(10));
        hatch.storage().insert(new ItemStack(Items.COPPER_INGOT), 80, false);
        Gt.check(counter.counter().request(new ItemStack(Items.COPPER_INGOT), 40), "demande refusee");
        h.succeedWhen(() -> {
            Gt.equal(40, out(counter, Items.COPPER_INGOT), "cuivre en sortie");
            Gt.equal(40, hatch.storage().count(new ItemStack(Items.COPPER_INGOT)), "reste dans la caisse");
        });
    }
}
