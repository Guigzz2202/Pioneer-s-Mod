package fr.pioneers.hunt;

import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.registries.PiEntityTypes;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.5-beta (lot 10, specification « Le monde vivant » §6, §9, decisions 10 et 11) : registre de la CHASSE : armes (mousquet
 * {@link MusketItem}, revolver {@link RevolverItem}, harpon {@link HarpoonItem} + entite {@link HarpoonEntity}), munitions (balle de plomb,
 * cartouche), porte-fusil ({@link GunRackBlock}, 11 bois, tuile {@link GunRackTile}), trophees ({@link TrophyBlock} : cerf, ours, requin,
 * tuile {@link TrophyTile}). Evenement « troupeau » : {@link HerdEvents} ; competence Chasse : {@link HuntSkill} ; tir : {@link Firearms}.
 * Ressources : tools/gc/hunt/hunt.py.
 */
public final class PiHunt {

    private PiHunt() {
    }

    /** Branche les crochets du chien (troupeau, flair) ; appele par {@code Pioneers}. */
    public static void init() {
        HerdEvents.hooks();
    }

    public static final RegistryObject<Item> MUSKET = PiCoreRegistries.ITEMS.register("musket", () -> new MusketItem(new Item.Properties()));
    public static final RegistryObject<Item> REVOLVER = PiCoreRegistries.ITEMS.register("revolver",
          () -> new RevolverItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> HARPOON = PiCoreRegistries.ITEMS.register("harpoon", () -> new HarpoonItem(new Item.Properties()));
    public static final RegistryObject<Item> LEAD_BALL = PiCoreRegistries.ITEMS.register("lead_ball", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CARTRIDGE = PiCoreRegistries.ITEMS.register("cartridge", () -> new Item(new Item.Properties()));

    public static final RegistryObject<EntityType<HarpoonEntity>> HARPOON_ENTITY = PiEntityTypes.ENTITY_TYPES.register("harpoon",
          () -> EntityType.Builder.<HarpoonEntity>of(HarpoonEntity::new, MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(2)
                .build("harpoon"));

    public static final List<BlockEntry<GunRackBlock>> GUN_RACKS = new ArrayList<>();

    static {
        for (String w : fr.pioneers.home.HomeWoods.ALL) {
            GUN_RACKS.add(PiCoreRegistries.block(w + "_gun_rack", () -> new GunRackBlock(woodProps(w)),
                  b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.gun_rack")));
        }
    }

    public static final BlockEntry<TrophyBlock> DEER_TROPHY = trophy(TrophyBlock.Kind.DEER);
    public static final BlockEntry<TrophyBlock> BEAR_TROPHY = trophy(TrophyBlock.Kind.BEAR);
    public static final BlockEntry<TrophyBlock> SHARK_TROPHY = trophy(TrophyBlock.Kind.SHARK);

    private static BlockEntry<TrophyBlock> trophy(TrophyBlock.Kind k) {
        return PiCoreRegistries.block(k.id(), () -> new TrophyBlock(k, BlockBehaviour.Properties.of().mapColor(k == TrophyBlock.Kind.BEAR ? MapColor.COLOR_BROWN
                                                                                                                                            : MapColor.WOOD)
              .strength(0.8F).sound(k == TrophyBlock.Kind.BEAR ? SoundType.WOOL : SoundType.WOOD).noOcclusion().ignitedByLava()),
              b -> new DescribedBlockItem(b, new Item.Properties().rarity(Rarity.RARE), "description.pioneers." + k.id()));
    }

    private static BlockBehaviour.Properties woodProps(String wood) {
        boolean nether = wood.equals("crimson") || wood.equals("warped");
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F, 2.0F)
              .sound(wood.equals("bamboo") ? SoundType.BAMBOO_WOOD : wood.equals("cherry") ? SoundType.CHERRY_WOOD : nether ? SoundType.NETHER_WOOD
                    : SoundType.WOOD).noOcclusion();
        return nether ? p : p.ignitedByLava();
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<GunRackTile>> GUN_RACK_TILE = PiTileEntityTypes.VANILLA_TILES.register("gun_rack",
          () -> BlockEntityType.Builder.of(GunRackTile::new, GUN_RACKS.stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<TrophyTile>> TROPHY_TILE = PiTileEntityTypes.VANILLA_TILES.register("trophy",
          () -> BlockEntityType.Builder.of(TrophyTile::new, DEER_TROPHY.getBlock(), BEAR_TROPHY.getBlock(), SHARK_TROPHY.getBlock()).build(null));
}
