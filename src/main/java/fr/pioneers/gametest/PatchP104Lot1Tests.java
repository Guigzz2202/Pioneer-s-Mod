package fr.pioneers.gametest;

import fr.pioneers.tech.kinetics.parts.HandCrankBlock;
import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import fr.pioneers.tech.kinetics.PiKinetics;
import fr.pioneers.logistics.warehouse.CounterBlock;
import fr.pioneers.logistics.warehouse.CounterTile;
import fr.pioneers.logistics.warehouse.CrateBlock;
import fr.pioneers.logistics.warehouse.CrateHatchBlock;
import fr.pioneers.logistics.warehouse.CrateHatchTile;
import fr.pioneers.logistics.warehouse.CrateLayout;
import fr.pioneers.logistics.warehouse.CrateStorage;
import fr.pioneers.logistics.warehouse.PiWarehouse;
import fr.pioneers.logistics.warehouse.WarehouseRopes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Patch 1.0.4-beta, lot 1 (GameTests) : l'entrepot. Caisses formees aux 3 longueurs selon Z et X (plan {@link CrateLayout}, la plus longue
 * complete), refus d'une longueur non admise (4), peinture des pieces ; insertion et extraction par un port ; aucun acces direct pour le
 * joueur ; demande au comptoir : un colis part de la caisse, voyage le long des cordes qui tournent (manivelle) et depose les objets en
 * sortie ; rien ne bouge sans rotation ; sauvegarde et relecture du contenu (piles de plus de 64) ; casser une piece ou la trappe : ni perte ni
 * duplication.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot1Tests {

    private PatchP104Lot1Tests() {
    }

    /** Construit une caisse de {@code n} blocs selon {@code axis} (origine relative {@code rel}, trappe au centre) ; renvoie la trappe. */
    private static CrateHatchTile build(GameTestHelper h, BlockPos rel, Direction.Axis axis, int n) {
        BlockPos origin = h.absolutePos(rel);
        CrateLayout.Length length = n == 3 ? CrateLayout.Length.SHORT : n == 5 ? CrateLayout.Length.MEDIUM : CrateLayout.Length.LONG;
        BlockPos hatch = null;
        for (int w = 0; w < n; w++) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    CrateLayout.Kind k = CrateLayout.kind(u, v, w, n);
                    BlockPos p = CrateLayout.at(origin, axis, u, v, w);
                    if (u == 1 && v == 2 && w == (n - 1) / 2) {
                        k = CrateLayout.Kind.HATCH;
                        hatch = p;
                    }
                    h.getLevel().setBlock(p, CrateLayout.block(k).defaultBlockState(), 3);
                }
            }
        }
        Gt.check(hatch != null && h.getLevel().getBlockEntity(hatch) instanceof CrateHatchTile, "pas de trappe (" + length.key() + ")");
        return (CrateHatchTile) h.getLevel().getBlockEntity(hatch);
    }

    // ================================================================== formation aux 3 longueurs, refus d'une longueur non admise
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_crate_form", timeoutTicks = 40)
    public static void p104_caisse_trois_tailles(GameTestHelper h) {
        int[] lengths = {3, 5, 7};
        for (int k = 0; k < 3; k++) {
            CrateHatchTile z = build(h, new BlockPos(2 + 5 * k, 2, 2), Direction.Axis.Z, lengths[k]);
            Gt.check(z.house().checkNow(), "caisse " + lengths[k] + " (Z) non formee : " + z.house().error());
            Gt.equal(lengths[k], z.length().blocks, "longueur formee (Z)");
            Gt.equal(CrateLayout.TYPES, z.storage().maxTypes(), "types de la caisse");
            Gt.equal(lengths[k], z.house().size()[2], "profondeur de la structure");
            //Pieces peintes : un coin du bas est un cadre a sa place (part != 0, axe Z)
            var corner = h.getLevel().getBlockState(z.found().origin());
            Gt.check(corner.getValue(CrateBlock.PART) == CrateLayout.partIndex(0, 0, CrateLayout.W_START), "coin peint");
            Gt.equal(Direction.Axis.Z, corner.getValue(CrateBlock.AXIS), "axe peint");
        }
        //Selon X (origine a l'extremite +X : w vers -X)
        CrateHatchTile x = build(h, new BlockPos(24, 2, 20), Direction.Axis.X, 5);
        Gt.check(x.house().checkNow(), "caisse 5 (X) non formee : " + x.house().error());
        Gt.equal(Direction.Axis.X, x.found().axis(), "axe X");
        //Longueur 4 : refusee (3, 5 ou 7 seulement) ; erreur commune de la caisse
        BlockPos o4 = h.absolutePos(new BlockPos(2, 2, 16));
        BlockPos hatch4 = null;
        for (int w = 0; w < 4; w++) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    int b = (u != 1 ? 1 : 0) + (v != 1 ? 1 : 0) + (w == 0 || w == 3 ? 1 : 0);
                    BlockPos p = CrateLayout.at(o4, Direction.Axis.Z, u, v, w);
                    if (b == 0) {
                        continue;
                    }
                    boolean hatchCell = u == 1 && v == 2 && w == 1;
                    h.getLevel().setBlock(p, (hatchCell ? PiWarehouse.CRATE_HATCH : b >= 2 ? PiWarehouse.CRATE_FRAME : PiWarehouse.CRATE_PANEL).getBlock()
                          .defaultBlockState(), 3);
                    if (hatchCell) {
                        hatch4 = p;
                    }
                }
            }
        }
        CrateHatchTile four = (CrateHatchTile) h.getLevel().getBlockEntity(hatch4);
        Gt.check(!four.house().checkNow(), "une caisse de 4 de long s'est formee");
        Gt.equal(CrateHatchTile.ERROR, four.house().error(), "erreur commune de la caisse");
        //Une piece retiree : la caisse se defait et se depeint
        CrateHatchTile small = (CrateHatchTile) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 4, 3)));
        h.getLevel().setBlock(small.found().origin(), Blocks.AIR.defaultBlockState(), 3);
        Gt.check(!small.house().checkNow(), "caisse formee sans un coin");
        Gt.equal(0L, (long) h.getLevel().getBlockState(h.absolutePos(new BlockPos(3, 4, 3))).getValue(CrateBlock.PART), "trappe depeinte");
        h.succeed();
    }

    // ================================================================== port : insertion et extraction ; joueur : aucun acces
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_crate_port", timeoutTicks = 40)
    public static void p104_port_et_acces(GameTestHelper h) {
        BlockPos origin = new BlockPos(4, 2, 4);
        CrateHatchTile hatch = build(h, origin, Direction.Axis.Z, 3);
        //Un port a la place du panneau du flanc ouest (u 0, v 1, w 1)
        BlockPos port = CrateLayout.at(h.absolutePos(origin), Direction.Axis.Z, 0, 1, 1);
        h.getLevel().setBlock(port, PiWarehouse.CRATE_PORT.getBlock().defaultBlockState(), 3);
        Gt.check(hatch.house().checkNow(), "caisse avec port non formee : " + hatch.house().error());
        Gt.equal(1, hatch.found().ports().size(), "un port");
        IItemHandler items = h.getLevel().getBlockEntity(port).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).resolve().orElse(null);
        Gt.check(items != null, "le port n'expose pas la caisse");
        //Insertion par la case libre, bien au-dela d'une pile
        for (int i = 0; i < 10; i++) {
            ItemStack rest = items.insertItem(items.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 64), false);
            Gt.check(rest.isEmpty(), "le port refuse des lingots");
        }
        Gt.equal(640, hatch.storage().count(new ItemStack(Items.IRON_INGOT)), "lingots ranges");
        Gt.equal(2, items.getSlots(), "une case par type + la case libre");
        ItemStack out = items.extractItem(0, 64, false);
        Gt.equal(64, out.getCount(), "extraction d'une pile");
        Gt.equal(576, hatch.storage().total(), "reste");
        //Contenance d'une case de type : 32 piles pour une caisse courte ; 1.0.5-beta (lot 22) : le type deborde aussi sur les 255 cases libres
        long room = hatch.storage().room(new ItemStack(Items.IRON_INGOT));
        Gt.equal(32 * 64 - 576 + 255L * 32 * 64, room, "place restante du type (sa case + les cases libres)");
        //Joueur : la trappe et les panneaux n'ont ni capacite d'objets ni interface
        Gt.check(hatch.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().isEmpty(), "la trappe expose son contenu");
        Gt.check(!(hatch instanceof net.minecraft.world.MenuProvider), "la trappe a une interface");
        BlockPos panel = CrateLayout.at(h.absolutePos(origin), Direction.Axis.Z, 2, 1, 1);
        Gt.check(h.getLevel().getBlockEntity(panel) == null, "un panneau a une tile");
        //Caisse defaite : le port se ferme, le contenu reste
        h.getLevel().setBlock(CrateLayout.at(h.absolutePos(origin), Direction.Axis.Z, 2, 2, 2), Blocks.AIR.defaultBlockState(), 3);
        hatch.house().checkNow();
        Gt.check(h.getLevel().getBlockEntity(port).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).resolve().isEmpty(),
              "port ouvert sur une caisse defaite");
        Gt.equal(576, hatch.storage().total(), "contenu garde caisse defaite");
        h.succeed();
    }

    // ================================================================== sauvegarde ; casse de la trappe : ni perte ni duplication
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_crate_save", timeoutTicks = 40)
    public static void p104_sauvegarde_et_casse(GameTestHelper h) {
        CrateHatchTile hatch = build(h, new BlockPos(4, 2, 4), Direction.Axis.Z, 5);
        Gt.check(hatch.house().checkNow(), "caisse non formee");
        CrateStorage s = hatch.storage();
        s.insert(new ItemStack(Items.COBBLESTONE), 3000, false);
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.setHoverName(net.minecraft.network.chat.Component.literal("Excalibur"));
        s.insert(named, 3, false);
        s.insert(new ItemStack(Items.DIAMOND_SWORD), 2, false);
        Gt.equal(3, s.types(), "types (NBT distinct)");
        //Aller-retour de sauvegarde
        CompoundTag tag = hatch.saveWithoutMetadata();
        CrateStorage back = new CrateStorage();
        back.load(tag.getCompound("storage"));
        Gt.equal(3000, back.count(new ItemStack(Items.COBBLESTONE)), "piles > 64 relues");
        Gt.equal(3, back.count(named), "objets nommes relus");
        Gt.equal(2, back.count(new ItemStack(Items.DIAMOND_SWORD)), "epees ordinaires relues");
        //La trappe cassee emporte tout ; reposee, tout revient ; rien n'est double
        ItemStack carried = CrateHatchBlock.withContents(s);
        CompoundTag bet = net.minecraft.world.item.BlockItem.getBlockEntityData(carried);
        Gt.check(bet != null && bet.contains("storage"), "objet de trappe sans contenu");
        BlockPos at = hatch.getBlockPos();
        h.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        Gt.check(!(h.getLevel().getBlockEntity(at) instanceof CrateHatchTile), "trappe toujours la");
        h.getLevel().setBlock(at, PiWarehouse.CRATE_HATCH.getBlock().defaultBlockState(), 3);
        CrateHatchTile again = (CrateHatchTile) h.getLevel().getBlockEntity(at);
        Gt.equal(0L, again.storage().total(), "trappe neuve vide (pas de duplication)");
        again.load(bet);
        Gt.check(again.house().checkNow(), "caisse reformee");
        Gt.equal(3005, again.storage().total(), "contenu revenu");
        //Table de butin : la trappe garde son contenu (copy_nbt storage)
        var loot = h.getLevel().getServer().getLootData().getLootTable(PiWarehouse.CRATE_HATCH.getBlock().getLootTable());
        Gt.check(loot != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "table de butin de la trappe");
        h.succeed();
    }

    /** Caisse courte + poulie sur la trappe, comptoir + poulie, une corde ; manivelle sur la poulie du comptoir si {@code turn}. */
    private static CounterTile network(GameTestHelper h, boolean turn, CrateHatchTile[] crate) {
        CrateHatchTile hatch = build(h, new BlockPos(2, 1, 2), Direction.Axis.Z, 3);
        Gt.check(hatch.house().checkNow(), "caisse non formee");
        crate[0] = hatch;
        BlockPos crateStation = hatch.getBlockPos().above();
        //1.0.4-beta (lot 27) : plus de poulie d'entrepot : la petite poulie (axe vertical) est la station
        h.getLevel().setBlock(crateStation, PiKinetics.SMALL_PULLEY.getBlock().defaultBlockState(), 3);
        BlockPos counter = h.absolutePos(new BlockPos(9, 3, 3));
        h.getLevel().setBlock(counter, PiWarehouse.COUNTER.getBlock().defaultBlockState().setValue(CounterBlock.FACING, Direction.WEST), 3);
        BlockPos counterStation = counter.above();
        h.getLevel().setBlock(counterStation, PiKinetics.SMALL_PULLEY.getBlock().defaultBlockState(), 3);
        Gt.check(WarehouseRopes.link(h.getLevel(), crateStation, counterStation, LinkType.FIBER_ROPE), "corde refusee : "
              + WarehouseRopes.check(h.getLevel(), crateStation, counterStation));
        if (turn) {
            BlockPos crank = counterStation.above();
            h.getLevel().setBlock(crank, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, Direction.DOWN), 3);
            HandCrankBlock.Tile tile = (HandCrankBlock.Tile) h.getLevel().getBlockEntity(crank);
            h.onEachTick(() -> tile.crank(10));
        }
        return (CounterTile) h.getLevel().getBlockEntity(counter);
    }

    // ================================================================== demande au comptoir : colis le long des cordes, objets en sortie
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_counter", timeoutTicks = 900)
    public static void p104_comptoir_colis(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = network(h, true, crate);
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 200, false);
        Gt.check(counter.counter().request(new ItemStack(Items.IRON_INGOT), 100), "demande refusee");
        boolean[] departed = {false};
        h.onEachTick(() -> {
            if (!counter.counter().parcels().isEmpty()) {
                departed[0] = true;
            }
        });
        h.succeedWhen(() -> {
            Gt.check(departed[0], "aucun colis n'est parti");
            int got = 0;
            for (var slot : counter.outputs()) {
                if (slot.getStack().is(Items.IRON_INGOT)) {
                    got += slot.getCount();
                }
            }
            Gt.equal(100, got, "lingots en sortie");
            Gt.equal(100, crate[0].storage().count(new ItemStack(Items.IRON_INGOT)), "reste dans la caisse");
        });
    }

    // ================================================================== sans rotation : rien ne bouge
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_counter_still", timeoutTicks = 120)
    public static void p104_sans_rotation(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = network(h, false, crate);
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 200, false);
        counter.counter().request(new ItemStack(Items.IRON_INGOT), 64);
        h.runAtTickTime(100, () -> {
            Gt.check(counter.counter().parcels().isEmpty(), "un colis est parti sans rotation");
            Gt.equal(200, crate[0].storage().count(new ItemStack(Items.IRON_INGOT)), "la caisse a perdu des objets sans rotation");
            Gt.equal(1, counter.counter().queue().size(), "demande en attente");
            Gt.check(counter.station() != null && counter.station().spin() == 0, "corde a l'arret");
            PulleyTile st = counter.station();
            Gt.equal(1, st.ropeCount(), "une corde");
            h.succeed();
        });
    }
}
