package fr.pioneers.clan.treasury;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 1.9 (MAJ RP, bloc 4) : journal d'AUDIT des clans, fichier horodate {@code clan_audit.log} dans le dossier du monde, hors des donnees de
 * monde : claims, retraits, votes, exclusions, guerres, administration. Une ligne par evenement : date, categorie, clan (nom et identifiant),
 * texte libre. L'auteur d'un bulletin SECRET n'y est jamais ecrit (les appelants ne le passent pas). Au-dela de 5 Mo, le fichier est
 * renomme en {@code clan_audit.log.1}. Lu par {@code /clan admin audit}.
 */
public final class ClanAudit {

    public static final String FILE = "clan_audit.log";
    private static final long ROTATE = 5L * 1024 * 1024;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ClanAudit() {
    }

    public static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }

    /**
     * Ecrit une ligne. {@code category} : claim, unclaim, withdraw, donate, bonus, tax, vote, kick, join, leave, regime, war, treaty, admin,
     * dissolve... ; {@code clan} peut etre nul (evenement de serveur).
     */
    public static void write(MinecraftServer server, String category, @Nullable Clan clan, String text) {
        String who = clan == null ? "-" : (clan.name == null || clan.name.isBlank() ? "(solo)" : clan.name) + " " + clan.id;
        String line = TIME.format(LocalDateTime.now()) + " [" + category + "] " + who + " : " + text.replace('\n', ' ');
        Path p = path(server);
        try {
            if (Files.exists(p) && Files.size(p) > ROTATE) {
                Files.move(p, p.resolveSibling(FILE + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
            try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException e) {
            Pioneers.LOGGER.warn("Audit des clans : ecriture impossible ({})", e.toString());
        }
    }

    /** Alerte d'administration (retrait anormal, regime change a repetition...) : ligne d'audit et message aux operateurs connectes. */
    public static void alert(MinecraftServer server, @Nullable Clan clan, String key, Object... args) {
        write(server, "alert", clan, key + " " + java.util.Arrays.toString(args));
        Component msg = Component.translatable(key, args).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(3)) {
                p.sendSystemMessage(msg);
            }
        }
    }

    /** Dernieres lignes du fichier (au plus {@code n}). */
    public static List<String> tail(MinecraftServer server, int n) {
        Path p = path(server);
        Deque<String> out = new ArrayDeque<>();
        if (!Files.exists(p)) {
            return new ArrayList<>();
        }
        try (var lines = Files.lines(p, StandardCharsets.UTF_8)) {
            lines.forEach(l -> {
                out.addLast(l);
                if (out.size() > n) {
                    out.pollFirst();
                }
            });
        } catch (IOException | java.io.UncheckedIOException e) {
            Pioneers.LOGGER.warn("Audit des clans : lecture impossible ({})", e.toString());
        }
        return new ArrayList<>(out);
    }
}
