package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import fr.pioneers.core.Wrenches;
import fr.pioneers.core.machine.MachineTile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP) : copie des reglages de faces a la cle, des le niveau 1 de Logistique ({@code logistics.copy_config}). Accroupi + clic droit
 * avec une cle sur une machine : la premiere fois, ses faces sont copiees dans la cle ; sur une autre machine du meme bloc, elles y sont
 * collees. Accroupi + clic droit dans le vide efface la copie. Les blocs que la cle regle deja (ports, faces de donnees) gardent leur
 * comportement : ce traitement passe apres eux.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class SideCopy {

    private static final String TAG = "pioneers_sides";

    private SideCopy() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack wrench = event.getItemStack();
        if (event.isCanceled() || !event.getEntity().isShiftKeyDown() || !Wrenches.isWrench(wrench)
            || !(event.getLevel().getBlockEntity(event.getPos()) instanceof MachineTile tile)
            || PioneerApi.modifier(event.getEntity(), "logistics.copy_config") <= 0) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String block = BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock()).toString();
        CompoundTag stored = wrench.getTag() == null ? null : wrench.getTag().getCompound(TAG);
        boolean paste = stored != null && block.equals(stored.getString("block")) && stored.getLong("from") != event.getPos().asLong();
        if (paste) {
            tile.getSideConfig().load(stored.getCompound("sides"));
            tile.setChanged();
            tile.getLevel().sendBlockUpdated(event.getPos(), tile.getBlockState(), tile.getBlockState(), 3);
            player.displayClientMessage(Component.translatable("pioneer.pioneers.sides.pasted", tile.getBlockState().getBlock().getName())
                  .withStyle(ChatFormatting.GREEN), true);
            player.level().playSound(null, event.getPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
        } else {
            CompoundTag copy = new CompoundTag();
            copy.putString("block", block);
            copy.putLong("from", event.getPos().asLong());
            copy.put("sides", tile.getSideConfig().save());
            wrench.getOrCreateTag().put(TAG, copy);
            player.displayClientMessage(Component.translatable("pioneer.pioneers.sides.copied", tile.getBlockState().getBlock().getName())
                  .withStyle(ChatFormatting.AQUA), true);
            player.level().playSound(null, event.getPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRightClickAir(PlayerInteractEvent.RightClickItem event) {
        ItemStack wrench = event.getItemStack();
        if (event.getEntity().isShiftKeyDown() && Wrenches.isWrench(wrench) && wrench.getTag() != null && wrench.getTag().contains(TAG)) {
            wrench.getTag().remove(TAG);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("pioneer.pioneers.sides.cleared").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }
}
