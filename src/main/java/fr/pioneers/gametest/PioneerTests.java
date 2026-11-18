package fr.pioneers.gametest;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerDefs;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.RpConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9 (MAJ RP, GameTests) : bloc 2 (le pionnier) : le profil suit le joueur (mort, changement de dimension, retour de l'End, deconnexion) et
 * les plafonds d'XP tiennent dans un vrai monde (jour de jeu, sauvegarde du joueur).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PioneerTests {

    private static final BlockPos AT = new BlockPos(4, 2, 4);

    private PioneerTests() {
    }

    /** Premier skill actif (son interrupteur est allume). */
    static String skill() {
        for (PioneerDefs.Skill sk : PioneerDefs.get().skills.values()) {
            if (PioneerApi.hasFeature(sk.feature())) {
                return sk.id();
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("aucun skill charge (data/pioneers/pioneer)");
    }

    /** Premiere vocation choisissable. */
    static String vocation() {
        for (PioneerDefs.Vocation v : PioneerDefs.get().vocations.values()) {
            if (PioneerApi.hasFeature(v.feature())) {
                return v.id();
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("aucune vocation chargee");
    }

    /** Remplit un profil reconnaissable : XP, Maitrise, vocation, solde, reputation, lecture. */
    static void fill(ServerPlayer p, String skill) {
        PioneerProfile prof = PioneerCap.get(p);
        prof.xp.put(skill, 1234.5);
        prof.masteries.add(skill);
        prof.vocation = vocation();
        prof.balance = 777;
        prof.reputation.put("restorers", 42);
        prof.loreRead.add("gametest_entry");
        prof.touch();
    }

    static void same(ServerPlayer p, String skill, String where) {
        PioneerProfile prof = PioneerCap.get(p);
        Gt.check(prof.initiated, where + " : initiation perdue");
        Gt.check(Math.abs(prof.xp(skill) - 1234.5) < 1e-9, where + " : XP perdue (" + prof.xp(skill) + ")");
        Gt.check(prof.masteries.contains(skill), where + " : Maitrise perdue");
        Gt.equal(vocation(), prof.vocation, where + " : vocation");
        Gt.equal(777, prof.balance, where + " : solde");
        Gt.equal(42, prof.reputation("restorers"), where + " : reputation");
        Gt.check(prof.loreRead.contains("gametest_entry"), where + " : Codex perdu");
    }

    /** Mort puis reapparition : le personnage suit le joueur, entier (aucune perte d'XP a la mort). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_profil_mort", timeoutTicks = 60)
    public static void profil_conserve_apres_la_mort(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String skill = skill();
        ServerPlayer p = s.player("gtdeath", "Revenante", AT);
        fill(p, skill);
        p.kill();
        Gt.check(p.isDeadOrDying(), "le joueur n'est pas mort");
        ServerPlayer q = s.server.getPlayerList().respawn(p, false);
        Gt.check(q != p, "la reapparition doit creer un nouveau joueur");
        same(q, skill, "apres la mort");
        s.done();
    }

    /** Changement de dimension (Nether) puis retour de l'End (copie complete) : le profil est le meme. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_profil_dimension", timeoutTicks = 60)
    public static void profil_conserve_au_changement_de_dimension(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String skill = skill();
        ServerPlayer p = s.player("gtdim", "Voyageuse", AT);
        fill(p, skill);
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
        Gt.check(p.level() == other, "le joueur n'a pas change de dimension");
        same(p, skill, "apres le changement de dimension");
        //retour de l'End : nouvelle entite, profil recopie par l'evenement Clone
        ServerPlayer q = s.server.getPlayerList().respawn(p, true);
        Gt.check(q != p, "le retour doit creer un nouveau joueur");
        same(q, skill, "apres le retour de l'End");
        s.done();
    }

    /** Regle de la Main : l'XP automatique d'un skill s'arrete au plafond du jour, plafond garde par la sauvegarde, rouvert le jour suivant. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_xp_plafond_jour", timeoutTicks = 60)
    public static void xp_automatique_plafonnee_par_jour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String skill = skill();
        int cap = PiCommonConfig.get(RpConfig.AUTO_CAP, 300);
        Gt.check(cap > 0, "plafond d'XP automatique nul dans la configuration");
        ServerPlayer p = s.player("gtcap", "Mecanicienne", AT);
        UUID id = p.getUUID();
        String account = p.getGameProfile().getName();
        PioneerApi.gainXp(p, skill, 1_000_000, PioneerApi.XpSource.AUTOMATIC, "gametest.auto", null);
        Gt.check(Math.abs(PioneerCap.get(p).xp(skill) - cap) < 1e-6, "XP automatique non plafonnee : " + PioneerCap.get(p).xp(skill));
        PioneerApi.gainXp(p, skill, 1_000_000, PioneerApi.XpSource.AUTOMATIC, "gametest.auto2", null);
        Gt.check(Math.abs(PioneerCap.get(p).xp(skill) - cap) < 1e-6, "le plafond du jour a ete depasse");
        //le plafond est persistant : il survit a la deconnexion
        Gt.disconnect(s.server, id);
        ServerPlayer q = s.reconnect(id, account, AT);
        PioneerApi.gainXp(q, skill, 1_000_000, PioneerApi.XpSource.AUTOMATIC, "gametest.auto3", null);
        Gt.check(Math.abs(PioneerCap.get(q).xp(skill) - cap) < 1e-6, "le plafond du jour s'est rouvert a la reconnexion");
        //jour de jeu suivant : la Regle de la Main rouvre le plafond
        s.level.setDayTime(s.level.getDayTime() + 24_000L);
        PioneerApi.gainXp(q, skill, 1_000_000, PioneerApi.XpSource.AUTOMATIC, "gametest.auto4", null);
        Gt.check(Math.abs(PioneerCap.get(q).xp(skill) - 2.0 * cap) < 1e-6, "plafond non rouvert le jour suivant : " + PioneerCap.get(q).xp(skill));
        s.done();
    }

    /** Plafond total d'un skill (4 x l'XP du niveau 10), niveau 10, titre de Maitrise inscrit au journal. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_xp_plafond_total", timeoutTicks = 60)
    public static void xp_plafond_total_et_maitrise(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String skill = skill();
        ServerPlayer p = s.player("gtmast", "Maitresse", AT);
        PioneerProfile prof = PioneerCap.get(p);
        PioneerApi.gainXp(p, skill, 1e9, PioneerApi.XpSource.HAND, "gametest.hand", null);
        double max = PioneerApi.xpFor(PioneerApi.MAX_LEVEL) * 4;
        Gt.check(Math.abs(prof.xp(skill) - max) < 1e-6, "XP totale non plafonnee : " + prof.xp(skill) + " (attendu " + max + ")");
        Gt.equal(PioneerApi.MAX_LEVEL, PioneerApi.level(p, skill), "niveau maximal");
        Gt.check(prof.masteries.contains(skill), "titre de Maitrise absent");
        Gt.check(prof.journal.stream().anyMatch(l -> l.key().equals("pioneer.pioneers.mastery")), "Maitrise absente du journal");
        PioneerApi.gainXp(p, skill, 1e9, PioneerApi.XpSource.HAND, "gametest.hand2", null);
        Gt.check(Math.abs(prof.xp(skill) - max) < 1e-6, "le plafond total a ete depasse");
        //aucun modificateur de skill au-dessus de 25 % (garde-fou du code, en plus des fichiers)
        for (PioneerDefs.Mod m : PioneerDefs.get().skill(skill).mods()) {
            if (!m.abs()) {
                Gt.check(Math.abs(PioneerApi.modifier(p, m.key())) <= 0.25 + 1e-9 || prof.vocation.length() > 0,
                      "modificateur " + m.key() + " au-dessus de 25 % : " + PioneerApi.modifier(p, m.key()));
            }
        }
        s.done();
    }
}
