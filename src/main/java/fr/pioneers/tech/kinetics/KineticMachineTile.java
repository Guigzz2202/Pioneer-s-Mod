package fr.pioneers.tech.kinetics;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.RedstoneMode;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 7) : ADAPTATEUR CINETIQUE. La tile reste un {@link KineticTile} (noeud du reseau de rotation : liens, phase, rendu
 * cinetique, synchro de la vitesse, contraintes) et porte les MEMES unites que {@code MachineTile} ({@link MachineUnit} : procede, generateur)
 * : elle leur prete un {@link UnitHost} (monde, position, cases dans l'ordre d'affichage et de sauvegarde, envoi au client) et son energie
 * motrice {@link PowerSource.Kinetic} (vitesse, couple et puissance du reseau LUS sur le noeud ; couple de charge DECLARE par l'unite et rendu
 * au reseau par {@link #loadTorque()}). Interface standard v2 ({@link MachineMenu}, valeurs NOMMEES declarees par l'unite, zone et
 * inventaire de {@link StandardLayout}).
 * <p>
 * Sauvegarde : les cles de {@link KineticTile} puis celles de l'unite (celles d'avant sa migration : mondes existants).
 */
public abstract class KineticMachineTile extends KineticTile implements MachineGui, MenuProvider {

    /** Rotation du noeud, pretee a l'unite. */
    protected final PowerSource.Kinetic power = new PowerSource.Kinetic(() -> speed, () -> torque, () -> network == null ? 0 : network.power());
    protected final UnitHost host = new Host();
    private final List<ItemSlot> slots = new ArrayList<>();
    private LazyOptional<ItemSlots> items = LazyOptional.empty();
    @Nullable
    private MachineUnit unit;
    @Nullable
    private UiValues named;
    /** 1.0.6 (Guillaume : dynamo « au norme des machines compactes ») : faces reglables (onglet Faces) d'une machine electrique cinetique. */
    @Nullable
    private SideConfig sides;
    /** 1.0.6 : commande redstone (onglet Redstone) ; {@link #redstoneTab} : la machine la propose. */
    private RedstoneMode redstone = RedstoneMode.IGNORED;
    private boolean redstoneTab;

    protected KineticMachineTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Pose l'unite de la machine (dans le constructeur, apres la creation de ses cases). */
    protected <U extends MachineUnit> U carry(U unit) {
        this.unit = unit;
        return unit;
    }

    @Nullable
    public MachineUnit unit() {
        return unit;
    }

    /**
     * 1.0.6 : faces reglables d'une ressource (energie d'une dynamo ou d'un moteur), comme les machines compactes : onglet Faces, reglage
     * sauve. Facade = {@code FACING} du bloc s'il en a une.
     */
    protected SideConfig enableSides(SideType type, SideMode initial, SideMode... allowed) {
        sides = new SideConfig(EnumSet.of(SideMode.NONE, allowed), initial, false, this::sideFacing, this::setChanged).addType(type);
        return sides;
    }

    private Direction sideFacing() {
        BlockState st = getBlockState();
        return st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
               ? st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) : Direction.NORTH;
    }

    /** 1.0.6 : la face {@code side} laisse-t-elle passer la ressource (pas de faces reglables : oui) ? */
    public boolean sideOpen(SideType type, @Nullable Direction side) {
        return sides == null || side == null || sides.mode(type, side) != SideMode.NONE;
    }

    /** 1.0.6 : commande redstone proposee (onglet Redstone, comme les machines compactes). */
    protected void enableRedstone() {
        redstoneTab = true;
    }

    public boolean hasRedstoneTab() {
        return redstoneTab;
    }

    @Override
    public boolean redstoneTab() {
        return redstoneTab;
    }

    /** La commande redstone laisse-t-elle travailler la machine ? */
    public boolean redstoneAllows() {
        return !redstoneTab || level == null || redstone.allows(level.hasNeighborSignal(worldPosition));
    }

    /** Cases declarees par l'unite (ordre d'affichage et de sauvegarde). */
    protected List<ItemSlot> slots() {
        return slots;
    }

    // ------------------------------------------------------------------
    // Reseau et travail
    // ------------------------------------------------------------------

    @Override
    public double loadTorque() {
        return power.load();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (unit != null) {
            unit.tick();
        }
    }

    // ------------------------------------------------------------------
    // Client, sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void writeClient(CompoundTag tag) {
        if (unit != null) {
            unit.writeClient(tag);
        }
    }

    @Override
    protected void readClient(CompoundTag tag) {
        if (unit != null) {
            unit.readClient(tag);
        }
    }

    @Override
    protected boolean clientDirty() {
        return unit != null && unit.clientDirty();
    }

    /** La machine envoie-t-elle des donnees propres au client a chaque synchronisation de la rotation (objet travaille, etat) ? */
    protected boolean ownClientData() {
        return true;
    }

    @Override
    protected boolean hasOwnClientData() {
        return ownClientData();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (unit != null) {
            unit.save(tag);
        }
        if (sides != null) {
            tag.put("sideConfig", sides.save());
        }
        if (redstoneTab) {
            tag.putInt("redstone", redstone.ordinal());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (unit != null) {
            unit.load(tag);
        }
        if (sides != null && tag.contains("sideConfig")) {
            sides.load(tag.getCompound("sideConfig"));
        }
        if (redstoneTab) {
            redstone = RedstoneMode.byIndex(tag.getInt("redstone"));
        }
    }

    // ------------------------------------------------------------------
    // Capacites
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !slots.isEmpty()) {
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
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public List<ItemSlot> guiSlots() {
        return slots;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    public UiValues uiValues() {
        if (named == null) {
            UiValues.Builder b = UiValues.builder();
            if (unit != null) {
                unit.declareValues(b);
            }
            if (redstoneTab) {
                b.add(UiValues.REDSTONE, () -> redstone.ordinal());
            }
            named = b.build();
        }
        return named;
    }

    @Override
    public int guiValueCount() {
        return uiValues().size();
    }

    @Override
    public long guiValue(int index) {
        return uiValues().get(index);
    }

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return sides;
    }

    /** 1.0.6 : onglet Redstone (meme action que les machines compactes), sinon les actions communes (faces) et propres. */
    @Override
    public void handleAction(net.minecraft.server.level.ServerPlayer player, int action) {
        if (redstoneTab && action == MachineTile.REDSTONE) {
            redstone = redstone.next();
            setChanged();
            return;
        }
        MachineGui.super.handleAction(player, action);
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    /** 1.0.2-beta : reglage a valeur (curseur) : confie a l'unite. */
    @Override
    public void handleSetting(net.minecraft.server.level.ServerPlayer player, int setting, int value) {
        if (unit != null) {
            unit.setting(setting, value);
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return standardMenu(id, inventory);
    }

    /** Ce que la tile prete a son unite. */
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
            markClientsDirty();
        }

        @Override
        public void syncNow() {
            markClientsDirty();
        }

        @Override
        public ItemSlot slot(ItemSlot slot) {
            slots.add(slot);
            items = LazyOptional.of(() -> new ItemSlots(slots, Automation.EXTERNAL));
            return slot;
        }

        @Override
        public PowerSource power() {
            return power;
        }
    }
}
