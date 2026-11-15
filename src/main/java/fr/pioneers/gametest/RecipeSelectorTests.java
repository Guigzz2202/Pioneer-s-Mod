package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.tech.computing.ComputerLib;
import fr.pioneers.tech.computing.script.Compiler;
import fr.pioneers.tech.computing.script.RecipeLockFns;
import fr.pioneers.tech.computing.script.Value;
import fr.pioneers.tech.computing.script.Vm;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * MAJ Recettes (lot 7, GameTests) : le selecteur de recette sur une vraie machine d'atelier : verrou pose (et refuse hors des recettes de la
 * machine), garde quand on casse et repose la machine, entonnoir qui ne peut plus deposer une entree etrangere a la recette verrouillee,
 * reglage par PioScript ; machine de tier insuffisant : message « Tier insuffisant » et rien de consomme. Les machines et recettes sont
 * choisies d'apres les recettes chargees (elles changent d'une MAJ a l'autre).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class RecipeSelectorTests {

    private RecipeSelectorTests() {
    }

    /** Deux recettes d'une machine d'atelier dont chacune a un objet que l'autre ne prend pas (a, b) et la machine. */
    private record Pair(IndustryMachine machine, RecipeChoice a, RecipeChoice b, ItemStack onlyA, ItemStack onlyB) {}

    @Nullable
    private static ItemStack only(RecipeChoice in, RecipeChoice other) {
        for (Counted c : in.items()) {
            for (ItemStack s : c.ingredient().getItems()) {
                if (!other.takesItem(s)) {
                    return s.copyWithCount(1);
                }
            }
        }
        return null;
    }

    /** Une machine d'atelier MKI posee au point relatif {@code rel}. */
    private static AtelierTile atelier(GameTestHelper h, BlockPos rel, IndustryMachine machine, CableTier tier) {
        Block block = PiIndustry.ATELIERS.get(machine).get(tier).getBlock();
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier pour " + machine.key);
        return (AtelierTile) h.getBlockEntity(rel);
    }

    /** Premiere machine d'atelier a deux recettes MKI distinguables par leurs objets (assembleur d'abord). */
    private static Pair pair(GameTestHelper h) {
        List<IndustryMachine> order = new ArrayList<>(PiIndustry.ATELIERS.keySet());
        order.remove(IndustryMachine.ASSEMBLER);
        order.add(0, IndustryMachine.ASSEMBLER);
        for (IndustryMachine m : order) {
            AtelierTile probe = atelier(h, new BlockPos(1, 2, 1), m, CableTier.BASIC);
            List<RecipeChoice> choices = probe.recipeChoices();
            for (int i = 0; i < choices.size(); i++) {
                RecipeChoice a = choices.get(i);
                if (a.tier() > 0 || a.items().isEmpty()) {
                    continue;
                }
                for (int j = i + 1; j < Math.min(choices.size(), i + 40); j++) {
                    RecipeChoice b = choices.get(j);
                    if (b.tier() > 0 || b.items().isEmpty()) {
                        continue;
                    }
                    ItemStack oa = only(a, b), ob = only(b, a);
                    if (oa != null && ob != null) {
                        h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
                        return new Pair(m, a, b, oa, ob);
                    }
                }
            }
            h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        }
        throw new AssertionError("aucune machine d'atelier a deux recettes distinctes");
    }

    // ================================================================== verrou pose
    /** Une recette de la machine se verrouille ; une recette etrangere ou inconnue est refusee (le verrou ne change pas). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_selecteur_verrou", timeoutTicks = 40)
    public static void selecteur_verrou_pose(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Pair p = pair(h);
        AtelierTile m = atelier(h, new BlockPos(4, 2, 4), p.machine(), CableTier.BASIC);
        Gt.check(m.lockedRecipe() == null, "verrou pose sur une machine neuve");
        Gt.check(m.setLockedRecipe(p.a().id()), "recette de la machine refusee");
        Gt.equal(p.a().id(), m.lockedRecipe(), "recette verrouillee");
        Gt.check(!m.setLockedRecipe(ResourceLocation.fromNamespaceAndPath("pioneers", "nope/inconnue")), "recette inconnue acceptee");
        Gt.equal(p.a().id(), m.lockedRecipe(), "verrou change par une recette refusee");
        Gt.check(m.lockedChoice() != null && m.lockedChoice().id().equals(p.a().id()), "recette verrouillee introuvable dans la liste");
        Gt.check(m.setLockedRecipe(null) && m.lockedRecipe() == null, "verrou non retire");
        s.done();
    }

    // ================================================================== garde apres casse et repose
    /** Casse : l'objet garde le verrou (BlockEntityTag) ; repose : la nouvelle machine le reprend. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_selecteur_casse", timeoutTicks = 40)
    public static void selecteur_garde_apres_casse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Pair p = pair(h);
        BlockPos rel = new BlockPos(4, 2, 4);
        AtelierTile m = atelier(h, rel, p.machine(), CableTier.BASIC);
        Gt.check(m.setLockedRecipe(p.b().id()), "recette refusee");
        BlockPos abs = h.absolutePos(rel);
        BlockState state = h.getBlockState(rel);
        List<ItemStack> drops = Block.getDrops(state, h.getLevel(), abs, m);
        ItemStack machineItem = drops.stream().filter(d -> d.getItem() == state.getBlock().asItem()).findFirst().orElse(ItemStack.EMPTY);
        Gt.check(!machineItem.isEmpty(), "la machine cassee ne tombe pas");
        Gt.equal(p.b().id(), RecipeLock.of(machineItem), "verrou perdu dans l'objet");
        h.setBlock(rel, Blocks.AIR);
        atelier(h, rel, p.machine(), CableTier.BASIC);
        BlockItem.updateCustomBlockEntityTag(h.getLevel(), null, abs, machineItem);
        AtelierTile again = (AtelierTile) h.getBlockEntity(rel);
        Gt.equal(p.b().id(), again.lockedRecipe(), "verrou perdu a la repose");
        s.done();
    }

    // ================================================================== entonnoir et entree etrangere
    /** Machine verrouillee sur A : un entonnoir y depose l'ingredient de A, jamais celui de B (il reste dans l'entonnoir). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_selecteur_entonnoir", timeoutTicks = 120)
    public static void selecteur_entonnoir_refuse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Pair p = pair(h);
        BlockPos rel = new BlockPos(4, 2, 4), hop = rel.above();
        AtelierTile m = atelier(h, rel, p.machine(), CableTier.BASIC);
        SideConfig sides = m.getSideConfig();
        for (RelativeSide r : RelativeSide.values()) {
            sides.set(SideType.ITEMS, r, SideMode.INPUT);
        }
        //Sans verrou, l'ingredient de B entrerait
        Gt.check(m.itemInputs().get(0).isItemValid(p.onlyB()), "l'ingredient de B n'est pas une entree de la machine");
        Gt.check(m.setLockedRecipe(p.a().id()), "recette A refusee");
        Gt.check(!m.itemInputs().get(0).isItemValid(p.onlyB()), "filtre de la case : l'ingredient etranger passe");
        h.setBlock(hop, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        Container hopper = (Container) h.getBlockEntity(hop);
        hopper.setItem(0, p.onlyB().copyWithCount(4));
        hopper.setItem(1, p.onlyA().copyWithCount(4));
        h.runAfterDelay(80, () -> {
            int foreign = 0, own = 0;
            for (int i = 0; i < hopper.getContainerSize(); i++) {
                ItemStack st = hopper.getItem(i);
                if (ItemStack.isSameItem(st, p.onlyB())) {
                    foreign += st.getCount();
                }
                if (ItemStack.isSameItem(st, p.onlyA())) {
                    own += st.getCount();
                }
            }
            int inside = 0;
            for (ItemSlot slot : m.itemInputs()) {
                Gt.check(!ItemStack.isSameItem(slot.getStack(), p.onlyB()), "l'entree etrangere est entree dans la machine");
                if (ItemStack.isSameItem(slot.getStack(), p.onlyA())) {
                    inside += slot.getCount();
                }
            }
            Gt.equal(4, foreign, "entree etrangere sortie de l'entonnoir");
            Gt.check(inside > 0 && own + inside == 4, "l'ingredient de la recette verrouillee n'est pas entre");
            s.done();
        });
    }

    // ================================================================== reglage par PioScript
    /** m.lockRecipe(id) / m.lockedRecipe() / m.unlockRecipe() sur une vraie machine (meme objet que link(n) et network.machine(n)). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_selecteur_pioscript", timeoutTicks = 40)
    public static void selecteur_reglage_pioscript(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Pair p = pair(h);
        AtelierTile m = atelier(h, new BlockPos(4, 2, 4), p.machine(), CableTier.BASIC);
        List<String> out = new ArrayList<>();
        Vm vm = new Vm(Compiler.compile("print(m.lockRecipe('" + p.a().id() + "'), m.lockedRecipe())\nprint(m.lockRecipe('pioneers:nope/x'))\n"
              + "print(m.unlockRecipe(), m.lockedRecipe())\nprint(m.lockRecipe('" + p.b().id() + "'))"), new Vm.Host() {
            @Override
            public void print(String text) {
                out.add(text);
            }

            @Override
            public long now() {
                return 0;
            }
        });
        vm.define("m", RecipeLockFns.install(new Value.PObject("link"), () -> ComputerLib.recipeLockTarget(m)));
        for (int i = 0; i < 20 && vm.state() != Vm.State.DONE && vm.state() != Vm.State.ERROR; i++) {
            vm.run(5000);
        }
        Gt.equal(Vm.State.DONE, vm.state(), "programme PioScript");
        Gt.equal(List.of("True " + p.a().id(), "False", "True None", "True"), out, "sorties du programme");
        Gt.equal(p.b().id(), m.lockedRecipe(), "verrou pose par PioScript");
        s.done();
    }

    // ================================================================== tier insuffisant
    /** MKI verrouillee sur une recette MKII ou plus, entrees fournies : « Tier insuffisant », rien de consomme ni produit. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_tier_insuffisant", timeoutTicks = 120)
    public static void tier_insuffisant_rien_consomme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine machine = null;
        RecipeChoice high = null;
        BlockPos rel = new BlockPos(4, 2, 4);
        for (IndustryMachine mm : PiIndustry.ATELIERS.keySet()) {
            AtelierTile probe = atelier(h, rel, mm, CableTier.BASIC);
            for (RecipeChoice c : probe.recipeChoices()) {
                if (c.tier() > 0 && !c.items().isEmpty() && c.fluids().isEmpty() && c.gases().isEmpty() && c.data().isEmpty()
                    && c.items().size() <= probe.itemInputs().size()) {
                    machine = mm;
                    high = c;
                    break;
                }
            }
            if (high != null) {
                break;
            }
        }
        Gt.check(high != null, "aucune recette d'atelier au-dessus du MKI");
        AtelierTile m = atelier(h, rel, machine, CableTier.BASIC);
        Gt.check(m.setLockedRecipe(high.id()), "recette refusee");
        List<ItemStack> given = new ArrayList<>();
        for (int i = 0; i < high.items().size(); i++) {
            Counted c = high.items().get(i);
            ItemStack st = c.ingredient().getItems()[0].copyWithCount(c.count());
            m.itemInputs().get(i).setStack(st.copy());
            given.add(st);
        }
        int tier = high.tier();
        h.runAfterDelay(60, () -> {
            Gt.equal(AtelierTile.TIER, m.guiValue(6), "etat de la machine (Tier insuffisant attendu)");
            Gt.equal(tier, m.guiValue(8), "tier demande");
            for (int i = 0; i < given.size(); i++) {
                Gt.equal(given.get(i).getCount(), m.itemInputs().get(i).getCount(), "entree consommee");
            }
            for (ItemSlot out : m.itemOutputs()) {
                Gt.check(out.isEmpty(), "produit sorti d'une recette au-dessus du tier");
            }
            s.done();
        });
    }
}
