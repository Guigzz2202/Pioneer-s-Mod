package fr.pioneers.core.profile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 1.9 (MAJ RP, bloc 2) : le personnage d'un joueur dans CE monde (capacite du joueur, sauvee avec lui, recopiee a la mort, synchronisee avec
 * son client). Les blocs suivants l'etendent (solde, reputations, journal des comptes, clan) avec une version de format et une migration
 * silencieuse. Seul {@link PioneerApi} modifie l'XP.
 */
public class PioneerProfile implements INBTSerializable<CompoundTag> {

    /** Version du format (1 : bloc 2 ; 2 : bloc 3, solde et reputations ; 3 : bloc 4, clan). */
    public static final int FORMAT = 3;
    public static final int RECENT = 20, JOURNAL = 60, LEDGER = 200;

    // ------------------------------------------------------------------ personnage
    public boolean initiated;
    /**
     * Etape de l'initiation en cours (0 nom, 1 vocation, 2 lecture) : une deconnexion reprend ici. 1.12.0 : l'etape de l'allure est retiree
     * (habillage : garde-robe cosmetique ; barbe et coiffure : skin du joueur) ; une etape 3 d'avant est relue comme la lecture.
     */
    public int initStep;
    public String name = "";
    public String vocation = "";
    public long createdDay;
    public boolean notebookGiven;
    /** Jour de jeu de la derniere reconversion (-1 : jamais). */
    public long lastReconversion = -1;

    // ------------------------------------------------------------------ skills
    public final Map<String, Double> xp = new HashMap<>();
    /** Titres de Maitrise obtenus : ils restent meme si le niveau baisse (reconversion). */
    public final Set<String> masteries = new LinkedHashSet<>();
    /** Plafond d'XP automatique, persistant : jour de jeu et XP deja comptee par skill ce jour-la. */
    public long autoDay = -1;
    public final Map<String, Double> autoToday = new HashMap<>();
    public final Deque<Gain> recent = new ArrayDeque<>();
    public final List<JournalLine> journal = new ArrayList<>();

    // ------------------------------------------------------------------ lore (LoreData, bloc 1)
    public final Set<String> loreRead = new LinkedHashSet<>();
    public final Set<String> notesRead = new LinkedHashSet<>();

    // ------------------------------------------------------------------ bloc 3 : monnaie et reputations
    public long balance;
    /** Reputation par peuple (identifiant du registre des peuples -> -300..+300). */
    public final Map<String, Integer> reputation = new HashMap<>();
    /** Points de reputation gagnes aujourd'hui, par peuple (plafond journalier). */
    public long repDay = -1;
    public final Map<String, Integer> repToday = new HashMap<>();
    /** Lectures de Codex comptees aujourd'hui (5 par jour au plus). */
    public int loreToday;
    public final Deque<CompoundTag> ledger = new ArrayDeque<>();
    public final Deque<CompoundTag> repLog = new ArrayDeque<>();
    /** Virements du jour (plafond quotidien). */
    public long payDay = -1, paidToday;
    /** Marques diverses de l'economie (confidences recues, manuels lus, caravanes conclues...). */
    public final Set<String> marks = new LinkedHashSet<>();

    // ------------------------------------------------------------------ bloc 4 : clan (cache, la verite est dans ClanData)
    public String clan = "";

    /** Modificateurs calcules (skills + vocation), vides a chaque changement. */
    public final transient Map<String, Double> cache = new HashMap<>();
    public transient boolean dirty = true;

    public record Gain(String skill, int amount, boolean auto) {
    }

    /** Une ligne du journal du Carnet : jour, cle de langue et arguments (deja des textes ou des cles prefixees par "#"). */
    public record JournalLine(long day, String key, List<String> args) {
    }

    public double xp(String skill) {
        return xp.getOrDefault(skill, 0.0);
    }

    public void journal(long day, String key, String... args) {
        journal.add(new JournalLine(day, key, List.of(args)));
        while (journal.size() > JOURNAL) {
            journal.remove(0);
        }
    }

    public void touch() {
        dirty = true;
        cache.clear();
    }

    public int reputation(String people) {
        return reputation.getOrDefault(people, 0);
    }

