package fr.pioneers.logistics.transport.legacy;

import fr.pioneers.core.Directions;
import fr.pioneers.core.block.Waterlogging;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2c) : fibre optique heritee ({@code <tier>_data_cable[_clear]}) : memes valeurs d'etat que l'ancien {@code DataCableBlock}
 * (none, cable, plug, disabled), tile {@code pioneers:data_cable}. Convertie en cable logistique du meme niveau (canal de donnees).
 */
public class LegacyFiberBlock extends LegacyCableBlock {

    public enum Connection implements StringRepresentable {
        NONE, CABLE, PLUG, DISABLED;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Map<Direction, EnumProperty<Connection>> FIBER = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Directions.ALL) {
            FIBER.put(direction, EnumProperty.create(direction.getName(), Connection.class));
        }
    }

    public LegacyFiberBlock(CableLevel level, boolean clear, Properties properties) {
        super(LegacyFamily.DATA, level, clear, properties);
    }

    @Override
    protected BlockState defaults(BlockState state) {
        for (EnumProperty<Connection> property : FIBER.values()) {
            state = state.setValue(property, Connection.NONE);
        }
        return state.setValue(Waterlogging.WATERLOGGED, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Waterlogging.WATERLOGGED);
        FIBER.values().forEach(builder::add);
    }

    @Override
    public int plugMask(BlockState state) {
        int mask = 0;
        for (Direction d : Directions.ALL) {
            if (state.getValue(FIBER.get(d)) == Connection.PLUG) {
                mask |= 1 << d.ordinal();
            }
        }
        return mask;
    }

    @Override
    public int disabledMask(BlockState state) {
        int mask = 0;
        for (Direction d : Directions.ALL) {
            if (state.getValue(FIBER.get(d)) == Connection.DISABLED) {
                mask |= 1 << d.ordinal();
            }
        }
        return mask;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new LegacyCableTile(PiTransport.LEGACY_FIBER_TILE.get(), pos, state);
    }
}
