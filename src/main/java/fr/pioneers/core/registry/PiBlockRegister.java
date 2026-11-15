package fr.pioneers.core.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

/**
 * Enregistrement d'un bloc et de son objet dans les registres du socle ({@link PiCoreRegistries}) ; memes signatures que l'ancien registre
 * de blocs de Mekanism, pour garder les ids et les appels existants.
 */
public final class PiBlockRegister {

    private final List<BlockRegistryObject<?, ?>> all = new ArrayList<>();

    public BlockRegistryObject<Block, BlockItem> register(String name, BlockBehaviour.Properties properties) {
        return register(name, () -> new Block(properties));
    }

    public <B extends Block> BlockRegistryObject<B, BlockItem> register(String name, Supplier<? extends B> block) {
        return registerDefaultProperties(name, block, BlockItem::new);
    }

    public <B extends Block, I extends BlockItem> BlockRegistryObject<B, I> registerDefaultProperties(String name, Supplier<? extends B> block,
          BiFunction<B, Item.Properties, I> item) {
        return register(name, block, b -> item.apply(b, new Item.Properties()));
    }

    public <B extends Block, I extends BlockItem> BlockRegistryObject<B, I> register(String name, Supplier<? extends B> block, Function<B, I> item) {
        RegistryObject<B> registered = PiCoreRegistries.BLOCKS.register(name, block);
        RegistryObject<I> blockItem = PiCoreRegistries.ITEMS.register(name, () -> item.apply(registered.get()));
        BlockRegistryObject<B, I> entry = new BlockRegistryObject<>(registered, blockItem);
        all.add(entry);
        return entry;
    }

    /** Tous les blocs enregistres par ce registre, dans l'ordre. */
    public List<BlockRegistryObject<?, ?>> getAllBlocks() {
        return Collections.unmodifiableList(all);
    }
}
