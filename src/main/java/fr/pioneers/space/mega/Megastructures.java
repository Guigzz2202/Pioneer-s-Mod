package fr.pioneers.space.mega;

import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.SolarSystem.Body;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : les MEGASTRUCTURES (table generee {@link GeneratedMegastructures}, source
 * T:au_dela/megastructures.py). Une megastructure par astre ; on la debloque d'un coup en livrant sa liste d'ingredients a un chantier
 * pose sur son astre ({@link MegaSiteBlock}, etat commun du serveur {@link MegaWorld}) ; elle apparait alors dans le ciel (objet MEGA de
 * {@code SkyScene}) et les multiblocs de port batis sur son astre (controleur = terminal) font ses recettes uniques.
 */
public final class Megastructures {

    /** Nombre de megastructures (au plus 63 : masque des debloquees sur un long). */
    public static final int COUNT = GeneratedMegastructures.IDS.length;
    private static final Map<String, Integer> BY_PORT = new HashMap<>();
    private static final Body[] BODY = new Body[COUNT];
    private static final int[] TOTAL = new int[COUNT];
    /** Objets des ingredients, resolus au premier usage (registres figes). */
    private static Item[][] items;

    /**
     * Astre d'un monde pour la garde des ports et des chantiers. Les GameTests (monde d'essai a la surface) le remplacent le temps d'un
     * test ; jamais ailleurs.
     */
    public static Function<Level, Body> bodyOf = level -> SolarSystem.fromDimension(level.dimension());

    static {
        for (int i = 0; i < COUNT; i++) {
            BY_PORT.put(GeneratedMegastructures.PORTS[i], i);
            BODY[i] = Body.valueOf(GeneratedMegastructures.BODIES[i]);
            int t = 0;
            for (int c : GeneratedMegastructures.COUNTS[i]) {
                t += c;
            }
            TOTAL[i] = t;
        }
    }

    private Megastructures() {
    }

    public static String id(int i) {
        return GeneratedMegastructures.IDS[i];
    }

    public static Body body(int i) {
        return BODY[i];
    }

    /** Cle du multibloc de port (terminal) de la megastructure. */
    public static String port(int i) {
        return GeneratedMegastructures.PORTS[i];
    }

    public static Component name(int i) {
        return Component.translatable("mega.pioneers." + id(i));
    }

    /** Megastructure de l'astre, ou -1. */
    public static int byBody(@Nullable Body body) {
        if (body != null) {
            for (int i = 0; i < COUNT; i++) {
                if (BODY[i] == body) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Megastructure de l'astre d'un monde (hors megastructures de station), ou -1. */
    public static int here(Level level) {
        return find(bodyOf.apply(level), false);
    }

    /** 1.0.7 (lot 10) : vrai si la megastructure se livre et se batit dans la station de son astre (Observatoire du Halo). */
    public static boolean station(int i) {
        return GeneratedMegastructures.STATION[i];
    }

    /** Megastructure de l'astre, a sa surface ({@code station} faux) ou dans sa station, ou -1. */
    private static int find(@Nullable Body body, boolean station) {
        if (body != null) {
            for (int i = 0; i < COUNT; i++) {
                if (BODY[i] == body && GeneratedMegastructures.STATION[i] == station) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 1.0.7 (lot 9) : astre d'un lieu : celui du monde ; dans la dimension des stations, l'astre que survole la station s'il n'a pas de
     * surface (le Soleil : l'Essaim se livre et ses ports se batissent dans la station du Soleil).
     */
    @Nullable
    public static Body bodyAt(Level level, net.minecraft.core.BlockPos pos) {
        Body body = bodyOf.apply(level);
        if (body == null && level.dimension() == fr.pioneers.space.PiDimensions.ORBIT) {
            Body over = fr.pioneers.space.station.OrbitalStations.bodyAt(pos.getX());
            if (over != null && over.dimension() == null) {
                return over;
            }
        }
        return body;
    }

    /**
     * Megastructure du lieu, ou -1 : celle de l'astre du monde ; dans la dimension des stations, celle de station de l'astre survole
     * (Observatoire du Halo), sinon celle d'un astre sans surface (Essaim du Soleil).
     */
    public static int here(Level level, net.minecraft.core.BlockPos pos) {
        Body body = bodyOf.apply(level);
        if (body != null) {
            return find(body, false);
        }
        if (level.dimension() == fr.pioneers.space.PiDimensions.ORBIT) {
            Body over = fr.pioneers.space.station.OrbitalStations.bodyAt(pos.getX());
            int i = find(over, true);
            return i >= 0 ? i : over != null && over.dimension() == null ? find(over, false) : -1;
        }
        return -1;
    }

    /** Megastructure dont la machine est le port, ou -1. */
    public static int byPort(String machineKey) {
        Integer i = BY_PORT.get(machineKey);
        return i == null ? -1 : i;
    }

    public static boolean isPort(String machineKey) {
        return BY_PORT.containsKey(machineKey);
    }

    public static int ingredientCount(int i) {
        return GeneratedMegastructures.COUNTS[i].length;
    }

    public static Item ingredient(int i, int k) {
        resolve();
        return items[i][k];
    }

    public static ItemStack ingredientStack(int i, int k) {
        return new ItemStack(ingredient(i, k));
    }

    /** Nombre demande de l'ingredient k. */
    public static int needed(int i, int k) {
        return GeneratedMegastructures.COUNTS[i][k];
    }

    /** Nombre total d'objets de la liste. */
    public static int total(int i) {
        return TOTAL[i];
    }

    /** Composants dont la megastructure est la seule source (ids). */
    public static String[] gated(int i) {
        return GeneratedMegastructures.GATED[i];
    }

    /** Geometrie du ciel (index dans {@code SkyScene.MEGA_KINDS}). */
    public static int skyKind(int i) {
        return GeneratedMegastructures.SKY_KIND[i];
    }

    /** Reglages de la geometrie du ciel (voir T:au_dela/megastructures.py SKY_KINDS). */
    public static double sky(int i, int k) {
        return GeneratedMegastructures.SKY[i][k];
    }

    /** Tous les reglages de la geometrie du ciel (tableau partage : ne pas modifier). */
    public static double[] skyParams(int i) {
        return GeneratedMegastructures.SKY[i];
    }

    /** 1.0.7 (ciel grandiose) : couleur d'accent de la megastructure (0xRRGGBB). */
    public static int accent(int i) {
        return GeneratedMegastructures.ACCENT[i];
    }

    public static int color(int i) {
        return GeneratedMegastructures.COLOR[i];
    }

    /** Index de l'ingredient que la pile fournit, ou -1. */
    public static int ingredientOf(int i, ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        resolve();
        for (int k = 0; k < items[i].length; k++) {
            if (stack.is(items[i][k])) {
                return k;
            }
        }
        return -1;
    }

    private static void resolve() {
        if (items != null) {
            return;
        }
        Item[][] out = new Item[COUNT][];
        for (int i = 0; i < COUNT; i++) {
            String[] ids = GeneratedMegastructures.INGREDIENTS[i];
            out[i] = new Item[ids.length];
            for (int k = 0; k < ids.length; k++) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(ids[k]));
                out[i][k] = item == null ? Items.BARRIER : item;
            }
        }
        items = out;
    }
}
