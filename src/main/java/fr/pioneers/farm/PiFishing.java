package fr.pioneers.farm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.FishItem;
import fr.pioneers.farm.fish.FishRoeBlock;
import fr.pioneers.farm.fish.FishRoeItem;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiBobber;
import fr.pioneers.farm.fish.PiFish;
import fr.pioneers.farm.fish.PiRodItem;
import fr.pioneers.mechanics.PiMechanics;
import fr.pioneers.registries.PiEntityTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registre de la peche et de la pisciculture : entite poisson (toutes especes) et bouchon, poissons du mod, seau a poisson, oeufs, cannes,
 * appats, oeuf d'apparition ; attributs et regles d'apparition ; les poissons vanilla ne naissent plus ; vers de terre en creusant.
 */
public final class PiFishing {

    private PiFishing() {
    }

    public static void init() {
    }

    public static final RegistryObject<EntityType<PiFish>> FISH = PiEntityTypes.ENTITY_TYPES.register("fish",
          () -> EntityType.Builder.<PiFish>of(PiFish::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.4F).clientTrackingRange(6).build("fish"));
    public static final RegistryObject<EntityType<PiBobber>> BOBBER = PiEntityTypes.ENTITY_TYPES.register("bobber",
          () -> EntityType.Builder.<PiBobber>of(PiBobber::new, MobCategory.MISC).sized(0.25F, 0.25F).noSave().noSummon().clientTrackingRange(8)
                .updateInterval(1).build("bobber"));

    public static final Map<String, RegistryObject<Item>> FISH_ITEMS = new LinkedHashMap<>();

    /**
     * 1.0.3-beta (lot 8) : une entite par espece {@code pioneers:fish_<id>} (boite de l'espece, {@code FishDef.width/height}) et son oeuf
     * d'apparition ; {@link #FISH} reste le type GENERIQUE (apparition par le milieu, seau, eclosion, oeuf generique) converti en l'espece par
     * {@link fr.pioneers.farm.animal.breed.BreedConversion}.
     */
    public static final List<RegistryObject<EntityType<PiFish>>> SPECIES_TYPES = new java.util.ArrayList<>();
    /** Oeufs des especes, dans leur ordre ; 1.0.5-beta (lot 28) : aucun pour une espece de peche seulement ({@link #EGGLESS}). */
    public static final List<RegistryObject<Item>> SPECIES_EGGS = new java.util.ArrayList<>();
    /**
     * 1.0.5-beta (lot 28, retour 36 de Guillaume : « le requin gris a deux oeufs d'apparition ») : une espece de peche seulement
     * ({@code FishSpawns.catchOnly} : le requin gris) n'a plus d'oeuf de POISSON ; son oeuf est celui du vrai requin
     * ({@code pioneers:gray_reef_shark_spawn_egg}) ; l'ancien {@code pioneers:fish_gray_reef_shark_spawn_egg} des mondes existants y est
     * remappe ({@code PiRemaps.P105_LOT28_ITEMS}). L'entite {@code pioneers:fish_gray_reef_shark} reste enregistree (un poisson de cette
     * espece qui entre dans un monde devient le vrai requin : {@code BreedConversion}).
     */
    public static boolean eggless(FishDef d) {
        return fr.pioneers.farm.fish.FishSpawns.catchOnly(d);
    }
    private static java.util.Map<EntityType<?>, Integer> speciesByType;

