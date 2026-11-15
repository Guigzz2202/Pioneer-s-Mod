package fr.pioneers.farm.food;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.GeneratedFarm;
import fr.pioneers.farm.PiFarm;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Objet de cuisine du mod (ingredient, plat, boisson) d'apres sa {@link FoodDef} : nourriture et effets, contenant rendu (bol, bouteille,
 * tasse, chope), boisson = geste de boire, alcool et cafe ({@link FoodEffects}). Bocaux : NBT {@code Source} = recolte d'origine (nom
 * « Confiture de fraises », couleur du contenu).
 */
public class FarmFoodItem extends Item {

    public static final String SOURCE = "Source";
    /** Recoltes dont le produit a un nom propre (cle {@code item.pioneers.<bocal>.<recolte>}). */
    private static final Set<String> NAMED = Set.of(GeneratedFarm.JAR_SOURCES);

    protected final FoodDef def;

    public FarmFoodItem(FoodDef def, Properties properties) {
        super(food(def, properties.stacksTo(def.stack())));
        this.def = def;
    }

    public FoodDef def() {
        return def;
    }

    static Properties food(FoodDef def, Properties properties) {
        if (!def.edible() || def.bites() > 0) {
            return properties;
        }
        FoodProperties.Builder b = new FoodProperties.Builder().nutrition(def.nutrition()).saturationMod(def.saturation());
        if (def.drink() || def.nutrition() <= 2) {
            b.alwaysEat();
        }
        if (def.fast()) {
            b.fast();
        }
        for (int i = 0; i < def.effects().length; i++) {
            String name = def.effects()[i];
            int ticks = def.seconds()[i] * 20, amp = def.amplifiers()[i];
            b.effect(() -> new MobEffectInstance(PiEffects.byName(name), ticks, amp), 1F);
        }
        return properties.food(b.build());
    }

    /**
     * 1.4 : Maj + clic droit sur le dessus d'un bloc (ou sur une assiette du meme plat) : le plat se pose ({@link PlacedDishBlock}) ; 1.6 : les
     * boissons aussi.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown() && PlacedDishBlock.placeable(def)) {
            InteractionResult r = PlacedDishBlock.place(context);
            if (r != InteractionResult.PASS) {
                return r;
            }
        }
        return super.useOn(context);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return def.drink() ? UseAnim.DRINK : super.getUseAnimation(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return def.drink() ? 32 : super.getUseDuration(stack);
    }

    public ItemStack container() {
        return def.container().isEmpty() ? ItemStack.EMPTY : new ItemStack(PiFarm.item(def.container()));
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !def.container().isEmpty();
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return container();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack container = container();
        ItemStack out = super.finishUsingItem(stack, level, entity);
        if (entity instanceof Player player && !level.isClientSide) {
            FoodEffects.drink(player, def.alcohol());
            for (String e : def.effects()) {
                if ("caffeine".equals(e)) {
                    FoodEffects.coffee(player);
                }
            }
            if (!container.isEmpty() && !player.getAbilities().instabuild) {
                if (out.isEmpty()) {
                    return container;
                }
                if (!player.getInventory().add(container)) {
                    player.drop(container, false);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Nom et infobulle
    // ------------------------------------------------------------------

    @Nullable
    public static Item source(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(SOURCE)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(stack.getTag().getString(SOURCE)));
        return item == Items.AIR ? null : item;
    }

    @Override
    public Component getName(ItemStack stack) {
        Item src = source(stack);
        if (src != null) {
            String id = BuiltInRegistries.ITEM.getKey(src).toString();
            return NAMED.contains(id) ? Component.translatable(getDescriptionId() + "." + ResourceLocation.parse(id).getPath())
                  : Component.translatable(getDescriptionId() + ".of", src.getDescription());
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        //1.0.3 (lot 13) : description facultative de l'objet (bouteilles de tirage : ou se fait la prise de mousse)
        if (net.minecraft.locale.Language.getInstance().has(getDescriptionId() + ".desc")) {
            tips.desc(getDescriptionId() + ".desc");
        }
        effectsTooltip(def, tooltip);
        if (PlacedDishBlock.placeable(def)) {
            //1.6 : les boissons se servent aussi ; nombre de bouchees ou de gorgees par portion
            tips.hint(def.drink() ? "farm.pioneers.food.place_drink" : "farm.pioneers.food.place_dish");
            int bites = PlacedDishBlock.serving(stack).bites();
            if (bites > 1) {
                tips.hint(def.drink() ? "farm.pioneers.food.served_sips" : "farm.pioneers.food.served_bites", bites);
            }
        }
    }

    public static void effectsTooltip(FoodDef def, List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        if (def.edible() && def.nutrition() > 0) {
            tips.spec("farm.pioneers.food.nutrition", def.nutrition(), Math.round(def.nutrition() * def.saturation() * 20) / 10F);
        }
        for (int i = 0; i < def.effects().length; i++) {
            MobEffect effect = PiEffects.byName(def.effects()[i]);
            if (effect == null) {
                continue;
            }
            MutableComponent line = Component.translatable(effect.getDescriptionId());
            if (def.amplifiers()[i] > 0) {
                line.append(" ").append(Component.translatable("potion.potency." + def.amplifiers()[i]));
            }
            int s = def.seconds()[i];
            line.append(Component.literal(" (" + (s >= 60 ? s / 60 + ":" + String.format("%02d", s % 60) : s + " s") + ")"));
            tips.custom(line.withStyle(effect.isBeneficial() ? ChatFormatting.BLUE : ChatFormatting.RED));
        }
        if (def.alcohol() > 0) {
            tips.note("farm.pioneers.food.alcohol", def.alcohol());
        }
        if (def.bites() > 0) {
            tips.good("farm.pioneers.food.place", def.bites());
        }
    }
}
