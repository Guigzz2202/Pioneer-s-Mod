package fr.pioneers.farm.food;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.4 : contenu d'une assiette ({@link PlacedDishBlock}) : un plat ou une boisson, 1 a {@link PlacedDishBlock#MAX} portions. NBT {@code dish}.
 * 1.6 (lot A1) : la derniere portion se mange en plusieurs bouchees ({@link #bites()} deja prises, NBT {@code bites}) ; une portion entamee
 * ne se reprend pas et ne retombe pas quand on casse le bloc.
 */
public class PlacedDishTile extends PiBlockEntity {

    private ItemStack dish = ItemStack.EMPTY;
    private int bites;

    public PlacedDishTile(BlockPos pos, BlockState state) {
        super(PlacedDishBlock.TILE.get(), pos, state);
    }

    public ItemStack dish() {
        return dish;
    }

    /** Bouchees (ou gorgees) deja prises sur la portion en cours. */
    public int bites() {
        return bites;
    }

    /** Portions entieres (sans la portion entamee). */
    public int whole() {
        return dish.getCount() - (bites > 0 ? 1 : 0);
    }

    public boolean accepts(ItemStack stack) {
        return dish.isEmpty() || ItemStack.isSameItemSameTags(dish, stack) && dish.getCount() < PlacedDishBlock.MAX;
    }

    public void add(ItemStack one) {
        if (dish.isEmpty()) {
            dish = one.copyWithCount(1);
            bites = 0;
        } else {
            dish.grow(1);
        }
        sync();
    }

    /** Une bouchee de plus sur la portion en cours (pas la derniere). */
    public void bite() {
        bites++;
        sync();
    }

    /** Retire une portion entiere (Maj + clic main vide) ; la portion entamee reste. */
    public ItemStack takeWhole() {
        ItemStack one = dish.split(1);
        sync();
        return one;
    }

    /** Retire la portion qu'on vient de finir. */
    public ItemStack takeEaten() {
        ItemStack one = dish.split(1);
        bites = 0;
        sync();
        return one;
    }

    /** Assiette vide : le bloc disparait. */
    public void emptyCheck() {
        if (dish.isEmpty() && level != null) {
            level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (!dish.isEmpty()) {
            tag.put("dish", dish.save(new CompoundTag()));
        }
        if (bites > 0) {
            tag.putInt("bites", bites);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        dish = tag.contains("dish") ? ItemStack.of(tag.getCompound("dish")) : ItemStack.EMPTY;
        bites = tag.getInt("bites");
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
