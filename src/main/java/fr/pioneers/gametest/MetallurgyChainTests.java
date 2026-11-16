package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.GasAmount;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ Recettes (lot 2, GameTest) : la chaine du fer jusqu'au caisson MKI sur de vraies machines d'atelier, dans le monde de test : minerai
 * de fer concasse -> fonte (creuset electrique, voie simple du fer : coke et chaux) -> acier (four a induction : fonte et ferraille, voie
 * simple de l'acier). 1.0.5-beta (lot 39, retour 64 de Guillaume : « acier : haut fourneau seulement ») : plus d'acier au creuset ni au four
 * a induction ; la chaine verifie que la voie est fermee puis reprend avec les lingots d'acier du haut fourneau. Machines ultimes (recettes MKI : 8 fois plus rapides), energie remplie a chaque etape. Les recettes des voies sont celles
 * de tools/gc/industry/processes.py (identifiants pioneers:processes/...).
 * MAJ Recettes (lot 3) : ilmenite concassee -> titane (voie simple du MKIII : separation magnetique, laitier titanifere au creuset,
 * electrolyse directe de l'oxyde) -> caisson MKIII ; wolframite concassee -> tungstene (voie simple du MKIV : table, reduction au plasma
 * d'hydrogene, frittage) -> caisson MKIV. La chaine de la bauxite (cuve d'electrolyse ignee : multibloc seulement) ne tient pas dans ce cadre
 * de machines d'atelier (decision L3-17).
 * MAJ Recettes (lot 9a) : formes refaites apres les lots 4a, 4b et 6 (plus de plaque a la presse ni de tige au tour depuis le lingot) :
 * acier et titane refondus au creuset puis coules en matrice (acier refroidi a l'eau), tungstene forge en billette au marteau d'estampage
 * puis lamine ; poutrelles soudees, toles a la calandre ; caissons du gabarit du lot 6 a la soudeuse a l'arc (x8), qui prennent les
 * poutrelles et les toles de la chaine (le reste du gabarit est fourni : bloc de metal, pieces du tier, plaques d'un autre metal, cadre).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MetallurgyChainTests {

    private MetallurgyChainTests() {
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("pioneers", id));
        Gt.check(it != null && it != net.minecraft.world.item.Items.AIR, "objet absent : " + id);
        return it;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    /** Machine d'atelier ultime posee en rel, energie pleine. */
    private static AtelierTile atelier(GameTestHelper h, BlockPos rel, IndustryMachine machine) {
        Block block = Gt.compact(machine, CableTier.ULTIMATE);   // 1.0.4-beta (lot 11) : kit compris (machines absorbees)
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier pour " + machine.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        return t;
    }

    private static void lock(AtelierTile m, String recipe) {
        Gt.check(m.setLockedRecipe(rl(recipe)), "recette " + recipe + " absente de la machine");
    }

    private static void give(AtelierTile m, int slot, ItemStack stack) {
        m.itemInputs().get(slot).setStack(stack);
    }

    private static int count(AtelierTile m, Item it) {
        int n = 0;
        for (ItemSlot s : m.itemOutputs()) {
            if (s.getStack().is(it)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Retire des sorties n objets (pour les donner a la machine suivante). */
    private static ItemStack take(AtelierTile m, Item it, int n) {
        int left = n;
        for (ItemSlot s : m.itemOutputs()) {
            if (left > 0 && s.getStack().is(it)) {
                int k = Math.min(left, s.getCount());
                ItemStack rest = s.getStack().copy();
                rest.shrink(k);
                s.setStack(rest.isEmpty() ? ItemStack.EMPTY : rest);
                left -= k;
            }
        }
        Gt.check(left == 0, "pas assez de " + it + " en sortie");
        return new ItemStack(it, n);
    }

    private static void gas(AtelierTile m, int tank, String id, long amount) {
        m.gasInputs().get(tank).setStack(GasStack.of(PioneersAPI.gas(rl(id)), amount));
    }

    private static void water(AtelierTile m, int tank, int mb) {
        m.fluidInputs().get(tank).setFluid(new FluidStack(Fluids.WATER, mb));
    }

    /**
     * MAJ Recettes (lot 9a) : caisson du gabarit du lot 6 (T:industry/structure_gen.py) a la soudeuse a l'arc ultime, recette {@code recipe}
     * verrouillee : chaque entree que la chaine a faite (paires objet, machine qui l'a en sortie) en est prise, les autres sont fournies
     * (bloc de metal, pieces du tier, plaques d'un autre metal, cadre) ; gaz fourni.
     */
    private static AtelierTile welderCasing(GameTestHelper h, BlockPos rel, String recipe, Object... made) {
        AtelierTile w = atelier(h, rel, IndustryMachine.ASSEMBLER);
        lock(w, recipe);
        RecipeChoice found = null;
        for (RecipeChoice c : w.recipeChoices()) {
            if (c.id().equals(rl(recipe))) {
                found = c;
            }
        }
        Gt.check(found != null, "recette " + recipe + " introuvable a la soudeuse");
        int fromChain = 0;
        for (int i = 0; i < found.items().size(); i++) {
            Counted c = found.items().get(i);
            ItemStack stack = null;
            for (int k = 0; k + 1 < made.length && stack == null; k += 2) {
                Item it = (Item) made[k];
                if (c.ingredient().test(new ItemStack(it))) {
                    stack = take((AtelierTile) made[k + 1], it, c.count());
                    fromChain++;
                }
            }
            give(w, i, stack != null ? stack : c.ingredient().getItems()[0].copyWithCount(c.count()));
        }
        Gt.equal(made.length / 2, fromChain, "entrees du caisson faites par la chaine");
        for (int i = 0; i < found.gases().size(); i++) {
            GasAmount g = found.gases().get(i);
            w.gasInputs().get(i).setStack(GasStack.of(g.stack().getGas(), g.amount() * 4));
        }
        return w;
    }

    private static void power(AtelierTile... ms) {
        for (AtelierTile m : ms) {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
        }
    }

    /** Verse dans le reservoir d'entree d'une machine tout le metal fondu sorti d'une autre (comme par une conduite refractaire). */
    private static void pour(AtelierTile from, AtelierTile to, int tank) {
        FluidStack molten = from.fluidOutputs().get(0).getFluid().copy();
        Gt.check(!molten.isEmpty(), "pas de metal fondu en sortie");
        from.fluidOutputs().get(0).setFluid(FluidStack.EMPTY);
        to.fluidInputs().get(tank).setFluid(molten);
    }

    private static int fluidOut(AtelierTile m) {
        return m.fluidOutputs().get(0).getFluidAmount();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_chaine_fer", timeoutTicks = 6000)
    public static void chaine_fer_caisson_mk1(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item crushed = item("crushed_iron"), coke = item("coke"), lime = item("quicklime"), pig = item("pig_iron"), scrap = item("iron_scrap");
        Item steel = item("steel_ingot"), plate = item("steel_plate"), die = item("plate_die"), beam = item("steel_beam"), sheet = item("steel_sheet");
        Item casing = item("industrial_casing_basic");
        AtelierTile crucible = atelier(h, new BlockPos(1, 2, 1), IndustryMachine.ELECTRIC_CRUCIBLE);
        //1.0.6 : plus de plaque d'acier coulee (cast_plate_steel fusionnee au lot 13 du 1.0.4-beta, T:industry/fusion.py) : la presse
        AtelierTile press = atelier(h, new BlockPos(5, 2, 1), IndustryMachine.PRESS);
        AtelierTile welder = atelier(h, new BlockPos(1, 2, 4), IndustryMachine.ASSEMBLER);
        AtelierTile calender = atelier(h, new BlockPos(3, 2, 4), IndustryMachine.ROLLING_MILL);
        List<AtelierTile> last = new ArrayList<>();
        // 1. fonte au creuset : 2 minerais concasses + 2 coke + 1 chaux -> 1 fonte (+ laitier) ; 10 fois
        lock(crucible, "processes/steel/induction_2");
        give(crucible, 0, new ItemStack(crushed, 20));
        give(crucible, 1, new ItemStack(coke, 20));
        give(crucible, 2, new ItemStack(lime, 10));
        h.startSequence()
              .thenWaitUntil(() -> {
                  power(crucible);
                  Gt.check(count(crucible, pig) >= 10, "fonte au creuset : " + count(crucible, pig) + " / 10");
              })
              // 2. 1.0.5-beta (lot 39, retour 64) : l'acier ne se fait plus qu'au haut fourneau : la voie « fonte + ferraille » du creuset est
              // retiree (aucune recette du creuset ni du four a induction ne sort de l'acier ordinaire) ; 15 lingots du haut fourneau
              .thenExecute(() -> {
                  Gt.check(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("pioneers", "processes/steel/induction_3")).isEmpty(),
                        "acier au creuset retire (retour 64)");
                  Gt.check(scrap != null && count(crucible, pig) >= 10, "fonte gardee (creuset)");
              })
              // 3. plaques pressees depuis les lingots du haut fourneau (1 lingot + matrice -> 1 plaque, matrice rendue : case Outil, lot 44 ;
              // 1.0.6 : la coulee de plaques d'acier du lot 4b n'existe plus) ; 10 fois
              .thenExecute(() -> {
                  lock(press, "bronze/steam/press/plate_steel");   // 1.0.6 : la copie MKI est desactivee (doublon)
                  give(press, 0, new ItemStack(steel, 10));
                  Gt.tool(press, new ItemStack(die, 1));
              })
              .thenWaitUntil(() -> {
                  power(press);
                  Gt.check(count(press, plate) >= 10, "plaques pressees : " + count(press, plate) + " / 10");
              })
              // 5. poutrelles soudees (3 plaques -> 2) et toles a la calandre (1 plaque -> 2)
              .thenExecute(() -> {
                  lock(welder, "industry/arc_welder/weld_beam_steel");
                  give(welder, 0, take(press, plate, 6));
                  lock(calender, "bronze/steam/rolling_mill/sheet_steel");
                  give(calender, 0, take(press, plate, 4));
              })
              .thenWaitUntil(() -> {
                  power(welder, calender);
                  Gt.check(count(welder, beam) >= 4 && count(calender, sheet) >= 8, "poutrelles " + count(welder, beam) + " / 4, toles "
                        + count(calender, sheet) + " / 8");
              })
              // 6. caisson MKI du gabarit (lot 6) : poutrelles et toles de la chaine ; bloc de bronze, tiges de laiton, plaques de cuivre et
              // cadre fournis
              .thenExecute(() -> last.add(welderCasing(h, new BlockPos(5, 2, 7), "industry/arc_welder/weld_casings", beam, welder, sheet, calender)))
              .thenWaitUntil(() -> {
                  power(last.get(0));
                  Gt.check(count(last.get(0), casing) >= 8, "caissons MKI : " + count(last.get(0), casing) + " / 8");
              })
              .thenExecute(s::done);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_chaine_ilmenite", timeoutTicks = 8000)
    public static void chaine_ilmenite_caisson_mk3(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item crushed = item("crushed_ilmenite"), conc = item("ilmenite_concentrate"), coke = item("coke"), slag = item("titanium_slag");
        Item salt = item("salt"), ti = item("titanium_ingot"), plate = item("titanium_plate"), die = item("plate_die"), sheet = item("titanium_sheet");
        Item casing = item("industrial_casing_elite");
        AtelierTile magnet = atelier(h, new BlockPos(1, 2, 1), IndustryMachine.ORE_SEPARATOR);
        AtelierTile crucible = atelier(h, new BlockPos(3, 2, 1), IndustryMachine.ELECTRIC_CRUCIBLE);
        AtelierTile cell = atelier(h, new BlockPos(5, 2, 1), IndustryMachine.ELECTROLYZER);
        AtelierTile melter = atelier(h, new BlockPos(7, 2, 1), IndustryMachine.ELECTRIC_CRUCIBLE);
        AtelierTile caster = atelier(h, new BlockPos(1, 2, 4), IndustryMachine.CASTING_MACHINE);
        AtelierTile calender = atelier(h, new BlockPos(3, 2, 4), IndustryMachine.ROLLING_MILL);
        List<AtelierTile> last = new ArrayList<>();
        // 1. separation magnetique grossiere du concasse : 2 concasses -> 1 concentre (rendement du minerai en plus)
        lock(magnet, "processes/titanium/oxide_electrolysis_2");
        give(magnet, 0, new ItemStack(crushed, 16));
        h.startSequence()
              .thenWaitUntil(() -> {
                  power(magnet);
                  Gt.check(count(magnet, conc) >= 8, "concentre d'ilmenite : " + count(magnet, conc) + " / 8");
              })
              // 2. laitier titanifere au creuset : 2 concentres + 1 coke -> 2 laitiers (+ fonte)
              .thenExecute(() -> {
                  lock(crucible, "processes/titanium/oxide_electrolysis_3");
                  give(crucible, 0, take(magnet, conc, 8));
                  give(crucible, 1, new ItemStack(coke, 4));
              })
              .thenWaitUntil(() -> {
                  power(crucible);
                  Gt.check(count(crucible, slag) >= 8, "laitier titanifere : " + count(crucible, slag) + " / 8");
              })
              // 3. electrolyse directe de l'oxyde dans le sel fondu : 2 laitiers + 1 sel -> 2 lingots de titane
              .thenExecute(() -> {
                  lock(cell, "processes/titanium/oxide_electrolysis_4");
                  give(cell, 0, take(crucible, slag, 8));
                  give(cell, 1, new ItemStack(salt, 4));
              })
              .thenWaitUntil(() -> {
                  power(cell);
                  Gt.check(count(cell, ti) >= 8, "titane a la cellule : " + count(cell, ti) + " / 8");
              })
              // 4. titane refondu (8 lingots -> 1 152 mB), plaques coulees en matrice (228 mB -> 1 plaque + jets) ; 4 plaques
              .thenExecute(() -> {
                  lock(melter, "industry/electric_crucible/melt_ingot_titanium");
                  give(melter, 0, take(cell, ti, 8));
              })
              .thenWaitUntil(() -> {
                  power(melter);
                  Gt.check(fluidOut(melter) >= 1_152, "titane fondu : " + fluidOut(melter) + " / 1152 mB");
              })
              .thenExecute(() -> {
                  lock(caster, "industry/casting_machine/cast_plate_titanium");
                  pour(melter, caster, 0);
                  Gt.tool(caster, new ItemStack(die, 5));   // 1.0.5-beta (lot 44) : case Outil
              })
              .thenWaitUntil(() -> {
                  power(caster);
                  Gt.check(count(caster, plate) >= 4, "plaques de titane : " + count(caster, plate) + " / 4");
              })
              // 5. toles a la calandre (1 plaque -> 2)
              .thenExecute(() -> {
                  lock(calender, "industry/calender/sheet_titanium");
                  give(calender, 0, take(caster, plate, 4));
              })
              .thenWaitUntil(() -> {
                  power(calender);
                  Gt.check(count(calender, sheet) >= 8, "toles de titane : " + count(calender, sheet) + " / 8");
              })
              // 6. caisson MKIII du gabarit : toles de titane de la chaine ; bloc d'inox, poutrelles, ressorts, plaques, cadre et argon fournis
              .thenExecute(() -> last.add(welderCasing(h, new BlockPos(5, 2, 7), "industry/arc_welder/weld_casings_elite", sheet, calender)))
              .thenWaitUntil(() -> {
                  power(last.get(0));
                  Gt.check(count(last.get(0), casing) >= 8, "caissons MKIII : " + count(last.get(0), casing) + " / 8");
              })
              .thenExecute(s::done);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_chaine_wolframite", timeoutTicks = 12000)
    public static void chaine_wolframite_caisson_mk4(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item crushed = item("crushed_wolframite"), conc = item("wolframite_concentrate"), dust = item("tungsten_dust"), w = item("tungsten_ingot");
        Item billet = item("tungsten_billet"), plate = item("tungsten_plate"), sheet = item("tungsten_sheet");
        Item casing = item("industrial_casing_ultimate");
        AtelierTile table = atelier(h, new BlockPos(1, 2, 1), IndustryMachine.ORE_SEPARATOR);
        AtelierTile plasma = atelier(h, new BlockPos(3, 2, 1), IndustryMachine.FURNACE);
        AtelierTile sinter = atelier(h, new BlockPos(5, 2, 1), IndustryMachine.FURNACE);
        AtelierTile hammer = atelier(h, new BlockPos(7, 2, 1), IndustryMachine.POWER_HAMMER);
        AtelierTile mill = atelier(h, new BlockPos(1, 2, 4), IndustryMachine.ROLLING_MILL);
        AtelierTile calender = atelier(h, new BlockPos(3, 2, 4), IndustryMachine.ROLLING_MILL);
        List<AtelierTile> last = new ArrayList<>();
        // 1. table a secousses sur le concasse : 4 concasses + eau -> 2 concentres (rendement du minerai en plus)
        lock(table, "processes/tungsten/plasma_2");
        give(table, 0, new ItemStack(crushed, 40));
        water(table, 0, 4_000);
        h.startSequence()
              .thenWaitUntil(() -> {
                  power(table);
                  Gt.check(count(table, conc) >= 20, "concentre de wolframite : " + count(table, conc) + " / 20");
              })
              // 2. reduction au plasma d'hydrogene : 2 concentres -> 2 poudres de tungstene
              .thenExecute(() -> {
                  lock(plasma, "processes/tungsten/plasma_3");
                  give(plasma, 0, take(table, conc, 20));
                  gas(plasma, 0, "hydrogen", 20_000);
              })
              .thenWaitUntil(() -> {
                  power(plasma);
                  Gt.check(count(plasma, dust) >= 20, "poudre de tungstene : " + count(plasma, dust) + " / 20");
              })
              // 3. frittage de la poudre sous hydrogene : 4 poudres -> 2 lingots
              .thenExecute(() -> {
                  lock(sinter, "processes/tungsten/plasma_4");
                  give(sinter, 0, take(plasma, dust, 20));
                  gas(sinter, 0, "hydrogen", 2_000);
              })
              .thenWaitUntil(() -> {
                  power(sinter);
                  Gt.check(count(sinter, w) >= 9, "lingots de tungstene : " + count(sinter, w) + " / 9");
              })
              // 4. billette forgee au marteau d'estampage (9 lingots, metal non coulable), plaques au laminoir (billette -> 9 plaques)
              .thenExecute(() -> {
                  lock(hammer, "industry/power_hammer/forge_billet_tungsten");
                  give(hammer, 0, take(sinter, w, 9));
              })
              .thenWaitUntil(() -> {
                  power(hammer);
                  Gt.check(count(hammer, billet) >= 1, "billette de tungstene non forgee");
              })
              .thenExecute(() -> {
                  lock(mill, "industry/rolling_mill/roll_billet_plate_tungsten");
                  give(mill, 0, take(hammer, billet, 1));
              })
              .thenWaitUntil(() -> {
                  power(mill);
                  Gt.check(count(mill, plate) >= 4, "plaques de tungstene : " + count(mill, plate) + " / 4");
              })
              // 5. toles a la calandre (1 plaque -> 2)
              .thenExecute(() -> {
                  lock(calender, "industry/calender/sheet_tungsten");
                  give(calender, 0, take(mill, plate, 4));
              })
              .thenWaitUntil(() -> {
                  power(calender);
                  Gt.check(count(calender, sheet) >= 8, "toles de tungstene : " + count(calender, sheet) + " / 8");
              })
              // 6. caisson MKIV du gabarit : toles de tungstene de la chaine ; bloc, poutrelles, engrenages, plaques d'osmium, cadre, argon
              // fournis
              .thenExecute(() -> last.add(welderCasing(h, new BlockPos(5, 2, 7), "industry/arc_welder/weld_casings_ult", sheet, calender)))
              .thenWaitUntil(() -> {
                  power(last.get(0));
                  Gt.check(count(last.get(0), casing) >= 8, "caissons MKIV : " + count(last.get(0), casing) + " / 8");
              })
              .thenExecute(s::done);
    }
}
