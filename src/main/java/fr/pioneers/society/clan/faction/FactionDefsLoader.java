package fr.pioneers.society.clan.faction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import java.io.Reader;
import javax.annotation.Nullable;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : charge {@code data/pioneers/clans/federal.json} (factions et peuples de joueurs) au demarrage et a chaque
 * {@code /reload}. Un fichier invalide est refuse avec un message clair et les valeurs de la specification restent en place.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FactionDefsLoader extends SimplePreparableReloadListener<FactionRules.Values> {

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new FactionDefsLoader());
    }

    @Override
    protected @NotNull FactionRules.Values prepare(@NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        JsonObject o = manager.getResource(Pioneers.rl("clans/federal.json")).map(FactionDefsLoader::read).orElse(null);
        if (o == null) {
            Pioneers.LOGGER.error("Factions : federal.json absent ou illisible, valeurs de la specification gardees");
            return FactionRules.DEFAULTS;
        }
        try {
            return FactionRules.parse(o);
        } catch (RuntimeException ex) {
            Pioneers.LOGGER.error("Factions : federal.json refuse ({}), valeurs de la specification gardees", ex.getMessage());
            return FactionRules.DEFAULTS;
        }
    }

    @Override
    protected void apply(@NotNull FactionRules.Values v, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        FactionRules.set(v);
    }

    @Nullable
    private static JsonObject read(net.minecraft.server.packs.resources.Resource res) {
        try (Reader r = res.openAsReader()) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception ex) {
            return null;
        }
    }
}
