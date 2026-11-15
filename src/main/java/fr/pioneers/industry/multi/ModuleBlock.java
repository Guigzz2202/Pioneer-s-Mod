package fr.pioneers.industry.multi;

import fr.pioneers.industry.IndustryModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Module de travail : pose a l'interieur d'un multibloc, il y ajoute des operations en parallele. {@link PartBlock#ACTIVE} : peint par le
 * controleur quand la machine travaille (modele anime).
 */
public class ModuleBlock extends Block {

    /**
     * 0.19.14 : module masque (peint par le controleur a la formation) : le controleur dessine a la place une seule grande machine
     * ({@link BigInterior}) ; aucun modele (rendu INVISIBLE), la forme reste pour les clics et la casse.
     */
    public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");

    public final IndustryModule module;

    public ModuleBlock(IndustryModule module, Properties properties) {
        super(properties);
        this.module = module;
        registerDefaultState(stateDefinition.any().setValue(PartBlock.ACTIVE, false).setValue(HIDDEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PartBlock.ACTIVE, HIDDEN);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return state.getValue(HIDDEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }
}
