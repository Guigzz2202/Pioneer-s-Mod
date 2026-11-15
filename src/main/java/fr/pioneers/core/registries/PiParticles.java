package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 0.20.26 : particules du mod (dessin : {@code client/particle}, textures : {@code assets/pioneers/particles/*.json}). */
public final class PiParticles {

    private PiParticles() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Pioneers.MODID);

    /** Bouffee du panache de vapeur des tours de refroidissement : grosse, blanche, monte, s'elargit et se dissipe. */
    public static final RegistryObject<SimpleParticleType> STEAM_PLUME = PARTICLES.register("steam_plume", () -> new SimpleParticleType(true));
}
