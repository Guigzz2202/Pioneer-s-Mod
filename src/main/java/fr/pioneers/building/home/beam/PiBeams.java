package fr.pioneers.building.home.beam;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.building.home.HomeWoods;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * 1.0.4-beta (lot 28) : les POUTRES DECORATIVES, une par essence ({@code <bois>_timber_beam} pour les 11 bois de {@link HomeWoods#ALL}, puis
 * {@code treated_timber_beam} en bois traite a la creosote, l'essence du mod). Ids distincts du composant {@code wooden_beam} (« Poutre »,
 * piece de charpente des recettes). Fabrication : etabli du charpentier, scie, une buche (tige, bloc de bambou) -> une poutre ; 8 poutres
 * + un seau de creosote -> 8 poutres en bois traite ({@code tools/gc/home/beams.py}). Onglet Construction, section « poutres ».
 */
public final class PiBeams {

    private PiBeams() {
    }

    public static void init() {
    }

    /** Essences dans l'ordre du menu creatif : les bois vanilla puis le bois traite. */
    public static final List<String> WOODS;
    public static final List<BlockEntry<TimberBeamBlock>> BEAMS = new ArrayList<>();

    static {
        List<String> woods = new ArrayList<>(HomeWoods.ALL);
        woods.add("treated");
        WOODS = List.copyOf(woods);
        for (String w : WOODS) {
            boolean nether = w.equals("crimson") || w.equals("warped");
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                  .strength(2.0F, 3.0F).sound(w.equals("bamboo") ? SoundType.BAMBOO_WOOD : nether ? SoundType.NETHER_WOOD : SoundType.WOOD);
            if (!nether) {
                p = p.ignitedByLava();
            }
            BlockBehaviour.Properties props = p;
            BEAMS.add(PiCoreRegistries.block(id(w), () -> new TimberBeamBlock(props)));
        }
    }

    public static String id(String wood) {
        return wood + "_timber_beam";
    }
}
