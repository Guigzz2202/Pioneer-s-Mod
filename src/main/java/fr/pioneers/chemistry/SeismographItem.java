package fr.pioneers.chemistry;

import fr.pioneers.core.Texts;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.geology.fluid.FluidDeposits;
import fr.pioneers.industry.IndustryEvents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sismographe : ecoute le sous-sol et trouve les gisements fluides (petrole, gaz, saumure). Sur un gisement : son type, ce qu'il reste, sa
 * pression ; sinon la direction et la distance du plus proche dans un rayon de {@link #RANGE} blocs.
 */
public class SeismographItem extends Item {

    public static final int RANGE = 160;
    private static final String[] DIRS = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};

    public SeismographItem(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        ServerLevel server = (ServerLevel) level;
        BlockPos pos = player.blockPosition();
        server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
        player.displayClientMessage(report(server, pos), false);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        player.getCooldowns().addCooldown(this, 40);
        IndustryEvents.award(server, pos, "chem_seismic");
        return InteractionResultHolder.consume(stack);
    }

    static String mb(long amount) {
        return Texts.grouped(amount / 1000);
    }

    public static Component report(ServerLevel level, BlockPos pos) {
        FluidDeposits.Deposit here = FluidDeposits.at(level, pos);
        if (here != null) {
            return Component.translatable("chemistry.pioneers.seismic.here", Component.translatable(here.type().translationKey()).withStyle(ChatFormatting.GOLD),
                  mb(here.left(level)), mb(here.reserve()), Math.round(here.pressure(level) * 100)).withStyle(ChatFormatting.AQUA);
        }
        FluidDeposits.Nearest near = FluidDeposits.nearest(level, pos, RANGE);
        if (near == null) {
            return Component.translatable("chemistry.pioneers.seismic.nothing", RANGE).withStyle(ChatFormatting.GRAY);
        }
        double angle = Math.toDegrees(Math.atan2(-(near.x() - pos.getX()), near.z() - pos.getZ()));
        int dir = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        int dist = (int) Math.max(1, Math.round(near.distance() / 8.0) * 8);
        return Component.translatable("chemistry.pioneers.seismic.near", Component.translatable(near.deposit().type().translationKey()), dist,
              Component.translatable("geology.pioneers.dir." + DIRS[dir])).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.hint("chemistry.pioneers.seismic.tip");
    }
}
