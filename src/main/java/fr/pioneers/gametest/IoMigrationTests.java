package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ Recettes (lot 9a) : migration des cases des machines deja posees dont l'io a change ({@link IoMigration}). Chaque test fabrique le
 * NBT d'une machine telle que la 1.9.2-alpha la sauvait (cases par rang, reservoirs par position, sans {@code ioVersion}), le fait relire par
 * une vraie machine, et verifie que chaque objet, fluide, gaz et la batterie sont a leur place ; puis que la machine resauvee porte la marque
 * et qu'une seconde lecture ne deplace plus rien.
 * <ul>
 *     <li>creuset electrique (L2-4) : (1, 0, 0, 1, 0, 0) -> (3, 2, 0, 1, 1, 1) : la batterie passe de la case 1 a la case 5 ;</li>
 *     <li>machine de coulee (L4b-9) : (1, 2, 1, 0, 0, 0) -> (1, 3, 2, 1, 0, 1) : la batterie passe de la case 3 a la case 4 ; et la coulee
 *     continue (multibloc) : (0, 1, 2, 0, 0, 0) -> (1, 2, 2, 1, 0, 1) : les sorties passent du tampon d'entree au tampon de sortie, le
 *     caloporteur chaud et la vapeur glissent d'un reservoir ;</li>
 *     <li>soudeuse a l'arc (L6-4) : (3, 1, 0, 0, 1, 0) -> (6, 2, 0, 0, 1, 0) : la sortie passe de la case 3 a la case 6, la batterie de 4 a 8.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class IoMigrationTests {

    private IoMigrationTests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static Fluid fluid(String id) {
        Fluid f = PiIndustry.fluid(id);
        Gt.check(f != Fluids.EMPTY, "fluide absent : " + id);
        return f;
    }

    private static BlockState facing(Block block) {
        BlockState st = block.defaultBlockState();
        return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : st;
    }

    private static AtelierTile atelier(GameTestHelper h, IndustryMachine machine, BlockPos rel) {
        h.setBlock(rel, facing(PiIndustry.ATELIERS.get(machine).get(CableTier.BASIC).getBlock()));
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier : " + machine.key);
        return (AtelierTile) h.getBlockEntity(rel);
    }

    private static ControllerTile controller(GameTestHelper h, IndustryMachine machine, BlockPos rel) {
        h.setBlock(rel, facing(PiIndustry.CONTROLLERS.get(machine).getBlock()));
        Gt.check(h.getBlockEntity(rel) instanceof ControllerTile, "pas de controleur : " + machine.key);
        return (ControllerTile) h.getBlockEntity(rel);
    }

    /** NBT d'une machine de la 1.9.2 : celui de la machine posee, sans marque de version, cases et reservoirs remplaces. */
    private static CompoundTag old(MachineTile tile, ListTag items, ListTag fluids, ListTag gases) {
        CompoundTag tag = tile.saveWithoutMetadata();
        tag.remove(IoMigration.NBT);
        tag.put("items", items);
        tag.put("fluids", fluids);
        tag.put("gases", gases);
        return tag;
    }

    private static ListTag items(Object... slotAndStack) {
        ListTag list = new ListTag();
        for (int i = 0; i < slotAndStack.length; i += 2) {
            CompoundTag e = ((ItemStack) slotAndStack[i + 1]).save(new CompoundTag());
            e.putShort("Slot", (short) (int) (Integer) slotAndStack[i]);
            list.add(e);
        }
        return list;
    }

    /** Reservoirs par position (null : vide). */
    private static ListTag tanks(CompoundTag... saved) {
        ListTag list = new ListTag();
        for (CompoundTag c : saved) {
            list.add(c == null ? new CompoundTag() : c);
        }
        return list;
    }

    private static CompoundTag fluid(Fluid f, int amount) {
        return new FluidStack(f, amount).writeToNBT(new CompoundTag());
    }

    private static CompoundTag gas(String id, long amount) {
        return GasStack.of(PioneersAPI.gas(rl(id)), amount).save();
    }

    private static void slot(MachineTile tile, int index, Item it, int count, String what) {
        ItemStack st = tile.guiSlots().get(index).getStack();
        Gt.check(st.is(it) && st.getCount() == count, what + " : case " + index + " = " + st + ", attendu " + count + " x " + it);
    }

    private static void empty(MachineTile tile, int index, String what) {
        Gt.check(tile.guiSlots().get(index).getStack().isEmpty(), what + " : case " + index + " devrait etre vide");
    }

    private static void tank(FluidTank t, Fluid f, int amount, String what) {
        Gt.check(t.getFluid().getFluid().isSame(f) && t.getFluidAmount() == amount, what + " : " + t.getFluid().getFluid() + " "
              + t.getFluidAmount() + ", attendu " + amount);
    }

    /** Resauve la machine : marque de version ; relue, rien ne bouge plus. */
    private static void stamped(MachineTile tile) {
        CompoundTag again = tile.saveWithoutMetadata();
        Gt.equal(IndustryMachine.IO_VERSION, again.getInt(IoMigration.NBT), "marque de version apres migration");
        CompoundTag before = again.copy();
        tile.load(again);
        CompoundTag after = tile.saveWithoutMetadata();
        Gt.equal(before.get("items"), after.get("items"), "seconde lecture : cases deplacees");
        Gt.equal(before.get("fluids"), after.get("fluids"), "seconde lecture : reservoirs deplaces");
        Gt.equal(before.get("gases"), after.get("gases"), "seconde lecture : gaz deplaces");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot9a_migration_creuset")
    public static void migration_creuset_cases(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.ELECTRIC_CRUCIBLE, new BlockPos(2, 2, 2));
        Item crushed = item("crushed_iron");
        Fluid steel = fluid("molten_steel");
        // 1.9.2 : case 0 = entree, case 1 = batterie ; reservoir 0 = sortie (metal fondu)
        m.load(old(m, items(0, new ItemStack(crushed, 5), 1, new ItemStack(Items.REDSTONE, 1)), tanks(fluid(steel, 1_000)), tanks()));
        slot(m, 0, crushed, 5, "entree du creuset");
        for (int i = 1; i < 5; i++) {
            empty(m, i, "creuset");
        }
        slot(m, 5, Items.REDSTONE, 1, "batterie du creuset");
        tank(m.fluidOutputs().get(0), steel, 1_000, "metal fondu du creuset");
        Gt.check(m.gasInputs().get(0).getStack().isEmpty() && m.gasOutputs().get(0).getStack().isEmpty(), "reservoirs de gaz neufs vides");
        stamped(m);
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot9a_migration_coulee")
    public static void migration_coulee_cases(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item die = item("plate_die"), plate = item("steel_plate"), nugget = item("steel_nugget"), slab = item("steel_slab");
        Fluid steel = fluid("molten_steel");
        // Machine de coulee (atelier) : 1.9.2 = moule 0, sorties 1-2, batterie 3 ; reservoir 0 = metal fondu
        AtelierTile m = atelier(h, IndustryMachine.CASTING_MACHINE, new BlockPos(2, 2, 2));
        m.load(old(m, items(0, new ItemStack(die, 1), 1, new ItemStack(plate, 3), 2, new ItemStack(nugget, 7), 3, new ItemStack(Items.REDSTONE, 1)),
              tanks(fluid(steel, 500)), tanks()));
        // 1.0.4-beta (lot 14) : la Machine de coulee absorbe la presse a injection (2 entrees : granules + moule) : une entree neuve en case 1
        // 1.0.5-beta (lot 44, retour 62) : la matrice d'avant passe de la case d'entree 0 a la case Outil (derniere case, 6)
        empty(m, 0, "premiere entree (la matrice est passee dans la case Outil)");
        empty(m, 1, "deuxieme entree (nouvelle, presse a injection)");
        slot(m, 6, die, 1, "matrice dans la case Outil");
        slot(m, 2, plate, 3, "premiere sortie");
        slot(m, 3, nugget, 7, "deuxieme sortie");
        empty(m, 4, "troisieme sortie (nouvelle)");
        slot(m, 5, Items.REDSTONE, 1, "batterie de la machine de coulee");
        tank(m.fluidInputs().get(0), steel, 500, "metal fondu");
        Gt.check(m.fluidInputs().get(1).isEmpty() && m.fluidOutputs().get(0).isEmpty(), "reservoirs neufs vides");
        stamped(m);
        // (1.0.4-beta lot 14 : la coulee continue est fondue dans la Machine de coulee : son controleur se relit dans celui de la Machine de
        //  coulee industrielle, PatchP104Lot14Tests)
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot9a_migration_soudeuse")
    public static void migration_soudeuse_cases(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item beam = item("steel_beam"), rod = item("brass_rod"), sheet = item("steel_sheet"), casing = item("industrial_casing_basic");
        // 1.9.2 : entrees 0-2, sortie 3, batterie 4 ; gaz [argon]. 1.0.4-beta (lot 14) : la soudeuse est fondue dans l'Assembleur : son NBT
        // (reconnu par sa forme : pas de reservoir de fluide, un de gaz) se relit dans l'Assembleur (9 entrees, 2 sorties, batterie)
        AtelierTile m = atelier(h, IndustryMachine.ASSEMBLER, new BlockPos(2, 2, 2));
        m.load(old(m, items(0, new ItemStack(beam, 4), 1, new ItemStack(rod, 8), 2, new ItemStack(sheet, 8), 3, new ItemStack(casing, 6),
              4, new ItemStack(Items.REDSTONE, 1)), tanks(), tanks(gas("argon", 500))));
        slot(m, 0, beam, 4, "premiere entree");
        slot(m, 1, rod, 8, "deuxieme entree");
        slot(m, 2, sheet, 8, "troisieme entree");
        for (int i = 3; i < 9; i++) {
            empty(m, i, "entree neuve");
        }
        slot(m, 9, casing, 6, "sortie de la soudeuse");
        empty(m, 10, "deuxieme sortie (neuve)");
        slot(m, 11, Items.REDSTONE, 1, "batterie de la soudeuse");
        Gt.equal(500L, m.gasInputs().get(0).getStack().getAmount(), "argon garde");
        stamped(m);
        s.done();
    }
}
