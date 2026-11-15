package fr.pioneers.gametest;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanDefs;
import fr.pioneers.clan.core.ClanRoles;
import fr.pioneers.clan.territory.ClanTerritory;
import fr.pioneers.clan.territory.TerritoryRules;
import fr.pioneers.clan.treasury.ClanFinance;
import fr.pioneers.clan.vote.ClanGov;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileEvents;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 8 (GameTests) : identite a la mort (le joueur mort est RETIRE du monde 20 ticks apres sa mort, ses capacites sont
 * invalidees : le profil reel reste lisible et l'initiation ne se rouvre jamais), changement de dimension et retour de l'End ; claims
 * charges d'office (ticket pose au claim, un seul, retire a l'abandon) ; roles : Tresorier (depense, pas de claim), Arpenteur (claim, pas de
 * depense), membre (rien), delegation des decisions du Representant (cotisation fixee en Empire par le Tresorier), hierarchie de la modification
 * des roles (on ne donne que ce qu'on a, a un role inferieur) ; migration des clans d'avant.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot8Tests {

    private static final BlockPos AT = new BlockPos(4, 2, 4);

    private PatchP104Lot8Tests() {
    }

    // ================================================================== identite a la mort
    /**
     * Le bug de Guillaume : mort, puis ecran de mort (le serveur retire le joueur du monde apres 20 ticks : {@code Entity.remove} invalide ses
     * capacites). Avant : profil de repli VIDE et partage, l'initiation se rouvrait. Ici : profil reel lisible, aucune initiation, un gain
     * pendant l'ecran de mort est garde, et tout suit a la reapparition.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_8_mort", timeoutTicks = 80)
    public static void p104_8_identite_mort(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String skill = PioneerTests.skill();
        ServerPlayer p = s.player("gt8death", "Revenante", AT);
        PioneerTests.fill(p, skill);
        Gt.check(!ProfileEvents.needsInitiation(p), "un pionnier initie ne doit pas etre initie");
        p.kill();
        Gt.check(p.isDeadOrDying(), "le joueur n'est pas mort");
        //ce que fait LivingEntity.tickDeath 20 ticks apres la mort (le joueur reste dans la liste jusqu'a sa reapparition)
        p.remove(Entity.RemovalReason.KILLED);
        Gt.check(p.isRemoved(), "le joueur mort n'a pas ete retire du monde");
        PioneerProfile dead = PioneerCap.find(p);
        Gt.check(dead != null, "profil REEL illisible pendant l'ecran de mort (capacite invalidee)");
        Gt.check(PioneerCap.get(p).initiated, "le profil lu pendant l'ecran de mort n'est pas initie (cause du bug)");
        Gt.check(!ProfileEvents.needsInitiation(p), "l'initiation se rouvrirait pendant l'ecran de mort");
        //un gain pendant l'ecran de mort (contrat, vente du bac) s'ecrit dans le VRAI profil
        PioneerCap.get(p).balance += 5;
        Gt.equal(782, dead.balance, "gain pendant l'ecran de mort perdu");
        ServerPlayer q = s.server.getPlayerList().respawn(p, false);
        Gt.check(q != p, "la reapparition doit creer un nouveau joueur");
        Gt.check(!ProfileEvents.needsInitiation(q), "l'initiation est redemandee apres la reapparition");
        Gt.equal(782, PioneerCap.get(q).balance, "solde apres la reapparition");
        PioneerCap.get(q).balance = 777;
        PioneerTests.same(q, skill, "apres la mort et l'ecran de mort");
        s.done();
    }

    /** Un joueur NON initie : l'initiation lui est due vivant, jamais mort (ecran de mort), de nouveau apres la reapparition. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_8_mort_nouveau", timeoutTicks = 80)
    public static void p104_8_initiation_jamais_morte(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gt8new", "Nouvelle", AT);
        PioneerCap.get(p).initiated = false;
        Gt.check(ProfileEvents.needsInitiation(p), "un joueur non initie doit recevoir l'initiation");
        //un joueur non initie ne subit aucun degat (ProfileEvents.onAttacked) : il meurt initie, puis redevient « non initie »
        PioneerCap.get(p).initiated = true;
        p.kill();
        Gt.check(p.isDeadOrDying(), "le joueur n'est pas mort");
        PioneerCap.get(p).initiated = false;
        Gt.check(!ProfileEvents.needsInitiation(p), "l'initiation s'ouvrirait par-dessus l'ecran de mort (avant le retrait)");
        p.remove(Entity.RemovalReason.KILLED);
        Gt.check(!ProfileEvents.needsInitiation(p), "l'initiation s'ouvrirait par-dessus l'ecran de mort");
        ServerPlayer q = s.server.getPlayerList().respawn(p, false);
        Gt.check(!PioneerCap.get(q).initiated && ProfileEvents.needsInitiation(q), "apres la reapparition, l'initiation reste due");
        PioneerCap.get(q).initiated = true;
        s.done();
    }

    /** Changement de dimension puis retour de l'End : identite et donnees RP intactes, aucune initiation. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_8_dimension", timeoutTicks = 80)
    public static void p104_8_identite_dimension(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String skill = PioneerTests.skill();
        ServerPlayer p = s.player("gt8dim", "Voyageuse", AT);
        PioneerTests.fill(p, skill);
        ServerLevel other = s.server.getLevel(Level.NETHER);
        if (other == null) {
            for (ServerLevel l : s.server.getAllLevels()) {
                if (l != s.level) {
                    other = l;
                }
            }
        }
        Gt.check(other != null, "aucune autre dimension sur ce serveur");
        p.teleportTo(other, 0.5, 100, 0.5, 0, 0);
        Gt.check(!ProfileEvents.needsInitiation(p), "initiation redemandee apres le changement de dimension");
        PioneerTests.same(p, skill, "apres le changement de dimension");
        ServerPlayer q = s.server.getPlayerList().respawn(p, true);
        Gt.check(!ProfileEvents.needsInitiation(q), "initiation redemandee au retour de l'End");
        PioneerTests.same(q, skill, "au retour de l'End");
        s.done();
    }

    // ================================================================== claims charges d'office
    static int owners(ServerLevel level, long chunk) {
        ForgeChunkManager.TicketTracker<UUID> t = ClanTests.forced(level).getEntityForcedChunks();
        int n = 0;
        for (LongSet set : t.getTickingChunks().values()) {
            if (set.contains(chunk)) {
                n++;
            }
        }
        for (LongSet set : t.getChunks().values()) {
            if (set.contains(chunk)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 1.0.5-beta (lot 13, retour de Guillaume : « les claims ne sont plus charges ») : un claim ne pose AUCUN ticket ; seul un emplacement
     * (paye a la pose) charge le chunk, et l'abandon du chunk libere l'emplacement.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_8_claims_charges", timeoutTicks = 200)
    public static void p104_8_claims_charges(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gt8load", "Arpenteuse", AT);
        Clan c = s.clan("GtCharge" + ClanTests.sfx(h), p);
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        ChunkPos f = new ChunkPos((origin.getX() >> 4) + 52, (origin.getZ() >> 4) + 3);
        String dim = ClanApi.dim(s.level);
        s.claim(c, f.getWorldPosition());
        Gt.check(c.claim(dim, f.toLong()) != null, "claim refuse");
        Gt.equal(0, owners(s.level, f.toLong()), "un claim pose encore un ticket (claims charges d'office retires)");
        Gt.check(!fr.pioneers.clan.territory.ClanChunkloading.isLoaded(c, dim, f.toLong()), "chunk revendique dit charge sans emplacement");
        ClanTerritory.unclaim(p, s.level, f.toLong());
        Gt.check(c.claim(dim, f.toLong()) == null, "abandon refuse");
        Gt.equal(0, owners(s.level, f.toLong()), "ticket apres l'abandon");
        s.done();
    }

    // ================================================================== roles : budget et territoire
    /**
     * Tresorier : depense (retrait sous son plafond) et fixe la cotisation en Empire (decision du Representant DELEGUEE) ; ne revendique pas.
     * Arpenteur : revendique ; ne depense pas. Membre : rien de tout ca. Achat de claims : Tresorier oui, Arpenteur et membre non.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_8_roles", timeoutTicks = 200)
    public static void p104_8_roles_budget_territoire(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer rep = s.player("gt8rep", "Cheffe", AT);
        ServerPlayer tre = s.player("gt8tre", "Tresoriere", AT.east());
        ServerPlayer arp = s.player("gt8arp", "Arpenteur", AT.south());
        ServerPlayer mem = s.player("gt8mem", "Membre", AT.west());
        Clan c = s.clan("GtRoles" + ClanTests.sfx(h), rep, tre, arp, mem);
        Gt.check(c.roles.containsKey("surveyor") && c.roles.containsKey("recruiter"), "roles proposes absents (Arpenteur, Recruteur)");
        s.role(c, tre, "steward");
        s.role(c, arp, "surveyor");
        s.role(c, mem, "member");
        c.treasury = 10_000;
        // permissions
        Gt.check(ClanTerritory.may(c, tre.getUUID(), Permission.WITHDRAW) && !ClanTerritory.may(c, tre.getUUID(), Permission.CLAIM),
              "Tresorier : depense oui, claim non");
        Gt.check(ClanTerritory.may(c, arp.getUUID(), Permission.CLAIM) && !ClanTerritory.may(c, arp.getUUID(), Permission.WITHDRAW),
              "Arpenteur : claim oui, depense non");
        Gt.check(!ClanTerritory.may(c, mem.getUUID(), Permission.CLAIM) && !ClanTerritory.may(c, mem.getUUID(), Permission.WITHDRAW),
              "Membre : ni claim ni depense");
        Gt.check(ClanTerritory.mayBuy(c, tre.getUUID()) && !ClanTerritory.mayBuy(c, arp.getUUID()) && !ClanTerritory.mayBuy(c, mem.getUUID()),
              "achat de claims : Tresorier seulement");
        // depenses (vraie action du serveur)
        Gt.check(ClanFinance.withdraw(tre, 100).error() == null, "le Tresorier ne peut pas retirer 100");
        Gt.equal(9_900, c.treasury, "tresor apres le retrait du Tresorier");
        Gt.check(ClanFinance.withdraw(arp, 100).error() != null, "l'Arpenteur a pu retirer");
        Gt.check(ClanFinance.withdraw(mem, 100).error() != null, "le membre a pu retirer");
        Gt.equal(9_900, c.treasury, "tresor touche par un role sans permission");
        // delegation : en Empire, la cotisation est une decision du Representant ; le Tresorier l'exerce en son nom
        Gt.check(ClanFinance.setTax(tre, c, "dues", 10).ok(), "le Tresorier ne peut pas fixer la cotisation (delegation)");
        Gt.equal(10, c.dues, "cotisation fixee par le Tresorier");
        Gt.check(!ClanFinance.setTax(mem, c, "dues", 20).ok(), "le membre a fixe la cotisation");
        Gt.equal(10, c.dues, "cotisation changee par le membre");
        // territoire (vraie action du serveur) : un chunk loin de tout ; le Tresorier et le membre sont refuses avant toute regle
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        long far = ChunkPos.asLong((origin.getX() >> 4) + 61, (origin.getZ() >> 4) - 7);
        String dim = ClanApi.dim(s.level);
        ClanTerritory.claim(tre, s.level, far);
        ClanTerritory.claim(mem, s.level, far);
        Gt.check(c.claim(dim, far) == null, "le Tresorier ou le membre a revendique");
        TerritoryRules.Verdict v = ClanTerritory.check(s.level, c, far, ClanClock.now());
        if (v == TerritoryRules.Verdict.OK || v == TerritoryRules.Verdict.OUTPOST) {
            ClanTerritory.claim(arp, s.level, far);
            Gt.check(c.claim(dim, far) != null, "l'Arpenteur n'a pas pu revendiquer (verdict " + v + ")");
        }
        s.done();
    }

    /** Modifier les roles : on ne touche qu'aux roles INFERIEURS, et (hors Representant) on ne donne que ce qu'on a soi-meme. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_8_hierarchie", timeoutTicks = 200)
    public static void p104_8_roles_hierarchie(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer rep = s.player("gt8hrep", "Cheffe", AT);
        ServerPlayer arp = s.player("gt8harp", "Arpenteuse", AT.east());
        Clan c = s.clan("GtHier" + ClanTests.sfx(h), rep, arp);
        s.role(c, arp, "surveyor");
        Gt.check(!ClanGov.decide(arp, c, "roles", perm("member", "claim", true), "").ok(), "l'Arpenteur modifie les roles sans la permission");
        Gt.check(ClanGov.decide(rep, c, "roles", perm("surveyor", "edit_roles", true), "").ok(), "le Representant ne peut pas deleguer « Modifier les roles »");
        Gt.check(c.roles.get("surveyor").perms.contains(Permission.EDIT_ROLES), "permission non donnee a l'Arpenteur");
        Gt.check(ClanGov.decide(arp, c, "roles", perm("member", "claim", true), "").ok(), "l'Arpenteur ne peut pas donner « Reclamer » au Membre");
        Gt.check(c.roles.get("member").perms.contains(Permission.CLAIM), "Reclamer non donne au Membre");
        ClanGov.Result r = ClanGov.decide(arp, c, "roles", perm("member", "withdraw", true), "");
        Gt.check(!r.ok() && "clan.pioneers.error.perm_not_own".equals(r.error()), "l'Arpenteur donne une permission qu'il n'a pas : " + r.error());
        Gt.check(!ClanGov.decide(arp, c, "roles", perm("steward", "claim", true), "").ok(), "l'Arpenteur modifie un role superieur (Tresorier)");
        Gt.check(!ClanGov.decide(arp, c, "regime", new CompoundTag(), "").ok(), "le regime se delegue");
        s.done();
    }

    private static CompoundTag perm(String role, String perm, boolean on) {
        CompoundTag d = new CompoundTag();
        d.putString("op", "perm");
        d.putString("role", role);
        d.putString("perm", perm);
        d.putBoolean("on", on);
        return d;
    }

    /** Clan d'avant le lot 8 : Arpenteur et Recruteur ajoutes une fois, aucun droit retire, « Acheter des claims » a Reclamer + Retirer. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_8_migration", timeoutTicks = 100)
    public static void p104_8_migration_roles(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer rep = s.player("gt8mig", "Ancienne", AT);
        Clan c = s.clan("GtMigr" + ClanTests.sfx(h), rep);
        c.roles.remove("surveyor");
        c.roles.remove("recruiter");
        c.extra.remove(ClanRoles.ROLES_VERSION);
        Clan.Role lt = c.roles.get("lieutenant");
        lt.perms.clear();
        lt.perms.add(Permission.CLAIM);
        lt.perms.add(Permission.WITHDRAW);
        lt.perms.add(Permission.KICK);
        Clan.Role guard = c.roles.get("guard");
        int guardPerms = guard.perms.size();
        Gt.check(ClanRoles.migrate(c, ClanDefs.get()), "migration non faite");
        Gt.check(c.roles.containsKey("surveyor") && c.roles.containsKey("recruiter"), "roles proposes non ajoutes");
        Gt.check(lt.perms.contains(Permission.BUY_CLAIMS) && lt.perms.contains(Permission.KICK) && lt.perms.size() == 4,
              "Bras droit : « Acheter des claims » ajoute, rien d'autre change");
        Gt.equal(guardPerms + (guard.perms.contains(Permission.BUY_CLAIMS) ? 1 : 0), guard.perms.size(), "Garde : permissions changees");
        Gt.check(!ClanRoles.migrate(c, ClanDefs.get()), "migration refaite une seconde fois");
        Clan copy = Clan.load(c.save());
        Gt.check(copy.roles.containsKey("surveyor") && copy.extra.getInt(ClanRoles.ROLES_VERSION) == ClanRoles.ROLES_V, "migration perdue a la sauvegarde");
        s.done();
    }
}
