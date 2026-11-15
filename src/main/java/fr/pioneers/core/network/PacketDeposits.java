package fr.pioneers.core.network;

import fr.pioneers.Pioneers;
import fr.pioneers.world.geology.DepositTypes;
import fr.pioneers.world.geology.fluid.FluidDepositTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.0.3-beta (lot 2) : types de gisements (solides et fluides) envoyes du serveur au client. Ce sont des donnees du serveur
 * ({@code data/<ns>/deposits}, {@code fluid_deposits}) : sans ce paquet, un client d'un serveur dedie n'en avait aucun et les categories JEI
 * « Gisements » et « Gisements fluides » etaient vides (bug de la beta multijoueur). Envoye a chaque synchronisation des donnees
 * ({@link OnDatapackSyncEvent} : connexion d'un joueur, avant les recettes, donc avant le demarrage de JEI ; {@code /reload} : a tous), le
 * texte JSON de chaque fichier, relu chez le client par le meme analyseur ({@code DepositType.parse}). En solo (serveur integre), les
 * listes du serveur servent deja au client : rien a faire.
 *
 * @param deposits identifiant -> JSON de chaque gisement solide.
 * @param fluids   identifiant -> JSON de chaque gisement fluide.
 */
public record PacketDeposits(Map<ResourceLocation, String> deposits, Map<ResourceLocation, String> fluids) {

    public void encode(FriendlyByteBuf buffer) {
        write(buffer, deposits);
        write(buffer, fluids);
    }

    public static PacketDeposits decode(FriendlyByteBuf buffer) {
        return new PacketDeposits(read(buffer), read(buffer));
    }

    private static void write(FriendlyByteBuf buffer, Map<ResourceLocation, String> map) {
        buffer.writeVarInt(map.size());
        map.forEach((id, json) -> {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(json, 262_144);
        });
    }

    private static Map<ResourceLocation, String> read(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        Map<ResourceLocation, String> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(buffer.readResourceLocation(), buffer.readUtf(262_144));
        }
        return map;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            if (ServerLifecycleHooks.getCurrentServer() == null) {          // client d'un serveur distant : les listes viennent du serveur
                DepositTypes.acceptSynced(deposits);
                FluidDepositTypes.acceptSynced(fluids);
            }
        });
        context.get().setPacketHandled(true);
    }

    /** Envoi a la synchronisation des donnees (au joueur qui se connecte, ou a tous apres un {@code /reload}). */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class Sync {

        private Sync() {
        }

        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            PacketDeposits packet = new PacketDeposits(DepositTypes.sources(), FluidDepositTypes.sources());
            if (event.getPlayer() != null) {
                PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
            } else {
                PiNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
            }
        }
    }
}
