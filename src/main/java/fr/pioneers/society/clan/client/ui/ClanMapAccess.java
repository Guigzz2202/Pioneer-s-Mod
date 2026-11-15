package fr.pioneers.society.clan.client.ui;

import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.10.3 : acces public (lecture seule) a la copie client de la carte strategique ({@link ClanMapModel}, paquet {@code CLAN_MAP}), pour la
 * minimap ({@code client/minimap}). Rien de nouveau ne transite pour les claims : la minimap s'abonne a la meme carte que la fiche
 * Territoire ({@code open} toutes les 5 s tant qu'elle en a besoin), grille de 31 x 31 chunks autour du joueur, brouillard compris.
 * <p>
 * Membres du clan hors de portee : la minimap demande {@code mates} a l'abonnement ; le serveur envoie alors toutes les 5 s
 * ({@code CLAN_MATES}) la position des membres EN LIGNE de SON clan dans la meme dimension (jamais un joueur d'un autre clan).
 */
public final class ClanMapAccess {

    /** Sortes de case (memes valeurs que le serveur). */
    public static final int FOG = ClanMapModel.FOG, FREE = ClanMapModel.FREE, OWN = ClanMapModel.OWN, ALLY = ClanMapModel.ALLY,
          NEUTRAL = ClanMapModel.NEUTRAL, ENEMY = ClanMapModel.ENEMY, PLOT = ClanMapModel.PLOT;
    public static final int SIEGE = ClanMapModel.SIEGE, CAPTURED = ClanMapModel.CAPTURED, OUTPOST = ClanMapModel.OUTPOST,
          HALL = ClanMapModel.HALL, TOWER = ClanMapModel.TOWER;

    /** Membre du clan (pseudo, position en blocs, teinture du clan), recu a {@code at} (ms). */
    public record Mate(String name, int x, int y, int z, int dye) {
    }

    private static long watched;
    private static boolean wanted;
    private static List<Mate> mates = List.of();
    private static long matesAt;
    private static String matesDim = "";

    private ClanMapAccess() {
    }

    /** La minimap veut la carte (et les membres du clan) : abonnement entretenu toutes les 5 s. */
    public static void keep(boolean withMates) {
        wanted = true;
        long now = Util.getMillis();
        if (now - watched < 5000) {
            return;
        }
        watched = now;
        CompoundTag t = new CompoundTag();
        t.putString("op", "open");
        t.putBoolean("fresh", false);
        t.putBoolean("mates", withMates);
        RpNet.toServer(RpActions.CLAN_MAP_REQUEST, t);
    }

    /** La minimap n'en veut plus (calque coupe) : la fiche Territoire peut fermer l'abonnement. */
    public static void release() {
        wanted = false;
        watched = 0;
    }

    /** La fiche Territoire ne ferme pas l'abonnement tant que la minimap s'en sert. */
    static boolean wanted() {
        return wanted;
    }

    public static boolean ready() {
        return ClanMapModel.ready();
    }

    public static String dim() {
        return ClanMapModel.dim;
    }

    public static int version() {
        return ClanMapModel.version;
    }

    /** Chunk du coin nord-ouest de la grille et cote. */
    public static int minChunkX() {
        return ClanMapModel.cx - ClanMapModel.r;
    }

    public static int minChunkZ() {
        return ClanMapModel.cz - ClanMapModel.r;
    }

    public static int size() {
        return ClanMapModel.n;
    }

    /** Case (gx, gz) de la grille. */
    public static int at(int gx, int gz) {
        return ClanMapModel.at(gx, gz);
    }

    public static int kind(int cell) {
        return ClanMapModel.kind(cell);
    }

    /** Indice de palette du proprietaire (0 : aucun). */
    public static int ownerIndex(int cell) {
        return (cell >>> ClanMapModel.OWNER_SHIFT) & 255;
    }

    /** Teinture (0..15) du proprietaire, ou -1. */
    public static int ownerDye(int cell) {
        ClanMapModel.Owner o = ClanMapModel.owner(cell);
        return o == null ? -1 : o.color();
    }

    /** 1.12.1 (F1) : le proprietaire est-il un clan individuel (Solitaire : un joueur seul, sans nom de clan) ? */
    public static boolean ownerSolo(int cell) {
        ClanMapModel.Owner o = ClanMapModel.owner(cell);
        return o != null && o.name().isEmpty();
    }

    /** Nom (ou etiquette) du proprietaire pour une infobulle, vide si inconnu ; un Solitaire est montre par son personnage. */
    public static String ownerName(int cell) {
        ClanMapModel.Owner o = ClanMapModel.owner(cell);
        if (o == null) {
            return "";
        }
        return !o.name().isEmpty() ? o.name() : o.soloOf();
    }

    /** Couleur pleine d'une teinture (memes couleurs que la carte du Carnet). */
    public static int dyeRgb(int dye) {
        return Ui.dye(dye);
    }

    // ------------------------------------------------------------------ membres du clan (CLAN_MATES)
    public static void receiveMates(CompoundTag t) {
        List<Mate> out = new ArrayList<>();
        int dye = t.getInt("dye");
        ListTag list = t.getList("mates", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < 256; i++) {
            CompoundTag m = list.getCompound(i);
            out.add(new Mate(m.getString("name"), m.getInt("x"), m.getInt("y"), m.getInt("z"), dye));
        }
        mates = out;
        matesDim = t.getString("dim");
        matesAt = Util.getMillis();
    }

    /** Membres connus depuis moins de 12 s (sinon rien : le serveur a cesse d'envoyer). */
    public static List<Mate> mates() {
        return Util.getMillis() - matesAt > 12_000 ? List.of() : mates;
    }

    /** Dimension des membres recus (cle {@code ClanApi.dim}), vide si rien de recent. */
    public static String matesDim() {
        return Util.getMillis() - matesAt > 12_000 ? "" : matesDim;
    }

    public static void clear() {
        mates = List.of();
        matesAt = 0;
        watched = 0;
    }
}
