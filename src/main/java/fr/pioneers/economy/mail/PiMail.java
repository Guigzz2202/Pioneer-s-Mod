package fr.pioneers.economy.mail;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** 1.0.4-beta (lot 29) : registres du courrier : la boite aux lettres (bloc, tuile) et la lettre. */
public final class PiMail {

    public static final BlockEntry<MailboxBlock> MAILBOX = PiCoreRegistries.block("mailbox", () -> new MailboxBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5F, 6.0F).sound(SoundType.LANTERN).noOcclusion()));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<MailboxTile>> MAILBOX_TILE = PiTileEntityTypes.VANILLA_TILES.register("mailbox",
          () -> BlockEntityType.Builder.of(MailboxTile::new, MAILBOX.getBlock()).build(null));

    public static final RegistryObject<Item> LETTER = PiCoreRegistries.ITEMS.register("letter", () -> new LetterItem(new Item.Properties().stacksTo(1)));

    private PiMail() {
    }

    /** Charge les registres (appele par {@code EconomyBootstrap.init}). */
    public static void init() {
        MailService.init();
        fr.pioneers.paperwork.PiPaperwork.init(); //1.0.5-beta (lot 16) : bacs a lettres et d archives
    }
}
