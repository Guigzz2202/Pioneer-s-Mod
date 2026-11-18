package fr.pioneers.space.simulation;

import fr.pioneers.space.SolarSystem.Body;
import java.util.Locale;

/**
 * Les huit planetes du systeme solaire (et seulement elles) : on les synthetise en miniature a partir des donnees,
 * on leur donne une atmosphere au Gravity Compressor, puis on les fait tourner dans le Simulateur du systeme solaire.
 */
public enum SimulatedPlanet {
    MERCURY(Body.MERCURY),
    VENUS(Body.VENUS),
    EARTH(Body.EARTH),
    MARS(Body.MARS),
    JUPITER(Body.JUPITER),
    SATURN(Body.SATURN),
    URANUS(Body.URANUS),
    NEPTUNE(Body.NEPTUNE);

    public final Body body;

    SimulatedPlanet(Body body) {
        this.body = body;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int color() {
        return body.color;
    }

    /** Distance au soleil dans le simulateur (0..1), pour le rendu des orbites. */
    public float orbit() {
        return (ordinal() + 1) / (float) values().length;
    }
}
