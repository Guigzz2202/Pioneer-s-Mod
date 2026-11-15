package fr.pioneers.farm;

import fr.pioneers.Pioneers;
import fr.pioneers.core.vanilla.VanillaReplacements;
import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.tree.TreeDef;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Evenements de l'Agriculture 2.0 : cultures vanilla remplacees a leur premiere pousse (champs des villages), carottes et pommes de terre
 * vanilla qui ne se plantent plus (ce sont des recoltes : il faut leurs graines), pousses d'arbres fruitiers chez le marchand ambulant.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FarmEvents {

    private FarmEvents() {
    }

    /** Culture vanilla -> culture du mod (et facteur d'age : la betterave n'a que 4 ages). */
    private static final Map<Block, String> VANILLA_CROPS = Map.of(Blocks.WHEAT, "wheat", Blocks.CARROTS, "carrot", Blocks.POTATOES, "potato",
          Blocks.BEETROOTS, "beetroot", Blocks.MELON_STEM, "melon", Blocks.PUMPKIN_STEM, "pumpkin", Blocks.ATTACHED_MELON_STEM, "melon",
          Blocks.ATTACHED_PUMPKIN_STEM, "pumpkin");

    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        BlockState state = event.getState();
        String crop = VANILLA_CROPS.get(state.getBlock());
        if (crop == null || !(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        int age;
        if (state.hasProperty(BlockStateProperties.AGE_3)) {
            age = state.getValue(BlockStateProperties.AGE_3) * 2 + 1;
        } else if (state.hasProperty(BlockStateProperties.AGE_7)) {
            age = state.getValue(BlockStateProperties.AGE_7);
        } else {
            age = 6;
        }
        FarmCropBlock block = PiFarm.CROPS.get(crop).get();
        BlockPos pos = event.getPos();
        level.setBlock(pos, block.getStateForAge(Math.min(age, block.getMaxAge())), 3);
        if (level.getBlockEntity(pos) instanceof CropTile tile) {
            tile.setGenes(Genes.wild(level.random));
        }
        event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.CARROT) && !stack.is(Items.POTATO) && !stack.is(Items.WHEAT_SEEDS) && !stack.is(Items.BEETROOT_SEEDS)
            && !stack.is(Items.MELON_SEEDS) && !stack.is(Items.PUMPKIN_SEEDS)) {
            return;
        }
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FarmBlock) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide) {
                event.getEntity().displayClientMessage(Component.translatable("farm.pioneers.vanilla_seed").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        for (TreeDef def : GeneratedFarm.TREES) {
            event.getRareTrades().add((trader, random) -> new MerchantOffer(
                  VanillaReplacements.convert(new ItemStack(Items.EMERALD, 4 + random.nextInt(4))),
                  new ItemStack(PiFarm.SAPLINGS.get(def.id()).get()), 3, 10, 0.05F));
        }
    }
}
