package fr.pioneers.farm.crop;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Graine genetique : pose sa culture et lui donne ses genes. Nom suivi de sa note (etoiles), infobulle : les quatre genes en jauges, le climat
 * prefere et les particularites (riziere, repousse, deux blocs). A la pose, previent si le climat est difficile ou hostile.
 */
public class GeneticSeedItem extends ItemNameBlockItem {

    private final CropDef def;

    public GeneticSeedItem(FarmCropBlock block, Properties properties) {
        super(block, properties);
        this.def = block.def();
    }

    public CropDef def() {
        return def;
    }

    @Override
    public Component getName(ItemStack stack) {
        MutableComponent name = Component.translatable(getDescriptionId(stack));
        if (Genes.has(stack)) {
            name.append(" ").append(stars(Genes.of(stack).stars()));
        }
        return name;
    }

    public static MutableComponent stars(int n) {
        return Component.literal("★".repeat(n)).withStyle(ChatFormatting.GOLD);
    }

    /** Jauge d'un gene : 10 carres. */
    public static MutableComponent bar(int value) {
        ChatFormatting colour = value >= 8 ? ChatFormatting.GREEN : value >= 5 ? ChatFormatting.YELLOW : value >= 3 ? ChatFormatting.GOLD
              : ChatFormatting.RED;
        return Component.literal("■".repeat(value)).withStyle(colour)
              .append(Component.literal("■".repeat(10 - value)).withStyle(ChatFormatting.DARK_GRAY))
              .append(Component.literal(" " + value).withStyle(ChatFormatting.GRAY));
    }

    public static void genesTooltip(Genes genes, List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        for (int i = 0; i < 4; i++) {
            tips.custom(Component.translatable("farm.pioneers.gene." + i).withStyle(ChatFormatting.GRAY).append(" ").append(bar(genes.get(i))));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        genesTooltip(Genes.of(stack), tooltip);
        Component from = Component.translatable("farm.pioneers.zone." + Climate.zone(def.tMin() + 0.01F));
        Component to = Component.translatable("farm.pioneers.zone." + Climate.zone(def.tMax() - 0.01F));
        tips.spec("farm.pioneers.seed.climate", from, to);
        if (def.hMin() >= 0.5F) {
            tips.spec("farm.pioneers.seed.wet");
        } else if (def.hMax() <= 0.8F) {
            tips.spec("farm.pioneers.seed.dry");
        }
        if (def.paddy()) {
            tips.note("farm.pioneers.seed.paddy");
        }
        if ("mushroom".equals(def.arch())) {
            tips.note("farm.pioneers.seed.mushroom");                     //1.0.3 (lot 13) : a l'ombre, sur substrat, mycelium ou podzol
        }
        if (def.regrows()) {
            tips.good("farm.pioneers.seed.regrow");
        }
        if (def.tall()) {
            tips.good("farm.pioneers.seed.tall");
        }
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        if (level.getBlockEntity(pos) instanceof CropTile tile) {
            tile.setGenes(Genes.of(stack));
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Genes genes = Genes.of(context.getItemInHand());
        InteractionResult result = super.place(context);
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (result.consumesAction() && !level.isClientSide && player != null) {
            BlockPos pos = context.getClickedPos();
            Climate.Status status = Climate.evaluate(level, pos, def, genes, FarmCropBlock.irrigated(level, pos));
            Component crop = Component.translatable("farm.pioneers.crop." + def.id());
            if (status == Climate.Status.HOSTILE) {
                player.displayClientMessage(Component.translatable("farm.pioneers.place.hostile", crop).withStyle(ChatFormatting.RED), true);
            } else if (status == Climate.Status.TOLERABLE) {
                player.displayClientMessage(Component.translatable("farm.pioneers.place.tolerable", crop).withStyle(ChatFormatting.GOLD), true);
            } else if (def.paddy() && !FarmCropBlock.flooded(level, pos)) {
                player.displayClientMessage(Component.translatable("farm.pioneers.place.paddy", crop).withStyle(ChatFormatting.GOLD), true);
            }
        }
        return result;
    }
}
