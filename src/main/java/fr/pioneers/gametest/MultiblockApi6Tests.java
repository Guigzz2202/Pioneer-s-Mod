package fr.pioneers.gametest;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.api.multiblock.MultiblockValues;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.world.geology.drill.SteamDrillTile;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.nuclear.FissionCore;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Multibloc (lot 6, GameTests) : 1. le reacteur a fission tourne sur son COEUR DECLARE PAR LA FICHE ({@link MultiblockLogic} =
 * {@link FissionCore}) : forme, valeurs d'interface NOMMEES ({@link MultiblockValues#R_MARK}...), sauvegarde formee (cle {@code reactor})
 * identique a l'aller-retour ; 2. multiblocs maison : le plan tire de la forme ({@link HouseShapes}) du haut fourneau et de la foreuse a vapeur,
 * construit dans le monde, se FORME en jeu (meme regle statique que le banc), et la sauvegarde de la tile fait l'aller-retour a l'identique.
 * Gabarit {@code tall32}.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MultiblockApi6Tests {

    private MultiblockApi6Tests() {
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb6_reactor", timeoutTicks = 100)
    public static void mb6_reacteur_coeur_de_la_fiche(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.FISSION_REACTOR);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "le reacteur ne se forme pas");
            Gt.check(tile.logic() instanceof FissionCore, "coeur de la fiche : FissionCore attendu");
            UiValues values = tile.uiValues();
            Gt.check(values != null && values.index(MultiblockValues.R_MARK) >= 0, "valeur nommee mb_reactor_mark absente");
            Gt.equal(1L, values.get(values.index(MultiblockValues.R_MARK)), "generation du reacteur (valeur nommee)");
            CompoundTag tag = tile.saveWithoutMetadata();
            Gt.check(tag.contains("reactor"), "cle de sauvegarde du coeur (reactor) gardee");
            BlockEntity copy = BlockEntity.loadStatic(tile.getBlockPos(), tile.getBlockState(), tag.copy());
            Gt.check(copy instanceof ControllerTile, "relecture : pas un controleur");
            Gt.equal(tag, copy.saveWithoutMetadata(), "sauvegarde d'un reacteur forme : aller-retour");
            s.done();
        });
    }

    /** Construit un plan maison (variante 0) avance vers {@code forward}, dans le gabarit ; rend la case relative de l'ancre. */
    static BlockPos buildHouse(GameTestHelper h, Blueprint plan, Direction forward) {
        Map<BlockPos, ProjectedStructure.Ghost> layout = plan.layout(0, BlockPos.ZERO, forward);
        int minX = 0, minY = 0, minZ = 0;
        for (BlockPos p : layout.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
        }
        BlockPos anchor = new BlockPos(2 - minX, 1 - minY, 2 - minZ);
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : layout.entrySet()) {
            BlockState st = e.getValue().state();
            if (st == null) {
                continue;
            }
            if (st.getBlock() == plan.controller()) {
                if (st.hasProperty(MachineBlock.FACING)) {
                    st = st.setValue(MachineBlock.FACING, forward);
                } else if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, forward);
                }
            }
            h.setBlock(anchor.offset(e.getKey()), st);
        }
        return anchor;
    }

    /** Aller-retour de la sauvegarde d'une tile (identique). */
    static void roundTrip(BlockEntity tile, String what) {
        CompoundTag tag = tile.saveWithoutMetadata();
        BlockEntity copy = BlockEntity.loadStatic(tile.getBlockPos(), tile.getBlockState(), tag.copy());
        Gt.check(copy != null, what + " : relecture impossible");
        Gt.equal(tag, copy.saveWithoutMetadata(), what + " : sauvegarde, aller-retour");
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb6_blast", timeoutTicks = 200)
    public static void mb6_haut_fourneau_plan_de_la_forme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Direction forward = Direction.SOUTH;
        BlockPos anchor = buildHouse(h, HouseShapes.BLAST_FURNACE, forward);
        BlockPos c = anchor.relative(forward);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(c);
            Gt.check(be instanceof BlastFurnaceTile, "pas de haut fourneau en " + c);
            BlastFurnaceTile tile = (BlastFurnaceTile) be;
            Gt.check(BlastFurnaceTile.fault(fr.pioneers.tech.industry.multi.Structure.View.of(h.getLevel()), h.absolutePos(c), tile.facing()) == null,
                  "plan du haut fourneau : case fautive");
            Gt.check(tile.formed(), "le haut fourneau construit au plan ne se forme pas");
            roundTrip(tile, "haut fourneau forme");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb6_drill", timeoutTicks = 200)
    public static void mb6_foreuse_a_vapeur_plan_de_la_forme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos anchor = buildHouse(h, HouseShapes.STEAM_DRILL, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(anchor);
            Gt.check(be instanceof SteamDrillTile, "pas de foreuse en " + anchor);
            SteamDrillTile tile = (SteamDrillTile) be;
            Gt.check(SteamDrillTile.fault(fr.pioneers.tech.industry.multi.Structure.View.of(h.getLevel()), h.absolutePos(anchor), tile.facing()) == null,
                  "plan de la foreuse : case fautive");
            Gt.check(tile.formed(), "la foreuse construite au plan ne se forme pas");
            roundTrip(tile, "foreuse formee");
            s.done();
        });
    }
}
