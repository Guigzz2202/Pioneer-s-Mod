package fr.pioneers.gametest;

import fr.pioneers.api.machine.PowerRules;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.IndustrialGlassBlock;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.tech.industry.multi.StructureFrameBlock;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 « Plus loin et au-dela », lot 1 (tier MKV) :
 * <ul>
 *     <li>REGLES : plancher des durees au MKV (une recette MKI de 8 s : 0,5 s au MKV, jamais moins ; recette MKV : 4 s a son tier),
 *     parallelisme 64 et carcasses a 3 000 K au MKV, rendement R(T6) = 4,375 ; le MKIV ne change pas ;</li>
 *     <li>BLOCS DE STRUCTURE MKV : carcasse, verre, cadre et bloc de tole du tier STELLAR, chacun de tier 4 ; les registres des machines
 *     restent aux quatre tiers MKI a MKIV ;</li>
 *     <li>ALLIAGE STELLAIRE et VERRE PHOTONIQUE enregistres, avec leur recette (four a arc et cristalliseur au MKIV) et celles des blocs.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot1Tests {

    private PatchP107Lot1Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_1_regles", timeoutTicks = 20)
    public static void p107_1_regles_du_mkv(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //recette MKI de 160 ticks (8 s) : MKIV 20 ticks (plancher 1 s), MKV 10 ticks (0,5 s), jamais moins
        Gt.equal(20, Tiers.ticks(160, CableTier.ULTIMATE.ordinal(), 0, false, null), "recette MKI au MKIV");
        Gt.equal(10, Tiers.ticks(160, CableTier.STELLAR.ordinal(), 0, false, null), "recette MKI au MKV : 0,5 s");
        Gt.equal(10, Tiers.ticks(20, CableTier.STELLAR.ordinal(), 0, false, null), "plancher de 10 ticks au MKV");
        Gt.equal(80, Tiers.ticks(80, CableTier.STELLAR.ordinal(), 4, false, null), "recette MKV : 4 s a son tier");
        Gt.equal(80, PowerRules.floorTicks(5, 3), "MKIV inchange : recette MKIV 4 s");
        Gt.equal(64, PowerRules.parallelCap(4), "parallelisme MKV");
        Gt.equal(3000, PowerRules.nominal(4), "carcasses MKV : 3 000 K");
        Gt.equal(32, PowerRules.parallelCap(3), "parallelisme MKIV inchange");
        Gt.check(Math.abs(PowerRules.GLOBAL_YIELD[6] - 4.375) < 1e-9, "R(T6) = 4,375");
        Gt.check(Math.abs(PowerRules.yieldFactor(6, 5) - 1.25) < 1e-9, "MKV / MKIV : x1,25");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_1_blocs", timeoutTicks = 20)
    public static void p107_1_blocs_de_structure_mkv(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CasingBlock casing = PiIndustry.CASINGS.get(CableTier.STELLAR).getBlock();
        IndustrialGlassBlock glass = PiIndustry.GLASSES.get(CableTier.STELLAR).getBlock();
        StructureFrameBlock frame = PiIndustry.FRAMES.get(CableTier.STELLAR).getBlock();
        SheetMetalBlock sheet = PiIndustry.SHEETS.get(CableTier.STELLAR).getBlock();
        Gt.equal(4, casing.tier, "carcasse MKV de tier 4");
        Gt.equal(4, glass.tier, "verre MKV de tier 4");
        Gt.equal(4, frame.tier, "cadre MKV de tier 4");
        Gt.equal(4, sheet.tier, "tole MKV de tier 4");
        Gt.equal("industrial_casing_stellar", ForgeRegistries.BLOCKS.getKey(PiIndustry.CASINGS.get(CableTier.STELLAR).getBlock()).getPath(), "id carcasse");
        Gt.equal("stellar_alloy_frame", ForgeRegistries.BLOCKS.getKey(PiIndustry.FRAMES.get(CableTier.STELLAR).getBlock()).getPath(), "id cadre");
        Gt.check(!PiIndustry.RADIATORS.containsKey(CableTier.STELLAR), "pas de radiateur MKV au lot 1");
        Gt.equal(4, CableTier.MK.size(), "quatre tiers de machines");
        for (String id : new String[]{"stellar_alloy_ingot", "stellar_alloy_plate", "stellar_alloy_foil", "stellar_alloy_sheet", "stellar_alloy_beam",
              "stellar_alloy_billet", "photonic_glass"}) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pioneers", id));
            Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        }
        var recipes = h.getLevel().getRecipeManager();
        for (String id : new String[]{"industry/arc_furnace/stellar_alloy_arc", "industry/crystallizer/photonic_glass",
              "industry/crafting/industrial_casing_stellar", "industry/crafting/industrial_glass_stellar", "industry/crafting/stellar_alloy_frame",
              "industry/crafting/stellar_alloy_sheet_metal", "industry/arc_welder/weld_casings_mkv"}) {
            Gt.check(recipes.byKey(new ResourceLocation("pioneers", id)).isPresent(), "recette absente : " + id);
        }
        s.done();
    }
}
