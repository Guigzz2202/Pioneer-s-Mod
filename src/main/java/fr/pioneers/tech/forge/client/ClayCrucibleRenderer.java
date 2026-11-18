package fr.pioneers.tech.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.core.RenderClock;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.MoltenLoad;
import fr.pioneers.tech.forge.casting.ClayCrucibleBlock;
import fr.pioneers.tech.forge.casting.ClayCrucibleTile;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Creuset d'argile : surface du metal (incandescente et ondulante, bulles qui crevent, pleine lumiere quand il coule ; couleur du metal quand
 * il est fige), morceaux solides qui attendent de fondre, braises de charbon de bois sous le pot. Interieur du pot : 5..11 px, fond a 1 px,
 * bord a 8 px (modele {@code tools/gc/forge/casting.py}).
 */
public class ClayCrucibleRenderer implements BlockEntityRenderer<ClayCrucibleTile> {

    private static final float FLOOR = 1.0F, RIM = 7.4F;
    /** Positions des morceaux solides (x, z en px, angle). */
    private static final float[][] SPOTS = {{6.6F, 6.8F, 20}, {9.3F, 7.1F, 110}, {7.4F, 9.4F, 200}, {9.4F, 9.6F, 300}, {8F, 8.1F, 60}, {6.9F, 8.4F, 160}};
    /** Braises sous le pot (x0, z0, x1, z1). */
    private static final float[][] COALS = {{4.5F, 5, 7, 7.5F}, {9, 4.5F, 11.5F, 7}, {5, 9, 7.5F, 11.5F}, {9.5F, 9.5F, 11.8F, 11.8F}, {7, 7, 9, 9}};

    public ClayCrucibleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull ClayCrucibleTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        float time = (float) (RenderClock.time(partial) % 24000.0);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        MoltenLoad load = tile.load();
        int total = load.total();
        float surface = FLOOR;
        if (total > 0) {
            surface = FLOOR + (RIM - FLOOR) * Math.min(1F, total / (float) MoltenLoad.CLAY_CRUCIBLE_MB);
            String metal = tile.clientMetal();
            if (tile.molten()) {
                int c = brighten(ForgeMetals.tint(metal, Math.max(tile.temperature(), 1000F)), 1.15F);
                KDraw d = new KDraw(pose, buffers, LightTexture.FULL_BRIGHT, overlay);
                float bob = 0.12F * (float) Math.sin(time * 0.21F);
                d.tint(0xFF000000 | c);
                d.box(5.03F, FLOOR, 5.03F, 10.97F, surface + bob, 10.97F, "forge_molten", "forge_molten");
                //Bulles qui montent et crevent a la surface
                for (int i = 0; i < 3; i++) {
                    float ph = (time * 0.035F + i * 0.37F) % 1F;
                    float bx = 6.2F + (i * 2.3F) % 3.6F, bz = 6.4F + (i * 1.7F) % 3.4F;
                    float r = 0.25F + 0.45F * ph;
                    d.tint(0xFF000000 | brighten(c, 1.25F));
                    d.box(bx - r, surface + bob - r * 0.6F, bz - r, bx + r, surface + bob + r * 0.5F, bz + r, "forge_molten", "forge_molten");
                }
                d.tint(0xFFFFFFFF);
            } else {
                int c = metal.isEmpty() ? 0x7A6A5A : Metals.colour(metal);
                int glow = ForgeMetals.glow(tile.temperature());
                int lit = glow == 0 ? light : LightTexture.pack(Math.max(LightTexture.block(light), (glow >>> 24) * 15 / 255), LightTexture.sky(light));
                KDraw d = new KDraw(pose, buffers, lit, overlay);
                d.tint(0xFF000000 | brighten(glow == 0 ? c : ForgeMetals.tint(metal, tile.temperature()), 1.5F));
                d.box(5.03F, FLOOR, 5.03F, 10.97F, surface, 10.97F, "steel", "steel");
                d.tint(0xFFFFFFFF);
            }
        }
        List<ItemStack> solids = tile.solids();
        int n = Math.min(SPOTS.length, solids.size());
        for (int i = 0; i < n; i++) {
            float[] s = SPOTS[i];
            pose.pushPose();
            pose.translate(s[0] / 16F, (surface + 0.6F + (i >= 4 ? 0.9F : 0)) / 16F, s[1] / 16F);
            pose.mulPose(Axis.YP.rotationDegrees(s[2]));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.24F, 0.24F, 0.24F);
            Minecraft.getInstance().getItemRenderer().renderStatic(solids.get(i), ItemDisplayContext.FIXED, light, overlay, pose, buffers, level, 0);
            pose.popPose();
        }
        if (tile.hasFuel()) {
            BlockState state = tile.getBlockState();
            boolean stand = state.hasProperty(ClayCrucibleBlock.STAND) && state.getValue(ClayCrucibleBlock.STAND);
            float y0 = stand ? -9F : 0F;
            boolean fire = tile.onFire();
            float flicker = fire ? 0.85F + 0.15F * (float) Math.sin(time * 0.6F) : 0.45F;
            KDraw d = new KDraw(pose, buffers, fire ? LightTexture.FULL_BRIGHT : light, overlay);
            int g = Math.round(255 * flicker);
            d.tint(0xFF000000 | g << 16 | g << 8 | g);
            for (int i = 0; i < COALS.length; i++) {
                float[] c = COALS[i];
                float h = 1.0F + (i % 3) * 0.35F;
                d.box(c[0], y0, c[1], c[2], y0 + h, c[3], "coals", "coals");
            }
            d.tint(0xFFFFFFFF);
        }
    }

    private static int brighten(int rgb, float k) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * k));
        int g = Math.min(255, Math.round((rgb >> 8 & 255) * k));
        int b = Math.min(255, Math.round((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
