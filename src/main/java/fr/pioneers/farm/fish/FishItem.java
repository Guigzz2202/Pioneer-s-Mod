package fr.pioneers.farm.fish;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Poisson du mod (cru) : nourriture selon l'espece, effets de sa propriete (venin, vision nocturne, force...), fiche (habitat, force, taille). */
public class FishItem extends Item {

    private final int species;

    public FishItem(int species, Properties properties) {
        super(food(GeneratedFish.SPECIES[species], properties));
        this.species = species;
    }

    public FishDef def() {
        return GeneratedFish.SPECIES[species];
    }

    /** Index de l'espece dans {@link GeneratedFish#SPECIES}. */
    public int species() {
        return species;
    }

    private static Properties food(FishDef d, Properties p) {
        FoodProperties.Builder b = new FoodProperties.Builder().nutrition(d.food()).saturationMod(0.15F).meat();
        for (String[] e : GeneratedFish.EFFECTS) {
            if (e[0].equals(d.prop())) {
                int ticks = Integer.parseInt(e[2]) * 20, amp = Integer.parseInt(e[3]);
                ResourceLocation id = ResourceLocation.parse(e[1]);
                b.effect(() -> new MobEffectInstance(ForgeRegistries.MOB_EFFECTS.getValue(id), ticks, amp), 1F);
            }
        }
        return p.food(b.build());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        //1.0.5-beta (lot 25) : le requin gris (peche seulement) : ce qu'on en fait
        if (FishSpawns.catchOnly(def())) {
            tips.desc("item.pioneers." + def().id() + ".desc");
        }
        tooltip(def(), stack, tooltip);
        tips.hint("farm.pioneers.fish.release_hint");
    }

    public static void tooltip(FishDef d, ItemStack stack, List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        if (stack.hasTag() && stack.getTag().contains("Size")) {
            tips.spec("farm.pioneers.fish.size", stack.getTag().getInt("Size"));
        }
        Component h = null;
        for (int i = 0; i < GeneratedFish.HABITATS.length; i++) {
            if (d.in(i)) {
                Component c = Component.translatable("farm.pioneers.fish.habitat." + GeneratedFish.HABITATS[i]);
                h = h == null ? c.copy() : h.copy().append(", ").append(c);
            }
        }
        if (h != null) {
            tips.spec("farm.pioneers.fish.habitat", h);
        }
        tips.spec(Component.translatable("farm.pioneers.fish.strength", d.strength()).append(" · ")
              .append(Component.translatable("farm.pioneers.fish.behaviour." + GeneratedFish.BEHAVIOURS[d.behaviour()])));
        if (!d.prop().isEmpty()) {
            tips.custom(Component.translatable("farm.pioneers.fish.prop." + d.prop())
                  .withStyle(d.legendary() ? ChatFormatting.GOLD : "venom".equals(d.prop()) ? ChatFormatting.RED : ChatFormatting.BLUE));
        }
    }
}
