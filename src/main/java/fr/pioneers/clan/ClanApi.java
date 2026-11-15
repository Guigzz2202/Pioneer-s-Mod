package fr.pioneers.clan;

import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpConfig;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 1.9 (MAJ RP, bloc 4) : facade publique des clans, exposee aux autres blocs et aux autres mods. {@link #canModify} dit si un joueur peut agir
 * sur une position (casser, poser, ouvrir, utiliser...) ; {@link #canTransfer} dit si un transfert du mod (conduite, cable, robot, rail,
 * reseau logistique) peut franchir une frontiere de chunk. Une seule lecture de {@link ClaimIndex} par appel.
 */
public final class ClanApi {

    /** Actions protegees. */
    public enum Action {
        BREAK, PLACE, CONTAINER, MACHINE, INTERACT, VEHICLE, ENTITY, FARMLAND, ATTACK_ANIMAL
    }

    /** Relation entre deux clans. */
    public enum Relation {
        SAME, ALLY, NEUTRAL, WAR
    }

    private ClanApi() {
    }

    public static boolean enabled() {
        return PioneerApi.hasFeature("clans");
    }

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    @Nullable
    public static Clan clanOf(MinecraftServer server, UUID player) {
        return ClanData.get(server).clanOf(player);
    }

    @Nullable
    public static Clan owner(MinecraftServer server, Level level, BlockPos pos) {
        UUID id = ClaimIndex.owner(dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return id == null ? null : ClanData.get(server).clan(id);
    }

    /** Relation entre deux clans : meme clan, allies (traite d'alliance ou meme faction), en guerre, ou neutres. */
    public static Relation relation(MinecraftServer server, @Nullable Clan a, @Nullable Clan b) {
        if (a == null || b == null) {
            return Relation.NEUTRAL;
        }
        if (a.id.equals(b.id)) {
            return Relation.SAME;
        }
        if (Wars.atWar(server, a, b)) {
            return Relation.WAR;
        }
        if (a.faction != null && a.faction.equals(b.faction)) {
            return Relation.ALLY;
        }
        var t = a.treaties.get(b.id);
        if (t != null && t.getBoolean("alliance") && (t.getLong("end") <= 0 || t.getLong("end") > System.currentTimeMillis())) {
            return Relation.ALLY;
        }
        //1.9 (MAJ RP, bloc 4) : alliance signee par la faction de l'un ou de l'autre (traite federal)
        if (fr.pioneers.clan.faction.FederalTreaties.alliance(server, a, b)) {
            return Relation.ALLY;
        }
        return Relation.NEUTRAL;
    }

    /** Peut-on agir ici ? Hors claim : oui. Les operateurs en creatif passent toujours. */
    public static boolean canModify(Player player, Level level, BlockPos pos, Action action) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp) || !enabled()) {
            return true;
        }
        UUID ownerId = ClaimIndex.owner(dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (ownerId == null) {
            return true;
        }
        if (sp.isCreative() && sp.hasPermissions(2)) {
            return true;
        }
        //1.9 (MAJ RP, bloc 4) : decision commune aux joueurs et aux automates (fr.pioneers.clan.territory.Access, banc de la matrice)
        return fr.pioneers.clan.territory.ClanAccess.canModify(sp.getServer(), level, sp.getUUID(), pos, action);
    }

    /**
     * Un transfert du mod peut-il aller de {@code from} a {@code to} ? Oui dans un meme chunk, entre deux chunks du meme proprietaire (ou deux
     * chunks sans proprietaire), entre allies ; non a travers toute autre frontiere (y compris d'un claim vers la nature et inversement).
     */
    public static boolean canTransfer(Level level, BlockPos from, BlockPos to) {
        int fx = from.getX() >> 4, fz = from.getZ() >> 4, tx = to.getX() >> 4, tz = to.getZ() >> 4;
        //1.9 (MAJ RP, bloc 4) : le cas courant (meme chunk) d'abord, appele a chaque transfert des machines
        if (fx == tx && fz == tz || level.isClientSide || !enabled() || !ClaimIndex.any()) {
            return true;
        }
        String dim = dim(level);
        UUID a = ClaimIndex.owner(dim, ChunkPos.asLong(fx, fz)), b = ClaimIndex.owner(dim, ChunkPos.asLong(tx, tz));
        if (a == null && b == null || a != null && a.equals(b)) {
            return true;
        }
        if (a == null || b == null || level.getServer() == null) {
            return false;
        }
        ClanData data = ClanData.get(level.getServer());
        Clan ca = data.clan(a), cb = data.clan(b);
        //1.9 (MAJ RP, lot 19) : ou transit accorde dans les deux sens (fr.pioneers.clan.territory.ClanTransit)
        return relation(level.getServer(), ca, cb) == Relation.ALLY || fr.pioneers.clan.territory.ClanTransit.mutual(ca, cb);
    }

    /** Taille max d'un clan au niveau donne (8, +4 par niveau, plafond de configuration). */
    public static int maxMembers(int level) {
        return Math.min(PiCommonConfig.get(RpConfig.MAX_MEMBERS, 40), 8 + 4 * (Math.max(1, level) - 1));
    }
}
