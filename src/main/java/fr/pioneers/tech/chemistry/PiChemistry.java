package fr.pioneers.tech.chemistry;

import fr.pioneers.tech.chemistry.OilFeature;
import fr.pioneers.tech.chemistry.OilSeepBlock;
import fr.pioneers.tech.chemistry.SeismographItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.space.PiFeatures;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.tech.industry.PiIndustry;

/**
 * Age de la chimie (0.15) : ce qui n'est pas genere par le catalogue de l'age industriel (machines, fluides, gaz et objets y sont) :
 * blocs du petrole « reel » (sable bitumineux, suintement), sismographe, mares et suintements a la generation du monde.
 */
public final class PiChemistry {

    private PiChemistry() {
    }

    public static void init() {
        PiIndustry.TAB.add(() -> SEISMOGRAPH.get());
        PiIndustry.TAB.add(() -> TAR_SAND.asItem());
        PiIndustry.TAB.add(() -> OIL_SEEP.asItem());
    }

    public static final BlockEntry<Block> TAR_SAND = PiCoreRegistries.block("tar_sand", () -> new Block(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_BLACK).strength(0.8F).sound(SoundType.SAND)));
    public static final BlockEntry<OilSeepBlock> OIL_SEEP = PiCoreRegistries.block("oil_seep", () -> new OilSeepBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.MUD).noOcclusion()));
    public static final RegistryObject<Item> SEISMOGRAPH = PiCoreRegistries.ITEMS.register("seismograph",
          () -> new SeismographItem(new Item.Properties().durability(512)));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> OIL = PiFeatures.FEATURES.register("oil_deposits", OilFeature::new);
}
