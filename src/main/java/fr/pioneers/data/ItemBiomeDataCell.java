package fr.pioneers.data;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.space.SolarSystem.Body;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cellule de donnees de biome : vierge, elle est remplie par le scanner de surface avec le releve d'un biome d'un astre.
 * Placee dans un analyseur de biomes, elle produit les donnees de ce biome (la cellule ne s'use pas).
 */
public class ItemBiomeDataCell extends Item {

    public static final String NBT_BODY = "body", NBT_BIOME = "biome";

    public ItemBiomeDataCell() {
        super(new Properties().stacksTo(16));
    }

    public static boolean isRecorded(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.getItem() instanceof ItemBiomeDataCell && tag != null && tag.contains(NBT_BIOME);
    }

    public static boolean isBlank(ItemStack stack) {
        return stack.getItem() instanceof ItemBiomeDataCell && !isRecorded(stack);
    }

    /** Cellule enregistree pour ce biome. */
    public static ItemStack recorded(Item item, Body body, String biome) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(NBT_BODY, body.name());
        tag.putString(NBT_BIOME, biome);
        return stack;
    }

    @Nullable
    public static Body getBody(ItemStack stack) {
        if (!isRecorded(stack)) {
            return null;
        }
        try {
            return Body.valueOf(stack.getTag().getString(NBT_BODY));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static String getBiome(ItemStack stack) {
        return isRecorded(stack) ? stack.getTag().getString(NBT_BIOME) : null;
    }

    public static Component biomeName(String biome) {
        ResourceLocation id = ResourceLocation.tryParse(biome);
        return id == null ? Component.literal(biome) : Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return isRecorded(stack) ? 1 : 16;
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isRecorded(stack);
    }

    @NotNull
    @Override
    public Rarity getRarity(@NotNull ItemStack stack) {
        return isRecorded(stack) ? Rarity.UNCOMMON : Rarity.COMMON;
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        String biome = getBiome(stack);
        if (biome == null) {
            return Component.translatable("item.pioneers.biome_data_cell.blank");
        }
        return Component.translatable("item.pioneers.biome_data_cell.recorded", biomeName(biome));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        Body body = getBody(stack);
        if (body == null) {
            tips.desc("item.pioneers.biome_data_cell.blank.desc");
        } else {
            tips.state("item.pioneers.biome_data_cell.body", Component.translatable(body.getTranslationKey()));
            tips.desc("item.pioneers.biome_data_cell.desc");
        }
    }
}
