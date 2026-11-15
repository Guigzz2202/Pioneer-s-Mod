// FICHIER GENERE par tools/gc/industry/nbt_models.py (1.0.4-beta, lot 25) : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.industry.multi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 25) : MODELES ET ANIMATIONS des multiblocs dans les structures de Guillaume ({@link GeneratedNbtShapes}, meme repere :
 * x a droite en regardant la facade, y en haut, z vers l'interieur, facade en z = 0 ; boites en cases, bornes comprises ; points en cases ;
 * pieces en pixels). SOURCE : {@code T:industry/nbt_models.py} ({@code MODELS}, {@code RETIRED}, {@code SPINS}, {@code PARTS}, {@code FX},
 * {@code HOUSE_FX}, {@code HOUSE_SPINS}). Lue par {@link NbtAnims} (serveur : boite de la machine interieure, blocs empilables masques ;
 * client : colonnes tournantes, pieces, particules).
 * <p>
 * Modeles interieurs d'avant RETIRES (interieur enferme, ou remplace) :
 * <ul>
 * <li>{@code arc_furnace} (FURNACE) : cuve fermee (toles)</li>
 * <li>{@code autoclave} (AUTOCLAVE) : cuve fermee (toles)</li>
 * <li>{@code bayer_digester} (TANK) : cuve fermee (toles)</li>
 * <li>{@code bender} (BRAKE) : plieuse fermee (toles)</li>
 * <li>{@code blast_furnace} (SHAFT) : cuve fermee (bobines, carcasses)</li>
 * <li>{@code boiler} (BOILER) : chaudiere fermee (toles)</li>
 * <li>{@code centrifuge} (CENTRIFUGE) : corps ferme (toles)</li>
 * <li>{@code chemical_reactor} (TANK) : cuve fermee (toles)</li>
 * <li>{@code coke_oven} (COKEBATTERY) : batterie fermee (carcasses)</li>
 * <li>{@code compressor} (PRESS) : presse fermee (toles, carcasses)</li>
 * <li>{@code converter} (FURNACE) : cornue fermee (carcasses)</li>
 * <li>{@code cracker} (HEATER) : colonne fermee (toles)</li>
 * <li>{@code cryogenic_unit} (SCREW) : corps ferme (toles)</li>
 * <li>{@code crystallizer} (TANK) : cuve fermee (toles)</li>
 * <li>{@code distillation_column} (COLUMN) : colonne fermee (toles)</li>
 * <li>{@code drill} (DRILL) : le train de forage de la structure tourne</li>
 * <li>{@code dryer} (KILN) : sechoir ferme (toles)</li>
 * <li>{@code extractor} (SCREW) : corps ferme (toles)</li>
 * <li>{@code extruder} (SCREW) : fourreau ferme (toles)</li>
 * <li>{@code fermenter} (TANK) : cuve fermee (toles)</li>
 * <li>{@code fluid_pump} (WELL) : les pompes a colonne de la structure tournent</li>
 * <li>{@code food_processor} (TANK) : cuve fermee (toles)</li>
 * <li>{@code furnace} (FURNACE) : four ferme (toles)</li>
 * <li>{@code gas_separator} (MEMBRANE) : colonnes pleines, sans interieur</li>
 * <li>{@code gas_turbine} (TURBINE) : turbine verticale : les rotors de 2,5 blocs poses dans la colonne centrale (lot 26) tournent</li>
 * <li>{@code hot_blast_stove} (HEATER) : cowper ferme, sans interieur</li>
 * <li>{@code hot_cell} (HOTCELL) : cellule fermee (toles, carcasses)</li>
 * <li>{@code kroll_reactor} (TANK) : cuve fermee (toles)</li>
 * <li>{@code pumpjack} (JACK) : balancier en cadres (fixe) : manivelles a contrepoids de part et d'autre du reducteur</li>
 * <li>{@code pyrolysis_oven} (KILN) : four ferme, sans interieur</li>
 * <li>{@code solid_combustion_generator} (BOILER) : chaudiere fermee (carcasses)</li>
 * <li>{@code steam_turbine} (TURBINE) : turbine verticale : les rotors de 2,5 blocs poses dans la colonne centrale (lot 26) tournent</li>
 * </ul>
 */
