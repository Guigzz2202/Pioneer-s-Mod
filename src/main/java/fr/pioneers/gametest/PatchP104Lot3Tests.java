package fr.pioneers.gametest;

import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.parts.HandCrankBlock;
import fr.pioneers.kinetics.rope.LinkType;
import fr.pioneers.registries.PiKinetics;
import fr.pioneers.warehouse.CounterBlock;
import fr.pioneers.warehouse.CounterCart;
import fr.pioneers.warehouse.CounterTile;
import fr.pioneers.warehouse.CounterUnit;
import fr.pioneers.warehouse.CrateHatchTile;
import fr.pioneers.warehouse.CrateLayout;
import fr.pioneers.warehouse.PiWarehouse;
import fr.pioneers.warehouse.WarehouseLines;
import fr.pioneers.warehouse.WarehouseRopes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 3 (GameTests) : POTEAUX de support des poulies et PANIER du comptoir. Relais (poulie d'entrepot, petite et grande
 * poulies de base) chacun sur 3 poteaux depuis le sol, colonnes calculees par l'aide a la pose (Maj + clic) : formes connectees (pied, tete
 * selon la poulie), cordes acceptees (les colis passent sans toucher les tetes) et colis livre au comptoir au sol ; panier de plusieurs
 * lignes : « Commander » cree toutes les demandes et vide le panier ; panier qui depasse le stock ou la file : ce qui ne part pas reste, avec
 * la raison ; rien ne bouge sans rotation.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot3Tests {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000104");

    private PatchP104Lot3Tests() {
    }

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

    private static BlockPos basePulley(GameTestHelper h, BlockPos abs, Block block) {
        h.getLevel().setBlock(abs, block.defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.Y), 3);
        return abs;
    }

    private static void rope(GameTestHelper h, BlockPos a, BlockPos b) {
        Gt.check(WarehouseRopes.link(h.getLevel(), a, b, LinkType.FIBER_ROPE), "corde " + a + " -> " + b + " refusee : " + WarehouseRopes.check(h.getLevel(), a, b));
    }

    private static HandCrankBlock.Tile crank(GameTestHelper h, BlockPos abs, Direction facing) {
        h.getLevel().setBlock(abs, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, facing), 3);
        return (HandCrankBlock.Tile) h.getLevel().getBlockEntity(abs);
    }

    private static int out(CounterTile counter, Item item) {
        int got = 0;
        for (var slot : counter.outputs()) {
            if (slot.getStack().is(item)) {
                got += slot.getCount();
            }
        }
        return got;
    }

    // ================================================================== relais en l'air (1.0.4-beta lot 27 : plus de poteaux) : colis livre
    @GameTest(template = Gt.TALL, batch = "pioneers_p104c_posts", timeoutTicks = 2000)
    public static void p104c_relais_sur_poteaux(GameTestHelper h) {
        CrateHatchTile crate = crate(h, new BlockPos(2, 1, 2));
        BlockPos w1 = warehousePulley(h, crate.getBlockPos().above());
        BlockPos wr = warehousePulley(h, h.absolutePos(new BlockPos(8, 4, 3)));
        BlockPos small = basePulley(h, h.absolutePos(new BlockPos(13, 4, 3)), PiKinetics.SMALL_PULLEY.getBlock());
        BlockPos large = basePulley(h, h.absolutePos(new BlockPos(13, 4, 10)), PiKinetics.LARGE_PULLEY.getBlock());
        BlockPos c = h.absolutePos(new BlockPos(17, 1, 10));
        h.getLevel().setBlock(c, PiWarehouse.COUNTER.getBlock().defaultBlockState().setValue(CounterBlock.FACING, Direction.WEST), 3);
        BlockPos w2 = warehousePulley(h, h.absolutePos(new BlockPos(17, 4, 10)));
        //Les colis passent sous les roues (rien dessous)
        Gt.check(WarehouseLines.underWheel(h.getLevel(), small) == null, "un colis toucherait sous la petite poulie : " + WarehouseLines.underWheel(h.getLevel(), small));
        Gt.check(WarehouseLines.underWheel(h.getLevel(), large) == null, "un colis toucherait sous la grande poulie");
        rope(h, w1, wr);
        rope(h, wr, small);
        rope(h, small, large);
        rope(h, large, w2);
        Gt.check(WarehouseLines.usable(h.getLevel(), wr, small, LinkType.FIBER_ROPE) && WarehouseLines.usable(h.getLevel(), small, large, LinkType.FIBER_ROPE)
                 && WarehouseLines.usable(h.getLevel(), large, w2, LinkType.FIBER_ROPE), "une corde des relais ne porte pas de colis");
        CounterTile counter = (CounterTile) h.getLevel().getBlockEntity(c);
        HandCrankBlock.Tile a = crank(h, w2.above(), Direction.DOWN), b = crank(h, w1.above(), Direction.DOWN);
        h.onEachTick(() -> {
            a.crank(10);
            b.crank(10);
        });
        Gt.equal(5, counter.counter().graph().links().size(), "reseau : 3 petites poulies et 2 poulies de relais");
        crate.storage().insert(new ItemStack(Items.IRON_INGOT), 200, false);
        counter.counter().cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 100);
        counter.counter().order(PLAYER);
        h.succeedWhen(() -> {
            Gt.equal(100, out(counter, Items.IRON_INGOT), "lingots livres par les relais");
            Gt.equal(100, crate.storage().count(new ItemStack(Items.IRON_INGOT)), "reste dans la caisse");
        });
    }

    /** Petit reseau : caisse (poulie sur la trappe), comptoir monte (poulie dessus), une corde ; rien ne tourne. */
    private static CounterTile simple(GameTestHelper h, CrateHatchTile[] crate) {
        crate[0] = crate(h, new BlockPos(2, 1, 2));
        BlockPos w1 = warehousePulley(h, crate[0].getBlockPos().above());
        BlockPos c = h.absolutePos(new BlockPos(9, 3, 3));
        //Le comptoir monte sur deux blocs (1.0.4-beta lot 27 : plus de poteaux d'entrepot)
        h.getLevel().setBlock(c.below(2), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c, PiWarehouse.COUNTER.getBlock().defaultBlockState().setValue(CounterBlock.FACING, Direction.WEST), 3);
        BlockPos w2 = warehousePulley(h, c.above());
        rope(h, w1, w2);
        CounterTile counter = (CounterTile) h.getLevel().getBlockEntity(c);
        Gt.check(counter.station() != null, "comptoir sans station");
        return counter;
    }

    // ================================================================== panier de plusieurs lignes : toutes les demandes d'un coup
    @GameTest(template = Gt.TALL, batch = "pioneers_p104c_cart", timeoutTicks = 40)
    public static void p104c_panier_multi_lignes(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = simple(h, crate);
        CounterUnit unit = counter.counter();
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 300, false);
        crate[0].storage().insert(new ItemStack(Items.COPPER_INGOT), 90, false);
        crate[0].storage().insert(new ItemStack(Items.OAK_LOG), 40, false);
        //Clics : + 1 pile de fer deux fois, + 1 pile de cuivre, + 7 buches (Maj + clic), puis - 1 pile de fer (clic droit)
        unit.cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 128);
        unit.cartSet(PLAYER, new ItemStack(Items.COPPER_INGOT), 64);
        unit.cartSet(PLAYER, new ItemStack(Items.OAK_LOG), 7);
        unit.cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 64);
        //Borne par le stock : 500 buches demandees, 40 au panier
        unit.cartSet(PLAYER, new ItemStack(Items.OAK_LOG), 500);
        CounterCart cart = unit.cart(PLAYER);
        Gt.equal(3, cart.lines.size(), "trois lignes au panier");
        Gt.equal(40, cart.count(new ItemStack(Items.OAK_LOG)), "ligne bornee par le stock");
        Gt.check(unit.queue().isEmpty(), "le panier a envoye quelque chose avant « Commander »");
        //Un autre joueur a son propre panier
        Gt.check(unit.cart(UUID.randomUUID()).lines.isEmpty(), "panier partage entre joueurs");
        CounterCart.Notice n = unit.order(PLAYER);
        Gt.equal("gui.pioneers.warehouse.order.ok", n.key(), "avis de commande");
        Gt.equal(3, unit.queue().size(), "trois demandes creees");
        Gt.equal(64L, unit.queued(new ItemStack(Items.IRON_INGOT)), "fer demande");
        Gt.equal(64L, unit.queued(new ItemStack(Items.COPPER_INGOT)), "cuivre demande");
        Gt.equal(40L, unit.queued(new ItemStack(Items.OAK_LOG)), "buches demandees");
        Gt.check(cart.lines.isEmpty(), "le panier ne s'est pas vide");
        //Sauvegarde : un panier non vide survit (meme joueur, meme contenu)
        unit.cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 10);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        unit.save(tag);
        unit.cartClear(PLAYER);
        unit.load(tag);
        Gt.equal(10, unit.cart(PLAYER).count(new ItemStack(Items.IRON_INGOT)), "panier relu de la sauvegarde");
        h.succeed();
    }

    // ================================================================== panier qui depasse le stock ou la file : il reste, avec la raison
    @GameTest(template = Gt.TALL, batch = "pioneers_p104c_partial", timeoutTicks = 40)
    public static void p104c_panier_partiel(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = simple(h, crate);
        CounterUnit unit = counter.counter();
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 100, false);
        unit.cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 100);
        //Le stock part entre-temps (un entonnoir, un autre comptoir...)
        crate[0].storage().extract(new ItemStack(Items.IRON_INGOT), 60, false);
        CounterCart.Notice n = unit.order(PLAYER);
        Gt.equal("gui.pioneers.warehouse.order.partial", n.key(), "avis partiel");
        Gt.equal("gui.pioneers.warehouse.reason.stock", n.reason(), "raison : le stock");
        Gt.equal(40L, unit.queued(new ItemStack(Items.IRON_INGOT)), "ce qui restait est parti");
        Gt.equal(60, unit.cart(PLAYER).count(new ItemStack(Items.IRON_INGOT)), "le reste est toujours au panier");
        //File pleine : 64 types deja demandes
        unit.clearQueue();
        unit.cart(PLAYER).lines.clear();
        Item[] fillers = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues().stream()
              .filter(i -> i != Items.AIR && i != Items.IRON_INGOT && i != Items.GOLD_INGOT).limit(CounterUnit.MAX_QUEUE).toArray(Item[]::new);
        for (Item i : fillers) {
            Gt.check(unit.request(new ItemStack(i), 1), "file remplie trop tot");
        }
        crate[0].storage().insert(new ItemStack(Items.GOLD_INGOT), 20, false);
        unit.cartSet(PLAYER, new ItemStack(Items.GOLD_INGOT), 20);
        n = unit.order(PLAYER);
        Gt.equal("gui.pioneers.warehouse.order.none", n.key(), "rien n'est parti");
        Gt.equal("gui.pioneers.warehouse.reason.queue", n.reason(), "raison : la file");
        Gt.equal(20, unit.cart(PLAYER).count(new ItemStack(Items.GOLD_INGOT)), "la ligne reste au panier");
        h.succeed();
    }

    // ================================================================== sans rotation : la commande attend, rien ne bouge
    @GameTest(template = Gt.TALL, batch = "pioneers_p104c_still", timeoutTicks = 140)
    public static void p104c_commande_sans_rotation(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = simple(h, crate);
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 64, false);
        counter.counter().cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 64);
        counter.counter().order(PLAYER);
        h.runAtTickTime(120, () -> {
            Gt.check(counter.counter().parcels().isEmpty(), "un colis est parti sans rotation");
            Gt.equal(64L, counter.counter().queued(new ItemStack(Items.IRON_INGOT)), "la demande attend dans la file");
            Gt.equal(64, crate[0].storage().count(new ItemStack(Items.IRON_INGOT)), "la caisse a perdu des objets sans rotation");
            Gt.equal(0, out(counter, Items.IRON_INGOT), "rien en sortie");
            h.succeed();
        });
    }
}
