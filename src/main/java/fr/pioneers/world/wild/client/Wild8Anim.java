package fr.pioneers.world.wild.client;

import fr.pioneers.world.wild.Giraffe;
import fr.pioneers.world.wild.Monkey;
import fr.pioneers.world.wild.Wild8Rules;
import fr.pioneers.world.wild.WildAnimal;
import fr.pioneers.world.wild.WildRules;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 8) : POSES de la savane et de la jungle, appelees par {@link WildModel#setupAnim} apres la remise a la pose initiale
 * (sortes 11 a 16) :
 * <ul>
 *     <li>pas lent et ample des grands animaux (girafe, elephant, rhinoceros : foulee reduite, meme cadence), galop des fauves ;</li>
 *     <li>girafe : BROUTE LA CIME (cou dresse, tete levee, machoire qui mache), broute bas, balance le cou, RUADE (pattes arriere
 *     detendues, croupe haute) ; couchee le cou droit ;</li>
 *     <li>lion : couche (faineant), gronde (oreilles couchees, queue qui fouette), rugit (tete levee), charge ;</li>
 *     <li>rhinoceros : broute, secoue la tete, charge tete basse corne en avant ;</li>
 *     <li>elephant : trompe qui se balance, qui cueille et porte a la bouche, levee pour BARRIR (oreilles deployees), recourbee a la charge ;
 *     oreilles qui battent ; defenses plus courtes chez la femelle ;</li>
 *     <li>singe : marche, ESCALADE (corps dresse contre le tronc, membres qui agrippent), REPAS assis (il epluche : la main gauche tire les
 *     lanieres ; il mange : la chair raccourcit ; il jette la peau : le bras part sur le cote), toilette ;</li>
 *     <li>tigre : A L'AFFUT (tapi : pattes flechies, ventre au ras du sol, tete basse, oreilles couchees, queue tendue), BOND (pattes avant
 *     lancees, arriere detendues), baille ; mort (pattes raides).</li>
 * </ul>
 */
final class Wild8Anim {

    private Wild8Anim() {
    }

    /** Foulee des grands animaux : meme cadence que le gibier, angle reduit (longues pattes). */
    static float stride(int kind) {
        return switch (kind) {
            case Wild8Rules.GIRAFFE -> 0.5F;
            case Wild8Rules.ELEPHANT -> 0.55F;
            case Wild8Rules.RHINO -> 0.75F;
            case Wild8Rules.MONKEY -> 1.0F;
            default -> 0.9F;
        };
    }

