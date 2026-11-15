package fr.pioneers.space.farm;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.PlantType;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Substrat hydroponique : tasse, il doit etre beche (par le robot agricole, ou a la houe) avant d'accueillir une graine.
 * Beche, il ressemble a une terre labouree gorgee d'eau.
 * <p>
 * 1.12.3 (lot F3, demande de Guillaume) : il ne sert plus seulement aux graines spatiales de la serre. Beche, il accueille TOUTES les
 * cultures de la Terre ({@code farm/crop/FarmCropBlock}) comme une terre labouree toujours humide, et les fait pousser
 * {@link #GROWTH} fois plus vite. Il ne change PAS leur climat : temperature et humidite du biome, marge de la Resistance, serre de verre,
 * exactement comme sur une terre labouree irriguee (l'irrigation compte, ni plus ni moins).
 */
public class HydroponicSubstrateBlock extends Block {

    /**
     * Croissance sur substrat beche, par rapport a une terre labouree humide (terre seche : x0,5). x1,5 : meme facteur que la solution
     * nutritive des fermes industrielles et que la serre spatiale MKII.
     */
    public static final float GROWTH = 1.5F;
    public static final BooleanProperty TILLED = BooleanProperty.create("tilled");
    private static final VoxelShape TILLED_SHAPE = box(0, 0, 0, 16, 15, 16);

    public HydroponicSubstrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TILLED);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return state.getValue(TILLED) ? TILLED_SHAPE : super.getShape(state, level, pos, context);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
    }

    /** La houe beche aussi le substrat. */
    @Nullable
    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate) {
        if (toolAction == ToolActions.HOE_TILL && !state.getValue(TILLED)) {
            return state.setValue(TILLED, true);
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }

    public static boolean isTilled(Level level, BlockPos pos) {
        return isTilled(level.getBlockState(pos));
    }

    /** Substrat beche (pret a recevoir une graine). */
    public static boolean isTilled(BlockState state) {
        return state.getBlock() instanceof HydroponicSubstrateBlock && state.getValue(TILLED);
    }

    /** Substrat encore tasse (a becher). */
    public static boolean isPacked(BlockState state) {
        return state.getBlock() instanceof HydroponicSubstrateBlock && !state.getValue(TILLED);
    }

    /** Sol ou se seme une culture de la Terre : terre labouree ou substrat beche (robots agriculteurs, cultures fanees). */
    public static boolean isFarmSoil(BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.FARMLAND) || isTilled(state);
    }

    /** 1.12.3 : beche, il porte les cultures (type CROP de Forge) comme la terre labouree. */
    @Override
    public boolean canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction facing, IPlantable plantable) {
        if (facing == Direction.UP && state.getValue(TILLED) && plantable.getPlantType(level, pos.relative(facing)) == PlantType.CROP) {
            return true;
        }
        return super.canSustainPlant(state, level, pos, facing, plantable);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("tooltip.pioneers.hydroponic_substrate");
        //Facteur ecrit dans la langue (T:core/lang_1123.py) : le garder egal a GROWTH
        tips.good("tooltip.pioneers.hydroponic_substrate.growth");
        tips.note("tooltip.pioneers.hydroponic_substrate.climate");
        tips.hint("tooltip.pioneers.hydroponic_substrate.till");
    }
}
