package fr.pioneers.tech.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.smithing.ForgeHearthBlock;
import fr.pioneers.tech.forge.smithing.ForgeHearthTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Foyer de forge : la piece dans la braise (barre a la couleur du metal et de son incandescence, lumineuse) et les lingots qui attendent,
 * froids, sur le rebord arriere. Repere du modele : facade au sud, braise a {@link #COALS_Y} px.
 */
public class HearthRenderer implements BlockEntityRenderer<ForgeHearthTile> {

    public static final float COALS_Y = 10F;
    private static final int VIEW = 48;
    /** Echelle de l'objet couche dans la braise (lot 15). */
    public static final float ITEM_SCALE = 0.5F;
    /**
     * 1.0.5-beta (lot 23, Guillaume : « regression : l'item en train de chauffer dans la forge n'est plus en 3D ») : vraie cause, lot 15 : la
     * barre en relief d'avant (boites {@code forge_work}) a ete remplacee par l'objet vise couche a plat ; un lingot ou une piece dessines par
     * {@code item/generated} n'ont qu'un pixel d'epaisseur (0,5 px a l'echelle 0,5) : une decalcomanie posee sur la braise. L'objet plat garde
     * sa vraie texture et sa teinte de chaleur mais prend du RELIEF : son epaisseur est multipliee par {@link #FLAT_DEPTH} (2 px dans la braise,
     * comme la barre d'avant, 1,6 a 2,6 px) ; un objet deja en 3D (tete d'outil, modele a elements) garde ses proportions.
     */
    public static final float FLAT_DEPTH = 4F;

    public HearthRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Facteur d'epaisseur de la piece couchee : {@link #FLAT_DEPTH} si le modele de l'objet vise est plat (objet d'inventaire dessine de face,
     * sans eclairage de bloc : {@code item/generated} et ses enfants), 1 s'il est deja en relief.
     */
    public static float depth(ItemStack hot, ForgeHearthTile tile) {
        ItemStack look = HeatedMetal.lookOf(hot, tile.getLevel());
        if (look.isEmpty()) {
            return FLAT_DEPTH;
        }
        return depthOf(Minecraft.getInstance().getItemRenderer().getModel(look, tile.getLevel(), null, 0).usesBlockLight());
    }

    /** PUR (banc) : epaisseur d'un modele selon qu'il est en relief ({@code usesBlockLight}) ou plat. */
    public static float depthOf(boolean modelIn3d) {
        return modelIn3d ? 1F : FLAT_DEPTH;
    }

    @Override
    public boolean shouldRender(@NotNull ForgeHearthTile tile, @NotNull Vec3 camera) {
        return Vec3.atCenterOf(tile.getBlockPos()).closerThan(camera, VIEW);
    }

    @Override
    public void render(@NotNull ForgeHearthTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        ItemStack hot = tile.hotPiece(), waiting = tile.waiting();
        if (hot.isEmpty() && waiting.isEmpty()) {
            return;
        }
        Direction facing = tile.getBlockState().hasProperty(ForgeHearthBlock.FACING) ? tile.getBlockState().getValue(ForgeHearthBlock.FACING)
              : Direction.SOUTH;
        KDraw d;
        if (!hot.isEmpty()) {
            //1.0.2-beta (lot 2) : un lopin d'acier (pas du metal chauffe) prend la temperature de la braise
            boolean metal = HeatedMetal.is(hot);
            float temp = metal ? HeatedMetal.temperatureNow(hot, tile.getLevel()) : tile.temperature();
            int glow = ForgeMetals.glow(temp) >>> 24;
            int lit = LightTexture.pack(Math.max(glow * 15 / 255, LightTexture.block(light)), LightTexture.sky(light));
            //Une barre par lingot de matiere, cote a cote dans la braise (le lopin : une seule, plus epaisse)
            int bars = metal ? Math.max(1, Math.min(4, HeatedMetal.amount(hot) / ForgeMetals.INGOT_MB)) : 1;
            //1.0.5-beta (lot 15) : metal chauffe qui a un objet vise (lingot, piece) : cet objet couche dans la braise, teinte par sa chaleur
            //(HeatedMetalItemRenderer) ; la barre generique ne reste que pour le lopin et la forge sans recette choisie
            if (metal && !HeatedMetal.lookOf(hot, tile.getLevel()).isEmpty()) {
                int shown = HeatedMetal.hasWork(hot) || !HeatedMetal.shape(hot).isEmpty() ? 1 : bars;
                for (int i = 0; i < shown; i++) {
                    pose.pushPose();
                    pose.translate(0.5F, (COALS_Y + 0.3F) / 16F, 0.5F);
                    pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
                    pose.translate(0F, i * 0.06F, ((i - (shown - 1) / 2F) * 3.0F) / 16F);
                    pose.mulPose(Axis.XP.rotationDegrees(90F));
                    pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE * depth(hot, tile));
                    Minecraft.getInstance().getItemRenderer().renderStatic(hot, ItemDisplayContext.FIXED, lit, overlay, pose, buffers, tile.getLevel(), 0);
                    pose.popPose();
                }
                hot = ItemStack.EMPTY;
            }
        }
        if (!hot.isEmpty()) {
            boolean metal = HeatedMetal.is(hot);
            float temp = metal ? HeatedMetal.temperatureNow(hot, tile.getLevel()) : tile.temperature();
            int glow = ForgeMetals.glow(temp) >>> 24;
            int lit = LightTexture.pack(Math.max(glow * 15 / 255, LightTexture.block(light)), LightTexture.sky(light));
            int bars = metal ? Math.max(1, Math.min(4, HeatedMetal.amount(hot) / ForgeMetals.INGOT_MB)) : 1;
            d = new KDraw(pose, buffers, lit, overlay);
            d.push().face(facing);
            d.tint(0xFF000000 | ForgeMetals.tint(metal ? HeatedMetal.metal(hot) : "steel", temp));
            float z0 = 8F - bars * 1.6F;
            for (int i = 0; i < bars; i++) {
                float z = z0 + i * 3.2F;
                d.box(4.5F, COALS_Y - 0.6F, z, 11.5F, COALS_Y + 1.0F, z + 2.6F, "forge_work", "forge_work");
            }
            d.tint(0xFFFFFFFF);
            d.pop();
        }
        if (!waiting.isEmpty()) {
            d = new KDraw(pose, buffers, light, overlay);
            d.push().face(facing);
            d.tint(0xFF000000 | ForgeMetals.colour(tile.waitingMetal()));
            int n = Math.min(3, waiting.getCount());
            for (int i = 0; i < n; i++) {
                d.box(2.2F + i * 4F, 12F, 1.3F, 5.6F + i * 4F, 13.2F, 2.9F, "forge_work", "forge_work");
            }
            d.tint(0xFFFFFFFF);
            d.pop();
        }
    }
}
