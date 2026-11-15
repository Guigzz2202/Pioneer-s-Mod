package fr.pioneers.robotics.logistics;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.registries.PiRobotics;
import fr.pioneers.robotics.Endpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Balise logistique posee : le bloc auquel elle est fixee, et ses reglages ({@link PointConfig}), synchronises vers les clients (carte,
 * aides visuelles, menu).
 */
public class TileEntityLogisticMarker extends PiBlockEntity implements LogisticPoint {

    private PointConfig config = new PointConfig();

    public TileEntityLogisticMarker(BlockPos pos, BlockState state) {
        super(PiRobotics.MARKER_TILE.get(), pos, state);
        if (state.hasProperty(BlockLogisticMarker.ROLE)) {
            config.role = state.getValue(BlockLogisticMarker.ROLE);
        }
    }

    public MarkerRole getRole() {
        return config.role;
    }

    /** Face sur laquelle la balise est posee. */
    public Direction getFacing() {
        return getBlockState().hasProperty(BlockLogisticMarker.FACING) ? getBlockState().getValue(BlockLogisticMarker.FACING) : Direction.UP;
    }

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

    /** Bloc balise, face d'acces (reglee, sinon celle de la balise) ; l'unite se tient a la place de la balise. */
    @Override
    public Endpoint endpoint() {
        Direction facing = getFacing();
        return new Endpoint(worldPosition.relative(facing.getOpposite()), config.accessSide != null ? config.accessSide : facing, worldPosition);
    }

    @Override
    public boolean isPointRemoved() {
        return isRemoved();
    }

    @Override
    public Component displayName() {
        if (!config.name.isEmpty()) {
            return Component.literal(config.name);
        }
        if (level != null) {
            return level.getBlockState(worldPosition.relative(getFacing().getOpposite())).getBlock().getName();
        }
        return Component.translatable(config.role.getTranslationKey());
    }

    @Override
    public ItemStack displayIcon() {
        if (level != null) {
            ItemStack stack = new ItemStack(level.getBlockState(worldPosition.relative(getFacing().getOpposite())).getBlock());
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return new ItemStack(PiRobotics.MARKER);
    }

    // ------------------------------------------------------------------
    // Registre
    // ------------------------------------------------------------------

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
    // Sauvegarde et synchro
    // ------------------------------------------------------------------

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("config")) {
            config = PointConfig.load(tag.getCompound("config"));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("config", config.save());
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put("config", config.save());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
