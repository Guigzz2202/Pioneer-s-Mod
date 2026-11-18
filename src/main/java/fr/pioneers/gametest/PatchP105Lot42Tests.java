package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.BatchRefund;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedBatchRefund;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 42 (verification finale des retours de jeu 5e serie ; point laisse ouvert par le lot 38 ; GameTests) : LOT EN COURS
 * d'un multibloc industriel (entrees prises au depart) sauve sur une recette qui n'est plus a sa machine, par le VRAI chemin du chargement
 * ({@code ControllerTile.load} puis {@code MultiProcess.start}) :
 * <ul>
 *     <li>copie retiree de la taille de pierre a la presse (lot 38, 113 copies {@code automation/mason/*}) : entrees RENDUES, chacune dans la
 *     table generee ;</li>
 *     <li>recette de presse partie a la scie industrielle (lot 40) : entrees d'AVANT rendues (le charbon compris, que la recette d'apres n'a
 *     plus) ;</li>
 *     <li>copie « steam_* » de la scierie retiree (lot 38) : lot REPRIS par la recette basse qui prend exactement les memes entrees ;</li>
 *     <li>recette toujours a la machine : lot repris tel quel ; decision pure (entrees differentes : rendu).</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot42Tests {

    private PatchP105Lot42Tests() {
    }

    static ResourceLocation rl(String path) {
        return Pioneers.rl(path);
    }

    static BlockState facing(Block block) {
        BlockState st = block.defaultBlockState();
        return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : st;
    }

    static ControllerTile controller(GameTestHelper h, IndustryMachine machine) {
        return controller(h, machine, new BlockPos(3, 2, 3));
    }

    static ControllerTile controller(GameTestHelper h, IndustryMachine machine, BlockPos at) {
        h.setBlock(at, facing(PiIndustry.CONTROLLERS.get(machine).getBlock()));
        ControllerTile ctrl = (ControllerTile) h.getBlockEntity(at);
        Gt.check(ctrl != null, "controleur pose : " + machine.key);
        return ctrl;
    }

    /** Monde existant : le controleur relit une sauvegarde dont le lot en cours ({@code count} operations) porte la recette {@code path}. */
    static void savedBatch(ControllerTile ctrl, String path, int count) {
        CompoundTag tag = ctrl.saveWithoutMetadata();
        tag.putString("batch", rl(path).toString());
        tag.putInt("batchCount", count);
        tag.putDouble("progress", 12);
        tag.putInt("duration", 200);
        ctrl.load(tag);
        BatchRefund.restart(ctrl);
    }

    static int total(List<ItemSlot> slots) {
        int n = 0;
        for (ItemSlot s : slots) {
            n += s.getStack().getCount();
        }
        return n;
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

    // ------------------------------------------------------------------ 1. taille de pierre a la presse (lot 38) : rendue

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_42_mason", timeoutTicks = 20)
    public static void p105_42_taille_de_pierre_rendue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //chaque copie du macon partie de la presse est dans la table, absente de la presse, sans remplacante : lot rendu
        int mason = 0;
        for (Map.Entry<String, String[]> e : GeneratedBatchRefund.TABLE.entrySet()) {
            if (!e.getKey().startsWith("press|automation/mason/")) {
                continue;
            }
            mason++;
            ResourceLocation id = rl(e.getKey().substring("press|".length()));
            Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.PRESS, id) == null, "encore a la presse : " + id);
            BatchRefund.Plan p = BatchRefund.plan(s.level, IndustryMachine.PRESS, id);
            Gt.check(p.kind() == BatchRefund.Kind.REFUND, "copie du macon rendue : " + id + " (" + p.kind() + ")");
            for (BatchRefund.Ref x : p.inputs().items()) {
                Gt.check(BatchRefund.item(x.ref()) != net.minecraft.world.item.Items.AIR, "objet rendu connu : " + x.ref());
            }
        }
        Gt.check(mason >= 113, "copies du macon dans la table : " + mason);
        //presse industrielle d'un monde d'avant le lot 38 : 3 operations de dalles d'andesite en cours
        ControllerTile ctrl = controller(h, IndustryMachine.PRESS);
        String path = "automation/mason/vanilla_andesite_slab";
        BatchRefund.Inputs in = BatchRefund.retired("press", rl(path));
        Gt.check(in != null && in.items().size() == 1, "dalle d'andesite dans la table");
        savedBatch(ctrl, path, 3);
        Gt.check(BatchRefund.batch(ctrl) == null, "plus de lot en cours");
        Gt.equal(0, BatchRefund.batchCount(ctrl), "lot vide");
        Item andesite = BatchRefund.item(in.items().get(0).ref());
        Gt.equal((int) (3 * in.items().get(0).count()), count(ctrl.itemOutputs(), andesite), "3 operations d'andesite rendues");
        s.done();
    }

    // ------------------------------------------------------------------ 2. presse partie a la scie (lot 40) : entrees d'AVANT

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_42_moved", timeoutTicks = 20)
    public static void p105_42_presse_deplacee_entrees_d_avant(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String path = "industry/press/press_slab_aluminium";
        //1.0.6 (une machine par transformation de forme) : bloc -> dalles au compresseur
        Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.COMPRESSOR, rl(path)) != null, "dalle d'aluminium au compresseur");
        Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.PRESS, rl(path)) == null, "dalle d'aluminium plus a la presse");
        ControllerTile ctrl = controller(h, IndustryMachine.PRESS);
        savedBatch(ctrl, path, 2);
        Gt.check(BatchRefund.batch(ctrl) == null, "plus de lot en cours");
        //avant le lot 40 : 1 bloc d'aluminium + 1 charbon par operation ; la recette de la scie n'a plus de charbon : il est rendu quand meme
        Gt.equal(4, total(ctrl.itemOutputs()), "2 blocs et 2 charbons rendus");
        int coal = 0;
        for (ItemSlot x : ctrl.itemOutputs()) {
            if (x.getStack().is(ItemTags.COALS)) {
                coal += x.getStack().getCount();
            }
        }
        Gt.equal(2, coal, "le charbon d'avant rendu");
        s.done();
    }

    // ------------------------------------------------------------------ 3. remplacante aux memes entrees : lot repris

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_42_resume", timeoutTicks = 20)
    public static void p105_42_reprise_memes_entrees(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ControllerTile ctrl = controller(h, IndustryMachine.SAWMILL);
        savedBatch(ctrl, "industry/sawmill/steam_planks_oak", 2);
        ResourceLocation now = BatchRefund.batch(ctrl);
        Gt.check(now != null && !now.getPath().equals("industry/sawmill/steam_planks_oak"), "lot repris par la remplacante : " + now);
        Gt.equal(2, BatchRefund.batchCount(ctrl), "2 operations gardees");
        Gt.equal(0, total(ctrl.itemOutputs()), "rien de rendu (le lot continue)");
        //recette toujours a la machine : le lot reprend tel quel, rien n'est rendu
        ControllerTile press = controller(h, IndustryMachine.PRESS, new BlockPos(6, 2, 6));
        savedBatch(press, "industry/press/press_plate_nickel", 1);
        Gt.equal(rl("industry/press/press_plate_nickel"), BatchRefund.batch(press), "plaque de nickel : lot garde");
        Gt.check(!BatchRefund.of(press, rl("industry/press/press_plate_nickel"), 1), "plaque de nickel : rien a rendre");
        //decision pure : une remplacante qui ne prend pas les memes nombres ne reprend pas le lot (les entrees sont rendues)
        BatchRefund.Inputs two = BatchRefund.parse(new String[]{"minecraft:oak_log*2", "", ""});
        BatchRefund.Inputs one = BatchRefund.parse(new String[]{"minecraft:oak_log*1", "", ""});
        Gt.check(BatchRefund.decide(two, one, BatchRefund::sameRef) == BatchRefund.Kind.REFUND, "entrees differentes : rendu");
        Gt.check(BatchRefund.decide(one, one, BatchRefund::sameRef) == BatchRefund.Kind.RESUME, "memes entrees : reprise");
        Gt.check(BatchRefund.decide(null, null, BatchRefund::sameRef) == BatchRefund.Kind.NONE, "recette inconnue : rien");
        s.done();
    }
}
