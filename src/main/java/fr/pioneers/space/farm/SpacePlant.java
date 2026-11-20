package fr.pioneers.space.farm;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.crop.Climate;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.environment.Environments;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Les plantes spatiales : 3 par astre interieur, 2 par astre exterieur, chacune adaptee au climat de son monde.
 * Leurs recoltes nourrissent le sequenceur biologique (recherche biologique de leur astre).
 * Elles poussent a l'etat sauvage a la surface de leur astre (on les casse pour les graines),
 * et ne se cultivent que dans une serre spatiale construite sur ce meme astre.
 */
public enum SpacePlant {
    //Lune : vide, froid, regolithe gris
    SELENITE_LICHEN("selenite_lichen", "selenite_dust", Planet.MOON, 0, 3, 0, null, 0, 0),
    SILVER_BEAN("silver_bean_vine", "silver_bean", Planet.MOON, 0, 5, 5, MobEffects.SLOW_FALLING, 300, 0.6F),
    ALABASTER_ORCHID("alabaster_orchid", "alabaster_petal", Planet.MOON, 9, 12, 0, null, 0, 0),
    //Mars : froid, sec, sol ferreux
    RUST_FERN("rust_fern", "rust_fiber", Planet.MARS, 0, 3, 0, null, 0, 0),
    THARSIS_TUBER("tharsis_tuber_plant", "tharsis_tuber", Planet.MARS, 0, 5, 7, MobEffects.DAMAGE_RESISTANCE, 400, 0.7F),
    SCARLET_THISTLE("scarlet_thistle", "scarlet_resin", Planet.MARS, 0, 9, 0, null, 0, 0),
    //Venus : brulante, acide, nuages soufres
    SULFUR_BLOOM("sulfur_bloom", "sulfur_pollen", Planet.VENUS, 4, 3, 0, null, 0, 0),
    EMBER_POD("ember_pod_plant", "ember_pod", Planet.VENUS, 6, 6, 6, MobEffects.FIRE_RESISTANCE, 600, 0.5F),
    MAGMA_BRAMBLE("magma_bramble", "volcanic_thorn", Planet.VENUS, 7, 9, 0, null, 0, 0),
    //Mercure : soleil ecrasant, sol metallique
    SOLAR_HELIOTROPE("solar_heliotrope", "photonic_crystal", Planet.MERCURY, 12, 12, 0, null, 0, 0),
    FERROVINE("ferrovine", "metallic_fiber", Planet.MERCURY, 0, 4, 0, null, 0, 0),
    ASH_BERRY("ash_berry_bush", "ash_berry", Planet.MERCURY, 0, 6, 4, MobEffects.NIGHT_VISION, 900, 1.2F),
    //Phobos
    STICKNEY_MOSS("stickney_moss", "stickney_spores", Planet.PHOBOS, 0, 3, 0, null, 0, 0F),
    FEARCAP_MUSHROOM("fearcap_mushroom", "fearcap", Planet.PHOBOS, 2, 6, 4, MobEffects.NIGHT_VISION, 400, 0.6F),
    //Deimos
    DREAD_BRAMBLE("dread_bramble", "dread_spine", Planet.DEIMOS, 0, 4, 0, null, 0, 0F),
    PALE_TUFT("pale_tuft", "pale_fluff", Planet.DEIMOS, 0, 3, 0, null, 0, 0F),
    //Ceres
    BRINE_REED("brine_reed", "brine_crystal", Planet.CERES, 0, 4, 0, null, 0, 0F),
    OCCATOR_BELL("occator_bell", "bell_fruit", Planet.CERES, 6, 7, 5, MobEffects.REGENERATION, 100, 0.6F),
    //Vesta
    EUCRITE_LICHEN("eucrite_lichen", "eucrite_flakes", Planet.VESTA, 0, 3, 0, null, 0, 0F),
    VESTA_ROSE("vesta_rose", "vesta_hip", Planet.VESTA, 0, 7, 3, MobEffects.DAMAGE_BOOST, 200, 0.4F),
    //Pallas
    OLIVINE_SPROUT("olivine_sprout", "olivine_bud", Planet.PALLAS, 3, 5, 0, null, 0, 0F),
    PALLAS_FAN("pallas_fan", "pallas_frond", Planet.PALLAS, 0, 4, 0, null, 0, 0F),
    //Hygie
    CARBON_CREEPER("carbon_creeper", "carbon_tendril", Planet.HYGIEA, 0, 3, 0, null, 0, 0F),
    SOOT_PUFFBALL("soot_puffball", "soot_puff", Planet.HYGIEA, 0, 5, 0, null, 0, 0F),
    //Io
    SULFUR_TORCH("sulfur_torch", "torch_bulb", Planet.IO, 10, 6, 0, null, 0, 0F),
    PELE_FERN("pele_fern", "pele_frond", Planet.IO, 0, 4, 0, null, 0, 0F),
    //Europe
    ICE_LILY("ice_lily", "ice_lily_bulb", Planet.EUROPA, 4, 6, 4, MobEffects.WATER_BREATHING, 600, 0.5F),
    CHAOS_CORAL("chaos_coral", "chaos_polyp", Planet.EUROPA, 2, 4, 0, null, 0, 0F),
    //Ganymede
    SULCUS_GRASS("sulcus_grass", "sulcus_straw", Planet.GANYMEDE, 0, 3, 0, null, 0, 0F),
    AURORA_BLOOM("aurora_bloom", "aurora_petal", Planet.GANYMEDE, 8, 7, 0, null, 0, 0F),
    //Callisto
    VALHALLA_SHRUB("valhalla_shrub", "valhalla_berry", Planet.CALLISTO, 0, 5, 3, MobEffects.MOVEMENT_SPEED, 300, 0.4F),
    CRATER_MOSS("crater_moss", "crater_lint", Planet.CALLISTO, 0, 3, 0, null, 0, 0F),
    //Encelade
    GEYSER_PLUME("geyser_plume", "plume_down", Planet.ENCELADUS, 4, 5, 0, null, 0, 0F),
    TIGER_GRASS("tiger_grass", "tiger_blade", Planet.ENCELADUS, 0, 3, 0, null, 0, 0F),
    //Titan
    METHANE_REED("methane_reed", "methane_pith", Planet.TITAN, 0, 4, 0, null, 0, 0F),
    AMBER_GOURD_VINE("amber_gourd_vine", "amber_gourd", Planet.TITAN, 0, 7, 6, MobEffects.SATURATION, 20, 1.0F),
    //Miranda
    CLIFF_IVY("cliff_ivy", "ivy_strand", Planet.MIRANDA, 0, 3, 0, null, 0, 0F),
    CORONA_CACTUS("corona_cactus", "corona_pad", Planet.MIRANDA, 0, 6, 3, MobEffects.ABSORPTION, 400, 0.3F),
    //Titania
    FROST_FERN("frost_fern", "frost_frond", Planet.TITANIA, 0, 3, 0, null, 0, 0F),
    MESSINA_LANTERN("messina_lantern", "lantern_fruit", Planet.TITANIA, 9, 7, 4, MobEffects.GLOWING, 200, 0.5F),
    //Triton
    NITROGEN_SPIRE("nitrogen_spire", "spire_crystal", Planet.TRITON, 5, 4, 0, null, 0, 0F),
    CANTALOUPE_VINE("cantaloupe_vine", "triton_cantaloupe", Planet.TRITON, 0, 7, 6, MobEffects.HEALTH_BOOST, 600, 0.8F),
    //Pluton
    HEART_BLOOM("heart_bloom", "heart_petal", Planet.PLUTO, 4, 6, 0, null, 0, 0F),
    THOLIN_SEDGE("tholin_sedge", "tholin_fiber", Planet.PLUTO, 0, 3, 0, null, 0, 0F),
    //Charon
    MORDOR_THISTLE("mordor_thistle", "mordor_burr", Planet.CHARON, 0, 4, 0, null, 0, 0F),
    DUSK_MUSHROOM("dusk_mushroom", "dusk_cap", Planet.CHARON, 3, 6, 3, MobEffects.INVISIBILITY, 200, 0.4F),
    // --- 0.10 : exoplanetes ---
    TWINLEAF("twinleaf", "binary_pear", Planet.TOLIMAN_B, 0, 5, 6, MobEffects.REGENERATION, 160, 0.8F),
    TWILIGHT_LILY("twilight_lily", "twilight_bulb", Planet.PROXIMA_B, 7, 6, 0, null, 0, 0F),
    FLARE_THORN("flare_thorn", "flare_spine", Planet.PROXIMA_D, 9, 5, 0, null, 0, 0F),
    CINDER_CACTUS("cinder_cactus", "cinder_fig", Planet.TRAPPIST_1B, 4, 6, 5, MobEffects.FIRE_RESISTANCE, 1200, 0.6F),
    TIDE_KELP("tide_kelp", "tide_pearl", Planet.TRAPPIST_1D, 3, 5, 0, null, 0, 0F),
    EDEN_BERRY_BUSH("eden_berry_bush", "eden_berry", Planet.TRAPPIST_1E, 0, 4, 4, MobEffects.LUCK, 2400, 1.0F),
    FROST_BELL("frost_bell", "frost_petal", Planet.TRAPPIST_1F, 5, 6, 0, null, 0, 0F),
    // --- 1.0.7 (lot 11) : les trois systemes lointains, deux par planete (T:au_dela/far_systems.py PLANTS) ---
    BEAM_LICHEN("beam_lichen", "beam_scale", Planet.DRAUGR, 3, 4, 0, null, 0, 0F),
    MAGNETIC_THORN("magnetic_thorn", "lodestone_spine", Planet.DRAUGR, 0, 6, 0, null, 0, 0F),
    STORM_BELL("storm_bell", "storm_petal", Planet.POLTERGEIST, 7, 5, 0, null, 0, 0F),
    ION_REED("ion_reed", "ion_pith", Planet.POLTERGEIST, 2, 4, 0, null, 0, 0F),
    PULSE_FERN("pulse_fern", "pulse_frond", Planet.PHOBETOR, 4, 4, 0, null, 0, 0F),
    GLIMMER_LANTERN("glimmer_lantern", "glimmer_fruit", Planet.PHOBETOR, 9, 7, 4, MobEffects.NIGHT_VISION, 600, 0.5F),
    DIAMOND_SPIRE("diamond_spire", "carbon_needle", Planet.VAN_MAANEN_B, 6, 6, 0, null, 0, 0F),
    GRAPHITE_CREEPER("graphite_creeper", "graphite_tendril", Planet.VAN_MAANEN_B, 0, 4, 0, null, 0, 0F),
    FILING_SHRUB("filing_shrub", "iron_berry", Planet.VAN_MAANEN_C, 0, 5, 0, null, 0, 0F),
    RUST_CAP_MUSHROOM("rust_cap_mushroom", "rust_cap", Planet.VAN_MAANEN_C, 2, 6, 0, null, 0, 0F),
    SNOWGLASS_LILY("snowglass_lily", "snowglass_bulb", Planet.VAN_MAANEN_D, 5, 6, 0, null, 0, 0F),
    NITROGEN_TUFT("nitrogen_tuft", "nitrogen_fluff", Planet.VAN_MAANEN_D, 0, 4, 0, null, 0, 0F),
    DISK_BLOOM("disk_bloom", "disk_petal", Planet.GAIA_BH1_B, 10, 6, 0, null, 0, 0F),
    HALO_BERRY_BUSH("halo_berry_bush", "halo_berry", Planet.GAIA_BH1_B, 0, 5, 5, MobEffects.LUCK, 1200, 0.8F),
    TIDAL_CACTUS("tidal_cactus", "tidal_fig", Planet.GAIA_BH1_C, 4, 6, 5, MobEffects.FIRE_RESISTANCE, 900, 0.6F),
    STRETCHED_VINE("stretched_vine", "stretched_fiber", Planet.GAIA_BH1_C, 0, 4, 0, null, 0, 0F),
    LENS_KELP("lens_kelp", "lens_pearl", Planet.GAIA_BH1_D, 3, 5, 0, null, 0, 0F),
    RIM_FERN("rim_fern", "rim_frond", Planet.GAIA_BH1_D, 0, 4, 0, null, 0, 0F);

