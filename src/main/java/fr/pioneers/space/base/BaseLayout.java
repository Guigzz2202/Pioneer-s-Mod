package fr.pioneers.space.base;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Plans des bases spatiales construites (1.0, lot 4, valides par Guillaume le 24/10/2026) : serre, garage du rover, baie de drones.
 * Multiblocs « construits litteralement » en carcasses, cadres et toles du tier (le plus faible donne le tier), comme l'industrie.
 * <p>
 * Repere local : x vers la droite du joueur, z vers l'avant (loin de lui), y vers le haut ; origine = centre du sol (dalle y = 0).
 * Le controleur regarde le joueur. Meme geometrie en Python : {@code tools/gc/core/base_shapes.py} (apercus).
 */
public final class BaseLayout {

    private BaseLayout() {
    }

    /** Ce qu'une case attend. Les cases absentes du plan ne sont pas verifiees. */
    public enum Role {
        /** Carcasse industrielle (tier) ; les ports de la base y sont acceptes. */
        CASING,
        /** Cadre de structure (tier). */
        FRAME,
        /** Tole de structure (tier) ; verre et ports acceptes. */
        SHEET,
        /** Verre industriel. */
        GLASS,
        /** Doit rester vide (air ou bloc remplacable). */
        FREE,
        /** Bac de substrat hydroponique (serre). */
        BED,
        /** Station de recharge du robot (serre). */
        PAD,
        /** Dock d'un rover (garage). */
        DOCK,
        /** Radar de prospection. */
        RADAR,
        /** Aire d'atterrissage d'un drone (baie). */
        DRONE_PAD,
        /** Le controleur. */
        CONTROLLER
    }

    public enum Kind {
        GREENHOUSE, GARAGE, DRONE_BAY;

        /** Tailles du plan : diametre du dome, nombre de travees, nombre d'aires. */
        public static final int SIZES = 4;
    }

    /** Diametre de la serre (9, 11, 13, 15). */
    public static int domeDiameter(int size) {
        return 9 + 2 * size;
    }

    /** Plan d'une base : case -> role (ordre : du bas vers le haut). */
    public static Map<BlockPos, Role> cells(Kind kind, int size) {
        Map<BlockPos, Role> out = new LinkedHashMap<>();
        switch (kind) {
            case GREENHOUSE -> greenhouse(out, size);
            case GARAGE -> garage(out, size + 1);
            case DRONE_BAY -> droneBay(out, size + 1);
        }
        return out;
    }

    /** Position locale du controleur. */
    public static BlockPos controller(Kind kind, int size) {
        return switch (kind) {
            case GREENHOUSE -> new BlockPos(0, 0, -(domeDiameter(size) / 2));
            case GARAGE -> new BlockPos(-2 * (size + 1), 1, 0);
            case DRONE_BAY -> new BlockPos(bayLength(size + 1) - 3 - bayLength(size + 1) / 2, 1, 1);
        };
    }

    /**
     * Direction locale vers laquelle regarde le controleur : 0 = -z (vers le joueur), 1 = -x (pignon gauche du garage).
     */
    public static boolean controllerOnLeftGable(Kind kind) {
        return kind == Kind.GARAGE;
    }

    // ------------------------------------------------------------------
    // Serre : socle rond, bacs dans le sol (rangees et allees du robot), station au centre, dome de verre a 4 nervures en cadres
    // ------------------------------------------------------------------