    static {
        FishDef[] all = GeneratedFish.SPECIES;
        for (FishDef d : all) {
            String id = "fish_" + d.id();
            RegistryObject<EntityType<PiFish>> ro = PiEntityTypes.ENTITY_TYPES.register(id, () -> EntityType.Builder.<PiFish>of(PiFish::new,
                  MobCategory.WATER_AMBIENT).sized(d.width(), d.height()).clientTrackingRange(6).build(id));
            SPECIES_TYPES.add(ro);
            if (!eggless(d)) {
                SPECIES_EGGS.add(PiCoreRegistries.ITEMS.register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(ro, d.colour(), d.accent(),
                      new Item.Properties())));
            }
        }
    }

    /** Type d'entite d'une espece. */
    public static EntityType<PiFish> speciesType(int species) {
        return SPECIES_TYPES.get(Math.max(0, Math.min(SPECIES_TYPES.size() - 1, species))).get();
    }

    /** Espece d'un type d'entite de poisson (-1 : type generique ou autre). */
    public static int speciesOf(EntityType<?> type) {
        if (speciesByType == null) {
            java.util.Map<EntityType<?>, Integer> m = new java.util.IdentityHashMap<>();
            for (int i = 0; i < SPECIES_TYPES.size(); i++) {
                m.put(SPECIES_TYPES.get(i).get(), i);
            }
            speciesByType = m;
        }
        Integer i = speciesByType.get(type);
        return i == null ? -1 : i;
    }

    /** Poisson d'une espece pret a poser (eclosion, relache) : directement l'entite de l'espece. */
    @Nullable
    public static PiFish create(Level level, int species, float size) {
        PiFish f = speciesType(species).create(level);
        if (f != null) {
            f.set(species, size);
        }
        return f;
    }

    static {
        FishDef[] all = GeneratedFish.SPECIES;
        for (int i = 0; i < all.length; i++) {
            if (all[i].item().startsWith("pioneers:")) {
                int species = i;
                String id = ResourceLocation.parse(all[i].item()).getPath();
                FISH_ITEMS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new FishItem(species, new Item.Properties())));
            }
        }
    }

    public static final RegistryObject<FishRoeBlock> ROE_BLOCK = PiCoreRegistries.BLOCKS.register("fish_roe_block",
          () -> new FishRoeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).noCollission().instabreak().sound(SoundType.WET_GRASS)
                .pushReaction(PushReaction.DESTROY).noOcclusion().randomTicks()));
    public static final RegistryObject<Item> FISH_ROE = PiCoreRegistries.ITEMS.register("fish_roe", () -> new FishRoeItem(new Item.Properties()));
    public static final RegistryObject<Item> STURGEON_ROE = PiCoreRegistries.ITEMS.register("sturgeon_roe", () -> new FishRoeItem(new Item.Properties()));
    public static final RegistryObject<Item> FISH_BUCKET = PiCoreRegistries.ITEMS.register("fish_bucket", () -> new MobBucketItem(FISH,
          () -> Fluids.WATER, () -> SoundEvents.BUCKET_EMPTY_FISH, new Item.Properties().stacksTo(1)) {
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            Tooltips tips = Tooltips.of(tooltip);
            if (stack.hasTag() && stack.getTag().contains("Species")) {
                int s = Math.max(0, Math.min(GeneratedFish.SPECIES.length - 1, stack.getTag().getInt("Species")));
                tips.state("farm.pioneers.fish." + GeneratedFish.SPECIES[s].id());
            }
        }
    });
    public static final RegistryObject<Item> FISH_SPAWN_EGG = PiCoreRegistries.ITEMS.register("fish_spawn_egg",
          () -> new ForgeSpawnEggItem(FISH, 0x4A7FA8, 0xE0C070, new Item.Properties()));
    public static final RegistryObject<Item> EARTHWORM = PiCoreRegistries.ITEMS.register("earthworm", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FISH_BAIT = PiCoreRegistries.ITEMS.register("fish_bait", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BAMBOO_ROD = PiCoreRegistries.ITEMS.register("bamboo_fishing_rod",
          () -> new PiRodItem(0, new Item.Properties().durability(192)));
    public static final RegistryObject<Item> BRONZE_ROD = PiCoreRegistries.ITEMS.register("bronze_fishing_rod",
          () -> new PiRodItem(1, new Item.Properties().durability(512)));
    public static final RegistryObject<Item> STEEL_ROD = PiCoreRegistries.ITEMS.register("steel_fishing_rod",
          () -> new PiRodItem(2, new Item.Properties().durability(1280)));

    public static final RegistryObject<Codec<Worms>> WORMS = PiMechanics.LOOT_MODIFIERS.register("earthworms", () -> Worms.CODEC);

    /**
     * Vers de terre : en creusant la terre (sans toucher de soie), une petite chance d'en trouver. 1.0.4-beta (lot 4) : 2,3 % (7 % avant ; Guillaume :
     * « Reduire la proba d'obtenir un ver de terre ») ; valeur des donnees : {@code loot_modifiers/earthworms.json} (T:core/p104_lot4.py).
     */
    public static class Worms extends LootModifier {

        public static final Codec<Worms> CODEC = RecordCodecBuilder.create(i -> codecStart(i).and(
              Codec.FLOAT.optionalFieldOf("chance", 0.023F).forGetter(m -> m.chance)).apply(i, Worms::new));
        static final TagKey<Block> SOILS = BlockTags.create(Pioneers.rl("worm_soils"));
        private final float chance;

        public Worms(LootItemCondition[] conditions, float chance) {
            super(conditions);
            this.chance = chance;
        }

        @NotNull
        @Override
        protected ObjectArrayList<ItemStack> doApply(@NotNull ObjectArrayList<ItemStack> loot, @NotNull LootContext context) {
            BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
            ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
            if (state == null || !state.is(SOILS) || tool != null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
                return loot;
            }
            if (context.getRandom().nextFloat() < chance) {
                loot.add(new ItemStack(EARTHWORM.get(), 1 + (context.getRandom().nextFloat() < 0.3F ? 1 : 0)));
            }
            return loot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return WORMS.get();
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(FISH.get(), AbstractFish.createAttributes().build());
            for (RegistryObject<EntityType<PiFish>> t : SPECIES_TYPES) {
                event.put(t.get(), AbstractFish.createAttributes().build());
            }
        }

        @SubscribeEvent
        public static void spawns(SpawnPlacementRegisterEvent event) {
            event.register(FISH.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PiFish::checkSpawn,
                  SpawnPlacementRegisterEvent.Operation.REPLACE);
            for (RegistryObject<EntityType<PiFish>> t : SPECIES_TYPES) {
                event.register(t.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PiFish::checkSpawn,
                      SpawnPlacementRegisterEvent.Operation.REPLACE);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        /** Les poissons vanilla ne naissent plus (les especes du mod les remplacent ; morue, saumon... restent des objets). */
        @SubscribeEvent
        public static void vanillaFish(MobSpawnEvent.SpawnPlacementCheck event) {
            EntityType<?> t = event.getEntityType();
            if ((t == EntityType.COD || t == EntityType.SALMON || t == EntityType.TROPICAL_FISH || t == EntityType.PUFFERFISH)
                && (event.getSpawnType() == MobSpawnType.NATURAL || event.getSpawnType() == MobSpawnType.CHUNK_GENERATION)) {
                event.setResult(Event.Result.DENY);
            }
        }
    }
}
