package fr.pioneers.society.economy.contract;

import java.util.UUID;

/**
 * 1.0.4-beta (lot 7) : moteur PUR des contrats (aucune classe de Minecraft hors NBT : banc {@code tools/rptest/ContractBench.java}, GameTests
 * {@code PatchP104Lot7Tests}). Toutes les methodes prennent l'heure {@code now} (temps reel du serveur, ms) : la facturation ne depend jamais
 * des ticks ; un joueur hors ligne est simplement rattrape a son retour.
 * <p>
 * Facturation : a chaque appel de {@link #settle}, les heures entieres ecoulees depuis le debut effectif (puis, la periode finie, les minutes
 * restantes) sont facturees ({@code billed} = prorata a la minute du temps facturable) ; le du est preleve sur le compte du client s'il est
 * connecte, au plus son solde (le reste est une dette affichee, relancee chaque heure : etat « impaye ») ; la somme prelevee va au
 * prestataire, mise de cote ({@code held}) tant qu'il est hors ligne. Aucun Shining n'est cree ni perdu : preleve = verse + mis de cote.
 * <p>
 * 1.0.4-beta (lot 29) : formule au MONTANT TOTAL = ce qui precede (du = part du total, {@link Contract#dueAt}) ; formule A L'HEURE
 * ({@link Contract#hourly}) : reserve versee par le client a l'acceptation et rechargee quand il est connecte, chaque heure reelle payee
 * depuis la reserve connecte ou non ({@link #settleHourly}, rattrapage idempotent : le nombre d'heures payees est sauve), suspension si la
 * reserve ne couvre pas l'heure, reprise par le client ({@link #resume}), heure entamee a la minute et reserve rendue a la fin. Chaque
 * evenement marquant laisse une LETTRE dans la boite aux lettres de la partie concernee ({@link Bank#letter}). Preleve = verse + mis de cote
 * + reserve + rendu.
 */
public final class ContractEngine {

    /** Banque et messagerie vues par le moteur (le serveur : {@code ContractService} ; le banc : une banque factice). */
    public interface Bank {

        boolean online(UUID who);

        /** Solde (appele seulement pour un joueur connecte). */
        long balance(UUID who);

        /** Prelevement exact chez le client connecte ; faux s'il n'a pas pu se faire (rien n'a change). */
        boolean debit(UUID who, long amount, Contract c);

        /** Versement exact au prestataire connecte ; faux s'il n'a pas pu se faire (plafond du compte : la somme reste de cote). */
        boolean credit(UUID who, long amount, Contract c);

        /** Message au joueur s'il est connecte ({@code contract.pioneers.msg.<key>}, arguments en texte). */
        void notify(UUID who, Contract c, String key, String... args);

        /**
         * 1.0.4-beta (lot 29) : lettre dans la boite aux lettres de {@code who}, connecte ou non ({@code mail.pioneers.kind.<kind>} ; montants
         * {@code a}, {@code b} selon la sorte). Par defaut : rien (banc).
         */
        default void letter(UUID who, Contract c, String kind, long a, long b) {
        }
    }

    /** Autre partie du contrat (nom affiche dans la lettre de {@code who}). */
    private static String other(Contract c, UUID who) {
        return who != null && who.equals(c.provider) ? c.clientName : c.providerName;
    }

    /** Termes affiches : montant total, ou taux horaire. */
    private static long terms(Contract c) {
        return c.hourly() ? c.rate : c.plannedTotal();
    }

    private ContractEngine() {
    }

    // ------------------------------------------------------------------ actions des joueurs (chaque refus rend la cle de l'erreur)
    public static String propose(Contract c, UUID actor, long now, Bank bank) {
        if (!actor.equals(c.provider)) {
            return "not_allowed";
        }
        if (c.state != Contract.State.DRAFT) {
            return "state";
        }
        String err = c.hourly() || c.total <= 0 ? ContractRules.validate(c.title, c.start, c.end, c.rate, now)
              : ContractRules.validate(c.title, c.start, c.end, false, c.total, now);
        if (err != null) {
            return err;
        }
        c.state = Contract.State.PROPOSED;
        c.proposed = now;
        c.line(now, "proposed", ContractRules.minutes(c.start, c.end), c.plannedTotal());
        if (c.hourly()) {
            bank.notify(c.client, c, "proposed_hourly", c.providerName, num(c), c.title, s(c.rate), s(c.plannedTotal()));
        } else if (c.total > 0) {
            bank.notify(c.client, c, "proposed_total", c.providerName, num(c), c.title, s(c.plannedTotal()));
        } else {
            bank.notify(c.client, c, "proposed", c.providerName, num(c), c.title, s(c.rate), s(c.plannedTotal()));   //taux du lot 7
        }
        bank.letter(c.client, c, "contract_proposed", terms(c), 0);
        return null;
    }

