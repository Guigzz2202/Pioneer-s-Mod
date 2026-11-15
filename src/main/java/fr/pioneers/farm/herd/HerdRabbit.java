package fr.pioneers.farm.herd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.Level;

/** 1.0.4-beta (lot 28) : lapin d'une race (type {@code pioneers:<race>_rabbit}) ; tout le reste est le lapin vanilla. */
public class HerdRabbit extends Rabbit implements HerdBreedEntity {

    public HerdRabbit(EntityType<? extends Rabbit> type, Level level) {
        super(type, level);
    }

    @Override
    public GeneratedHerd.HerdType herdType() {
        return HerdBreeds.typeOf(getType());
    }
}
