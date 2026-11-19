package fr.pioneers.space.sky.client.mega;

import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.sky.client.SkyScene;

/**
 * 1.0.7 (« ciel grandiose ») : les megastructures dessinees en 3D, vues de leur astre (calcul pur : le jeu et le banc construisent le meme
 * tampon). Une sorte de ciel = un modele ; une megastructure sans modele garde son dessin d'avant (calcul de {@link SkyScene}).
 */
public final class MegaModels {

    private MegaModels() {
    }

    /** Vrai si la megastructure a son modele en 3D (vu de son astre). */
    public static boolean has(int i) {
        int kind = Megastructures.skyKind(i);
        return kind == SkyScene.MEGA_TETHER || kind == SkyScene.MEGA_RING;
    }

    /** Construit l'image de la megastructure dans le tampon (sans le vider), a l'echelle de son modele. */
    public static void build(int i, MegaBuffer b, MegaView v) {
        switch (Megastructures.skyKind(i)) {
            case SkyScene.MEGA_TETHER -> {
                b.scale = ElevatorModel.SCALE;
                ElevatorModel.build(b, v);
            }
            case SkyScene.MEGA_RING -> {
                b.scale = DiademModel.SCALE;
                DiademModel.build(b, v);
            }
            default -> {
            }
        }
        b.scale = 1;
        v.litOverride = -1;
    }

    /** Hauteur (degres) ou regarder pour la voir en entier (apercus, captures). */
    public static double viewPitch(int i) {
        return Megastructures.skyKind(i) == SkyScene.MEGA_TETHER ? 36 : 30;
    }
}
