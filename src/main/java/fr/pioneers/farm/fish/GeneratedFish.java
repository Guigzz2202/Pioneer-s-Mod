package fr.pioneers.farm.fish;

/** GENERE par tools/gc/farm2/gen_java.py depuis fish.py : especes de poissons. */
public final class GeneratedFish {

    private GeneratedFish() {
    }

    /** Habitats (bits de FishDef.habitats), silhouettes, comportements. */
    public static final String[] HABITATS = {"fresh_cold", "fresh_temperate", "fresh_warm", "swamp", "cave", "sea_cold", "sea_temperate", "sea_warm", "deep"};
    public static final String[] SHAPES = {"fusiform", "tall", "flat", "eel", "big", "round", "sturgeon", "billfish", "tuna", "catfish", "shark"};
    public static final String[] BEHAVIOURS = {"calm", "rush", "jump", "erratic", "runner"};

    public static final FishDef[] SPECIES = {
        new FishDef("brook_trout", "pioneers:brook_trout", 0, 0.4589F, 0.27F, 0.20F, 4, 2, 100, 3, "", 2, 20, 40, 0x606E46, 0xC83C32, 0.49F),
        new FishDef("arctic_char", "pioneers:arctic_char", 0, 0.4468F, 0.34F, 0.18F, 4, 4, 70, 1, "", 2, 30, 60, 0x465A60, 0xFADCC8, 0.61F),
        new FishDef("grayling", "pioneers:grayling", 0, 0.4992F, 0.29F, 0.25F, 3, 3, 70, 1, "", 2, 25, 45, 0x78787E, 0x963C78, 0.53F),
        new FishDef("pike", "pioneers:pike", 0, 0.6512F, 0.49F, 0.31F, 6, 1, 60, 3, "", 2, 50, 110, 0x5A6E3C, 0xC8C878, 0.90F),
        new FishDef("salmon", "minecraft:salmon", 0, 0.5956F, 0.47F, 0.27F, 6, 2, 80, 33, "", 2, 50, 100, 0x6E7882, 0x3C4650, 0.86F),
        new FishDef("sturgeon", "pioneers:sturgeon", 6, 1.1887F, 1.02F, 0.42F, 9, 4, 12, 3, "roe", 3, 120, 300, 0x5A5650, 0x3C3834, 1.86F),
        new FishDef("burbot", "pioneers:burbot", 3, 0.4680F, 0.34F, 0.18F, 3, 0, 50, 1, "", 2, 30, 60, 0x6E603C, 0x463C28, 0.61F),
        new FishDef("carp", "pioneers:carp", 1, 0.7311F, 0.43F, 0.37F, 6, 4, 100, 6, "", 2, 40, 90, 0xAA823C, 0x825A28, 0.78F),
        new FishDef("perch", "pioneers:perch", 1, 0.4180F, 0.25F, 0.21F, 2, 3, 120, 2, "", 2, 15, 35, 0x828C3C, 0xDC5A28, 0.44F),
        new FishDef("roach", "pioneers:roach", 1, 0.4119F, 0.25F, 0.23F, 1, 0, 140, 2, "", 2, 12, 25, 0xAAB4BA, 0xD23C32, 0.39F),
        new FishDef("bream", "pioneers:bream", 1, 0.5726F, 0.31F, 0.39F, 3, 0, 100, 2, "", 2, 30, 50, 0x968C6E, 0x5A5046, 0.57F),
        new FishDef("zander", "pioneers:zander", 0, 0.5894F, 0.41F, 0.27F, 5, 1, 60, 2, "", 2, 40, 80, 0x787E6E, 0x464C3C, 0.74F),
        new FishDef("catfish", "pioneers:catfish", 9, 1.1684F, 0.88F, 0.44F, 9, 4, 15, 6, "", 3, 100, 250, 0x3C4238, 0x282C26, 1.61F),
        new FishDef("tench", "pioneers:tench", 0, 0.4792F, 0.28F, 0.20F, 3, 0, 90, 10, "", 2, 20, 45, 0x5A6E32, 0x3C461E, 0.51F),
        new FishDef("eel", "pioneers:eel", 3, 0.5179F, 0.43F, 0.18F, 4, 3, 80, 10, "", 2, 40, 90, 0x464228, 0x28281A, 0.78F),
        new FishDef("barbel", "pioneers:barbel", 0, 0.5520F, 0.36F, 0.25F, 5, 4, 70, 2, "", 2, 30, 70, 0x968250, 0x6E5A32, 0.66F),
        new FishDef("tilapia", "pioneers:tilapia", 1, 0.4876F, 0.27F, 0.26F, 3, 0, 120, 4, "", 2, 20, 40, 0x8C9696, 0xC85046, 0.49F),
        new FishDef("piranha", "pioneers:piranha", 1, 0.4092F, 0.25F, 0.25F, 3, 3, 90, 4, "venom", 2, 15, 30, 0x828C96, 0x3C3C46, 0.42F),
        new FishDef("arapaima", "pioneers:arapaima", 4, 1.3090F, 1.08F, 0.72F, 10, 2, 8, 4, "legendary", 3, 150, 300, 0x5A6450, 0xC83C32, 1.96F),
        new FishDef("electric_eel", "pioneers:electric_eel", 3, 0.9116F, 0.78F, 0.25F, 6, 3, 25, 12, "electric", 2, 100, 200, 0x3C3C32, 0xF0E678, 1.42F),
        new FishDef("discus", "pioneers:discus", 1, 0.4472F, 0.25F, 0.29F, 2, 3, 60, 4, "", 2, 12, 20, 0x3C8CA0, 0xF0C85A, 0.36F),
        new FishDef("gar", "pioneers:gar", 0, 0.7570F, 0.60F, 0.30F, 6, 1, 50, 12, "", 2, 60, 150, 0x6E6E46, 0x463C28, 1.09F),
        new FishDef("snakehead", "pioneers:snakehead", 3, 0.5650F, 0.43F, 0.18F, 6, 1, 50, 8, "", 2, 40, 90, 0x5A503C, 0x28241E, 0.78F),
        new FishDef("bowfin", "pioneers:bowfin", 0, 0.6549F, 0.41F, 0.28F, 5, 4, 60, 8, "", 2, 40, 80, 0x646E46, 0x28321E, 0.74F),
        new FishDef("cavefish", "pioneers:cavefish", 0, 0.3779F, 0.25F, 0.18F, 1, 0, 100, 16, "nightvision", 2, 8, 12, 0xECD6D2, 0xF0AAAA, 0.31F),
        new FishDef("glowfish", "pioneers:glowfish", 5, 0.5016F, 0.25F, 0.26F, 2, 3, 40, 16, "nightvision", 2, 10, 18, 0x3CC8D2, 0xFAFAA0, 0.34F),
        new FishDef("cod", "minecraft:cod", 0, 0.7686F, 0.45F, 0.40F, 4, 0, 120, 96, "", 2, 40, 100, 0x96825A, 0x6E5A3C, 0.82F),
        new FishDef("herring", "pioneers:herring", 0, 0.4660F, 0.26F, 0.18F, 1, 0, 160, 32, "", 2, 20, 35, 0x506E8C, 0xAABED2, 0.47F),
        new FishDef("mackerel", "pioneers:mackerel", 0, 0.4715F, 0.29F, 0.18F, 3, 4, 140, 96, "", 2, 25, 45, 0x286E6E, 0x14323C, 0.53F),
        new FishDef("pollock", "pioneers:pollock", 0, 0.6374F, 0.43F, 0.30F, 4, 4, 100, 32, "", 2, 40, 90, 0x3C4646, 0x282C2C, 0.78F),
        new FishDef("halibut", "pioneers:halibut", 2, 1.2240F, 0.88F, 0.18F, 8, 0, 20, 288, "", 3, 100, 250, 0x5A5646, 0x3C382E, 1.61F),
        new FishDef("wolffish", "pioneers:wolffish", 3, 0.7077F, 0.54F, 0.25F, 6, 1, 40, 288, "", 2, 60, 120, 0x646A6E, 0x3C4046, 0.97F),
        new FishDef("sea_bass", "pioneers:sea_bass", 0, 0.6369F, 0.39F, 0.32F, 5, 1, 100, 64, "", 2, 35, 80, 0x8C96A0, 0x505A64, 0.72F),
        new FishDef("sea_bream", "pioneers:sea_bream", 1, 0.5516F, 0.30F, 0.29F, 4, 3, 100, 192, "", 2, 25, 50, 0xAAB0B4, 0xF0C83C, 0.55F),
        new FishDef("sardine", "pioneers:sardine", 0, 0.4845F, 0.25F, 0.18F, 1, 0, 180, 192, "", 2, 12, 20, 0x466E8C, 0x3C465A, 0.36F),
        new FishDef("anchovy", "pioneers:anchovy", 0, 0.4598F, 0.25F, 0.18F, 1, 0, 180, 192, "", 2, 10, 18, 0x507882, 0xAAB4BE, 0.34F),
        new FishDef("sole", "pioneers:sole", 2, 0.6528F, 0.29F, 0.18F, 2, 0, 90, 64, "", 2, 25, 45, 0x826E50, 0x5A4C38, 0.53F),
        new FishDef("turbot", "pioneers:turbot", 2, 0.9067F, 0.41F, 0.18F, 4, 0, 60, 96, "", 2, 40, 80, 0x6E6450, 0x464032, 0.74F),
        new FishDef("red_mullet", "pioneers:red_mullet", 0, 0.4501F, 0.25F, 0.19F, 2, 3, 110, 192, "", 2, 15, 30, 0xD25A46, 0xF0C850, 0.42F),
        new FishDef("conger", "pioneers:conger", 3, 1.0282F, 0.88F, 0.31F, 7, 1, 40, 96, "", 2, 100, 250, 0x5A6064, 0x28282C, 1.61F),
        new FishDef("monkfish", "pioneers:monkfish", 5, 0.9882F, 0.58F, 0.40F, 6, 0, 40, 320, "nightvision", 2, 50, 150, 0x6E5A46, 0x463828, 1.05F),
        new FishDef("tuna", "pioneers:tuna", 8, 1.5708F, 1.08F, 0.86F, 9, 4, 25, 192, "", 3, 150, 300, 0x283C6E, 0xF0DC50, 1.96F),
        new FishDef("swordfish", "pioneers:swordfish", 7, 1.4724F, 1.37F, 0.92F, 10, 2, 10, 128, "strength", 3, 200, 400, 0x3C4664, 0x282E46, 2.48F),
        new FishDef("mahi_mahi", "pioneers:mahi_mahi", 0, 0.9802F, 0.64F, 0.54F, 7, 2, 50, 128, "", 2, 80, 150, 0x3CAA78, 0x2878C8, 1.16F),
        new FishDef("barracuda", "pioneers:barracuda", 0, 0.8181F, 0.66F, 0.37F, 7, 1, 50, 128, "", 2, 80, 160, 0x828C96, 0x28323C, 1.20F),
        new FishDef("grouper", "pioneers:grouper", 4, 1.0346F, 0.64F, 0.54F, 8, 0, 30, 192, "", 3, 80, 150, 0x6E5A46, 0x46382C, 1.16F),
        new FishDef("clownfish", "pioneers:clownfish", 5, 0.4093F, 0.25F, 0.18F, 1, 3, 100, 128, "", 2, 8, 12, 0xF0781E, 0xFAFAFA, 0.31F),
        new FishDef("pufferfish", "minecraft:pufferfish", 5, 0.6778F, 0.26F, 0.30F, 2, 0, 70, 128, "venom", 2, 15, 40, 0xDCBE50, 0x645028, 0.47F),
        new FishDef("lionfish", "pioneers:lionfish", 1, 0.5431F, 0.28F, 0.37F, 3, 3, 40, 128, "venom", 2, 25, 40, 0xC8503C, 0xFAFAFA, 0.51F),
        new FishDef("tropical_fish", "minecraft:tropical_fish", 1, 0.4285F, 0.25F, 0.36F, 1, 3, 120, 128, "", 2, 8, 15, 0x3CA0E6, 0xFAFAFA, 0.32F),
        new FishDef("anglerfish", "pioneers:anglerfish", 5, 0.5957F, 0.34F, 0.33F, 5, 3, 30, 256, "nightvision", 2, 30, 60, 0x322832, 0xB4FAE6, 0.61F),
        new FishDef("lanternfish", "pioneers:lanternfish", 0, 0.3509F, 0.25F, 0.18F, 1, 0, 120, 256, "nightvision", 2, 5, 15, 0x283246, 0x78DCFA, 0.31F),
        new FishDef("oarfish", "pioneers:oarfish", 3, 1.6000F, 1.40F, 1.12F, 10, 4, 4, 256, "legendary", 3, 300, 800, 0xC8D2DC, 0xDC323C, 3.00F),
        new FishDef("coelacanth", "pioneers:coelacanth", 4, 1.2852F, 0.88F, 0.71F, 9, 0, 4, 256, "legendary", 3, 150, 200, 0x32466E, 0xE6E6F0, 1.61F),
        new FishDef("gray_reef_shark", "pioneers:gray_reef_shark", 10, 1.3037F, 1.21F, 0.78F, 9, 4, 10, 128, "", 3, 150, 190, 0x707880, 0x24262A, 2.20F)
    };

    /** Effets a la consommation par propriete : propriete, effet, secondes, niveau. */
    public static final String[][] EFFECTS = {
        {"venom", "minecraft:poison", "12", "1"},
        {"venom", "minecraft:nausea", "10", "0"},
        {"nightvision", "minecraft:night_vision", "120", "0"},
        {"strength", "minecraft:strength", "60", "0"},
        {"legendary", "minecraft:regeneration", "20", "1"},
        {"legendary", "minecraft:absorption", "120", "1"},
        {"legendary", "minecraft:luck", "300", "0"},
        {"electric", "minecraft:haste", "60", "0"}
    };
}
