package fr.pioneers.tech.bronze.steam;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.bronze.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/**
 * API Machine (lot 8b) : UNITE de la machine a vapeur, code de l'ancienne {@code SteamEngineTile} qui ne fait plus que la porter (source du
 * reseau de rotation et noeud de vapeur : coffre, faces, echanges {@link Steam#exchangeAll}). Energie motrice : la VAPEUR du coffre
 * ({@link PowerSource.Steam}, pretee par la tile) ; la rotation est LUE sur le noeud. 1.0.2-beta (lot 3, plus de pression) : part du couple
 * = vapeur fournie (prise {@link SteamRules#engineNeed} ; 1.0.6 : a la puissance rendue, {@link SteamRules#ENGINE_FULL_USE} mB/t a pleine
 * puissance ; 1 si le coffre la tient), reduite par l'humidite, +25 % avec un condenseur devant l'echappement. Aucune cle de sauvegarde propre (la vapeur
 * {@code steam} / {@code wet} reste a la tile).
 */
public class SteamEngineUnit extends MachineUnit {

    private final SteamEngineTile tile;
    private final PowerSource.Steam steam;
    private final PowerSource.Kinetic rotation;
    private double used, effective, delivered;
    private float stroke;

    public SteamEngineUnit(UnitHost machine, SteamEngineTile tile, PowerSource.Steam steam, PowerSource.Kinetic rotation) {
        super(machine);
        this.tile = tile;
        this.steam = steam;
        this.rotation = rotation;
    }

    public double used() {
        return used;
    }

    /** 1.0.6 : couple rendu au reseau a ce tick (N.m), donne par le reseau. */
    public void delivered(double torque) {
        delivered = Math.max(0, torque);
    }

    /** Part du couple retenue au dernier tick (0..1,25). */
    public double effective() {
        return effective;
    }

    /**
     * Part du couple pour une vapeur fournie {@code supply} (0..1) : reduite par l'humidite. 1.0.4-beta (lot 27, Guillaume : « supprimer
     * l'injecteur et le condenseur ») : plus de condenseur devant l'echappement (il donnait +25 % et rendait l'eau) : la machine marche comme
     * sans lui, la vapeur usee part a l'air.
     */
    public double effective(double supply) {
        return supply * (1 - 0.5 * steam.volume().wet());
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = pos();
        double omega = rotation.omega();
        //1.0.6 (revision de la mecanique) : vapeur a la PUISSANCE rendue (couple rendu par le reseau x vitesse) ; part du couple de demarrage
        //qui serait rendue a pleine vapeur (la courbe est proportionnelle au couple de demarrage) : prise demandee ; a l'arret, celle de 10 tr/min
        double stall = SteamEngineTile.TORQUE * fr.pioneers.tech.kinetics.KineticsConfig.sourcePower();
        double share = stall <= 0 ? 0 : delivered / stall;
        double full = effective > 0.02 ? share / effective : 1;
        double need = SteamRules.engineNeed(omega, Math.max(full, omega < 0.5 ? 1 : 0));
        double supply = steam.volume().supply(need);
        effective = effective(supply);
        used = omega > 0.5 ? steam.take(SteamRules.engineUse(omega, share)) : 0;
        //Bouffees d'echappement, deux par tour
        if (omega > 1) {
            float before = stroke;
            stroke += (float) (omega * KineticTile.DEG_PER_TICK);
            if ((int) (before / 180) != (int) (stroke / 180)) {
                Direction f = tile.facing();
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5 + f.getStepX() * 0.55, pos.getY() + 0.75, pos.getZ() + 0.5 + f.getStepZ() * 0.55,
                      2, 0.05, 0.05, 0.05, 0.06);
                level.playSound(null, pos, PiBronze.SOUND_CHUFF.get(), SoundSource.BLOCKS, 0.6F,
                      0.8F + (float) (omega / SteamEngineTile.FREE) * 0.4F);
            }
            stroke %= 3600;
            if (level.getGameTime() % 200 == 0) {
                BronzeEvents.award(level, pos, "steam_engine");
            }
        }
    }

    // ------------------------------------------------------------------
    // Interface : 0 vitesse x100, 1 couple x10, 2 vapeur du coffre (mB ; pression x1000 avant le lot 3), 3 part du couple x1000 (pression
    // utile avant), 4 consommation x100, 5 humidite x1000, 6 contenance du coffre (1.0.4-beta, lot 27 : plus de condenseur, sa valeur 6 est
    // retiree)
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.SPEED, () -> Math.round(rotation.speed() * 100)).add(UiValues.TORQUE, () -> Math.round(rotation.torque() * 10))
              .add(UiValues.STEAM, () -> Math.round(steam.stored())).add(UiValues.USEFUL, () -> Math.round(effective * 1000))
              .add(UiValues.STEAM_USE, () -> Math.round(used * 100)).add(UiValues.WET, () -> Math.round(steam.volume().wet() * 1000))
              .add(UiValues.STEAM_MAX, () -> Math.round(steam.capacity()));
    }
}
