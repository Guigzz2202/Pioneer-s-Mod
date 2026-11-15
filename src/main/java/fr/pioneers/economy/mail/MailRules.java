package fr.pioneers.economy.mail;

import java.util.List;

/**
 * 1.0.4-beta (lot 29, Guillaume : « Boite aux lettres : quand une personne recoit un virement ou un contrat, la boite aux lettres recoit une
 * lettre, de plus elle sert egalement d'inventaire ; la boite aux lettres a un point d'exclamation au-dessus si le joueur a un nouvel item
 * dedans qu'il n'a pas consulte ») : regles PURES du courrier (sans Minecraft).
 * <ul>
 *     <li>UNE boite par joueur, quel que soit le nombre de blocs « boite aux lettres » poses : toutes montrent le MEME contenu (comme le coffre
 *     de l'Ender), garde dans une donnee du monde ; casser un bloc ne perd rien ;</li>
 *     <li>{@link #SLOTS} cases (un coffre simple) ou le proprietaire range ce qu'il veut ; le courrier y arrive dans la premiere case libre,
 *     sinon il attend (au plus {@link #PENDING_MAX} lettres, les plus anciennes au-dela sont perdues) et entre des que de la place se libere ;</li>
 *     <li>pas d'envoi d'objets entre joueurs (le mod n'avait aucun courrier : stockage simple, choix du lot 29) ; le serveur seul ecrit des
 *     lettres : virement recu, evenements des contrats ;</li>
 *     <li>« non lu » = lettres arrivees depuis la derniere ouverture d'une de ses boites : le proprietaire voit un « ! » au-dessus de ses
 *     boites tant qu'il en reste ; ouvrir une boite remet le compte a zero.</li>
 * </ul>
 */
public final class MailRules {

    /** Cases de la boite (3 rangees de 9). */
    public static final int SLOTS = 27;
    /** Lettres qui attendent une case libre, au plus. */
    public static final int PENDING_MAX = 64;
    /** Compte des non lus envoye au client, borne. */
    public static final int UNREAD_MAX = 99;
    /** Portee d'une boite ouverte (blocs) : au-dela, l'ecran se ferme. */
    public static final double REACH = 8;

    /**
     * Sortes de lettres (cle de langue {@code mail.pioneers.kind.<sorte>} = objet, {@code mail.pioneers.body.<sorte>} = texte). Les
     * contrats : propose, retire, signe, refuse, expire, suspendu (fonds insuffisants d'un contrat a l'heure), repris, resilie, termine,
     * paiement en retard (contrat au montant total), dette reglee.
     */
    public static final List<String> KINDS = List.of("transfer", "contract_proposed", "contract_withdrawn", "contract_accepted",
          "contract_refused", "contract_expired", "contract_suspended", "contract_resumed", "contract_terminated", "contract_finished",
          "contract_unpaid", "contract_paid",
          //1.0.5-beta (lot 26) : documents du Carnet (onglet Documents)
          "document_received", "document_letter", "document_accepted", "document_refused", "document_cancelled", "document_settled",
          "document_overdue");

    private MailRules() {
    }

    /** Premiere case vide parmi {@code empty} (vrai = vide), ou -1. */
    public static int firstEmpty(boolean[] empty) {
        for (int i = 0; i < empty.length; i++) {
            if (empty[i]) {
                return i;
            }
        }
        return -1;
    }
}
