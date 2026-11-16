package fr.pioneers.gametest;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.resource.ResourceStore;
import fr.pioneers.api.machine.unit.FuelGenerator;
import fr.pioneers.api.machine.unit.IndustryProcess;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.energy.GeneratorTile;
import fr.pioneers.tech.energy.PiEnergy;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.buffer.BufferTile;
import fr.pioneers.logistics.transport.buffer.BufferType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Machine (lot 3, GameTests) : les unites de stockage et de generateur sur de vraies machines : aller-retour de sauvegarde d'un buffer
 * de CHAQUE ressource (objets, fluide, gaz, energie, chaleur, air : contenu sous {@code contents/<cle>}, format d'avant le lot 3, faces et
 * face avant gardees, valeurs nommees aux indices d'avant), aller-retour d'un generateur a combustion ({@code burn}, {@code burnPower}) et
 * d'un panneau solaire ({@code factor}), et FE du generateur a combustion inchanges (source unique {@link GeneratorOutput}).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineApi3Tests {

    private MachineApi3Tests() {
    }

    private static BufferTile buffer(GameTestHelper h, BlockPos rel, BufferType type) {
        Block block = PiTransport.buffer(type, CableTier.ADVANCED).getBlock();
        h.setBlock(rel, block.defaultBlockState());
        Gt.check(h.getBlockEntity(rel) instanceof BufferTile, "pas de tuile de buffer pour " + type);
        return (BufferTile) h.getBlockEntity(rel);
    }

    /** Remplit le buffer d'un peu de sa ressource ; renvoie la cle attendue sous {@code contents}. */
    private static String fill(BufferTile b) {
        ResourceStore store = b.unit().store;
        switch (b.getBufferType()) {
            case ITEM -> b.getSlots().get(3).setStack(new ItemStack(Items.IRON_INGOT, 37));
            case FLUID -> b.getFluidTank().fill(new FluidStack(Fluids.WATER, 4_321), IFluidHandler.FluidAction.EXECUTE);
            case GAS -> b.getGasTank().insert(PiIndustry.steam(3_210), Action.EXECUTE, Automation.INTERNAL);
            case ENERGY -> ((ResourceStore.Energy) store).buffer.setStored(123_456);
            case HEAT -> ((ResourceStore.Heat) store).body.addHeat(2_000_000);
            case PRESSURE -> ((ResourceStore.Pressure) store).volume.insertAir(1_234, false);
        }
        return store.resource.nbtKey;
    }

    // ================================================================== buffers : un de chaque ressource
    @GameTest(template = Gt.SMALL, batch = "pioneers_api3_buffers_nbt", timeoutTicks = 40)
    public static void api3_buffers_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int x = 1;
        for (BufferType type : BufferType.values()) {
            BufferTile a = buffer(h, new BlockPos(x, 2, 2), type);
            String key = fill(a);
            a.setFront(Direction.EAST);
            CompoundTag tag = a.saveWithoutMetadata();
            Gt.check(tag.contains(BufferTile.NBT_CONTENTS), type + " : cle « contents » manquante");
            Gt.check(tag.getCompound(BufferTile.NBT_CONTENTS).contains(key), type + " : contenu attendu sous contents/" + key);
            Gt.check(tag.contains("config"), type + " : cle « config » manquante");
            Gt.equal("east", tag.getString("front"), type + " : face avant");
            BufferTile b = buffer(h, new BlockPos(x, 2, 5), type);
            b.load(tag.copy());
            Gt.equal(Math.round(a.unit().store.amount()), Math.round(b.unit().store.amount()), type + " : quantite relue");
            Gt.equal(a.fill(), b.fill(), type + " : remplissage relu");
            Gt.equal(tag, b.saveWithoutMetadata(), type + " : sauvegarde identique apres relecture");
            //Valeurs nommees aux indices d'avant le lot 3 (quantite / temperature / air en 0, contenance en 1)
            UiValues ui = b.uiValues();
            Gt.equal(a.guiValueCount(), ui.size(), type + " : nombre de valeurs");
            for (int i = 0; i < ui.size(); i++) {
                Gt.equal(a.guiValue(i), b.guiValue(i), type + " : valeur " + ui.keys().get(i).name());
            }
            x += 1;
        }
        s.done();
    }

    // ================================================================== generateurs : sauvegarde
    @GameTest(template = Gt.SMALL, batch = "pioneers_api3_generator_nbt", timeoutTicks = 40)
    public static void api3_generateur_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block block = PiIndustry.ATELIERS.get(IndustryMachine.SOLID_COMBUSTION_GENERATOR).get(CableTier.ELITE).getBlock();
        h.setBlock(new BlockPos(2, 2, 2), block.defaultBlockState());
        AtelierTile a = (AtelierTile) h.getBlockEntity(new BlockPos(2, 2, 2));
        Gt.check(a.unit() instanceof IndustryProcess p && p.generator() != null, "le generateur a combustion n'a pas d'unite FuelGenerator");
        CompoundTag tag = a.saveWithoutMetadata();
        tag.putInt("burn", 77);
        tag.putLong("burnPower", 32_768);
        h.setBlock(new BlockPos(5, 2, 2), block.defaultBlockState());
        AtelierTile b = (AtelierTile) h.getBlockEntity(new BlockPos(5, 2, 2));
        b.load(tag.copy());
        CompoundTag again = b.saveWithoutMetadata();
        Gt.equal(77, again.getInt("burn"), "combustion restante relue");
        Gt.equal(32_768, again.getLong("burnPower"), "puissance de la combustion relue");
        Gt.equal(32_768, b.production(), "production relue (FE/t)");
        //Panneau solaire : facteur sauve (cle d'avant), valeurs nommees aux indices d'avant (12 : 4 communes + 8)
        Block solar = PiEnergy.SOLAR.get(1).getBlock();
        h.setBlock(new BlockPos(2, 2, 5), solar.defaultBlockState());
        GeneratorTile g = (GeneratorTile) h.getBlockEntity(new BlockPos(2, 2, 5));
        CompoundTag st = g.saveWithoutMetadata();
        Gt.check(st.contains("factor"), "panneau solaire : cle « factor » manquante");
        st.putDouble("factor", 0.5);
        h.setBlock(new BlockPos(5, 2, 5), solar.defaultBlockState());
        GeneratorTile g2 = (GeneratorTile) h.getBlockEntity(new BlockPos(5, 2, 5));
        g2.load(st.copy());
        Gt.equal(0.5, g2.saveWithoutMetadata().getDouble("factor"), "panneau solaire : facteur relu");
        Gt.equal(13, g2.guiValueCount(), "panneau solaire : 13 valeurs d'interface (1.0.4-beta lot 5 : + surplus, en fin de liste)");
        Gt.equal(g2.peak(), g2.guiValue(6), "panneau solaire : production maximale a l'indice 6");
        s.done();
    }

    // ================================================================== generateur a combustion : FE inchanges
    @GameTest(template = Gt.SMALL, batch = "pioneers_api3_combustion_fe", timeoutTicks = 100)
    public static void api3_combustion_fe(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block block = PiIndustry.ATELIERS.get(IndustryMachine.SOLID_COMBUSTION_GENERATOR).get(CableTier.BASIC).getBlock();
        BlockPos at = new BlockPos(4, 2, 4);
        h.setBlock(at, block.defaultBlockState());
        AtelierTile a = (AtelierTile) h.getBlockEntity(at);
        ItemStack coal = new ItemStack(Items.COAL, 4);
        IndustrialRecipe fuel = IndustryRecipes.fuel(coal);
        Gt.check(fuel != null, "le charbon n'est pas un combustible");
        long expected = GeneratorOutput.perTick(fuel, 0, false);
        ItemSlot input = a.itemInputs().get(0);
        input.setStack(coal);
        h.runAfterDelay(30, () -> {
            FuelGenerator gen = ((IndustryProcess) a.unit()).generator();
            Gt.equal(expected, gen.production(), "FE/t de la combustion (GeneratorOutput.perTick)");
            //Rien autour ne prend l'energie : la reserve tient tout ce qui a ete produit
            long stored = a.getEnergy().getStored();
            Gt.check(stored > 0 && stored % expected == 0, "energie produite par pas entiers de " + expected + " FE : " + stored);
            s.done();
        });
    }
}
