package fr.pioneers.society.clan.core;

import fr.pioneers.society.clan.Clan;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 1.9 (MAJ RP, bloc 4) : evenements des clans, postes sur le bus Forge ({@code MinecraftForge.EVENT_BUS}) APRES le changement : les autres
 * paquets (territoire, guerre, statistiques, Carnet) s'y abonnent au lieu d'appeler les fondations. Aucun n'est annulable : les refus se
 * decident avant, cote serveur.
 */
public abstract class ClanEvent extends Event {

    public final MinecraftServer server;
    public final Clan clan;

    protected ClanEvent(MinecraftServer server, Clan clan) {
        this.server = server;
        this.clan = clan;
    }

    /** Un clan est fonde par une Charte (ou un clan solo vient d'etre cree : {@code clan.solo}). */
    public static class Created extends ClanEvent {

        public Created(MinecraftServer server, Clan clan) {
            super(server, clan);
        }
    }

    /** Un joueur entre ({@code converted} : le clan solo est devenu un clan ordinaire). */
    public static class MemberJoined extends ClanEvent {

        public final UUID player;
        public final boolean converted;

        public MemberJoined(MinecraftServer server, Clan clan, UUID player, boolean converted) {
            super(server, clan);
            this.player = player;
            this.converted = converted;
        }
    }

    /** Un joueur part ({@code reason} : leave, kick, admin) ; {@code backToSolo} : le clan est redevenu solo ; {@code empty} : plus personne. */
    public static class MemberLeft extends ClanEvent {

        public final UUID player;
        public final String reason;
        public final boolean backToSolo, empty;

        public MemberLeft(MinecraftServer server, Clan clan, UUID player, String reason, boolean backToSolo, boolean empty) {
            super(server, clan);
            this.player = player;
            this.reason = reason;
            this.backToSolo = backToSolo;
            this.empty = empty;
        }
    }

    /**
     * Le clan solo d'un Solitaire a ete absorbe par le clan qu'il rejoint : claims, fonctions, emplacements de chunkloading, points et
     * tresor ont ete transferes ({@code clan} = clan d'accueil, {@code from} = identifiant du clan solo disparu).
     */
    public static class SoloMerged extends ClanEvent {

        public final UUID from;
        public final UUID player;
        /** Le clan solo absorbe, deja retire des donnees mais encore lisible (ses {@code extra} par exemple). */
        public final Clan solo;

        public SoloMerged(MinecraftServer server, Clan clan, Clan solo, UUID player) {
            super(server, clan);
            this.from = solo.id;
            this.solo = solo;
            this.player = player;
        }
    }

    /** Nouveau Representant ({@code previous} peut etre nul). */
    public static class RepresentativeChanged extends ClanEvent {

        @Nullable
        public final UUID previous;
        public final UUID current;

        public RepresentativeChanged(MinecraftServer server, Clan clan, @Nullable UUID previous, UUID current) {
            super(server, clan);
            this.previous = previous;
            this.current = current;
        }
    }

    /** Le regime en vigueur a change (apres le delai de 7 jours, une Revolution ou le choix d'un clan converti). */
    public static class RegimeChanged extends ClanEvent {

        public final String previous;

        public RegimeChanged(MinecraftServer server, Clan clan, String previous) {
            super(server, clan);
            this.previous = previous;
        }
    }

    /** Clan vide : tresor detruit, membres partis ; ses claims seront liberes apres le delai ({@link ClanCore#finishDissolution}). */
    public static class Emptied extends ClanEvent {

        public Emptied(MinecraftServer server, Clan clan) {
            super(server, clan);
        }
    }

    /** Clan retire des donnees (claims liberes) : dernier evenement d'un clan. */
    public static class Dissolved extends ClanEvent {

        public Dissolved(MinecraftServer server, Clan clan) {
            super(server, clan);
        }
    }
}
