package fr.pioneers.space.sky.client.mega;

import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.sky.client.SkyScene;

/**
 * 1.0.7 (« ciel grandiose ») : les dix megastructures dessinees en 3D, vues de leur astre (calcul pur : le jeu et le banc construisent le
 * meme tampon). Une megastructure = un modele, choisi par son identifiant : Ascenseur de Mars, Diademe, Grand Forage, Cite des nuages,
 * Anneau-Rotor, Boucle, Descente, Coeur froid, Essaim (autour du Soleil, dans les ciels du systeme), Observatoire du Halo (la nuit, au
 * point oppose au Soleil). 1.0.7 (lot 11) : Couronne de Proxima (autour de l'etoile, vue de Proxima b) et Collier des Marees (d'un horizon a
 * l'autre, vu de TRAPPIST-1e), dessines dans le ciel des exoplanetes.
 */
public final class MegaModels {

    /** Modeles, dans l'ordre de leurs identifiants. */
    static final int ELEVATOR = 0, DIADEM = 1, DRILL = 2, CITY = 3, ROTOR = 4, LOOP = 5, DESCENT = 6, COLD = 7, SWARM = 8, HALO = 9, CROWN = 10,
          NECKLACE = 11, CAGE = 12, WELL = 13, PENROSE = 14;
    private static final String[] IDS = {"mars_elevator", "mercury_diadem", "venus_great_drill", "jupiter_cloud_city", "saturn_ring_rotor",
                                         "uranus_loop", "neptune_descent", "pluto_cold_heart", "sun_swarm", "halo_observatory", "proxima_crown",
                                         "tidal_necklace", "pulsar_cage", "crystal_well", "penrose_ring"};
    private static int[] model;

    private MegaModels() {
    }

    /** Modele de la megastructure (-1 : aucun). */
    static int model(int i) {
        if (model == null) {
            int[] m = new int[Megastructures.COUNT];
            for (int k = 0; k < m.length; k++) {
                m[k] = -1;
                for (int j = 0; j < IDS.length; j++) {
                    if (IDS[j].equals(Megastructures.id(k))) {
                        m[k] = j;
                    }
                }
            }
            model = m;
        }
        return i < 0 || i >= model.length ? -1 : model[i];
    }

    /** Vrai si la megastructure a son modele en 3D. */
    public static boolean has(int i) {
        return model(i) >= 0;
    }

    /** Vraie pour l'Essaim : visible autour du Soleil dans tous les ciels du systeme (pas seulement de son astre). */
    public static boolean everywhere(int i) {
        return model(i) == SWARM;
    }

    /** Vraie pour les geantes : dessinee dans leur ciel propre ({@code ExoSkyEffects}), pas par {@link SkyScene}. */
    public static boolean giant(int i) {
        int m = model(i);
        return m == CITY || m == ROTOR || m == LOOP || m == DESCENT;
    }

    /** 1.0.7 (lot 11) : vraie pour les exoplanetes : dessinee dans leur ciel propre, a la lumiere de leur etoile. */
    public static boolean exo(int i) {
        int m = model(i);
        return m == CROWN || m == NECKLACE || m == CAGE || m == WELL || m == PENROSE;
    }

    /** Construit l'image de la megastructure dans le tampon (sans le vider), a l'echelle de son modele. */
    public static void build(int i, MegaBuffer b, MegaView v) {
        switch (model(i)) {
            case ELEVATOR -> {
                b.scale = ElevatorModel.SCALE;
                ElevatorModel.build(b, v);
            }
            case DIADEM -> {
                b.scale = DiademModel.SCALE;
                DiademModel.build(b, v);
            }
            case DRILL -> {
                b.scale = DrillModel.SCALE;
                DrillModel.build(b, v);
            }
            case CITY -> {
                b.scale = CloudCityModel.SCALE;
                CloudCityModel.build(b, v);
            }
            case ROTOR -> {
                b.scale = RingRotorModel.SCALE;
                RingRotorModel.build(b, v);
            }
            case LOOP -> {
                b.scale = LoopModel.SCALE;
                LoopModel.build(b, v);
            }
            case DESCENT -> {
                b.scale = DescentModel.SCALE;
                DescentModel.build(b, v);
            }
            case COLD -> {
                b.scale = ColdHeartModel.SCALE;
                ColdHeartModel.build(b, v);
            }
            case SWARM -> {
                b.scale = SwarmModel.SCALE;
                SwarmModel.build(b, v);
            }
            case HALO -> {
                b.scale = HaloModel.SCALE;
                HaloModel.build(b, v);
            }
            case CROWN -> {
                b.scale = CrownModel.SCALE;
                CrownModel.build(b, v);
            }
            case NECKLACE -> {
                b.scale = NecklaceModel.SCALE;
                NecklaceModel.build(b, v);
            }
            case CAGE -> {
                b.scale = CageModel.SCALE;
                CageModel.build(b, v);
            }
            case WELL -> {
                b.scale = WellModel.SCALE;
                WellModel.build(b, v);
            }
            case PENROSE -> {
                b.scale = PenroseModel.SCALE;
                PenroseModel.build(b, v);
            }
            default -> {
            }
        }
        b.scale = 1;
        v.litOverride = -1;
    }

    /** Azimut (degres, 0 = nord) ou regarder pour la voir (apercus, captures) ; v : lumiere du moment (Essaim, Halo). */
    public static double viewYaw(int i, MegaView v) {
        return switch (model(i)) {
            case SWARM, CROWN, CAGE, WELL, PENROSE -> Math.toDegrees(Math.atan2(v.lx, -v.lz));
            case NECKLACE -> NecklaceModel.topYaw();
            case HALO -> Math.toDegrees(Math.atan2(-v.lx, v.lz + 0.25));
            default -> Megastructures.skyParams(i)[0];
        };
    }

    /** Hauteur (degres) ou regarder pour la voir en entier (apercus, captures). */
    public static double viewPitch(int i, MegaView v) {
        return switch (model(i)) {
            case ELEVATOR -> 36;
            case DRILL, COLD -> 18;
            case CITY -> 12;
            case LOOP -> 45;
            case DESCENT -> 40;
            case SWARM -> Math.max(5, Math.min(70, Math.toDegrees(Math.asin(v.ly))));
            case HALO -> Math.max(10, Math.min(70, Math.toDegrees(Math.asin(Math.max(0, -v.ly)))));
            case CROWN -> Math.max(8, Math.min(60, Math.toDegrees(Math.asin(v.ly)) + 4));
            case CAGE, PENROSE -> Math.max(8, Math.min(65, Math.toDegrees(Math.asin(v.ly)) + 3));
            case WELL -> Math.max(10, Math.min(70, Math.toDegrees(Math.asin(v.ly)) + 8));
            case NECKLACE -> 50;
            default -> 30;
        };
    }
}
