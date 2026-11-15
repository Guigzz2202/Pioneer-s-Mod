package fr.pioneers.industry.multi;

import fr.pioneers.core.Texts;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.industry.Tiers;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Radiateur passif (4 tiers) : prend la chaleur de ses voisins (cables, ports de chaleur) et la rend a l'air. */
public class RadiatorBlock extends Block implements EntityBlock {

    public final CableTier tier;

    public RadiatorBlock(CableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** MAJ simplification : les chiffres du tier ({@link RadiatorTile}, source unique), aussi dans JEI (categorie Chaleur). */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter world, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        int t = tier.ordinal();
        tips.tier(t);
        tips.spec("industry.pioneers.radiator.spec_loss", Texts.grouped(Math.round(RadiatorTile.DISSIPATION[t])),
              Texts.grouped(Math.round(RadiatorTile.CONDUCTANCE[t])));
        int nominal = Tiers.nominal(t);
        long rated = Math.round(RadiatorTile.rated(t, t, nominal));
        tips.spec("industry.pioneers.radiator.spec_rated", Texts.grouped(nominal), Texts.grouped(rated),
              Texts.grouped(Math.round(rated / ControllerTile.WATER_J)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new RadiatorTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide || type != PiIndustry.RADIATOR_TILE.get() ? null : (l, p, s, tile) -> ((RadiatorTile) tile).tick();
    }
}
