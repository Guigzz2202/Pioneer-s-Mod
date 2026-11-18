package fr.pioneers.tech.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9.2 (retours de Guillaume : « un meilleur design, pas seulement la tete ») : la nacelle est un bloc plein, pose en haut d'un mat que le
 * joueur construit lui-meme ; le rotor est un bloc a part ({@link WindRotorBlock}) pose sur sa face avant. Sans rotor, rien ; avec un rotor,
 * la production maximale est celle du tier le plus faible des deux, et le disque a degager est celui du rotor. Eoliennes posees avant la
 * 1.9.2 (sans rotor) : a la premiere mise a jour, le rotor du meme tier est pose devant elles (ou lache en objet si la case est prise).
 * <p>
 * 0.20.45 : eolienne. La nacelle se pose en haut d'un mat (n'importe quels blocs) ; le rotor tourne devant elle (face avant), dans un disque
 * de {@link EnergyKind#WIND_RADIUS} blocs a garder libre (feuilles, murs, autres pales : le rendement tombe vite).
 * Facteur = altitude (10 % au niveau de la mer, plein a 100 blocs au-dessus) x degagement² x vent x air.
 * Vent : rafales lentes (0,55 a 1,15 selon l'heure et le lieu), pluie x1,25, orage x1,6. Air : 1 sur Terre, rien dans le vide ni en
 * station, 0,5 dans le Nether, sinon selon la densite de l'atmosphere (Mars 0,375, Titan 1,5).
 * <p>
 * Interface : 8 altitude x1000, 9 degagement x1000, 10 vent x1000 (meteo et air compris), 11 tier du rotor + 1 (0 : pas de rotor).
 * <p>
 * API Machine (lot 3) : la logique (mesure, rotor rendu aux eoliennes d'avant la 1.9.2) est dans {@link WindLogic} ; la tile garde le
 * rendu de l'angle du rotor (client).
 */
public class WindTurbineTile extends GeneratorTile {

    public WindTurbineTile(BlockPos pos, BlockState state) {
        super(PiEnergy.WIND_TILE.get(), pos, state, EnergyKind.WIND, a -> new WindLogic(a, fr.pioneers.core.machine.TieredMachineBlock.tierOf(state)));
    }

    // ------------------------------------------------------------------
    // Rendu : angle du rotor (client), vitesse selon le facteur recu
    // ------------------------------------------------------------------

    private float angle, lastAngle;

    @Override
    protected void clientTick() {
        lastAngle = angle;
        //De 3 a 14 degres par tick, plus lent pour les grands rotors (tier du rotor pose devant ; 1.10.1 : rotors agrandis, MKIV a 70 %)
        int rt = level == null ? tier : Math.max(0, WindLogic.rotorIn(level, worldPosition, facing()));
        float factor = clientFactor();
        float speed = factor <= 0.001F ? 0.3F : (3 + 11 * Math.min(1, factor)) * (1.15F - 0.15F * rt);
        angle = (angle + speed) % 360F;
        if (angle < lastAngle) {
            lastAngle -= 360F;
        }
    }

    public float rotorAngle(float partial) {
        return lastAngle + (angle - lastAngle) * partial;
    }
}