    static <T extends WildAnimal> void setup(WildModel<T> m, T e, float limb, float amount, float age, float yaw, float pitch) {
        int k = m.kind, act = e.action();
        float a = Math.min(1F, amount);
        tusks(m, e);
        if (e.deathTime > 0) {
            m.dead();
            trunk(m, 0.4F, 0.3F, 0.2F);
            if (e instanceof Monkey mk) {
                banana(m, mk, -1);
            }
            return;
        }
        if (e instanceof Monkey mk && (mk.climbing() || act == Wild8Rules.CLIMB)) {
            climb(m, age, a);
            banana(m, mk, -1);
            return;
        }
        if (act == WildRules.REST) {
            m.lying(age);
            if (k == Wild8Rules.LION || k == Wild8Rules.TIGER) {
                m.head.y += 0.5F;                                     //la tete posee sur les pattes
                if (m.tail != null) {
                    m.tail.yRot = 0.6F;
                }
            }
            idleParts(m, e, age, a, act);
            trunk(m, -1.5F, 0.12F, 0.18F);                           //couche : la trompe posee devant lui sur le sol
            if (e instanceof Monkey mk) {
                banana(m, mk, -1);
            }
            return;
        }
        if (act == Wild8Rules.STALK) {
            crouch(m, limb, a, age);
            return;
        }
        if (act == Wild8Rules.POUNCE) {
            pounce(m);
            return;
        }
        if (e instanceof Monkey mk && (act == Wild8Rules.EAT || act == WildRules.DISPLAY)) {
            sitUp(m, mk, age, act == Wild8Rules.EAT ? mk.eatTicks(age - (int) age) : -1F);
            return;
        }
        m.walkQuad(e, limb, a);
        float s = stride(k);
        for (ModelPart l : m.legs) {
            l.xRot *= s;
        }
        m.look(yaw, pitch);
        ModelPart head = m.head, neck = m.neck;
        switch (act) {
            case Wild8Rules.BROWSE -> {
                //la cime des acacias : cou dresse a la verticale, tete levee, la machoire mache
                if (neck != null) {
                    neck.xRot -= 0.36F;
                    neck.yScale = 1.14F;                               //il s'etire
                }
                head.xRot -= 0.55F;
                if (m.snout != null) {
                    m.snout.xRot = 0.08F * Mth.sin(age * 0.9F);
                }
            }
            case WildRules.GRAZE -> {
                if (k == Wild8Rules.GIRAFFE && neck != null) {
                    neck.xRot += 0.95F;
                    head.xRot += 0.35F;
                } else if (k == Wild8Rules.ELEPHANT) {
                    head.xRot += 0.2F;
                    float p = 0.5F + 0.5F * Mth.sin(age * 0.11F);    //la trompe cueille au sol puis porte a la bouche
                    trunk(m, -0.25F + 0.2F * p, 1.3F * p, 1.4F * p);
                } else {
                    head.xRot += 0.55F + 0.06F * Mth.sin(age * 0.5F);
                }
            }
            case WildRules.DISPLAY -> {
                switch (k) {
                    case Wild8Rules.GIRAFFE -> {
                        if (neck != null) {
                            neck.zRot = 0.22F * Mth.sin(age * 0.15F);  //le cou se balance
                        }
                    }
                    case Wild8Rules.ELEPHANT -> {
                        head.xRot -= 0.3F;                             //barrit : trompe levee, oreilles deployees
                        trunk(m, -1.7F, -0.8F, -0.5F);
                        ears(m, 0.55F);
                    }
                    case Wild8Rules.RHINO -> head.xRot += -0.25F + 0.35F * Mth.sin(age * 0.5F);
                    default -> head.xRot -= 0.5F + 0.04F * Mth.sin(age * 0.3F);   //rugit, baille
                }
            }
            case WildRules.ALERT -> {
                if (k == Wild8Rules.LION || k == Wild8Rules.TIGER) {
                    head.xRot += 0.12F;                                //gronde : oreilles couchees
                    if (m.rightEar != null) {
                        m.rightEar.xRot += 0.5F;
                        m.leftEar.xRot += 0.5F;
                    }
                } else if (neck != null) {
                    neck.xRot -= 0.25F;
                    head.xRot += 0.15F;
                } else {
                    head.xRot -= 0.15F;
                }
            }
            case WildRules.CHARGE -> {
                head.xRot += k == Wild8Rules.RHINO ? 0.4F : 0.18F;    //le rhinoceros baisse la corne
                if (k == Wild8Rules.ELEPHANT) {
                    trunk(m, 0.6F, 0.9F, 0.6F);
                    ears(m, 0.6F);
                }
            }
            case Wild8Rules.KICK -> kick(m, e);
            default -> {
            }
        }
        idleParts(m, e, age, a, act);
        if (e instanceof Monkey mk) {
            banana(m, mk, -1);
        }
    }

    // ------------------------------------------------------------------ pieces

    @Nullable
    static ModelPart part(WildModel<?> m, String name) {
        return m.named.get(name);
    }

    /** Trompe : trois anneaux (le premier part du museau). */
    static void trunk(WildModel<?> m, float a1, float a2, float a3) {
        ModelPart t1 = part(m, "trunk_1"), t2 = part(m, "trunk_2"), t3 = part(m, "trunk_3");
        if (t1 != null) {
            t1.xRot = a1;
            t2.xRot = a2;
            t3.xRot = a3;
        }
    }

    /** Oreilles de l'elephant ecartees de {@code spread} (0 : contre la tete). */
    static void ears(WildModel<?> m, float spread) {
        if (m.rightEar != null && m.kind == Wild8Rules.ELEPHANT) {
            m.rightEar.yRot -= spread;
            m.leftEar.yRot += spread;
        }
    }

    /** Defenses : plus courtes chez la femelle, a peine sorties chez le petit. */
    static void tusks(WildModel<?> m, WildAnimal e) {
        float s = e.isBaby() ? 0.35F : e.sex() == WildRules.FEMALE ? 0.65F : 1F;
        for (String n : new String[]{"right_tusk", "left_tusk"}) {
            ModelPart p = part(m, n);
            if (p != null) {
                p.yScale = s;
            }
        }
    }

