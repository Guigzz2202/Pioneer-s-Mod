package fr.pioneers.world.season;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** 1.0.5-beta (lot 1) : registres des saisons : le calendrier (bloc mural et son objet). */
public final class PiSeasons {

    public static final BlockEntry<CalendarBlock> CALENDAR = PiCoreRegistries.block("calendar",
          () -> new CalendarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5F).sound(SoundType.WOOD).noOcclusion()
                .noCollission().ignitedByLava()),
          b -> new CalendarItem(b, new Item.Properties()));

    private PiSeasons() {
    }

    /** Charge les registres (appele par {@code Pioneers}) ; 1.0.5-beta (lot 2) : inscrit les saisons comme fournisseur de precipitations. */
    public static void init() {
        SeasonPrecipitation.register();
    }
}
