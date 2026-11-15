package fr.pioneers.gametest;

import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.tech.bronze.autofoundry.FoundryVatBlock;
import fr.pioneers.core.common.block.Connections;
import fr.pioneers.tech.computing.ScreenBlock;
import fr.pioneers.tech.computing.ScreenWall;
import fr.pioneers.tech.forge.casting.HandBellowsBlock;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.NbtAnims;
import fr.pioneers.tech.industry.multi.NbtRoles;
import fr.pioneers.tech.industry.multi.PortBlock;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.tech.industry.multi.StructureFrameBlock;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiComputing;
import fr.pioneers.core.registries.PiForge;
import fr.pioneers.core.registries.PiIndustry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 26, GameTests) : retours de Guillaume du 09/11. Turbine : la colonne centrale de rotors est exigee (masquee a la formation,
 * structure defaite sans un rotor) ; port pose a la place d'une tole : accepte a la peau de tole (lot 30), d'une charpente : refuse ;
 * interface claire des machines de salle blanche ; ecrans de supervision en L : deux ecrans rectangulaires (le plus grand d'abord), cadre
 * entre eux ; cuve de fonderie : cuve en diagonale haute lue ; soufflet a main qui se tourne vers un feu de camp pose apres lui.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot26Tests {

    private PatchP104Lot26Tests() {
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_26_turbine", timeoutTicks = 200)
    public static void p104_26_turbine_exige_ses_rotors(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.STEAM_TURBINE);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "turbine : le plan (avec ses rotors) ne se forme pas");
            Direction front = tile.getBlockState().getValue(ControllerBlock.FACING);
            List<BlockPos> rotors = NbtAnims.stackedCells("steam_turbine", h.absolutePos(c), front);
            Gt.equal(7, rotors.size(), "turbine : 7 rotors dans la colonne centrale");
            for (BlockPos p : rotors) {
                BlockState st = h.getLevel().getBlockState(p);
                Gt.check(st.getBlock() instanceof StackedColumnBlock b && "turbine_rotor".equals(b.family) && st.getValue(StackedColumnBlock.HIDDEN),
                      "turbine : rotor absent ou pas masque en " + p);
            }
            h.getLevel().setBlock(rotors.get(3), Blocks.AIR.defaultBlockState(), 3);
            tile.requestCheck();
            h.runAfterDelay(3, () -> {
                Gt.check(!tile.formed(), "turbine formee sans sa colonne de rotors");
                Gt.equal(Structure.Error.MISSING_BLOCK, StructureMatcher.match(h.getLevel(), h.absolutePos(c), front, IndustryMachine.STEAM_TURBINE).error(),
                      "turbine sans rotor : bloc attendu");
                s.done();
            });
        });
    }

    /**
     * 1.0.4-beta (lot 30) : un port remplace de nouveau une TOLE (forme, peau de tole peinte), jamais une charpente (« port mal place »).
     */
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_26_ports", timeoutTicks = 200)
    public static void p104_26_port_sur_carcasse_ou_tole(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.CRUSHER);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "broyeur : le plan ne se forme pas");
            BlockPos sheet = null, frame = null;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, 0, -1), c.offset(4, 4, 8))) {
                Block b = h.getLevel().getBlockState(h.absolutePos(p)).getBlock();
                if (sheet == null && b instanceof SheetMetalBlock) {
                    sheet = p.immutable();
                } else if (frame == null && b instanceof StructureFrameBlock) {
                    frame = p.immutable();
                }
            }
            Gt.check(sheet != null && frame != null, "broyeur : tole ou charpente absente de la structure");
            BlockState port = PiIndustry.PORTS.get(PortBlock.Type.ITEM).getBlock().defaultBlockState();
            BlockState wasSheet = h.getBlockState(sheet), wasFrame = h.getBlockState(frame);
            h.setBlock(sheet, port);
            tile.requestCheck();
            BlockPos atSheet = sheet, atFrame = frame;
            h.runAfterDelay(3, () -> {
                Gt.check(tile.formed(), "broyeur : un port a la place d'une tole doit etre accepte (lot 30)");
                Gt.check(h.getBlockState(atSheet).getValue(Connections.ON_SHEET), "port sur une tole : peau de tole non peinte");
                h.setBlock(atSheet, wasSheet);
                h.setBlock(atFrame, port);
                tile.requestCheck();
                h.runAfterDelay(3, () -> {
                    Gt.check(!tile.formed(), "broyeur forme avec un port a la place d'une charpente");
                    Gt.equal(Structure.Error.PORT_NOT_HERE, StructureMatcher.match(h.getLevel(), h.absolutePos(c), tile.getBlockState().getValue(ControllerBlock.FACING),
                          IndustryMachine.CRUSHER).error(), "port sur une charpente : « port mal place »");
                    h.setBlock(atFrame, wasFrame);
                    tile.requestCheck();
                    h.runAfterDelay(3, () -> {
                        Gt.check(tile.formed(), "broyeur : la charpente remise, la structure ne se reforme pas");
                        s.done();
                    });
                });
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_26_cleanroom", timeoutTicks = 200)
    public static void p104_26_salle_blanche_interface_claire(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.PHOTOLITHOGRAPH);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "graveur optique : le plan ne se forme pas");
            Gt.check(GeneratedNbtShapes.of("photolithograph").cleanroom(), "graveur optique : pas une structure de salle blanche");
            Gt.equal(NbtRoles.CLEANROOM_GUI, tile.guiTier(), "graveur optique : interface de salle blanche");
            Gt.check(h.getBlockState(c).is(Connections.CLEANROOM), "controleur du graveur hors de la famille des parois de salle blanche");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_26_screens", timeoutTicks = 100)
    public static void p104_26_ecrans_en_L(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState screen = PiComputing.SUPERVISION_SCREEN.getBlock().defaultBlockState().setValue(ScreenBlock.FACING, Direction.NORTH);
        //vu de face (ecran tourne vers le nord) : la droite du spectateur est l'ouest
        BlockPos a = new BlockPos(4, 2, 4), b = a.west(), up = a.above();
        for (BlockPos p : new BlockPos[]{a, b, up}) {
            h.setBlock(p, screen);
        }
        h.runAfterDelay(2, () -> {
            Map<BlockPos, ScreenWall.Rect> walls = ScreenWall.partition(h.getLevel(), h.absolutePos(a), Direction.NORTH, null);
            Gt.equal(3, walls.size(), "ecrans en L : 3 dalles");
            ScreenWall.Rect low = walls.get(h.absolutePos(a)), top = walls.get(h.absolutePos(up));
            Gt.check(low != null && low.w() == 2 && low.h() == 1 && low.master().equals(h.absolutePos(a)), "ecrans en L : ecran du bas de 2 x 1 " + low);
            Gt.check(top != null && top.w() == 1 && top.h() == 1 && top != low, "ecrans en L : ecran du haut de 1 x 1 " + top);
            Gt.check(!h.getBlockState(a).getValue(ScreenBlock.UP) && !h.getBlockState(up).getValue(ScreenBlock.DOWN),
                  "ecrans en L : cadre entre les deux ecrans");
            Gt.check(h.getBlockState(a).getValue(ScreenBlock.RIGHT) && h.getBlockState(b).getValue(ScreenBlock.LEFT), "ecrans en L : ecran du bas d'un seul tenant");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_26_vat", timeoutTicks = 100)
    public static void p104_26_cuve_empilement_partiel(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState vat = PiBronze.FOUNDRY_VAT.getBlock().defaultBlockState();
        BlockPos m = new BlockPos(4, 2, 4);
        for (BlockPos p : new BlockPos[]{m.west(), m, m.east(), m.above()}) {
            h.setBlock(p, vat);
        }
        h.runAfterDelay(2, () -> {
            BlockState mid = h.getBlockState(m), east = h.getBlockState(m.east());
            Gt.check(mid.getValue(Connections.property(Direction.UP)), "cuve du milieu : cuve au-dessus");
            Gt.check(!mid.getValue(FoundryVatBlock.ABOVE.get(Direction.EAST)), "cuve du milieu : pas de cuve en diagonale haute a l'est (jupe)");
            Gt.check(east.getValue(FoundryVatBlock.ABOVE.get(Direction.WEST)), "cuve de l'est : cuve en diagonale haute a l'ouest");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_26_bellows", timeoutTicks = 100)
    public static void p104_26_soufflet_se_relie(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos p = new BlockPos(4, 2, 4);
        h.setBlock(p.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(p.east().below(), Blocks.STONE.defaultBlockState());
        h.setBlock(p, PiForge.HAND_BELLOWS.getBlock().defaultBlockState().setValue(HandBellowsBlock.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> {
            h.setBlock(p.east(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            h.runAfterDelay(2, () -> {
                Gt.equal(Direction.EAST, h.getBlockState(p).getValue(HandBellowsBlock.FACING), "soufflet : tourne vers le feu de camp pose apres lui");
                s.done();
            });
        });
    }
}
