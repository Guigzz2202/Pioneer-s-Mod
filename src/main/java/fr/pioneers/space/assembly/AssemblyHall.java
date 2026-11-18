package fr.pioneers.space.assembly;

import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.core.recipes.AssemblyStation;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 1.9.2 (retours de Guillaume : « les tables d'assemblage robotique et de fusees devraient etre des multiblocs ») : plans des deux
 * multiblocs d'assemblage, construits litteralement en carcasses, cadres, toles et verre industriels (une seule taille chacun) :
 * <ul>
 *     <li>{@link Kind#ROBOTICS} : baie d'assemblage robotique, atelier vitre de 7 x 6 x 9 ; un convoyeur amene les pieces du fond vers
 *     la borne de recharge du milieu, ou quatre bras articules montent l'unite ;</li>
 *     <li>{@link Kind#ROCKET} : hall d'integration des fusees, tour vitree de 9 x 15 x 9 a ceintures de cadres et grande porte de verre ;
 *     la fusee s'assemble debout sur les dalles de lancement du sol, le long d'une tour de service, sous deux ponts roulants.</li>
 * </ul>
 * Repere local : x a droite du joueur, z vers l'avant (loin de lui), y vers le haut ; origine = centre du sol (y = 0). Les plans sont
 * symetriques en x. Le controleur est au milieu du bas de la facade et regarde le joueur. Interieur libre (non verifie, sauf la piece
 * metier) : la machinerie animee y est dessinee par le controleur.
 */
public final class AssemblyHall {

    private AssemblyHall() {
    }

    /** Ce qu'une case attend. */
    public enum Role {
        /** Carcasse industrielle (tier) ; ports industriels acceptes. */
        CASING,
        /** Cadre de structure (tier). */
        FRAME,
        /** Tole (tier) ; verre et ports acceptes. */
        SHEET,
        /** Verre industriel (tier). */
        GLASS,
        /** Doit rester vide (air ou bloc remplacable). */
        FREE,
        /** Bloc metier : borne de recharge (baie robotique) ou dalle de lancement (hall des fusees). */
        WORK,
        /** Le controleur. */
        CONTROLLER
    }

    public enum Kind {
        ROBOTICS(AssemblyStation.ROVER, IndustryStyle.DIGITAL, 3, 4, 5, 8_000_000L),
        ROCKET(AssemblyStation.ROCKET, IndustryStyle.MECHANICAL, 4, 4, 14, 16_000_000L);

        public final AssemblyStation station;
        public final IndustryStyle style;
        /** Demi-largeur (x), demi-profondeur (z) et hauteur du toit (y) du plan. */
        public final int halfX, halfZ, roof;
        /** Reserve d'energie du controleur (FE), comme les anciennes tables. */
        public final long energy;

        Kind(AssemblyStation station, IndustryStyle style, int halfX, int halfZ, int roof, long energy) {
            this.station = station;
            this.style = style;
            this.halfX = halfX;
            this.halfZ = halfZ;
            this.roof = roof;
            this.energy = energy;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Interieur libre (px) : largeur (x), hauteur, profondeur (z). */
        public int innerWidth() {
            return 16 * (2 * halfX - 1);
        }

        public int innerHeight() {
            return 16 * (roof - 1);
        }

        public int innerDepth() {
            return 16 * (2 * halfZ - 1);
        }
    }

    /** Position locale du controleur. */
    public static BlockPos controller(Kind kind) {
        return new BlockPos(0, 1, -kind.halfZ);
    }

    /** Position monde d'une case locale (origine = centre du sol, {@code forward} = +z local). */
    public static BlockPos at(BlockPos origin, Direction forward, int x, int y, int z) {
        Direction right = forward.getClockWise();
        return origin.offset(right.getStepX() * x + forward.getStepX() * z, y, right.getStepZ() * x + forward.getStepZ() * z);
    }

    /** Plan : case -> role (du bas vers le haut). */
    public static Map<BlockPos, Role> cells(Kind kind) {
        Map<BlockPos, Role> out = new LinkedHashMap<>();
        if (kind == Kind.ROBOTICS) {
            robotics(out);
        } else {
            rocket(out);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Baie d'assemblage robotique : 7 x 6 x 9
    // ------------------------------------------------------------------

    private static void robotics(Map<BlockPos, Role> out) {
        int hx = 3, hz = 4, roof = 5;
        for (int y = 0; y <= roof; y++) {
            for (int z = -hz; z <= hz; z++) {
                for (int x = -hx; x <= hx; x++) {
                    boolean ex = Math.abs(x) == hx, ez = Math.abs(z) == hz;
                    Role role;
                    if (y == 0) {
                        role = Role.CASING;                                             //dalle
                    } else if (y == roof) {
                        //toit : rive en cadres, verriere au-dessus de la borne et du convoyeur
                        role = ex || ez ? Role.FRAME : Math.abs(x) <= 1 && z >= -2 && z <= 2 ? Role.GLASS : Role.SHEET;
                    } else if (!ex && !ez) {
                        //interieur ; la borne de recharge au milieu du sol, un peu vers la facade
                        role = y == 1 && x == 0 && z == -1 ? Role.WORK : Role.FREE;
                    } else if (ex && ez || ex && z == 0) {
                        role = Role.FRAME;                                              //poteaux d'angle et de travee
                    } else if (y == 1 || y == 4) {
                        role = Role.SHEET;                                              //plinthe et bandeau
                    } else if (ez && z > 0) {
                        role = Role.SHEET;                                              //fond (arrivee du convoyeur)
                    } else {
                        role = Role.GLASS;                                              //vitrages des flancs et de la facade
                    }
                    out.put(new BlockPos(x, y, z), role);
                }
            }
        }
        out.put(controller(Kind.ROBOTICS), Role.CONTROLLER);
    }

    // ------------------------------------------------------------------
    // Hall d'integration des fusees : 9 x 15 x 9
    // ------------------------------------------------------------------

    private static void rocket(Map<BlockPos, Role> out) {
        int h = 4, roof = 14;
        for (int y = 0; y <= roof; y++) {
            for (int z = -h; z <= h; z++) {
                for (int x = -h; x <= h; x++) {
                    boolean ex = Math.abs(x) == h, ez = Math.abs(z) == h;
                    Role role;
                    if (y == 0) {
                        role = Math.abs(x) <= 1 && Math.abs(z) <= 1 ? Role.WORK : Role.CASING;      //sol, dalles de lancement au milieu
                    } else if (y == roof) {
                        role = ex || ez ? Role.FRAME : Math.abs(x) <= 1 && Math.abs(z) <= 1 ? Role.GLASS : Role.SHEET;
                    } else if (!ex && !ez) {
                        role = Role.FREE;
                    } else {
                        int p = ex ? z : x;                                                 //position le long de la face
                        boolean door = z == -h && !ex && Math.abs(x) <= 1 && y >= 2 && y <= 12;
                        if (ex && ez) {
                            role = Role.FRAME;                                              //colonnes d'angle
                        } else if (door) {
                            role = Role.GLASS;                                              //grande porte vitree
                        } else if (y == 5 || y == 9 || y == 13) {
                            role = Role.FRAME;                                              //ceintures
                        } else if (y == 1 || Math.abs(p) >= 3 || z == -h) {
                            role = Role.SHEET;                                              //plinthe, piles de toles, piedroits de la porte
                        } else {
                            role = Role.GLASS;                                              //baies vitrees
                        }
                    }
                    out.put(new BlockPos(x, y, z), role);
                }
            }
        }
        out.put(controller(Kind.ROCKET), Role.CONTROLLER);
    }
}
