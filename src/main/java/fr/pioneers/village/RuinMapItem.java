package fr.pioneers.village;

import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.ruins.gen.RuinLocator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : carte des trois ruines les plus proches du lieu ou on la deplie la premiere fois (vendue par le Cartographe, service
 * Archives). Les trois marques sont gravees dans l'objet et recopiees dans le journal du Carnet ; une relecture redit la direction.
 */
public class RuinMapItem extends Item {

    public RuinMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.contains("ruins")) {
                ListTag l = new ListTag();
                PioneerProfile p = PioneerCap.get(sp);
                for (RuinLocator.Spot s : RuinLocator.near(sp.serverLevel(), sp.blockPosition(), 2000)) {
                    if (l.size() >= 3) {
                        break;
                    }
                    CompoundTag c = new CompoundTag();
                    c.putInt("x", s.pos().getX());
                    c.putInt("z", s.pos().getZ());
                    c.putInt("t", s.tier());
                    l.add(c);
                    p.journal(PioneerApi.day(sp.getServer()), "journal.pioneers.map_ruin", "MK" + "I".repeat(s.tier()),
                          String.valueOf(s.pos().getX()), String.valueOf(s.pos().getZ()));
                }
                tag.put("ruins", l);
                p.touch();
                ProfileSync.full(sp);
            }
            ListTag l = tag.getList("ruins", 10);
            if (l.isEmpty()) {
                sp.displayClientMessage(Component.translatable("village.pioneers.map.empty"), false);
            }
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                net.minecraft.core.BlockPos to = new net.minecraft.core.BlockPos(c.getInt("x"), sp.getBlockY(), c.getInt("z"));
                sp.displayClientMessage(Component.translatable("village.pioneers.map.line", "MK" + "I".repeat(Math.max(1, c.getInt("t"))),
                      Confidences.direction(sp.blockPosition(), to), c.getInt("x"), c.getInt("z")).withStyle(ChatFormatting.GOLD), false);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        int n = stack.getTag() == null ? -1 : stack.getTag().getList("ruins", 10).size();
        tips.desc(n < 0 ? "village.pioneers.map.blank" : "village.pioneers.map.marks", n);
    }
}
