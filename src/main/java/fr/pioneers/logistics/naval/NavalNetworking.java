package fr.pioneers.logistics.naval;

import fr.pioneers.core.network.PacketRail;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.logistics.rail.CarMenu;
import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.StationLogistics;
import fr.pioneers.logistics.rail.train.Car;
import fr.pioneers.logistics.rail.train.CarType;
import fr.pioneers.logistics.rail.train.Schedule;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

/**
 * 0.20.11 : fiche d'un navire et d'un port. Elles reutilisent les ecrans du rail : la fiche du train (horaire, conditions, logistique,
 * envoi) recoit {@code naval} = vrai et montre les machines et les cales ; la fiche de gare sert au port (nom, limite, logistique). Les
 * actions arrivent par les memes paquets ; {@code RailNetworking} les renvoie ici quand l'id est celui d'un navire ou d'un port.
 */
public final class NavalNetworking {

    private NavalNetworking() {
    }

    public static void openShip(ServerPlayer player, NavalWorld w, Ship s) {
        CompoundTag tag = shipTag(w, s);
        tag.putBoolean("open", true);
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRail.Info(0, tag));
    }

    private static void refresh(ServerPlayer player, NavalWorld w, Ship s) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRail.Info(0, shipTag(w, s)));
    }

    static CompoundTag shipTag(NavalWorld w, Ship s) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", s.id);
        tag.putBoolean("naval", true);
        tag.putByte("kind", (byte) s.kind.ordinal());
        tag.putByte("tier", (byte) s.tier);
        tag.putString("name", s.name);
        tag.putBoolean("auto", s.auto);
        tag.putBoolean("canAuto", true);
        tag.putByte("status", (byte) s.status.ordinal());
        tag.putInt("stop", s.stopIndex);
        tag.putFloat("speed", s.speed * 20);
        tag.putInt("index", 0);
        Harbor dest = s.harbor == 0 ? null : w.harbor(s.harbor);
        tag.putString("dest", dest == null ? "" : dest.name);
        tag.put("schedule", s.schedule.save());
        ListTag cars = new ListTag();
        for (Car c : s.allCars()) {
            CompoundTag ct = new CompoundTag();
            ct.putByte("type", (byte) c.type.ordinal());
            ct.putByte("tier", (byte) c.tier);
            ct.putFloat("fill", c.fill());
            int fuel = 0;
            if (c.type == CarType.STEAM || c.type == CarType.TENDER) {
                for (int i = 0; i < c.items.getSlots(); i++) {
                    fuel += c.items.getStackInSlot(i).getCount();
                }
            }
            ct.putInt("fuel", fuel);
            ct.putInt("slots", c.items.getSlots() + (CarMenu.hasLine(c.type, c.tier) && !CarMenu.steam(c.type) ? 1000 : 0));
            ct.putLong("sa", c.storeAmount());
            ct.putLong("sc", c.storeCapacity());
            cars.add(ct);
        }
        tag.put("cars", cars);
        ListTag nl = new ListTag();
        w.harborNames().forEach(n -> nl.add(StringTag.valueOf(n)));
        tag.put("stations", nl);
        tag.putString("group", "");
        tag.put("groups", new ListTag());
        tag.put("inter", new ListTag());
        tag.put("library", new ListTag());
        tag.putBoolean("logistic", s.logistic);
        tag.putBoolean("canLogistic", s.kind.cargo != null);
        tag.putInt("fuelPct", w.fuelPercent(s));
        tag.putByte("tempKind", (byte) s.tempKind.ordinal());
        tag.putString("tempName", "");
        tag.putInt("tempIndex", s.tempIndex);
        tag.put("temp", Schedule.saveStops(s.temp));
        tag.putBoolean("docked", s.state == Ship.State.DOCKED);
        tag.putInt("seats", s.kind.seats(s.tier));
        NavalDispatcher.Delivery d = w.deliveries.get(s.id);
        if (d != null) {
            //Meme forme que les livraisons du rail (la fiche les lit avec Delivery.load)
            CompoundTag dt = new CompoundTag();
            dt.putUUID("id", s.id);
            dt.put("cargo", d.cargo().save());
            dt.putLong("amount", d.amount());
            dt.putLong("from", d.from());
            dt.putLong("to", d.to());
            dt.putUUID("train", s.id);
            dt.putString("depot", "");
            dt.putLong("created", 0);
            boolean atStop = s.state == Ship.State.DOCKED;
            int state = s.tempIndex == 0 ? atStop ? 1 : 0 : s.tempIndex == 1 ? atStop ? 3 : 2 : 4;
            dt.putByte("state", (byte) state);
            Harbor from = w.harbor(d.from()), to = w.harbor(d.to());
            dt.putString("fromName", from == null ? "?" : from.name);
            dt.putString("toName", to == null ? "?" : to.name);
            dt.putString("trainName", s.name);
            dt.putLong("carried", NavalWorld.carried(s, d.cargo()));
            tag.put("delivery", dt);
        }
        return tag;
    }

    private static boolean near(ServerPlayer player, NavalWorld w, Ship s, double max) {
        ShipEntity e = w.view(s.id);
        return e != null && e.distanceToSqr(player) < max * max;
    }

    /** Action de la fiche (paquet {@code TrainEdit} dont l'id est celui d'un navire). Vrai si c'etait bien un navire. */
    public static boolean editShip(ServerPlayer player, PacketRail.TrainEdit edit) {
        NavalWorld w = NavalWorld.get(player.serverLevel());
        Ship s = w.ship(edit.train());
        if (s == null) {
            return false;
        }
        if (player.isSpectator()) {
            return true;
        }
        int a = edit.action();
        if ((a == PacketRail.TrainEdit.BOARD || a == PacketRail.TrainEdit.INVENTORY) && !near(player, w, s, 24)) {
            return true;
        }
        switch (a) {
            case PacketRail.TrainEdit.REFRESH -> {
                refresh(player, w, s);
                return true;
            }
            case PacketRail.TrainEdit.NAME -> s.name = edit.text().trim().length() > 32 ? edit.text().trim().substring(0, 32) : edit.text().trim();
            case PacketRail.TrainEdit.MODE -> w.setMode(s, edit.value() != 0);
            case PacketRail.TrainEdit.SCHEDULE -> {
                Schedule sc = Schedule.load(edit.data() == null ? new CompoundTag() : edit.data());
                if (sc.stops.size() <= 32) {
                    w.setSchedule(s, sc, edit.value());
                }
            }
            case PacketRail.TrainEdit.BOARD -> {
                board(player, w, s);
                return true;
            }
            case PacketRail.TrainEdit.OPEN -> {
                openShip(player, w, s);
                return true;
            }
            case PacketRail.TrainEdit.INVENTORY -> {
                openHold(player, w, s, edit.value());
                return true;
            }
            case PacketRail.TrainEdit.SKIP -> w.skipTo(s, edit.value());
            case PacketRail.TrainEdit.LOGISTIC -> w.setLogistic(s, edit.value() != 0);
            case PacketRail.TrainEdit.SEND -> w.sendTo(s, edit.text().trim());
            case PacketRail.TrainEdit.CANCEL_TEMP -> w.cancelTemp(s);
            default -> {
            }
        }
        w.setDirty();
        refresh(player, w, s);
        return true;
    }

    /** Monter a bord d'un ferry (a quai, place libre). */
    static void board(ServerPlayer player, NavalWorld w, Ship s) {
        ShipEntity e = w.view(s.id);
        if (e == null || s.kind != ShipKind.FERRY) {
            return;
        }
        if (e.getPassengers().size() >= s.kind.seats(s.tier)) {
            player.displayClientMessage(Component.translatable("naval.pioneers.ship.full"), true);
            return;
        }
        player.startRiding(e, true);
    }

    /** Inventaire d'une machine ou d'une cale ({@code index} dans machines + cales). */
    static void openHold(ServerPlayer player, NavalWorld w, Ship s, int index) {
        var all = s.allCars();
        if (index < 0 || index >= all.size()) {
            return;
        }
        Car car = all.get(index);
        ShipEntity view = w.view(s.id);
        if (view == null || car.items.getSlots() <= 0 && !CarMenu.hasLine(car.type, car.tier)) {
            return;
        }
        NetworkHooks.openScreen(player, new CarMenu.Provider(s.id, car, view, index), buf -> {
            buf.writeVarInt(view.getId());
            buf.writeVarInt(car.items.getSlots());
            buf.writeByte(car.type.ordinal());
            buf.writeByte(car.tier);
            buf.writeUUID(s.id);
            buf.writeVarInt(index);
        });
    }

    /** Trois coups : le navire redevient un objet (contenu garde), s'il est arrete ou a quai. */
    public static void pickUp(ServerPlayer player, NavalWorld w, ShipEntity e) {
        Ship s = w.ship(e.ship);
        if (s == null) {
            return;
        }
        if (s.speed > 0.02F && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("naval.pioneers.ship.moving"), true);
            return;
        }
        w.remove(s.id);
        if (!player.getAbilities().instabuild || player.isShiftKeyDown()) {
            var stack = ShipItem.stack(s.kind, s.tier);
            stack.getOrCreateTag().put(ShipItem.CONTENTS, s.saveContents());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    // ------------------------------------------------------------------ carte O

    /** Navires autour du joueur, au format des trains de la carte (points le long de la coque, en huitiemes de bloc). */
    public static void mapShips(ServerPlayer player, int view, ListTag out) {
        NavalWorld w = NavalWorld.get(player.serverLevel());
        for (Ship s : w.ships()) {
            if (Math.abs(s.x - player.getX()) > view + 64 || Math.abs(s.z - player.getZ()) > view + 64) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putUUID("id", s.id);
            c.putString("name", s.name.isEmpty() ? s.id.toString().substring(0, 8) : s.name);
            c.putByte("status", (byte) s.status.ordinal());
            c.putBoolean("auto", s.auto);
            c.putBoolean("logistic", s.logistic);
            c.putString("group", "");
            Harbor dest = s.harbor == 0 ? null : w.harbor(s.harbor);
            c.putString("dest", dest == null ? "" : dest.name);
            c.putFloat("speed", s.speed * 20);
            c.putByte("temp", (byte) s.tempKind.ordinal());
            int n = ShipKind.length(s.tier);
            int[] xz = new int[n * 2];
            for (int i = 0; i < n; i++) {
                double along = n / 2.0 - 0.5 - i;
                xz[2 * i] = (int) Math.round((s.x + s.forwardX() * along) * 8);
                xz[2 * i + 1] = (int) Math.round((s.z + s.forwardZ() * along) * 8);
            }
            c.putIntArray("cars", xz);
            out.add(c);
        }
    }

    /**
     * 0.20.27 : mer de la carte O : liaisons des balises (routes tracees), balises, et route restante de chaque navire en marche, autour du
     * joueur (points en huitiemes de bloc).
     */
    public static void mapSea(ServerPlayer player, int view, CompoundTag tag) {
        NavalWorld w = NavalWorld.get(player.serverLevel());
        double px = player.getX(), pz = player.getZ(), reach = view + 256;
        ListTag lanes = new ListTag();
        for (SeaBeacon.Lane l : w.lanes()) {
            if (l.state != SeaBeacon.Lane.State.OK || !nearRoute(l.points, px, pz, reach)) {
                continue;
            }
            lanes.add(new IntArrayTag(pack(l.points, 0)));
        }
        tag.put("seaLanes", lanes);
        ListTag routes = new ListTag();
        for (Ship s : w.ships()) {
            if (s.state != Ship.State.SAILING || s.route.size() < 2 || !nearRoute(s.route, px, pz, reach)) {
                continue;
            }
            List<double[]> rest = new ArrayList<>();
            rest.add(new double[]{s.x, s.z});
            rest.addAll(s.route.subList(Math.min(s.routeIndex, s.route.size() - 1), s.route.size()));
            routes.add(new IntArrayTag(pack(rest, 0)));
        }
        tag.put("seaRoutes", routes);
        LongArrayList beacons = new LongArrayList();
        for (SeaBeacon b : w.beacons()) {
            BlockPos p = b.blockPos();
            if (Math.abs(p.getX() - px) <= view && Math.abs(p.getZ() - pz) <= view) {
                beacons.add(b.pos);
            }
        }
        tag.putLongArray("seaBeacons", beacons.toLongArray());
    }

    private static boolean nearRoute(List<double[]> pts, double px, double pz, double reach) {
        for (double[] p : pts) {
            if (Math.abs(p[0] - px) <= reach && Math.abs(p[1] - pz) <= reach) {
                return true;
            }
        }
        return false;
    }

    private static int[] pack(List<double[]> pts, int from) {
        int[] xz = new int[(pts.size() - from) * 2];
        for (int i = from; i < pts.size(); i++) {
            xz[2 * (i - from)] = (int) Math.round(pts.get(i)[0] * 8);
            xz[2 * (i - from) + 1] = (int) Math.round(pts.get(i)[1] * 8);
        }
        return xz;
    }

    /** Ports autour du joueur, au format des gares de la carte. */
    public static void mapHarbors(ServerPlayer player, int view, ListTag out) {
        NavalWorld w = NavalWorld.get(player.serverLevel());
        for (Harbor h : w.harbors()) {
            BlockPos p = h.blockPos();
            if (Math.abs(p.getX() - player.getBlockX()) > view || Math.abs(p.getZ() - player.getBlockZ()) > view) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putLong("pos", h.pos);
            c.putString("name", h.name);
            c.putByte("role", (byte) h.logistics.role.ordinal());
            c.putInt("limit", h.limit);
            c.putInt("coming", w.assigned(h, null));
            c.putBoolean("harbor", true);
            out.add(c);
        }
    }

    // ------------------------------------------------------------------ ports (fiche de gare)

    public static void openHarbor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        sendHarbor(player, level, pos, true);
    }

    private static void sendHarbor(ServerPlayer player, ServerLevel level, BlockPos pos, boolean open) {
        NavalWorld w = NavalWorld.get(level);
        Harbor h = w.harbor(pos.asLong());
        if (h == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putBoolean("harbor", true);
        tag.putString("name", h.name);
        tag.putInt("limit", h.limit);
        int coming = 0, here = 0, same = 0;
        for (Ship s : w.ships()) {
            if (s.harbor == h.pos) {
                coming++;
                if (s.state == Ship.State.DOCKED) {
                    here++;
                }
            }
        }
        for (Harbor o : w.harbors()) {
            if (o.name.equalsIgnoreCase(h.name)) {
                same++;
            }
        }
        tag.putInt("coming", coming);
        tag.putInt("here", here);
        tag.putInt("same", same);
        tag.put("logi", h.logistics.save());
        ListTag stock = new ListTag();
        Map<Cargo, Long> m = w.stock.get(h.pos);
        if (m != null) {
            for (Map.Entry<Cargo, Long> e : m.entrySet()) {
                CompoundTag c = e.getKey().save();
                c.putLong("n", e.getValue());
                stock.add(c);
            }
        }
        tag.put("stock", stock);
        tag.putBoolean("network", false);
        tag.put("groups", new ListTag());
        tag.putInt("posts", 1);
        tag.putByte("berth", (byte) h.berth.ordinal());
        tag.putByte("maxTier", (byte) h.maxTier);
        tag.putBoolean("open", open);
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRail.Info(1, tag));
    }

    /** Fiche de port modifiee (paquet {@code StationEdit} sur un port). Vrai si c'etait bien un port. */
    public static boolean editHarbor(ServerPlayer player, PacketRail.StationEdit edit) {
        ServerLevel level = player.serverLevel();
        NavalWorld w = NavalWorld.get(level);
        Harbor h = w.harbor(edit.pos().asLong());
        if (h == null) {
            return false;
        }
        if (player.isSpectator() || player.distanceToSqr(edit.pos().getCenter()) > 144) {
            return true;
        }
        String n = edit.name().trim();
        if (!n.isEmpty()) {
            h.name = n.length() > 64 ? n.substring(0, 64) : n;
        }
        h.limit = Math.max(-1, Math.min(64, edit.limit()));
        if (!edit.logistics().isEmpty()) {
            h.logistics = StationLogistics.load(edit.logistics());
        }
        w.setDirty();
        if (level.getBlockEntity(edit.pos()) instanceof HarborTile tile) {
            tile.refreshPoint();
        }
        sendHarbor(player, level, edit.pos(), false);
        return true;
    }
}
