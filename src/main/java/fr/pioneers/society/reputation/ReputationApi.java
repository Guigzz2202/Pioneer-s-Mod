package fr.pioneers.society.reputation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.world.ruins.RuinApi;
import fr.pioneers.world.ruins.RuinEvents;
import fr.pioneers.world.ruins.RuinSites;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : reputation du joueur aupres de chaque peuple (table identifiant -> -300..+300 dans le profil). Un seul point
 * d'entree pour les actes, {@link #applyAct} (tableau {@code data/pioneers/reputation/acts.json}) ; gains plafonnes par peuple et par jour
 * ({@code reputationDailyCap}, sauf une restauration, une fois par ruine), pertes jamais plafonnees. Aucun tick : rien ne bouge avec le
 * temps. Les paliers ({@code tiers.json}) modulent les prix et le commerce. Le bloc 4 branche la cascade (clan, faction) par {@link #cascade}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ReputationApi extends SimpleJsonResourceReloadListener {

    public static final ReputationApi INSTANCE = new ReputationApi();
    public static final int LOG = 30;

    /** Palier : identifiant, borne haute, effets sur les prix (achat, vente), refus de commerce, stock exclusif, palier d'affinite des ouvriers. */
    public record Tier(String id, int max, double buy, double sell, boolean refuse, boolean exclusive, int workersTrust) {
    }

    /** Cascade du bloc 4 (100 % joueur, 50 % clan, 25 % faction ou peuple de joueurs) : appelee apres chaque changement du joueur. */
    public interface Cascade {

        void apply(ServerPlayer player, String people, int delta, String cause);
    }

    @Nullable
    public static Cascade cascade;

    private static volatile List<Tier> tiers = List.of(new Tier("hostile", -200, 0, 0, true, false, 3), new Tier("wary", -60, 0.10, -0.10, false, false, 3),
          new Tier("unknown", 59, 0, 0, false, false, 3), new Tier("esteemed", 149, -0.05, 0.05, false, true, 3),
          new Tier("ally", 300, -0.10, 0.10, false, true, 2));
    private static volatile JsonObject acts = new JsonObject();
    private static volatile int min = -300, max = 300;

    private ReputationApi() {
        super(new com.google.gson.Gson(), "reputation");
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        try {
            JsonElement t = map.get(Pioneers.rl("tiers"));
            if (t != null) {
                JsonObject o = t.getAsJsonObject();
                List<Tier> list = new ArrayList<>();
                o.getAsJsonArray("tiers").forEach(x -> {
                    JsonObject xo = x.getAsJsonObject();
                    list.add(new Tier(xo.get("id").getAsString(), xo.get("max").getAsInt(), xo.has("buy") ? xo.get("buy").getAsDouble() : 0,
                          xo.has("sell") ? xo.get("sell").getAsDouble() : 0, xo.has("refuse") && xo.get("refuse").getAsBoolean(),
                          xo.has("exclusive") && xo.get("exclusive").getAsBoolean(), xo.has("workers_trust") ? xo.get("workers_trust").getAsInt() : 3));
                });
                list.sort((a, b) -> Integer.compare(a.max(), b.max()));
                tiers = List.copyOf(list);
                min = o.has("min") ? o.get("min").getAsInt() : -300;
                max = o.has("max") ? o.get("max").getAsInt() : 300;
            }
            JsonElement a = map.get(Pioneers.rl("acts"));
            if (a != null) {
                acts = a.getAsJsonObject();
            }
        } catch (RuntimeException ex) {
            Pioneers.LOGGER.error("Reputation illisible (data/pioneers/reputation) : {}", ex.toString());
        }
    }

    // ------------------------------------------------------------------ lecture
    public static int reputation(ServerPlayer player, String people) {
        return PioneerCap.get(player).reputation(people);
    }

    public static Tier tierOf(int points) {
        for (Tier t : tiers) {
            if (points <= t.max()) {
                return t;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    public static Tier tier(ServerPlayer player, String people) {
        return tierOf(reputation(player, people));
    }

    // ------------------------------------------------------------------ ecriture
    /**
     * Ajoute {@code delta} points chez {@code people} (borne -300..+300). Un gain est plafonne par jour et par peuple sauf {@code uncapped}.
     * Renvoie le changement effectif.
     */
    public static int add(ServerPlayer player, String people, int delta, String cause, boolean uncapped) {
        if (delta == 0 || PeopleApi.get(player.getServer(), people) == null) {
            return 0;
        }
        PioneerProfile p = PioneerCap.get(player);
        long day = PioneerApi.day(player.getServer());
        if (p.repDay != day) {
            p.repDay = day;
            p.repToday.clear();
            p.loreToday = 0;
        }
        int d = delta;
        if (d > 0 && !uncapped) {
            int cap = PiCommonConfig.get(RpConfig.REPUTATION_DAILY_CAP, 30);
            int used = p.repToday.getOrDefault(people, 0);
            d = Math.max(0, Math.min(d, cap - used));
            if (d == 0) {
                return 0;
            }
            p.repToday.put(people, used + d);
        }
        int old = p.reputation(people);
        int now = Math.max(min, Math.min(max, old + d));
        if (now == old) {
            return 0;
        }
        p.reputation.put(people, now);
        CompoundTag log = new CompoundTag();
        log.putLong("d", day);
        log.putString("p", people);
        log.putInt("v", now - old);
        log.putString("c", cause);
        p.repLog.addLast(log);
        while (p.repLog.size() > LOG) {
            p.repLog.pollFirst();
        }
        p.touch();
        Tier before = tierOf(old), after = tierOf(now);
        if (!before.id().equals(after.id())) {
            CompoundTag t = new CompoundTag();
            t.putString("people", people);
            t.putString("tier", after.id());
            t.putBoolean("up", now > old);
            RpNet.send(player, RpActions.REPUTATION_TIER, t);
            player.level().playSound(null, player.blockPosition(), RpSounds.SEAL.get(), SoundSource.PLAYERS, 0.7F, now > old ? 1.1F : 0.8F);
            //1.9 (MAJ RP, lot 19) : progres « Estime » et « Allie » (palier atteint en montant, chez un peuple quelconque)
            if (now > old && ("esteemed".equals(after.id()) || "ally".equals(after.id()))) {
                fr.pioneers.core.registries.PiTriggers.MECHANICS.trigger(player, "reputation_esteemed");
                if ("ally".equals(after.id())) {
                    fr.pioneers.core.registries.PiTriggers.MECHANICS.trigger(player, "reputation_ally");
                }
            }
        }
        ProfileSync.full(player);
        MinecraftForge.EVENT_BUS.post(new ReputationChangedEvent(player, people, old, now, cause));
        if (cascade != null) {
            cascade.apply(player, people, now - old, cause);
        }
        return now - old;
    }

    /**
     * Applique un acte du tableau a tous les peuples (colonne de chaque peuple de villageois, plus {@code own} chez {@code ownPeople}).
     * {@code onceKey} non nul : l'acte ne compte qu'une fois pour cette cle (ruine, villageois...).
     */
    public static void applyAct(ServerPlayer player, String act, @Nullable String ownPeople, @Nullable String onceKey) {
        JsonObject a = acts.getAsJsonObject(act);
        if (a == null) {
            return;
        }
        PioneerProfile p = PioneerCap.get(player);
        if (onceKey != null && !p.marks.add("rep:" + act + ":" + onceKey)) {
            return;
        }
        if (a.has("daily")) {
            long day = PioneerApi.day(player.getServer());
            if (p.repDay != day) {
                p.repDay = day;
                p.repToday.clear();
                p.loreToday = 0;
            }
            if (p.loreToday >= a.get("daily").getAsInt()) {
                return;
            }
            p.loreToday++;
        }
        boolean uncapped = a.has("uncapped") && a.get("uncapped").getAsBoolean();
        Map<String, Integer> deltas = new HashMap<>();
        for (PeopleApi.People people : PeopleApi.villagerPeoples()) {
            if (a.has(people.id())) {
                deltas.merge(people.id(), a.get(people.id()).getAsInt(), Integer::sum);
            }
        }
        //1.9 (MAJ RP, bloc 4) : un peuple de joueurs juge les actes comme le modele de sa doctrine (colonne du peuple de villageois modele)
        for (PeopleApi.People people : PeopleApi.playerPeoples(player.getServer())) {
            String model = PeopleApi.model(player.getServer(), people.id());
            if (a.has(model)) {
                deltas.merge(people.id(), a.get(model).getAsInt(), Integer::sum);
            }
        }
        if (ownPeople != null && a.has("own")) {
            deltas.merge(ownPeople, a.get("own").getAsInt(), Integer::sum);
        }
        deltas.forEach((people, d) -> add(player, people, d, act, uncapped));
    }

    /**
     * 1.0.5-beta (lot 16, retours de Guillaume : « rappeler ce qui plait / deplait a chaque peuple ») : les actes du tableau tels qu'un peuple
     * les juge, lus dans les VRAIES donnees ({@code acts.json}) : colonne du peuple ({@code column} : son id, ou celui du peuple modele d'un
     * peuple de joueurs), plus la part {@code own} quand l'acte touche l'un des siens. Une ligne : {acte, points, points en plus chez lui,
     * plafond par jour (0 = aucun), jamais plafonne}. Les actes sans effet sur ce peuple sont omis ; tri : gains decroissants puis pertes.
     */
    public static List<int[]> judged(String column, List<String> namesOut) {
        return judged(acts, column, namesOut);
    }

    /** {@link #judged(String, List)} sur un tableau donne (PUR : GameTests, banc). */
    public static List<int[]> judged(JsonObject table, String column, List<String> namesOut) {
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : table.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject a = e.getValue().getAsJsonObject();
            int v = a.has(column) ? a.get(column).getAsInt() : 0;
            int own = a.has("own") ? a.get("own").getAsInt() : 0;
            if (v == 0 && own == 0) {
                continue;
            }
            int daily = a.has("daily") ? a.get("daily").getAsInt() : 0;
            boolean uncapped = a.has("uncapped") && a.get("uncapped").getAsBoolean();
            rows.add(new Object[]{e.getKey(), new int[]{v, own, daily, uncapped ? 1 : 0}});
        }
        rows.sort((x, y) -> {
            int vx = shown((int[]) x[1]), vy = shown((int[]) y[1]);
            return vx != vy ? Integer.compare(vy, vx) : ((String) x[0]).compareTo((String) y[0]);
        });
        List<int[]> out = new ArrayList<>();
        for (Object[] r : rows) {
            namesOut.add((String) r[0]);
            out.add((int[]) r[1]);
        }
        return out;
    }

    /** Points montres pour une ligne de {@link #judged} : ceux du peuple, ou la part « chez lui » quand l'acte ne vise que les siens. */
    public static int shown(int[] row) {
        return row[0] != 0 ? row[0] : row[1];
    }

    // ------------------------------------------------------------------ evenements du bloc 1
    @SubscribeEvent
    public static void onDismantled(RuinEvents.Dismantled e) {
        if (e.player != null) {
            applyAct(e.player, "dismantle", null, "site" + e.site);
        }
    }

    @SubscribeEvent
    public static void onRestored(RuinEvents.Restored e) {
        RuinSites.Site s = RuinApi.site(e.level, e.site);
        if (s == null) {
            return;
        }
        for (UUID u : s.contributors.keySet()) {
            ServerPlayer c = e.level.getServer().getPlayerList().getPlayer(u);
            if (c != null) {
                applyAct(c, "restore", null, "site" + e.site);
            }
        }
    }

    @SubscribeEvent
    public static void onSentinel(RuinEvents.SentinelKilled e) {
        if (e.player != null) {
            applyAct(e.player, "sentinel_killed", null, null);
        }
    }

    @SubscribeEvent
    public static void onStandby(RuinEvents.SiteStandby e) {
        if (e.player != null) {
            applyAct(e.player, "standby", null, null);
        }
    }

    @SubscribeEvent
    public static void onLore(RuinEvents.LoreRead e) {
        if (e.player != null) {
            applyAct(e.player, "read_lore", null, null);
        }
    }

    // ------------------------------------------------------------------ evenements publics
    /** Reputation d'un joueur changee chez un peuple (ancienne et nouvelle valeur, cause = identifiant de l'acte). */
    public static class ReputationChangedEvent extends Event {

        public final ServerPlayer player;
        public final String people, cause;
        public final int before, after;

        public ReputationChangedEvent(ServerPlayer player, String people, int before, int after, String cause) {
            this.player = player;
            this.people = people;
            this.before = before;
            this.after = after;
            this.cause = cause;
        }
    }

    /** Nom affichable d'un peuple (cle de langue des peuples de villageois, nom saisi pour un peuple de joueurs). */
    public static Component peopleName(@Nullable net.minecraft.server.MinecraftServer server, String id) {
        PeopleApi.People p = PeopleApi.get(server, id);
        return p != null && p.player() ? Component.literal(p.displayName()) : Component.translatable("people.pioneers." + id);
    }
}
