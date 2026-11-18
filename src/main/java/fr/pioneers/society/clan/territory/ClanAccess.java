package fr.pioneers.society.clan.territory;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanRoles;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 1.9 (MAJ RP, bloc 4) : protection cote serveur, a partir de l'index des claims (une lecture) et des donnees du clan proprietaire. Sert a
 * {@code ClanApi.canModify} (joueur connecte) et aux automates d'un joueur (robots, poses a distance) par l'identifiant de leur proprietaire.
 */
public final class ClanAccess {

    private ClanAccess() {
    }

    /** Le joueur {@code player} (connecte ou non) peut-il faire {@code action} en {@code pos} ? Hors claim : oui. */
    public static boolean canModify(MinecraftServer server, Level level, UUID player, BlockPos pos, ClanApi.Action action) {
        if (level.isClientSide || !ClanApi.enabled()) {
            return true;
        }
        String dim = ClanApi.dim(level);
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        UUID ownerId = ClaimIndex.owner(dim, chunk);
        if (ownerId == null) {
            return true;
        }
        ClanData data = ClanData.get(server);
        Clan owner = data.clan(ownerId);
        if (owner == null) {
            //clan dissous dont les chunks attendent leur liberation : personne n'y touche, sauf a l'expiration
            return false;
        }
        Clan.Claim claim = owner.claim(dim, chunk);
        Clan mine = data.clanOf(player);
        return Access.decide(caseOf(server, owner, claim, mine, player), action);
    }

    /** Situation d'un acteur face a un claim (relation, zone, rang, permissions effectives, zone federale, scelles). */
    public static Access.Case caseOf(MinecraftServer server, Clan owner, @Nullable Clan.Claim claim, @Nullable Clan mine, UUID player) {
        ClanApi.Relation rel = ClanApi.relation(server, mine, owner);
        Access.Zone zone = claim == null ? Access.Zone.MEMBERS : Access.Zone.valueOf(claim.zone.name());
        boolean federal = claim != null && claim.federal && mine != null && owner.faction != null && owner.faction.equals(mine.faction);
        boolean sealed = claim != null && claim.sealedUntil > ClanClock.now();
        boolean sealedForMe = sealed && mine != null && mine.id.equals(claim.sealedFor);
        //1.0.3-beta (lot 16) : categorie du visiteur (sans clan ou Solitaire = etranger) et matrice du proprietaire
        ZonePerms.Visitor visitor = ZonePerms.visitor(rel, mine == null || mine.solo);
        return new Access.Case(rel, zone, claim == null ? 0 : claim.minRank, rel == ClanApi.Relation.SAME ? ClanRoles.rank(owner, player) : 0,
              p -> rel == ClanApi.Relation.SAME && ClanRoles.has(owner, player, p), federal, sealed, sealedForMe, visitor, owner.zonePerms);
    }

    /**
     * 1.0.3-beta (lot 16) : pourquoi ce geste est refuse, pour la barre d'action. Visiteur refuse par la matrice : « Territoire de X :
     * « Conteneurs » est ferme aux etrangers » ; zone Privee : message de la zone ; sinon (membre sans la permission de son role, scelles,
     * zone federale, clan dissous) le message d'avant ({@code clan.pioneers.protect.denied}).
     */
    public static Component denial(MinecraftServer server, Level level, UUID player, BlockPos pos, @Nullable ClanApi.Action action) {
        String dim = ClanApi.dim(level);
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        UUID ownerId = ClaimIndex.owner(dim, chunk);
        ClanData data = ClanData.get(server);
        Clan owner = ownerId == null ? null : data.clan(ownerId);
        if (owner == null) {
            return Component.translatable("clan.pioneers.protect.denied", Component.translatable("clan.pioneers.territory.someone"));
        }
        Component name = ClanMessages.name(server, owner);
        Clan.Claim claim = owner.claim(dim, chunk);
        Access.Case c = caseOf(server, owner, claim, data.clanOf(player), player);
        boolean sealedBox = c.sealed() && action == ClanApi.Action.CONTAINER;
        if (action == null || c.relation() == ClanApi.Relation.SAME || sealedBox || c.federalSameFaction()) {
            return Component.translatable("clan.pioneers.protect.denied", name);
        }
        if (c.zone() == Access.Zone.PRIVATE) {
            return Component.translatable("clan.pioneers.protect.denied_private", name);
        }
        return Component.translatable("clan.pioneers.protect.denied_visitor", name,
              Component.translatable("clan.pioneers.zone_perm.group." + ZonePerms.Group.of(action).id()),
              Component.translatable("clan.pioneers.zone_perm.closed_to." + c.visitor().id()));
    }

    /** Clan proprietaire d'une position (null hors claim, ou clan dissous). */
    @Nullable
    public static Clan owner(MinecraftServer server, Level level, BlockPos pos) {
        UUID id = ClaimIndex.owner(ClanApi.dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return id == null ? null : ClanData.get(server).clan(id);
    }

    /** Position dans un claim (lecture de l'index seulement). */
    public static boolean claimed(Level level, BlockPos pos) {
        return ClaimIndex.owner(ClanApi.dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)) != null;
    }

    public static boolean claimed(Level level, long chunk) {
        return ClaimIndex.owner(ClanApi.dim(level), chunk) != null;
    }
}
