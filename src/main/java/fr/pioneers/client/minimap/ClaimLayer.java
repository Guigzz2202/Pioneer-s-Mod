package fr.pioneers.client.minimap;

import fr.pioneers.society.clan.client.ui.ClanMapAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.10.3 : territoires des clans sur la minimap, d'apres la carte strategique deja synchronisee ({@link ClanMapAccess}, 31 x 31 chunks
 * autour du joueur, brouillard du serveur respecte). Geometrie refaite seulement quand la carte change ({@code version}) : bandes de chunks
 * de meme proprietaire (une rangee a la fois) et traits de frontiere entre proprietaires differents, en coordonnees du monde.
 * Couleurs : chaque clan a la teinture de son clan (1.12.1 : un clan individuel en gris clair), aplat plus soutenu pour le tien, frontieres
 * soulignees d'un liseré sombre (lisibles sur la neige et le sable), les ennemis en guerre cercles de rouge, zones disputees (siege, chunk
 * saisi) pulsees en rouge.
 */
final class ClaimLayer {

    /** Bande de chunks (blocs), couleur ARGB, disputee. */
    record Band(int x0, int z0, int x1, int z1, int argb, boolean disputed) {
    }

    /** Frontiere (blocs), couleur ARGB. */
    record Edge(int x0, int z0, int x1, int z1, int argb) {
    }

    static List<Band> bands = List.of();
    static List<Edge> edges = List.of();
    static String dim = "";
    private static int seen = -1;

    private ClaimLayer() {
    }

    static void refresh() {
        if (ClanMapAccess.version() == seen) {
            return;
        }
        seen = ClanMapAccess.version();
        dim = ClanMapAccess.dim();
        if (!ClanMapAccess.ready()) {
            bands = List.of();
            edges = List.of();
            return;
        }
        int n = ClanMapAccess.size(), mx = ClanMapAccess.minChunkX(), mz = ClanMapAccess.minChunkZ();
        List<Band> b = new ArrayList<>();
        List<Edge> e = new ArrayList<>();
        for (int gz = 0; gz < n; gz++) {
            int start = -1, sig = 0, argb = 0;
            boolean disputed = false;
            for (int gx = 0; gx <= n; gx++) {
                int cell = gx < n ? ClanMapAccess.at(gx, gz) : 0;
                int s = signature(cell);
                if (start >= 0 && s != sig) {
                    b.add(new Band((mx + start) << 4, (mz + gz) << 4, (mx + gx) << 4, (mz + gz + 1) << 4, argb, disputed));
                    start = -1;
                }
                if (start < 0 && s != 0 && gx < n) {
                    start = gx;
                    sig = s;
                    argb = fill(cell);
                    disputed = (cell & (ClanMapAccess.SIEGE | ClanMapAccess.CAPTURED)) != 0;
                }
            }
            for (int gx = 0; gx < n; gx++) {
                int a = owner(ClanMapAccess.at(gx, gz));
                if (gx + 1 < n) {
                    int c = owner(ClanMapAccess.at(gx + 1, gz));
                    if (a != c) {
                        int x = (mx + gx + 1) << 4, z = (mz + gz) << 4;
                        e.add(new Edge(x, z, x, z + 16, line(ClanMapAccess.at(a != 0 ? gx : gx + 1, gz))));
                    }
                }
                if (gz + 1 < n) {
                    int c = owner(ClanMapAccess.at(gx, gz + 1));
                    if (a != c) {
                        int x = (mx + gx) << 4, z = (mz + gz + 1) << 4;
                        e.add(new Edge(x, z, x + 16, z, line(ClanMapAccess.at(gx, a != 0 ? gz : gz + 1))));
                    }
                }
            }
        }
        bands = b;
        edges = e;
    }

    /** Proprietaire d'une case (0 : libre ou brouillard). */
    private static int owner(int cell) {
        int k = ClanMapAccess.kind(cell);
        return k == ClanMapAccess.FOG || k == ClanMapAccess.FREE ? 0 : ClanMapAccess.ownerIndex(cell) << 4 | k;
    }

    /** Ce qui distingue deux bandes (proprietaire, sorte, disputee). */
    private static int signature(int cell) {
        int o = owner(cell);
        return o == 0 ? 0 : o << 1 | ((cell & (ClanMapAccess.SIEGE | ClanMapAccess.CAPTURED)) != 0 ? 1 : 0);
    }

    /** 1.12.1 (F1) : gris clair d'un clan individuel (un Solitaire : le blanc de la teinture 0 se perdait sur la neige). */
    static final int SOLO_RGB = 0xB4BCC6;

    /**
     * Couleur du proprietaire : la teinture de son clan ; un clan individuel en gris clair ; un clan a la teinture blanche est un peu
     * assombri (lisible sur la neige et le sable, le liseré sombre de la frontiere fait le reste).
     */
    static int rgb(int cell) {
        if (ClanMapAccess.ownerSolo(cell)) {
            return SOLO_RGB;
        }
        int dye = ClanMapAccess.ownerDye(cell);
        int c = dye < 0 ? 0xFFD24A : ClanMapAccess.dyeRgb(dye);
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return r > 225 && g > 225 && b > 225 ? 0xD8DEE6 : c;
    }

    private static int fill(int cell) {
        int k = ClanMapAccess.kind(cell);
        //1.12.1 (F1) : aplats plus francs (la couleur du clan se lit d'un coup d'oeil)
        int alpha = switch (k) {
            case ClanMapAccess.OWN -> 0x68;
            case ClanMapAccess.ALLY -> 0x5C;
            case ClanMapAccess.ENEMY -> 0x60;
            default -> 0x50;
        };
        return alpha << 24 | rgb(cell);
    }

    private static int line(int cell) {
        if (ClanMapAccess.kind(cell) == ClanMapAccess.ENEMY) {
            return 0xF0E5484D;
        }
        return 0xE0000000 | SurfaceShade.scale(rgb(cell), 1.15F);
    }

    static void clear() {
        bands = List.of();
        edges = List.of();
        seen = -1;
    }
}
