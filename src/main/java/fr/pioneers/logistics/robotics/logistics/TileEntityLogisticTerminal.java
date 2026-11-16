package fr.pioneers.logistics.robotics.logistics;

import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Terminal logistique : le joueur y commande des objets parmi les stocks du reseau, et les unites les livrent dans sa reserve (27
 * emplacements) ou il vient les prendre. Pour le reseau, c'est un demandeur dont les demandes sont les commandes en cours (chaque
 * commande garde sa case : sa demande garde son identifiant) ; il n'est jamais une source.
 */
public class TileEntityLogisticTerminal extends BaseContainerBlockEntity implements LogisticPoint {

    public static final int SLOTS = 27, MAX_ORDERS = PointConfig.MAX_ENTRIES;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private String name = "";
    /** Commandes en cours : ressource (null : case libre) et quantite qui reste a livrer. */
    private final LogisticResource[] orders = new LogisticResource[MAX_ORDERS];
    private final long[] remaining = new long[MAX_ORDERS];
    private final long[] requested = new long[MAX_ORDERS];

    public TileEntityLogisticTerminal(BlockPos pos, BlockState state) {
        super(PiRobotics.TERMINAL_TILE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Commandes
    // ------------------------------------------------------------------

    /** Ajoute une commande (cumulee avec celle du meme objet) ; false si toutes les cases sont prises. */
    public boolean order(ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) {
            return false;
        }
        LogisticResource res = LogisticResource.of(stack);
        int free = -1;
        for (int i = 0; i < MAX_ORDERS; i++) {
            if (res.equals(orders[i])) {
                remaining[i] += count;
                requested[i] += count;
                setChanged();
                return true;
            }
            if (orders[i] == null && free < 0) {
                free = i;
            }
        }
        if (free < 0) {
            return false;
        }
        orders[free] = res;
        remaining[free] = count;
        requested[free] = count;
        setChanged();
        return true;
    }

    public void cancel(int index) {
        if (index >= 0 && index < MAX_ORDERS) {
            orders[index] = null;
            remaining[index] = requested[index] = 0;
            setChanged();
        }
    }

    /** Une unite vient de livrer : la commande avance (et disparait une fois complete). */
    public void delivered(LogisticResource res, long amount) {
        for (int i = 0; i < MAX_ORDERS && amount > 0; i++) {
            if (orders[i] != null && orders[i].covers(res)) {
                long used = Math.min(amount, remaining[i]);
                remaining[i] -= used;
                amount -= used;
                if (remaining[i] <= 0) {
                    orders[i] = null;
                    remaining[i] = requested[i] = 0;
                }
            }
        }
        setChanged();
    }

    /** 0.20.5 : ce qui reste a livrer, par ressource (le rail logistique peut l'apporter par train). */
    public Map<LogisticResource, Long> pending() {
        Map<LogisticResource, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < MAX_ORDERS; i++) {
            if (orders[i] != null && remaining[i] > 0) {
                out.merge(orders[i], remaining[i], Long::sum);
            }
        }
        return out;
    }

    /** Commandes pour l'interface : objet, demande, reste. */
    public ListTag ordersTag() {
        ListTag list = new ListTag();
        for (int i = 0; i < MAX_ORDERS; i++) {
            if (orders[i] != null) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("index", i);
                tag.put("item", orders[i].item().copyWithCount(1).save(new CompoundTag()));
                tag.putLong("requested", requested[i]);
                tag.putLong("remaining", remaining[i]);
                list.add(tag);
            }
        }
        return list;
    }

    private long count(LogisticResource res) {
        long count = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && res.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
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

    /** Un demandeur dont chaque case de commande est une demande : « garder ce que j'ai deja, plus ce qui reste a livrer ». */
    @Override
    public PointConfig getConfig() {
        PointConfig config = new PointConfig();
        config.role = MarkerRole.REQUESTER;
        config.name = name;
        config.priority = 1;
        for (int i = 0; i < MAX_ORDERS; i++) {
            if (orders[i] != null && remaining[i] > 0) {
                config.entries.add(new PointConfig.Entry(orders[i], count(orders[i]) + remaining[i]));
            } else {
                config.entries.add(new PointConfig.Entry(LogisticResource.any(Resource.ITEMS), 0));
            }
        }
        return config;
    }

    @Override
    public void setConfig(PointConfig config) {
        //Seul le nom se regle : les demandes sont les commandes du joueur
        name = config.name;
        setChanged();
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
        return name.isEmpty() ? getBlockState().getBlock().getName() : Component.literal(name);
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
    // Reserve
    // ------------------------------------------------------------------

    @NotNull
    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @NotNull
    @Override
    protected AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
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
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        name = tag.getString("name");
        for (int i = 0; i < MAX_ORDERS; i++) {
            orders[i] = null;
            remaining[i] = requested[i] = 0;
        }
        for (Tag entry : tag.getList("orders", Tag.TAG_COMPOUND)) {
            CompoundTag order = (CompoundTag) entry;
            int i = order.getInt("index");
            if (i >= 0 && i < MAX_ORDERS) {
                orders[i] = LogisticResource.load(order.getCompound("res"));
                remaining[i] = order.getLong("remaining");
                requested[i] = order.getLong("requested");
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putString("name", name);
        ListTag list = new ListTag();
        for (int i = 0; i < MAX_ORDERS; i++) {
            if (orders[i] != null) {
                CompoundTag order = new CompoundTag();
                order.putInt("index", i);
                order.put("res", orders[i].save());
                order.putLong("remaining", remaining[i]);
                order.putLong("requested", requested[i]);
                list.add(order);
            }
        }
        tag.put("orders", list);
    }

}
