package fr.pioneers.logistics.caravan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : donnees de caravane d'une bete de bat (cheval, ane, mulet, chameau, dromadaire : capacite {@link PiCaravan#CAP}) :
 * BAT (pose ou non, cases d'apres la charge de bat, contenu), ROLE (tete, suiveur n), ITINERAIRE (relais, boucle / aller-retour, etape
 * visee, sens), ETAT (au repos, en route, au relais, arretee + raison), proprietaire, rations ; et la VUE envoyee aux clients
 * ({@link PacketCaravan} : rendu du bat rempli, rabats ouverts, fiche au regard).
 */
public final class CaravanData implements INBTSerializable<CompoundTag> {

    public static final int NONE = 0, HEAD = 1, FOLLOWER = 2;
    public static final int IDLE = 0, TRAVEL = 1, STOP = 2, STOPPED = 3;
    public static final int REASON_NONE = 0, REASON_RELAY_GONE = 1;

    // ------------------------------------------------------------------ bat
    public boolean pack;
    /** Cases utilisables du bat (fixees a la pose d'apres la charge de bat de la bete). */
    public int slots;
    public final ItemStackHandler items = new ItemStackHandler(GeneratedCaravan.MAX_SLOTS);

    // ------------------------------------------------------------------ caravane
    public int role = NONE;
    /** Suiveur : rang dans la file (1 = juste derriere la tete) et tete. */
    public int index;
    @Nullable
    public UUID head;
    public final List<BlockPos> stops = new ArrayList<>();
    public final List<String> names = new ArrayList<>();
    public boolean loop = true;
    public String dim = "";
    public int next, dir = 1;
    public int state = IDLE, reason = REASON_NONE;
    @Nullable
    public UUID owner;
    public String ownerName = "";
    /** Ticks passes a l'arret courant. */
    public int stopTicks;
    /** Blocs qu'il reste a parcourir avant d'avoir faim. */
    public float fed = GeneratedCaravan.FED_BLOCKS;

    // ------------------------------------------------------------------ serveur seulement (non sauve)
    public double best = Double.MAX_VALUE;
    public int stuck, detour;
    public boolean waiting, open;
    public long lastView;
    /** Tete : distance a l'etape visee ; suiveur : nom et distance de l'etape de sa tete (pour la fiche). */
    public int dist;
    public String followerNext = "";
    /** Tete : la file (suiveurs dans l'ordre, relue chaque seconde). */
    public java.util.List<net.minecraft.world.entity.Mob> file = new ArrayList<>();
    public double lastX = Double.NaN, lastZ = Double.NaN;
    /**
     * 1.0.4-beta (lot 29) : tete : point de marche en cours (null : a choisir), tick ou il a ete choisi, nombre de secours (teleportations)
     * depuis le depart du relais (fiche, banc) ; non sauve.
     */
    @Nullable
    public Vec3 hopTarget;
    public long hopAt;
    public int rescues;
    /** 1.0.4-beta (lot 29) : pas de la caravane (blocs / s : charge et faim comprises), calcule par la tete et recopie aux suiveurs. */
    public double pace;

    // ------------------------------------------------------------------ client
    public int clientUsed, clientStage, clientNextDist;
    public String clientNext = "";
    public boolean clientHungry, clientWaiting;
    public float openAnim;

    public boolean active() {
        return role == HEAD && (state == TRAVEL || state == STOP);
    }

    public boolean hungry() {
        return fed <= 0;
    }

