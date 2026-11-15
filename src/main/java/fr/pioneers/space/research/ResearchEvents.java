package fr.pioneers.space.research;

import fr.pioneers.Pioneers;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Charge l'etat de la recherche au demarrage du serveur et l'envoie aux joueurs qui se connectent.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ResearchEvents {

    private ResearchEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        //Met a jour les effets des le demarrage (et non a la premiere utilisation)
        ResearchEffects.update(ResearchState.get(event.getServer()).getCompleted());
    }

    /** 0.20.46 (audit) : la synchro retenue (progres des laboratoires, echantillons) part au plus tard 10 ticks apres. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 5 == 0) {
            ResearchState.get(event.getServer()).flushPending(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            //0.18.0 : jalons deja obtenus par ce joueur (mondes d'avant, progres obtenus hors ligne)
            ResearchState state = ResearchState.get(player.server);
            for (ResourceLocation id : ResearchMilestones.all()) {
                Advancement advancement = player.server.getAdvancements().getAdvancement(id);
                if (advancement != null && !state.hasMilestone(id) && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                    state.addMilestone(player.server, id);
                }
            }
            state.sendTo(player);
        }
    }

    /** 0.18.0 : un progres qui sert de jalon a la recherche spatiale. */
    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResourceLocation id = event.getAdvancement().getId();
            if (ResearchMilestones.all().contains(id) && ResearchState.get(player.server).addMilestone(player.server, id)) {
                player.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                      "research.pioneers.milestone.reached", event.getAdvancement().getChatComponent()), false);
            }
        }
    }
}
