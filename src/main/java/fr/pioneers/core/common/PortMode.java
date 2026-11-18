package fr.pioneers.core.common;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.core.compat.mekanism.MekanismCompat;
import fr.pioneers.core.compat.mekanism.MekanismGasBridge;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.NotNull;

/**
 * Specialisation d'un port de multibloc : un type de contenu (objets, fluide, gaz, donnees) dans un sens (entree ou sortie).
 * Chaque multibloc ne propose que les modes qui ont un sens pour ses recettes ; le configurateur de Mekanism (accroupi + clic droit)
 * passe au mode suivant.
 */
public enum PortMode implements StringRepresentable {
    ITEM_INPUT(Kind.ITEM, false),
    ITEM_OUTPUT(Kind.ITEM, true),
    FLUID_INPUT(Kind.FLUID, false),
    FLUID_OUTPUT(Kind.FLUID, true),
    GAS_INPUT(Kind.GAS, false),
    GAS_OUTPUT(Kind.GAS, true),
    DATA_INPUT(Kind.DATA, false),
    DATA_OUTPUT(Kind.DATA, true);

    /** Ce que le port fait passer. */
    public enum Kind {
        ITEM(0xD0D0D0), FLUID(0x3F8CFF), GAS(0xF2D64B), DATA(0xA27BFF);

        public final int color;

        Kind(int color) {
            this.color = color;
        }

        /** Capacite de ce type de contenu. */
        public boolean handles(Capability<?> capability) {
            return switch (this) {
                case ITEM -> capability == ForgeCapabilities.ITEM_HANDLER;
                case FLUID -> capability == ForgeCapabilities.FLUID_HANDLER;
                case GAS -> capability == PioneersAPI.GAS_HANDLER || MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(capability);
                case DATA -> capability == PioneersAPI.DATA_HANDLER;
            };
        }
    }

    public final Kind kind;
    private final boolean output;

    PortMode(Kind kind, boolean output) {
        this.kind = kind;
        this.output = output;
    }

    public boolean isOutput() {
        return output;
    }

    /** Une capacite de contenu (objets, fluide, gaz, donnees) qui n'est pas celle de ce mode doit etre coupee. */
    public boolean blocks(Capability<?> capability) {
        for (Kind other : Kind.values()) {
            if (other != kind && other.handles(capability)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getTranslationKey() {
        return "port.pioneers.mode." + getSerializedName();
    }

    public MutableComponent getName() {
        return Component.translatable(getTranslationKey()).withStyle(output ? ChatFormatting.GOLD : ChatFormatting.AQUA);
    }

    /** Mode suivant parmi ceux autorises. */
    public static PortMode next(PortMode current, List<PortMode> allowed) {
        int index = allowed.indexOf(current);
        return allowed.get((index + 1) % allowed.size());
    }

    public static PortMode byName(String name, PortMode fallback) {
        for (PortMode mode : values()) {
            if (mode.getSerializedName().equals(name)) {
                return mode;
            }
        }
        return fallback;
    }
}
