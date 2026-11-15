package fr.pioneers.client.village;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.rp.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.village.NeedsBoardBlock;
import fr.pioneers.village.VillageBlocks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 1.9 (MAJ RP, bloc 3) : client des villages : ecran du villageois (et son panneau de dialogue, 1.9.1), Tableau des besoins, emissaire
 * Colporteur (1.9.1), marque de peuple (echarpe ou ruban) sur les villageois, icones de service sur le Tableau.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VillageClient {

    /** Peuple de chaque villageois suivi (identifiant d'entite -> peuple). */
    static final Map<Integer, String> PEOPLE = new HashMap<>();

    /**
     * 1.9.1 (retours de Guillaume) : une ligne du panneau de dialogue de la fiche du villageois : la replique (avec le nom de celui qui
     * parle) ou une narration (geste, refus). Plus aucune bulle au-dessus des tetes : tout s'ecrit dans la fiche.
     */
    public record Line(@javax.annotation.Nullable Component speaker, Component text, boolean narration) {
    }

    /** Historique de la visite en cours : repris si l'on revient au meme villageois dans les 3 minutes (apres un echange, par exemple). */
    static final class Visit {

        int entity = -1;
        long last;
        final java.util.List<Line> lines = new java.util.ArrayList<>();
    }

    static final Visit VISIT = new Visit();
    static final int MAX_LINES = 40;

    static Visit visit(int entity) {
        long now = System.currentTimeMillis();
        if (VISIT.entity != entity || now - VISIT.last > 180_000L) {
            VISIT.entity = entity;
            VISIT.lines.clear();
        }
        VISIT.last = now;
        return VISIT;
    }

    static void add(Visit v, Line line) {
        v.lines.add(line);
        while (v.lines.size() > MAX_LINES) {
            v.lines.remove(0);
        }
    }

    private VillageClient() {
    }

    /** Gestionnaires des paquets (appele par RpClient). */
    public static void handlers() {
        RpClient.on(RpActions.VILLAGER_VIEW, t -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof VillagerScreen v && v.entity() == t.getInt("entity")) {
                v.read(t);
            } else {
                mc.setScreen(new VillagerScreen(t));
            }
        });
        RpClient.on(RpActions.EMISSARY_VIEW, t -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof EmissaryScreen e && e.entity() == t.getInt("entity")) {
                e.read(t);
            } else {
                mc.setScreen(new EmissaryScreen(t));
            }
        });
        RpClient.on(RpActions.VILLAGER_PEOPLE, t -> PEOPLE.put(t.getInt("entity"), t.getString("people")));
        RpClient.on(RpActions.BOARD_VIEW, t -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof BoardScreen b) {
                b.read(t);
            } else {
                mc.setScreen(new BoardScreen(t));
            }
        });
    }

    // ------------------------------------------------------------------ marque de peuple, rendu du Tableau
    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        LivingEntityRenderer<Villager, VillagerModel<Villager>> r = event.getRenderer(EntityType.VILLAGER);
        if (r != null) {
            r.addLayer(new MarkLayer(r));
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VillageBlocks.BOARD_TILE.get(), BoardRenderer::new);
    }

    /** Echarpe ou ruban aux couleurs du peuple, par-dessus la tenue du villageois. */
    static final class MarkLayer extends RenderLayer<Villager, VillagerModel<Villager>> {

        MarkLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull Villager v, float limb, float limbAmount,
              float pt, float age, float yaw, float pitch) {
            String people = PEOPLE.get(v.getId());
            if (people == null || v.isInvisible() || people.isEmpty()) {
                return;
            }
            ResourceLocation tex = Pioneers.rl("textures/entity/village/mark_" + people + ".png");
            renderColoredCutoutModel(getParentModel(), tex, pose, buffers, light, v, 1, 1, 1);
        }
    }

    /** Cinq icones de service sur la reglette du Tableau (allumees si le service est actif). */
    static final class BoardRenderer implements BlockEntityRenderer<NeedsBoardBlock.Tile> {

        private static final String[] SERVICES = {"water", "energy", "workshop", "storage", "archives"};

        BoardRenderer(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull NeedsBoardBlock.Tile tile, float pt, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
              int overlay) {
            var face = tile.getBlockState().getValue(NeedsBoardBlock.FACING);
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-face.toYRot() + 180));
            pose.translate(-0.5, 0, -0.5);
            for (int k = 0; k < SERVICES.length; k++) {
                boolean on = tile.services().contains(SERVICES[k]);
                ResourceLocation tex = Pioneers.rl("textures/gui/village/service_" + SERVICES[k] + ".png");
                VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
                //facade au nord (z = 7 px) ; vu de face, la k-ieme icone (gauche a droite) est centree en x = 14,5 - 3k ; un peu en avant
                float xc = 14.5F - 3 * k;
                float x0 = (xc - 1.25F) / 16F, x1 = (xc + 1.25F) / 16F, y0 = 5.25F / 16F, y1 = y0 + 2.5F / 16F, z = 6.95F / 16F;
                int c = on ? 255 : 70;
                int l = on ? LightTexture.FULL_BRIGHT : light;
                Matrix4f m = pose.last().pose();
                var n = pose.last().normal();
                vc.vertex(m, x1, y0, z).color(c, c, c, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, -1).endVertex();
                vc.vertex(m, x0, y0, z).color(c, c, c, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, -1).endVertex();
                vc.vertex(m, x0, y1, z).color(c, c, c, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, -1).endVertex();
                vc.vertex(m, x1, y1, z).color(c, c, c, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 0, -1).endVertex();
            }
            pose.popPose();
        }
    }

    static void clear() {
        PEOPLE.clear();
        VISIT.entity = -1;
        VISIT.lines.clear();
    }

    static CompoundTag empty() {
        return new CompoundTag();
    }
}
