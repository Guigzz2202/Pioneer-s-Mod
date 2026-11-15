package fr.pioneers.world.wild;

import fr.pioneers.world.season.Seasons;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

/**
 * 1.0.5-beta (lot 6, specification §4, §7) : le LIEVRE. Plus grand que le lapin, longues oreilles a bout noir ; tres rapide, il ZIGZAGUE
 * en fuyant ; pelage BLANC en hiver et toujours dans un biome enneige ({@link WildRules#winterCoat}) ; se dresse aux aguets. Butin : la
 * viande et la peau du lapin (choix du lot 6).
 */
public class Hare extends WildAnimal {

    public Hare(EntityType<? extends Animal> type, Level level) {
        super(type, level, WildRules.HARE);
    }

    /** Pelage d'hiver (blanc) ici et maintenant. */
    public boolean winterCoat() {
        Seasons.Date d = date();
        return WildRules.winterCoat(d == null ? 0 : d.season().ordinal(), snowyHere());
    }
}
