package fr.pioneers.tech.forge.casting;

import fr.pioneers.tech.forge.PiForge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.2-beta (lot 2) : tuile du moule d'enclume pose ({@link AnvilMoldBlock}) : celle d'un moule pose ({@link PlacedMoldTile} : coulee a la
 * pince, refroidissement, piece prise main vide, usure du moule), mais elle GARDE le metal d'une coulee partielle sans limite de temps
 * ({@link #keepsPartial}) : 1 440 mB (10 lingots) se versent en plusieurs fois au creuset en argile (288 mB par fusion). Memes cles NBT que
 * le moule pose.
 * <p>
 * 1.0.2-beta (lot 5) : le moule ne donne plus que l'enclume en fer (seule coulee {@code pioneers:casting} du moule : 1 440 mB de fer) ; la
 * pince refuse le bronze avec un message ({@code casting.pioneers.anvil_mold.iron_only}). Un moule d'un monde d'avant qui garde du bronze
 * partiel (plus de recette) ne le garde plus : il fige comme un moule pose ordinaire, en lingots et pepites de bronze (rien ne se perd).
 */
public class AnvilMoldTile extends PlacedMoldTile {

    /** Forme du moule (champ {@code shape} de son objet {@code MoldItem}). */
    public static final String SHAPE = "anvil";
    /** 1.0.2-beta (lot 5) : seul metal de l'enclume (recette {@code bronze/casting/iron_anvil}). */
    public static final String METAL = "iron";

    public AnvilMoldTile(BlockPos pos, BlockState state) {
        super(PiForge.PLACED_ANVIL_MOLD_TILE.get(), pos, state);
    }

    @Override
    protected boolean keepsPartial() {
        //Lot 5 : seulement un metal qui a encore sa coulee (le fer) ; le bronze partiel d'avant fige en lingots et pepites
        return metal.isEmpty() || recipeFor(metal) != null;
    }
}
