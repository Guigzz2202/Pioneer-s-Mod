package fr.pioneers.api.multiblock;

import fr.pioneers.api.machine.MachineRegistry;
import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.industry.IndustryMachine;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : fiche de multibloc &lt;-&gt; fiche machine du controleur &lt;-&gt; blocs controleurs &lt;-&gt; machine du catalogue
 * industriel. Les blocs sont retrouves par leur id dans le registre differe du socle (meme chemin hors du jeu pour le banc), une fois.
 */
public final class MultiblockRegistry {

    private static final Map<String, MultiblockSpec> BY_KEY = new HashMap<>();
    private static Map<Block, MultiblockSpec> byBlock;

    static {
        for (MultiblockSpec spec : GeneratedMultiblocks.ALL) {
            BY_KEY.put(spec.key(), spec);
        }
    }

    private MultiblockRegistry() {
    }

    public static List<MultiblockSpec> all() {
        return GeneratedMultiblocks.ALL;
    }

    @Nullable
    public static MultiblockSpec byKey(@Nullable String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /** Fiche du multibloc d'une machine du catalogue industriel (cle = id du controleur). */
    @Nullable
    public static MultiblockSpec of(IndustryMachine machine) {
        return machine.industrial ? BY_KEY.get(machine.multiId()) : null;
    }

    /** Fiche du multibloc dont ce bloc est le controleur, ou null. */
    @Nullable
    public static MultiblockSpec of(Block block) {
        return index().get(block);
    }

    /** Fiche machine du controleur (interface, infobulle, JEI). */
    @Nullable
    public static MachineSpec machine(MultiblockSpec spec) {
        return MachineRegistry.byKey(spec.machine());
    }

    /** Fiche de la machine compacte soeur, ou null. */
    @Nullable
    public static MachineSpec compact(MultiblockSpec spec) {
        return MachineRegistry.byKey(spec.compact());
    }

    private static synchronized Map<Block, MultiblockSpec> index() {
        if (byBlock != null) {
            return byBlock;
        }
        Map<String, Block> ids = new HashMap<>();
        for (RegistryObject<Block> ro : PiCoreRegistries.BLOCKS.getEntries()) {
            if (ro.isPresent()) {
                ids.put(ro.getId().getPath(), ro.get());
            }
        }
        Map<Block, MultiblockSpec> map = new IdentityHashMap<>();
        for (MultiblockSpec spec : GeneratedMultiblocks.ALL) {
            for (String id : spec.controller()) {
                Block b = ids.get(id);
                if (b != null) {
                    map.put(b, spec);
                }
            }
        }
        byBlock = map;
        return map;
    }
}
