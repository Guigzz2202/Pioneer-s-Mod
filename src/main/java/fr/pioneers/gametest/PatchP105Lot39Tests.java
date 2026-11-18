package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.api.machine.tier.TierRecipes.TierRecipe;
import fr.pioneers.tech.bronze.autofoundry.AutoFoundryTile;
import fr.pioneers.tech.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.tech.bronze.foundry.CrucibleUnit;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 39 (retours de jeu 5e serie, 4 : four a induction et creuset ; retour 51, et retour 64 de la 6e serie : acier au
 * haut fourneau seulement ; specification docs/specification-age-bronze-1.0.5.md §4 ; GameTests) :
 * <ul>
 *     <li>FOUR A INDUCTION A VAPEUR (T1) : 3 entrees solides -> 1 lingot (bronze 3 + 1 -> 4, acier rouge a 3 entrees, alliage rouge),
 *     6 mB/t de vapeur ; famille « induction » : jamais plus de 3 entrees, 1 sortie, ni eau ni acier ;</li>
 *     <li>FOUR A INDUCTION MKI (T2) : atelier rendu (aimants alnico a 3 entrees, pour de vrai) ; recettes du T1 converties ; ni fluide ni
 *     gaz ; aucune recette d'acier ;</li>
 *     <li>CREUSET : 3 entrees d'objets (+ un gaz), laitier en sous-produit (minerai 10 %, recyclage 5 %, le metal fondu n'est pas diminue) ;
 *     acier ordinaire : plus aucune recette au creuset (seulement la refonte de l'acier) ;</li>
 *     <li>MONDES EXISTANTS : fourneau a creuset et fonderie a 4 cases de metal (4e case rangee ou posee au sol, gardee si la tile est sauvee
 *     avant son premier tick) ; creuset electrique a 4 entrees (IO_VERSION 7 -> 8 : 4e entree dans une sortie libre, sinon au sol, gardee
 *     si sauve avant le premier tick) ; lot en cours d'un creuset industriel sur une recette partie ou retiree : entrees rendues ; verrous
 *     remappes.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot39Tests {

    private PatchP105Lot39Tests() {
    }

    static ResourceLocation rl(String path) {
        return Pioneers.rl(path);
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    static int count(List<ItemSlot> slots, Item item) {
        int n = 0;
        for (ItemSlot s : slots) {
            if (s.getStack().is(item)) {
                n += s.getStack().getCount();
            }
        }
        return n;
    }

    static int ground(GameTestHelper h, BlockPos at, Item item) {
        int n = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(at)).inflate(2), e -> e.getItem().is(item))) {
            n += e.getItem().getCount();
        }
        return n;
    }

    static BlockState facing(Block block) {
        BlockState st = block.defaultBlockState();
        return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : st;
    }

    static ListTag slots(Object... slotAndStack) {
        ListTag list = new ListTag();
        for (int i = 0; i < slotAndStack.length; i += 2) {
            CompoundTag e = ((ItemStack) slotAndStack[i + 1]).save(new CompoundTag());
            e.putShort("Slot", (short) (int) (Integer) slotAndStack[i]);
            list.add(e);
        }
        return list;
    }

    static boolean steel(ItemStack st) {
        var melt = ForgeMetals.meltOf(st);
        return melt != null && "steel".equals(melt.metal());
    }

    // ------------------------------------------------------------------ 1. four a induction a vapeur (T1)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_39_induction", timeoutTicks = 20)
    public static void p105_39_induction_trois_entrees(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(3, SteamMachine.INDUCTION_FURNACE.inputs, "four a induction a vapeur : 3 entrees");
        Gt.check(Math.abs(SteamMachine.INDUCTION_FURNACE.steamUse() - 6) < 1e-9, "6 mB/t de vapeur (le double des autres)");
        Gt.check(Math.abs(SteamMachine.MACERATOR.steamUse() - 3) < 1e-9, "3 mB/t pour les autres machines a vapeur");
        List<TierRecipe> family = TierRecipes.of(s.level, "induction");
        Gt.check(family.size() >= 9, "famille induction : recettes du T1 (" + family.size() + ")");
        for (TierRecipe r : family) {
            Gt.check(r.inputs().size() <= 3 && r.outputs().size() == 1 && r.water() == 0, "induction : 3 entrees solides -> 1 objet : " + r.id());
            Gt.check(!r.outputs().get(0).stack().is(item("steel_ingot")), "aucun acier au four a induction (retour 64) : " + r.id());
        }
        SteamProcessTile f = (SteamProcessTile) place(h, new BlockPos(1, 1, 1), PiBronze.STEAM_MACHINES.get(SteamMachine.INDUCTION_FURNACE).getBlock());
        Gt.equal("induction", f.process().family, "famille du four a induction a vapeur");
        //bronze : 3 cuivre + 1 etain -> 4 lingots (rien ne se cree)
        f.inputs().get(0).setStack(new ItemStack(item("copper_ingot"), 3));
        f.inputs().get(1).setStack(new ItemStack(item("tin_ingot"), 1));
        Gt.check(f.process().craftNow(), "bronze au four a induction a vapeur");
        Gt.equal(4, count(f.guiSlots(), item("bronze_ingot")), "3 cuivre + 1 etain -> 4 lingots de bronze");
        Gt.check(f.inputs().get(0).getStack().isEmpty() && f.inputs().get(1).getStack().isEmpty(), "entrees prises");
        //acier rouge : 3 entrees (2 acier + 4 redstone + 1 boule de charbon) -> 2 lingots
        SteamProcessTile g = (SteamProcessTile) place(h, new BlockPos(7, 1, 1), PiBronze.STEAM_MACHINES.get(SteamMachine.INDUCTION_FURNACE).getBlock());
        g.inputs().get(0).setStack(new ItemStack(item("steel_ingot"), 2));
        g.inputs().get(1).setStack(new ItemStack(item("redstone"), 4));
        g.inputs().get(2).setStack(new ItemStack(item("coal_ball"), 1));
        Gt.check(g.process().craftNow(), "acier rouge au four a induction a vapeur (3 entrees)");
        Gt.equal(2, count(g.guiSlots(), item("redsteel_ingot")), "2 acier + 4 redstone + 1 boule de charbon -> 2 lingots d'acier rouge");
        //alliage rouge : 1 cuivre + 2 redstone -> 1 lingot
        SteamProcessTile r = (SteamProcessTile) place(h, new BlockPos(1, 1, 7), PiBronze.STEAM_MACHINES.get(SteamMachine.INDUCTION_FURNACE).getBlock());
        r.inputs().get(0).setStack(new ItemStack(item("copper_ingot"), 1));
        r.inputs().get(1).setStack(new ItemStack(item("redstone"), 2));
        Gt.check(r.process().craftNow(), "alliage rouge au four a induction a vapeur");
        Gt.equal(1, count(r.guiSlots(), item("red_alloy_ingot")), "1 cuivre + 2 redstone -> 1 lingot d'alliage rouge");
        //boule de charbon : combustible (4 charbons)
        Gt.check(net.minecraftforge.common.ForgeHooks.getBurnTime(new ItemStack(item("coal_ball")), null) > 0, "boule de charbon : combustible");
        s.done();
    }

    static net.minecraft.world.level.block.entity.BlockEntity place(GameTestHelper h, BlockPos at, Block block) {
        h.setBlock(at, facing(block));
        Gt.check(h.getBlockEntity(at) != null, "machine posee : " + ForgeRegistries.BLOCKS.getKey(block));
        return h.getBlockEntity(at);
    }

    // ------------------------------------------------------------------ 2. four a induction MKI (T2)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_39_induction_mk", timeoutTicks = 600)
    public static void p105_39_induction_mki(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine m = IndustryMachine.INDUCTION_FURNACE;
        Gt.check(m.itemsIn == 3 && m.itemsOut == 1 && m.fluidsIn == 0 && m.fluidsOut == 0 && m.gasesIn == 0 && m.gasesOut == 0,
              "four a induction : io 3 / 1, ni fluide ni gaz");
        Gt.check(PiIndustry.CONTROLLERS.get(m) == null, "pas de multibloc du four a induction (decision §7)");
        List<IndustrialRecipe> list = IndustryRecipes.of(s.level, m);
        Gt.check(list.stream().anyMatch(IndustrialRecipe::converted), "recettes du T1 converties au MKI");
        Gt.check(list.stream().anyMatch(x -> x.getId().equals(rl("industry/induction_furnace/alloy_invar"))), "invar au four a induction");
        for (IndustrialRecipe x : list) {
            Gt.check(x.items().size() <= 3 && x.fluids().isEmpty() && x.gases().isEmpty() && x.fluidResults().isEmpty() && x.gasResults().isEmpty()
                     && x.results().size() == 1, "four a induction : solide -> solide : " + x.getId());
            Gt.check(!x.results().get(0).stack().is(item("steel_ingot")), "aucun acier au four a induction : " + x.getId());
        }
        //aimants alnico : 3 entrees (plaques d'aluminium, de nickel, de cobalt) -> 3 aimants, pour de vrai
        IndustrialRecipe alnico = IndustryRecipes.byId(s.level, m, rl("industry/induction_furnace/rc_alnico_magnet"));
        Gt.check(alnico != null && alnico.items().size() == 3, "aimant alnico a 3 entrees au four a induction");
        AtelierTile t = (AtelierTile) place(h, new BlockPos(3, 2, 3), Gt.compact(m, CableTier.ULTIMATE));
        Gt.check(t.setLockedRecipe(alnico.getId()), "aimant alnico au selecteur du four a induction");
        for (int i = 0; i < 3; i++) {
            t.itemInputs().get(i).setStack(alnico.items().get(i).ingredient().getItems()[0].copyWithCount(alnico.items().get(i).count() * 2));
        }
        Item magnet = alnico.results().get(0).stack().getItem();
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            Gt.check(count(t.itemOutputs(), magnet) >= alnico.results().get(0).stack().getCount(), "aimants alnico faits au four a induction MKIV");
        });
    }

    // ------------------------------------------------------------------ 3. creuset : 3 entrees, laitier, plus d'acier

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_39_crucible", timeoutTicks = 20)
    public static void p105_39_creuset_trois_entrees(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine c = IndustryMachine.ELECTRIC_CRUCIBLE;
        Gt.equal(3, c.itemsIn, "creuset : 3 entrees d'objets");
        Gt.equal(1, c.gasesIn, "creuset : un gaz en entree");
        //1.0.6 : IO_VERSION 9 (broyeur, four, centre d'usinage et scie a l'io de leurs recettes) ; l'epoque du creuset reste 8
        Gt.check(IndustryMachine.IO_VERSION >= 8, "IO_VERSION 8 au moins (creuset 4 -> 3 entrees)");
        Gt.equal(3, CrucibleFurnaceTile.METAL_SLOTS, "fourneau a creuset : 3 cases de metal");
        CrucibleFurnaceTile f = (CrucibleFurnaceTile) place(h, new BlockPos(1, 1, 1), PiBronze.CRUCIBLE_FURNACE.getBlock());
        Gt.equal(2 + CrucibleFurnaceTile.METAL_SLOTS, f.guiSlots().size(), "fourneau a creuset : creuset, combustible, 3 metaux");
        for (IndustrialRecipe r : IndustryRecipes.of(s.level, c)) {
            Gt.check(r.items().size() <= 3, "creuset : 3 entrees au plus : " + r.getId());
            boolean makesSteel = r.results().stream().anyMatch(x -> x.stack().is(item("steel_ingot")))
                                 || r.fluidResults().stream().anyMatch(x -> ForgeRegistries.FLUIDS.getKey(x.getFluid()).getPath().equals("molten_steel"));
            if (makesSteel) {
                //retour 64 : seule la REFONTE de l'acier (recyclage) sort de l'acier du creuset
                for (var in : r.items()) {
                    Gt.check(steel(in.ingredient().getItems()[0]), "acier fait au creuset (retour 64) : " + r.getId());
                }
            }
        }
        for (String gone : List.of("processes/steel/induction_3", "processes/steel/oxygen_casting_8", "processes/steel/oxygen_casting_8c",
              "industry/induction_furnace/induction_steel")) {
            Gt.check(s.level.getRecipeManager().byKey(rl(gone)).isEmpty(), "recette d'acier retiree du creuset : " + gone);
        }
        //laitier : fonte du minerai 10 %, recyclage 5 % ; le rendement du tier porte sur le metal fondu
        IndustrialRecipe ore = IndustryRecipes.byId(s.level, c, rl("industry/electric_crucible/melt_raw_iron"));
        Gt.check(ore != null && ore.results().size() == 1 && ore.results().get(0).stack().is(item("slag"))
                 && Math.abs(ore.results().get(0).chance() - 0.10F) < 1e-6, "fonte du minerai : laitier 10 %");
        Gt.check(ore.yieldOnFluid() && ore.yieldedFluid(0) == 216 && ore.yieldedFluid(3) == 432, "rendement du tier sur le metal fondu (216 / 432 mB)");
        IndustrialRecipe rec = IndustryRecipes.byId(s.level, c, rl("industry/electric_crucible/recycle_iron_plate"));
        Gt.check(rec != null && rec.results().size() == 1 && Math.abs(rec.results().get(0).chance() - 0.05F) < 1e-6
                 && rec.fluidResults().get(0).getAmount() == 144, "recyclage : laitier 5 %, 144 mB (1.0.6 : 100 %)");
        //le haut fourneau reste la source de l'acier (Java) ; l'acier se refond toujours au creuset
        Gt.check(IndustryRecipes.byId(s.level, c, rl("industry/electric_crucible/melt_ingot_steel")) != null, "refonte de l'acier gardee");
        s.done();
    }

    // ------------------------------------------------------------------ 4. mondes existants : 4e case du fourneau, de la fonderie, du creuset

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_39_migration", timeoutTicks = 100)
    public static void p105_39_creusets_migres(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item copper = item("copper_ingot"), tin = item("tin_ingot"), lead = item("lead_ingot"), zinc = item("zinc_ingot");
        //a) fourneau a creuset d'avant : cases 0 creuset, 1 combustible, 2..5 metal ; la 4e (case 5) va dans une case libre
        BlockPos a = new BlockPos(1, 1, 1);
        CrucibleFurnaceTile fa = (CrucibleFurnaceTile) place(h, a, PiBronze.CRUCIBLE_FURNACE.getBlock());
        CompoundTag ta = fa.saveWithoutMetadata();
        ta.put("slots", slots(2, new ItemStack(copper, 3), 5, new ItemStack(tin, 5)));
        fa.load(ta);
        Gt.equal(1, fa.furnace().legacy().size(), "4e case relue (a ranger)");
        //sauvee AVANT le premier tick : gardee (cle legacy_metal)
        CompoundTag saved = fa.saveWithoutMetadata();
        Gt.check(saved.getList(CrucibleUnit.LEGACY, 10).size() == 1, "4e case gardee si le fourneau est sauve avant son premier tick");
        fa.serverTick(s.level);
        Gt.equal(5, count(fa.guiSlots(), tin), "4e case rangee dans une case de metal libre");
        Gt.equal(3, count(fa.guiSlots(), copper), "1re case gardee");
        Gt.check(fa.furnace().legacy().isEmpty(), "plus rien a ranger");
        //b) toutes les cases occupees : la 4e est posee au sol, rien de perdu ; relue d'une sauvegarde faite avant le premier tick
        BlockPos b = new BlockPos(7, 1, 1);
        CrucibleFurnaceTile fb = (CrucibleFurnaceTile) place(h, b, PiBronze.CRUCIBLE_FURNACE.getBlock());
        CompoundTag tb = fb.saveWithoutMetadata();
        tb.put("slots", slots(2, new ItemStack(copper, 64), 3, new ItemStack(tin, 64), 4, new ItemStack(zinc, 64), 5, new ItemStack(lead, 7)));
        fb.load(tb);
        CompoundTag early = fb.saveWithoutMetadata();
        CrucibleFurnaceTile fb2 = (CrucibleFurnaceTile) place(h, b, PiBronze.CRUCIBLE_FURNACE.getBlock());
        fb2.load(early);
        fb2.serverTick(s.level);
        Gt.equal(7, ground(h, b, lead), "4e case posee au sol (cases pleines)");
        Gt.equal(64 * 3, count(fb2.guiSlots(), copper) + count(fb2.guiSlots(), tin) + count(fb2.guiSlots(), zinc), "3 cases gardees");
        //c) fonderie automatique d'avant : cases 0 combustible, 1..4 metal ; la 4e (case 4) va dans une case libre
        BlockPos c = new BlockPos(1, 1, 7);
        AutoFoundryTile fc = (AutoFoundryTile) place(h, c, PiBronze.AUTO_FOUNDRY.getBlock());
        CompoundTag tc = fc.saveWithoutMetadata();
        tc.put("slots", slots(1, new ItemStack(copper, 2), 4, new ItemStack(tin, 6)));
        fc.load(tc);
        fc.serverTick(s.level);
        Gt.equal(6, count(fc.guiSlots(), tin) + ground(h, c, tin), "fonderie : 4e case rangee (ou au sol), rien de perdu");
        Gt.equal(6, count(fc.guiSlots(), tin), "fonderie : 4e case rangee dans une case libre");
        //d) creuset electrique MKI d'avant (IO_VERSION 7 : 4 entrees, 2 sorties, batterie) : la 4e entree va a une sortie libre
        BlockPos d = new BlockPos(7, 2, 7);
        AtelierTile cd = (AtelierTile) place(h, d, PiIndustry.ATELIERS.get(IndustryMachine.ELECTRIC_CRUCIBLE).get(CableTier.BASIC).getBlock());
        Item crushed = item("crushed_iron"), coke = item("coke"), lime = item("quicklime"), raw = item("raw_copper"), slag = item("slag");
        Item pig = item("pig_iron");
        CompoundTag td = cd.saveWithoutMetadata();
        td.putInt(IoMigration.NBT, 7);
        td.put("items", slots(0, new ItemStack(crushed, 5), 1, new ItemStack(coke, 3), 2, new ItemStack(lime, 2), 3, new ItemStack(raw, 4),
              4, new ItemStack(slag, 1)));
        cd.load(td);
        Gt.equal(5, count(cd.itemInputs(), crushed), "1re entree gardee");
        Gt.equal(3, count(cd.itemInputs(), coke), "2e entree gardee");
        Gt.equal(2, count(cd.itemInputs(), lime), "3e entree gardee");
        Gt.equal(4, count(cd.itemOutputs(), raw), "4e entree dans la sortie libre");
        Gt.equal(1, count(cd.itemOutputs(), slag), "sortie d'avant gardee");
        Gt.equal(IndustryMachine.IO_VERSION, cd.saveWithoutMetadata().getInt(IoMigration.NBT), "marque de version 8");
        //e) les deux sorties occupees : la 4e entree est rendue au sol au premier tick ; gardee si sauvee avant
        CompoundTag te = cd.saveWithoutMetadata();
        te.putInt(IoMigration.NBT, 7);
        te.put("items", slots(0, new ItemStack(crushed, 5), 1, new ItemStack(coke, 3), 2, new ItemStack(lime, 2), 3, new ItemStack(raw, 4),
              4, new ItemStack(slag, 1), 5, new ItemStack(pig, 2)));
        cd.load(te);
        CompoundTag kept = cd.saveWithoutMetadata();
        Gt.check(kept.getList(IoMigration.OVERFLOW, 10).size() == 1, "4e entree sans place gardee si le creuset est sauve avant son premier tick");
        AtelierTile ce = (AtelierTile) place(h, d, PiIndustry.ATELIERS.get(IndustryMachine.ELECTRIC_CRUCIBLE).get(CableTier.BASIC).getBlock());
        ce.load(kept);
        Gt.equal(3, count(ce.itemOutputs(), slag) + count(ce.itemOutputs(), pig), "sorties gardees");
        h.runAfterDelay(5, () -> {
            Gt.equal(4, ground(h, d, raw), "4e entree rendue au sol, une seule fois");
            Gt.check(ce.saveWithoutMetadata().getList(IoMigration.OVERFLOW, 10).isEmpty(), "plus rien a rendre");
            s.done();
        });
    }

    // ------------------------------------------------------------------ 5. lots en cours et verrous d'une recette partie ou retiree

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_39_refund", timeoutTicks = 20)
    public static void p105_39_lots_rendus(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (Map.Entry<String, String> e : PiRemaps.P105_LOT39_RECIPES.entrySet()) {
            Gt.check(s.level.getRecipeManager().byKey(rl(e.getValue())).isPresent(), "remplacante chargee : " + e.getValue());
            Gt.check(s.level.getRecipeManager().byKey(rl(e.getKey())).isEmpty(), "ancienne recette absente : " + e.getKey());
            Gt.equal(rl(e.getValue()), PiRemaps.recipeId(rl(e.getKey())), "verrou remappe : " + e.getKey());
        }
        //1.0.5-beta (lot 42) : les lots rendus sont dans la table generee GeneratedBatchRefund (un seul mecanisme : BatchRefund)
        int seen = 0;
        for (Map.Entry<String, String[]> e : fr.pioneers.tech.industry.multi.GeneratedBatchRefund.TABLE.entrySet()) {
            if (!e.getKey().startsWith("electric_crucible|")) {
                continue;
            }
            seen++;
            fr.pioneers.tech.industry.multi.BatchRefund.Inputs in = fr.pioneers.tech.industry.multi.BatchRefund.parse(e.getValue());
            Gt.check(!in.items().isEmpty(), "entrees rendues : " + e.getKey());
            for (fr.pioneers.tech.industry.multi.BatchRefund.Ref x : in.items()) {
                Gt.check(fr.pioneers.tech.industry.multi.BatchRefund.item(x.ref()) != net.minecraft.world.item.Items.AIR, "objet rendu connu : " + x.ref());
                Gt.check(x.count() > 0, "nombre rendu : " + e.getKey());
            }
            //la recette n'est plus au creuset (partie au four a induction, ou retiree)
            String path = e.getKey().substring("electric_crucible|".length());
            Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.ELECTRIC_CRUCIBLE, rl(path)) == null, "encore au creuset : " + path);
        }
        Gt.check(seen >= 27, "lots du creuset dans la table : " + seen);
        //un creuset industriel dont le lot en cours (2 operations) etait l'acier par induction : entrees rendues dans ses tampons de sortie
        ControllerTile ctrl = (ControllerTile) place(h, new BlockPos(3, 2, 3), PiIndustry.CONTROLLERS.get(IndustryMachine.ELECTRIC_CRUCIBLE).getBlock());
        Gt.check(fr.pioneers.tech.industry.multi.BatchRefund.of(ctrl, rl("industry/induction_furnace/induction_steel"), 2), "lot d'acier par induction rendu");
        Gt.equal(4, count(ctrl.itemOutputs(), item("iron_ingot")), "2 operations : 4 lingots de fer rendus");
        Gt.equal(2, count(ctrl.itemOutputs(), item("coke")), "2 operations : 2 cokes rendus");
        Gt.check(!fr.pioneers.tech.industry.multi.BatchRefund.of(ctrl, rl("industry/electric_crucible/melt_raw_iron"), 1), "recette gardee : rien a rendre");
        s.done();
    }
}
