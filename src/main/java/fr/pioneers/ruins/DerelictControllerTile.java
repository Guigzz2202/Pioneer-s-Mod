package fr.pioneers.ruins;

import fr.pioneers.core.block.PiBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : tuile du Controleur delabre. La generation y ecrit le multibloc d'origine et la boite de la ruine
 * ({@link #setup}) ; au premier chargement cote serveur, elle cree le site dans {@link RuinSites}.
 */
public class DerelictControllerTile extends PiBlockEntity {

    private String machine = "";
    private BlockPos min = BlockPos.ZERO, max = BlockPos.ZERO;
    private int forward = 2;

    public DerelictControllerTile(BlockPos pos, BlockState state) {
        super(RuinBlocks.CONTROLLER_TILE.get(), pos, state);
    }

    /** Appele par la generation : cle du multibloc, boite de la ruine, facade (index 2D de la direction). */
    public void setup(String machine, BlockPos min, BlockPos max, int forward) {
        this.machine = machine;
        this.min = min.immutable();
        this.max = max.immutable();
        this.forward = forward;
        setChanged();
    }

    public String machine() {
        return machine;
    }

    public int forward() {
        return forward;
    }

    public int tier() {
        return getBlockState().getBlock() instanceof DerelictBlock d ? d.tier : 1;
    }

    @Nullable
    public RuinSites.Site site(ServerLevel level) {
        if (machine.isEmpty()) {
            return null;
        }
        return RuinSites.get(level).ensure(level, worldPosition.asLong(), tier(), machine, min, max);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) {
            site(sl);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("machine", machine);
        tag.putLong("min", min.asLong());
        tag.putLong("max", max.asLong());
        tag.putInt("forward", forward);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        machine = tag.getString("machine");
        min = BlockPos.of(tag.getLong("min"));
        max = BlockPos.of(tag.getLong("max"));
        forward = tag.contains("forward") ? tag.getInt("forward") : 2;
    }
}
