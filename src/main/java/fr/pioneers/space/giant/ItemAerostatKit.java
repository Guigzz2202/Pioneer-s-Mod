package fr.pioneers.space.giant;

import fr.pioneers.space.item.ItemWithDescription;
import fr.pioneers.space.PiDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Kit d'aerostat : dans une geante gazeuse, deploie une plateforme flottante de 9 x 9 sous les pieds du joueur.
 */
public class ItemAerostatKit extends ItemWithDescription {

    public ItemAerostatKit() {
        super(new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON));
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        if (!PiDimensions.isGasGiant(level)) {
            player.displayClientMessage(Component.translatable("item.pioneers.aerostat_kit.not_here").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        BlockPos deck = player.blockPosition().below();
        if (!Aerostats.canBuild(server, deck)) {
            player.displayClientMessage(Component.translatable("item.pioneers.aerostat_kit.blocked").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        Aerostats.build(server, deck);
        Aerostats.register(server, deck);
        player.teleportTo(deck.getX() + 0.5, deck.getY() + 1, deck.getZ() + 0.5);
        player.fallDistance = 0;
        level.playSound(null, deck, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
        player.displayClientMessage(Component.translatable("item.pioneers.aerostat_kit.deployed").withStyle(ChatFormatting.AQUA), true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }
}
