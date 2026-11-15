package fr.pioneers.building.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 5) : INTERRUPTEUR D'ECLAIRAGE, pose au mur, au sol ou au plafond comme un levier ({@code FACE}, {@code FACING}),
 * {@code POWERED} = sur « marche ». Trois styles par age ({@link Style}) : levier de bois (age mecanique), levier de bronze, interrupteur
 * electrique (age industriel) ; memes regles.
 * <ul>
 *     <li>clic droit : marche / arret de TOUTES ses lumieres ({@link LightLinks#switchChanged}, celles des troncons decharges a leur
 *     chargement), son de la manette, etat sauve (bloc + donnees de liaison) ;</li>
 *     <li>accroupi + clic droit MAIN VIDE : memorise l'interrupteur (ou termine la liaison s'il l'etait), puis accroupi + clic droit sur chaque
 *     lumiere pour la lier / delier ({@link LightLinks#switchClicked}) ;</li>
 *     <li>redstone : sur « marche », il emet un signal de 15 comme un levier (et forte sur le bloc qui le porte).</li>
 * </ul>
 */
public class LightSwitchBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Styles : id, age, son (marche / arret : hauteur). */
    public enum Style {
        WOOD("wooden_light_lever", 0), BRONZE("bronze_light_lever", 1), ELECTRIC("electric_light_switch", 2);

        public final String id;
        public final int age;

        Style(String id, int age) {
            this.id = id;
            this.age = age;
        }

        SoundEvent sound(boolean on) {
            return this == ELECTRIC ? on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF : SoundEvents.LEVER_CLICK;
        }

        float pitch(boolean on) {
            return switch (this) {
                case WOOD -> on ? 0.55F : 0.45F;
                case BRONZE -> on ? 0.75F : 0.65F;
                case ELECTRIC -> on ? 1.6F : 1.4F;
            };
        }
    }

    public final Style style;

    public LightSwitchBlock(Style style, Properties properties) {
        super(properties);
        this.style = style;
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL)
              .setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POWERED);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return LightShapes.lightSwitch(state.getValue(FACE), state.getValue(FACING));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!LightLinks.linkingHand(player.getItemInHand(hand)) || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                LightLinks.switchClicked(server, sp, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        toggle(state, (ServerLevel) level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** Marche / arret (joueur, ou GameTest avec {@code player} null) : etat, son, voisins (signal), lumieres reliees. */
    public BlockState toggle(BlockState state, ServerLevel level, BlockPos pos, @Nullable Player player) {
        boolean on = !state.getValue(POWERED);
        BlockState next = state.setValue(POWERED, on);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        updateNeighbours(next, level, pos);
        level.playSound(null, pos, style.sound(on), SoundSource.BLOCKS, style == Style.ELECTRIC ? 0.5F : 0.3F, style.pitch(on));
        level.gameEvent(player, on ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        LightLinks.switchChanged(level, pos, on);
        return next;
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction dir) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction dir) {
        return state.getValue(POWERED) && getConnectedDirection(state) == dir ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!moving && !state.is(next.getBlock())) {
            if (state.getValue(POWERED)) {
                updateNeighbours(state, level, pos);
            }
            if (level instanceof ServerLevel server) {
                LightLinks.switchRemoved(server, pos);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
