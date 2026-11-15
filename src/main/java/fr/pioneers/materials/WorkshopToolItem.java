package fr.pioneers.materials;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.forge.smithing.HandForged;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outil d'atelier (marteau, lime, scie, couteau, mortier), comme dans GregTech : utilise dans une recette de la table de craft, il reste dans la
 * grille et perd un point de durabilite. Les recettes le designent par son tag {@code pioneers:tools/<outil>}.
 */
public class WorkshopToolItem extends Item {

    private final GeneratedMaterials.Entry entry;

    /** Fiche de l'outil (materiau, forme). */
    public GeneratedMaterials.Entry entry() {
        return entry;
    }

    public WorkshopToolItem(GeneratedMaterials.Entry entry, Properties properties) {
        super(properties);
        this.entry = entry;
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack used = stack.copy();
        used.setCount(1);
        used.setDamageValue(used.getDamageValue() + 1);
        return used.getDamageValue() >= used.getMaxDamage() ? ItemStack.EMPTY : used;
    }

    /** Forge primaire : +10 % de durabilite si l'outil a ete monte avec une piece forgee main. */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return HandForged.maxDamage(stack, super.getMaxDamage(stack));
    }

    @Override
    public boolean isRepairable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("tooltip.pioneers.workshop_tool");
        //1.0.4-beta (lot 28) : materiau, durabilite et tier : infobulle commune des equipements (ToolProfiles.ToolClientEvents, J:core/tooltip/GearTips)
    }
}
