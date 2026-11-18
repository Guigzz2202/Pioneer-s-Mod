package fr.pioneers.logistics.caravan;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : unite du RELAIS DE CARAVANE (API Machine) : son ORDRE (charger, decharger, les deux, passer), son FILTRE (9 objets,
 * vide = tout), sa CONDITION de depart (apres l'attente, bats pleins, bats vides), l'ATTENTE (minimum, secondes), le PROPRIETAIRE ; valeurs
 * d'interface (ordre, condition, attente, filtre en ids bruts, conteneur voisin, rations, caravanes presentes). Les transferts sont faits
 * par la caravane a l'arret ({@link Caravans#serve}) ; les reglages viennent de l'interface ({@link RelayTile#handleSetting}).
 */
public class RelayUnit extends MachineUnit {

    public static final int SETTING_ORDER = 0, SETTING_COND = 1, SETTING_WAIT = 2, SETTING_FILTER = 10;
    public static final UiValues.Key ORDER = UiValues.Key.of("relay_order"), COND = UiValues.Key.of("relay_cond"),
          WAIT = UiValues.Key.of("relay_wait"), CONTAINER = UiValues.Key.of("relay_container"), RATIONS = UiValues.Key.of("relay_rations"),
          HERE = UiValues.Key.of("relay_here");
    public static final UiValues.Key[] FILTER_KEYS = new UiValues.Key[GeneratedCaravan.FILTER];

    static {
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            FILTER_KEYS[i] = UiValues.Key.of("relay_filter_" + i);
        }
    }

    private final RelayTile tile;
    public int order = CaravanRules.LOAD, cond = CaravanRules.TIME, wait = GeneratedCaravan.WAIT_DEFAULT;
    /** Filtre : objets (null = case vide). */
    public final Item[] filter = new Item[GeneratedCaravan.FILTER];
    @Nullable
    public UUID owner;
    public String ownerName = "";
    /** Etat montre (relu chaque seconde) : conteneur voisin, rations dans la mangeoire, caravanes a l'arret ici. */
    public int container, rations, here;
    private long lastLook;

    public RelayUnit(UnitHost host, RelayTile tile) {
        super(host);
        this.tile = tile;
    }

    /** Ids bruts du filtre (0 = vide), pour {@link CaravanRules#keeps}. */
    public int[] filterIds() {
        int[] out = new int[filter.length];
        for (int i = 0; i < filter.length; i++) {
            out[i] = filter[i] == null || filter[i] == Items.AIR ? 0 : BuiltInRegistries.ITEM.getId(filter[i]);
        }
        return out;
    }

    @Override
    public void tick() {
        if (level() == null || level().getGameTime() - lastLook < 20) {
            return;
        }
        lastLook = level().getGameTime();
        int c = tile.container() != null ? 1 : 0, r = tile.rations(false), h = Caravans.stoppedAt(tile);
        if (c != container || r != rations || h != here) {
            container = c;
            rations = r;
            here = h;
            machine.markSync();
        }
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(ORDER, () -> order).add(COND, () -> cond).add(WAIT, () -> wait);
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            int k = i;
            values.add(FILTER_KEYS[i], () -> filter[k] == null ? 0 : BuiltInRegistries.ITEM.getId(filter[k]));
        }
        values.add(CONTAINER, () -> container).add(RATIONS, () -> rations).add(HERE, () -> here);
    }

    /** Reglage (deja autorise par la tuile) ; valeurs du client bornees ici. */
    @Override
    public void setting(int setting, int value) {
        if (setting == SETTING_ORDER) {
            order = Math.floorMod(value, CaravanRules.ORDERS);
        } else if (setting == SETTING_COND) {
            cond = Math.floorMod(value, CaravanRules.CONDITIONS);
        } else if (setting == SETTING_WAIT) {
            wait = CaravanRules.clampWait(value);
        } else if (setting >= SETTING_FILTER && setting < SETTING_FILTER + filter.length) {
            Item it = value <= 0 ? null : BuiltInRegistries.ITEM.byId(value);
            //Pas deux fois le meme objet
            for (int i = 0; i < filter.length; i++) {
                if (it != null && filter[i] == it) {
                    filter[i] = null;
                }
            }
            filter[setting - SETTING_FILTER] = it == Items.AIR ? null : it;
        } else {
            return;
        }
        machine.changed();
        machine.syncNow();
    }

    @Override
    public void save(CompoundTag tag) {
        CompoundTag r = new CompoundTag();
        r.putByte("order", (byte) order);
        r.putByte("cond", (byte) cond);
        r.putInt("wait", wait);
        ListTag f = new ListTag();
        for (Item it : filter) {
            f.add(StringTag.valueOf(it == null ? "" : BuiltInRegistries.ITEM.getKey(it).toString()));
        }
        r.put("filter", f);
        if (owner != null) {
            r.putUUID("owner", owner);
        }
        r.putString("owner_name", ownerName);
        tag.put("relay", r);
    }

    @Override
    public void load(CompoundTag tag) {
        CompoundTag r = tag.getCompound("relay");
        order = Math.floorMod(r.getByte("order"), CaravanRules.ORDERS);
        cond = Math.floorMod(r.getByte("cond"), CaravanRules.CONDITIONS);
        wait = r.contains("wait") ? CaravanRules.clampWait(r.getInt("wait")) : GeneratedCaravan.WAIT_DEFAULT;
        ListTag f = r.getList("filter", Tag.TAG_STRING);
        for (int i = 0; i < filter.length; i++) {
            String s = i < f.size() ? f.getString(i) : "";
            ResourceLocation id = s.isEmpty() ? null : ResourceLocation.tryParse(s);
            Item it = id == null ? null : BuiltInRegistries.ITEM.get(id);
            filter[i] = it == Items.AIR ? null : it;
        }
        owner = r.hasUUID("owner") ? r.getUUID("owner") : null;
        ownerName = r.getString("owner_name");
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putString("relay_owner", ownerName);
    }

    @Override
    public void readClient(CompoundTag tag) {
        ownerName = tag.getString("relay_owner");
    }
}
