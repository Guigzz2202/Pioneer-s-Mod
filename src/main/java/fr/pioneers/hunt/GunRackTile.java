package fr.pioneers.hunt;

import fr.pioneers.Pioneers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** 1.0.5-beta (lot 10) : contenu du porte-fusil : 3 rangs, une arme par rang (envoye au client pour le rendu). */
public class GunRackTile extends BlockEntity {

    public static final int SLOTS = 3;
    public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, Pioneers.rl("gun_rack_weapons"));
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public GunRackTile(BlockPos pos, BlockState state) {
        super(PiHunt.GUN_RACK_TILE.get(), pos, state);
    }

    public static boolean accepts(ItemStack s) {
        return !s.isEmpty() && s.is(WEAPONS);
    }

    public ItemStack get(int i) {
        return items.get(i);
    }

    public void set(int i, ItemStack s) {
        items.set(i, s);
        changed();
    }

    public ItemStack take(int i) {
        ItemStack s = items.get(i);
        items.set(i, ItemStack.EMPTY);
        changed();
        return s;
    }

    public int firstFree() {
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public int firstFull() {
        for (int i = 0; i < SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public int count() {
        int n = 0;
        for (ItemStack s : items) {
            n += s.isEmpty() ? 0 : 1;
        }
        return n;
    }

    void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items, true);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        for (int i = 0; i < SLOTS; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = super.getUpdateTag();
        ContainerHelper.saveAllItems(t, items, true);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(0.5);
    }
}
