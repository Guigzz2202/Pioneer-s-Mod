package fr.pioneers.space.content.universe;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.registries.PiItems;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Galaxies que simule le Simulateur d'univers : chacune a son cycle, sa consommation et ce qu'elle laisse dans les cases de sortie.
 */
public enum GalaxyType {
    /** Spirale barree : des bras bleutes pleins d'etoiles jeunes. */
    SPIRAL(600, 60_000_000L, 2, 0x8CB8FF, List.of(out(() -> PiItems.STARDUST.get(), 8)), out(() -> PiItems.DARK_MATTER.get(), 1), 0.15F),
    /** Elliptique geante : des etoiles vieilles et des restes d'etoiles a neutrons. */
    ELLIPTICAL(900, 80_000_000L, 3, 0xFFC878, List.of(out(() -> PiItems.STARDUST.get(), 4), out(() -> PiItems.NEUTRONIUM_INGOT.get(), 1)),
          out(() -> PiItems.NEUTRONIUM_INGOT.get(), 1), 0.2F),
    /** Irreguliere : nuages de gaz et halos de matiere noire. */
    IRREGULAR(1200, 80_000_000L, 3, 0xFF8CD8, List.of(out(() -> PiItems.STARDUST.get(), 6), out(() -> PiItems.DARK_MATTER.get(), 1)),
          out(() -> PiItems.STARDUST.get(), 6), 0.35F),
    /** Quasar : un trou noir supermassif qui devore sa galaxie et crache deux jets de plasma. */
    QUASAR(2400, 150_000_000L, 6, 0xC8F0FF, List.of(out(() -> PiItems.QUASAR_CRYSTAL.get(), 1), out(() -> PiItems.DARK_MATTER.get(), 2),
          out(() -> PiItems.NEUTRONIUM_INGOT.get(), 2)), out(() -> PiItems.QUASAR_CRYSTAL.get(), 1), 0.1F);

    /** Duree d'un cycle (ticks). */
    private final int duration;
    private final long energyPerTickFE;
    public final long plasmaPerTick;
    public final int color;
    private final List<Output> outputs;
    private final Output bonus;
    private final float bonusChance;

    GalaxyType(int duration, long energyPerTickFE, long plasmaPerTick, int color, List<Output> outputs, Output bonus, float bonusChance) {
        this.duration = duration;
        this.energyPerTickFE = energyPerTickFE;
        this.plasmaPerTick = plasmaPerTick;
        this.color = color;
        this.outputs = outputs;
        this.bonus = bonus;
        this.bonusChance = bonusChance;
    }

    /** Duree d'un cycle (ticks), selon la configuration. */
    public int cycleTicks() {
        return Math.max(20, (int) Math.round(duration * PiCommonConfig.get(PiCommonConfig.UNIVERSE_DURATION, 1.0)));
    }

    /** Energie consommee par tick (FE), selon la configuration. */
    public long energyPerTick() {
        return Math.max(1, Math.round(energyPerTickFE * PiCommonConfig.get(PiCommonConfig.UNIVERSE_ENERGY, 1.0)));
    }

    /** Chance du produit bonus a chaque cycle, selon la configuration. */
    public float bonusChance() {
        return (float) Math.min(1, bonusChance * PiCommonConfig.get(PiCommonConfig.UNIVERSE_OUTPUT, 1.0));
    }

    public record Output(Supplier<Item> item, int count) {

        public ItemStack stack() {
            return new ItemStack(item.get(), count);
        }
    }

    private static Output out(Supplier<Item> item, int count) {
        return new Output(item, count);
    }

    public List<ItemStack> outputs() {
        return outputs.stream().map(Output::stack).toList();
    }

    public ItemStack bonus() {
        return bonus.stack();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getTranslationKey() {
        return "universe.pioneers.galaxy." + id();
    }

    public static GalaxyType byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : SPIRAL;
    }
}
