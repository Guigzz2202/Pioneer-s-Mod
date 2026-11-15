package fr.pioneers.clan.stats;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.core.ClanRoles;
import fr.pioneers.clan.seal.ClanLevels;
import fr.pioneers.clan.territory.ClanTerritory;
import fr.pioneers.clan.war.ClanDiplomacy;
import fr.pioneers.clan.war.ClanWar;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerDefs;
import fr.pioneers.core.profile.SkillHooks;
import fr.pioneers.core.profile.SkillLevelUpEvent;
import fr.pioneers.core.profile.VocationChangedEvent;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.economy.Values;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.9 (MAJ RP, bloc 4) : statistiques des joueurs et des clans. Tout part d'evenements existants, jamais d'un calcul par tick :
 * production (crochet de {@code PioneerApi.gainXp}, valeur de la table du bloc 3, meme fatigue, plafond journalier), combats (morts de
 * creatures hostiles, de Sentinelles, de joueurs ennemis ; morts du joueur), contribution (jetons verses au tresor, lus dans le journal du
 * clan), presence (temps de jeu), potentiel de frappe (connexion, changement d'equipement, une fois par minute au plus). L'agregation des
 * clans tourne toutes les 10 minutes. {@link #sheet} et {@link #directory} donnent les fiches de l'onglet Clan : AUCUN pseudo de compte hors
 * du clan (et dans le clan, seulement avec la permission de voir les identites).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanStats {

    /** Agregat d'un clan (memoire, refait toutes les 10 minutes). */
    static final class Agg {

        long prod7, prod30, contributed7, at;
        final long[] curve;
        /** 1.9 (MAJ RP, bloc 4) : potentiel de frappe par jour (somme des plus hauts potentiels du jour des membres presents). */
        final long[] potCurve;
        final Map<String, Long> skills = new TreeMap<>();
        int hostiles7, sentinels7, enemies7, deaths7, potential, active, vocations, rank, ranked;
        final Map<UUID, Long> member7 = new HashMap<>();
        ListTag warehouses = new ListTag();

        Agg(int days) {
            curve = new long[days];
            potCurve = new long[days];
        }
    }

    private static final Map<String, Long> UNIT = new HashMap<>();
    /** Centiemes de jeton produits pas encore convertis en points de clan, par clan. */
    private static final Map<UUID, Long> CENTI = new HashMap<>();
    private static final Map<UUID, Long> PLAY_FROM = new HashMap<>();
    private static final Map<UUID, Long> DIRTY = new HashMap<>();
    private static final Map<UUID, Agg> AGG = new HashMap<>();
    private static long lastAggregate, lastScan;
    private static final String[] BRACKETS = {"weak", "medium", "strong"};

    private ClanStats() {
    }

    /** Branche le crochet de production dans le gain d'XP (aucun ecouteur par tick). */
    public static void install() {
        PioneerApi.production = ClanStats::produced;
    }

    public static void clear() {
        UNIT.clear();
        CENTI.clear();
        PLAY_FROM.clear();
        DIRTY.clear();
        AGG.clear();
        lastAggregate = 0;
        lastScan = 0;
    }

    // ------------------------------------------------------------------ production
    /**
     * Valeur produite par une action qui donne de l'XP (main ou machine du joueur) : nombre d'actions = XP de base / XP de la source, valeur
     * de l'objet de reference ({@code stats.json}), fatigue de repetition du gain d'XP, plafond de 10 000 jetons-equivalent par jour.
     */
    public static void produced(ServerPlayer player, String skill, String action, double base, double fatigue) {
        if (!ClanApi.enabled()) {
            return;
        }
        StatsRules.Values v = StatsRules.get();
        StatsRules.Ref ref = v.production().get(action);
        PioneerDefs.Source src = PioneerDefs.get().sources.get(action);
        if (ref == null || src == null || src.xp() <= 0) {
            return;
        }
        long add = StatsRules.produced(unit(action, ref), ref.count(), base / src.xp(), fatigue);
        if (add <= 0) {
            return;
        }
        MinecraftServer server = player.getServer();
        ClanStatsData data = ClanStatsData.get(server);
        StatsBucket b = data.of(player.getUUID()).today(PioneerApi.day(server));
        add = StatsRules.capped(add, b.total, v.productionCap());
        if (add > 0) {
            b.produced.merge(skill, add, Long::sum);
            b.total += add;
            data.changed();
            //points de clan (niveau) : 1 point par 100 jetons produits, compte par le niveau du clan (centiemes gardes d'une action a l'autre)
            Clan clan = ClanData.get(server).clanOf(player.getUUID());
            if (clan != null) {
                long centi = CENTI.merge(clan.id, add, Long::sum);
                if (centi >= 100) {
                    CENTI.put(clan.id, centi % 100);
                    ClanLevels.addProduction(clan, centi / 100);
                }
            }
        }
    }

    private static long unit(String action, StatsRules.Ref ref) {
        Long u = UNIT.get(action);
        if (u != null) {
            return u;
        }
        long v = ref.value();
        if (!ref.item().isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(ref.item());
            Item it = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
            long x = it == Items.AIR ? 0 : Values.value(new ItemStack(it));
            if (x > 0) {
                v = x;
            }
        }
        UNIT.put(action, v);
        return v;
    }

    /** Apres un rechargement des donnees : valeurs de reference relues. */
    public static void reloaded() {
        UNIT.clear();
    }

    // ------------------------------------------------------------------ contribution
    /** Jetons verses au tresor par un joueur (dons, cotisations, impots), au jour de jeu {@code day}. Appel direct possible. */
    public static void contributed(MinecraftServer server, UUID player, long amount, long day) {
        if (amount <= 0) {
            return;
        }
        ClanStatsData data = ClanStatsData.get(server);
        ClanStatsData.Player p = data.of(player);
        StatsBucket b = day == PioneerApi.day(server) ? p.today(day) : p.at(day);
        if (b != null) {
            b.contributed += amount;
            data.changed();
        }
    }

    /**
     * Contributions lues dans le journal de chaque clan (lignes posterieures a la derniere lecture) : cotisations, impot d'activite, versements
     * au tresor (hors tribut de guerre). Le versant est retrouve par son nom de personnage parmi les membres.
     */
    static void scanJournals(MinecraftServer server) {
        ClanStatsData sd = ClanStatsData.get(server);
        long wall = System.currentTimeMillis();
        for (Clan c : ClanData.get(server).clans()) {
            Long from = sd.scanned.get(c.id);
            if (from == null) {
                sd.scanned.put(c.id, wall);
                sd.changed();
                continue;
            }
            long last = from;
            Map<String, UUID> names = null;
            for (CompoundTag e : c.journal) {
                long t = e.getLong("t");
                if (t <= from) {
                    continue;
                }
                last = Math.max(last, t);
                String k = e.getString("k");
                ListTag a = e.getList("a", Tag.TAG_STRING);
                String who;
                String amount;
                if ("clan.pioneers.log.dues".equals(k) || "clan.pioneers.log.tax".equals(k)) {
                    who = a.getString(0);
                    amount = a.getString(1);
                } else if ("clan.pioneers.log.treasury_in".equals(k) && !a.getString(0).contains("tribute") && !a.getString(0).contains(".war")) {
                    who = a.getString(2);
                    amount = a.getString(1);
                } else {
                    continue;
                }
                if (names == null) {
                    names = names(server, c);
                }
                UUID u = names.get(who);
                if (u != null) {
                    try {
                        contributed(server, u, Long.parseLong(amount), e.getLong("d"));
                    } catch (NumberFormatException ignored) {
                        //montant illisible : ligne ignoree
                    }
                }
            }
            if (last > from) {
                sd.scanned.put(c.id, last);
                sd.changed();
            }
        }
    }

    /** Nom de personnage (et nom du compte, pour les lignes anciennes) -&gt; membre. Reste cote serveur. */
    private static Map<String, UUID> names(MinecraftServer server, Clan c) {
        Map<String, UUID> out = new HashMap<>();
        for (Clan.Member m : c.members.values()) {
            if (m.account != null && !m.account.isBlank()) {
                out.put(m.account, m.id);
            }
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p != null) {
                out.put(EconomyApi.name(p), m.id);
            }
            if (m.character != null && !m.character.isBlank()) {
                out.put(m.character, m.id);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ evenements
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ClanApi.enabled()) {
            long now = ClanClock.now();
            PLAY_FROM.put(p.getUUID(), now);
            ClanStatsData data = ClanStatsData.get(p.getServer());
            StrikePotential.compute(p, data.of(p.getUUID()), now);
            data.changed();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            flushPlay(p.getServer(), p.getUUID(), ClanClock.now());
            PLAY_FROM.remove(p.getUUID());
            DIRTY.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEquipment(LivingEquipmentChangeEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            DIRTY.putIfAbsent(p.getUUID(), ClanClock.now());
        }
    }

    @SubscribeEvent
    public static void onLevelUp(SkillLevelUpEvent e) {
        DIRTY.putIfAbsent(e.player().getUUID(), ClanClock.now());
    }

    @SubscribeEvent
    public static void onVocation(VocationChangedEvent e) {
        DIRTY.putIfAbsent(e.player().getUUID(), ClanClock.now());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || !ClanApi.enabled()) {
            return;
        }
        MinecraftServer server = dead.getServer();
        if (server == null) {
            return;
        }
        long day = PioneerApi.day(server);
        ClanStatsData data = ClanStatsData.get(server);
        if (e.getSource().getEntity() instanceof ServerPlayer killer && killer != dead) {
            StatsBucket b = data.of(killer.getUUID()).today(day);
            if (dead.getType().is(SkillHooks.SENTINELS)) {
                b.sentinels++;
            } else if (dead instanceof Enemy) {
                b.hostiles++;
            } else if (dead instanceof ServerPlayer victim && ClanWar.pvpAllowed(killer, victim)) {
                b.enemies++;
            }
            data.changed();
        }
        if (dead instanceof ServerPlayer victim) {
            data.of(victim.getUUID()).today(day).deaths++;
            data.changed();
        }
    }

    private static void flushPlay(MinecraftServer server, UUID u, long now) {
        Long from = PLAY_FROM.get(u);
        if (from == null || server == null) {
            return;
        }
        long sec = Math.max(0, (now - from) / 1000L);
        if (sec > 0) {
            ClanStatsData data = ClanStatsData.get(server);
            data.of(u).today(PioneerApi.day(server)).played += sec;
            data.changed();
            PLAY_FROM.put(u, from + sec * 1000L);
        }
    }

    // ------------------------------------------------------------------ controle (une fois par seconde)
    /** Potentiels a refaire (une fois par minute au plus), contributions (chaque minute), agregation des clans (toutes les 10 minutes). */
    public static void tick(MinecraftServer server) {
        if (!ClanApi.enabled()) {
            return;
        }
        StatsRules.Values v = StatsRules.get();
        long now = ClanClock.now();
        if (!DIRTY.isEmpty()) {
            ClanStatsData data = ClanStatsData.get(server);
            for (UUID u : new ArrayList<>(DIRTY.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p == null) {
                    DIRTY.remove(u);
                    continue;
                }
                ClanStatsData.Player s = data.of(u);
                if (now - s.potentialAt >= v.minIntervalMs()) {
                    StrikePotential.compute(p, s, now);
                    data.changed();
                    DIRTY.remove(u);
                }
            }
        }
        if (now - lastScan >= 60_000L) {
            lastScan = now;
            scanJournals(server);
        }
        if (now - lastAggregate >= v.aggregateMs()) {
            lastAggregate = now;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                flushPlay(server, p.getUUID(), now);
            }
            UNIT.clear();
            aggregate(server, now);
        }
    }

    // ------------------------------------------------------------------ potentiel
    public static int potentialOf(MinecraftServer server, UUID player) {
        ClanStatsData.Player p = ClanStatsData.get(server).peek(player);
        return p == null ? 0 : p.potential;
    }

    /** Potentiel de frappe d'un clan : somme de ses membres actifs, + 5 % par vocation distincte (15 % au plus). */
    public static int clanPotential(MinecraftServer server, Clan clan) {
        ClanStatsData data = ClanStatsData.get(server);
        long now = ClanClock.now();
        int sum = 0;
        Set<String> voc = new HashSet<>();
        for (Clan.Member m : clan.members.values()) {
            if (!m.active(now)) {
                continue;
            }
            ClanStatsData.Player p = data.peek(m.id);
            if (p != null) {
                sum += p.potential;
                if (!p.vocation.isEmpty()) {
                    voc.add(p.vocation);
                }
            }
        }
        return StatsRules.clanPotential(StatsRules.get(), sum, voc.size());
    }

    // ------------------------------------------------------------------ agregation
    static void aggregate(MinecraftServer server, long now) {
        ClanStatsData data = ClanStatsData.get(server);
        StatsRules.Values v = StatsRules.get();
        long today = PioneerApi.day(server);
        AGG.clear();
        List<Map.Entry<Clan, Agg>> ranked = new ArrayList<>();
        for (Clan c : ClanData.get(server).clans()) {
            Agg a = new Agg(v.days());
            Set<String> voc = new HashSet<>();
            int sum = 0;
            for (Clan.Member m : c.members.values()) {
                ClanStatsData.Player p = data.peek(m.id);
                boolean active = m.active(now);
                if (active) {
                    a.active++;
                }
                if (p == null) {
                    continue;
                }
                if (active) {
                    sum += p.potential;
                    if (!p.vocation.isEmpty()) {
                        voc.add(p.vocation);
                    }
                }
                long mine7 = 0;
                for (int d = 0; d < v.days(); d++) {
                    StatsBucket b = p.at(today - d);
                    if (b == null) {
                        continue;
                    }
                    a.curve[v.days() - 1 - d] += b.total;
                    a.potCurve[v.days() - 1 - d] += b.potential;
                    a.prod30 += b.total;
                    b.produced.forEach((k, x) -> a.skills.merge(k, x, Long::sum));
                    if (d < 7) {
                        a.prod7 += b.total;
                        mine7 += b.total;
                        a.hostiles7 += b.hostiles;
                        a.sentinels7 += b.sentinels;
                        a.enemies7 += b.enemies;
                        a.deaths7 += b.deaths;
                        a.contributed7 += b.contributed;
                    }
                }
                a.member7.put(m.id, mine7);
            }
            a.vocations = voc.size();
            a.potential = StatsRules.clanPotential(v, sum, voc.size());
            a.warehouses = warehouses(server, c);
            a.at = now;
            AGG.put(c.id, a);
            if (!c.solo) {
                ranked.add(Map.entry(c, a));
            }
        }
        ranked.sort(Comparator.comparingLong((Map.Entry<Clan, Agg> e) -> -e.getValue().prod7).thenComparing(e -> e.getKey().name));
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).getValue().rank = i + 1;
            ranked.get(i).getValue().ranked = ranked.size();
        }
    }

    private static Agg agg(MinecraftServer server, Clan c) {
        Agg a = AGG.get(c.id);
        if (a == null) {
            aggregate(server, ClanClock.now());
            lastAggregate = ClanClock.now();
            a = AGG.get(c.id);
        }
        return a == null ? new Agg(StatsRules.get().days()) : a;
    }

    /** Stock des Entrepots (conteneurs designes, chunks charges seulement) : les 12 objets les plus nombreux. */
    private static ListTag warehouses(MinecraftServer server, Clan c) {
        Map<Item, Long> count = new LinkedHashMap<>();
        int seen = 0;
        for (Map.Entry<String, String> e : c.functions.entrySet()) {
            if (!"warehouse".equals(e.getValue())) {
                continue;
            }
            int bar = e.getKey().lastIndexOf('|');
            if (bar <= 0) {
                continue;
            }
            ServerLevel level = ClanTerritory.level(server, e.getKey().substring(0, bar));
            BlockPos pos;
            try {
                pos = BlockPos.of(Long.parseLong(e.getKey().substring(bar + 1)));
            } catch (NumberFormatException x) {
                continue;
            }
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler h = be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (h == null) {
                continue;
            }
            seen++;
            for (int i = 0; i < h.getSlots() && i < 256; i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) {
                    count.merge(s.getItem(), (long) s.getCount(), Long::sum);
                }
            }
        }
        ListTag out = new ListTag();
        count.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(12).forEach(e -> {
            CompoundTag t = new CompoundTag();
            t.putString("item", BuiltInRegistries.ITEM.getKey(e.getKey()).toString());
            t.putLong("count", e.getValue());
            out.add(t);
        });
        if (seen == 0) {
            return out;
        }
        CompoundTag head = new CompoundTag();
        head.putInt("loaded", seen);
        out.add(0, head);
        return out;
    }

    // ------------------------------------------------------------------ fiches de l'onglet Clan
    /**
     * Fiche publique d'un clan (annuaire) : nom, etiquette, devise, couleur, banniere, regime, niveau, effectif, nom de personnage du
     * Representant, potentiel de frappe en trois paliers ({@code weak}, {@code medium}, {@code strong}), jamais le chiffre.
     */
    public static CompoundTag card(MinecraftServer server, Clan c) {
        CompoundTag t = new CompoundTag();
        t.putString("id", c.id.toString());
        t.putString("name", c.name);
        t.putString("tag", c.tag);
        t.putString("motto", c.motto);
        t.putInt("color", c.color);
        t.put("banner", c.banner.copy());
        t.putString("regime", c.regime);
        t.putBoolean("solo", c.solo);
        t.putInt("level", c.level);
        t.putInt("members", c.members.size());
        t.putString("representative", ClanMessages.character(server, c, c.representative));
        t.putString("strength", BRACKETS[StatsRules.bracket(StatsRules.get(), clanPotential(server, c))]);
        return t;
    }

    /** Annuaire public des clans (clans solo exclus). */
    public static CompoundTag directory() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? new CompoundTag() : directory(server);
    }

    public static CompoundTag directory(MinecraftServer server) {
        CompoundTag out = new CompoundTag();
        ListTag l = new ListTag();
        for (Clan c : ClanData.get(server).clans()) {
            if (!c.solo && c.name != null && !c.name.isBlank()) {
                l.add(card(server, c));
            }
        }
        out.put("clans", l);
        return out;
    }

    /**
     * Fiche d'un clan vue par {@code viewer}. Hors du clan : la fiche publique (un allie voit en plus la liste des noms de personnage).
     * Membre : effectif, territoire, guerres, traites, creneaux ; avec la permission de voir les statistiques : production sur 7 et 30 jours
     * (courbe de 30 jours, detail par skill), potentiel exact, combats, contributions, stock des Entrepots, classement ; tresor avec la
     * permission de le voir ; fiche Membres (pseudo du compte seulement avec la permission de voir les identites).
     */
    public static CompoundTag sheet(ServerPlayer viewer, Clan clan) {
        MinecraftServer server = viewer.getServer();
        CompoundTag t = card(server, clan);
        ClanData data = ClanData.get(server);
        Clan mine = data.clanOf(viewer.getUUID());
        boolean member = mine != null && mine.id.equals(clan.id);
        t.putBoolean("member", member);
        if (!member) {
            if (mine != null && ClanApi.relation(server, mine, clan) == ClanApi.Relation.ALLY) {
                ListTag chars = new ListTag();
                for (UUID u : clan.members.keySet()) {
                    chars.add(StringTag.valueOf(ClanMessages.character(server, clan, u)));
                }
                t.put("characters", chars);
            }
            return t;
        }
        UUID me = viewer.getUUID();
        boolean stats = ClanRoles.has(clan, me, Permission.VIEW_STATS), ids = ClanRoles.has(clan, me, Permission.VIEW_IDENTITIES);
        long now = ClanClock.now();
        t.putInt("claims", clan.claimCount());
        t.putInt("budget", ClanTerritory.budget(clan, now));
        t.putLong("points", clan.points);
        t.put("wars", ClanWar.view(server, clan));
        t.put("treaties", ClanDiplomacy.view(server, clan));
        t.putIntArray("windows", ClanWar.windows(clan, now));
        if (clan.pendingWindows != null) {
            t.putIntArray("pendingWindows", clan.pendingWindows);
            t.putLong("windowsFrom", clan.windowsFrom);
        }
        if (ClanRoles.has(clan, me, Permission.TREASURY_VIEW)) {
            t.putLong("treasury", clan.treasury);
            long[] flow = flows(clan, now);
            t.putLong("income7", flow[0]);
            t.putLong("spent7", flow[1]);
        }
        Agg a = stats ? agg(server, clan) : null;
        if (a != null) {
            CompoundTag s = new CompoundTag();
            s.putLong("prod7", a.prod7);
            s.putLong("prod30", a.prod30);
            s.putLongArray("curve", a.curve);
            s.putLongArray("potCurve", a.potCurve);
            CompoundTag sk = new CompoundTag();
            a.skills.forEach(sk::putLong);
            s.put("skills", sk);
            s.putInt("potential", clanPotential(server, clan));
            s.putInt("active", a.active);
            s.putInt("vocations", a.vocations);
            s.putInt("hostiles7", a.hostiles7);
            s.putInt("sentinels7", a.sentinels7);
            s.putInt("enemies7", a.enemies7);
            s.putInt("deaths7", a.deaths7);
            s.putLong("contributed7", a.contributed7);
            s.putInt("rank", a.rank);
            s.putInt("ranked", a.ranked);
            s.put("warehouses", a.warehouses.copy());
            s.putLong("at", a.at);
            t.put("stats", s);
        }
        ClanStatsData sd = ClanStatsData.get(server);
        ListTag ml = new ListTag();
        for (Clan.Member m : clan.members.values()) {
            CompoundTag x = new CompoundTag();
            x.putString("character", ClanMessages.character(server, clan, m.id));
            if (ids && m.account != null) {
                x.putString("account", m.account);
            }
            Clan.Role r = clan.roles.get(m.role);
            x.putString("role", r == null ? m.role : r.name);
            x.putInt("rank", r == null ? 0 : r.rank);
            x.putLong("lastSeen", m.lastSeen);
            x.putLong("joined", m.joined);
            x.putBoolean("online", server.getPlayerList().getPlayer(m.id) != null);
            x.putBoolean("active", m.active(now));
            ClanStatsData.Player p = sd.peek(m.id);
            if (p != null) {
                x.putString("vocation", p.vocation);
                x.putInt("avgSkill10", p.avgSkill10);
                if (stats) {
                    x.putInt("potential", p.potential);
                    x.putLong("prod7", a == null ? 0 : a.member7.getOrDefault(m.id, 0L));
                }
            }
            ml.add(x);
        }
        t.put("roster", ml);
        return t;
    }

    /** Entrees et sorties du tresor sur 7 jours reels, lues dans le journal du clan. */
    private static long[] flows(Clan c, long now) {
        long in = 0, out = 0, since = System.currentTimeMillis() - 7 * 86_400_000L;
        for (CompoundTag e : c.journal) {
            if (e.getLong("t") < since) {
                continue;
            }
            String k = e.getString("k");
            ListTag a = e.getList("a", Tag.TAG_STRING);
            try {
                switch (k) {
                    case "clan.pioneers.log.treasury_in" -> in += Long.parseLong(a.getString(1));
                    case "clan.pioneers.log.treasury_out" -> out += Long.parseLong(a.getString(1));
                    case "clan.pioneers.log.tax", "clan.pioneers.log.dues" -> in += Long.parseLong(a.getString(1));
                    default -> {
                    }
                }
            } catch (NumberFormatException ignored) {
                //montant illisible : ligne ignoree
            }
        }
        return new long[]{in, out};
    }

    /** Statistiques personnelles d'un joueur sur {@code days} jours (Solitaire compris) : production, combats, contribution, presence. */
    public static CompoundTag personal(MinecraftServer server, UUID player, int days) {
        CompoundTag t = new CompoundTag();
        ClanStatsData.Player p = ClanStatsData.get(server).peek(player);
        if (p == null) {
            return t;
        }
        long today = PioneerApi.day(server);
        long prod = 0, contrib = 0, play = 0;
        int h = 0, s = 0, e = 0, d = 0;
        CompoundTag sk = new CompoundTag();
        for (int i = 0; i < Math.min(days, p.ring.length); i++) {
            StatsBucket b = p.at(today - i);
            if (b == null) {
                continue;
            }
            prod += b.total;
            contrib += b.contributed;
            play += b.played;
            h += b.hostiles;
            s += b.sentinels;
            e += b.enemies;
            d += b.deaths;
            b.produced.forEach((k, x) -> sk.putLong(k, sk.getLong(k) + x));
        }
        t.putLong("produced", prod);
        t.put("skills", sk);
        t.putLong("contributed", contrib);
        t.putLong("played", play);
        t.putInt("hostiles", h);
        t.putInt("sentinels", s);
        t.putInt("enemies", e);
        t.putInt("deaths", d);
        t.putInt("potential", p.potential);
        t.putInt("combat", p.combat);
        t.putInt("weapon", p.weapon);
        t.putInt("armor10", p.armor10);
        return t;
    }

    /** Vocation et nom d'un joueur connecte (pour les fiches) ; null s'il est hors ligne. */
    @Nullable
    static String vocationOf(MinecraftServer server, UUID u) {
        ServerPlayer p = server.getPlayerList().getPlayer(u);
        return p == null ? null : PioneerCap.get(p).vocation;
    }
}
