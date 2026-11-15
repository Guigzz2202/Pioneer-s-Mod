package fr.pioneers.society.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : villages connus d'une dimension (donnee de monde). Un village nait a sa decouverte (joueur a moins de 96 blocs de
 * sa cloche) ; identifiant = position de la cloche. Il porte son peuple (reference au registre des peuples), son evenement, son decor, et le
 * REGISTRE de ses villageois : affinite de chacun envers chaque joueur (16 joueurs au plus par villageois). La reputation du village envers
 * un joueur est la moyenne de ce registre (un villageois qui ne connait pas le joueur compte pour 0), jamais une lecture des entites chargees.
 */
public final class VillageData extends SavedData {

    public static final String NAME = "pioneers_villages";
    public static final int MAX_PLAYERS = 16;

    public static final class Village {

        public final long id;
        public final BlockPos center;
        public String people;
        /** Peuple d'avant un ralliement (bloc 4) : le village y retourne a la dissolution d'un peuple de joueurs. */
        public String previousPeople = "";
        public boolean decor;
        public String event = "";
        public String eventItem = "";
        public long eventUntil = -1;
        /** Second evenement (service Entrepot : deux evenements a la fois). */
        public String event2 = "";
        public String event2Item = "";
        public long event2Until = -1;
        public long lastDraw = -1;
        /** Registre : villageois -> (joueur -> affinite). L'ordre des joueurs suit le plus recent. */
        public final Map<UUID, LinkedHashMap<UUID, Integer>> register = new HashMap<>();
        /** Ce que chaque joueur a fait pour le village (restaurations, echanges) : les cinq premiers sur le Tableau. */
        public final Map<UUID, Long> deeds = new HashMap<>();
        public final Map<UUID, String> deedNames = new HashMap<>();
        public final List<BlockPos> fountains = new ArrayList<>(), lamps = new ArrayList<>(), boards = new ArrayList<>();
        /** 1.9 (MAJ RP, bloc 4) : bannieres du decor de la place (refaites aux couleurs du peuple apres un ralliement ou un retour). */
        public final List<BlockPos> banners = new ArrayList<>();
        /** 1.9 (MAJ RP, bloc 4) : peuple dont les bannieres de la place portent les couleurs (vide = celles du decor d'origine). */
        public String bannerPeople = "";
        /** 1.9.1 (retours de Guillaume) : mats des bannieres et version du decor (2 = place d'un seul tenant ; 0 = ancienne regle, a refaire). */
        public final List<BlockPos> masts = new ArrayList<>();
        public int decorVersion;
        public final Set<String> services = new HashSet<>();
        /** Jour de la derniere prime de Veille (+3 une fois par jour). */
        public long standbyDay = -1;
        /** Raids deja comptes (jour de jeu de la victoire). */
        public long raidDay = -1;
        /**
         * 1.0.3-beta (lot 11b) : village fonde par un joueur ({@link FoundingRules}) : fondateur (null : village de PNJ), son nom au moment
         * de la fondation, nom donne au village (vide : « Village de &lt;fondateur&gt; »), village ne de la pose (pas de cloche, pas de decor).
         */
        @Nullable
        public UUID owner;
        public String ownerName = "";
        public String name = "";
        public boolean founded;

        Village(long id, BlockPos center, String people) {
            this.id = id;
            this.center = center;
            this.people = people;
        }

        public int affinity(UUID villager, UUID player) {
            Map<UUID, Integer> m = register.get(villager);
            return m == null ? 0 : m.getOrDefault(player, 0);
        }

        /** Reputation du village envers ce joueur : moyenne du registre (inconnus = 0). */
        public int average(UUID player) {
            if (register.isEmpty()) {
                return 0;
            }
            long sum = 0;
            for (Map<UUID, Integer> m : register.values()) {
                sum += m.getOrDefault(player, 0);
            }
            return (int) Math.round(sum / (double) register.size());
        }

