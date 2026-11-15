package fr.pioneers.tech.industry.atelier;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.GeneratorUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.Surplus;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.energy.EnergyPush;
import fr.pioneers.tech.kinetics.KineticNode;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * API Machine (lot 7) : UNITE DE GENERATEUR de la dynamo (code de l'ancienne {@code DynamoTile}, qui ne fait plus que la porter par
 * l'adaptateur cinetique et rester le noeud du reseau) : 1 J de travail = {@link DynamoTile#FE_PER_JOULE} FE ; excitation adaptee (le
 * reseau regle l'amortissement : {@code generatorDamping}, lu ici) ; bornes du tier (puissance, couple, vitesse) ; reserve de 200 ticks ;
 * energie poussee par les 4 faces hors de l'axe. Cles {@code energy}, {@code excitation} d'avant le lot 7.
 */
public class DynamoUnit extends GeneratorUnit {

    private static final double RAD = 2 * Math.PI / 60.0;

    private final DynamoTile tile;
    private final PowerSource.Kinetic power;
    public final CableTier cableTier;
    public final EnergyBuffer energy;
    private int excitation;
    // Dernier tick (serveur) : amortissement attribue, puissance, energie produite (FE/t, fraction gardee), etat
    private double damping, mechanical, producedNow, fraction, match;
    private int state, idleOut;

    public DynamoUnit(UnitHost machine, DynamoTile tile, CableTier tier) {
        super(machine, tier.ordinal());
        this.tile = tile;
        this.cableTier = tier;
        this.power = (PowerSource.Kinetic) machine.power();
        long capacity = ratedFe() * DynamoTile.RESERVE_TICKS;
        energy = new EnergyBuffer(() -> capacity, 0, (int) Math.min(Integer.MAX_VALUE, capacity), machine::changed);
    }

    public long ratedFe() {
        return DynamoTile.RATED_FE[cableTier.ordinal()];
    }

    public double ratedTorque() {
        return DynamoTile.RATED_TORQUE[cableTier.ordinal()];
    }

    /** 1.0.4-beta (lot 5) : production perdue reserve pleine (etat NO_OUTPUT = « Surplus perdu »). */
    public final Surplus surplus = new Surplus();

    /** Place pour au moins un tick de production nominale : sinon (reserve pleine) l'etat dit « surplus perdu » (1.0.4 : elle garde sa charge). */
    private boolean hasRoom() {
        return energy.getNeeded() >= ratedFe();
    }

    // ------------------------------------------------------------------
    // Reseau : generateur a excitation adaptee (lu par le noeud)
    // ------------------------------------------------------------------

    public double rating() {
        //1.0.4-beta (lot 5) : reserve pleine, la dynamo GARDE sa charge (la source ne part plus a vide) : le surplus est perdu (Surplus)
        return DynamoTile.EXCITATION[excitation] > 0 ? ratedFe() * 20.0 / DynamoTile.FE_PER_JOULE : 0;
    }

    public double excitationShare() {
        return DynamoTile.EXCITATION[excitation];
    }

    public void damping(double c) {
        damping = c;
    }

    /** Couple de la charge electrique (N.m). */
    public double electricTorque() {
        return damping * power.omega();
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        double w = power.omega();
        if (rating() <= 0) {
            damping = 0;
        }
        mechanical = damping * w * w * RAD;
        double fe = mechanical / 20.0 * DynamoTile.FE_PER_JOULE + fraction;
        long whole = (long) Math.floor(fe);
        fraction = fe - whole;
        long inserted = whole > 0 ? energy.insert(Math.min(whole, energy.getNeeded()), false) : 0;
        producedNow = mechanical / 20.0 * DynamoTile.FE_PER_JOULE;
        if (inserted < whole) {
            fraction = 0;
        }
        surplus.note(level.getGameTime(), whole, inserted);
        Direction.Axis axis = tile.axis();
        long given = energy.getStored() <= 0 ? 0 : EnergyPush.toNeighbours(level, pos(), energy.getStored(), (d, be) -> d.getAxis() != axis
              && !(be instanceof KineticNode), amount -> energy.extract(amount, false));
        idleOut = given > 0 || energy.getStored() <= 0 ? 0 : Math.min(1_000, idleOut + 1);
        double capacity = tile.networkCapacity();
        match = capacity <= 1 ? 0 : Math.min(1, mechanical / capacity);
        state = tile.jammed() ? DynamoTile.STALLED : DynamoTile.EXCITATION[excitation] <= 0 ? DynamoTile.OFF : w < 0.05 ? DynamoTile.STOPPED
              : w > tile.maxSpeed() ? DynamoTile.OVERSPEED : !hasRoom() ? DynamoTile.NO_OUTPUT
              : electricTorque() >= ratedTorque() * 0.98 ? DynamoTile.TORQUE_LIMIT
              : mechanical >= ratedFe() * 20.0 / DynamoTile.FE_PER_JOULE * 0.98 ? DynamoTile.SATURATED : DynamoTile.PRODUCING;
    }

    /** Aucun voisin n'a pris d'energie depuis 2 s alors que la reserve en contient. */
    public boolean noConsumer() {
        return idleOut > 40;
    }

    public double producedNow() {
        return producedNow;
    }

    /** Excitation suivante / precedente (action de l'interface). */
    public void cycleExcitation(boolean next) {
        int n = DynamoTile.EXCITATION.length;
        excitation = (excitation + (next ? 1 : n - 1)) % n;
        machine.changed();
    }

    /**
     * Valeurs d'avant le lot 7 : 0 reserve, 1 capacite, 2 FE/t x100, 3 vitesse x100, 4 couple x10, 5 puissance mecanique (W), 6 adaptation
     * x1000, 7 excitation, 8 etat, 9 FE/t nominaux, 10 vitesse max, 11 couple nominal, 12 sortie (1 = un voisin prend l'energie).
     */
    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.ENERGY, energy::getStored).add(UiValues.CAPACITY, energy::getCapacity).add(UiValues.OUTPUT, () -> Math.round(producedNow * 100))
              .add(UiValues.SPEED, () -> Math.round(power.speed() * 100)).add(UiValues.TORQUE, () -> Math.round(electricTorque() * 10))
              .add(UiValues.MECHANICAL, () -> Math.round(mechanical)).add(UiValues.MATCH, () -> Math.round(match * 1000))
              .add(UiValues.EXCITATION, () -> excitation).add(UiValues.STATUS, () -> state).add(UiValues.RATED, this::ratedFe)
              .add(UiValues.MAX_SPEED, () -> Math.round(tile.maxSpeed())).add(UiValues.RATED_TORQUE, () -> Math.round(ratedTorque()))
              .add(UiValues.OUTPUT_OK, () -> noConsumer() ? 0 : 1);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("energy", energy.serializeNBT());
        tag.putInt("excitation", excitation);
    }

    @Override
    public void load(CompoundTag tag) {
        energy.deserializeNBT(tag.getCompound("energy"));
        excitation = Math.max(0, Math.min(DynamoTile.EXCITATION.length - 1, tag.getInt("excitation")));
    }

    // ------------------------------------------------------------------
    // Interface standard v2 : vitesse (energie motrice), reserve, carte des mesures, ecran raccourci + excitation
    // ------------------------------------------------------------------

    public static final StandardLayout.Rect RESERVE = new StandardLayout.Rect(StandardLayout.PANEL_X, StandardLayout.TOP, StandardLayout.GAUGE_W,
          StandardLayout.GAUGE_H);
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(RESERVE.x() + RESERVE.w() + 6, StandardLayout.TOP,
          StandardLayout.WIDTH - 8 - (RESERVE.x() + RESERVE.w() + 6), StandardLayout.GAUGE_H);

    public static List<StandardLayout.Part> parts() {
        StandardLayout.Rect p = StandardLayout.POWER;
        return List.of(new StandardLayout.Part("power", new StandardLayout.Rect(p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2)),
              new StandardLayout.Part("energy", new StandardLayout.Rect(RESERVE.x() - 1, RESERVE.y() - 1, RESERVE.w() + 2, RESERVE.h() + 2)),
              new StandardLayout.Part("card", CARD), new StandardLayout.Part("screen", StandardLayout.shortScreen(StandardLayout.WIDTH)),
              new StandardLayout.Part("setting", StandardLayout.settingRow(StandardLayout.WIDTH)));
    }
}
