package fr.pioneers.farm.fish;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.PiFishing;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Oeufs de poisson (NBT {@code Species}) : se replantent sur un herbier (clic droit sur l'herbe marine) ; ceux d'esturgeon font le caviar. */
public class FishRoeItem extends Item {

    public FishRoeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(int species, int count) {
        species = Mth.clamp(species, 0, GeneratedFish.SPECIES.length - 1);
        boolean sturgeon = "roe".equals(GeneratedFish.SPECIES[species].prop());
        ItemStack s = new ItemStack(sturgeon ? PiFishing.STURGEON_ROE.get() : PiFishing.FISH_ROE.get(), count);
        s.getOrCreateTag().putInt("Species", species);
        return s;
    }

    public static int species(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("Species")) {
            return Mth.clamp(stack.getTag().getInt("Species"), 0, GeneratedFish.SPECIES.length - 1);
        }
        return stack.is(PiFishing.STURGEON_ROE.get()) ? FishItems.index("sturgeon") : 0;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState s = level.getBlockState(pos);
        if (!s.is(Blocks.SEAGRASS)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, PiFishing.ROE_BLOCK.get().defaultBlockState().setValue(FishRoeBlock.SPECIES, species(ctx.getItemInHand())), 3);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 0.8F, 1.2F);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (stack.is(PiFishing.STURGEON_ROE.get())) {
            return super.getName(stack);
        }
        return Component.translatable("item.pioneers.fish_roe.of", Component.translatable("farm.pioneers.fish." + GeneratedFish.SPECIES[species(stack)].id()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("farm.pioneers.fish.roe_hint");
    }
}
