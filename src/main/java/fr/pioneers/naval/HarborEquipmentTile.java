package fr.pioneers.naval;

import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.registries.PiNaval;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.14 : equipement de quai. Relie a sa capitainerie ({@link #master}) quand le quai se forme ; il expose aux cables la reserve de la
 * capitainerie qui correspond a sa ressource ({@link HarborEquipment#exposes}). Etat recopie par la capitainerie pour l'animation :
 * {@link #deployed} (un navire qu'il sert est amarre : bras deploye, grue tournee vers lui), {@link #working} (transfert en cours),
 * {@link #reach} et {@link #drop} (distance et denivele jusqu'au pont du navire, en blocs), {@link #tier} (couleurs du tier du port).
 */
public class HarborEquipmentTile extends PiBlockEntity {

    @Nullable
    private BlockPos master;
    public int tier;
    public boolean deployed, working;
    public float reach = 1.5F, drop;
    private int ticks;

    //Client : animation
    public float deploy, deployO, phase, phaseO;

    public HarborEquipmentTile(BlockPos pos, BlockState state) {
        super(PiNaval.EQUIPMENT_TILE.get(), pos, state);
    }

    public HarborEquipment kind() {
        return getBlockState().getBlock() instanceof HarborEquipmentBlock b ? b.kind : HarborEquipment.CRANE;
    }

    public Direction water() {
        return getBlockState().hasProperty(HarborEquipmentBlock.FACING) ? getBlockState().getValue(HarborEquipmentBlock.FACING) : Direction.NORTH;
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    /** Appele par la capitainerie a chaque releve du quai. */
    void link(@Nullable BlockPos controller, int tier) {
        if (!Objects.equals(master, controller) || this.tier != tier) {
            master = controller;
            this.tier = tier;
            if (controller == null) {
                deployed = false;
                working = false;
            }
            sync();
            //Les cables voisins reprennent la reserve de la capitainerie (ou la perdent)
            invalidateCaps();
            reviveCaps();
            if (level != null && !level.isClientSide) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
    }

    /** Etat d'animation pousse par la capitainerie (seulement quand il change). */
    void animate(boolean deployed, boolean working, float reach, float drop) {
        if (this.deployed != deployed || this.working != working || Math.abs(this.reach - reach) > 0.01F || Math.abs(this.drop - drop) > 0.01F) {
            this.deployed = deployed;
            this.working = working;
            this.reach = reach;
            this.drop = drop;
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    private HarborTile controller() {
        if (master == null || level == null || !level.isLoaded(master)) {
            return null;
        }
        return level.getBlockEntity(master) instanceof HarborTile h ? h : null;
    }

    void serverTick() {
        if (++ticks % 40 != 7 || master == null) {
            return;
        }
        HarborTile h = controller();
        if (h == null && level != null && level.isLoaded(master) || h != null && !h.hasEquipment(worldPosition)) {
            link(null, tier);
        }
    }

    void clientTick() {
        deployO = deploy;
        phaseO = phase;
        float target = deployed ? 1 : 0;
        deploy += Math.max(-0.035F, Math.min(0.035F, target - deploy));
        if (working && deploy > 0.95F) {
            phase += 1;
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (kind().exposes(cap)) {
            HarborTile h = controller();
            //1.9 (MAJ RP, bloc 4) : ni vers une capitainerie de l'autre cote d'une frontiere de claim refusee
            if (h != null && level != null && ClanApi.canTransfer(level, worldPosition, h.getBlockPos())) {
                return h.getCapability(cap, null);
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (master != null) {
            tag.putLong("master", master.asLong());
        }
        tag.putByte("tier", (byte) tier);
        tag.putBoolean("deployed", deployed);
        tag.putBoolean("working", working);
        tag.putFloat("reach", reach);
        tag.putFloat("drop", drop);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        master = tag.contains("master") ? BlockPos.of(tag.getLong("master")) : null;
        tier = tag.getByte("tier");
        deployed = tag.getBoolean("deployed");
        working = tag.getBoolean("working");
        reach = tag.contains("reach") ? tag.getFloat("reach") : 1.5F;
        drop = tag.getFloat("drop");
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4, 4, 4);
    }
}
