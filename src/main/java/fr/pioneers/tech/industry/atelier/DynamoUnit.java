package fr.pioneers.tech.industry.atelier;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.GeneratorUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.Surplus;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.energy.EnergyPush;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.side.SideType;
import fr.pioneers.tech.kinetics.KineticNode;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

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
        //1.0.6 : case de recharge sous la reserve (meme place que les machines compactes) : la dynamo charge l'objet
        battery = machine.slot(ItemSlot.of(s -> s.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false),
              machine::changed, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y).ghostIcon("ghost_battery"));
    }

    /** 1.0.6 : case de recharge (l'objet se charge depuis la reserve). */
    public final ItemSlot battery;

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
        //1.0.6 : commande redstone (onglet Redstone) : arretee, plus d'excitation
        //1.0.6 (revision de la mecanique, Guillaume : « des fois j'atteins 100 % de charge alors que rien n'est connecte ») : elle ne prend
        //que l'energie que sa reserve peut garder (reserve pleine : aucune charge sur l'arbre) ; les sources regulees gardent leur vitesse
        if (DynamoTile.EXCITATION[excitation] <= 0 || !tile.redstoneAllows()) {
            return 0;
        }
        return Math.min(ratedFe(), energy.getNeeded()) * 20.0 / DynamoTile.FE_PER_JOULE;
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
        //1.0.6 : recharge de l'objet de la case, puis sortie par les faces ouvertes (onglet Faces)
        if (!battery.isEmpty() && energy.getStored() > 0) {
            battery.getStack().getCapability(ForgeCapabilities.ENERGY).ifPresent(item ->
                  energy.extract(item.receiveEnergy((int) Math.min(Integer.MAX_VALUE, Math.min(ratedFe(), energy.getStored())), false), false));
        }
        long given = energy.getStored() <= 0 ? 0 : EnergyPush.toNeighbours(level, pos(), energy.getStored(), (d, be) -> d.getAxis() != axis
              && !(be instanceof KineticNode) && tile.sideOpen(SideType.ENERGY, d), amount -> energy.extract(amount, false));
        idleOut = given > 0 || energy.getStored() <= 0 ? 0 : Math.min(1_000, idleOut + 1);
        double capacity = tile.networkCapacity();
        match = capacity <= 1 ? 0 : Math.min(1, mechanical / capacity);
        state = tile.jammed() ? DynamoTile.STALLED : DynamoTile.EXCITATION[excitation] <= 0 || !tile.redstoneAllows() ? DynamoTile.OFF : w < 0.05 ? DynamoTile.STOPPED
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
        if (!battery.isEmpty()) {
            tag.put("battery", battery.getStack().save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        energy.deserializeNBT(tag.getCompound("energy"));
        excitation = Math.max(0, Math.min(DynamoTile.EXCITATION.length - 1, tag.getInt("excitation")));
        battery.setStack(tag.contains("battery") ? net.minecraft.world.item.ItemStack.of(tag.getCompound("battery")) : net.minecraft.world.item.ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------
    // Interface standard (1.0.6, Guillaume : « au norme des machines compactes ») : reserve et case de recharge a gauche comme partout,
    // rotation en entree (jauge de vitesse a la place de la premiere jauge), fleche, carte des mesures et de l'excitation, ecran standard
    // ------------------------------------------------------------------

    /** Jauge de vitesse (entree : la rotation), fleche, carte (mesures en haut, bouton d'excitation en bas). */
    public static final StandardLayout.Rect SPEED = new StandardLayout.Rect(28, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.GAUGE_H);
    public static final StandardLayout.Rect ARROW = new StandardLayout.Rect(SPEED.x() + SPEED.w() + 6, StandardLayout.TOP + StandardLayout.GAUGE_H / 2 - 4,
          28, 8);
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(ARROW.x() + ARROW.w() + 6, StandardLayout.TOP,
          StandardLayout.WIDTH - 8 - (ARROW.x() + ARROW.w() + 6), StandardLayout.GAUGE_H - 16);
    public static final StandardLayout.Rect BUTTON = new StandardLayout.Rect(CARD.x(), CARD.y() + CARD.h() + 3, CARD.w(), 12);
    public static final StandardLayout.Rect ENERGY = new StandardLayout.Rect(8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);

    public static List<StandardLayout.Part> parts() {
        StandardLayout.Rect s = StandardLayout.screen(StandardLayout.WIDTH);
        return List.of(new StandardLayout.Part("energy", new StandardLayout.Rect(ENERGY.x() - 1, ENERGY.y() - 1, ENERGY.w() + 2, ENERGY.h() + 2)),
              new StandardLayout.Part("battery", new StandardLayout.Rect(StandardLayout.BATTERY_X - 1, StandardLayout.BATTERY_Y - 1, 18, 18)),
              new StandardLayout.Part("gauge", new StandardLayout.Rect(SPEED.x() - 1, SPEED.y() - 1, SPEED.w() + 2, SPEED.h() + 2)),
              new StandardLayout.Part("arrow", ARROW), new StandardLayout.Part("card", CARD), new StandardLayout.Part("setting", BUTTON),
              new StandardLayout.Part("screen", s));
    }
}