public final class GeneratedNbtModels {

    /** Machine interieure : famille, boite (cases), axe long le long de x (sinon z). */
    public record Model(BigInterior.Kind kind, int x0, int y0, int z0, int x1, int y1, int z1, boolean alongX) {
    }

    /** Colonne qui tourne : lettre (R rotor de turbine, D train de forage, P pompe a colonne : blocs poses), x, y0..y1, z (cases). */
    public record Spin(char letter, int x, int y0, int y1, int z) {
    }

    /** Piece mobile ajoutee (manivelle) : epaisseur x0..x1, centre (cy, cz), rayon (px). */
    public record Part(String kind, float x0, float x1, float cy, float cz, float r) {
    }

    /** Point d'emission de particules en marche (cases). */
    public record Fx(Kind kind, float x, float y, float z) {

        /** Sortes de particules. */
        public enum Kind {
            SMOKE, SOOT, FLAME, SPARK, EMBERS, STEAM, PUFF, FROST, DUST
        }
    }

    /** Animations d'un multibloc. */
    public record Anim(String key, @Nullable Model model, List<Spin> spins, List<Part> parts, List<Fx> fx) {
    }

    private static final Map<String, Anim> BY_KEY = new HashMap<>();
    private static final Map<String, Anim> HOUSE = new HashMap<>();
    /** Lettre -> {tours par seconde, montee, descente (ticks), echelle horizontale (balayage dans la case ou l'interieur)}. */
    public static final Map<Character, float[]> SPIN_SPEED = new HashMap<>();
    /** Piece -> {tours par seconde, montee, descente (ticks)}. */
    public static final Map<String, float[]> PART_SPEED = new HashMap<>();

