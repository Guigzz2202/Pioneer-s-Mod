package fr.pioneers.common;

import fr.pioneers.Pioneers;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Cles de traduction du mod (les textes sont dans assets/pioneers/lang).
 */
public enum PiLang {
    CHAMBER_PORT_MODE("chamber", "port_mode"),
    // Etats du trou noir
    // Descriptions des blocs (infobulles)
    // Erreurs de formation
    FORMATION_MISSING_EMITTER("solar_simulator", "formation.missing_emitter"),
    FORMATION_MISSING_RING("solar_simulator", "formation.missing_ring"),
    FORMATION_MISPLACED_EMITTER("solar_simulator", "formation.misplaced_emitter"),
    FORMATION_ABSORBER_TOP("solar_absorber", "formation.top"),
    FORMATION_ABSORBER_MISPLACED("solar_absorber", "formation.misplaced_collector"),
    // Interface
    // Gravity Compressor
    // Espace
    CAPSULE("rocket", "capsule"),
    CAPSULE_HINT("rocket", "capsule.hint"),
    ROCKET_ORBIT("rocket", "orbit"),
    ROCKET_HUD_ALTITUDE("rocket", "hud.altitude"),
    ROCKET_HUD_CHOOSE("rocket", "hud.choose"),
    MAP_TITLE("rocket", "map.title"),
    MAP_REACHABLE("rocket", "map.reachable"),
    MAP_LOCKED("rocket", "map.locked"),
    MAP_HERE("rocket", "map.here"),
    MAP_GRAVITY("rocket", "map.gravity"),
    MAP_NEEDS_TIER("rocket", "map.needs_tier"),
    MAP_VIEW_ONLY("rocket", "map.view_only"),
    ASSEMBLER_BUILD("assembler", "build"),
    ASSEMBLER_CANCEL("assembler", "cancel"),
    ASSEMBLER_PROGRESS("assembler", "progress"),
    DESCRIPTION_SPACE_SEEDS("description", "space_seeds"),
    DESCRIPTION_BLUEPRINT_CARGO("description", "rocket_blueprint_cargo"),
    MAP_LOGISTICS("rocket", "map.logistics"),
    MAP_LOGISTICS_EMPTY("rocket", "map.logistics_empty"),
    MAP_DEPART("rocket", "map.depart"),
    MAP_HERE_SHORT("rocket", "map.here_short"),
    GREENHOUSE_FORMED("greenhouse", "formed"),
    GREENHOUSE_NOT_FORMED("greenhouse", "not_formed"),
    GREENHOUSE_NO_ROBOT("greenhouse", "no_robot"),
    GREENHOUSE_WRONG_PLANET("greenhouse", "wrong_planet"),
    GREENHOUSE_PLANTS("greenhouse", "plants"),
    GREENHOUSE_BATTERY("greenhouse", "battery"),
    GREENHOUSE_HARVESTS("greenhouse", "harvests"),
    GREENHOUSE_SEEDS("greenhouse", "seeds"),
    GREENHOUSE_OUTPUT("greenhouse", "output"),
    DESCRIPTION_BLUEPRINT("description", "rocket_blueprint"),
    AUTO_PAD_PROBES("auto_pad", "probes"),
    ANTENNA_EXPECTED("antenna", "expected"),
    ANTENNA_RATE("antenna", "rate"),
    ANTENNA_LOSS("antenna", "loss"),
    ANTENNA_MIXED("antenna", "mixed"),
    ANTENNA_IDLE("antenna", "idle"),
    ANTENNA_NO_CABLE("antenna", "no_cable"),
    ASSEMBLER_REPEAT_ON("assembler", "repeat_on"),
    ASSEMBLER_REPEAT_OFF("assembler", "repeat_off"),
    ROCKET_NO_DESTINATION("rocket", "no_destination"),
    ROCKET_NEEDS_PAD("rocket", "needs_pad"),
    ROCKET_NOT_FUELED("rocket", "not_fueled"),
    ROCKET_LIFTOFF("rocket", "liftoff"),
    ROCKET_HUD_FUEL("rocket", "hud.fuel"),
    ROCKET_HUD_LAUNCH("rocket", "hud.launch"),
    ROCKET_HUD_COUNTDOWN("rocket", "hud.countdown"),
    GUI_ENERGY_STORED("gui", "energy_stored");

    private final String key;

    PiLang(String type, String path) {
        this.key = Util.makeDescriptionId(type, Pioneers.rl(path));
    }

    public String getTranslationKey() {
        return key;
    }

    /** Texte traduit ; les arguments {@link Component} gardent leur style, les autres sont affiches tels quels. */
    public MutableComponent translate(Object... args) {
        return Component.translatable(key, args);
    }

    /** Texte traduit d'une couleur. */
    public MutableComponent translateColored(ChatFormatting color, Object... args) {
        return translate(args).withStyle(color);
    }
}
