package fr.pioneers.gametest;

import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.logistics.naval.HarborLayout;
import fr.pioneers.logistics.robotics.hub.RoboticHubValidator;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Multibloc (lot 8a, GameTests) : les MULTIBLOCS MAISON sur le CYCLE DE VIE COMMUN ({@link MultiblockCore.House}). Pour chaque maison qui
 * tient dans le gabarit {@code tall32} : son plan (tire de la forme, {@link HouseShapes}) est construit, la structure se FORME par le cycle
 * commun (facade du controleur cherchee dans les 4 orientations), la sauvegarde de la tile fait l'aller-retour a l'identique (aucune cle de
 * sauvegarde ne depend de la formation), les cases sont inscrites au registre des hotes (les ports y trouvent leur hote :
 * {@link MultiblockPort#house}), puis une piece retiree ROMPT la structure avec l'ERREUR COMMUNE (quoi, ou, attendu du plan) et la reposer la
 * REFORME. Le simulateur spatial (globe de 25 de haut) ne tient pas dans le gabarit : son cycle est verifie par le banc des multiblocs.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MultiblockApi8aTests {

    private MultiblockApi8aTests() {
    }

    /** Construit le plan {@code index} de la maison {@code key} (variante 0) ; rend l'ancre relative et le monde pose (relatif). */
    private static Map<BlockPos, BlockState> build(GameTestHelper h, Blueprint plan, Direction forward, BlockPos[] anchorOut) {
        Map<BlockPos, ProjectedStructure.Ghost> layout = plan.layout(0, BlockPos.ZERO, forward);
        int minX = 0, minY = 0, minZ = 0;
        for (BlockPos p : layout.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
        }
        BlockPos anchor = new BlockPos(2 - minX, 3 - minY, 2 - minZ);
        Map<BlockPos, BlockState> placed = new TreeMap<>();
        for (Map.Entry<BlockPos, ProjectedStructure.Ghost> e : layout.entrySet()) {
            BlockState st = e.getValue().state();
            if (st != null) {
                h.setBlock(anchor.offset(e.getKey()), st);
                placed.put(anchor.offset(e.getKey()), st);
            }
        }
        anchorOut[0] = anchor;
        return placed;
    }

    /** Controleur (ou maitre) maison de la case relative, ou null. */
    private static MultiblockCore.House.Holder holder(GameTestHelper h, BlockPos rel) {
        BlockEntity be = h.getBlockEntity(rel);
        return be instanceof MultiblockCore.House.Holder holder ? holder : null;
    }

    /** Forme la maison du controleur {@code c} : essaie les 4 facades (cycle commun, verification immediate) ; vrai si formee. */
    private static boolean form(GameTestHelper h, BlockPos c) {
        BlockState st = h.getBlockState(c);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (st.hasProperty(MachineBlock.FACING)) {
                h.getLevel().setBlock(h.absolutePos(c), st.setValue(MachineBlock.FACING, d), 2);
            } else if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                h.getLevel().setBlock(h.absolutePos(c), st.setValue(BlockStateProperties.HORIZONTAL_FACING, d), 2);
            }
            MultiblockCore.House.Holder holder = holder(h, c);
            if (holder != null && holder.house().checkNow()) {
                return true;
            }
            if (!st.hasProperty(MachineBlock.FACING) && !st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                break;
            }
        }
        return false;
    }

    /**
     * Scenario commun : formation, aller-retour de sauvegarde, registre des hotes, rupture avec erreur commune, reformation.
     *
     * @param controllerRel case du controleur (ou du maitre) relative a l'ancre ; null : la case du bloc controleur du plan
     * @param remove        case retiree relative a l'ancre ; null : la premiere piece du plan (ordre des cases) hors controleur
     * @param registry      la maison inscrit-elle des cases au registre des hotes ?
     */
    private static void scenario(GameTestHelper h, String key, int planIndex, BlockPos controllerRel, BlockPos remove, boolean registry, boolean water) {
        Gt.Scope s = Gt.begin(h);
        HouseShapes.HouseShape shape = null;
        for (HouseShapes.HouseShape sh : HouseShapes.all()) {
            if (sh.key().equals(key)) {
                shape = sh;
            }
        }
        Gt.check(shape != null, "fiche maison " + key + " absente");
        Blueprint plan = shape.plans().get(planIndex);
        BlockPos[] a = new BlockPos[1];
        Map<BlockPos, BlockState> placed = build(h, plan, Direction.SOUTH, a);
        BlockPos anchor = a[0];
        if (water) {
            //Port : bassin en eau (cases WATER du plan, a la hauteur de la capitainerie)
            for (HarborLayout.Cell cell : HarborLayout.cells(planIndex)) {
                if (cell.role() == HarborLayout.Role.WATER) {
                    BlockPos c = HarborLayout.at(anchor, Direction.SOUTH, cell.x(), cell.z());
                    for (int d = 0; d < HarborLayout.WATER_DEPTH; d++) {
                        h.setBlock(c.below(d), Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }
        BlockPos c = controllerRel;
        if (c == null) {
            for (Map.Entry<BlockPos, BlockState> e : placed.entrySet()) {
                if (e.getValue().getBlock() == plan.controller()) {
                    c = e.getKey();
                }
            }
        } else {
            c = anchor.offset(controllerRel);
        }
        BlockPos controller = c;
        Gt.check(controller != null, key + " : controleur introuvable dans le plan");
        h.runAfterDelay(10, () -> {
            Gt.check(holder(h, controller) != null, key + " : la tile du controleur ne porte pas le cycle commun");
            Gt.check(form(h, controller), key + " : le plan construit ne se forme pas par le cycle commun ("
                                          + holder(h, controller).house().legacyError() + ")");
            MultiblockCore.House.Holder holder = holder(h, controller);
            MultiblockApi6Tests.roundTrip((BlockEntity) holder, key + " forme");
            BlockPos cut = remove == null ? null : anchor.offset(remove);
            if (cut == null) {
                for (BlockPos p : placed.keySet()) {
                    if (!p.equals(controller) && placed.get(p).getBlock() != plan.controller()) {
                        cut = p;
                        break;
                    }
                }
            }
            if (registry) {
                Gt.check(MultiblockPort.house(h.getLevel(), h.absolutePos(cut), MultiblockCore.House.Holder.class) == holder,
                      key + " : piece absente du registre des hotes");
            }
            BlockState was = h.getBlockState(cut);
            h.setBlock(cut, Blocks.AIR.defaultBlockState());
            holder.house().checkNow();
            Gt.check(!holder.house().formed(), key + " : forme sans la piece " + cut);
            Gt.check(holder.house().error() != null, key + " : erreur commune (quoi) absente");
            Gt.check(holder.house().expected() != null, key + " : erreur commune (attendu du plan) absente");
            Gt.check(!registry || MultiblockPort.house(h.getLevel(), h.absolutePos(cut), MultiblockCore.House.Holder.class) == null,
                  key + " : registre des hotes garde une structure rompue");
            h.setBlock(cut, was);
            Gt.check(holder.house().checkNow(), key + " : ne se reforme pas apres la piece reposee");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_stabilizer", timeoutTicks = 200)
    public static void mb8a_stabilisateur(GameTestHelper h) {
        //Sans noyau : le controleur suit la chambre (cases inscrites par le noyau, pas par lui)
        scenario(h, "space_stabilizer_controller", 0, null, null, false, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_absorber", timeoutTicks = 200)
    public static void mb8a_absorbeur(GameTestHelper h) {
        scenario(h, "solar_absorber_controller", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_greenhouse", timeoutTicks = 200)
    public static void mb8a_serre(GameTestHelper h) {
        scenario(h, "greenhouse_controller", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_garage", timeoutTicks = 200)
    public static void mb8a_garage(GameTestHelper h) {
        scenario(h, "mining_outpost_controller", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_drone_bay", timeoutTicks = 200)
    public static void mb8a_baie_de_drones(GameTestHelper h) {
        scenario(h, "drone_bay", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_launch", timeoutTicks = 200)
    public static void mb8a_complexe_de_lancement(GameTestHelper h) {
        scenario(h, "launch_pad_core", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_hall_robotics", timeoutTicks = 200)
    public static void mb8a_baie_d_assemblage(GameTestHelper h) {
        scenario(h, "robotic_assembly_bay", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_hall_rocket", timeoutTicks = 200)
    public static void mb8a_hall_d_integration(GameTestHelper h) {
        scenario(h, "rocket_integration_hall", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_harbor", timeoutTicks = 200)
    public static void mb8a_port(GameTestHelper h) {
        scenario(h, "harbor", 0, null, null, true, true);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_boiler", timeoutTicks = 200)
    public static void mb8a_chaudiere(GameTestHelper h) {
        //Forme libre : sans le premier corps (au-dessus du foyer), plus de chaudiere
        scenario(h, "firebox", 0, BlockPos.ZERO, new BlockPos(0, 1, 0), true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_blast", timeoutTicks = 200)
    public static void mb8a_haut_fourneau(GameTestHelper h) {
        scenario(h, "blast_furnace", 0, null, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_drill", timeoutTicks = 200)
    public static void mb8a_foreuse(GameTestHelper h) {
        scenario(h, "steam_drill", 0, BlockPos.ZERO, null, true, false);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_mb8a_hub", timeoutTicks = 200)
    public static void mb8a_hub_robotique(GameTestHelper h) {
        //Le maitre est le coin bas du cube (forme par la file des pieces) ; son cycle commun le tient ensuite
        int r = RoboticHubValidator.WIDTH / 2;
        scenario(h, "robotic_hub", 0, new BlockPos(-r, 0, -r), null, true, false);
    }
}
