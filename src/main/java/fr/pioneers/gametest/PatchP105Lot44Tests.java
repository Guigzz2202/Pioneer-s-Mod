package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.Remanents;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 44 (retours de jeu 6e serie, 2 : retour 62 de Guillaume, « Les items types "Moules" "Forme d'extrusion" et autres items
 * remanants doivent aller dans un slot reserve dans leur machine » ; controle tools/check_p105_44.py ; banc tools/remanent/run.sh) :
 * <ul>
 *     <li>PRESSE : la matrice a plaque posee par un entonnoir (gestionnaire d'objets de l'exterieur) va dans la case Outil, jamais dans une
 *     case d'entree ; la presse aplatit avec elle et la garde (chance de la recette : usure) ; aucune matrice aux sorties ;</li>
 *     <li>COULEE : la machine de coulee coule le cuivre fondu dans sa matrice a lingot (case Outil), les lingots sortent, la matrice reste ;
 *     un multibloc de coulee (controleur) recoit la matrice par ses ports dans sa case Outil ;</li>
 *     <li>MONDE EXISTANT : une machine sauvee a l'ancienne (matrice dans une case d'entree, case Outil deja prise par une autre matrice) se
 *     relit sans rien perdre : la matrice d'avant passe dans la case Outil, le surplus est garde puis pose au sol au premier tick.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot44Tests {

    private PatchP105Lot44Tests() {
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

    static BlockState facing(Block block) {
        BlockState st = block.defaultBlockState();
        return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : st;
    }

    static AtelierTile atelier(GameTestHelper h, IndustryMachine m, BlockPos at) {
        h.setBlock(at, facing(PiIndustry.ATELIERS.get(m).get(CableTier.BASIC).getBlock()));
        Gt.check(h.getBlockEntity(at) instanceof AtelierTile, "pas d'atelier : " + m.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(at);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        return t;
    }

    // ------------------------------------------------------------------ 1. presse : matrice dans la case Outil

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_44_press", timeoutTicks = 600)
    public static void p105_44_presse_matrice_case_outil(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile p = atelier(h, IndustryMachine.PRESS, new BlockPos(2, 2, 2));
        Gt.check(p.setLockedRecipe(rl("industry/press/press_plate_iron")), "plaque de fer a la presse absente");
        Item ingot = Items.IRON_INGOT, die = item("plate_die"), plate = item("iron_plate");
        //Une entree d'entonnoir : tout le gestionnaire d'objets vu de l'exterieur (cases d'entree, sorties, batterie, case Outil)
        ItemSlots hopper = new ItemSlots(p.guiSlots(), Automation.EXTERNAL);
        ItemStack offer = new ItemStack(die);
        for (int i = 0; i < hopper.getSlots() && !offer.isEmpty(); i++) {
            offer = hopper.insertItem(i, offer, false);
        }
        Gt.check(offer.isEmpty() && Gt.toolSlot(p).getStack().is(die), "entonnoir : la matrice va dans la case Outil");
        for (ItemSlot in : p.itemInputs()) {
            Gt.check(!in.getStack().is(die) && !in.isItemValid(new ItemStack(die)), "une case d'entree refuse la matrice");
        }
        Gt.check(hopper.extractItem(p.guiSlots().indexOf(Gt.toolSlot(p)), 1, true).isEmpty(), "l'exterieur ne sort jamais la matrice");
        p.itemInputs().get(0).setStack(new ItemStack(ingot, 3));
        h.succeedWhen(() -> {
            p.getEnergy().setStored(p.getEnergy().getCapacity());
            Gt.check(count(p.itemOutputs(), plate) >= 1, "la presse aplatit avec la matrice de la case Outil");
            Gt.equal(0, count(p.itemOutputs(), die), "aucune matrice aux sorties : elle reste dans sa case");
            Gt.check(Gt.toolSlot(p).getCount() <= 1, "jamais de matrice en plus");
        });
    }

    // ------------------------------------------------------------------ 2. coulee : matrice a lingot

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_44_cast", timeoutTicks = 800)
    public static void p105_44_coulee_matrice_case_outil(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.CASTING_MACHINE, new BlockPos(2, 2, 2));
        Gt.check(m.setLockedRecipe(rl("industry/casting_machine/cast_ingot_copper")), "coulee du cuivre absente de la machine de coulee");
        Item die = item("ingot_die"), ingot = item("copper_ingot");
        m.fluidInputs().get(0).setFluid(new FluidStack(ForgeRegistries.FLUIDS.getValue(rl("molten_copper")), 1_000));
        Gt.tool(m, new ItemStack(die, 3));                                // 3 matrices non empilables dans la case Outil (usure 1 sur 100)
        Gt.check(Gt.toolSlot(m).getCount() == 3 && Gt.toolSlot(m).getLimit(new ItemStack(die)) == 64, "case Outil : jusqu'a 64 matrices non empilables");
        Gt.check(Remanents.isTool(IndustryMachine.CASTING_MACHINE.key, new ItemStack(die)), "la matrice a lingot est un outil de la machine de coulee");
        //Multibloc de coulee : la matrice arrive par les ports d'objets dans la case Outil du controleur
        BlockPos at = new BlockPos(6, 2, 6);
        h.setBlock(at, facing(PiIndustry.CONTROLLERS.get(IndustryMachine.CASTING_MACHINE).getBlock()));
        ControllerTile c = (ControllerTile) h.getBlockEntity(at);
        Gt.check(c != null, "controleur de la machine de coulee industrielle pose");
        var ports = c.itemHandler(false);
        ItemStack offer = new ItemStack(die, 2);
        for (int i = 0; i < ports.getSlots() && !offer.isEmpty(); i++) {
            offer = ports.insertItem(i, offer, false);
        }
        Gt.check(offer.isEmpty() && Gt.toolSlot(c).getCount() == 2, "ports : 2 matrices (non empilables) rangees dans la case Outil du controleur");
        for (ItemSlot in : c.itemInputs()) {
            Gt.check(in.isEmpty(), "ports : aucune matrice dans une case d'entree du controleur");
        }
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.check(count(m.itemOutputs(), ingot) >= 3, "coulee : " + count(m.itemOutputs(), ingot) + " lingots / 3 au moins");
            Gt.equal(0, count(m.itemOutputs(), die), "coulee : la matrice reste dans sa case Outil");
        });
    }

    // ------------------------------------------------------------------ 3. monde existant

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_44_migration", timeoutTicks = 40)
    public static void p105_44_migration_machine_d_avant(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item die = item("plate_die"), press = item("press_die");
        AtelierTile fresh = atelier(h, IndustryMachine.PRESS, new BlockPos(2, 2, 2));
        int tool = fresh.guiSlots().indexOf(Gt.toolSlot(fresh));
        Gt.equal(fresh.guiSlots().size() - 1, tool, "la case Outil est la derniere case (index des cases d'avant inchanges)");
        //Sauvegarde a l'ancienne : matrice a plaque en case d'entree 1 ; et la meme presse sauvee apres le lot avec une matrice de presse en case
        //Outil et une matrice a plaque restee en entree (case Outil prise : rien ne se perd)
        CompoundTag old = fresh.saveWithoutMetadata();
        ListTag items = new ListTag();
        CompoundTag entry = new ItemStack(die).save(new CompoundTag());
        entry.putShort("Slot", (short) 1);
        items.add(entry);
        old.put("items", items);
        AtelierTile a = atelier(h, IndustryMachine.PRESS, new BlockPos(4, 2, 2));
        a.load(old.copy());
        Gt.check(a.itemInputs().get(1).isEmpty(), "ancienne matrice sortie de la case d'entree");
        Gt.check(Gt.toolSlot(a).getStack().is(die), "ancienne matrice dans la case Outil");
        CompoundTag both = old.copy();
        ListTag two = both.getList("items", Tag.TAG_COMPOUND);
        CompoundTag inTool = new ItemStack(press).save(new CompoundTag());
        inTool.putShort("Slot", (short) tool);
        two.add(inTool);
        AtelierTile b = atelier(h, IndustryMachine.PRESS, new BlockPos(6, 2, 2));
        b.load(both);
        Gt.check(Gt.toolSlot(b).getStack().is(press), "case Outil d'apres gardee (matrice de presse)");
        Gt.check(b.itemInputs().get(1).isEmpty(), "la matrice a plaque quitte la case d'entree");
        CompoundTag saved = b.saveWithoutMetadata();
        Gt.equal(1, saved.getList(IoMigration.OVERFLOW, Tag.TAG_COMPOUND).size(), "matrice sans place gardee a la sauvegarde (ioOverflow)");
        s.done();
    }
}
