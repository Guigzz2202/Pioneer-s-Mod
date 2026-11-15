package fr.pioneers.client.model;

import fr.pioneers.Pioneers;
import fr.pioneers.common.block.Connections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 23, Guillaume : « les casing et textures connectees sont cassees ») : rendu des blocs a textures connectees
 * ({@link Connections.Connected} : carcasses, verres industriels, salle blanche, foyers, bobines, aire de lancement) d'apres leurs VOISINS
 * REELS, lus a la construction du chunk ({@link #getModelData}), et plus d'apres les six booleens sauves dans l'etat : un etat perime
 * (bloc pose par un code ou une structure sans mise a jour de ses propres cotes, blocs des lots 21 / 21b relus sans booleens) se dessine
 * quand meme raccorde. Le modele multipart choisit ses quarts sur l'etat qu'on lui passe : on lui passe l'etat recalcule.
 */
public class ConnectedTexturesModel extends BakedModelWrapper<BakedModel> {

    /** Etat recalcule d'apres les voisins (absent : rendu hors du monde, etat tel quel). */
    private static final ModelProperty<BlockState> ACTUAL = new ModelProperty<>();

    private final Connections.Connected block;

    private ConnectedTexturesModel(BakedModel original, Connections.Connected block) {
        super(original);
        this.block = block;
    }

    /** Enveloppe les modeles de tous les etats des blocs connectes du mod (ModelEvent.ModifyBakingResult). */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (Map.Entry<ResourceKey<Block>, Block> e : ForgeRegistries.BLOCKS.getEntries()) {
            if (!e.getKey().location().getNamespace().equals(Pioneers.MODID) || !(e.getValue() instanceof Connections.Connected connected)) {
                continue;
            }
            for (BlockState state : e.getValue().getStateDefinition().getPossibleStates()) {
                ResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(key);
                if (model != null && !(model instanceof ConnectedTexturesModel)) {
                    models.put(key, new ConnectedTexturesModel(model, connected));
                }
            }
        }
    }

    private BlockState actual(@Nullable BlockState state, ModelData data) {
        BlockState fixed = data.get(ACTUAL);
        return fixed != null && state != null && fixed.getBlock() == state.getBlock() ? fixed : state;
    }

    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        BlockState fixed = state.hasProperty(Connections.property(Direction.UP)) ? Connections.actual(state, level, pos, block.connectFamily()) : state;
        return super.getModelData(level, pos, fixed, modelData).derive().with(ACTUAL, fixed).build();
    }

    @NotNull
    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return super.getRenderTypes(actual(state, data), rand, data);
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data,
          @Nullable RenderType renderType) {
        return super.getQuads(actual(state, data), side, rand, data, renderType);
    }
}
