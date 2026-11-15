package fr.pioneers.farm.animal;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Donnees d'un animal d'elevage (capacite attachee aux vaches, moutons, cochons, poules, chevres) : race, sexe, domestique (ne a la ferme,
 * nourri a la main, mene en laisse, a mange a une mangeoire), faim et soif (0..100, seulement pour les domestiques), minuteries de production
 * (lait, toison), temps passe affame. Synchronise vers les clients ({@link PacketAnimal}) : robe, taille et fiche au regard.
 */
public final class AnimalData implements INBTSerializable<CompoundTag> {

    public boolean init;
    public int breed;
    public boolean male;
    public boolean domestic;
    public float hunger = 100, thirst = 100;
    /** Secondes restantes avant le prochain lait / la repousse de la toison (chevre angora) ; secondes passees a vide. */
    public float milkTimer, woolTimer;
    public int starving;
    /** Poules (1.3.1, ponte geree par le mod) : secondes avant l'envie de pondre ; en quete d'un pondoir depuis {@link #layWait} s. */
    public float layTimer;
    public boolean laying;
    public int layWait;
    /** Cote client : avancement de la production (0..1) recu du serveur. */
    public float produce;
    // Derniere synchro (serveur)
    int sentHunger = -1, sentThirst = -1, sentProduce = -1, sentFlags = -1, sinceSync;

    /**
     * 1.0.2-beta (Guillaume : « les animaux ne doivent plus mourir de faim ni de soif ; jauge basse = tres peu de produits, le systeme etait trop
     * punitif ») : part de la production (lait, oeufs, toison, viande) selon la plus basse des deux jauges : pleine de 50 a 100, puis
     * PROPORTIONNELLE jusqu'au plancher {@link #MIN_PRODUCTION} a jauge vide (avant : 0, l'animal ne produisait plus rien puis mourait).
     */
    public float production() {
        if (!domestic) {
            return 0.5F;
        }
        return production(Math.min(hunger, thirst));
    }

    /** Production minimale d'un animal domestique aux jauges vides (part de la normale). */
    public static final float MIN_PRODUCTION = 0.1F;

    /** Part de la production pour une jauge (0..100) : {@link #MIN_PRODUCTION} a 0, lineaire, 1 des 50. */
    public static float production(float need) {
        float f = Math.max(0, Math.min(1, need / 50F));
        return MIN_PRODUCTION + (1 - MIN_PRODUCTION) * f;
    }

    public boolean weak() {
        return domestic && Math.min(hunger, thirst) < 30;
    }

    public int flags() {
        return (male ? 1 : 0) | (domestic ? 2 : 0) | (starving > 0 ? 4 : 0) | (init ? 8 : 0);
    }

    public void flags(int f) {
        male = (f & 1) != 0;
        domestic = (f & 2) != 0;
        starving = (f & 4) != 0 ? 1 : 0;
        init = (f & 8) != 0;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("init", init);
        t.putByte("breed", (byte) breed);
        t.putBoolean("male", male);
        t.putBoolean("domestic", domestic);
        t.putFloat("hunger", hunger);
        t.putFloat("thirst", thirst);
        t.putFloat("milk", milkTimer);
        t.putFloat("wool", woolTimer);
        t.putInt("starving", starving);
        t.putFloat("lay", layTimer);
        t.putBoolean("laying", laying);
        return t;
    }

    @Override
    public void deserializeNBT(CompoundTag t) {
        init = t.getBoolean("init");
        breed = t.getByte("breed");
        male = t.getBoolean("male");
        domestic = t.getBoolean("domestic");
        hunger = t.contains("hunger") ? t.getFloat("hunger") : 100;
        thirst = t.contains("thirst") ? t.getFloat("thirst") : 100;
        milkTimer = t.getFloat("milk");
        woolTimer = t.getFloat("wool");
        starving = t.getInt("starving");
        layTimer = t.getFloat("lay");
        laying = t.getBoolean("laying");
    }
}
