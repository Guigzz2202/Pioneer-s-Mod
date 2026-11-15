package fr.pioneers.tech.industry.construction;

import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 1.0.4-beta (lot 21, 21c) : paroi de foyer d'un tier : grande grille a barreaux sur les quatre faces laterales (identiques), tole du tier
 * dessus / dessous ; textures connectees entre foyers du meme tier ; aucune orientation.
 * <p>1.0.4-beta (lot 23, Guillaume : « par defaut les firebox sont eteintes, elles s'animent de feu quand le multi est en fonctionnement ») :
 * {@link #LIT} faux par defaut (cendres froides derriere la grille, aucune lumiere) ; le controleur du multibloc qui la contient l'allume
 * quand il travaille ({@code api.multiblock.MultiblockCore.paintActive}) : feu anime (texture a images, .mcmeta) et lumiere
 * {@link PiConstruction#FIREBOX_LIGHT}.
 */
public class FireboxWallBlock extends TwinConnectedBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FireboxWallBlock(CableTier tier, Properties properties) {
        super("firebox_", tier, properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
