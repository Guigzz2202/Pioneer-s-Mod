package fr.pioneers.logistics.robotics.logistics;

import fr.pioneers.logistics.robotics.RobotUnitEntity;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Places de recharge d'un noeud : chaque unite reserve une place (aerienne pour les drones, au sol pour les autres) ; les reservations
 * des unites disparues sont liberees.
 */
public final class ChargePads {

    private final Int2IntOpenHashMap taken = new Int2IntOpenHashMap();

    public ChargePads() {
        taken.defaultReturnValue(-1);
    }

    /** Place reservee (ou nouvelle) parmi {@code spots} ; null si toutes sont prises. */
    @Nullable
    public Vec3 reserve(RobotUnitEntity unit, List<Vec3> spots, int offset) {
        int index = taken.get(unit.getId());
        if (index >= 0 && index - offset >= 0 && index - offset < spots.size()) {
            return spots.get(index - offset);
        }
        for (int i = 0; i < spots.size(); i++) {
            if (!taken.containsValue(offset + i)) {
                taken.put(unit.getId(), offset + i);
                return spots.get(i);
            }
        }
        return null;
    }

    public boolean has(RobotUnitEntity unit) {
        return taken.containsKey(unit.getId());
    }

    public void leave(RobotUnitEntity unit) {
        taken.remove(unit.getId());
    }

    public int size() {
        return taken.size();
    }

    /** Oublie les unites disparues. */
    public void cleanup(Level level) {
        if (level instanceof ServerLevel server) {
            taken.keySet().removeIf(id -> !(server.getEntity(id) instanceof RobotUnitEntity unit) || !unit.isAlive());
        }
    }
}
