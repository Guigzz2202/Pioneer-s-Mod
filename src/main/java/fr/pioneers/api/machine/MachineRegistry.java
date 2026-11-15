package fr.pioneers.api.machine;

import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 1) : fiche &lt;-&gt; blocs. Les blocs de chaque fiche sont retrouves par leur id dans le registre differe du socle
 * ({@link PiCoreRegistries#BLOCKS}, meme chemin hors du jeu pour le banc des registres), une fois, a la premiere demande (apres
 * l'enregistrement). Au lot 1, l'enregistrement des blocs et des tiles reste dans les registres d'aujourd'hui (ids inchanges) ; le registre
 * ne fait que relier.
 */
public final class MachineRegistry {

    /** Bloc d'une fiche : la fiche, l'indice du bloc dans {@link MachineSpec#blocks()} et son tier affiche ({@link MachineSpec#tierOf}). */
    public record Entry(MachineSpec spec, int index) {

        public int tier() {
            return spec.tierOf(index);
        }
    }

    private static final Map<String, MachineSpec> BY_KEY = new HashMap<>();
    private static Map<Block, Entry> byBlock;
    private static Map<MachineSpec, List<Block>> blocks;
    private static Map<Block, String> stations;

    static {
        for (MachineSpec spec : GeneratedMachines.ALL) {
            BY_KEY.put(spec.key(), spec);
        }
    }

    private MachineRegistry() {
    }

    public static List<MachineSpec> all() {
        return GeneratedMachines.ALL;
    }

    @Nullable
    public static MachineSpec byKey(@Nullable String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /** Fiche du bloc (null : ce n'est pas une machine de l'API). */
    @Nullable
    public static Entry of(Block block) {
        return index().get(block);
    }

    /** Lot 7 : age d'un poste HORS API (etabli, forge primaire : {@link GeneratedMachines#STATION_AGES}), ou null. */
    @Nullable
    public static String stationAge(Block block) {
        index();
        return stations.get(block);
    }

    /** Blocs d'une fiche dans l'ordre des tiers (ceux qui existent). */
    public static List<Block> blocks(MachineSpec spec) {
        index();
        return blocks.getOrDefault(spec, List.of());
    }

    private static synchronized Map<Block, Entry> index() {
        if (byBlock != null) {
            return byBlock;
        }
        Map<String, Block> ids = new HashMap<>();
        for (RegistryObject<Block> ro : PiCoreRegistries.BLOCKS.getEntries()) {
            if (ro.isPresent()) {
                ids.put(ro.getId().getPath(), ro.get());
            }
        }
        Map<Block, Entry> map = new IdentityHashMap<>();
        Map<MachineSpec, List<Block>> lists = new IdentityHashMap<>();
        for (MachineSpec spec : GeneratedMachines.ALL) {
            List<Block> list = new ArrayList<>();
            for (int i = 0; i < spec.blocks().size(); i++) {
                Block b = ids.get(spec.blocks().get(i));
                if (b != null) {
                    map.put(b, new Entry(spec, i));
                    list.add(b);
                }
            }
            lists.put(spec, Collections.unmodifiableList(list));
        }
        Map<Block, String> st = new IdentityHashMap<>();
        GeneratedMachines.STATION_AGES.forEach((id, age) -> {
            Block b = ids.get(id);
            if (b != null) {
                st.put(b, age);
            }
        });
        stations = st;
        blocks = lists;
        byBlock = map;
        return map;
    }
}
