package fr.pioneers.robotics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Les ordres robotiques d'une dimension (transports et zones), partages par tous les joueurs.
 */
public class RoboticsOrders extends SavedData {

    public static final int MAX_ORDERS = 128;
    private static final String NAME = "pioneers_robotics";

    private final Map<Integer, RoboticOrder> orders = new LinkedHashMap<>();
    private int nextId = 1;
    /** Reseau passif : unites de chaque genre qui peuvent livrer en meme temps (PASSIVE_ALL : sans limite). */
    public static final int PASSIVE_ALL = 64;
    private final int[] passive = initialPassive();

    private static int[] initialPassive() {
        int[] limits = new int[UnitKind.values().length];
        Arrays.fill(limits, PASSIVE_ALL);
        return limits;
    }

    /** Les intendants reparent les degats (explosions) dans la couverture. */
    private boolean repairs = true;

    public boolean repairs() {
        return repairs;
    }

    public void setRepairs(boolean repairs) {
        if (this.repairs != repairs) {
            this.repairs = repairs;
            setDirty();
        }
    }

    public int[] passiveLimits() {
        return passive;
    }

    public void setPassiveLimit(UnitKind kind, int limit) {
        limit = Math.max(0, Math.min(PASSIVE_ALL, limit));
        if (passive[kind.ordinal()] != limit) {
            passive[kind.ordinal()] = limit;
            setDirty();
        }
    }

    public static RoboticsOrders get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RoboticsOrders::load, RoboticsOrders::new, NAME);
    }

    public Collection<RoboticOrder> all() {
        return orders.values();
    }

    public List<RoboticOrder> snapshot() {
        return new ArrayList<>(orders.values());
    }

    @Nullable
    public RoboticOrder get(int id) {
        return orders.get(id);
    }

    /** Ajoute (id 0) ou remplace un ordre ; false si refuse (trop d'ordres, ordre incoherent). */
    public boolean put(RoboticOrder order) {
        if (!order.isValid()) {
            return false;
        }
        RoboticOrder old = order.id > 0 ? orders.get(order.id) : null;
        if (old == null) {
            if (orders.size() >= MAX_ORDERS) {
                return false;
            }
            order.id = nextId++;
            order.delivered = 0;
        } else {
            //On garde l'avancement si la quantite a livrer n'a pas change
            order.delivered = old.amount == order.amount && old.recurring == order.recurring ? old.delivered : 0;
        }
        order.status = RoboticOrder.Status.WAITING;
        orders.put(order.id, order);
        setDirty();
        return true;
    }

    public void remove(int id) {
        if (orders.remove(id) != null) {
            setDirty();
        }
    }

    public static RoboticsOrders load(CompoundTag tag) {
        RoboticsOrders data = new RoboticsOrders();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        data.repairs = !tag.contains("repairs") || tag.getBoolean("repairs");
        if (tag.contains("passive")) {
            int[] saved = tag.getIntArray("passive");
            for (int i = 0; i < Math.min(saved.length, data.passive.length); i++) {
                data.passive[i] = Math.max(0, Math.min(PASSIVE_ALL, saved[i]));
            }
        }
        for (Tag entry : tag.getList("orders", Tag.TAG_COMPOUND)) {
            RoboticOrder order = RoboticOrder.load((CompoundTag) entry);
            //Le minage par zone est retire : ses anciennes zones disparaissent
            if (order.type == RoboticOrder.Type.ZONE && (order.task == RobotTask.MINER || order.task == RobotTask.COLLECTOR)) {
                continue;
            }
            if (order.id > 0) {
                data.orders.put(order.id, order);
                data.nextId = Math.max(data.nextId, order.id + 1);
            }
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("nextId", nextId);
        tag.putIntArray("passive", passive);
        tag.putBoolean("repairs", repairs);
        ListTag list = new ListTag();
        for (RoboticOrder order : orders.values()) {
            list.add(order.save(false));
        }
        tag.put("orders", list);
        return tag;
    }
}
