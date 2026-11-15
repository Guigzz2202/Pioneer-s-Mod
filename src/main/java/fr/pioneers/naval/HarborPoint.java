package fr.pioneers.naval;

import fr.pioneers.rail.logistics.RailResources;
import fr.pioneers.rail.logistics.StationLogistics;
import fr.pioneers.robotics.Endpoint;
import fr.pioneers.robotics.LogisticResource;
import fr.pioneers.robotics.logistics.LogisticPoint;
import fr.pioneers.robotics.logistics.LogisticsRegistry;
import fr.pioneers.robotics.logistics.MarkerRole;
import fr.pioneers.robotics.logistics.PointConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : pont entre un port logistique et le reseau robotique (comme les postes de gare) : un port qui FOURNIT devient un demandeur (les
 * robots y apportent ses ressources jusqu'a la quantite voulue), un port qui DEMANDE un point a vider (les robots rangent ce que les navires
 * ont decharge), un port qui fait les deux un tampon. Acces par le dessus du bloc.
 */
public final class HarborPoint implements LogisticPoint {

    private final HarborTile tile;
    private PointConfig config = new PointConfig();
    private boolean registered;

    HarborPoint(HarborTile tile) {
        this.tile = tile;
    }

    void update(ServerLevel level, NavalWorld w) {
        Harbor h = w.harbor(tile.getBlockPos().asLong());
        PointConfig next = h == null ? null : configFor(h);
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

    void remove(@Nullable Level level) {
        if (registered && level != null) {
            LogisticsRegistry.get(level).removePoint(this);
        }
        registered = false;
    }

    @Nullable
    private static PointConfig configFor(Harbor h) {
        StationLogistics l = h.logistics;
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
        c.name = h.name.length() > PointConfig.NAME_LENGTH ? h.name.substring(0, PointConfig.NAME_LENGTH) : h.name;
        c.priority = l.priority;
        for (StationLogistics.Entry e : l.entries) {
            LogisticResource r = RailResources.toLogistic(e.cargo());
            if (r != null && c.entries.size() < PointConfig.MAX_ENTRIES) {
                c.entries.add(new PointConfig.Entry(r, role == MarkerRole.UNLOAD ? 0 : e.amount()));
            }
        }
        return c.entries.isEmpty() && role != MarkerRole.UNLOAD ? null : c;
    }

    @Nullable
    @Override
    public Level getPointLevel() {
        return tile.getLevel();
    }

    @Override
    public BlockPos getPointPos() {
        return tile.getBlockPos();
    }

    @Override
    public PointConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(PointConfig config) {
    }

    @Override
    public Endpoint endpoint() {
        return new Endpoint(tile.getBlockPos(), Direction.UP);
    }

    @Override
    public boolean isPointRemoved() {
        return tile.isRemoved() || !registered;
    }

    @Override
    public Component displayName() {
        return config.name.isEmpty() ? tile.getBlockState().getBlock().getName() : Component.literal(config.name);
    }

    @Override
    public ItemStack displayIcon() {
        return new ItemStack(tile.getBlockState().getBlock());
    }
}
