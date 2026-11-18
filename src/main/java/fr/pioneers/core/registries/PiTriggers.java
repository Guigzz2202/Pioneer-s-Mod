package fr.pioneers.core.registries;

import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Criteres de progres du mod. {@code pioneers:mechanics} : evenement de l'age mecanique nomme dans le progres
 * ({@code "conditions": {"event": "part_broken"}}) : premiere rotation, piece cassee, fosse reussie...
 */
public final class PiTriggers {

    private PiTriggers() {
    }

    public static final MechanicsTrigger MECHANICS = new MechanicsTrigger();

    public static void init() {
    }

    /** Inscription des criteres (dans le travail differe de la mise en place commune : la table vanilla n'est pas concurrente). */
    public static void register() {
        CriteriaTriggers.register(MECHANICS);
    }

    public static final class MechanicsTrigger extends SimpleCriterionTrigger<MechanicsTrigger.Instance> {

        static final ResourceLocation ID = Pioneers.rl("mechanics");

        @NotNull
        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @NotNull
        @Override
        protected Instance createInstance(@NotNull JsonObject json, @NotNull ContextAwarePredicate player, @NotNull DeserializationContext context) {
            return new Instance(player, GsonHelper.getAsString(json, "event", ""));
        }

        public void trigger(ServerPlayer player, String event) {
            trigger(player, instance -> instance.event.equals(event));
        }

        public static final class Instance extends AbstractCriterionTriggerInstance {

            final String event;

            Instance(ContextAwarePredicate player, String event) {
                super(ID, player);
                this.event = event;
            }

            @NotNull
            @Override
            public JsonObject serializeToJson(@NotNull SerializationContext context) {
                JsonObject json = super.serializeToJson(context);
                json.addProperty("event", event);
                return json;
            }
        }
    }
}
