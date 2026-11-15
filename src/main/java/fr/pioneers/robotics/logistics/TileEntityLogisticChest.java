package fr.pioneers.robotics.logistics;

import fr.pioneers.registries.PiRobotics;
import fr.pioneers.robotics.Endpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Coffre logistique : inventaire et reglages logistiques. Les unites s'y presentent au-dessus (ou sur un cote libre).
 */
public class TileEntityLogisticChest extends BaseContainerBlockEntity implements LogisticPoint {

    private NonNullList<ItemStack> items;
    private PointConfig config = new PointConfig();

    public TileEntityLogisticChest(BlockPos pos, BlockState state) {
        super(PiRobotics.CHEST_TILE.get(), pos, state);
        int rows = state.getBlock() instanceof BlockLogisticChest chest ? chest.size.rows : 3;
        items = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
        config.role = MarkerRole.STORAGE;
    }

    private int rows() {
        return items.size() / 9;
    }

    // ------------------------------------------------------------------
    // Point logistique
    // ------------------------------------------------------------------

    @Override
    public Level getPointLevel() {
        return level;
    }

    @Override
    public BlockPos getPointPos() {
        return worldPosition;
    }

    @Override
    public PointConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(PointConfig config) {
        //Un coffre n'a pas de face particuliere
        config.accessSide = null;
        config.machineMode = false;
        this.config = config;
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(BlockLogisticMarker.ROLE) && state.getValue(BlockLogisticMarker.ROLE) != config.role) {
                level.setBlock(worldPosition, state.setValue(BlockLogisticMarker.ROLE, config.role), Block.UPDATE_ALL);
            }
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Endpoint endpoint() {
        BlockPos stand = worldPosition.above();
        if (level != null && !level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = worldPosition.relative(dir);
                if (level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
                    stand = side;
                    break;
                }
            }
        }
        return new Endpoint(worldPosition, null, stand);
    }

    @Override
    public boolean isPointRemoved() {
        return isRemoved();
    }

    @Override
    public Component displayName() {
        return config.name.isEmpty() ? getBlockState().getBlock().getName() : Component.literal(config.name);
    }

    @Override
    public ItemStack displayIcon() {
        return new ItemStack(getBlockState().getBlock());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            LogisticsRegistry.get(level).addPoint(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            LogisticsRegistry.get(level).removePoint(this);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null) {
            LogisticsRegistry.get(level).removePoint(this);
        }
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------------
    // Inventaire
    // ------------------------------------------------------------------

    @NotNull
    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @NotNull
    @Override
    protected AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        MenuType<ChestMenu> type = switch (rows()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 4 -> MenuType.GENERIC_9x4;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
        return new ChestMenu(type, id, inventory, this, rows());
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @NotNull
    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) {
            setChanged();
        }
        return stack;
    }

    @NotNull
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
               && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchro
    // ------------------------------------------------------------------

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.contains("config")) {
            config = PointConfig.load(tag.getCompound("config"));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.put("config", config.save());
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put("config", config.save());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag.contains("config")) {
            config = PointConfig.load(tag.getCompound("config"));
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, entity -> {
            CompoundTag tag = new CompoundTag();
            tag.put("config", config.save());
            return tag;
        });
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }
}
