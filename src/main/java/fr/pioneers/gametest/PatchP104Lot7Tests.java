package fr.pioneers.gametest;

import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.contract.Contract;
import fr.pioneers.society.economy.contract.ContractData;
import fr.pioneers.society.economy.contract.ContractEngine;
import fr.pioneers.society.economy.contract.ContractItems;
import fr.pioneers.society.economy.contract.ContractRules;
import fr.pioneers.society.economy.contract.ContractService;
import fr.pioneers.society.economy.contract.InvoiceItem;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 7 (GameTests) : contrats entre joueurs au taux horaire, en TEMPS SIMULE (l'heure est passee aux methodes du service,
 * jamais attendue) avec de vrais joueurs factices et leurs vrais comptes ({@link EconomyApi}). Creation et proposition par le formulaire du
 * service (client par son nom), acceptation par le client seul, prelevement a l'heure puis au prorata a la minute, resiliation, fonds
 * insuffisants (dette, reprise), rien sans acceptation (expiration), client et prestataire hors ligne (rattrapage a la connexion), sauvegarde
 * relue, facture imprimee contre une feuille de papier. Chaque test retire ses contrats a la fin.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot7Tests {

    private static final long H = ContractRules.HOUR, M = ContractRules.MINUTE;

    private PatchP104Lot7Tests() {
    }

    /** {annee, mois, jour, heure, minute} d'un instant, dans le fuseau du serveur (comme le formulaire du Carnet). */
    static int[] fields(long ms) {
        ZonedDateTime d = Instant.ofEpochMilli(ms).atZone(ContractService.zone());
        return new int[]{d.getYear(), d.getMonthValue(), d.getDayOfMonth(), d.getHour(), d.getMinute()};
    }

    static long base() {
        return ContractService.now() / M * M + H;
    }

    static void balance(ServerPlayer p, long v) {
        PioneerCap.get(p).balance = v;
    }

    /** Contrat propose par {@code provider} a {@code client} de {@code start} a {@code start + hours}, au taux {@code rate}. */
    static Contract offer(ServerPlayer provider, ServerPlayer client, long start, int hours, long rate, long now) {
        ContractService.Saved s = ContractService.save(provider, 0, client.getGameProfile().getName(), "Garde de la mine", "Ronde de nuit",
              fields(start), fields(start + hours * H), rate, true, now);
        Gt.check(s.error() == null, "proposition refusee : " + s.error());
        Contract c = ContractData.get(provider.getServer()).get(s.id());
        Gt.check(c != null && c.state == Contract.State.PROPOSED, "contrat non propose");
        return c;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104g_contrat", timeoutTicks = 20)
    public static void p104_7_creation_acceptation_prelevement_au_prorata(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee (interrupteur economy)");
        ServerPlayer pv = s.player("gtcpv", "Anselme", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gtccl", "Mireille", new BlockPos(3, 1, 1));
        ServerPlayer other = s.player("gtcx", "Tiers", new BlockPos(5, 1, 1));
        balance(pv, 0);
        balance(cl, 1000);
        long t0 = base();
        Contract c = offer(pv, cl, t0, 8, 60, t0 - H);
        ContractData data = ContractData.get(s.server);
        try {
            Gt.equal(480, c.plannedTotal(), "montant prevu (8 h x 60)");
            Gt.equal("not_allowed", ContractService.act(other, c.id, "accept", t0 - H), "un tiers ne peut pas accepter");
            Gt.equal("not_allowed", ContractService.act(pv, c.id, "accept", t0 - H), "le prestataire n'accepte pas a la place du client");
            Gt.equal("not_allowed", ContractService.act(other, c.id, "terminate", t0 - H), "un tiers ne peut pas resilier");
            Gt.check(ContractService.act(cl, c.id, "accept", t0 - 30 * M) == null, "acceptation du client");
            Gt.check(c.state == Contract.State.ACCEPTED, "accepte, a venir");
            ContractEngine.Bank bank = ContractService.bank(s.server);
            ContractEngine.settle(c, t0 + 30 * M, bank);
            Gt.equal(1000, EconomyApi.balance(cl), "rien a la premiere demi-heure");
            ContractEngine.settle(c, t0 + H, bank);
            Gt.equal(940, EconomyApi.balance(cl), "1 h prelevee chez le client");
            Gt.equal(60, EconomyApi.balance(pv), "1 h versee au prestataire");
            Gt.check(ContractService.act(pv, c.id, "terminate", t0 + H + 45 * M) == null, "resiliation par le prestataire");
            Gt.check(c.state == Contract.State.TERMINATED, "resilie");
            Gt.equal(105, c.billed, "prorata a la minute : 1 h 45 a 60/h");
            Gt.equal(895, EconomyApi.balance(cl), "client : 105 en tout");
            Gt.equal(105, EconomyApi.balance(pv), "prestataire : 105 en tout");
            ContractEngine.settle(c, t0 + 9 * H, bank);
            Gt.equal(895, EconomyApi.balance(cl), "plus rien apres la resiliation");
            Gt.check(!c.open(), "contrat clos et solde");
        } finally {
            data.remove(c.id);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104g_insuffisant", timeoutTicks = 20)
    public static void p104_7_fonds_insuffisants_dette_puis_reglement(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer pv = s.player("gtfpv", "Joran", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gtfcl", "Brunelle", new BlockPos(3, 1, 1));
        balance(pv, 0);
        balance(cl, 30);
        long t0 = base();
        Contract c = offer(pv, cl, t0, 3, 25, t0 - H);
        ContractData data = ContractData.get(s.server);
        try {
            Gt.check(ContractService.act(cl, c.id, "accept", t0 - H) == null, "acceptation");
            ContractEngine.Bank bank = ContractService.bank(s.server);
            ContractEngine.settle(c, t0 + 2 * H, bank);
            Gt.equal(50, c.billed, "2 h facturees");
            Gt.equal(30, c.paid, "paye au plus le solde");
            Gt.equal(0, EconomyApi.balance(cl), "solde du client a zero, jamais negatif");
            Gt.equal(20, c.owed(), "dette affichee");
            Gt.equal("unpaid", c.shownState(), "contrat impaye");
            Gt.equal("insufficient", ContractService.act(cl, c.id, "pay", t0 + 2 * H + M), "regler sans fonds : refus clair");
            balance(cl, 200);
            Gt.check(ContractService.act(cl, c.id, "pay", t0 + 2 * H + 2 * M) == null, "reglement de la dette");
            Gt.equal(0, c.owed(), "dette soldee");
            Gt.equal(50, EconomyApi.balance(pv), "prestataire paye de tout le facture");
            ContractEngine.settle(c, t0 + 3 * H, bank);
            Gt.equal("finished", c.shownState(), "termine a la fin");
            Gt.equal(75, EconomyApi.balance(pv), "3 h payees");
        } finally {
            data.remove(c.id);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104g_sans_acceptation", timeoutTicks = 20)
    public static void p104_7_rien_sans_acceptation(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer pv = s.player("gtnpv", "Ansel", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gtncl", "Ida", new BlockPos(3, 1, 1));
        balance(pv, 0);
        balance(cl, 1000);
        long t0 = base();
        Contract c = offer(pv, cl, t0, 2, 100, t0 - H);
        ContractData data = ContractData.get(s.server);
        try {
            ContractEngine.Bank bank = ContractService.bank(s.server);
            for (long t = t0; t < t0 + 2 * H; t += 10 * M) {
                ContractEngine.settle(c, t, bank);
            }
            Gt.equal(1000, EconomyApi.balance(cl), "rien preleve sans acceptation");
            ContractEngine.settle(c, t0 + 2 * H, bank);
            Gt.check(c.state == Contract.State.EXPIRED, "expire a la fin sans reponse");
            Gt.check(ContractService.act(cl, c.id, "accept", t0 + 3 * H) != null, "acceptation refusee apres la fin");
            Gt.equal(1000, EconomyApi.balance(cl), "toujours rien preleve");
            Gt.equal(0, EconomyApi.balance(pv), "rien verse");
            //brouillon : jamais vu ni accepte par le client
            ContractService.Saved d = ContractService.save(pv, 0, cl.getGameProfile().getName(), "Brouillon", "", fields(t0), fields(t0 + H), 10,
                  false, t0 - H);
            Gt.check(d.error() == null, "brouillon refuse : " + d.error());
            Gt.equal("not_allowed", ContractService.act(cl, d.id(), "accept", t0 - H), "un brouillon n'est pas visible du client");
            Gt.check(ContractService.act(pv, d.id(), "delete", t0 - H) == null, "brouillon supprime");
            Gt.check(data.get(d.id()) == null, "brouillon retire du registre");
            Gt.equal("client_self", ContractService.save(pv, 0, pv.getGameProfile().getName(), "Moi", "", fields(t0), fields(t0 + H), 10, false,
                  t0).error(), "on n'est pas son propre client");
        } finally {
            data.remove(c.id);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104g_hors_ligne", timeoutTicks = 20)
    public static void p104_7_hors_ligne_et_redemarrage(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer pv = s.player("gthpv", "Aubin", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gthcl", "Laure", new BlockPos(3, 1, 1));
        balance(pv, 0);
        balance(cl, 1000);
        UUID pvId = pv.getUUID(), clId = cl.getUUID();
        String pvAcc = pv.getGameProfile().getName(), clAcc = cl.getGameProfile().getName();
        long t0 = base();
        Contract c = offer(pv, cl, t0, 5, 10, t0 - H);
        ContractData data = ContractData.get(s.server);
        try {
            Gt.check(ContractService.act(cl, c.id, "accept", t0 - H) == null, "acceptation");
            Gt.disconnect(s.server, clId);
            ContractEngine.Bank bank = ContractService.bank(s.server);
            for (long t = t0; t <= t0 + 3 * H; t += 20 * M) {
                ContractEngine.settle(c, t, bank);
            }
            Gt.equal(30, c.billed, "facture pendant l'absence du client");
            Gt.equal(0, c.paid, "rien preleve hors ligne");
            //redemarrage : le registre relu garde tout
            ContractData relu = Gt.reload(data);
            Contract r = relu.get(c.id);
            Gt.check(r != null && r.save().equals(c.save()), "contrat relu identique");
            //le client revient, le prestataire s'en va : preleve et mis de cote
            Gt.disconnect(s.server, pvId);
            ServerPlayer back = s.reconnect(clId, clAcc, new BlockPos(3, 1, 1));
            ContractService.login(back, t0 + 3 * H + 5 * M);
            Gt.equal(30, c.paid, "rattrape a la connexion du client");
            Gt.equal(970, EconomyApi.balance(back), "client preleve a son retour");
            Gt.equal(30, c.held, "mis de cote pour le prestataire absent");
            ServerPlayer pvBack = s.reconnect(pvId, pvAcc, new BlockPos(1, 1, 1));
            ContractService.login(pvBack, t0 + 3 * H + 6 * M);
            Gt.equal(0, c.held, "verse au retour du prestataire");
            Gt.check(EconomyApi.balance(pvBack) >= 30, "prestataire paye a sa connexion");
        } finally {
            data.remove(c.id);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104g_facture", timeoutTicks = 20)
    public static void p104_7_facture_imprimee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer pv = s.player("gtipv", "Ernest", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gticl", "Odile", new BlockPos(3, 1, 1));
        balance(cl, 1000);
        long t0 = base();
        Contract c = offer(pv, cl, t0, 2, 40, t0 - H);
        ContractData data = ContractData.get(s.server);
        try {
            Gt.equal("state", ContractService.act(pv, c.id, "print", t0 - H), "pas de facture avant l'acceptation");
            Gt.check(ContractService.act(cl, c.id, "accept", t0 - H) == null, "acceptation");
            ContractEngine.settle(c, t0, ContractService.bank(s.server));
            pv.getInventory().clearContent();
            Gt.equal("paper", ContractService.act(pv, c.id, "print", t0 - H), "sans papier : refus");
            pv.getInventory().add(new ItemStack(Items.PAPER, 2));
            Gt.check(ContractService.act(pv, c.id, "print", t0 - H) == null, "impression");
            int paper = 0;
            ItemStack invoice = ItemStack.EMPTY;
            for (int i = 0; i < pv.getInventory().getContainerSize(); i++) {
                ItemStack st = pv.getInventory().getItem(i);
                if (st.is(Items.PAPER)) {
                    paper += st.getCount();
                } else if (st.is(ContractItems.INVOICE.get())) {
                    invoice = st;
                }
            }
            Gt.equal(1, paper, "une feuille consommee");
            Gt.check(!invoice.isEmpty(), "facture dans l'inventaire");
            Gt.equal(c.id, InvoiceItem.data(invoice).getInt("id"), "numero de la facture");
            Gt.equal(80, InvoiceItem.data(invoice).getLong("planned"), "montant prevu sur la facture");
        } finally {
            data.remove(c.id);
        }
        s.done();
    }
}
