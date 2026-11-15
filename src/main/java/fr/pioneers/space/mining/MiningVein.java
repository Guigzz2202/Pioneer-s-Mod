package fr.pioneers.space.mining;

import fr.pioneers.space.farm.SpacePlant.Planet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Types de gisements que le radar de prospection peut reperer, par astre.
 * La frequence depend du biome ou se trouve le gisement (le tharsium se cache sous les volcans de Tharsis,
 * la glace dans les calottes et les bassins d'ombre...). La durete fixe la tete de forage minimale.
 */
public enum MiningVein {
    //Lune
    LUNARIUM(Planet.MOON, 1, 0x8C78EC, "pioneers:raw_lunarium", 1, 2, "pioneers:raw_iron", 0.15F, 45, 1.0F, "lunar_maria", 70),
    ILMENITE(Planet.MOON, 1, 0x7A7A86, "pioneers:raw_iron", 1, 2, "pioneers:osmium_dust", 0.3F, 30, 1.2F, "lunar_maria", 45),
    SHADOW_ICE(Planet.MOON, 1, 0xBFE4FF, "pioneers:lunar_ice", 1, 2, "minecraft:packed_ice", 0.1F, 0, 0.8F, "shadow_basin", 90),
    IMPACT_MELT(Planet.MOON, 1, 0x6FA060, "pioneers:iron_meteorite", 1, 2, "pioneers:raw_gold", 0.2F, 8, 0.7F, "ejecta_fields", 50),
    //Mars
    MARSIUM(Planet.MARS, 2, 0xE2703F, "pioneers:raw_marsium", 1, 2, "pioneers:raw_iron", 0.2F, 45, 1.0F),
    HEMATITE(Planet.MARS, 1, 0x5A5E74, "pioneers:raw_iron", 1, 3, "pioneers:redstone", 0.35F, 25, 1.1F, "arcadia_planitia", 40, "valles_marineris", 45),
    THARSIUM(Planet.MARS, 4, 0x46E296, "pioneers:raw_tharsium", 1, 1, "pioneers:raw_marsium", 0.3F, 3, 0.6F, "tharsis_volcanoes", 40),
    POLAR_ICE(Planet.MARS, 1, 0xE8F4FF, "pioneers:mars_polar_ice", 1, 2, "pioneers:dry_ice", 0.3F, 0, 0.9F, "polar_ice_cap", 80),
    DELTA_SEDIMENTS(Planet.MARS, 1, 0xC9A27A, "pioneers:mars_clay", 1, 2, "pioneers:mars_gypsum", 0.3F, 0, 0.9F, "jezero_delta", 70),
    //Venus
    VENUSIUM(Planet.VENUS, 3, 0xF6C446, "pioneers:raw_venusium", 1, 2, "pioneers:sulfur", 0.35F, 45, 1.0F),
    SULFUR(Planet.VENUS, 1, 0xE8E060, "pioneers:sulfur", 2, 4, "minecraft:glowstone_dust", 0.1F, 30, 1.2F),
    GOLD(Planet.VENUS, 2, 0xFFD040, "pioneers:raw_gold", 1, 2, "pioneers:raw_copper", 0.3F, 18, 0.8F),
    //Mercure
    MERCURIUM(Planet.MERCURY, 4, 0xC4D0E2, "pioneers:raw_mercurium", 1, 2, "pioneers:raw_iron", 0.25F, 45, 1.0F),
    SIDEROPHILE(Planet.MERCURY, 2, 0x8E8A84, "pioneers:raw_iron", 2, 3, "pioneers:raw_tin", 0.35F, 30, 1.2F),
    URANINITE(Planet.MERCURY, 3, 0x6EE070, "pioneers:pitchblende", 1, 1, "pioneers:raw_lead", 0.4F, 15, 0.7F),
    //Phobos
    PHOBITE(Planet.PHOBOS, 2, 0x6E6258, "pioneers:raw_phobite", 1, 2, "pioneers:coal", 0.2F, 45, 1.0F, "stickney_crater", 80),
    PHOBOS_CARBON(Planet.PHOBOS, 1, 0x2E2A28, "pioneers:coal", 2, 4, "minecraft:flint", 0.3F, 30, 1.2F, "phobos_dark_plains", 55),
    //Deimos
    DEIMITE(Planet.DEIMOS, 2, 0xC8B49A, "pioneers:raw_deimite", 1, 2, "pioneers:raw_iron", 0.2F, 45, 1.0F, "deimos_bright_ridges", 80),
    DEIMOS_IRON(Planet.DEIMOS, 1, 0x8A7A6A, "pioneers:raw_iron", 1, 2, "pioneers:raw_copper", 0.25F, 30, 1.1F),
    //Ceres
    AHUNITE(Planet.CERES, 3, 0x9ED8F0, "pioneers:raw_ahunite", 1, 2, "pioneers:salt", 0.2F, 45, 1.0F, "ahuna_mons", 80),
    CERES_BRINE(Planet.CERES, 1, 0xE8F0F4, "pioneers:salt", 2, 4, "pioneers:ceres_ice", 0.3F, 30, 1.1F, "occator_faculae", 75),
    //Vesta
    VESTALITE(Planet.VESTA, 3, 0xC88A8A, "pioneers:raw_vestalite", 1, 2, "pioneers:raw_iron", 0.2F, 45, 1.0F, "rheasilvia_basin", 80),
    VESTA_IRON(Planet.VESTA, 2, 0x7A7A86, "pioneers:raw_iron", 2, 3, "pioneers:raw_gold", 0.15F, 30, 1.2F, "vesta_dark_terrain", 50),
    //Pallas
    PALLASITE(Planet.PALLAS, 3, 0x8EC24A, "pioneers:raw_pallasite", 1, 2, "pioneers:raw_iron", 0.2F, 45, 1.0F, "pallas_cratered_terrain", 80),
    PALLAS_OLIVINE(Planet.PALLAS, 2, 0x7AAA4A, "pioneers:raw_iron", 1, 2, "pioneers:emerald", 0.06F, 30, 1.0F, "pallas_great_basin", 50),
    //Hygie
    HYGEITE(Planet.HYGIEA, 3, 0x5A4A7A, "pioneers:raw_hygeite", 1, 2, "pioneers:coal", 0.2F, 45, 1.0F, "hygiea_cratered_highlands", 80),
    HYGIEA_CARBON(Planet.HYGIEA, 2, 0x1E1A24, "pioneers:coal", 2, 4, "pioneers:diamond", 0.03F, 30, 1.2F, "hygiea_carbon_fields", 70),
    //Io
    LOKITE(Planet.IO, 4, 0xF06A28, "pioneers:raw_lokite", 1, 2, "pioneers:sulfur", 0.2F, 45, 1.0F, "io_paterae", 80),
    IO_SULFUR(Planet.IO, 2, 0xE8E060, "pioneers:sulfur", 2, 4, "pioneers:redstone", 0.2F, 30, 1.2F, "io_sulfur_plains", 70),
    //Europe
    CONAMARITE(Planet.EUROPA, 4, 0xB89670, "pioneers:raw_conamarite", 1, 2, "pioneers:salt", 0.2F, 45, 1.0F, "europa_ridged_plains", 80),
    EUROPA_SALT(Planet.EUROPA, 2, 0xD8C8A8, "pioneers:salt", 2, 4, "minecraft:prismarine_crystals", 0.1F, 30, 1.1F, "conamara_chaos", 60),
    //Ganymede
    SULCITE(Planet.GANYMEDE, 4, 0xA8B4C0, "pioneers:raw_sulcite", 1, 2, "pioneers:raw_iron", 0.2F, 45, 1.0F, "uruk_sulcus", 80),
    GANYMEDE_MAGNETITE(Planet.GANYMEDE, 3, 0x4A4E58, "pioneers:raw_iron", 1, 3, "pioneers:osmium_dust", 0.25F, 30, 1.1F, "galileo_regio", 50),
    //Callisto
    VALHALLITE(Planet.CALLISTO, 4, 0x8C6A4C, "pioneers:raw_valhallite", 1, 2, "pioneers:callisto_ice", 0.2F, 45, 1.0F, "callisto_knobby_terrain", 80),
    CALLISTO_ICE(Planet.CALLISTO, 2, 0xC8DCEC, "pioneers:callisto_ice", 1, 2, "pioneers:raw_copper", 0.2F, 30, 0.9F, "valhalla_basin", 55),
    //Encelade
    TIGRITE(Planet.ENCELADUS, 5, 0x4ED8E8, "pioneers:raw_tigrite", 1, 2, "pioneers:enceladus_blue_ice", 0.2F, 45, 1.0F, "enceladus_cratered_terrain", 80),
    ENCELADUS_ICE(Planet.ENCELADUS, 2, 0x9EE0F4, "pioneers:enceladus_blue_ice", 1, 2, "pioneers:salt", 0.3F, 30, 0.9F, "tiger_stripes", 60),
    //Titan
    KRAKENITE(Planet.TITAN, 5, 0xD89A3A, "pioneers:raw_krakenite", 1, 2, "pioneers:coal", 0.2F, 45, 1.0F, "kraken_mare", 80),
    TITAN_HYDROCARBONS(Planet.TITAN, 3, 0x5A3A1E, "pioneers:coal", 2, 4, "pioneers:sulfur", 0.2F, 30, 1.2F, "titan_dune_fields", 55),
    //Miranda
    VERONITE(Planet.MIRANDA, 5, 0x6E86A8, "pioneers:raw_veronite", 1, 2, "pioneers:miranda_banded_ice", 0.2F, 45, 1.0F, "verona_rupes", 80),
    MIRANDA_ICE(Planet.MIRANDA, 3, 0xB4BCC8, "pioneers:miranda_banded_ice", 1, 2, "pioneers:raw_iron", 0.25F, 30, 0.9F),
    //Titania
    MESSINITE(Planet.TITANIA, 5, 0xC0A0B0, "pioneers:raw_messinite", 1, 2, "pioneers:raw_lead", 0.2F, 45, 1.0F, "titania_cratered_plains", 80),
    TITANIA_LEAD(Planet.TITANIA, 3, 0x7A7E8A, "pioneers:raw_lead", 1, 2, "pioneers:raw_iron", 0.25F, 30, 1.0F, "messina_chasmata", 50),
    //Triton
    RUACHITE(Planet.TRITON, 5, 0xE8B4C4, "pioneers:raw_ruachite", 1, 2, "pioneers:triton_nitrogen_ice", 0.2F, 45, 1.0F, "ruach_planitia", 80),
    TRITON_NITROGEN(Planet.TRITON, 3, 0xF0C8D4, "pioneers:triton_nitrogen_ice", 1, 2, "minecraft:packed_ice", 0.3F, 30, 0.9F, "triton_polar_cap", 60),
    //Pluton
    SPUTNIKITE(Planet.PLUTO, 5, 0xF0E0C0, "pioneers:raw_sputnikite", 1, 2, "pioneers:pluto_methane_ice", 0.2F, 45, 1.0F, "hillary_montes", 80),
    PLUTO_METHANE(Planet.PLUTO, 3, 0xB8D4F0, "pioneers:pluto_methane_ice", 1, 2, "pioneers:pluto_nitrogen_ice", 0.3F, 30, 0.9F, "sputnik_planitia", 60),
    //Charon
    MORDORITE(Planet.CHARON, 5, 0x9A3A2A, "pioneers:raw_mordorite", 1, 2, "pioneers:charon_tholin", 0.2F, 45, 1.0F, "serenity_chasma", 80),
    CHARON_THOLINS(Planet.CHARON, 3, 0x82493A, "pioneers:charon_tholin", 2, 3, "pioneers:pitchblende", 0.08F, 30, 1.0F, "mordor_macula", 60),
    // --- 0.10 : exoplanetes ---
    TOLIMANITE(Planet.TOLIMAN_B, 5, 0xE0D070, "pioneers:raw_tolimanite", 1, 2, "pioneers:raw_gold", 0.25F, 45, 1.0F, "verdigris_highlands", 80),
    TOLIMAN_COPPER(Planet.TOLIMAN_B, 3, 0x4FA088, "pioneers:raw_copper", 2, 4, "pioneers:emerald", 0.05F, 30, 1.2F, "toliman_verdant_lowlands", 55),
    TERMINITE(Planet.PROXIMA_B, 5, 0xC07AB8, "pioneers:raw_terminite", 1, 2, "minecraft:amethyst_shard", 0.25F, 45, 1.0F, "proxima_twilight", 80),
    PROXIMA_IRON(Planet.PROXIMA_B, 3, 0x9A5A44, "pioneers:raw_iron", 2, 3, "pioneers:proxima_glacier_ice", 0.2F, 30, 1.1F, "proxima_dayside_scorch", 55),
    FLARIUM(Planet.PROXIMA_D, 5, 0xFFB050, "pioneers:raw_flarium", 1, 2, "pioneers:flare_crystal", 0.2F, 45, 1.0F, "proxima_d_flare_fields", 80),
    PROXIMA_D_SLAG(Planet.PROXIMA_D, 3, 0x6A4A3A, "pioneers:raw_iron", 2, 4, "pioneers:raw_gold", 0.2F, 30, 1.2F, "proxima_d_iron_ridges", 60),
    PYRRHITE(Planet.TRAPPIST_1B, 5, 0xB0302A, "pioneers:raw_pyrrhite", 1, 2, "minecraft:magma_block", 0.25F, 45, 1.0F, "trappist_1b_volcanic_peaks", 80),
    TRAPPIST_1B_OBSIDIAN(Planet.TRAPPIST_1B, 4, 0x2A1E36, "minecraft:obsidian", 1, 2, "pioneers:sulfur", 0.3F, 30, 1.0F, "trappist_1b_obsidian_flats", 65),
    THALASSITE(Planet.TRAPPIST_1D, 5, 0x3A7ED8, "pioneers:raw_thalassite", 1, 2, "minecraft:prismarine_crystals", 0.25F, 45, 1.0F, "trappist_1d_archipelago", 80),
    TRAPPIST_1D_SALT(Planet.TRAPPIST_1D, 3, 0xE0E8EC, "pioneers:salt", 3, 5, "minecraft:prismarine_shard", 0.2F, 30, 1.2F, "trappist_1d_tidal_flats", 60),
    EDENITE(Planet.TRAPPIST_1E, 5, 0x9A5AE0, "pioneers:raw_edenite", 1, 2, "pioneers:lapis", 0.3F, 45, 1.0F, "trappist_1e_slate_hills", 80),
    TRAPPIST_1E_LAPIS(Planet.TRAPPIST_1E, 3, 0x5A3AA8, "pioneers:lapis", 3, 6, "minecraft:amethyst_shard", 0.2F, 30, 1.1F, "trappist_1e_violet_meadows", 55),
    BOREALITE(Planet.TRAPPIST_1F, 5, 0x9AF0E8, "pioneers:raw_borealite", 1, 2, "pioneers:trappist_1f_blue_ice", 0.25F, 45, 1.0F, "trappist_1f_cryovolcanic_ridges", 80),
    TRAPPIST_1F_ICE(Planet.TRAPPIST_1F, 3, 0x7AB8E8, "pioneers:trappist_1f_blue_ice", 1, 2, "minecraft:blue_ice", 0.2F, 30, 0.9F, "trappist_1f_ice_sheet", 60);

    public final Planet body;
    public final int hardness;
    public final int color;
    private final String primaryId, byproductId;
    public final int minCount, maxCount;
    public final float byproductChance;
    public final int baseWeight;
    /** Taille relative du gisement. */
    public final float size;
    private final Map<String, Integer> biomeWeights = new HashMap<>();

    MiningVein(Planet body, int hardness, int color, String primary, int minCount, int maxCount, String byproduct, float byproductChance, int baseWeight,
          float size, Object... biomes) {
        this.body = body;
        this.hardness = hardness;
        this.color = color;
        this.primaryId = primary;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.byproductId = byproduct;
        this.byproductChance = byproductChance;
        this.baseWeight = baseWeight;
        this.size = size;
        for (int i = 0; i < biomes.length; i += 2) {
            biomeWeights.put("pioneers:" + biomes[i], (Integer) biomes[i + 1]);
        }
    }

    /** Poids de tirage dans un biome donne (identifiant complet). */
    public int weightIn(@Nullable String biome) {
        return biome == null ? baseWeight : biomeWeights.getOrDefault(biome, baseWeight);
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
        return item == null || item == Items.AIR ? Items.COBBLESTONE : item;
    }

    public Item getPrimary() {
        return item(primaryId);
    }

    public Item getByproduct() {
        return item(byproductId);
    }

    public ItemStack getIcon() {
        return new ItemStack(getPrimary());
    }

    public String getTranslationKey() {
        return "mining.pioneers.vein." + name().toLowerCase(Locale.ROOT);
    }

    public static MiningVein byIndex(int index) {
        MiningVein[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
