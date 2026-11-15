package fr.pioneers.client.space;

import fr.pioneers.registries.PiSounds;
import fr.pioneers.space.CapsuleEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Grondement continu des retrofusees de la capsule : son volume suit la poussee (principale et laterale).
 */
public class CapsuleThrusterSound extends AbstractTickableSoundInstance {

    private final CapsuleEntity capsule;

    public CapsuleThrusterSound(CapsuleEntity capsule) {
        super(PiSounds.CAPSULE_THRUSTER.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.capsule = capsule;
        looping = true;
        delay = 0;
        volume = 0.01F;
        x = capsule.getX();
        y = capsule.getY();
        z = capsule.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (!capsule.isAlive() || capsule.hasLanded()) {
            stop();
            return;
        }
        x = capsule.getX();
        y = capsule.getY();
        z = capsule.getZ();
        float target = capsule.isMainThrusting() ? 1.0F : capsule.isSideThrusting() ? 0.35F : 0.0F;
        volume += (target - volume) * 0.3F;
        pitch = 0.9F + volume * 0.2F;
    }
}
