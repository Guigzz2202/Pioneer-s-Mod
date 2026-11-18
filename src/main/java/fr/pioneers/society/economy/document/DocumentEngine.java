package fr.pioneers.society.economy.document;

import javax.annotation.Nullable;

/**
 * 1.0.5-beta (lot 26) : moteur PUR des documents (aucune classe de Minecraft hors NBT : banc {@code tools/documents/DocumentBench.java},
 * GameTests {@code PatchP105Lot26Tests}). Toutes les methodes prennent le jour de jeu {@code today} ; un joueur hors ligne est rattrape a son
 * retour. Chaque refus rend la cle de l'erreur ({@code document.pioneers.error.<cle>}).
 * <p>
 * Cycle : brouillon -> envoye (signe par l'auteur si le type l'exige : contrat, devis, reconnaissance de dette, bon de commande, acte de vente,
 * procuration) -> accepte (signe par le destinataire) ou refuse ; une facture et une reconnaissance de dette courent des l'envoi, un recu est
 * « paye » des l'envoi. ECHEANCES (decision du lot 26, dans la ligne des contrats d'avant qui prelevaient chaque heure) : a son jour, une
 * echeance d'un document SIGNE par la partie qui paie (contrat, reconnaissance de dette, bon de commande, acte de vente) est prelevee d'office
 * si elle le peut (joueur connecte avec le solde, ou tresor du clan) ; sinon, et pour une facture (rien de signe), le payeur est rappele (chat,
 * une fois par jour de jeu ; lettre le premier jour puis tous les 7 jours) et le document passe « en retard » des le lendemain ; il se regle
 * a tout moment par « Payer l'echeance ». Toutes reglees : « paye ». Le paye peut faire grace du reste (« Annuler ») ; l'auteur peut reprendre
 * un document envoye tant que rien n'est accepte ni paye. Aucun Shining n'est cree ni perdu : chaque reglement est un transfert exact.
 */
public final class DocumentEngine {

    /** Cote d'une partie qui agit (le service verifie qui est qui et ses droits ; un clan agit par un membre qui a le droit du tresor). */
    public enum Side {
        AUTHOR, RECIPIENT
    }

    /** Banque et messagerie vues par le moteur (le serveur : {@code DocumentService} ; le banc : une banque factice). */
    public interface Bank {

        /** {@code payer} peut regler {@code amount} maintenant (joueur connecte avec le solde, ou tresor du clan). */
        boolean canPay(Document.Party payer, long amount);

        /** Transfert exact de {@code payer} a {@code payee} ; faux si rien n'a change. */
        boolean transfer(Document.Party payer, Document.Party payee, long amount, Document d);

        /** Message dans le chat de la partie si elle est la ({@code document.pioneers.msg.<key>}). */
        void notify(Document.Party who, Document d, String key, String... args);

        /** Lettre dans la boite aux lettres de la partie, connectee ou non ({@code mail.pioneers.kind.<kind>}). Par defaut : rien (banc). */
        default void letter(Document.Party who, Document d, String kind, long amount) {
        }
    }

    /** Une lettre de rappel tous les {@code LETTER_EVERY} jours de retard (le chat, lui, rappelle chaque jour). */
    public static final long LETTER_EVERY = 7;

    private DocumentEngine() {
    }

    private static String num(Document d) {
        return Integer.toString(d.id);
    }

    private static String s(long v) {
        return Long.toString(v);
    }

