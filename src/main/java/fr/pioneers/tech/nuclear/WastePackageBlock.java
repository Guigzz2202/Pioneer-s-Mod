package fr.pioneers.tech.nuclear;

import fr.pioneers.Pioneers;
import fr.pioneers.core.Directions;
import fr.pioneers.tech.industry.IndustryEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Colis de stockage geologique (0.17.1) : verre de dechets ou coques compactees dans un conteneur d'acier et de cuivre. Source de radiation
 * moyenne, sauf s'il est enfoui : sous Y = 0 et entoure sur ses 6 faces de roche du tag {@code pioneers:repository_host} (stockage
 * geologique : il ne rayonne plus ; progres « deep_storage »). Reevalue a la pose et a chaque changement d'un voisin.
 */
public class WastePackageBlock extends Block {

    public static final TagKey<Block> HOST = TagKey.create(Registries.BLOCK, Pioneers.rl("repository_host"));

    public WastePackageBlock(Properties properties) {
        super(properties);
    }

    /** Colis enfoui dans une roche hote, assez profond ? */
    public static boolean buried(Level level, BlockPos pos) {
        if (pos.getY() >= 0) {
            return false;
        }
        for (Direction d : Directions.ALL) {
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n) || !level.getBlockState(n).is(HOST)) {
                return false;
            }
        }
        return true;
    }

    private static void update(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        RadiationData data = RadiationData.get(server);
        boolean buried = buried(level, pos);
        boolean registered = data.hasBlock(pos);
        if (buried && registered) {
            data.removeBlock(pos);
            IndustryEvents.award(level, pos, "deep_storage");
        } else if (!buried && !registered) {
            data.addBlock(pos, RadiationData.WASTE_PACKAGE);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(state.getBlock())) {
            update(level, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        update(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!next.is(state.getBlock())) {
            RadioactiveBlock.unregister(level, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
