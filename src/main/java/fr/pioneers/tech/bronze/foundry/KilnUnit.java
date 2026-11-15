package fr.pioneers.tech.bronze.foundry;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.Hearth;
import fr.pioneers.tech.bronze.LitBronzeBlock;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.registries.PiBronze;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 8b) : UNITE du four de potier, code de l'ancienne {@code KilnTile} qui ne fait plus que la porter. Energie motrice : le FEU
 * du foyer ({@link PowerSource.Fuel}) : il monte vers {@link KilnTile#MAX_C} degres quand il brule (plus avec un meilleur combustible) ;
 * chaque piece cuit sur place des que sa temperature est atteinte (recettes {@code pioneers:kiln}) ; la cendre tombe dans sa case.
 * Sauvegarde au format d'avant : {@code slots} (tile), foyer, {@code temperature}, {@code progress} ; cases : combustible, 4 places, cendre.
 */
public class KilnUnit extends MachineUnit {

    final ItemSlot fuel, ash;
    final List<ItemSlot> ware = new ArrayList<>();
    private final int[] progress = new int[4];
    private final Hearth hearth = new Hearth();
    private double temperature = 20;

    public KilnUnit(UnitHost machine) {
        super(machine);
        fuel = machine.slot(new ItemSlot(() -> 64, s -> ForgeHooks.getBurnTime(s, null) > 0, (s, a) -> true, (s, a) -> !a.external(),
              machine::changed, KilnTile.FUEL_X, KilnTile.FUEL_Y));
        for (int i = 0; i < 4; i++) {
            int[] p = KilnTile.warePos(i);
            ware.add(machine.slot(new ItemSlot(() -> 16, s -> true, (s, a) -> recipe(s) != null || !a.external(), (s, a) -> !a.external() || recipe(s) == null,
                  machine::changed, p[0], p[1])));
        }
        ash = machine.slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, machine::changed, KilnTile.ASH_X,
              KilnTile.ASH_Y));
    }

    public PowerSource.Fuel power() {
        return new PowerSource.Fuel(() -> temperature, hearth::ratio);
    }

    @Nullable
    KilnRecipe recipe(ItemStack stack) {
        Level level = level();
        if (stack.isEmpty() || level == null) {
            return null;
        }
        for (KilnRecipe recipe : PiRecipes.sorted(level, PiBronze.KILN.get())) {
            if (recipe.input().test(stack)) {
                return recipe;
            }
        }
        return null;
    }

    public double temperature() {
        return temperature;
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        boolean pending = false;
        for (ItemSlot slot : ware) {
            pending |= recipe(slot.getStack()) != null;
        }
        if (hearth.tick(fuel, pending, 1)) {
            ash.insert(new ItemStack(PiMechanics.WOOD_ASH.get()), Action.EXECUTE, Automation.INTERNAL);
        }
        double target = hearth.lit() ? KilnTile.MAX_C + hearth.bonus() * 1.5 : 20;
        temperature += (target - temperature) * (target > temperature ? 0.004 : 0.002);
        for (int i = 0; i < ware.size(); i++) {
            ItemSlot slot = ware.get(i);
            KilnRecipe recipe = recipe(slot.getStack());
            if (recipe == null || temperature < recipe.temperature()) {
                progress[i] = recipe == null ? 0 : Math.max(0, progress[i] - 1);
                continue;
            }
            if (++progress[i] >= recipe.time()) {
                progress[i] = 0;
                ItemStack out = recipe.result().copy();
                out.setCount(out.getCount() * slot.getCount());
                slot.setStack(out);
                level.playSound(null, pos(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
                BronzeEvents.award(level, pos(), "kiln");
                machine.changed();
            }
        }
        LitBronzeBlock.setLit(level, pos(), machine.state(), hearth.lit());
    }

    // Interface : 0 temperature x10, 1 combustible x1000, 2..5 cuisson x1000 par place (-1 : rien a cuire) : memes indices qu'avant
    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.HEAT, () -> Math.round(temperature * 10)).add(UiValues.BURN, () -> Math.round(hearth.ratio() * 1000));
        for (int i = 0; i < 4; i++) {
            int index = i;
            values.add(UiValues.firing(i), () -> {
                KilnRecipe recipe = recipe(ware.get(index).getStack());
                return recipe == null ? -1 : Math.round(progress[index] * 1000.0 / recipe.time());
            });
        }
    }

    @Override
    public void save(CompoundTag tag) {
        hearth.save(tag);
        tag.putDouble("temperature", temperature);
        tag.putIntArray("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        hearth.load(tag);
        temperature = tag.contains("temperature") ? tag.getDouble("temperature") : 20;
        int[] p = tag.getIntArray("progress");
        System.arraycopy(p, 0, progress, 0, Math.min(p.length, progress.length));
    }
}
