package fr.pioneers.world.ruins;

import javax.annotation.Nullable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 1.9 (MAJ RP, bloc 1) : jumeau delabre d'une piece de multibloc (casing, port, bloc de metier, gravats). Sans inventaire, sans capacite, sans
 * fonction : une ruine ne se forme jamais en machine. {@link #WORN} = variante tres abimee (choisie par la generation). La casse lache des
 * scraps (table de butin), le Toucher de soie le bloc lui-meme ; casser un bloc delabre eveille le site ({@code RuinEvents}).
 */
public class DerelictBlock extends Block {

    public static final BooleanProperty WORN = BooleanProperty.create("worn");
    public final String role;
    public final int tier;

    public DerelictBlock(String role, int tier, Properties properties) {
        super(properties);
        this.role = role;
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(WORN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }
}
