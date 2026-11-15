package fr.pioneers.world.ruins.gen;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.RpConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : feature {@code pioneers:ruin_annex}, appelee une fois par chunk de l'Overworld. Recalcule les sites voisins (grille
 * deterministe, aucune donnee sauvee) et le plan de chacun ({@link RuinPlan#annexes}) ; si une annexe tombe dans ce chunk, verifie que le
 * site existe vraiment (terrain lu dans le bruit, comme la ruine) puis la pose ({@link AnnexBuilder}). Ni Sentinelle, ni bloc du mod.
 */
public class AnnexFeature extends Feature<NoneFeatureConfiguration> {

    private static final int ANNEX_HALF = RuinPlan.ANNEX_HALF;

    public AnnexFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(@NotNull FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RuinFeature.Context c = RuinFeature.Context.of(context);
        int max = PiCommonConfig.get(RpConfig.RUIN_ANNEXES, 2);
        if (c == null || max <= 0 || c.data().annexes().isEmpty()) {
            return false;
        }
        GenSettings s = c.data().settings();
        ChunkPos chunk = new ChunkPos(context.origin());
        int reach = s.maxHalf() * 2 + s.annexMax() + 16;
        boolean placed = false;
        try {
            for (RuinGrid.Site cand : RuinGrid.candidatesNear(c.seed(), c.layout(), chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), reach)) {
                RuinPlan plan = RuinPlan.of(cand, s);
                if (plan == null) {
                    continue;
                }
                Boolean real = null;
                for (RuinPlan.Annex a : plan.annexes(s, c.data(), max)) {
                    if (a.x() >> 4 != chunk.x || a.z() >> 4 != chunk.z) {
                        continue;
                    }
                    if (real == null) {
                        real = RuinGrid.isSite(c.seed(), c.layout(), cand, c.terrain());
                    }
                    if (!real || nearOtherSite(c, cand, a)) {
                        continue;
                    }
                    AnnexTemplate t = c.data().annexes().get(a.type()).get(a.variant());
                    placed |= new AnnexBuilder(context.level(), chunk, t, a, plan).build();
                }
            }
        } catch (RuntimeException ex) {
            Pioneers.LOGGER.warn("Annexe de ruine non generee en {} : {}", chunk, ex.toString());
        }
        return placed;
    }

    /**
     * 1.9 (MAJ RP, GameTests) : pose les annexes prevues par le plan d'un site (chacune dans son chunk, sans relire le terrain) et renvoie
     * celles qui ont ete posees. Seul usage : {@code fr.pioneers.gametest}.
     */
    public static List<RuinPlan.Annex> placeAnnexesForTest(WorldGenLevel level, RuinPlan plan, int max) {
        List<RuinPlan.Annex> out = new ArrayList<>();
        GenData.Snapshot data = GenData.current();
        if (data == null || data.annexes().isEmpty()) {
            return out;
        }
        for (RuinPlan.Annex a : plan.annexes(data.settings(), data, max)) {
            AnnexTemplate t = data.annexes().get(a.type()).get(a.variant());
            if (new AnnexBuilder(level, new ChunkPos(a.x() >> 4, a.z() >> 4), t, a, plan).build()) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * L'emplacement est-il pris ? Par une autre ruine (sa boite et sa marge), ou par une annexe d'un autre site qui passe avant (tier plus
     * haut, sinon graine plus petite) : les deux sites font le meme calcul, une seule annexe est posee.
     */
    private static boolean nearOtherSite(RuinFeature.Context c, RuinGrid.Site own, RuinPlan.Annex a) {
        GenSettings s = c.data().settings();
        int ruin = (int) Math.ceil((s.maxHalf() + s.margin()) * Math.sqrt(2)) + ANNEX_HALF;
        int reach = s.maxHalf() * 2 + s.annexMax() + 16;
        int max = PiCommonConfig.get(RpConfig.RUIN_ANNEXES, 2);
        for (RuinGrid.Site o : RuinGrid.candidatesNear(c.seed(), c.layout(), a.x(), a.z(), reach)) {
            if (o.equals(own)) {
                continue;
            }
            boolean close = RuinGrid.dist2(o.centerX() - a.x(), o.centerZ() - a.z()) <= (long) ruin * ruin;
            if (!close) {
                boolean first = o.tier() > own.tier() || o.tier() == own.tier() && o.seed() < own.seed();
                RuinPlan p = first ? RuinPlan.of(o, s) : null;
                boolean clash = false;
                for (RuinPlan.Annex b : p == null ? List.<RuinPlan.Annex>of() : p.annexes(s, c.data(), max)) {
                    clash |= RuinGrid.dist2(b.x() - a.x(), b.z() - a.z()) <= (long) (2 * ANNEX_HALF + 2) * (2 * ANNEX_HALF + 2);
                }
                if (!clash) {
                    continue;
                }
            }
            if (RuinGrid.isSite(c.seed(), c.layout(), o, c.terrain())) {
                return true;
            }
        }
        return false;
    }
}
