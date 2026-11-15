package fr.pioneers.logistics.naval;

import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registries.PiNaval;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.20 : plan projete du port (Maj + clic droit main vide sur la capitainerie) : bassin en U a la taille minimale du navire de chaque
 * tier (largeur 3 / 5 / 5 / 5, bras de 9 / 11 / 13 / 17), capitainerie au milieu du fond, une grue et un poste de soutage suggeres sur les
 * bras (tout equipement convient a la place d'une dalle). Le plan est tourne vers le regard du joueur : le bassin devant lui.
 */
public final class HarborBlueprint implements Blueprint {

    private final Block controller;
    private final int tier;

    private HarborBlueprint(Block controller, int tier) {
        this.controller = controller;
        this.tier = tier;
    }

    /** Fournisseur pour {@code Projections.addProvider}. */
    public static List<? extends Blueprint> forBlock(Block block) {
        if (block instanceof HarborBlock h) {
            return List.of(new HarborBlueprint(block, h.tier()));
        }
        return List.of();
    }

    /** 0.20.22 : un seul plan, celui du tier de la capitainerie : Maj + clic droit l'affiche, un second le masque. */
    @Override
    public int variants() {
        return 1;
    }

    /** Demi-largeur du bassin pour ce tier (voir {@link HarborLayout#halfWidth}). */
    public static int halfWidth(int tier) {
        return HarborLayout.halfWidth(tier);
    }

    /** Longueur des bras pour ce tier (voir {@link HarborLayout#armLength}). */
    public static int armLength(int tier) {
        return HarborLayout.armLength(tier);
    }

    /** 0.20.23 : le plan projete est exactement celui que la capitainerie verifie ({@link HarborLayout}). */
    @Override
    public Map<BlockPos, ProjectedStructure.Ghost> layout(int variant, BlockPos anchor, Direction forward) {
        Map<BlockPos, ProjectedStructure.Ghost> out = new LinkedHashMap<>();
        int t = Math.min(3, tier);
        int mid = (HarborLayout.armLength(t) + 1) / 2;
        Set<Block> members = new HashSet<>();
        members.add(PiNaval.QUAY.getBlock());
        PiNaval.EQUIPMENT.values().forEach(e -> members.add(e.getBlock()));
        for (HarborLayout.Cell cell : HarborLayout.cells(t)) {
            BlockPos p = HarborLayout.at(anchor, forward, cell.x(), cell.z());
            switch (cell.role()) {
                case CONTROLLER -> {
                    BlockState head = controller.defaultBlockState();
                    if (head.hasProperty(MachineBlock.FACING)) {
                        head = head.setValue(MachineBlock.FACING, forward.getOpposite());
                    }
                    out.put(p, new ProjectedStructure.Ghost(head, Set.of()));
                }
                case QUAY -> {
                    //une grue a mi-bras a droite, un poste de soutage en face (tout equipement convient a la place d'une dalle)
                    Block b = PiNaval.QUAY.getBlock();
                    if (!cell.back() && cell.z() == mid) {
                        b = PiNaval.EQUIPMENT.get(cell.x() > 0 ? HarborEquipment.CRANE : HarborEquipment.BUNKER).getBlock();
                    }
                    put(out, members, p, b, HarborLayout.facing(cell, forward));
                }
                default -> {
                }
            }
            if (cell.role() != HarborLayout.Role.WATER) {
                //0.20.25 : mur du quai sur HarborLayout.HEIGHT blocs (sous la capitainerie aussi)
                for (int dy = 1; dy < HarborLayout.HEIGHT; dy++) {
                    BlockState wall = PiNaval.QUAY.getBlock().defaultBlockState().setValue(QuayBlock.COVERED, true);
                    out.put(p.below(dy), new ProjectedStructure.Ghost(wall, members));
                }
            }
        }
        return out;
    }

    private static void put(Map<BlockPos, ProjectedStructure.Ghost> out, Set<Block> members, BlockPos p, Block b, Direction facing) {
        BlockState st = b.defaultBlockState();
        if (st.hasProperty(QuayBlock.FACING)) {
            st = st.setValue(QuayBlock.FACING, facing);
        }
        out.put(p, new ProjectedStructure.Ghost(st, members));
    }

    @Override
    public Block controller() {
        return controller;
    }

    @Override
    public String getTranslationKey() {
        return "projector.pioneers.harbor";
    }

    @Nullable
    @Override
    public String getVariantKey(int variant) {
        return "projector.pioneers.harbor.size." + Math.min(3, tier);
    }
}
