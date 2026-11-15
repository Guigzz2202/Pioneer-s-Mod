package fr.pioneers.space.tile;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * API Machine (lot 8c) : LOGIQUE DU CHUNKLOADER (code d'avant de {@link TileEntityChunkLoader}, deplace) : batterie (case declaree par
 * l'unite, seule case comme avant), consommation du rayon voulu, pause apres une panne, chunks forces / relaches. Cles sauvees :
 * {@code radius}, {@code loadedRadius}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : rayon voulu, rayon charge (-1 : rien), cout du rayon voulu (FE/t).
 */
public final class ChunkLoaderUnit extends MachineUnit {

    public static final UiValues.Key RADIUS = UiValues.Key.of("radius"), LOADED = UiValues.Key.of("loaded_radius"), COST = UiValues.Key.of("cost");

    private final ItemSlot battery;
    int radius = 1;
    /** Rayon actuellement charge (-1 = rien). */
    private int loadedRadius = -1;
    /** Pause apres une panne d'energie, pour ne pas charger / decharger en boucle. */
    int cooldown;

    public ChunkLoaderUnit(MachineTile.Access machine) {
        super(machine);
        battery = machine.batterySlot(TileEntityChunkLoader.BATTERY_X, TileEntityChunkLoader.BATTERY_Y);
    }

    int maxRadius() {
        return TileEntityChunkLoader.maxRadius(TieredMachineBlock.tierOf(machine.state()));
    }

    @Override
    public void tick() {
        machine.discharge(battery, TileEntityChunkLoader.COST_FE[maxRadius()] * 4);
        int wanted = -1;
        if (cooldown > 0) {
            cooldown--;
        } else if (machine.canWork()) {
            if (machine.use(TileEntityChunkLoader.COST_FE[radius])) {
                wanted = radius;
            } else {
                cooldown = 100;
            }
        }
        if (wanted != loadedRadius) {
            setLoaded(wanted);
        }
        machine.markWorking(loadedRadius >= 0);
    }

    /** Remplace les chunks forces par ceux du nouveau rayon. */
    void setLoaded(int newRadius) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int cx = pos().getX() >> 4, cz = pos().getZ() >> 4;
        if (loadedRadius >= 0) {
            for (int dx = -loadedRadius; dx <= loadedRadius; dx++) {
                for (int dz = -loadedRadius; dz <= loadedRadius; dz++) {
                    ForgeChunkManager.forceChunk(serverLevel, Pioneers.MODID, pos(), cx + dx, cz + dz, false, true);
                }
            }
        }
        if (newRadius >= 0) {
            for (int dx = -newRadius; dx <= newRadius; dx++) {
                for (int dz = -newRadius; dz <= newRadius; dz++) {
                    ForgeChunkManager.forceChunk(serverLevel, Pioneers.MODID, pos(), cx + dx, cz + dz, true, true);
                }
            }
        }
        loadedRadius = newRadius;
        machine.changed();
    }

    /** Reglage du joueur : rayon voulu, borne au tier. */
    void step(boolean up) {
        radius = Math.max(0, Math.min(maxRadius(), radius + (up ? 1 : -1)));
        cooldown = 0;
        machine.changed();
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(RADIUS, () -> radius).add(LOADED, () -> loadedRadius).add(COST, () -> TileEntityChunkLoader.getCost(radius));
    }

    @Override
    public void load(CompoundTag tag) {
        radius = tag.contains("radius") ? Math.max(0, Math.min(TileEntityChunkLoader.MAX_RADIUS, tag.getInt("radius"))) : 1;
        if (machine.state() != null) {
            radius = Math.min(radius, maxRadius());
        }
        loadedRadius = tag.contains("loadedRadius") ? tag.getInt("loadedRadius") : -1;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("radius", radius);
        tag.putInt("loadedRadius", loadedRadius);
    }
}
