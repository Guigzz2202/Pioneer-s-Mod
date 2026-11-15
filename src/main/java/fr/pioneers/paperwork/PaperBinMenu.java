package fr.pioneers.paperwork;

import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.economy.mail.Letters;
import fr.pioneers.economy.mail.MailData;
import fr.pioneers.ruins.LoreItem;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 16) : menu d'un bac a papiers. Les cases du bac sont des cases du menu (synchronisees par Minecraft, NBT compris : l'ecran
 * lit expediteur, objet, date, lu / non lu, entree du Codex) mais INACTIVES (ni dessinees ni cliquables) : l'ecran les montre en liste. Les 36
 * cases du joueur sont normales ; Maj + clic d'un papier accepte le range. Les autres gestes passent par {@link RpActions#PAPER_BIN}, revus
 * par le serveur (menu ouvert, acces, case) : prendre, lire (lettre marquee lue ; ecrit du Codex : lecture et classement au Codex), tout
 * ranger depuis l'inventaire, relever la boite aux lettres.
 */
public class PaperBinMenu extends AbstractContainerMenu {

    public static final int W = 256, H = 232, INV_X = 48, INV_Y = 150, SLOT = 18;
    public final PaperBinKind kind;
    private final Container bin;
    @Nullable
    private final PaperBinTile tile;

    static {
        RpNet.onServer(RpActions.PAPER_BIN, PaperBinMenu::request);
    }

    /** Charge la classe (et son traitement reseau). */
    public static void init() {
    }

    public PaperBinMenu(int id, Inventory inv, PaperBinKind kind, Container bin, @Nullable PaperBinTile tile) {
        super(PiPaperwork.MENU.get(), id);
        this.kind = kind;
        this.bin = bin;
        this.tile = tile;
        for (int i = 0; i < kind.slots; i++) {
            addSlot(new Slot(bin, i, -10000, -10000) {
                @Override
                public boolean isActive() {
                    return false;
                }

                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return PaperBinMenu.this.kind.accepts(stack);
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * SLOT, INV_Y + r * SLOT));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, INV_X + c * SLOT, INV_Y + 58));
        }
    }

    /** Client : sorte du bac lue dans le tampon d'ouverture. */
    public static PaperBinMenu client(int id, Inventory inv, FriendlyByteBuf buf) {
        PaperBinKind kind = PaperBinKind.byOrdinal(buf.readByte());
        return new PaperBinMenu(id, inv, kind, new SimpleContainer(kind.slots), null);
    }

    public ItemStack paper(int slot) {
        return slot >= 0 && slot < kind.slots ? bin.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        if (tile == null) {
            return true;
        }
        BlockPos pos = tile.getBlockPos();
        return !tile.isRemoved() && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 && (!(p instanceof ServerPlayer sp)
                                                                                                                  || tile.mayUse(sp));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < kind.slots) {
            if (!moveItemStackTo(stack, kind.slots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!kind.accepts(stack) || !moveItemStackTo(stack, 0, kind.slots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    // ------------------------------------------------------------------ gestes (serveur)
    private static void request(ServerPlayer player, CompoundTag t) {
        if (!(player.containerMenu instanceof PaperBinMenu m) || m.containerId != t.getInt("c") || m.tile == null || !m.tile.mayUse(player)) {
            return;
        }
        int i = t.getInt("i");
        switch (t.getString("op")) {
            case "take" -> m.take(player, i);
            case "read" -> m.read(player, i);
            case "deposit" -> deposit(player.getInventory(), m.bin, m.kind);
            case "mailbox" -> {
                if (m.kind == PaperBinKind.LETTERS) {
                    fromMailbox(player, m.bin);
                }
            }
            default -> {
            }
        }
        m.broadcastChanges();
    }

    /** Prendre la case {@code i} : dans l'inventaire (refuse s'il est plein). */
    public boolean take(ServerPlayer player, int i) {
        ItemStack s = paper(i);
        if (s.isEmpty()) {
            return false;
        }
        ItemStack copy = s.copy();
        if (!player.getInventory().add(copy)) {
            return false;
        }
        bin.setItem(i, copy.isEmpty() ? ItemStack.EMPTY : copy);
        bin.setChanged();
        return true;
    }

    /** Lire la case {@code i} : la lettre devient lue ; l'ecrit du Codex est lu (Codex, evenement, ecran de lecture envoye au client). */
    public void read(ServerPlayer player, int i) {
        ItemStack s = paper(i);
        if (s.isEmpty()) {
            return;
        }
        if (Letters.data(s) != null && !Letters.read(s)) {
            Letters.markRead(s);
            bin.setChanged();
        } else if (s.getItem() instanceof LoreItem) {
            LoreItem.readStored(player, s);
        }
    }

    /** Range tous les papiers acceptes de l'inventaire ; renvoie le nombre d'objets ranges. PUR (inventaire et bac donnes). */
    public static int deposit(Container inv, Container bin, PaperBinKind kind) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (kind.accepts(s)) {
                int before = s.getCount();
                ItemStack rest = insert(bin, s.copy(), kind);
                inv.setItem(i, rest);
                n += before - rest.getCount();
            }
        }
        if (n > 0) {
            bin.setChanged();
            inv.setChanged();
        }
        return n;
    }

    /** Releve la boite aux lettres du joueur : lettres et factures passent dans le bac (le reste du courrier y reste). */
    public static int fromMailbox(ServerPlayer player, Container bin) {
        MailData data = MailData.get(player.getServer());
        MailData.Box box = data.box(player.getUUID());
        int n = deposit(box.items, bin, PaperBinKind.LETTERS);
        if (n > 0) {
            data.setDirty();
        }
        return n;
    }

    /** Pose {@code s} dans le bac (piles d'abord, puis cases vides) ; renvoie ce qui n'a pas tenu. */
    static ItemStack insert(Container bin, ItemStack s, PaperBinKind kind) {
        for (int pass = 0; pass < 2 && !s.isEmpty(); pass++) {
            for (int i = 0; i < kind.slots && !s.isEmpty(); i++) {
                ItemStack in = bin.getItem(i);
                if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameTags(in, s) && in.getCount() < in.getMaxStackSize()) {
                    int k = Math.min(s.getCount(), in.getMaxStackSize() - in.getCount());
                    in.grow(k);
                    s.shrink(k);
                } else if (pass == 1 && in.isEmpty()) {
                    bin.setItem(i, s.copy());
                    s = ItemStack.EMPTY;
                }
            }
        }
        return s;
    }

    /** Client : demande au serveur ({@code op} : take, read, deposit, mailbox). */
    public void ask(String op, int i) {
        CompoundTag t = new CompoundTag();
        t.putInt("c", containerId);
        t.putString("op", op);
        t.putInt("i", i);
        RpNet.toServer(RpActions.PAPER_BIN, t);
    }
}
