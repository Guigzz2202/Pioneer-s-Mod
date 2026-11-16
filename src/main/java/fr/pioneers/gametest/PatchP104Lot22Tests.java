package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.construction.PiConstruction;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.construction.StackedColumnBlock.Part;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.SiloCore;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.4-beta (lot 22) : blocs empilables (rotor de turbine, train de forage, pompe a colonne MKI a MKIV : morceau d'apres les jumeaux du
 * dessus et du dessous, jamais entre deux tiers, pas une carcasse) et les quatre controleurs du lot sur leur structure PROVISOIRE (plan du
 * moteur commun bati en carcasses MKI) : la cheminee industrielle detruit la vapeur detendue sans energie ; le four a pyrolyse rendu fait
 * le gaz de cokerie et le goudron du charbon ; le four a coke ne sort plus que coke et creosote ; la plantation forestiere fait pousser un
 * chene ; le silo a objets stocke, rend par sa case de sortie et garde son stock dans sa sauvegarde.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot22Tests {

    private PatchP104Lot22Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    // ================================================================== blocs empilables

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_22_stacked", timeoutTicks = 40)
    public static void p104_22_blocs_empilables_en_colonne(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal(12, PiConstruction.stacked().size(), "12 blocs empilables");
        int x = 1;
        for (Map.Entry<String, Map<CableTier, BlockEntry<StackedColumnBlock>>> fam : PiConstruction.STACKED.entrySet()) {
            Block basic = fam.getValue().get(CableTier.BASIC).getBlock(), elite = fam.getValue().get(CableTier.ELITE).getBlock();
            Gt.check(!(basic instanceof CasingBlock), fam.getKey() + " : une carcasse");
            Gt.check(ForgeRegistries.BLOCKS.containsKey(rl(fam.getKey() + "_basic")), fam.getKey() + "_basic absent");
            //seul, puis colonne de trois, puis un bloc d'un autre tier au-dessus : il ne se raccorde pas
            BlockPos p0 = new BlockPos(x, 1, 2), p1 = p0.above(), p2 = p1.above(), p3 = p2.above();
            h.setBlock(p0, basic.defaultBlockState());
            Gt.equal(Part.SINGLE, h.getBlockState(p0).getValue(StackedColumnBlock.PART), fam.getKey() + " seul");
            h.setBlock(p1, basic.defaultBlockState());
            h.setBlock(p2, basic.defaultBlockState());
            h.setBlock(p3, elite.defaultBlockState());
            Gt.equal(Part.BOTTOM, h.getBlockState(p0).getValue(StackedColumnBlock.PART), fam.getKey() + " bas");
            Gt.equal(Part.MIDDLE, h.getBlockState(p1).getValue(StackedColumnBlock.PART), fam.getKey() + " milieu");
            Gt.equal(Part.TOP, h.getBlockState(p2).getValue(StackedColumnBlock.PART), fam.getKey() + " haut (MKIII au-dessus : autre tier)");
            Gt.equal(Part.SINGLE, h.getBlockState(p3).getValue(StackedColumnBlock.PART), fam.getKey() + " MKIII seul");
            //le milieu casse : deux morceaux
            h.setBlock(p1, Blocks.AIR.defaultBlockState());
            Gt.equal(Part.SINGLE, h.getBlockState(p0).getValue(StackedColumnBlock.PART), fam.getKey() + " bas redevenu seul");
            Gt.equal(Part.SINGLE, h.getBlockState(p2).getValue(StackedColumnBlock.PART), fam.getKey() + " haut redevenu seul");
            Gt.check(!h.getBlockState(p0).canOcclude(), fam.getKey() + " : bloc non plein (pas d'occlusion)");
            x += 2;
        }
        h.succeed();
    }

    // ================================================================== controleurs sur la structure provisoire

    private static ControllerTile formed(GameTestHelper h, IndustryMachine m) {
        BlockPos c = MultiblockApiTests.build(h, m);
        return MultiblockApiTests.controller(h, c);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_22_chimney", timeoutTicks = 400)
    public static void p104_22_cheminee_detruit_la_vapeur_detendue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(PiIndustry.controller(IndustryMachine.EXHAUST_STACK) != null, "controleur de la cheminee");
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl("industrial_chimney")) || PiRemaps.p104Lot15Block("industrial_chimney") != null,
              "industrial_chimney (lot 15) reutilise");
        ControllerTile tile = formed(h, IndustryMachine.EXHAUST_STACK);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "cheminee : la structure provisoire ne se forme pas (" + tile.guiValue(5) + ")");
            Gt.check(!tile.gasInTanks().isEmpty(), "cheminee : pas de reservoir de gaz");
            tile.gasInTanks().get(0).setStack(GasStack.of(PioneersAPI.gas(rl("exhaust_steam")), 8_000));
            tile.getEnergy().setStored(0);
            for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), IndustryMachine.EXHAUST_STACK)) {
                Gt.check(r.results().isEmpty() && r.fluidResults().isEmpty() && r.gasResults().isEmpty(), r.getId() + " : la cheminee ne rend rien");
            }
            h.succeedWhen(() -> {
                Gt.check(tile.gasInTanks().get(0).getStack().getAmount() < 8_000, "vapeur detendue pas detruite (sans energie)");
                for (var t : tile.gasOutTanks()) {
                    Gt.check(t.getStack().isEmpty(), "la cheminee rend un gaz");
                }
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_22_pyrolysis", timeoutTicks = 1200)
    public static void p104_22_four_a_pyrolyse_fait_le_gaz_de_cokerie(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(PiRemaps.p104Block("industrial_pyrolysis_oven") == null, "four a pyrolyse encore remappe vers la batterie");
        Gt.equal("industrial_pyrolysis_oven", PiRemaps.simplifiedBlock("advanced_pyrolysis_oven"), "four a pyrolyse compact -> son controleur");
        ControllerTile tile = formed(h, IndustryMachine.PYROLYSIS_OVEN);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "four a pyrolyse : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            tile.itemInputs().get(0).setStack(new ItemStack(ForgeRegistries.ITEMS.getValue(rl("coal")), 8));
            h.succeedWhen(() -> {
                tile.getEnergy().setStored(tile.getEnergy().getCapacity());
                boolean gas = tile.gasOutTanks().stream().anyMatch(t -> !t.getStack().isEmpty() && t.getStack().getGas().getRegistryName().equals(rl("coke_oven_gas")));
                boolean tar = tile.fluidOutTanks().stream().anyMatch(t -> t.getFluid().getFluid() == ForgeRegistries.FLUIDS.getValue(rl("coal_tar")));
                Gt.check(gas, "pas de gaz de cokerie");
                Gt.check(tar, "pas de goudron de houille");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_22_coke", timeoutTicks = 20)
    public static void p104_22_four_a_coke_seulement_coke_et_creosote(GameTestHelper h) {
        Gt.begin(h);
        ResourceLocation creosote = rl("creosote");
        int n = 0;
        for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), IndustryMachine.COKE_OVEN)) {
            Gt.check(r.gasResults().isEmpty(), r.getId() + " : le four a coke rend un gaz");
            for (FluidStack f : r.fluidResults()) {
                Gt.equal(creosote, ForgeRegistries.FLUIDS.getKey(f.getFluid()), r.getId() + " : autre liquide que la creosote");
            }
            Gt.check(r.family() == null, r.getId() + " : recette d'une famille absorbee au four a coke");
            n++;
        }
        Gt.check(n >= 3, "recettes du four a coke : " + n);
        h.succeed();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_22_trees", timeoutTicks = 2400)
    public static void p104_22_plantation_forestiere_fait_pousser_un_chene(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(PiRemaps.p104Block("tree_plantation") == null, "plantation encore remappee vers la ferme hydroponique");
        Gt.check(IndustryRecipes.of(h.getLevel(), IndustryMachine.HYDROPONIC_FARM).stream().noneMatch(r -> "tree_plantation".equals(r.family())),
              "sylviculture encore a la ferme hydroponique");
        ControllerTile tile = formed(h, IndustryMachine.TREE_PLANTATION);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "plantation : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            tile.itemInputs().get(0).setStack(new ItemStack(Items.OAK_SAPLING, 2));
            tile.fluidInTanks().get(0).setFluid(new FluidStack(Fluids.WATER, 8_000));
            h.succeedWhen(() -> {
                tile.getEnergy().setStored(tile.getEnergy().getCapacity());
                boolean logs = tile.itemOutputs().stream().anyMatch(o -> o.getStack().is(Items.OAK_LOG));
                Gt.check(logs, "pas de buches de chene");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_22_silo", timeoutTicks = 200)
    public static void p104_22_silo_stocke_et_rend(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ControllerTile tile = formed(h, IndustryMachine.ITEM_SILO);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "silo : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            ItemSlot in = tile.itemInputs().get(0), out = tile.itemOutputs().get(0);
            in.setStack(new ItemStack(Items.COBBLESTONE, 64));
            h.runAfterDelay(4, () -> {
                in.setStack(new ItemStack(Items.COBBLESTONE, 40));
                h.runAfterDelay(4, () -> {
                    long[] v = SiloCore.unpack(tile.guiValue(SiloCore.VALUE_STORED));
                    Gt.equal(104L, v[0], "objets tenus");
                    Gt.check(in.isEmpty(), "entree pas videe dans le stock");
                    Gt.check(out.getStack().is(Items.COBBLESTONE) && out.getCount() == 64, "sortie pas tenue pleine : " + out.getStack());
                    Gt.check(!in.isItemValid(new ItemStack(Items.DIRT)), "le silo prend un deuxieme objet");
                    Gt.check(SiloCore.capacity((int) v[1], tile.tier()) >= 104, "contenance");
                    //le stock reste dans la sauvegarde (et donc dans l'objet du controleur casse : copy_nbt « silo »)
                    CompoundTag tag = tile.saveWithoutMetadata();
                    Gt.check(tag.contains("silo"), "pas de cle silo");
                    Gt.equal(40L, tag.getCompound("silo").getLong("stored"), "stock sauve (hors case de sortie)");
                    BlockEntity copy = Gt.reloadTile(tile);
                    Gt.check(copy instanceof ControllerTile, "relecture");
                    Gt.equal(tag, copy.saveWithoutMetadata(), "sauvegarde du silo : aller-retour");
                    s.done();
                });
            });
        });
    }
}
