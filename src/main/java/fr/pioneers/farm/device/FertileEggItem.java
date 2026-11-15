package fr.pioneers.farm.device;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.GeneratedAnimals;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Oeuf feconde : pondu par une poule quand un coq adulte est a 8 blocs (70 %), porte la race de la mere (NBT {@code Breed}) ; il n'eclot
 * qu'en couveuse. Teinte de la coquille selon la race ({@link GeneratedAnimals#EGG_COLOURS}).
 */
public class FertileEggItem extends Item {

    public static final String BREED = "Breed";

    public FertileEggItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(int breed) {
        ItemStack s = new ItemStack(PiFarmDevices.FERTILE_EGG.get());
        s.getOrCreateTag().putInt(BREED, breed);
        return s;
    }

    public static int breed(ItemStack stack) {
        return stack.hasTag() ? Math.max(0, Math.min(stack.getTag().getInt(BREED), AnimalSpecies.CHICKEN.breeds().length - 1)) : 0;
    }

    public static Component breedName(ItemStack stack) {
        return Component.translatable("farm.pioneers.breed.chicken." + AnimalSpecies.CHICKEN.breed(breed(stack)).id());
    }

    public static int colour(ItemStack stack) {
        int b = breed(stack);
        return b < GeneratedAnimals.EGG_COLOURS.length ? GeneratedAnimals.EGG_COLOURS[b] : 0xF0E6D2;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("farm.pioneers.device.egg.breed", breedName(stack));
        tips.desc("farm.pioneers.device.egg.hint");
    }
}
