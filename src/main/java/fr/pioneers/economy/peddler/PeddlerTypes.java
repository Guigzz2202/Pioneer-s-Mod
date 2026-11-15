package fr.pioneers.economy.peddler;

import fr.pioneers.Pioneers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 3) : registre du Colporteur (marchand ambulant du mod, categorie CREATURE sans apparition naturelle). */
public final class PeddlerTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Pioneers.MODID);

    public static final RegistryObject<EntityType<PeddlerEntity>> PEDDLER = ENTITY_TYPES.register("peddler",
          () -> EntityType.Builder.of(PeddlerEntity::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10).build("peddler"));

    private PeddlerTypes() {
    }

    public static void init(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(PeddlerTypes::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(PEDDLER.get(), Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.5)
              .add(Attributes.FOLLOW_RANGE, 24).build());
    }
}
