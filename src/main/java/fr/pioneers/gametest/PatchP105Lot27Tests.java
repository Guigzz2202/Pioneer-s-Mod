package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.bronze.steam.FireboxTile;
import fr.pioneers.common.projector.BlueprintBill;
import fr.pioneers.common.projector.ProjectedStructure;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.kinetics.machines.MechanicalRecipe;
import fr.pioneers.recipe.PiRecipes;
import fr.pioneers.registries.PiTransport;
import fr.pioneers.transport.CableTier;
import fr.pioneers.transport.buffer.BufferType;
import fr.pioneers.transport.trash.PiTrash;
import fr.pioneers.transport.trash.TrashCanTile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 27 (retours de jeu 3e serie, 1 : tamis, poubelle, JEI, cable ; GameTests) :
 * <ul>
 *     <li>TAMIS (retour 31) : la table chargee de chacune des 4 sources (sable, gravier, sable noir, gravier alluvial) = celle de Guillaume
 *     (produits, chances) ; tamisage STATISTIQUE par le vrai tirage ({@link MechanicalRecipe#roll} au rendement du filtre de fibres et du
 *     tamis a secousses : tier 1, x1, sans second tirage) : frequence de chaque produit dans la tolerance (5 ecarts types + 0,2 %) ; le tuf
 *     concasse donne le gravier alluvial a l'etabli du macon, au concasseur a machoires, au broyeur a vapeur et aux broyeurs ;</li>
 *     <li>POUBELLE (retour 32) : objets, fluides, gaz, energie par toutes les faces ; ni chaleur ni air comprime (aucune capacite) ; un buffer
 *     de chaleur et un buffer d'air colles ne s'y raccordent pas ;</li>
 *     <li>CHAUDIERE (retour 33) : formee sans tube de niveau (et sans la cheminee au-dessus du premier bloc) ; case du tube FACULTATIVE dans le
 *     plan et dans la nomenclature de la page JEI « Multiblocs » (pastille = blocs exiges).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot27Tests {

    /** Tables du retour 31 (= T:mechanics/data.py SIFTING) : source -> produit -> chance. */
    static final Map<String, Map<String, Double>> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("sand", table("pioneers:cassiterite_fragment", 0.50, "pioneers:malachite_fragment", 0.70, "pioneers:gold_flake", 0.02,
              "minecraft:clay_ball", 0.60));
        TABLES.put("gravel", table("pioneers:magnetite_fragment", 0.60, "pioneers:hematite_fragment", 0.40, "pioneers:malachite_fragment", 0.75,
              "pioneers:gold_flake", 0.02, "minecraft:flint", 0.10));
        TABLES.put("black_sand", table("pioneers:cassiterite_fragment", 0.70, "pioneers:magnetite_fragment", 0.30, "pioneers:hematite_fragment", 0.55,
              "pioneers:gold_flake", 0.04, "pioneers:zircon", 0.08));
        TABLES.put("alluvial_gravel", table("pioneers:malachite_fragment", 0.80, "pioneers:cassiterite_fragment", 0.65, "pioneers:gold_flake", 0.30,
              "minecraft:clay_ball", 0.40));
    }

    private PatchP105Lot27Tests() {
    }

    private static Map<String, Double> table(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return m;
    }

    private static String id(ItemStack s) {
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(s.getItem());
        return k == null ? "?" : k.toString();
    }

    private static Optional<? extends Recipe<?>> recipe(GameTestHelper h, String path) {
        return h.getLevel().getRecipeManager().byKey(new ResourceLocation("pioneers", path));
    }

    // ================================================================== 31. tamis : tables exactes et tirage statistique
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_27_sieve", timeoutTicks = 20)
    public static void p105_27_tamis_statistique(GameTestHelper h) {
        RandomSource random = RandomSource.create(27L);
        int n = 20_000;
        for (Map.Entry<String, Map<String, Double>> src : TABLES.entrySet()) {
            Optional<? extends Recipe<?>> r = recipe(h, "mechanical/sifting/" + src.getKey());
            Gt.check(r.isPresent() && r.get() instanceof MechanicalRecipe m && m.kind() == MechanicalRecipe.Kind.SIFTING, "tamisage absent : " + src.getKey());
            MechanicalRecipe m = (MechanicalRecipe) r.get();
            //La table : exactement les produits de Guillaume, une fois chacun, quantite 1, sans maille exigee
            Map<String, Double> got = new LinkedHashMap<>();
            for (MechanicalRecipe.Result res : m.results()) {
                Gt.check(got.put(id(res.stack()), (double) res.chance()) == null, src.getKey() + " : produit en double " + id(res.stack()));
                Gt.equal(1, res.stack().getCount(), src.getKey() + " : quantite de " + id(res.stack()));
                Gt.check(res.mesh() <= 1, src.getKey() + " : maille exigee pour " + id(res.stack()));
            }
            Gt.equal(src.getValue().keySet(), got.keySet(), src.getKey() + " : produits");
            for (Map.Entry<String, Double> e : src.getValue().entrySet()) {
                Gt.check(Math.abs(got.get(e.getKey()) - e.getValue()) < 1e-6, src.getKey() + " : chance de " + e.getKey() + " = " + got.get(e.getKey()));
            }
            //Tirage : rendement du filtre de fibres (tier 1) et du tamis a secousses (MechanicalProcess.craft : 1, x1, 0)
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                List<ItemStack> out = m.roll(random, 1, 1F, 0F);
                java.util.Set<String> once = new java.util.HashSet<>();
                for (ItemStack s : out) {
                    Gt.check(once.add(id(s)), src.getKey() + " : un produit tire deux fois dans la meme passe");
                    seen.merge(id(s), s.getCount(), Integer::sum);
                }
            }
            for (Map.Entry<String, Double> e : src.getValue().entrySet()) {
                double p = e.getValue(), f = seen.getOrDefault(e.getKey(), 0) / (double) n, tol = 5 * Math.sqrt(p * (1 - p) / n) + 0.002;
                Gt.check(Math.abs(f - p) <= tol, String.format(java.util.Locale.ROOT, "%s : %s tire a %.4f pour %.2f (tolerance %.4f)", src.getKey(),
                      e.getKey(), f, p, tol));
            }
            Gt.check(seen.keySet().stream().allMatch(src.getValue()::containsKey), src.getKey() + " : produit hors table " + seen.keySet());
        }
        //Tuf concasse -> gravier alluvial, a chaque machine de concassage
        String alluvial = "pioneers:alluvial_gravel";
        Optional<? extends Recipe<?>> jaw = recipe(h, "mechanical/jaw_crushing/tuff_to_alluvial_gravel");
        Gt.check(jaw.isPresent() && jaw.get() instanceof MechanicalRecipe m && m.kind() == MechanicalRecipe.Kind.JAW_CRUSHING
              && id(m.results().get(0).stack()).equals(alluvial) && m.accepts(new ItemStack(net.minecraft.world.item.Items.TUFF)), "concasseur a machoires : tuf");
        //1.0.5-beta (lot 38) : la copie du broyeur a vapeur est retiree (doublon) : il fait la recette du concasseur (famille broyage)
        Gt.check(recipe(h, "bronze/steam/macerator/tuff_alluvial_gravel").isEmpty(), "broyeur a vapeur : doublon du tuf retire");
        Gt.check(fr.pioneers.api.machine.tier.TierRecipes.byId(h.getLevel(), "broyage", fr.pioneers.Pioneers.rl("mechanical/jaw_crushing/tuff_to_alluvial_gravel")) != null,
              "broyeur a vapeur : tuf par la recette du concasseur");
        for (String path : new String[]{"bronze/bench/tuff_alluvial_gravel", "crushing/tuff"}) {
            Optional<? extends Recipe<?>> r = recipe(h, path);
            Gt.check(r.isPresent(), "recette absente : " + path);
            Gt.equal(alluvial, id(r.get().getResultItem(h.getLevel().registryAccess())), "produit de " + path);
        }
        Gt.check(!h.getLevel().getRecipeManager().getAllRecipesFor(PiRecipes.MECHANICAL.get(MechanicalRecipe.Kind.SIFTING).get()).isEmpty(), "tamisage");
        h.succeed();
    }

    // ================================================================== 32. poubelle : ni chaleur ni air comprime
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_27_trash", timeoutTicks = 20)
    public static void p105_27_poubelle_refuse_chaleur_et_air(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 1, 4);
        h.setBlock(rel, PiTrash.TRASH_CAN.getBlock());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(be instanceof TrashCanTile, "poubelle sans tuile");
        Gt.equal(List.of("items", "fluids", "gases", "energy"), List.of(TrashCanTile.RESOURCES), "ressources acceptees");
        for (Direction d : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
            String f = d == null ? "sans face" : d.getName();
            Gt.check(be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).isPresent() && be.getCapability(ForgeCapabilities.FLUID_HANDLER, d).isPresent()
                  && be.getCapability(PioneersAPI.GAS_HANDLER, d).isPresent() && be.getCapability(ForgeCapabilities.ENERGY, d).isPresent(),
                  "objets, fluides, gaz ou energie refuses : " + f);
            Gt.check(!be.getCapability(PioneersAPI.HEAT_HANDLER, d).isPresent(), "la poubelle prend la chaleur : " + f);
            Gt.check(!be.getCapability(PioneersAPI.PRESSURE_HANDLER, d).isPresent(), "la poubelle prend l'air comprime : " + f);
            Gt.check(!be.getCapability(PioneersAPI.DATA_HANDLER, d).isPresent(), "la poubelle prend la donnee : " + f);
        }
        //Buffers de chaleur et d'air colles : ils ne trouvent rien a qui donner du cote de la poubelle
        h.setBlock(rel.west(), PiTransport.buffer(BufferType.HEAT, CableTier.BASIC).getBlock());
        h.setBlock(rel.east(), PiTransport.buffer(BufferType.PRESSURE, CableTier.BASIC).getBlock());
        BlockPos abs = h.absolutePos(rel);
        Gt.check(!h.getLevel().getBlockEntity(abs).getCapability(PioneersAPI.HEAT_HANDLER, Direction.WEST).isPresent()
              && !h.getLevel().getBlockEntity(abs).getCapability(PioneersAPI.PRESSURE_HANDLER, Direction.EAST).isPresent(), "raccord chaleur / air");
        h.succeed();
    }

    // ================================================================== 33. chaudiere : tube de niveau facultatif (validation, plan, JEI)
    @GameTest(template = Gt.TALL, batch = "pioneers_p105_27_boiler", timeoutTicks = 200)
    public static void p105_27_chaudiere_sans_tube_de_niveau(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //Plan et nomenclature (meme code que la page JEI) : tube de niveau et 2e cheminee facultatifs, rien d'autre
        Map<BlockPos, ProjectedStructure.Ghost> layout = HouseShapes.BOILER.layout(0, BlockPos.ZERO, Direction.NORTH);
        Block gauge = HouseNbt.block('w', false);
        int optional = 0;
        for (ProjectedStructure.Ghost g : layout.values()) {
            if (g.optional()) {
                optional++;
                Gt.check(g.state() != null && (g.state().getBlock() == gauge || g.state().getBlock() == HouseNbt.block('k', false)),
                      "case facultative inattendue : " + g.state());
            } else if (g.state() != null) {
                Gt.check(g.state().getBlock() != gauge, "tube de niveau exige par le plan");
            }
        }
        Gt.equal(2, optional, "cases facultatives de la chaudiere en cuivre (tube de niveau, 2e cheminee)");
        BlueprintBill bill = BlueprintBill.of(HouseShapes.BOILER, 0, b -> {
            ResourceLocation k = ForgeRegistries.BLOCKS.getKey(b);
            return k == null ? null : k.getPath();
        });
        Gt.check(bill.lines().stream().anyMatch(l -> l.block() == gauge && l.optional()) && bill.lines().stream().noneMatch(l -> l.block() == gauge && !l.optional()),
              "JEI : tube de niveau pas en ligne facultative");
        Gt.equal(bill.total() - 2, bill.required(), "JEI : pastille des blocs exiges");
        Gt.check(bill.lines().stream().noneMatch(l -> l.others().contains(net.minecraft.world.level.block.Blocks.AIR)), "JEI : « accepte aussi : air »");
        //Monde : construite au plan, puis tube de niveau et 2e cheminee retires : toujours formee, aucune case fautive
        BlockPos c = MultiblockApi6Tests.buildHouse(h, HouseShapes.BOILER, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            Gt.check(h.getBlockEntity(c) instanceof FireboxTile, "pas de foyer de chaudiere en " + c);
            FireboxTile box = (FireboxTile) h.getBlockEntity(c);
            Gt.check(box.formed(), "la chaudiere construite au plan ne se forme pas");
            BlockPos w = HouseNbt.at(h.absolutePos(c), box.facing(), HouseNbt.first(FireboxTile.SHAPE, 'w'));
            Gt.check(h.getLevel().getBlockState(w).is(gauge), "tube de niveau pose par le plan");
            h.getLevel().destroyBlock(w, false);
            BlockPos k = HouseNbt.at(h.absolutePos(c), box.facing(), HouseNbt.first(FireboxTile.SHAPE, 'k')).above();
            h.getLevel().destroyBlock(k, false);
            box.requestCheck();
            h.runAfterDelay(3, () -> {
                Gt.check(box.formed(), "chaudiere defaite sans tube de niveau");
                Gt.check(HouseNbt.fault(Structure.View.of(h.getLevel()), h.absolutePos(c), box.facing(), FireboxTile.SHAPE) == null,
                      "case fautive sans tube de niveau");
                s.done();
            });
        });
    }
}