    /**
     * Astre de culture (memes noms que {@link Body}). Le sol naturel de chaque astre est donne par l'etiquette de blocs
     * {@code pioneers:soil/<astre>}.
     */
    public enum Planet {
        MOON, MARS, VENUS, MERCURY, PHOBOS, DEIMOS, CERES, VESTA, PALLAS, HYGIEA, IO, EUROPA, GANYMEDE, CALLISTO, ENCELADUS, TITAN, MIRANDA, TITANIA, TRITON, PLUTO, CHARON,
        //0.10 : exoplanetes
        TOLIMAN_B, PROXIMA_B, PROXIMA_D, TRAPPIST_1B, TRAPPIST_1D, TRAPPIST_1E, TRAPPIST_1F,
        //1.0.7 (lot 11) : les trois systemes lointains
        DRAUGR, POLTERGEIST, PHOBETOR, VAN_MAANEN_B, VAN_MAANEN_C, VAN_MAANEN_D, GAIA_BH1_B, GAIA_BH1_C, GAIA_BH1_D;

        private TagKey<Block> soil;

        public Body body() {
            return Body.valueOf(name());
        }

        public ResourceKey<Level> dimension() {
            return body().dimension();
        }

        public boolean isSoil(BlockState state) {
            if (soil == null) {
                soil = BlockTags.create(Pioneers.rl("soil/" + name().toLowerCase(Locale.ROOT)));
            }
            return state.is(soil);
        }

