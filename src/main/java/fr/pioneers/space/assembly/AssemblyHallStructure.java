package fr.pioneers.space.assembly;

import fr.pioneers.common.block.Connections;
import fr.pioneers.industry.multi.CasingBlock;
import fr.pioneers.industry.multi.IndustrialGlassBlock;
import fr.pioneers.industry.multi.IndustryStyle;
import fr.pioneers.industry.multi.PortBlock;
import fr.pioneers.industry.multi.SheetMetalBlock;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.industry.multi.StructureFrameBlock;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.registries.PiWorksMachines;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume) : validation des multiblocs d'assemblage ({@link AssemblyHall}) autour de leur controleur (une seule taille,
 * une seule orientation : celle de la facade du controleur). Le tier est celui de la piece la plus faible (carcasses, cadres, toles, verre).
 * A la formation, carcasses, verre, ports et controleur prennent le metier et le tier (comme un multibloc industriel).
 * En cas d'echec, {@link Result#error} donne la premiere case fautive (message a l'ecran du controleur).
 */
public final class AssemblyHallStructure {

    private AssemblyHallStructure() {
    }

    /**
     * Structure lue : formee ou non, tier (0..3), origine (centre du sol), avant du plan, ports, pieces a peindre, toutes les cases ;
     * premiere case fautive (null si formee) et role attendu.
     */
    public record Result(boolean formed, int tier, BlockPos origin, Direction forward, List<BlockPos> ports, List<BlockPos> members,
                         Set<BlockPos> cells, @Nullable BlockPos error, @Nullable AssemblyHall.Role expected) {
    }

    /** Avant du plan : la facade du controleur regarde le joueur (-z local). */
    public static Direction forward(Direction controllerFacing) {
        return controllerFacing.getOpposite();
    }

    public static BlockPos origin(AssemblyHall.Kind kind, BlockPos controller, Direction forward) {
        BlockPos c = AssemblyHall.controller(kind);
        return controller.subtract(AssemblyHall.at(BlockPos.ZERO, forward, c.getX(), c.getY(), c.getZ()));
    }

    public static Result check(Level level, AssemblyHall.Kind kind, BlockPos controller, Direction facing) {
        return check(Structure.View.of(level), kind, controller, facing);
    }

    /** API Multibloc (lot 6) : verification sur un monde reel ou factice (banc). */
    public static Result check(Structure.View level, AssemblyHall.Kind kind, BlockPos controller, Direction facing) {
        Direction forward = forward(facing);
        BlockPos origin = origin(kind, controller, forward);
        int tier = 3;
        List<BlockPos> ports = new ArrayList<>(), members = new ArrayList<>();
        Set<BlockPos> cells = new HashSet<>();
        BlockPos error = null;
        AssemblyHall.Role expected = null;
        for (Map.Entry<BlockPos, AssemblyHall.Role> e : AssemblyHall.cells(kind).entrySet()) {
            BlockPos l = e.getKey();
            BlockPos pos = AssemblyHall.at(origin, forward, l.getX(), l.getY(), l.getZ());
            cells.add(pos);
            if (error != null) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                error = pos;
                expected = e.getValue();
                continue;
            }
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            boolean ok = true;
            switch (e.getValue()) {
                case CONTROLLER -> ok = pos.equals(controller);
                case CASING -> {
                    if (block instanceof CasingBlock casing && !casing.scorched()) {
                        tier = Math.min(tier, casing.tier);
                        members.add(pos);
                    } else if (isPort(block)) {
                        ports.add(pos);
                    } else {
                        ok = false;
                    }
                }
                case FRAME -> {
                    if (block instanceof StructureFrameBlock frame) {
                        tier = Math.min(tier, frame.tier);
                    } else {
                        ok = false;
                    }
                }
                case SHEET -> {
                    if (block instanceof SheetMetalBlock sheet) {
                        tier = Math.min(tier, sheet.tier);
                    } else if (block instanceof IndustrialGlassBlock glass) {
                        tier = Math.min(tier, glass.tier);
                        members.add(pos);
                    } else if (isPort(block)) {
                        ports.add(pos);
                    } else {
                        ok = false;
                    }
                }
                case GLASS -> {
                    if (block instanceof IndustrialGlassBlock glass) {
                        tier = Math.min(tier, glass.tier);
                        members.add(pos);
                    } else {
                        ok = false;
                    }
                }
                case FREE -> ok = state.isAir() || state.canBeReplaced();
                case WORK -> ok = block == work(kind);
            }
            if (!ok) {
                error = pos;
                expected = e.getValue();
            }
        }
        return new Result(error == null, Math.max(0, tier), origin, forward, ports, members, cells, error, expected);
    }

    /** Ports industriels acceptes : objets et energie. */
    public static boolean isPort(Block block) {
        return block instanceof PortBlock port && (port.type == PortBlock.Type.ITEM || port.type == PortBlock.Type.ENERGY);
    }

    /** Bloc metier : borne de recharge des robots, ou dalle de lancement. */
    public static Block work(AssemblyHall.Kind kind) {
        return kind == AssemblyHall.Kind.ROBOTICS ? PiBlocks.CHARGE_PAD.getBlock() : PiBlocks.LAUNCH_PAD.getBlock();
    }

    /** Bloc conseille pour le plan projete (tier basique) ; null = case vide. */
    @Nullable
    public static Block suggested(AssemblyHall.Kind kind, AssemblyHall.Role role) {
        return switch (role) {
            case CASING -> PiIndustry.CASINGS.values().iterator().next().getBlock();
            case FRAME -> PiIndustry.FRAMES.values().iterator().next().getBlock();
            case SHEET -> PiIndustry.SHEETS.values().iterator().next().getBlock();
            case GLASS -> PiIndustry.GLASS.getBlock();
            case WORK -> work(kind);
            case CONTROLLER -> PiWorksMachines.HALLS.get(kind).getBlock();
            case FREE -> null;
        };
    }

    /** Habille la structure (metier, tier) ou la rend neutre ({@code formed} faux), sans prevenir les voisins. */
    public static void paint(Level level, AssemblyHall.Kind kind, Result result, boolean formed) {
        IndustryStyle style = formed ? kind.style : IndustryStyle.NEUTRAL;
        int t = formed ? result.tier() + 1 : 0;
        List<BlockPos> all = new ArrayList<>(result.members());
        all.addAll(result.ports());
        for (BlockPos p : all) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            BlockState next = state;
            if (next.hasProperty(IndustryStyle.STYLE) && next.getValue(IndustryStyle.STYLE) != style) {
                next = next.setValue(IndustryStyle.STYLE, style);
            }
            if (next.getBlock() instanceof IndustrialGlassBlock glass) {
                //0.20.28 : la vitre garde la couleur de son propre tier
                next = Connections.connect(next.setValue(IndustryStyle.TIER, glass.tier + 1), level, p, Connections.INDUSTRIAL_GLASS);
            } else if (next.hasProperty(IndustryStyle.TIER) && next.getValue(IndustryStyle.TIER) != t) {
                next = next.setValue(IndustryStyle.TIER, t);
            }
            if (next.getBlock() instanceof CasingBlock) {
                next = Connections.connect(next, level, p, Connections.INDUSTRIAL);
            }
            if (next != state) {
                level.setBlock(p, next, Block.UPDATE_CLIENTS);
            }
        }
    }
}
