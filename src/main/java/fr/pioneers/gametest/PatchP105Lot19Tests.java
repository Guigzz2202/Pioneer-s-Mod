package fr.pioneers.gametest;

import fr.pioneers.core.profile.KnownPlayers;
import fr.pioneers.forge.HeatGlow;
import fr.pioneers.paperwork.PaperBinKind;
import fr.pioneers.ruins.LoreIndex;
import fr.pioneers.wild.Boar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 19 (GameTests ; verification finale des retours de jeu, lots 13 a 18) : chaque correction de comportement du lot.
 * Le selecteur de joueur ne revele pas le personnage derriere un compte hors du clan ; la recherche d'enclos d'un sanglier ne charge jamais
 * un troncon ; l'incandescence d'une piece chaude monte toujours avec la temperature, meme sur une texture sombre ; le bac d'archives peut
 * ranger tout le Codex.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot19Tests {

    private PatchP105Lot19Tests() {
    }

    /** Deux joueurs sans clan : la liste du selecteur donne le compte, jamais le nom de pionnier ; la regle pure le garde pour le clan. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_19_picker", timeoutTicks = 20)
    public static void p105_19_selecteur_sans_identite(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer a = s.player("gtpka", "Anselme", new BlockPos(1, 1, 1));
        ServerPlayer b = s.player("gtpkb", "Mireille", new BlockPos(3, 1, 1));
        ListTag l = KnownPlayers.view(s.server, a, "", "any").getList("list", Tag.TAG_COMPOUND);
        boolean seen = false;
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            if (e.getUUID("u").equals(b.getUUID())) {
                seen = true;
                Gt.equal(b.getGameProfile().getName(), e.getString("n"), "le compte est donne");
                Gt.equal("", e.getString("p"), "le nom de pionnier n'est pas donne hors du clan");
            }
        }
        Gt.check(seen, "le joueur connecte est dans la liste");
        Gt.check(KnownPlayers.view(s.server, a, "Mireille", "any").getList("list", Tag.TAG_COMPOUND).isEmpty(),
              "chercher le nom de pionnier ne trouve personne");
        KnownPlayers.Known k = new KnownPlayers.Known(b.getUUID(), "gtpkb", "Mireille", 1);
        Gt.equal("Mireille", KnownPlayers.shownPioneer(k, true), "membre du clan qui voit les identites : le nom est donne");
        Gt.equal("", KnownPlayers.shownPioneer(k, false), "sinon : rien");
        s.done();
    }

    /** Une case dans un troncon jamais charge compte comme une cloture, et le troncon reste non charge. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_19_boar", timeoutTicks = 20)
    public static void p105_19_sanglier_troncon_non_charge(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        BlockPos far = new BlockPos(28_000_000, 64, 28_000_000);
        Gt.check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "troncon lointain non charge au depart");
        Gt.check(Boar.blocks(level, far), "case non chargee = cloture (champ epargne)");
        Gt.check(!level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "la recherche n'a pas charge le troncon");
        Gt.check(!Boar.blocks(level, h.absolutePos(new BlockPos(1, 2, 1))), "case d'air chargee : franchissable");
        s.done();
    }

    /** Incandescence : clarte rendue toujours croissante de 800 a 1 400 degres, 1 350 nettement plus clair que 950, meme a clarte 0,2. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_19_heat", timeoutTicks = 20)
    public static void p105_19_incandescence_croissante(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (float l : new float[]{0.12F, 0.2F, 0.34F, 0.5F, 0.7F}) {
            float prev = -1;
            for (int t = 800; t <= 1400; t += 50) {
                float[] o = HeatGlow.shade(new float[]{l, l, l}, HeatGlow.passes(HeatGlow.factor(t, l)));
                float r = HeatGlow.lum(o[0], o[1], o[2]);
                Gt.check(r + 1E-4F >= prev, "clarte croissante (texture " + l + ", " + t + " degres)");
                prev = r;
            }
            float[] a = HeatGlow.shade(new float[]{l, l, l}, HeatGlow.passes(HeatGlow.factor(950, l)));
            float[] b = HeatGlow.shade(new float[]{l, l, l}, HeatGlow.passes(HeatGlow.factor(1350, l)));
            Gt.check(HeatGlow.lum(b[0], b[1], b[2]) >= 0.75F && HeatGlow.lum(b[0], b[1], b[2]) > HeatGlow.lum(a[0], a[1], a[2]) + 0.1F,
                  "1 350 degres blanc-jaune et plus clair que 950 (texture " + l + ")");
        }
        float[] cold = HeatGlow.factor(300, 0.3F);
        Gt.check(cold[0] == 1F && cold[1] == 1F && cold[2] == 1F, "froid : la vraie texture");
        Gt.check(HeatGlow.passes(HeatGlow.factor(1400, 0.1F)).length <= 1 + HeatGlow.MAX_ADD, "passes bornees");
        s.done();
    }

    /** Le bac d'archives range tout le Codex (entrees et notes du serveur). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_19_archive", timeoutTicks = 20)
    public static void p105_19_bac_archives_tout_le_codex(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(LoreIndex.size() > 100, "Codex du serveur charge (" + LoreIndex.size() + " ecrits)");
        Gt.check(PaperBinKind.ARCHIVES.slots >= LoreIndex.size(), "le bac d'archives a une place par ecrit du Codex");
        s.done();
    }
}
