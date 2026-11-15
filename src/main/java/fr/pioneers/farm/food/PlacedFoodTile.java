package fr.pioneers.farm.food;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9.2 (retours de Guillaume : « tous les plats et ingredients doivent etre plaçables ») : contenu d'un tas pose ({@link PlacedFoodBlock}) :
 * UNE pile d'un meme objet (NBT compris : bocal d'origine, taille d'un poisson...), 1 a {@link PlacedFoodBlock#capacity} unites. NBT
 * {@code pile}. Rien ne se perd : casser le bloc rend la pile entiere.
 */
public class PlacedFoodTile extends PiBlockEntity {

    private ItemStack pile = ItemStack.EMPTY;

    public PlacedFoodTile(BlockPos pos, BlockState state) {
        super(PlacedFoodBlock.TILE.get(), pos, state);
    }

    public ItemStack pile() {
        return pile;
    }

    /**
     * 1.0.5-beta (lot 28, retour 36) : un requin pose deborde de son bloc (26 px de long) : boite de rendu elargie d'un demi-bloc de chaque
     * cote (avant : la forme de collision, le bout du requin disparaissait au bord de l'ecran).
     */
    @NotNull
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(0.5, 0.25, 0.5);
    }

    public int count() {
        return pile.getCount();
    }

    /** Peut-on y ajouter une unite de {@code stack} ? */
    public boolean accepts(ItemStack stack) {
        return pile.isEmpty() || ItemStack.isSameItemSameTags(pile, stack) && pile.getCount() < PlacedFoodBlock.capacity(pile);
    }

    public void add(ItemStack one) {
        if (pile.isEmpty()) {
            pile = one.copyWithCount(1);
        } else {
            pile.grow(1);
        }
        sync();
    }

    /** Retire {@code n} unites (au plus ce qu'il y a) ; le bloc disparait quand le tas est vide. */
    public ItemStack take(int n) {
        ItemStack out = pile.split(Math.max(1, n));
        sync();
        if (pile.isEmpty() && level != null && !level.isClientSide) {
            level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (!pile.isEmpty()) {
            //Compte sur un int : une pile peut depasser 64 si un jour la capacite le permet
            CompoundTag t = pile.save(new CompoundTag());
            t.putInt("n", pile.getCount());
            tag.put("pile", t);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("pile")) {
            CompoundTag t = tag.getCompound("pile");
            pile = ItemStack.of(t);
            if (t.contains("n")) {
                pile.setCount(t.getInt("n"));
            }
        } else {
            pile = ItemStack.EMPTY;
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