        /** Change l'affinite d'un villageois (borne -100..+100) ; renvoie la nouvelle valeur. */
        int set(UUID villager, UUID player, int value) {
            LinkedHashMap<UUID, Integer> m = register.computeIfAbsent(villager, k -> new LinkedHashMap<>());
            m.remove(player);
            int v = Math.max(-100, Math.min(100, value));
            m.put(player, v);
            //16 joueurs au plus : on oublie le moins recent
            while (m.size() > MAX_PLAYERS) {
                UUID first = m.keySet().iterator().next();
                m.remove(first);
            }
            return v;
        }

        public boolean hasEvent(String type, long day) {
            return type.equals(event) && day < eventUntil || type.equals(event2) && day < event2Until;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id);
            t.putLong("c", center.asLong());
            t.putString("p", people);
            t.putString("pp", previousPeople);
            t.putBoolean("decor", decor);
            t.putString("e", event);
            t.putString("ei", eventItem);
            t.putLong("eu", eventUntil);
            t.putString("e2", event2);
            t.putString("e2i", event2Item);
            t.putLong("e2u", event2Until);
            t.putLong("ld", lastDraw);
            t.putLong("sd", standbyDay);
            t.putLong("rd", raidDay);
            if (owner != null) {
                t.putUUID("own", owner);
                t.putString("ownn", ownerName);
            }
            t.putString("nm", name);
            t.putBoolean("fd", founded);
            ListTag reg = new ListTag();
            register.forEach((v, m) -> {
                CompoundTag c = new CompoundTag();
                c.putUUID("v", v);
                ListTag ps = new ListTag();
                m.forEach((p, a) -> {
                    CompoundTag x = new CompoundTag();
                    x.putUUID("p", p);
                    x.putInt("a", a);
                    ps.add(x);
                });
                c.put("ps", ps);
                reg.add(c);
            });
            t.put("reg", reg);
            ListTag dl = new ListTag();
            deeds.forEach((p, n) -> {
                CompoundTag x = new CompoundTag();
                x.putUUID("p", p);
                x.putLong("n", n);
                x.putString("name", deedNames.getOrDefault(p, ""));
                dl.add(x);
            });
            t.put("deeds", dl);
            t.put("fo", positions(fountains));
            t.put("la", positions(lamps));
            t.put("bo", positions(boards));
            t.put("bn", positions(banners));
            t.putString("bp", bannerPeople);
            t.put("ma", positions(masts));
            t.putInt("dv", decorVersion);
            ListTag sv = new ListTag();
            services.forEach(s -> sv.add(StringTag.valueOf(s)));
            t.put("sv", sv);
            return t;
        }

        static Village load(CompoundTag t) {
            Village v = new Village(t.getLong("id"), BlockPos.of(t.getLong("c")), t.getString("p"));
            v.previousPeople = t.getString("pp");
            v.decor = t.getBoolean("decor");
            v.event = t.getString("e");
            v.eventItem = t.getString("ei");
            v.eventUntil = t.getLong("eu");
            v.event2 = t.getString("e2");
            v.event2Item = t.getString("e2i");
            v.event2Until = t.getLong("e2u");
            v.lastDraw = t.contains("ld") ? t.getLong("ld") : -1;
            v.standbyDay = t.contains("sd") ? t.getLong("sd") : -1;
            v.raidDay = t.contains("rd") ? t.getLong("rd") : -1;
            v.owner = t.hasUUID("own") ? t.getUUID("own") : null;
            v.ownerName = t.getString("ownn");
            v.name = t.getString("nm");
            v.founded = t.getBoolean("fd");
            for (Tag x : t.getList("reg", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) x;
                LinkedHashMap<UUID, Integer> m = new LinkedHashMap<>();
                for (Tag y : c.getList("ps", Tag.TAG_COMPOUND)) {
                    CompoundTag p = (CompoundTag) y;
                    m.put(p.getUUID("p"), p.getInt("a"));
                }
                v.register.put(c.getUUID("v"), m);
            }
            for (Tag x : t.getList("deeds", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) x;
                v.deeds.put(c.getUUID("p"), c.getLong("n"));
                v.deedNames.put(c.getUUID("p"), c.getString("name"));
            }
            read(t.getList("fo", Tag.TAG_LONG), v.fountains);
            read(t.getList("la", Tag.TAG_LONG), v.lamps);
            read(t.getList("bo", Tag.TAG_LONG), v.boards);
            read(t.getList("bn", Tag.TAG_LONG), v.banners);
            v.bannerPeople = t.getString("bp");
            read(t.getList("ma", Tag.TAG_LONG), v.masts);
            v.decorVersion = t.getInt("dv");
            for (Tag x : t.getList("sv", Tag.TAG_STRING)) {
                v.services.add(x.getAsString());
            }
            return v;
        }

