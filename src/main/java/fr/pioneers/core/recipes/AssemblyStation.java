package fr.pioneers.core.recipes;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Les quatre tables d'assemblage : chacune ne propose que les recettes de son poste.
 */
public enum AssemblyStation {
    /** Fusees et kit de station orbitale (1.9.2 : hall d'integration des fusees, multibloc). */
    ROCKET,
    /** Unites robotiques : drones, robots, rovers, tortues (1.9.2 : baie d'assemblage robotique, multibloc). */
    ROVER,
    /** Modules de fusee. */
    MODULE,
    /** Combinaisons spatiales et unites de support de vie. */
    EQUIPMENT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component getName() {
        return Component.translatable("assembler.pioneers.station." + id());
    }

    @Nullable
    public static AssemblyStation byId(String id) {
        for (AssemblyStation station : values()) {
            if (station.id().equals(id)) {
                return station;
            }
        }
        return null;
    }
}
