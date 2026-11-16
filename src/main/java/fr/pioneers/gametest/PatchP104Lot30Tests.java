package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.block.Connections;
import fr.pioneers.core.block.Covers;
import fr.pioneers.core.block.PiCoverFrame;
import fr.pioneers.core.profile.WaypointTeleport;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.PortBlock;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferType;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.LogisticCableTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 30, GameTests) : retours de Guillaume du 09/11 10 h 20. Four industriel bati tel que le NBT (une seule carcasse, aucune
 * derogation) : un port a la place d'une tole est accepte et prend la peau de tole (hors de la famille des carcasses) ; cadre de cache
 * (seul moyen de cacher, lime : cache puis cadre rendus, cle : coupe le cote vise comme sur un cable nu) ; cache du lot 27 sans cadre relue
 * comme cadre + cache ; teleportation aux waypoints refusee en survie et en aventure (operateur compris), permise en creatif et spectateur ;
 * place sure jamais sous le sol ni a -64.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot30Tests {

    private PatchP104Lot30Tests() {
    }

    private static BlockHitResult hit(BlockPos abs, Direction side) {
        return new BlockHitResult(Vec3.atCenterOf(abs).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5), side, abs, false);
    }

    private static ItemStack click(GameTestHelper h, Player p, BlockPos abs, ItemStack held, Direction side) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        h.getLevel().getBlockState(abs).use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit(abs, side));
        return p.getItemInHand(InteractionHand.MAIN_HAND);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(Pioneers.rl(id));
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_30_ports", timeoutTicks = 200)
    public static void p104_30_four_tel_que_le_nbt_port_sur_tole(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        GeneratedNbtShapes.Shape shape = GeneratedNbtShapes.of("furnace");
        Gt.equal(1L, shape.layout().chars().filter(ch -> ch == 'C').count(), "four : une seule carcasse, comme le NBT (plus de derogation)");
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.FURNACE);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "four : le plan (tel que le NBT) ne se forme pas");
            BlockPos sheet = null;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, 1, -1), c.offset(3, 3, 4))) {
                if (h.getLevel().getBlockState(h.absolutePos(p)).getBlock() instanceof SheetMetalBlock) {
                    sheet = p.immutable();
                    break;
                }
            }
            Gt.check(sheet != null, "four : aucune tole");
            BlockState port = PiIndustry.PORTS.get(PortBlock.Type.FLUID).getBlock().defaultBlockState();
            h.setBlock(sheet, port);
            tile.requestCheck();
            BlockPos at = sheet;
            h.runAfterDelay(3, () -> {
                Gt.check(tile.formed(), "four : un port a la place d'une tole doit etre accepte");
                BlockState painted = h.getBlockState(at);
                Gt.check(painted.getValue(Connections.ON_SHEET), "port sur une tole : peau de tole");
                Gt.check(!Connections.member(painted, Connections.INDUSTRIAL), "port a la peau de tole : hors de la famille des carcasses");
                for (Direction d : Direction.values()) {
                    BlockState n = h.getBlockState(at.relative(d));
                    if (n.getBlock() instanceof CasingBlock) {
                        Gt.check(!n.getValue(Connections.property(d.getOpposite())), "carcasse voisine raccordee au port a la peau de tole");
                    }
                }
                s.done();
            });
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_30_frame", timeoutTicks = 40)
    public static void p104_30_cadre_de_cache_lime_et_cle(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos a = h.absolutePos(new BlockPos(3, 2, 3)), b = a.east();
        BlockState cable = PiTransport.LOGISTIC_CABLES.get(CableLevel.BASIC).getBlock().defaultBlockState();
        h.getLevel().setBlock(a, cable, 3);
        h.getLevel().setBlock(b, cable, 3);
        h.getLevel().setBlock(b.east(), PiTransport.BUFFERS.get(BufferType.ITEM).get(CableTier.BASIC).getBlock().defaultBlockState(), 3);
        Player p = h.makeMockPlayer();
        //bloc plein sur un cable nu : rien (seul le cadre recoit une cache)
        click(h, p, a, new ItemStack(Items.STONE_BRICKS), Direction.UP);
        Gt.check(!Covers.framed(h.getLevel().getBlockState(a)), "pas de cache sans cadre");
        ItemStack frames = click(h, p, a, new ItemStack(PiCoverFrame.FRAME.asItem(), 2), Direction.UP);
        Gt.equal(1L, (long) frames.getCount(), "un cadre pris");
        BlockState framed = h.getLevel().getBlockState(a);
        Gt.check(framed.getValue(Covers.FRAMED) && !framed.getValue(Covers.COVERED), "cadre installe, sans cache");
        Gt.check(framed.isCollisionShapeFullBlock(h.getLevel(), a), "cadre : forme pleine");
        Gt.check(framed.getLightBlock(h.getLevel(), a) < 15, "cadre sans cache : la lumiere passe");
        //cle : coupe le cote vise, comme sur un cable nu
        click(h, p, a, new ItemStack(item("wrench")), Direction.EAST);
        Gt.check(h.getLevel().getBlockEntity(a) instanceof LogisticCableTile t && t.isCut(Direction.EAST), "cle sur le cadre : cote est coupe");
        click(h, p, a, new ItemStack(item("wrench")), Direction.EAST);
        Gt.check(!(h.getLevel().getBlockEntity(a) instanceof LogisticCableTile t2 && t2.isCut(Direction.EAST)), "cle : cote est retabli");
        click(h, p, a, new ItemStack(Items.STONE_BRICKS), Direction.UP);
        Gt.check(h.getLevel().getBlockState(a).getValue(Covers.COVERED), "bloc plein sur le cadre : cache posee");
        //lime : la cache d'abord (rendue au joueur), puis le cadre
        p.getInventory().clearContent();
        ItemStack file = new ItemStack(item("iron_file"));
        int wear = file.getDamageValue();
        ItemStack after = click(h, p, a, file, Direction.UP);
        BlockState peeled = h.getLevel().getBlockState(a);
        Gt.check(!peeled.getValue(Covers.COVERED) && peeled.getValue(Covers.FRAMED), "lime : la cache part, le cadre reste");
        Gt.check(p.getInventory().countItem(Items.STONE_BRICKS) == 1, "lime : la brique est rendue");
        Gt.check(after.getDamageValue() == wear + 1, "lime : un point d'usure");
        click(h, p, a, after, Direction.UP);
        Gt.check(!Covers.framed(h.getLevel().getBlockState(a)), "lime sur le cadre sans cache : le cadre part");
        Gt.check(p.getInventory().countItem(PiCoverFrame.FRAME.asItem()) == 1, "lime : le cadre est rendu");
        Gt.check(!h.getLevel().getBlockState(a).isCollisionShapeFullBlock(h.getLevel(), a), "cable nu");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_30_legacy", timeoutTicks = 40)
    public static void p104_30_cache_du_lot_27_garde(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos a = h.absolutePos(new BlockPos(3, 2, 3));
        //monde du lot 27 : cable couvert sans cadre (etat couvert, cache dans la tuile)
        BlockState legacy = PiTransport.LOGISTIC_CABLES.get(CableLevel.BASIC).getBlock().defaultBlockState().setValue(Covers.COVERED, true);
        h.getLevel().setBlock(a, legacy, 3);
        Gt.check(h.getLevel().getBlockEntity(a) instanceof LogisticCableTile, "cable couvert : tuile");
        ((LogisticCableTile) h.getLevel().getBlockEntity(a)).setCover(Blocks.OAK_PLANKS.defaultBlockState());
        BlockState st = h.getLevel().getBlockState(a);
        Gt.check(Covers.framed(st) && Covers.covered(st), "cache du lot 27 = cadre + cache");
        Player p = h.makeMockPlayer();
        p.getInventory().clearContent();
        click(h, p, a, new ItemStack(item("iron_file")), Direction.UP);
        BlockState peeled = h.getLevel().getBlockState(a);
        Gt.check(!peeled.getValue(Covers.COVERED) && peeled.getValue(Covers.FRAMED), "lime : la cache part, le cadre implicite devient reel");
        Gt.check(p.getInventory().countItem(Items.OAK_PLANKS) == 1, "la planche est rendue : rien de perdu");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_30_tp_mode", timeoutTicks = 20)
    public static void p104_30_tp_jamais_en_survie(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gttp30", "Ines", new BlockPos(1, 1, 1));
        p.setGameMode(GameType.CREATIVE);
        Gt.check(!WaypointTeleport.allowed(p), "joueur ordinaire en creatif : pas de teleportation");
        Gt.op(s.server, p);   // 1.0.6 : niveau 4 (op() donne le niveau 0 du serveur de GameTests)
        try {
            p.setGameMode(GameType.SURVIVAL);
            Gt.check(!WaypointTeleport.allowed(p), "operateur en survie : refuse");
            p.setGameMode(GameType.ADVENTURE);
            Gt.check(!WaypointTeleport.allowed(p), "operateur en aventure : refuse");
            p.setGameMode(GameType.CREATIVE);
            Gt.check(WaypointTeleport.allowed(p), "operateur en creatif : permis");
            p.setGameMode(GameType.SPECTATOR);
            Gt.check(WaypointTeleport.allowed(p), "operateur en spectateur : permis");
        } finally {
            p.setGameMode(GameType.SURVIVAL);
            s.server.getPlayerList().deop(p.getGameProfile());
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_30_tp_spot", timeoutTicks = 20)
    public static void p104_30_tp_jamais_sous_le_sol(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos col = h.absolutePos(new BlockPos(4, 1, 4));
        int min = h.getLevel().getMinBuildHeight();
        for (int wanted : new int[]{min, min + 1, 0, col.getY() - 3, col.getY() + 40, h.getLevel().getMaxBuildHeight() + 10}) {
            BlockPos spot = WaypointTeleport.safeSpot(h.getLevel(), col.getX(), wanted, col.getZ());
            Gt.check(spot != null, "aucune place pour un waypoint a " + wanted);
            int ground = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
            Gt.check(spot.getY() > min + 1, "waypoint a " + wanted + " : arrivee a -64 (" + spot.getY() + ")");
            Gt.check(spot.getY() >= ground, "waypoint a " + wanted + " : arrivee sous le sol (" + spot.getY() + " < " + ground + ")");
            Gt.check(h.getLevel().getBlockState(spot).getCollisionShape(h.getLevel(), spot).isEmpty()
                     && h.getLevel().getFluidState(spot).isEmpty(), "waypoint a " + wanted + " : pieds dans un bloc ou un liquide");
        }
        s.done();
    }
}
