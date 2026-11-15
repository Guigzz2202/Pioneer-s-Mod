package fr.pioneers.farm.aquarium;

/**
 * 1.0.5-beta (lot 25, Guillaume : « l'aquarium devrait ne pas avoir de deco par defaut, le joueur est libre de composer son aquarium :
 * clic droit en etant accroupi ouvre l'interface ou le joueur peut (en fonction de la taille d'aquarium) customiser le sien : sol (dirt,
 * gravier, sable, sable noir autre...), vegetation (algues, coraux, herbes marines), decoration (coquillage, roches etc...) ») : la
 * COMPOSITION du fond, regles PURES (aucune classe de Minecraft ; controle {@code tools/check_p105_25.py}, banc
 * {@code tools/aquarium/swim_bench.sh}).
 * <ul>
 *     <li>chaque case du FOND d'un bassin (aquarium sans aquarium dessous) offre {@link #SLOTS} emplacements de 8 x 8 px (2 x 2) : le nombre
 *     d'emplacements suit la taille du bassin ;</li>
 *     <li>un emplacement a un SOL ({@link Group#SOL}, couche de {@link #GROUND_H} px) et un ELEMENT pose dessus (vegetation ou decoration) ;
 *     une plante demande un sol (elle s'y enracine), sauf la lentille d'eau / nenuphar qui flotte a la surface ;</li>
 *     <li>chaque sorte coute UN objet reel (celui de {@link Kind#item}), rendu quand on la retire, la remplace ou casse la case ;</li>
 *     <li>hauteurs : fixes, sauf le varech (55 % de la colonne d'eau) ; toujours au moins {@link #ROOM_MIN} px d'eau libre au-dessus (les
 *     poissons passent au-dessus du decor, {@code AquariumSwim.lift}).</li>
 * </ul>
 * Les CLES des sortes ({@link Kind#key}) sont sauvees dans le NBT : ne jamais les renommer (ordre libre).
 */
public final class AquariumDecor {

    private AquariumDecor() {
    }

    public enum Group {
        SOL, VEGETATION, DECORATION
    }

    /**
     * Sorte d'element : cle (NBT, langue {@code aquarium.pioneers.decor.<cle>}), groupe, objet consomme, hauteur (px au-dessus du sol ; -1 =
     * varech, selon la colonne), rayon d'emprise (px), texture principale (atlas des blocs).
     */
    public record Kind(String key, Group group, String item, float height, float radius, String texture) {

        public boolean ground() {
            return group == Group.SOL;
        }

        /** Flotte a la surface (lentille d'eau / nenuphar) : aucun encombrement au fond. */
        public boolean floating() {
            return "lily_pad".equals(key);
        }

        /** Plante enracinee : demande un sol. */
        public boolean rooted() {
            return group == Group.VEGETATION && !floating();
        }
    }

    /** Emplacements par case du fond (2 x 2), hauteur du sol (px), eau libre minimale au-dessus d'un element (px). */
    public static final int SLOTS = 4;
    public static final float GROUND_H = 2.0F, ROOM_MIN = 6.0F, KELP_PART = 0.55F;

    public static final Kind[] KINDS = {
        // ---- sols
        new Kind("sand", Group.SOL, "minecraft:sand", 0, 0, "minecraft:block/sand"),
        new Kind("red_sand", Group.SOL, "minecraft:red_sand", 0, 0, "minecraft:block/red_sand"),
        new Kind("black_sand", Group.SOL, "pioneers:black_sand", 0, 0, "pioneers:block/mechanics/black_sand"),
        new Kind("gravel", Group.SOL, "minecraft:gravel", 0, 0, "minecraft:block/gravel"),
        new Kind("dirt", Group.SOL, "minecraft:dirt", 0, 0, "minecraft:block/dirt"),
        new Kind("clay", Group.SOL, "minecraft:clay", 0, 0, "minecraft:block/clay"),
        new Kind("pebbles", Group.SOL, "minecraft:cobblestone", 0, 0, "minecraft:block/cobblestone"),
        // ---- vegetation
        new Kind("seagrass", Group.VEGETATION, "minecraft:seagrass", 6.5F, 3.1F, "minecraft:block/seagrass"),
        new Kind("kelp", Group.VEGETATION, "minecraft:kelp", -1F, 3.1F, "minecraft:block/kelp_plant"),
        new Kind("tube_coral", Group.VEGETATION, "minecraft:tube_coral", 6F, 3.3F, "minecraft:block/tube_coral"),
        new Kind("brain_coral", Group.VEGETATION, "minecraft:brain_coral", 5F, 3.3F, "minecraft:block/brain_coral"),
        new Kind("bubble_coral", Group.VEGETATION, "minecraft:bubble_coral", 6F, 3.3F, "minecraft:block/bubble_coral"),
        new Kind("fire_coral", Group.VEGETATION, "minecraft:fire_coral", 6F, 3.3F, "minecraft:block/fire_coral"),
        new Kind("horn_coral", Group.VEGETATION, "minecraft:horn_coral", 6F, 3.3F, "minecraft:block/horn_coral"),
        new Kind("sea_pickle", Group.VEGETATION, "minecraft:sea_pickle", 3F, 2.4F, "minecraft:block/sea_pickle"),
        new Kind("lily_pad", Group.VEGETATION, "minecraft:lily_pad", 0, 0, "minecraft:block/lily_pad"),
        // ---- decoration
        new Kind("seashell", Group.DECORATION, "pioneers:seashell", 1.2F, 3.3F, "pioneers:item/farm/crustacean/seashell"),
        new Kind("rock", Group.DECORATION, "minecraft:stone", 4.3F, 3.3F, "minecraft:block/stone"),
        new Kind("mossy_rock", Group.DECORATION, "minecraft:mossy_cobblestone", 4.3F, 3.3F, "minecraft:block/mossy_cobblestone"),
        new Kind("driftwood", Group.DECORATION, "minecraft:stick", 2.6F, 3.3F, "minecraft:block/stripped_birch_log"),
        new Kind("amphora", Group.DECORATION, "minecraft:decorated_pot", 5F, 3.3F, "minecraft:block/terracotta"),
        new Kind("chest", Group.DECORATION, "minecraft:chest", 5.8F, 3.3F, "minecraft:block/oak_planks"),
    };

