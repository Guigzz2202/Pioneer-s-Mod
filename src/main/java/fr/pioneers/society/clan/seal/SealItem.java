package fr.pioneers.society.clan.seal;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : Sceau de fonction. Clic droit sur un bloc du territoire du clan (permission SEALS) : le serveur verifie, puis le
 * joueur choisit une des sept fonctions dans une fiche de papier ; le Sceau est consomme a la designation, rendu quand elle tombe. Clic droit
 * sur un bloc deja designe : la meme fiche propose de lever le Sceau.
 */
public class SealItem extends Item {

    public SealItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer p) {
            ClanSeals.offer(p, ctx.getClickedPos());
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.function_seal.desc");
        tips.hint("item.pioneers.function_seal.use");
    }
}
