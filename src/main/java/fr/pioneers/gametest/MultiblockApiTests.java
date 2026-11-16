package fr.pioneers.gametest;

import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.IndustrialBlueprint;
import fr.pioneers.tech.industry.multi.ModuleBlock;
import fr.pioneers.tech.industry.multi.Structure;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Multibloc (lot 5, GameTests) : un multibloc de CHAQUE FAMILLE (procede, generateur, puits, pompe, foreuse, cuve, reacteur, fusion,
 * calcul, plate-forme offshore) construit dans le monde selon son plan (celui de la projection et de JEI) : il se FORME (cycle de vie commun
 * {@link MultiblockCore}), sa sauvegarde FORMEE fait l'aller-retour a l'identique (reservoir rempli), il se ROMPT quand une carcasse est
 * cassee (erreur commune : la case cassee) et se REFORME quand elle revient, et il se reforme apres RELECTURE de sa sauvegarde (reprise
 * apres chargement : la formation n'est jamais sauvee, elle est revalidee). Gabarit {@code tall32} (32 x 24 x 32).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MultiblockApiTests {

    private MultiblockApiTests() {
    }

    /** Construit le plan (variante 0) du multibloc, controleur facade au nord, tout dans le gabarit ; rend la case relative du controleur. */
    static BlockPos build(GameTestHelper h, IndustryMachine m) {
        Direction front = Direction.NORTH, forward = front.getOpposite();
        Map<BlockPos, ProjectedStructure.Ghost> plan = IndustrialBlueprint.of(m).layout(0, BlockPos.ZERO, forward);
        int minX = 0, minY = 0, minZ = 0;
        for (BlockPos p : plan.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
        }
        //Forme libre : 4 blocs d'eau et un fond de pierre sous chaque colonne montante
        BlockPos c = new BlockPos(1 - minX, (m.freeform ? 6 : 1) - minY, 1 - minZ);
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : plan.entrySet()) {
            BlockState s = e.getValue().state();
            BlockPos rel = c.offset(e.getKey());
            if (s == null) {
                continue;
            }
            if (s.hasProperty(MachineBlock.FACING) && !(s.getBlock() instanceof ModuleBlock)) {
                if (rel.equals(c)) {
                    s = s.setValue(MachineBlock.FACING, front);
                }
            }
            h.setBlock(rel, s);
            //1.0.4-beta (lot 23) : les colonnes montantes sont des pompes a colonne (bloc empilable), plus des modules
            if (m.freeform && (s.getBlock() instanceof ModuleBlock || s.getBlock() instanceof StackedColumnBlock)) {
                for (int d = 1; d <= 4; d++) {
                    h.setBlock(rel.below(d), Blocks.WATER.defaultBlockState());
                }
                h.setBlock(rel.below(5), Blocks.STONE.defaultBlockState());
            }
        }
        return c;
    }

    static ControllerTile controller(GameTestHelper h, BlockPos c) {
        BlockEntity be = h.getBlockEntity(c);
        Gt.check(be instanceof ControllerTile, "pas de controleur en " + c);
        return (ControllerTile) be;
    }

    /** Scenario commun : formation, sauvegarde formee, rupture et reformation, relecture. */
    static void family(GameTestHelper h, IndustryMachine m) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = build(h, m);
        ControllerTile tile = controller(h, c);
        //1.0.4-beta (lot 24) : structure de Guillaume sans piece a tier (salle blanche) : tier fixe
        fr.pioneers.tech.industry.multi.ShapeTemplate t = m.freeform ? null : fr.pioneers.tech.industry.multi.ShapeTemplate.of(m).get(0);
        int tier0 = t != null && t.nbt && t.fixedTier >= 0 ? t.fixedTier : 0;
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), m.multiId() + " : le plan ne se forme pas (" + tile.guiValue(5) + ")");
            Gt.equal(tier0, tile.tier(), "tier du plan en carcasses MKI");
            //Sauvegarde FORMEE : reservoir de caloporteur rempli, aller-retour identique
            tile.coolantTank().setFluid(new FluidStack(Fluids.WATER, 1_234));
            CompoundTag tag = tile.saveWithoutMetadata();
            BlockEntity copy = Gt.reloadTile(tile);
            Gt.check(copy instanceof ControllerTile, "relecture : pas un controleur");
            Gt.equal(tag, copy.saveWithoutMetadata(), "sauvegarde d'un controleur forme : aller-retour");
            Gt.equal(1_234, ((ControllerTile) copy).coolantTank().getFluidAmount(), "caloporteur relu");
            //Rupture : une carcasse cassee
            BlockPos casing = null;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-30, -2, -30), c.offset(30, 22, 30))) {
                BlockPos abs = h.absolutePos(p);
                //1.0.4-beta (lot 24) : une carcasse, ou une paroi de salle blanche (structures de Guillaume sans carcasse)
                net.minecraft.world.level.block.Block b = h.getLevel().isLoaded(abs) ? h.getLevel().getBlockState(abs).getBlock() : null;
                boolean piece = b instanceof CasingBlock cb && !cb.scorched() || b instanceof fr.pioneers.tech.industry.construction.CleanroomPanelBlock;
                if (!p.equals(c) && piece && tile.structure() != null && tile.structure().isPart(abs)) {
                    casing = p.immutable();
                    break;
                }
            }
            Gt.check(casing != null, "aucune carcasse dans la structure");
            BlockPos broken = casing;
            BlockState was = h.getBlockState(broken);
            h.getLevel().destroyBlock(h.absolutePos(broken), false);
            tile.requestCheck();
            Gt.later(h, 3, () -> {
                Gt.check(!tile.formed(), "toujours forme sans une carcasse");
                if (!m.freeform) {
                    Gt.equal(h.absolutePos(broken), tile.errorAt(), "erreur commune : la case de la carcasse cassee");
                }
                h.setBlock(broken, was);
                tile.requestCheck();
                Gt.later(h, 3, () -> {
                    Gt.check(tile.formed(), "ne se reforme pas apres la carcasse reposee");
                    //Reprise apres chargement : sauvegarde relue dans un controleur repose, revalidee au premier tick
                    CompoundTag saved = tile.saveWithoutMetadata();
                    BlockState state = h.getBlockState(c);
                    h.setBlock(c, Blocks.AIR.defaultBlockState());
                    h.setBlock(c, state);
                    ControllerTile again = controller(h, c);
                    again.load(saved);
                    Gt.later(h, 3, () -> {
                        Gt.check(again.formed(), "ne se reforme pas apres relecture de sa sauvegarde");
                        Gt.equal(1_234, again.coolantTank().getFluidAmount(), "caloporteur apres relecture");
                        Gt.equal(Structure.Error.NONE.ordinal(), (int) again.guiValue(5), "aucune erreur apres relecture");
                        s.done();
                    });
                });
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_process", timeoutTicks = 100)
    public static void mb_procede(GameTestHelper h) {
        family(h, IndustryMachine.CRYOGENIC_UNIT);          // 1.0.4-beta (lot 13) : l'echangeur a plaques est fondu dans l'Unite cryogenique
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_generator", timeoutTicks = 100)
    public static void mb_generateur(GameTestHelper h) {
        family(h, IndustryMachine.GAS_TURBINE);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_well", timeoutTicks = 100)
    public static void mb_puits(GameTestHelper h) {
        family(h, IndustryMachine.PUMPJACK);                // 1.0.4-beta (lot 13) : le puits de saumure est fondu dans le chevalet de pompage
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_pump", timeoutTicks = 100)
    public static void mb_pompe(GameTestHelper h) {
        family(h, IndustryMachine.FLUID_PUMP);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_drill", timeoutTicks = 100)
    public static void mb_foreuse(GameTestHelper h) {
        family(h, IndustryMachine.DRILL);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_tank", timeoutTicks = 100)
    public static void mb_cuve(GameTestHelper h) {
        family(h, IndustryMachine.STORAGE_TANK);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_reactor", timeoutTicks = 100)
    public static void mb_reacteur(GameTestHelper h) {
        family(h, IndustryMachine.FISSION_REACTOR);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_fusion", timeoutTicks = 100)
    public static void mb_fusion(GameTestHelper h) {
        family(h, IndustryMachine.STELLARATOR);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_compute", timeoutTicks = 100)
    public static void mb_calcul(GameTestHelper h) {
        family(h, IndustryMachine.DATA_CENTER);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb_offshore", timeoutTicks = 100)
    public static void mb_plateforme(GameTestHelper h) {
        family(h, IndustryMachine.OFFSHORE_PLATFORM);
    }
}
