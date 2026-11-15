package fr.pioneers.api.data;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

/**
 * Role d'une face de machine pour les donnees (memes couleurs que la configuration des faces des machines).
 */
public enum DataSideMode {
    /** Face fermee : pas de prise, les cables ne s'y branchent pas. */
    NONE(null, 0x7A7A7A),
    /** Prise d'entree : accepte les donnees. */
    INPUT(ChatFormatting.DARK_RED, 0xC9071F),
    /** Prise de sortie : la machine y pousse ses donnees (ejection automatique). */
    OUTPUT(ChatFormatting.DARK_BLUE, 0x366BD0),
    /** Prise mixte : entree et extraction a la demande, sans ejection. */
    INPUT_OUTPUT(ChatFormatting.DARK_PURPLE, 0xA460D9);

    @Nullable
    private final ChatFormatting color;
    private final int rgb;

    DataSideMode(@Nullable ChatFormatting color, int rgb) {
        this.color = color;
        this.rgb = rgb;
    }

    /** Couleur du texte (null pour une face fermee). */
    @Nullable
    public ChatFormatting getColor() {
        return color;
    }

    /** Couleur RGB pour le rendu (gris pour une face fermee). */
    public int getRgb() {
        return rgb;
    }

    public boolean canInput() {
        return this == INPUT || this == INPUT_OUTPUT;
    }

    public boolean canOutput() {
        return this == OUTPUT || this == INPUT_OUTPUT;
    }

    public boolean isConnectable() {
        return this != NONE;
    }

    public String getTranslationKey() {
        return "data.pioneers.side." + name().toLowerCase(Locale.ROOT);
    }

    public Component getName() {
        Component name = Component.translatable(getTranslationKey());
        return color == null ? name : name.copy().withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
    }

    public static DataSideMode byIndex(int index) {
        DataSideMode[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
