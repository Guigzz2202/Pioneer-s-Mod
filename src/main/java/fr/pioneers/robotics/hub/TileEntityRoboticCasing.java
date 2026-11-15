package fr.pioneers.robotics.hub;

import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.network.PacketTileAction;
import fr.pioneers.registries.PiRobotics;
import fr.pioneers.robotics.UnitKind;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Boitier du hub robotique (et base des ports et des portes de baie). Chaque piece connait le coin bas de son hub (le maitre) ; le maitre,
 * toujours un boitier, porte les donnees du hub ({@link RoboticHubData}) et les sauvegarde avec lui. Bouton : rappel des unites, genre.
 */
public class TileEntityRoboticCasing extends PiBlockEntity implements PacketTileAction.Handler, MenuProvider, MultiblockPort, MultiblockCore.House.Holder {

    public static final int ACTION_KIND = 10, ACTION_RECALL = 0;

    /** Coin bas du hub forme dont la piece fait partie (null : hors hub). */
    @Nullable
    private BlockPos master;
    /** Donnees du hub (maitre seulement ; gardees si le hub se defait). */
    @Nullable
    private RoboticHubData hub;
    private int ticks;
    /** Lot 8a : cycle de vie commun des multiblocs maison, tenu par le MAITRE du hub (coin bas du cube) : verification chaque seconde ou a la demande (pose / casse dans le cube : registre des hotes), revalidee au chargement. */
    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public net.minecraft.world.level.Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public net.minecraft.core.Direction front() {
            return net.minecraft.core.Direction.NORTH;
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (!(level instanceof ServerLevel server)) {
                return null;
            }
            RoboticHubValidator.Result r = RoboticHubValidator.validate(server, worldPosition);
            if (r == null) {
                //Une partie n'est pas chargee : on ne conclut rien
                return null;
            }
            if (r.valid()) {
                java.util.List<BlockPos> cells = new java.util.ArrayList<>();
                BlockPos max = worldPosition.offset(RoboticHubValidator.WIDTH - 1, RoboticHubValidator.HEIGHT - 1, RoboticHubValidator.WIDTH - 1);
                BlockPos.betweenClosed(worldPosition, max).forEach(c -> cells.add(c.immutable()));
                return MultiblockCore.House.Survey.ok(cells, r);
            }
            String key = r.error() != null && r.error().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey()
                  : MultiblockCore.House.UNFORMED;
            return MultiblockCore.House.Survey.fail(key, null, r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            HubStructure.form((ServerLevel) level, worldPosition, (RoboticHubValidator.Result) survey.data());
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            if (!survey.removal()) {
                HubStructure.unform((ServerLevel) level, TileEntityRoboticCasing.this);
            }
        }

        @Override
        public void houseChanged() {
            setChanged();
        }
    }, 20);

    public TileEntityRoboticCasing(BlockPos pos, BlockState state) {
        this(PiRobotics.CASING_TILE.get(), pos, state);
    }

    protected TileEntityRoboticCasing(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------
    // Hub
    // ------------------------------------------------------------------

    @Nullable
    public BlockPos getMaster() {
        return master;
    }

    public boolean isMaster() {
        return worldPosition.equals(master);
    }

    void setMaster(@Nullable BlockPos master) {
        BlockPos value = master == null ? null : master.immutable();
        if (Objects.equals(value, this.master)) {
            return;
        }
        this.master = value;
        setChanged();
        sync();
        onMasterChanged();
    }

    /** Le hub de la piece change (forme, defait) : les capacites exposees aussi. */
    protected void onMasterChanged() {
    }

    /** Donnees propres (maitre), creees au besoin. */
    RoboticHubData ownHub() {
        if (hub == null) {
            hub = new RoboticHubData(this);
        }
        return hub;
    }

    @Nullable
    RoboticHubData storedHub() {
        return hub;
    }

    /** Le hub forme dont la piece fait partie, ou null. */
    @Nullable
    public RoboticHubData getHub() {
        if (master == null || level == null) {
            return null;
        }
        if (isMaster()) {
            return hub != null && hub.isFormed() ? hub : null;
        }
        if (!level.isLoaded(master) || !(level.getBlockEntity(master) instanceof TileEntityRoboticCasing tile) || !tile.isMaster()) {
            return null;
        }
        return tile.hub != null && tile.hub.isFormed() ? tile.hub : null;
    }

    @Nullable
    public TileEntityRoboticCasing masterTile() {
        if (master == null || level == null || !level.isLoaded(master)) {
            return null;
        }
        return level.getBlockEntity(master) instanceof TileEntityRoboticCasing tile && tile.isMaster() ? tile : null;
    }

    void serverTick() {
        if (!isMaster() || hub == null || !(level instanceof ServerLevel server)) {
            return;
        }
        //Verification de la structure : au premier tick (rechargement), puis chaque seconde (verre retire...) : cycle commun
        ticks++;
        house.tick();
        if (!isMaster()) {
            return;
        }
        if (hub.isFormed() && hub.tick(server)) {
            sync();
        }
    }

    /** Renvoie l'etat aux clients (rendu, maitre). */
    void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    /** Lot 8a : un bloc a change dans le cube (registre des hotes) : le maitre revalide au prochain tick, une piece libre passe par la file. */
    @Override
    public void requestCheck() {
        if (isMaster()) {
            house.requestCheck();
        } else if (level instanceof ServerLevel server) {
            HubStructure.queue(server, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        house.removed(true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && master == null) {
            //Piece hors hub : on verifie la structure au prochain tick
            HubStructure.queue(server, worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("constants.pioneers.robotic_hub");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        TileEntityRoboticCasing tile = masterTile();
        return tile == null || tile.getHub() == null ? null : new RoboticHubMenu(id, inventory, tile);
    }

    @Override
    public void handleAction(ServerPlayer player, int action) {
        RoboticHubData data = getHub();
        if (data == null) {
            return;
        }
        if (action == ACTION_RECALL) {
            data.recallAll();
        } else if (action >= ACTION_KIND && action < ACTION_KIND + UnitKind.values().length) {
            data.setKind(UnitKind.values()[action - ACTION_KIND]);
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchronisation
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (master != null) {
            tag.put("master", NbtUtils.writeBlockPos(master));
        }
        if (hub != null && (hub.hasContents() || isMaster())) {
            tag.put("hub", hub.save());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        master = tag.contains("master") ? NbtUtils.readBlockPos(tag.getCompound("master")) : null;
        if (tag.contains("hub")) {
            ownHub().load(tag.getCompound("hub"));
        }
        if (master != null && isMaster() && hub != null) {
            //Rechargement : la structure est reverifiee au premier tick
            hub.setClientFormed(true, master);
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (master != null) {
            tag.put("master", NbtUtils.writeBlockPos(master));
        }
        if (isMaster() && hub != null) {
            CompoundTag data = new CompoundTag();
            hub.writeUpdateTag(data);
            tag.put("hubState", data);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        master = tag.contains("master") ? NbtUtils.readBlockPos(tag.getCompound("master")) : null;
        if (isMaster()) {
            RoboticHubData data = ownHub();
            data.setClientFormed(true, master);
            if (tag.contains("hubState")) {
                data.readUpdateTag(tag.getCompound("hubState"));
            }
        } else if (hub != null) {
            hub.setClientFormed(false, null);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        handleUpdateTag(tag == null ? new CompoundTag() : tag);
    }

    /** Le rendu du maitre couvre tout le hub et deborde sur le toit (coupole, gyrophare). */
    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        if (isMaster()) {
            return new AABB(worldPosition, worldPosition.offset(RoboticHubValidator.WIDTH, RoboticHubValidator.HEIGHT + 2, RoboticHubValidator.WIDTH));
        }
        return new AABB(worldPosition);
    }

    /**
     * Lot 8c : CONTRAT DE PORT COMMUN ({@link MultiblockPort#portHandler}) : le hub prete son energie et son tampon (comme son port propre,
     * {@code TileEntityRoboticPort}) a un port commun de sa structure.
     */
    @Nullable
    @Override
    public Object portHandler(fr.pioneers.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        RoboticHubData hub = getHub();
        if (hub == null) {
            return null;
        }
        return switch (type) {
            case ENERGY -> hub.energyContainer;
            case ITEM -> new fr.pioneers.core.inventory.ItemSlots(hub.getBuffer(), fr.pioneers.core.Automation.EXTERNAL);
            default -> null;
        };
    }
}
