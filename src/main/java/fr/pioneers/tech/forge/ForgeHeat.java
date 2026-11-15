package fr.pioneers.tech.forge;

import fr.pioneers.tech.bronze.AirBlastReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Forge primaire : sources de chaleur et d'air. Un bloc qui chauffe ce qui est pose dessus (feu de camp allume, foyer de forge...) se
 * reconnait par {@link #heatBelowC} ; un soufflet (a main, mecanique, souffleur a vapeur) trouve son destinataire par {@link #airReceiver}.
 */
public final class ForgeHeat {

    /** Feu de camp allume (degres C). */
    public static final int CAMPFIRE_C = 1100;
    /** Charbon de bois au pied du feu (ajoute par le recipient qui le brule). */
    public static final int CHARCOAL_BONUS_C = 100;
    /** Apport maximal des soufflets (a main ou mecanique). */
    public static final int BELLOWS_MAX_C = 500;
    /** Une bouffee du soufflet a main (retombe ensuite). */
    public static final int HAND_PUFF_C = 40;

    private ForgeHeat() {
    }

    /** Ce qu'il faut pour atteindre une temperature, du plus simple au plus lourd. */
    public enum Level {
        CAMPFIRE(CAMPFIRE_C),
        CHARCOAL(CAMPFIRE_C + CHARCOAL_BONUS_C),
        BELLOWS(CAMPFIRE_C + CHARCOAL_BONUS_C + BELLOWS_MAX_C),
        INDUSTRIAL(Integer.MAX_VALUE);

        /** Temperature maximale atteinte a ce niveau (degres C). */
        public final int maxC;

        Level(int maxC) {
            this.maxC = maxC;
        }

        public static Level toReach(int tempC) {
            for (Level level : values()) {
                if (tempC <= level.maxC) {
                    return level;
                }
            }
            return INDUSTRIAL;
        }
    }

    /**
     * Tuile qui chauffe ce qui est pose sur elle (foyer de forge, et toute future source). Le feu de camp vanilla est reconnu sans cette
     * interface.
     */
    public interface HeatSource {

        /** Vrai si le feu brule. */
        boolean isLit();

        /** Temperature offerte au-dessus (degres C), soufflet et combustible compris. */
        int heatC();
    }

    /** Vrai pour un feu de camp (ou de ames) allume. */
    public static boolean isLitCampfire(BlockState state) {
        return state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
    }

    /**
     * Temperature offerte par ce qui est sous {@code pos} (degres C) : feu de camp allume = {@link #CAMPFIRE_C}, {@link HeatSource} allumee =
     * sa {@link HeatSource#heatC()}, sinon 0. Les bonus (charbon de bois, soufflet) sont ajoutes par le recipient lui-meme.
     */
    public static int heatBelowC(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.isLoaded(below)) {
            return 0;
        }
        BlockState state = level.getBlockState(below);
        if (isLitCampfire(state)) {
            return CAMPFIRE_C;
        }
        if (state.hasBlockEntity() && level.getBlockEntity(below) instanceof HeatSource source && source.isLit()) {
            return source.heatC();
        }
        return 0;
    }

    /**
     * Destinataire de l'air d'un soufflet tourne vers {@code target} : la tuile elle-meme si elle recoit l'air ; si c'est un feu de camp (ou
     * une {@link HeatSource} qui ne recoit pas l'air elle-meme), la tuile posee dessus (creuset d'argile). Null sinon. Utilise par le soufflet
     * mecanique, le souffleur a vapeur et le soufflet a main.
     */
    @Nullable
    public static AirBlastReceiver airReceiver(net.minecraft.world.level.Level level, BlockPos target) {
        if (!level.isLoaded(target)) {
            return null;
        }
        BlockState state = level.getBlockState(target);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(target) : null;
        if (be instanceof AirBlastReceiver receiver) {
            return receiver;
        }
        if (isLitCampfire(state) || be instanceof HeatSource) {
            BlockPos above = target.above();
            if (level.isLoaded(above) && level.getBlockEntity(above) instanceof AirBlastReceiver receiver) {
                return receiver;
            }
        }
        return null;
    }
}