    /** Le prestataire reprend sa proposition (retour au brouillon, modifiable). */
    public static String withdraw(Contract c, UUID actor, long now, Bank bank) {
        if (!actor.equals(c.provider)) {
            return "not_allowed";
        }
        if (c.state != Contract.State.PROPOSED) {
            return "state";
        }
        c.state = Contract.State.DRAFT;
        c.line(now, "withdrawn", 0, 0);
        bank.notify(c.client, c, "withdrawn", c.providerName, num(c), c.title);
        bank.letter(c.client, c, "contract_withdrawn", 0, 0);
        return null;
    }

    public static String accept(Contract c, UUID actor, long now, Bank bank) {
        if (!actor.equals(c.client)) {
            return "not_allowed";
        }
        if (c.state != Contract.State.PROPOSED) {
            return "state";
        }
        if (now >= c.end) {
            expire(c, now, bank);
            return "past";
        }
        if (c.hourly()) {
            //la reserve doit couvrir au moins la premiere heure (ou tout le contrat s'il est plus court)
            long first = Math.min(c.rate, ContractRules.reserveTarget(c.rate, ContractRules.minutes(Math.max(now, c.start), c.end)));
            if (!bank.online(c.client) || bank.balance(c.client) < first) {
                return "reserve";
            }
        }
        c.state = Contract.State.ACCEPTED;
        c.accepted = now;
        c.line(now, "accepted", c.plannedMinutes(), c.plannedTotal());
        bank.notify(c.provider, c, "accepted", c.clientName, num(c), c.title);
        bank.letter(c.provider, c, "contract_accepted", 0, 0);
        if (c.hourly()) {
            topUp(c, now, bank);
        }
        settle(c, now, bank);
        return null;
    }

    public static String refuse(Contract c, UUID actor, long now, Bank bank) {
        if (!actor.equals(c.client)) {
            return "not_allowed";
        }
        if (c.state != Contract.State.PROPOSED) {
            return "state";
        }
        c.state = Contract.State.REFUSED;
        c.stopped = now;
        c.line(now, "refused", 0, 0);
        bank.notify(c.provider, c, "refused", c.clientName, num(c), c.title);
        bank.letter(c.provider, c, "contract_refused", 0, 0);
        return null;
    }

    /** Resiliation par l'une ou l'autre partie : la periode s'arrete maintenant, facturee au prorata a la minute. */
    public static String terminate(Contract c, UUID actor, long now, Bank bank) {
        if (!c.party(actor)) {
            return "not_allowed";
        }
        if (c.state != Contract.State.ACCEPTED && c.state != Contract.State.RUNNING && c.state != Contract.State.SUSPENDED) {
            return "state";
        }
        String who = actor.equals(c.provider) ? c.providerName : c.clientName;
        if (c.hourly()) {
            settleHourly(c, now, bank);                                 //heures entieres dues jusqu'ici d'abord (ou deja au terme)
            if (c.state != Contract.State.ACCEPTED && c.state != Contract.State.RUNNING && c.state != Contract.State.SUSPENDED) {
                return "state";
            }
            terminateHourly(c, actor, now, bank);
        } else {
            c.stopped = Math.min(c.end, Math.max(now, c.effStart()));
            c.state = Contract.State.TERMINATED;
            c.terminatedBy = actor;
            c.line(now, "terminated", ContractRules.minutes(c.effStart(), c.stopped), 0);
            settle(c, now, bank);
        }
        for (UUID u : new UUID[]{c.provider, c.client}) {
            bank.notify(u, c, "terminated", who, num(c), c.title, s(c.billed), s(c.paid));
            bank.letter(u, c, "contract_terminated", c.billed, c.paid);
        }
        return null;
    }

    /** Le client demande un prelevement tout de suite (bouton « Regler »). */
    public static String payNow(Contract c, UUID actor, long now, Bank bank) {
        if (!actor.equals(c.client)) {
            return "not_allowed";
        }
        if (!c.billing() || c.owed() <= 0) {
            return "nothing_due";
        }
        if (c.state == Contract.State.SUSPENDED) {
            return resume(c, actor, now, bank);
        }
        long before = c.paid;
        settle(c, now, bank);
        if (c.hourly() && c.owed() > 0) {
            collectHourly(c, now, bank);
        }
        if (c.paid == before) {
            return "insufficient";
        }
        return null;
    }

