package fr.pioneers.gametest;

import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.parts.HandCrankBlock;
import fr.pioneers.kinetics.rope.LinkType;
import fr.pioneers.kinetics.rope.PulleyTile;
import fr.pioneers.registries.PiKinetics;
import fr.pioneers.warehouse.CounterBlock;
import fr.pioneers.warehouse.CounterTile;
import fr.pioneers.warehouse.CounterUnit;
import fr.pioneers.warehouse.CrateHatchTile;
import fr.pioneers.warehouse.CrateLayout;
import fr.pioneers.warehouse.PiWarehouse;
import fr.pioneers.warehouse.WarehouseLines;
import fr.pioneers.warehouse.WarehouseRopes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 34 (GameTests ; Guillaume : « Mon comptoir ne se connecte pas au reseau ») : le MONTAGE DE GUILLAUME reproduit
 * (lu dans sa region : comptoir au sol, petite poulie 2 blocs au-dessus, relais = petite poulie sur un arbre debout, petite poulie posee sur
 * la trappe d'une caisse 3 x 3 x 3, cordes a la meme hauteur, rotation) : le comptoir voit la caisse et ses objets, une commande arrive (avant
 * le lot 34 la corde vers la caisse etait refusee : un colis aurait touche le bord du dessus de la caisse). Puis une erreur de pose courante
 * par test : le diagnostic du comptoir ({@link CounterUnit#diagnose}) dit exactement ce qui manque ; un bloc genant retire, le comptoir se
 * relie seul (reseau incomplet recalcule).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot34Tests {

    private PatchP104Lot34Tests() {
    }

    /** Caisse 3 x 3 x 3 selon Z (coin relatif {@code rel}) ; {@code skip} : une piece non posee (caisse incomplete). */
    private static CrateHatchTile crate(GameTestHelper h, BlockPos rel, boolean skip) {
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
                    } else if (skip && u == 1 && v == 0 && w == 1) {
                        continue;
                    }
                    h.getLevel().setBlock(p, CrateLayout.block(k).defaultBlockState(), 3);
                }
            }
        }
        CrateHatchTile t = (CrateHatchTile) h.getLevel().getBlockEntity(hatch);
        Gt.check(t.house().checkNow() != skip, skip ? "caisse incomplete formee" : "caisse non formee : " + t.house().error());
        return t;
    }

    private static BlockPos pulley(GameTestHelper h, BlockPos abs, Block block) {
        h.getLevel().setBlock(abs, block.defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.Y), 3);
        return abs;
    }

    private static BlockPos small(GameTestHelper h, BlockPos abs) {
        return pulley(h, abs, PiKinetics.SMALL_PULLEY.getBlock());
    }

    private static CounterTile counter(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, PiWarehouse.COUNTER.getBlock().defaultBlockState().setValue(CounterBlock.FACING, Direction.WEST), 3);
        return (CounterTile) h.getLevel().getBlockEntity(abs);
    }

    private static void rope(GameTestHelper h, BlockPos a, BlockPos b) {
        Gt.check(WarehouseRopes.link(h.getLevel(), a, b, LinkType.FIBER_ROPE), "corde " + a + " -> " + b + " refusee : " + WarehouseRopes.check(h.getLevel(), a, b));
    }

    private static void crank(GameTestHelper h, BlockPos abs) {
        h.getLevel().setBlock(abs, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, Direction.DOWN), 3);
        HandCrankBlock.Tile t = (HandCrankBlock.Tile) h.getLevel().getBlockEntity(abs);
        h.onEachTick(() -> t.crank(10));
    }

    private static void diag(CounterTile counter, int code, String what) {
        CounterUnit.Diagnosis d = counter.counter().diagnosis();
        Gt.equal(CounterUnit.DIAG_KEYS[code], CounterUnit.DIAG_KEYS[d.code()], what);
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

    /** Le montage de Guillaume (memes ecarts) : caisse en (2, 1, 2), trappe (3, 3, 3) ; relais sur arbre en (8, 4, 3) ; comptoir en (13, 1, 3). */
    private static CounterTile guillaume(GameTestHelper h, CrateHatchTile[] crate, boolean turn) {
        crate[0] = crate(h, new BlockPos(2, 1, 2), false);
        BlockPos onHatch = small(h, crate[0].getBlockPos().above());
        for (int y = 1; y <= 3; y++) {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(8, y, 3)), PiKinetics.WOODEN_SHAFT.getBlock().defaultBlockState()
                  .setValue(AxisKineticBlock.AXIS, Direction.Axis.Y), 3);
        }
        BlockPos relay = small(h, h.absolutePos(new BlockPos(8, 4, 3)));
        CounterTile counter = counter(h, new BlockPos(13, 1, 3));
        BlockPos station = small(h, h.absolutePos(new BlockPos(13, 4, 3)));
        Gt.equal(onHatch.getY(), relay.getY(), "ligne a une seule hauteur");
        rope(h, station, relay);
        rope(h, relay, onHatch);
        Gt.check(counter.station() != null && counter.station().getBlockPos().equals(station), "le comptoir ne voit pas sa poulie");
        Gt.equal(2, WarehouseLines.gap(h.getLevel(), station), "descente de 2 blocs (comme chez Guillaume)");
        if (turn) {
            crank(h, station.above());
        }
        return counter;
    }

    // ================================================================== montage correct : la caisse vue, une commande arrive
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_34_chain", timeoutTicks = 2000)
    public static void p104_34_montage_de_guillaume(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = guillaume(h, crate, true);
        crate[0].storage().insert(new ItemStack(Items.HOPPER), 320, false);
        Gt.equal(3, counter.counter().graph().links().size(), "reseau : station du comptoir, relais, station de la caisse");
        Gt.equal(1, counter.counter().graph().hatches(h.getLevel()).size(), "la caisse est reliee (avant le lot 34 : 0)");
        Gt.check(counter.counter().graph().skipped().isEmpty(), "une corde ecartee : " + counter.counter().graph().skipped());
        Gt.equal(320L, counter.counter().contents().values().stream().mapToLong(Long::longValue).sum(), "le comptoir voit les 320 entonnoirs");
        Gt.check(counter.counter().request(new ItemStack(Items.HOPPER), 64), "demande refusee");
        h.succeedWhen(() -> {
            diag(counter, CounterUnit.D_OK, "diagnostic d'un montage correct");
            Gt.equal(64, out(counter, Items.HOPPER), "entonnoirs en sortie du comptoir");
            Gt.equal(256, crate[0].storage().count(new ItemStack(Items.HOPPER)), "reste dans la caisse");
        });
    }

    // ================================================================== sans rotation : relie, mais « corde a l'arret »
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_34_still", timeoutTicks = 40)
    public static void p104_34_sans_rotation(GameTestHelper h) {
        CounterTile counter = guillaume(h, new CrateHatchTile[1], false);
        diag(counter, CounterUnit.D_STILL, "corde a l'arret");
        h.succeed();
    }

    // ================================================================== erreurs de pose : le bon message
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_34_errors", timeoutTicks = 40)
    public static void p104_34_erreurs_au_comptoir(GameTestHelper h) {
        //Rien au-dessus du comptoir (la capture de Guillaume telle qu'il la decrivait)
        diag(counter(h, new BlockPos(2, 1, 2)), CounterUnit.D_NO_PULLEY, "aucune petite poulie au-dessus");
        //Poulie normale au-dessus : elle ne fait que relayer
        CounterTile c2 = counter(h, new BlockPos(6, 1, 2));
        pulley(h, h.absolutePos(new BlockPos(6, 4, 2)), PiKinetics.PULLEY.getBlock());
        diag(c2, CounterUnit.D_BIG_PULLEY, "poulie trop grande");
        //Colonne bouchee
        CounterTile c3 = counter(h, new BlockPos(10, 1, 2));
        small(h, h.absolutePos(new BlockPos(10, 4, 2)));
        h.getLevel().setBlock(h.absolutePos(new BlockPos(10, 2, 2)), Blocks.STONE.defaultBlockState(), 3);
        diag(c3, CounterUnit.D_COLUMN, "descente bouchee");
        //Trop haute : 10 blocs de vide
        CounterTile c4 = counter(h, new BlockPos(14, 1, 2));
        small(h, h.absolutePos(new BlockPos(14, 12, 2)));
        diag(c4, CounterUnit.D_TOO_HIGH, "poulie trop haute");
        //Petite poulie sans corde
        CounterTile c5 = counter(h, new BlockPos(18, 1, 2));
        small(h, h.absolutePos(new BlockPos(18, 4, 2)));
        diag(c5, CounterUnit.D_NO_ROPE, "aucune corde");
        //Une corde, mais aucune caisse au bout
        CounterTile c6 = counter(h, new BlockPos(22, 1, 2));
        BlockPos s6 = small(h, h.absolutePos(new BlockPos(22, 4, 2)));
        rope(h, s6, small(h, h.absolutePos(new BlockPos(27, 4, 2))));
        diag(c6, CounterUnit.D_NO_CRATE, "aucune caisse");
        h.succeed();
    }

    // ================================================================== caisse non formee
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_34_unformed", timeoutTicks = 40)
    public static void p104_34_caisse_non_formee(GameTestHelper h) {
        CrateHatchTile hatch = crate(h, new BlockPos(2, 1, 2), true);
        BlockPos onHatch = small(h, hatch.getBlockPos().above());
        CounterTile counter = counter(h, new BlockPos(9, 1, 3));
        BlockPos station = small(h, h.absolutePos(new BlockPos(9, 4, 3)));
        rope(h, station, onHatch);
        diag(counter, CounterUnit.D_UNFORMED, "caisse non formee");
        Gt.equal(hatch.getBlockPos().asLong(), counter.counter().diagnosis().a(), "la trappe de la caisse incomplete est donnee");
        h.succeed();
    }

    // ================================================================== poulie a cote de la trappe (au-dessus d'un coin de la caisse)
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_34_off_hatch", timeoutTicks = 40)
    public static void p104_34_poulie_hors_trappe(GameTestHelper h) {
        CrateHatchTile hatch = crate(h, new BlockPos(2, 1, 2), false);
        BlockPos corner = small(h, hatch.getBlockPos().above().offset(1, 0, 1));
        CounterTile counter = counter(h, new BlockPos(10, 1, 4));
        BlockPos station = small(h, h.absolutePos(new BlockPos(10, 4, 4)));
        rope(h, station, corner);
        diag(counter, CounterUnit.D_OFF_HATCH, "poulie au-dessus d'un coin de la caisse");
        Gt.equal(corner.asLong(), counter.counter().diagnosis().a(), "la poulie mal placee est donnee");
        h.succeed();
    }

    // ================================================================== corde genee, puis le bloc retire : le comptoir se relie seul
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_34_rope", timeoutTicks = 500)
    public static void p104_34_corde_genee_puis_degagee(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = guillaume(h, crate, true);
        BlockPos stone = h.absolutePos(new BlockPos(11, 4, 3));
        h.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        //Le bloc est pose sans toucher aux cordes : le reseau se relit de lui-meme (CounterUnit.REFRESH, puis RETRY tant qu'il est incomplet)
        h.runAtTickTime(CounterUnit.REFRESH + 30, () -> {
            diag(counter, CounterUnit.D_ROPE_BLOCKED, "corde genee");
            Gt.equal(stone.asLong(), counter.counter().diagnosis().c(), "le bloc genant est donne");
            h.getLevel().setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
        });
        h.runAtTickTime(CounterUnit.REFRESH + CounterUnit.RETRY + 80, () -> {
            Gt.equal(1, counter.counter().graph().hatches(h.getLevel()).size(), "la caisse n'est pas revenue apres le retrait du bloc");
            diag(counter, CounterUnit.D_OK, "relie apres le retrait du bloc");
            h.succeed();
        });
    }

    /** Lot 34 : la roue d'une station passe au-dessus d'une caisse 3 x 3 : la corde la plus basse d'un relais a deux cordes y suffit. */
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_34_lift", timeoutTicks = 20)
    public static void p104_34_hauteur_des_roues(GameTestHelper h) {
        Gt.check(PulleyTile.STATION_LIFT >= 4F, "roue de station trop basse");
        CounterTile counter = guillaume(h, new CrateHatchTile[1], false);
        PulleyTile relay = (PulleyTile) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(8, 4, 3)));
        Gt.check(relay.ropeYPx() - WarehouseLines.HANG_BOTTOM > 2F, "colis trop bas sous le relais : " + relay.ropeYPx());
        Gt.check(counter.counter().graph().skipped().isEmpty(), "corde ecartee");
        h.succeed();
    }
}
