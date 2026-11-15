package fr.pioneers.core.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 1.12.1 (F1) : environnement du joueur pour la ligne climat / oxygene / gravite de la minimap (serveur -> client), envoye par
 * {@code space/environment/EnvironmentSync} au plus une fois par seconde et seulement s'il a change. Le dernier recu est garde ici
 * ({@link #last()}, code commun sans classe client) et lu par la minimap.
 *
 * @param gravity     gravite de jeu (1 = Terre).
 * @param oxygen      {@link #NONE}, {@link #AIR} (atmosphere respirable) ou {@link #PRESSURIZED} (piece pressurisee).
 * @param temperature degres Celsius.
 * @param humidity    humidite du biome en % (-1 : sans objet, hors de la Terre).
 * @param hazard      danger principal de l'astre (ordinal de {@code BodyEnvironment.Hazard}).
 * @param climateT    1.0.5-beta (lot 14) : temperature de jeu du lieu, saison comprise, celle des cultures ({@code Climate.temperature}) ;
 *                    NaN hors de la Terre.
 * @param climateH    1.0.5-beta (lot 14) : humidite du lieu (0..1), saison comprise, celle des cultures sans irrigation
 *                    ({@code Climate.humidity}) ; NaN hors de la Terre.
 */
public record PacketEnvironment(float gravity, int oxygen, int temperature, int humidity, int hazard, float climateT, float climateH) {

    public static final int NONE = 0, AIR = 1, PRESSURIZED = 2;

    /** 1.0.5-beta (lot 14) : climat des cultures connu (Terre). */
    public boolean hasClimate() {
        return !Float.isNaN(climateT) && !Float.isNaN(climateH);
    }

    @Nullable
    private static volatile PacketEnvironment last;

    /** Dernier environnement recu (client), null si rien depuis l'arrivee dans la dimension. */
    @Nullable
    public static PacketEnvironment last() {
        return last;
    }

    /** Oublie l'environnement (deconnexion, changement de dimension cote client). */
    public static void clear() {
        last = null;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(gravity);
        buffer.writeByte(oxygen);
        buffer.writeShort(temperature);
        buffer.writeByte(humidity);
        buffer.writeByte(hazard);
        buffer.writeFloat(climateT);
        buffer.writeFloat(climateH);
    }

    public static PacketEnvironment decode(FriendlyByteBuf buffer) {
        return new PacketEnvironment(buffer.readFloat(), buffer.readByte(), buffer.readShort(), buffer.readByte(), buffer.readByte(), buffer.readFloat(),
              buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> last = this);
        context.get().setPacketHandled(true);
    }
}
