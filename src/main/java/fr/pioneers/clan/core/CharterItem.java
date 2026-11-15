package fr.pioneers.clan.core;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
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
 * 1.9 (MAJ RP, bloc 4) : la Charte de clan. Vierge : clic droit = le fondateur la redige (nom, etiquette, couleur, devise, regime). Ouverte
 * (cire du fondateur, a la couleur du clan) : deux autres joueurs la signent dans les 10 minutes, a moins de 8 blocs du fondateur (clic droit
 * sur le fondateur qui la tient, ou clic droit avec la Charte qu'il leur a passee, ou {@code /clan sign}). Scellee, elle est consommee et le
 * clan nait. Brouillon dans le NBT {@code charter}.
 */
public class CharterItem extends Item {

    public CharterItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Charter.use(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return Charter.isOpen(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag d = Charter.draft(stack);
        if (d == null) {
            tips.desc("description.pioneers.clan_charter");
            return;
        }
        tips.custom(Component.translatable("clan.pioneers.charter.tooltip_name", d.getString("name"), d.getString("tag"))
              .withStyle(ClanMessages.dye(d.getInt("color"))));
        tips.spec("clan.pioneers.regime." + d.getString("regime"));
        tips.spec("clan.pioneers.charter.tooltip_founder", d.getString("founderName"));
        tips.spec("clan.pioneers.charter.tooltip_signatures", Charter.signatures(d).size(), d.getInt("need"));
        tips.hint("clan.pioneers.charter.text");
    }
}
