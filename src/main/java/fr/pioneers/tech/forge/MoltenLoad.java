package fr.pioneers.tech.forge;

import fr.pioneers.tech.bronze.foundry.Metals;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Contenu d'un creuset d'argile (pose sur le feu ou porte par la pince) : melange metal -> mB, temperature datee (refroidissement calcule
 * a la lecture, comme {@link HeatedMetal}). Le metal versable est {@link Metals#resolve} du melange (cuivre + etain = bronze...).
 * <p>
 * NBT : {@code mix} {metal: mB}, {@code temp} (float, degres C a l'instant {@code stamp}), {@code stamp} (long, heure du monde).
 */
public final class MoltenLoad {

    /** Contenance du creuset d'argile : 2 lingots. */
    public static final int CLAY_CRUCIBLE_MB = 2 * ForgeMetals.INGOT_MB;
    /** Constante de temps du refroidissement dans la pince (ticks) : le metal fige en une vingtaine de secondes. */
    public static final float TONGS_TAU_TICKS = 2400F; //1.1.1 : 2 min

    private final Map<String, Integer> mix = new LinkedHashMap<>();
    private float temp = ForgeMetals.AMBIENT_C;
    private long stamp;

    public boolean isEmpty() {
        return total() <= 0;
    }

    public int total() {
        int t = 0;
        for (int v : mix.values()) {
            t += v;
        }
        return t;
    }

    public Map<String, Integer> mix() {
        return Collections.unmodifiableMap(mix);
    }

    /** Metal ou alliage du melange (vide si le creuset est vide ou si rien ne colle : 1.0.2-beta, plus de melange impur). */
    public String metal() {
        return Metals.resolve(mix);
    }

    /** Temperature a atteindre pour que le melange coule. */
    public int pourC() {
        return Metals.pourC(mix);
    }

    /**
     * 1.0.3-beta (lot 7b) : plan de coulee du contenu (regle commune {@link Metals#plan}, sans filtre) ; {@code need} = mB que le moule demande
     * pour un metal (0 : toute quantite, negatif : le moule ne prend pas ce metal).
     */
    public Metals.Plan plan(ToIntFunction<String> need) {
        return Metals.plan(mix, "", false, need);
    }

    /** Metal qui coulerait d'abord (le bain entier s'il est un metal ou un alliage, sinon sa plus grande part) ; vide si le creuset est vide. */
    public String pourMetal() {
        List<Metals.Portion> list = Metals.pourable(mix, "", false);
        return list.isEmpty() ? "" : list.get(0).metal();
    }

    /** 1.0.3-beta (lot 7b) : coule {@code mb} de {@code metal} (retrait exact, {@link Metals#pour}) ; renvoie ce qui est vraiment sorti. */
    public int pour(String metal, int mb) {
        return Metals.pour(mix, metal, mb).values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 1.0.3-beta (lot 7b) : sortie de secours, le contenu en lingots, pepites et scorie ({@link Metals#spill}) ; le creuset est vide ensuite. */
    public List<ItemStack> spill() {
        return Metals.spill(mix);
    }

    public void add(String metal, int mb) {
        //1.3.2 : un alliage fondu compte comme ses composants (bronze + cuivre + etain = bronze)
        Metals.addTo(mix, metal, mb);
    }

    /** Retire {@code mb} au prorata de chaque metal ; renvoie la quantite retiree. */
    public int drain(int mb) {
        int total = total();
        if (total <= 0 || mb <= 0) {
            return 0;
        }
        int take = Math.min(mb, total);
        if (take == total) {
            mix.clear();
            return take;
        }
        int left = take;
        String last = null;
        for (Map.Entry<String, Integer> e : mix.entrySet()) {
            int part = (int) ((long) e.getValue() * take / total);
            e.setValue(e.getValue() - part);
            left -= part;
            last = e.getKey();
        }
        if (left > 0 && last != null) {
            for (Map.Entry<String, Integer> e : mix.entrySet()) {
                int part = Math.min(left, e.getValue());
                e.setValue(e.getValue() - part);
                left -= part;
            }
        }
        mix.values().removeIf(v -> v <= 0);
        return take;
    }

    public void clear() {
        mix.clear();
    }

    // ------------------------------------------------------------------
    // Temperature
    // ------------------------------------------------------------------

    /** Fixe la temperature maintenant ({@code gameTime} = heure du monde). */
    public void setTemperature(float tempC, long gameTime) {
        temp = tempC;
        stamp = gameTime;
    }

    /** Temperature a l'heure donnee, en refroidissant vers l'ambiante avec la constante de temps {@code tauTicks}. */
    public float temperatureAt(long gameTime, float tauTicks) {
        return HeatedMetal.cool(temp, gameTime - stamp, tauTicks);
    }

    /** Temperature enregistree (sans refroidissement). */
    public float storedTemperature() {
        return temp;
    }

    public long stamp() {
        return stamp;
    }

    /** Vrai si le melange coule a cette heure (pince : {@link #TONGS_TAU_TICKS}). */
    public boolean isMolten(long gameTime, float tauTicks) {
        return !isEmpty() && temperatureAt(gameTime, tauTicks) >= pourC();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag m = new CompoundTag();
        mix.forEach(m::putInt);
        tag.put("mix", m);
        tag.putFloat("temp", temp);
        tag.putLong("stamp", stamp);
        return tag;
    }

    public static MoltenLoad load(@Nullable CompoundTag tag) {
        MoltenLoad load = new MoltenLoad();
        if (tag != null) {
            CompoundTag m = tag.getCompound("mix");
            for (String k : m.getAllKeys()) {
                load.add(k, m.getInt(k));
            }
            load.temp = tag.contains("temp") ? tag.getFloat("temp") : ForgeMetals.AMBIENT_C;
            load.stamp = tag.getLong("stamp");
        }
        return load;
    }

    public MoltenLoad copy() {
        return load(save());
    }
}
