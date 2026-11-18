package fr.pioneers.tech.bronze.autofoundry;

import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.tech.bronze.AirBlastReceiver;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.tech.bronze.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4) : tile d'une piece de la fonderie automatique (foyer ou cuve). Sans etat propre : elle retrouve son hote par le registre
 * des multiblocs maison ({@link MultiblockPort#house}) et lui prete
 * <ul>
 *     <li>les CASES de la fonderie aux entonnoirs et au cable logistique (capacite d'objets du controleur : le combustible va au foyer, le
 *     metal aux cases de fusion ; rien ne sort par une piece) ;</li>
 *     <li>l'AIR d'un soufflet mecanique ou d'une soufflerie a vapeur tourne vers elle ({@link AirBlastReceiver}).</li>
 * </ul>
 * Structure non formee : aucune capacite, l'air se perd.
 */
public class FoundryPartTile extends PiBlockEntity implements AirBlastReceiver {

    public FoundryPartTile(BlockPos pos, BlockState state) {
        super(PiBronze.FOUNDRY_PART_TILE.get(), pos, state);
    }

    /** Fonderie formee qui contient cette piece, ou null. */
    @Nullable
    public AutoFoundryTile host() {
        AutoFoundryTile host = MultiblockPort.house(level, worldPosition, AutoFoundryTile.class);
        return host != null && host.formed() ? host : null;
    }

    @Override
    public void blast(double strength) {
        AutoFoundryTile host = host();
        if (host != null) {
            host.blast(strength);
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            AutoFoundryTile host = host();
            if (host != null) {
                return host.inputCapability().cast();
            }
        }
        return super.getCapability(cap, side);
    }
}
