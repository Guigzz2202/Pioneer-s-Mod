package fr.pioneers.farm;

import java.util.List;

/** GENERE par tools/gc/farm2/gen_java.py : ids de l'Agriculture 2.0 pour l'onglet creatif (Java pur, sans Minecraft). */
public final class FarmIds {

    private FarmIds() {
    }

    /** Graines, dans l'ordre des cultures. */
    public static final List<String> SEEDS = List.of(
          "wheat_seeds", "barley_seeds", "oats_seeds", "rye_seeds", "rice_seeds", "amaranth_seeds", "corn_seeds", "cotton_seeds",
          "flax_seeds", "hemp_seeds", "rapeseed_seeds", "sunflower_sowing_seeds", "soybean_seeds", "hops_seeds", "lettuce_seeds",
          "spinach_seeds", "kale_seeds", "cabbage_seeds", "cauliflower_seeds", "broccoli_seeds", "bok_choy_seeds", "leek_seeds",
          "artichoke_seeds", "rhubarb_seeds", "basil_seeds", "carrot_seeds", "potato_seeds", "beetroot_seeds", "parsnip_seeds",
          "radish_seeds", "sweet_potato_seeds", "onion_seeds", "garlic_seeds", "tomato_seeds", "green_bean_seeds", "pea_seeds",
          "cucumber_seeds", "bell_pepper_seeds", "hot_pepper_seeds", "eggplant_seeds", "zucchini_seeds", "pumpkin_seeds", "melon_seeds",
          "strawberry_seeds", "blueberry_seeds", "cranberry_seeds", "grape_seeds", "pineapple_seeds", "starfruit_seeds", "coffee_seeds",
          "tobacco_seeds", "tea_seeds", "brown_mushroom_spores", "red_mushroom_spores", "button_mushroom_spores"
    );

    /** Recoltes du mod, dans l'ordre des cultures. */
    public static final List<String> PRODUCE = List.of(
          "barley", "oats", "rye", "paddy_rice", "amaranth", "corn", "cotton", "flax", "hemp_stalk", "rapeseed", "sunflower_seeds",
          "soybean", "hops", "lettuce", "spinach", "kale", "red_cabbage", "cauliflower", "broccoli", "bok_choy", "leek", "artichoke",
          "rhubarb", "basil", "parsnip", "radish", "sweet_potato", "onion", "garlic", "tomato", "green_bean", "pea_pod", "cucumber",
          "bell_pepper", "hot_pepper", "eggplant", "zucchini", "strawberry", "blueberry", "cranberry", "grapes", "pineapple", "starfruit",
          "coffee_cherries", "tobacco_leaf", "tea_leaves", "button_mushroom"
    );

    /** Fruits des arbres. */
    public static final List<String> FRUITS = List.of(
          "pear", "cherries", "apricot", "peach", "plum", "orange", "lemon", "pomegranate", "mango", "banana", "olives"
    );

    /** Pousses d'arbres fruitiers. */
    public static final List<String> SAPLINGS = List.of(
          "apple_sapling", "pear_sapling", "cherry_sapling", "apricot_sapling", "peach_sapling", "plum_sapling", "orange_sapling",
          "lemon_sapling", "pomegranate_sapling", "mango_sapling", "banana_sapling", "olive_sapling"
    );

    /** Feuilles d'arbres fruitiers. */
    public static final List<String> LEAVES = List.of(
          "apple_leaves", "pear_leaves", "cherry_leaves", "apricot_leaves", "peach_leaves", "plum_leaves", "orange_leaves", "lemon_leaves",
          "pomegranate_leaves", "mango_leaves", "banana_leaves", "olive_leaves"
    );

    /** Contenants. */
    public static final List<String> CONTAINERS = List.of(
          "cup", "unfired_cup", "wooden_mug", "bottle", "tin_can"
    );

