package fr.pioneers.core.registry;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

/**
 * Un bloc et son objet enregistres ensemble par {@link PiBlockRegister} (remplace l'ancien objet de registre de Mekanism, memes methodes).
 */
public class BlockRegistryObject<B extends Block, I extends Item> implements ItemLike, Supplier<B> {

    private final RegistryObject<B> block;
    private final RegistryObject<I> item;

    public BlockRegistryObject(RegistryObject<B> block, RegistryObject<I> item) {
        this.block = block;
        this.item = item;
    }

    public B getBlock() {
        return block.get();
    }

    @Override
    public B get() {
        return block.get();
    }

    @NotNull
    @Override
    public I asItem() {
        return item.get();
    }

    public ItemStack getItemStack() {
        return new ItemStack(asItem());
    }

    public ItemStack getItemStack(int count) {
        return new ItemStack(asItem(), count);
    }

    public ResourceLocation getRegistryName() {
        return block.getId();
    }

    public String getName() {
        return block.getId().getPath();
    }

    public String getTranslationKey() {
        return getBlock().getDescriptionId();
    }

}
