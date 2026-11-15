package fr.pioneers.farm.food;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Conserve spatiale : un plat scelle dans une boite (NBT {@code Dish} = id du plat). Piles de 16 par plat ; se mange comme le plat (meme
 * nourriture, meme saturation, memes effets : {@link #getFoodProperties(ItemStack, LivingEntity)}). Etiquette a la couleur du plat.
 */
public class CannedFoodItem extends FarmFoodItem {

    public static final String DISH = "Dish";

    public CannedFoodItem(FoodDef def, Properties properties) {
        super(def, properties.food(new FoodProperties.Builder().nutrition(1).saturationMod(0.1F).build()));
    }

    @Nullable
    public static Item dish(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(DISH)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(stack.getTag().getString(DISH)));
        return item == Items.AIR ? null : item;
    }

    public static ItemStack of(Item can, ItemStack dish) {
        ItemStack out = new ItemStack(can);
        out.getOrCreateTag().putString(DISH, BuiltInRegistries.ITEM.getKey(dish.getItem()).toString());
        return out;
    }

    /** Peut-on mettre cet objet en conserve ? Tout ce qui se mange, sauf une conserve. */
    public static boolean cannable(ItemStack stack) {
        return !stack.isEmpty() && stack.isEdible() && !(stack.getItem() instanceof CannedFoodItem);
    }

    @Override
    public @Nullable FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        Item dish = dish(stack);
        if (dish != null) {
            FoodProperties props = new ItemStack(dish).getFoodProperties(entity);
            if (props != null) {
                return props;
            }
        }
        return super.getFoodProperties(stack, entity);
    }

    @Override
    public Component getName(ItemStack stack) {
        Item dish = dish(stack);
        return dish == null ? super.getName(stack) : Component.translatable("item.pioneers.canned_food.of", dish.getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        Item dish = dish(stack);
        if (dish instanceof FarmFoodItem food) {
            effectsTooltip(food.def(), tooltip);
        } else if (dish == null) {
            tips.desc("farm.pioneers.food.can_empty");
        }
    }
}
