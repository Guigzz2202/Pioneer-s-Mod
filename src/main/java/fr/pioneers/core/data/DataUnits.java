package fr.pioneers.core.data;

import fr.pioneers.core.Texts;
import net.minecraft.network.chat.Component;

/**
 * Les donnees se comptent en megaoctets (Mo), affiches en Go / To au-dela.
 */
public final class DataUnits {

    private DataUnits() {
    }

    public static Component format(long megabytes) {
        if (megabytes >= CreativeDataTank.INFINITE) {
            return Component.literal("∞");
        } else if (megabytes >= 1_000_000) {
            return Component.translatable("data.pioneers.unit.tb", String.format("%.2f", megabytes / 1_000_000.0));
        } else if (megabytes >= 10_000) {
            return Component.translatable("data.pioneers.unit.gb", String.format("%.1f", megabytes / 1_000.0));
        }
        return Component.translatable("data.pioneers.unit.mb", Texts.format(megabytes));
    }
}
