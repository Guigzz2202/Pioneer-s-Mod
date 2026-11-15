package fr.pioneers.core.block;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.46 (audit de performance) : base de toutes les tiles du mod.
 * <p>
 * {@link BlockEntity#setChanged()} previent aussi les six voisins (comparateurs) : six lectures de blocs a chaque appel. Les reservoirs
 * (energie, chaleur, pression) l'appellent a chaque changement de contenu, donc plusieurs fois par tick et par cable : ici, les voisins ne sont
 * prevenus que si le bloc donne vraiment un signal de comparateur. La sauvegarde du chunk est marquee comme avant.
 */
public abstract class PiBlockEntity extends BlockEntity {

    /** 1.9 (MAJ RP) : joueur qui a pose le bloc (XP automatique, statistiques de clan) ; inconnu (null) pour les blocs poses avant. */
    @Nullable
    private UUID owner;

    protected PiBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 1.9 (MAJ RP) : proprietaire connecte (null s'il est absent ou inconnu). */
    @Nullable
    public net.minecraft.server.level.ServerPlayer ownerOnline() {
        if (owner == null || !(level instanceof net.minecraft.server.level.ServerLevel sl)) {
            return null;
        }
        return sl.getServer().getPlayerList().getPlayer(owner);
    }

    /** 1.9 (MAJ RP) : facteur d'un modificateur de skill du proprietaire connecte (1 sinon). */
    public double ownerFactor(String key) {
        net.minecraft.server.level.ServerPlayer p = ownerOnline();
        return p == null ? 1 : fr.pioneers.core.profile.PioneerApi.factor(p, key);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID(OWNER, owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
    }

    public static final String OWNER = "pioneers_owner";

    @Override
    public void setChanged() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasAnalogOutputSignal()) {
            super.setChanged();
        } else {
            level.blockEntityChanged(worldPosition);
        }
    }
}
