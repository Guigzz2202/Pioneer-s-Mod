package fr.pioneers.farm.herd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.level.Level;

/** 1.0.4-beta (lot 28) : ane d'une race (type {@code pioneers:<race>_donkey}) ; coffre, monte, mulet avec un cheval : l'ane vanilla. */
public class HerdDonkey extends Donkey implements HerdBreedEntity {

    public HerdDonkey(EntityType<? extends Donkey> type, Level level) {
        super(type, level);
    }

    @Override
    public GeneratedHerd.HerdType herdType() {
        return HerdBreeds.typeOf(getType());
    }
}
