package fr.pioneers.space.giant;

import fr.pioneers.item.ItemWithDescription;
import fr.pioneers.registries.PiSounds;
import fr.pioneers.space.PiDimensions;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * Scanner de gaz : revele les poches alentour (contour lumineux pendant 10 secondes) et en fait le bilan.
 * Clic droit sur une poche : la designe aux drones (prioritaire ; seule cible en mode « designees »).
 */
public class ItemGasScanner extends ItemWithDescription {

    public static final int RANGE = 96;

    public ItemGasScanner() {
        super(new Item.Properties().stacksTo(1));
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!PiDimensions.isGasGiant(level)) {
            player.displayClientMessage(Component.translatable("item.pioneers.gas_scanner.not_here").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, 40);
        List<GasPocketEntity> pockets = level.getEntitiesOfClass(GasPocketEntity.class, new AABB(player.blockPosition()).inflate(RANGE));
        Map<PocketKind, Integer> counts = new EnumMap<>(PocketKind.class);
        GasPocketEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (GasPocketEntity pocket : pockets) {
            pocket.reveal(200);
            counts.merge(pocket.getKind(), 1, Integer::sum);
            double d = pocket.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = pocket;
            }
        }
        level.playSound(null, player.blockPosition(), PiSounds.SCANNER_PING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (pockets.isEmpty()) {
            player.displayClientMessage(Component.translatable("item.pioneers.gas_scanner.none").withStyle(ChatFormatting.GRAY), true);
        } else {
            MutableComponent line = Component.translatable("item.pioneers.gas_scanner.found", pockets.size()).withStyle(ChatFormatting.AQUA);
            for (Map.Entry<PocketKind, Integer> entry : counts.entrySet()) {
                line.append(Component.literal(" · " + entry.getValue() + " ").withStyle(ChatFormatting.GRAY))
                      .append(Component.translatable(entry.getKey().getTranslationKey()).withStyle(style -> style.withColor(entry.getKey().color)));
            }
            player.sendSystemMessage(line);
            if (nearest != null) {
                GasPocketEntity n = nearest;
                player.sendSystemMessage(Component.translatable("item.pioneers.gas_scanner.nearest", Component.translatable(n.getKind().getTranslationKey()),
                      (int) Math.sqrt(best), (int) n.getY(), Component.translatable(PocketKind.Layer.at(n.getY()).getTranslationKey()))
                      .withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResultHolder.consume(stack);
    }
}
