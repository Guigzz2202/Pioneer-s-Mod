package fr.pioneers.equipment.gear;

import com.mojang.serialization.Codec;
import fr.pioneers.Pioneers;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.equipment.gear.EnergyBoltEntity;
import fr.pioneers.equipment.gear.GearKind;
import fr.pioneers.equipment.gear.GearLootModifier;
import fr.pioneers.equipment.gear.GearModule;
import fr.pioneers.equipment.gear.GearUpgradeRecipe;
import fr.pioneers.equipment.gear.ModularArmorItem;
import fr.pioneers.equipment.gear.ModularBladeItem;
import fr.pioneers.equipment.gear.ModularRifleItem;
import fr.pioneers.equipment.gear.ModularTier;
import fr.pioneers.equipment.gear.ModularToolItem;
import fr.pioneers.equipment.gear.ModulationBenchTile;
import fr.pioneers.equipment.gear.ModuleItem;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.core.recipes.PiRecipes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * 0.20.15 : equipement modulaire : 4 tiers (Graviton, Stellaris, Neutronique, Singularite) d'armures, d'outils (quatre outils puis le
 * multi-outil), de lames et de fusils a plasma ; leurs composants (blindage, cellule energetique) ; le chassis et les 32 modules ;
 * l'etabli de modulation ; le tir d'energie.
 */
public final class PiGear {

    private PiGear() {
    }

    public static void init() {
    }

    /** Objets modulaires par id, dans l'ordre de l'onglet (tier, armure, outils, armes). */
    public static final Map<String, RegistryObject<Item>> GEAR = new LinkedHashMap<>();
    /** Composants : {@code <tier>_plating}, {@code <tier>_cell}. */
    public static final Map<String, RegistryObject<Item>> PARTS = new LinkedHashMap<>();
    public static final Map<GearModule, RegistryObject<Item>> MODULES = new EnumMap<>(GearModule.class);

    static {
        for (ModularTier tier : ModularTier.values()) {
            for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) {
                String id = tier.id() + "_" + type.getName();
                GEAR.put(id, PiCoreRegistries.ITEMS.register(id, () -> new ModularArmorItem(tier, type, new Item.Properties())));
            }
            List<GearKind> tools = tier.ordinal() < 2 ? List.of(GearKind.PICKAXE, GearKind.AXE, GearKind.SHOVEL, GearKind.HOE) : List.of(GearKind.MULTITOOL);
            for (GearKind kind : tools) {
                String id = tier.id() + "_" + kind.id();
                GEAR.put(id, PiCoreRegistries.ITEMS.register(id, () -> new ModularToolItem(tier, kind, new Item.Properties())));
            }
            GEAR.put(tier.id() + "_blade", PiCoreRegistries.ITEMS.register(tier.id() + "_blade", () -> new ModularBladeItem(tier, new Item.Properties())));
            GEAR.put(tier.id() + "_rifle", PiCoreRegistries.ITEMS.register(tier.id() + "_rifle", () -> new ModularRifleItem(tier, new Item.Properties())));
            for (String part : new String[]{"plating", "cell"}) {
                String id = tier.id() + "_" + part;
                PARTS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new Item(new Item.Properties().rarity(tier.rarity))));
            }
        }
        for (GearModule m : GearModule.values()) {
            MODULES.put(m, PiCoreRegistries.ITEMS.register("module_" + m.id(), () -> new ModuleItem(m, new Item.Properties())));
        }
    }

    public static final RegistryObject<Item> MODULE_FRAME = PiCoreRegistries.ITEMS.register("module_frame", () -> new Item(new Item.Properties()));
    /** Apparence du tir d'energie (pas dans les onglets). */
    public static final RegistryObject<Item> ENERGY_BOLT_ITEM = PiCoreRegistries.ITEMS.register("energy_bolt", () -> new Item(new Item.Properties()));

    public static final BlockEntry<MachineBlock> BENCH = PiCoreRegistries.block("modulation_bench", () -> new MachineBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(),
          () -> PiGear.BENCH_TILE.get(), ModulationBenchTile::new));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ModulationBenchTile>> BENCH_TILE = PiTileEntityTypes.VANILLA_TILES.register("modulation_bench",
          () -> BlockEntityType.Builder.of(ModulationBenchTile::new, BENCH.getBlock()).build(null));

    public static final RegistryObject<EntityType<EnergyBoltEntity>> ENERGY_BOLT = PiEntityTypes.ENTITY_TYPES.register("energy_bolt",
          () -> EntityType.Builder.<EnergyBoltEntity>of(EnergyBoltEntity::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(8)
                .updateInterval(2)
                .build(Pioneers.rl("energy_bolt").toString()));

    public static final RegistryObject<RecipeSerializer<GearUpgradeRecipe>> UPGRADE_SERIALIZER = PiRecipes.RECIPE_SERIALIZERS.register("gear_upgrade",
          GearUpgradeRecipe.Serializer::new);

    public static final RegistryObject<Codec<GearLootModifier>> LOOT = PiMechanics.LOOT_MODIFIERS.register("modular_tool", () -> GearLootModifier.CODEC);

    /** Ordre de l'onglet Equipement : etabli, modules, puis par tier : composants, armure, outils, armes. */
    public static List<Item> creative() {
        List<Item> out = new ArrayList<>();
        out.add(BENCH.asItem());
        out.add(MODULE_FRAME.get());
        MODULES.values().forEach(m -> out.add(m.get()));
        for (ModularTier tier : ModularTier.values()) {
            out.add(PARTS.get(tier.id() + "_plating").get());
            out.add(PARTS.get(tier.id() + "_cell").get());
            GEAR.forEach((id, item) -> {
                if (id.startsWith(tier.id() + "_")) {
                    out.add(item.get());
                }
            });
        }
        return out;
    }
}
