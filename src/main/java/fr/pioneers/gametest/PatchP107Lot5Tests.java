package fr.pioneers.gametest;

import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.mega.MkvCores;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 « Plus loin et au-dela », lot 5 (Mars et Mercure) :
 * <ul>
 *     <li>le Diademe sur Mercure, son port et ses recettes uniques ; les multiblocs inedits (four solaire, presse isostatique) et leurs
 *     recettes ; les objets des chaines et les coeurs MKV enregistres ;</li>
 *     <li>coeur MKV : un multibloc de famille en structure MKV reste MKIV sans son coeur, passe MKV avec le coeur de son domaine (le coeur
 *     d'un autre domaine ne va pas), le coeur est garde a la relecture.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot5Tests {

    private PatchP107Lot5Tests() {
    }

    private static ItemStack stack(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id)));
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_5_data", timeoutTicks = 20)
    public static void p107_5_diademe_et_multiblocs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int diadem = Megastructures.byBody(Body.MERCURY);
        Gt.check(diadem >= 0, "Diademe sur Mercure");
        Gt.equal("mercury_diadem", Megastructures.id(diadem), "id du Diademe");
        for (String key : new String[]{"mercury_diadem_port", "solar_concentrator", "isostatic_press"}) {
            IndustryMachine m = IndustryMachine.byKey(key);
            Gt.check(m != null && PiIndustry.controller(m) != null, "multibloc absent : " + key);
            Gt.check(!IndustryRecipes.of(h.getLevel(), m).isEmpty(), "aucune recette : " + key);
        }
        for (String id : new String[]{"pioneers:superalloy_melt_charge", "pioneers:isostatic_preform", "pioneers:forming_mkv_core",
              "pioneers:thermal_mkv_core", "pioneers:machine_scale_monocrystal_blade", "pioneers:active_heliothermal_mirror",
              "pioneers:ultimate_refractory_crucible"}) {
            Gt.check(ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(id)), "objet absent : " + id);
        }
        Gt.check(MkvCores.fits(stack("pioneers:forming_mkv_core"), "press"), "coeur de mise en forme : presse");
        Gt.check(!MkvCores.fits(stack("pioneers:forming_mkv_core"), "furnace"), "coeur de mise en forme : pas le four");
        Gt.check(MkvCores.fits(stack("pioneers:thermal_mkv_core"), "electric_crucible"), "coeur thermique : creuset");
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_5_core", timeoutTicks = 200)
    public static void p107_5_coeur_mkv_du_multibloc_de_famille(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ControllerTile tile = PatchP107Lot4Tests.build(h, IndustryMachine.byKey("press"), true);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "presse MKV : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            Gt.equal(3, tile.tier(), "structure MKV sans coeur : reste MKIV");
            Gt.check(!tile.mkvCore.install(ItemStack.EMPTY), "coeur vide pose");
            Gt.check(tile.mkvCore.install(stack("pioneers:thermal_mkv_core")), "pose du coeur thermique");
            Gt.equal(3, tile.tier(), "coeur d'un autre domaine : reste MKIV");
            tile.mkvCore.take();
            Gt.check(tile.mkvCore.install(stack("pioneers:forming_mkv_core")), "pose du coeur de mise en forme");
            Gt.check(!tile.mkvCore.install(stack("pioneers:forming_mkv_core")), "deux coeurs");
            Gt.equal(4, tile.tier(), "structure MKV et coeur du domaine : MKV");
            CompoundTag tag = tile.saveWithoutMetadata();
            Gt.check(tag.contains("mkvCore"), "coeur absent de la sauvegarde");
            ControllerTile copy = (ControllerTile) Gt.reloadTile(tile);
            Gt.check(!copy.mkvCore.get().isEmpty(), "coeur perdu a la relecture");
            s.done();
        });
    }
}
