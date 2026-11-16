package fr.pioneers.logistics.transport.legacy;

import fr.pioneers.core.Directions;
import fr.pioneers.core.block.Waterlogging;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2c, spec §8.1) : « cable herite ». Les 60 identifiants des anciens cables restent enregistres (a retirer en 1.12) : sans objet,
 * hors onglets et JEI, invisibles ; chaque bloc est converti en cable logistique du meme niveau au chargement de son chunk par
 * {@link LegacyMigration} (sa tile se signale a {@code onLoad} ; un tick aleatoire rattrape un bloc sans tile). Casse avant d'etre converti :
 * il rend le cable logistique qui le remplace.
 * <p>
 * 1.0.6 (memoire du client) : l'etat ne garde que l'eau. Les six faces (aucune / cable / branchee) faisaient 1 458 etats par bloc, et les
 * 60 blocs 141 352 etats (45 % de tous les etats du mod), chacun avec son modele et ses tables. Un monde ancien se relit quand meme : les
 * proprietes inconnues d'un etat sauve sont ignorees (etat par defaut, eau relue) et les faces branchees se retrouvent sur les voisins a la
 * conversion ({@link #plugMask}). Seules les coupures inscrites dans l'etat d'une fibre sont perdues (les cables gardaient les leurs dans la tile).
 */
public class LegacyCableBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    public final LegacyFamily family;
    /** Niveau logistique qui le remplace. */
    public final CableLevel level;
    public final boolean clear;

    public LegacyCableBlock(LegacyFamily family, CableLevel level, boolean clear, Properties properties) {
        super(properties);
        this.family = family;
        this.level = level;
        this.clear = clear;
        registerDefaultState(defaults(stateDefinition.any()));
    }

    protected BlockState defaults(BlockState state) {
        return state.setValue(Waterlogging.WATERLOGGED, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Waterlogging.WATERLOGGED);
    }

    /**
     * Faces branchees sur un bloc (l'ancien etat PLUG), bits = Direction.ordinal : un ancien cable se branchait sur tout voisin a tile qui
     * n'est pas un cable ; l'etat ne le garde plus (1.0.6), on le relit donc sur les voisins.
     */
    public int plugMask(BlockGetter level, BlockPos pos) {
        int mask = 0;
        for (Direction d : Directions.ALL) {
            BlockPos at = pos.relative(d);
            Block other = level.getBlockState(at).getBlock();
            if (!(other instanceof LegacyCableBlock) && !(other instanceof LogisticCableBlock) && level.getBlockEntity(at) != null) {
                mask |= 1 << d.ordinal();
            }
        }
        return mask;
    }

    /** Faces coupees inscrites dans l'etat (fibres : valeur {@code disabled}) ; les cables gardaient leurs coupures dans la tile. */
    public int disabledMask(BlockState state) {
        return 0;
    }

    /**
     * Etat a utiliser a la place d'un etat lu dans un ancien plan, un chantier de robots ou une reparation : un cable herite devient le cable
     * logistique du meme niveau (etat par defaut, eau gardee) ; tout autre etat est rendu tel quel.
     */
    public static BlockState upgrade(BlockState state) {
        if (state.getBlock() instanceof LegacyCableBlock legacy) {
            boolean water = state.hasProperty(Waterlogging.WATERLOGGED) && state.getValue(Waterlogging.WATERLOGGED);
            return legacy.replacement().defaultBlockState().setValue(Waterlogging.WATERLOGGED, water);
        }
        return state;
    }

    /** Le cable logistique qui le remplace. */
    public Block replacement() {
        return PiTransport.logisticCable(level, clear).getBlock();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new LegacyCableTile(PiTransport.LEGACY_CABLE_TILE.get(), pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return CORE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, world, pos, old, moving);
        if (!world.isClientSide && old.getBlock() != state.getBlock()) {
            LegacyMigration.queue(world, pos);
        }
    }

    @Override
    public boolean isRandomlyTicking(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos pos, @NotNull RandomSource random) {
        LegacyMigration.queue(world, pos);
    }

    /** Les voisins ne changent rien : l'etat sauve est lu tel quel par la conversion. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor world,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        Waterlogging.tick(state, world, pos);
        return state;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(@NotNull BlockState state) {
        return Waterlogging.fluid(state);
    }

    /** Casse avant la conversion : le cable logistique du meme niveau. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(@NotNull BlockState state, @NotNull LootParams.Builder builder) {
        return List.of(new ItemStack(replacement()));
    }
}
