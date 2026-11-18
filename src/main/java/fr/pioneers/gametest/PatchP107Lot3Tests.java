package fr.pioneers.gametest;

import fr.pioneers.api.machine.MachineRegistry;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.machine.MachineKind;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 « Plus loin et au-dela », lot 3 (machines compactes MKV) :
 * <ul>
 *     <li>chaque machine de famille a sa compacte MKV (stellar_), fiche de l'API au tier 4 (MKV), tier global 6 ;</li>
 *     <li>sa fabrication a l'etabli de montage existe ;</li>
 *     <li>au MKV une operation ne descend jamais sous 10 ticks (four du kit, recette d'atelier) ;</li>
 *     <li>l'infobulle dit « MKV » (et plus « Creatif ») pour le tier 4 ; le cable du MKV porte les objets (logistique ultime).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot3Tests {

    private PatchP107Lot3Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_3_machines", timeoutTicks = 20)
    public static void p107_3_machines_mkv(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(15, GeneratedTierFamilies.MKV_MACHINES.size(), "compactes MKV des familles");
        var recipes = h.getLevel().getRecipeManager();
        for (String base : GeneratedTierFamilies.MKV_MACHINES) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("pioneers", "stellar_" + base));
            Gt.check(b != null && b != Blocks.AIR, "compacte MKV absente : " + base);
            MachineRegistry.Entry e = MachineRegistry.of(b);
            Gt.check(e != null, "compacte MKV hors de l'API : " + base);
            Gt.equal(4, e.tier(), "tier MK de stellar_" + base);
            Gt.equal(6, e.spec().globalTier(e.index()), "tier global de stellar_" + base);
            Gt.equal(6, (long) GeneratedTierFamilies.TIER_OF.get("stellar_" + base), "famille : tier 6 de stellar_" + base);
            Gt.check(recipes.byKey(new ResourceLocation("pioneers", "au_dela/machines/stellar_" + base)).isPresent(), "fabrication de stellar_" + base);
        }
        Gt.check(MachineKind.FURNACE.ticks(CableTier.STELLAR) >= 10, "four MKV : jamais sous 10 ticks");
        Gt.check(MachineKind.FURNACE.ticks(CableTier.STELLAR) <= MachineKind.FURNACE.ticks(CableTier.ULTIMATE), "four MKV au moins aussi rapide");
        Gt.equal(25, Tiers.ticks(400, CableTier.STELLAR.ordinal(), 0, false, null), "recette MKI de 20 s au MKV : x16");
        Gt.equal(10, Tiers.ticks(80, CableTier.STELLAR.ordinal(), 0, false, null), "recette MKI de 4 s au MKV : plancher de 10 ticks");
        Gt.check(Tooltips.tierName(4).getContents() instanceof TranslatableContents t && t.getKey().equals("industry.pioneers.tier.stellar"),
              "infobulle : tier 4 = MKV");
        Gt.check(Tooltips.tierName(Tooltips.CREATIVE).getContents() instanceof TranslatableContents c
              && c.getKey().equals("tooltip.pioneers.tier.creative"), "infobulle : creatif");
        Gt.equal(CableLevel.ULTIMATE, CableLevel.byTier(CableTier.STELLAR), "cable du MKV : logistique ultime");
        s.done();
    }
}