    /** Ce qui vit au repos comme en marche : trompe qui se balance, oreilles qui battent, queue qui fouette, toupet. */
    static void idleParts(WildModel<?> m, WildAnimal e, float age, float a, int act) {
        int k = m.kind;
        if (k == Wild8Rules.ELEPHANT && act != WildRules.GRAZE && act != WildRules.DISPLAY && act != WildRules.CHARGE) {
            trunk(m, 0.05F * Mth.sin(age * 0.08F), 0.1F * Mth.sin(age * 0.08F + 1F), 0.16F * Mth.sin(age * 0.08F + 2F));
            ears(m, 0.18F * (0.5F + 0.5F * Mth.sin(age * 0.14F)));
        }
        if (m.tail != null) {
            boolean angry = act == WildRules.ALERT || act == WildRules.CHARGE;
            float rate = angry ? 0.45F : 0.12F, amp = angry ? 0.45F : 0.18F;
            if (k == Wild8Rules.MONKEY) {
                m.tail.zRot = 0.2F * Mth.sin(age * 0.1F);
            } else if (act != WildRules.REST) {
                //1.0.5-beta (lot 30) : phase INTEGREE (Wild9Anim.phase) : la cadence change avec la colere sans faire sauter la queue
                m.tail.zRot = amp * Mth.sin(Wild9Anim.phase(e, age, rate)) + 0.15F * a * Mth.cos(age * 0.6F);
            }
        }
        if (m.rightEar != null && k != Wild8Rules.ELEPHANT && act != WildRules.ALERT) {
            float t = age % 100F;
            if (t < 6F) {
                m.rightEar.zRot += 0.2F * Mth.sin(t * 1.05F);
            }
        }
    }

    /** Ruade de la girafe : pattes arriere detendues vers l'arriere, croupe haute, tete basse. */
    static void kick(WildModel<?> m, WildAnimal e) {
        float f = e instanceof Giraffe g && g.kicking() ? 1F : 0.8F;
        m.body.xRot = 0.08F;
        m.body.y -= 1.3F;
        m.legs[2].xRot = m.legs[3].xRot = 1.05F * f;
        m.legs[0].xRot = m.legs[1].xRot = -0.08F;
        if (m.neck != null) {
            m.neck.xRot += 0.35F;
        }
        if (m.tail != null) {
            m.tail.xRot = 1.2F;
        }
    }

    /** Tigre a l'affut : pattes flechies (raccourcies), ventre au ras du sol, tete basse, oreilles couchees, queue tendue ; pas lent. */
    static void crouch(WildModel<?> m, float limb, float a, float age) {
        float s = 0.62F, L = m.shape.legLen();
        m.body.y = 25F - s * (L + 1F);
        for (ModelPart l : m.legs) {
            l.yScale = s;
        }
        float w = Mth.cos(limb * 0.6662F) * 0.55F * a, w2 = -w;
        m.legs[0].xRot = w2;
        m.legs[1].xRot = w;
        m.legs[2].xRot = w;
        m.legs[3].xRot = w2;
        m.head.y += 1.0F;
        m.head.xRot = -0.12F;
        if (m.rightEar != null) {
            m.rightEar.xRot += 0.6F;
            m.leftEar.xRot += 0.6F;
        }
        if (m.tail != null) {
            m.tail.xRot = 1.25F;
            m.tail.zRot = 0.08F * Mth.sin(age * 0.5F);               //seul le bout frissonne
        }
    }

    /** Bond du tigre : corps lance, pattes avant tendues vers l'avant, pattes arriere detendues. */
    static void pounce(WildModel<?> m) {
        m.body.xRot = -0.22F;
        m.legs[0].xRot = m.legs[1].xRot = -1.25F;
        m.legs[2].xRot = m.legs[3].xRot = 1.05F;
        m.head.xRot = 0.1F;
        if (m.tail != null) {
            m.tail.xRot = 1.5F;
        }
    }

