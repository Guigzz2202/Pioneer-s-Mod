package fr.pioneers.farm.crop;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.farm.PiFarm;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Tuile d'une culture : genes, climat vu au dernier tick (affiche au regard), soins recus depuis la derniere recolte ({@link #FERTILIZED} :
 * compost, engrais, fertirrigation ; {@link #POLLINATED} : abeilles d'une ruche).
 * <p>
 * 1.0.5-beta (lot 1, specification §2.1, decision 4) : DELAI DE GRACE. {@link #hostileSince} = instant (temps du jour du monde) ou la
 * plante a ete vue hors climat pour la premiere fois d'affilee, -1 sinon ; elle fane un jour de jeu plus tard
 * ({@link FarmCropBlock#GRACE_TICKS}). Un simple horodatage : aucun cout par tick. L'ancien compteur de ticks hostiles (cle « hostile »)
 * n'est plus lu : le delai part du premier tick hors climat apres le chargement (aucune culture d'un monde existant ne meurt au chargement).
 * Envoye au client pour le compte a rebours de la fiche au regard.
 */
public class CropTile extends PiBlockEntity {

    private Genes genes = Genes.BASE;
    private Climate.Status climate = Climate.Status.IDEAL;
    private long hostileSince = -1;
    public static final int FERTILIZED = 1, POLLINATED = 2;
    private int care;

    public CropTile(BlockPos pos, BlockState state) {
        super(PiFarm.CROP_TILE.get(), pos, state);
    }

    public Genes genes() {
        return genes;
    }

    public void setGenes(Genes genes) {
        this.genes = genes;
        sync();
    }

    public Climate.Status climate() {
        return climate;
    }

    void setClimate(Climate.Status status) {
        if (status != climate) {
            climate = status;
            sync();
        }
    }

    /** Instant (temps du jour) du debut de la periode hors climat en cours, -1 si la plante est dans son climat. */
    public long hostileSince() {
        return hostileSince;
    }

    public void setHostileSince(long t) {
        if (t != hostileSince) {
            hostileSince = t;
            sync();
        }
    }

    public int care() {
        return care;
    }

    public boolean has(int flag) {
        return (care & flag) != 0;
    }

    /** Ajoute un soin ; vrai si c'est nouveau. */
    public boolean addCare(int flag) {
        if ((care & flag) != 0) {
            return false;
        }
        care |= flag;
        sync();
        return true;
    }

    public void clearCare() {
        if (care != 0) {
            care = 0;
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(Genes.TAG, genes.packed());
        tag.putByte("climate", (byte) climate.ordinal());
        tag.putLong("hostile_since", hostileSince);
        tag.putByte("care", (byte) care);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        genes = Genes.unpack(tag.getInt(Genes.TAG));
        int c = tag.getByte("climate");
        climate = Climate.Status.values()[Math.max(0, Math.min(c, Climate.Status.values().length - 1))];
        hostileSince = tag.contains("hostile_since") ? tag.getLong("hostile_since") : -1;
        care = tag.getByte("care");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt(Genes.TAG, genes.packed());
        tag.putByte("climate", (byte) climate.ordinal());
        tag.putByte("care", (byte) care);
        tag.putLong("hostile_since", hostileSince);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
