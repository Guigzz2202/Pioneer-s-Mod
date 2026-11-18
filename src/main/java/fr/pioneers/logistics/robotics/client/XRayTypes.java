package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderType;

/**
 * Types de rendu « rayons X » du planificateur : traits et faces translucides dessines sans test de profondeur (le cadre de la zone et
 * ses poignees restent visibles a travers le terrain). Shaders vanilla seulement.
 */
final class XRayTypes extends RenderType {

    static final RenderType LINES = create("pioneers_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1024, false,
          false, CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.empty()))
                .setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).createCompositeState(false));
    static final RenderType QUADS = create("pioneers_xray_quads", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1024, false, true,
          CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).createCompositeState(false));

    private XRayTypes() {
        super("pioneers_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> {
        }, () -> {
        });
    }
}
