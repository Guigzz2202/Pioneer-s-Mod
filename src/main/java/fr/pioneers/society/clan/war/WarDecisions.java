package fr.pioneers.society.clan.war;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.society.clan.vote.ClanVotes;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : les decisions de diplomatie et de guerre suivent le regime du clan (tableau "qui tranche quoi"). Deux types du
 * moteur de votes : {@code war} (declarer, capituler) et {@code treaty} (accepter ou rompre un traite, cessez-le-feu) ; l'action precise est
 * dans les donnees ({@code action}). {@link ClanGov#decide} applique tout de suite (Representant, quand le regime la lui donne) ou ouvre un
 * vote ; l'effet branche ici ({@link ClanVotes#register}) verifie tout a nouveau au moment de l'appliquer.
 */
public final class WarDecisions {

    public static final String WAR = "war", TREATY = "treaty";
    public static final String DECLARE = "declare", SURRENDER = "surrender", CEASEFIRE = "ceasefire", ACCEPT = "accept", BREAK = "break";

    private WarDecisions() {
    }

    /** Branche les effets et les verifications dans le moteur de votes (appele au chargement du mod). */
    public static void install() {
        ClanVotes.register(WAR, WarDecisions::effect);
        ClanVotes.register(TREATY, WarDecisions::effect);
        ClanGov.registerCheck(WAR, WarDecisions::check);
        ClanGov.registerCheck(TREATY, WarDecisions::check);
    }

    /** Effet branche dans le moteur de votes : l'auteur de la decision est garde pour le journal. */
    private static boolean effect(MinecraftServer server, ClanVotes.Decision d) {
        if (d.clan() == null) {
            return false;
        }
        CompoundTag data = d.data().copy();
        if (d.author() != null) {
            data.putUUID("by", d.author());
        }
        return apply(server, d.clan(), data);
    }

    /** Decision du tableau d'une action. */
    public static String decisionOf(String action) {
        return DECLARE.equals(action) || SURRENDER.equals(action) ? WAR : TREATY;
    }

    /** Engage une decision du clan ; previent le joueur (appliquee, vote ouvert, ou refus). Renvoie vrai si rien n'a ete refuse. */
    public static boolean submit(ServerPlayer by, Clan clan, CompoundTag data, String text) {
        String action = data.getString("action");
        //intitule traduisible de la proposition (cle + arguments, "#" = cle de langue) pour l'affichage du vote
        data.putString("label", "clan.pioneers.war.vote." + action);
        net.minecraft.nbt.ListTag args = new net.minecraft.nbt.ListTag();
        fr.pioneers.society.clan.ClanData cd = fr.pioneers.society.clan.ClanData.get(by.getServer());
        Clan other = data.hasUUID("target") ? cd.clan(data.getUUID("target")) : data.hasUUID("other") ? cd.clan(data.getUUID("other")) : null;
        args.add(net.minecraft.nbt.StringTag.valueOf(other == null ? "?" : other.name));
        if (data.contains("kind")) {
            args.add(net.minecraft.nbt.StringTag.valueOf("#clan.pioneers.treaty.kind." + data.getString("kind")));
        }
        if (data.contains("reason")) {
            args.add(net.minecraft.nbt.StringTag.valueOf(data.getString("reason")));
        }
        data.put("labelArgs", args);
        ClanGov.Result r = ClanGov.decide(by, clan, decisionOf(action), data, text);
        if (!r.ok()) {
            by.sendSystemMessage(Component.translatable(r.error() == null ? "clan.pioneers.error.failed" : r.error()).withStyle(ChatFormatting.RED));
            return false;
        }
        if (r.outcome() == ClanGov.Outcome.PROPOSED) {
            by.sendSystemMessage(Component.translatable("clan.pioneers.war.vote_opened", Component.translatable("clan.pioneers.war.action." + action)));
        }
        return true;
    }

    /** Verification prealable (au depot et a la fermeture d'un vote) : cle de refus ou null. */
    @Nullable
    static String check(MinecraftServer server, Clan clan, @Nullable ServerPlayer actor, CompoundTag data) {
        return switch (data.getString("action")) {
            case DECLARE -> ClanWar.checkDeclare(server, clan, data);
            case SURRENDER -> ClanWar.checkSurrender(server, clan, data);
            case CEASEFIRE -> ClanWar.checkCeasefire(server, clan, data);
            case ACCEPT -> ClanDiplomacy.checkAccept(server, clan, data);
            case BREAK -> ClanDiplomacy.checkBreak(server, clan, data);
            default -> "clan.pioneers.error.invalid";
        };
    }

    /** Effet d'une decision adoptee (directe ou votee) ; faux si elle ne peut plus s'appliquer. */
    static boolean apply(MinecraftServer server, Clan clan, CompoundTag data) {
        if (check(server, clan, null, data) != null) {
            return false;
        }
        return switch (data.getString("action")) {
            case DECLARE -> ClanWar.applyDeclare(server, clan, data);
            case SURRENDER -> ClanWar.applySurrender(server, clan, data);
            case CEASEFIRE -> ClanWar.applyCeasefire(server, clan, data);
            case ACCEPT -> ClanDiplomacy.applyAccept(server, clan, data);
            case BREAK -> ClanDiplomacy.applyBreak(server, clan, data);
            default -> false;
        };
    }
}
