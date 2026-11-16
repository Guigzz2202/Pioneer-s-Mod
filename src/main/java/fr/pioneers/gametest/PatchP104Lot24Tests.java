package fr.pioneers.gametest;

import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.tech.bronze.foundry.KilnTile;
import fr.pioneers.tech.bronze.steam.BoilerPart;
import fr.pioneers.tech.bronze.steam.BoilerPartBlock;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.bronze.CokeOvenTile;
import fr.pioneers.tech.industry.construction.FireboxWallBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.ShapeTemplate;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 24, GameTests) : les STRUCTURES DE GUILLAUME (NBT) appliquees aux multiblocs. Un echantillon par famille construit selon
 * son plan (celui de la projection et de JEI, tire du NBT) se FORME et TOURNE UNE RECETTE : four industriel (parois de foyer, allumees en
 * marche et eteintes a la rupture), broyeur (bassin), graveur optique (salle blanche : tier fixe), ferme hydroponique (substrat, verre),
 * assembleur (chaine ouverte) ; monde d'avant : un controleur sauve a l'ancienne forme se relit « Non forme » sans rien perdre. Age du bronze :
 * four de potier et four a coke primitif devenus multiblocs (formation, rupture), chaudiere (cheminee de hauteur libre, dome teinte par le
 * corps du dessous), foreuse a vapeur (controleur au bord, train en bronze au centre).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot24Tests {

    private PatchP104Lot24Tests() {
    }

    /** Premiere recette faisable au tier de la structure, sans gaz ni chaleur, pression ou air exiges, et qui tient dans les cases. */
    private static IndustrialRecipe pick(GameTestHelper h, IndustryMachine m, ControllerTile tile) {
        for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), m)) {
            if (r.tier() <= tile.tier() && r.gases().isEmpty() && r.temperature() <= 0 && r.pressure() <= 1 && r.air() <= 0 && !r.items().isEmpty()
                && r.items().size() <= tile.itemInputs().size() && r.fluids().size() <= tile.fluidInTanks().size() && r.data().isEmpty()
                && r.items().stream().allMatch(c -> c.ingredient().getItems().length > 0)
                && r.fluids().stream().allMatch(f -> !f.displayed().isEmpty())) {
                return r;
            }
        }
        return null;
    }

    /** Construit le multibloc selon son plan, le remplit pour une recette et attend un produit. */
    private static void runs(GameTestHelper h, IndustryMachine m, Runnable extra) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(GeneratedNbtShapes.of(m.key) != null, m.multiId() + " : pas de structure NBT");
        BlockPos c = MultiblockApiTests.build(h, m);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), m.multiId() + " : la structure de Guillaume ne se forme pas (" + tile.guiValue(5) + ")");
            IndustrialRecipe r = pick(h, m, tile);
            Gt.check(r != null, m.multiId() + " : aucune recette simple a faire");
            for (int i = 0; i < r.items().size(); i++) {
                ItemStack one = r.items().get(i).ingredient().getItems()[0];
                tile.itemInputs().get(i).setStack(one.copyWithCount(Math.min(one.getMaxStackSize(), r.items().get(i).count() * 4)));
            }
            for (int i = 0; i < r.fluids().size(); i++) {
                FluidStack f = r.fluids().get(i).displayed().get(0);
                tile.fluidInTanks().get(i).setFluid(new FluidStack(f.getFluid(), r.fluids().get(i).amount() * 4));
            }
            h.succeedWhen(() -> {
                tile.getEnergy().setStored(tile.getEnergy().getCapacity());
                boolean made = tile.itemOutputs().stream().anyMatch(o -> !o.isEmpty()) || tile.fluidOutTanks().stream().anyMatch(t -> !t.isEmpty())
                               || tile.gasOutTanks().stream().anyMatch(t -> !t.getStack().isEmpty());
                Gt.check(made, m.multiId() + " : rien produit par " + r.getId());
                extra.run();
                s.done();
            });
        });
    }

    /** Parois de foyer de la structure formee d'un controleur. */
    private static List<BlockPos> fireboxes(GameTestHelper h, ControllerTile tile) {
        List<BlockPos> out = new ArrayList<>();
        if (tile.structure() != null && tile.structure().members() != null) {
            for (BlockPos p : tile.structure().members()) {
                if (h.getLevel().getBlockState(p).getBlock() instanceof FireboxWallBlock) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_furnace", timeoutTicks = 1200)
    public static void p104_24_four_industriel_forme_cuit_et_allume_ses_foyers(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine m = IndustryMachine.FURNACE;
        BlockPos c = MultiblockApiTests.build(h, m);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "four industriel : la structure de Guillaume ne se forme pas (" + tile.guiValue(5) + ")");
            List<BlockPos> fire = fireboxes(h, tile);
            Gt.equal(7, fire.size(), "parois de foyer du four (NBT : 7)");
            for (BlockPos p : fire) {
                Gt.check(!h.getLevel().getBlockState(p).getValue(FireboxWallBlock.LIT), "foyer allume a l'arret");
            }
            IndustrialRecipe r = pick(h, m, tile);
            Gt.check(r != null, "four : aucune recette simple");
            ItemStack one = r.items().get(0).ingredient().getItems()[0];
            tile.itemInputs().get(0).setStack(one.copyWithCount(Math.min(one.getMaxStackSize(), r.items().get(0).count() * 8)));
            h.succeedWhen(() -> {
                tile.getEnergy().setStored(tile.getEnergy().getCapacity());
                Gt.check(fire.stream().allMatch(p -> h.getLevel().getBlockState(p).getValue(FireboxWallBlock.LIT)), "foyers eteints en marche (recette " + r.getId() + ", entree "
                      + tile.itemInputs().get(0).getStack() + ", sorties " + tile.itemOutputs().stream().map(o -> o.getStack().toString()).toList()
                      + ", etat " + tile.guiValue(3) + ")");
                //Rupture : une paroi de foyer cassee, les autres s'eteignent
                h.getLevel().destroyBlock(fire.get(0), false);
                tile.requestCheck();
                Gt.later(h, 3, () -> {
                    Gt.check(!tile.formed(), "four toujours forme sans une paroi de foyer");
                    for (int i = 1; i < fire.size(); i++) {
                        Gt.check(!h.getLevel().getBlockState(fire.get(i)).getValue(FireboxWallBlock.LIT), "foyer encore allume apres la rupture");
                    }
                    s.done();
                });
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_crusher", timeoutTicks = 1200)
    public static void p104_24_broyeur_forme_et_broie(GameTestHelper h) {
        runs(h, IndustryMachine.CRUSHER, () -> {
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_assembler", timeoutTicks = 1200)
    public static void p104_24_assembleur_chaine_ouverte_forme_et_assemble(GameTestHelper h) {
        runs(h, IndustryMachine.ASSEMBLER, () -> {
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_hydro", timeoutTicks = 2400)
    public static void p104_24_ferme_hydroponique_substrat_forme_et_pousse(GameTestHelper h) {
        runs(h, IndustryMachine.HYDROPONIC_FARM, () -> {
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_litho", timeoutTicks = 1200)
    public static void p104_24_graveur_salle_blanche_tier_fixe(GameTestHelper h) {
        IndustryMachine m = IndustryMachine.PHOTOLITHOGRAPH;
        ShapeTemplate t = ShapeTemplate.of(m).get(0);
        Gt.check(t.nbt && t.fixedTier >= 0, "graveur : structure de salle blanche a tier fixe");
        runs(h, m, () -> {
        });
    }

    /** Monde d'avant : un controleur a l'ancienne forme (ici : sans sa structure) se relit non forme, son contenu garde. */
    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_old", timeoutTicks = 100)
    public static void p104_24_ancienne_forme_non_formee_contenu_garde(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine m = IndustryMachine.CRUSHER;
        BlockPos c = MultiblockApiTests.build(h, m);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "broyeur forme");
            tile.itemInputs().get(0).setStack(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 17));
            CompoundTag saved = tile.saveWithoutMetadata();
            //« Ancienne forme » : la structure autour est defaite (carcasses de la facade otees), le controleur relu
            for (int cell : ShapeTemplate.of(m).get(0).cells()) {
                if (ShapeTemplate.y(cell) == 0 && ShapeTemplate.z(cell) == 0 && ShapeTemplate.x(cell) != 0) {
                    BlockPos p = fr.pioneers.api.multiblock.StructureMatcher.set(new BlockPos.MutableBlockPos(), h.absolutePos(c), Direction.SOUTH,
                          Direction.SOUTH.getClockWise(), ShapeTemplate.x(cell), 0, 0).immutable();
                    h.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                }
            }
            BlockState state = h.getBlockState(c);
            h.setBlock(c, Blocks.AIR.defaultBlockState());
            h.setBlock(c, state);
            ControllerTile again = MultiblockApiTests.controller(h, c);
            again.load(saved);
            again.requestCheck();
            Gt.later(h, 3, () -> {
                Gt.check(!again.formed(), "ancienne forme : formee a tort");
                Gt.check(again.guiValue(5) != Structure.Error.NONE.ordinal(), "ancienne forme : pas d'erreur commune");
                Gt.equal(17, again.itemInputs().get(0).getCount(), "contenu du controleur garde");
                s.done();
            });
        });
    }

    // ------------------------------------------------------------------ age du bronze

    /** Construit un plan maison (convention : avant du plan = facade du controleur) ; rend le controleur. */
    private static BlockPos house(GameTestHelper h, fr.pioneers.core.common.projector.Blueprint plan, Direction facing) {
        return MultiblockApi6Tests.buildHouse(h, plan, facing);
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_kiln", timeoutTicks = 200)
    public static void p104_24_four_de_potier_multibloc(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = house(h, HouseShapes.POTTERY_KILN, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(c);
            Gt.check(be instanceof KilnTile, "pas de four de potier en " + c);
            KilnTile kiln = (KilnTile) be;
            Gt.check(kiln.formed(), "le four de potier construit au plan ne se forme pas");
            Gt.check(KilnTile.fault(Structure.View.of(h.getLevel()), h.absolutePos(c), kiln.facing()) == null, "plan du four : case fautive");
            //Une brique du socle otee : non forme
            HouseNbt.Cell brick = HouseNbt.first(KilnTile.SHAPE, 'r');
            BlockPos p = HouseNbt.at(h.absolutePos(c), kiln.facing(), brick);
            h.getLevel().destroyBlock(p, false);
            kiln.requestCheck();
            Gt.later(h, 3, () -> {
                Gt.check(!kiln.formed(), "four de potier forme sans une brique");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_coke", timeoutTicks = 200)
    public static void p104_24_four_a_coke_primitif_multibloc(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = house(h, HouseShapes.COKE_OVEN, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(c);
            Gt.check(be instanceof CokeOvenTile, "pas de four a coke en " + c);
            CokeOvenTile oven = (CokeOvenTile) be;
            Gt.check(oven.formed(), "le four a coke construit au plan ne se forme pas");
            HouseNbt.Cell t = HouseNbt.first(CokeOvenTile.SHAPE, 'T');
            Gt.check(t != null && h.getLevel().getBlockState(HouseNbt.at(h.absolutePos(c), oven.facing(), t)).is(PiBronze.TUYERE.getBlock()),
                  "tuyere du four a coke a l'arriere");
            h.getLevel().destroyBlock(HouseNbt.at(h.absolutePos(c), oven.facing(), t), false);
            oven.requestCheck();
            Gt.later(h, 3, () -> {
                Gt.check(!oven.formed(), "four a coke forme sans sa tuyere");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_boiler", timeoutTicks = 200)
    public static void p104_24_chaudiere_cheminee_et_dome(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = house(h, HouseShapes.BOILER, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(c);
            Gt.check(be instanceof FireboxTile, "pas de foyer de chaudiere en " + c);
            FireboxTile box = (FireboxTile) be;
            Gt.check(box.formed(), "la chaudiere construite au plan ne se forme pas");
            Gt.equal(11, box.waterCapacity() / BoilerPart.WATER, "11 corps");
            double draft = box.draft();
            //Cheminee plus haute : meilleur tirage (comme avant)
            HouseNbt.Cell k = HouseNbt.first(FireboxTile.SHAPE, 'k');
            BlockPos top = HouseNbt.at(h.absolutePos(c), box.facing(), k).above(2);
            BlockState chimney = PiBronze.BOILER_PARTS.get(BoilerPart.CHIMNEY).getBlock().defaultBlockState();
            h.getLevel().setBlock(top, chimney, 3);
            h.getLevel().setBlock(top.above(), chimney, 3);
            box.requestCheck();
            //Dome : cuivre sur un corps en cuivre
            HouseNbt.Cell d = HouseNbt.first(FireboxTile.SHAPE, 'd');
            BlockPos dome = HouseNbt.at(h.absolutePos(c), box.facing(), d);
            Gt.check(!h.getLevel().getBlockState(dome).getValue(BoilerPartBlock.STEEL), "dome sur du cuivre : virole en acier a tort");
            Gt.later(h, 3, () -> {
                Gt.check(box.formed(), "chaudiere defaite par une cheminee plus haute");
                Gt.check(box.draft() > draft, "cheminee plus haute : tirage pas meilleur");
                //Corps du dessous en acier : le dome prend la couleur de l'acier
                h.getLevel().setBlock(dome.below(), PiBronze.BOILER_PARTS.get(BoilerPart.STEEL_SHELL).getBlock().defaultBlockState(), 3);
                Gt.later(h, 2, () -> {
                    Gt.check(h.getLevel().getBlockState(dome).getValue(BoilerPartBlock.STEEL), "dome sur de l'acier : virole pas grise");
                    s.done();
                });
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_24_drill", timeoutTicks = 200)
    public static void p104_24_foreuse_a_vapeur_controleur_au_bord(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = house(h, HouseShapes.STEAM_DRILL, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(c);
            Gt.check(be instanceof fr.pioneers.world.geology.drill.SteamDrillTile, "pas de foreuse en " + c);
            fr.pioneers.world.geology.drill.SteamDrillTile drill = (fr.pioneers.world.geology.drill.SteamDrillTile) be;
            Gt.check(drill.formed(), "la foreuse construite au plan ne se forme pas");
            Gt.check(!drill.center().equals(drill.getBlockPos()), "le centre de la foreuse n'est plus le controleur");
            Gt.check(h.getLevel().getBlockState(drill.center()).is(fr.pioneers.tech.industry.construction.PiConstruction.DRILL_STRING_BRONZE.getBlock()),
                  "train de forage en bronze au centre");
            s.done();
        });
    }
}
