package fr.pioneers.farm.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 6, Guillaume : « implementer les blocs de carottes, patates et autre produit de ferme ») : BLOC DE STOCKAGE D'UNE RECOLTE
 * (9 recoltes = 1 bloc, et retour, comme la botte de paille). Trois facons selon le produit ({@link Style}) : caisse de bois pleine (racines,
 * tubercules, legumes), deux cagettes empilees (fruits et legumes fragiles), sac de jute ouvert (grains). Oriente vers le joueur a la pose
 * (pochoir, poignees, nœud du sac). Aucune tuile. Modeles, textures, recettes, butins, tags : {@code tools/gc/farm2/crop_storage.py} (table
 * {@code STORAGE}, recopiee dans {@link GeneratedCropStorage}).
 */
public class CropStorageBlock extends HorizontalDirectionalBlock {

    /** Facon du bloc : forme (caisse et cagettes = cube plein ; sac = plus etroit et plus bas que le cube). */
    public enum Style {
        CRATE(Block.box(0, 0, 0, 16, 16, 16)),
        TRAY(Block.box(0, 0, 0, 16, 16, 16)),
        SACK(Block.box(1.5, 0, 1.5, 14.5, 14, 14.5));

        public final VoxelShape shape;

        Style(VoxelShape shape) {
            this.shape = shape;
        }
    }

    public final Style style;
    /** Recolte stockee (id complet), pour les fiches et les tests. */
    public final String produce;

    public CropStorageBlock(Style style, String produce, Properties properties) {
        super(properties);
        this.style = style;
        this.produce = produce;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return style.shape;
    }

    /** Un sac ne cache pas ses voisins ; la caisse et les cagettes sont pleines sur les cotes (le dessus est ouvert sur la recolte). */
    @Override
    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
    }
}
