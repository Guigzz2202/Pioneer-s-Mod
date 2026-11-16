package fr.pioneers.gametest;

import com.mojang.authlib.GameProfile;
import fr.pioneers.logistics.caravan.CaravanData;
import fr.pioneers.logistics.caravan.CaravanRules;
import fr.pioneers.logistics.caravan.Caravans;
import fr.pioneers.logistics.caravan.GeneratedCaravan;
import fr.pioneers.logistics.caravan.PiCaravan;
import fr.pioneers.logistics.caravan.RelayTile;
import fr.pioneers.logistics.caravan.RouteSheetItem;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.WaypointTeleport;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.contract.Contract;
import fr.pioneers.society.economy.contract.ContractData;
import fr.pioneers.society.economy.contract.ContractEngine;
import fr.pioneers.society.economy.contract.ContractRules;
import fr.pioneers.society.economy.contract.ContractService;
import fr.pioneers.society.economy.mail.Letters;
import fr.pioneers.society.economy.mail.MailData;
import fr.pioneers.society.economy.mail.MailRules;
import fr.pioneers.society.economy.mail.MailService;
import fr.pioneers.society.economy.mail.MailboxTile;
import fr.pioneers.society.economy.mail.PiMail;
import fr.pioneers.guide.JourneySteps;
import fr.pioneers.guide.party.PartyData;
import fr.pioneers.guide.party.PartyService;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 29 (GameTests, retours de Guillaume du 09/11 : « multijoueur ») : contrat A L'HEURE en temps simule (reserve,
 * chaque heure payee joueurs hors ligne, rattrapage apres un « arret du serveur » sans doubler, suspension, reprise) et au MONTANT TOTAL ;
 * LETTRE recue dans la boite aux lettres (virement, contrat d'un joueur hors ligne, ouverture = lu, boite pleine, proprietaire) ; PARTIE du
 * guide (union a l'entree, etape partagee, depart sans rien perdre, absent rattrape a la connexion, refus clairs) ; marche d'une CARAVANE
 * jusqu'au relais sans secours ; teleportation des ADMINISTRATEURS aux waypoints (droit, place sure). Chaque test retire ce qu'il a cree.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot29Tests {

    private static final long H = ContractRules.HOUR, M = ContractRules.MINUTE;

    private PatchP104Lot29Tests() {
    }

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

    // ------------------------------------------------------------------ contrats
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104l29_horaire", timeoutTicks = 20)
    public static void p104_29_contrat_horaire_rattrapage_sans_doublon(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer pv = s.player("gthpv", "Armand", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gthcl", "Berthe", new BlockPos(3, 1, 1));
        UUID pvId = pv.getUUID(), clId = cl.getUUID();
        String pvAcc = pv.getGameProfile().getName(), clAcc = cl.getGameProfile().getName();
        balance(pv, 0);
        balance(cl, 1000);
        long t0 = base();
        ContractService.Saved sv = ContractService.save(pv, 0, clAcc, "Garde du relais", "", fields(t0), fields(t0 + 30 * H), true, 10, true, t0 - H);
        Gt.check(sv.error() == null, "contrat a l'heure refuse : " + sv.error());
        ContractData data = ContractData.get(s.server);
        Contract c = data.get(sv.id());
        try {
            Gt.check(c.hourly() && c.rate == 10, "formule a l'heure, 10 / h");
            Gt.check(ContractService.act(cl, c.id, "accept", t0 - H) == null, "acceptation");
            Gt.equal(240, c.reserve, "reserve de 24 h versee");
            Gt.equal(760, EconomyApi.balance(cl), "client : 1000 - 240");
            Gt.disconnect(s.server, clId);
            Gt.disconnect(s.server, pvId);
            ContractEngine.Bank bank = ContractService.bank(s.server);
            ContractEngine.settle(c, t0 + H, bank);
            Gt.equal(1, c.hours, "1 h payee, les deux joueurs hors ligne");
            //arret du serveur de 1 h a 6 h : registre relu, puis rattrapage
            ContractData relu = Gt.reload(data);
            Contract r = relu.get(c.id);
            Gt.check(r != null && r.save().equals(c.save()), "contrat relu identique");
            ContractEngine.settle(r, t0 + 6 * H, bank);
            ContractEngine.settle(r, t0 + 6 * H, bank);
            ContractEngine.settle(r, t0 + 6 * H + 30 * M, bank);
            Gt.equal(6, r.hours, "rattrapage : 6 heures, pas une de plus");
            Gt.equal(60, r.paid, "60 payes depuis la reserve");
            Gt.equal(60, r.held, "de cote pour le prestataire absent");
            ServerPlayer pvBack = s.reconnect(pvId, pvAcc, new BlockPos(1, 1, 1));
            ContractService.login(pvBack, t0 + 6 * H + 31 * M);
            ContractEngine.settle(r, t0 + 6 * H + 31 * M, bank);
            Gt.equal(0, r.held, "verse a la connexion du prestataire");
            Gt.check(EconomyApi.balance(pvBack) >= 60, "prestataire paye");
            //suspension : reserve videe par un client absent jusqu'au bout de ses 24 h
            ContractEngine.settle(r, t0 + 25 * H, bank);
            Gt.check(r.state == Contract.State.SUSPENDED, "reserve epuisee : suspendu (" + r.state + ")");
            Gt.equal(25, r.hours, "24 heures payees, la 25e facturee sans reserve");
            Gt.equal(10, r.owed(), "dette : la 25e heure");
            ContractEngine.settle(r, t0 + 27 * H, bank);
            Gt.equal(25, r.hours, "suspendu : les heures ne comptent plus");
            ServerPlayer clBack = s.reconnect(clId, clAcc, new BlockPos(3, 1, 1));
            balance(clBack, 500);
            Gt.check(ContractEngine.resume(r, clId, t0 + 27 * H, bank) == null, "reprise par le client");
            Gt.check(r.state == Contract.State.RUNNING && r.owed() == 0, "en cours, rien du");
            Gt.equal(t0 + 28 * H, r.nextHourAt(), "prochaine heure une heure apres la reprise");
        } finally {
            data.remove(c.id);
            MailData.get(s.server).forget(pvId);
            MailData.get(s.server).forget(clId);
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104l29_total", timeoutTicks = 20)
    public static void p104_29_contrat_montant_total(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer pv = s.player("gttpv", "Celeste", new BlockPos(1, 1, 1));
        ServerPlayer cl = s.player("gttcl", "Damien", new BlockPos(3, 1, 1));
        balance(pv, 0);
        balance(cl, 1000);
        long t0 = base();
        ContractService.Saved sv = ContractService.save(pv, 0, cl.getGameProfile().getName(), "Charpente", "", fields(t0), fields(t0 + 3 * H), false,
              300, true, t0 - H);
        Gt.check(sv.error() == null, "contrat au montant total refuse : " + sv.error());
        Gt.equal("amount", ContractService.save(pv, 0, cl.getGameProfile().getName(), "Rien", "", fields(t0), fields(t0 + H), false, 0, false, t0)
              .error(), "montant total nul refuse");
        ContractData data = ContractData.get(s.server);
        Contract c = data.get(sv.id());
        try {
            Gt.check(!c.hourly() && c.total == 300 && c.rate == 0, "formule au montant total");
            Gt.equal(300, c.plannedTotal(), "prevu = le montant");
            Gt.check(ContractService.act(cl, c.id, "accept", t0 - H) == null, "acceptation");
            ContractEngine.Bank bank = ContractService.bank(s.server);
            ContractEngine.settle(c, t0 + H, bank);
            Gt.equal(100, c.paid, "une heure sur trois : un tiers");
            ContractEngine.settle(c, t0 + 3 * H, bank);
            Gt.equal(300, c.paid, "tout le montant au terme");
            Gt.check(c.state == Contract.State.FINISHED, "termine");
            //contrat d'avant le lot 29 (sans formule) : relu au montant total calcule au taux d'origine
            CompoundTag old = c.save();
            old.remove("pay");
            old.putLong("total", 0);
            old.putLong("rate", 25);
            Contract legacy = Contract.load(old);
            Gt.check(!legacy.hourly() && legacy.plannedTotal() == 75, "ancien contrat : 3 h x 25 = 75, au montant total");
        } finally {
            data.remove(c.id);
            MailData.get(s.server).forget(pv.getUUID());
            MailData.get(s.server).forget(cl.getUUID());
        }
        s.done();
    }

    // ------------------------------------------------------------------ boite aux lettres
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104l29_lettre", timeoutTicks = 20)
    public static void p104_29_lettre_recue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer from = s.player("gtlfr", "Eloi", new BlockPos(1, 1, 1));
        ServerPlayer to = s.player("gtlto", "Fanny", new BlockPos(3, 1, 1));
        UUID toId = to.getUUID();
        String toAcc = to.getGameProfile().getName();
        balance(from, 500);
        balance(to, 0);
        MailData data = MailData.get(s.server);
        ContractData contracts = ContractData.get(s.server);
        int id = 0;
        try {
            Gt.check(EconomyApi.transfer(from, to, 120).ok(), "virement");
            MailData.Box box = data.box(toId);
            Gt.equal(1, box.letters(), "une lettre dans la boite du beneficiaire");
            Gt.equal(1, box.unread, "non lue");
            CompoundTag t = Letters.data(box.items.getItem(0));
            Gt.check(t != null && "transfer".equals(t.getString("k")) && t.getLong("a") == 120 && "Eloi".equals(t.getString("f")),
                  "lettre : virement de 120 par Eloi");
            //contrat propose puis retire pendant que le client est hors ligne : la lettre l'attend
            long t0 = base();
            ContractService.Saved sv = ContractService.save(from, 0, toAcc, "Moisson", "", fields(t0), fields(t0 + 2 * H), false, 80, true, t0 - H);
            Gt.check(sv.error() == null, "contrat propose");
            id = sv.id();
            Gt.disconnect(s.server, toId);
            Gt.check(ContractService.act(from, id, "withdraw", t0 - H) == null, "proposition retiree");
            Gt.equal(3, box.letters(), "virement, contrat propose, proposition retiree (client hors ligne)");
            Gt.equal(3, box.unread, "trois non lues");
            //la boite : posee par son proprietaire, ouverte = lue ; un autre joueur ne l'ouvre pas
            ServerPlayer back = s.reconnect(toId, toAcc, new BlockPos(3, 1, 1));
            BlockPos pos = new BlockPos(5, 1, 5);
            h.setBlock(pos, PiMail.MAILBOX.getBlock().defaultBlockState());
            MailboxTile tile = (MailboxTile) h.getBlockEntity(pos);
            tile.claim(back);
            Gt.check(toId.equals(tile.owner()), "proprietaire = celui qui la pose");
            MailService.open(back, h.absolutePos(pos));
            Gt.equal(0, box.unread, "ouverte : plus rien de non lu");
            back.closeContainer();
            //boite pleine : la lettre attend une case libre
            for (int i = 0; i < MailRules.SLOTS; i++) {
                if (box.items.getItem(i).isEmpty()) {
                    box.items.setItem(i, new ItemStack(Items.STONE));
                }
            }
            MailService.deliver(s.server, toId, Letters.transfer("Eloi", 5, t0, ContractService.zone()));
            Gt.equal(1, box.pending.size(), "boite pleine : une lettre en attente");
            box.items.setItem(MailRules.SLOTS - 1, ItemStack.EMPTY);
            box.flush();
            Gt.equal(0, box.pending.size(), "case liberee : la lettre entre");
            Gt.check(box.items.getItem(MailRules.SLOTS - 1).is(PiMail.LETTER.get()), "la lettre est dans la case liberee");
            //sauvegarde
            MailData relu = Gt.reload(data);
            Gt.equal(4, relu.box(toId).letters(), "boite relue : 4 lettres");
        } finally {
            if (id > 0) {
                contracts.remove(id);
            }
            data.forget(from.getUUID());
            data.forget(toId);
        }
        s.done();
    }

    // ------------------------------------------------------------------ partie du guide
    static void earn(ServerPlayer p, String id) {
        Advancement adv = p.getServer().getAdvancements().getAdvancement(ResourceLocation.parse(id));
        Gt.check(adv != null, "progres introuvable : " + id);
        for (String c : p.getAdvancements().getOrStartProgress(adv).getRemainingCriteria()) {
            p.getAdvancements().award(adv, c);
        }
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104l29_partie", timeoutTicks = 20)
    public static void p104_29_partie_progression_commune(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer a = s.player("gtpa", "Gaspard", new BlockPos(1, 1, 1));
        ServerPlayer b = s.player("gtpb", "Hortense", new BlockPos(3, 1, 1));
        UUID bId = b.getUUID();
        String bAcc = b.getGameProfile().getName();
        String s1 = JourneySteps.ALL.get(0).advancement(), s2 = JourneySteps.ALL.get(1).advancement(), s3 = JourneySteps.ALL.get(2).advancement(),
              s4 = JourneySteps.ALL.get(3).advancement(), s5 = JourneySteps.ALL.get(4).advancement();
        PartyData data = PartyData.get(s.server);
        long now = System.currentTimeMillis();
        try {
            earn(a, s1);
            earn(b, s2);
            Gt.equal("self", PartyService.invite(a, a, now), "pas d'invitation a soi-meme");
            Gt.equal("unknown_player", PartyService.invite(a, null, now), "joueur absent : refus clair");
            Gt.equal("no_invite", PartyService.accept(b, now), "rien a accepter");
            Gt.check(PartyService.invite(a, b, now) == null, "invitation");
            Gt.check(PartyService.accept(b, now) == null, "acceptation : la partie se forme");
            PartyData.Party party = data.partyOf(a.getUUID());
            Gt.check(party != null && party == data.partyOf(bId) && party.members.size() == 2, "partie de deux membres");
            Gt.check(PartyService.done(a).containsAll(Set.of(s1, s2)) && PartyService.done(b).containsAll(Set.of(s1, s2)), "union a l'entree");
            earn(a, s3);
            Gt.check(PartyService.done(b).contains(s3), "etape faite par un membre : validee pour l'autre");
            //absent : rattrape a sa connexion
            Gt.disconnect(s.server, bId);
            earn(a, s4);
            ServerPlayer back = s.reconnect(bId, bAcc, new BlockPos(3, 1, 1));
            PartyService.sync(back);
            Gt.check(PartyService.done(back).contains(s4), "membre absent : etape recue a sa connexion");
            //depart : on garde tout ; la partie a deux se dissout
            Gt.check(PartyService.leave(back) == null, "depart");
            Gt.check(data.partyOf(a.getUUID()) == null && data.partyOf(bId) == null, "partie dissoute (un seul membre restant)");
            Gt.check(PartyService.done(back).containsAll(Set.of(s1, s2, s3, s4)), "le partant garde sa progression");
            earn(a, s5);
            Gt.check(!PartyService.done(back).contains(s5), "hors de la partie : plus de partage");
            Gt.equal("not_in_party", PartyService.leave(back), "plus de partie a quitter");
            //invitation expiree
            data.invite(bId, new PartyData.Invite(a.getUUID(), "Gaspard", now - (PartyService.INVITE_MINUTES + 1) * 60_000L));
            Gt.equal("expired", PartyService.accept(back, now), "invitation expiree");
            PartyData relu = Gt.reload(data);
            Gt.check(relu.partyOf(a.getUUID()) == null, "registre relu");
        } finally {
            for (PartyData.Party p : java.util.List.copyOf(data.parties())) {
                if (p.members.containsKey(a.getUUID()) || p.members.containsKey(bId)) {
                    data.remove(p);
                }
            }
            data.takeInvite(bId);
        }
        s.done();
    }

    // ------------------------------------------------------------------ caravane
    @GameTest(template = Gt.LARGE, batch = "pioneers_p104l29_caravane", timeoutTicks = 400)
    public static void p104_29_caravane_marche_au_pas(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(Math.abs(CaravanRules.walkSpeed(CaravanRules.navSpeed(GeneratedCaravan.PACE, 0.225), 0.225) - GeneratedCaravan.PACE) < 1e-6,
              "loi de vitesse : un cheval moyen marche au pas de la caravane");
        FakePlayer owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("caravanier_l29".getBytes()), "caravanier_l29"));
        BlockPos pa = new BlockPos(16, 1, 6), pb = new BlockPos(16, 1, 28);
        h.setBlock(pa, PiCaravan.RELAY.getBlock().defaultBlockState());
        h.setBlock(pb, PiCaravan.RELAY.getBlock().defaultBlockState());
        ((RelayTile) h.getBlockEntity(pa)).placedBy(owner);
        ((RelayTile) h.getBlockEntity(pb)).placedBy(owner);
        ItemStack sheet = new ItemStack(PiCaravan.ROUTE_SHEET.get());
        RouteSheetItem.addStop(owner, sheet, (RelayTile) h.getBlockEntity(pa));
        RouteSheetItem.addStop(owner, sheet, (RelayTile) h.getBlockEntity(pb));
        //1.0.6 : le sol du gabarit est en y = 1 : le cheval nait au-dessus (en y = 1 il etait dans la pierre)
        Horse horse = h.spawn(EntityType.HORSE, new BlockPos(16, 2, 8));
        horse.setTamed(true);
        CaravanData d = PiCaravan.get(horse);
        Gt.check(Caravans.equip(null, horse, d, new ItemStack(PiCaravan.PACK_SADDLE.get())), "bat pose");
        Gt.check(Caravans.assign(owner, horse, d, sheet), "caravane lancee");
        d.next = 1;
        Vec3 stand = ((RelayTile) h.getBlockEntity(pb)).standPoint();
        long start = h.getLevel().getGameTime();
        h.succeedWhen(() -> {
            Gt.check(d.rescues == 0, "aucune teleportation de secours");
            Gt.check(d.state == CaravanData.STOP, "arrivee au relais (" + d.dist + " blocs restants)");
            long seconds = (h.getLevel().getGameTime() - start) / 20;
            Gt.check(seconds <= 15, "au pas : " + seconds + " s pour ~22 blocs");
            Gt.check(horse.position().distanceTo(stand) < 4, "au point d'attache du relais");
            Caravans.stop(owner, horse, d);
            s.done();
        });
    }

    // ------------------------------------------------------------------ waypoints
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104l29_waypoint", timeoutTicks = 20)
    public static void p104_29_waypoint_administrateur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gtwp", "Ines", new BlockPos(1, 1, 1));
        Gt.check(!WaypointTeleport.admin(p), "joueur ordinaire : pas de teleportation (regle d'avant)");
        Gt.op(s.server, p);   // 1.0.6 : niveau 4 (op() donne le niveau 0 du serveur de GameTests)
        try {
            Gt.check(WaypointTeleport.admin(p), "operateur : administrateur, en survie aussi");
        } finally {
            s.server.getPlayerList().deop(p.getGameProfile());
        }
        //place sure : pieds et tete libres au-dessus d'un sol, au plus pres de la hauteur du waypoint
        BlockPos floor = h.absolutePos(new BlockPos(4, 0, 4));
        h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE.defaultBlockState());
        BlockPos spot = WaypointTeleport.safeSpot(h.getLevel(), floor.getX(), floor.getY() + 1, floor.getZ());
        Gt.equal(floor.getY() + 2, spot.getY(), "waypoint dans un bloc : place juste au-dessus");
        s.done();
    }
}