    /**
     * 1.0.4-beta (lot 29) : le client reprend un contrat a l'heure suspendu : il regle la dette et recharge la reserve d'au moins une heure
     * (ou du reste du contrat) ; les heures recommencent a compter maintenant (le temps suspendu n'est pas du).
     */
    public static String resume(Contract c, UUID actor, long now, Bank bank) {
        if (!actor.equals(c.client)) {
            return "not_allowed";
        }
        if (c.state != Contract.State.SUSPENDED) {
            return "state";
        }
        if (now >= c.end) {
            settle(c, now, bank);
            return "past";
        }
        long first = Math.min(c.rate, ContractRules.reserveTarget(c.rate, ContractRules.minutes(now, c.end)));
        if (!bank.online(c.client) || bank.balance(c.client) < c.owed() + first) {
            return "insufficient";
        }
        long debt = c.owed();
        if (debt > 0) {
            if (!bank.debit(c.client, debt, c)) {
                return "insufficient";
            }
            c.paid += debt;
            c.held += debt;
            c.line(now, "paid", 0, debt);
        }
        c.shortFunds = false;
        c.paused += Math.max(0, now - c.pauseAt);
        c.pauseAt = 0;
        c.state = Contract.State.RUNNING;
        topUp(c, now, bank);
        c.line(now, "resumed", 0, 0);
        for (UUID u : new UUID[]{c.provider, c.client}) {
            bank.notify(u, c, "resumed", num(c), c.title);
            bank.letter(u, c, "contract_resumed", 0, 0);
        }
        payout(c, bank);
        return null;
    }

    // ------------------------------------------------------------------ temps qui passe
    /**
     * Fait avancer le contrat jusqu'a {@code now} : expiration d'une proposition, debut, heures facturees, fin, prelevement, versement,
     * relances. Rend vrai si quelque chose a change (a sauver et a montrer).
     */
    public static boolean settle(Contract c, long now, Bank bank) {
        boolean changed = false;
        if (c.hourly()) {
            return settleHourly(c, now, bank);
        }
        if (c.state == Contract.State.PROPOSED) {
            if (now >= c.end) {
                expire(c, now, bank);
                return true;
            }
            return false;
        }
        if (c.state == Contract.State.ACCEPTED && now >= c.effStart()) {
            c.state = Contract.State.RUNNING;
            c.line(c.effStart(), "started", 0, 0);
            for (UUID u : new UUID[]{c.provider, c.client}) {
                bank.notify(u, c, "started", num(c), c.title, s(c.rate));
            }
            changed = true;
        }
        if (!c.billing()) {
            return changed;
        }
        boolean fresh = false;
        long target = c.checkpoint(now);
        if (target > c.billedMinutes) {
            long newBilled = Math.max(c.billed, c.dueAt(target));
            c.line(Math.min(now, c.stopAt()), "billed", target - c.billedMinutes, newBilled - c.billed);
            c.billedMinutes = target;
            c.billed = newBilled;
            fresh = true;
            changed = true;
        }
        if (c.state == Contract.State.RUNNING && now >= c.end) {
            c.state = Contract.State.FINISHED;
            c.stopped = c.end;
            changed = true;
            collect(c, now, bank, fresh);
            for (UUID u : new UUID[]{c.provider, c.client}) {
                bank.notify(u, c, "finished", num(c), c.title, s(c.billed), s(c.paid));
                bank.letter(u, c, "contract_finished", c.billed, c.paid);
            }
            c.line(c.end, "finished", c.billedMinutes, c.billed);
        } else {
            changed |= collect(c, now, bank, fresh);
        }
        changed |= payout(c, bank);
        return changed;
    }