    // ------------------------------------------------------------------ actions
    @Nullable
    public static String send(Document d, Side side, String signer, long today, Bank bank) {
        if (side != Side.AUTHOR) {
            return "not_allowed";
        }
        if (d.status != DocStatus.DRAFT) {
            return "state";
        }
        String err = DocumentRules.validate(d.type, d.subject, d.body, d.lines, d.dues, today, true);
        if (err != null) {
            return err;
        }
        d.sent = today;
        if (d.type.signed || d.type.authorPays()) {
            d.authorSign = signer;
            d.authorSignDay = today;
        }
        d.status = d.type == DocType.RECEIPT ? DocStatus.PAID : DocStatus.SENT;
        if (d.status == DocStatus.PAID) {
            d.closed = today;
        }
        d.event(today, "sent", d.total(), signer);
        bank.notify(d.recipient, d, "received", d.author.name(), num(d), d.subject);
        bank.letter(d.recipient, d, d.type == DocType.LETTER ? "document_letter" : "document_received", d.total());
        if (d.running()) {
            settle(d, today, bank);
        }
        return null;
    }

    /** L'auteur reprend un document envoye (retour au brouillon) tant qu'il n'est ni accepte ni paye, meme en partie. */
    @Nullable
    public static String withdraw(Document d, Side side, long today, Bank bank) {
        if (side != Side.AUTHOR) {
            return "not_allowed";
        }
        if (d.status != DocStatus.SENT || d.accepted >= 0 || d.paid() > 0 || d.type == DocType.LETTER || d.type == DocType.RECEIPT || d.timed > 0) {
            return "state";
        }
        d.status = DocStatus.DRAFT;
        d.sent = -1;
        d.authorSign = "";
        d.authorSignDay = -1;
        d.lastReminder = -1;
        d.overdueSince = -1;
        d.event(today, "withdrawn", 0, d.author.name());
        bank.notify(d.recipient, d, "withdrawn", d.author.name(), num(d), d.subject);
        return null;
    }

    @Nullable
    public static String accept(Document d, Side side, String signer, long today, Bank bank) {
        if (side != Side.RECIPIENT) {
            return "not_allowed";
        }
        if (!d.type.signed || d.status != DocStatus.SENT || d.timed > 0) {
            return "state";
        }
        d.status = DocStatus.ACCEPTED;
        d.accepted = today;
        d.recipientSign = signer;
        d.recipientSignDay = today;
        d.event(today, "accepted", 0, signer);
        bank.notify(d.author, d, "accepted", d.recipient.name(), num(d), d.subject);
        bank.letter(d.author, d, "document_accepted", 0);
        if (d.running()) {
            settle(d, today, bank);
        }
        return null;
    }

    @Nullable
    public static String refuse(Document d, Side side, String signer, long today, Bank bank) {
        if (side != Side.RECIPIENT) {
            return "not_allowed";
        }
        if (!d.type.signed || d.status != DocStatus.SENT || d.timed > 0) {
            return "state";
        }
        d.status = DocStatus.REFUSED;
        d.closed = today;
        d.event(today, "refused", 0, signer);
        bank.notify(d.author, d, "refused", d.recipient.name(), num(d), d.subject);
        bank.letter(d.author, d, "document_refused", 0);
        return null;
    }

    /**
     * Annule : un document payable qui court ne peut etre annule que par la partie PAYEE (elle fait grace du reste) ; sinon par l'auteur
     * (devis, procuration revoquee, document envoye sans reponse...).
     */
    @Nullable
    public static String cancel(Document d, Side side, long today, Bank bank) {
        if (d.status == DocStatus.DRAFT || d.status.closed() || d.timed > 0 || d.type == DocType.LETTER) {
            return "state";
        }
        boolean payeeSide = d.payee() != null && (d.type.authorPays() ? side == Side.RECIPIENT : side == Side.AUTHOR);
        if (d.running() ? !payeeSide : side != Side.AUTHOR) {
            return "not_allowed";
        }
        d.status = DocStatus.CANCELLED;
        d.closed = today;
        String who = side == Side.AUTHOR ? d.author.name() : d.recipient.name();
        d.event(today, "cancelled", d.owed(), who);
        Document.Party other = side == Side.AUTHOR ? d.recipient : d.author;
        bank.notify(other, d, "cancelled", who, num(d), d.subject);
        bank.letter(other, d, "document_cancelled", d.owed());
        return null;
    }

