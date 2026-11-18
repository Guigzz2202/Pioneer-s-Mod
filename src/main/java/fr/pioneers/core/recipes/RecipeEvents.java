package fr.pioneers.core.recipes;

import fr.pioneers.Pioneers;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * Invalide les caches de recettes quand les datapacks sont recharges (serveur) ou synchronises (client).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class RecipeEvents {

    private RecipeEvents() {
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @NotNull
            @Override
            protected Void prepare(@NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
                PiRecipes.invalidate();
            }
        });
    }

    /** 0.20.46 (audit) : les tags arrivent apres les recettes a la connexion : les caches « accepte cet objet ? » sont refaits. */
    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        PiRecipes.invalidate();
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class Client {

        private Client() {
        }

        @SubscribeEvent
        public static void onRecipes(RecipesUpdatedEvent event) {
            PiRecipes.invalidate();
        }
    }
}
