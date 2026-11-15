package fr.pioneers.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * Touches du mod (modifiables dans Options > Commandes).
 */
public final class PiKeys {

    private PiKeys() {
    }

    public static final String CATEGORY = "key.categories.pioneers";

    /** Ouvre la carte du systeme solaire (consultation, ou choix de destination en orbite). */
    public static final KeyMapping SOLAR_MAP = new KeyMapping("key.pioneers.solar_map", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    /** Capsule : deploie le parachute. */
    public static final KeyMapping PARACHUTE = new KeyMapping("key.pioneers.parachute", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    /** Capsule : pilote automatique. */
    public static final KeyMapping AUTOPILOT = new KeyMapping("key.pioneers.autopilot", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    /** Ouvre l'arbre des recherches (consultation, n'importe ou). */
    public static final KeyMapping RESEARCH = new KeyMapping("key.pioneers.research", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    /** Robotique : menu des ordres ; Maj : nouvel ordre vers le bloc vise ; pendant une visee : choisir le bloc. */
    public static final KeyMapping ROBOTICS = new KeyMapping("key.pioneers.robotics", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
    /** 0.20.15 : ecran des modules de l'equipement modulaire porte (marche / arret). */
    /** 1.4.2 : le guide de Pioneer's (plus d'objet guide). */
    public static final KeyMapping GUIDE = new KeyMapping("key.pioneers.guide", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    /** 1.0.3-beta (lot 2) : etape du Periple affichee a l'ecran : sa page du guide ; Maj + touche : sa recette dans JEI. */
    public static final KeyMapping JOURNEY = new KeyMapping("key.pioneers.journey", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    /** 1.0.5-beta (lot 5) : R par defaut (G est au rappel des animaux, decision 8 de la specification « Le monde vivant »). */
    public static final KeyMapping GEAR = new KeyMapping("key.pioneers.gear", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    /**
     * 1.0.5-beta (lot 5) : RAPPEL des animaux dont on est le maitre (chiens, chats, montures). Appui court : tous ; maintien : roue pour en
     * choisir un ({@code client/pet/PetRecallClient}).
     */
    public static final KeyMapping PET_RECALL = new KeyMapping("key.pioneers.pet_recall", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    /** 1.0.3-beta (lot 9) : mode du jetpack a hydrogene porte (poussee seule / vol stationnaire). */
    public static final KeyMapping JETPACK_MODE = new KeyMapping("key.pioneers.jetpack_mode", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    /**
     * API Machine (lot 3) : ouvre / ferme le carre du selecteur de recette dans l'interface d'une machine. Ctrl + R par defaut : aucune
     * lettre seule (la recherche du carre les prend toutes, E ferme l'inventaire, R / U sont a JEI sur une case survolee).
     */
    public static final KeyMapping RECIPE_SQUARE = new KeyMapping("key.pioneers.recipe_square", KeyConflictContext.GUI, KeyModifier.CONTROL,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    // 1.10.3 : minimap
    /** Affiche ou masque la minimap. */
    public static final KeyMapping MINIMAP = new KeyMapping("key.pioneers.minimap", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    /** Carte plein ecran (memes calques, deplacement, zoom, liste des waypoints). */
    public static final KeyMapping WORLD_MAP = new KeyMapping("key.pioneers.world_map", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    /** Nouveau waypoint a la position du joueur. */
    public static final KeyMapping WAYPOINT = new KeyMapping("key.pioneers.waypoint", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);
    public static final KeyMapping MINIMAP_ZOOM_IN = new KeyMapping("key.pioneers.minimap_zoom_in", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, CATEGORY);
    public static final KeyMapping MINIMAP_ZOOM_OUT = new KeyMapping("key.pioneers.minimap_zoom_out", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, CATEGORY);
    /** Reglages de la minimap (sans touche par defaut : aussi depuis la carte plein ecran). */
    public static final KeyMapping MINIMAP_SETTINGS = new KeyMapping("key.pioneers.minimap_settings", KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
}
