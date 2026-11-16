package fr.pioneers.space.base;

import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.core.common.block.Connections;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.IndustrialGlassBlock;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.tech.industry.multi.StructureFrameBlock;
import fr.pioneers.space.base.PiBaseMachines;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Validation des bases spatiales construites ({@link BaseLayout}) : on essaie les 4 tailles, de la plus grande a la plus petite, autour du
 * controleur. Le tier de la base est celui de sa piece la plus faible (carcasses, cadres, toles). A la formation, carcasses et verres
 * prennent le metier de la base et son tier (comme un multibloc industriel).
 */
public final class BaseStructure {

    private BaseStructure() {
    }

    /**
     * Base formee : taille (0..3), tier (0..3), origine du plan (centre du sol), direction « avant » du plan, cases fonctionnelles
     * (bacs, docks, aires) et ports de la base, membres a peindre.
     */
    public record Result(BaseLayout.Kind kind, int size, int tier, BlockPos origin, Direction forward, List<BlockPos> functional, BlockPos pad,
                         List<BlockPos> ports, List<BlockPos> members) {

        /** Position monde d'une case locale. */
        public BlockPos at(int x, int y, int z) {
            return BaseStructure.at(origin, forward, x, y, z);
        }

        /** Direction monde de la porte du garage (vers le joueur, -z local). */
        public Direction front() {
            return forward.getOpposite();
        }
    }

    /** Direction « avant » du plan d'apres la facade du controleur. */
    public static Direction forward(BaseLayout.Kind kind, Direction controllerFacing) {
        return BaseLayout.controllerOnLeftGable(kind) ? controllerFacing.getClockWise() : controllerFacing.getOpposite();
    }

    public static BlockPos at(BlockPos origin, Direction forward, int x, int y, int z) {
        Direction right = forward.getClockWise();
        return origin.offset(right.getStepX() * x + forward.getStepX() * z, y, right.getStepZ() * x + forward.getStepZ() * z);
    }

    /** La plus grande base valide autour du controleur (monde reel), ou null. */
    @Nullable
    public static Result find(Level level, BlockPos controller, Direction facing, BaseLayout.Kind kind) {
        return find(Structure.View.of(level), controller, facing, kind);
    }

