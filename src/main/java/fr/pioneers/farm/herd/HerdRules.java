package fr.pioneers.farm.herd;

import java.util.Random;

/**
 * 1.0.4-beta (lot 9) : regles PURES des races de lapins et de chevaux (sans Minecraft : GameTests et bancs) : race au climat (3 fois plus
 * dans sa plage, comme les autres elevages), stats tirees dans la plage de la race, HEREDITE au croisement (race de l'un des deux parents,
 * 50 / 50 ; chaque stat = moyenne des parents +/- 6 %, bornee a la plage de la race du petit elargie de 10 % : la selection gagne un peu
 * d'une generation a l'autre, sans sortir de la race).
 */
public final class HerdRules {

    private HerdRules() {
    }

    /** Ecart d'une stat au croisement (+/- 6 %) et marge au-dela des bornes de la race (10 %). */
    public static final float MUTATION = 0.06F, MARGIN = 0.10F;

    /** Race au climat (temperature du biome) parmi les races d'un genre (lapins : tous ; chevaux : kind 0 seulement). */
    public static int climateBreed(float temperature, float[][] ranges, Random random) {
        int total = 0;
        int[] w = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            if (ranges[i] == null) {
                continue;
            }
            w[i] = temperature >= ranges[i][0] && temperature <= ranges[i][1] ? 3 : 1;
            total += w[i];
        }
        int pick = random.nextInt(Math.max(1, total)), i = 0;
        while (i < w.length - 1 && pick >= w[i]) {
            pick -= w[i];
            i++;
        }
        return i;
    }

    public static float[][] rabbitRanges() {
        GeneratedHerd.RabbitBreed[] b = GeneratedHerd.RABBITS;
        float[][] r = new float[b.length][];
        for (int i = 0; i < b.length; i++) {
            r[i] = new float[]{b[i].climMin(), b[i].climMax()};
        }
        return r;
    }

    /** Plages des CHEVAUX seulement (l'ane et le mulet ont leur race par leur type). */
    public static float[][] horseRanges() {
        GeneratedHerd.HorseBreed[] b = GeneratedHerd.HORSES;
        float[][] r = new float[b.length][];
        for (int i = 0; i < b.length; i++) {
            r[i] = b[i].kind() == 0 ? new float[]{b[i].climMin(), b[i].climMax()} : null;
        }
        return r;
    }

    /** Index de la race de l'ane (kind 1) ou du mulet (kind 2). */
    /** 1.0.4-beta (lot 28) : plages de climat des seules races d'une sorte (1 ane, 5 lama...) ; null pour les autres. */
    public static float[][] kindRanges(int kind) {
        GeneratedHerd.HorseBreed[] b = GeneratedHerd.HORSES;
        float[][] r = new float[b.length][];
        for (int i = 0; i < b.length; i++) {
            r[i] = b[i].kind() == kind ? new float[]{b[i].climMin(), b[i].climMax()} : null;
        }
        return r;
    }

    /** 1.0.4-beta (lot 28) : race d'un petit d'ane ou de lama : celle d'un parent de la sorte voulue, 50 / 50 ; sinon la premiere de la sorte. */
    public static int kindChild(int a, int b, int kind, Random random) {
        boolean okA = a >= 0 && a < GeneratedHerd.HORSES.length && GeneratedHerd.HORSES[a].kind() == kind;
        boolean okB = b >= 0 && b < GeneratedHerd.HORSES.length && GeneratedHerd.HORSES[b].kind() == kind;
        if (okA && okB) {
            return childBreed(a, b, random);
        }
        return okA ? a : okB ? b : kindBreed(kind);
    }

    public static int kindBreed(int kind) {
        GeneratedHerd.HorseBreed[] b = GeneratedHerd.HORSES;
        for (int i = 0; i < b.length; i++) {
            if (b[i].kind() == kind) {
                return i;
            }
        }
        return 0;
    }

    /** Lapin ne au monde : stats de la race +/- 10 %. */
    public static void rollRabbit(HerdData d, int breed, Random random) {
        GeneratedHerd.RabbitBreed b = GeneratedHerd.RABBITS[breed];
        d.kind = HerdData.RABBIT;
        d.breed = breed;
        d.growth = vary(b.growth(), random, 0.10F);
        d.litter = vary(b.litter(), random, 0.10F);
        d.meat = vary(b.meat(), random, 0.10F);
        d.pelt = vary(b.pelt(), random, 0.10F);
        d.wool = b.wool() > 0 ? vary(b.wool(), random, 0.10F) : 0;
        d.woolTimer = 0;
        d.init = true;
    }

    /** Cheval ne au monde : trait et bat tires dans la plage de la race (les attributs vanilla : {@link #rollRange}). */
    public static void rollHorse(HerdData d, int breed, Random random) {
        GeneratedHerd.HorseBreed b = GeneratedHerd.HORSES[breed];
        d.kind = HerdData.HORSE;
        d.breed = breed;
        d.draft = Math.round(rollRange(b.draftMin(), b.draftMax(), random));
        d.pack = Math.round(rollRange(b.packMin(), b.packMax(), random));
        d.init = true;
    }

    public static float rollRange(float min, float max, Random random) {
        return min + (max - min) * random.nextFloat();
    }

    /** Race du petit : celle de l'un des deux parents (50 / 50). */
    public static int childBreed(int a, int b, Random random) {
        return a == b || random.nextBoolean() ? a : b;
    }

    /** Stat heritee : moyenne des parents +/- {@link #MUTATION}, bornee a [min, max] de la race elargie de {@link #MARGIN}. */
    public static float inherit(float a, float b, float min, float max, Random random) {
        float v = (a + b) / 2F * (1 + (random.nextFloat() * 2 - 1) * MUTATION);
        float lo = min * (1 - MARGIN), hi = max * (1 + MARGIN);
        return Math.max(lo, Math.min(hi, v));
    }

    /** Lapereau : race 50 / 50, stats heritees (bornes de la race du petit : sa valeur +/- 25 %). */
    public static HerdData rabbitChild(HerdData a, HerdData b, Random random) {
        HerdData c = new HerdData();
        c.kind = HerdData.RABBIT;
        c.breed = childBreed(a.breed, b.breed, random);
        GeneratedHerd.RabbitBreed r = GeneratedHerd.RABBITS[c.breed];
        c.growth = inherit(a.growth, b.growth, r.growth() * 0.75F, r.growth() * 1.25F, random);
        c.litter = inherit(a.litter, b.litter, r.litter() * 0.75F, r.litter() * 1.25F, random);
        c.meat = inherit(a.meat, b.meat, r.meat() * 0.75F, r.meat() * 1.25F, random);
        c.pelt = inherit(a.pelt, b.pelt, r.pelt() * 0.75F, r.pelt() * 1.25F, random);
        c.wool = r.wool() > 0 ? inherit(Math.max(a.wool, r.wool() * 0.5F), Math.max(b.wool, r.wool() * 0.5F), r.wool() * 0.75F, r.wool() * 1.25F, random) : 0;
        c.init = true;
        return c;
    }

    /** Poulain : race 50 / 50 (deux chevaux), trait et bat herites. */
    public static HerdData horseChild(HerdData a, HerdData b, int breed, Random random) {
        HerdData c = new HerdData();
        c.kind = HerdData.HORSE;
        c.breed = breed;
        GeneratedHerd.HorseBreed h = GeneratedHerd.HORSES[breed];
        c.draft = Math.round(inherit(a.draft, b.draft, h.draftMin(), h.draftMax(), random));
        c.pack = Math.round(inherit(a.pack, b.pack, h.packMin(), h.packMax(), random));
        c.init = true;
        return c;
    }

    static float vary(float v, Random random, float k) {
        return v * (1 + (random.nextFloat() * 2 - 1) * k);
    }

    /** Hauteur de saut (blocs) d'un jump_strength (formule du wiki, cheval vanilla). */
    public static double jumpHeight(double j) {
        return -0.1817584952 * j * j * j + 3.689713992 * j * j + 2.128599134 * j - 0.343930367;
    }
}
