package fr.pioneers.lighting;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 5, Guillaume : « on pose des lumieres et on connecte un interrupteur avec accroupi clic droit ») : LUMINAIRE du mod,
 * pose au sol, au mur ou au plafond selon sa sorte ({@link Kind#mounts}) ; {@code LIT} = allume. Regle ({@link LightLinks#desired}) :
 * <ul>
 *     <li>non relie : toujours allume (comme une torche) ;</li>
 *     <li>relie a un interrupteur : allume si l'interrupteur est sur « marche » OU si le luminaire recoit un signal de redstone (il reste
 *     pilotable : relie a un interrupteur laisse sur « arret », il obeit a la redstone seule).</li>
 * </ul>
 * Accroupi + clic droit MAIN VIDE apres avoir memorise un interrupteur : lie / delie ({@link LightLinks#lightClicked}). Pas de notion
 * d'alimentation electrique pour l'eclairage dans le mod : les luminaires electriques s'allument et s'eteignent simplement (choix du lot).
 * Modeles : {@code tools/gc/lighting/lighting.py} (un par sorte, pose et etat), formes {@link LightShapes} (memes cotes).
 */
public class LightFixtureBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Sortes de luminaires : id, age (0 meca, 1 bronze, 2 electrique), lumiere allumee, poses possibles. */
    public enum Kind {
        BRONZE_WALL_SCONCE("bronze_wall_sconce", 1, 13, EnumSet.of(AttachFace.WALL)),
        BRONZE_LANTERN("bronze_lantern", 1, 14, EnumSet.of(AttachFace.FLOOR, AttachFace.CEILING)),
        BRONZE_CHANDELIER("bronze_chandelier", 1, 15, EnumSet.of(AttachFace.CEILING)),
        ELECTRIC_WALL_LAMP("electric_wall_lamp", 2, 15, EnumSet.of(AttachFace.WALL)),
        ELECTRIC_CEILING_LAMP("electric_ceiling_lamp", 2, 15, EnumSet.of(AttachFace.CEILING)),
        INDUSTRIAL_CAGE_LAMP("industrial_cage_lamp", 2, 15, EnumSet.of(AttachFace.FLOOR, AttachFace.WALL, AttachFace.CEILING));

        public final String id;
        public final int age, light;
        public final Set<AttachFace> mounts;

        Kind(String id, int age, int light, Set<AttachFace> mounts) {
            this.id = id;
            this.age = age;
            this.light = light;
            this.mounts = mounts;
        }

        public boolean electric() {
            return age >= 2;
        }
    }

    public final Kind kind;

    public LightFixtureBlock(Kind kind, Properties properties) {
        super(properties.lightLevel(s -> s.getValue(LIT) ? kind.light : 0));
        this.kind = kind;
        AttachFace first = kind.mounts.iterator().next();
        registerDefaultState(stateDefinition.any().setValue(FACE, first).setValue(FACING, Direction.NORTH).setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, LIT);
    }

    /** Comme la pose vanilla (faces regardees dans l'ordre), mais seulement sur les poses de la sorte ; allume (pas encore relie). */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        for (Direction d : ctx.getNearestLookingDirections()) {
            AttachFace face = d.getAxis() == Direction.Axis.Y ? d == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR : AttachFace.WALL;
            if (!kind.mounts.contains(face)) {
                continue;
            }
            Direction facing = face == AttachFace.WALL ? d.getOpposite() : ctx.getHorizontalDirection();
            BlockState st = defaultBlockState().setValue(FACE, face).setValue(FACING, facing).setValue(LIT, true);
            if (st.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                return st;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        Direction toSupport = getConnectedDirection(state).getOpposite();
        BlockPos support = pos.relative(toSupport);
        //lanterne et lustre pendus : un appui au centre suffit (poutre, barreau, chaine...) ; au mur et au sol : face pleine
        return state.getValue(FACE) == AttachFace.CEILING ? Block.canSupportCenter(level, support, Direction.DOWN)
              : level.getBlockState(support).isFaceSturdy(level, support, toSupport.getOpposite());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return LightShapes.fixture(kind, state.getValue(FACE), state.getValue(FACING));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        //on passe sous un lustre et a cote d'une applique sans s'y cogner
        return kind == Kind.BRONZE_LANTERN && state.getValue(FACE) == AttachFace.FLOOR ? getShape(state, level, pos, ctx)
              : Shapes.empty();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !LightLinks.linkingHand(player.getItemInHand(hand)) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            LightLinks.lightClicked(server, sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level instanceof ServerLevel server && server.getBlockState(pos).is(this)) {
            //relie : la redstone l'allume aussi
            LightLinks.apply(server, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!next.is(state.getBlock()) && level instanceof ServerLevel server) {
            LightLinks.lightRemoved(server, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
