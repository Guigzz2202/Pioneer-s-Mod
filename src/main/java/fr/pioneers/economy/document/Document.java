package fr.pioneers.economy.document;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.0.5-beta (lot 26, retour 14 de Guillaume) : un DOCUMENT du Carnet (onglet Documents) : type ({@link DocType}), statut ({@link DocStatus}),
 * auteur (un joueur), destinataire (un joueur ou un clan), objet, corps (texte multiligne), lignes de montants (en Shining), ECHEANCIER (montant
 * + jour de jeu ; total = somme des lignes), signatures, historique. Donnee pure (NBT seulement) : le cycle de vie est dans
 * {@link DocumentEngine}, les regles dans {@link DocumentRules}, le registre dans {@link DocumentData}.
 * <p>
 * Un contrat au TEMPS REEL d'avant le lot 26 (ou cree au temps reel depuis l'onglet) garde son moteur : le document porte alors son numero
 * ({@link #timed}) et son statut est lu sur le contrat ({@link DocumentMigration#statusOf}). Une facture imprimee d'avant garde son instantane
 * d'origine ({@link #legacy}).
 */
public final class Document {

    /** Sorte d'une partie : un joueur (UUID du compte) ou un clan (UUID du clan). */
    public enum PartyKind {
        PLAYER, CLAN;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Une partie du document : sorte, identifiant, nom affiche (pionnier ou clan). */
    public record Party(PartyKind kind, @Nullable UUID id, String name) {

        public static Party player(@Nullable UUID id, String name) {
            return new Party(PartyKind.PLAYER, id, name == null ? "" : name);
        }

        public static Party clan(@Nullable UUID id, String name) {
            return new Party(PartyKind.CLAN, id, name == null ? "" : name);
        }

        public boolean clan() {
            return kind == PartyKind.CLAN;
        }

        public boolean is(@Nullable UUID who) {
            return who != null && who.equals(id);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("k", kind.key());
            if (id != null) {
                t.putUUID("u", id);
            }
            t.putString("n", name);
            return t;
        }

        static Party load(CompoundTag t) {
            return new Party("clan".equals(t.getString("k")) ? PartyKind.CLAN : PartyKind.PLAYER, t.hasUUID("u") ? t.getUUID("u") : null,
                  t.getString("n"));
        }
    }

    /** Ligne de montant : libelle, montant (Shining, >= 0). */
    public record Line(String label, long amount) {
    }

    /** Echeance : montant, jour de jeu ou elle est due, jour ou elle a ete reglee (-1 : pas encore). */
    public static final class Due {

        public long amount, day, paidDay = -1;

        public Due(long amount, long day) {
            this.amount = amount;
            this.day = day;
        }

        public boolean paid() {
            return paidDay >= 0;
        }
    }

    /** Ligne d'historique : jour de jeu, sorte ({@code created}, {@code sent}, {@code paid}...), montant, qui. */
    public record Event(long day, String kind, long amount, String who) {
    }

    public final int id;
    public DocType type = DocType.LETTER;
    public DocStatus status = DocStatus.DRAFT;
    public Party author = Party.player(null, ""), recipient = Party.player(null, "");
    /** Nom saisi pour le destinataire (pseudo du compte ou nom du clan), pour modifier le brouillon. */
    public String recipientAccount = "";
    public String subject = "", body = "";
    public final List<Line> lines = new ArrayList<>();
    public final List<Due> dues = new ArrayList<>();
    /** Jours de jeu : creation, envoi, acceptation, cloture (-1 : pas encore). */
    public long created = -1, sent = -1, accepted = -1, closed = -1;
    /** Signatures : nom du signataire et jour (vide / -1 : pas signe). */
    public String authorSign = "", recipientSign = "";
    public long authorSignDay = -1, recipientSignDay = -1;
    /** Contrat au temps reel lie (moteur {@code ContractEngine}), 0 : aucun. */
    public int timed;
    /** Dernier jour de rappel au payeur (-1 : jamais), premier jour de retard (-1 : a jour). */
    public long lastReminder = -1, overdueSince = -1;
    public final Deque<Event> history = new ArrayDeque<>();
    /** Facture imprimee d'avant le lot 26 : instantane d'origine (NBT {@code invoice} de l'objet), garde tel quel. */
    @Nullable
    public CompoundTag legacy;

    public Document(int id) {
        this.id = id;
    }

    // ------------------------------------------------------------------ calculs
    /** Total des lignes. */
    public long total() {
        long t = 0;
        for (Line l : lines) {
            t += Math.max(0, l.amount());
        }
        return t;
    }

    /** Somme des echeances (le serveur exige qu'elle vaille le total). */
    public long scheduled() {
        long t = 0;
        for (Due d : dues) {
            t += Math.max(0, d.amount);
        }
        return t;
    }

    public long paid() {
        long t = 0;
        for (Due d : dues) {
            if (d.paid()) {
                t += d.amount;
            }
        }
        return t;
    }

    public long owed() {
        return Math.max(0, scheduled() - paid());
    }

    /** Prochaine echeance non reglee (la plus tot), ou {@code null}. */
    @Nullable
    public Due nextDue() {
        Due best = null;
        for (Due d : dues) {
            if (!d.paid() && (best == null || d.day < best.day)) {
                best = d;
            }
        }
        return best;
    }

    /** Echeances non reglees dues au jour {@code day} (dans l'ordre des jours). */
    public List<Due> dueBy(long day) {
        List<Due> out = new ArrayList<>();
        for (Due d : dues) {
            if (!d.paid() && d.day <= day) {
                out.add(d);
            }
        }
        out.sort((a, b) -> Long.compare(a.day, b.day));
        return out;
    }

    /** Les echeances courent : document payable, envoye (et accepte s'il doit l'etre), pas clos. */
    public boolean running() {
        if (!type.payable() || timed > 0 || status.closed() || status == DocStatus.DRAFT) {
            return false;
        }
        return !type.signed || accepted >= 0;
    }

    /** Partie qui paie les echeances, ou {@code null}. */
    @Nullable
    public Party payer() {
        return switch (type.payer) {
            case RECIPIENT -> recipient;
            case AUTHOR -> author;
            case NONE -> null;
        };
    }

    /** Partie payee, ou {@code null}. */
    @Nullable
    public Party payee() {
        return switch (type.payer) {
            case RECIPIENT -> author;
            case AUTHOR -> recipient;
            case NONE -> null;
        };
    }

    public void event(long day, String kind, long amount, String who) {
        history.addLast(new Event(day, kind, amount, who == null ? "" : who));
        while (history.size() > DocumentRules.HISTORY) {
            history.pollFirst();
        }
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("type", type.key());
        t.putString("status", status.key());
        t.put("author", author.save());
        t.put("to", recipient.save());
        t.putString("acc", recipientAccount);
        t.putString("subject", subject);
        t.putString("body", body);
        ListTag ls = new ListTag();
        for (Line l : lines) {
            CompoundTag c = new CompoundTag();
            c.putString("l", l.label());
            c.putLong("a", l.amount());
            ls.add(c);
        }
        t.put("lines", ls);
        ListTag ds = new ListTag();
        for (Due d : dues) {
            CompoundTag c = new CompoundTag();
            c.putLong("a", d.amount);
            c.putLong("d", d.day);
            c.putLong("p", d.paidDay);
            ds.add(c);
        }
        t.put("dues", ds);
        t.putLong("created", created);
        t.putLong("sent", sent);
        t.putLong("accepted", accepted);
        t.putLong("closed", closed);
        t.putString("sa", authorSign);
        t.putString("sr", recipientSign);
        t.putLong("sad", authorSignDay);
        t.putLong("srd", recipientSignDay);
        t.putInt("timed", timed);
        t.putLong("remind", lastReminder);
        t.putLong("late", overdueSince);
        ListTag h = new ListTag();
        for (Event e : history) {
            CompoundTag c = new CompoundTag();
            c.putLong("d", e.day());
            c.putString("k", e.kind());
            c.putLong("a", e.amount());
            c.putString("w", e.who());
            h.add(c);
        }
        t.put("hist", h);
        if (legacy != null) {
            t.put("legacy", legacy.copy());
        }
        return t;
    }

    public static Document load(CompoundTag t) {
        Document d = new Document(t.getInt("id"));
        d.type = DocType.of(t.getString("type"));
        d.status = DocStatus.of(t.getString("status"));
        d.author = Party.load(t.getCompound("author"));
        d.recipient = Party.load(t.getCompound("to"));
        d.recipientAccount = t.getString("acc");
        d.subject = t.getString("subject");
        d.body = t.getString("body");
        for (Tag x : t.getList("lines", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            d.lines.add(new Line(c.getString("l"), c.getLong("a")));
        }
        for (Tag x : t.getList("dues", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            Due due = new Due(c.getLong("a"), c.getLong("d"));
            due.paidDay = c.contains("p") ? c.getLong("p") : -1;
            d.dues.add(due);
        }
        d.created = num(t, "created");
        d.sent = num(t, "sent");
        d.accepted = num(t, "accepted");
        d.closed = num(t, "closed");
        d.authorSign = t.getString("sa");
        d.recipientSign = t.getString("sr");
        d.authorSignDay = num(t, "sad");
        d.recipientSignDay = num(t, "srd");
        d.timed = t.getInt("timed");
        d.lastReminder = num(t, "remind");
        d.overdueSince = num(t, "late");
        for (Tag x : t.getList("hist", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            d.history.addLast(new Event(c.getLong("d"), c.getString("k"), c.getLong("a"), c.getString("w")));
        }
        d.legacy = t.contains("legacy", Tag.TAG_COMPOUND) ? t.getCompound("legacy").copy() : null;
        return d;
    }

    private static long num(CompoundTag t, String k) {
        return t.contains(k) ? t.getLong(k) : -1;
    }
}
