package fr.pioneers.core.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 1.9 (MAJ RP, bloc 2) : definitions des skills, des vocations et des gains d'XP, lues dans {@code data/pioneers/pioneer/} par
 * {@link PioneerDefsLoader} (cote serveur) puis envoyees aux clients (memes objets, pour l'interface du Carnet). Rien d'equilibrage en dur :
 * tout vient des fichiers ecrits par {@code tools/gc/rp/pioneer_data.py}.
 */
public final class PioneerDefs {

    /** Un effet chiffre : cle, valeur atteinte au niveau 10 (skills) ou valeur fixe (traits de vocation), {@code abs} = pas un pourcentage. */
    public record Mod(String key, double value, boolean abs) {

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("k", key);
            t.putDouble("v", value);
            t.putBoolean("a", abs);
            return t;
        }

        static Mod load(CompoundTag t) {
            return new Mod(t.getString("k"), t.getDouble("v"), t.getBoolean("a"));
        }
    }

    public record Skill(String id, int order, String icon, String feature, List<Mod> mods) {
    }

    public record Trait(String id, List<Mod> mods) {

        /** 1.9.1 (retours de Guillaume) : trait absent (vocation neutre : ni avantage ni defaut). */
        public static final Trait NONE = new Trait("", List.of());

        public boolean present() {
            return !id.isEmpty();
        }
    }

    public record Vocation(String id, int order, List<String> affinities, String feature, String emblem, Trait advantage, Trait defect) {

        public boolean affine(String skill) {
            return affinities.contains(skill);
        }

        /**
         * 1.9.1 (retours de Guillaume) : vocation neutre (le Polyvalent) : aucune affinite, ni avantage ni defaut. Elle compte quand meme
         * comme une vocation differente pour la Brigade.
         */
        public boolean neutral() {
            return affinities.isEmpty() && !advantage.present() && !defect.present();
        }
    }

    /** Action -> skill et XP de base. */
    public record Source(String skill, double xp) {
    }

    private static volatile PioneerDefs current = new PioneerDefs(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());

    public final Map<String, Skill> skills;
    public final Map<String, Vocation> vocations;
    public final Map<String, Source> sources;

    PioneerDefs(Map<String, Skill> skills, Map<String, Vocation> vocations, Map<String, Source> sources) {
        this.skills = Collections.unmodifiableMap(skills);
        this.vocations = Collections.unmodifiableMap(vocations);
        this.sources = Collections.unmodifiableMap(sources);
    }

    public static PioneerDefs get() {
        return current;
    }

    static void set(PioneerDefs defs) {
        current = defs;
    }

    @Nullable
    public Skill skill(String id) {
        return skills.get(id);
    }

    @Nullable
    public Vocation vocation(String id) {
        return id == null ? null : vocations.get(id);
    }

    /** Skills dans l'ordre du Carnet. */
    public List<Skill> orderedSkills() {
        List<Skill> l = new ArrayList<>(skills.values());
        l.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return l;
    }

    public List<Vocation> orderedVocations() {
        List<Vocation> l = new ArrayList<>(vocations.values());
        l.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return l;
    }

    // ------------------------------------------------------------------ envoi aux clients
    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag sk = new ListTag();
        for (Skill s : skills.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", s.id());
            t.putInt("o", s.order());
            t.putString("i", s.icon());
            t.putString("f", s.feature());
            t.put("m", mods(s.mods()));
            sk.add(t);
        }
        root.put("skills", sk);
        ListTag vo = new ListTag();
        for (Vocation v : vocations.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", v.id());
            t.putInt("o", v.order());
            ListTag aff = new ListTag();
            v.affinities().forEach(a -> aff.add(StringTag.valueOf(a)));
            t.put("a", aff);
            t.putString("f", v.feature());
            t.putString("e", v.emblem());
            t.putString("at", v.advantage().id());
            t.put("am", mods(v.advantage().mods()));
            t.putString("dt", v.defect().id());
            t.put("dm", mods(v.defect().mods()));
            vo.add(t);
        }
        root.put("vocations", vo);
        CompoundTag src = new CompoundTag();
        sources.forEach((k, s) -> {
            CompoundTag t = new CompoundTag();
            t.putString("s", s.skill());
            t.putDouble("x", s.xp());
            src.put(k, t);
        });
        root.put("sources", src);
        return root;
    }

    private static ListTag mods(List<Mod> mods) {
        ListTag l = new ListTag();
        mods.forEach(m -> l.add(m.save()));
        return l;
    }

    private static List<Mod> mods(ListTag l) {
        List<Mod> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            out.add(Mod.load(l.getCompound(i)));
        }
        return List.copyOf(out);
    }

    public static PioneerDefs load(CompoundTag root) {
        Map<String, Skill> sk = new LinkedHashMap<>();
        ListTag l = root.getList("skills", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            sk.put(t.getString("id"), new Skill(t.getString("id"), t.getInt("o"), t.getString("i"), t.getString("f"),
                  mods(t.getList("m", Tag.TAG_COMPOUND))));
        }
        Map<String, Vocation> vo = new LinkedHashMap<>();
        l = root.getList("vocations", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            List<String> aff = new ArrayList<>();
            ListTag a = t.getList("a", Tag.TAG_STRING);
            for (int j = 0; j < a.size(); j++) {
                aff.add(a.getString(j));
            }
            vo.put(t.getString("id"), new Vocation(t.getString("id"), t.getInt("o"), List.copyOf(aff), t.getString("f"), t.getString("e"),
                  new Trait(t.getString("at"), mods(t.getList("am", Tag.TAG_COMPOUND))),
                  new Trait(t.getString("dt"), mods(t.getList("dm", Tag.TAG_COMPOUND)))));
        }
        Map<String, Source> src = new LinkedHashMap<>();
        CompoundTag s = root.getCompound("sources");
        for (String k : s.getAllKeys()) {
            CompoundTag t = s.getCompound(k);
            src.put(k, new Source(t.getString("s"), t.getDouble("x")));
        }
        return new PioneerDefs(sk, vo, src);
    }

    /** Recu du serveur (client) ; sur un serveur integre, c'est la meme instance. */
    public static void receive(CompoundTag root) {
        set(load(root));
    }
}