        public String getTranslationKey() {
            return "solar.pioneers." + name().toLowerCase();
        }
    }

    /** Identifiant du bloc sauvage (les graines sont "&lt;bloc&gt;_seeds"). */
    public final String blockName;
    /** Identifiant de la recolte. */
    public final String produceName;
    public final Planet planet;
    public final int light;
    /** Rarete a l'etat sauvage (1 groupe pour N chunks). */
    public final int rarity;
    /** Nourriture : points de faim (0 = materiau). */
    public final int nutrition;
    @Nullable
    public final MobEffect effect;
    public final int effectTicks;
    public final float saturation;

    SpacePlant(String blockName, String produceName, Planet planet, int light, int rarity, int nutrition, @Nullable MobEffect effect, int effectTicks,
          float saturation) {
        this.blockName = blockName;
        this.produceName = produceName;
        this.planet = planet;
        this.light = light;
        this.rarity = rarity;
        this.nutrition = nutrition;
        this.effect = effect;
        this.effectTicks = effectTicks;
        this.saturation = saturation;
    }

    public boolean isFood() {
        return nutrition > 0;
    }

    public String seedName() {
        return blockName + "_seeds";
    }

    public boolean growsIn(Level level) {
        return level.dimension() == planet.dimension();
    }

    // ------------------------------------------------------------------
    // 1.12.3 (lot F3) : climat d'origine et genetique (comme les cultures de la Terre)
    // ------------------------------------------------------------------

