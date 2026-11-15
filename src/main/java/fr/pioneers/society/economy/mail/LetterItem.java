package fr.pioneers.society.economy.mail;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 29) : une LETTRE arrivee dans la boite aux lettres ({@link Letters}). Nom = son objet (« Lettre : Virement recu ») ;
 * infobulle : expediteur, contrat, montant, date ; clic droit : la lecture (ecran {@code client/economy/LetterScreen}). Elle ne vaut que son
 * papier et ne change rien a ce qu'elle annonce.
 */
public class LetterItem extends Item {

    public LetterItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        CompoundTag t = Letters.data(stack);
        return t == null || !Letters.known(t.getString("k")) ? super.getName(stack)
              : Component.translatable("item.pioneers.letter.named", Component.translatable("mail.pioneers.kind." + t.getString("k")));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag t = Letters.data(stack);
        if (t == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.society.economy.client.LetterScreen.open(t));
        } else {
            Letters.markRead(stack); //1.0.5-beta (lot 16) : lu / non lu (bac a lettres)
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        CompoundTag t = Letters.data(stack);
        if (t == null) {
            tips.desc("item.pioneers.letter.desc");
            return;
        }
        if (!t.getString("f").isEmpty()) {
            tips.stat("mail.pioneers.label.from", t.getString("f"));
        }
        if (t.getInt("n") > 0) {
            tips.stat("mail.pioneers.label.contract", Component.translatable("mail.pioneers.contract_ref", t.getInt("n"), t.getString("t")));
        }
        if (t.getLong("a") > 0) {
            tips.stat("mail.pioneers.label.amount", "contract_proposed".equals(t.getString("k")) ? Letters.terms(t.getString("m"), t.getLong("a"))
                  : Letters.tokens(t.getLong("a")));
        }
        tips.stat("mail.pioneers.label.date", t.getString("ds"));
        tips.hint("item.pioneers.letter.hint");
    }
}
