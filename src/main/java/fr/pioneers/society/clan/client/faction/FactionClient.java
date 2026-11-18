package fr.pioneers.society.clan.client.faction;

import fr.pioneers.Pioneers;
import fr.pioneers.society.rp.client.RpClient;
import fr.pioneers.society.clan.faction.FactionItems;
import fr.pioneers.society.clan.faction.FederalPact;
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
 * 1.9 (MAJ RP, bloc 4) : cote client des factions : ecran du Pacte federal ({@code FACTION_VIEW} sorte « pact » : etat, erreur ou
 * fermeture), predicat de modele {@code pioneers:sealed} (Pacte ouvert : sceau de cire) et teinte du calque de cire a la couleur choisie.
 * Les fiches de la faction ({@code FACTION_VIEW} sorte « sheet ») sont dessinees par l'onglet Clan du Carnet.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FactionClient {

    /**
     * Fiches de la faction ({@code FACTION_VIEW} sorte « sheet ») : l'onglet Clan du Carnet branche ici son affichage (un seul traitement par
     * action reseau : ne pas enregistrer {@code FACTION_VIEW} ailleurs).
     */
    public static java.util.function.Consumer<CompoundTag> sheetHandler = t -> {
    };

    private FactionClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(FactionItems.PACT.get(), Pioneers.rl("sealed"), (stack, level, entity, seed) -> FederalPact.isOpen(stack) ? 1F : 0F);
            RpClient.on(RpActions.FACTION_VIEW, FactionClient::view);
        });
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> {
            if (layer != 1) {
                return -1;
            }
            CompoundTag d = FederalPact.draft(stack);
            return d == null ? DyeColor.RED.getFireworkColor() : DyeColor.byId(d.getInt("color") & 15).getFireworkColor();
        }, FactionItems.PACT.get());
    }

    private static void view(CompoundTag t) {
        if (!"pact".equals(t.getString("kind"))) {
            sheetHandler.accept(t);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (t.getBoolean("close")) {
            if (mc.screen instanceof PactScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (mc.screen instanceof PactScreen s) {
            s.read(t);
            if (t.contains("error")) {
                RpClient.play(RpSounds.BOOK_CLOSE.get(), 1F);
            }
        } else if (!t.contains("error")) {
            mc.setScreen(new PactScreen(t));
        }
    }
}
