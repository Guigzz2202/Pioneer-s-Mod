package fr.pioneers.space.environment;

import fr.pioneers.Pioneers;
import fr.pioneers.api.environment.BodyEnvironment;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.network.PacketEnvironment;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.space.SolarSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 1.12.1 (F1, demande de Guillaume : « la map doit donner des infos sur le climat, la presence d'oxygene, la gravite ») : ce que la ligne
 * d'environnement de la minimap affiche, calcule par le SERVEUR (pieces pressurisees et reglages des astres par donnees n'existent que la),
 * une fois par seconde et par joueur (decale par joueur), envoye seulement quand ca change ({@link PacketEnvironment}, 7 octets).
 * <ul>
 *     <li>climat : sur Terre (et dans les dimensions sans astre) temperature et humidite du biome a la position (temperature abaissee en
 *     altitude, comme les cultures : {@link Climate}) ; ailleurs, temperature moyenne de l'astre, humidite sans objet ;</li>
 *     <li>oxygene : atmosphere respirable, piece pressurisee ({@link OxygenRooms}) ou rien ;</li>
 *     <li>gravite : celle de l'astre ({@link Environments}, 1 = Terre).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnvironmentSync {

    private static final Map<UUID, PacketEnvironment> SENT = new HashMap<>();

    private EnvironmentSync() {
    }

    /** Temperature de jeu d'un biome (vanilla : 0,8 = plaine) en degres Celsius, a la louche : 0,8 -> 17 °C, 2 -> 47 °C, 0 -> -3 °C. */
    public static int celsius(float biomeTemperature) {
        return Math.round(biomeTemperature * 25F - 3F);
    }

    /** Ce que la minimap affiche pour ce joueur, la ou il est. */
    public static PacketEnvironment read(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BodyEnvironment env = Environments.get(level);
        BlockPos pos = player.blockPosition();
        SolarSystem.Body body = SolarSystem.fromDimension(level.dimension());
        boolean earthLike = body == null || body == SolarSystem.Body.EARTH;
        //1.0.5-beta (lot 14) : temperature et humidite des cultures (memes fonctions), envoyees aussi brutes pour les libelles de la minimap
        float[] climate = earthLike ? climate(level, pos) : new float[]{Float.NaN, Float.NaN};
        int temperature = earthLike ? celsius(climate[0]) : env.temperature();
        int humidity = earthLike ? Math.round(Math.max(0, Math.min(1, climate[1])) * 100) : -1;
        int oxygen = env.breathable() ? PacketEnvironment.AIR
              : OxygenRooms.isPressurized(level, BlockPos.containing(player.getEyePosition())) ? PacketEnvironment.PRESSURIZED : PacketEnvironment.NONE;
        return new PacketEnvironment((float) env.gravity(), oxygen, temperature, humidity, env.hazard().ordinal(), climate[0], climate[1]);
    }

    /**
     * 1.0.5-beta (lot 14) : {temperature de jeu, humidite} du lieu, saison comprise : EXACTEMENT celles des cultures ({@link Climate#temperature},
     * {@link Climate#humidity} : meme lieu, meme date ; l'irrigation ne compte pas, elle depend du champ).
     */
    public static float[] climate(ServerLevel level, BlockPos pos) {
        return new float[]{Climate.temperature(level, pos), Climate.humidity(level, pos)};
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || (player.tickCount + player.getId()) % 20 != 0) {
            return;
        }
        PacketEnvironment now = read(player);
        if (!now.equals(SENT.get(player.getUUID()))) {
            SENT.put(player.getUUID(), now);
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), now);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
