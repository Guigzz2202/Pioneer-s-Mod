package fr.pioneers.forge.casting;

import fr.pioneers.bronze.foundry.MoldItem;
import fr.pioneers.forge.ForgeBlock;
import fr.pioneers.registries.PiForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 2, notes de Guillaume : « les enclumes auront desormais un moule specifique, qu'il faudra remplir avec le creuset pour
 * obtenir la premiere enclume en bronze ; l'enclume en fer aussi ; 10 lingots ; le creuset en argile permet de remplir en plusieurs fois le
 * moule ; porte d'entree de la forge ; le moule fera 1 bloc complet de haut ») : MOULE D'ENCLUME pose (bloc SANS objet, comme
 * {@link PlacedMoldBlock}) : un bloc d'argile cuite cercle de fer, d'un bloc de haut, creuse d'une empreinte d'enclume (table, taille,
 * semelle, bigorne). Pose par clic droit d'un moule d'enclume cuit sur le dessus d'un bloc ({@link #place}, appele par
 * {@code MoldItem.useOn}) ; la tuile ({@link AnvilMoldTile}) garde le moule (usure) et le metal verse a la pince, coulee apres coulee
 * (1 440 mB ; lot 5 : enclume en fer seulement, recette {@code pioneers:casting} {@code bronze/casting/iron_anvil}). Empreinte : {@link Cavity#BOXES} (rendu du metal ;
 * meme geometrie que {@code T:forge/casting.py ANVIL_CAVITY}).
 */
public class AnvilMoldBlock extends ForgeBlock {

    /** Fond et haut de l'empreinte (px) ; le dessus du moule est a 16. */
    public static final float FLOOR = 3, TOP = 15.2F;
    /**
     * Rectangles de l'empreinte {x0, z0, x1, z1} en px, moule tourne vers le sud (talon, table, bigorne en deux pas) ; classe a part, sans
     * Minecraft : le banc hors jeu la lit sans charger le bloc.
     */
    public static final class Cavity {
        public static final float[][] BOXES = {{1.5F, 5.5F, 2.5F, 10.5F}, {2.5F, 4.5F, 12.5F, 11.5F}, {12.5F, 5.5F, 14F, 10.5F}, {14F, 6.5F, 15F, 9.5F}};

        private Cavity() {
        }
    }

    public AnvilMoldBlock(Properties properties) {
        super(properties, AnvilMoldTile::new, Block.box(0, 0, 0, 16, 16, 16));
    }

    /** Pose un moule d'enclume cuit au-dessus de {@code support} ; vrai si c'est fait. */
    public static boolean place(Level level, BlockPos support, Direction facing, ItemStack mold) {
        if (!(mold.getItem() instanceof MoldItem item) || !item.shape.equals(AnvilMoldTile.SHAPE)) {
            return false;
        }
        BlockPos pos = support.above();
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        if (!level.setBlock(pos, PiForge.PLACED_ANVIL_MOLD.get().defaultBlockState().setValue(FACING, facing), 3)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof AnvilMoldTile tile) {
            tile.setMold(mold);
        }
        return true;
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return level.getBlockEntity(pos) instanceof AnvilMoldTile tile ? tile.mold().copy() : ItemStack.EMPTY;
    }
}
