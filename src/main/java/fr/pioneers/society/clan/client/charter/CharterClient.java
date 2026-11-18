package fr.pioneers.society.clan.client.charter;

import fr.pioneers.Pioneers;
import fr.pioneers.society.rp.client.RpClient;
import fr.pioneers.society.clan.core.Charter;
import fr.pioneers.society.clan.core.ClanItems;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.9 (MAJ RP, bloc 4) : cote client de la Charte : ecran ({@code CHARTER_VIEW} : etat, erreur ou fermeture), predicat de modele
 * {@code pioneers:sealed} (Charte ouverte : sceau de cire) et teinte du calque de cire a la couleur choisie.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CharterClient {

    private CharterClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ClanItems.CHARTER.get(), Pioneers.rl("sealed"), (stack, level, entity, seed) -> Charter.isOpen(stack) ? 1F : 0F);
            RpClient.on(RpActions.CHARTER_VIEW, CharterClient::view);
        });
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> {
            if (layer != 1) {
                return -1;
            }
            CompoundTag d = Charter.draft(stack);
            return d == null ? DyeColor.RED.getFireworkColor() : DyeColor.byId(d.getInt("color") & 15).getFireworkColor();
        }, ClanItems.CHARTER.get());
    }

    private static void view(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        if (t.getBoolean("close")) {
            if (mc.screen instanceof CharterScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (mc.screen instanceof CharterScreen s) {
            s.read(t);
            if (t.contains("error")) {
                RpClient.play(RpSounds.BOOK_CLOSE.get(), 1F);
            }
        } else if (!t.contains("error")) {
            mc.setScreen(new CharterScreen(t));
        }
    }
}
