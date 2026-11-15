package fr.pioneers.geology;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/** Les types de gisements, lus dans les donnees ({@code deposits/}) ; tries par identifiant pour un tirage identique partout. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class DepositTypes extends SimpleJsonResourceReloadListener {

    private static volatile List<DepositType> all = List.of();
    /** 1.0.3-beta (lot 2) : texte JSON de chaque fichier lu par le serveur, envoye aux clients ({@code network/PacketDeposits}). */
    private static volatile Map<ResourceLocation, String> sources = Map.of();

    private DepositTypes() {
        super(new Gson(), "deposits");
    }

    public static List<DepositType> all() {
        return all;
    }

    /** Texte JSON de chaque fichier du serveur, par identifiant (pour la synchronisation des clients). */
    public static Map<ResourceLocation, String> sources() {
        return sources;
    }

    /** 1.0.3-beta (lot 2) : liste recue du serveur (client d'un serveur distant), relue par le meme analyseur. */
    public static void acceptSynced(Map<ResourceLocation, String> files) {
        Map<ResourceLocation, JsonElement> parsed = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                parsed.put(id, JsonParser.parseString(json));
            } catch (RuntimeException e) {
                Pioneers.LOGGER.warn("Gisement recu illisible {} : {}", id, e.getMessage());
            }
        });
        load(parsed);
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> files, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, String> text = new LinkedHashMap<>();
        files.forEach((id, json) -> text.put(id, json.toString()));
        sources = Map.copyOf(text);
        load(files);
    }

    private static void load(Map<ResourceLocation, JsonElement> files) {
        List<DepositType> out = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                DepositType type = DepositType.parse(id, json.getAsJsonObject());
                if (type != null) {
                    out.add(type);
                }
            } catch (RuntimeException e) {
                Pioneers.LOGGER.warn("Gisement illisible {} : {}", id, e.getMessage());
            }
        });
        out.sort(Comparator.comparing(t -> t.id().toString()));
        all = List.copyOf(out);
        Deposits.clearCaches();
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new DepositTypes());
    }
}
