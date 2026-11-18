package fr.pioneers.tech.kinetics.parts;

import fr.pioneers.core.block.Covers;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticMaterial;
import fr.pioneers.tech.kinetics.KineticTile;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Engrenage. Pignon (petit) : s'engrene avec un pignon voisin dans son plan (rapport 1:1, sens inverse). Rouet (grand) : s'engrene en
 * diagonale avec un pignon (rapport 1:2 : le pignon tourne deux fois plus vite, en sens inverse). Les deux transmettent aussi par leur axe.
 * <p>1.0.5-beta (lot 23, Guillaume : « placer un frame en bois sur les engrenages (cela deconnecte l'axe au centre, mais permet toujours les
 * connexions entre engrenages) ; par defaut les engrenages n'ont plus d'axe au centre, les textures se connectent si besoin ») :
 * <ul>
 * <li>CADRE ({@link Covers#FRAMED}, le cadre de cache en bois) : clic droit avec un cadre de cache = le cadre s'installe (deux flasques de bois
 * sur les faces de l'axe, palier et pivot fixes) ; l'engrenage tourne sur son pivot : plus aucun lien par l'axe ({@link CogTile#shaftFactor}
 * = 0), les engrenements dans le plan restent ; lime = cadre rendu ; casser = le cadre d'abord ({@link Covers#breakCover}) ;</li>
 * <li>AXE : dessine seulement du cote d'un vrai bout d'arbre voisin ({@code KineticVisuals.cogAt}) ; aucune regle de transmission changee.</li>
 * </ul>
 */
public class CogBlock extends AxisKineticBlock {

    private static final Map<Direction.Axis, VoxelShape> SMALL = new EnumMap<>(Direction.Axis.class), LARGE = new EnumMap<>(Direction.Axis.class),
          FRAME = new EnumMap<>(Direction.Axis.class);
    /** 1.0.5-beta (lot 23) : epaisseur des flasques du cadre (px), sur les deux faces de l'axe. */
    public static final float FRAME_T = 2F;

    static {
        SMALL.put(Direction.Axis.Y, Shapes.or(Block.box(1, 5, 1, 15, 11, 15), ShaftBlock.SHAPES.get(Direction.Axis.Y)));
        SMALL.put(Direction.Axis.X, Shapes.or(Block.box(5, 1, 1, 11, 15, 15), ShaftBlock.SHAPES.get(Direction.Axis.X)));
        SMALL.put(Direction.Axis.Z, Shapes.or(Block.box(1, 1, 5, 15, 15, 11), ShaftBlock.SHAPES.get(Direction.Axis.Z)));
        LARGE.put(Direction.Axis.Y, Shapes.or(Block.box(0, 6, 0, 16, 10, 16), ShaftBlock.SHAPES.get(Direction.Axis.Y)));
        LARGE.put(Direction.Axis.X, Shapes.or(Block.box(6, 0, 0, 10, 16, 16), ShaftBlock.SHAPES.get(Direction.Axis.X)));
        LARGE.put(Direction.Axis.Z, Shapes.or(Block.box(0, 0, 6, 16, 16, 10), ShaftBlock.SHAPES.get(Direction.Axis.Z)));
        FRAME.put(Direction.Axis.Y, Shapes.or(Block.box(0, 0, 0, 16, FRAME_T, 16), Block.box(0, 16 - FRAME_T, 0, 16, 16, 16)));
        FRAME.put(Direction.Axis.X, Shapes.or(Block.box(0, 0, 0, FRAME_T, 16, 16), Block.box(16 - FRAME_T, 0, 0, 16, 16, 16)));
        FRAME.put(Direction.Axis.Z, Shapes.or(Block.box(0, 0, 0, 16, 16, FRAME_T), Block.box(0, 0, 16 - FRAME_T, 16, 16, 16)));
    }

    public final KineticMaterial material;
    public final boolean large;

    public CogBlock(KineticMaterial material, boolean large, Properties properties) {
        super(properties);
        this.material = material;
        this.large = large;
        registerDefaultState(defaultBlockState().setValue(Covers.FRAMED, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Covers.FRAMED);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        VoxelShape gear = (large ? LARGE : SMALL).get(axis(state));
        return Covers.framed(state) ? Shapes.or(gear, FRAME.get(axis(state))) : gear;
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 23) : cadre
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult framed = Covers.useFrameOnly(state, level, pos, player, hand);
        if (framed != null) {
            relink(level, pos);
            return framed;
        }
        return super.interact(state, level, pos, player, hand, hit);
    }

    /** Le cadre change les liens par l'axe : reseau de l'engrenage et de ses deux voisins d'axe reconstruits (serveur). */
    static void relink(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof KineticTile tile) {
            tile.invalidateNetwork();
        }
        Direction.Axis axis = axis(level.getBlockState(pos));
        for (Direction d : new Direction[]{Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
              Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)}) {
            if (level.isLoaded(pos.relative(d)) && level.getBlockEntity(pos.relative(d)) instanceof KineticTile near) {
                near.invalidateNetwork();
            }
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (Covers.breakCover(state, level, pos, player)) {
            relink(level, pos);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return Covers.progress(state, player, level, pos, super.getDestroyProgress(state, player, level, pos));
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return Covers.sound(state, level, pos, entity, super.getSoundType(state, level, pos, entity));
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull BlockState state) {
        ItemStack frame = Covers.pick(state, level, pos);
        return frame != null ? frame : super.getCloneItemStack(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        Covers.dropOnRemove(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CogTile(pos, state);
    }
}