        private static ListTag positions(List<BlockPos> list) {
            ListTag l = new ListTag();
            list.forEach(p -> l.add(LongTag.valueOf(p.asLong())));
            return l;
        }

        private static void read(ListTag l, List<BlockPos> into) {
            for (Tag x : l) {
                into.add(BlockPos.of(((LongTag) x).getAsLong()));
            }
        }
    }

    private final Map<Long, Village> villages = new HashMap<>();

    public static VillageData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VillageData::load, VillageData::new, NAME);
    }

    public Collection<Village> all() {
        return villages.values();
    }

    @Nullable
    public Village village(long id) {
        return villages.get(id);
    }

    Village create(BlockPos bell, String people) {
        Village v = villages.computeIfAbsent(bell.asLong(), k -> new Village(k, bell.immutable(), people));
        setDirty();
        return v;
    }

    /** Village le plus proche d'un point (cloche a moins de {@code radius} blocs) ou null. */
    @Nullable
    public Village nearest(BlockPos pos, double radius) {
        Village best = null;
        double bd = radius * radius;
        for (Village v : villages.values()) {
            double d = v.center.distSqr(pos);
            if (d <= bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    /** 1.0.3-beta (lot 11b) : rayon d'un village fonde (ou revendique) : {@link FoundingRules#radius} de ses blocs de village. */
    public static int radius(Village v) {
        return FoundingRules.radius(Math.max(1, v.boards.size() + v.fountains.size() + v.lamps.size()));
    }

    /** 1.0.3-beta (lot 11b) : village a fondateur le plus proche dont le rayon (a plat) contient ce point, ou null. */
    @Nullable
    public Village owned(BlockPos pos) {
        Village best = null;
        double bd = Double.MAX_VALUE;
        for (Village v : villages.values()) {
            if (v.owner == null) {
                continue;
            }
            double dx = v.center.getX() - pos.getX(), dz = v.center.getZ() - pos.getZ(), d = dx * dx + dz * dz;
            int r = radius(v);
            if (d <= (double) r * r && d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    public List<Village> within(BlockPos pos, double radius) {
        List<Village> out = new ArrayList<>();
        for (Village v : villages.values()) {
            if (v.center.distSqr(pos) <= radius * radius) {
                out.add(v);
            }
        }
        return out;
    }

    public void changed() {
        setDirty();
    }

    /** 1.0.3-beta (lot 11b) : retire un village (nettoyage des GameTests seulement). */
    public void remove(long id) {
        if (villages.remove(id) != null) {
            setDirty();
        }
    }

    private static VillageData load(CompoundTag tag) {
        VillageData d = new VillageData();
        for (Tag t : tag.getList("v", Tag.TAG_COMPOUND)) {
            Village v = Village.load((CompoundTag) t);
            d.villages.put(v.id, v);
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag l = new ListTag();
        villages.values().forEach(v -> l.add(v.save()));
        tag.put("v", l);
        return tag;
    }
}