    /** Piles dans le bat (cases occupees). */
    public int used() {
        int n = 0;
        for (int i = 0; i < Math.min(slots, items.getSlots()); i++) {
            n += items.getStackInSlot(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    /** Remplissage 0..1 (objets / place des cases utilisables). */
    public float fill() {
        if (!pack || slots <= 0) {
            return 0;
        }
        float f = 0;
        for (int i = 0; i < Math.min(slots, items.getSlots()); i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) {
                f += s.getCount() / (float) s.getMaxStackSize();
            }
        }
        return f / slots;
    }

    /** Niveau de remplissage visible (0 vide, 1, 2, 3 plein). */
    public int stage() {
        float f = fill();
        return f <= 0 ? 0 : f < 0.34F ? 1 : f < 0.75F ? 2 : 3;
    }

    /** Bats pleins : toutes les cases utilisables a pile complete. */
    public boolean full() {
        if (!pack || slots <= 0) {
            return true;
        }
        for (int i = 0; i < slots; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    public void clearRoute() {
        stops.clear();
        names.clear();
        next = 0;
        dir = 1;
        state = IDLE;
        reason = REASON_NONE;
        stopTicks = 0;
        waiting = false;
        open = false;
    }

    // ------------------------------------------------------------------ sauvegarde
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("pack", pack);
        t.putInt("slots", slots);
        if (pack) {
            t.put("items", items.serializeNBT());
        }
        t.putByte("role", (byte) role);
        t.putInt("index", index);
        if (head != null) {
            t.putUUID("head", head);
        }
        ListTag ls = new ListTag();
        for (int i = 0; i < stops.size(); i++) {
            CompoundTag s = new CompoundTag();
            s.putLong("pos", stops.get(i).asLong());
            s.putString("name", i < names.size() ? names.get(i) : "");
            ls.add(s);
        }
        t.put("stops", ls);
        t.putBoolean("loop", loop);
        t.putString("dim", dim);
        t.putInt("next", next);
        t.putByte("dir", (byte) dir);
        t.putByte("state", (byte) state);
        t.putByte("reason", (byte) reason);
        if (owner != null) {
            t.putUUID("owner", owner);
        }
        t.putString("owner_name", ownerName);
        t.putInt("stop_ticks", stopTicks);
        t.putFloat("fed", fed);
        return t;
    }

    @Override
    public void deserializeNBT(CompoundTag t) {
        pack = t.getBoolean("pack");
        slots = t.getInt("slots");
        if (t.contains("items")) {
            ItemStackHandler h = new ItemStackHandler();
            h.deserializeNBT(t.getCompound("items"));
            for (int i = 0; i < items.getSlots(); i++) {
                items.setStackInSlot(i, i < h.getSlots() ? h.getStackInSlot(i) : ItemStack.EMPTY);
            }
        } else {
            for (int i = 0; i < items.getSlots(); i++) {
                items.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        role = t.getByte("role");
        index = t.getInt("index");
        head = t.hasUUID("head") ? t.getUUID("head") : null;
        stops.clear();
        names.clear();
        ListTag ls = t.getList("stops", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size(); i++) {
            stops.add(BlockPos.of(ls.getCompound(i).getLong("pos")));
            names.add(ls.getCompound(i).getString("name"));
        }
        loop = !t.contains("loop") || t.getBoolean("loop");
        dim = t.getString("dim");
        next = t.getInt("next");
        dir = t.contains("dir") ? t.getByte("dir") : 1;
        state = t.getByte("state");
        reason = t.getByte("reason");
        owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
        ownerName = t.getString("owner_name");
        stopTicks = t.getInt("stop_ticks");
        fed = t.contains("fed") ? t.getFloat("fed") : GeneratedCaravan.FED_BLOCKS;
    }

    // ------------------------------------------------------------------ vue des clients
    /** Ce que voient les clients (rendu et fiche) ; aucun contenu d'inventaire. */
    public CompoundTag view(int distanceToNext) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("pack", pack);
        t.putInt("slots", slots);
        t.putInt("used", used());
        t.putByte("stage", (byte) stage());
        t.putBoolean("open", open);
        t.putByte("role", (byte) role);
        t.putInt("index", index);
        t.putByte("state", (byte) state);
        t.putByte("reason", (byte) reason);
        t.putBoolean("hungry", hungry());
        t.putBoolean("waiting", waiting);
        t.putString("next", role == FOLLOWER ? followerNext : next >= 0 && next < names.size() ? names.get(next) : "");
        t.putInt("dist", distanceToNext);
        t.putString("owner_name", ownerName);
        return t;
    }

    public void readView(CompoundTag t) {
        pack = t.getBoolean("pack");
        slots = t.getInt("slots");
        clientUsed = t.getInt("used");
        clientStage = t.getByte("stage");
        open = t.getBoolean("open");
        role = t.getByte("role");
        index = t.getInt("index");
        state = t.getByte("state");
        reason = t.getByte("reason");
        clientHungry = t.getBoolean("hungry");
        clientWaiting = t.getBoolean("waiting");
        clientNext = t.getString("next");
        clientNextDist = t.getInt("dist");
        ownerName = t.getString("owner_name");
    }
}
