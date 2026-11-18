package fr.pioneers.logistics.rail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.logistics.rail.block.StationBlock;
import fr.pioneers.logistics.rail.block.StationTile;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 : nom de la gare ecrit sur les deux faces de l'ecran du panneau (texte lumineux vert, comme les ecrans des machines).
 * <p>
 * 0.20.46 (audit) : texte mis en forme une fois par nom (cache par rendu) ; plus dessine au-dela de {@link #RANGE} blocs (64 avant).
 */
public class StationRenderer implements BlockEntityRenderer<StationTile> {

    /** 0.20.46 (audit) : distance d'affichage du nom (blocs). */
    static final int RANGE = 48;
    /** 0.20.46 (audit) : textes deja mis en forme, par nom (fil de rendu ; 128 au plus ; refait avec le rendu au rechargement des ressources). */
    private final Map<String, Text> texts = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Text> eldest) {
            return size() > 128;
        }
    };

    private record Text(FormattedCharSequence text, float width) {
    }

    private final Font font;

    public StationRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.getFont();
    }

    @Override
    public void render(@NotNull StationTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        String name = tile.name();
        if (name.isEmpty()) {
            return;
        }
        Direction facing = tile.getBlockState().getValue(StationBlock.FACING);
        int k = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        Text cached = texts.get(name);
        if (cached == null) {
            FormattedCharSequence seq = Component.literal(name).getVisualOrderText();
            cached = new Text(seq, font.width(seq));
            texts.put(name, cached);
        }
        FormattedCharSequence text = cached.text();
        float width = cached.width();
        float scale = Math.min(0.0075F, 0.66F / Math.max(1, width));
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90 * k + (side == 1 ? 180 : 0)));
            pose.translate(0, 18.3 / 16, 0.72 / 16 + 0.001);
            pose.scale(scale, -scale, scale);
            font.drawInBatch(text, -width / 2, -4, 0xFF8CF5C6, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                  LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    @Override
    public int getViewDistance() {
        return RANGE;
    }
}
