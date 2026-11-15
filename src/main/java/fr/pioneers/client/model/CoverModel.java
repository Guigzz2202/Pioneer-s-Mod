package fr.pioneers.client.model;

import fr.pioneers.Pioneers;
import fr.pioneers.core.block.Covers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 27) : rendu des CACHES ({@link Covers}) : un cable logistique ou un arbre de rotation couvert ({@link Covers#COVERED}) est
 * dessine avec le vrai modele de son bloc de cache, dans le maillage du tronçon (aucun cout par image ; l'arbre n'est plus dessine par son
 * rendu tournant). La cache est lue dans la tuile a la construction du chunk ({@link #getModelData}) ou prise dans les donnees de modele de
 * la tuile (fissures de casse, particules). Teintes (herbe, feuillages) : celles de la cache ({@link #colors}).
 * <p>1.0.4-beta (lot 30) : un cable ou un arbre ENCADRE sans cache ({@link Covers#FRAMED}) garde son propre modele et recoit en plus celui du
 * cadre de cache ({@code block/logistics/cover_frame} : douze traverses de bois aux aretes ; l'arbre tournant reste dessine par son rendu).
 */
public class CoverModel extends BakedModelWrapper<BakedModel> {

    /** Donnees de modele de la cache elle-meme (textures connectees d'une carcasse...). */
    private static final ModelProperty<ModelData> COVER_DATA = new ModelProperty<>();

    private CoverModel(BakedModel original) {
        super(original);
    }

    /** Enveloppe les modeles des etats couverts des blocs a cache du mod (ModelEvent.ModifyBakingResult). */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (Block block : coverable()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!Covers.framed(state)) {
                    continue;
                }
                ResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(key);
                if (model != null && !(model instanceof CoverModel)) {
                    models.put(key, new CoverModel(model));
                }
            }
        }
    }

    /** Blocs du mod qui portent {@link Covers#COVERED}. */
    public static List<Block> coverable() {
        return ForgeRegistries.BLOCKS.getEntries().stream()
              .filter(e -> e.getKey().location().getNamespace().equals(Pioneers.MODID) && e.getValue().defaultBlockState().hasProperty(Covers.COVERED))
              .map(Map.Entry::getValue).toList();
    }

    /** Teintes des blocs a cache : celles du bloc de cache (herbe, feuilles...), aucune sinon. */
    public static void colors(RegisterColorHandlersEvent.Block event) {
        List<Block> blocks = coverable();
        event.register((state, level, pos, tint) -> {
            BlockState cover = level == null || pos == null ? null : Covers.coverOf(state, level, pos);
            return cover == null ? -1 : Minecraft.getInstance().getBlockColors().getColor(cover, level, pos, tint);
        }, blocks.toArray(Block[]::new));
    }

    private static BakedModel model(BlockState cover) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(cover);
    }

    /** 1.0.4-beta (lot 30) : cadre sans cache : modele propre du bloc + cadre de cache. */
    private static boolean frameOnly(@Nullable BlockState state) {
        return state != null && Covers.framed(state) && !Covers.covered(state);
    }

    @Nullable
    private static BlockState cover(@Nullable BlockState state, ModelData data) {
        BlockState cover = data.get(Covers.COVER);
        return cover != null && state != null && Covers.covered(state) ? cover : null;
    }

    private static ModelData coverData(ModelData data) {
        ModelData inner = data.get(COVER_DATA);
        return inner != null ? inner : ModelData.EMPTY;
    }

    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        BlockState cover = Covers.coverOf(state, level, pos);
        if (cover == null) {
            return super.getModelData(level, pos, state, modelData);
        }
        ModelData inner = model(cover).getModelData(level, pos, cover, ModelData.EMPTY);
        return modelData.derive().with(Covers.COVER, cover).with(COVER_DATA, inner).build();
    }

    @NotNull
    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        if (frameOnly(state)) {
            BlockState frame = Covers.frameState();
            return ChunkRenderTypeSet.union(super.getRenderTypes(state, rand, data), model(frame).getRenderTypes(frame, rand, ModelData.EMPTY));
        }
        BlockState cover = cover(state, data);
        return cover == null ? super.getRenderTypes(state, rand, data) : model(cover).getRenderTypes(cover, rand, coverData(data));
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data,
          @Nullable RenderType renderType) {
        if (frameOnly(state)) {
            //chaque modele dans ses propres passes (le cable peut etre en decoupe, le cadre est plein) : rien dessine deux fois
            BlockState frame = Covers.frameState();
            BakedModel fm = model(frame);
            boolean own = renderType == null || super.getRenderTypes(state, rand, data).contains(renderType);
            boolean framePass = renderType == null || fm.getRenderTypes(frame, rand, ModelData.EMPTY).contains(renderType);
            List<BakedQuad> out = new ArrayList<>(own ? super.getQuads(state, side, rand, data, renderType) : List.of());
            if (framePass) {
                out.addAll(fm.getQuads(frame, side, rand, ModelData.EMPTY, renderType));
            }
            return out;
        }
        BlockState cover = cover(state, data);
        return cover == null ? super.getQuads(state, side, rand, data, renderType) : model(cover).getQuads(cover, side, rand, coverData(data), renderType);
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        BlockState cover = data.get(Covers.COVER);
        return cover == null ? super.getParticleIcon(data) : model(cover).getParticleIcon(coverData(data));
    }
}
