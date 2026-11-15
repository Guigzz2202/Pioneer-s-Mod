package fr.pioneers.world.wild.client;

import fr.pioneers.world.wild.MarineAnimal;
import fr.pioneers.world.wild.Penguin;
import fr.pioneers.world.wild.Wild7Rules;
import fr.pioneers.world.wild.Wild8Rules;
import fr.pioneers.world.wild.Wild9Rules;
import fr.pioneers.world.wild.WildAnimal;
import fr.pioneers.world.wild.WildRules;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 9) : ANIMATIONS de la mer et des cotes (appele par {@link WildModel#setupAnim} apres la pose initiale, sortes 17 a 31).
 * <ul>
 *     <li>cetaces : ondulation VERTICALE qui part du corps vers la caudale (deux anneaux puis la caudale, dephasees), pectorales qui
 *     godillent, tangage de la nage ; SOUFFLE en surface (corps a plat, tete levee, caudale au repos) ; PLONGEE (tete en bas, caudale
 *     levee hors de l'eau) ; attaque (machoire du cachalot, tete de l'orque) ;</li>
 *     <li>requins : ondulation LATERALE (tete qui contre-balance, pedoncule et caudale qui balaient) ; MORSURE : machoire ouverte, museau
 *     releve ; requin-baleine : bouche entrouverte, lent ;</li>
 *     <li>raie manta : battement des ailes qui se propage de la base a la pointe ;</li>
 *     <li>phoque, otarie : a terre, reptation (avant et arriere qui se soulevent tour a tour) ; PRELASSE (en banane : tete et nageoires
 *     arriere relevees) ; dresse et qui aboie (otarie) ; NAGE et plongee (ondulation, nageoires arriere qui balaient, avant plaquees) ;</li>
 *     <li>mouette : oiseau du lot 7 ({@link Wild7Anim#bird}), plane ailes tendues une partie du vol ;</li>
 *     <li>manchot, pingouin : dandinement, ventre a terre, appel (tete au ciel), NAGE a plat ventre ailerons battants, poisson au bec,
 *     repas (poisson avale).</li>
 * </ul>
 */
final class Wild9Anim {

    private Wild9Anim() {
    }

    static <T extends WildAnimal> void setup(WildModel<T> m, T e, float limb, float amount, float age, float yaw, float pitch) {
        int k = m.kind;
        easeClock(e, age);
        ModelPart fish = m.named.get("fish");
        if (fish != null) {
            fish.visible = e instanceof Penguin p && p.hasFish();
        }
        if (Wild9Rules.cetacean(k)) {
            cetacean(m, e, amount, age, yaw, pitch);
        } else if (Wild9Rules.shark(k)) {
            shark(m, e, amount, age, yaw, pitch);
        } else if (k == Wild9Rules.MANTA) {
            manta(m, e, amount, age, pitch);
        } else if (Wild9Rules.pinniped(k)) {
            pinniped(m, e, limb, amount, age, yaw, pitch);
        } else if (k == Wild9Rules.SEAGULL) {
            float a = Math.min(1F, amount);
            Wild7Anim.bird(m, e, limb, a, age, yaw, pitch);
            boolean flying = e.action() == Wild7Rules.FLY || !e.onGround() && e.action() != Wild7Rules.PERCH;
            if (flying && e.deathTime == 0 && (age % 90F) < 50F) {
                //vol plane : ailes tendues, a peine fremissantes
                m.rightWing.zRot = 1.5F + 0.04F * Mth.sin(age * 0.5F);
                m.leftWing.zRot = -m.rightWing.zRot;
            }
        } else {
            auk(m, e, limb, amount, age, yaw, pitch);
        }
    }

    @Nullable
    static ModelPart p(WildModel<?> m, String name) {
        return m.named.get(name);
    }

    /** Periode commune des phases de nage : sin(ph), sin(ph - k) et sin(ph / 2) y sont continus. */
    static final float PERIOD = (float) (4 * Math.PI);

    /**
     * 1.0.5-beta (lot 21, retour de Guillaume : « animations des animaux marins tres buggees quand il y a une collision, effet saccade ») :
     * PHASE INTEGREE. Avant : {@code ph = age * f * (0,7 + 0,7 * vitesse)} : la frequence dependait de la vitesse de nage
     * ({@code walkAnimation}, qui varie a chaque poussee, chaque choc contre un bloc, un bateau ou le joueur, et a chaque acceleration) et la
     * phase etait recalculee depuis la naissance de la bete : a 5 minutes d'age, 1 % de vitesse en plus faisait sauter la queue de plusieurs
     * radians d'une image a l'autre. Maintenant la phase avance de (temps ecoule) x (frequence du moment) : la frequence peut changer, la
     * queue ne saute jamais. Premiere image, retour en arriere du temps ou bete revue apres plus de 10 ticks : la phase repart de l'age.
     */
    public static float phase(WildAnimal e, float age, float rate) {
        float last = e.animAge;
        if (last < 0F || age < last || age - last > 10F) {
            e.animPhase = (age * rate) % PERIOD;
        } else {
            e.animPhase = (e.animPhase + (age - last) * rate) % PERIOD;
        }
        e.animAge = age;
        return e.animPhase;
    }

    // ------------------------------------------------------------------ transitions lissees (1.0.5-beta lot 28, retour 38)

    /** Emplacements de {@link WildAnimal#animEase} : attaque (charge ou morsure), morsure, souffle, plongee, vitesse de nage (blocs / tick). */
    static final int E_ATTACK = 0, E_BITE = 1, E_BLOW = 2, E_DIVE = 3, E_SPEED = 4;
    /** Temps ecoule depuis l'image precedente de cette bete (ticks) ; -1 a la premiere image (les valeurs prennent leur cible d'un coup). */
    static float easeDt;

    /** Horloge des transitions : comme {@link #phase}, remise a zero au premier dessin, a un retour du temps ou apres 10 ticks sans dessin. */
    static void easeClock(WildAnimal e, float age) {
        float last = e.animEaseAge;
        easeDt = last < 0F || age < last || age - last > 10F ? -1F : age - last;
        e.animEaseAge = age;
    }

    /**
     * 1.0.5-beta (lot 28) : valeur LISSEE vers {@code target} (approche exponentielle, {@code perTick} = part de l'ecart rattrapee par tick,
     * integree sur le temps reel entre deux images : aucune dependance a la cadence d'images). Avant : amplitude de la charge (+0,12 rad),
     * machoire et tete de la morsure, poses du souffle et de la plongee changees d'une image a l'autre.
     */
    static float ease(WildAnimal e, int slot, float target, float perTick) {
        if (easeDt < 0F) {
            e.animEase[slot] = target;
            return target;
        }
        float k = 1F - (float) Math.pow(1F - perTick, easeDt);
        e.animEase[slot] += (target - e.animEase[slot]) * k;
        return e.animEase[slot];
    }

    /** Vitesse affichee (blocs par tick, deplacement interpole du client, vertical compris), lissee sur une demi-seconde environ. */
    static float swimSpeed(WildAnimal e) {
        float v = (float) Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getY() - e.yo) * (e.getY() - e.yo) + (e.getZ() - e.zo) * (e.getZ() - e.zo));
        return ease(e, E_SPEED, Math.min(v, 2F), 0.15F);
    }

    static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static boolean swimming(WildAnimal e) {
        return e.isInWater() || e.action() == Wild9Rules.SWIM || e.action() == Wild9Rules.DIVE;
    }

    // ------------------------------------------------------------------ cetaces

    static <T extends WildAnimal> void cetacean(WildModel<T> m, T e, float amount, float age, float yaw, float pitch) {
        ModelPart tail = m.tail, tail2 = p(m, "tail_2"), fluke = p(m, "fluke"), rf = p(m, "right_fin"), lf = p(m, "left_fin"), jaw = p(m, "jaw");
        int act = e.action();
        if (e.deathTime > 0) {
            tail.xRot = 0.15F;
            if (tail2 != null) {
                tail2.xRot = 0.2F;
            }
            rf.zRot -= 0.5F;
            lf.zRot += 0.5F;
            return;
        }
        //1.0.5-beta (lot 28, retour 39) : FREQUENCE proportionnelle a la vitesse affichee : un battement par Wild9Rules.FLUKE_STRIDE longueur
        //de corps parcourue (baleine a bosse 3,6 s, bleue 6,6 s, orque 1,1 s a la croisiere ; avant 1,5 a 1,9 s pour toutes les baleines),
        //amplitude plus ample, poses du souffle, de la plongee et de l'attaque LISSEES (avant : changees d'un coup)
        int j = m.kind - fr.pioneers.world.wild.GeneratedWild9.FIRST;
        float len = fr.pioneers.world.wild.GeneratedWild9.LENGTH[j] * (e.isBaby() ? 0.55F : 1F);
        double cruise = Wild9Rules.cruise(fr.pioneers.world.wild.GeneratedWild9.SWIM[j]);
        float v = swimSpeed(e);
        float speed = Mth.clamp((float) (v / cruise), 0F, 1F);
        float ph = phase(e, age, Wild9Rules.flukeRate(len, v, cruise));
        float amp = 0.16F + 0.26F * speed;
        float bodyX = pitch + amp * 0.1F * Mth.sin(ph + 1.6F);
        float headX = m.head.xRot;
        float t1 = amp * Mth.sin(ph), t2 = amp * 1.3F * Mth.sin(ph - 1.0F), t3 = amp * 1.6F * Mth.sin(ph - 2.0F);
        m.head.yRot = yaw * 0.12F;
        float fin = 0.08F + 0.1F * speed;
        rf.zRot += fin * Mth.sin(ph * 0.5F);
        lf.zRot -= fin * Mth.sin(ph * 0.5F);
        if (m.kind == Wild9Rules.HUMPBACK) {
            rf.xRot += 0.15F * Mth.sin(ph * 0.5F + 1F);                      //longues pectorales qui godillent
            lf.xRot = rf.xRot;
        }
        //souffle : en surface, corps a plat, tete legerement levee, la caudale se repose
        float b = ease(e, E_BLOW, act == Wild9Rules.BLOW ? 1F : 0F, 0.12F);
        //plongee : tete en bas, la queue se cambre et la caudale sort de l'eau
        float d = ease(e, E_DIVE, act == Wild9Rules.DIVE ? 1F : 0F, 0.12F);
        float sum = b + d;
        if (sum > 1F) {
            b /= sum;
            d /= sum;
        }
        float rest = 1F - b - d;
        bodyX = bodyX * rest + -0.05F * b + (Math.max(pitch, 0.35F) + 0.15F) * d;
        headX = mix(headX, -0.08F, b);
        t1 = t1 * rest + 0.05F * Mth.sin(age * 0.1F) * b - 0.32F * d;
        t2 = t2 * rest + 0.08F * b - 0.42F * d;
        t3 = t3 * rest + 0.1F * b + (-0.5F + 0.1F * Mth.sin(age * 0.2F)) * d;
        float a = ease(e, E_ATTACK, act == WildRules.CHARGE || act == Wild9Rules.BITE ? 1F : 0F, 0.25F);
        if (jaw != null) {
            jaw.xRot = mix(jaw.xRot, 0.35F + 0.25F * Math.abs(Mth.sin(age * 0.4F)), a);
        }
        headX = mix(headX, -0.12F, a);
        m.body.xRot = bodyX;
        m.head.xRot = headX;
        tail.xRot = t1;
        if (tail2 != null) {
            tail2.xRot = t2;
        }
        if (fluke != null) {
            fluke.xRot = t3;
        }
    }

    // ------------------------------------------------------------------ requins

    static <T extends WildAnimal> void shark(WildModel<T> m, T e, float amount, float age, float yaw, float pitch) {
        ModelPart tail = m.tail, tail2 = p(m, "tail_2"), jaw = p(m, "jaw"), rf = p(m, "right_fin"), lf = p(m, "left_fin");
        if (e.deathTime > 0) {
            tail.yRot = 0.3F;
            if (jaw != null) {
                jaw.xRot = 0.3F;
            }
            return;
        }
        float speed = Mth.clamp(amount * 2.2F, 0F, 1F);
        boolean giant = m.kind == Wild9Rules.WHALE_SHARK;
        float f = giant ? 0.14F : 0.3F;
        int act = e.action();
        //1.0.5-beta (lot 28, retour 38) : charge et morsure LISSEES (avant : amplitude +0,12 rad, machoire et tete changees d'un coup a chaque
        //charge et a chaque morsure : la queue du grand blanc sautait quand il fonce sur le nageur)
        float att = ease(e, E_ATTACK, act == WildRules.CHARGE || act == Wild9Rules.BITE ? 1F : 0F, 0.2F);
        float bite = ease(e, E_BITE, act == Wild9Rules.BITE ? 1F : 0F, 0.45F);
        //1.0.5-beta (lot 21) : l'attaque accelere la FREQUENCE (avant : phase x 1,3, un saut a chaque charge ou morsure)
        float ph = phase(e, age, f * (0.7F + 0.7F * speed) * (1F + 0.3F * att));
        float amp = (giant ? 0.14F : 0.2F) + 0.2F * speed + 0.12F * att;
        m.body.xRot = pitch;
        m.body.yRot = amp * 0.12F * Mth.sin(ph);
        m.head.yRot = -amp * 0.35F * Mth.sin(ph) + yaw * 0.25F;
        tail.yRot = amp * Mth.sin(ph - 0.8F);
        if (tail2 != null) {
            tail2.yRot = amp * 1.4F * Mth.sin(ph - 1.6F);
        }
        rf.zRot += 0.06F * Mth.sin(ph * 0.5F);
        lf.zRot -= 0.06F * Mth.sin(ph * 0.5F);
        if (jaw != null) {
            float rest = giant ? 0.18F + 0.05F * Mth.sin(age * 0.2F) : 0.04F;
            float charge = mix(rest, 0.2F, att);
            //morsure : machoire grande ouverte, museau releve
            jaw.xRot = mix(charge, 0.55F * Math.abs(Mth.sin(age * 0.7F)) + 0.25F, bite);
            m.head.xRot = mix(m.head.xRot, -0.22F, bite);
        }
    }

    // ------------------------------------------------------------------ raie manta

    static <T extends WildAnimal> void manta(WildModel<T> m, T e, float amount, float age, float pitch) {
        ModelPart rw = m.rightWing, lw = m.leftWing, rt = p(m, "right_wing_tip"), lt = p(m, "left_wing_tip");
        if (e.deathTime > 0) {
            rw.zRot = lw.zRot = 0F;
            return;
        }
        float speed = Mth.clamp(amount * 2.2F, 0F, 1F);
        boolean flee = e.action() == WildRules.FLEE;
        float ph = phase(e, age, (0.12F + 0.08F * speed) * (flee ? 1.4F : 1F));
        float amp = 0.18F + 0.32F * speed + (flee ? 0.15F : 0F);
        m.body.xRot = pitch + 0.04F * Mth.sin(ph + 1.2F);
        rw.zRot = -amp * Mth.sin(ph);
        lw.zRot = -rw.zRot;
        if (rt != null) {
            rt.zRot = -amp * 0.9F * Mth.sin(ph - 0.8F);
            lt.zRot = -rt.zRot;
        }
        if (m.tail != null) {
            m.tail.yRot = 0.1F * Mth.sin(ph * 0.5F);
        }
    }

    // ------------------------------------------------------------------ phoque, otarie

    static <T extends WildAnimal> void pinniped(WildModel<T> m, T e, float limb, float amount, float age, float yaw, float pitch) {
        ModelPart chest = p(m, "chest"), hind = p(m, "hind"), neck = m.neck, rf = p(m, "right_flipper"), lf = p(m, "left_flipper");
        ModelPart rh = p(m, "right_hind_flipper"), lh = p(m, "left_hind_flipper");
        boolean lion = m.kind == Wild9Rules.SEA_LION;
        int act = e.action();
        if (e.deathTime > 0) {
            chest.xRot = 0.1F;
            m.head.xRot = 0.3F;
            return;
        }
        if (swimming(e)) {
            //nage : corps fusele, avant plaque, l'arriere ondule et les nageoires arriere balaient
            float ph = age * 0.35F;
            float amp = 0.2F + 0.2F * Mth.clamp(amount * 2F, 0F, 1F);
            m.body.xRot = pitch + 0.06F * Mth.sin(ph + 1F);
            chest.xRot = -0.08F * Mth.sin(ph);
            hind.xRot = amp * Mth.sin(ph - 0.8F);
            if (lion) {
                rf.zRot = -0.25F - 0.45F * Mth.sin(ph);                       //l'otarie nage avec ses nageoires avant
                lf.zRot = -rf.zRot;
            } else {
                rf.zRot = -0.15F;
                lf.zRot = 0.15F;
                rf.xRot = lf.xRot = 0.9F;
                rh.yRot = 0.35F * Mth.sin(ph * 1.2F);
                lh.yRot = rh.yRot;
            }
            m.head.xRot = -pitch * 0.4F;
            return;
        }
        float a = Math.min(1F, amount * 2F);
        m.head.yRot = yaw * 0.6F;
        m.head.xRot += pitch * 0.5F;
        switch (act) {
            case WildRules.REST -> {
                //prelasse : en banane, la tete et les nageoires arriere relevees
                float br = 0.02F * Mth.sin(age * 0.08F);
                chest.xRot = -0.22F + br;
                hind.xRot = -0.38F;
                m.head.xRot += 0.1F - 0.06F * Mth.sin(age * 0.03F);
                rh.xRot = lh.xRot = -0.25F;
                rf.zRot *= 0.6F;
                lf.zRot *= 0.6F;
                m.body.xScale = m.body.zScale = 1.0F + br;
            }
            case WildRules.DISPLAY, WildRules.ALERT -> {
                //dresse sur l'avant (l'otarie aboie, tete en l'air)
                float up = lion ? 0.85F : 0.45F;
                chest.xRot = -up;
                if (neck != null) {
                    neck.xRot += 0.2F;
                }
                m.head.xRot += up * 0.6F - (act == WildRules.DISPLAY ? 0.35F * Math.abs(Mth.sin(age * 0.6F)) : 0F);
                if (lion) {
                    rf.xRot = lf.xRot = up;
                }
            }
            default -> {
                //reptation : l'avant puis l'arriere se soulevent tour a tour
                float ph = limb * 0.9F;
                chest.xRot = -0.22F * Math.max(0F, Mth.sin(ph)) * a;
                hind.xRot = -0.25F * Math.max(0F, Mth.sin(ph + Mth.PI)) * a;
                rf.xRot = 0.5F * Mth.sin(ph) * a;
                lf.xRot = lion ? -rf.xRot : rf.xRot;
                if (lion) {
                    rh.yRot = 0.6F * Mth.sin(ph) * a + 0.5F;                  //l'otarie ramene ses nageoires arriere sous elle
                    lh.yRot = -rh.yRot;
                }
            }
        }
    }

    // ------------------------------------------------------------------ manchot, pingouin

    static <T extends WildAnimal> void auk(WildModel<T> m, T e, float limb, float amount, float age, float yaw, float pitch) {
        ModelPart rw = m.rightWing, lw = m.leftWing;
        ModelPart[] legs = m.legs;
        int act = e.action();
        if (e.deathTime > 0) {
            legs[0].xRot = 0.5F;
            legs[1].xRot = 0.6F;
            return;
        }
        m.head.yRot = yaw * 0.7F;
        if (swimming(e)) {
            //nage a plat ventre, ailerons qui battent ("vol" sous l'eau), pattes en gouvernail
            float ph = age * 0.55F;
            m.body.xRot = 1.42F + pitch;
            rw.zRot = 0.35F + 0.55F * Mth.sin(ph);
            lw.zRot = -rw.zRot;
            rw.xRot = lw.xRot = -0.3F;
            for (ModelPart l : legs) {
                l.xRot = 0.15F;
            }
            m.head.xRot = -1.2F;
            return;
        }
        float a = Math.min(1F, amount * 1.6F);
        switch (act) {
            case WildRules.REST -> {
                //a plat ventre sur la glace (glisse, se repose), ailerons le long du corps
                m.body.xRot = 1.42F;
                m.body.y = 24F - m.shape.bodyLen() / 2F;
                m.head.xRot = -1.15F;
                for (ModelPart l : legs) {
                    l.xRot = 0.2F;
                }
                rw.zRot = 0.2F;
                lw.zRot = -0.2F;
                return;
            }
            case WildRules.DISPLAY -> {
                //appel de la colonie : bec au ciel, ailerons ecartes en arriere
                m.head.xRot = -0.85F - 0.08F * Mth.sin(age * 0.7F);
                rw.zRot = 0.45F;
                lw.zRot = -0.45F;
                rw.xRot = lw.xRot = 0.35F;
                m.body.xRot -= 0.06F;
            }
            case WildRules.GRAZE -> m.head.xRot = 0.7F + 0.25F * Math.max(0F, Mth.sin(age * 0.9F));
            case Wild8Rules.EAT -> {
                //repas : la tete se rejette en arriere, le poisson disparait au fil du repas
                m.head.xRot = -0.6F - 0.3F * Math.abs(Mth.sin(age * 0.5F));
            }
            default -> m.head.xRot += pitch * 0.5F;
        }
        //dandinement : le corps bascule d'une patte sur l'autre, petits pas, ailerons un peu ecartes
        float w = Mth.cos(limb * 1.1F) * 0.9F * a;
        legs[0].xRot += w;
        legs[1].xRot -= w;
        m.body.zRot = 0.12F * Mth.sin(limb * 0.55F) * a;
        if (act != WildRules.DISPLAY) {
            rw.zRot += 0.25F * a;
            lw.zRot -= 0.25F * a;
        }
    }
}
