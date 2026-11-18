package fr.pioneers.tech.industry.engraving;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.industry.recipe.WearTool;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MAJ Recettes (lot 8a, spec « Le graveur optique ») : objectif de gravure, l'outil fait de lentilles, de filtres, de prismes et de
 * miroirs que le graveur optique use d'un point par gravure (objectif en verre 64, objectif UV 256, optique EUV 1 024). Use jusqu'au bout,
 * il devient l'objectif use du meme tier, que la rectifieuse reconditionne pour la moitie de son cout. Ni reparation a l'enclume ou a la
 * grille, ni enchantement : seule la rectifieuse le remet a neuf.
 */
public class EngravingObjectiveItem extends Item implements WearTool {

    private final Supplier<Item> worn;

    public EngravingObjectiveItem(int uses, Supplier<Item> worn, Properties properties) {
        super(properties.stacksTo(1).durability(uses).setNoRepair());
        this.worn = worn;
    }

    @Override
    public int usesLeft(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    @Override
    public ItemStack wear(ItemStack stack, int uses) {
        int damage = stack.getDamageValue() + Math.max(0, uses);
        if (damage >= stack.getMaxDamage()) {
            return new ItemStack(worn.get());
        }
        ItemStack out = stack.copyWithCount(1);
        out.setDamageValue(damage);
        return out;
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean isRepairable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("item.pioneers.engraving_objective.uses", usesLeft(stack), stack.getMaxDamage());
        tips.desc(getDescriptionId() + ".desc");
    }
}
