package fr.pioneers.farm.animal.breed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import fr.pioneers.farm.animal.AnimalSpecies;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.goat.GoatAi;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Race de chevre (1.0.3-beta lot 8) : chevre vanilla dont la race est le type ({@link PiBreeds}). Le cerveau vanilla ne cherche ses
 * partenaires que parmi le type {@code minecraft:goat} ({@code AnimalMakeLove(EntityType.GOAT)}) : le comportement {@link BreedMakeLove}
 * (toute chevre de race) est ajoute a l'activite de repos, avec les memes conditions que celle de {@code GoatAi}.
 */
public class PiGoat extends Goat implements BreedAnimal {

    public PiGoat(EntityType<? extends Goat> type, Level level) {
        super(type, level);
    }

    @Override
    public AnimalSpecies species() {
        return AnimalSpecies.GOAT;
    }

    @Override
    public int breed() {
        return Math.max(0, PiBreeds.breedOf(getType()));
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return EntityType.GOAT.getDefaultLootTable();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<Goat> brain = (Brain<Goat>) super.makeBrain(dynamic);
        brain.addActivityWithConditions(Activity.IDLE, ImmutableList.of(Pair.of(1, new BreedMakeLove(1.0F))),
              ImmutableSet.of(Pair.of(MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_ABSENT),
                    Pair.of(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT)));
        return brain;
    }

    @Nullable
    @Override
    public Goat getBreedOffspring(ServerLevel level, AgeableMob mate) {
        Goat child = PiBreeds.offspring(this, mate, level, getRandom(), Goat.class);
        if (child != null) {
            Memories.init(child, level.getRandom());
            boolean screaming = mate instanceof Goat g && g.isScreamingGoat() || isScreamingGoat();
            child.setScreamingGoat(screaming && getRandom().nextDouble() < 0.3 || getRandom().nextDouble() < GOAT_SCREAMING_CHANCE);
        }
        return child;
    }

    /** Acces aux memoires de depart des chevres ({@code GoatAi.initMemories} est protege). */
    static final class Memories extends GoatAi {

        static void init(Goat goat, net.minecraft.util.RandomSource random) {
            initMemories(goat, random);
        }
    }
}
