package fr.pioneers.economy.document;

import fr.pioneers.economy.contract.ContractRules;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * 1.0.5-beta (lot 26) : regles PURES des documents (bornes et validation du serveur ; banc {@code tools/documents/DocumentBench.java}).
 * Montants en Shining ENTIERS ; jours = jours de jeu ({@code PioneerApi.day}). Le serveur valide TOUT ce que le client envoie : type connu,
 * objet et corps bornes, lignes (montants >= 0, libelles bornes, total <= plafond d'un compte), echeancier (montants >= 1, jours a venir et
 * bornes, SOMME = TOTAL), argent exige selon le type ; il ne coupe jamais en silence (il refuse avec la cle {@code document.pioneers.error.*}).
 */
public final class DocumentRules {

    /** Objet, libelle d'une ligne, nom saisi du destinataire (caracteres). */
    public static final int SUBJECT_MAX = 60, LABEL_MAX = 40, NAME_MAX = 32;
    /** Corps : memes bornes que la lettre de contrat du lot 16. */
    public static final int BODY_MAX = ContractRules.BODY_MAX, BODY_LINES = ContractRules.BODY_LINES;
    /** Lignes de montants et echeances au plus (une page du formulaire du Carnet chacune). */
    public static final int LINES_MAX = 8, DUES_MAX = 8;
    /** Montant d'une ligne, total et echeance au plus (plafond d'un compte). */
    public static final long AMOUNT_MAX = ContractRules.TOTAL_MAX;
    /** Une echeance au plus 3 650 jours de jeu apres l'envoi. */
    public static final long DUE_DAYS_MAX = 3650;
    /** Documents encore ouverts (brouillon, envoye, accepte, en retard) par auteur. */
    public static final int OPEN_MAX = 40;
    /** Lignes d'historique gardees par document. */
    public static final int HISTORY = 32;
    /** Un document clos disparait de la liste {@code KEEP_DAYS} jours de jeu apres sa cloture (une lettre : apres son envoi). */
    public static final long KEEP_DAYS = 120;
    /** Echeancier propose par defaut : une echeance a 7 jours. */
    public static final long DEFAULT_DUE_IN = 7;

    private DocumentRules() {
    }

    /** Texte d'une ligne (objet, libelle, nom) nettoye et borne (sans codes de couleur ni retours a la ligne). */
    public static String clean(String s, int max) {
        return ContractRules.clean(s, max);
    }

    /** Corps nettoye (fins de ligne gardees). */
    public static String cleanBody(String s) {
        return ContractRules.cleanBody(s);
    }

    /** Total des lignes (0 si vide ; -1 si un montant est negatif ; plafonne au-dela pour ne pas deborder). */
    public static long total(List<Document.Line> lines) {
        long t = 0;
        for (Document.Line l : lines) {
            if (l.amount() < 0) {
                return -1;
            }
            t = Math.min(Long.MAX_VALUE / 4, t + l.amount());
        }
        return t;
    }

    public static long sum(List<Document.Due> dues) {
        long t = 0;
        for (Document.Due d : dues) {
            t = Math.min(Long.MAX_VALUE / 4, t + Math.max(0, d.amount));
        }
        return t;
    }

    /**
     * Verifie un document ; rend la cle courte de l'erreur ({@code document.pioneers.error.<cle>}) ou {@code null}.
     *
     * @param today jour de jeu ; {@code send} : verification de l'envoi (echeances a venir, corps exige)
     */
    @Nullable
    public static String validate(DocType type, String subject, String body, List<Document.Line> lines, List<Document.Due> dues, long today,
          boolean send) {
        if (subject == null || subject.isBlank()) {
            return "subject";
        }
        if (subject.length() > SUBJECT_MAX) {
            return "subject_long";
        }
        String bp = ContractRules.bodyProblem(body);
        if (bp != null) {
            return bp;
        }
        if (send && !type.amounts && (body == null || body.isBlank())) {
            return "body_empty";
        }
        if (!type.amounts && !lines.isEmpty()) {
            return "no_amounts";
        }
        if (lines.size() > LINES_MAX) {
            return "lines_many";
        }
        for (Document.Line l : lines) {
            if (l.label() == null || l.label().isBlank()) {
                return "label";
            }
            if (l.label().length() > LABEL_MAX) {
                return "label_long";
            }
            if (l.amount() < 0) {
                return "amount_negative";
            }
            if (l.amount() > AMOUNT_MAX) {
                return "amount_big";
            }
        }
        long total = total(lines);
        if (total > AMOUNT_MAX) {
            return "total_big";
        }
        if (type.money && total < 1) {
            return "total_zero";
        }
        if (!type.schedule && !dues.isEmpty()) {
            return "no_schedule";
        }
        if (dues.size() > DUES_MAX) {
            return "dues_many";
        }
        for (Document.Due d : dues) {
            if (d.amount < 1) {
                return "due_amount";
            }
            if (d.amount > AMOUNT_MAX) {
                return "amount_big";
            }
            if (send && d.day < today) {
                return "due_past";
            }
            if (d.day > today + DUE_DAYS_MAX || d.day < 0) {
                return "due_far";
            }
        }
        if (type.payable() && total > 0 && dues.isEmpty()) {
            return "schedule_empty";
        }
        if (!dues.isEmpty() && sum(dues) != total) {
            return "schedule_total";
        }
        return null;
    }

    /** Echeances rangees par jour (ordre stable). */
    public static List<Document.Due> sorted(List<Document.Due> dues) {
        List<Document.Due> out = new ArrayList<>(dues);
        out.sort((a, b) -> Long.compare(a.day, b.day));
        return out;
    }

    /**
     * Echeancier en {@code n} parts egales (le reste sur la premiere) a partir du jour {@code first}, une tous les {@code step} jours ; aide
     * du formulaire (le joueur ajuste ensuite).
     */
    public static List<Document.Due> split(long total, int n, long first, long step) {
        List<Document.Due> out = new ArrayList<>();
        n = Math.max(1, Math.min(DUES_MAX, n));
        if (total <= 0) {
            return out;
        }
        n = (int) Math.min(n, total);
        long part = total / n, rest = total - part * n;
        for (int i = 0; i < n; i++) {
            out.add(new Document.Due(part + (i == 0 ? rest : 0), first + i * Math.max(1, step)));
        }
        return out;
    }
}
