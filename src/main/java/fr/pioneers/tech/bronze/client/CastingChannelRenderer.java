package fr.pioneers.tech.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldBlock;
import fr.pioneers.tech.bronze.autofoundry.CastingChannelBlock;
import fr.pioneers.tech.bronze.autofoundry.CastingChannelTile;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.core.RenderClock;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 4) : le METAL QUI COULE dans un conduit de coulee : nappe incandescente dans la rigole (le coeur et chaque bras raccorde),
 * motif qui defile dans le sens du courant, et au bec un filet qui tombe dans le moule automatique du dessous. Teinte : blanc-jaune du metal en
 * fusion, nuance de la couleur du metal ; toujours eclaire (il rougeoie). {@link #draw} est aussi appele par le banc hors jeu (tools/kinetics,
 * scene « channel »).
 */
public class CastingChannelRenderer implements BlockEntityRenderer<CastingChannelTile> {

    /** Surface du metal dans la rigole (px) ; bords interieurs de la rigole : 6 .. 10. */
    public static final float SURFACE = 3.6F, IN0 = 6.02F, IN1 = 9.98F;

    public CastingChannelRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull CastingChannelTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (!tile.flowing() || tile.getLevel() == null) {
            return;
        }
        BlockState s = tile.getBlockState();
        if (!(s.getBlock() instanceof CastingChannelBlock)) {
            return;
        }
        float drop = 0;
        if (tile.spout()) {
            BlockState below = tile.getLevel().getBlockState(tile.getBlockPos().below());
            drop = below.hasProperty(AutoMoldBlock.FORM) ? 16 - below.getValue(AutoMoldBlock.FORM).top() : 4;
        }
        float time = (float) (RenderClock.time(partial) % 64000.0);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        draw(new KDraw(pose, buffers, LightTexture.FULL_BRIGHT, overlay), s.getValue(CastingChannelBlock.NORTH), s.getValue(CastingChannelBlock.SOUTH),
              s.getValue(CastingChannelBlock.EAST), s.getValue(CastingChannelBlock.WEST), tile.spout(), drop, tile.metal(), time);
    }

    /** Couleur du metal en fusion : blanc-jaune, nuance de la couleur du metal (un quart). */
    public static int glow(String metal) {
        int c = Metals.colour(metal);
        int r = (255 * 3 + (c >> 16 & 255)) / 4, g = (220 * 3 + (c >> 8 & 255)) / 4, b = (120 * 3 + (c & 255)) / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * Le metal dans la rigole (repere du bloc, px) : coeur, bras raccordes, filet du bec sur {@code drop} px sous le bloc (jusqu'au dessus du
     * moule) ; {@code time} en ticks (defilement).
     */
    public static void draw(KDraw d, boolean north, boolean south, boolean east, boolean west, boolean spout, float drop, String metal, float time) {
        float y0 = CastingChannelBlock.FLOOR + 0.02F, y1 = SURFACE;
        d.push();
        d.tint(glow(metal));
        d.scroll = (time * 0.6F) % 16;
        d.box(IN0, y0, IN0, IN1, y1, IN1, "forge_molten", "forge_molten");
        if (north) {
            d.box(IN0, y0, 0.02F, IN1, y1, IN0, "forge_molten", "forge_molten");
        }
        if (south) {
            d.box(IN0, y0, IN1, IN1, y1, 15.98F, "forge_molten", "forge_molten");
        }
        if (west) {
            d.box(0.02F, y0, IN0, IN0, y1, IN1, "forge_molten", "forge_molten");
        }
        if (east) {
            d.box(IN1, y0, IN0, 15.98F, y1, IN1, "forge_molten", "forge_molten");
        }
        if (spout && drop > 0) {
            //Filet qui tombe du bec (anneau du modele : ouverture 7 .. 9) dans le moule
            d.box(7.3F, -drop, 7.3F, 8.7F, y0, 8.7F, "forge_molten", "forge_molten");
        }
        d.scroll = 0;
        d.tint(0xFFFFFFFF);
        d.pop();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
