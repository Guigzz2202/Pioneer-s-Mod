package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 16, retours de Guillaume : « un selecteur de joueur deja vu au lieu de taper les pseudos ») : REGISTRE des joueurs venus sur
 * ce serveur (donnee de monde {@code pioneers_known_players} : compte, nom de pionnier, derniere venue), tenu a la connexion et a la
 * deconnexion, borne a {@link #KEEP} entrees (les plus anciennes partent). Le SERVEUR est la source de verite : le selecteur demande la liste
 * ({@link RpActions#PLAYERS_REQUEST}), recoit au plus {@link #LIST_MAX} joueurs (connectes d'abord, puis du plus recent), et la demande
 * finale (contrat, virement, invitation) repasse par la resolution habituelle du serveur (un nom inconnu reste refuse).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class KnownPlayers extends SavedData {

    public static final String NAME = "pioneers_known_players";
    public static final int KEEP = 2048, LIST_MAX = 200, QUERY_MAX = 16;
    /** Usages connus (le selecteur ne montre que des joueurs connectes pour un virement : le serveur refuse un absent). */
    public static final Set<String> PURPOSES = Set.of("contract", "pay", "party", "any");

    /** Un joueur connu : compte, nom de pionnier, derniere venue (ms). */
    public record Known(UUID id, String account, String pioneer, long seen) {
    }

    private final Map<UUID, Known> known = new LinkedHashMap<>();

    private KnownPlayers() {
    }

    static {
        RpNet.onServer(RpActions.PLAYERS_REQUEST, KnownPlayers::request);
    }

    /** Charge la classe (et son traitement reseau). */
    public static void init() {
    }

    public static KnownPlayers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KnownPlayers::load, KnownPlayers::new, NAME);
    }

    private static KnownPlayers load(CompoundTag t) {
        KnownPlayers d = new KnownPlayers();
        for (Tag x : t.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            if (c.hasUUID("u")) {
                d.known.put(c.getUUID("u"), new Known(c.getUUID("u"), c.getString("n"), c.getString("p"), c.getLong("s")));
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag t) {
        ListTag l = new ListTag();
        for (Known k : known.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", k.id());
            c.putString("n", k.account());
            c.putString("p", k.pioneer());
            c.putLong("s", k.seen());
            l.add(c);
        }
        t.put("players", l);
        return t;
    }

    /** Note la venue d'un joueur (connexion, deconnexion) ; garde au plus {@link #KEEP} joueurs. */
    public void seen(UUID id, String account, String pioneer, long now) {
        known.remove(id);
        known.put(id, new Known(id, account, pioneer == null || pioneer.isBlank() ? account : pioneer, now));
        while (known.size() > KEEP) {
            known.remove(known.keySet().iterator().next());
        }
        setDirty();
    }

    @Nullable
    public Known byAccount(String account) {
        for (Known k : known.values()) {
            if (k.account().equalsIgnoreCase(account)) {
                return k;
            }
        }
        return null;
    }

    public int size() {
        return known.size();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            get(p.getServer()).seen(p.getUUID(), p.getGameProfile().getName(), PioneerCap.get(p).name, System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            get(p.getServer()).seen(p.getUUID(), p.getGameProfile().getName(), PioneerCap.get(p).name, System.currentTimeMillis());
        }
    }

    // ------------------------------------------------------------------ liste (PURE : banc, GameTests)
    /**
     * Joueurs a montrer : connectes d'abord (ordre alphabetique), puis les autres du plus recent ; sans {@code self} ; filtre insensible a la
     * casse sur le compte ou le nom de pionnier ; {@code onlineOnly} pour un virement ; au plus {@link #LIST_MAX}.
     */
    public static List<Known> list(Iterable<Known> registry, Set<UUID> online, @Nullable UUID self, String query, boolean onlineOnly) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() > QUERY_MAX) {
            q = q.substring(0, QUERY_MAX);
        }
        List<Known> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Known k : registry) {
            if (k.id().equals(self) || !seen.add(k.id()) || (onlineOnly && !online.contains(k.id()))) {
                continue;
            }
            //lot 19 : filtre sur le COMPTE seulement (filtrer sur le nom de pionnier revelerait le lien compte <-> personnage)
            if (!q.isEmpty() && !k.account().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(k);
        }
        out.sort(Comparator.<Known>comparingInt(k -> online.contains(k.id()) ? 0 : 1)
              .thenComparing((a, b) -> online.contains(a.id()) ? a.account().compareToIgnoreCase(b.account()) : Long.compare(b.seen(), a.seen())));
        return out.size() > LIST_MAX ? new ArrayList<>(out.subList(0, LIST_MAX)) : out;
    }

    /** 1.0.5-beta (lot 19) : nom de pionnier montre dans le selecteur : seulement si le demandeur peut voir cette identite. PUR. */
    public static String shownPioneer(Known k, boolean mayKnow) {
        return mayKnow ? k.pioneer() : "";
    }

    // ------------------------------------------------------------------ reseau
    private static void request(ServerPlayer player, CompoundTag t) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        String purpose = PURPOSES.contains(t.getString("purpose")) ? t.getString("purpose") : "any";
        RpNet.send(player, RpActions.PLAYERS_VIEW, view(server, player, t.getString("q"), purpose));
    }

    public static CompoundTag view(MinecraftServer server, ServerPlayer player, String query, String purpose) {
        KnownPlayers d = get(server);
        Set<UUID> online = new HashSet<>();
        List<Known> all = new ArrayList<>();
        //les joueurs connectes sont toujours connus (meme avant leur premiere deconnexion), avec leur nom de pionnier courant
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(p.getUUID());
            all.add(new Known(p.getUUID(), p.getGameProfile().getName(), PioneerCap.get(p).name.isBlank() ? p.getGameProfile().getName()
                  : PioneerCap.get(p).name, System.currentTimeMillis()));
        }
        all.addAll(d.known.values());
        CompoundTag out = new CompoundTag();
        out.putString("purpose", purpose);
        ListTag l = new ListTag();
        //1.0.5-beta (lot 19, relecture) : le lien compte <-> personnage reste PRIVE (regle des clans : permission « Voir les identites », qui
        //« ne sort jamais du clan ») : le nom de pionnier n'accompagne le compte que pour un membre de MON clan, si mon role le permet
        fr.pioneers.society.clan.Clan mine = fr.pioneers.society.clan.ClanApi.clanOf(server, player.getUUID());
        boolean ids = mine != null && fr.pioneers.society.clan.core.ClanRoles.has(mine, player.getUUID(), fr.pioneers.society.clan.Permission.VIEW_IDENTITIES);
        for (Known k : list(all, online, player.getUUID(), query, "pay".equals(purpose))) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", k.id());
            c.putString("n", k.account());
            c.putString("p", shownPioneer(k, ids && mine.member(k.id()) != null));
            c.putBoolean("o", online.contains(k.id()));
            c.putLong("s", k.seen());
            l.add(c);
        }
        out.put("list", l);
        out.putLong("now", System.currentTimeMillis());
        return out;
    }
}
