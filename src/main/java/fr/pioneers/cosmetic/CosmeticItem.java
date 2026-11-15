package fr.pioneers.cosmetic;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import javax.annotation.Nullable;
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
 * 1.12.0 (lot E4) : une piece de tenue cosmetique ({@code pioneers:cosmetic_<tenue>_<piece>}). Pas une armure : aucune protection, ne va
 * jamais dans les cases d'armure. Se porte dans la garde-robe du joueur (panneau cosmetique de l'inventaire, Maj + clic, ou clic droit
 * l'objet en main) ; son modele 3D est dessine par {@code client/cosmetic/CosmeticLayer} a la place de l'armure de son emplacement.
 */
public class CosmeticItem extends Item {

    public final String outfit;
    public final CosmeticPiece piece;

    public CosmeticItem(String outfit, CosmeticPiece piece) {
        super(new Item.Properties().stacksTo(1));
        this.outfit = outfit;
        this.piece = piece;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            //Clic droit : enfile la piece ; celle qu'on portait revient dans la main
            player.setItemInHand(hand, CosmeticActions.equip(sp, stack.copy()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips.of(tooltip)
              .desc(Component.translatable("tooltip.pioneers.cosmetic.piece", Component.translatable("cosmetic.pioneers.outfit." + outfit),
                    Component.translatable("cosmetic.pioneers.piece." + piece.key)))
              .note("tooltip.pioneers.cosmetic.no_armor")
              .hint("tooltip.pioneers.cosmetic.wear");
    }
}
