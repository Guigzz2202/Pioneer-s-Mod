package fr.pioneers.common;

import fr.pioneers.Pioneers;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.4.2 : le guide n'est plus un objet mais une touche ({@code key.pioneers.guide}, H par defaut) : a sa premiere connexion avec le mod, chaque
 * joueur recoit un message qui lui indique la touche (celle qu'il a reglee : le nom est resolu chez lui).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class GuideGift {

    private static final String TOLD = Pioneers.MODID + ":guide_key_told";

    private GuideGift() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean(TOLD)) {
            persisted.putBoolean(TOLD, true);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            player.sendSystemMessage(Component.translatable("guide.pioneers.welcome",
                  Component.keybind("key.pioneers.guide").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)).withStyle(ChatFormatting.AQUA));
        }
    }
}
