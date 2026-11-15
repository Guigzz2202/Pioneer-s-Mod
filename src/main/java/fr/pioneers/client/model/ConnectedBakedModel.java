package fr.pioneers.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Modele "cuit" a textures connectees. Tous les quads possibles sont precalcules ; a l'affichage on choisit
 * pour chaque quart de face la bonne tuile selon les voisins (calcules dans {@link #getModelData}).
 */
public class ConnectedBakedModel implements IDynamicBakedModel {

    public static final ModelProperty<byte[]> CONNECTIONS = new ModelProperty<>();

    /** [variante (0 = normal, 1 = actif)][face][quart][tuile] */
    private final BakedQuad[][][][] quads;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;
    /** Type de rendu impose par le JSON ("render_type"), vide = celui par defaut (opaque). */
    private final RenderTypeGroup renderTypes;
    private final ChunkRenderTypeSet blockRenderTypes;

    public ConnectedBakedModel(BakedQuad[][][][] quads, TextureAtlasSprite particle, ItemTransforms transforms, RenderTypeGroup renderTypes) {
        this.quads = quads;
        this.particle = particle;
        this.transforms = transforms;
        this.renderTypes = renderTypes;
        this.blockRenderTypes = renderTypes.isEmpty() ? null : ChunkRenderTypeSet.of(renderTypes.block());
    }

    @NotNull
    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return blockRenderTypes == null ? IDynamicBakedModel.super.getRenderTypes(state, rand, data) : blockRenderTypes;
    }

    @NotNull
    @Override
    public List<RenderType> getRenderTypes(@NotNull ItemStack stack, boolean fabulous) {
        if (renderTypes.isEmpty()) {
            return IDynamicBakedModel.super.getRenderTypes(stack, fabulous);
        }
        return List.of(fabulous ? renderTypes.entityFabulous() : renderTypes.entity());
    }

    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        return modelData.derive().with(CONNECTIONS, ConnectedTextures.compute(level, pos)).build();
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data,
          @Nullable RenderType renderType) {
        if (side == null) {
            return List.of();
        }
        int variant = quads.length > 1 && state != null && (state.hasProperty(BlockStateProperties.ENABLED)
              && state.getValue(BlockStateProperties.ENABLED) || isActive(state)) ? 1 : 0;
        byte[] connections = data.get(CONNECTIONS);
        BakedQuad[][] faceQuads = quads[variant][side.get3DDataValue()];
        List<BakedQuad> result = new ArrayList<>(4);
        for (int q = 0; q < 4; q++) {
            int tile = connections == null ? ConnectedTextures.NONE : connections[side.get3DDataValue() * 4 + q];
            result.add(faceQuads[q][tile]);
        }
        return result;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @NotNull
    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @NotNull
    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    /** Bloc "actif" (propriete {@code active} des machines du socle et de Mekanism). */
    private static boolean isActive(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("active") && property.getValueClass() == Boolean.class) {
                return (Boolean) state.getValue(property);
            }
        }
        return false;
    }
}
