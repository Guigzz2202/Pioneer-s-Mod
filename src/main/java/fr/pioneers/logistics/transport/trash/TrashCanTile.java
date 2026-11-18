package fr.pioneers.logistics.transport.trash;

import fr.pioneers.api.PioneersAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 22) : tuile de la POUBELLE : aucune donnee, aucun tick ; seulement les capacites des puits {@link TrashSink} sur TOUTES
 * les faces (et sans face) : objets, fluides, gaz, energie. Jamais de donnees ({@link #RESOURCES} : la liste exacte, lue par les controles
 * {@code tools/check_p105_22.py} et {@code check_p105_27.py}). L'interface ({@link TrashCanMenu}) n'est qu'une case ou deposer une pile et
 * une case ou vider un recipient.
 * <p>
 * 1.0.5-beta (lot 27, decision de Guillaume : « la poubelle ne doit pas prendre la chaleur, de plus l'air n'est pas une ressource ») : plus
 * de capacite {@code HEAT_HANDLER} ni {@code PRESSURE_HANDLER} : le canal chaleur et le canal pression du cable logistique, les buffers de
 * chaleur et d'air, les ports de multibloc ne s'y raccordent plus.
 */
public class TrashCanTile extends BlockEntity implements MenuProvider {

    /** Ressources acceptees (ordre de la fiche au regard et du guide) ; la donnee n'y est jamais. */
    public static final String[] RESOURCES = {"items", "fluids", "gases", "energy"};

    private LazyOptional<?> items, fluids, gases, energy;

    public TrashCanTile(BlockPos pos, BlockState state) {
        super(PiTrash.TILE.get(), pos, state);
        caps();
    }

    private void caps() {
        items = LazyOptional.of(() -> TrashSink.ITEMS);
        fluids = LazyOptional.of(() -> TrashSink.FLUIDS);
        gases = LazyOptional.of(() -> TrashSink.GASES);
        energy = LazyOptional.of(() -> TrashSink.ENERGY);
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        caps();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (!remove) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                return items.cast();
            }
            if (cap == ForgeCapabilities.FLUID_HANDLER) {
                return fluids.cast();
            }
            if (cap == PioneersAPI.GAS_HANDLER) {
                return gases.cast();
            }
            if (cap == ForgeCapabilities.ENERGY) {
                return energy.cast();
            }
        }
        //PioneersAPI.DATA_HANDLER : jamais (la poubelle refuse la donnee) ; HEAT_HANDLER et PRESSURE_HANDLER : jamais depuis le lot 27
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
        fluids.invalidate();
        gases.invalidate();
        energy.invalidate();
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pioneers.trash_can");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new TrashCanMenu(id, inventory, getBlockPos());
    }
}
