package fr.pioneers.core.profile;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.registries.PiTriggers;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.9 (MAJ RP, bloc 2) : FACADE du personnage. Seul point d'entree de l'XP ({@link #gainXp}) : Regle de la Main (main 100 %, machines du
 * joueur 20 % sous conditions et plafond journalier persistant), affinite de vocation, brigade, fatigue de repetition, sources deja comptees
 * dans la journee ; lecture des niveaux et des modificateurs (skills lineaires + traits de vocation, en cache dans le profil).
 */
public final class PioneerApi {

    public enum XpSource {
        HAND, AUTOMATIC
    }

    public static final int MAX_LEVEL = 10;
    /** Rayon de la brigade (blocs) ; le bloc 4 l'etend a 48 blocs autour d'un Atelier de clan. */
    public static ToIntFunction<ServerPlayer> brigadeRadius = p -> PiCommonConfig.get(RpConfig.BRIGADE_RADIUS, 24);

    /** 1.9 (MAJ RP, bloc 4) : crochet de la production (statistiques de clan), appele par chaque gain d'XP apres la fatigue. */
    public interface Production {

        void produced(ServerPlayer player, String skill, String action, double base, double fatigue);
    }

    /** Branche par {@code ClanStats.install} (bloc 4) ; null tant que les clans ne sont pas charges. */
    @Nullable
    public static volatile Production production;

    /** Interrupteurs envoyes par le serveur a un client (qui n'a pas le fichier de reglages du serveur) ; null cote serveur. */
    @Nullable
    public static volatile Map<String, Boolean> remoteFeatures;

    public static final String[] FEATURES = {"economy", "clans", "ruins", "sentinels", "war"};

    private PioneerApi() {
    }

    // ------------------------------------------------------------------ courbe
    /** XP cumulee pour atteindre le niveau n : passer de n-1 a n coute 100 n (5 500 au niveau 10). */
    public static double xpFor(int level) {
        return 50.0 * level * (level + 1);
    }

    public static int levelOf(double xp) {
        int l = 0;
        while (l < MAX_LEVEL && xp >= xpFor(l + 1)) {
            l++;
        }
        return l;
    }

    public static int level(Player player, String skill) {
        return levelOf(PioneerCap.get(player).xp(skill));
    }

    @Nullable
    public static String vocation(Player player) {
        String v = PioneerCap.get(player).vocation;
        return v.isEmpty() ? null : v;
    }

    /** Interrupteurs des blocs : "economy", "clans", "ruins", "sentinels", "war". */
    public static boolean hasFeature(String name) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        Map<String, Boolean> remote = remoteFeatures;
        if (remote != null && remote.containsKey(name)) {
            return remote.get(name);
        }
        return switch (name) {
            case "economy" -> PiCommonConfig.get(RpConfig.ECONOMY, true);
            case "clans" -> PiCommonConfig.get(RpConfig.CLANS, true);
            case "ruins" -> PiCommonConfig.get(RpConfig.RUINS, true);
            case "sentinels" -> PiCommonConfig.get(RpConfig.SENTINELS, true);
            case "war" -> PiCommonConfig.get(RpConfig.WAR, true);
            default -> false;
        };
    }

    // ------------------------------------------------------------------ modificateurs
    /**
     * Somme des effets de la cle : skills (valeur au niveau 10 x niveau / 10) et traits de la vocation (valeur fixe). Un pourcentage est
     * renvoye en fraction (0.2 = +20 %). Toujours borne par les garde-fous (25 % pour un bonus de skill ou d'avantage, 15 % pour un defaut) :
     * les fichiers sont verifies au chargement par tools/check_pioneer.py, le code borne en plus.
     */
    public static double modifier(Player player, String key) {
        PioneerProfile p = PioneerCap.get(player);
        if (p.dirty || p.cache.isEmpty()) {
            rebuild(p);
        }
        return p.cache.getOrDefault(key, 0.0);
    }

    /** {@code (1 + modifier)} borne a 0 au moins : facteur pret a multiplier. */
    public static double factor(Player player, String key) {
        return Math.max(0, 1 + modifier(player, key));
    }

    /**
     * 1.0.5-beta (lot 10, decision 10 de la specification) : plafonds propres des effets de la Chasse (valeurs validees : +30 % de viande et de
     * cuir, trophee x 2, -30 % de dispersion, +50 % de flair) ; 25 % pour tous les autres effets. Meme table que {@code SPEC_CEILINGS} de
     * T:rp/pioneer_data.py.
     */
    public static final Map<String, Double> CEILINGS = Map.of("hunting.yield", 0.30, "hunting.trophy", 1.0, "hunting.reload", 0.25,
          "hunting.spread", 0.30, "hunting.scent", 0.50);

    public static double ceiling(String key) {
        return CEILINGS.getOrDefault(key, 0.25);
    }

    static void rebuild(PioneerProfile p) {
        PioneerDefs defs = PioneerDefs.get();
        Map<String, Double> out = new HashMap<>();
        for (PioneerDefs.Skill s : defs.skills.values()) {
            if (!hasFeature(s.feature())) {
                continue;
            }
            int l = levelOf(p.xp(s.id()));
            for (PioneerDefs.Mod m : s.mods()) {
                double v = m.value() * l / MAX_LEVEL;
                if (!m.abs()) {
                    double cap = ceiling(m.key());
                    v = Math.max(-cap, Math.min(cap, v));
                }
                out.merge(m.key(), v, Double::sum);
            }
        }
        PioneerDefs.Vocation v = defs.vocation(p.vocation);
        if (v != null && hasFeature(v.feature())) {
            for (PioneerDefs.Mod m : v.advantage().mods()) {
                out.merge(m.key(), m.abs() ? m.value() : Math.max(-0.25, Math.min(0.25, m.value())), Double::sum);
            }
            for (PioneerDefs.Mod m : v.defect().mods()) {
                out.merge(m.key(), m.abs() ? m.value() : Math.max(-0.15, Math.min(0.15, m.value())), Double::sum);
            }
        }
        p.cache.clear();
        p.cache.putAll(out);
        p.cache.put("", 0.0);
        p.dirty = false;
    }

    // ------------------------------------------------------------------ jour de jeu
    public static long day(@Nullable MinecraftServer server) {
        if (server == null) {
            server = ServerLifecycleHooks.getCurrentServer();
        }
        return server == null ? 0 : server.overworld().getDayTime() / 24_000L;
    }

    public static long day(Level level) {
        return level.getServer() != null ? day(level.getServer()) : level.getDayTime() / 24_000L;
    }

    // ------------------------------------------------------------------ XP
    /** Action a la main sans position (la table {@code xp_sources} donne le skill et l'XP de base). */
    public static void gain(ServerPlayer player, String action) {
        gain(player, action, 1.0, null);
    }

    /** Action a la main a une position : une position n'est comptee qu'une fois par jour de jeu et par joueur (poser puis casser...). */
    public static void gain(ServerPlayer player, String action, double count, @Nullable BlockPos pos) {
        PioneerDefs.Source s = PioneerDefs.get().sources.get(action);
        if (s != null) {
            gainXp(player, s.skill(), s.xp() * count, XpSource.HAND, action, pos);
        }
    }

    /**
     * Action d'une machine, d'un robot ou d'un programme : 20 % de l'XP, seulement si le proprietaire est connecte, dans la meme dimension, a
     * {@code autoRadius} blocs au plus, et que le chunk est charge.
     */
    public static void gainAuto(ServerLevel level, BlockPos machine, @Nullable UUID owner, String action, double count) {
        if (owner == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != level || !level.isLoaded(machine)) {
            return;
        }
        double r = PiCommonConfig.get(RpConfig.AUTO_RADIUS, 32);
        if (player.distanceToSqr(machine.getX() + 0.5, machine.getY() + 0.5, machine.getZ() + 0.5) > r * r) {
            return;
        }
        PioneerDefs.Source s = PioneerDefs.get().sources.get(action);
        if (s != null) {
            gainXp(player, s.skill(), s.xp() * count, XpSource.AUTOMATIC, action, null);
        }
    }

    /** Le joueur peut-il gagner de l'XP (ni faux joueur, ni spectateur, ni creatif sauf reglage) ? */
    public static boolean learns(ServerPlayer player) {
        if (player instanceof FakePlayer || player.isSpectator()) {
            return false;
        }
        return !player.isCreative() || PiCommonConfig.get(RpConfig.XP_IN_CREATIVE, false);
    }

    /** Coeur des gains d'XP. */
    public static void gainXp(ServerPlayer player, String skill, double base, XpSource src, String action, @Nullable BlockPos pos) {
        PioneerDefs defs = PioneerDefs.get();
        PioneerDefs.Skill def = defs.skill(skill);
        if (def == null || base <= 0 || !learns(player) || !hasFeature(def.feature())) {
            return;
        }
        PioneerProfile p = PioneerCap.get(player);
        long day = day(player.getServer());
        AntiCheat.roll(day);
        if (pos != null && src == XpSource.HAND && !AntiCheat.firstToday(player.getUUID(), action, pos)) {
            return;
        }
        double amount = base * (src == XpSource.HAND ? PiCommonConfig.get(RpConfig.HAND_XP, 1.0) : PiCommonConfig.get(RpConfig.AUTO_XP, 0.2));
        double fatigue = AntiCheat.fatigue(player.getUUID(), action);
        amount *= fatigue;
        //1.9 (MAJ RP, bloc 4) : valeur produite pour les statistiques de clan (meme fatigue ; aucun ecouteur par tick)
        if (production != null) {
            production.produced(player, skill, action, base, fatigue);
        }
        PioneerDefs.Vocation v = defs.vocation(p.vocation);
        if (v != null && v.affine(skill) && hasFeature(v.feature())) {
            amount *= PiCommonConfig.get(RpConfig.AFFINITY, 1.5);
        }
        if (src == XpSource.HAND) {
            amount *= 1 + brigade(player, p);
        } else {
            if (p.autoDay != day) {
                p.autoDay = day;
                p.autoToday.clear();
            }
            double cap = PiCommonConfig.get(RpConfig.AUTO_CAP, 300);
            double done = p.autoToday.getOrDefault(skill, 0.0);
            if (done >= cap) {
                Notifier.capReached(player, skill, day);
                return;
            }
            amount = Math.min(amount, cap - done);
            p.autoToday.put(skill, done + amount);
        }
        if (amount <= 0) {
            return;
        }
        int before = levelOf(p.xp(skill));
        double total = Math.min(xpFor(MAX_LEVEL) * 4, p.xp(skill) + amount);
        p.xp.put(skill, total);
        int after = levelOf(total);
        MinecraftForge.EVENT_BUS.post(new XpGainedEvent(player, skill, action, amount, src == XpSource.AUTOMATIC));
        Notifier.gained(player, skill, amount, src == XpSource.AUTOMATIC);
        if (after > before) {
            p.touch();
            for (int l = before + 1; l <= after; l++) {
                p.journal(day, "pioneer.pioneers.level_up", String.valueOf(l), "#skill.pioneers." + skill);
                MinecraftForge.EVENT_BUS.post(new SkillLevelUpEvent(player, skill, l));
            }
            Notifier.levelUp(player, skill, after);
            if (after == MAX_LEVEL && p.masteries.add(skill)) {
                p.journal(day, "pioneer.pioneers.mastery", "#skill.pioneers." + skill + ".mastery");
                MinecraftForge.EVENT_BUS.post(new MasteryEvent(player, skill));
                Notifier.mastery(player, skill);
            }
            advancements(player, p);
            ProfileSync.full(player);
        }
    }

    /**
     * 1.9 (MAJ RP, lot 19) : progres du pionnier (critere {@code pioneers:mechanics}) : niveau 5 d'un savoir-faire, Maitrise, reconversion.
     * Appele a chaque passage de niveau et a la connexion (un profil qui les meritait deja les recoit).
     */
    public static void advancements(ServerPlayer player, PioneerProfile p) {
        int best = 0;
        for (double xp : p.xp.values()) {
            best = Math.max(best, levelOf(xp));
        }
        if (best >= 5) {
            PiTriggers.MECHANICS.trigger(player, "pioneer_level_5");
        }
        if (!p.masteries.isEmpty()) {
            PiTriggers.MECHANICS.trigger(player, "pioneer_mastery");
        }
        if (p.lastReconversion >= 0) {
            PiTriggers.MECHANICS.trigger(player, "pioneer_reconversion");
        }
    }

    /** Brigade : +5 % par vocation distincte d'un autre pionnier present dans le rayon, +15 % au plus. */
    static double brigade(ServerPlayer player, PioneerProfile p) {
        int radius = brigadeRadius.applyAsInt(player);
        Set<String> seen = new HashSet<>();
        double r2 = (double) radius * radius;
        for (Player other : player.level().players()) {
            if (other == player || other.isSpectator() || other.distanceToSqr(player) > r2) {
                continue;
            }
            String v = PioneerCap.get(other).vocation;
            if (!v.isEmpty() && !v.equals(p.vocation)) {
                seen.add(v);
            }
        }
        if (seen.isEmpty()) {
            return 0;
        }
        return Math.min(PiCommonConfig.get(RpConfig.BRIGADE_MAX, 0.15), seen.size() * PiCommonConfig.get(RpConfig.BRIGADE_STEP, 0.05));
    }

    public static int brigadePercent(ServerPlayer player) {
        return (int) Math.round(brigade(player, PioneerCap.get(player)) * 100);
    }

    // ------------------------------------------------------------------ anti-triche (en memoire)
    /** Fatigue de repetition (fenetre glissante d'une minute) et sources deja comptees dans la journee (bornees). */
    static final class AntiCheat {

        private static final Map<UUID, Map<String, Deque<Long>>> WINDOWS = new HashMap<>();
        private static final Map<UUID, Set<Long>> COUNTED = new HashMap<>();
        private static long day = Long.MIN_VALUE;
        static final int MAX_SOURCES = 4096;

        private AntiCheat() {
        }

        static void roll(long today) {
            if (today != day) {
                day = today;
                COUNTED.clear();
            }
        }

        static boolean firstToday(UUID player, String action, BlockPos pos) {
            Set<Long> set = COUNTED.computeIfAbsent(player, k -> new HashSet<>());
            if (set.size() >= MAX_SOURCES) {
                set.clear();
            }
            return set.add(pos.asLong() * 31 + action.hashCode());
        }

        static double fatigue(UUID player, String action) {
            long now = Util.getMillis();
            Deque<Long> d = WINDOWS.computeIfAbsent(player, k -> new LinkedHashMap<>()).computeIfAbsent(action, k -> new ArrayDeque<>());
            while (!d.isEmpty() && now - d.peekFirst() > 60_000) {
                d.pollFirst();
            }
            d.addLast(now);
            if (d.size() > 512) {
                d.pollFirst();
            }
            int n = d.size();
            if (n > PiCommonConfig.get(RpConfig.FATIGUE_TENTH, 60)) {
                return 0.1;
            }
            return n > PiCommonConfig.get(RpConfig.FATIGUE_HALF, 30) ? 0.5 : 1.0;
        }

        static void forget(UUID player) {
            WINDOWS.remove(player);
            COUNTED.remove(player);
        }

        static void clear() {
            WINDOWS.clear();
            COUNTED.clear();
            day = Long.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------ notifications (regroupees, envoyees toutes les 10 ticks)
    static final class Notifier {

        private static final Map<UUID, Map<String, double[]>> PENDING = new HashMap<>();
        private static final Map<UUID, Long> CAP_TOLD = new HashMap<>();

        private Notifier() {
        }

        static void gained(ServerPlayer player, String skill, double amount, boolean auto) {
            double[] acc = PENDING.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>()).computeIfAbsent(skill + (auto ? "@" : ""),
                  k -> new double[1]);
            acc[0] += amount;
        }

        static void levelUp(ServerPlayer player, String skill, int level) {
            CompoundTag t = new CompoundTag();
            t.putString("skill", skill);
            t.putInt("level", level);
            t.putString("kind", "level");
            RpNet.send(player, RpActions.TOAST, t);
        }

        static void mastery(ServerPlayer player, String skill) {
            CompoundTag t = new CompoundTag();
            t.putString("skill", skill);
            t.putString("kind", "mastery");
            RpNet.send(player, RpActions.TOAST, t);
        }

        static void capReached(ServerPlayer player, String skill, long day) {
            Long told = CAP_TOLD.get(player.getUUID());
            if (told == null || told != day) {
                CAP_TOLD.put(player.getUUID(), day);
                CompoundTag t = new CompoundTag();
                t.putString("kind", "cap");
                RpNet.send(player, RpActions.TOAST, t);
            }
        }

        /** Envoie les gains accumules (appele toutes les 10 ticks par ProfileEvents). */
        static void flush(MinecraftServer server) {
            if (PENDING.isEmpty()) {
                return;
            }
            for (Map.Entry<UUID, Map<String, double[]>> e : PENDING.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
                if (player == null) {
                    continue;
                }
                PioneerProfile p = PioneerCap.get(player);
                ListTag list = new ListTag();
                for (Map.Entry<String, double[]> g : e.getValue().entrySet()) {
                    boolean auto = g.getKey().endsWith("@");
                    String skill = auto ? g.getKey().substring(0, g.getKey().length() - 1) : g.getKey();
                    CompoundTag c = new CompoundTag();
                    c.putString("s", skill);
                    c.putDouble("g", g.getValue()[0]);
                    c.putBoolean("a", auto);
                    c.putDouble("x", p.xp(skill));
                    list.add(c);
                    int shown = (int) Math.round(g.getValue()[0]);
                    if (shown >= 1) {
                        p.recent.addFirst(new PioneerProfile.Gain(skill, shown, auto));
                        while (p.recent.size() > PioneerProfile.RECENT) {
                            p.recent.pollLast();
                        }
                    }
                }
                CompoundTag t = new CompoundTag();
                t.put("gains", list);
                RpNet.send(player, RpActions.XP_GAIN, t);
            }
            PENDING.clear();
        }

        static void forget(UUID player) {
            PENDING.remove(player);
            CAP_TOLD.remove(player);
        }
    }
}