    // ------------------------------------------------------------------ NBT
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag t = new CompoundTag();
        t.putInt("format", FORMAT);
        t.putBoolean("initiated", initiated);
        t.putInt("step", initStep);
        t.putString("name", name);
        t.putString("vocation", vocation);
        t.putLong("created", createdDay);
        t.putBoolean("book", notebookGiven);
        t.putLong("reconv", lastReconversion);
        CompoundTag x = new CompoundTag();
        xp.forEach(x::putDouble);
        t.put("xp", x);
        t.put("mastery", strings(masteries));
        t.putLong("autoDay", autoDay);
        CompoundTag a = new CompoundTag();
        autoToday.forEach(a::putDouble);
        t.put("auto", a);
        ListTag r = new ListTag();
        for (Gain g : recent) {
            CompoundTag c = new CompoundTag();
            c.putString("s", g.skill());
            c.putInt("n", g.amount());
            c.putBoolean("a", g.auto());
            r.add(c);
        }
        t.put("recent", r);
        ListTag j = new ListTag();
        for (JournalLine l : journal) {
            CompoundTag c = new CompoundTag();
            c.putLong("d", l.day());
            c.putString("k", l.key());
            c.put("a", strings(l.args()));
            j.add(c);
        }
        t.put("journal", j);
        t.put("lore", strings(loreRead));
        t.put("notes", strings(notesRead));
        t.putLong("balance", balance);
        CompoundTag rep = new CompoundTag();
        reputation.forEach(rep::putInt);
        t.put("rep", rep);
        t.putLong("repDay", repDay);
        CompoundTag rt = new CompoundTag();
        repToday.forEach(rt::putInt);
        t.put("repToday", rt);
        t.putInt("loreToday", loreToday);
        t.put("ledger", tags(ledger));
        t.put("repLog", tags(repLog));
        t.putLong("payDay", payDay);
        t.putLong("paid", paidToday);
        t.put("marks", strings(marks));
        t.putString("clan", clan);
        return t;
    }

    @Override
    public void deserializeNBT(CompoundTag t) {
        int format = t.getInt("format");
        initiated = t.getBoolean("initiated");
        initStep = Math.min(2, t.getInt("step"));
        name = t.getString("name");
        vocation = t.getString("vocation");
        createdDay = t.getLong("created");
        notebookGiven = t.getBoolean("book");
        //1.12.0 (lot E4) : l'ancienne allure (cle « allure ») n'est plus lue : elle disparait a la prochaine sauvegarde
        lastReconversion = t.contains("reconv") ? t.getLong("reconv") : -1;
        xp.clear();
        CompoundTag x = t.getCompound("xp");
        for (String k : x.getAllKeys()) {
            xp.put(k, x.getDouble(k));
        }
        masteries.clear();
        masteries.addAll(strings(t.getList("mastery", Tag.TAG_STRING)));
        autoDay = t.contains("autoDay") ? t.getLong("autoDay") : -1;
        autoToday.clear();
        CompoundTag a = t.getCompound("auto");
        for (String k : a.getAllKeys()) {
            autoToday.put(k, a.getDouble(k));
        }
        recent.clear();
        ListTag r = t.getList("recent", Tag.TAG_COMPOUND);
        for (int i = 0; i < r.size(); i++) {
            CompoundTag c = r.getCompound(i);
            recent.add(new Gain(c.getString("s"), c.getInt("n"), c.getBoolean("a")));
        }
        journal.clear();
        ListTag j = t.getList("journal", Tag.TAG_COMPOUND);
        for (int i = 0; i < j.size(); i++) {
            CompoundTag c = j.getCompound(i);
            journal.add(new JournalLine(c.getLong("d"), c.getString("k"), List.copyOf(strings(c.getList("a", Tag.TAG_STRING)))));
        }
        loreRead.clear();
        loreRead.addAll(strings(t.getList("lore", Tag.TAG_STRING)));
        notesRead.clear();
        notesRead.addAll(strings(t.getList("notes", Tag.TAG_STRING)));
        balance = Math.max(0, t.getLong("balance"));
        reputation.clear();
        if (format < 2 && t.contains("rep_restorers")) {
            //Migration silencieuse d'un ancien profil a trois champs fixes vers la table identifiant -> entier
            reputation.put("restorers", t.getInt("rep_restorers"));
            reputation.put("bare_hands", t.getInt("rep_bare_hands"));
            reputation.put("peddlers", t.getInt("rep_peddlers"));
        }
        CompoundTag rep = t.getCompound("rep");
        for (String k : rep.getAllKeys()) {
            reputation.put(k, rep.getInt(k));
        }
        repDay = t.contains("repDay") ? t.getLong("repDay") : -1;
        repToday.clear();
        CompoundTag rt = t.getCompound("repToday");
        for (String k : rt.getAllKeys()) {
            repToday.put(k, rt.getInt(k));
        }
        loreToday = t.getInt("loreToday");
        ledger.clear();
        ledger.addAll(tags(t.getList("ledger", Tag.TAG_COMPOUND)));
        repLog.clear();
        repLog.addAll(tags(t.getList("repLog", Tag.TAG_COMPOUND)));
        payDay = t.contains("payDay") ? t.getLong("payDay") : -1;
        paidToday = t.getLong("paid");
        marks.clear();
        marks.addAll(strings(t.getList("marks", Tag.TAG_STRING)));
        clan = t.getString("clan");
        touch();
    }

    /** Copie complete (mort, changement de dimension) : passe par le NBT pour ne rien oublier. */
    public void copyFrom(PioneerProfile other) {
        deserializeNBT(other.serializeNBT());
    }

    private static ListTag strings(Iterable<String> it) {
        ListTag l = new ListTag();
        for (String s : it) {
            l.add(StringTag.valueOf(s));
        }
        return l;
    }

    private static List<String> strings(ListTag l) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    private static ListTag tags(Iterable<CompoundTag> it) {
        ListTag l = new ListTag();
        for (CompoundTag c : it) {
            l.add(c.copy());
        }
        return l;
    }

    private static List<CompoundTag> tags(ListTag l) {
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getCompound(i));
        }
        return out;
    }
}