    static {
        put("aquafarm", new Model(BigInterior.Kind.FISHFARM, 1, 2, 1, 3, 5, 3, false),
              List.of(),
              List.of(),
              List.of());
        put("arc_furnace", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SPARK, 3.5F, 5.2F, 3.5F), new Fx(Fx.Kind.SPARK, 3.5F, 5.2F, 4.5F), new Fx(Fx.Kind.SPARK, 3.5F, 5.2F, 5.5F), new Fx(Fx.Kind.SMOKE, 3.5F, 6.0F, 4.5F)));
        put("assembler", new Model(BigInterior.Kind.ASSEMBLY, 1, 1, 0, 1, 1, 6, false),
              List.of(),
              List.of(),
              List.of());
        put("autoclave", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.PUFF, 2.5F, 7.0F, 2.5F)));
        put("bayer_digester", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.PUFF, 3.5F, 9.0F, 3.5F)));
        put("blast_furnace", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.FLAME, 2.5F, 9.0F, 2.5F), new Fx(Fx.Kind.SOOT, 2.5F, 9.0F, 2.5F)));
        put("boiler", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 1.5F, 5.0F, 1.5F)));
        put("bottler", new Model(BigInterior.Kind.BOTTLER, 1, 1, 0, 1, 1, 6, false),
              List.of(),
              List.of(),
              List.of());
        put("casting_machine", new Model(BigInterior.Kind.CASTER, 1, 1, 0, 1, 2, 6, false),
              List.of(),
              List.of(),
              List.of());
        put("centrifuge_cascade", new Model(BigInterior.Kind.CASCADE, 0, 1, 1, 2, 4, 7, false),
              List.of(),
              List.of(),
              List.of());
        put("chemical_bath", new Model(BigInterior.Kind.GALVANIZE, 1, 1, 1, 1, 2, 3, false),
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 1.5F, 3.0F, 2.5F)));
        put("chemical_reactor", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.PUFF, 2.5F, 5.0F, 2.5F)));
        put("coke_oven", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SOOT, 1.5F, 4.0F, 1.5F)));
        put("converter", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.FLAME, 2.5F, 7.0F, 2.5F), new Fx(Fx.Kind.EMBERS, 2.5F, 7.0F, 2.5F), new Fx(Fx.Kind.SMOKE, 2.5F, 7.0F, 2.5F)));
        put("cracker", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.FLAME, 2.5F, 15.0F, 2.5F), new Fx(Fx.Kind.SMOKE, 2.5F, 15.0F, 2.5F)));
        put("crusher", new Model(BigInterior.Kind.CRUSHER, 1, 1, 1, 3, 3, 5, false),
              List.of(),
              List.of(),
              List.of());
        put("cryogenic_unit", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.FROST, 2.5F, 7.0F, 2.5F), new Fx(Fx.Kind.STEAM, 2.5F, 7.0F, 2.5F)));
        put("crystallizer", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 2.5F, 5.0F, 2.5F)));
        put("distillation_column", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 2.5F, 24.0F, 2.5F)));
        put("drill", null,
              List.of(new Spin('D', 2, 0, 6, 2)),
              List.of(),
              List.of(new Fx(Fx.Kind.DUST, 2.5F, 1.0F, 2.5F)));
        put("dryer", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 3.5F, 6.0F, 1.5F)));
        put("electric_crucible", new Model(BigInterior.Kind.FURNACE, 2, 2, 1, 4, 6, 3, false),
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.EMBERS, 3.5F, 7.0F, 2.5F), new Fx(Fx.Kind.SMOKE, 3.5F, 7.0F, 2.5F)));
        put("electrolyzer", new Model(BigInterior.Kind.CELL, 1, 1, 1, 1, 2, 3, false),
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 1.5F, 3.0F, 2.5F)));
        put("exhaust_stack", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 1.5F, 15.0F, 1.5F), new Fx(Fx.Kind.SMOKE, 1.5F, 15.0F, 1.5F)));
        put("fermenter", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.PUFF, 3.5F, 6.0F, 2.5F)));
        put("fluid_pump", null,
              List.of(new Spin('P', 2, 1, 3, 2)),
              List.of(),
              List.of());
        put("food_processor", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 1.5F, 5.0F, 1.5F)));
        put("furnace", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 1.5F, 4.0F, 1.5F)));
        put("gas_separator", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.STEAM, 2.5F, 10.0F, 2.5F)));
        put("gas_turbine", null,
              List.of(new Spin('R', 2, 1, 7, 2)),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 2.5F, 9.0F, 2.5F)));
        put("hot_blast_stove", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 2.5F, 7.0F, 2.5F)));
        put("hydroponic_farm", new Model(BigInterior.Kind.HYDRO, 1, 2, 1, 3, 4, 3, false),
              List.of(),
              List.of(),
              List.of());
        put("item_silo", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.DUST, 2.5F, 11.0F, 2.5F)));
        put("kroll_reactor", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 3.5F, 6.0F, 5.5F)));
        put("liquid_combustion_generator", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 1.0F, 3.0F, 1.0F), new Fx(Fx.Kind.SMOKE, 4.0F, 3.0F, 1.0F)));
        put("machining_center", new Model(BigInterior.Kind.LATHE, 1, 1, 0, 1, 1, 6, false),
              List.of(),
              List.of(),
              List.of());
        put("mixer", new Model(BigInterior.Kind.MIXCOLUMN, 3, 1, 0, 3, 3, 2, false),
              List.of(),
              List.of(),
              List.of());
        put("ore_separator", new Model(BigInterior.Kind.SIEVE, 1, 1, 1, 3, 2, 5, false),
              List.of(),
              List.of(),
              List.of());
        put("photolithograph", new Model(BigInterior.Kind.LITHO, 1, 1, 1, 3, 3, 3, false),
              List.of(),
              List.of(),
              List.of());
        put("pick_and_place", new Model(BigInterior.Kind.PICKPLACE, 1, 1, 0, 1, 1, 6, false),
              List.of(),
              List.of(),
              List.of());
        put("power_hammer", new Model(BigInterior.Kind.FORGE, 1, 1, 0, 3, 2, 2, true),
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.EMBERS, 2.5F, 1.2F, 1.5F)));
        put("press", new Model(BigInterior.Kind.HPRESS, 1, 1, 0, 3, 2, 2, true),
              List.of(),
              List.of(),
              List.of());
        put("pumpjack", null,
              List.of(),
              List.of(new Part("crank", 11.0F, 15.0F, 48.0F, 16.0F, 14.0F), new Part("crank", 33.0F, 37.0F, 48.0F, 16.0F, 14.0F)),
              List.of());
        put("pyrolysis_oven", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 1.5F, 4.0F, 2.5F)));
        put("recycler", new Model(BigInterior.Kind.CRUSHER, 1, 1, 1, 3, 5, 3, false),
              List.of(),
              List.of(),
              List.of());
        put("rolling_mill", new Model(BigInterior.Kind.ROLLING, 1, 1, 0, 1, 1, 6, false),
              List.of(),
              List.of(),
              List.of());
        put("sawmill", new Model(BigInterior.Kind.SAWLINE, 1, 1, 0, 1, 1, 6, false),
              List.of(),
              List.of(),
              List.of());
        put("semiconductor_fab", new Model(BigInterior.Kind.FAB, 1, 1, 1, 3, 3, 7, false),
              List.of(),
              List.of(),
              List.of());
        put("solid_combustion_generator", null,
              List.of(),
              List.of(),
              List.of(new Fx(Fx.Kind.SMOKE, 2.5F, 4.0F, 1.5F)));
        put("spent_fuel_pool", new Model(BigInterior.Kind.FUELPOOL, 1, 2, 1, 3, 3, 5, false),
              List.of(),
              List.of(),
              List.of());
        put("steam_turbine", null,
              List.of(new Spin('R', 2, 1, 7, 2)),
              List.of(),
              List.of());
        put("tree_plantation", new Model(BigInterior.Kind.TREES, 1, 2, 2, 5, 6, 4, true),
              List.of(),
              List.of(),
              List.of());
        put("wiremill", new Model(BigInterior.Kind.ROLLING, 1, 1, 0, 1, 1, 6, false),
              List.of(),
              List.of(),
              List.of());
        house("auto_foundry", List.of(), List.of(new Fx(Fx.Kind.SMOKE, 1.5F, 5.0F, 1.5F)));
        house("blast_furnace", List.of(), List.of(new Fx(Fx.Kind.FLAME, 2.5F, 7.0F, 2.5F), new Fx(Fx.Kind.SOOT, 2.5F, 7.0F, 2.5F)));
        house("bronze_boiler", List.of(), List.of(new Fx(Fx.Kind.PUFF, 1.5F, 4.0F, 0.5F)));
        house("coke_oven", List.of(), List.of(new Fx(Fx.Kind.SOOT, 1.5F, 3.8F, 1.5F)));
        house("pottery_kiln", List.of(), List.of(new Fx(Fx.Kind.SMOKE, 1.5F, 3.8F, 2.5F)));
        house("steam_drill", List.of(new Spin('D', 1, 0, 3, 1)), List.of(new Fx(Fx.Kind.PUFF, 1.5F, 7.0F, 1.5F)));
        house("steel_boiler", List.of(), List.of(new Fx(Fx.Kind.PUFF, 1.5F, 4.0F, 0.5F)));
        SPIN_SPEED.put('D', new float[]{0.5F, 80, 120, 0.847F});
        SPIN_SPEED.put('P', new float[]{0.75F, 60, 100, 1.0F});
        SPIN_SPEED.put('R', new float[]{1.5F, 160, 240, 1.0F});
        PART_SPEED.put("crank", new float[]{0.4F, 80, 120});
    }

    private GeneratedNbtModels() {
    }

    private static void put(String key, @Nullable Model model, List<Spin> spins, List<Part> parts, List<Fx> fx) {
        BY_KEY.put(key, new Anim(key, model, spins, parts, fx));
    }

    private static void house(String key, List<Spin> spins, List<Fx> fx) {
        HOUSE.put(key, new Anim(key, null, spins, List.of(), fx));
    }

    /** Animations d'un multibloc industriel a NBT (cle du catalogue), ou null. */
    @Nullable
    public static Anim of(String key) {
        return BY_KEY.get(key);
    }

    /** Animations d'un multibloc maison (cle de {@code GeneratedHouseNbt}), ou null. */
    @Nullable
    public static Anim house(String key) {
        return HOUSE.get(key);
    }

    /** Cles des multiblocs industriels animes. */
    public static java.util.Set<String> keys() {
        return BY_KEY.keySet();
    }
}
