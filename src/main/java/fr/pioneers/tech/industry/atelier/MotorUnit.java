package fr.pioneers.tech.industry.atelier;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 1.0.6 (Guillaume, 17/11/2026 : « un moteur electrique qui genere de la rotation via de l'electricite, MKI a IV, de plus en plus de force
 * selon les tiers ; la vitesse de rotation est ajustable ») : UNITE DU MOTEUR ELECTRIQUE. Source du reseau de rotation :
 * <ul>
 *     <li>couple de demarrage {@link MotorTile#RATED_TORQUE} (x4 par tier), regime = vitesse REGLEE (1 a {@link MotorTile#MAX_SPEED}
 *     tr/min) : source regulee comme les autres (1.0.6, {@link fr.pioneers.tech.kinetics.KineticNetwork}) : il tient la vitesse reglee tant
 *     que la charge ne depasse pas son couple nominal, et n'entraine plus au-dessus ;</li>
 *     <li>il paie en energie la puissance mecanique qu'il donne : couple x vitesse / rendement ({@link MotorTile#EFFICIENCY}) ; une reserve
 *     qui ne suit pas (ou au-dela de {@link MotorTile#RATED_FE} FE/t) baisse son couple d'autant au tick suivant (sous-alimentation) ;</li>
 *     <li>sens de rotation inversable ; commande redstone (arrete) ; case de batterie (videe dans la reserve) ; faces d'entree reglables.</li>
 * </ul>
 */
public class MotorUnit extends MachineUnit {

    private static final double RAD = 2 * Math.PI / 60.0;
    /** Etats de l'interface. */
    public static final int STOPPED = 0, RUNNING = 1, NO_POWER = 2, WEAK = 3, DRIVEN = 4, STALLED = 5, OFF = 6, TORQUE_LIMIT = 7;

    private final MotorTile tile;
    private final PowerSource.Kinetic power;
    public final CableTier cableTier;
    public final EnergyBuffer energy;
    public final ItemSlot battery;
    /** Vitesse reglee (tr/min) et sens (+1 / -1). */
    private int target = 60, direction = 1;
    /** Part du couple que la reserve a pu payer au dernier tick (0..1 ; 0 a la pose : demarrage progressif) ; puissance mecanique et energie
     * du dernier tick. */
    private double supply, mechanical, drawn, fraction, delivered;
    private int state;

    public MotorUnit(UnitHost machine, MotorTile tile, CableTier tier) {
        super(machine);
        this.tile = tile;
        this.cableTier = tier;
        this.power = (PowerSource.Kinetic) machine.power();
        long capacity = ratedFe() * MotorTile.RESERVE_TICKS;
        energy = new EnergyBuffer(() -> capacity, (int) Math.min(Integer.MAX_VALUE, ratedFe() * 2), 0, machine::changed);
        battery = machine.slot(ItemSlot.of(s -> s.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canExtract).orElse(false),
              machine::changed, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y).ghostIcon("ghost_battery"));
        target = Math.min(target, maxSpeed());
    }

    private int t() {
        return cableTier.ordinal();
    }

    public long ratedFe() {
        return MotorTile.RATED_FE[t()];
    }

    public double ratedTorque() {
        return MotorTile.RATED_TORQUE[t()];
    }

    public int maxSpeed() {
        return MotorTile.MAX_SPEED[t()];
    }

    public int target() {
        return target;
    }

    public int direction() {
        return direction;
    }

    // ------------------------------------------------------------------ source du reseau (lue par le noeud)

    /** Couple de demarrage (N.m, signe = sens) : celui du tier, reduit par la sous-alimentation ; 0 a l'arret. */
    public double sourceTorque() {
        if (target <= 0 || !tile.redstoneAllows()) {
            return 0;
        }
        return direction * ratedTorque() * supply;
    }

    /** Vitesse a vide : la vitesse reglee. */
    public double sourceFreeSpeed() {
        return Math.max(1, target);
    }

    /** 1.0.6 : couple rendu au reseau au dernier tick (N.m, donne par le reseau : source regulee a la vitesse reglee). */
    public double torqueAt(double rpm) {
        return delivered;
    }

    /** 1.0.6 : couple rendu, donne par le reseau a chaque tick. */
    public void delivered(double torque) {
        delivered = Math.max(0, torque);
    }

    // ------------------------------------------------------------------ marche

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        //batterie videe dans la reserve
        if (!battery.isEmpty() && energy.getNeeded() > 0) {
            battery.getStack().getCapability(ForgeCapabilities.ENERGY).ifPresent(item ->
                  energy.insert(item.extractEnergy((int) Math.min(Integer.MAX_VALUE, Math.min(ratedFe() * 2, energy.getNeeded())), false), false));
        }
        double rpm = power.speed();
        double torque = torqueAt(rpm);
        mechanical = torque * Math.abs(rpm) * RAD;
        double need = mechanical / 20.0 / MotorTile.EFFICIENCY[t()] + fraction;
        long whole = (long) Math.floor(need);
        fraction = need - whole;
        long paid = whole > 0 ? energy.extract(Math.min(whole, energy.getStored()), false) : 0;
        drawn = paid + fraction;
        //part payee (reserve qui ne suit pas) et plafond de puissance du tier : couple reduit au tick suivant ; il remonte en douceur (demarrage
        //progressif) ; reserve vide et rien paye : plus de couple (jamais de rotation gratuite)
        double want = Math.min(whole <= 0 ? 1 : paid / (double) whole, need <= 0 ? 1 : Math.min(1, ratedFe() / need));
        if (energy.getStored() <= 0 && paid <= 0) {
            want = 0;
        }
        supply = want < supply ? want : Math.min(1, supply + 0.1);
        state = tile.jammed() ? STALLED : target <= 0 ? STOPPED : !tile.redstoneAllows() ? OFF
              : energy.getStored() <= 0 && supply <= 0.05 ? NO_POWER : supply < 0.98 ? WEAK
              : rpm * direction > target + 0.5 ? DRIVEN : torque > 0 && rpm * direction < target * 0.97 ? TORQUE_LIMIT : RUNNING;
    }

    /** Vitesse reglee : +-1 tr/min (x10 avec Maj), bornee de 0 a la vitesse du tier ; sens inverse. */
    public void adjust(int delta) {
        target = Math.max(0, Math.min(maxSpeed(), target + delta));
        machine.changed();
    }

    public void reverse() {
        direction = -direction;
        machine.changed();
    }

    /** Reglage a valeur (curseur) : vitesse visee, bornee ici (elle vient du client). */
    @Override
    public void setting(int setting, int value) {
        if (setting == 0) {
            target = Math.max(0, Math.min(maxSpeed(), value));
            machine.changed();
        }
    }

    public double mechanical() {
        return mechanical;
    }

    public double drawnPerTick() {
        return drawn;
    }

    /** Valeurs de l'interface (nommees). */
    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.ENERGY, energy::getStored).add(UiValues.CAPACITY, energy::getCapacity)
              .add(UiValues.USAGE, () -> Math.round(drawn * 100)).add(UiValues.SPEED, () -> Math.round(power.speed() * 100))
              .add(UiValues.TORQUE, () -> Math.round(torqueAt(power.speed()) * 10)).add(UiValues.MECHANICAL, () -> Math.round(mechanical))
              .add(UiValues.STATUS, () -> state).add(UiValues.RATED, this::ratedFe).add(UiValues.MAX_SPEED, this::maxSpeed)
              .add(UiValues.RATED_TORQUE, () -> Math.round(ratedTorque())).add(UiValues.TARGET_RPM, () -> target)
              .add(MotorTile.DIRECTION, () -> direction > 0 ? 0 : 1).add(UiValues.MATCH, () -> Math.round(supply * 1000));
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("energy", energy.serializeNBT());
        tag.putInt("target", target);
        tag.putInt("direction", direction);
        tag.putDouble("supply", supply);
        if (!battery.isEmpty()) {
            tag.put("battery", battery.getStack().save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        energy.deserializeNBT(tag.getCompound("energy"));
        target = tag.contains("target") ? Math.max(0, Math.min(maxSpeed(), tag.getInt("target"))) : Math.min(60, maxSpeed());
        direction = tag.getInt("direction") < 0 ? -1 : 1;
        supply = tag.contains("supply") ? Math.max(0, Math.min(1, tag.getDouble("supply"))) : 0;
        battery.setStack(tag.contains("battery") ? ItemStack.of(tag.getCompound("battery")) : ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ interface (meme rangement que la dynamo, sens inverse)

    /** Reserve et batterie a gauche ; carte de reglage, fleche, jauge de vitesse en SORTIE (a droite) ; ecran standard. */
    public static final StandardLayout.Rect ENERGY = DynamoUnit.ENERGY;
    public static final StandardLayout.Rect SPEED = new StandardLayout.Rect(StandardLayout.WIDTH - 8 - StandardLayout.GAUGE_W, StandardLayout.TOP,
          StandardLayout.GAUGE_W, StandardLayout.GAUGE_H);
    public static final StandardLayout.Rect ARROW = new StandardLayout.Rect(SPEED.x() - 6 - 28, StandardLayout.TOP + StandardLayout.GAUGE_H / 2 - 4, 28, 8);
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(28, StandardLayout.TOP, ARROW.x() - 6 - 28, StandardLayout.GAUGE_H - 16);
    /** Rangee de reglage sous la carte : moins, vitesse visee, plus, sens. */
    public static final StandardLayout.Rect SETTING = new StandardLayout.Rect(CARD.x(), CARD.y() + CARD.h() + 3, CARD.w(), 12);

    public static List<StandardLayout.Part> parts() {
        return List.of(new StandardLayout.Part("energy", new StandardLayout.Rect(ENERGY.x() - 1, ENERGY.y() - 1, ENERGY.w() + 2, ENERGY.h() + 2)),
              new StandardLayout.Part("battery", new StandardLayout.Rect(StandardLayout.BATTERY_X - 1, StandardLayout.BATTERY_Y - 1, 18, 18)),
              new StandardLayout.Part("card", CARD), new StandardLayout.Part("setting", SETTING), new StandardLayout.Part("arrow", ARROW),
              new StandardLayout.Part("gauge", new StandardLayout.Rect(SPEED.x() - 1, SPEED.y() - 1, SPEED.w() + 2, SPEED.h() + 2)),
              new StandardLayout.Part("screen", StandardLayout.screen(StandardLayout.WIDTH)));
    }
}
