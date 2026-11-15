package fr.pioneers.client.pet;

import com.mojang.blaze3d.platform.InputConstants;
import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.pet.PacketPetList;
import fr.pioneers.pet.PacketPetRecall;
import fr.pioneers.pet.PetRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 1.0.5-beta (lot 5, specification §5.5) : la touche RAPPEL cote client. Appui court (relachee avant {@link PetRules#WHEEL_HOLD} ticks) :
 * « rappeler tous mes animaux » ; maintien : la ROUE ({@link PetWheelScreen}, liste demandee au serveur) ; relacher sur un animal le rappelle
 * seul. Le serveur juge de tout ({@link PacketPetRecall}). Une seule fois par installation : si la touche des modules de l'equipement est
 * encore sur G (ancien defaut, sauve dans options.txt), elle passe sur R pour ne pas se marcher dessus avec le rappel.
 * <p>
 * 1.0.5-beta (lot 33, retour 45 : « de la latence ») : la roue s'ouvre a l'APPUI, dans l'evenement de la touche (meme image), avec la liste
 * du cache {@link WheelCache} (poussee par le serveur) ; l'appui bref reste « tout rappeler » ({@link WheelCache#decide}, au relachement).
 * Le tick ne sert plus que de filet (appui que l'evenement n'aurait pas vu). Cache vide a la deconnexion.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class PetRecallClient {

    private PetRecallClient() {
    }

    private static boolean migrated;

    /** Appui sur la touche Rappel au clavier : la roue tout de suite. */
    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && PiKeys.PET_RECALL.matches(event.getKey(), event.getScanCode())) {
            open(Minecraft.getInstance());
        }
    }

    /** Touche Rappel mise sur un bouton de la souris. */
    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS && PiKeys.PET_RECALL.matchesMouse(event.getButton())) {
            open(Minecraft.getInstance());
        }
    }

    /** Ouvre la roue (en jeu, aucun ecran ouvert) et demande la liste fraiche ; le contenu du cache s'affiche des cette image. */
    static void open(Minecraft mc) {
        if (mc.player == null || mc.screen != null) {
            return;
        }
        PiNetwork.sendToServer(new PacketPetRecall(PacketPetRecall.LIST, new UUID(0, 0)));
        mc.setScreen(new PetWheelScreen(Util.getMillis()));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!migrated) {
            migrated = true;
            migrateGearKey(mc);
        }
        boolean clicked = false;
        while (PiKeys.PET_RECALL.consumeClick()) {
            clicked = true;
        }
        //filet : appui vu par la touche mais pas par l'evenement (rare) -> la roue s'ouvre au tick ; ecran ouvert (roue comprise) : clic jete
        if (clicked && mc.screen == null) {
            open(mc);
        }
    }

    /** Liste recue du serveur (reponse, envoi du ou poussee) : dans le cache ; la roue ouverte la reprend a l'image suivante. */
    public static void receive(List<PacketPetList.Row> rows) {
        WheelCache.CLIENT.receive(rows);
    }

    /** Deconnexion (ou connexion a un autre serveur) : rien du monde d'avant. */
    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WheelCache.CLIENT.clear();
    }

    @SubscribeEvent
    public static void loggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        WheelCache.CLIENT.clear();
    }

    /** Relachement : tous, un, ou rien ({@link WheelCache#decide}). */
    static void recall(int what, UUID id) {
        if (what == WheelCache.ALL) {
            PiNetwork.sendToServer(new PacketPetRecall(PacketPetRecall.ALL, new UUID(0, 0)));
        } else if (what == WheelCache.ONE && id != null) {
            recallOne(id);
        }
    }

    static void recallOne(UUID id) {
        PiNetwork.sendToServer(new PacketPetRecall(PacketPetRecall.ONE, id));
    }

    /** Une fois : modules de l'equipement sur G (ancien defaut) et rappel sur G -> modules sur R. Marque dans pioneers/keys_105.txt. */
    static void migrateGearKey(Minecraft mc) {
        try {
            Path mark = mc.gameDirectory.toPath().resolve("pioneers").resolve("keys_105.txt");
            if (Files.exists(mark)) {
                return;
            }
            KeyMapping gear = PiKeys.GEAR, recall = PiKeys.PET_RECALL;
            if (gear.getKey().getValue() == GLFW.GLFW_KEY_G && recall.getKey().getValue() == GLFW.GLFW_KEY_G) {
                gear.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
                KeyMapping.resetMapping();
                mc.options.save();
            }
            Files.createDirectories(mark.getParent());
            Files.writeString(mark, "1.0.5-beta : touche des modules de l'equipement deplacee de G vers R (rappel des animaux sur G)\n");
        } catch (IOException | RuntimeException e) {
            Pioneers.LOGGER.warn("Touches 1.0.5 : migration impossible", e);
        }
    }
}