    /** Ingredients. */
    public static final List<String> INGREDIENTS = List.of(
          "oat_flakes", "white_rice", "corn_meal", "green_malt", "malt", "crushed_malt", "yeast", "cream", "butter", "curd", "cheese",
          "tomato_sauce", "cooking_oil", "vinegar", "dried_chili", "green_coffee", "roasted_coffee", "ground_coffee", "dried_tea",
          "cured_tobacco", "pipe_tobacco", "roasted_cocoa", "cocoa_powder", "tofu", "beet_sugar", "fresh_cheese_wheel",
          "aged_cheese_wheel", "dried_fish_fillet", "cured_salmon", "cured_bacon", "mayonnaise", "tomato_slices", "ketchup", "pesto",
          "vinaigrette", "bechamel", "vegetable_broth", "chicken_broth", "fish_stock", "breadcrumbs", "croutons", "grated_cheese",
          "whipped_cream", "custard", "caramel", "tortilla", "salsa", "soy_sauce", "curry_paste", "herb_butter", "tapenade",
          "berry_coulis", "sauerkraut", "hot_sauce", "cranberry_sauce", "champagne_tirage", "sparkling_cider_tirage", "dried_game_meat",
          "lamp_oil"
    );

    /** Preparations crues. */
    public static final List<String> PREPARATIONS = List.of(
          "dough", "pastry_dough", "pasta_dough", "fresh_pasta", "batter", "cookie_dough", "cake_batter", "beaten_eggs", "potato_sticks",
          "minced_meat", "bacon", "raw_sausage", "fish_fillet", "raw_croissant", "raw_apple_pie", "raw_cherry_pie", "raw_berry_pie",
          "raw_pumpkin_pie", "raw_pizza", "raw_quiche", "raw_gratin", "raw_lasagna", "raw_stuffed_peppers", "masa", "raw_multigrain_bread",
          "raw_granola", "raw_oat_cookies", "raw_rye_bread", "raw_crispbread", "raw_amaranth_fritters", "raw_cornbread", "raw_seed_bread",
          "raw_spanakopita", "raw_gyoza", "raw_cauliflower_gratin", "raw_vegetable_quiche", "raw_flamiche", "raw_stuffed_artichokes",
          "raw_artichoke_dip", "raw_veggie_pizza", "raw_rhubarb_pie", "raw_strawberry_rhubarb_crisp", "raw_margherita", "raw_carrot_cake",
          "raw_roasted_roots", "sweet_potato_sticks", "raw_sweet_potato_pie", "raw_samosas", "raw_moussaka", "raw_eggplant_parmigiana",
          "raw_zucchini_fritters", "raw_stuffed_zucchini", "raw_zucchini_bread", "raw_pumpkin_bread", "raw_tian", "raw_crumble",
          "raw_fruit_tart", "raw_tarte_tatin", "raw_pear_tart", "raw_clafoutis", "raw_black_forest", "raw_plum_tart", "raw_apricot_tart",
          "raw_orange_cake", "raw_lemon_tart", "raw_lemon_chicken", "raw_banana_bread", "raw_banana_fritters", "raw_pineapple_cake",
          "raw_starfruit_tart", "raw_blueberry_muffins", "raw_cranberry_scones", "raw_strawberry_tart", "raw_cheesecake", "raw_grape_tart",
          "raw_focaccia", "raw_fish_skewers", "raw_fish_pie", "raw_papillote", "raw_pissaladiere", "raw_friture", "raw_fish_cakes",
          "raw_stuffed_fish", "raw_tropical_fish_grill", "raw_creole_fritters", "raw_lobster_thermidor", "raw_crab_cakes",
          "raw_shrimp_tempura", "raw_croque_monsieur", "raw_royal_omelette", "raw_stuffed_mushrooms", "raw_mushroom_pie", "raw_baguette",
          "raw_brioche", "raw_chocolate_croissant", "raw_raisin_swirl", "madeleine_batter", "brownie_batter", "raw_pound_cake",
          "raw_kouign_amann", "raw_pretzel", "raw_apple_turnover", "raw_game_skewers", "raw_stuffed_turkey", "raw_panini"
    );

