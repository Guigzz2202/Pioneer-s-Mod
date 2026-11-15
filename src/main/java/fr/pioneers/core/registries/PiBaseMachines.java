package fr.pioneers.core.registries;

import fr.pioneers.core.common.projector.Projections;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.space.ItemRocket;
import fr.pioneers.space.LaunchPadStructure;
import fr.pioneers.space.base.BaseBlueprint;
import fr.pioneers.space.base.DronePadBlock;
import fr.pioneers.space.tile.TileEntityDroneBay;
import fr.pioneers.space.tile.TileEntityGreenhouse;
import fr.pioneers.space.tile.TileEntityLaunchPad;
import fr.pioneers.space.tile.TileEntityMiningOutpost;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

/**
 * Bases planetaires passees au socle (lot D) : controleur de serre (puis avant-poste, drone bay, pas de tir). Les blocs de structure
 * (coques, verres, ports) restent dans {@link PiBlocks}.
 */
public final class PiBaseMachines {

    private PiBaseMachines() {
    }

    public static void init() {
        //1.0 (lot 4) : plans projetables des bases construites (serre, garage du rover, baie de drones)
        Projections.addProvider(BaseBlueprint::forBlock);
    }

    /** Aire d'atterrissage d'un drone (baie de drones construite). */
    public static final BlockEntry<DronePadBlock> DRONE_PAD = PiCoreRegistries.block("drone_pad",
          () -> new DronePadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F, 9.0F)
                .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 4)));

    public static final BlockEntry<MachineBlock> GREENHOUSE_CONTROLLER = controller("greenhouse_controller", MapColor.COLOR_GREEN,
          () -> PiBaseMachines.GREENHOUSE_CONTROLLER_TILE.get(), TileEntityGreenhouse::new);
    public static final RegistryObject<BlockEntityType<TileEntityGreenhouse>> GREENHOUSE_CONTROLLER_TILE = PiScienceMachines.tile("greenhouse_controller",
          TileEntityGreenhouse::new, GREENHOUSE_CONTROLLER);

    public static final BlockEntry<MachineBlock> MINING_OUTPOST_CONTROLLER = controller("mining_outpost_controller", MapColor.COLOR_ORANGE,
          () -> PiBaseMachines.MINING_OUTPOST_CONTROLLER_TILE.get(), TileEntityMiningOutpost::new);
    public static final RegistryObject<BlockEntityType<TileEntityMiningOutpost>> MINING_OUTPOST_CONTROLLER_TILE = PiScienceMachines.tile(
          "mining_outpost_controller", TileEntityMiningOutpost::new, MINING_OUTPOST_CONTROLLER);

    /** Baie de drones (geantes gazeuses) : bloc unique a modele 3D. */
    public static final BlockEntry<MachineBlock> DRONE_BAY = PiCoreRegistries.block("drone_bay", () -> new MachineBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_ORANGE).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
          .lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 8 : 0), () -> PiBaseMachines.DRONE_BAY_TILE.get(), TileEntityDroneBay::new));
    public static final RegistryObject<BlockEntityType<TileEntityDroneBay>> DRONE_BAY_TILE = PiScienceMachines.tile("drone_bay", TileEntityDroneBay::new,
          DRONE_BAY);

    /** Coeur du complexe de lancement : avec une fusee en main, on la pose au lieu d'ouvrir l'interface. */
    public static final BlockEntry<MachineBlock> LAUNCH_PAD_CORE = PiCoreRegistries.block("launch_pad_core", () -> new MachineBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 8 : 0), () -> PiBaseMachines.LAUNCH_PAD_CORE_TILE.get(), TileEntityLaunchPad::new) {
        @NotNull
        @Override
        public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
              @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
            if (player.getItemInHand(hand).getItem() instanceof ItemRocket) {
                return InteractionResult.PASS;
            }
            return super.use(state, level, pos, player, hand, hit);
        }

        /** 0.20.34 : cœur retire : ses plaques et sa tour perdent la couleur du tier. */
        @Override
        @SuppressWarnings("deprecation")
        public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
            super.onRemove(state, level, pos, newState, moving);
            if (!level.isClientSide && !newState.is(state.getBlock())) {
                LaunchPadStructure.unpaint(level, pos);
            }
        }
    });
    public static final RegistryObject<BlockEntityType<TileEntityLaunchPad>> LAUNCH_PAD_CORE_TILE = PiScienceMachines.tile("launch_pad_core",
          TileEntityLaunchPad::new, LAUNCH_PAD_CORE);

    /** Controleur de structure : un cube plein (face avant = ecran). */
    static BlockEntry<MachineBlock> controller(String name, MapColor color, Supplier<? extends BlockEntityType<?>> type,
          BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        return PiCoreRegistries.block(name, () -> new MachineBlock(BlockBehaviour.Properties.of().mapColor(color).strength(3.5F, 9.0F).sound(SoundType.METAL)
              .requiresCorrectToolForDrops().lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 8 : 0), type, factory));
    }
}
