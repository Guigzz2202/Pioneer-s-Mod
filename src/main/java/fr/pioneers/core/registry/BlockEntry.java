package fr.pioneers.core.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

/** Un bloc et son objet, enregistres ensemble par le socle (sans Mekanism). */
public record BlockEntry<B extends Block>(RegistryObject<B> block, RegistryObject<? extends BlockItem> item) implements ItemLike, Supplier<B> {

    public B getBlock() {
        return block.get();
    }

    @Override
    public B get() {
        return block.get();
    }

    @NotNull
    @Override
    public Item asItem() {
        return item.get();
    }

    public ItemStack getItemStack() {
        return new ItemStack(asItem());
    }
}
