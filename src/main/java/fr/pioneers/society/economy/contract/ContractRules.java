package fr.pioneers.society.economy.contract;

/**
 * 1.0.4-beta (lot 7, Guillaume : « Systeme de facturation (avec contrat date heure debut date heure fin, taux horaire) », precision « entre
 * joueurs, temps reel ») : regles PURES des contrats (aucune classe de Minecraft : banc {@code tools/rptest/ContractBench.java}).
 * <p>
 * Montants en Shining ENTIERS (la monnaie du mod n'a pas de centimes) ; le du se calcule a la minute : {@code floor(minutes x taux / 60)}, sur
 * le temps ecoule depuis le debut effectif (le plus tard du debut prevu et de l'acceptation). Le prelevement se fait a chaque heure entiere
 * ecoulee, puis a la fin (ou a la resiliation) pour les minutes de la derniere heure entamee ; le cumul est toujours recalcule depuis le
 * debut, si bien que la somme des prelevements vaut exactement le prorata a la minute (aucun arrondi qui s'accumule).
 */
public final class ContractRules {

    public static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR;
    /** Taux horaire : 1 a 1 000 000 Shining par heure reelle. */
    public static final long RATE_MIN = 1, RATE_MAX = 1_000_000L;
    /** Duree : une minute a 366 jours. */
    public static final long DURATION_MIN = MINUTE, DURATION_MAX = 366 * DAY;
    /** Montant prevu au plus (le plafond d'un compte, {@code EconomyApi.MAX}). */
    public static final long TOTAL_MAX = 1_000_000_000L;
    public static final int TITLE_MAX = 40, NOTE_MAX = 120, NAME_MAX = 16;
    /** 1.0.5-beta (lot 16) : corps de la lettre du contrat : caracteres et lignes au plus (bornes du serveur ; le Carnet affiche le compte). */
    public static final int BODY_MAX = 1200, BODY_LINES = 24;
    /** Contrats encore ouverts (brouillon, propose, accepte, en cours, ou clos avec un reste du) par prestataire. */
    public static final int OPEN_MAX = 20;
    /** Lignes d'historique gardees par contrat (les plus recentes). */
    public static final int HISTORY = 64;
    /** Un contrat clos et solde disparait de la liste 90 jours apres sa fin. */
    public static final long KEEP_CLOSED = 90 * DAY;
    /** 1.0.4-beta (lot 29) : reserve d'un contrat a l'heure : jusqu'a 24 heures d'avance (au plus le reste du contrat). */
    public static final int RESERVE_HOURS = 24;

    private ContractRules() {
    }

    /** Du pour {@code minutes} minutes au taux horaire {@code rate} (arrondi a l'inferieur). */
    public static long amount(long minutes, long rate) {
        return Math.floorDiv(Math.max(0, minutes) * Math.max(0, rate), 60L);
    }

    /**
     * 1.0.4-beta (lot 29) : part d'un montant total pour {@code minutes} minutes sur {@code planned} (arrondie a l'inferieur ; tout le montant
     * des que la periode est entiere, ou si elle est vide).
     */
    public static long share(long total, long minutes, long planned) {
        if (planned <= 0 || minutes >= planned) {
            return Math.max(0, total);
        }
        return Math.floorDiv(Math.max(0, total) * Math.max(0, minutes), planned);
    }

    /** 1.0.4-beta (lot 29) : reserve visee d'un contrat a l'heure : {@code RESERVE_HOURS} heures, au plus les heures (entamees) qui restent. */
    public static long reserveTarget(long rate, long remainingMinutes) {
        long hours = Math.min(RESERVE_HOURS, Math.max(0, (remainingMinutes + 59) / 60));
        return hours * Math.max(0, rate);
    }

    /**
     * 1.0.4-beta (lot 29) : termes d'un contrat selon sa formule ; {@code amount} = montant total ({@code total}) ou taux horaire
     * ({@code hourly}). Rend la cle courte de l'erreur ou {@code null}.
     */
    public static String validate(String title, long start, long end, boolean hourly, long amount, long now) {
        if (hourly) {
            return validate(title, start, end, amount, now);
        }
        String err = validate(title, start, end, 1, now);
        if (err != null) {
            return err;
        }
        if (amount < 1 || amount > TOTAL_MAX) {
            return "amount";
        }
        return null;
    }

    /** Minutes entieres de {@code from} a {@code to} (0 si {@code to <= from}). */
    public static long minutes(long from, long to) {
        return to <= from ? 0 : (to - from) / MINUTE;
    }

    /**
     * Verifie les termes d'un contrat ; rend la cle courte de l'erreur ({@code contract.pioneers.error.<cle>}) ou {@code null}.
     *
     * @param now heure du serveur, ou {@code Long.MIN_VALUE} pour ne pas exiger une fin a venir (brouillon)
     */
    public static String validate(String title, long start, long end, long rate, long now) {
        if (title == null || title.isBlank()) {
            return "title";
        }
        if (title.length() > TITLE_MAX) {
            return "title_long";
        }
        if (rate < RATE_MIN || rate > RATE_MAX) {
            return "rate";
        }
        if (end <= start) {
            return "order";
        }
        if (end - start < DURATION_MIN) {
            return "short";
        }
        if (end - start > DURATION_MAX) {
            return "long";
        }
        if (amount(minutes(start, end), rate) > TOTAL_MAX) {
            return "total";
        }
        if (now != Long.MIN_VALUE && end <= now) {
            return "past";
        }
        return null;
    }

    /** Texte libre nettoye (sans codes de couleur ni retours a la ligne), borne a {@code max} caracteres. */
    public static String clean(String s, int max) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c == '§' || Character.isISOControl(c)) {
                continue;
            }
            b.append(c);
        }
        return b.toString().trim();
    }

    /**
     * 1.0.5-beta (lot 16) : corps de lettre nettoye : fins de ligne unifiees ({@code \n}), sans codes de couleur ni autres caracteres de
     * controle, espaces de fin de ligne et lignes vides finales retires, borne a {@link #BODY_LINES} lignes et {@link #BODY_MAX} caracteres.
     */
    public static String cleanBody(String s) {
        if (s == null) {
            return "";
        }
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length && i < BODY_LINES; i++) {
            StringBuilder l = new StringBuilder();
            for (char c : lines[i].toCharArray()) {
                if (c != '\u00a7' && !Character.isISOControl(c)) {
                    l.append(c);
                }
            }
            if (i > 0) {
                b.append('\n');
            }
            b.append(l.toString().stripTrailing());
        }
        String out = b.toString();
        while (out.endsWith("\n")) {
            out = out.substring(0, out.length() - 1);
        }
        return out.length() > BODY_MAX ? out.substring(0, BODY_MAX).stripTrailing() : out;
    }

    /** 1.0.5-beta (lot 16) : corps refuse tel quel (« body_long », « body_lines ») ou {@code null} : le serveur ne coupe pas en silence. */
    public static String bodyProblem(String s) {
        if (s == null) {
            return null;
        }
        String n = s.replace("\r\n", "\n").replace('\r', '\n');
        if (n.length() > BODY_MAX) {
            return "body_long";
        }
        if (n.split("\n", -1).length > BODY_LINES) {
            return "body_lines";
        }
        return null;
    }
}
