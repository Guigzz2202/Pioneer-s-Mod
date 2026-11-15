package fr.pioneers.economy.document;

import java.util.Locale;

/**
 * 1.0.5-beta (lot 26) : statut d'un document : brouillon, envoye (en attente de reponse, ou en cours pour une facture / une reconnaissance
 * de dette), accepte (signe par le destinataire, echeances en cours), paye (toutes les echeances reglees ; un recu des l'envoi), en retard
 * (une echeance passee n'est pas reglee), annule (par l'auteur ; contrat au temps reel resilie ou expire), refuse (par le destinataire).
 */
public enum DocStatus {
    DRAFT, SENT, ACCEPTED, PAID, OVERDUE, CANCELLED, REFUSED;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DocStatus of(String s) {
        for (DocStatus t : values()) {
            if (t.name().equals(s) || t.key().equals(s)) {
                return t;
            }
        }
        return DRAFT;
    }

    /** Clos : plus rien ne se passe (paye, annule, refuse). */
    public boolean closed() {
        return this == PAID || this == CANCELLED || this == REFUSED;
    }
}
