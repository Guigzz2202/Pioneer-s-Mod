package fr.pioneers.industry.bronze;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeSources;
import fr.pioneers.industry.IndustryEvents;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.IoTank;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import fr.pioneers.industry.recipe.RecipeMatch;
import fr.pioneers.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 7) : UNITE DE PROCEDE du four a coke (code de l'ancienne {@code CokeOvenTile}, qui ne fait plus que la porter) : recettes
 * industrielles du four a coke au tier 0 (duree x2), goudron dans son reservoir (16 000 mB, extractible par toutes les faces), gaz rejetes,
 * selecteur de recette. Energie motrice : le feu du four lui-meme (aucun combustible : la charge cuit en vase clos). Cles {@code progress},
 * {@code tar}, {@code lockedRecipe} et ordre des cases (entree, sortie) d'avant le lot 7.
 */
public class CokeOvenProcess extends ProcessUnit implements RecipeMatch.Buffers {

    public static final int IDLE = 0, WORKING = 1, FULL = 2, NO_RECIPE = 3;

    public final IoTank tar;
    private final BasicGasTank vent = BasicGasTank.output(Long.MAX_VALUE / 4, null);
    @Nullable
    private IndustrialRecipe current;

    public CokeOvenProcess(UnitHost machine) {
        super(machine, CableTier.BASIC, new StandardLayout.Counts(1, 1, 0, 0, 1, 0));
        int[] in = layout.itemIn.get(0), out = layout.itemOut.get(0);
        //MAJ Recettes (lot 7) : verrouille, le four ne prend que l'ingredient de sa recette
        inputs.add(machine.slot(new ItemSlot(() -> 64, s -> lockAllows(s, () -> true), (s, a) -> true, (s, a) -> !a.external(), machine::changed, in[0],
              in[1])));
        outputs.add(machine.slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, machine::changed, out[0], out[1])));
        tar = new IoTank(16_000, true, f -> true, () -> {
            machine.changed();
            machine.markSync();
        });
        fluidOut.add(tar);
        gasOut.add(vent);
    }

    public boolean working() {
        return status == WORKING;
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        vent.setStack(GasStack.EMPTY);
        if (current == null || level.getGameTime() % 20 == 0) {
            //MAJ Recettes (lot 7) : verrou pose : seule la recette verrouillee est essayee
            IndustrialRecipe locked = lock.isSet() ? IndustryRecipes.byId(level, IndustryMachine.COKE_OVEN, lock.id()) : null;
            IndustrialRecipe found = RecipeMatch.find(IndustryRecipes.of(level, IndustryMachine.COKE_OVEN), this, 0, false, locked);
            if (found != current) {
                current = found;
                progress = 0;
                duration = found == null ? 1 : found.time() * 2;
            }
        }
        if (current == null) {
            status = inputs.get(0).isEmpty() ? IDLE : NO_RECIPE;
        } else if (!RecipeMatch.fits(current, this, 1, 0, 1)) {
            status = FULL;
        } else {
            status = WORKING;
            if (++progress >= duration) {
                progress = 0;
                if (RecipeMatch.supply(current, this, 1) <= 0) {
                    //Entrees retirees entre-temps : rien n'est produit
                    current = null;
                    return;
                }
                RecipeMatch.consume(current, this, 1);
                RecipeMatch.produce(current, this, 1, level.random, 0, 1);
                IndustryEvents.award(level, pos(), "industry_coke_oven");
                current = null;
            }
        }
        boolean lit = status == WORKING;
        BlockState state = machine.state();
        if (state.getValue(CokeOvenBlock.LIT) != lit) {
            level.setBlock(pos(), state.setValue(CokeOvenBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        machine.changed();
    }

    /** Valeurs d'avant le lot 7 : 0 avancement (ticks), 1 duree, 2 etat, 3 goudron (mB) ; puis la contenance du reservoir. */
    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.PROGRESS, () -> progress).add(UiValues.DURATION, () -> duration).add(UiValues.STATUS, () -> status)
              .add(UiValues.FLUID, tar::getFluidAmount);
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.put("tar", tar.writeToNBT(new CompoundTag()));
        lock.writeClient(tag);
    }

    @Override
    public void readClient(CompoundTag tag) {
        tar.readFromNBT(tag.getCompound("tar"));
        lock.readClient(tag);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("progress", progress);
        tag.put("tar", tar.writeToNBT(new CompoundTag()));
        lock.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        progress = tag.getInt("progress");
        tar.readFromNBT(tag.getCompound("tar"));
        lock.load(tag);
    }

    // ------------------------------------------------------------------
    // Selecteur de recette
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return RecipeSources.industrial(IndustryMachine.COKE_OVEN, false);
    }

    @Override
    public void recipeLockChanged() {
        current = null;
        machine.syncNow();
    }

    @Override
    public int recipeTier() {
        return 0;
    }

    @Override
    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemSlot slot : inputs) {
            out.add(slot.getStack());
        }
        return out;
    }
}
