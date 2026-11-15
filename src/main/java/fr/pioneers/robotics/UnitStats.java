package fr.pioneers.robotics;

import net.minecraft.nbt.CompoundTag;

/**
 * Multiplicateurs appliques aux unites d'un hub par ses ameliorations (vitesse, soute, batterie, consommation, travail, energie de
 * travail) et outils speciaux (toucher de soie, fortune).
 */
public record UnitStats(double speed, double cargo, double battery, double drain, double work, double workEnergy, boolean silk, int fortune) {

    public static final UnitStats BASE = new UnitStats(1, 1, 1, 1, 1, 1, false, 0);

    /** Effets cumules des cartes : nombre par amelioration (deja borne). */
    public static UnitStats of(int[] cards) {
        int speed = cards[RoboticUpgrade.SPEED.ordinal()], cargo = cards[RoboticUpgrade.CARGO.ordinal()];
        int battery = cards[RoboticUpgrade.BATTERY.ordinal()], efficiency = cards[RoboticUpgrade.EFFICIENCY.ordinal()];
        boolean silk = cards[RoboticUpgrade.SILK_TOUCH.ordinal()] > 0;
        int fortune = silk ? 0 : cards[RoboticUpgrade.FORTUNE.ordinal()];
        double s = (1 + 0.2 * speed) * (1 - 0.08 * cargo) * (1 - 0.05 * battery);
        double drain = (1 + 0.25 * speed) * Math.max(0.3, 1 - 0.15 * efficiency);
        double work = (1 - 0.08 * efficiency) * (silk ? 0.7 : 1) * (1 - 0.15 * fortune);
        double workEnergy = (silk ? 2 : 1) * (1 + 0.5 * fortune);
        //0.17.2 : carte d'IA (reseau de neurones) : trajets, batterie, consommation et travail
        if (cards.length > RoboticUpgrade.AI.ordinal() && cards[RoboticUpgrade.AI.ordinal()] > 0) {
            s *= 1.25;
            drain *= 0.8;
            work *= 1.15;
            return new UnitStats(s, 1 + 0.4 * cargo, (1 + 0.4 * battery) * 1.25, drain, work, workEnergy, silk, fortune);
        }
        return new UnitStats(s, 1 + 0.4 * cargo, 1 + 0.4 * battery, drain, work, workEnergy, silk, fortune);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("speed", speed);
        tag.putDouble("cargo", cargo);
        tag.putDouble("battery", battery);
        tag.putDouble("drain", drain);
        tag.putDouble("work", work);
        tag.putDouble("workEnergy", workEnergy);
        tag.putBoolean("silk", silk);
        tag.putByte("fortune", (byte) fortune);
        return tag;
    }

    public static UnitStats load(CompoundTag tag) {
        if (!tag.contains("speed")) {
            return BASE;
        }
        return new UnitStats(tag.getDouble("speed"), tag.getDouble("cargo"), tag.getDouble("battery"), tag.getDouble("drain"), tag.getDouble("work"),
              tag.getDouble("workEnergy"), tag.getBoolean("silk"), tag.getByte("fortune"));
    }
}
