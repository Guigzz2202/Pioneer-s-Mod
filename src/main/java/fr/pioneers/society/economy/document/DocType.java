package fr.pioneers.society.economy.document;

import java.util.Locale;

/**
 * 1.0.5-beta (lot 26, retour 14 de Guillaume : « Renomme la section contrat en "Documents", le but est de produire une multitude de documents
 * (Facture, Contrat, Lettre etc...) les joueurs peuvent choisir le type, les montants en jeu, les echeances des montants etc... ») : TYPES de
 * documents du Carnet (onglet Documents). Liste finale decidee au lot 26 (ordre du selecteur) :
 * <ul>
 *     <li>{@link #INVOICE} facture : montants, echeancier, payee par le destinataire (rappels, « en retard », jamais prelevee d'office :
 *     il n'a rien signe) ;</li>
 *     <li>{@link #CONTRACT} contrat : montants facultatifs, echeancier, signe par les deux parties ; une fois accepte, chaque echeance est
 *     prelevee d'office chez le destinataire (comme les contrats d'avant) ; les contrats au TEMPS REEL d'avant (au total ou a l'heure) restent
 *     des contrats du Carnet, avec leur moteur ({@code economy/contract/ContractEngine}) ;</li>
 *     <li>{@link #LETTER} lettre : objet et corps, deposee dans la boite aux lettres du destinataire ;</li>
 *     <li>{@link #QUOTE} devis : montants proposes, accepte ou refuse par le destinataire, rien n'est paye ;</li>
 *     <li>{@link #RECEIPT} recu : atteste un paiement deja fait (aucun mouvement d'argent), « paye » des l'envoi ;</li>
 *     <li>{@link #IOU} reconnaissance de dette : l'auteur s'engage (signature a l'envoi) a payer le destinataire selon l'echeancier,
 *     preleve d'office ;</li>
 *     <li>{@link #PURCHASE_ORDER} bon de commande : l'auteur commande au destinataire ; accepte par le fournisseur, l'auteur paie selon
 *     l'echeancier (preleve d'office) ;</li>
 *     <li>{@link #BILL_OF_SALE} acte de vente : l'auteur vend au destinataire, signe par les deux, l'acheteur paie selon l'echeancier
 *     (preleve d'office) ;</li>
 *     <li>{@link #PROXY} procuration : l'auteur donne pouvoir au destinataire (texte), acceptee par lui, revocable par l'auteur.</li>
 * </ul>
 * Donnee pure (aucune classe de Minecraft).
 */
public enum DocType {
    //              montants  argent exige  echeancier  signature  payeur          prelevement
    INVOICE(true, true, true, false, Payer.RECIPIENT, false),
    CONTRACT(true, false, true, true, Payer.RECIPIENT, true),
    LETTER(false, false, false, false, Payer.NONE, false),
    QUOTE(true, true, false, true, Payer.NONE, false),
    RECEIPT(true, true, false, false, Payer.NONE, false),
    IOU(true, true, true, false, Payer.AUTHOR, true),
    PURCHASE_ORDER(true, true, true, true, Payer.AUTHOR, true),
    BILL_OF_SALE(true, true, true, true, Payer.RECIPIENT, true),
    PROXY(false, false, false, true, Payer.NONE, false);

    /** Qui paie les echeances : le destinataire, l'auteur (reconnaissance de dette, bon de commande), personne. */
    public enum Payer {
        RECIPIENT, AUTHOR, NONE
    }

    /** Lignes de montants permises ; au moins un Shining exige ; echeancier ; acceptation (signature) du destinataire exigee. */
    public final boolean amounts, money, schedule, signed;
    public final Payer payer;
    /** Echeances prelevees d'office a leur jour (la partie qui paie a signe) ; sinon rappels et « en retard » seulement. */
    public final boolean autoDebit;

    DocType(boolean amounts, boolean money, boolean schedule, boolean signed, Payer payer, boolean autoDebit) {
        this.amounts = amounts;
        this.money = money;
        this.schedule = schedule;
        this.signed = signed;
        this.payer = payer;
        this.autoDebit = autoDebit;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** L'AUTEUR paie (reconnaissance de dette, bon de commande) ; sinon le destinataire (ou personne). */
    public boolean authorPays() {
        return payer == Payer.AUTHOR;
    }

    /** Des echeances se paient (echeancier et payeur). */
    public boolean payable() {
        return schedule && payer != Payer.NONE;
    }

    public static DocType of(String s) {
        for (DocType t : values()) {
            if (t.key().equals(s) || t.name().equals(s)) {
                return t;
            }
        }
        return LETTER;
    }

    /** Type connu (le serveur refuse un type inconnu au lieu de le lire comme une lettre). */
    public static boolean known(String s) {
        for (DocType t : values()) {
            if (t.key().equals(s)) {
                return true;
            }
        }
        return false;
    }
}
