package fr.pioneers.farm.crustacean;

import fr.pioneers.farm.fish.FishSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 1.4 : tirage d'une espece de crustace selon le milieu (habitats des poissons, {@link FishSpawns#habitat}) et la sorte (aquatique ou de
 * rivage), ponderee par la rarete ; ce que donne un crustace (chair crue, ou coquillage pour le bernard-l'ermite).
 */
public final class CrustaceanSpawns {

    private CrustaceanSpawns() {
    }

    /** Espece pour ce point (-1 si aucune). */
    public static int pick(Level level, BlockPos pos, RandomSource random, boolean aquatic) {
        return pickIn(FishSpawns.habitat(level, pos), random, aquatic);
    }

    public static int pickIn(int habitat, RandomSource random, boolean aquatic) {
        CrustaceanDef[] all = GeneratedCrustaceans.SPECIES;
        int total = 0;
        for (CrustaceanDef d : all) {
            if (d.aquatic() == aquatic && d.in(habitat)) {
                total += d.rarity();
            }
        }
        if (total <= 0) {
            return -1;
        }
        int r = random.nextInt(total);
        for (int i = 0; i < all.length; i++) {
            if (all[i].aquatic() == aquatic && all[i].in(habitat)) {
                r -= all[i].rarity();
                if (r < 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Espece quelconque de la sorte (oeuf d'apparition hors de son milieu). */
    public static int any(RandomSource random, boolean aquatic) {
        CrustaceanDef[] all = GeneratedCrustaceans.SPECIES;
        int n = 0;
        for (CrustaceanDef d : all) {
            if (d.aquatic() == aquatic) {
                n++;
            }
        }
        int k = random.nextInt(Math.max(1, n));
        for (int i = 0; i < all.length; i++) {
            if (all[i].aquatic() == aquatic && k-- == 0) {
                return i;
            }
        }
        return 0;
    }

    /** Ce que laisse un crustace (ou ce qu'attrape un casier) : chair crue (1, ou 2 pour un gros), coquillage pour le bernard-l'ermite. */
    public static ItemStack catchOf(int species, float individual, RandomSource random) {
        CrustaceanDef d = GeneratedCrustaceans.SPECIES[Math.max(0, Math.min(GeneratedCrustaceans.SPECIES.length - 1, species))];
        if (d.meat().isEmpty()) {
            return new ItemStack(PiCrustaceans.SEASHELL.get());
        }
        var item = PiCrustaceans.RAW.get(d.id());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int n = 1 + (individual > 1.05F && random.nextFloat() < 0.5F ? 1 : 0);
        return new ItemStack(item.get(), n);
    }
}