    /** Temperature moyenne de l'astre d'origine (degres Celsius, {@code data/<ns>/pioneers/environments}). */
    public int originTemperature() {
        return Environments.get(planet.dimension()).temperature();
    }

    /**
     * Marge de temperature toleree autour du climat d'origine (degres Celsius) : la marge des cultures de la Terre
     * ({@link Genes#tolerance()}, en unites de temperature de biome) convertie a l'echelle de la minimap (1 unite = 25 °C) : 2 °C
     * (Resistance 1) a 11 °C (Resistance 10).
     */
    public static int toleranceCelsius(Genes genes) {
        return Math.round(genes.tolerance() * 25F);
    }

    /**
     * Climat d'une serre spatiale pour cette plante : ideal sur son astre d'origine ; ailleurs, supportable (pousse ralentie, x0,45 comme
     * les cultures de la Terre) si la temperature moyenne de l'astre est dans la marge de la Resistance ; hostile sinon (rien ne pousse).
     */
    public Climate.Status climate(Level level, Genes genes) {
        if (growsIn(level)) {
            return Climate.Status.IDEAL;
        }
        Body here = SolarSystem.fromDimension(level.dimension());
        if (here == null) {
            return Climate.Status.HOSTILE;
        }
        int delta = Math.abs(Environments.get(level).temperature() - originTemperature());
        return delta <= toleranceCelsius(genes) ? Climate.Status.TOLERABLE : Climate.Status.HOSTILE;
    }

    /** Plantes d'un astre. */
    public static List<SpacePlant> of(Body body) {
        List<SpacePlant> list = new ArrayList<>();
        for (SpacePlant plant : values()) {
            if (plant.planet.body() == body) {
                list.add(plant);
            }
        }
        return list;
    }

    public static SpacePlant byIndex(int index) {
        SpacePlant[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
