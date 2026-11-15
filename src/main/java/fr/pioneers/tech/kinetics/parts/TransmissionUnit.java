package fr.pioneers.tech.kinetics.parts;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Part;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.2-beta (Guillaume : « ajouter une interface de type slider a la boite de vitesses ») : logique API Machine des trois boites en ligne
 * (boite de vitesses, embrayage, inverseur), portee par {@link InlineBlock.Tile} (adaptateur cinetique). Elle ne tourne pas (la physique reste
 * celle du noeud : {@code shaftFactor}) : elle publie les valeurs NOMMEES de l'interface (vitesse d'entree et de sortie, couple, rapport,
 * sorte, signal) et recoit le reglage du CURSEUR ({@link #SETTING_RATIO} : position 0..4 de {@link InlineBlock#SLIDER}, bornee). Aucune cle
 * de sauvegarde : le rapport et le signal sont dans l'etat du bloc.
 */
public final class TransmissionUnit extends MachineUnit {

    /** Reglage du curseur : position du rapport (0 = x1/4 .. 4 = x4). */
    public static final int SETTING_RATIO = 0;
    /** Echelle des jauges de vitesse (tr/min). */
    public static final double SCALE = 120;
    /** Jauge de la vitesse de sortie (l'entree est la jauge de l'energie motrice, {@link StandardLayout#POWER}). */
    public static final Rect OUT = StandardLayout.ageGauge(0);
    /** Carte des mesures, a droite des deux jauges. */
    public static final Rect CARD = StandardLayout.ageCard(OUT.x() + OUT.w() + 6, 0);

    private final InlineBlock.Tile tile;

    public TransmissionUnit(UnitHost machine, InlineBlock.Tile tile) {
        super(machine);
        this.tile = tile;
    }

    /** Elements de l'interface (controle des chevauchements, planches) : jauges, carte, ecran, curseur pour la boite de vitesses. */
    public static List<Part> parts(boolean slider) {
        List<Part> out = new ArrayList<>();
        out.add(StandardLayout.framed("power", StandardLayout.POWER));
        out.add(StandardLayout.framed("gauge", OUT));
        out.add(new Part("card", CARD));
        if (slider) {
            out.add(new Part("screen", StandardLayout.sliderScreen(StandardLayout.WIDTH)));
            out.add(new Part("slider", StandardLayout.sliderRow(StandardLayout.WIDTH)));
        } else {
            out.add(new Part("screen", StandardLayout.screen(StandardLayout.WIDTH)));
        }
        return out;
    }

    @Override
    public void tick() {
    }

    private BlockState state() {
        return machine.state();
    }

    private int ratio() {
        BlockState s = state();
        return s.hasProperty(InlineBlock.RATIO) ? s.getValue(InlineBlock.RATIO) : 0;
    }

    private boolean powered() {
        BlockState s = state();
        return s.hasProperty(InlineBlock.POWERED) && s.getValue(InlineBlock.POWERED);
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.SPEED, () -> Math.round(tile.getSpeed() * 100)).add(UiValues.OUT_SPEED, () -> Math.round(tile.outputSpeed() * 100))
              .add(UiValues.TORQUE, () -> Math.round(tile.transmitted() * 10)).add(UiValues.RATIO, () -> InlineBlock.sliderOf(ratio()))
              .add(UiValues.TRANSMISSION, () -> tile.kind().ordinal()).add(UiValues.POWERED, () -> powered() ? 1 : 0)
              .add(UiValues.MAX_SPEED, () -> Math.round(tile.maxSpeed())).add(UiValues.RATED_TORQUE, () -> Math.round(tile.maxTorque()));
    }

    /** Curseur de la boite de vitesses : la position recue (client) est bornee, puis le rapport est pose dans l'etat du bloc. */
    @Override
    public void setting(int setting, int value) {
        Level level = level();
        if (setting != SETTING_RATIO || level == null || tile.kind() != InlineBlock.Kind.GEARSHIFT) {
            return;
        }
        int position = Math.max(0, Math.min(InlineBlock.SLIDER.length - 1, value));
        InlineBlock.setRatio(level, pos(), state(), InlineBlock.SLIDER[position]);
    }
}
