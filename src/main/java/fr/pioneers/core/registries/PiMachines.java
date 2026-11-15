package fr.pioneers.core.registries;

import fr.pioneers.space.content.DataBankBlock;
import fr.pioneers.core.block.DescribedBlockItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.BlockRegistryObject;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.space.environment.AirlockDoorBlock;
import fr.pioneers.space.tile.TileEntityDataBank;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

/**
 * Machines ajoutees par la bêta de Pioneer's (blocs, tiles et menus au meme endroit) :
 * banques de donnees, machines de survie, tables d'assemblage specialisees, simulation planetaire.
 * <p>
 * Les registres differes appartiennent a {@link PiBlocks} et {@link PiTileEntityTypes} :
 * {@link #init()} doit etre appele avant leur enregistrement.
 */
public final class PiMachines {

    private PiMachines() {
    }

    public static void init() {
        //Charge la classe (et donc ses enregistrements differes)
    }

    // ------------------------------------------------------------------
    // Banques de donnees
    // ------------------------------------------------------------------

    /** Banques de donnees (socle : bloc {@link DataBankBlock}, interface standard). Memes ids qu'avant. */
    public static final Map<TileEntityDataBank.Tier, BlockEntry<DataBankBlock>> DATA_BANKS =
          new EnumMap<>(TileEntityDataBank.Tier.class);
    public static final Map<TileEntityDataBank.Tier, RegistryObject<BlockEntityType<TileEntityDataBank>>>
          DATA_BANK_TILES = new EnumMap<>(TileEntityDataBank.Tier.class);

    static {
        for (TileEntityDataBank.Tier tier : TileEntityDataBank.Tier.values()) {
            var block = PiCoreRegistries.block(tier.id(), () -> new DataBankBlock(
                  BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F)
                        .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(),
                  () -> DATA_BANK_TILES.get(tier).get(), (pos, state) -> new TileEntityDataBank(pos, state, tier)),
                  b -> new DescribedBlockItem(b, new Item.Properties(), "description.pioneers.data_bank"));
            DATA_BANKS.put(tier, block);
            DATA_BANK_TILES.put(tier, PiTileEntityTypes.VANILLA_TILES.register(tier.id(),
                  () -> BlockEntityType.Builder.of((pos, state) -> new TileEntityDataBank(pos, state, tier), block.getBlock())
                        .build(null)));
        }
    }

    // ------------------------------------------------------------------
    // Survie : sas (le diffuseur et le collecteur sont dans PiSpaceMachines)
    // ------------------------------------------------------------------

    /** Porte de sas etanche (deux blocs de haut). */
    public static final BlockRegistryObject<AirlockDoorBlock, DoubleHighBlockItem> AIRLOCK_DOOR =
          PiBlocks.BLOCKS.register("airlock_door", () -> new AirlockDoorBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                      .strength(5.0F, 12.0F).sound(SoundType.METAL).noOcclusion()
                      .pushReaction(PushReaction.DESTROY)),
                block -> new DoubleHighBlockItem(block, new Item.Properties()));

    // Simulateur du systeme solaire : remplace par le Simulateur spatial (PiSpaceSim)
}
