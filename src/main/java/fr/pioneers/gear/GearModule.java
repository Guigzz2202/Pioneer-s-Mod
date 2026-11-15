package fr.pioneers.gear;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.15 : modules de l'equipement modulaire (memes ids et meme ordre que tools/gc/gear/spec.py). Chaque module installe occupe un
 * emplacement par niveau ; le niveau maximal d'un module est plafonne par le tier de l'objet ({@link #cap}) ; certains modules exigent un
 * tier minimal (le vol : Neutronique).
 * <p>
 * Categories d'accueil : H casque, C plastron, L jambieres, F bottes, A toute piece d'armure, T outils, B lame, R fusil, X tout.
 * Les modules {@link #passive() passifs} (blindage, cellule auxiliaire) agissent meme sans energie.
 */
public enum GearModule {
    NIGHT_VISION("H", 1, 0),
    RESPIRATOR("H", 1, 0),
    SCANNER("H", 3, 0),
    NUTRITION("H", 1, 1),
    SOLAR("H", 3, 0),
    JETPACK("C", 4, 0),
    FLIGHT("C", 2, 2),
    SHIELD("C", 4, 0),
    NANITES("C", 3, 1),
    THERMAL("C", 1, 0),
    MAGNET("C", 3, 0),
    SERVO("L", 4, 0),
    STEP("L", 1, 0),
    HYDRO("L", 3, 0),
    JUMP("F", 4, 0),
    DAMPERS("F", 1, 0),
    KINETIC("F", 3, 0),
    PLATING("A", 4, 0),
    BATTERY("X", 4, 0),
    EXCAVATION("T", 2, 0),
    OVERCLOCK("T", 4, 0),
    FORTUNE("T", 3, 0),
    SILK_TOUCH("T", 1, 0),
    SMELTING("T", 1, 1),
    TELEPORT("T", 1, 1),
    REACH("T", 2, 0),
    PLASMA_EDGE("B", 4, 0),
    VAMPIRISM("B", 3, 1),
    LOOTING("B", 3, 0),
    OVERCHARGE("R", 4, 0),
    VOLLEY("R", 2, 1),
    RAPID_FIRE("R", 3, 0);

    private static final GearModule[] VALUES = values();

    public final String categories;
    public final int maxLevel;
    /** Tier minimal de l'objet (0 = Graviton). */
    public final int minTier;

    GearModule(String categories, int maxLevel, int minTier) {
        this.categories = categories;
        this.maxLevel = maxLevel;
        this.minTier = minTier;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static GearModule byId(String id) {
        for (GearModule m : VALUES) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    public static GearModule byIndex(int index) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, index))];
    }

    /** Le module va-t-il dans cette sorte d'objet (categorie seulement, sans le tier) ? */
    public boolean fits(GearKind kind) {
        return categories.indexOf(kind.category) >= 0 || categories.contains("A") && kind.isArmor() || categories.contains("X");
    }

    /** Niveau maximal sur un objet de ce tier : niveau du module, plafonne a tier + 2 (MKI : 2, MKII : 3, ensuite 4). */
    public int cap(ModularTier tier) {
        return tier.ordinal() < minTier ? 0 : Math.min(maxLevel, tier.ordinal() + 2);
    }

    /** Agit sans energie (blindage, cellule auxiliaire). */
    public boolean passive() {
        return this == PLATING || this == BATTERY;
    }

    public Component displayName() {
        return Component.translatable("gear.pioneers.module." + id());
    }

    public Component effect() {
        return Component.translatable("gear.pioneers.module." + id() + ".desc");
    }

    /** Nom de la categorie d'accueil (texte de l'infobulle). */
    public Component where() {
        return Component.translatable("gear.pioneers.category." + categories.toLowerCase(Locale.ROOT));
    }
}
