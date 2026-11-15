package fr.pioneers.tech.kinetics;

import fr.pioneers.tech.kinetics.client.KineticItemRenderer;
import fr.pioneers.core.block.Covers;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Objet d'une piece de l'age mecanique : son icone est la piece elle-meme, dessinee par le meme rendu que dans le monde
 * ({@code KineticItemRenderer}) ; l'infobulle donne sa description et ses limites.
 */
public class KineticBlockItem extends BlockItem {

    public KineticBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        String key = getDescriptionId() + ".desc";
        tips.desc(key);
        KineticSpecs.describe(getBlock(), tooltip);
        if (getBlock().defaultBlockState().hasProperty(Covers.COVERED)) {
            tips.hint("cover.pioneers.hint");                       //1.0.4-beta (lot 27) : arbres caches
        }
    }

    /** 0.20.42 : aides a la pose (ligne d'arbres prolongee, engrenage en diagonale) ; Maj : pose brute. */
    @Nullable
    @Override
    public BlockPlaceContext updatePlacementContext(@NotNull BlockPlaceContext context) {
        BlockPlaceContext helped = KineticPlacement.help(getBlock(), context);
        return helped != null ? helped : super.updatePlacementContext(context);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return KineticItemRenderer.instance();
            }
        });
    }
}
