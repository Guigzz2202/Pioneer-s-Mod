package fr.pioneers.space.research;

import fr.pioneers.Pioneers;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.registries.PiItems;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.research.ResearchNode.Kind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Les astres a maitriser et leurs quatre paliers de recherche (orbite, surface, geologie, biologie), regroupes en chapitres
 * (un par systeme).
 */
public final class BodyResearch {

    private BodyResearch() {
    }

    /** Chapitres (systemes) dans l'ordre de la progression ; le dernier (singularite) n'a pas d'astre. */
    public static final List<List<Body>> CHAPTERS = List.of(
          List.of(Body.MOON),
          List.of(Body.MARS, Body.PHOBOS, Body.DEIMOS),
          List.of(Body.VENUS, Body.MERCURY),
          List.of(Body.CERES, Body.VESTA, Body.PALLAS, Body.HYGIEA),
          List.of(Body.IO, Body.EUROPA, Body.GANYMEDE, Body.CALLISTO),
          List.of(Body.ENCELADUS, Body.TITAN),
          List.of(Body.MIRANDA, Body.TITANIA),
          List.of(Body.TRITON),
          List.of(Body.PLUTO, Body.CHARON),
          List.of(),
          //0.10 : geantes gazeuses (sans paliers d'astre), Alpha du Centaure, TRAPPIST-1, cosmos
          List.of(),
          List.of(Body.TOLIMAN_B, Body.PROXIMA_B, Body.PROXIMA_D),
          List.of(Body.TRAPPIST_1B, Body.TRAPPIST_1D, Body.TRAPPIST_1E, Body.TRAPPIST_1F),
          List.of()
    );
    /** Nombre de biomes differents a analyser pour la recherche de surface. */
    public static final int SURFACE_SCANS = 3;

    private static final Map<Body, String> ICON_BLOCKS = new EnumMap<>(Body.class);

    static {
        String[] icons = {"MOON", "moon_sand", "MARS", "mars_sand", "PHOBOS", "phobos_regolith", "DEIMOS", "deimos_regolith", "VENUS", "venus_regolith",
                          "MERCURY", "mercury_regolith", "CERES", "ceres_salt", "VESTA", "vesta_regolith", "PALLAS", "pallas_regolith",
                          "HYGIEA", "hygiea_regolith", "IO", "io_sulfur", "EUROPA", "europa_ice", "GANYMEDE", "ganymede_regolith",
                          "CALLISTO", "callisto_regolith", "ENCELADUS", "enceladus_snow", "TITAN", "titan_sand", "MIRANDA", "miranda_regolith",
                          "TITANIA", "titania_regolith", "TRITON", "triton_frost", "PLUTO", "pluto_frost", "CHARON", "charon_regolith",
                          "TOLIMAN_B", "toliman_moss", "PROXIMA_B", "proxima_twilight_soil", "PROXIMA_D", "proxima_d_slag",
                          "TRAPPIST_1B", "trappist_1b_obsidian_crust", "TRAPPIST_1D", "trappist_1d_sand", "TRAPPIST_1E", "trappist_1e_soil",
                          "TRAPPIST_1F", "trappist_1f_snow"};
        for (int i = 0; i < icons.length; i += 2) {
            ICON_BLOCKS.put(Body.valueOf(icons[i]), icons[i + 1]);
        }
    }

    /** L'astre a des recherches (et donc une progression a maitriser). */
    public static boolean isResearchBody(Body body) {
        return ICON_BLOCKS.containsKey(body);
    }

    /** Recherche d'un palier (null si l'astre n'a pas de recherches). */
    @Nullable
    public static ResearchNode node(Body body, Kind kind) {
        if (!isResearchBody(body)) {
            return null;
        }
        String prefix = switch (kind) {
            case LAB -> "ORBIT_";
            case SURFACE -> "SURFACE_";
            case GEOLOGY -> "GEOLOGY_";
            case BIOLOGY -> "BIOLOGY_";
            case ASTEROIDS -> null;
        };
        return prefix == null ? null : ResearchNode.valueOf(prefix + body.name());
    }

    /** Paliers termines (0 a 4) selon {@code done}. */
    public static int mastery(Body body, Predicate<ResearchNode> done) {
        int level = 0;
        for (Kind kind : new Kind[]{Kind.LAB, Kind.SURFACE, Kind.GEOLOGY, Kind.BIOLOGY}) {
            ResearchNode node = node(body, kind);
            if (node != null && done.test(node)) {
                level++;
            }
        }
        return level;
    }

    public static ItemStack icon(Body body, Kind kind) {
        return switch (kind) {
            case SURFACE -> new ItemStack(PiItems.SURFACE_SCANNER.get());
            case GEOLOGY -> {
                List<MiningMaterial> materials = MiningMaterial.of(body);
                yield materials.isEmpty() ? new ItemStack(PiVanillaMaterials.item("raw_iron")) : new ItemStack(PiItems.RAW.get(materials.get(0)).get());
            }
            case BIOLOGY -> {
                List<SpacePlant> plants = SpacePlant.of(body);
                yield plants.isEmpty() ? new ItemStack(Items.WHEAT) : new ItemStack(PiItems.PRODUCE.get(plants.get(plants.size() > 1 ? 1 : 0)).get());
            }
            default -> {
                Item item = ForgeRegistries.ITEMS.getValue(Pioneers.rl(ICON_BLOCKS.getOrDefault(body, "moon_sand")));
                yield new ItemStack(item == null ? Items.STONE : item);
            }
        };
    }
}
