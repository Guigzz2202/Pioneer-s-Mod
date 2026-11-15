package fr.pioneers.logistics.robotics.client;

import fr.pioneers.api.gas.Gas;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.Resource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * Icone 16x16 d'une ressource : l'objet ; la texture du fluide ou du gaz teintee ; un carre colore pour les donnees ; un eclair pour
 * l'energie. Les familles entieres gardent l'icone de la famille.
 */
final class ResourceIcon {

    private ResourceIcon() {
    }

    static void draw(GuiGraphics graphics, LogisticResource res, int x, int y) {
        switch (res.type) {
            case ITEMS -> {
                if (!res.isAny()) {
                    graphics.renderFakeItem(res.item(), x, y);
                    return;
                }
                if (res.tagKey() != null) {
                    //Tag : son premier objet, marque d'un #
                    graphics.renderFakeItem(res.icon(), x, y);
                    graphics.pose().pushPose();
                    graphics.pose().translate(0, 0, 200);
                    graphics.fill(x + 9, y, x + 16, y + 8, 0xC0000000);
                    graphics.drawString(Minecraft.getInstance().font, "#", x + 10, y, 0xFF8CF5C6, false);
                    graphics.pose().popPose();
                    return;
                }
            }
            case FLUIDS -> {
                FluidStack fluid = res.fluid();
                if (!fluid.isEmpty()) {
                    IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
                    sprite(graphics, ext.getStillTexture(fluid), ext.getTintColor(fluid), x, y);
                    return;
                }
            }
            case GAS -> {
                Gas gas = res.gas();
                if (gas != null) {
                    sprite(graphics, gas.getIcon(), gas.getTint(), x, y);
                    return;
                }
            }
            case DATA -> {
                if (res.data() != null) {
                    int color = 0xFF000000 | res.color();
                    graphics.fill(x + 2, y + 2, x + 14, y + 14, 0xFF101010);
                    graphics.fill(x + 3, y + 3, x + 13, y + 13, color);
                    graphics.fill(x + 5, y + 5, x + 11, y + 6, 0xA0FFFFFF);
                    graphics.fill(x + 5, y + 8, x + 11, y + 9, 0xA0FFFFFF);
                    graphics.fill(x + 5, y + 11, x + 9, y + 12, 0xA0FFFFFF);
                    return;
                }
            }
            case ENERGY -> {
                int c = 0xFFFF5040;
                graphics.fill(x + 8, y + 1, x + 11, y + 3, c);
                graphics.fill(x + 7, y + 3, x + 10, y + 6, c);
                graphics.fill(x + 5, y + 6, x + 12, y + 8, c);
                graphics.fill(x + 7, y + 8, x + 10, y + 11, c);
                graphics.fill(x + 6, y + 11, x + 9, y + 15, c);
                return;
            }
            case PRESSURE -> {
                //Manometre : cadran clair, aiguille sombre
                int rim = 0xFF55606F, dial = 0xFFDCE4EC, needle = 0xFFB5413A;
                graphics.fill(x + 4, y + 2, x + 12, y + 14, rim);
                graphics.fill(x + 2, y + 4, x + 14, y + 12, rim);
                graphics.fill(x + 5, y + 3, x + 11, y + 13, dial);
                graphics.fill(x + 3, y + 5, x + 13, y + 11, dial);
                graphics.fill(x + 7, y + 7, x + 9, y + 9, 0xFF2C323C);
                graphics.fill(x + 9, y + 5, x + 10, y + 8, needle);
                graphics.fill(x + 10, y + 4, x + 11, y + 6, needle);
                graphics.fill(x + 7, y + 12, x + 9, y + 15, rim);
                return;
            }
        }
        graphics.renderFakeItem(new ItemStack(res.type.icon), x, y);
        if (res.type != Resource.ITEMS || res.isAny()) {
            //Famille entiere : petit asterisque
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(Minecraft.getInstance().font, "*", x + 11, y - 1, 0xFFFFD040, true);
            graphics.pose().popPose();
        }
    }

    private static void sprite(GuiGraphics graphics, ResourceLocation texture, int tint, int x, int y) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        float r = (tint >> 16 & 255) / 255F, g = (tint >> 8 & 255) / 255F, b = (tint & 255) / 255F;
        graphics.blit(x, y, 0, 16, 16, sprite, r, g, b, 1);
    }
}
