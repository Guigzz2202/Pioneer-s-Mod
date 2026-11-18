package fr.pioneers.tech.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.core.RenderClock;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/** Icone des pieces de l'age mecanique : le modele fixe du bloc puis sa partie mobile, comme dans le monde (en rotation lente en main). */
public class KineticItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static KineticItemRenderer instance;

    private KineticItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static KineticItemRenderer instance() {
        if (instance == null) {
            instance = new KineticItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return;
        }
        BlockState state = item.getBlock().defaultBlockState();
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        float scale = ItemVisuals.scale(state);
        if (scale != 1) {
            pose.translate(0.5, 0.5, 0.5);
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
        }
        mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay, ModelData.EMPTY, RenderType.cutout());
        if (context == ItemDisplayContext.GUI || mc.level == null) {
            //0.20.46 (audit) : icone immobile (angle 15) : geometrie enregistree une fois par etat, rejouee a chaque image
            recorded(state).replay(pose, buffers.getBuffer(KDraw.renderType()), light, overlay);
        } else {
            //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent), fraction de l'image comprise
            float angle = (float) (RenderClock.time(mc.getFrameTime()) * 2 % 720);
            KineticVisuals.draw(new KDraw(pose, buffers, light, overlay), state, null, angle, null);
            ItemVisuals.extra(new KDraw(pose, buffers, light, overlay), state);
        }
        pose.popPose();
    }

    /** 0.20.46 (audit) : geometrie des icones par etat (videe au rechargement des textures). */
    private final Map<BlockState, KRecord> recorded = new IdentityHashMap<>();
    private int generation = -1;

    private KRecord recorded(BlockState state) {
        if (generation != KDraw.generation()) {
            generation = KDraw.generation();
            recorded.clear();
        }
        KRecord rec = recorded.get(state);
        if (rec == null) {
            rec = record(state, 15);
            recorded.put(state, rec);
        }
        return rec;
    }

    /** Dessin de la piece (comme dans l'inventaire) dans le repere local, sans lumiere ni superposition (posees au rejeu). */
    static KRecord record(BlockState state, float angle) {
        KRecord rec = new KRecord();
        PoseStack local = new PoseStack();
        KineticVisuals.draw(new KDraw(local, rec, KRecord.LIGHT, KRecord.OVERLAY), state, null, angle, null);
        ItemVisuals.extra(new KDraw(local, rec, KRecord.LIGHT, KRecord.OVERLAY), state);
        return rec;
    }
}
