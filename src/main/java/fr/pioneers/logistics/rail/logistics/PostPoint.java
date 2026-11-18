package fr.pioneers.logistics.rail.logistics;

import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.net.RailNetwork;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.logistics.LogisticPoint;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import fr.pioneers.logistics.robotics.logistics.MarkerRole;
import fr.pioneers.logistics.robotics.logistics.PointConfig;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.5 : pont entre une gare logistique et le reseau robotique. Chaque poste de son quai devient un point logistique : demandeur dans une
 * gare qui fournit (les robots y apportent ses ressources, jusqu'a la quantite a garder), point a vider dans une gare qui demande (les robots
 * rangent ce que les trains ont decharge), tampon dans une gare qui fait les deux. Les reglages viennent de la gare (pas de balise a poser).
 */
public final class PostPoint implements LogisticPoint {

    private final BlockEntity post;
    private final Direction access;
    private PointConfig config = new PointConfig();
    private boolean registered;

    public PostPoint(BlockEntity post, Direction access) {
        this.post = post;
        this.access = access;
    }

    /** 0.20.46 (audit) : tuile chargee (serveur) : le poste est connu du releve des quais ({@link WorldStationStock}). */
    public void loaded(@Nullable Level level) {
        WorldStationStock.postLoaded(level, post.getBlockPos());
    }

    /** Toutes les secondes (serveur) : inscrit, met a jour ou retire le point selon la gare du poste. */
    public void update(ServerLevel level, StationPost holder) {
        //0.20.46 (audit) : redit au releve des quais (filet si le chargement de la tuile n'a pas ete vu)
        WorldStationStock.postLoaded(level, post.getBlockPos());
        RailWorld w = RailWorld.get(level);
        long at = w.stocks.stationOf(post.getBlockPos());
        RailNetwork.Station s = at == 0 ? null : w.net.station(at);
        PointConfig next = s == null ? null : configFor(s, holder.holds());
        if (next == null) {
            remove(level);
            return;
        }
        config = next;
        if (!registered) {
            LogisticsRegistry.get(level).addPoint(this);
            registered = true;
        }
    }

    public void remove(@Nullable Level level) {
        WorldStationStock.postUnloaded(level, post.getBlockPos());
        if (registered && level != null) {
            LogisticsRegistry.get(level).removePoint(this);
        }
        registered = false;
    }

    @Nullable
    private static PointConfig configFor(RailNetwork.Station s, Set<Cargo.Kind> holds) {
        StationLogistics l = s.logistics;
        MarkerRole role = switch (l.role) {
            case SUPPLY -> MarkerRole.REQUESTER;
            case DEMAND -> MarkerRole.UNLOAD;
            case BOTH -> MarkerRole.BUFFER;
            default -> null;
        };
        if (role == null) {
            return null;
        }
        PointConfig c = new PointConfig();
        c.role = role;
        c.name = s.name.length() > PointConfig.NAME_LENGTH ? s.name.substring(0, PointConfig.NAME_LENGTH) : s.name;
        c.priority = l.priority;
        for (StationLogistics.Entry e : l.entries) {
            if (!holds.contains(e.cargo().kind())) {
                continue;
            }
            LogisticResource r = RailResources.toLogistic(e.cargo());
            if (r != null && c.entries.size() < PointConfig.MAX_ENTRIES) {
                c.entries.add(new PointConfig.Entry(r, role == MarkerRole.UNLOAD ? 0 : e.amount()));
            }
        }
        //Un demandeur sans ressource tenue par ce poste n'a rien a demander
        return c.entries.isEmpty() && role != MarkerRole.UNLOAD ? null : c;
    }

    @Nullable
    @Override
    public Level getPointLevel() {
        return post.getLevel();
    }

    @Override
    public BlockPos getPointPos() {
        return post.getBlockPos();
    }

    @Override
    public PointConfig getConfig() {
        return config;
    }

    /** Les reglages se font a la gare. */
    @Override
    public void setConfig(PointConfig config) {
    }

    @Override
    public Endpoint endpoint() {
        return new Endpoint(post.getBlockPos(), access);
    }

    @Override
    public boolean isPointRemoved() {
        return post.isRemoved() || !registered;
    }

    @Override
    public Component displayName() {
        return config.name.isEmpty() ? post.getBlockState().getBlock().getName() : Component.literal(config.name);
    }

    @Override
    public ItemStack displayIcon() {
        return new ItemStack(post.getBlockState().getBlock());
    }
}