    /** La plus grande base valide autour du controleur, ou null. */
    @Nullable
    public static Result find(Structure.View level, BlockPos controller, Direction facing, BaseLayout.Kind kind) {
        Direction forward = forward(kind, facing);
        for (int size = BaseLayout.Kind.SIZES - 1; size >= 0; size--) {
            BlockPos c = BaseLayout.controller(kind, size);
            //origine telle que la case controleur tombe sur le controleur
            BlockPos origin = controller.subtract(at(BlockPos.ZERO, forward, c.getX(), c.getY(), c.getZ()));
            Result result = validate(level, kind, size, origin, forward, controller);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Nullable
    private static Result validate(Structure.View level, BaseLayout.Kind kind, int size, BlockPos origin, Direction forward, BlockPos controller) {
        int tier = 3;
        List<BlockPos> functional = new ArrayList<>(), ports = new ArrayList<>(), members = new ArrayList<>();
        BlockPos pad = null;
        for (Map.Entry<BlockPos, BaseLayout.Role> e : BaseLayout.cells(kind, size).entrySet()) {
            BlockPos l = e.getKey();
            BlockPos pos = at(origin, forward, l.getX(), l.getY(), l.getZ());
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            switch (e.getValue()) {
                case CONTROLLER -> {
                    if (!pos.equals(controller)) {
                        return null;
                    }
                }
                case CASING -> {
                    if (block instanceof CasingBlock casing) {
                        if (casing.scorched()) {
                            return null;
                        }
                        tier = Math.min(tier, casing.tier);
                        members.add(pos);
                    } else if (isPort(kind, block)) {
                        ports.add(pos);
                    } else {
                        return null;
                    }
                }
                case FRAME -> {
                    if (!(block instanceof StructureFrameBlock frame)) {
                        return null;
                    }
                    tier = Math.min(tier, frame.tier);
                }
                case SHEET -> {
                    if (block instanceof SheetMetalBlock sheet) {
                        tier = Math.min(tier, sheet.tier);
                    } else if (block instanceof IndustrialGlassBlock glass) {
                        tier = Math.min(tier, glass.tier);
                        members.add(pos);
                    } else if (isPort(kind, block)) {
                        ports.add(pos);
                    } else {
                        return null;
                    }
                }
                case GLASS -> {
                    if (!(block instanceof IndustrialGlassBlock glass)) {
                        return null;
                    }
                    tier = Math.min(tier, glass.tier);
                    members.add(pos);
                }
                case FREE -> {
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return null;
                    }
                }
                case BED -> {
                    if (block != PiBlocks.HYDROPONIC_SUBSTRATE.getBlock()) {
                        return null;
                    }
                    functional.add(pos);
                }
                case PAD -> {
                    if (block != PiBlocks.CHARGE_PAD.getBlock()) {
                        return null;
                    }
                    pad = pos;
                }
                case DOCK -> {
                    if (block != PiBlocks.ROVER_DOCK.getBlock()) {
                        return null;
                    }
                    functional.add(pos);
                }
                case RADAR -> {
                    if (block != PiBlocks.PROSPECTING_RADAR.getBlock()) {
                        return null;
                    }
                }
                case DRONE_PAD -> {
                    if (block != PiBaseMachines.DRONE_PAD.getBlock()) {
                        return null;
                    }
                    functional.add(pos);
                }
            }
        }
        return new Result(kind, size, Math.max(0, tier), origin, forward, functional, pad, ports, members);
    }

    /** Ports propres a la base (energie et objets) acceptes a la place d'une carcasse ou d'une tole. */
    private static boolean isPort(BaseLayout.Kind kind, Block block) {
        return switch (kind) {
            case GREENHOUSE -> block == PiBlocks.GREENHOUSE_PORT.getBlock();
            case GARAGE -> block == PiBlocks.MINING_PORT.getBlock();
            case DRONE_BAY -> false;
        };
    }

    /** Metier de la base (habillage des carcasses et du verre). */
    public static IndustryStyle style(BaseLayout.Kind kind) {
        return switch (kind) {
            case GREENHOUSE -> IndustryStyle.LIVING;
            case GARAGE -> IndustryStyle.MECHANICAL;
            case DRONE_BAY -> IndustryStyle.FLUID;
        };
    }

    /** Habille les carcasses et le verre de la base (sans prevenir les voisins). */
    public static void paint(Level level, Result result) {
        IndustryStyle style = style(result.kind());
        int t = result.tier() + 1;
        for (BlockPos p : result.members()) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            BlockState next = state;
            if (next.hasProperty(IndustryStyle.STYLE) && next.getValue(IndustryStyle.STYLE) != style) {
                next = next.setValue(IndustryStyle.STYLE, style);
            }
            if (next.hasProperty(IndustryStyle.TIER) && next.getValue(IndustryStyle.TIER) != t) {
                next = next.setValue(IndustryStyle.TIER, t);
            }
            if (next.getBlock() instanceof CasingBlock) {
                next = Connections.connect(next, level, p, Connections.INDUSTRIAL);
            } else if (next.getBlock() instanceof IndustrialGlassBlock glass) {
                //0.20.28 : la vitre garde la couleur de son propre tier
                next = Connections.connect(next.setValue(IndustryStyle.TIER, glass.tier + 1), level, p, Connections.INDUSTRIAL_GLASS);
            }
            if (next != state) {
                level.setBlock(p, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Bloc conseille pour le plan projete (tier basique) et blocs acceptes a la place. */
    public static Block suggested(BaseLayout.Kind kind, BaseLayout.Role role) {
        return switch (role) {
            case CASING -> PiIndustry.CASINGS.values().iterator().next().getBlock();
            case FRAME -> PiIndustry.FRAMES.values().iterator().next().getBlock();
            case SHEET -> PiIndustry.SHEETS.values().iterator().next().getBlock();
            case GLASS -> PiIndustry.GLASS.getBlock();
            case BED -> PiBlocks.HYDROPONIC_SUBSTRATE.getBlock();
            case PAD -> PiBlocks.CHARGE_PAD.getBlock();
            case DOCK -> PiBlocks.ROVER_DOCK.getBlock();
            case RADAR -> PiBlocks.PROSPECTING_RADAR.getBlock();
            case DRONE_PAD -> PiBaseMachines.DRONE_PAD.getBlock();
            case CONTROLLER -> switch (kind) {
                case GREENHOUSE -> PiBaseMachines.GREENHOUSE_CONTROLLER.getBlock();
                case GARAGE -> PiBaseMachines.MINING_OUTPOST_CONTROLLER.getBlock();
                case DRONE_BAY -> PiBaseMachines.DRONE_BAY.getBlock();
            };
            case FREE -> null;
        };
    }
}