    /** Singe qui grimpe : corps dresse contre le tronc, membres qui agrippent en alternance, queue pendante. */
    static void climb(WildModel<?> m, float age, float a) {
        float up = 1.45F;
        m.body.xRot = -up;
        float g = Mth.sin(age * 0.7F) * 0.45F;
        m.legs[0].xRot = -0.35F + g;
        m.legs[1].xRot = -0.35F - g;
        m.legs[2].xRot = -0.15F - g;
        m.legs[3].xRot = -0.15F + g;
        m.head.xRot = up * 0.9F;
        if (m.tail != null) {
            m.tail.xRot = 2.6F;
        }
    }

    /**
     * Singe assis : le repas (t >= 0, ticks depuis son debut : epluche, mange, jette la peau, se leche les doigts) ou la toilette (t < 0).
     * Le corps pivote sur la hanche, pattes arriere au sol, bras vers la bouche.
     */
    static void sitUp(WildModel<?> m, Monkey mk, float age, float t) {
        float up = 1.0F;
        m.body.xRot = -up;
        m.legs[2].xRot = m.legs[3].xRot = up;
        ModelPart ra = m.legs[0], la = m.legs[1];
        m.head.xRot = up * 0.85F;
        if (m.tail != null) {
            m.tail.xRot = 1.9F;
            m.tail.zRot = 0.2F * Mth.sin(age * 0.1F);
        }
        if (t < 0) {
            //toilette : la main gauche gratte la tete, la droite pend
            ra.xRot = up - 0.6F;
            la.xRot = up - 2.3F;
            la.zRot = -0.5F + 0.25F * Mth.sin(age * 1.4F);
            m.head.yRot = 0.3F;
            banana(m, mk, -1);
            return;
        }
        int stage = Wild8Rules.eatStage((int) t);
        switch (stage) {
            case 0 -> {
                //epluche : la banane devant la poitrine, la main gauche tire les lanieres vers le bas
                ra.xRot = up - 1.55F;
                la.xRot = up - 1.6F + 0.45F * Math.abs(Mth.sin(t * 0.35F));
                la.zRot = -0.25F;
                m.head.xRot += 0.25F;                                //il regarde ce qu'il fait
            }
            case 1 -> {
                //mange : la banane a la bouche, la tete machonne, la chair raccourcit
                ra.xRot = up - 2.05F + 0.08F * Mth.sin(t * 0.9F);
                la.xRot = up - 1.3F;
                m.head.xRot += 0.06F * Mth.sin(t * 1.3F);
            }
            case 2 -> {
                //jette la peau : le bras part sur le cote et vers l'arriere
                float f = Mth.clamp((t - Wild8Rules.EAT_END) / (Wild8Rules.THROW_AT - Wild8Rules.EAT_END), 0F, 1F);
                ra.xRot = up - 1.6F + 0.9F * f;
                ra.zRot = 1.3F * f;
                la.xRot = up - 0.9F;
            }
            default -> {
                //se leche les doigts
                ra.xRot = up - 2.1F;
                la.xRot = up - 0.7F;
                m.head.xRot += 0.15F;
            }
        }
        banana(m, mk, t);
    }

    /** Banane tenue (peau jaune) et sa chair : visibles selon le repas (t : ticks du repas ; -1 : hors repas, seulement s'il en tient une). */
    static void banana(WildModel<?> m, Monkey mk, float t) {
        ModelPart peel = part(m, "banana"), flesh = part(m, "banana_flesh");
        if (peel == null) {
            return;
        }
        if (t < 0) {
            peel.visible = mk.hasBanana();
            peel.xRot = -1.4F;                                        //tenue le long de l'avant-bras, pointe en l'air
            flesh.visible = false;
            return;
        }
        int stage = Wild8Rules.eatStage((int) t);
        peel.visible = stage == 0 || stage == 1 || stage == 2 && t < Wild8Rules.THROW_AT;
        flesh.visible = stage == 0 && t > Wild8Rules.PEEL_END * 0.5F || stage == 1;
        if (stage >= 1) {
            peel.zScale = 0.55F;                                      //les lanieres pendent sous la chair
            peel.yScale = 1.2F;
        }
        if (stage == 1) {
            float eaten = (t - Wild8Rules.PEEL_END) / (Wild8Rules.EAT_END - Wild8Rules.PEEL_END);
            flesh.zScale = Math.max(0.15F, 1F - 0.85F * eaten);
        }
    }
}
