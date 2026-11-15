package fr.pioneers.clan.war;

import com.google.gson.JsonElement;
import fr.pioneers.Pioneers;
import fr.pioneers.clan.rep.ClanRepRules;
import fr.pioneers.clan.stats.ClanStats;
import fr.pioneers.clan.stats.StatsRules;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : donnees de la guerre, des statistiques et de la reputation de clan, dans {@code data/pioneers/clans/} :
 * {@code war.json} ({@link WarRules}), {@code stats.json} ({@link StatsRules}), {@code reputation.json} ({@link ClanRepRules}). Un fichier
 * illisible est refuse avec un message clair : les valeurs de depart restent.
 */
public final class WarData extends SimpleJsonResourceReloadListener {

    public static final WarData INSTANCE = new WarData();

    private WarData() {
        super(new com.google.gson.Gson(), "clans");
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        read(map, "war", e -> WarRules.set(WarRules.parse(e.getAsJsonObject())));
        read(map, "stats", e -> StatsRules.set(StatsRules.parse(e.getAsJsonObject())));
        read(map, "reputation", e -> ClanRepRules.set(ClanRepRules.parse(e.getAsJsonObject())));
        ClanStats.reloaded();
    }

    private static void read(Map<ResourceLocation, JsonElement> map, String name, java.util.function.Consumer<JsonElement> f) {
        JsonElement e = map.get(Pioneers.rl(name));
        if (e == null) {
            return;
        }
        try {
            f.accept(e);
        } catch (RuntimeException ex) {
            Pioneers.LOGGER.error("Donnees des clans illisibles (data/pioneers/clans/{}.json) : {}", name, ex.toString());
        }
    }
}
