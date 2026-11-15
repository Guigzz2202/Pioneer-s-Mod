package fr.pioneers.client.minimap;

import fr.pioneers.client.rp.RpClient;
import fr.pioneers.naval.ShipEntity;
import fr.pioneers.rail.train.RailCarEntity;
import fr.pioneers.robotics.RobotUnitEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeColor;

/**
 * 1.10.3 : instantane des entites montrees par la minimap, refait tous les {@link #PERIOD} ticks (jamais a l'image) parmi les entites QUE
 * LE CLIENT CONNAIT (portee de suivi du serveur) : aucune position n'est demandee au serveur. Les membres du clan hors de portee viennent
 * a part ({@code ClanMapAccess.mates}).
 */
final class EntityMarkers {

    static final int PERIOD = 2;

    enum Type {
        HOSTILE, PASSIVE, ITEM, VEHICLE, SHIP, CART, ROBOT, PLAYER
    }

    record Marker(Type type, double x, double y, double z, float yaw, int color, String name) {
    }

    static List<Marker> markers = List.of();
    private static int ticks;

    private EntityMarkers() {
    }

    static void tick(ClientLevel level, LocalPlayer self, MinimapLayers l) {
        if (++ticks % PERIOD != 0) {
            return;
        }
        if (!l.entities && !l.players) {
            markers = List.of();
            return;
        }
        List<Marker> out = new ArrayList<>();
        double r2 = (double) l.radius * l.radius;
        for (Entity e : level.entitiesForRendering()) {
            if (e == self || e.isRemoved() || e.isInvisibleTo(self)) {
                continue;
            }
            double dx = e.getX() - self.getX(), dz = e.getZ() - self.getZ();
            boolean player = e instanceof Player;
            //joueurs : toute la portee de suivi ; autres entites : rayon et ecart vertical reglables
            if (!player && (dx * dx + dz * dz > r2 || Math.abs(e.getY() - self.getY()) > l.height)) {
                continue;
            }
            Type t = type(e);
            if (t == null || !shown(t, l)) {
                continue;
            }
            int color = switch (t) {
                case HOSTILE -> l.cHostile;
                case PASSIVE -> l.cPassive;
                case ITEM -> l.cItem;
                case PLAYER -> playerColor((Player) e, l);
                default -> 0xFFFFFF;
            };
            String name = "";
            if (player && l.playerNames) {
                name = ((Player) e).getGameProfile().getName();
            } else if (!player && l.names && e.hasCustomName() && e.getCustomName() != null) {
                name = e.getCustomName().getString();
            }
            out.add(new Marker(t, e.getX(), e.getY(), e.getZ(), e.getYRot(), color, name));
            if (out.size() >= 512) {
                break;
            }
        }
        markers = out;
    }

    private static boolean shown(Type t, MinimapLayers l) {
        return switch (t) {
            case PLAYER -> l.players;
            case HOSTILE -> l.entities && l.hostile;
            case PASSIVE -> l.entities && l.passive;
            case ITEM -> l.entities && l.items;
            case VEHICLE, SHIP, CART -> l.entities && l.vehicles;
            case ROBOT -> l.entities && l.robots;
        };
    }

    private static Type type(Entity e) {
        if (e instanceof Player) {
            return Type.PLAYER;
        }
        if (e instanceof RobotUnitEntity) {
            return Type.ROBOT;
        }
        if (e instanceof RailCarEntity) {
            return Type.VEHICLE;
        }
        if (e instanceof ShipEntity || e instanceof Boat) {
            return Type.SHIP;
        }
        if (e instanceof AbstractMinecart) {
            return Type.CART;
        }
        if (e instanceof ItemEntity) {
            return Type.ITEM;
        }
        if (e instanceof Enemy) {
            return Type.HOSTILE;
        }
        if (e instanceof Mob) {
            return Type.PASSIVE;
        }
        return null;
    }

    /** Couleur d'un joueur : teinture de son clan (recue avec son identite), sinon la couleur des joueurs. */
    private static int playerColor(Player p, MinimapLayers l) {
        RpClient.Seen s = RpClient.SEEN.get(p.getId());
        int dye = s == null ? -1 : s.clanColor();
        return dye < 0 ? l.cPlayer : DyeColor.byId(dye & 15).getFireworkColor() & 0xFFFFFF;
    }

    static void clear() {
        markers = List.of();
    }
}
