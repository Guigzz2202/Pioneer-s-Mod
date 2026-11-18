package fr.pioneers.tech.mechanics;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.tech.kinetics.KineticEvents;
import fr.pioneers.tech.kinetics.machines.MechanicalRecipe;
import fr.pioneers.tech.kinetics.machines.MeshItem;
import fr.pioneers.core.recipes.PiRecipes;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tamis a main : posez une maille (clic droit avec la maille), versez du gravier ou du sable (jusqu'a 16), puis secouez (clic droit main
 * vide, 5 secousses par charge). Ce qui reste dans la maille saute hors du tamis.
 */
public class HandSieveBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);
    public static final int SHAKES = 5;

    public HandSieveBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Tile tile)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (held.getItem() instanceof MeshItem) {
            if (!tile.mesh.isEmpty()) {
                Block.popResource(level, pos.above(), tile.mesh);
            }
            tile.mesh = held.split(1);
            tile.sync();
            return InteractionResult.CONSUME;
        }
        if (recipeFor(level, held) != null) {
            if (tile.sediment.isEmpty() || ItemStack.isSameItemSameTags(tile.sediment, held) && tile.sediment.getCount() < 16) {
                int n = Math.min(held.getCount(), 16 - tile.sediment.getCount());
                ItemStack moved = held.split(n);
                if (tile.sediment.isEmpty()) {
                    tile.sediment = moved;
                } else {
                    tile.sediment.grow(moved.getCount());
                }
                tile.sync();
            }
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (player.isShiftKeyDown()) {
                tile.empty(level, pos);
                return InteractionResult.CONSUME;
            }
            if (tile.mesh.isEmpty()) {
                player.displayClientMessage(Component.translatable("block.pioneers.hand_sieve.no_mesh"), true);
                return InteractionResult.CONSUME;
            }
            tile.shake((ServerLevel) level, player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Tile tile) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), tile.sediment);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), tile.mesh);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    public static class Tile extends PiBlockEntity {

        public ItemStack sediment = ItemStack.EMPTY, mesh = ItemStack.EMPTY;
        public int shakes;

        public Tile(BlockPos pos, BlockState state) {
            super(PiMechanics.HAND_SIEVE_TILE.get(), pos, state);
        }

        void empty(Level level, BlockPos pos) {
            Block.popResource(level, pos.above(), sediment);
            sediment = ItemStack.EMPTY;
            shakes = 0;
            sync();
        }

        void shake(ServerLevel level, Player player) {
            MechanicalRecipe recipe = recipeFor(level, sediment);
            if (recipe == null) {
                return;
            }
            player.causeFoodExhaustion(0.02F);
            shakes++;
            level.playSound(null, worldPosition, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.7F, 1.0F + level.random.nextFloat() * 0.3F);
            if (sediment.getItem() instanceof BlockItem block) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.getBlock().defaultBlockState()), worldPosition.getX() + 0.5,
                      worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.05);
            }
            if (shakes >= SHAKES) {
                shakes = 0;
                sediment.shrink(recipe.count());
                int tier = mesh.getItem() instanceof MeshItem m ? m.tier : 1;
                MeshItem m = mesh.getItem() instanceof MeshItem mi ? mi : null;
                for (ItemStack out : recipe.roll(level.random, tier, m == null ? 1 : m.chanceBonus(), m == null ? 0 : m.secondRoll())) {
                    Block.popResource(level, worldPosition.above(), out);
                }
                mesh.setDamageValue(mesh.getDamageValue() + 1);
                if (mesh.getDamageValue() >= mesh.getMaxDamage()) {
                    mesh = ItemStack.EMPTY;
                    level.playSound(null, worldPosition, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
                }
                KineticEvents.award(level, worldPosition, "sieve");
            }
            sync();
        }

        void sync() {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        protected void saveAdditional(@NotNull CompoundTag tag) {
            super.saveAdditional(tag);
            tag.put("sediment", sediment.save(new CompoundTag()));
            tag.put("mesh", mesh.save(new CompoundTag()));
            tag.putInt("shakes", shakes);
        }

        @Override
        public void load(@NotNull CompoundTag tag) {
            super.load(tag);
            sediment = ItemStack.of(tag.getCompound("sediment"));
            mesh = ItemStack.of(tag.getCompound("mesh"));
            shakes = tag.getInt("shakes");
        }

        @NotNull
        @Override
        public CompoundTag getUpdateTag() {
            return saveWithoutMetadata();
        }

        @Nullable
        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        public String fill() {
            return String.format(Locale.ROOT, "%d", sediment.getCount());
        }
    }

    /** Recette de tamisage de ce sediment (la premiere qui l'accepte), ou null ; 1.0.2-beta : reprise de la batee retiree. */
    @Nullable
    static MechanicalRecipe recipeFor(Level level, ItemStack sediment) {
        if (sediment.isEmpty()) {
            return null;
        }
        for (MechanicalRecipe r : PiRecipes.sorted(level, PiRecipes.MECHANICAL.get(MechanicalRecipe.Kind.SIFTING).get())) {
            if (r.accepts(sediment)) {
                return r;
            }
        }
        return null;
    }
}
