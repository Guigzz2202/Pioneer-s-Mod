package fr.pioneers.client.logistic;

import fr.pioneers.network.PacketLogistic;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.transport.logistic.DiagramSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2b) : cote client du diagramme : reception des paquets (instantane, debits), envoi des editions, presse-papiers.
 * <ul>
 *     <li><b>Point</b> : presse-papiers en memoire (reglages d'une machine dans tous les canaux) ;</li>
 *     <li><b>Reseau entier</b> : memoire + fichier {@code <jeu>/pioneers/cablages/dernier.nbt} (garde d'une session a l'autre, d'un monde a
 *     l'autre : on colle sur un autre reseau, les reglages vont aux machines du meme type).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class LogisticDiagramClient {

    @Nullable
    private static CompoundTag pointClipboard;
    @Nullable
    private static CompoundTag networkClipboard;

    private LogisticDiagramClient() {
    }

    /** Instantane recu : ouvre l'ecran (ouverture), le met a jour, ou le ferme (reseau disparu). */
    public static void snapshot(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        LogisticDiagramScreen open = mc.screen instanceof LogisticDiagramScreen s ? s : null;
        if (tag.getBoolean("gone")) {
            if (open != null) {
                open.onClose();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("logistic.pioneers.diagram.gone").withStyle(ChatFormatting.GRAY), true);
                }
            }
            return;
        }
        DiagramSnapshot snap = DiagramSnapshot.load(tag);
        int selected = tag.contains("selUid") ? tag.getInt("selUid") : 0;
        String lastOp = tag.getString("lastOp");
        if (open != null && (open.cable() == snap.cable || !tag.getBoolean("open"))) {
            open.update(snap, selected, lastOp);
        } else if (tag.getBoolean("open")) {
            mc.setScreen(new LogisticDiagramScreen(snap));
        }
    }

    public static void stats(CompoundTag tag) {
        if (Minecraft.getInstance().screen instanceof LogisticDiagramScreen screen) {
            screen.stats(tag);
        }
    }

    /** Envoie une edition ; faux (et rien n'est envoye) si elle depasse la taille d'un paquet ({@link PacketLogistic#EDIT_MAX}). */
    static boolean send(CompoundTag op) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(op, new DataOutputStream(out));
            if (out.size() > PacketLogistic.EDIT_MAX) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        PiNetwork.sendToServer(new PacketLogistic.Edit(op));
        return true;
    }

    static void close() {
        PiNetwork.sendToServer(new PacketLogistic.Close());
    }

    // ------------------------------------------------------------------
    // Presse-papiers
    // ------------------------------------------------------------------

    static void copyPoint(CompoundTag copy) {
        pointClipboard = copy.copy();
    }

    @Nullable
    static CompoundTag pointClipboard() {
        return pointClipboard;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("pioneers").resolve("cablages").resolve("dernier.nbt");
    }

    /** Copie le reseau entier (memoire + fichier). Rend faux si l'ecriture du fichier a echoue (la memoire est gardee). */
    static boolean copyNetwork(CompoundTag template) {
        networkClipboard = template.copy();
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(template, path.toFile());
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Reseau copie : memoire, sinon le dernier fichier ; null si rien. */
    @Nullable
    static CompoundTag networkClipboard() {
        if (networkClipboard == null) {
            try {
                Path path = file();
                if (Files.isRegularFile(path) && Files.size(path) < 256 * 1024) {
                    networkClipboard = NbtIo.readCompressed(path.toFile());
                }
            } catch (IOException | RuntimeException e) {
                networkClipboard = null;
            }
        }
        return networkClipboard;
    }
}
