package fr.pioneers.gametest;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.bag.BagContents;
import fr.pioneers.building.home.bag.BagInterfaceTile;
import fr.pioneers.building.home.bag.BagKind;
import fr.pioneers.tech.kinetics.parts.HandCrankBlock;
import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.core.registries.PiTransport;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.ChannelDef;
import fr.pioneers.logistics.transport.logistic.ChannelKind;
import fr.pioneers.logistics.transport.logistic.LogisticApi;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticCableTile;
import fr.pioneers.logistics.transport.logistic.LogisticNetwork;
import fr.pioneers.logistics.transport.logistic.LogisticNetworks;
import fr.pioneers.logistics.transport.logistic.PointConfig;
import fr.pioneers.logistics.transport.logistic.Role;
import fr.pioneers.logistics.warehouse.CounterBlock;
import fr.pioneers.logistics.warehouse.CounterTile;
import fr.pioneers.logistics.warehouse.CrateHatchTile;
import fr.pioneers.logistics.warehouse.CrateLayout;
import fr.pioneers.logistics.warehouse.PiWarehouse;
import fr.pioneers.logistics.warehouse.WarehouseRopes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 45 (retours de jeu 6e serie, 3 : logistique ; controle tools/check_p105_45.py ; bancs tools/cables/run.sh SiteBench et
 * tools/bags/bag_interface_bench.sh) :
 * <ul>
 *     <li>63 : FACE « RIEN » DECONNECTEE : dans un reseau a canaux, le coffre sans role perd sa fiche (etat NONE, masque de la tile), reste un
 *     raccord (diagramme) ; un role donne la rebranche et les objets passent ;</li>
 *     <li>66 : RESEAU COUPE PAR UN CHUNK NON CHARGE QUI RESTE ENTIER : un chunk du milieu traite comme non charge (crochet des GameTests) : le
 *     reseau reconstruit par l'index garde tous ses cables ; une machine d'un chunk non charge est en pause (rien ne bouge, rien ne se perd),
 *     puis reprend ;</li>
 *     <li>65 : SAC VIDE PAR L'ENTREE D'ENTREPOT (case de sac : le contenu part en colis vers la caisse, le sac vide reste) ;</li>
 *     <li>65 : INTERFACE DE SAC LUE PAR UN CABLE (fiche vers l'interface, le contenu du sac part au coffre, sac retire en cours : rien n'est
 *     duplique).</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot45Tests {

    private PatchP105Lot45Tests() {
    }

    private static LogisticCableBlock cable(CableLevel level) {
        return PiTransport.logisticCable(level, false).getBlock();
    }

    /** Ligne de cables de x0 a x1 (y, z fixes) ; chaque cable relit ses voisins (fiches vers les machines). */
    private static void line(GameTestHelper h, int x0, int x1, int y, int z) {
        LogisticCableBlock block = cable(CableLevel.BASIC);
        for (int x = x0; x <= x1; x++) {
            h.setBlock(new BlockPos(x, y, z), block);
        }
        for (int x = x0; x <= x1; x++) {
            BlockPos rel = new BlockPos(x, y, z);
            h.setBlock(rel, block.refresh(h.getLevel(), h.absolutePos(rel), h.getBlockState(rel)));
        }
    }

    private static int items(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            n += c.getItem(i).getCount();
        }
        return n;
    }

    /** Canal d'objets ; points : {@code from} en entree (tirer), {@code to} en sortie. */
    private static int channel(ServerLevel level, BlockPos cable, long from, long to) {
        ChannelDef def = new ChannelDef(0, ChannelKind.ITEM);
        def.name = "Lot 45";
        Gt.check(LogisticApi.putChannel(level, cable, def), "pas de reseau pour creer le canal");
        LogisticNetwork net = LogisticApi.network(level, cable);
        //La creation materialise le mode auto : les canaux auto devenus reels sont retires (seul le canal du test reste)
        for (ChannelDef d : net.config().live()) {
            if (d.uid != def.uid) {
                LogisticApi.deleteChannel(level, cable, d.uid);
            }
        }
        if (from != 0) {
            Gt.check(LogisticApi.putPoint(level, cable, from, def.uid, new PointConfig(Role.INPUT, true)), "point d'entree refuse");
        }
        if (to != 0) {
            Gt.check(LogisticApi.putPoint(level, cable, to, def.uid, new PointConfig(Role.OUTPUT, true)), "point de sortie refuse");
        }
        return def.uid;
    }

    // ================================================================== 63 : face « Rien » deconnectee, un role la rebranche
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_45_rien", timeoutTicks = 400)
    public static void p105_45_face_rien_deconnectee(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 2, 4), b = new BlockPos(7, 2, 4);
        h.setBlock(a, Blocks.CHEST);
        h.setBlock(b, Blocks.CHEST);
        ((Container) h.getBlockEntity(a)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        line(h, 2, 6, 2, 4);
        ServerLevel level = h.getLevel();
        BlockPos cableB = h.absolutePos(new BlockPos(6, 2, 4));
        long keyA = h.absolutePos(a).asLong(), keyB = h.absolutePos(b).asLong();
        Gt.check(LogisticCableBlock.link(level.getBlockState(cableB), Direction.EAST) == LogisticCableBlock.Link.PLUG, "pas de fiche vers le coffre B");
        int[] uid = {0};
        h.runAfterDelay(4, () -> {
            //Canal : A en entree seulement ; B n'a aucun role (« Rien » partout) : deconnecte
            uid[0] = channel(level, cableB, keyA, 0);
            LogisticNetwork net = LogisticApi.network(level, cableB);
            Gt.check(net != null && !net.connected(keyB) && net.attach(keyB) != null, "B (« Rien » partout) n'est pas deconnecte");
        });
        h.runAfterDelay(12, () -> {
            Gt.equal(LogisticCableBlock.Link.NONE, LogisticCableBlock.link(level.getBlockState(cableB), Direction.EAST), "fiche vers B retiree (etat)");
            Gt.check(level.getBlockEntity(cableB) instanceof LogisticCableTile t && t.isDetached(Direction.EAST), "face vers B marquee deconnectee (tile)");
            LogisticNetwork net = LogisticApi.network(level, cableB);
            Gt.check(net != null && net.attach(keyB) != null, "B n'est plus un raccord du reseau (il doit rester dans le schema)");
            Gt.equal(32, items((Container) h.getBlockEntity(a)), "des objets sont partis vers une machine deconnectee");
            //Un role rebranche B
            Gt.check(LogisticApi.putPoint(level, cableB, keyB, uid[0], new PointConfig(Role.OUTPUT, true)), "role de B refuse");
        });
        h.runAfterDelay(20, () -> Gt.equal(LogisticCableBlock.Link.PLUG, LogisticCableBlock.link(level.getBlockState(cableB), Direction.EAST),
              "fiche vers B rendue par le role"));
        h.runAfterDelay(30, () -> h.succeedWhen(() -> {
            Gt.equal(32, items((Container) h.getBlockEntity(b)), "objets arrives dans B rebranche");
            Gt.equal(0, items((Container) h.getBlockEntity(a)), "A vide");
        }));
    }

    // ================================================================== 66 : chunk non charge au milieu, reseau entier ; machine en pause
    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_45_chunks", timeoutTicks = 900)
    public static void p105_45_reseau_coupe_par_un_chunk_non_charge_reste_entier(GameTestHelper h) {
        BlockPos a = new BlockPos(0, 2, 4), b = new BlockPos(31, 2, 4);
        h.setBlock(a, Blocks.CHEST);
        h.setBlock(b, Blocks.CHEST);
        ((Container) h.getBlockEntity(a)).setItem(0, new ItemStack(Items.IRON_INGOT, 48));
        line(h, 1, 30, 2, 4);
        ServerLevel level = h.getLevel();
        BlockPos cableA = h.absolutePos(new BlockPos(1, 2, 4));
        long keyA = h.absolutePos(a).asLong(), keyB = h.absolutePos(b).asLong();
        //Chunk du milieu : celui qui suit la premiere frontiere de chunk de la ligne
        //(jamais celui du premier cable : x = 1 reste charge)
        int border = Gt.borderX(h, Gt.LARGE_SIZE);
        ChunkPos middle = new ChunkPos(h.absolutePos(new BlockPos(border == 0 ? 17 : border + 1, 2, 4)));
        boolean bInMiddle = new ChunkPos(h.absolutePos(b)).equals(middle);
        int[] size = {0};
        h.runAfterDelay(4, () -> {
            LogisticNetwork net = LogisticApi.network(level, cableA);
            Gt.check(net != null, "reseau non construit");
            size[0] = net.graph.size;
            Gt.equal(30, size[0], "cables du reseau");
            channel(level, cableA, keyA, keyB);
            //Le chunk du milieu « se decharge » (GameTests : crochet) ; une reconstruction passe par l'index
            LogisticNetworks.testUnloaded(level, middle, true);
            LogisticNetworks.invalidate(level, cableA);
        });
        h.runAfterDelay(8, () -> {
            LogisticNetwork net = LogisticApi.network(level, cableA);
            Gt.check(net != null, "reseau perdu apres la reconstruction");
            Gt.equal(size[0], net.graph.size, "reseau coupe : des cables manquent (le reseau doit rester entier par l'index)");
            Gt.check(net.attach(keyB) != null, "la machine du bout n'est plus raccordee");
            Gt.equal(bInMiddle ? 1 : 0, net.pausedCount(), "machines en pause");
        });
        h.runAfterDelay(200, () -> {
            int inA = items((Container) h.getBlockEntity(a)), inB = items((Container) h.getBlockEntity(b));
            Gt.equal(48, inA + inB, "objets perdus ou crees");
            if (bInMiddle) {
                Gt.equal(48, inA, "B est en pause (chunk non charge) : rien ne doit lui arriver");
            } else {
                Gt.equal(48, inB, "chunk du milieu non charge : les objets passent quand meme");
            }
            LogisticNetworks.testUnloaded(level, middle, false);
        });
        h.runAfterDelay(260, () -> h.succeedWhen(() -> {
            LogisticNetwork net = LogisticApi.network(level, cableA);
            Gt.check(net != null && net.pausedCount() == 0, "machine toujours en pause apres le rechargement");
            Gt.equal(48, items((Container) h.getBlockEntity(b)), "objets arrives apres le rechargement");
        }));
    }

    // ================================================================== 65 : l'entree d'entrepot vide le sac vers la caisse
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

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_45_inlet", timeoutTicks = 2400)
    public static void p105_45_entree_entrepot_vide_le_sac(GameTestHelper h) {
        CrateHatchTile crate = crate(h, new BlockPos(2, 1, 2));
        BlockPos w1 = crate.getBlockPos().above();
        h.getLevel().setBlock(w1, PiKinetics.SMALL_PULLEY.getBlock().defaultBlockState(), 3);
        BlockPos c = h.absolutePos(new BlockPos(9, 3, 3));
        h.getLevel().setBlock(c.below(2), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(c, PiWarehouse.INLET.getBlock().defaultBlockState().setValue(CounterBlock.FACING, Direction.WEST), 3);
        h.getLevel().setBlock(c.above(), PiKinetics.SMALL_PULLEY.getBlock().defaultBlockState(), 3);
        CounterTile inlet = (CounterTile) h.getLevel().getBlockEntity(c);
        Gt.check(inlet.station() != null && inlet.bagSlot() != null, "entree sans poulie ou sans case de sac");
        Gt.check(WarehouseRopes.link(h.getLevel(), w1, inlet.station().getBlockPos(), LinkType.FIBER_ROPE), "corde refusee");
        for (BlockPos crankAt : new BlockPos[]{w1.above(), inlet.station().getBlockPos().above()}) {
            h.getLevel().setBlock(crankAt, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, Direction.DOWN), 3);
        }
        HandCrankBlock.Tile k1 = (HandCrankBlock.Tile) h.getLevel().getBlockEntity(w1.above());
        HandCrankBlock.Tile k2 = (HandCrankBlock.Tile) h.getLevel().getBlockEntity(inlet.station().getBlockPos().above());
        h.onEachTick(() -> {
            k1.crank(10);
            k2.crank(10);
        });
        //Le sac a dos plein dans la case de sac (le joueur : seul autorise) ; un entonnoir ne peut pas l'y poser
        ItemStack pack = new ItemStack(PiHome.BAGS.get(BagKind.LEATHER_BACKPACK).get());
        BagContents.insert(pack, BagKind.LEATHER_BACKPACK, new ItemStack(Items.IRON_INGOT, 64), false);
        BagContents.insert(pack, BagKind.LEATHER_BACKPACK, new ItemStack(Items.COAL, 32), false);
        Gt.check(!inlet.bagSlot().canInsert(pack, Automation.EXTERNAL), "un entonnoir pose un sac dans la case de sac");
        Gt.check(inlet.bagSlot().insert(pack, Action.EXECUTE, Automation.MANUAL).isEmpty(), "le joueur ne peut pas poser son sac");
        h.succeedWhen(() -> {
            Gt.equal(64L, crate.storage().count(new ItemStack(Items.IRON_INGOT)), "lingots du sac ranges dans la caisse");
            Gt.equal(32L, crate.storage().count(new ItemStack(Items.COAL)), "charbon du sac range dans la caisse");
            ItemStack left = inlet.bagSlot().getStack();
            Gt.check(left.is(PiHome.BAGS.get(BagKind.LEATHER_BACKPACK).get()), "le sac vide n'est plus dans sa case");
            Gt.equal(0, BagContents.count(BagContents.read(left, BagKind.LEATHER_BACKPACK)), "le sac n'est pas vide");
        });
    }

    // ================================================================== 65 : l'interface de sac lue par un cable logistique
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_45_bag", timeoutTicks = 600)
    public static void p105_45_interface_de_sac_lue_par_un_cable(GameTestHelper h) {
        BlockPos iface = new BlockPos(1, 2, 4), chest = new BlockPos(7, 2, 4);
        h.setBlock(iface, PiWarehouse.BAG_INTERFACE.getBlock());
        h.setBlock(chest, Blocks.CHEST);
        line(h, 2, 6, 2, 4);
        ServerLevel level = h.getLevel();
        BlockPos cableNear = h.absolutePos(new BlockPos(2, 2, 4));
        //Sans sac : la fiche existe deja (inventaire de 0 case, toujours expose)
        Gt.equal(LogisticCableBlock.Link.PLUG, LogisticCableBlock.link(level.getBlockState(cableNear), Direction.WEST), "pas de fiche vers l'interface de sac");
        BagInterfaceTile tile = (BagInterfaceTile) h.getBlockEntity(iface);
        ItemStack pack = new ItemStack(PiHome.BAGS.get(BagKind.TANNED_BACKPACK).get());
        for (int i = 0; i < 6; i++) {
            BagContents.insert(pack, BagKind.TANNED_BACKPACK, new ItemStack(Items.OAK_LOG, 64), false);
        }
        Gt.check(tile.bagSlot().insert(pack, Action.EXECUTE, Automation.MANUAL).isEmpty(), "sac refuse par sa case");
        long keyI = h.absolutePos(iface).asLong(), keyC = h.absolutePos(chest).asLong();
        h.runAfterDelay(4, () -> channel(level, cableNear, keyI, keyC));
        ItemStack[] removed = {ItemStack.EMPTY};
        h.onEachTick(() -> {
            //Sac retire EN COURS de transfert (le joueur le reprend des que le coffre a recu) : ce qui reste dedans y reste, rien n'est duplique
            if (removed[0].isEmpty() && items((Container) h.getBlockEntity(chest)) > 0) {
                removed[0] = tile.bagSlot().extract(1, Action.EXECUTE, Automation.MANUAL);
                Gt.check(!removed[0].isEmpty(), "sac impossible a reprendre");
            }
        });
        h.succeedWhen(() -> {
            Gt.check(!removed[0].isEmpty(), "rien n'est sorti du sac par le cable");
            int inChest = items((Container) h.getBlockEntity(chest));
            int inBag = BagContents.count(BagContents.read(removed[0], BagKind.TANNED_BACKPACK));
            Gt.equal(6 * 64, inChest + inBag, "objets perdus ou dupliques (coffre " + inChest + ", sac " + inBag + ")");
            Gt.check(inBag > 0, "le sac s'est vide avant d'etre repris (test sans transfert en cours)");
            Gt.equal(0, tile.contents().getSlots(), "l'interface sans sac expose encore des cases");
        });
    }
}
