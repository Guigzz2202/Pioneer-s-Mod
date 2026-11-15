package fr.pioneers.gametest;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.kinetics.parts.HandCrankBlock;
import fr.pioneers.kinetics.rope.LinkType;
import fr.pioneers.network.PacketWarehouse;
import fr.pioneers.registries.PiKinetics;
import fr.pioneers.warehouse.CounterBlock;
import fr.pioneers.warehouse.CounterCart;
import fr.pioneers.warehouse.CounterTile;
import fr.pioneers.warehouse.CounterUnit;
import fr.pioneers.warehouse.CrateHatchTile;
import fr.pioneers.warehouse.CrateLayout;
import fr.pioneers.warehouse.PiWarehouse;
import fr.pioneers.warehouse.RecipeAsk;
import fr.pioneers.warehouse.StockKey;
import fr.pioneers.warehouse.StockSync;
import fr.pioneers.warehouse.WarehouseRopes;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Patch 1.0.5-beta, lot 17 (GameTests) : le COMPTOIR D'ENTREPOT et ses INTERFACES. Interface d'entree : ce qu'un entonnoir y pose part en
 * colis et arrive dans la caisse (un entonnoir n'y reprend rien) ; interface de sortie : une regle « garder N » se remplit, la goulotte pousse
 * dans le coffre qu'elle regarde, la regle redemande ; recette de JEI au panier (alternatives d'apres le stock, ce qui manque n'est pas
 * ajoute, « Commander » aussitot) ; paquet de recette hors bornes refuse (lignes, alternatives, fabrications, nombres ; decodage borne) ;
 * commande dirigee vers une interface de sortie du reseau ; synchro par differences.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot17Tests {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001017");

    private PatchP105Lot17Tests() {
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

    private static BlockPos pulley(GameTestHelper h, BlockPos abs) {
        h.getLevel().setBlock(abs, PiKinetics.SMALL_PULLEY.getBlock().defaultBlockState(), 3);
        return abs;
    }

    private static void rope(GameTestHelper h, BlockPos a, BlockPos b) {
        Gt.check(WarehouseRopes.link(h.getLevel(), a, b, LinkType.FIBER_ROPE), "corde " + a + " -> " + b + " refusee : " + WarehouseRopes.check(h.getLevel(), a, b));
    }

    private static HandCrankBlock.Tile crank(GameTestHelper h, BlockPos abs) {
        h.getLevel().setBlock(abs, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, Direction.DOWN), 3);
        return (HandCrankBlock.Tile) h.getLevel().getBlockEntity(abs);
    }

    /** Une station (comptoir ou interface) montee sur deux blocs de pierre, petite poulie dessus. */
    private static CounterTile station(GameTestHelper h, BlockPos rel, Block block, Direction facing) {
        BlockPos c = h.absolutePos(rel);
        h.getLevel().setBlock(c.below(2), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c, block.defaultBlockState().setValue(CounterBlock.FACING, facing), 3);
        pulley(h, c.above());
        CounterTile t = (CounterTile) h.getLevel().getBlockEntity(c);
        Gt.check(t.station() != null, "station sans poulie");
        return t;
    }

    /** Caisse + une station reliee (corde), les deux poulies entrainees a la manivelle. */
    private static CounterTile line(GameTestHelper h, CrateHatchTile[] crate, Block block, Direction facing, boolean turn) {
        crate[0] = crate(h, new BlockPos(2, 1, 2));
        BlockPos w1 = pulley(h, crate[0].getBlockPos().above());
        CounterTile t = station(h, new BlockPos(9, 3, 3), block, facing);
        rope(h, w1, t.station().getBlockPos());
        if (turn) {
            HandCrankBlock.Tile a = crank(h, w1.above()), b = crank(h, t.station().getBlockPos().above());
            h.onEachTick(() -> {
                a.crank(10);
                b.crank(10);
            });
        }
        return t;
    }

    // ================================================================== interface d'entree : l'entonnoir pose, le colis range dans la caisse
    @GameTest(template = Gt.TALL, batch = "pioneers_p105_17_inlet", timeoutTicks = 2000)
    public static void p105_17_entree_vers_caisse(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile inlet = line(h, crate, PiWarehouse.INLET.getBlock(), Direction.WEST, true);
        Gt.equal(CounterTile.Role.INLET, inlet.role(), "role de l'interface d'entree");
        IItemHandler side = inlet.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElse(null);
        Gt.check(side != null && side.getSlots() == 9, "capacite de l'entree : 9 cases");
        Gt.equal(0, side.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false).getCount(), "l'entonnoir pose 64 lingots");
        Gt.check(side.extractItem(0, 64, true).isEmpty(), "un entonnoir reprend dans l'interface d'entree");
        Gt.check(inlet.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().isEmpty(), "capacite par le dessus (la poulie)");
        h.succeedWhen(() -> {
            Gt.equal(64L, crate[0].storage().count(new ItemStack(Items.IRON_INGOT)), "lingots ranges dans la caisse");
            Gt.equal(0, inlet.held(new ItemStack(Items.IRON_INGOT)), "cases de l'entree videes");
        });
    }

    // ================================================================== interface de sortie : « garder 32 », goulotte vers un coffre, redemande
    @GameTest(template = Gt.TALL, batch = "pioneers_p105_17_outlet", timeoutTicks = 3000)
    public static void p105_17_sortie_garde_et_pousse(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile outlet = line(h, crate, PiWarehouse.OUTLET.getBlock(), Direction.SOUTH, true);
        BlockPos chestPos = outlet.getBlockPos().south();
        h.getLevel().setBlock(chestPos.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        crate[0].storage().insert(new ItemStack(Items.COAL), 200, false);
        CounterUnit unit = outlet.counter();
        Gt.check(unit.keepSet(new ItemStack(Items.COAL), 32), "regle « garder 32 » refusee");
        Gt.check(!unit.keepSet(new ItemStack(Items.IRON_INGOT), 0) || unit.keeps().size() == 1, "regle a 0 ajoutee");
        //Sauvegarde des regles
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        unit.save(tag);
        unit.keepSet(new ItemStack(Items.COAL), 0);
        unit.load(tag);
        Gt.equal(1, unit.keeps().size(), "regle relue de la sauvegarde");
        IItemHandler side = outlet.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
        Gt.equal(5, side.insertItem(0, new ItemStack(Items.COAL, 5), true).getCount(), "un entonnoir remplit la sortie");
        h.succeedWhen(() -> {
            int inChest = 0;
            if (h.getLevel().getBlockEntity(chestPos) instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    inChest += c.getItem(i).is(Items.COAL) ? c.getItem(i).getCount() : 0;
                }
            }
            //La goulotte a vide la sortie dans le coffre au moins deux fois : la regle a redemande
            Gt.check(inChest >= 64, "charbon pousse dans le coffre : " + inChest);
            Gt.check(outlet.held(new ItemStack(Items.COAL)) <= 32, "plus que la regle dans la sortie");
        });
    }

    // ================================================================== recette de JEI au panier : alternatives, manques, « Commander »
    @GameTest(template = Gt.TALL, batch = "pioneers_p105_17_recipe", timeoutTicks = 40)
    public static void p105_17_recette_au_panier(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = line(h, crate, PiWarehouse.COUNTER.getBlock(), Direction.WEST, false);
        CounterUnit unit = counter.counter();
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 10, false);
        crate[0].storage().insert(new ItemStack(Items.OAK_PLANKS), 5, false);
        crate[0].storage().insert(new ItemStack(Items.BIRCH_PLANKS), 10, false);
        Map<StockKey, ItemStack> protos = new HashMap<>();
        for (ItemStack s : List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND), new ItemStack(Items.OAK_PLANKS),
              new ItemStack(Items.BIRCH_PLANKS))) {
            protos.put(StockKey.of(s), s);
        }
        StockKey iron = StockKey.of(new ItemStack(Items.IRON_INGOT)), diamond = StockKey.of(new ItemStack(Items.DIAMOND)),
              oak = StockKey.of(new ItemStack(Items.OAK_PLANKS)), birch = StockKey.of(new ItemStack(Items.BIRCH_PLANKS));
        //2 fois : 3 fer + 1 diamant (absent) : le fer va au panier, le diamant manque
        CounterCart.Notice n = unit.recipe(PLAYER, List.of(new RecipeAsk.Line<>(List.of(iron), 3), new RecipeAsk.Line<>(List.of(diamond), 1)), protos, 2, false);
        CounterCart cart = unit.cart(PLAYER);
        Gt.equal(6, cart.count(new ItemStack(Items.IRON_INGOT)), "fer de la recette au panier");
        Gt.equal(0, cart.count(new ItemStack(Items.DIAMOND)), "ce qui manque est ajoute");
        Gt.equal("gui.pioneers.warehouse.recipe.partial", n.key(), "avis partiel");
        Gt.equal(2L, n.b(), "nombre manquant");
        //Alternatives (un tag de planches) : 3 fois 4 planches = 12 : les plus fournies d'abord (10 bouleau), le reste en chene
        unit.cartClear(PLAYER);
        n = unit.recipe(PLAYER, List.of(new RecipeAsk.Line<>(List.of(oak, birch), 4)), protos, 3, false);
        Gt.equal("gui.pioneers.warehouse.recipe.ok", n.key(), "recette complete");
        Gt.equal(10, cart.count(new ItemStack(Items.BIRCH_PLANKS)), "bouleau pris en premier");
        Gt.equal(2, cart.count(new ItemStack(Items.OAK_PLANKS)), "reste en chene");
        //Maj (le plus possible) : stock 15 planches - 12 deja au panier = 3 -> 0 fabrication de 4 de plus
        Gt.equal(0, RecipeAsk.maxTimes(RecipeAsk.merge(List.of(new RecipeAsk.Line<>(List.of(oak, birch), 4))),
              k -> unit.available(protos.get(k)) - cart.count(protos.get(k)), 64), "maximum d'apres le stock et le panier");
        //Ctrl : « Commander » aussitot
        unit.cartClear(PLAYER);
        n = unit.recipe(PLAYER, List.of(new RecipeAsk.Line<>(List.of(iron), 2)), protos, 1, true);
        Gt.equal("gui.pioneers.warehouse.order.ok", n.key(), "commande aussitot");
        Gt.equal(2L, unit.queued(new ItemStack(Items.IRON_INGOT)), "fer en file");
        Gt.check(cart.lines.isEmpty(), "panier vide apres la commande");
        h.succeed();
    }

    // ================================================================== paquet de recette hors bornes : refuse (decodage borne)
    @GameTest(template = Gt.TALL, batch = "pioneers_p105_17_bounds", timeoutTicks = 20)
    public static void p105_17_paquet_hors_bornes(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        List<ItemStack> one = List.of(new ItemStack(Items.IRON_INGOT));
        Gt.check(new PacketWarehouse.Recipe(pos, List.of(one), List.of(2), 1, 0).valid(), "recette correcte refusee");
        List<List<ItemStack>> many = new ArrayList<>();
        List<Integer> per = new ArrayList<>();
        for (int i = 0; i <= RecipeAsk.MAX_LINES; i++) {
            many.add(one);
            per.add(1);
        }
        Gt.check(!new PacketWarehouse.Recipe(pos, many, per, 1, 0).valid(), "17 lignes acceptees");
        Gt.check(!new PacketWarehouse.Recipe(pos, List.of(one), List.of(1), RecipeAsk.MAX_TIMES + 1, 0).valid(), "65 fabrications acceptees");
        Gt.check(!new PacketWarehouse.Recipe(pos, List.of(one), List.of(0), 1, 0).valid(), "0 objet par fabrication accepte");
        Gt.check(!new PacketWarehouse.Recipe(pos, List.of(one), List.of(RecipeAsk.MAX_PER_CRAFT + 1), 1, 0).valid(), "65 objets par fabrication acceptes");
        List<ItemStack> alts = new ArrayList<>();
        for (int i = 0; i <= RecipeAsk.MAX_ALTERNATIVES; i++) {
            alts.add(new ItemStack(Items.IRON_INGOT));
        }
        Gt.check(!new PacketWarehouse.Recipe(pos, List.of(alts), List.of(1), 1, 0).valid(), "17 alternatives acceptees");
        //Paquet forge par un client : 1 000 lignes annoncees -> decodage arrete, paquet vide, refuse
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(1000);
        PacketWarehouse.Recipe forged = PacketWarehouse.Recipe.decode(buf);
        Gt.check(forged.lines().isEmpty() && !forged.valid(), "1 000 lignes decodees");
        //Aller-retour d'un paquet correct
        FriendlyByteBuf ok = new FriendlyByteBuf(Unpooled.buffer());
        new PacketWarehouse.Recipe(pos, List.of(one, one), List.of(3, 1), 4, PacketWarehouse.Recipe.ORDER).encode(ok);
        PacketWarehouse.Recipe back = PacketWarehouse.Recipe.decode(ok);
        Gt.check(back.valid() && back.lines().size() == 2 && back.times() == 4 && back.flags() == PacketWarehouse.Recipe.ORDER, "aller-retour du paquet");
        //Plan pur : des lignes en double fusionnees, alternatives bornees
        Gt.equal(1, RecipeAsk.merge(List.of(new RecipeAsk.Line<>(List.of("a"), 1), new RecipeAsk.Line<>(List.of("a"), 2))).size(), "lignes identiques fusionnees");
        //Synchro par differences : 3 types, puis un change, un disparait
        StockSync<String> sync = new StockSync<>();
        Gt.equal(3, sync.delta(Map.of("a", 1L, "b", 2L, "c", 3L), 1024).size(), "premiere liste");
        List<StockSync.Change<String>> d = sync.delta(Map.of("a", 1L, "b", 5L), 1024);
        Gt.equal(2, d.size(), "deux differences (b change, c disparait)");
        Gt.equal(2, sync.size(), "le client connait deux types");
        h.succeed();
    }

    // ================================================================== commande dirigee vers une interface de sortie du reseau
    @GameTest(template = Gt.TALL, batch = "pioneers_p105_17_dest", timeoutTicks = 40)
    public static void p105_17_commande_vers_sortie(GameTestHelper h) {
        CrateHatchTile[] crate = new CrateHatchTile[1];
        CounterTile counter = line(h, crate, PiWarehouse.COUNTER.getBlock(), Direction.WEST, false);
        CounterTile outlet = station(h, new BlockPos(15, 3, 3), PiWarehouse.OUTLET.getBlock(), Direction.WEST);
        rope(h, counter.station().getBlockPos(), outlet.station().getBlockPos());
        crate[0].storage().insert(new ItemStack(Items.IRON_INGOT), 100, false);
        CounterUnit unit = counter.counter();
        Gt.check(unit.destinations().contains(outlet.getBlockPos()), "l'interface de sortie n'est pas une destination : " + unit.destinations());
        Gt.check(unit.destTile(outlet.getBlockPos()) == outlet, "destination non reconnue");
        Gt.check(unit.destTile(counter.getBlockPos()) == null, "le comptoir pris pour une interface de sortie");
        unit.cart(PLAYER).dest = outlet.getBlockPos();
        unit.cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 40);
        CounterCart.Notice n = unit.order(PLAYER);
        Gt.equal("gui.pioneers.warehouse.order.ok", n.key(), "commande vers la sortie");
        Gt.equal(40L, outlet.counter().queued(new ItemStack(Items.IRON_INGOT)), "demande dans la file de l'interface de sortie");
        Gt.check(unit.queue().isEmpty(), "demande restee au comptoir");
        //Interface disparue : retour a la sortie du comptoir
        h.getLevel().setBlock(outlet.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        unit.cartSet(PLAYER, new ItemStack(Items.IRON_INGOT), 10);
        unit.order(PLAYER);
        Gt.check(unit.cart(PLAYER).dest == null, "destination gardee apres la casse de l'interface");
        Gt.equal(10L, unit.queued(new ItemStack(Items.IRON_INGOT)), "commande revenue au comptoir");
        h.succeed();
    }
}
