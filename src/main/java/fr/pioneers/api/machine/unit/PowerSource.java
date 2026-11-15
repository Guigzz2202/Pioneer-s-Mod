package fr.pioneers.api.machine.unit;

import fr.pioneers.bronze.steam.SteamVolume;
import java.util.function.DoubleSupplier;

/**
 * API Machine (lot 7) : ENERGIE MOTRICE d'une machine sans FE, declaree par sa fiche ({@code power} : KINETIC_IN / KINETIC_OUT, STEAM_IN,
 * FUEL) et pretee a ses unites par l'hote ({@link UnitHost#power()}). La physique ne bouge pas : la rotation reste celle du reseau
 * ({@code kinetics.KineticNetwork} : la tile reste un noeud), la vapeur celle du volume de la machine ({@link SteamVolume}, echanges par
 * {@code Steam.exchangeAll}), le feu celui du foyer de la tile. L'unite LIT la source et DECLARE sa charge (couple demande au reseau,
 * vapeur prise) ; elle ne calcule jamais la physique elle-meme.
 */
public abstract class PowerSource {

    /** Sortes d'energie motrice (meme vocabulaire que {@code MachineSpec.Power}). */
    public enum Kind {
        KINETIC, STEAM, FUEL
    }

    public abstract Kind kind();

    /**
     * Rotation : vitesse (tr/min, repere du noeud), couple transmis (N.m) et puissance du reseau (W) lus sur le noeud ; l'unite declare le
     * couple qu'elle demande ({@link #declareLoad}), que la tile rend au reseau par {@code loadTorque()}.
     */
    public static final class Kinetic extends PowerSource {

        private final DoubleSupplier speed, torque, power;
        private double load;

        public Kinetic(DoubleSupplier speed, DoubleSupplier torque, DoubleSupplier power) {
            this.speed = speed;
            this.torque = torque;
            this.power = power;
        }

        @Override
        public Kind kind() {
            return Kind.KINETIC;
        }

        /** Vitesse du noeud (tr/min, signee). */
        public double speed() {
            return speed.getAsDouble();
        }

        /** Vitesse en valeur absolue. */
        public double omega() {
            return Math.abs(speed.getAsDouble());
        }

        public double torque() {
            return torque.getAsDouble();
        }

        /** Puissance du reseau (W), 0 hors reseau. */
        public double networkPower() {
            return power.getAsDouble();
        }

        /** Couple de charge que la machine demande au reseau (N.m). */
        public void declareLoad(double torque) {
            load = torque;
        }

        public double load() {
            return load;
        }
    }

    /**
     * Vapeur : la reserve de la machine (1.0.2-beta, lot 3 : une QUANTITE, plus de pression) ; l'unite prend son debit fixe quand elle
     * travaille ({@link #ready} puis {@link #take}). La reserve se remplit par les echanges avec les voisins et les cables (tile =
     * {@code SteamNode}).
     */
    public static final class Steam extends PowerSource {

        private final SteamVolume volume;

        public Steam(SteamVolume volume) {
            this.volume = volume;
        }

        @Override
        public Kind kind() {
            return Kind.STEAM;
        }

        public SteamVolume volume() {
            return volume;
        }

        /** Vapeur en reserve (mB). */
        public double stored() {
            return volume.steam();
        }

        /** Contenance de la reserve (mB). */
        public double capacity() {
            return volume.capacity();
        }

        /** La reserve tient-elle la prise {@code need} (mB) de ce tick ? */
        public boolean ready(double need) {
            return volume.steam() >= need - 1e-9;
        }

        /** Prend jusqu'a {@code amount} mB de vapeur ; renvoie ce qui a ete pris. */
        public double take(double amount) {
            return volume.take(amount);
        }
    }

    /**
     * Feu direct : foyer de la tile (temps de combustion restant, temperature). L'unite lit la chaleur ; le foyer (allumage, air des
     * soufflets) reste a la tile.
     */
    public static final class Fuel extends PowerSource {

        private final DoubleSupplier temperature;
        private final DoubleSupplier burning;

        public Fuel(DoubleSupplier temperature, DoubleSupplier burning) {
            this.temperature = temperature;
            this.burning = burning;
        }

        @Override
        public Kind kind() {
            return Kind.FUEL;
        }

        /** Temperature du foyer (°C). */
        public double temperature() {
            return temperature.getAsDouble();
        }

        /** Part du feu (0 eteint .. 1 plein feu). */
        public double burning() {
            return burning.getAsDouble();
        }
    }
}
