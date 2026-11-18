package fr.pioneers.tech.bronze.machines;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.tech.bronze.AirBlastReceiver;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.steam.SteamRules;
import fr.pioneers.tech.forge.ForgeHeat;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/**
 * API Machine (lot 7) : UNITE de la soufflerie a vapeur (code de l'ancienne {@code SteamBlowerTile}, qui ne fait plus que la porter) :
 * energie motrice = la vapeur ({@link PowerSource.Steam}) ; 1.0.2-beta (lot 3, plus de pression) : avec de la vapeur en reserve et sans
 * signal redstone, elle souffle {@link #MAX_AIR} dans le four d'en face ({@link AirBlastReceiver} : creuset, haut fourneau, forge) pour
 * {@link SteamRules#BLOWER_USE} mB de vapeur par tick (moins si la reserve s'epuise). Aucune cle propre (la vapeur reste a la tile).
 */
public class SteamBlowerUnit extends MachineUnit {

    public static final double MAX_AIR = SteamRules.BLOWER_AIR;

    private final SteamBlowerTile tile;
    private final PowerSource.Steam steam;
    private double air, used;
    private int beat;

    public SteamBlowerUnit(UnitHost machine, SteamBlowerTile tile) {
        super(machine);
        this.tile = tile;
        this.steam = (PowerSource.Steam) machine.power();
    }

    public double air() {
        return air;
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos at = pos();
        air = 0;
        used = 0;
        if (steam.stored() < 1 || level.hasNeighborSignal(at)) {
            return;
        }
        AirBlastReceiver receiver = ForgeHeat.airReceiver(level, at.relative(tile.facing()));
        if (receiver == null) {
            return;
        }
        double need = SteamRules.BLOWER_USE;
        double got = steam.take(need);
        air = MAX_AIR * got / need;
        used = got;
        if (air <= 0.01) {
            return;
        }
        receiver.blast(air);
        if (++beat >= 16) {
            beat = 0;
            Direction f = tile.facing();
            level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5 - f.getStepX() * 0.2, at.getY() + 1.05, at.getZ() + 0.5 - f.getStepZ() * 0.2, 1, 0.05,
                  0.02, 0.05, 0.02);
            level.playSound(null, at, PiBronze.SOUND_CHUFF.get(), SoundSource.BLOCKS, 0.35F, 0.7F + (float) air * 0.15F);
            if (level.getGameTime() % 200 < 16) {
                BronzeEvents.award(level, at, "steam_blower");
            }
        }
    }

    /** Valeurs d'avant le lot 7 : 0 vapeur en reserve (mB ; pression x1000 avant le lot 3 du 1.0.2-beta), 1 air x1000, 2 vapeur prise x100 ; 3 contenance. */
    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.STEAM, () -> Math.round(steam.stored())).add(UiValues.BLAST, () -> Math.round(air * 1000))
              .add(UiValues.STEAM_USE, () -> Math.round(used * 100))
              .add(UiValues.STEAM_MAX, () -> Math.round(steam.capacity()));
    }

    /** Elements de l'interface v2 : manometre, carte du souffle, ecran d'etat. */
    public static List<StandardLayout.Part> parts() {
        return List.of(new StandardLayout.Part("power", new StandardLayout.Rect(StandardLayout.POWER.x() - 1, StandardLayout.POWER.y() - 1,
                    StandardLayout.POWER.w() + 2, StandardLayout.POWER.h() + 2)),
              new StandardLayout.Part("card", new StandardLayout.Rect(StandardLayout.PANEL_X, StandardLayout.TOP, StandardLayout.WIDTH - 8 - StandardLayout.PANEL_X,
                    StandardLayout.GAUGE_H)),
              new StandardLayout.Part("screen", StandardLayout.screen(StandardLayout.WIDTH)));
    }
}
