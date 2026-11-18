package fr.pioneers.world.ruins.sentinel;

import fr.pioneers.Pioneers;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.9 (MAJ RP, bloc 1) : registres des Sentinelles (cinq types d'entites, fonction de butin du Module de memoire) et attributs de base.
 * Categorie MISC : hors du plafond de monstres et de toute apparition naturelle (comme les villageois) ; aucune table d'apparition, aucun
 * oeuf. Les attributs reels (PV, degats, vitesse, armure) sont poses par tier depuis les donnees a l'apparition et au chargement.
 */
public final class SentinelTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Pioneers.MODID);
    public static final DeferredRegister<LootItemFunctionType> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Pioneers.MODID);

    public static final RegistryObject<EntityType<GuardianEntity>> GUARDIAN = register(SentinelRole.GUARDIAN,
          EntityType.Builder.of(GuardianEntity::new, MobCategory.MISC));
    public static final RegistryObject<EntityType<PatrollerEntity>> PATROLLER = register(SentinelRole.PATROLLER,
          EntityType.Builder.of(PatrollerEntity::new, MobCategory.MISC));
    public static final RegistryObject<EntityType<TurretEntity>> TURRET = register(SentinelRole.TURRET,
          EntityType.Builder.of(TurretEntity::new, MobCategory.MISC));
    public static final RegistryObject<EntityType<RepairerEntity>> REPAIRER = register(SentinelRole.REPAIRER,
          EntityType.Builder.of(RepairerEntity::new, MobCategory.MISC));
    public static final RegistryObject<EntityType<ForemanEntity>> FOREMAN = register(SentinelRole.FOREMAN,
          EntityType.Builder.of(ForemanEntity::new, MobCategory.MISC));

    public static final RegistryObject<LootItemFunctionType> MEMORY = LOOT_FUNCTIONS.register("sentinel_memory",
          () -> new LootItemFunctionType(new SentinelMemoryFunction.Serializer()));

    private static final Map<SentinelRole, RegistryObject<? extends EntityType<? extends SentinelEntity>>> BY_ROLE = new EnumMap<>(SentinelRole.class);

    static {
        BY_ROLE.put(SentinelRole.GUARDIAN, GUARDIAN);
        BY_ROLE.put(SentinelRole.PATROLLER, PATROLLER);
        BY_ROLE.put(SentinelRole.TURRET, TURRET);
        BY_ROLE.put(SentinelRole.REPAIRER, REPAIRER);
        BY_ROLE.put(SentinelRole.FOREMAN, FOREMAN);
    }

    private SentinelTypes() {
    }

    private static <T extends SentinelEntity> RegistryObject<EntityType<T>> register(SentinelRole role, EntityType.Builder<T> builder) {
        return ENTITY_TYPES.register(role.entityId(), () -> builder.sized(role.width, role.height).fireImmune().clientTrackingRange(10)
              .build(role.entityId()));
    }

    public static EntityType<? extends SentinelEntity> type(SentinelRole role) {
        return BY_ROLE.get(role).get();
    }

    /** Attributs de base (remplaces par tier a l'apparition) : bus MOD. */
    static void attributes(EntityAttributeCreationEvent event) {
        for (SentinelRole role : SentinelRole.values()) {
            AttributeSupplier.Builder b = Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ATTACK_DAMAGE, 4)
                  .add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.ARMOR, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                  .add(Attributes.FOLLOW_RANGE, 24);
            if (role == SentinelRole.PATROLLER) {
                b.add(Attributes.FLYING_SPEED, 0.3);
            }
            event.put(type(role), b.build());
        }
    }
}
