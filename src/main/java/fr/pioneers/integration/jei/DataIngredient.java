package fr.pioneers.integration.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.core.data.DataUnits;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

/**
 * Les donnees comme ingredient JEI : elles apparaissent dans la liste, et un clic montre ce qui les produit (analyseurs) ou les consomme
 * (synthese planetaire). Icone : un cristal teinte de la couleur du type, avec la quantite en petit.
 */
public final class DataIngredient {

    private DataIngredient() {
    }

    public static final IIngredientType<DataStack> TYPE = () -> DataStack.class;
    private static final ResourceLocation ICON = Pioneers.rl("textures/gui/jei/data.png");

    /** Une pile par type de donnees (quantite 1), pour la liste de JEI. */
    public static List<DataStack> all() {
        List<DataStack> list = new ArrayList<>();
        for (DataType type : PioneersAPI.dataTypeRegistry().getValues()) {
            if (!type.isEmptyType()) {
                list.add(DataStack.of(type, 1));
            }
        }
        return list;
    }

    /** Quantite compacte pour la surimpression (1,5k, 64k, 2M...). */
    static String compact(long amount) {
        if (amount < 1_000) {
            return Long.toString(amount);
        }
        String[] units = {"k", "M", "G", "T"};
        double value = amount;
        int unit = -1;
        while (value >= 1_000 && unit < units.length - 1) {
            value /= 1_000;
            unit++;
        }
        String number = value >= 100 || value == Math.floor(value) ? String.format("%.0f", Math.floor(value)) : String.format("%.1f", value);
        return number + units[unit];
    }

    public static final class Helper implements IIngredientHelper<DataStack> {

        @NotNull
        @Override
        public IIngredientType<DataStack> getIngredientType() {
            return TYPE;
        }

        @NotNull
        @Override
        public String getDisplayName(DataStack stack) {
            return stack.getType().getName().getString();
        }

        @NotNull
        @Override
        public String getUniqueId(DataStack stack, @NotNull UidContext context) {
            return "data:" + getResourceLocation(stack);
        }

        @NotNull
        @Override
        public ResourceLocation getResourceLocation(DataStack stack) {
            ResourceLocation name = stack.getType().getRegistryName();
            return name == null ? Pioneers.rl("empty") : name;
        }

        @NotNull
        @Override
        public DataStack copyIngredient(DataStack stack) {
            return stack.copy();
        }

        @NotNull
        @Override
        public DataStack normalizeIngredient(DataStack stack) {
            return stack.copyWithAmount(1);
        }

        @Override
        public long getAmount(DataStack stack) {
            return stack.getAmount();
        }

        @NotNull
        @Override
        public DataStack copyWithAmount(DataStack stack, long amount) {
            return stack.copyWithAmount(amount);
        }

        @Override
        public boolean isValidIngredient(DataStack stack) {
            return !stack.getType().isEmptyType();
        }

        @NotNull
        @Override
        public Iterable<Integer> getColors(DataStack stack) {
            return List.of(stack.getType().getColor());
        }

        @NotNull
        @Override
        public String getErrorInfo(DataStack stack) {
            return stack == null ? "null" : stack.toString();
        }
    }

    public static final class Renderer implements IIngredientRenderer<DataStack> {

        @Override
        public void render(@NotNull GuiGraphics graphics, @NotNull DataStack stack) {
            int color = stack.getType().getColor();
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, 1);
            graphics.blit(ICON, 0, 0, 0, 0, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            if (stack.getAmount() > 1) {
                Font font = Minecraft.getInstance().font;
                String text = compact(stack.getAmount());
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.pose().scale(0.5F, 0.5F, 1);
                graphics.drawString(font, text, 32 - font.width(text), 24, 0xFFFFFFFF, true);
                graphics.pose().popPose();
            }
        }

        @NotNull
        @Override
        public List<Component> getTooltip(@NotNull DataStack stack, @NotNull TooltipFlag flag) {
            List<Component> lines = new ArrayList<>();
            lines.add(stack.getType().getName().copy().withStyle(style -> style.withColor(TextColor.fromRgb(stack.getType().getColor()))));
            if (stack.getAmount() > 1) {
                lines.add(DataUnits.format(stack.getAmount()).copy().withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("jei.pioneers.data.kind").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
    }
}
