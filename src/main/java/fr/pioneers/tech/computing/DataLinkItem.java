package fr.pioneers.tech.computing;

import fr.pioneers.tech.compute.FactoryAiCoreTile;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configurateur de liaison (0.17.0) : Maj + clic droit sur une machine = la memorise ; clic droit sur une armoire a relais, un automate ou
 * un ecran de supervision = la lui lie (meme dimension, 64 blocs au plus). La machine retenue reste en memoire pour en lier plusieurs.
 */
public class DataLinkItem extends Item {

    private static final String POS = "linkPos", DIM = "linkDim";

    public DataLinkItem(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile == null || player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean target = tile instanceof LogicTile || tile instanceof ScreenTile || tile instanceof ComputerTile
                         || tile instanceof FactoryAiCoreTile;
        if (player.isShiftKeyDown() || !target) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong(POS, pos.asLong());
            tag.putString(DIM, level.dimension().location().toString());
            player.displayClientMessage(Component.translatable("computing.pioneers.link.stored", Probes.name(tile)).withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
            return InteractionResult.CONSUME;
        }
        BlockPos stored = stored(stack, level);
        if (stored == null) {
            player.displayClientMessage(Component.translatable("computing.pioneers.link.none").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        boolean ok;
        //1.9 (MAJ RP) : la Robotique allonge la portee de la liaison
        LogicTile.rangeFactor = fr.pioneers.core.profile.PioneerApi.factor(player, "robotics.remote_range");
        try {
            if (tile instanceof ScreenTile screen) {
                BlockState state = screen.getBlockState();
                BlockPos master = ScreenBlock.master(level, pos, state.getValue(ScreenBlock.FACING));
                ok = level.getBlockEntity(master) instanceof ScreenTile m && m.setLink(stored);
            } else if (tile instanceof ComputerTile computer) {
                ok = computer.addLink(stored);
            } else if (tile instanceof FactoryAiCoreTile core) {
                //0.17.2 : noyau d'usine (16 / 64 machines)
                ok = core.addLink(stored);
            } else {
                ok = ((LogicTile) tile).addLink(stored);
            }
        } finally {
            LogicTile.rangeFactor = 1.0;
        }
        player.displayClientMessage(Component.translatable(ok ? "computing.pioneers.link.done" : "computing.pioneers.link.failed",
              Probes.name(Probes.at(level, stored))).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        level.playSound(null, pos, ok ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.5F, ok ? 1.4F : 1.0F);
        return InteractionResult.CONSUME;
    }

    /** Machine memorisee (dans cette dimension), ou null. */
    @Nullable
    public static BlockPos stored(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(POS) || !level.dimension().location().toString().equals(tag.getString(DIM))) {
            return null;
        }
        return BlockPos.of(tag.getLong(POS));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(POS)) {
            BlockPos p = BlockPos.of(tag.getLong(POS));
            tips.state("computing.pioneers.link.tooltip", p.getX(), p.getY(), p.getZ());
        }
        tips.desc("description.pioneers.data_link");
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(POS);
    }
}
