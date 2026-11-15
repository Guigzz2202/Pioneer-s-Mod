package fr.pioneers.world.pet;

import fr.pioneers.Pioneers;
import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification « Le monde vivant » §5) : registre des CHIENS ET DES CHATS : une entite par race (convention du mod
 * depuis la 1.0.3-beta lot 8 et la 1.0.4-beta lot 28) {@code pioneers:<race>_dog} ({@link PetDog}, 8 races) et {@code pioneers:<race>_cat}
 * ({@link PetCat}, 8 races), leurs oeufs, la NICHE (chien) et le PANIER (chat) dans les 11 bois des meubles ({@code <bois>_dog_kennel},
 * {@code <bois>_cat_basket}, tuile du panier a 3 cases), le menu de l'interface de l'animal ({@link PetMenu}), le ticket de chunks du rappel,
 * les tags d'entites des proies du chat ({@code pioneers:cat_prey} : les rats du lot 7) et des predateurs du betail que le chien chasse de son
 * territoire ({@code pioneers:livestock_predators} : chiens sauvages, renards ; le vrai loup du lot 7 y entrera, et tout le groupe PREDATEUR
 * du socle de faune). Donnees des races : {@code tools/gc/pets/pets.py} ({@link GeneratedPets}).
 */
public final class PiPets {

    private PiPets() {
    }

    public static void init() {
    }

    public static final List<RegistryObject<EntityType<PetDog>>> DOGS = new ArrayList<>();
    public static final List<RegistryObject<EntityType<PetCat>>> CATS = new ArrayList<>();
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    public static final List<BlockEntry<PetHomeBlock>> KENNELS = new ArrayList<>(), BASKETS = new ArrayList<>();
    private static Map<EntityType<?>, Integer> dogIndex, catIndex;

    public static final TagKey<EntityType<?>> CAT_PREY = TagKey.create(Registries.ENTITY_TYPE, Pioneers.rl("cat_prey"));
    public static final TagKey<EntityType<?>> LIVESTOCK_PREDATORS = TagKey.create(Registries.ENTITY_TYPE, Pioneers.rl("livestock_predators"));

    /** Ticket de chunks pose le temps du rappel d'un animal dont le troncon n'etait pas charge (expire seul). */
    public static final TicketType<ChunkPos> RECALL_TICKET = TicketType.create("pioneers_pet_recall", Comparator.comparingLong(ChunkPos::toLong),
          PetRules.LOAD_WAIT + 40);

    static {
        for (int i = 0; i < GeneratedPets.DOG_BREEDS; i++) {
            GeneratedPets.DogBreed b = GeneratedPets.DOGS[i];
            String id = b.id() + "_dog";
            RegistryObject<EntityType<PetDog>> ro = PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PetDog>of(PetDog::new,
                  MobCategory.CREATURE).sized(b.width(), b.height()).clientTrackingRange(10).build(id));
            DOGS.add(ro);
            EGGS.add(PiCoreRegistries.ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, b.egg1(), b.egg2(), new Item.Properties())));
        }
        for (int i = 0; i < GeneratedPets.CATS.length; i++) {
            GeneratedPets.CatBreed b = GeneratedPets.CATS[i];
            String id = b.id() + "_cat";
            RegistryObject<EntityType<PetCat>> ro = PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PetCat>of(PetCat::new,
                  MobCategory.CREATURE).sized(b.width(), b.height()).clientTrackingRange(8).build(id));
            CATS.add(ro);
            EGGS.add(PiCoreRegistries.ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, b.egg1(), b.egg2(), new Item.Properties())));
        }
        for (String w : fr.pioneers.building.home.HomeWoods.ALL) {
            KENNELS.add(PiCoreRegistries.block(w + "_dog_kennel", () -> new PetHomeBlock(props(w), false),
                  b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.dog_kennel")));
            BASKETS.add(PiCoreRegistries.block(w + "_cat_basket", () -> new PetHomeBlock(props(w), true),
                  b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.cat_basket")));
        }
    }

    private static BlockBehaviour.Properties props(String wood) {
        boolean nether = wood.equals("crimson") || wood.equals("warped");
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 2.0F)
              .sound(wood.equals("bamboo") ? SoundType.BAMBOO_WOOD : wood.equals("cherry") ? SoundType.CHERRY_WOOD : nether ? SoundType.NETHER_WOOD
                    : SoundType.WOOD).noOcclusion();
        return nether ? p : p.ignitedByLava();
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CatBasketTile>> BASKET_TILE = PiTileEntityTypes.VANILLA_TILES.register("cat_basket",
          () -> BlockEntityType.Builder.of(CatBasketTile::new, BASKETS.stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));

    public static final RegistryObject<MenuType<PetMenu>> MENU = PiCoreRegistries.MENUS.register("pet", () -> IForgeMenuType.create(PetMenu::client));

    private static synchronized void index() {
        if (dogIndex == null) {
            Map<EntityType<?>, Integer> d = new IdentityHashMap<>(), c = new IdentityHashMap<>();
            for (int i = 0; i < DOGS.size(); i++) {
                d.put(DOGS.get(i).get(), i);
            }
            for (int i = 0; i < CATS.size(); i++) {
                c.put(CATS.get(i).get(), i);
            }
            dogIndex = d;
            catIndex = c;
        }
    }

    /** Race d'un type de chien (0 si le type n'en est pas un). */
    public static int dogBreed(EntityType<?> type) {
        index();
        return dogIndex.getOrDefault(type, 0);
    }

    public static int catBreed(EntityType<?> type) {
        index();
        return catIndex.getOrDefault(type, 0);
    }

    @Nullable
    public static EntityType<PetDog> dogType(int breed) {
        return breed >= 0 && breed < DOGS.size() ? DOGS.get(breed).get() : null;
    }

    @Nullable
    public static EntityType<PetCat> catType(int breed) {
        return breed >= 0 && breed < CATS.size() ? CATS.get(breed).get() : null;
    }

    /** Race d'un chat vanilla d'apres sa variante ({@link GeneratedPets#CAT_VARIANTS}) ; europeen tigre par defaut. */
    public static int catBreedOfVariant(@Nullable String variant) {
        for (String[] v : GeneratedPets.CAT_VARIANTS) {
            if (v[0].equals(variant)) {
                return Integer.parseInt(v[1]);
            }
        }
        return 0;
    }

    public static boolean isHome(Block b, boolean cat) {
        return b instanceof PetHomeBlock h && h.cat == cat;
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            for (RegistryObject<EntityType<PetDog>> t : DOGS) {
                event.put(t.get(), Wolf.createAttributes().build());
            }
            for (RegistryObject<EntityType<PetCat>> t : CATS) {
                event.put(t.get(), Cat.createAttributes().build());
            }
        }
    }
}
