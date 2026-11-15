package fr.pioneers.logistics.rail;

import fr.pioneers.logistics.naval.NavalNetworking;
import fr.pioneers.logistics.rail.logistics.Delivery;
import fr.pioneers.logistics.rail.logistics.RailAlert;
import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.net.RailNetwork;
import fr.pioneers.logistics.rail.train.Train;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 0.20.5 : le chemin de fer sur la carte O (touche O) : voies par qualite et occupation (toutes les 2 s), signaux, gares, trains et leurs
 * vehicules, livraisons du repartiteur et alertes, autour du joueur. Ajoute a l'etat envoye par {@code RoboticsMapService}.
 */
public final class RailMapData {

    private RailMapData() {
    }

    private static boolean near(BlockPos p, ServerPlayer player, int view) {
        return Math.abs(p.getX() - player.getBlockX()) <= view && Math.abs(p.getZ() - player.getBlockZ()) <= view;
    }

    public static CompoundTag snapshot(ServerPlayer player, int view, boolean tracks) {
        ServerLevel level = player.serverLevel();
        RailWorld w = RailWorld.get(level);
        long now = level.getGameTime();
        CompoundTag tag = new CompoundTag();
        if (tracks) {
            LongArrayList anchors = new LongArrayList();
            IntArrayList info = new IntArrayList();
            for (Piece p : w.net.pieces()) {
                if (!near(p.anchor, player, view) || anchors.size() >= 20_000) {
                    continue;
                }
                anchors.add(p.key());
                info.add(p.shape.ordinal() | p.quality.ordinal() << 8 | p.kind.ordinal() << 12 | Math.min(3, p.route) << 17);
            }
            tag.putLongArray("tracks", anchors.toLongArray());
            tag.putIntArray("trackInfo", info.toIntArray());
        }
        //Occupation : toujours (elle bouge vite)
        LongArrayList busy = new LongArrayList();
        for (Train t : w.trains()) {
            for (long a : t.occupied) {
                busy.add(a);
            }
        }
        tag.putLongArray("busy", busy.toLongArray());
        ListTag trains = new ListTag();
        for (Train t : w.trains()) {
            if (t.cars.isEmpty() || !near(t.headBlock(w.net), player, view + 64)) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putUUID("id", t.id);
            c.putString("name", t.name.isEmpty() ? t.id.toString().substring(0, 8) : t.name);
            c.putByte("status", (byte) t.status.ordinal());
            c.putBoolean("auto", t.auto);
            c.putBoolean("logistic", t.logistic);
            c.putString("group", t.group);
            RailNetwork.Station dest = w.net.station(t.destination);
            c.putString("dest", dest == null ? "" : dest.name);
            c.putFloat("speed", Math.abs(t.speed) / 0.8F);
            c.putByte("temp", (byte) t.tempKind.ordinal());
            int[] xz = new int[t.cars.size() * 2];
            for (int i = 0; i < t.cars.size(); i++) {
                Vec3 p = t.pose(w.net, i).center();
                xz[2 * i] = (int) Math.round(p.x * 8);
                xz[2 * i + 1] = (int) Math.round(p.z * 8);
            }
            c.putIntArray("cars", xz);
            trains.add(c);
        }
        //0.20.11 : les navires (trace le long de la coque) et les ports passent par les memes listes
        NavalNetworking.mapShips(player, view, trains);
        tag.put("trains", trains);
        ListTag stations = new ListTag();
        for (RailNetwork.Station s : w.net.stations()) {
            if (!near(s.pos, player, view)) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putLong("pos", s.pos.asLong());
            c.putString("name", s.name);
            c.putByte("role", (byte) s.logistics.role.ordinal());
            c.putInt("limit", s.limit);
            int coming = 0;
            for (Train t : w.trains()) {
                if (t.auto && t.destination == s.pos.asLong()) {
                    coming++;
                }
            }
            c.putInt("coming", coming);
            stations.add(c);
        }
        NavalNetworking.mapHarbors(player, view, stations);
        tag.put("stations", stations);
        NavalNetworking.mapSea(player, view, tag);
        LongArrayList sig = new LongArrayList();
        IntArrayList asp = new IntArrayList();
        for (RailNetwork.Signal s : w.net.signals()) {
            if (near(s.pos, player, view)) {
                sig.add(s.pos.asLong());
                asp.add(s.aspect);
            }
        }
        tag.putLongArray("signals", sig.toLongArray());
        tag.putIntArray("aspects", asp.toIntArray());
        ListTag deliveries = new ListTag();
        for (Delivery d : w.dispatcher.deliveries()) {
            deliveries.add(RailNetworking.deliveryTag(w, d));
        }
        tag.put("deliveries", deliveries);
        ListTag alerts = new ListTag();
        for (RailAlert a : w.dispatcher.alerts()) {
            CompoundTag c = new CompoundTag();
            c.putByte("type", (byte) a.type().ordinal());
            c.putLong("pos", a.pos());
            c.putString("subject", a.subject());
            c.putInt("age", (int) Math.min(Integer.MAX_VALUE, (now - a.first()) / 20));
            alerts.add(c);
        }
        tag.put("alerts", alerts);
        return tag;
    }
}
