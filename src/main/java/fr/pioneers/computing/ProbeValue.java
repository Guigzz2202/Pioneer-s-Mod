package fr.pioneers.computing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * Grandeur lue sur une machine par une sonde (0.17.0) : cle stable (gardee par les regles et les ecrans), libelle (cle de langue
 * {@code computing.pioneers.value.<label>}, argument entier eventuel : numero de reservoir), valeur, unite, maximum pour les jauges (0 : pas
 * de jauge) et prefixe de cle de langue pour les valeurs enumerees (statut).
 */
public record ProbeValue(String key, String label, int arg, double value, String unit, double max, String enumPrefix) {

    public static ProbeValue of(String key, double value, String unit, double max) {
        return new ProbeValue(key, key, -1, value, unit, max, "");
    }

    public Component name() {
        return arg < 0 ? Component.translatable("computing.pioneers.value." + label) : Component.translatable("computing.pioneers.value." + label, arg + 1);
    }

    /** Valeur mise en forme (enumerees : texte traduit). */
    public Component text() {
        if (!enumPrefix.isEmpty()) {
            return Component.translatable(enumPrefix + (int) Math.round(value));
        }
        return Component.literal(format(value, unit));
    }

    public static String format(double value, String unit) {
        String number;
        double a = Math.abs(value);
        if (a >= 1_000_000_000) {
            number = String.format(Locale.ROOT, "%.2fG", value / 1e9);
        } else if (a >= 1_000_000) {
            number = String.format(Locale.ROOT, "%.2fM", value / 1e6);
        } else if (a >= 10_000) {
            number = String.format(Locale.ROOT, "%.1fk", value / 1e3);
        } else if (a == Math.rint(a)) {
            number = String.valueOf((long) value);
        } else {
            number = String.format(Locale.ROOT, "%.1f", value);
        }
        return unit.isEmpty() ? number : number + " " + unit;
    }

    /** Remplissage de la jauge (0..1), ou -1 sans jauge. */
    public double fill() {
        return max <= 0 ? -1 : Math.max(0, Math.min(1, value / max));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("k", key);
        tag.putString("l", label);
        tag.putInt("a", arg);
        tag.putDouble("v", value);
        tag.putString("u", unit);
        tag.putDouble("m", max);
        tag.putString("e", enumPrefix);
        return tag;
    }

    public static ProbeValue load(CompoundTag tag) {
        return new ProbeValue(tag.getString("k"), tag.getString("l"), tag.getInt("a"), tag.getDouble("v"), tag.getString("u"), tag.getDouble("m"),
              tag.getString("e"));
    }

    public static ListTag saveAll(List<ProbeValue> values) {
        ListTag list = new ListTag();
        values.forEach(v -> list.add(v.save()));
        return list;
    }

    public static List<ProbeValue> loadAll(ListTag list) {
        List<ProbeValue> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            out.add(load(list.getCompound(i)));
        }
        return out;
    }

    public static List<ProbeValue> loadAll(CompoundTag tag, String name) {
        return loadAll(tag.getList(name, Tag.TAG_COMPOUND));
    }

    /** Valeur de cle {@code key} dans une liste, ou null. */
    public static ProbeValue find(List<ProbeValue> values, String key) {
        for (ProbeValue v : values) {
            if (v.key.equals(key)) {
                return v;
            }
        }
        return null;
    }
}
