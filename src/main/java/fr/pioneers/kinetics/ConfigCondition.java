package fr.pioneers.kinetics;

import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * Condition de recette {@code pioneers:config} : vraie si le reglage booleen {@code key} de pioneers-mechanics.toml est actif (ex. le charbon de
 * bois au four vanilla, {@code furnaceCharcoal}). Inversable par {@code forge:not}.
 */
public record ConfigCondition(String key) implements ICondition {

    public static final ResourceLocation ID = Pioneers.rl("config");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return switch (key) {
            case "furnaceCharcoal" -> KineticsConfig.furnaceCharcoal();
            case "vanillaOres" -> KineticsConfig.vanillaOres();
            case "animalFood" -> KineticsConfig.animalFood();
            default -> false;
        };
    }

    public static final class Serializer implements IConditionSerializer<ConfigCondition> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, ConfigCondition value) {
            json.addProperty("key", value.key);
        }

        @Override
        public ConfigCondition read(JsonObject json) {
            return new ConfigCondition(GsonHelper.getAsString(json, "key"));
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
