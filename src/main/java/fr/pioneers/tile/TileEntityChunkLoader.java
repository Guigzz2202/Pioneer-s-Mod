package fr.pioneers.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.registries.PiSpaceMachines;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chunkloader spatial (socle) : garde charges les chunks autour de lui (rayon 0 a 3, soit 1 a 49 chunks). Plus le rayon est grand, plus
 * il consomme : 100, 600, 1 600, 3 000 puis 5 000 FE/t ; le tier (MKI a MKIV) borne le rayon (1 a 4). Sans energie (ou arrete par la redstone), il relache les chunks.
 * <p>
 * Interface : 4 rayon voulu, 5 rayon charge (-1 : rien), 6 cout du rayon voulu (FE/t). Actions : {@link #INCREASE}, {@link #DECREASE}.
 * <p>
 * API Machine (lot 8c) : logique dans {@link ChunkLoaderUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class TileEntityChunkLoader extends MachineTile {

    /** Rayon le plus grand (MKIV) ; chaque tier ouvre un anneau de chunks de plus : MKI 3x3, MKII 5x5, MKIII 7x7, MKIV 9x9. */
    public static final int MAX_RADIUS = 4;
    public static final long[] COST_FE = {100, 600, 1_600, 3_000, 5_000};
    public static final long ENERGY_STORAGE_FE = 600_000;
    public static final int INCREASE = CUSTOM, DECREASE = CUSTOM + 1;
    public static final int BATTERY_X = StandardLayout.BATTERY_X, BATTERY_Y = StandardLayout.BATTERY_Y;

    /** API Machine (lot 8c) : la logique (batterie, consommation, chunks forces) est dans l'unite portee. */
    private final ChunkLoaderUnit unit;
    private boolean unloading;

    public TileEntityChunkLoader(BlockPos pos, BlockState state) {
        super(PiSpaceMachines.CHUNK_LOADER_TILE.get(), pos, state, ENERGY_STORAGE_FE * (1 + TieredMachineBlock.tierOf(state)),
              (int) (COST_FE[maxRadius(TieredMachineBlock.tierOf(state))] * 4));
        unit = carry(new ChunkLoaderUnit(access()));
    }

    @Override
    protected Set<SideMode> allowedModes() {
        return EnumSet.of(SideMode.NONE, SideMode.INPUT);
    }

    @Override
    protected void tick() {
    }

    @Override
    public void onChunkUnloaded() {
        //Dechargement normal du monde : Forge garde les tickets, on ne relache rien
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        //Bloc casse : on relache tous les chunks
        if (!unloading && level != null && !level.isClientSide) {
            unit.setLoaded(-1);
        }
        super.setRemoved();
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == INCREASE || action == DECREASE) {
            unit.step(action == INCREASE);
        }
    }

    public static long getCost(int radius) {
        return COST_FE[Math.max(0, Math.min(MAX_RADIUS, radius))];
    }

    /** Rayon le plus grand d'un tier (0 = MKI). */
    public static int maxRadius(int tier) {
        return Math.min(MAX_RADIUS, tier + 1);
    }

    public int maxRadius() {
        return maxRadius(machineTier());
    }

    public static int chunkCount(int radius) {
        return (2 * radius + 1) * (2 * radius + 1);
    }

    public int getRadius() {
        return unit.radius;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.serviceParts(true, StandardLayout.setting(StandardLayout.WIDTH, StandardLayout.STEP_A), StandardLayout.setting(StandardLayout.WIDTH, StandardLayout.STEP_B));
    }

    /** API Machine (lot 4) : valeurs NOMMEES (lot 8c : declarees par l'unite, memes cles et indices). */
    public static final UiValues.Key RADIUS = ChunkLoaderUnit.RADIUS, LOADED = ChunkLoaderUnit.LOADED, COST = ChunkLoaderUnit.COST;
}
