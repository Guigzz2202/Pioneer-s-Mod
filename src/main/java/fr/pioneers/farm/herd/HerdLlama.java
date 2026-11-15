package fr.pioneers.farm.herd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.Level;

/** 1.0.4-beta (lot 28) : lama d'une race (type {@code pioneers:<race>_llama}) ; tapis, coffre, crachat, caravane de lamas : le lama vanilla. */
public class HerdLlama extends Llama implements HerdBreedEntity {

    public HerdLlama(EntityType<? extends Llama> type, Level level) {
        super(type, level);
    }

    @Override
    public GeneratedHerd.HerdType herdType() {
        return HerdBreeds.typeOf(getType());
    }
}
