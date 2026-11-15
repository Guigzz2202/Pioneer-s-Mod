package fr.pioneers.geology;

import fr.pioneers.bronze.BronzeEvents;
import fr.pioneers.core.Texts;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marteau de prospecteur : pic leger (mine la pierre) qui sonde le sous-sol au clic droit sur le sol. Sur un gisement : son minerai, sa
 * reserve et le niveau de foreuse ; sinon la direction et la distance du plus proche (64 blocs), sans dire ce que c'est.
 */
public class ProspectorHammerItem extends PickaxeItem {

    public static final int RANGE = 64;
    private static final String[] DIRS = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};

    /** Portee de l'echo (blocs) : les marteaux des tiers superieurs sondent plus loin. */
    private final int range;

    public ProspectorHammerItem(Tier tier, Properties properties) {
        this(tier, RANGE, properties);
    }

    public ProspectorHammerItem(Tier tier, int range, Properties properties) {
        super(tier, 0, -2.6F, properties);
        this.range = range;
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel server = (ServerLevel) level;
        BlockPos pos = context.getClickedPos();
        server.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1F, 0.7F + server.random.nextFloat() * 0.3F);
        player.displayClientMessage(report(server, pos, range), false);
        context.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
        player.getCooldowns().addCooldown(this, 20);
        BronzeEvents.award(server, pos, "prospect");
        return InteractionResult.CONSUME;
    }

    public static Component report(ServerLevel level, BlockPos pos) {
        return report(level, pos, RANGE);
    }

    /** Rapport du coup de marteau : gisement sous les pieds, sinon le plus proche dans la portee. */
    public static Component report(ServerLevel level, BlockPos pos, int range) {
        Deposits.Deposit here = Deposits.at(level, pos.getX() >> 4, pos.getZ() >> 4);
        if (here != null) {
            int left = here.left(level);
            return Component.translatable("geology.pioneers.here", Component.translatable(here.type().translationKey()).withStyle(ChatFormatting.GOLD),
                  Texts.grouped(left), Texts.grouped(here.reserve()),
                  here.type().tier()).withStyle(ChatFormatting.AQUA);
        }
        Deposits.Nearest near = Deposits.nearest(level, pos, range);
        if (near == null) {
            return Component.translatable("geology.pioneers.nothing", range).withStyle(ChatFormatting.GRAY);
        }
        double angle = Math.toDegrees(Math.atan2(-(near.x() - pos.getX()), near.z() - pos.getZ()));
        int dir = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        int dist = (int) Math.max(1, Math.round(near.distance() / 4.0) * 4);
        return Component.translatable("geology.pioneers.near", dist, Component.translatable("geology.pioneers.dir." + DIRS[dir])).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.hint("geology.pioneers.hammer_tip");
    }
}
