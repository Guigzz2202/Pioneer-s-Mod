package fr.pioneers.logistics.transport.buffer;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.StorageUnit;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.side.Fronted;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tile commune des buffers. API Machine (lot 3) : elle ne fait plus que PORTER l'unite de stockage ({@link StorageUnit} : ressource,
 * stockage, faces, ejection, recipients, creatif, etat visible, jauges, valeurs nommees, sauvegarde) et lui servir de facade pour le reste
 * du code (rendu {@code RenderBuffer}, bloc, kits d'amelioration, GameTests). Tout est range sous {@code contents} (copie dans l'objet quand
 * on casse le bloc, par sa table de butin) : format d'avant le lot 3. Interface : la standard v2 (inventaire dans le cadre, onglets a droite).
 */
public class BufferTile extends PiBlockEntity implements MenuProvider, MachineGui, Fronted {

    public static final String NBT_CONTENTS = "contents";
    /** Cases d'entree (haut) et de sortie (bas) des recipients. */
    public static final int IO_X = StorageUnit.IO_X, IN_Y = StorageUnit.IN_Y, OUT_Y = StorageUnit.OUT_Y;

    private final StorageUnit unit;
    @Nullable
    private UiValues values;
    /** 0.20.46 (audit) : boite de rendu fixe (le bloc entier, ou sont les jauges), au lieu du volume de collision recalcule a chaque image. */
    @Nullable
    private AABB renderBox;

    public BufferTile(BlockPos pos, BlockState state) {
        super(PiTransport.BUFFER_TILE.get(), pos, state);
        BufferBlock block = state.getBlock() instanceof BufferBlock b ? b : null;
        BufferType type = block == null ? BufferType.ITEM : block.type;
        CableTier tier = block == null ? CableTier.BASIC : block.tier;
        unit = new StorageUnit(new StorageUnit.Host() {
            @Nullable
            @Override
            public Level level() {
                return level;
            }

            @Override
            public BlockPos pos() {
                return worldPosition;
            }

            @Override
            public BlockState state() {
                return getBlockState();
            }

            @Override
            public void changed() {
                setChanged();
            }

            @Override
            public boolean creative() {
                return BufferTile.this.creative();
            }
        }, type, tier, block != null && block.isCreative());
    }

    /** L'unite de stockage (API Machine). */
    public StorageUnit unit() {
        return unit;
    }

    @Override
    public void setFront(Direction front) {
        unit.setFront(front);
    }

    public List<ItemSlot> getIoSlots() {
        return unit.ioSlots();
    }

    public BufferType getBufferType() {
        return unit.type;
    }

    public CableTier getTier() {
        return unit.tier;
    }

    public List<ItemSlot> getSlots() {
        return unit.slots();
    }

    /** 1.0 : buffer creatif ? */
    public boolean creative() {
        return getBlockState().getBlock() instanceof BufferBlock b && b.isCreative();
    }

    public void tickServer() {
        unit.tick();
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public List<ItemSlot> guiSlots() {
        return unit.type == BufferType.ITEM ? unit.slots() : unit.ioSlots();
    }

    @Override
    public String guiTier() {
        return unit.tier.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public int guiHeight() {
        return unit.guiHeight();
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return unit.guiParts();
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @NotNull
    @Override
    public UiValues uiValues() {
        if (values == null) {
            values = unit.values();
        }
        return values;
    }

    @Override
    public int guiValueCount() {
        return uiValues().size();
    }

    @Override
    public long guiValue(int index) {
        return uiValues().get(index);
    }

    @Override
    public SideConfig getSideConfig() {
        return unit.sides();
    }

    @Nullable
    public FluidTank getFluidTank() {
        return unit.fluidTank();
    }

    @Nullable
    public BasicGasTank getGasTank() {
        return unit.gasTank();
    }

    /** Client : remplissage a afficher (lisse). */
    public double shownFill(long gameTime) {
        return unit.shownFill(gameTime);
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        if (renderBox == null) {
            renderBox = new AABB(worldPosition);
        }
        return renderBox;
    }

    /** Remplissage (0..1). */
    public double fill() {
        return unit.fill();
    }

    int comparator() {
        return unit.comparator();
    }

    /** Etat lisible (clic droit). */
    Component status() {
        return unit.status();
    }

    // ------------------------------------------------------------------
    // Capacite et interface
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return unit.handles(cap) ? unit.capability(cap, side) : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        unit.invalidate();
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return standardMenu(id, inventory);
    }

    // ------------------------------------------------------------------
    // Sauvegarde (le contenu suit l'objet), envoi au client (jauges des fluides et des gaz)
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        unit.save(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        unit.load(tag);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        return unit.syncsContents() ? saveWithoutMetadata() : super.getUpdateTag();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return unit.syncsContents() ? ClientboundBlockEntityDataPacket.create(this) : null;
    }
}