    /** Prelevement du du chez le client connecte (au plus son solde) ; dette et relances sinon. */
    static boolean collect(Contract c, long now, Bank bank, boolean fresh) {
        long owe = c.owed();
        if (owe <= 0) {
            boolean was = c.shortFunds;
            c.shortFunds = false;
            return was;
        }
        if (!bank.online(c.client)) {
            return false; //calcul au retour du client
        }
        boolean changed = false;
        long take = Math.min(owe, Math.max(0, bank.balance(c.client)));
        if (take > 0 && bank.debit(c.client, take, c)) {
            c.paid += take;
            c.held += take;
            owe -= take;
            c.line(now, "paid", 0, take);
            bank.notify(c.client, c, "paid", num(c), s(take), c.providerName, s(owe));
            changed = true;
        }
        if (owe > 0) {
            boolean remind = fresh || !c.shortFunds || now - c.lastReminder >= ContractRules.HOUR;
            if (!c.shortFunds) {
                bank.letter(c.client, c, "contract_unpaid", owe, 0);
            }
            c.shortFunds = true;
            if (remind) {
                c.lastReminder = now;
                c.line(now, "short", 0, owe);
                bank.notify(c.client, c, "short", num(c), s(owe), c.providerName);
                bank.notify(c.provider, c, "short_provider", num(c), c.clientName, s(owe));
                changed = true;
            }
        } else if (c.shortFunds) {
            c.shortFunds = false;
            bank.letter(c.provider, c, "contract_paid", c.paid, 0);
            changed = true;
        }
        return changed;
    }

    /** Versement au prestataire connecte de ce qui a ete preleve pour lui. */
    static boolean payout(Contract c, Bank bank) {
        boolean changed = false;
        //1.0.4-beta (lot 29) : reserve rendue au client (contrat a l'heure fini ou resilie), a sa connexion s'il etait absent
        if (c.refund > 0 && bank.online(c.client) && bank.credit(c.client, c.refund, c)) {
            bank.notify(c.client, c, "refunded", num(c), s(c.refund));
            c.refund = 0;
            changed = true;
        }
        if (c.held <= 0 || !bank.online(c.provider)) {
            return changed;
        }
        long amount = c.held;
        if (!bank.credit(c.provider, amount, c)) {
            return changed;
        }
        c.held = 0;
        bank.notify(c.provider, c, "received", num(c), s(amount), c.clientName);
        return true;
    }

    private static void expire(Contract c, long now, Bank bank) {
        c.state = Contract.State.EXPIRED;
        c.stopped = now;
        c.line(now, "expired", 0, 0);
        for (UUID u : new UUID[]{c.provider, c.client}) {
            bank.notify(u, c, "expired", num(c), c.title);
            bank.letter(u, c, "contract_expired", 0, 0);
        }
    }

    // ------------------------------------------------------------------ 1.0.4-beta (lot 29) : contrats a l'heure
    /**
     * Fait avancer un contrat a l'heure jusqu'a {@code now} : debut, chaque heure entiere ecoulee payee depuis la reserve (rechargee entre
     * deux heures si le client est connecte), suspension si la reserve ne couvre pas l'heure, fin (heure entamee a la minute, reserve
     * rendue), dette reglee quand le client est connecte, versements. IDEMPOTENT : les heures payees sont comptees ({@code hours}) et l'heure
     * k n'est due qu'a {@code clockStart + k h} ; un rattrapage apres un arret du serveur paie chaque heure manquante une seule fois.
     */
    static boolean settleHourly(Contract c, long now, Bank bank) {
        boolean changed = false;
        if (c.state == Contract.State.PROPOSED) {
            if (now >= c.end) {
                expire(c, now, bank);
                return true;
            }
            return false;
        }
        if (c.state == Contract.State.ACCEPTED) {
            changed |= topUp(c, now, bank);
            if (now >= c.effStart()) {
                c.state = Contract.State.RUNNING;
                c.line(c.effStart(), "started", 0, 0);
                for (UUID u : new UUID[]{c.provider, c.client}) {
                    bank.notify(u, c, "started", num(c), c.title, s(c.rate));
                }
                changed = true;
            }
        }
        if (c.state == Contract.State.RUNNING) {
            changed |= topUp(c, now, bank);
            long stop = Math.min(now, c.end);
            while (c.state == Contract.State.RUNNING) {
                long tk = c.nextHourAt();
                if (tk > stop) {
                    break;
                }
                changed = true;
                if (!payHour(c, tk, 60, c.rate)) {
                    suspend(c, tk, bank);
                    break;
                }
                topUp(c, now, bank);
            }
            if (c.state == Contract.State.RUNNING && now >= c.end) {
                topUp(c, now, bank);
                long minutes = ContractRules.minutes(c.clockStart() + c.hours * ContractRules.HOUR, c.end);
                payHour(c, c.end, minutes, ContractRules.amount(minutes, c.rate));
                finishHourly(c, c.end, bank);
                changed = true;
            }
        } else if (c.state == Contract.State.SUSPENDED && now >= c.end) {
            finishHourly(c, c.end, bank);
            changed = true;
        }
        if (c.owed() > 0 && c.state != Contract.State.SUSPENDED && c.state != Contract.State.RUNNING) {
            changed |= collectHourly(c, now, bank);
        }
        changed |= payout(c, bank);
        return changed;
    }

