package fr.pioneers.tech.kinetics.client;

import fr.pioneers.tech.bronze.steam.SteamEngineBlock;
import fr.pioneers.tech.kinetics.parts.CogBlock;
import fr.pioneers.tech.kinetics.parts.FlywheelBlock;
import fr.pioneers.tech.kinetics.rope.PulleyBlock;
import fr.pioneers.tech.kinetics.sources.BeamEngineBlock;
import fr.pioneers.tech.kinetics.sources.HorseMillBlock;
import fr.pioneers.tech.kinetics.sources.WaterWheelBlock;
import fr.pioneers.tech.kinetics.sources.WindmillBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.level.block.state.BlockState;

/** Reglages des icones : reduction des pieces qui debordent du bloc, dessins propres aux objets (lots suivants). */
public final class ItemVisuals {

    private ItemVisuals() {
    }

    private static final List<BiPredicate<KDraw, BlockState>> EXTRAS = new ArrayList<>();
    private static final List<ToDoubleFunction<BlockState>> SCALES = new ArrayList<>();

    public static void register(BiPredicate<KDraw, BlockState> extra) {
        EXTRAS.add(extra);
    }

    public static void scale(ToDoubleFunction<BlockState> scale) {
        SCALES.add(scale);
    }

    static float scale(BlockState state) {
        if (state.getBlock() instanceof CogBlock cog && cog.large || state.getBlock() instanceof FlywheelBlock) {
            return 0.62F;
        }
        if (state.getBlock() instanceof WindmillBlock) {
            return 0.22F;
        }
        if (state.getBlock() instanceof WaterWheelBlock wheel) {
            return wheel.large ? 0.3F : 0.45F;
        }
        if (state.getBlock() instanceof HorseMillBlock) {
            return 0.24F;
        }
        if (state.getBlock() instanceof SteamEngineBlock) {
            return 0.62F;
        }
        if (state.getBlock() instanceof BeamEngineBlock) {
            return 0.52F;
        }
        if (state.getBlock() instanceof PulleyBlock pulley && pulley.radiusPx() > 8) {
            return 0.62F;
        }
        for (var s : SCALES) {
            double v = s.applyAsDouble(state);
            if (v > 0) {
                return (float) v;
            }
        }
        return 1;
    }

    static void extra(KDraw d, BlockState state) {
        for (var e : EXTRAS) {
            if (e.test(d, state)) {
                return;
            }
        }
    }
}
