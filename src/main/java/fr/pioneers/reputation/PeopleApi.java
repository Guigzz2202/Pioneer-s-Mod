package fr.pioneers.reputation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, blocs 3 et 4) : registre DYNAMIQUE des peuples. Les trois peuples de villageois sont des fichiers de donnees
 * ({@code data/pioneers/peoples/*.json}) ; le bloc 4 y ajoute les peuples de joueurs (donnee de monde {@link PlayerPeoples}). Un peuple porte
 * une DOCTRINE (catalogue de commerce, ton) : le code ne lit jamais le catalogue dans l'identifiant du peuple.
 */
public final class PeopleApi extends SimpleJsonResourceReloadListener {

    public static final PeopleApi INSTANCE = new PeopleApi();
    public static final String RESTORERS = "restorers", BARE_HANDS = "bare_hands", PEDDLERS = "peddlers";

    /** Un peuple : identifiant, doctrine (lettered / earthbound / mercantile), part des villages (%), couleur RGB, teinture, peuple de joueurs. */
    public record People(String id, String doctrine, int share, int color, String dye, boolean player, String displayName) {

        public boolean villagers() {
            return !player;
        }
    }

    private static volatile Map<String, People> data = defaults();

    private PeopleApi() {
        super(new com.google.gson.Gson(), "peoples");
    }

    private static Map<String, People> defaults() {
        Map<String, People> m = new LinkedHashMap<>();
        m.put(RESTORERS, new People(RESTORERS, "lettered", 30, 0x2F4B8C, "blue", false, ""));
        m.put(BARE_HANDS, new People(BARE_HANDS, "earthbound", 50, 0x8C8A84, "light_gray", false, ""));
        m.put(PEDDLERS, new People(PEDDLERS, "mercantile", 20, 0x4E8F7E, "cyan", false, ""));
        return m;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<String, People> m = new LinkedHashMap<>();
        for (String id : List.of(RESTORERS, BARE_HANDS, PEDDLERS)) {
            JsonElement e = map.get(Pioneers.rl(id));
            if (e != null) {
                try {
                    m.put(id, read(e.getAsJsonObject()));
                } catch (RuntimeException ex) {
                    Pioneers.LOGGER.warn("Peuple illisible ({}) : {}", id, ex.toString());
                }
            }
        }
        map.forEach((id, e) -> {
            if (!m.containsKey(id.getPath()) && id.getNamespace().equals(Pioneers.MODID)) {
                try {
                    m.put(id.getPath(), read(e.getAsJsonObject()));
                } catch (RuntimeException ex) {
                    Pioneers.LOGGER.warn("Peuple illisible ({}) : {}", id, ex.toString());
                }
            }
        });
        if (m.isEmpty()) {
            m.putAll(defaults());
        }
        data = m;
    }

    private static People read(JsonObject o) {
        return new People(o.get("id").getAsString(), o.get("doctrine").getAsString(), o.has("share") ? o.get("share").getAsInt() : 0,
              Integer.parseInt(o.get("color").getAsString(), 16), o.has("dye") ? o.get("dye").getAsString() : "white",
              o.has("player") && o.get("player").getAsBoolean(), "");
    }

    // ------------------------------------------------------------------ lecture
    /** Les peuples de villageois (donnees) puis ceux des joueurs (serveur courant). */
    public static Collection<People> all(@Nullable MinecraftServer server) {
        List<People> out = new ArrayList<>(data.values());
        if (server != null) {
            out.addAll(PlayerPeoples.get(server).peoples.values());
        }
        return out;
    }

    public static Collection<People> villagerPeoples() {
        return data.values();
    }

    @Nullable
    public static People get(@Nullable MinecraftServer server, String id) {
        People p = data.get(id);
        if (p == null && server != null) {
            p = PlayerPeoples.get(server).peoples.get(id);
        }
        return p;
    }

    /** Doctrine d'un peuple (celle des Mains Nues si le peuple est inconnu). */
    public static String doctrine(@Nullable MinecraftServer server, String id) {
        People p = get(server, id);
        return p == null ? "earthbound" : p.doctrine();
    }

    /**
     * 1.9 (MAJ RP, bloc 4) : peuple de villageois MODELE d'un peuple (celui dont il reprend la doctrine : voix, noms, icones) ; un peuple de
     * villageois est son propre modele. Inconnu : les Mains Nues.
     */
    public static String model(@Nullable MinecraftServer server, String id) {
        if (data.containsKey(id)) {
            return id;
        }
        String doctrine = doctrine(server, id);
        for (People p : data.values()) {
            if (p.doctrine().equals(doctrine)) {
                return p.id();
            }
        }
        return BARE_HANDS;
    }

    /** 1.9 (MAJ RP, bloc 4) : peuples de joueurs du serveur (vide sans serveur). */
    public static Collection<People> playerPeoples(@Nullable MinecraftServer server) {
        return server == null ? List.of() : List.copyOf(PlayerPeoples.get(server).peoples.values());
    }

    /** Tirage reproductible du peuple d'un village selon les parts (graine + position). */
    public static String pick(long hash) {
        int total = 0;
        for (People p : data.values()) {
            total += Math.max(0, p.share());
        }
        if (total <= 0) {
            return BARE_HANDS;
        }
        int r = (int) Math.floorMod(hash, (long) total);
        for (People p : data.values()) {
            r -= Math.max(0, p.share());
            if (r < 0) {
                return p.id();
            }
        }
        return BARE_HANDS;
    }

    /** Peuples de joueurs (bloc 4) : nes d'une secession, enregistres dans la donnee de monde. */
    public static final class PlayerPeoples extends SavedData {

        private static final String NAME = "pioneers_player_peoples";
        public final Map<String, People> peoples = new LinkedHashMap<>();

        public static PlayerPeoples get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(PlayerPeoples::load, PlayerPeoples::new, NAME);
        }

        public void put(People p) {
            peoples.put(p.id(), p);
            setDirty();
        }

        public void remove(String id) {
            if (peoples.remove(id) != null) {
                setDirty();
            }
        }

        private static PlayerPeoples load(CompoundTag tag) {
            PlayerPeoples d = new PlayerPeoples();
            for (Tag t : tag.getList("p", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                d.peoples.put(c.getString("id"), new People(c.getString("id"), c.getString("doctrine"), 0, c.getInt("color"), c.getString("dye"),
                      true, c.getString("name")));
            }
            return d;
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            ListTag l = new ListTag();
            for (People p : peoples.values()) {
                CompoundTag c = new CompoundTag();
                c.putString("id", p.id());
                c.putString("doctrine", p.doctrine());
                c.putInt("color", p.color());
                c.putString("dye", p.dye());
                c.putString("name", p.displayName());
                l.add(c);
            }
            tag.put("p", l);
            return tag;
        }
    }
}
