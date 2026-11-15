package fr.pioneers.farm.animal.breed;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.Animal;

/**
 * Accouplement des animaux a cerveau (chevres de race, 1.0.3-beta lot 8) : meme deroulement que {@code AnimalMakeLove} de Minecraft (le
 * partenaire le plus proche visible, rapprochement, petit apres 60 a 110 ticks a moins de 3 blocs), mais le partenaire est tout animal de la
 * MEME CLASSE ({@code canMate}) au lieu d'un seul type d'entite : deux races de chevres s'accouplent.
 */
public class BreedMakeLove extends Behavior<Animal> {

    private final float speed;
    private long spawnAt;

    public BreedMakeLove(float speed) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT, MemoryModuleType.BREED_TARGET,
              MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
              110);
        this.speed = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Animal owner) {
        return owner.isInLove() && partner(owner).isPresent();
    }

    @Override
    protected void start(ServerLevel level, Animal owner, long time) {
        Animal other = partner(owner).get();
        owner.getBrain().setMemory(MemoryModuleType.BREED_TARGET, other);
        other.getBrain().setMemory(MemoryModuleType.BREED_TARGET, owner);
        BehaviorUtils.lockGazeAndWalkToEachOther(owner, other, speed);
        spawnAt = time + 60 + owner.getRandom().nextInt(50);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Animal owner, long time) {
        if (!owner.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)
            || !(owner.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get() instanceof Animal other)) {
            return false;
        }
        return other.isAlive() && owner.canMate(other) && BehaviorUtils.entityIsVisible(owner.getBrain(), other) && time <= spawnAt;
    }

    @Override
    protected void tick(ServerLevel level, Animal owner, long time) {
        if (!(owner.getBrain().getMemory(MemoryModuleType.BREED_TARGET).orElse(null) instanceof Animal other)) {
            return;
        }
        BehaviorUtils.lockGazeAndWalkToEachOther(owner, other, speed);
        if (owner.closerThan(other, 3.0) && time >= spawnAt) {
            owner.spawnChildFromBreeding(level, other);
            owner.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
            other.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        }
    }

    @Override
    protected void stop(ServerLevel level, Animal owner, long time) {
        owner.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        owner.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        spawnAt = 0;
    }

    private Optional<? extends Animal> partner(Animal owner) {
        return owner.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get()
              .findClosest(e -> e instanceof Animal a && a != owner && a.getClass() == owner.getClass() && owner.canMate(a)).map(Animal.class::cast);
    }
}
