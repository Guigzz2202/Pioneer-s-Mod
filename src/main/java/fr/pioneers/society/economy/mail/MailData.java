package fr.pioneers.society.economy.mail;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 29) : les BOITES AUX LETTRES des joueurs (donnee de monde de l'Overworld, {@code pioneers_mail}) : une boite par joueur
 * ({@link MailRules}) : ses {@link MailRules#SLOTS} cases (les memes pour tous ses blocs « boite aux lettres »), les lettres qui attendent
 * une case libre, le nombre de lettres non lues. Un joueur sans bloc pose recoit quand meme son courrier : il l'attend ici.
 */
public final class MailData extends SavedData {

    public static final String NAME = "pioneers_mail";
    private final Map<UUID, Box> boxes = new HashMap<>();

    /** La boite d'un joueur : cases, lettres en attente, non lues, nom (pour les messages). */
    public final class Box {

        public final UUID owner;
        public String name = "";
        public final SimpleContainer items = new SimpleContainer(MailRules.SLOTS);
        public final Deque<ItemStack> pending = new ArrayDeque<>();
        public int unread;

        Box(UUID owner) {
            this.owner = owner;
            items.addListener(c -> setDirty());
        }

        /** Range une lettre dans la premiere case libre ; faux si la boite est pleine. */
        boolean store(ItemStack letter) {
            boolean[] empty = new boolean[items.getContainerSize()];
            for (int i = 0; i < empty.length; i++) {
                empty[i] = items.getItem(i).isEmpty();
            }
            int slot = MailRules.firstEmpty(empty);
            if (slot < 0) {
                return false;
            }
            items.setItem(slot, letter);
            return true;
        }

        /** Fait entrer les lettres en attente tant qu'il y a de la place ; rend le nombre entre. */
        public int flush() {
            int n = 0;
            while (!pending.isEmpty() && store(pending.peekFirst())) {
                pending.pollFirst();
                n++;
            }
            if (n > 0) {
                setDirty();
            }
            return n;
        }

        /** Lettres dans les cases (sans celles qui attendent). */
        public int letters() {
            int n = 0;
            for (int i = 0; i < items.getContainerSize(); i++) {
                n += items.getItem(i).is(PiMail.LETTER.get()) ? 1 : 0;
            }
            return n;
        }
    }

    public static MailData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MailData::load, MailData::new, NAME);
    }

    /** La boite du joueur (creee vide a la demande). */
    public Box box(UUID owner) {
        return boxes.computeIfAbsent(owner, Box::new);
    }

    @Nullable
    public Box find(UUID owner) {
        return boxes.get(owner);
    }

    /** Oublie la boite d'un joueur (GameTests : joueurs factices). */
    public void forget(UUID owner) {
        if (boxes.remove(owner) != null) {
            setDirty();
        }
    }

    /** Depose une lettre : premiere case libre, sinon en attente (les plus anciennes au-dela de PENDING_MAX sont perdues) ; +1 non lu. */
    public Box deliver(UUID to, ItemStack letter) {
        Box b = box(to);
        b.flush();
        if (!b.store(letter)) {
            b.pending.addLast(letter);
            while (b.pending.size() > MailRules.PENDING_MAX) {
                b.pending.pollFirst();
            }
        }
        b.unread = Math.min(MailRules.UNREAD_MAX, b.unread + 1);
        setDirty();
        return b;
    }

    /** Le proprietaire ouvre une de ses boites : le courrier en attente entre, plus rien de non lu. */
    public void opened(UUID owner) {
        Box b = box(owner);
        b.flush();
        if (b.unread != 0) {
            b.unread = 0;
            setDirty();
        }
    }

    // ------------------------------------------------------------------ NBT
    public static MailData load(CompoundTag tag) {
        MailData d = new MailData();
        for (Tag x : tag.getList("boxes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            if (!c.hasUUID("u")) {
                continue;
            }
            Box b = d.box(c.getUUID("u"));
            b.name = c.getString("name");
            b.unread = c.getInt("unread");
            for (Tag y : c.getList("items", Tag.TAG_COMPOUND)) {
                CompoundTag s = (CompoundTag) y;
                int slot = s.getByte("Slot") & 255;
                if (slot < b.items.getContainerSize()) {
                    b.items.setItem(slot, ItemStack.of(s));
                }
            }
            for (Tag y : c.getList("pending", Tag.TAG_COMPOUND)) {
                ItemStack s = ItemStack.of((CompoundTag) y);
                if (!s.isEmpty()) {
                    b.pending.addLast(s);
                }
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (Box b : boxes.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", b.owner);
            c.putString("name", b.name);
            c.putInt("unread", b.unread);
            ListTag items = new ListTag();
            for (int i = 0; i < b.items.getContainerSize(); i++) {
                ItemStack s = b.items.getItem(i);
                if (!s.isEmpty()) {
                    CompoundTag st = s.save(new CompoundTag());
                    st.putByte("Slot", (byte) i);
                    items.add(st);
                }
            }
            c.put("items", items);
            ListTag pend = new ListTag();
            for (ItemStack s : b.pending) {
                pend.add(s.save(new CompoundTag()));
            }
            c.put("pending", pend);
            list.add(c);
        }
        tag.put("boxes", list);
        return tag;
    }
}
