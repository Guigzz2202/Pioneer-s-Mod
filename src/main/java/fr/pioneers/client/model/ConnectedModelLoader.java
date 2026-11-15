package fr.pioneers.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import fr.pioneers.core.Directions;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;

/**
 * Chargeur de modele "pioneers:connected".
 * <pre>
 * {
 *   "loader": "pioneers:connected",
 *   "parent": "minecraft:block/block",
 *   "tiles": "pioneers:block/stabilization_chamber_casing",          // dossier contenant none/h/v/inner/full.png
 *   "active_tiles": "pioneers:block/stabilization_chamber_port_output" // optionnel : tuiles quand le bloc est "actif"
 * }
 * </pre>
 */
public class ConnectedModelLoader implements IGeometryLoader<ConnectedModelLoader.Geometry> {

    public static final ConnectedModelLoader INSTANCE = new ConnectedModelLoader();

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) {
        ResourceLocation tiles = ResourceLocation.parse(GsonHelper.getAsString(json, "tiles"));
        ResourceLocation activeTiles = json.has("active_tiles") ? ResourceLocation.parse(GsonHelper.getAsString(json, "active_tiles")) : null;
        return new Geometry(tiles, activeTiles);
    }

    public record Geometry(ResourceLocation tiles, ResourceLocation activeTiles) implements IUnbakedGeometry<Geometry> {

        private static final FaceBakery FACE_BAKERY = new FaceBakery();

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
              ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
            int variants = activeTiles == null ? 1 : 2;
            BakedQuad[][][][] quads = new BakedQuad[variants][6][4][ConnectedTextures.TILE_NAMES.length];
            TextureAtlasSprite particle = null;
            for (int variant = 0; variant < variants; variant++) {
                ResourceLocation base = variant == 0 ? tiles : activeTiles;
                TextureAtlasSprite[] sprites = new TextureAtlasSprite[ConnectedTextures.TILE_NAMES.length];
                for (int t = 0; t < sprites.length; t++) {
                    ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "/" + ConnectedTextures.TILE_NAMES[t]);
                    sprites[t] = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, tex));
                }
                if (particle == null) {
                    particle = sprites[ConnectedTextures.NONE];
                }
                for (Direction face : Directions.ALL) {
                    for (int q = 0; q < 4; q++) {
                        for (int t = 0; t < sprites.length; t++) {
                            quads[variant][face.get3DDataValue()][q][t] = bakeQuadrant(face, q, sprites[t], modelLocation);
                        }
                    }
                }
            }
            ResourceLocation renderTypeHint = context.getRenderTypeHint();
            RenderTypeGroup renderTypes = renderTypeHint == null ? RenderTypeGroup.EMPTY : context.getRenderType(renderTypeHint);
            return new ConnectedBakedModel(quads, particle, context.getTransforms(), renderTypes);
        }

        /** Cuit le quart {@code quadrant} de la face {@code face}, avec les UV par defaut (le quart correspondant de la texture). */
        private static BakedQuad bakeQuadrant(Direction face, int quadrant, TextureAtlasSprite sprite, ResourceLocation modelLocation) {
            float[] from = {0, 0, 0};
            float[] to = {16, 16, 16};
            restrict(from, to, ConnectedTextures.uAxis(face), ConnectedTextures.uSign(quadrant));
            restrict(from, to, ConnectedTextures.vAxis(face), ConnectedTextures.vSign(quadrant));
            Map<Direction, BlockElementFace> faces = new EnumMap<>(Direction.class);
            //UV nulles : Minecraft les calcule a partir de la position, exactement comme pour un cube_all
            faces.put(face, new BlockElementFace(face, -1, "", new BlockFaceUV(null, 0)));
            BlockElement element = new BlockElement(new Vector3f(from[0], from[1], from[2]), new Vector3f(to[0], to[1], to[2]), faces, null, true);
            return FACE_BAKERY.bakeQuad(element.from, element.to, element.faces.get(face), sprite, face, BlockModelRotation.X0_Y0, null, true, modelLocation);
        }

        private static void restrict(float[] from, float[] to, Direction.Axis axis, int sign) {
            int i = axis.ordinal();
            if (sign < 0) {
                to[i] = 8;
            } else {
                from[i] = 8;
            }
        }
    }
}
