package fr.pioneers.ruins.sentinel;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : lecture serveur de {@code data/pioneers/sentinels/*.json} (regles, composition, un fichier par role) au
 * chargement des donnees (et a chaque /reload : les Sentinelles deja posees reprennent leurs valeurs a leur prochain chargement).
 */
public final class SentinelData extends SimpleJsonResourceReloadListener {

    public static final SentinelData INSTANCE = new SentinelData();
    private static volatile SentinelRules rules = SentinelRules.defaults();

    private SentinelData() {
        super(new Gson(), "sentinels");
    }

    public static SentinelRules rules() {
        return rules;
    }

    static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<String, JsonObject> files = new HashMap<>();
        map.forEach((id, json) -> {
            if (Pioneers.MODID.equals(id.getNamespace()) && json.isJsonObject()) {
                files.put(id.getPath(), json.getAsJsonObject());
            }
        });
        if (!files.containsKey("rules") || !files.containsKey("spawn")) {
            Pioneers.LOGGER.warn("Sentinelles : donnees incompletes (data/pioneers/sentinels), valeurs par defaut");
        }
        rules = SentinelRules.parse(files);
    }
}