    /** Plats. */
    public static final List<String> DISHES = List.of(
          "yogurt", "smoked_bacon", "ham", "sausage", "smoked_sausage", "smoked_fish", "raisins", "dried_fruit", "chocolate", "bread",
          "croissant", "cookies", "pancakes", "jam_toast", "cake", "apple_pie", "cherry_pie", "berry_pie", "pumpkin_pie", "pizza",
          "quiche", "porridge", "rice_pudding", "tomato_soup", "vegetable_soup", "pumpkin_soup", "onion_soup", "mushroom_soup", "borscht",
          "fish_soup", "beef_stew", "rabbit_stew", "chili", "risotto", "paella", "ratatouille", "pasta_tomato", "fried_rice", "raclette",
          "salad", "fruit_salad", "steak", "grilled_pork", "roast_chicken", "grilled_mutton", "roast_rabbit", "grilled_fish",
          "grilled_salmon", "fried_egg", "omelette", "fries", "baked_potato", "gratin", "lasagna", "stuffed_peppers", "roasted_vegetables",
          "burger", "sandwich", "fish_and_chips", "sushi", "caviar_toast", "jam", "pickles", "caviar", "canned_food", "cured_ham",
          "saucisson", "beef_jerky", "smoked_salmon", "mashed_potatoes", "barley_soup", "orzotto", "scotch_broth", "multigrain_bread",
          "grain_salad", "granola", "muesli", "oat_cookies", "rye_bread", "crispbread", "rye_porridge", "amaranth_porridge", "alegria",
          "amaranth_fritters", "popcorn", "polenta", "cornbread", "corn_chowder", "roasted_corn", "tacos", "fajitas", "nachos",
          "roasted_sunflower_seeds", "seed_bread", "edamame", "miso_soup", "caesar_salad", "blt", "nicoise_salad", "creamed_spinach",
          "spanakopita", "saag", "kale_chips", "caldo_verde", "kale_salad", "colcannon", "braised_red_cabbage", "stuffed_cabbage",
          "coleslaw", "choucroute", "kimchi", "kimchi_radish", "stir_fry", "ramen", "gyoza", "braised_bok_choy", "lettuce_soup",
          "cauliflower_gratin", "aloo_gobi", "roasted_cauliflower", "cauliflower_soup", "broccoli_soup", "roasted_broccoli",
          "broccoli_pasta", "vegetable_quiche", "vichyssoise", "flamiche", "leeks_vinaigrette", "pot_au_feu", "stuffed_artichokes",
          "artichoke_dip", "artichokes_vinaigrette", "veggie_pizza", "rhubarb_pie", "rhubarb_compote", "strawberry_rhubarb_crisp",
          "caprese", "pesto_pasta", "margherita", "bruschetta", "carrot_cake", "glazed_carrots", "carrot_soup", "roasted_roots",
          "root_mash", "beet_salad", "beet_risotto", "beet_chips", "parsnip_soup", "parsnip_fries", "radish_toast", "radish_salad",
          "sweet_potato_fries", "sweet_potato_pie", "sweet_potato_curry", "green_bean_salad", "sauteed_green_beans", "minestrone",
          "pea_soup", "peas_french", "samosas", "spring_rice", "tzatziki", "gazpacho", "cucumber_salad", "maki", "fajita_veggies",
          "shakshuka", "peperonata", "chili_beans", "moussaka", "baba_ganoush", "eggplant_parmigiana", "grilled_eggplant",
          "zucchini_fritters", "stuffed_zucchini", "zucchini_bread", "roasted_pumpkin", "pumpkin_risotto", "pumpkin_curry",
          "pumpkin_bread", "ratatouille_tian", "melon_prosciutto", "watermelon_feta", "crumble", "fruit_compote", "stone_fruit_compote",
          "fruit_tart", "tarte_tatin", "baked_apple", "poached_pear", "pear_tart", "pear_salad", "clafoutis", "black_forest",
          "cherry_soup", "peach_melba", "plum_tart", "apricot_tart", "grilled_peaches", "plum_dumplings", "apricot_chicken",
          "orange_chicken", "orange_cake", "crepes_suzette", "citrus_salad", "lemon_tart", "lemon_chicken", "pomegranate_salad",
          "mango_sticky_rice", "mango_chutney", "tropical_salad", "banana_bread", "banana_fritters", "banana_split", "banana_pancakes",
          "pineapple_cake", "sweet_sour_pork", "grilled_pineapple", "starfruit_tart", "starfruit_salad", "blueberry_muffins",
          "blueberry_pancakes", "festive_roast", "cranberry_scones", "strawberry_tart", "strawberry_shortcake", "cheesecake",
          "berry_parfait", "grape_tart", "grape_salad", "focaccia", "tapenade_toast", "baked_whole_fish", "fish_skewers", "fisherman_stew",
          "fish_curry", "brandade", "fish_pie", "papillote", "meuniere", "ceviche", "fish_tacos", "escabeche", "poke_bowl", "pissaladiere",
          "sardines_tomato", "rollmops", "grilled_tuna_steak", "matelote", "friture", "fish_cakes", "stuffed_fish", "quenelles",
          "fish_in_beer", "tropical_fish_grill", "island_curry", "tropical_ceviche", "spicy_fish_soup", "creole_fritters", "bisque",
          "seafood_pasta", "seafood_risotto", "paella_marinera", "gumbo", "seafood_platter", "lobster_thermidor", "crab_cakes",
          "shrimp_cocktail", "crayfish_etouffee", "king_crab_legs", "garlic_shrimp", "shrimp_tempura", "crab_salad", "crayfish_boil",
          "lobster_roll", "burger_deluxe", "croque_monsieur", "fish_burger", "chicken_with_mushrooms", "royal_omelette",
          "stuffed_mushrooms", "mushroom_pie", "sauteed_mushrooms", "baguette", "brioche", "chocolate_croissant", "raisin_swirl",
          "madeleines", "brownies", "pound_cake", "kouign_amann", "pretzel", "apple_turnover", "smoked_game_meat", "game_stew",
          "game_skewers", "stuffed_turkey", "pigeon_with_peas", "skate_with_butter", "game_pate", "cheese_sandwich", "tuna_sandwich",
          "club_sandwich", "egg_sandwich", "salmon_sandwich", "veggie_sandwich", "chicken_sandwich", "pate_sandwich", "panini",
          "barley_sugar", "butter_caramels", "fruit_jellies", "marshmallows", "nougat", "lollipop", "sour_drops", "chocolate_truffles"
    );

