package fr.pioneers.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 30) : cadre de cache en bois ({@link PiCoverFrame}) pose seul : douze traverses de 2 px aux aretes du cube (modele
 * {@code block/logistics/cover_frame}, le meme qui est dessine autour d'un cable ou d'un arbre encadre : {@code client/model/CoverModel}).
 * Forme pleine (on marche dessus, on le vise entier), ne cache ni la lumiere ni le ciel.
 */
public class CoverFrameBlock extends Block {

    public CoverFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 1.0F;
    }
}
