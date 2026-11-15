package fr.pioneers.farm.herd;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 1.0.4-beta (lot 9) : donnees d'un lapin ou d'un cheval (ane, mulet) du mod, attachees aux ENTITES VANILLA (capacite {@link PiHerd#CAP}) :
 * race ({@link GeneratedHerd}), et les stats HERITEES qui ne sont pas deja des attributs vanilla : lapins (croissance, portee, viande, peau,
 * laine : multiplicateurs propres a l'individu, autour de ceux de sa race) ; chevaux (force de trait et charge de bat en kg ; vitesse, saut
 * et sante restent les attributs vanilla, fixes par la race a la naissance). Synchronisee vers les clients ({@link PacketHerd}) : robe,
 * echelle, fiche au regard.
 */
public final class HerdData implements INBTSerializable<CompoundTag> {

    public static final int RABBIT = 0, HORSE = 1;

    public boolean init;
    public int kind;
    /** Race (index dans {@link GeneratedHerd#RABBITS} ou {@link GeneratedHerd#HORSES}) ; -1 = pas encore connue (client). */
    public int breed = -1;
    public float growth = 1, litter = 7, meat = 1, pelt = 1, wool;
    /** Lapins angora : secondes avant que la laine ait repousse (0 = prete). */
    public float woolTimer;
    /** Chevaux : force de trait et charge de bat (kg) ; lues par les caravanes (lot 10). */
    public int draft, pack;

    public GeneratedHerd.RabbitBreed rabbit() {
        GeneratedHerd.RabbitBreed[] b = GeneratedHerd.RABBITS;
        return b[Math.max(0, Math.min(b.length - 1, breed))];
    }

    public GeneratedHerd.HorseBreed horse() {
        GeneratedHerd.HorseBreed[] b = GeneratedHerd.HORSES;
        return b[Math.max(0, Math.min(b.length - 1, breed))];
    }

    public HerdData copy() {
        HerdData d = new HerdData();
        d.deserializeNBT(serializeNBT());
        return d;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("init", init);
        t.putByte("kind", (byte) kind);
        t.putByte("breed", (byte) breed);
        if (kind == RABBIT) {
            t.putFloat("growth", growth);
            t.putFloat("litter", litter);
            t.putFloat("meat", meat);
            t.putFloat("pelt", pelt);
            t.putFloat("wool", wool);
            t.putFloat("wool_t", woolTimer);
        } else {
            t.putInt("draft", draft);
            t.putInt("pack", pack);
        }
        return t;
    }

    @Override
    public void deserializeNBT(CompoundTag t) {
        init = t.getBoolean("init");
        kind = t.getByte("kind");
        breed = t.contains("breed") ? t.getByte("breed") : -1;
        growth = t.contains("growth") ? t.getFloat("growth") : 1;
        litter = t.contains("litter") ? t.getFloat("litter") : 7;
        meat = t.contains("meat") ? t.getFloat("meat") : 1;
        pelt = t.contains("pelt") ? t.getFloat("pelt") : 1;
        wool = t.getFloat("wool");
        woolTimer = t.getFloat("wool_t");
        draft = t.getInt("draft");
        pack = t.getInt("pack");
    }
}