    /** Boissons. */
    public static final List<String> DRINKS = List.of(
          "coffee", "tea", "hot_chocolate", "apple_juice", "orange_juice", "grape_juice", "pear_juice", "lemonade", "beer", "wine",
          "aged_wine", "cider", "perry", "mead", "whisky", "vodka", "rum", "brandy", "kvass", "green_smoothie", "rhubarb_lemonade",
          "agua_fresca", "smoothie", "pomegranate_juice", "mango_lassi", "island_punch", "champagne", "sparkling_cider"
    );

    /** Seaux des fluides de la cuisine. */
    public static final List<String> FLUIDS = List.of(
          "grape_must_bucket", "apple_juice_bucket", "pear_juice_bucket", "orange_juice_bucket", "cane_juice_bucket", "honey_water_bucket",
          "sweet_wort_bucket", "hopped_wort_bucket", "potato_mash_bucket", "beer_bucket", "wine_bucket", "aged_wine_bucket",
          "cider_bucket", "perry_bucket", "mead_bucket", "potato_wash_bucket", "cane_wash_bucket", "vinegar_bucket", "raw_whisky_bucket",
          "whisky_bucket", "vodka_bucket", "rum_bucket", "brandy_bucket", "honey_bucket"
    );

    /** Postes de cuisine et de transformation. */
    public static final List<String> STATIONS = List.of(
          "cutting_board", "kitchen_stove", "cooking_pot", "frying_pan", "bread_oven", "mixing_bowl", "drying_rack", "preserves_jar",
          "fermenting_vat", "aging_barrel", "brew_kettle", "copper_still", "fruit_press", "cheese_press", "butter_churn", "can_sealer",
          "smokehouse"
    );

    /** Ferme animee (1.3.1) : arroseurs, composteur, epouvantail, meteo, ruche, pondoir, couveuse, distributeur. */
    public static final List<String> DEVICES = List.of(
          "bronze_sprinkler", "steel_sprinkler", "compost_bin", "compost", "scarecrow", "weather_station", "apiary", "hive_frame",
          "honey_frame", "honey_extractor", "beeswax", "nest_box", "fertile_egg", "incubator", "auto_feeder", "dish_shelf", "crow_spawn_egg"
    );

    /** Elevage : mangeoire, abreuvoir, aliment compose. */
    public static final List<String> LIVESTOCK = List.of(
          "feeder", "water_trough", "animal_feed"
    );

    /** Peche : cannes, appats, seau, oeufs. */
    public static final List<String> FISHING = List.of(
          "bamboo_fishing_rod", "bronze_fishing_rod", "steel_fishing_rod", "earthworm", "fish_bait", "fish_bucket", "fish_roe",
          "sturgeon_roe", "fish_spawn_egg"
    );

