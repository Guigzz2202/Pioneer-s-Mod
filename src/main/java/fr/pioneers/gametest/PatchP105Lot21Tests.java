package fr.pioneers.gametest;

import fr.pioneers.bronze.bench.BenchRecipe;
import fr.pioneers.bronze.bench.BenchTile;
import fr.pioneers.bronze.bench.BenchType;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.industry.atelier.AtelierTile;
import fr.pioneers.machine.MachineKind;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.registries.PiMachineKit;
import fr.pioneers.transport.CableTier;
import fr.pioneers.wild.Boar;
import fr.pioneers.wild.PiWild;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 21 (retours de jeu 2e serie, 1/2 : faune et bugs, GameTests) :
 * <ul>
 *     <li>SANGLIER (plantage du 11/11 : {@code Boar$RootCrops.tick}, culture nulle) : un sanglier affame fouille trois cultures de suite, loin
 *     de tout joueur (le GameTest n'a pas de joueur : IA allegee, jusqu'a 7 ticks sans {@code canContinueToUse}), sans planter ; le but,
 *     appele hors de son tour (culture nulle), ne fait rien ;</li>
 *     <li>FOURS ELECTRIQUES : 80 / 40 / 20 / 10 ticks du MKI au MKIV ; un MKIV cuit un boeuf en 10 ticks (rien avant) ;</li>
 *     <li>SEAU DE CREOSOTE : le bois traite a l'etabli du charpentier rend le seau vide (dans la case du seau).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot21Tests {

    private PatchP105Lot21Tests() {
    }

    // ------------------------------------------------------------------ 1. sanglier

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_21_boar", timeoutTicks = 1600)
    public static void p105_21_sanglier_fouille_sans_planter(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos[] crops = {new BlockPos(12, 1, 10), new BlockPos(13, 1, 10), new BlockPos(14, 1, 10)};
        for (BlockPos c : crops) {
            h.setBlock(c.below(), Blocks.FARMLAND);
            h.setBlock(c, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        Boar b = PatchP105Lot10Tests.add(s, PiWild.BOAR.get(), crops[0].west(5));
        b.setPersistenceRequired();
        b.makeHungry();
        WrappedGoal root = b.rootGoal();
        Gt.check(root != null, "but RootCrops absent du sanglier");
        final WrappedGoal goal = root;
        //appele hors de son tour (culture nulle : etat apres une fouille) : ne fait rien, ne plante pas
        goal.getGoal().tick();
        h.onEachTick(() -> {
            if (!goal.isRunning()) {
                goal.getGoal().tick();                         //le selecteur peut encore l'appeler un tick (crash du 11/11/2026)
            }
            b.makeHungry();
        });
        h.succeedWhen(() -> {
            for (BlockPos c : crops) {
                Gt.check(!h.getBlockState(c).is(Blocks.WHEAT), "le sanglier n'a pas encore fouille " + c);
            }
            Gt.check(b.isAlive(), "sanglier perdu");
            s.done();
        });
    }

    // ------------------------------------------------------------------ 2. fours electriques

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_21_four", timeoutTicks = 200)
    public static void p105_21_fours_electriques(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int[] want = {80, 40, 20, 10};
        for (CableTier t : CableTier.values()) {
            Gt.equal(want[t.ordinal()], MachineKind.FURNACE.ticks(t), "four electrique " + t.id() + " : ticks par cuisson");
        }
        Gt.equal(81_920L, MachineKind.FURNACE.power(CableTier.BASIC) * MachineKind.FURNACE.ticks(CableTier.BASIC), "MKI : FE par cuisson");
        Gt.equal(655_360L, MachineKind.FURNACE.power(CableTier.ULTIMATE) * MachineKind.FURNACE.ticks(CableTier.ULTIMATE), "MKIV : FE par cuisson");
        BlockPos rel = new BlockPos(2, 2, 2);
        BlockState st = PiMachineKit.MACHINES.get(MachineKind.FURNACE).get(CableTier.ULTIMATE).getBlock().defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de four electrique MKIV");
        AtelierTile f = (AtelierTile) h.getBlockEntity(rel);
        f.getEnergy().setStored(f.getEnergy().getCapacity());
        f.itemInputs().get(0).setStack(new ItemStack(Items.BEEF, 3));
        h.startSequence()
              .thenExecuteAfter(6, () -> Gt.equal(0, PatchP105Lot14Tests.cooked(f), "MKIV : rien de cuit avant 10 ticks"))
              .thenExecuteAfter(10, () -> Gt.check(PatchP105Lot14Tests.cooked(f) >= 1, "MKIV : boeuf cuit en 10 ticks (vu a 16)"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 3. seau de creosote rendu

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pioneers", id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_21_seau", timeoutTicks = 100)
    public static void p105_21_seau_de_creosote_rendu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(4, 2, 4);
        ServerPlayer player = s.player("gt21seau", "Charpentiere", new BlockPos(2, 2, 2));
        h.setBlock(at, PiBronze.BENCHES.get(BenchType.CARPENTER).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof BenchTile, "pas d'etabli du charpentier");
        BenchTile bench = (BenchTile) h.getBlockEntity(at);
        List<BenchRecipe> recipes = BenchTile.recipes(h.getLevel(), BenchType.CARPENTER);
        int index = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).getId().equals(new ResourceLocation("pioneers", "warehouse/treated_wood_creosote"))) {
                index = i;
            }
        }
        Gt.check(index >= 0, "recette du bois traite a la creosote absente");
        BenchRecipe r = recipes.get(index);
        Gt.check(!r.remainders().isEmpty() && r.remainders().get(0).is(Items.BUCKET), "JEI : le seau vide rendu doit etre annonce");
        List<ItemSlot> slots = bench.guiSlots();
        slots.get(0).setStack(r.tools().get(0).getItems()[0].copy());
        ItemStack planks = r.ingredients().get(0).ingredient().getItems()[0].copy();
        planks.setCount(16);
        slots.get(BenchType.TOOLS).setStack(planks);
        slots.get(BenchType.TOOLS + 1).setStack(new ItemStack(item("creosote_bucket")));
        bench.handleCustom(player, BenchTile.SELECT + index);
        bench.handleCustom(player, BenchTile.CRAFT);
        Gt.check(bench.outputSlot().getStack().is(item("treated_wood")), "bois traite fabrique");
        Gt.equal(16, bench.outputSlot().getCount(), "16 planches traitees");
        Gt.check(slots.get(BenchType.TOOLS + 1).getStack().is(Items.BUCKET), "le seau vide doit etre rendu a la place du seau de creosote");
        s.done();
    }
}
