package fr.pioneers.clan.territory;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.core.ClanRoles;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.economy.EconomyApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, lot 19) : la permission de transit (spec bloc 4 : « robots, rails et reseaux logistiques d'un autre clan ne traversent
 * qu'avec la permission de transit ou un traite d'alliance »). Un membre qui la detient ouvre le territoire de son clan aux automates d'un
 * clan nomme : ses robots et chantiers y agissent ({@link ClanAgents#canAct}), et quand les deux clans se l'accordent l'un a l'autre, les
 * transferts du mod franchissent leur frontiere commune ({@code ClanApi.canTransfer}). Rangee dans {@code Clan.extra["transit"]} (ids de
 * clans). Le simple passage d'un robot ou d'un train n'est jamais bloque (decision M1 : un claim pose sur une voie d'autrui ne la coupe pas).
 */
public final class ClanTransit {

    static final String KEY = "transit";

    private ClanTransit() {
    }

    /** {@code owner} a-t-il ouvert son territoire aux automates du clan {@code other} ? */
    public static boolean grants(@Nullable Clan owner, @Nullable UUID other) {
        if (owner == null || other == null) {
            return false;
        }
        String s = other.toString();
        ListTag l = owner.extra.getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            if (s.equals(l.getString(i))) {
                return true;
            }
        }
        return false;
    }

    /** Les deux clans se sont-ils accorde le transit l'un a l'autre (condition des transferts a leur frontiere) ? */
    public static boolean mutual(@Nullable Clan a, @Nullable Clan b) {
        return a != null && b != null && grants(a, b.id) && grants(b, a.id);
    }

    /** Clans auxquels {@code owner} accorde le transit (encore existants). */
    public static List<Clan> granted(MinecraftServer server, Clan owner) {
        List<Clan> out = new ArrayList<>();
        ClanData data = ClanData.get(server);
        ListTag l = owner.extra.getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) {
            try {
                Clan c = data.clan(UUID.fromString(l.getString(i)));
                if (c != null) {
                    out.add(c);
                }
            } catch (IllegalArgumentException ignored) {
                //identifiant illisible : ignore
            }
        }
        return out;
    }

    /** {@code /clan transit grant|revoke <clan>} : demande la permission TRANSIT ; inscrit au journal, refait les reseaux des deux clans. */
    public static void set(ServerPlayer p, String name, boolean on) {
        MinecraftServer server = p.getServer();
        ClanData data = ClanData.get(server);
        Clan c = data.clanOf(p.getUUID());
        if (c == null) {
            ClanMessages.refuse(p, "clan.pioneers.error.no_clan");
            return;
        }
        if (!ClanRoles.has(c, p.getUUID(), Permission.TRANSIT)) {
            ClanMessages.refuse(p, "clan.pioneers.error.permission");
            return;
        }
        Clan other = data.byName(name == null ? "" : name.trim());
        if (other == null || other.id.equals(c.id)) {
            ClanMessages.refuse(p, "clan.pioneers.error.no_such_clan");
            return;
        }
        String id = other.id.toString();
        ListTag l = c.extra.getList(KEY, Tag.TAG_STRING);
        ListTag next = new ListTag();
        boolean had = false;
        for (int i = 0; i < l.size(); i++) {
            if (id.equals(l.getString(i))) {
                had = true;
            } else {
                next.add(l.get(i).copy());
            }
        }
        if (on) {
            next.add(StringTag.valueOf(id));
        }
        if (had == on) {
            ClanMessages.tell(p, on ? "clan.pioneers.transit.granted" : "clan.pioneers.transit.revoked", ClanMessages.name(server, other));
            return;
        }
        c.extra.put(KEY, next);
        c.log(PioneerApi.day(server), on ? "clan.pioneers.log.transit_grant" : "clan.pioneers.log.transit_revoke", ClanMessages.name(server, other).getString(),
              EconomyApi.name(p));
        data.changed();
        BorderSplits.clanChanged(server, c);
        BorderSplits.clanChanged(server, other);
        ClanMessages.tell(p, on ? "clan.pioneers.transit.granted" : "clan.pioneers.transit.revoked", ClanMessages.name(server, other));
    }

    /** {@code /clan transit list}. */
    public static void list(ServerPlayer p) {
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null) {
            ClanMessages.refuse(p, "clan.pioneers.error.no_clan");
            return;
        }
        List<String> names = granted(p.getServer(), c).stream().map(x -> ClanMessages.name(p.getServer(), x).getString()).toList();
        if (names.isEmpty()) {
            ClanMessages.tell(p, "clan.pioneers.transit.none");
        } else {
            ClanMessages.tell(p, "clan.pioneers.transit.list", String.join(", ", names));
        }
    }
}
