package fr.pioneers.registries;

import fr.pioneers.Pioneers;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du mod : musiques spatiales (composees pour le mod) et bruitages du voyage, des geantes gazeuses et des evenements.
 * Les fichiers sont declares dans {@code assets/pioneers/sounds.json}.
 */
public final class PiSounds {

    private PiSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Pioneers.MODID);

    // --- Musiques (une par ambiance ; chacune tire au hasard parmi plusieurs pistes) ---
    /** Astres rocheux proches (Lune, Mars, Mercure, Venus...) : plus rythmee. */
    public static final RegistryObject<SoundEvent> MUSIC_ROCKY = music("music.space.rocky");
    /** Astres lointains et glaces (lunes exterieures, Pluton...) : apaisee, aerienne. */
    public static final RegistryObject<SoundEvent> MUSIC_DISTANT = music("music.space.distant");
    /** Exoplanetes (Alpha du Centaure, TRAPPIST-1). */
    public static final RegistryObject<SoundEvent> MUSIC_EXOPLANET = music("music.space.exoplanet");
    /** Geantes gazeuses. */
    public static final RegistryObject<SoundEvent> MUSIC_GAS_GIANT = music("music.space.gas_giant");
    /** Stations orbitales. */
    public static final RegistryObject<SoundEvent> MUSIC_ORBIT = music("music.space.orbit");

    // --- Voyage ---
    public static final RegistryObject<SoundEvent> TRANSIT_BURN = sound("transit.burn");
    public static final RegistryObject<SoundEvent> TRANSIT_WARP = sound("transit.warp");
    public static final RegistryObject<SoundEvent> CAPSULE_THRUSTER = sound("capsule.thruster");
    public static final RegistryObject<SoundEvent> CAPSULE_CHUTE = sound("capsule.chute");
    public static final RegistryObject<SoundEvent> CAPSULE_TOUCHDOWN = sound("capsule.touchdown");
    public static final RegistryObject<SoundEvent> CAPSULE_ALARM = sound("capsule.alarm");
    // --- Geantes gazeuses ---
    public static final RegistryObject<SoundEvent> DRONE_HUM = sound("drone.hum");
    public static final RegistryObject<SoundEvent> DRONE_DOCK = sound("drone.dock");
    public static final RegistryObject<SoundEvent> SCANNER_PING = sound("scanner.ping");
    public static final RegistryObject<SoundEvent> POCKET_HARVEST = sound("pocket.harvest");
    // --- Evenements et multiblocs ---
    public static final RegistryObject<SoundEvent> EVENT_ALARM = sound("event.alarm");
    public static final RegistryObject<SoundEvent> METEOR_FALL = sound("event.meteor");
    public static final RegistryObject<SoundEvent> ABSORBER_HUM = sound("absorber.hum");
    public static final RegistryObject<SoundEvent> SIMULATOR_HUM = sound("simulator.hum");

    // --- Disques de juke-box : une piste chacun ---
    /** Pistes, dans l'ordre des disques (nom du fichier, duree en secondes). */
    public static final String[] DISC_TRACKS = {"regolithe", "tharsis", "lumiere_cendree", "orbite_lointaine", "neige_de_pluton", "sous_les_nuages",
                                                "station", "deux_soleils", "rouge_eternel"};
    public static final int[] DISC_SECONDS = {162, 150, 150, 156, 144, 160, 140, 150, 150};
    public static final Map<String, RegistryObject<SoundEvent>> DISCS = new LinkedHashMap<>();

    static {
        for (String track : DISC_TRACKS) {
            DISCS.put(track, sound("music_disc." + track));
        }
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(name)));
    }

    private static RegistryObject<SoundEvent> music(String name) {
        return sound(name);
    }
}
