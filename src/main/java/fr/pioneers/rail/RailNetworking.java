package fr.pioneers.rail;

import fr.pioneers.naval.NavalNetworking;
import fr.pioneers.network.PacketRail;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.rail.block.StationTile;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.logistics.Delivery;
import fr.pioneers.rail.logistics.RailLibrary;
import fr.pioneers.rail.logistics.StationLogistics;
import fr.pioneers.rail.logistics.WorldStationStock;
import fr.pioneers.rail.net.RailNetwork;
import fr.pioneers.rail.train.Car;
import fr.pioneers.rail.train.CarType;
import fr.pioneers.rail.train.RailCarEntity;
import fr.pioneers.rail.train.Schedule;
import fr.pioneers.rail.train.Train;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 0.20.0 : fiches envoyees aux joueurs (train, gare) et modifications recues. Un joueur ne modifie un train qu'a 24 blocs d'un de ses
 * vehicules visibles, une gare qu'a 12 blocs de son panneau.
 * <p>
 * 0.20.5 : la fiche d'un train s'edite aussi a distance (depuis la carte) ; seuls monter a bord, l'inventaire et le detelage demandent
 * d'etre a 24 blocs. La fiche porte le groupe, les interruptions (et toute la bibliotheque), le service logistique et la livraison.
 */
public final class RailNetworking {

    private RailNetworking() {
    }

