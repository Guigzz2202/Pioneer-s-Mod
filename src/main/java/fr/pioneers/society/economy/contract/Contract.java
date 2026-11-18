package fr.pioneers.society.economy.contract;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.0.4-beta (lot 7) : un contrat entre deux joueurs (prestataire = createur, client), au taux horaire, sur une periode en temps REEL du
 * serveur (millisecondes depuis 1970). Donnee pure (NBT seulement) : le cycle de vie et la facturation sont dans {@link ContractEngine}.
 * <p>
 * Cycle : brouillon -> propose -> accepte (ou refuse, ou expire si la fin passe avant la reponse) -> en cours (au debut effectif) -> termine
 * (a la fin) ou resilie (par l'une ou l'autre partie, au prorata). Rien n'est preleve avant l'acceptation ; la periode facturee commence au
 * plus tard du debut prevu et de l'acceptation. {@link #billed} = du facture a ce jour, {@link #paid} = preleve chez le client,
 * {@link #held} = preleve mais pas encore verse au prestataire hors ligne.
 * <p>
 * 1.0.4-beta (lot 29, Guillaume : « Adapter les contrats pour avoir le choix entre montant total ou montant horaire », precision : heure
 * REELLE, paiement chaque heure tant que le contrat court, joueurs connectes ou non, heures dues pendant l'arret du serveur rattrapees sans
 * doubler) : deux FORMULES ({@link Pay}).
 * <ul>
 *     <li>{@link Pay#TOTAL} : un montant pour toute la periode ({@link #total}), etale au prorata et preleve a chaque heure entiere comme au
 *     lot 7 (client connecte ; dette sinon). Un contrat d'avant le lot 29 (sans formule, {@link #total} = 0) est lu comme un montant total
 *     calcule au taux d'origine : ses chiffres ne changent pas ({@link #dueAt}) ;</li>
 *     <li>{@link Pay#HOURLY} : un taux par heure reelle ({@link #rate}). A l'acceptation, le client verse une RESERVE (jusqu'a
 *     {@link ContractRules#RESERVE_HOURS} heures), rechargee de son compte chaque fois qu'il est connecte ; chaque heure entiere ecoulee
 *     ({@link #hours}) est payee depuis la reserve, connecte ou non, au prestataire (de cote s'il est absent, verse a sa connexion). Reserve
 *     trop courte : l'heure est payee de ce qu'il reste, le reste est une dette et le contrat est SUSPENDU ({@link State#SUSPENDED} : les
 *     heures ne comptent plus, {@link #pauseAt}) jusqu'a ce que le client le reprenne en reglant (le temps suspendu s'ajoute a
 *     {@link #paused}) ; a la fin ou a la resiliation, l'heure entamee est payee a la minute et la reserve restante rendue au client
 *     ({@link #refund}, verse a sa connexion).</li>
 * </ul>
 */
public final class Contract {

    public enum State {
        DRAFT, PROPOSED, REFUSED, EXPIRED, ACCEPTED, RUNNING, FINISHED, TERMINATED, SUSPENDED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static State of(String s) {
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return DRAFT;
            }
        }
    }

    /** 1.0.4-beta (lot 29) : formule du contrat. */
    public enum Pay {
        TOTAL, HOURLY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Pay of(String s) {
            return "hourly".equals(s) ? HOURLY : TOTAL;
        }
    }

    /** Ligne d'historique : instant, sorte ({@code billed}, {@code paid}, {@code short}, {@code accepted}...), minutes, montant. */
    public record Line(long at, String kind, long minutes, long amount) {
    }

    public final int id;
    public UUID provider, client;
    public String providerName = "", clientName = "", title = "", note = "";
    /**
     * 1.0.5-beta (lot 16, retours de Guillaume : « pouvoir rediger une vraie lettre ») : corps de la lettre du contrat, texte libre multiligne
     * ({@link ContractRules#BODY_MAX} caracteres, {@link ContractRules#BODY_LINES} lignes au plus, retours a la ligne gardes). Un contrat
     * d'avant n'a pas de corps : sa description ({@code note}) en tient lieu a la lecture ({@link #load}).
     */
    public String body = "";
    /** Nom de compte du client tel que le prestataire l'a saisi (pour modifier le brouillon). */
    public String clientAccount = "";
    public long start, end, rate;
    public State state = State.DRAFT;
    public long created, proposed, accepted, stopped;
    @Nullable
    public UUID terminatedBy;
    public long billedMinutes, billed, paid, held;
    /** Vrai apres une tentative de prelevement qui n'a pas tout couvert (contrat « impaye ») ; faux des que le du est solde. */
    public boolean shortFunds;
    public long lastReminder;
    /**
     * 1.0.4-beta (lot 29) : formule ; montant total ({@link Pay#TOTAL}, 0 = contrat d'avant le lot 29 au taux) ; heures entieres payees,
     * reserve du client, instant de la suspension (0 : aucune), temps suspendu cumule (ms), reste de la reserve a rendre au client hors ligne
     * ({@link Pay#HOURLY}).
     */
    public Pay pay = Pay.TOTAL;
    public long total, hours, reserve, pauseAt, paused, refund;
    public final Deque<Line> history = new ArrayDeque<>();

    public Contract(int id) {
        this.id = id;
    }

    // ------------------------------------------------------------------ calculs
    /** Debut de la periode facturee : le plus tard du debut prevu et de l'acceptation. */
    public long effStart() {
        return accepted > 0 ? Math.max(start, accepted) : start;
    }

    /** Fin de la periode facturee : la fin prevue, ou l'instant de la resiliation. */
    public long stopAt() {
        return state == State.TERMINATED ? stopped : end;
    }

    /** Minutes prevues (de {@link #effStart} a la fin prevue, ou de debut a fin avant l'acceptation). */
    public long plannedMinutes() {
        return ContractRules.minutes(effStart(), end);
    }

    public long plannedTotal() {
        return pay == Pay.TOTAL && total > 0 ? total : ContractRules.amount(plannedMinutes(), rate);
    }

    public boolean hourly() {
        return pay == Pay.HOURLY;
    }

    /** Formule au montant total (aussi les contrats d'avant le lot 29) : du cumule pour {@code minutes} facturables. */
    public long dueAt(long minutes) {
        return total > 0 ? ContractRules.share(total, minutes, plannedMinutes()) : ContractRules.amount(minutes, rate);
    }

    /** Contrat a l'heure : origine des heures (debut effectif + temps suspendu). */
    public long clockStart() {
        return effStart() + paused;
    }

    /** Contrat a l'heure : fin de la prochaine heure a payer. */
    public long nextHourAt() {
        return clockStart() + (hours + 1) * ContractRules.HOUR;
    }

    /**
     * Minutes facturables a l'instant {@code now} : les heures ENTIERES ecoulees depuis le debut effectif, ou, la periode finie (fin ou
     * resiliation), toutes les minutes de la periode.
     */
    public long checkpoint(long now) {
        long eff = effStart(), stop = stopAt();
        if (now >= stop) {
            return ContractRules.minutes(eff, stop);
        }
        return ContractRules.minutes(eff, now) / 60 * 60;
    }

    /** Reste du (facture moins preleve). */
    public long owed() {
        return Math.max(0, billed - paid);
    }

    /** Facture, en cours ou fini : de l'argent a pu circuler. */
    public boolean billing() {
        return state == State.RUNNING || state == State.FINISHED || state == State.TERMINATED || state == State.SUSPENDED;
    }

    /** Encore vivant : sans reponse, en attente, en cours, ou clos avec un reste du ou une somme a verser. */
    public boolean open() {
        return switch (state) {
            case DRAFT, PROPOSED, ACCEPTED, RUNNING, SUSPENDED -> true;
            case FINISHED, TERMINATED -> owed() > 0 || held > 0 || refund > 0;
            default -> false;
        };
    }

    /** Etat affiche : celui du cycle, ou {@code unpaid} quand un prelevement n'a pas pu tout couvrir. */
    public String shownState() {
        if (state == State.SUSPENDED) {
            return "suspended";
        }
        return billing() && shortFunds && owed() > 0 ? "unpaid" : state.key();
    }

    public boolean party(UUID who) {
        return who.equals(provider) || who.equals(client);
    }

    public void line(long at, String kind, long minutes, long amount) {
        history.addLast(new Line(at, kind, minutes, amount));
        while (history.size() > ContractRules.HISTORY) {
            history.pollFirst();
        }
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        if (provider != null) {
            t.putUUID("pv", provider);
        }
        if (client != null) {
            t.putUUID("cl", client);
        }
        t.putString("pvn", providerName);
        t.putString("cln", clientName);
        t.putString("cla", clientAccount);
        t.putString("title", title);
        t.putString("note", note);
        t.putString("body", body);
        t.putLong("start", start);
        t.putLong("end", end);
        t.putLong("rate", rate);
        t.putString("state", state.name());
        t.putLong("created", created);
        t.putLong("proposed", proposed);
        t.putLong("accepted", accepted);
        t.putLong("stopped", stopped);
        if (terminatedBy != null) {
            t.putUUID("by", terminatedBy);
        }
        t.putLong("bm", billedMinutes);
        t.putLong("billed", billed);
        t.putLong("paid", paid);
        t.putLong("held", held);
        t.putBoolean("short", shortFunds);
        t.putLong("remind", lastReminder);
        t.putString("pay", pay.key());
        t.putLong("total", total);
        t.putLong("hours", hours);
        t.putLong("reserve", reserve);
        t.putLong("pauseAt", pauseAt);
        t.putLong("paused", paused);
        t.putLong("refund", refund);
        ListTag h = new ListTag();
        for (Line l : history) {
            CompoundTag c = new CompoundTag();
            c.putLong("t", l.at());
            c.putString("k", l.kind());
            c.putLong("m", l.minutes());
            c.putLong("a", l.amount());
            h.add(c);
        }
        t.put("hist", h);
        return t;
    }

    public static Contract load(CompoundTag t) {
        Contract c = new Contract(t.getInt("id"));
        c.provider = t.hasUUID("pv") ? t.getUUID("pv") : null;
        c.client = t.hasUUID("cl") ? t.getUUID("cl") : null;
        c.providerName = t.getString("pvn");
        c.clientName = t.getString("cln");
        c.clientAccount = t.getString("cla");
        c.title = t.getString("title");
        c.note = t.getString("note");
        //1.0.5-beta (lot 16) : lettre ; migration : un contrat sans corps reprend sa description (bornee comme un corps)
        c.body = t.contains("body") ? ContractRules.cleanBody(t.getString("body")) : "";
        if (c.body.isEmpty() && !c.note.isEmpty()) {
            c.body = ContractRules.cleanBody(c.note);
        }
        c.start = t.getLong("start");
        c.end = t.getLong("end");
        c.rate = t.getLong("rate");
        c.state = State.of(t.getString("state"));
        c.created = t.getLong("created");
        c.proposed = t.getLong("proposed");
        c.accepted = t.getLong("accepted");
        c.stopped = t.getLong("stopped");
        c.terminatedBy = t.hasUUID("by") ? t.getUUID("by") : null;
        c.billedMinutes = t.getLong("bm");
        c.billed = t.getLong("billed");
        c.paid = t.getLong("paid");
        c.held = t.getLong("held");
        c.shortFunds = t.getBoolean("short");
        c.lastReminder = t.getLong("remind");
        //1.0.4-beta (lot 29) : sans « pay » (contrat d'avant le lot 29) : montant total au taux d'origine (total = 0, voir dueAt)
        c.pay = Pay.of(t.getString("pay"));
        c.total = t.getLong("total");
        c.hours = t.getLong("hours");
        c.reserve = t.getLong("reserve");
        c.pauseAt = t.getLong("pauseAt");
        c.paused = t.getLong("paused");
        c.refund = t.getLong("refund");
        for (Tag x : t.getList("hist", Tag.TAG_COMPOUND)) {
            CompoundTag l = (CompoundTag) x;
            c.history.addLast(new Line(l.getLong("t"), l.getString("k"), l.getLong("m"), l.getLong("a")));
        }
        return c;
    }
}
