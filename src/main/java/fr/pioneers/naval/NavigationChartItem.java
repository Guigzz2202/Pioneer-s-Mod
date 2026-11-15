package fr.pioneers.naval;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.27 : carte de navigation : relie les balises maritimes. Clic droit sur une balise : elle est retenue ; clic droit sur une autre : les
 * deux sont reliees (ou deliees si elles l'etaient) et la seconde est retenue a son tour (on enchaine A -> B -> C). La route de la liaison
 * se calcule en tache de fond ; le joueur est prevenu du resultat. Maj + clic droit dans le vide : oublie la balise retenue.
 */
public class NavigationChartItem extends Item {

    public NavigationChartItem(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof SeaBeaconBlock)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.SUCCESS;
        }
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();
        String dim = sl.dimension().location().toString();
        NavalWorld w = NavalWorld.get(sl);
        if (w.beacon(pos.asLong()) == null) {
            w.addBeacon(pos);
        }
        if (!tag.contains("beacon") || !dim.equals(tag.getString("dim"))) {
            select(tag, pos, dim);
            say(player, Component.translatable("naval.pioneers.chart.selected", pos.toShortString()));
            return InteractionResult.CONSUME;
        }
        long from = tag.getLong("beacon");
        if (from == pos.asLong()) {
            tag.remove("beacon");
            say(player, Component.translatable("naval.pioneers.chart.cleared"));
            return InteractionResult.CONSUME;
        }
        NavalWorld.LinkResult r = w.toggleLink(from, pos.asLong(), player == null ? null : player.getUUID());
        switch (r) {
            case LINKED -> say(player, Component.translatable("naval.pioneers.chart.linking", BlockPos.of(from).toShortString(), pos.toShortString()));
            case UNLINKED -> say(player, Component.translatable("naval.pioneers.chart.unlinked", BlockPos.of(from).toShortString(), pos.toShortString()));
            case TOO_FAR -> say(player, Component.translatable("naval.pioneers.chart.too_far", (int) SeaBeacon.MAX_LINK));
            default -> say(player, Component.translatable("naval.pioneers.chart.missing"));
        }
        select(tag, pos, dim);
        return InteractionResult.CONSUME;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag() && stack.getTag().contains("beacon")) {
            if (!level.isClientSide) {
                stack.getTag().remove("beacon");
                say(player, Component.translatable("naval.pioneers.chart.cleared"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static void select(CompoundTag tag, BlockPos pos, String dim) {
        tag.putLong("beacon", pos.asLong());
        tag.putString("dim", dim);
    }

    private static void say(@Nullable Player player, Component c) {
        if (player != null) {
            player.displayClientMessage(c, true);
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("beacon");
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        if (stack.hasTag() && stack.getTag().contains("beacon")) {
            tips.state("naval.pioneers.chart.tooltip.selected", BlockPos.of(stack.getTag().getLong("beacon")).toShortString());
        }
        tips.desc("naval.pioneers.chart.tooltip");
    }
}
