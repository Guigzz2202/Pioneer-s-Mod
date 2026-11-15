package fr.pioneers.building.home;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.farm.food.DishShelfBlock;
import fr.pioneers.building.home.bag.BagItem;
import fr.pioneers.building.home.bag.BagKind;
import fr.pioneers.building.home.bag.BagMenu;
import fr.pioneers.building.home.bag.BagUpgradeRecipe;
import fr.pioneers.building.home.basket.BasketBlock;
import fr.pioneers.building.home.basket.BasketTile;
import fr.pioneers.building.home.furniture.CabinetBlock;
import fr.pioneers.building.home.furniture.CabinetTile;
import fr.pioneers.building.home.furniture.ChairBlock;
import fr.pioneers.building.home.furniture.SeatEntity;
import fr.pioneers.building.home.furniture.TableBlock;
import fr.pioneers.building.home.line.HookBlock;
import fr.pioneers.building.home.line.HookTile;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

/**
 * Patch 1.0.3-beta, lot 7 (demandes de Guillaume du 05/11 : « maison et rangement ») : registre des sacs (sacs a dos 16 / 32 cases, sacs a
 * graines, a pousses, a poissons : {@link BagKind}), des meubles par bois (table a pieds connectes, chaise a siege, meuble de rangement
 * comptoir / placard haut : {@link #WOODS}), des etageres a plats par bois ({@link DishShelfBlock#VARIANTS}) et du crochet a corde (fil a
 * linge, sechage : {@link HookBlock}). Modeles, textures, recettes, langue : {@code tools/gc/home/*} (source unique des bois : la meme liste).
 */
public final class PiHome {

    private PiHome() {
    }

    /** Charge les registres (appele par {@code Pioneers}). */
    public static void init() {
    }

    /**
     * Les bois des meubles et des etageres (bois vanilla qui ont des planches ; le mod n'a pas de planches a lui) : meme liste et meme ordre que
     * {@code WOODS} de {@code tools/gc/home/woods.py}.
     */
    public static final List<String> WOODS = HomeWoods.ALL;

    private static BlockBehaviour.Properties wood(String wood) {
        boolean nether = wood.equals("crimson") || wood.equals("warped");
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2F, 3F).noOcclusion()
              .sound(wood.equals("bamboo") ? SoundType.BAMBOO_WOOD : wood.equals("cherry") ? SoundType.CHERRY_WOOD : nether ? SoundType.NETHER_WOOD : SoundType.WOOD);
        return nether ? p : p.ignitedByLava();
    }

    // ------------------------------------------------------------------ sacs
    public static final Map<BagKind, RegistryObject<BagItem>> BAGS = new EnumMap<>(BagKind.class);

    static {
        for (BagKind kind : BagKind.values()) {
            BAGS.put(kind, PiCoreRegistries.ITEMS.register(kind.id, () -> new BagItem(kind, new Item.Properties().stacksTo(1))));
        }
    }

    public static final RegistryObject<MenuType<BagMenu>> BAG_MENU = PiCoreRegistries.MENUS.register("bag", () -> IForgeMenuType.create(BagMenu::client));
    /** 1.0.5-beta (lot 31) : ecran des sacs de rangement (cases virtuelles a grosse capacite). */
    public static final RegistryObject<MenuType<fr.pioneers.building.home.bag.StorageBagMenu>> STORAGE_BAG_MENU = PiCoreRegistries.MENUS.register("storage_bag",
          () -> IForgeMenuType.create(fr.pioneers.building.home.bag.StorageBagMenu::client));
    public static final RegistryObject<RecipeSerializer<BagUpgradeRecipe>> BAG_UPGRADE = PiRecipes.RECIPE_SERIALIZERS.register("bag_upgrade",
          BagUpgradeRecipe.Serializer::new);

    // ------------------------------------------------------------------ meubles par bois
    public static final Map<String, BlockEntry<TableBlock>> TABLES = new LinkedHashMap<>();
    public static final Map<String, BlockEntry<ChairBlock>> CHAIRS = new LinkedHashMap<>();
    public static final Map<String, BlockEntry<CabinetBlock>> CABINETS = new LinkedHashMap<>();

    static {
        for (String w : WOODS) {
            TABLES.put(w, PiCoreRegistries.block(w + "_table", () -> new TableBlock(wood(w))));
            CHAIRS.put(w, PiCoreRegistries.block(w + "_chair", () -> new ChairBlock(wood(w))));
            CABINETS.put(w, PiCoreRegistries.block(w + "_cabinet", () -> new CabinetBlock(wood(w))));
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CabinetTile>> CABINET_TILE = PiTileEntityTypes.VANILLA_TILES.register("cabinet",
          () -> BlockEntityType.Builder.of(CabinetTile::new, CABINETS.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = PiEntityTypes.ENTITY_TYPES.register("seat",
          () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).noSummon().fireImmune().clientTrackingRange(8)
                .updateInterval(20).build("seat"));

    // ------------------------------------------------------------------ crochet a corde (fil a linge, sechage)
    public static final BlockEntry<HookBlock> ROPE_HOOK = PiCoreRegistries.block("rope_hook", () -> new HookBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                .pushReaction(PushReaction.DESTROY)));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<HookTile>> HOOK_TILE = PiTileEntityTypes.VANILLA_TILES.register("rope_hook",
          () -> BlockEntityType.Builder.of(HookTile::new, ROPE_HOOK.getBlock()).build(null));

    // ------------------------------------------------------------------ 1.0.3-beta (lot 11b) : panier en osier (produits agricoles)
    //1.0.3-beta (lot 12) : deux formats ; le panier d'origine garde son id pour la demi-hauteur, le panier haut est nouveau
    public static final BlockEntry<BasketBlock> BASKET = PiCoreRegistries.block("basket", () -> new BasketBlock(basket(), false));
    public static final BlockEntry<BasketBlock> TALL_BASKET = PiCoreRegistries.block("tall_basket", () -> new BasketBlock(basket(), true));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BasketTile>> BASKET_TILE = PiTileEntityTypes.VANILLA_TILES.register("basket",
          () -> BlockEntityType.Builder.of(BasketTile::new, BASKET.getBlock(), TALL_BASKET.getBlock()).build(null));

    private static BlockBehaviour.Properties basket() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5F).sound(SoundType.SCAFFOLDING).noOcclusion().ignitedByLava()
              .pushReaction(PushReaction.DESTROY);
    }

    /** Tous les ids du lot (ordre du menu creatif et des controles) : sacs, puis par bois table, chaise, meuble, etagere ; crochet. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (BagKind k : BagKind.values()) {
            out.add(k.id);
        }
        for (String w : WOODS) {
            out.add(w + "_table");
            out.add(w + "_chair");
            out.add(w + "_cabinet");
            out.add(DishShelfBlock.id(w));
            out.add(DishShelfBlock.wallId(w));
        }
        out.add("rope_hook");
        out.add("basket");
        out.add("tall_basket");
        return out;
    }

    public static net.minecraft.resources.ResourceLocation rl(String path) {
        return Pioneers.rl(path);
    }
}
