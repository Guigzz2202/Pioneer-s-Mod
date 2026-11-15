package fr.pioneers.farm.storage;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 1.0.4-beta (lot 6) : blocs de stockage des recoltes ({@link CropStorageBlock}), un par entree de {@link GeneratedCropStorage} (table de
 * {@code tools/gc/farm2/crop_storage.py}). Caisse et cagettes : bois (hache) ; sac : laine (houe, comme la botte de paille). Compostables
 * comme la botte de paille (0,85). Recettes : 9 recoltes a l'etabli agricole, retour en 9 sur la grille.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PiCropStorage {

    private PiCropStorage() {
    }

    public static void init() {
    }

    private static final Map<String, BlockEntry<CropStorageBlock>> BLOCKS = new LinkedHashMap<>();

    private static BlockBehaviour.Properties props(CropStorageBlock.Style style) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of();
        return style == CropStorageBlock.Style.SACK ? p.mapColor(MapColor.WOOL).strength(0.6F).sound(SoundType.WOOL).noOcclusion()
              : p.mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion();
    }

    static {
        for (GeneratedCropStorage.Entry e : GeneratedCropStorage.ENTRIES) {
            BLOCKS.put(e.id(), PiCoreRegistries.block(e.id(), () -> new CropStorageBlock(e.style(), e.produce(), props(e.style()))));
        }
    }

    public static Map<String, BlockEntry<CropStorageBlock>> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    /** Ids dans l'ordre de la table (onglet creatif). */
    public static List<String> ids() {
        return new ArrayList<>(BLOCKS.keySet());
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BLOCKS.values().forEach(b -> ComposterBlock.COMPOSTABLES.putIfAbsent(b.asItem(), 0.85F)));
    }
}
