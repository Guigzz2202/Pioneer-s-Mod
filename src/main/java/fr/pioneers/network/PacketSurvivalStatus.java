package fr.pioneers.network;

import fr.pioneers.client.ClientSurvival;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Etat de survie du joueur (serveur -> client), chaque seconde hors de l'atmosphere terrestre : pour l'affichage.
 *
 * @param active       hors d'une atmosphere respirable.
 * @param safe         a l'abri (piece pressurisee, fusee, capsule, creatif).
 * @param pressurized  dans une piece pressurisee.
 * @param requiredTier combinaison exigee par l'astre.
 * @param suitTier     combinaison complete portee (0 = aucune).
 * @param oxygen       reserve du plastron en pour mille (-1 = pas de reserve).
 * @param hazard       danger principal (ordinal de BodyEnvironment.Hazard).
 * @param temperature  temperature moyenne de l'astre.
 */
public record PacketSurvivalStatus(boolean active, boolean safe, boolean pressurized, int requiredTier, int suitTier, int oxygen, int hazard, int temperature) {

    public static final PacketSurvivalStatus NONE = new PacketSurvivalStatus(false, true, false, 0, 0, -1, 0, 15);

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeBoolean(safe);
        buffer.writeBoolean(pressurized);
        buffer.writeByte(requiredTier);
        buffer.writeByte(suitTier);
        buffer.writeShort(oxygen);
        buffer.writeByte(hazard);
        buffer.writeShort(temperature);
    }

    public static PacketSurvivalStatus decode(FriendlyByteBuf buffer) {
        return new PacketSurvivalStatus(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readByte(), buffer.readByte(),
              buffer.readShort(), buffer.readByte(), buffer.readShort());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSurvival.update(this));
    }
}
