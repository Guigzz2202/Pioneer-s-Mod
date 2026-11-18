package fr.pioneers.tech.industry;

import java.util.List;
import java.util.Map;

/**
 * GENERE par tools/gc/core/p105_lot46.py (NE PAS MODIFIER A LA MAIN) : 1.0.5-beta (lot 46, retour 72 de Guillaume : « Il manque 2
 * machines importantes : Un block qui genere de l'eau mkI a IV (Moins performant que la pompe multiblock) et une machine qui genere
 * les differents types de roches » ; « echantillon + eau + lave jamais consommee »). Lue par {@link RockSamples} et
 * {@code IndustryProcess} (genres {@code WATER} et {@code ROCKS} du catalogue).
 */
public final class GeneratedLot46 {

    /** Bloc a eau : debit (mB/t) par tier MKI..MKIV ; au plus le quart de {@link #PUMP_RATE}. */
    public static final int[] WATER_RATE = {25, 100, 400, 1600};
    /** Pompe multibloc du meme tier (mB/t), mesuree dans le code par le generateur (MultiProcess.tickPump, PowerRules.PARALLEL). */
    public static final int[] PUMP_RATE = {200, 800, 3200, 16000};
    /** Generateur de roches : ticks par roche (MKI..MKIV). */
    public static final int[] ROCK_TICKS = {40, 20, 10, 5};
    /** Eau et lave exigees dans les reservoirs du generateur de roches (mB), jamais prelevees. */
    public static final int SAMPLE_FLUID = 1000;

    /** Roches permises, dans l'ordre (JEI) ; tier minimal (0 = MKI .. 3 = MKIV) : {@link #ROCK_TIER}. */
    public static final List<String> ROCKS = List.of(
          "minecraft:stone", "minecraft:cobblestone", "minecraft:granite", "minecraft:diorite",
          "minecraft:andesite", "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:tuff",
          "minecraft:calcite", "minecraft:dripstone_block", "minecraft:basalt", "minecraft:smooth_basalt",
          "minecraft:netherrack", "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:blackstone",
          "minecraft:end_stone", "pioneers:moon_rock", "pioneers:lunar_basalt", "pioneers:mars_rock",
          "pioneers:mars_basalt", "pioneers:mars_layered_rock", "pioneers:mars_ochre_rock", "pioneers:phobos_rock",
          "pioneers:deimos_rock", "pioneers:venus_rock", "pioneers:venus_basalt", "pioneers:tessera_rock",
          "pioneers:mercury_rock", "pioneers:mercury_dark_rock", "pioneers:mercury_basalt", "pioneers:ceres_rock",
          "pioneers:vesta_basalt", "pioneers:pallas_rock", "pioneers:hygiea_rock", "pioneers:io_basalt",
          "pioneers:ganymede_rock", "pioneers:callisto_rock", "pioneers:miranda_rock", "pioneers:charon_rock",
          "pioneers:proxima_rock", "pioneers:proxima_d_rock", "pioneers:trappist_1b_rock", "pioneers:trappist_1d_rock",
          "pioneers:trappist_1e_rock", "pioneers:trappist_1f_rock");

    /** Roche permise -> tier minimal du generateur (0 = MKI .. 3 = MKIV). */
    public static final Map<String, Integer> ROCK_TIER = Map.ofEntries(
          Map.entry("minecraft:stone", 0),
          Map.entry("minecraft:cobblestone", 0),
          Map.entry("minecraft:granite", 0),
          Map.entry("minecraft:diorite", 0),
          Map.entry("minecraft:andesite", 0),
          Map.entry("minecraft:sandstone", 0),
          Map.entry("minecraft:red_sandstone", 0),
          Map.entry("minecraft:tuff", 1),
          Map.entry("minecraft:calcite", 1),
          Map.entry("minecraft:dripstone_block", 1),
          Map.entry("minecraft:basalt", 1),
          Map.entry("minecraft:smooth_basalt", 1),
          Map.entry("minecraft:netherrack", 1),
          Map.entry("minecraft:deepslate", 2),
          Map.entry("minecraft:cobbled_deepslate", 2),
          Map.entry("minecraft:blackstone", 2),
          Map.entry("minecraft:end_stone", 3),
          Map.entry("pioneers:moon_rock", 3),
          Map.entry("pioneers:lunar_basalt", 3),
          Map.entry("pioneers:mars_rock", 3),
          Map.entry("pioneers:mars_basalt", 3),
          Map.entry("pioneers:mars_layered_rock", 3),
          Map.entry("pioneers:mars_ochre_rock", 3),
          Map.entry("pioneers:phobos_rock", 3),
          Map.entry("pioneers:deimos_rock", 3),
          Map.entry("pioneers:venus_rock", 3),
          Map.entry("pioneers:venus_basalt", 3),
          Map.entry("pioneers:tessera_rock", 3),
          Map.entry("pioneers:mercury_rock", 3),
          Map.entry("pioneers:mercury_dark_rock", 3),
          Map.entry("pioneers:mercury_basalt", 3),
          Map.entry("pioneers:ceres_rock", 3),
          Map.entry("pioneers:vesta_basalt", 3),
          Map.entry("pioneers:pallas_rock", 3),
          Map.entry("pioneers:hygiea_rock", 3),
          Map.entry("pioneers:io_basalt", 3),
          Map.entry("pioneers:ganymede_rock", 3),
          Map.entry("pioneers:callisto_rock", 3),
          Map.entry("pioneers:miranda_rock", 3),
          Map.entry("pioneers:charon_rock", 3),
          Map.entry("pioneers:proxima_rock", 3),
          Map.entry("pioneers:proxima_d_rock", 3),
          Map.entry("pioneers:trappist_1b_rock", 3),
          Map.entry("pioneers:trappist_1d_rock", 3),
          Map.entry("pioneers:trappist_1e_rock", 3),
          Map.entry("pioneers:trappist_1f_rock", 3));

    /** Refus nommes (cle de raison) ; les minerais sont refuses par regle. */
    public static final Map<String, String> REFUSED = Map.ofEntries(
          Map.entry("minecraft:amethyst_block", "resource"),
          Map.entry("minecraft:ancient_debris", "resource"),
          Map.entry("minecraft:budding_amethyst", "resource"),
          Map.entry("minecraft:crying_obsidian", "resource"),
          Map.entry("minecraft:dark_prismarine", "resource"),
          Map.entry("minecraft:gilded_blackstone", "resource"),
          Map.entry("minecraft:glowstone", "resource"),
          Map.entry("minecraft:magma_block", "resource"),
          Map.entry("minecraft:obsidian", "resource"),
          Map.entry("minecraft:prismarine", "resource"),
          Map.entry("minecraft:purpur_block", "resource"),
          Map.entry("minecraft:sculk", "resource"),
          Map.entry("pioneers:europa_salt", "resource"),
          Map.entry("pioneers:io_red_sulfur", "resource"),
          Map.entry("pioneers:titania_rock", "resource"),
          Map.entry("pioneers:trappist_1d_shale", "resource"),
          Map.entry("pioneers:triton_rock", "resource"),
          Map.entry("pioneers:verdigris_rock", "resource"));

    private GeneratedLot46() {
    }
}