    /** Poissons du mod. */
    public static final List<String> FISH = List.of(
          "brook_trout", "arctic_char", "grayling", "pike", "sturgeon", "burbot", "carp", "perch", "roach", "bream", "zander", "catfish",
          "tench", "eel", "barbel", "tilapia", "piranha", "arapaima", "electric_eel", "discus", "gar", "snakehead", "bowfin", "cavefish",
          "glowfish", "herring", "mackerel", "pollock", "halibut", "wolffish", "sea_bass", "sea_bream", "sardine", "anchovy", "sole",
          "turbot", "red_mullet", "conger", "monkfish", "tuna", "swordfish", "mahi_mahi", "barracuda", "grouper", "clownfish", "lionfish",
          "anglerfish", "lanternfish", "oarfish", "coelacanth", "gray_reef_shark"
    );

    /** 1.0.3-beta lot 8 : oeufs d'apparition des races (espece par espece, ordre des races). */
    public static final List<String> BREED_EGGS = List.of(
          "holstein_cow_spawn_egg", "jersey_cow_spawn_egg", "charolais_cow_spawn_egg", "highland_cow_spawn_egg", "angus_cow_spawn_egg",
          "merino_sheep_spawn_egg", "suffolk_sheep_spawn_egg", "texel_sheep_spawn_egg", "ouessant_sheep_spawn_egg",
          "jacob_sheep_spawn_egg", "large_white_pig_spawn_egg", "gascon_pig_spawn_egg", "duroc_pig_spawn_egg", "pietrain_pig_spawn_egg",
          "kunekune_pig_spawn_egg", "leghorn_chicken_spawn_egg", "marans_chicken_spawn_egg", "brahma_chicken_spawn_egg",
          "gauloise_chicken_spawn_egg", "silkie_chicken_spawn_egg", "alpine_goat_spawn_egg", "saanen_goat_spawn_egg",
          "boer_goat_spawn_egg", "angora_goat_spawn_egg", "poitevine_goat_spawn_egg"
    );

    /** 1.0.3-beta lot 8 : oeufs d'apparition des poissons (ordre des especes ; 1.0.5-beta lot 28 : sans les especes de peche seulement). */
    public static final List<String> FISH_EGGS = List.of(
          "fish_brook_trout_spawn_egg", "fish_arctic_char_spawn_egg", "fish_grayling_spawn_egg", "fish_pike_spawn_egg",
          "fish_salmon_spawn_egg", "fish_sturgeon_spawn_egg", "fish_burbot_spawn_egg", "fish_carp_spawn_egg", "fish_perch_spawn_egg",
          "fish_roach_spawn_egg", "fish_bream_spawn_egg", "fish_zander_spawn_egg", "fish_catfish_spawn_egg", "fish_tench_spawn_egg",
          "fish_eel_spawn_egg", "fish_barbel_spawn_egg", "fish_tilapia_spawn_egg", "fish_piranha_spawn_egg", "fish_arapaima_spawn_egg",
          "fish_electric_eel_spawn_egg", "fish_discus_spawn_egg", "fish_gar_spawn_egg", "fish_snakehead_spawn_egg",
          "fish_bowfin_spawn_egg", "fish_cavefish_spawn_egg", "fish_glowfish_spawn_egg", "fish_cod_spawn_egg", "fish_herring_spawn_egg",
          "fish_mackerel_spawn_egg", "fish_pollock_spawn_egg", "fish_halibut_spawn_egg", "fish_wolffish_spawn_egg",
          "fish_sea_bass_spawn_egg", "fish_sea_bream_spawn_egg", "fish_sardine_spawn_egg", "fish_anchovy_spawn_egg", "fish_sole_spawn_egg",
          "fish_turbot_spawn_egg", "fish_red_mullet_spawn_egg", "fish_conger_spawn_egg", "fish_monkfish_spawn_egg", "fish_tuna_spawn_egg",
          "fish_swordfish_spawn_egg", "fish_mahi_mahi_spawn_egg", "fish_barracuda_spawn_egg", "fish_grouper_spawn_egg",
          "fish_clownfish_spawn_egg", "fish_pufferfish_spawn_egg", "fish_lionfish_spawn_egg", "fish_tropical_fish_spawn_egg",
          "fish_anglerfish_spawn_egg", "fish_lanternfish_spawn_egg", "fish_oarfish_spawn_egg", "fish_coelacanth_spawn_egg"
    );
}
