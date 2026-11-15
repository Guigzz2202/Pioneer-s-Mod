package fr.pioneers.building.lighting;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * 1.0.4-beta (lot 5, « Lumiere ») : blocs de l'eclairage. Six luminaires de l'age du bronze a l'electrique ({@link LightFixtureBlock.Kind} :
 * applique, lanterne et lustre de bronze ; applique electrique, plafonnier, lampe-cage industrielle) et trois interrupteurs
 * ({@link LightSwitchBlock.Style} : levier de bois, levier de bronze, interrupteur electrique). Liaisons : {@link LightLinks}. Ressources :
 * {@code tools/gc/lighting/lighting.py} (modeles, etats, textures, recettes, butins) ; langue {@code tools/gc/core/lang_p104.py} ; guide
 * {@code tools/gc/lighting/guide_chapter.py}.
 */
public final class PiLighting {

    private PiLighting() {
    }

    public static void init() {
    }

    private static BlockBehaviour.Properties props(int age) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().noOcclusion().pushReaction(PushReaction.DESTROY);
        return age == 0 ? p.mapColor(MapColor.WOOD).strength(0.5F).sound(SoundType.WOOD)
              : age == 1 ? p.mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.LANTERN)
              : p.mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.METAL);
    }

    public static final Map<LightFixtureBlock.Kind, BlockEntry<LightFixtureBlock>> FIXTURES = new EnumMap<>(LightFixtureBlock.Kind.class);
    public static final Map<LightSwitchBlock.Style, BlockEntry<LightSwitchBlock>> SWITCHES = new EnumMap<>(LightSwitchBlock.Style.class);

    static {
        for (LightSwitchBlock.Style s : LightSwitchBlock.Style.values()) {
            SWITCHES.put(s, PiCoreRegistries.block(s.id, () -> new LightSwitchBlock(s, props(s.age))));
        }
        for (LightFixtureBlock.Kind k : LightFixtureBlock.Kind.values()) {
            FIXTURES.put(k, PiCoreRegistries.block(k.id, () -> new LightFixtureBlock(k, props(k.age))));
        }
    }

    /** Tous les blocs (interrupteurs puis luminaires, par age) dans l'ordre de l'onglet creatif. */
    public static List<BlockEntry<? extends Block>> all() {
        List<BlockEntry<? extends Block>> out = new ArrayList<>(SWITCHES.values());
        out.addAll(FIXTURES.values());
        return out;
    }
}
