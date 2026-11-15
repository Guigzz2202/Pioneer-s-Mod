package fr.pioneers.society.clan.core;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.core.profile.PioneerCap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : messages des clans (chat systeme, textes traduits chez le joueur). Les noms montres sont des NOMS DE PERSONNAGE :
 * jamais un pseudo de compte hors du clan. Les arguments de journal prefixes par « # » sont des cles de langue.
 */
public final class ClanMessages {

    private ClanMessages() {
    }

    /** Message a tous les membres connectes (ceux qui ont la permission si {@code perm} n'est pas nul). */
    public static void members(MinecraftServer server, Clan c, @Nullable Permission perm, Component msg) {
        for (UUID u : c.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && (perm == null || ClanRoles.has(c, u, perm))) {
                p.sendSystemMessage(msg);
            }
        }
    }

    /** Message aux Representants (Delegues) connectes des clans d'une faction. */
    public static void delegates(MinecraftServer server, Faction f, Component msg) {
        ClanData data = ClanData.get(server);
        for (UUID id : f.clans) {
            Clan c = data.clan(id);
            if (c != null) {
                members(server, c, null, msg);
            }
        }
    }

    public static void tell(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args));
    }

    public static void refuse(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }

    /** En-tete « [ETQ] » a la couleur du clan devant un message. */
    public static MutableComponent tagged(Clan c, Component msg) {
        String tag = c.tag == null || c.tag.isBlank() ? "" : "[" + c.tag + "] ";
        return Component.literal(tag).withStyle(dye(c.color)).append(msg.copy().withStyle(ChatFormatting.GRAY));
    }

    /** Nom affiche d'un clan : son nom, ou pour un clan solo sans nom celui de son Solitaire. */
    public static Component name(MinecraftServer server, Clan c) {
        if (c.name != null && !c.name.isBlank()) {
            return Component.literal(c.name);
        }
        return Component.translatable("clan.pioneers.solo_name", character(server, c, c.representative));
    }

    /** Nom de personnage d'un membre (profil s'il est connecte, sinon le dernier connu) ; jamais le pseudo du compte. */
    public static String character(MinecraftServer server, Clan c, @Nullable UUID u) {
        if (u == null) {
            return "?";
        }
        ServerPlayer p = server.getPlayerList().getPlayer(u);
        if (p != null) {
            String n = PioneerCap.get(p).name;
            if (n != null && !n.isBlank()) {
                return n;
            }
        }
        Clan.Member m = c.member(u);
        return m != null && m.character != null && !m.character.isBlank() ? m.character : "?";
    }

    /** Nom de personnage d'un joueur connecte (cle « clan.pioneers.unnamed » faute de mieux), pour les journaux. */
    public static String character(ServerPlayer p) {
        String n = PioneerCap.get(p).name;
        return n == null || n.isBlank() ? "#clan.pioneers.unnamed" : n;
    }

    /**
     * Argument de journal : texte, ou cle de langue si prefixe par « # ». 1.0.3-beta (lot 16) : « #cle|a|b » = cle avec ses propres
     * arguments (libelle « Achat de territoire (n° 12) »).
     */
    public static Object arg(String s) {
        if (!s.startsWith("#")) {
            return s;
        }
        String[] parts = s.substring(1).split("\\|");
        return parts.length == 1 ? Component.translatable(parts[0]) : Component.translatable(parts[0], (Object[]) java.util.Arrays.copyOfRange(parts, 1,
              parts.length));
    }

    /** Ligne de journal traduite. */
    public static Component line(String key, java.util.List<String> args) {
        return Component.translatable(key, args.stream().map(ClanMessages::arg).toArray());
    }

    public static ChatFormatting dye(int color) {
        return switch (color & 15) {
            case 0 -> ChatFormatting.WHITE;
            case 1 -> ChatFormatting.GOLD;
            case 2 -> ChatFormatting.LIGHT_PURPLE;
            case 3 -> ChatFormatting.AQUA;
            case 4 -> ChatFormatting.YELLOW;
            case 5 -> ChatFormatting.GREEN;
            case 6 -> ChatFormatting.LIGHT_PURPLE;
            case 7 -> ChatFormatting.DARK_GRAY;
            case 8 -> ChatFormatting.GRAY;
            case 9 -> ChatFormatting.DARK_AQUA;
            case 10 -> ChatFormatting.DARK_PURPLE;
            case 11 -> ChatFormatting.BLUE;
            case 12 -> ChatFormatting.GOLD;
            case 13 -> ChatFormatting.DARK_GREEN;
            case 14 -> ChatFormatting.RED;
            default -> ChatFormatting.BLACK;
        };
    }
}
