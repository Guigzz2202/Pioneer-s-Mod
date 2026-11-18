package fr.pioneers.farm.client.leisure;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.leisure.GeneratedLeisure.Tune;
import fr.pioneers.farm.leisure.Instrument;
import fr.pioneers.farm.leisure.InstrumentItem;
import fr.pioneers.farm.leisure.PacketNote;
import fr.pioneers.farm.leisure.PiLeisure;
import fr.pioneers.core.network.PiNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.5 : joue les notes cote client : la note s'entend tout de suite chez le joueur, puis part au serveur ({@link PacketNote}) pour les autres.
 * Joue aussi un air connu pas a pas (un pas = un masque de notes et une duree en ticks), meme ecran ferme, tant que l'instrument est tenu.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class TunePlayer {

    @Nullable
    private static Tune tune;
    @Nullable
    private static Instrument instrument;
    private static int step, wait;
    /** Heure (ticks client) de la derniere frappe de chaque touche : la touche s'allume un instant. */
    static final long[] HIT = new long[16];
    static long clock;

    private TunePlayer() {
    }

    public static void play(Instrument held, int keys) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || keys == 0) {
            return;
        }
        for (int key = 0; key < held.keys(); key++) {
            if ((keys & 1 << key) != 0) {
                player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), PiLeisure.sound(held, key), SoundSource.RECORDS, 1F,
                      held.pitch(key), false);
                HIT[key] = clock;
            }
        }
        LeisureClient.heard(player.getId());                                    //1.6 : animation des bras et de l'instrument
        PiNetwork.sendToServer(new PacketNote(held.ordinal(), keys));
    }

    public static void start(Instrument held, Tune next) {
        instrument = held;
        tune = next;
        step = 0;
        wait = 0;
    }

    public static void stop() {
        tune = null;
    }

    @Nullable
    public static Tune playing() {
        return tune;
    }

    /** Fraction jouee de l'air en cours (0..1). */
    public static float progress() {
        return tune == null ? 0 : step / (float) tune.notes().length;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clock++;
        LocalPlayer player = Minecraft.getInstance().player;
        if (tune == null) {
            return;
        }
        if (player == null || InstrumentItem.held(player) != instrument) {
            tune = null;
            return;
        }
        if (Minecraft.getInstance().isPaused() || --wait > 0) {
            return;
        }
        if (step >= tune.notes().length) {
            tune = null;
            return;
        }
        play(instrument, tune.notes()[step]);
        wait = tune.ticks()[step];
        step++;
    }
}
