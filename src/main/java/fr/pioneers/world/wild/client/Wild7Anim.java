package fr.pioneers.world.wild.client;

import fr.pioneers.world.wild.Wild7Rules;
import fr.pioneers.world.wild.WildAnimal;
import fr.pioneers.world.wild.WildRules;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * 1.0.5-beta (lot 7) : POSES des predateurs et de la petite faune, appelees par {@link WildModel#setupAnim} apres la remise a la pose
 * initiale (sortes 5 a 10) :
 * <ul>
 *     <li>loup, ours, rat (quadrupedes du lot 6) : marche, course (galop), couche, flairage, animation propre — le loup HURLE (tete levee),
 *     l'ours SE DRESSE sur ses pattes arriere (pattes avant pendantes), le rat s'assoit et se TOILETTE —, charge tete basse, queue du loup
 *     qui remue (et se tend a la course), queue du rat qui ondule, mort ;</li>
 *     <li>pic-vert, pigeon (oiseaux) : marche (le pigeon hoche la tete), VOL (ailes battantes a l'horizontale, pattes repliees), picorage,
 *     pic-vert ACCROCHE AU TRONC (corps dresse, queue en appui) qui TAMBOURINE (tete en salves), pigeon qui ROUCOULE (poitrail gonfle,
 *     queue trainante), pigeon juche (assis), mort ;</li>
 *     <li>serpent : ONDULATION qui court le long des anneaux en chaine, ENROULE au repos, avant du corps DRESSE (aguets, morsure), langue
 *     dardee par instants, mort (allonge).</li>
 * </ul>
 */
final class Wild7Anim {

    private Wild7Anim() {
    }

    static <T extends WildAnimal> void setup(WildModel<T> m, T e, float limb, float amount, float age, float yaw, float pitch) {
        float a = Math.min(1F, amount);
        switch (m.kind) {
            case Wild7Rules.WOODPECKER, Wild7Rules.PIGEON -> bird(m, e, limb, a, age, yaw, pitch);
            case Wild7Rules.SNAKE -> snake(m, e, limb, a, age, yaw);
            default -> quad(m, e, limb, a, age, yaw, pitch);
        }
    }

    // ------------------------------------------------------------------ quadrupedes

    static <T extends WildAnimal> void quad(WildModel<T> m, T e, float limb, float a, float age, float yaw, float pitch) {
        int k = m.kind, act = e.action();
        if (e.deathTime > 0) {
            m.dead();
            return;
        }
        if (act == WildRules.REST) {
            m.lying(age);
            if (k == Wild7Rules.RAT && m.tail != null) {
                m.tail.xRot = 1.3F;
                m.tail.yRot = 0.6F;
            }
            return;
        }
        m.walkQuad(e, limb, a);
        m.look(yaw, pitch);
        ModelPart body = m.body, head = m.head;
        ModelPart[] legs = m.legs;
        switch (act) {
            case WildRules.GRAZE -> {
                head.xRot += k == Wild7Rules.RAT ? 0.4F + 0.15F * Mth.sin(age * 1.4F) : 0.6F + 0.08F * Mth.sin(age * 0.6F);
                if (m.snout != null && k == Wild7Rules.RAT) {
                    m.snout.yRot = 0.12F * Mth.sin(age * 2.2F);            //le museau du rat frémit
                }
            }
            case WildRules.DISPLAY -> {
                if (k == Wild7Rules.WOLF) {
                    head.xRot = -0.95F + 0.04F * Mth.sin(age * 0.3F);     //hurlement : museau vers le ciel
                } else {
                    //l'ours se dresse, le rat s'assoit : le corps pivote sur la hanche, pattes arriere au sol, pattes avant pendantes
                    float up = k == Wild7Rules.BEAR ? 1.15F : 0.85F;
                    body.xRot = -up;
                    legs[2].xRot = legs[3].xRot = up;
                    legs[0].xRot = legs[1].xRot = up - (k == Wild7Rules.BEAR ? 0.55F : 1.15F + 0.25F * Mth.sin(age * 1.6F));
                    head.xRot += up * 0.85F;
                }
            }
            case WildRules.ALERT -> {
                head.xRot -= 0.2F;
                if (m.rightEar != null) {
                    m.rightEar.xRot -= 0.25F;
                    m.leftEar.xRot -= 0.25F;
                }
            }
            case WildRules.CHARGE -> head.xRot += 0.35F;
            default -> {
            }
        }
        if (m.tail != null) {
            if (k == Wild7Rules.WOLF) {
                boolean run = act == WildRules.CHARGE || act == WildRules.FLEE || a > 0.6F;
                m.tail.xRot = run ? 1.15F : m.tail.xRot;
                m.tail.zRot = run ? 0.1F * Mth.sin(age * 0.8F) : 0.18F * Mth.sin(age * 0.35F);
            } else if (k == Wild7Rules.RAT) {
                m.tail.yRot = 0.35F * Mth.sin(age * 0.18F + limb * 0.5F);
            }
        }
        if (m.rightEar != null && act != WildRules.ALERT) {
            float t = age % 90F;
            if (t < 6F) {
                m.rightEar.zRot += 0.2F * Mth.sin(t * 1.05F);
            }
        }
    }

    // ------------------------------------------------------------------ oiseaux

    static <T extends WildAnimal> void bird(WildModel<T> m, T e, float limb, float a, float age, float yaw, float pitch) {
        int k = m.kind, act = e.action();
        ModelPart body = m.body, head = m.head, neck = m.neck, tail = m.tail, rw = m.rightWing, lw = m.leftWing;
        ModelPart chest = m.named.get("chest");
        if (e.deathTime > 0) {
            m.legs[0].xRot = 0.5F;
            m.legs[1].xRot = 0.6F;
            return;
        }
        boolean clinging = k == Wild7Rules.WOODPECKER && !e.onGround() && (act == Wild7Rules.PERCH || act == WildRules.DISPLAY);
        boolean flying = !clinging && (act == Wild7Rules.FLY || !e.onGround() && act != Wild7Rules.PERCH);
        head.yRot = yaw * 0.6F;
        head.xRot += pitch * 0.5F;
        if (clinging) {
            //accroche au tronc : corps dresse contre l'ecorce, pattes qui agrippent, queue en appui, tete vers le tronc
            float up = 1.25F;
            body.xRot = -up;
            head.xRot += up * 0.85F;
            for (ModelPart l : m.legs) {
                l.xRot = -0.25F;
            }
            if (tail != null) {
                tail.xRot = 0.25F;
            }
            if (act == WildRules.DISPLAY) {
                head.xRot += 0.45F * Math.abs(Mth.sin(age * 3.2F));    //tambour : coups de bec en salve
            }
            return;
        }
        if (flying) {
            float flap = Mth.sin(age * 1.5F) * 0.75F;
            rw.zRot = 1.45F + flap;
            lw.zRot = -rw.zRot;
            rw.xRot = lw.xRot = 0.1F;
            for (ModelPart l : m.legs) {
                l.xRot = 1.15F;                                        //pattes repliees en arriere
            }
            if (tail != null) {
                tail.xRot -= 0.1F;
                tail.xScale = 1.25F;
            }
            body.xRot = -0.08F + 0.04F * flap;
            return;
        }
        //au sol : pas, hochement de tete du pigeon
        float w = Mth.cos(limb * 0.9F) * 1.1F * a;
        m.legs[0].xRot = w;
        m.legs[1].xRot = -w;
        if (neck != null) {
            neck.xRot += Mth.sin(limb * 1.8F) * 0.3F * a;
        }
        switch (act) {
            case WildRules.GRAZE -> {
                if (neck != null) {
                    neck.xRot += 0.9F + 0.35F * Math.max(0F, Mth.sin(age * 0.9F));
                    head.xRot += 0.3F;
                } else {
                    head.xRot += 0.8F + 0.35F * Math.max(0F, Mth.sin(age * 1.1F));
                    body.xRot = 0.2F;
                }
            }
            case WildRules.DISPLAY -> {
                //le pigeon fait le beau : poitrail gonfle, cou rond, queue trainante, petite rotation
                if (chest != null) {
                    chest.xScale = chest.yScale = chest.zScale = 1.18F + 0.04F * Mth.sin(age * 0.5F);
                }
                if (neck != null) {
                    neck.xRot -= 0.25F;
                    neck.xScale = neck.zScale = 1.15F;
                }
                head.xRot += 0.35F + 0.25F * Math.max(0F, Mth.sin(age * 0.5F));
                if (tail != null) {
                    tail.xRot = 0.45F;
                    tail.xScale = 1.45F;
                }
                rw.zRot = 0.12F;
                lw.zRot = -0.12F;
            }
            case Wild7Rules.PERCH -> {
                //juche : pose, plumage gonfle, pattes sous le ventre
                body.y += 1.2F;
                for (ModelPart l : m.legs) {
                    l.yScale = 0.35F;
                }
                body.xScale = body.zScale = 1.06F;
                if (neck != null) {
                    neck.xRot -= 0.3F;
                }
            }
            case WildRules.ALERT -> head.xRot -= 0.3F;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ serpent

    static final int RINGS = 6;

    static <T extends WildAnimal> void snake(WildModel<T> m, T e, float limb, float a, float age, float yaw) {
        int act = e.action();
        ModelPart body = m.body, head = m.head;
        ModelPart[] seg = new ModelPart[RINGS];
        for (int i = 0; i < RINGS; i++) {
            seg[i] = m.named.get("seg_" + (i + 1));
        }
        if (m.snout != null) {
            m.snout.visible = (age % 50F) < 7F || act == WildRules.ALERT && (age % 18F) < 8F;    //langue dardee
        }
        if (e.deathTime > 0) {
            head.xRot = 0.1F;
            if (m.snout != null) {
                m.snout.visible = false;
            }
            return;
        }
        if (act == WildRules.REST) {
            //enroule : chaque anneau tourne du meme angle, tete posee sur le corps
            body.yRot = 0.4F;
            body.x -= 4F;                                          //la spirale est recentree sur la bete
            body.z += 7F;
            for (ModelPart s : seg) {
                s.yRot = 0.72F;
            }
            head.yRot = -0.5F + 0.08F * Mth.sin(age * 0.05F);
            head.xRot = -0.1F;
            return;
        }
        //ondulation : l'angle absolu de l'anneau i suit A sin(phase - i k) ; chaque anneau tourne de la difference avec le precedent.
        //1.0.5-beta (lot 30, retour 4 « pas seulement les animaux marins ») : la part au repos est INTEGREE (Wild9Anim.phase) ; avant
        //age x 0,08 x (1 - a) : a (vitesse de marche) change a chaque choc ou poussee et la phase sautait de age x 0,08 x da (des radians).
        float amp = 0.18F + 0.32F * a, phase = limb * 0.75F + Wild9Anim.phase(e, age, 0.08F * (1F - a)), lag = 0.95F;
        float prev = amp * Mth.sin(phase);
        body.yRot = prev;
        for (int i = 0; i < RINGS; i++) {
            float abs = amp * Mth.sin(phase - (i + 1) * lag);
            seg[i].yRot = abs - prev;
            prev = abs;
        }
        head.yRot = -body.yRot * 0.7F + yaw * 0.5F;
        if (act == WildRules.ALERT || act == WildRules.CHARGE) {
            //avant du corps dresse : le premier anneau se releve, le suivant revient a plat ; la morsure projette la tete
            float up = act == WildRules.CHARGE ? 0.7F : 0.55F;
            body.xRot = -up;
            body.y -= (m.shape.bodyLen() - 1F) * Mth.sin(up);
            seg[0].xRot = up;
            head.xRot = up * 0.6F + (act == WildRules.CHARGE ? 0.25F * Math.max(0F, Mth.sin(age * 0.9F)) : 0F);
        }
    }
}
