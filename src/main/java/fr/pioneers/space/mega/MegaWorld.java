package fr.pioneers.space.mega;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : l'etat des megastructures du MONDE (une de chaque sorte par astre et par monde, commune au
 * serveur : decision de Guillaume du 18/11/2026). Pour chacune : ce qui a ete livre au chantier (par ingredient), debloquee ou non (et quand),
 * qui a ouvert le chantier (affichage). Sauve avec l'Overworld ; envoye aux clients a la connexion et a chaque changement, au plus une fois
 * par seconde ({@link MegaEvents}), jamais a chaque tick.
 */
public final class MegaWorld extends SavedData {

    static final String NAME = "pioneers_megastructures";

    private final int[][] delivered = new int[Megastructures.COUNT][];
    private final boolean[] unlocked = new boolean[Megastructures.COUNT];
    private final long[] unlockedAt = new long[Megastructures.COUNT];
    private final UUID[] opener = new UUID[Megastructures.COUNT];
    private final String[] openerName = new String[Megastructures.COUNT];
    /** Version de l'etat (augmente a chaque changement) : envoi aux clients quand elle change. */
    private int version;

    private MegaWorld() {
        for (int i = 0; i < Megastructures.COUNT; i++) {
            delivered[i] = new int[Megastructures.ingredientCount(i)];
            openerName[i] = "";
        }
    }

    public static MegaWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MegaWorld::load, MegaWorld::new, NAME);
    }

    public boolean unlocked(int i) {
        return i >= 0 && unlocked[i];
    }

    /** Masque des megastructures debloquees (bit i). */
    public long unlockedMask() {
        long m = 0;
        for (int i = 0; i < Megastructures.COUNT; i++) {
            if (unlocked[i]) {
                m |= 1L << i;
            }
        }
        return m;
    }

    public int delivered(int i, int k) {
        return delivered[i][k];
    }

    public int missing(int i, int k) {
        return unlocked[i] ? 0 : Math.max(0, Megastructures.needed(i, k) - delivered[i][k]);
    }

    public String openerName(int i) {
        return openerName[i];
    }

    @Nullable
    public UUID opener(int i) {
        return opener[i];
    }

    public int version() {
        return version;
    }

    /** Premier chantier pose : le chantier est ouvert par ce joueur (affichage ; la megastructure reste commune). */
    public void open(int i, ServerPlayer player) {
        if (opener[i] == null) {
            opener[i] = player.getUUID();
            openerName[i] = player.getGameProfile().getName();
            changed();
        }
    }

    /**
     * Livre une pile au chantier de la megastructure i : rend le nombre d'objets pris (au plus ce qui manque de cet ingredient). La liste
     * complete debloque la megastructure ({@link MegaEvents#unlocked}).
     */
    public int accept(@Nullable MinecraftServer server, int i, ItemStack stack, boolean simulate) {
        if (i < 0 || unlocked[i]) {
            return 0;
        }
        int k = Megastructures.ingredientOf(i, stack);
        if (k < 0) {
            return 0;
        }
        int take = Math.min(stack.getCount(), missing(i, k));
        if (take > 0 && !simulate) {
            delivered[i][k] += take;
            changed();
            if (complete(i)) {
                unlock(server, i);
            }
        }
        return take;
    }

    private boolean complete(int i) {
        for (int k = 0; k < delivered[i].length; k++) {
            if (delivered[i][k] < Megastructures.needed(i, k)) {
                return false;
            }
        }
        return true;
    }

    /** Debloque la megastructure (liste complete, ou commande d'operateur et GameTests). */
    public void unlock(@Nullable MinecraftServer server, int i) {
        if (unlocked[i]) {
            return;
        }
        unlocked[i] = true;
        unlockedAt[i] = server == null ? 0 : server.overworld().getGameTime();
        for (int k = 0; k < delivered[i].length; k++) {
            delivered[i][k] = Megastructures.needed(i, k);
        }
        changed();
        if (server != null) {
            MegaEvents.unlocked(server, i);
        }
    }

    /** Remet une megastructure a zero (commande d'operateur et GameTests). */
    public void reset(int i) {
        unlocked[i] = false;
        unlockedAt[i] = 0;
        opener[i] = null;
        openerName[i] = "";
        java.util.Arrays.fill(delivered[i], 0);
        changed();
    }

    /** Remet l'etat sauve dans {@code tag} (GameTests : l'etat commun est rendu tel qu'il etait). */
    public void restore(CompoundTag tag) {
        MegaWorld copy = load(tag);
        for (int i = 0; i < Megastructures.COUNT; i++) {
            unlocked[i] = copy.unlocked[i];
            unlockedAt[i] = copy.unlockedAt[i];
            opener[i] = copy.opener[i];
            openerName[i] = copy.openerName[i];
            delivered[i] = copy.delivered[i].clone();
        }
        changed();
    }

    private void changed() {
        version++;
        setDirty();
        MegaEvents.markDirty();
    }

    /** Etat envoye aux clients. */
    public PacketMegaState packet() {
        int[][] d = new int[Megastructures.COUNT][];
        for (int i = 0; i < Megastructures.COUNT; i++) {
            d[i] = delivered[i].clone();
        }
        return new PacketMegaState(unlockedMask(), d, openerName.clone());
    }

    private static MegaWorld load(CompoundTag tag) {
        MegaWorld w = new MegaWorld();
        ListTag list = tag.getList("megas", Tag.TAG_COMPOUND);
        for (int n = 0; n < list.size(); n++) {
            CompoundTag t = list.getCompound(n);
            //par id : une megastructure retiree des donnees est ignoree, une nouvelle part de zero
            int i = indexOf(t.getString("id"));
            if (i < 0) {
                continue;
            }
            w.unlocked[i] = t.getBoolean("unlocked");
            w.unlockedAt[i] = t.getLong("at");
            if (t.hasUUID("opener")) {
                w.opener[i] = t.getUUID("opener");
            }
            w.openerName[i] = t.getString("openerName");
            CompoundTag d = t.getCompound("delivered");
            for (int k = 0; k < w.delivered[i].length; k++) {
                String item = GeneratedMegastructures.INGREDIENTS[i][k];
                w.delivered[i][k] = Math.min(Megastructures.needed(i, k), d.getInt(item));
            }
        }
        return w;
    }

    private static int indexOf(String id) {
        for (int i = 0; i < Megastructures.COUNT; i++) {
            if (Megastructures.id(i).equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (int i = 0; i < Megastructures.COUNT; i++) {
            CompoundTag t = new CompoundTag();
            t.putString("id", Megastructures.id(i));
            t.putBoolean("unlocked", unlocked[i]);
            t.putLong("at", unlockedAt[i]);
            if (opener[i] != null) {
                t.putUUID("opener", opener[i]);
            }
            t.putString("openerName", openerName[i]);
            CompoundTag d = new CompoundTag();
            for (int k = 0; k < delivered[i].length; k++) {
                d.putInt(GeneratedMegastructures.INGREDIENTS[i][k], delivered[i][k]);
            }
            t.put("delivered", d);
            list.add(t);
        }
        tag.put("megas", list);
        return tag;
    }

    /** Objets livres au chantier de la megastructure i. */
    public int deliveredTotal(int i) {
        int done = 0;
        for (int k = 0; k < delivered[i].length; k++) {
            done += delivered[i][k];
        }
        return done;
    }
}