    /** Sorte d'une cle (null si inconnue : element d'une version future, ignore). */
    public static Kind of(String key) {
        for (Kind k : KINDS) {
            if (k.key().equals(key)) {
                return k;
            }
        }
        return null;
    }

    public static int index(Kind k) {
        for (int i = 0; i < KINDS.length; i++) {
            if (KINDS[i] == k) {
                return i;
            }
        }
        return -1;
    }

    public static Kind byIndex(int i) {
        return i >= 0 && i < KINDS.length ? KINDS[i] : null;
    }

    /** Centre de l'emplacement {@code slot} (0..3 : nord-ouest, nord-est, sud-ouest, sud-est) dans sa case, px. */
    public static float slotX(int slot) {
        return (slot & 1) == 0 ? 4F : 12F;
    }

    public static float slotZ(int slot) {
        return (slot & 2) == 0 ? 4F : 12F;
    }

    /** Emplacement d'une case qui contient le point (x, z) en px de la case (0..16). */
    public static int slotAt(float x, float z) {
        return (x >= 8 ? 1 : 0) + (z >= 8 ? 2 : 0);
    }

    /**
     * Hauteur d'un element (px au-dessus du sol) dans une colonne d'eau de {@code water} px (du dessus du sol a la surface) : le varech prend
     * {@link #KELP_PART} de la colonne ; tout element laisse au moins {@link #ROOM_MIN} px d'eau libre.
     */
    public static float height(Kind k, float water) {
        if (k == null || k.ground() || k.floating()) {
            return 0;
        }
        float h = k.height() < 0 ? Math.max(5F, water * KELP_PART) : k.height();
        return Math.max(0.5F, Math.min(h, water - ROOM_MIN));
    }

    /** Refus d'une pose (cle de langue {@code aquarium.pioneers.decor.refuse.<raison>}), null si la pose est possible. */
    public static String refusal(Kind kind, Kind ground) {
        if (kind == null) {
            return "unknown";
        }
        if (kind.rooted() && ground == null) {
            return "no_ground";
        }
        return null;
    }

    /**
     * Composition equivalente au decor TIRE AU HASARD des aquariums d'avant le lot 25 (lot 14 : sable partout, plaques de gravier, deux ou
     * trois cailloux, une touffe d'herbier ou de varech contre chaque vitre ; meme graine) : sols {@code ground[4]}, elements {@code piece[4]}
     * (cles, null = vide). {@code glass} : nord, sud, ouest, est sans aquarium voisin.
     */
    public static String[][] legacy(int seed, boolean[] glass) {
        String[] ground = {"sand", "sand", "sand", "sand"}, piece = new String[SLOTS];
        //les plaques de gravier : la plus grande donne son emplacement au gravier
        int gravel = (int) (rnd(seed, 40) * 4) & 3;
        ground[gravel] = "gravel";
        //les cailloux (moitie moussus) : le premier dans son emplacement
        int rock = slotAt(4F + 8F * rnd(seed, 71), 4F + 8F * rnd(seed, 81));
        piece[rock] = "rock";
        //plantes contre les vitres : emplacement du milieu du cote, varech 40 %
        int[][] sideSlots = {{0, 1}, {2, 3}, {0, 2}, {1, 3}};
        for (int i = 0; i < 4; i++) {
            if (glass != null && glass[i]) {
                int s = sideSlots[i][rnd(seed, 100 + i) < 0.5F ? 0 : 1];
                if (piece[s] == null) {
                    piece[s] = rnd(seed, 110 + i) < 0.4F ? "kelp" : "seagrass";
                }
            }
        }
        if (piece[(rock + 3) & 3] == null && rnd(seed, 70) < 0.5F) {
            piece[(rock + 3) & 3] = "mossy_rock";
        }
        return new String[][]{ground, piece};
    }

    /** Graine -> nombre dans [0, 1) (meme fonction que {@code AquariumSwim.rnd}, pour la conversion des anciens decors). */
    public static float rnd(int seed, int k) {
        int h = seed * 0x9E3779B1 + k * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
