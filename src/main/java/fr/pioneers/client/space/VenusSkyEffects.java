package fr.pioneers.client.space;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Ciel de Venus : atmosphere epaisse jaune-orange, brouillard dense (on voit a peine au loin), pas de pluie.
 * Les nuages restent (le plafond nuageux de Venus) mais aucun coucher de soleil n'est visible a travers.
 */
public class VenusSkyEffects extends DimensionSpecialEffects {

    public VenusSkyEffects() {
        super(192, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        //Meme la nuit, la brume garde une lueur orangee (chaleur du sol)
        return color.multiply(brightness * 0.85 + 0.15, brightness * 0.8 + 0.1, brightness * 0.75 + 0.05);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return true;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }
}