    /** La partie qui paie regle la prochaine echeance (avant son jour ou en retard). */
    @Nullable
    public static String pay(Document d, Side side, long today, Bank bank) {
        Document.Party payer = d.payer(), payee = d.payee();
        if (payer == null || payee == null || (d.type.authorPays() ? side != Side.AUTHOR : side != Side.RECIPIENT)) {
            return "not_allowed";
        }
        if (!d.running()) {
            return "state";
        }
        Document.Due due = d.nextDue();
        if (due == null) {
            return "state";
        }
        if (!bank.canPay(payer, due.amount) || !bank.transfer(payer, payee, due.amount, d)) {
            return "funds";
        }
        due.paidDay = today;
        d.event(today, "paid", due.amount, payer.name());
        bank.notify(payee, d, "paid", payer.name(), num(d), d.subject, s(due.amount));
        refresh(d, today, bank);
        return null;
    }

    // ------------------------------------------------------------------ temps qui passe
    /**
     * Rattrape le document au jour {@code today} : echeances dues prelevees d'office si le type le prevoit et si le payeur le peut, statut
     * « en retard » au lendemain d'une echeance non reglee, rappels, « paye » quand tout est regle. Vrai si quelque chose a change.
     */
    public static boolean settle(Document d, long today, Bank bank) {
        if (!d.running()) {
            return false;
        }
        boolean changed = false;
        Document.Party payer = d.payer(), payee = d.payee();
        if (d.type.autoDebit && payer != null && payee != null) {
            for (Document.Due due : d.dueBy(today)) {
                if (!bank.canPay(payer, due.amount) || !bank.transfer(payer, payee, due.amount, d)) {
                    break;
                }
                due.paidDay = today;
                d.event(today, "debited", due.amount, payer.name());
                bank.notify(payee, d, "paid", payer.name(), num(d), d.subject, s(due.amount));
                bank.notify(payer, d, "debited", num(d), d.subject, s(due.amount));
                changed = true;
            }
        }
        return refresh(d, today, bank) || changed;
    }

    /** Statut d'apres les echeances : paye, en retard (et rappels), ou de nouveau a jour. */
    static boolean refresh(Document d, long today, Bank bank) {
        DocStatus before = d.status;
        long remindedBefore = d.lastReminder;
        if (d.nextDue() == null) {
            d.status = DocStatus.PAID;
            d.closed = today;
            d.overdueSince = -1;
            d.event(today, "settled", d.paid(), "");
            if (d.payee() != null) {
                bank.notify(d.payee(), d, "settled", num(d), d.subject, s(d.paid()));
                bank.letter(d.payee(), d, "document_settled", d.paid());
            }
            if (d.payer() != null) {
                bank.notify(d.payer(), d, "settled", num(d), d.subject, s(d.paid()));
            }
            return true;
        }
        long late = 0;
        for (Document.Due due : d.dues) {
            if (!due.paid() && due.day < today) {
                late += due.amount;
            }
        }
        if (late > 0) {
            if (d.status != DocStatus.OVERDUE) {
                d.status = DocStatus.OVERDUE;
                d.overdueSince = today;
                d.event(today, "overdue", late, "");
                if (d.payee() != null) {
                    bank.notify(d.payee(), d, "overdue_payee", d.payer() == null ? "" : d.payer().name(), num(d), d.subject, s(late));
                }
            }
            if (d.lastReminder < today && d.payer() != null) {
                d.lastReminder = today;
                bank.notify(d.payer(), d, "reminder", num(d), d.subject, s(late));
                if ((today - d.overdueSince) % LETTER_EVERY == 0) {
                    bank.letter(d.payer(), d, "document_overdue", late);
                }
            }
        } else {
            d.status = d.type.signed ? DocStatus.ACCEPTED : DocStatus.SENT;
            d.overdueSince = -1;
        }
        return d.status != before || d.lastReminder != remindedBefore;
    }
}
