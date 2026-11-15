package fr.pioneers.tech.industry.multi;

import fr.pioneers.core.Directions;
import fr.pioneers.tech.industry.IndustryMachine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Multibloc sans forme imposee (plate-forme offshore) : l'ensemble des pieces industrielles reliees au controleur (carcasses, verre, ports,
 * modules du bon type), quelle que soit leur disposition. Le joueur construit le pont qu'il veut, avec les blocs decoratifs qu'il veut autour
 * (ils ne comptent pas). Chaque module (colonne montante) compte s'il plonge dans l'eau jusqu'au fond : au moins {@link #MIN_WATER} blocs
 * d'eau sous lui puis un fond solide a moins de {@link #MAX_DEPTH} blocs. Tier = plus faible carcasse ; parallelisme = 2 par colonne valide.
 * 0.20.37 : la plate-forme descend elle-meme ses tubes ({@link OffshoreRisers}) ; une colonne ne produit qu'une fois son tube au fond.
 */
public final class FreeformStructure {

    /**
     * Pieces au plus (pont compris), cote maximal de l'emprise (blocs) et hauteur au plus autour du controleur. 0.20.37 (Guillaume : « taille max
     * de 64x64, ports, controleur et colonnes ou on veut ») : l'emprise se mesure sur tout le pont (le controleur peut etre dans un coin).
     */
    public static final int MAX_PARTS = 16_384, MAX_SIZE = 64, MAX_SPAN = MAX_SIZE - 1, MAX_RISE = 24;
    public static final int MIN_WATER = 3, MAX_DEPTH = 128;

    private FreeformStructure() {
    }

    public static Structure.Result validate(Level level, BlockPos controller, IndustryMachine machine) {
        return validate(Structure.View.of(level), controller, machine);
    }

    /** API Multibloc (lot 5) : sur une vue du monde (le monde du jeu, ou le monde factice du banc). */
    public static Structure.Result validate(Structure.View level, BlockPos controller, IndustryMachine machine) {
        Set<BlockPos> members = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        members.add(controller);
        queue.add(controller);
        int tier = 99, casings = 0;
        List<BlockPos> ports = new ArrayList<>(), modules = new ArrayList<>();
        BlockPos firstDry = null;
        int minX = controller.getX(), minY = controller.getY(), minZ = controller.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.state(pos);
            Block block = state.getBlock();
            if (block instanceof CasingBlock casing) {
                if (casing.scorched()) {
                    return Structure.Result.fail(Structure.Error.SCORCHED, pos, controller);
                }
                tier = Math.min(tier, casing.tier);
                casings++;
            } else if (block instanceof PortBlock) {
                ports.add(pos);
            } else if (block instanceof ControllerBlock && !pos.equals(controller)) {
                return Structure.Result.fail(Structure.Error.OTHER_CONTROLLER, pos, controller);
            } else if (block instanceof ModuleBlock || riser(machine, state)) {
                if (block instanceof ModuleBlock module && machine.module != null && module.module != machine.module) {
                    return Structure.Result.fail(Structure.Error.WRONG_MODULE, pos, controller);
                }
                if (reachesSeabed(level, pos)) {
                    modules.add(pos);
                } else if (firstDry == null) {
                    firstDry = pos;
                }
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            for (Direction d : Directions.ALL) {
                BlockPos next = pos.relative(d);
                if (members.contains(next) || !level.loaded(next) || Math.abs(next.getX() - controller.getX()) > MAX_SPAN
                    || Math.abs(next.getZ() - controller.getZ()) > MAX_SPAN || Math.abs(next.getY() - controller.getY()) > MAX_RISE) {
                    continue;
                }
                BlockState ns = level.state(next);
                if (fr.pioneers.api.multiblock.StructureMatcher.isFrame(ns) || ns.getBlock() instanceof ModuleBlock || riser(machine, ns)) {
                    if (members.size() >= MAX_PARTS) {
                        return Structure.Result.fail(Structure.Error.TOO_BIG, next, controller);
                    }
                    members.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        if (casings == 0) {
            return Structure.Result.fail(Structure.Error.NO_CASING, null, controller);
        }
        if (maxX - minX + 1 > MAX_SIZE || maxZ - minZ + 1 > MAX_SIZE) {
            //0.20.37 : emprise de plus de 64 x 64
            BlockPos far = new BlockPos(maxX - minX + 1 > MAX_SIZE ? (maxX - controller.getX() > controller.getX() - minX ? maxX : minX) : controller.getX(),
                  controller.getY(), maxZ - minZ + 1 > MAX_SIZE ? (maxZ - controller.getZ() > controller.getZ() - minZ ? maxZ : minZ) : controller.getZ());
            return Structure.Result.fail(Structure.Error.TOO_BIG, far, controller);
        }
        if (modules.isEmpty()) {
            return Structure.Result.fail(firstDry != null ? Structure.Error.DRY_RISER : Structure.Error.NO_MODULE, firstDry, controller);
        }
        int count = members.size();
        return new Structure.Result(Structure.Error.NONE, null, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), tier, modules.size(),
              count, count, List.copyOf(ports), maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, Set.copyOf(members), List.copyOf(modules), 0);
    }

    /** 1.0.4-beta (lot 23) : colonne montante de la plate-forme = pompe a colonne (role du module, par classe ou par tag). */
    private static boolean riser(IndustryMachine machine, BlockState state) {
        return machine.module != null && fr.pioneers.api.multiblock.Role.module(machine.module).accepts(state);
    }

    /** La colonne sous ce module plonge-t-elle dans l'eau jusqu'a un fond solide ? */
    public static boolean reachesSeabed(Level level, BlockPos module) {
        return reachesSeabed(Structure.View.of(level), module);
    }

    /** Lot 5 : sur une vue du monde. */
    public static boolean reachesSeabed(Structure.View level, BlockPos module) {
        BlockPos.MutableBlockPos p = module.mutable();
        int water = 0;
        for (int i = 0; i < MAX_DEPTH; i++) {
            p.move(Direction.DOWN);
            if (p.getY() < level.minY() || !level.loaded(p)) {
                return false;
            }
            if (level.water(p)) {
                water++;
                continue;
            }
            return water >= MIN_WATER && level.sturdy(p);
        }
        return false;
    }
}
