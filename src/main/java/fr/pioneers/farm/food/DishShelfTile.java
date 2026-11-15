package fr.pioneers.farm.food;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.8 : contenu d'une etagere a plats ({@link DishShelfBlock}) : un plat ou une boisson par place. NBT {@code shelf} [slot, objet].
 * 1.0.3-beta (lot 12) : {@link ShelfJoints#CAPACITY} places : planche basse 0..3, planche haute 4..7 (etagere au sol du sommet) ; la murale
 * ({@link WallShelfBlock}, meme tuile) n'utilise que 0..3. Les etageres d'avant (places 0..3) se relisent telles quelles.
 */
public class DishShelfTile extends PiBlockEntity {

    private final NonNullList<ItemStack> items = NonNullList.withSize(ShelfJoints.CAPACITY, ItemStack.EMPTY);

    public DishShelfTile(BlockPos pos, BlockState state) {
        super(DishShelfBlock.TILE.get(), pos, state);
    }

    public ItemStack get(int slot) {
        return items.get(slot);
    }

    public void set(int slot, ItemStack one) {
        items.set(slot, one);
        sync();
    }

    public ItemStack take(int slot) {
        ItemStack out = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        sync();
        return out;
    }

    /** Premiere place libre parmi {@code n} a partir de {@code first} (-1 : planche pleine). */
    public int firstEmpty(int first, int n) {
        for (int i = first; i < first + n && i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Place occupee la plus proche de {@code slot} parmi {@code n} a partir de {@code first} (-1 : planche vide). */
    public int nearestFull(int slot, int first, int n) {
        for (int d = 1; d < n; d++) {
            for (int s : new int[]{slot - d, slot + d}) {
                if (s >= first && s < first + n && s < items.size() && !items.get(s).isEmpty()) {
                    return s;
                }
            }
        }
        return -1;
    }

    /** 1.9.2 : liaisons recalculees au premier passage ; 1.0.3-beta (lot 12) : murales d'avant converties ({@link DishShelfBlock#tick}). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
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
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                CompoundTag t = items.get(i).save(new CompoundTag());
                t.putByte("slot", (byte) i);
                list.add(t);
            }
        }
        tag.put("shelf", list);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ListTag list = tag.getList("shelf", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int s = t.getByte("slot");
            if (s >= 0 && s < items.size()) {
                items.set(s, ItemStack.of(t));
            }
        }
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
