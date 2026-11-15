package fr.pioneers.bronze;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.api.multiblock.GeneratedHouseNbt;
import fr.pioneers.core.Automation;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.industry.multi.GeneratedNbtModels;
import fr.pioneers.industry.multi.NbtAnims;
import fr.pioneers.industry.multi.NbtFx;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base des blocs-entites de l'age du bronze (ateliers, fours, chaudiere, machines a vapeur, haut fourneau) : cases d'objets exposees aux
 * entonnoirs, interface standard ({@link MachineGui} + {@link MachineMenu}), synchronisation client limitee, tick serveur.
 * <p>
 * API Machine (lot 7) : une tile du bronze peut PORTER une unite de l'API ({@link #carry}) : elle lui prete un {@link UnitHost} (cases
 * dans l'ordre, envoi au client, etat du bloc) et son energie motrice ({@link #power()} : vapeur du volume de la machine, feu du foyer) ;
 * l'unite publie ses valeurs d'interface NOMMEES ({@link #uiValues()}) ; la tile garde la physique (echanges de vapeur, foyer, air des
 * soufflets) et appelle l'unite a chaque tick. Une tile qui porte une unite a l'interface standard v2 ({@link #v2()}).
 */
public abstract class BronzeTile extends PiBlockEntity implements MachineGui, MenuProvider {

    protected final List<ItemSlot> slots = new ArrayList<>();
    private LazyOptional<ItemSlots> items = LazyOptional.empty();
    private boolean clientDirty;
    private long lastSync;
    @Nullable
    private UnitHost host;
    @Nullable
    private MachineUnit unit;
    @Nullable
    private UiValues named;

    protected BronzeTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Ajoute une case (a appeler dans le constructeur, dans l'ordre d'affichage). */
    protected ItemSlot slot(ItemSlot slot) {
        slots.add(slot);
        items = LazyOptional.of(() -> new ItemSlots(slots, Automation.EXTERNAL));
        return slot;
    }

    protected void changed() {
        setChanged();
    }

    // ------------------------------------------------------------------
    // API Machine (lot 7) : unite portee
    // ------------------------------------------------------------------

    /** Ce que la tile prete a son unite (cases, monde, envoi au client, energie motrice). */
    protected final UnitHost host() {
        if (host == null) {
            host = new Host();
        }
        return host;
    }

    /** Pose l'unite de la machine (dans le constructeur). */
    protected <U extends MachineUnit> U carry(U unit) {
        this.unit = unit;
        return unit;
    }

    @Nullable
    public MachineUnit unit() {
        return unit;
    }

    /** Energie motrice pretee a l'unite (vapeur, feu) ; null : aucune. */
    @Nullable
    protected PowerSource power() {
        return null;
    }

    /** Interface standard v2 (zone et inventaire de {@link StandardLayout}) : les tiles qui portent une unite. */
    protected boolean v2() {
        return unit != null;
    }

    @Override
    public UiValues uiValues() {
        if (unit == null) {
            return null;
        }
        if (named == null) {
            UiValues.Builder b = UiValues.builder();
            unit.declareValues(b);
            named = b.build();
        }
        return named;
    }

    @Nullable
    private UiValues namedIndices;

    /**
     * Lot 8b : valeurs NOMMEES d'une tile sans unite qui garde ses indices d'avant ({@code guiValue} / {@code guiValueCount} redefinis) : la
     * cle n nomme la valeur d'indice n. A n'appeler que depuis un {@code uiValues()} redefini par une telle tile.
     */
    protected final UiValues namedIndices(UiValues.Key... keys) {
        if (namedIndices == null) {
            UiValues.Builder b = UiValues.builder();
            for (int i = 0; i < keys.length; i++) {
                int index = i;
                b.add(keys[i], () -> guiValue(index));
            }
            namedIndices = b.build();
        }
        return namedIndices;
    }

    private final class Host implements UnitHost {

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
        public void markSync() {
            markClient();
        }

        @Override
        public void syncNow() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        public ItemSlot slot(ItemSlot slot) {
            return BronzeTile.this.slot(slot);
        }

        @Nullable
        @Override
        public PowerSource power() {
            return BronzeTile.this.power();
        }
    }

    /** Tick serveur (appele par {@link BronzeBlock}). */
    public void serverTick(ServerLevel level) {
    }

    /** Tick client (particules, sons d'ambiance). */
    public void clientTick() {
    }

    /**
     * 1.0.4-beta (lot 25) : particules d'une structure maison en marche ({@code GeneratedNbtModels.house(key)} : haut de la cheminee du four
     * de potier, voute du four a coke, gueulard du haut fourneau, bain de la fonderie, couronne de la foreuse, dome des chaudieres) ; a
     * appeler du tick client du controleur quand il travaille ({@code on}). Points passes au monde comme la structure (facade du bloc).
     */
    /** 1.0.4-beta (lot 25) : le bloc est allume (etat {@code lit} : feu, cuisson, coulee en cours). */
    protected boolean lit() {
        BlockState st = getBlockState();
        return st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT);
    }

    protected void houseFx(String key, boolean on) {
        if (level == null || !on) {
            return;
        }
        GeneratedNbtModels.Anim a = GeneratedNbtModels.house(key);
        GeneratedHouseNbt.Shape s = GeneratedHouseNbt.of(key);
        if (a == null || s == null) {
            return;
        }
        BlockState st = getBlockState();
        Direction front = st.hasProperty(BronzeBlock.FACING) ? st.getValue(BronzeBlock.FACING) : Direction.NORTH;
        for (GeneratedNbtModels.Fx fx : a.fx()) {
            double[] p = NbtAnims.point(worldPosition, front, s.cx(), s.cy(), s.cz(), fx.x(), fx.y(), fx.z());
            NbtFx.tick(level, fx.kind(), p[0], p[1], p[2], 1, level.random);
        }
    }

    // ------------------------------------------------------------------
    // Synchronisation client (etat visible : feu, temperature, niveau...)
    // ------------------------------------------------------------------

    /** Demande un envoi au client (au plus toutes les {@code minTicks}). */
    protected void markClient() {
        clientDirty = true;
    }

    protected void flushClient(ServerLevel level, int minTicks) {
        if (clientDirty && level.getGameTime() - lastSync >= minTicks) {
            clientDirty = false;
            lastSync = level.getGameTime();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    protected void writeClient(CompoundTag tag) {
        if (unit != null) {
            unit.writeClient(tag);
        }
    }

    protected void readClient(CompoundTag tag) {
        if (unit != null) {
            unit.readClient(tag);
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeClient(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readClient(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            readClient(packet.getTag());
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde, contenu, capacites
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (!slots.isEmpty()) {
            tag.put("slots", ItemSlots.save(slots));
        }
        if (unit != null) {
            unit.save(tag);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (!slots.isEmpty()) {
            ItemSlots.load(slots, tag.getList("slots", 10));
        }
        if (unit != null) {
            unit.load(tag);
        }
    }

    public void dropContents(Consumer<ItemStack> drop) {
        for (ItemSlot slot : slots) {
            if (!slot.isEmpty()) {
                drop.accept(slot.getStack().copy());
                slot.setStack(ItemStack.EMPTY);
            }
        }
    }

    /** Faces qui exposent les cases aux entonnoirs (toutes par defaut). */
    protected boolean exposesItems(@Nullable Direction side) {
        return true;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !slots.isEmpty() && exposesItems(side)) {
            return items.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Override
    public List<ItemSlot> guiSlots() {
        return slots;
    }

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

    @Override
    public int guiValueCount() {
        UiValues v = uiValues();
        return v == null ? 0 : v.size();
    }

    @Override
    public long guiValue(int index) {
        UiValues v = uiValues();
        return v == null ? 0 : v.get(index);
    }

    @Override
    public int guiWidth() {
        return v2() ? StandardLayout.WIDTH : MachineGui.super.guiWidth();
    }

    @Override
    public int guiInventory() {
        return v2() ? StandardLayout.INVENTORY : MachineGui.super.guiInventory();
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return standardMenu(id, inventory);
    }
}
