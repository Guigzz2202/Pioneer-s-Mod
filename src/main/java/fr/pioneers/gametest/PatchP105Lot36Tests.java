package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.vanilla.VanillaRecipeFilter;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.core.registries.PiBronze;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 36 (retours de jeu 5e serie, 1 : metaux et bateaux ; retour 61 « Pour les bateaux en bois il fallait justement que
 * ces derniers soient craftables ailleurs que dans le chantier naval » ; reponse de Guillaume : « a l'etabli du charpentier + au chantier
 * naval (pas l'etabli vanilla) » ; GameTests) :
 * <ul>
 *     <li>apres le filtre du serveur, chacun des 18 bateaux en bois vanilla (8 bois x bateau, bateau a coffre ; radeau et radeau a coffre de
 *     bambou) a sa recette a l'etabli du CHARPENTIER et au CHANTIER NAVAL, jamais a la grille ni ailleurs ; les recettes du charpentier sont
 *     rangees APRES toutes ses autres recettes (l'indice choisi des etablis des mondes existants ne bouge pas) ;</li>
 *     <li>un bateau en chene se FABRIQUE par le menu de l'etabli du charpentier (scie + 5 planches de chene).</li>
 * </ul>
 * Recettes : T:core/vanilla_map.py CARPENTER_BOATS (ecrites par T:core/devanilla.py) ; filtre : J:core/vanilla/VanillaRecipeFilter.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot36Tests {

    private PatchP105Lot36Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_36_boats", timeoutTicks = 20)
    public static void p105_36_bateaux_charpentier_et_chantier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        var server = s.level.getServer();
        VanillaRecipeFilter.filter(server);
        Set<Item> shipyard = new HashSet<>();
        for (Recipe<?> r : server.getRecipeManager().getRecipes()) {
            Gt.check(!VanillaRecipeFilter.vehicleCraft(r, server), "recette d'un vehicule hors des ateliers et du charpentier : " + r.getId());
            if (r instanceof IndustrialRecipe ir && ir.machine() == IndustryMachine.SHIPYARD) {
                ir.results().forEach(res -> shipyard.add(res.stack().getItem()));
            }
            if (r instanceof BenchRecipe b && b.result().getItem() instanceof BoatItem) {
                Gt.equal(BenchType.CARPENTER, b.bench(), "bateau a un autre etabli que le charpentier : " + r.getId());
            }
        }
        List<BenchRecipe> carpenter = BenchTile.recipes(s.level, BenchType.CARPENTER);
        Set<Item> atCarpenter = new HashSet<>();
        int firstBoat = -1;
        for (int i = 0; i < carpenter.size(); i++) {
            if (carpenter.get(i).result().getItem() instanceof BoatItem) {
                atCarpenter.add(carpenter.get(i).result().getItem());
                if (firstBoat < 0) {
                    firstBoat = i;
                }
            } else {
                Gt.check(firstBoat < 0, "recette du charpentier rangee apres les bateaux : " + carpenter.get(i).getId());
            }
        }
        int boats = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            if (it instanceof BoatItem && "minecraft".equals(BuiltInRegistries.ITEM.getKey(it).getNamespace())) {
                boats++;
                Gt.check(shipyard.contains(it), "bateau sans recette au chantier naval : " + it);
                Gt.check(atCarpenter.contains(it), "bateau sans recette a l'etabli du charpentier : " + it);
            }
        }
        Gt.equal(18, boats, "8 bois x (bateau, bateau a coffre) + radeau et radeau a coffre de bambou");
        Gt.equal(18, carpenter.size() - firstBoat, "18 bateaux en fin de liste du charpentier");
        ItemStack plank = new ItemStack(Items.OAK_PLANKS), none = ItemStack.EMPTY;
        Gt.check(!PatchP105Lot32Tests.vehicle(h, PatchP105Lot32Tests.grid(h, plank, none, plank, plank, plank, plank)),
              "pas de bateau en chene a la grille (5 planches)");
        Gt.check(!PatchP105Lot32Tests.vehicle(h, PatchP105Lot32Tests.grid(h, new ItemStack(Items.CHEST), new ItemStack(Items.OAK_BOAT))),
              "pas de bateau a coffre a la grille");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_36_boat_craft", timeoutTicks = 40)
    public static void p105_36_bateau_fabrique_au_charpentier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer player = s.player("gt36barque", "Charpentier", new BlockPos(2, 2, 2));
        BlockPos at = new BlockPos(4, 2, 4);
        h.setBlock(at, PiBronze.BENCHES.get(BenchType.CARPENTER).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof BenchTile, "pas d'etabli du charpentier");
        BenchTile bench = (BenchTile) h.getBlockEntity(at);
        List<BenchRecipe> recipes = BenchTile.recipes(h.getLevel(), BenchType.CARPENTER);
        int index = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).result().is(Items.OAK_BOAT)) {
                index = i;
            }
        }
        Gt.check(index >= 0, "bateau en chene absent de l'etabli du charpentier");
        BenchRecipe r = recipes.get(index);
        Gt.equal(1, r.tools().size(), "un seul outil (la scie)");
        List<ItemSlot> slots = bench.guiSlots();
        slots.get(0).setStack(r.tools().get(0).getItems()[0].copy());
        slots.get(BenchType.TOOLS).setStack(new ItemStack(Items.OAK_PLANKS, 7));
        bench.handleCustom(player, BenchTile.SELECT + index);
        bench.handleCustom(player, BenchTile.CRAFT);
        Gt.check(bench.outputSlot().getStack().is(Items.OAK_BOAT), "bateau en chene fabrique au charpentier");
        Gt.equal(1, bench.outputSlot().getCount(), "un bateau");
        Gt.equal(2, slots.get(BenchType.TOOLS).getCount(), "5 planches consommees");
        s.done();
    }
}