    public static void openTrain(ServerPlayer player, RailWorld world, Train t, int index) {
        CompoundTag tag = trainTag(world, t, index);
        tag.putBoolean("open", true);
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRail.Info(0, tag));
    }

    private static void refresh(ServerPlayer player, RailWorld world, Train t, int index) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRail.Info(0, trainTag(world, t, index)));
    }

    private static CompoundTag trainTag(RailWorld world, Train t, int index) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", t.id);
        tag.putString("name", t.name);
        tag.putBoolean("auto", t.auto);
        tag.putBoolean("canAuto", t.canRunAuto());
        tag.putByte("status", (byte) t.status.ordinal());
        tag.putInt("stop", t.stopIndex);
        tag.putFloat("speed", Math.abs(t.speed) / 0.8F);
        tag.putInt("index", index);
        RailNetwork.Station dest = t.destination == 0 ? null : world.net.station(t.destination);
        tag.putString("dest", dest == null ? "" : dest.name);
        tag.put("schedule", t.schedule.save());
        ListTag cars = new ListTag();
        for (Car c : t.cars) {
            CompoundTag ct = new CompoundTag();
            ct.putByte("type", (byte) c.type.ordinal());
            ct.putByte("tier", (byte) c.tier);
            ct.putBoolean("flip", c.flipped);
            ct.putFloat("fill", c.fill());
            ct.putInt("water", c.water);
            ct.putInt("cap", c.waterCapacity());
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
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (RailNetwork.Station s : world.net.stations()) {
            names.add(s.name);
        }
        ListTag nl = new ListTag();
        names.forEach(n -> nl.add(StringTag.valueOf(n)));
        tag.put("stations", nl);
        //0.20.5
        tag.putString("group", t.group);
        ListTag groups = new ListTag();
        world.library.groups().forEach(g -> groups.add(StringTag.valueOf(g.name)));
        tag.put("groups", groups);
        ListTag on = new ListTag();
        world.interruptsOf(t).forEach(n -> on.add(StringTag.valueOf(n)));
        tag.put("inter", on);
        ListTag lib = new ListTag();
        world.library.interrupts().forEach(i -> lib.add(i.save()));
        tag.put("library", lib);
        tag.putBoolean("logistic", t.logistic);
        tag.putInt("fuelPct", world.fuelPercent(t));
        tag.putByte("tempKind", (byte) t.tempKind.ordinal());
        tag.putString("tempName", t.tempName);
        tag.putInt("tempIndex", t.tempIndex);
        tag.put("temp", Schedule.saveStops(t.temp));
        Delivery d = world.dispatcher.delivery(t.delivery);
        if (d != null) {
            tag.put("delivery", deliveryTag(world, d));
        }
        return tag;
    }

    public static CompoundTag deliveryTag(RailWorld world, Delivery d) {
        CompoundTag dt = d.save();
        RailNetwork.Station from = world.net.station(d.from), to = world.net.station(d.to);
        dt.putString("fromName", from == null ? "?" : from.name);
        dt.putString("toName", to == null ? "?" : to.name);
        Train t = world.train(d.train);
        dt.putString("trainName", t == null ? "" : t.name.isEmpty() ? t.id.toString().substring(0, 8) : t.name);
        if (t != null) {
            dt.putLong("carried", RailDispatcher.carried(t, d.cargo));
        }
        return dt;
    }

    private static boolean near(ServerPlayer player, RailWorld world, Train t, double max) {
        for (Car c : t.cars) {
            RailCarEntity e = world.view(c.id);
            if (e != null && e.distanceToSqr(player) < max * max) {
                return true;
            }
        }
        return false;
    }

    public static void editTrain(ServerPlayer player, PacketRail.TrainEdit edit) {
        RailWorld world = RailWorld.get(player.serverLevel());
        Train t = world.train(edit.train());
        if (t == null) {
            //0.20.11 : la fiche des navires passe par le meme paquet
            NavalNetworking.editShip(player, edit);
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        //0.20.5 : a distance (carte) sauf monter a bord, inventaire, detelage
        int a = edit.action();
        if ((a == PacketRail.TrainEdit.BOARD || a == PacketRail.TrainEdit.INVENTORY || a == PacketRail.TrainEdit.DECOUPLE) && !near(player, world, t, 24)) {
            return;
        }
        int index = Math.max(0, Math.min(t.cars.size() - 1, edit.value()));
        switch (edit.action()) {
            case PacketRail.TrainEdit.REFRESH -> {
                refresh(player, world, t, index);
                return;
            }
            case PacketRail.TrainEdit.NAME -> t.name = edit.text().trim().length() > 32 ? edit.text().trim().substring(0, 32) : edit.text().trim();
            case PacketRail.TrainEdit.MODE -> world.setMode(t, edit.value() != 0);
            case PacketRail.TrainEdit.SCHEDULE -> {
                Schedule s = Schedule.load(edit.data() == null ? new CompoundTag() : edit.data());
                if (s.stops.size() <= 32) {
                    world.setSchedule(t, s, Math.max(0, Math.min(Math.max(0, s.stops.size() - 1), edit.value())));
                    //1.9 (MAJ RP) : XP de Logistique pour un horaire d'au moins deux arrets (une fois par jour et par train)
                    if (s.stops.size() >= 2) {
                        fr.pioneers.core.profile.PioneerApi.gain(player, "logistics.route", 1,
                              net.minecraft.core.BlockPos.of(t.id.getMostSignificantBits() ^ t.id.getLeastSignificantBits()));
                    }
                }
            }
            case PacketRail.TrainEdit.BOARD -> {
                world.board(player, t, index);
                return;
            }
            case PacketRail.TrainEdit.DECOUPLE -> {
                if (!t.auto && Math.abs(t.speed) < 0.01F && edit.value() >= 0 && edit.value() < t.cars.size() - 1) {
                    world.split(t, edit.value(), false);
                    Train kept = world.train(t.id);
                    if (kept != null) {
                        refresh(player, world, kept, 0);
                    }
                }
                return;
            }
            case PacketRail.TrainEdit.OPEN -> {
                openTrain(player, world, t, index);
                return;
            }
            case PacketRail.TrainEdit.INVENTORY -> {
                world.openInventory(player, t, index);
                return;
            }
            case PacketRail.TrainEdit.SKIP -> {
                if (!t.schedule.isEmpty()) {
                    world.cancelTemp(t);
                    world.setSchedule(t, t.schedule, Math.floorMod(edit.value(), t.schedule.stops.size()));
                }
            }
            case PacketRail.TrainEdit.GROUP -> world.setGroup(t, edit.text());
            case PacketRail.TrainEdit.INTERRUPTS -> {
                List<String> names = new ArrayList<>();
                for (Tag n : edit.data().getList("names", Tag.TAG_STRING)) {
                    names.add(n.getAsString());
                }
                world.setInterrupts(t, names);
            }
            case PacketRail.TrainEdit.LOGISTIC -> world.setLogistic(t, edit.value() != 0);
            case PacketRail.TrainEdit.INTERRUPT_PUT -> {
                RailLibrary.Interrupt i = RailLibrary.Interrupt.load(edit.data());
                world.putInterrupt(edit.text(), i);
            }
            case PacketRail.TrainEdit.INTERRUPT_DEL -> world.removeInterrupt(edit.text());
            case PacketRail.TrainEdit.SEND -> world.sendTo(t, edit.text().trim());
            case PacketRail.TrainEdit.CANCEL_TEMP -> world.cancelTemp(t);
            default -> {
            }
        }
        world.setDirty();
        refresh(player, world, t, index);
    }

    // ------------------------------------------------------------------ gares

    public static void openStation(ServerPlayer player, ServerLevel level, BlockPos pos) {
        sendStation(player, level, pos, true);
    }

    private static void sendStation(ServerPlayer player, ServerLevel level, BlockPos pos, boolean open) {
        RailWorld world = RailWorld.get(level);
        RailNetwork.Station st = world.net.station(pos);
        if (st == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.asLong());
        tag.putString("name", st.name);
        tag.putInt("limit", st.limit);
        int coming = 0, here = 0, same = 0;
        for (Train t : world.trains()) {
            if (t.auto && t.destination == pos.asLong()) {
                coming++;
                if (t.status == Train.Status.AT_STATION) {
                    here++;
                }
            }
        }
        for (RailNetwork.Station s : world.net.stations()) {
            if (s.name.equalsIgnoreCase(st.name)) {
                same++;
            }
        }
        tag.putInt("coming", coming);
        tag.putInt("here", here);
        tag.putInt("same", same);
        //0.20.5 : logistique de la gare, stock du quai, reseau robotique, groupes
        tag.put("logi", st.logistics.save());
        ListTag stock = new ListTag();
        for (Map.Entry<Cargo, Long> e : world.stocks.stock(st).entrySet()) {
            CompoundTag c = e.getKey().save();
            c.putLong("n", e.getValue());
            stock.add(c);
        }
        tag.put("stock", stock);
        tag.putBoolean("network", world.stocks.network(st, Cargo.ENERGY) >= 0);
        ListTag groups = new ListTag();
        world.library.groups().forEach(g -> groups.add(StringTag.valueOf(g.name)));
        tag.put("groups", groups);
        int posts = world.stocks instanceof WorldStationStock ws ? ws.posts(st).size() : 0;
        tag.putInt("posts", posts);
        tag.putBoolean("open", open);
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRail.Info(1, tag));
    }

    public static void editStation(ServerPlayer player, PacketRail.StationEdit edit) {
        if (NavalNetworking.editHarbor(player, edit)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || player.distanceToSqr(edit.pos().getCenter()) > 144 || !level.isLoaded(edit.pos())) {
            return;
        }
        if (level.getBlockEntity(edit.pos()) instanceof StationTile tile) {
            if (!edit.name().isBlank()) {
                tile.rename(level, edit.name());
            }
            tile.setLimit(level, edit.limit());
            RailWorld world = RailWorld.get(level);
            RailNetwork.Station st = world.net.station(edit.pos());
            if (st != null && !edit.logistics().isEmpty()) {
                st.logistics = StationLogistics.load(edit.logistics());
                world.setDirty();
            }
            tile.refresh(level);
            sendStation(player, level, edit.pos(), false);
        }
    }
}