    /** Paie une heure (ou la fin d'heure) {@code due} depuis la reserve ; faux si la reserve ne l'a pas couverte (le reste est du). */
    private static boolean payHour(Contract c, long at, long minutes, long due) {
        if (minutes == 60) {
            c.hours++;
        }
        c.billedMinutes += minutes;
        c.billed += due;
        long take = Math.min(due, c.reserve);
        c.reserve -= take;
        c.paid += take;
        c.held += take;
        if (due > 0) {
            c.line(at, "hour", minutes, take);
        }
        return take >= due;
    }

    /** Recharge la reserve du client connecte jusqu'a sa cible ({@link ContractRules#reserveTarget}). Vrai si elle a change. */
    static boolean topUp(Contract c, long now, Bank bank) {
        if ((c.state != Contract.State.RUNNING && c.state != Contract.State.ACCEPTED) || !bank.online(c.client)) {
            return false;
        }
        long from = Math.max(now, c.effStart());
        long target = ContractRules.reserveTarget(c.rate, ContractRules.minutes(from, c.end));
        long need = target - c.reserve;
        if (need <= 0) {
            return false;
        }
        long take = Math.min(need, Math.max(0, bank.balance(c.client)));
        if (take <= 0 || !bank.debit(c.client, take, c)) {
            return false;
        }
        c.reserve += take;
        return true;
    }

    /** Reserve trop courte pour l'heure {@code at} : suspension (dette = reste de l'heure), message et lettre aux deux parties. */
    private static void suspend(Contract c, long at, Bank bank) {
        c.state = Contract.State.SUSPENDED;
        c.pauseAt = at;
        c.shortFunds = true;
        c.lastReminder = at;
        c.line(at, "suspended", 0, c.owed());
        for (UUID u : new UUID[]{c.provider, c.client}) {
            bank.notify(u, c, "suspended", num(c), c.title, s(c.owed()));
            bank.letter(u, c, "contract_suspended", c.owed(), 0);
        }
    }

    /** Fin d'un contrat a l'heure (terme ou suspendu au terme) : reserve rendue, message et lettre aux deux parties. */
    private static void finishHourly(Contract c, long at, Bank bank) {
        c.state = Contract.State.FINISHED;
        c.stopped = at;
        giveBack(c, at);
        c.line(at, "finished", c.billedMinutes, c.billed);
        for (UUID u : new UUID[]{c.provider, c.client}) {
            bank.notify(u, c, "finished", num(c), c.title, s(c.billed), s(c.paid));
            bank.letter(u, c, "contract_finished", c.billed, c.paid);
        }
    }

    /** Resiliation d'un contrat a l'heure : heure entamee a la minute (en cours seulement), reserve rendue. */
    private static void terminateHourly(Contract c, UUID actor, long now, Bank bank) {
        long stop = Math.min(c.end, Math.max(now, c.effStart()));
        if (c.state == Contract.State.RUNNING) {
            topUp(c, now, bank);
            long minutes = ContractRules.minutes(c.clockStart() + c.hours * ContractRules.HOUR, stop);
            payHour(c, stop, minutes, ContractRules.amount(minutes, c.rate));
        }
        if (c.state == Contract.State.SUSPENDED) {
            stop = Math.min(stop, Math.max(c.pauseAt, c.effStart()));
        }
        c.stopped = stop;
        c.state = Contract.State.TERMINATED;
        c.terminatedBy = actor;
        c.line(now, "terminated", c.billedMinutes, 0);
        giveBack(c, now);
        collectHourly(c, now, bank);
        payout(c, bank);
    }

    /** Reste de la reserve rendu au client (verse a sa connexion par {@link #payout}). */
    private static void giveBack(Contract c, long at) {
        if (c.reserve > 0) {
            c.refund += c.reserve;
            c.line(at, "refund", 0, c.reserve);
            c.reserve = 0;
        }
    }

    /** Dette d'un contrat a l'heure (heure non couverte, fin) : prelevee chez le client connecte, au plus son solde ; rappel chaque heure. */
    static boolean collectHourly(Contract c, long now, Bank bank) {
        boolean changed = collect(c, now, bank, false);
        payout(c, bank);
        return changed;
    }

    private static String num(Contract c) {
        return Integer.toString(c.id);
    }

    private static String s(long v) {
        return Long.toString(v);
    }
}
