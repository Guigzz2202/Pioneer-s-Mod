package fr.pioneers.kinetics.kitchen;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.registries.PiKinetics;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.6 : registre des postes mecaniques de la cuisine (presse, baratte, hachoir, petrin, extracteur de miel). Blocs et objets par
 * {@link PiKinetics#part} (objet a rendu 3D de l'age mecanique), un seul type de tuile enregistre par {@link PiKinetics#tile} : le rendu des
 * pieces tournantes s'y applique comme aux autres machines. Charge par {@code Pioneers} apres la cuisine.
 */
public final class PiKitchenMachines {

    private PiKitchenMachines() {
    }

    public static void init() {
    }

    public static final Map<KitchenMachineType, BlockEntry<KitchenMachineBlock>> MACHINES = new EnumMap<>(KitchenMachineType.class);

    static BlockBehaviour.Properties props(KitchenMachineType type) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().noOcclusion();
        if (type == KitchenMachineType.HONEY_EXTRACTOR) {
            return p.mapColor(MapColor.METAL).strength(2.5F, 5.0F).sound(SoundType.METAL);
        }
        return p.mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD);
    }

    static {
        for (KitchenMachineType type : KitchenMachineType.values()) {
            MACHINES.put(type, PiKinetics.part(type.id(), () -> new KitchenMachineBlock(type, props(type))));
        }
    }

    public static BlockEntry<KitchenMachineBlock> machine(KitchenMachineType type) {
        return MACHINES.get(type);
    }

    public static final RegistryObject<BlockEntityType<KitchenMachineTile>> TILE = PiKinetics.tile("kitchen_machine", KitchenMachineTile::new,
          () -> MACHINES.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));
}
