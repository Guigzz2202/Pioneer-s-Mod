package fr.pioneers.farm.leisure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import fr.pioneers.client.farm.leisure.LeisureClient;
import fr.pioneers.network.PiNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 1.5 : notes jouees, du client au serveur : masque des touches frappees ensemble (bit i = note i, ou frappe i du tambour). Le serveur verifie
 * l'instrument tenu, limite le debit ({@link #MAX_PER_SECOND} paquets par seconde et par joueur) et fait entendre les notes aux autres joueurs
 * (le joueur les entend deja chez lui) jusqu'a 48 blocs, avec une note de musique au-dessus de sa tete. 1.6 : les joueurs qui le voient recoivent
 * {@link Fx} pour animer ses bras et son instrument.
 */
public record PacketNote(int instrument, int keys) {

    public static final int MAX_PER_SECOND = 16, MAX_KEYS = 4;
    private static final Map<UUID, long[]> RATE = new HashMap<>();

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(instrument);
        buffer.writeVarInt(keys);
    }

    public static PacketNote decode(FriendlyByteBuf buffer) {
        return new PacketNote(buffer.readByte(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        Instrument held = player == null ? null : InstrumentItem.held(player);
        if (held == null || held.ordinal() != instrument || keys <= 0 || keys >= 1 << held.keys() || Integer.bitCount(keys) > MAX_KEYS) {
            return;
        }
        long now = player.level().getGameTime();
        long[] rate = RATE.computeIfAbsent(player.getUUID(), k -> new long[2]);
        if (now - rate[0] >= 20) {
            rate[0] = now;
            rate[1] = 0;
        }
        if (++rate[1] > MAX_PER_SECOND) {
            return;
        }
        int first = -1;
        for (int key = 0; key < held.keys(); key++) {
            if ((keys & 1 << key) != 0) {
                player.level().playSound(player, player.getX(), player.getEyeY(), player.getZ(), PiLeisure.sound(held, key), SoundSource.RECORDS, 3F,
                      held.pitch(key));
                first = first < 0 ? key : first;
            }
        }
        player.serverLevel().sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + player.getBbHeight() + 0.5, player.getZ(), 0,
              (first % 24) / 24.0, 0, 0, 1);
        PiNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), new Fx(player.getId()));
    }

    /** 1.6 : du serveur aux joueurs qui voient le musicien : il vient de jouer une note (animation, {@link LeisureClient#heard}). */
    public record Fx(int entity) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(entity);
        }

        public static Fx decode(FriendlyByteBuf buffer) {
            return new Fx(buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LeisureClient.heard(entity));
        }
    }
}
