package fr.pioneers.tech.machine;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.machine.KitMachineBlock;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiTileEntityTypes;

/** Kit de machines de base (broyeur, four electrique, compresseur) en quatre tiers : {@code <tier>_<machine>}. */
public final class PiMachineKit {

    private PiMachineKit() {
    }

    public static void init() {
    }

    public static final Map<MachineKind, Map<CableTier, BlockEntry<KitMachineBlock>>> MACHINES = new EnumMap<>(MachineKind.class);

    static {
        for (MachineKind kind : MachineKind.values()) {
            Map<CableTier, BlockEntry<KitMachineBlock>> tiers = new EnumMap<>(CableTier.class);
            //1.0.7 (lot 3) : compacte MKV des machines de famille (broyeur, four, compresseur ; GeneratedTierFamilies.MKV_MACHINES)
            for (CableTier tier : fr.pioneers.api.machine.tier.GeneratedTierFamilies.MKV_MACHINES.contains(kind.name)
                  ? CableTier.values() : CableTier.MK.toArray(new CableTier[0])) {
                tiers.put(tier, PiCoreRegistries.block(kind.id(tier), () -> new KitMachineBlock(kind, tier, BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                      .lightLevel(state -> state.getValue(KitMachineBlock.ACTIVE) ? 8 : 0))));
            }
            MACHINES.put(kind, tiers);
        }
    }

    /**
     * API Machine (lot 2) : type de tile du kit, GARDE comme alias (les machines posees le portent dans leur sauvegarde) ; il fabrique la tile
     * generique des machines a procede ({@link AtelierTile}, procede du kit).
     */
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<AtelierTile>> PROCESSING_TILE = PiTileEntityTypes.VANILLA_TILES.register("kit_machine",
          () -> BlockEntityType.Builder.of(AtelierTile::create, blocks(false)).build(null));

    private static Block[] blocks(boolean generator) {
        List<Block> list = new ArrayList<>();
        MACHINES.forEach((kind, tiers) -> {
            if (kind.isGenerator() == generator) {
                tiers.values().forEach(entry -> list.add(entry.getBlock()));
            }
        });
        return list.toArray(Block[]::new);
    }
}
