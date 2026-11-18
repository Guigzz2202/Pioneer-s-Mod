package fr.pioneers.tech.mechanics;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/** Objet combustible (sciure...). */
public class FuelItem extends Item {

    private final int burn;

    public FuelItem(int burn, Properties properties) {
        super(properties);
        this.burn = burn;
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> type) {
        return burn;
    }
}
