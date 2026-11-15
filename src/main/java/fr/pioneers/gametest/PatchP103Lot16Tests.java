package fr.pioneers.gametest;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.territory.Access;
import fr.pioneers.clan.territory.ClanAccess;
import fr.pioneers.clan.territory.ClanTerritory;
import fr.pioneers.clan.territory.TerritoryRules;
import fr.pioneers.clan.territory.ZonePerms;
import fr.pioneers.economy.EconomyApi;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.3-beta, lot 16 (GameTests) : territoire du clan.
 * <ul>
 *   <li>prix progressif des claims achetes (configuration du serveur : 75, 170, 340, 1 200, 3 805) ;</li>
 *   <li>achat au tresor : debit au prix calcule par le serveur, budget + 1, libelle « Achat de territoire (n° k) » au journal, achat perime
 *   (prix vu par le client depasse) refuse, budget garde apres l'abandon d'un chunk (aucun remboursement), sauvegarde relue ;</li>
 *   <li>tresor insuffisant : refus, rien ne bouge ;</li>
 *   <li>matrice par defaut = ancienne regle (zone Membres fermee, zone Ouverte : portes et vehicules pour tous, conteneurs et machines pour
 *   les allies) ;</li>
 *   <li>un visiteur de chaque categorie (allie, neutre, etranger, ennemi) autorise ou refuse selon la matrice reglee, sur un vrai claim.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot16Tests {

    private PatchP103Lot16Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_16_prix", timeoutTicks = 20)
    public static void p103_16_prix_progressif(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        TerritoryRules.Purchase p = ClanTerritory.purchase();
        //1.0.5-beta (lot 13, Guillaume : « prix fixe 100 Shining ») : plus de progression
        for (int i = 1; i <= p.max(); i++) {
            Gt.equal(100L, TerritoryRules.claimPrice(p, i), "prix fixe du claim achete no " + i);
        }
        Gt.equal(500L, TerritoryRules.claimsPrice(p, 7, 5), "cinq claims de plus");
        Gt.equal(200, p.max(), "plafond d'achat d'un clan");
        Gt.equal(50, p.soloMax(), "plafond d'achat d'un Solitaire");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_16_achat", timeoutTicks = 40)
    public static void p103_16_achat_debite_et_augmente_le_budget(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee : l'achat de claims en a besoin");
        ServerPlayer rep = s.player("gtbuy", "Acheteuse", new BlockPos(4, 2, 4));
        Clan c = s.clan("GtAchat" + Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff), rep);
        long now = fr.pioneers.clan.core.ClanClock.now();
        c.treasury = 1000;
        int before = ClanTerritory.budget(c, now);
        Gt.equal(null, ClanTerritory.buy(rep, 1, 0), "achat d'un claim");
        Gt.equal(900, c.treasury, "tresor debite de 100 (prix fixe, 1.0.5-beta lot 13)");
        Gt.equal(1, c.boughtClaims, "claims achetes");
        Gt.equal(before + 1, ClanTerritory.budget(c, now), "budget + 1");
        CompoundTag last = c.journal.peekLast();
        Gt.check(last != null && "clan.pioneers.log.buy_claims".equals(last.getString("k")), "ligne de journal de l'achat");
        boolean labelled = false;
        for (CompoundTag l : c.journal) {
            labelled |= "clan.pioneers.log.treasury_out".equals(l.getString("k"))
                        && "#clan.pioneers.reason.territory|1".equals(l.getList("a", net.minecraft.nbt.Tag.TAG_STRING).getString(0));
        }
        Gt.check(labelled, "libelle « Achat de territoire (n° 1) » dans l'historique du tresor");
        //le client avait vu 0 achat : le prix a change, refus
        Gt.equal("clan.pioneers.territory.buy.stale", ClanTerritory.buy(rep, 1, 0), "achat perime refuse");
        //deux de plus d'un coup : 2 x 100
        Gt.equal(null, ClanTerritory.buy(rep, 2, 1), "achat de deux claims");
        Gt.equal(700, c.treasury, "tresor debite de 200");
        Gt.equal(3, c.boughtClaims, "trois claims achetes");
        //un chunk reclame puis rendu : rien n'est rembourse, le budget achete reste
        BlockPos at = h.absolutePos(new BlockPos(4, 2, 4));
        s.claim(c, at);
        ClanTerritory.drop(s.server, c, ClanApi.dim(s.level), Gt.chunk(at), true);
        Gt.equal(3, c.boughtClaims, "achetes gardes apres l'abandon");
        Gt.equal(700, c.treasury, "aucun remboursement");
        //sauvegarde : compteur et matrice relus
        c.zonePerms = c.zonePerms.with(Access.Zone.OPEN, ZonePerms.Visitor.STRANGER, ZonePerms.Group.CROPS.bit());
        Clan back = Clan.load(c.save());
        Gt.equal(3, back.boughtClaims, "claims achetes relus");
        Gt.check(back.zonePerms.equals(c.zonePerms), "permissions des visiteurs relues");
        CompoundTag old = new CompoundTag();
        old.putUUID("id", java.util.UUID.randomUUID());
        Gt.check(Clan.load(old).zonePerms.isDefault() && Clan.load(old).boughtClaims == 0, "ancien clan sans reglage : defauts");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_16_tresor", timeoutTicks = 20)
    public static void p103_16_tresor_insuffisant(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer rep = s.player("gtpoor", "Fauchee", new BlockPos(4, 2, 4));
        Clan c = s.clan("GtPauvre" + Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff), rep);
        c.treasury = 149;
        Gt.equal("clan.pioneers.territory.buy.treasury", ClanTerritory.buy(rep, 1, 0), "tresor insuffisant");
        Gt.equal(149, c.treasury, "tresor intact");
        Gt.equal(0, c.boughtClaims, "rien d'achete");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_16_defauts", timeoutTicks = 20)
    public static void p103_16_matrice_par_defaut(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (ZonePerms.Visitor v : ZonePerms.Visitor.values()) {
            Gt.equal(0, ZonePerms.DEFAULTS.mask(Access.Zone.MEMBERS, v), "zone Membres fermee : " + v);
            Gt.equal(0, ZonePerms.DEFAULTS.mask(Access.Zone.PRIVATE, v), "zone Privee fermee : " + v);
            Access.Case c = new Access.Case(v == ZonePerms.Visitor.ENEMY ? ClanApi.Relation.WAR : v == ZonePerms.Visitor.ALLY ? ClanApi.Relation.ALLY
                  : ClanApi.Relation.NEUTRAL, Access.Zone.OPEN, 0, 0, p -> false, false, false, false, v, ZonePerms.DEFAULTS);
            boolean ally = v == ZonePerms.Visitor.ALLY;
            for (ClanApi.Action a : ClanApi.Action.values()) {
                boolean old = switch (a) {
                    case INTERACT, VEHICLE -> true;
                    case CONTAINER, MACHINE -> ally;
                    default -> false;
                };
                Gt.equal(old, Access.decide(c, a), "zone Ouverte, " + v + ", " + a);
            }
        }
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_16_visiteurs", timeoutTicks = 60)
    public static void p103_16_visiteurs_selon_la_matrice(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos stand = new BlockPos(4, 2, 4);
        String tag = Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff);
        ServerPlayer owner = s.player("gtown", "Proprio", stand), allyRep = s.player("gtall", "Alliee", stand);
        ServerPlayer neutral = s.player("gtneu", "Neutre", stand), stranger = s.player("gtstr", "Etrangere", stand);
        ServerPlayer enemy = s.player("gtene", "Ennemie", stand);
        Clan a = s.clan("GtVisite" + tag, owner), b = s.clan("GtAmi" + tag, allyRep), n = s.clan("GtNeutre" + tag, neutral);
        Clan e = s.clan("GtEnnemi" + tag, enemy);
        s.ally(a, owner, b, allyRep);
        s.war(e, a);
        BlockPos at = h.absolutePos(stand);
        s.claim(a, at);
        Gt.check(n != null, "clan neutre");
        ClanApi.Action C = ClanApi.Action.CONTAINER, I = ClanApi.Action.INTERACT, F = ClanApi.Action.FARMLAND;
        //zone Membres par defaut : personne
        for (ServerPlayer p : new ServerPlayer[]{allyRep, neutral, stranger, enemy}) {
            Gt.check(!ClanAccess.canModify(s.server, s.level, p.getUUID(), at, I), "zone Membres fermee par defaut : " + p.getGameProfile().getName());
        }
        //conteneurs ouverts aux seuls etrangers, cultures aux seuls allies
        a.zonePerms = a.zonePerms.with(Access.Zone.MEMBERS, ZonePerms.Visitor.STRANGER, ZonePerms.Group.CONTAINERS.bit())
              .with(Access.Zone.MEMBERS, ZonePerms.Visitor.ALLY, ZonePerms.Group.CROPS.bit());
        Gt.check(ClanAccess.canModify(s.server, s.level, stranger.getUUID(), at, C), "etranger : conteneurs ouverts");
        Gt.check(!ClanAccess.canModify(s.server, s.level, neutral.getUUID(), at, C), "neutre : conteneurs fermes");
        Gt.check(!ClanAccess.canModify(s.server, s.level, allyRep.getUUID(), at, C), "allie : conteneurs fermes");
        Gt.check(ClanAccess.canModify(s.server, s.level, allyRep.getUUID(), at, F), "allie : cultures ouvertes");
        Gt.check(!ClanAccess.canModify(s.server, s.level, enemy.getUUID(), at, C), "ennemi : conteneurs fermes");
        //zone Ouverte : ennemis fermes, neutres ouverts (portes)
        a.claim(ClanApi.dim(s.level), Gt.chunk(at)).zone = Clan.Zone.OPEN;
        a.zonePerms = a.zonePerms.with(Access.Zone.OPEN, ZonePerms.Visitor.ENEMY, 0);
        Gt.check(ClanAccess.canModify(s.server, s.level, neutral.getUUID(), at, I), "neutre : portes en zone Ouverte");
        Gt.check(!ClanAccess.canModify(s.server, s.level, enemy.getUUID(), at, I), "ennemi : zone Ouverte fermee");
        Gt.check(ClanAccess.canModify(s.server, s.level, allyRep.getUUID(), at, C), "allie : conteneurs en zone Ouverte (defaut)");
        //le message de refus dit la categorie
        Gt.check(ClanAccess.denial(s.server, s.level, enemy.getUUID(), at, I).getString().length() > 0, "message de refus");
        Gt.check(ClanAccess.canModify(s.server, s.level, owner.getUUID(), at, ClanApi.Action.BREAK), "le proprietaire bat toujours");
        s.done();
    }
}
