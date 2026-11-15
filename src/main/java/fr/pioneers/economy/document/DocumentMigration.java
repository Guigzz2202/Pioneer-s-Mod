package fr.pioneers.economy.document;

import fr.pioneers.economy.contract.Contract;
import fr.pioneers.economy.contract.ContractRules;
import net.minecraft.nbt.CompoundTag;

/**
 * 1.0.5-beta (lot 26) : MIGRATION PURE des contrats et des factures d'avant l'onglet Documents (banc sur un NBT d'avant FIGE dans le depot :
 * {@code tools/documents/legacy_world.snbt}, ecrit par le vrai code d'avant le lot 26 ; GameTests {@code PatchP105Lot26Tests}).
 * <ul>
 *     <li>un CONTRAT ({@code pioneers_contracts}, NBT du lot 7 au lot 16 : au taux, au total, a l'heure, avec ou sans lettre) devient un
 *     document de type contrat au MEME numero ; le contrat lui-meme est garde tel quel ({@code DocumentData} le porte) et son moteur au temps
 *     reel continue (prelevements, reserve, suspension, lettres) : rien n'est perdu ni recalcule ;</li>
 *     <li>une FACTURE imprimee (objet {@code pioneers:contract_invoice}, NBT {@code invoice} : instantane d'un contrat) devient un document
 *     de type facture (lignes, echeances reglee / due, statut) qui garde l'instantane d'origine ({@link Document#legacy}).</li>
 * </ul>
 */
public final class DocumentMigration {

    private DocumentMigration() {
    }

    /** Statut de document d'un contrat au temps reel (lu a chaque vue : il suit le contrat). */
    public static DocStatus statusOf(Contract c) {
        return switch (c.state) {
            case DRAFT -> DocStatus.DRAFT;
            case PROPOSED -> DocStatus.SENT;
            case REFUSED -> DocStatus.REFUSED;
            case EXPIRED -> DocStatus.CANCELLED;
            case ACCEPTED, RUNNING -> c.shortFunds && c.owed() > 0 ? DocStatus.OVERDUE : DocStatus.ACCEPTED;
            case SUSPENDED -> DocStatus.OVERDUE;
            case FINISHED -> c.owed() > 0 ? DocStatus.OVERDUE : DocStatus.PAID;
            case TERMINATED -> c.owed() > 0 ? DocStatus.OVERDUE : DocStatus.CANCELLED;
        };
    }

    /** Document d'un contrat au temps reel (meme numero ; parties, objet, lettre, montant prevu, signatures, statut). */
    public static Document fromContract(Contract c) {
        Document d = new Document(c.id);
        d.type = DocType.CONTRACT;
        d.timed = c.id;
        sync(d, c);
        d.event(-1, "migrated", c.plannedTotal(), "");
        return d;
    }

    /** Remet le document d'un contrat au temps reel a jour du contrat (parties, termes, statut, signatures). */
    public static void sync(Document d, Contract c) {
        d.author = Document.Party.player(c.provider, c.providerName);
        d.recipient = Document.Party.player(c.client, c.clientName);
        d.recipientAccount = c.clientAccount;
        d.subject = DocumentRules.clean(c.title, DocumentRules.SUBJECT_MAX);
        d.body = c.body.isEmpty() ? ContractRules.cleanBody(c.note) : c.body;
        d.lines.clear();
        if (!c.title.isEmpty() || c.plannedTotal() > 0) {
            d.lines.add(new Document.Line(DocumentRules.clean(c.title.isEmpty() ? "#" + c.id : c.title, DocumentRules.LABEL_MAX),
                  Math.max(0, c.plannedTotal())));
        }
        d.dues.clear();
        d.status = statusOf(c);
        d.authorSign = c.proposed > 0 ? c.providerName : "";
        d.recipientSign = c.accepted > 0 ? c.clientName : "";
    }

    /**
     * Document d'une facture imprimee d'avant (NBT {@code invoice} : {@code ContractService.card} + {@code printed} + {@code zone}) : une
     * ligne au facture, une echeance reglee (le paye) et une due (le reste), statut paye / en retard / envoye ; l'instantane est garde.
     */
    public static Document fromInvoice(CompoundTag inv) {
        Document d = new Document(inv.getInt("id"));
        d.type = DocType.INVOICE;
        d.timed = inv.getInt("id");
        d.author = Document.Party.player(inv.hasUUID("pv") ? inv.getUUID("pv") : null, inv.getString("pvn"));
        d.recipient = Document.Party.player(inv.hasUUID("cl") ? inv.getUUID("cl") : null, inv.getString("cln"));
        d.recipientAccount = inv.getString("cla");
        String title = inv.getString("title");
        d.subject = DocumentRules.clean(title.isEmpty() ? "#" + d.id : title, DocumentRules.SUBJECT_MAX);
        String body = inv.contains("body") ? inv.getString("body") : "";
        d.body = ContractRules.cleanBody(body.isEmpty() ? inv.getString("note") : body);
        long billed = Math.max(0, inv.getLong("billed")), paid = Math.min(billed, Math.max(0, inv.getLong("paid")));
        long owed = billed - paid;
        d.lines.add(new Document.Line(DocumentRules.clean(title.isEmpty() ? "#" + d.id : title, DocumentRules.LABEL_MAX), billed));
        if (paid > 0) {
            Document.Due p = new Document.Due(paid, 0);
            p.paidDay = 0;
            d.dues.add(p);
        }
        if (owed > 0) {
            d.dues.add(new Document.Due(owed, 0));
        }
        d.status = owed <= 0 ? DocStatus.PAID : inv.getBoolean("short") || "unpaid".equals(inv.getString("shown")) ? DocStatus.OVERDUE
              : DocStatus.SENT;
        d.authorSign = inv.getString("pvn");
        d.recipientSign = inv.getLong("accepted") > 0 ? inv.getString("cln") : "";
        d.legacy = inv.copy();
        d.event(-1, "migrated", billed, "");
        return d;
    }
}