    private static void greenhouse(Map<BlockPos, Role> out, int size) {
        int d = domeDiameter(size), c = d / 2;
        double r = d / 2.0, rd = r - 1.0;
        for (int dx = -c; dx <= c; dx++) {
            for (int dz = -c; dz <= c; dz++) {
                double rho = Math.hypot(dx, dz);
                if (rho > r - 0.2) {
                    continue;
                }
                Role floor = Role.CASING;
                if (rho <= rd - 1.2 && !(dx == 0 && dz == 0) && dx != 0 && dz != 0 && Math.floorMod(dz, 3) != 0) {
                    floor = Role.BED;
                }
                out.put(new BlockPos(dx, 0, dz), floor);
            }
        }
        out.put(new BlockPos(0, 0, -c), Role.CONTROLLER);
        int top = (int) Math.ceil(rd) + 1;
        for (int y = 1; y <= top; y++) {
            for (int dx = -c; dx <= c; dx++) {
                for (int dz = -c; dz <= c; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz + (y - 0.5) * (y - 0.5));
                    if (dist > rd - 1.0 && dist <= rd) {
                        out.put(new BlockPos(dx, y, dz), y == 1 || dx == 0 || dz == 0 ? Role.FRAME : Role.GLASS);
                    } else if (dist <= rd - 1.0) {
                        out.put(new BlockPos(dx, y, dz), dx == 0 && dz == 0 && y == 1 ? Role.PAD : Role.FREE);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Garage du rover : dalle, poteaux et poutres en cadres, bardage et toit en toles, une porte et un dock par travee, mat radar
    // ------------------------------------------------------------------

    private static void garage(Map<BlockPos, Role> out, int bays) {
        int hl = 2 * bays, height = 5;
        for (int dx = -hl; dx <= hl; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                out.put(new BlockPos(dx, 0, dz), Role.CASING);
            }
        }
        for (int y = 1; y < height; y++) {
            for (int dx = -hl; dx <= hl; dx++) {
                int ix = dx + hl;
                for (int dz = -3; dz <= 3; dz++) {
                    boolean edgeX = Math.abs(dx) == hl, edgeZ = Math.abs(dz) == 3;
                    Role role;
                    if (edgeX || edgeZ) {
                        boolean post = edgeZ && (ix % 4 == 0 || edgeX);
                        if (post) {
                            role = Role.FRAME;
                        } else if (dz == -3 && ix % 4 != 0 && y <= 3) {
                            role = Role.FREE;                                   //porte de la travee
                        } else if (dz == 3 && y == 3 && ix % 4 == 2) {
                            role = Role.GLASS;
                        } else if (y == height - 1) {
                            role = Role.FRAME;                                  //poutres
                        } else {
                            role = Role.SHEET;
                        }
                    } else if (y == height - 1) {
                        role = Role.SHEET;                                      //toit
                    } else {
                        role = y == 1 && dz == 0 && ix % 4 == 2 ? Role.DOCK : Role.FREE;
                    }
                    out.put(new BlockPos(dx, y, dz), role);
                }
            }
        }
        out.put(new BlockPos(-hl, 1, 0), Role.CONTROLLER);
        for (int y = height; y < height + 3; y++) {
            out.put(new BlockPos(hl - 2, y, 0), Role.FRAME);
        }
        out.put(new BlockPos(hl - 2, height + 3, 0), Role.RADAR);
    }

    // ------------------------------------------------------------------
    // Baie de drones : pont (bordure en cadres), une aire et un reservoir par drone, cabine vitree avec le controleur, radar
    // ------------------------------------------------------------------

    static int bayLength(int pads) {
        return 3 * pads + 6;
    }

    private static void droneBay(Map<BlockPos, Role> out, int pads) {
        int len = bayLength(pads), half = len / 2;
        //iz : 0 (fond) .. 8 (devant, cote joueur) ; z local = iz' - 4 avec iz' = 8 - iz
        for (int ix = 0; ix < len; ix++) {
            for (int iz = 0; iz < 9; iz++) {
                boolean border = ix == 0 || ix == len - 1 || iz == 0 || iz == 8;
                out.put(bay(ix, 0, iz, half), border ? Role.FRAME : Role.CASING);
            }
        }
        for (int p = 0; p < pads; p++) {
            for (int ix = 2 + 3 * p; ix <= 3 + 3 * p; ix++) {
                for (int iz = 5; iz <= 6; iz++) {
                    out.put(bay(ix, 1, iz, half), Role.DRONE_PAD);
                }
            }
            int x0 = 2 + 3 * p;
            for (int y = 1; y <= 4; y++) {
                for (int ix = x0; ix <= x0 + 1; ix++) {
                    for (int iz = 1; iz <= 2; iz++) {
                        out.put(bay(ix, y, iz, half), y == 1 || y == 4 ? Role.FRAME : Role.SHEET);
                    }
                }
            }
        }
        int cx0 = len - 4;
        for (int ix = cx0; ix <= cx0 + 2; ix++) {
            for (int iz = 1; iz <= 3; iz++) {
                for (int y = 1; y <= 3; y++) {
                    boolean edge = ix == cx0 || ix == cx0 + 2 || iz == 1 || iz == 3 || y == 3;
                    Role role;
                    if (!edge) {
                        role = Role.FREE;
                    } else if ((ix == cx0 || ix == cx0 + 2) && (iz == 1 || iz == 3)) {
                        role = Role.FRAME;
                    } else if (y == 2) {
                        role = Role.GLASS;
                    } else {
                        role = Role.SHEET;
                    }
                    out.put(bay(ix, y, iz, half), role);
                }
            }
        }
        out.put(bay(cx0 + 1, 1, 3, half), Role.CONTROLLER);
        out.put(bay(cx0 + 1, 4, 2, half), Role.RADAR);
    }

    private static BlockPos bay(int ix, int y, int iz, int half) {
        return new BlockPos(ix - half, y, 4 - iz);
    }
}
