package fr.pioneers.farm.herd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.Level;

/** 1.0.4-beta (lot 28) : cheval d'une race (type {@code pioneers:<race>_horse}) ; monte, armure, apprivoisement : le cheval vanilla. */
public class HerdHorse extends Horse implements HerdBreedEntity {

    public HerdHorse(EntityType<? extends Horse> type, Level level) {
        super(type, level);
    }

    @Override
    public GeneratedHerd.HerdType herdType() {
        return HerdBreeds.typeOf(getType());
    }
}
