package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import fr.pioneers.registries.PiSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.9 (MAJ RP) : sons de la MAJ, tous synthetises en Python ({@code tools/gc/rp/sounds_rp.py} : sons/rp/*.ogg + sounds.json + sous-titres).
 * Pas de bruit de piece : la monnaie est une ecriture.
 */
public final class RpSounds {

    // ------------------------------------------------------------------ Carnet (bloc 2)
    public static final RegistryObject<SoundEvent> PAGE = sound("rp.page");
    public static final RegistryObject<SoundEvent> LEVEL_UP = sound("rp.level_up");
    public static final RegistryObject<SoundEvent> MASTERY = sound("rp.mastery");
    public static final RegistryObject<SoundEvent> SEAL = sound("rp.seal");
    public static final RegistryObject<SoundEvent> REWRITE = sound("rp.rewrite");
    public static final RegistryObject<SoundEvent> BOOK_CLOSE = sound("rp.book_close");
    // ------------------------------------------------------------------ ruines (bloc 1)
    public static final RegistryObject<SoundEvent> SERVO_WAKE = sound("rp.servo_wake");
    public static final RegistryObject<SoundEvent> METAL_STEP = sound("rp.metal_step");
    public static final RegistryObject<SoundEvent> ALARM = sound("rp.alarm");
    public static final RegistryObject<SoundEvent> PISTON = sound("rp.piston");
    public static final RegistryObject<SoundEvent> SHUTDOWN = sound("rp.shutdown");
    public static final RegistryObject<SoundEvent> LASER_CHARGE = sound("rp.laser_charge");
    public static final RegistryObject<SoundEvent> SHRED = sound("rp.shred");
    public static final RegistryObject<SoundEvent> KEYPAD = sound("rp.keypad");
    // ------------------------------------------------------------------ economie et villages (bloc 3)
    public static final RegistryObject<SoundEvent> QUILL = sound("rp.quill");
    public static final RegistryObject<SoundEvent> PAPER_PIN = sound("rp.paper_pin");
    public static final RegistryObject<SoundEvent> LLAMA_BELLS = sound("rp.llama_bells");
    public static final RegistryObject<SoundEvent> TRUST = sound("rp.trust");
    // ------------------------------------------------------------------ clans (bloc 4)
    public static final RegistryObject<SoundEvent> BALLOT = sound("rp.ballot");
    public static final RegistryObject<SoundEvent> INTRUSION = sound("rp.intrusion");
    public static final RegistryObject<SoundEvent> SIEGE_HORN = sound("rp.siege_horn");
    public static final RegistryObject<SoundEvent> CAPTURE_UP = sound("rp.capture_up");
    public static final RegistryObject<SoundEvent> CAPTURE_DOWN = sound("rp.capture_down");
    public static final RegistryObject<SoundEvent> TRUCE = sound("rp.truce");

    private RpSounds() {
    }

    static void init() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return PiSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(name)));
    }
}
