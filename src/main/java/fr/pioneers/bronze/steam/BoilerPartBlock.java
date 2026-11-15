package fr.pioneers.bronze.steam;

import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Corps de chaudiere, dome de vapeur ou cheminee. Les corps recoivent l'eau (seau, tuyau, injecteur) et la donnent a leur foyer ; le dome est
 * la sortie de vapeur ou se branchent les tuyaux ; la cheminee fait le tirage. Clic droit : etat de la chaudiere.
 */
public class BoilerPartBlock extends BaseEntityBlock {

    public final BoilerPart part;
    /**
     * 1.0.4-beta (lot 24, Guillaume : « le dome de vapeur prend la couleur de la cuve du dessous (orange ou gris pour l'acier) ») : dome pose
     * sur un corps en acier (virole grise) ; faux sinon (cuivre). Tenue a jour a la pose et quand le bloc du dessous change ; les autres pieces
     * la gardent fausse.
     */
    public static final BooleanProperty STEEL = BooleanProperty.create("steel");

    public BoilerPartBlock(BoilerPart part, Properties properties) {
        super(properties);
        this.part = part;
        registerDefaultState(SteamPorts.defaults(defaultBlockState()).setValue(STEEL, false));
    }

    /** 1.0.4-beta (lot 24) : couleur du dome d'apres le bloc du dessous. */
    private BlockState tint(BlockState state, BlockState below) {
        return part == BoilerPart.STEAM_DOME ? state.setValue(STEEL, below.getBlock() instanceof BoilerPartBlock b && b.part == BoilerPart.STEEL_SHELL) : state;
    }

    /** 0.20.44 : brides de vapeur du dome (toutes ses faces sauf le dessous). */
    private final SteamPorts.Faces faces = (s, side) -> part() == BoilerPart.STEAM_DOME && side != Direction.DOWN;

    private BoilerPart part() {
        return part;
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block,
          BlockState> builder) {
        SteamPorts.add(builder);
        builder.add(STEEL);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return tint(SteamPorts.refresh(defaultBlockState(), context.getLevel(), context.getClickedPos(), faces),
              context.getLevel().getBlockState(context.getClickedPos().below()));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbour,
          @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        BlockState out = SteamPorts.update(super.updateShape(state, side, neighbour, level, pos, neighbourPos), side, neighbour, faces);
        return side == Direction.DOWN ? tint(out, neighbour) : out;
    }

    /** 1.0.4-beta (lot 24) : couleur recalculee a toute pose (plan projete, code) : le voisin du dessous peut etre pose avant. */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        BlockState want = tint(state, level.getBlockState(pos.below()));
        if (want != state && !level.isClientSide) {
            level.setBlock(pos, want, Block.UPDATE_CLIENTS);
        }
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return part == BoilerPart.CHIMNEY ? null : new BoilerPartTile(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        FireboxTile firebox = level.getBlockEntity(pos) instanceof BoilerPartTile tile ? tile.controller() : null;
        ItemStack held = player.getItemInHand(hand);
        //1.0.1 : tout seau d'eau (vanilla, en argile, d'un autre mod)
        if (firebox != null && Buckets.isWaterBucket(held)) {
            if (!level.isClientSide && firebox.fillWater(Buckets.VOLUME, true) >= Buckets.VOLUME && Buckets.drainHeld(player, hand, Fluids.WATER)) {
                firebox.fillWater(Buckets.VOLUME, false);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1F, 1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (part != BoilerPart.CHIMNEY && !level.isClientSide) {
            player.displayClientMessage(firebox == null ? Component.translatable("steam.pioneers.boiler.unformed").withStyle(ChatFormatting.RED)
                                                        : firebox.summary(), true);
        }
        return part == BoilerPart.CHIMNEY ? InteractionResult.PASS : InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BoilerPartTile tile && tile.controller() != null) {
            tile.controller().requestCheck();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (part == BoilerPart.CHIMNEY && !(level.getBlockState(pos.above()).getBlock() instanceof BoilerPartBlock) && random.nextInt(3) == 0
            && level.getBlockState(pos.below()).getBlock() instanceof BoilerPartBlock) {
            //La fumee elle-meme vient du foyer (qui sait s'il brule) : ici, un simple voile quand la cheminee est chaude
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        if (part.shell()) {
            tips.spec("steam.pioneers.shell_spec", fr.pioneers.core.Texts.format(part.steam));
        }
    }
}
