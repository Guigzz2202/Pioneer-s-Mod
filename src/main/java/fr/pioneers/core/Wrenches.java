package fr.pioneers.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Outils de reglage reconnus (cles des mods techniques, configurateur de Mekanism) : sans dependance a un mod precis. */
public final class Wrenches {

    public static final TagKey<Item> TOOLS_WRENCH = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "tools/wrench"));
    public static final TagKey<Item> WRENCHES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "wrenches"));
    private static final ResourceLocation MEKANISM_CONFIGURATOR = ResourceLocation.fromNamespaceAndPath("mekanism", "configurator");

    private Wrenches() {
    }

    public static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(TOOLS_WRENCH) || stack.is(WRENCHES) || MEKANISM_CONFIGURATOR.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }
}
