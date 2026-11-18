package fr.pioneers.society.village;

import fr.pioneers.Pioneers;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : identite d'un villageois (capacite sur l'entite, reprise par les mondes existants : un villageois recoit son
 * identite a son premier chargement) : nom, peuple, village, et ce qu'il retient de chaque joueur (16 au plus, on oublie le moins recent) :
 * derniere conversation, echanges du jour, Carnet montre, Confidence donnee. L'AFFINITE elle-meme vit dans le registre du village
 * ({@link VillageData}) ; un villageois sans village la garde ici.
 */
public final class VillagerLore {

    public static final Capability<VillagerLore> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** Ce que le villageois retient d'un joueur. */
    public static final class Bond {

        public int affinity;
        public long talkDay = -1, tradeDay = -1, freeRepairDay = -1;
        public int tradeCount;
        public boolean book, trust4, confided, extraSecret, workerHired;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("a", affinity);
            t.putLong("td", talkDay);
            t.putLong("xd", tradeDay);
            t.putLong("fr", freeRepairDay);
            t.putInt("xc", tradeCount);
            t.putBoolean("b", book);
            t.putBoolean("t4", trust4);
            t.putBoolean("c", confided);
            t.putBoolean("x", extraSecret);
            return t;
        }

        static Bond load(CompoundTag t) {
            Bond b = new Bond();
            b.affinity = t.getInt("a");
            b.talkDay = t.getLong("td");
            b.tradeDay = t.getLong("xd");
            b.freeRepairDay = t.contains("fr") ? t.getLong("fr") : -1;
            b.tradeCount = t.getInt("xc");
            b.book = t.getBoolean("b");
            b.trust4 = t.getBoolean("t4");
            b.confided = t.getBoolean("c");
            b.extraSecret = t.getBoolean("x");
            return b;
        }
    }

    public boolean identified;
    /** Nom litteral, ou cle de langue precedee de « # » (surnoms des villages-etapes). */
    public String name = "";
    public String people = "";
    public long village;
    public final LinkedHashMap<UUID, Bond> bonds = new LinkedHashMap<>();
    /** Quotas d'achat du jour : jour de jeu et unites achetees par objet. */
    public long quotaDay = -1;
    public final Map<String, Integer> bought = new HashMap<>();
    /** Trois dernieres repliques (pas de repetition). */
    public final Deque<String> recent = new ArrayDeque<>();

    public static VillagerLore get(Villager v) {
        VillagerLore l = v.getCapability(CAP).resolve().orElse(null);
        return l != null ? l : new VillagerLore();
    }

    public Component displayName() {
        return name.startsWith("#") ? Component.translatable(name.substring(1)) : Component.literal(name);
    }

    public Bond bond(UUID player) {
        Bond b = bonds.remove(player);
        if (b == null) {
            b = new Bond();
        }
        bonds.put(player, b);
        while (bonds.size() > VillageData.MAX_PLAYERS) {
            bonds.remove(bonds.keySet().iterator().next());
        }
        return b;
    }

    @Nullable
    public Bond peek(UUID player) {
        return bonds.get(player);
    }

    public int bought(String item, long day) {
        if (quotaDay != day) {
            quotaDay = day;
            bought.clear();
        }
        return bought.getOrDefault(item, 0);
    }

    public void addBought(String item, int n, long day) {
        bought(item, day);
        bought.merge(item, n, Integer::sum);
    }

    public void remember(String line) {
        recent.addLast(line);
        while (recent.size() > 3) {
            recent.pollFirst();
        }
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("id", identified);
        t.putString("name", name);
        t.putString("people", people);
        t.putLong("village", village);
        ListTag b = new ListTag();
        bonds.forEach((u, bond) -> {
            CompoundTag c = bond.save();
            c.putUUID("u", u);
            b.add(c);
        });
        t.put("bonds", b);
        t.putLong("qd", quotaDay);
        CompoundTag q = new CompoundTag();
        bought.forEach(q::putInt);
        t.put("q", q);
        ListTag r = new ListTag();
        recent.forEach(s -> r.add(StringTag.valueOf(s)));
        t.put("recent", r);
        return t;
    }

    void load(CompoundTag t) {
        identified = t.getBoolean("id");
        name = t.getString("name");
        people = t.getString("people");
        village = t.getLong("village");
        bonds.clear();
        for (Tag x : t.getList("bonds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            bonds.put(c.getUUID("u"), Bond.load(c));
        }
        quotaDay = t.getLong("qd");
        bought.clear();
        CompoundTag q = t.getCompound("q");
        for (String k : q.getAllKeys()) {
            bought.put(k, q.getInt(k));
        }
        recent.clear();
        for (Tag x : t.getList("recent", Tag.TAG_STRING)) {
            recent.add(x.getAsString());
        }
    }

    // ------------------------------------------------------------------ capacite
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(VillagerLore.class);
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Villager) {
                event.addCapability(Pioneers.rl("villager_lore"), new Provider());
            }
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final VillagerLore data = new VillagerLore();
        private final LazyOptional<VillagerLore> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }
    }
}
