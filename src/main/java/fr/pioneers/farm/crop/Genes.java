package fr.pioneers.farm.crop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Genetique d'une graine ou d'une plante (1 a 10 chacun) : Rendement, Croissance, Resistance, Fertilite. Le joueur ne croise rien : a chaque
 * recolte, chaque graine rendue herite des genes de la plante et chaque gene peut muter d'un point (la Fertilite rend la mutation plus
 * frequente et plus souvent favorable). NBT : entier {@code Genes} (4 bits par gene) : deux graines de memes genes s'empilent.
 */
public record Genes(int yield, int growth, int resistance, int fertility) {

    public static final String TAG = "Genes";
    public static final int MIN = 1, MAX = 10;
    /** Graine sans pedigree (creatif, recettes, anciennes graines). */
    public static final Genes BASE = new Genes(1, 1, 1, 1);

    public Genes {
        yield = Mth.clamp(yield, MIN, MAX);
        growth = Mth.clamp(growth, MIN, MAX);
        resistance = Mth.clamp(resistance, MIN, MAX);
        fertility = Mth.clamp(fertility, MIN, MAX);
    }

    public int packed() {
        return yield | growth << 4 | resistance << 8 | fertility << 12;
    }

    public static Genes unpack(int v) {
        if (v <= 0) {
            return BASE;
        }
        return new Genes(v & 15, v >> 4 & 15, v >> 8 & 15, v >> 12 & 15);
    }

    public static Genes of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG) ? unpack(tag.getInt(TAG)) : BASE;
    }

    public static boolean has(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG);
    }

    /** Pose les genes sur la pile (rien pour les genes de base : la pile reste empilable avec les graines sans NBT). */
    public ItemStack apply(ItemStack stack) {
        if (equals(BASE)) {
            if (stack.hasTag()) {
                stack.getTag().remove(TAG);
                if (stack.getTag().isEmpty()) {
                    stack.setTag(null);
                }
            }
        } else {
            stack.getOrCreateTag().putInt(TAG, packed());
        }
        return stack;
    }

    public int get(int i) {
        return switch (i) {
            case 0 -> yield;
            case 1 -> growth;
            case 2 -> resistance;
            default -> fertility;
        };
    }

    public int score() {
        return yield + growth + resistance + fertility;
    }

    /** Note de 1 a 5 etoiles. */
    public int stars() {
        int s = score();
        return s <= 10 ? 1 : s <= 17 ? 2 : s <= 25 ? 3 : s <= 33 ? 4 : 5;
    }

    /** Graine sauvage (herbes) : 1 a 3. */
    public static Genes wild(RandomSource random) {
        return new Genes(1 + random.nextInt(3), 1 + random.nextInt(3), 1 + random.nextInt(3), 1 + random.nextInt(3));
    }

    /** Graine fille : chaque gene mute d'un point avec une probabilite 0,15 + 0,03 F, vers le haut avec 0,35 + 0,04 F. */
    public Genes child(RandomSource random) {
        return child(random, 0, 0);
    }

    /** Graine fille d'une plante soignee : {@code mutate} et {@code up} s'ajoutent aux probabilites (engrais, pollinisation). */
    public Genes child(RandomSource random, float mutate, float up) {
        float pMutate = 0.15F + 0.03F * fertility + mutate, pUp = Math.min(0.95F, 0.35F + 0.04F * fertility + up);
        int[] g = {yield, growth, resistance, fertility};
        for (int i = 0; i < 4; i++) {
            if (random.nextFloat() < pMutate) {
                g[i] += random.nextFloat() < pUp ? 1 : -1;
            }
        }
        return new Genes(g[0], g[1], g[2], g[3]);
    }

    /** Facteur de recolte : 0,72 (R 1) a 1,8 (R 10). */
    public float yieldFactor() {
        return 0.6F + 0.12F * yield;
    }

    /** Facteur de vitesse : 0,69 (C 1) a 1,5 (C 10). */
    public float growthFactor() {
        return 0.6F + 0.09F * growth;
    }

    /** Marge climatique tolere hors de la plage ideale (temperature et humidite). */
    public float tolerance() {
        return 0.05F + 0.04F * resistance;
    }

    /** Nombre de graines rendues par une recolte (1 a 3). */
    public int seedCount(RandomSource random) {
        int n = 1;
        if (random.nextFloat() < 0.25F + 0.05F * fertility) {
            n++;
        }
        if (random.nextFloat() < 0.1F * (fertility - 3)) {
            n++;
        }
        return n;
    }
}
