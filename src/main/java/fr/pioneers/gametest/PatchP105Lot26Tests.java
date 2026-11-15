package fr.pioneers.gametest;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.contract.Contract;
import fr.pioneers.society.economy.contract.ContractData;
import fr.pioneers.society.economy.contract.ContractEngine;
import fr.pioneers.society.economy.contract.ContractItems;
import fr.pioneers.society.economy.contract.ContractRules;
import fr.pioneers.society.economy.contract.ContractService;
import fr.pioneers.society.economy.contract.InvoiceItem;
import fr.pioneers.society.economy.document.DocStatus;
import fr.pioneers.society.economy.document.DocType;
import fr.pioneers.society.economy.document.Document;
import fr.pioneers.society.economy.document.DocumentData;
import fr.pioneers.society.economy.document.DocumentEngine;
import fr.pioneers.society.economy.document.DocumentRules;
import fr.pioneers.society.economy.document.DocumentService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 26 (retours de jeu 2e serie, Documents et infobulles, GameTests) : UN DOCUMENT DE CHAQUE TYPE cree et envoye par le
 * vrai service (brouillon, validation du serveur, statut, signature, lettre) et une facture a un CLAN reglee depuis le tresor ; CONTRAT ACCEPTE
 * PUIS PAYE PAR ECHEANCES (prelevement d'office au jour de jeu, joueur absent en retard puis rattrape, derniere echeance a la main : paye) ;
 * MIGRATION d'un ancien contrat (NBT d'avant le lot 29, sans formule ni lettre) et d'une ancienne facture imprimee (objet contract_invoice).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot26Tests {

    private PatchP105Lot26Tests() {
    }

    /** Demande « save » du Carnet : type, destinataire, objet, corps, lignes, echeances, envoi. */
    static CompoundTag form(DocType type, String toKind, String to, long[] lines, long[][] dues, boolean send) {
        CompoundTag t = new CompoundTag();
        t.putString("op", "save");
        t.putString("type", type.key());
        t.putString("toKind", toKind);
        t.putString("to", to);
        t.putString("subject", "Essai " + type.key());
        t.putString("body", "Bonjour,\nvoici le document.");
        ListTag ls = new ListTag();
        for (int i = 0; i < lines.length; i++) {
            CompoundTag c = new CompoundTag();
            c.putString("l", "Ligne " + (i + 1));
            c.putLong("a", lines[i]);
            ls.add(c);
        }
        t.put("lines", ls);
        ListTag ds = new ListTag();
        for (long[] d : dues) {
            CompoundTag c = new CompoundTag();
            c.putLong("a", d[0]);
            c.putLong("d", d[1]);
            ds.add(c);
        }
        t.put("dues", ds);
        t.putBoolean("send", send);
        return t;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_26_types", timeoutTicks = 20)
    public static void p105_26_un_document_de_chaque_type(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee (interrupteur economy)");
        ServerPlayer au = s.player("gtdau", "Anselme", new BlockPos(1, 1, 1));
        ServerPlayer to = s.player("gtdto", "Mireille", new BlockPos(3, 1, 1));
        PatchP104Lot7Tests.balance(au, 5000);
        PatchP104Lot7Tests.balance(to, 5000);
        DocumentData data = DocumentData.get(s.server);
        long today = PioneerApi.day(s.server);
        List<Integer> made = new ArrayList<>();
        try {
            for (DocType t : DocType.values()) {
                long[] lines = t.amounts ? new long[]{300, 200} : new long[0];
                long[][] dues = t.payable() ? new long[][]{{250, today + 2}, {250, today + 9}} : new long[0][];
                DocumentService.Saved sv = DocumentService.save(au, form(t, "player", to.getGameProfile().getName(), lines, dues, true));
                Gt.check(sv.error() == null && sv.id() > 0, "document " + t.key() + " refuse : " + sv.error());
                made.add(sv.id());
                Document d = data.get(sv.id());
                Gt.check(d != null && d.type == t, "document " + t.key() + " enregistre");
                Gt.equal(t == DocType.RECEIPT ? DocStatus.PAID : DocStatus.SENT, d.status, "statut apres envoi : " + t.key());
                Gt.check(d.recipient.is(to.getUUID()) && d.author.is(au.getUUID()), "parties : " + t.key());
                Gt.equal(t.amounts ? 500L : 0L, d.total(), "total : " + t.key());
                Gt.check(DocumentService.sees(s.server, to, d), "le destinataire voit le document " + t.key());
                Gt.check(DocumentEngine.Side.RECIPIENT == DocumentService.side(s.server, to, d), "cote du destinataire : " + t.key());
                Document back = Document.load(d.save());
                Gt.check(back.save().equals(d.save()), "relu apres sauvegarde : " + t.key());
            }
            //validation du serveur : echeancier faux, montant negatif, a soi-meme, inconnu
            Gt.equal("schedule_total", DocumentService.save(au, form(DocType.INVOICE, "player", to.getGameProfile().getName(), new long[]{500},
                  new long[][]{{400, today + 2}}, false)).error(), "somme des echeances != total refusee");
            Gt.equal("amount_negative", DocumentService.save(au, form(DocType.QUOTE, "player", to.getGameProfile().getName(), new long[]{-5},
                  new long[0][], false)).error(), "montant negatif refuse");
            Gt.equal("to_self", DocumentService.save(au, form(DocType.LETTER, "player", au.getGameProfile().getName(), new long[0], new long[0][],
                  false)).error(), "document a soi-meme refuse");
            Gt.equal("to_unknown", DocumentService.save(au, form(DocType.LETTER, "player", "Personne_Inconnue_26", new long[0], new long[0][], false))
                  .error(), "destinataire inconnu refuse");
            CompoundTag bad = form(DocType.LETTER, "player", to.getGameProfile().getName(), new long[0], new long[0][], false);
            bad.putString("type", "faux_type");
            Gt.equal("type", DocumentService.save(au, bad).error(), "type inconnu refuse");
            //facture a un clan, reglee depuis le tresor par son fondateur
            Clan clan = s.clan("Clan des essais 26", to);
            clan.treasury = 1000;
            DocumentService.Saved sv = DocumentService.save(au, form(DocType.INVOICE, "clan", clan.name, new long[]{400}, new long[][]{{400, today + 1}},
                  true));
            Gt.check(sv.error() == null, "facture au clan refusee : " + sv.error());
            made.add(sv.id());
            long before = EconomyApi.balance(au);
            Gt.check(DocumentService.act(to, sv.id(), "pay") == null, "le fondateur paie depuis le tresor");
            Gt.equal(600L, clan.treasury, "tresor debite");
            Gt.equal(before + 400, EconomyApi.balance(au), "auteur credite");
            Gt.equal(DocStatus.PAID, data.get(sv.id()).status, "facture du clan payee");
            Gt.equal("own_clan", DocumentService.save(to, form(DocType.LETTER, "clan", clan.name, new long[0], new long[0][], false)).error(),
                  "pas a son propre clan");
        } finally {
            for (int id : made) {
                data.remove(id);
            }
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_26_installments", timeoutTicks = 20)
    public static void p105_26_contrat_accepte_puis_paye_par_echeances(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer au = s.player("gtdca", "Anselme", new BlockPos(1, 1, 1));
        ServerPlayer to = s.player("gtdcb", "Mireille", new BlockPos(3, 1, 1));
        ServerPlayer other = s.player("gtdcx", "Tiers", new BlockPos(5, 1, 1));
        PatchP104Lot7Tests.balance(au, 0);
        PatchP104Lot7Tests.balance(to, 1000);
        DocumentData data = DocumentData.get(s.server);
        long d0 = PioneerApi.day(s.server);
        DocumentService.Saved sv = DocumentService.save(au, form(DocType.CONTRACT, "player", to.getGameProfile().getName(), new long[]{900},
              new long[][]{{300, d0 + 1}, {300, d0 + 2}, {300, d0 + 5}}, true));
        Gt.check(sv.error() == null, "contrat refuse : " + sv.error());
        Document d = data.get(sv.id());
        try {
            DocumentEngine.Bank bank = DocumentService.bank(s.server);
            Gt.equal(DocStatus.SENT, d.status, "envoye, a signer");
            Gt.equal("not_allowed", DocumentService.act(other, d.id, "accept"), "un tiers ne signe pas");
            Gt.equal("not_allowed", DocumentService.act(au, d.id, "accept"), "l'auteur ne signe pas a la place du destinataire");
            DocumentEngine.settle(d, d0 + 5, bank);
            Gt.equal(1000L, EconomyApi.balance(to), "rien preleve avant la signature");
            Gt.check(DocumentService.act(to, d.id, "accept") == null, "signe et accepte");
            Gt.equal(DocStatus.ACCEPTED, d.status, "accepte");
            Gt.check(!d.recipientSign.isEmpty() && d.recipientSignDay >= 0, "signature du destinataire posee");
            DocumentEngine.settle(d, d0 + 1, bank);
            Gt.equal(700L, EconomyApi.balance(to), "1re echeance prelevee au jour 1");
            Gt.equal(300L, EconomyApi.balance(au), "1re echeance versee a l'auteur");
            //absent au jour 2 : en retard le lendemain, rattrape a son retour
            UUID toId = to.getUUID();
            String account = to.getGameProfile().getName();
            Gt.disconnect(s.server, toId);
            DocumentEngine.settle(d, d0 + 2, bank);
            Gt.equal(DocStatus.ACCEPTED, d.status, "jour 2, absent : pas encore en retard");
            DocumentEngine.settle(d, d0 + 3, bank);
            Gt.equal(DocStatus.OVERDUE, d.status, "en retard le lendemain");
            ServerPlayer back = s.reconnect(toId, account, new BlockPos(3, 1, 1));
            DocumentEngine.settle(d, d0 + 3, bank);
            Gt.equal(300L, d.owed(), "rattrape au retour : l'echeance du jour 2 prelevee, celle du jour 5 a venir");
            Gt.equal(DocStatus.ACCEPTED, d.status, "de nouveau a jour");
            Gt.check(DocumentService.act(back, d.id, "pay") == null, "derniere echeance reglee a la main");
            Gt.equal(DocStatus.PAID, d.status, "contrat paye");
            Gt.equal(100L, EconomyApi.balance(back), "client : 900 en tout");
            Gt.equal(900L, EconomyApi.balance(au), "auteur : 900 recus");
            Gt.equal(d.total(), d.scheduled(), "total = somme des echeances");
        } finally {
            data.remove(d.id);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_26_migration", timeoutTicks = 20)
    public static void p105_26_migration_d_un_ancien_contrat_et_d_une_ancienne_facture(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        UUID pv = UUID.randomUUID(), cl = UUID.randomUUID();
        //ancien registre : un contrat au taux du lot 7 (sans formule ni lettre), en cours, en partie preleve
        ContractData old = new ContractData();
        Contract c = old.create();
        c.provider = pv;
        c.client = cl;
        c.providerName = "Anselme";
        c.clientName = "Mireille";
        c.title = "Garde de la mine";
        c.note = "Ronde de nuit";
        c.start = 1_793_000_000_000L;
        c.end = c.start + 6 * ContractRules.HOUR;
        c.rate = 25;
        c.state = Contract.State.RUNNING;
        c.accepted = c.start;
        c.billed = 50;
        c.paid = 50;
        c.billedMinutes = 120;
        CompoundTag oldTag = old.save(new CompoundTag());
        CompoundTag ct = oldTag.getList("c", 10).getCompound(0);
        for (String k : List.of("pay", "total", "hours", "reserve", "pauseAt", "paused", "refund", "body")) {
            ct.remove(k);
        }
        DocumentData data = new DocumentData();
        data.migrate(ContractData.load(oldTag));
        Contract m = data.contracts().get(c.id);
        Document d = data.get(c.id);
        Gt.check(m != null && d != null, "contrat et document migres au meme numero");
        Gt.equal(Contract.load(ct).save(), m.save(), "contrat garde a l'octet pres");
        Gt.equal(DocType.CONTRACT, d.type, "document de type contrat");
        Gt.equal(c.id, d.timed, "lie a son contrat au temps reel");
        Gt.equal(DocStatus.ACCEPTED, d.status, "en cours = accepte");
        Gt.equal(150L, d.total(), "montant prevu au taux d'origine (6 h x 25)");
        Gt.equal("Ronde de nuit", d.body, "la description tient lieu de lettre");
        Gt.check(data.create().id > c.id, "numeros suivants apres les anciens");
        Contract fresh = Contract.load(ct);
        ContractEngine.Bank never = new ContractEngine.Bank() {
            public boolean online(UUID w) { return false; }
            public long balance(UUID w) { return 0; }
            public boolean debit(UUID w, long a, Contract x) { return false; }
            public boolean credit(UUID w, long a, Contract x) { return false; }
            public void notify(UUID w, Contract x, String k, String... a) { }
        };
        ContractEngine.settle(fresh, c.end + ContractRules.HOUR, never);
        ContractEngine.settle(m, c.end + ContractRules.HOUR, never);
        Gt.equal(fresh.save(), m.save(), "le moteur au temps reel continue a l'identique sur le contrat migre");
        //ancienne facture imprimee : objet contract_invoice au NBT « invoice »
        ItemStack inv = new ItemStack(ContractItems.INVOICE.get());
        CompoundTag snap = ContractService.card(Contract.load(ct), c.start + 2 * ContractRules.HOUR);
        snap.putLong("printed", c.start + 2 * ContractRules.HOUR);
        snap.putString("zone", "UTC");
        inv.getOrCreateTag().put(InvoiceItem.TAG, snap.copy());
        Gt.check(InvoiceItem.migrate(inv), "facture migree");
        Gt.check(!InvoiceItem.migrate(inv), "migree une seule fois");
        Document f = Document.load(inv.getTag().getCompound(InvoiceItem.DOCUMENT));
        Gt.equal(DocType.INVOICE, f.type, "document de type facture");
        Gt.equal(50L, f.total(), "facture : le facture");
        Gt.equal(50L, f.paid(), "facture : le paye");
        Gt.equal(DocStatus.PAID, f.status, "facture soldee = payee");
        Gt.equal(snap, inv.getTag().getCompound(InvoiceItem.TAG), "instantane d'origine garde");
        Gt.equal(snap, f.legacy, "instantane d'origine dans le document");
        Gt.check(DocumentRules.validate(DocType.INVOICE, f.subject, f.body, f.lines, f.dues, 0, false) == null, "facture migree valide");
        s.done();
    }
}
