package fr.pioneers.farm.food;

import fr.pioneers.Pioneers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Effets de la cuisine : Repu (la faim ne baisse plus), Cafeine (vitesse, cadence de frappe), Nervosite (exces de cafe), Ivresse (I a III,
 * suit l'alcool bu : {@link FoodEffects}).
 */
public final class PiEffects {

    private PiEffects() {
    }

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Pioneers.MODID);

    public static final RegistryObject<MobEffect> WELL_FED = EFFECTS.register("well_fed", () -> new Simple(MobEffectCategory.BENEFICIAL, 0xE8A040));
    public static final RegistryObject<MobEffect> CAFFEINE = EFFECTS.register("caffeine", () -> new Simple(MobEffectCategory.BENEFICIAL, 0x6B4226)
          .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6b0c6a3e-6f5d-4c34-9a8a-0f3c6a5b1c01", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL)
          .addAttributeModifier(Attributes.ATTACK_SPEED, "6b0c6a3e-6f5d-4c34-9a8a-0f3c6a5b1c02", 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> JITTERS = EFFECTS.register("jitters", () -> new Simple(MobEffectCategory.HARMFUL, 0x9A7A50)
          .addAttributeModifier(Attributes.ATTACK_DAMAGE, "6b0c6a3e-6f5d-4c34-9a8a-0f3c6a5b1c03", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
    public static final RegistryObject<MobEffect> TIPSY = EFFECTS.register("tipsy", () -> new Simple(MobEffectCategory.NEUTRAL, 0xB04A6A)
          .addAttributeModifier(Attributes.MOVEMENT_SPEED, "6b0c6a3e-6f5d-4c34-9a8a-0f3c6a5b1c04", -0.06, AttributeModifier.Operation.MULTIPLY_TOTAL)
          .addAttributeModifier(Attributes.LUCK, "6b0c6a3e-6f5d-4c34-9a8a-0f3c6a5b1c05", 1, AttributeModifier.Operation.ADDITION));

    public static MobEffect byName(String name) {
        return switch (name) {
            case "well_fed" -> WELL_FED.get();
            case "caffeine" -> CAFFEINE.get();
            case "jitters" -> JITTERS.get();
            case "tipsy" -> TIPSY.get();
            default -> ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.parse(name));
        };
    }

    /** Effet sans action propre (ses effets sont dans les attributs ou dans {@link FoodEffects}). */
    static class Simple extends MobEffect {

        Simple(MobEffectCategory category, int colour) {
            super(category, colour);
        }
    }
}
