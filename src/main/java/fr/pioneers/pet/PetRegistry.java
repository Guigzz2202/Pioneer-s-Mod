package fr.pioneers.pet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification §5.5) : REGISTRE DES ANIMAUX DE CHAQUE MAITRE (SavedData {@code pioneers_pets} de l'Overworld) : par
 * maitre, ses animaux (UUID, sorte chien / chat / monture, type d'entite, nom, dimension, derniere position connue). Mis a jour a l'arrivee
 * dans un monde, au dechargement, au nom donne, au rappel ; retire a la mort. Sert au rappel d'un animal dont le troncon n'est pas charge
 * (on charge son troncon le temps du rappel) et a la roue de la touche Rappel.
 */
public final class PetRegistry extends SavedData {

    public record Entry(UUID id, int kind, String type, String name, ResourceLocation dim, double x, double y, double z) {

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putByte("kind", (byte) kind);
            t.putString("type", type);
            t.putString("name", name);
            t.putString("dim", dim.toString());
            t.putDouble("x", x);
            t.putDouble("y", y);
            t.putDouble("z", z);
            return t;
        }

        static Entry load(CompoundTag t) {
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            return new Entry(t.getUUID("id"), t.getByte("kind"), t.getString("type"), t.getString("name"),
                  dim == null ? new ResourceLocation("overworld") : dim, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
        }
    }

    private final Map<UUID, Map<UUID, Entry>> byMaster = new HashMap<>();
    private final Map<UUID, UUID> masterOf = new HashMap<>();

    public static PetRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PetRegistry::load, PetRegistry::new, "pioneers_pets");
    }

    public static PetRegistry load(CompoundTag tag) {
        PetRegistry r = new PetRegistry();
        for (Tag m : tag.getList("masters", Tag.TAG_COMPOUND)) {
            CompoundTag mt = (CompoundTag) m;
            UUID master = mt.getUUID("master");
            for (Tag e : mt.getList("pets", Tag.TAG_COMPOUND)) {
                Entry en = Entry.load((CompoundTag) e);
                r.byMaster.computeIfAbsent(master, k -> new LinkedHashMap<>()).put(en.id(), en);
                r.masterOf.put(en.id(), master);
            }
        }
        return r;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag masters = new ListTag();
        for (Map.Entry<UUID, Map<UUID, Entry>> m : byMaster.entrySet()) {
            CompoundTag mt = new CompoundTag();
            mt.putUUID("master", m.getKey());
            ListTag pets = new ListTag();
            m.getValue().values().forEach(e -> pets.add(e.save()));
            mt.put("pets", pets);
            masters.add(mt);
        }
        tag.put("masters", masters);
        return tag;
    }

    /** Animal vu maintenant (maitre, nom, place) ; sans maitre : retire. */
    public void update(Entity e) {
        UUID master = PetMaster.master(e);
        if (master == null) {
            remove(e.getUUID());
            return;
        }
        UUID old = masterOf.get(e.getUUID());
        if (old != null && !old.equals(master)) {
            remove(e.getUUID());
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        String name = e.hasCustomName() ? e.getCustomName().getString() : "";
        Entry en = new Entry(e.getUUID(), PetMaster.kind(e), type.toString(), name, e.level().dimension().location(), e.getX(), e.getY(), e.getZ());
        Entry before = byMaster.computeIfAbsent(master, k -> new LinkedHashMap<>()).put(e.getUUID(), en);
        if (!sameLine(before, en)) {
            PetListSync.SERVER.owe(master);   //1.0.5-beta (lot 33) : la ligne de la roue change -> liste poussee au maitre
        }
        masterOf.put(e.getUUID(), master);
        setDirty();
    }

    /**
     * 1.0.5-beta (lot 33) : meme ligne de la roue (sorte, type, nom, dimension) ? La place seule ne pousse rien (le client mesure la distance des
     * betes qu'il voit ; la liste est redemandee a chaque ouverture).
     */
    static boolean sameLine(@Nullable Entry a, Entry b) {
        return a != null && a.kind() == b.kind() && a.type().equals(b.type()) && a.name().equals(b.name()) && a.dim().equals(b.dim());
    }

    public void remove(UUID pet) {
        UUID m = masterOf.remove(pet);
        if (m != null) {
            PetListSync.SERVER.owe(m);
            Map<UUID, Entry> pets = byMaster.get(m);
            if (pets != null) {
                pets.remove(pet);
                if (pets.isEmpty()) {
                    byMaster.remove(m);
                }
            }
            setDirty();
        }
    }

    public List<Entry> list(UUID master) {
        Map<UUID, Entry> pets = byMaster.get(master);
        return pets == null ? List.of() : new ArrayList<>(pets.values());
    }

    @Nullable
    public Entry find(UUID pet) {
        UUID m = masterOf.get(pet);
        return m == null ? null : byMaster.get(m).get(pet);
    }

    @Nullable
    public UUID masterOf(UUID pet) {
        return masterOf.get(pet);
    }
}
