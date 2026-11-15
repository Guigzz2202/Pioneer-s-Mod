package fr.pioneers.world.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification §5.4, decision 7) : INTERFACE D'UN ANIMAL (Maj + clic droit main vide) : chien, chat ou monture.
 * Donnees fixes a l'ouverture (sorte, race, type, maitre, droits, aptitudes) ; donnees vivantes en {@link ContainerData} (mode, adoption,
 * sante, niche liee et ses coordonnees en deux moities de 16 bits, bouton Chasse mis en avant par {@link HuntEvents#highlight}).
 * Actions : {@link PacketPet}. Ferme si l'animal meurt ou s'eloigne a plus de 8 blocs.
 */
public class PetMenu extends AbstractContainerMenu {

    public static final int MODE = 0, ADOPT = 1, HEALTH = 2, MAX_HEALTH = 3, LINKED = 4, HX = 5, HX2 = 6, HY = 7, HZ = 8, HZ2 = 9, HUNT_LIT = 10,
          SITTING = 11, COUNT = 12;

    /** 1.0.5-beta (lot 31) : cadence de la relecture du bouton Chasse (ticks). */
    public static final int HUNT_REFRESH = 10;
    private int huntTick;

    public final int entityId, kind, breed;
    public final String type, master;
    public final boolean canEdit, hasMaster, packed;
    public final float[] aptitudes;
    private final ContainerData data;
    @Nullable
    private final LivingEntity entity;
    @Nullable
    private final Player player;

    public PetMenu(int id, Inventory inv, LivingEntity e, Player p) {
        this(id, inv, e.getId(), PetMaster.kind(e), e instanceof Pet pet ? pet.breed() : -1, BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),
              PetMaster.masterName(e), PetMaster.canCommand(e, p), PetMaster.master(e) != null, packed(e), aptitudes(e), new SimpleContainerData(COUNT),
              e, p);
        refresh();
    }

    private PetMenu(int id, Inventory inv, int entityId, int kind, int breed, String type, String master, boolean canEdit, boolean hasMaster,
          boolean packed, float[] apt, ContainerData data, @Nullable LivingEntity e, @Nullable Player p) {
        super(PiPets.MENU.get(), id);
        this.entityId = entityId;
        this.kind = kind;
        this.breed = breed;
        this.type = type;
        this.master = master;
        this.canEdit = canEdit;
        this.hasMaster = hasMaster;
        this.packed = packed;
        this.aptitudes = apt;
        this.data = data;
        this.entity = e;
        this.player = p;
        addDataSlots(data);
    }

    static boolean packed(Entity e) {
        fr.pioneers.logistics.caravan.CaravanData d = fr.pioneers.logistics.caravan.PiCaravan.get(e);
        return d != null && d.pack;
    }

    static float[] aptitudes(Entity e) {
        if (e instanceof Pet p && p.isCat()) {
            GeneratedPets.CatBreed b = GeneratedPets.CATS[p.breed()];
            return new float[]{b.fishing(), b.ratting(), b.independence()};
        }
        if (e instanceof Pet p) {
            GeneratedPets.DogBreed b = GeneratedPets.DOGS[p.breed()];
            return new float[]{b.scent(), b.speed(), b.strength(), b.cold()};
        }
        return new float[0];
    }

    public static void write(FriendlyByteBuf buf, LivingEntity e, Player p) {
        buf.writeVarInt(e.getId());
        buf.writeVarInt(PetMaster.kind(e));
        buf.writeVarInt(e instanceof Pet pet ? pet.breed() : -1);
        buf.writeUtf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
        buf.writeUtf(PetMaster.masterName(e));
        buf.writeBoolean(PetMaster.canCommand(e, p));
        buf.writeBoolean(PetMaster.master(e) != null);
        buf.writeBoolean(packed(e));
        float[] a = aptitudes(e);
        buf.writeVarInt(a.length);
        for (float f : a) {
            buf.writeFloat(f);
        }
    }

    public static PetMenu client(int id, Inventory inv, FriendlyByteBuf buf) {
        int eid = buf.readVarInt(), kind = buf.readVarInt(), breed = buf.readVarInt();
        String type = buf.readUtf(), master = buf.readUtf();
        boolean edit = buf.readBoolean(), has = buf.readBoolean(), packed = buf.readBoolean();
        float[] a = new float[buf.readVarInt()];
        for (int i = 0; i < a.length; i++) {
            a[i] = buf.readFloat();
        }
        return new PetMenu(id, inv, eid, kind, breed, type, master, edit, has, packed, a, new SimpleContainerData(COUNT), null, null);
    }

    void refresh() {
        if (entity == null) {
            return;
        }
        data.set(HEALTH, Math.round(entity.getHealth() * 10));
        data.set(MAX_HEALTH, Math.round(entity.getMaxHealth() * 10));
        data.set(ADOPT, PetMaster.adoptable(entity) ? 1 : 0);
        if (entity instanceof Pet p) {
            data.set(MODE, p.mode().ordinal());
            GlobalPos h = p.home();
            data.set(LINKED, h == null ? 0 : h.dimension().equals(entity.level().dimension()) ? 1 : 2);
            BlockPos b = h == null ? BlockPos.ZERO : h.pos();
            data.set(HX, b.getX() & 0xFFFF);
            data.set(HX2, (b.getX() >> 16) & 0xFFFF);
            data.set(HY, b.getY());
            data.set(HZ, b.getZ() & 0xFFFF);
            data.set(HZ2, (b.getZ() >> 16) & 0xFFFF);
            //1.0.5-beta (lot 31, retour 43) : allume seulement si une chasse est a portee du flair du chien (HuntRules.huntInReach) ; relu a
            //l'ouverture puis toutes les HUNT_REFRESH ticks (synchronise au client par les donnees du menu)
            if (huntTick-- <= 0) {
                huntTick = HUNT_REFRESH;
                data.set(HUNT_LIT, !p.isCat() && player != null && HuntEvents.highlighted(p.self(), player) ? 1 : 0);
            }
            data.set(SITTING, p.self().isOrderedToSit() ? 1 : 0);
        }
    }

    /** 1.0.5-beta (lot 31) : relit tout de suite le bouton Chasse (GameTests, changement de mode). */
    public void recheckHunt() {
        huntTick = 0;
        refresh();
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    public int get(int i) {
        return data.get(i);
    }

    public PetMode mode() {
        return PetMode.of(data.get(MODE));
    }

    /** Coordonnees de la niche liee (client) ; null si aucune. */
    @Nullable
    public BlockPos home() {
        if (data.get(LINKED) == 0) {
            return null;
        }
        int x = (data.get(HX) & 0xFFFF) | (data.get(HX2) << 16), z = (data.get(HZ) & 0xFFFF) | (data.get(HZ2) << 16);
        return new BlockPos(x, (short) data.get(HY), z);
    }

    public boolean mount() {
        return kind == PetMaster.MOUNT;
    }

    public boolean cat() {
        return kind == PetMaster.CAT;
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        Entity e = entity != null ? entity : player.level().getEntity(entityId);
        return e != null && e.isAlive() && e.distanceToSqr(player) < 64 && (!(e instanceof AbstractHorse) || PetMaster.tamed(e));
    }
}
