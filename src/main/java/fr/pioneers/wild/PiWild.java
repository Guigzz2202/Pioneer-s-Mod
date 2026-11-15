package fr.pioneers.wild;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiEntityTypes;
import fr.pioneers.registries.PiSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 6, specification « Le monde vivant » §4 « Forets, plaines et partout », §7, §9) : registre du GIBIER : cinq especes
 * ({@code pioneers:boar} {@link Boar}, {@code deer} {@link Deer}, {@code turkey} {@link Turkey}, {@code hare} {@link Hare}, {@code bison}
 * {@link Bison} ; une entite par espece, le sexe et l'age dans la bete), leurs oeufs, les butins nouveaux (venaison, viande de sanglier, de
 * bison, de dindon, leurs cuissons, bois de cerf), les sons (sons vanilla assembles, {@code assets/pioneers/sounds.json} « wild.* »), les
 * tags de nourriture de l'elevage ({@code pioneers:wild_food/<espece>}). Les especes entrent dans le socle de faune par {@link WildFauna}
 * (groupe GIBIER : elles deviennent seules des cibles du mode Chasse du chien). Donnees : {@code tools/gc/wild/wild.py} ({@link GeneratedWild}).
 */
public final class PiWild {

    private PiWild() {
    }

    public static void init() {
    }

    public static final List<RegistryObject<? extends EntityType<? extends WildAnimal>>> TYPES = new ArrayList<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    public static final Map<String, RegistryObject<Item>> ITEMS = new HashMap<>();
    /** Sons : « espece.son » (ambient, hurt, death et le son propre : charge, bell, strut, jump, angry). */
    public static final Map<String, RegistryObject<SoundEvent>> SOUNDS = new HashMap<>();
    public static final List<TagKey<Item>> FOOD = new ArrayList<>();

    public static final RegistryObject<EntityType<Boar>> BOAR = type(WildRules.BOAR, Boar::new);
    public static final RegistryObject<EntityType<Deer>> DEER = type(WildRules.DEER, Deer::new);
    public static final RegistryObject<EntityType<Turkey>> TURKEY = type(WildRules.TURKEY, Turkey::new);
    public static final RegistryObject<EntityType<Hare>> HARE = type(WildRules.HARE, Hare::new);
    public static final RegistryObject<EntityType<Bison>> BISON = type(WildRules.BISON, Bison::new);

    static {
        for (GeneratedWild.Food f : GeneratedWild.ITEMS) {
            ITEMS.put(f.id(), PiCoreRegistries.ITEMS.register(f.id(), food(f)));
        }
        String[][] sounds = {{"ambient", "hurt", "death", "charge"}, {"ambient", "hurt", "death", "bell"}, {"ambient", "hurt", "death", "strut"},
                             {"ambient", "hurt", "death", "jump"}, {"ambient", "hurt", "death", "angry"}};
        for (int k = 0; k < GeneratedWild.SPECIES.length; k++) {
            String id = GeneratedWild.SPECIES[k].id();
            for (String s : sounds[k]) {
                String name = "wild." + id + "." + s;
                SOUNDS.put(id + "." + s, PiSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(name))));
            }
            FOOD.add(TagKey.create(Registries.ITEM, Pioneers.rl("wild_food/" + id)));
        }
    }

    private static <T extends WildAnimal> RegistryObject<EntityType<T>> type(int kind, EntityType.EntityFactory<T> factory) {
        GeneratedWild.Species s = GeneratedWild.SPECIES[kind];
        RegistryObject<EntityType<T>> ro = PiEntityTypes.ENTITY_TYPES.register(s.id(), () -> EntityType.Builder.of(factory, MobCategory.CREATURE)
              .sized(s.width(), s.height()).clientTrackingRange(10).build(s.id()));
        TYPES.add(ro);
        EGGS.add(PiCoreRegistries.ITEMS.register(s.id() + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, s.egg1(), s.egg2(), new Item.Properties())));
        return ro;
    }

    private static Supplier<Item> food(GeneratedWild.Food f) {
        if (f.nutrition() <= 0) {
            return () -> new Described(new Item.Properties(), "description.pioneers." + f.id());
        }
        return () -> {
            FoodProperties.Builder b = new FoodProperties.Builder().nutrition(f.nutrition()).saturationMod(f.saturation());
            if (f.meat()) {
                b.meat();
            }
            if (f.hunger()) {
                b.effect(() -> new MobEffectInstance(MobEffects.HUNGER, 600, 0), 0.3F);
            }
            return new Item(new Item.Properties().food(b.build()));
        };
    }

    @Nullable
    public static Item item(String id) {
        RegistryObject<Item> ro = ITEMS.get(id);
        return ro == null ? null : ro.get();
    }

    @Nullable
    public static SoundEvent sound(int kind, String name) {
        RegistryObject<SoundEvent> ro = SOUNDS.get(WildAnimal.speciesOf(kind).id() + "." + name);
        return ro == null ? null : ro.get();
    }

    /** Objet simple qui affiche sa description (bois de cerf). */
    static final class Described extends Item {

        final String key;

        Described(Properties p, String key) {
            super(p);
            this.key = key;
        }

        @Override
        public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            for (int k = 0; k < TYPES.size(); k++) {
                event.put(TYPES.get(k).get(), WildAnimal.createAttributes(k).build());
            }
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void spawns(SpawnPlacementRegisterEvent event) {
            for (RegistryObject<? extends EntityType<? extends WildAnimal>> t : TYPES) {
                event.register((EntityType<WildAnimal>) t.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                      WildAnimal::checkWildSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
            }
        }
    }
}
