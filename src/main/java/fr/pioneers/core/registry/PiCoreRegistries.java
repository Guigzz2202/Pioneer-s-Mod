package fr.pioneers.core.registry;

import fr.pioneers.Pioneers;
import fr.pioneers.core.block.DescribedBlockItem;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registres vanilla/Forge du socle : blocs, objets et menus enregistres sans les aides de Mekanism. */
public final class PiCoreRegistries {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Pioneers.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Pioneers.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Pioneers.MODID);

    private PiCoreRegistries() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
    }

    /** Bloc avec un objet qui affiche sa description ({@code description.pioneers.<nom>}). */
    public static <B extends Block> BlockEntry<B> block(String name, Supplier<B> block) {
        return block(name, block, b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers." + name));
    }

    /** Bloc avec un objet simple. */
    public static <B extends Block> BlockEntry<B> plainBlock(String name, Supplier<B> block) {
        return block(name, block, b -> new BlockItem(b, new Item.Properties()));
    }

    public static <B extends Block> BlockEntry<B> block(String name, Supplier<B> block, Function<B, ? extends BlockItem> item) {
        RegistryObject<B> registered = BLOCKS.register(name, block);
        RegistryObject<? extends BlockItem> blockItem = ITEMS.register(name, () -> item.apply(registered.get()));
        return new BlockEntry<>(registered, blockItem);
    }
}
