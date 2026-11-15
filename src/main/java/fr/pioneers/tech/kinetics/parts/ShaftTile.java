package fr.pioneers.tech.kinetics.parts;

import fr.pioneers.core.block.Covers;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticMaterial;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.core.registries.PiKinetics;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Arbre de transmission ; 1.0.4-beta (lot 27) : porte la cache eventuelle ({@link Covers}), sauvee et envoyee au client. */
public class ShaftTile extends KineticTile implements Covers.Coverable {

    @Nullable
    private BlockState cover;

    public ShaftTile(BlockPos pos, BlockState state) {
        super(PiKinetics.SHAFT_TILE.get(), pos, state);
    }

    @Override
    public KineticMaterial material() {
        return getBlockState().getBlock() instanceof ShaftBlock shaft ? shaft.material : KineticMaterial.WOOD;
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == AxisKineticBlock.axis(getBlockState()) ? 1 : 0;
    }

    @Override
    public double inertia() {
        return material().shaftInertia;
    }

    @Override
    protected List<ItemStack> brokenDrops() {
        return material() == KineticMaterial.WOOD ? List.of(new ItemStack(Items.STICK, 2)) : List.of(new ItemStack(PiVanillaMaterials.item("iron_nugget"), 4));
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 27) : cache
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState cover() {
        return cover;
    }

    @Override
    public void setCover(@Nullable BlockState cover) {
        if (this.cover != cover) {
            this.cover = cover;
            markClientsDirty();
        }
    }

    /**
     * La cache ne change qu'a la pose ou a la casse ({@link #markClientsDirty}) : l'arbre n'a pas a renvoyer le paquet du bloc a chaque
     * synchronisation de la rotation (elle part dans le paquet du reseau), malgre {@link #writeClient}.
     */
    @Override
    protected boolean hasOwnClientData() {
        return false;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        Covers.save(tag, cover);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        BlockState received = Covers.load(tag);
        if (received != cover) {
            cover = received;
            Covers.changedOnClient(this);
        }
    }

    @NotNull
    @Override
    public ModelData getModelData() {
        return cover == null ? ModelData.EMPTY : ModelData.builder().with(Covers.COVER, cover).build();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        Covers.save(tag, cover);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        cover = Covers.load(tag);
    }
}
