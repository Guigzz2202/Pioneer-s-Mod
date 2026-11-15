package fr.pioneers.kinetics.parts;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * 1.0.2-beta (Guillaume : « ajouter une interface de type slider au regulateur a boules ») : logique API Machine du regulateur a boules, portee
 * par {@link GovernorBlock.Tile} (adaptateur cinetique). Vitesse visee REGLABLE AU CURSEUR de {@link #MIN_RPM} a {@link #MAX_RPM} tr/min
 * ({@link #SETTING_TARGET}, valeur du client bornee) ; la cle passe au cran suivant de {@link GovernorBlock#TARGETS}. Au-dessus de la visee,
 * le noeud freine le reseau (raideur {@code governorStiffness}) et le bloc emet un signal de 15 (hysteresis 0,995 / 0,95, comme avant).
 * Sauvegarde : {@code rpm} (l'ancienne cle {@code target}, un cran de 0 a 7, est relue) ; client : {@code r}.
 */
public final class GovernorUnit extends MachineUnit {

    public static final int SETTING_TARGET = 0;
    public static final int MIN_RPM = 5, MAX_RPM = 120, DEFAULT_RPM = 30;
    /** Carte des mesures, a droite de la jauge de vitesse. */
    public static final Rect CARD = StandardLayout.ageCard(StandardLayout.PANEL_X, 0);

    private final GovernorBlock.Tile tile;
    private int rpm = DEFAULT_RPM;
    private boolean limiting;

    public GovernorUnit(UnitHost machine, GovernorBlock.Tile tile) {
        super(machine);
        this.tile = tile;
    }

    /** Elements de l'interface : jauge de vitesse, carte, ecran raccourci, curseur. */
    public static List<Part> parts() {
        return List.of(StandardLayout.framed("power", StandardLayout.POWER), new Part("card", CARD),
              new Part("screen", StandardLayout.sliderScreen(StandardLayout.WIDTH)), new Part("slider", StandardLayout.sliderRow(StandardLayout.WIDTH)));
    }

    public int target() {
        return rpm;
    }

    public boolean limiting() {
        return limiting;
    }

    /** Vitesse visee bornee ; vrai si elle change (sauvegarde, client, reseau). */
    public boolean setTarget(int value) {
        int v = Math.max(MIN_RPM, Math.min(MAX_RPM, value));
        if (v == rpm) {
            return false;
        }
        rpm = v;
        machine.changed();
        machine.markSync();
        tile.invalidateNetwork();
        return true;
    }

    /** Cle : cran suivant de {@link GovernorBlock#TARGETS} au-dessus de la visee (apres le dernier : le premier). */
    public void nextNotch() {
        for (int t : GovernorBlock.TARGETS) {
            if (t > rpm) {
                setTarget(t);
                return;
            }
        }
        setTarget(GovernorBlock.TARGETS[0]);
    }

    @Override
    public void tick() {
        Level level = level();
        if (level == null) {
            return;
        }
        double s = Math.abs(tile.getSpeed());
        boolean now = limiting ? s > rpm * 0.95 : s > rpm * 0.995;
        if (now != limiting) {
            limiting = now;
            level.updateNeighborsAt(pos(), machine.state().getBlock());
        }
    }

    @Override
    public void setting(int setting, int value) {
        if (setting == SETTING_TARGET) {
            setTarget(value);
        }
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.SPEED, () -> Math.round(tile.getSpeed() * 100)).add(UiValues.TARGET_RPM, () -> rpm)
              .add(UiValues.LIMITING, () -> limiting ? 1 : 0).add(UiValues.TORQUE, () -> Math.round(tile.torqueNow() * 10))
              .add(UiValues.MAX_SPEED, () -> Math.round(tile.maxSpeed())).add(UiValues.RATED_TORQUE, () -> Math.round(tile.maxTorque()));
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("rpm", rpm);
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("rpm")) {
            rpm = Math.max(MIN_RPM, Math.min(MAX_RPM, tag.getInt("rpm")));
        } else if (tag.contains("target")) {
            //Monde d'avant la 1.0.2-beta : cran de 0 a 7
            int[] t = GovernorBlock.TARGETS;
            rpm = t[Math.max(0, Math.min(t.length - 1, tag.getInt("target")))];
        } else {
            rpm = DEFAULT_RPM;
        }
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putInt("r", rpm);
    }

    @Override
    public void readClient(CompoundTag tag) {
        rpm = tag.contains("r") ? tag.getInt("r") : DEFAULT_RPM;
    }
}
