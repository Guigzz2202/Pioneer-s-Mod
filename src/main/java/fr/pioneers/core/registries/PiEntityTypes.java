package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import fr.pioneers.space.CapsuleEntity;
import fr.pioneers.space.LaunchVisualEntity;
import fr.pioneers.space.RocketEntity;
import fr.pioneers.space.RocketTier;
import fr.pioneers.space.event.MeteorEntity;
import fr.pioneers.space.giant.GasPocketEntity;
import fr.pioneers.space.giant.HarvesterDroneEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entites du mod.
 */
public final class PiEntityTypes {

    private PiEntityTypes() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Pioneers.MODID);

    /** Fusees habitees, tiers 1 a 10 (taille de la boite de collision selon le tier). */
    public static final Map<RocketTier, RegistryObject<EntityType<RocketEntity>>> ROCKETS = new EnumMap<>(RocketTier.class);

    static {
        for (RocketTier tier : RocketTier.values()) {
            ROCKETS.put(tier, ENTITY_TYPES.register(tier.getName(),
                  () -> EntityType.Builder.<RocketEntity>of((type, level) -> new RocketEntity(type, level, tier), MobCategory.MISC)
                        .sized(tier.width, tier.height)
                        .clientTrackingRange(16)
                        .updateInterval(1)
                        .fireImmune()
                        .build(Pioneers.rl(tier.getName()).toString())));
        }
    }

    /** Fusee "spectacle" des lancements automatiques (sondes, cargos). */
    public static final RegistryObject<EntityType<LaunchVisualEntity>> LAUNCH_VISUAL = ENTITY_TYPES.register("launch_visual",
          () -> EntityType.Builder.<LaunchVisualEntity>of(LaunchVisualEntity::new, MobCategory.MISC)
                .sized(1.6F, 7.0F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .build(Pioneers.rl("launch_visual").toString()));

    /** Capsule d'arrivee (descente en parachute, soute contenant la fusee). */
    public static final RegistryObject<EntityType<CapsuleEntity>> CAPSULE = ENTITY_TYPES.register("capsule",
          () -> EntityType.Builder.<CapsuleEntity>of(CapsuleEntity::new, MobCategory.MISC)
                .sized(1.4F, 1.6F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .fireImmune()
                .build(Pioneers.rl("capsule").toString()));

    /** Poche de gaz des geantes gazeuses (non sauvegardee). */
    public static final RegistryObject<EntityType<GasPocketEntity>> GAS_POCKET = ENTITY_TYPES.register("gas_pocket",
          () -> EntityType.Builder.<GasPocketEntity>of(GasPocketEntity::new, MobCategory.MISC)
                .sized(3.0F, 3.0F)
                .clientTrackingRange(12)
                .updateInterval(5)
                .fireImmune()
                .noSave()
                .build(Pioneers.rl("gas_pocket").toString()));

    /** Drone collecteur (non sauvegarde : la baie le considere rentre apres un rechargement). */
    public static final RegistryObject<EntityType<HarvesterDroneEntity>> HARVESTER_DRONE = ENTITY_TYPES.register("harvester_drone",
          () -> EntityType.Builder.<HarvesterDroneEntity>of(HarvesterDroneEntity::new, MobCategory.MISC)
                .sized(0.8F, 0.5F)
                .clientTrackingRange(12)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .build(Pioneers.rl("harvester_drone").toString()));

    /** Meteore des pluies de meteores (non sauvegarde). */
    public static final RegistryObject<EntityType<MeteorEntity>> METEOR = ENTITY_TYPES.register("meteor",
          () -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .fireImmune()
                .noSave()
                .build(Pioneers.rl("meteor").toString()));
}
