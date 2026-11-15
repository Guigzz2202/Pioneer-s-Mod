package fr.pioneers.society.clan.faction;

import fr.pioneers.society.clan.core.ClanMessages;
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
 * 1.9 (MAJ RP, bloc 4) : le Pacte federal. Vierge : clic droit = le Representant d'un clan le redige. Ouvert (cire a la couleur de la
 * faction) : clic droit = son etat, signer au nom de son clan, ou le sceller (fondateur). Resume du brouillon dans le NBT {@code pact}.
 */
public class FederalPactItem extends Item {

    public FederalPactItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            FederalPact.use(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return FederalPact.isOpen(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag d = FederalPact.draft(stack);
        if (d == null) {
            tips.desc("description.pioneers.federal_pact");
            return;
        }
        tips.custom(Component.translatable("clan.pioneers.pact.tooltip_name", d.getString("name"), d.getString("tag"))
              .withStyle(ClanMessages.dye(d.getInt("color"))));
        tips.spec("clan.pioneers.regime." + d.getString("regime"));
        tips.spec("clan.pioneers.pact.tooltip_founder", d.getString("founderName"));
        tips.spec("clan.pioneers.pact.tooltip_signed", d.getInt("signed"));
        tips.hint("clan.pioneers.pact.text");
    }
}
