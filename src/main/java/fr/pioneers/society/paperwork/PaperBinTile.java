package fr.pioneers.society.paperwork;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.block.PiBlockEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 16) : tuile d'un bac a papiers : contenu ({@link PaperBinKind#slots} cases, NBT {@code Items}), nom du proprietaire (le
 * proprietaire lui-meme est celui de {@link PiBlockEntity}, pose a la pose du bloc). Acces : {@link PaperBinRules#mayUse} (proprietaire,
 * membres de son clan, operateurs). Le nombre de papiers (et de lettres non lues) part au client pour le modele et l'infobulle au regard.
 */
public class PaperBinTile extends PiBlockEntity implements MenuProvider {

    public final PaperBinKind kind;
    public final SimpleContainer items;
    private String ownerName = "";
    /** Nombre de papiers (client : pour l'infobulle au regard). */
    private int count;

    public PaperBinTile(BlockPos pos, BlockState state) {
        super(PiPaperwork.TILE.get(), pos, state);
        this.kind = state.getBlock() instanceof PaperBinBlock b ? b.kind : PaperBinKind.LETTERS;
        this.items = new SimpleContainer(kind.slots) {
            @Override
            public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
                return kind.accepts(stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                PaperBinTile.this.contentsChanged();
            }
        };
    }

    void contentsChanged() {
        int n = 0;
        for (int i = 0; i < items.getContainerSize(); i++) {
            if (!items.getItem(i).isEmpty()) {
                n++;
            }
        }
        boolean sync = n != count;
        count = n;
        setChanged();
        if (sync && level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public int count() {
        return count;
    }

    public String ownerName() {
        return ownerName;
    }

    public void claim(Player player) {
        setOwner(player.getUUID());
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Ce joueur peut ouvrir, prendre et deposer (le premier qui ouvre un bac sans proprietaire le prend). */
    public boolean mayUse(ServerPlayer player) {
        UUID owner = owner();
        Clan oc = owner == null ? null : ClanApi.clanOf(player.getServer(), owner);
        Clan pc = ClanApi.clanOf(player.getServer(), player.getUUID());
        return PaperBinRules.mayUse(owner, player.getUUID(), oc == null ? null : oc.id, pc == null ? null : pc.id, player.hasPermissions(2));
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, itemsList(), true);
        tag.putString("ownerName", ownerName);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        net.minecraft.core.NonNullList<ItemStack> l = net.minecraft.core.NonNullList.withSize(kind.slots, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, l);
        for (int i = 0; i < kind.slots; i++) {
            items.setItem(i, l.get(i));
        }
        ownerName = tag.getString("ownerName");
        count = tag.contains("count") ? tag.getInt("count") : count;
    }

    private net.minecraft.core.NonNullList<ItemStack> itemsList() {
        net.minecraft.core.NonNullList<ItemStack> l = net.minecraft.core.NonNullList.withSize(kind.slots, ItemStack.EMPTY);
        for (int i = 0; i < kind.slots; i++) {
            l.set(i, items.getItem(i));
        }
        return l;
    }

    // ------------------------------------------------------------------ client : nombre de papiers et proprietaire seulement
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("count", count);
        t.putString("ownerName", ownerName);
        return t;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        count = tag.getInt("count");
        ownerName = tag.getString("ownerName");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag());
        }
    }

    // ------------------------------------------------------------------ menu
    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.pioneers." + kind.id);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new PaperBinMenu(id, inv, kind, items, this);
    }

    public Container container() {
        return items;
    }
}
