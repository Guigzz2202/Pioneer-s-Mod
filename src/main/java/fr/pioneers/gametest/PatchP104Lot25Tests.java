package fr.pioneers.gametest;

import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.world.geology.drill.SteamDrillTile;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.multi.BigInterior;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.NbtAnims;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 25, GameTests) : MODELES ET ANIMATIONS des multiblocs. La machine interieure d'une halle (assembleur) est dessinee dans la
 * boite de la table (air seulement, entre les poteaux), plus dans la boite des cases interieures ; la turbine n'a plus l'ancien rotor
 * horizontal (le rotor de 2,5 blocs est dessine par le controleur) ; le train de forage de la foreuse et celui de la foreuse a vapeur sont
 * masques a la formation (le controleur les fait tourner) et de nouveau dessines a la rupture.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot25Tests {

    private PatchP104Lot25Tests() {
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_25_line", timeoutTicks = 200)
    public static void p104_25_machine_interieure_dans_sa_boite(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.ASSEMBLER);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "assembleur : la halle ne se forme pas");
            BigInterior.View v = tile.bigView();
            Gt.check(v != null && v.kind() == BigInterior.Kind.ASSEMBLY, "assembleur : pas de chaine dessinee");
            Direction front = tile.getBlockState().getValue(ControllerBlock.FACING);
            BigInterior.View want = NbtAnims.view(h.absolutePos(c), front, GeneratedNbtShapes.of("assembler"),
                  NbtAnims.of("assembler").model(), v.tint());
            Gt.check(v.min().equals(want.min()) && v.max().equals(want.max()), "assembleur : boite de la chaine " + v.min() + " " + v.max());
            for (BlockPos p : BlockPos.betweenClosed(v.min(), v.max())) {
                Gt.check(h.getLevel().getBlockState(p).isAir(), "assembleur : la chaine traverse un bloc en " + p);
            }
            Gt.check(v.stands().length == 3, "assembleur : portiques aux trois paires de poteaux");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_25_turbine", timeoutTicks = 200)
    public static void p104_25_turbine_sans_ancien_rotor(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.STEAM_TURBINE);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "turbine : ne se forme pas");
            Gt.check(BigInterior.kind("steam_turbine") == null && tile.bigView() == null, "turbine : l'ancien rotor horizontal est encore dessine");
            Gt.check(NbtAnims.of("steam_turbine") != null && !NbtAnims.of("steam_turbine").spins().isEmpty(), "turbine : pas de rotor tournant");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_25_drill", timeoutTicks = 200)
    public static void p104_25_foreuse_train_masque_puis_rendu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.DRILL);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "foreuse : ne se forme pas");
            Direction front = tile.getBlockState().getValue(ControllerBlock.FACING);
            List<BlockPos> cells = NbtAnims.stackedCells("drill", h.absolutePos(c), front);
            Gt.check(cells.size() == 7, "foreuse : 7 cases de train attendues, " + cells.size());
            for (BlockPos p : cells) {
                BlockState st = h.getLevel().getBlockState(p);
                Gt.check(st.getBlock() instanceof StackedColumnBlock && st.getValue(StackedColumnBlock.HIDDEN), "foreuse : train pas masque en " + p);
            }
            //un morceau du train ote : la structure se defait, le reste du train est de nouveau dessine par le monde
            h.getLevel().destroyBlock(cells.get(3), false);
            tile.requestCheck();
            h.runAfterDelay(3, () -> {
                Gt.check(!tile.formed(), "foreuse formee sans son train");
                for (BlockPos p : cells) {
                    BlockState st = h.getLevel().getBlockState(p);
                    Gt.check(!(st.getBlock() instanceof StackedColumnBlock) || !st.getValue(StackedColumnBlock.HIDDEN),
                          "foreuse defaite : train encore masque en " + p);
                }
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_25_steam_drill", timeoutTicks = 200)
    public static void p104_25_foreuse_a_vapeur_train_masque(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApi6Tests.buildHouse(h, HouseShapes.STEAM_DRILL, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(c);
            Gt.check(be instanceof SteamDrillTile, "pas de foreuse a vapeur en " + c);
            SteamDrillTile drill = (SteamDrillTile) be;
            Gt.check(drill.formed(), "la foreuse a vapeur ne se forme pas");
            List<BlockPos> cells = HouseNbt.positions(drill.getBlockPos(), drill.facing(), SteamDrillTile.SHAPE, "D");
            for (BlockPos p : cells) {
                BlockState st = h.getLevel().getBlockState(p);
                Gt.check(st.getBlock() instanceof StackedColumnBlock && st.getValue(StackedColumnBlock.HIDDEN),
                      "foreuse a vapeur : train en bronze pas masque en " + p);
            }
            h.getLevel().destroyBlock(HouseNbt.at(drill.getBlockPos(), drill.facing(), HouseNbt.first(SteamDrillTile.SHAPE, 'c')), false);
            drill.requestCheck();
            h.runAfterDelay(5, () -> {
                Gt.check(!drill.formed(), "foreuse a vapeur formee sans sa couronne");
                for (BlockPos p : cells) {
                    BlockState st = h.getLevel().getBlockState(p);
                    Gt.check(!(st.getBlock() instanceof StackedColumnBlock) || !st.getValue(StackedColumnBlock.HIDDEN),
                          "foreuse a vapeur defaite : train encore masque en " + p);
                }
                s.done();
            });
        });
    }
}
