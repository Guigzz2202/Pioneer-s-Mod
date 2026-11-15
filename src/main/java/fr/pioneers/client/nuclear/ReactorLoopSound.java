package fr.pioneers.client.nuclear;

import fr.pioneers.Pioneers;
import fr.pioneers.compute.ComputeView;
import fr.pioneers.industry.multi.ControllerTile;
import fr.pioneers.nuclear.ReactorView;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ambiance sonore en boucle d'un grand multibloc (0.17.1, etendue en 0.17.2) : ronronnement (fission MKI), pompes a sodium (MKII), chant
 * du champ (MKIII), grondement du tokamak (mk4), accord du stellarator (mk5), ventilation du centre de donnees, pompe du refrigerateur a
 * dilution. Le volume suit l'intensite (puissance ou charge) ; le son s'arrete quand la machine s'eteint, se defait ou que le joueur
 * s'eloigne. Lancee par les rendus (une instance par controleur). Sons de {@code sounds/nuclear/} (sounds.json) : pas d'evenement
 * enregistre necessaire cote client.
 */
public class ReactorLoopSound extends AbstractTickableSoundInstance {

    private static final Map<Long, ReactorLoopSound> PLAYING = new HashMap<>();
    private static final double RANGE = 40;

    private final BlockPos controller;
    private final String sound;
    private float target;

    private ReactorLoopSound(BlockPos controller, String sound, Vec3 center) {
        super(SoundEvent.createVariableRangeEvent(Pioneers.rl(sound)), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.controller = controller.immutable();
        this.sound = sound;
        looping = true;
        delay = 0;
        volume = 0.01F;
        x = center.x;
        y = center.y;
        z = center.z;
    }

    /** Son d'un reacteur selon sa marque (1..5). */
    static String reactorSound(int mark) {
        return "nuclear.reactor_mk" + Math.max(1, Math.min(5, mark));
    }

    /** Appele a chaque image par le rendu d'un reacteur. */
    static void update(BlockPos controller, ReactorView view, Vec3 center) {
        update(controller, reactorSound(view.mark()), view.power() > 0.01F ? Math.min(1.6F, 0.15F + 1.45F * view.power()) : 0, center);
    }

    /** Appele a chaque image par un rendu : lance la boucle si l'intensite est non nulle et qu'elle ne joue pas deja. */
    static void update(BlockPos controller, String sound, float intensity, Vec3 center) {
        long key = controller.asLong();
        ReactorLoopSound s = PLAYING.get(key);
        if (s != null && (s.isStopped() || !s.sound.equals(sound))) {
            if (!s.isStopped()) {
                s.stopNow();
            }
            PLAYING.remove(key);
            s = null;
        }
        if (s == null && intensity > 0.01F) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.position().distanceTo(center) > RANGE) {
                return;
            }
            s = new ReactorLoopSound(controller, sound, center);
            PLAYING.put(key, s);
            mc.getSoundManager().play(s);
        }
        if (s != null) {
            s.target = intensity;
            s.x = center.x;
            s.y = center.y;
            s.z = center.z;
        }
    }

    private void stopNow() {
        stop();
    }

    /** Intensite courante lue sur le controleur (null : plus rien a jouer). */
    private Float current(ControllerTile tile) {
        ReactorView rv = tile.reactorView();
        if (rv != null) {
            return rv.power();
        }
        ComputeView cv = tile.computeView();
        return cv != null ? RenderCompute.soundLevel(cv) : null;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null || !level.isLoaded(controller)) {
            stop();
            return;
        }
        Float now = level.getBlockEntity(controller) instanceof ControllerTile tile ? current(tile) : null;
        if (now == null || mc.player.position().distanceTo(new Vec3(x, y, z)) > RANGE + 8) {
            stop();
            return;
        }
        float want = now > 0.01F ? target : 0;
        volume += (want - volume) * 0.08F;
        ReactorView rv = level.getBlockEntity(controller) instanceof ControllerTile tile ? tile.reactorView() : null;
        pitch = rv != null ? 0.85F + 0.25F * Math.min(1, rv.power()) + (rv.scrammed() ? -0.1F : 0) : 0.9F + 0.15F * Math.min(1, now);
        if (want == 0 && volume < 0.01F) {
            stop();
        }
    }
}
