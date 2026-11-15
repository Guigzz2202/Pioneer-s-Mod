package fr.pioneers.clan.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : charge {@code data/pioneers/clans/} au demarrage et a chaque {@code /reload}. Un fichier invalide est refuse avec un
 * message clair dans le journal du serveur (et les autres se chargent) ; sans constitutions completes, la fondation de clans est refusee.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanDefsLoader extends SimplePreparableReloadListener<ClanDefs> {

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new ClanDefsLoader());
    }

    @Override
    protected @NotNull ClanDefs prepare(@NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<String, JsonObject> regimes = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("clans/regimes", rl -> rl.getPath().endsWith(".json")).entrySet()) {
            if (!Pioneers.MODID.equals(e.getKey().getNamespace())) {
                continue;
            }
            String p = e.getKey().getPath();
            String id = p.substring(p.lastIndexOf('/') + 1, p.length() - 5);
            JsonObject o = read(e.getValue(), "regimes/" + id + ".json", errors);
            if (o != null) {
                regimes.put(id, o);
            }
        }
        ClanDefs defs = ClanDefsParser.parse(regimes, file(manager, "roles", errors), file(manager, "permissions", errors),
              file(manager, "levels", errors), file(manager, "rules", errors), errors);
        for (String err : errors) {
            Pioneers.LOGGER.error("Clans : fichier refuse, {}", err);
        }
        return defs;
    }

    @Override
    protected void apply(@NotNull ClanDefs defs, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        ClanDefs.set(defs);
        Pioneers.LOGGER.info("Clans : {} regimes, {} roles, {} permissions, {} niveaux{}", defs.regimes.size(), defs.roles.size(), defs.perms.size(),
              defs.levels.size(), defs.ready() ? "" : " (INCOMPLET : fondation des clans refusee)");
    }

    @Nullable
    private static JsonObject file(ResourceManager manager, String name, List<String> errors) {
        return manager.getResource(Pioneers.rl("clans/" + name + ".json")).map(r -> read(r, name + ".json", errors)).orElse(null);
    }

    @Nullable
    private static JsonObject read(Resource res, String name, List<String> errors) {
        try (Reader r = res.openAsReader()) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception ex) {
            errors.add(name + " : JSON illisible (" + ex.getMessage() + ")");
            return null;
        }
    }
}
