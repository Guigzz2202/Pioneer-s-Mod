package fr.pioneers.integration.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.Texts;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

/**
 * Les gaz du socle comme ingredient JEI (sans Mekanism) : texture du gaz teintee, en carre 16 x 16 dans la liste, en jauge remplie
 * selon une capacite dans les recettes.
 */
public final class GasIngredient {

    private GasIngredient() {
    }

    public static final IIngredientType<GasStack> TYPE = () -> GasStack.class;

    /** Une pile de 1 000 mB par gaz visible, pour la liste de JEI. */
    public static List<GasStack> all() {
        List<GasStack> list = new ArrayList<>();
        for (Gas gas : PioneersAPI.allGases()) {
            if (!gas.isEmptyType() && !gas.isHidden()) {
                list.add(GasStack.of(gas, 1000));
            }
        }
        return list;
    }

    public static final class Helper implements IIngredientHelper<GasStack> {

        @NotNull
        @Override
        public IIngredientType<GasStack> getIngredientType() {
            return TYPE;
        }

        @NotNull
        @Override
        public String getDisplayName(GasStack stack) {
            return stack.getGas().getName().getString();
        }

        @NotNull
        @Override
        public String getUniqueId(GasStack stack, @NotNull UidContext context) {
            return "gas:" + getResourceLocation(stack);
        }

        @NotNull
        @Override
        public ResourceLocation getResourceLocation(GasStack stack) {
            ResourceLocation name = stack.getGas().getRegistryName();
            return name == null ? Pioneers.rl("empty") : name;
        }

        @NotNull
        @Override
        public GasStack copyIngredient(GasStack stack) {
            return stack.copy();
        }

        @NotNull
        @Override
        public GasStack normalizeIngredient(GasStack stack) {
            return stack.copyWithAmount(1000);
        }

        @Override
        public long getAmount(GasStack stack) {
            return stack.getAmount();
        }

        @NotNull
        @Override
        public GasStack copyWithAmount(GasStack stack, long amount) {
            return stack.copyWithAmount(amount);
        }

        @Override
        public boolean isValidIngredient(GasStack stack) {
            return !stack.isEmpty();
        }

        @NotNull
        @Override
        public Iterable<Integer> getColors(GasStack stack) {
            return List.of(stack.getGas().getTint());
        }

        @NotNull
        @Override
        public String getErrorInfo(GasStack stack) {
            return stack == null ? "null" : stack.toString();
        }
    }

    /** Rendu : carre 16 x 16 par defaut, ou jauge {@code width} x {@code height} remplie a {@code amount / capacity}. */
    public static final class Renderer implements IIngredientRenderer<GasStack> {

        private final long capacity;
        private final int width, height;

        public Renderer() {
            this(0, 16, 16);
        }

        public Renderer(long capacity, int width, int height) {
            this.capacity = capacity;
            this.width = width;
            this.height = height;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, @NotNull GasStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            int filled = capacity <= 0 ? height : (int) Math.max(1, Math.min(height, Math.round((double) height * stack.getAmount() / capacity)));
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stack.getGas().getIcon());
            int tint = stack.getGas().getTint();
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F, 1);
            //Tuiles de 16 x 16 depuis le bas (la derniere rangee et la derniere colonne sont rognees par etirement)
            for (int y = 0; y < filled; y += 16) {
                int h = Math.min(16, filled - y);
                for (int x = 0; x < width; x += 16) {
                    int w = Math.min(16, width - x);
                    graphics.blit(x, height - y - h, 0, w, h, sprite);
                }
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }

        @NotNull
        @Override
        public List<Component> getTooltip(@NotNull GasStack stack, @NotNull TooltipFlag flag) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("jei.pioneers.res.gas_name", stack.getGas().getName()));   //1.0.6 : « Azote (Gaz) »
            lines.add(Component.translatable("jei.pioneers.mb", Texts.grouped(stack.getAmount()))
                  .withStyle(ChatFormatting.GRAY));
            return lines;
        }
    }
}
