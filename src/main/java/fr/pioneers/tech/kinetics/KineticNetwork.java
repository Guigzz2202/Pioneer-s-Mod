package fr.pioneers.tech.kinetics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Reseau de force rotative : ensemble de noeuds relies par des arbres, des engrenages et des cordes. Chaque noeud i tourne a r_i x Omega
 * (Omega = vitesse de reference, celle du premier noeud). Equilibre et regime transitoire en forme exacte (Euler implicite) :
 * <pre>
 * D = somme r_i tau0_i                        (pousse des sources a l'arret)
 * S = somme r_i^2 |tau0_i| / omegaLibre_i     (baisse de couple des sources avec la vitesse)
 * L = somme |r_j| tauC_j / rendement           (couple demande par les machines)
 * F = somme r_k^2 f_k ,  J = somme r_k^2 J_k   (frottements, inertie)
 * J dOmega/dt = D - S Omega - sgn(Omega) L - F Omega
 * </pre>
 * Le reseau cale si |D| <= L. Tout est rapporte a l'arbre de reference : la puissance se conserve d'un rapport a l'autre (aux rendements pres).
 */
public final class KineticNetwork {

    /** 2 pi / 60 / dt : conversion tr/min par tick -> rad/s2. */
    private static final double C = 2 * Math.PI / 60.0 / 0.05;
    private static final double REST = 1e-3;

    final List<KineticNode> nodes = new ArrayList<>();
    final List<Double> ratios = new ArrayList<>();
    /** 1.12.4 (lot F4) : variateurs traverses depuis la reference (null : aucun) ; rapport effectif = rapport x produit des k^exposant. */
    final List<Chain> chains = new ArrayList<>();
    /** 1.12.4 (lot F4) : variateurs (regulateurs de rotation) du reseau, leur rapport k courant et leur rang dans {@link #nodes}. */
    final List<KineticNode> variators = new ArrayList<>();
    private double[] k = new double[0], sentK = new double[0];
    private int[] variatorNode = new int[0];
    /** Rapports effectifs du tick (variateurs compris). */
    private double[] eff = new double[0];
    /** Rapport minimal d'un variateur (1/64). */
    public static final double VARIATOR_MIN = 1.0 / 64;
    /** Remontee maximale du rapport d'un variateur par tick (la descente est immediate : la consigne n'est jamais depassee). */
    private static final double VARIATOR_RISE = 1.05;
    private static final double RAD = 2 * Math.PI / 60.0;
    boolean jammed;
    /** Vitesse de reference (tr/min). */
    private double speed;
    // Dernier bilan (affichage : tachymetre, dynamometre, fiche du reseau)
    private double drive, load, referenceTorque, power, capacity, generated;
    private int sources, machines;
    /**
     * 0.20.42 : phase de chaque noeud (degres, son repere), avancee a chaque tick de r_i x vitesse ; recalee a la construction sur r_i x Theta
     * (Theta tire d'un noeud qui connait sa phase). Synchronisation des clients decidee pour tout le reseau a la fois.
     */
    private double[] phases = new double[0];
    private double sentSpeed = Double.NaN;
    private boolean sentJam;
    private int age, sentAge;
    /** 0.20.46 (audit) : etat deja envoye aux clients repris a la construction (pas de renvoi complet au premier tick). */
    private boolean carried;
    /**
     * 1.0.5-beta (lot 29) : plus de resynchronisation periodique (avant : toutes les 200 ticks tant que le reseau tournait, un paquet par joueur
     * et un paquet de bloc par piece a donnees propres) : seulement quand la phase vraie s'est ecartee de celle que les clients extrapolent
     * (vitesse envoyee) de plus de {@link #DRIFT} degres sur la piece au plus grand rapport, au plus toutes les {@link #DRIFT_TICKS} ticks. Un
     * reseau a vitesse constante n'envoie plus rien.
     */
    static final double DRIFT = 15;
    static final int DRIFT_TICKS = 20;
    /** Ecart (degres, repere de reference) entre la phase vraie et la phase extrapolee par les clients depuis le dernier envoi. */
    private double drift;
    /** Plus grand rapport effectif (en valeur absolue) du reseau. */
    private double maxRatio = 1;

    void add(KineticNode node, double ratio) {
        add(node, ratio, null);
    }

    /** 1.12.4 (lot F4) : noeud atteint en traversant des variateurs ({@code chain}, null : aucun). */
    void add(KineticNode node, double ratio, @Nullable Chain chain) {
        nodes.add(node);
        ratios.add(ratio);
        chains.add(chain);
    }

    /** 1.12.4 (lot F4) : rang du variateur dans le reseau (ajoute au premier appel). */
    int variatorIndex(KineticNode node) {
        for (int i = 0; i < variators.size(); i++) {
            if (variators.get(i) == node) {
                return i;
            }
        }
        variators.add(node);
        return variators.size() - 1;
    }

    /**
     * 1.12.4 (lot F4) : variateurs traverses par le chemin de la reference a un noeud (liste chainee partagee par les noeuds d'un meme cote) :
     * +1 = passe de l'entree a la sortie (vitesse x k), -1 = de la sortie a l'entree (vitesse / k).
     */
    static final class Chain {

        final int variator, exponent;
        @Nullable
        final Chain parent;

        private Chain(int variator, int exponent, @Nullable Chain parent) {
            this.variator = variator;
            this.exponent = exponent;
            this.parent = parent;
        }

        @Nullable
        static Chain with(@Nullable Chain chain, int variator, int exponent) {
            return exponent == 0 ? chain : new Chain(variator, exponent, chain);
        }

        /** Exposant total de chaque variateur ({@code n} variateurs au moins). */
        static int[] exponents(@Nullable Chain chain, int n) {
            int size = n;
            for (Chain c = chain; c != null; c = c.parent) {
                size = Math.max(size, c.variator + 1);
            }
            int[] e = new int[size];
            for (Chain c = chain; c != null; c = c.parent) {
                e[c.variator] += c.exponent;
            }
            return e;
        }

        /** Meme traversee nette des variateurs (deux chemins vers un noeud doivent s'accorder, sinon le montage est bloque). */
        static boolean same(@Nullable Chain a, @Nullable Chain b) {
            if (a == b) {
                return true;
            }
            int[] x = exponents(a, 0), y = exponents(b, 0);
            int n = Math.max(x.length, y.length);
            for (int i = 0; i < n; i++) {
                if ((i < x.length ? x[i] : 0) != (i < y.length ? y[i] : 0)) {
                    return false;
                }
            }
            return true;
        }

        double factor(double[] k) {
            double f = 1;
            for (Chain c = this; c != null; c = c.parent) {
                double kv = c.variator < k.length ? k[c.variator] : 1;
                f *= c.exponent > 0 ? Math.pow(kv, c.exponent) : 1 / Math.pow(kv, -c.exponent);
            }
            return f;
        }
    }

    /** Rapports effectifs (variateurs compris) dans {@link #eff}. */
    private void effective() {
        int n = nodes.size();
        if (eff.length != n) {
            eff = new double[n];
        }
        double max = 0;
        for (int i = 0; i < n; i++) {
            Chain c = i < chains.size() ? chains.get(i) : null;
            eff[i] = c == null ? ratios.get(i) : ratios.get(i) * c.factor(k);
            max = Math.max(max, Math.abs(eff[i]));
        }
        maxRatio = Math.max(1e-6, max);
    }

    /** 1.12.4 (lot F4) : rapport effectif courant d'un noeud du reseau (variateurs compris) ; 0 s'il n'y est pas. */
    public double effectiveRatio(KineticNode node) {
        for (int i = 0; i < nodes.size() && i < eff.length; i++) {
            if (nodes.get(i) == node) {
                return eff[i];
            }
        }
        return 0;
    }

    /** Banc d'essai (PhaseBench) : construction sans monde. */
    void start(double speed) {
        start(speed, 0);
    }

    void start(double speed, long now) {
        this.speed = Double.isFinite(speed) ? speed : 0;
        //1.12.4 (lot F4) : variateurs : rapport repris de la piece, rang de la piece dans le reseau
        int nv = variators.size();
        k = new double[nv];
        sentK = new double[nv];
        variatorNode = new int[nv];
        for (int v = 0; v < nv; v++) {
            KineticNode node = variators.get(v);
            double r = node.variatorRatio();
            k[v] = Double.isFinite(r) ? Math.max(VARIATOR_MIN, Math.min(1, r)) : 1;
            sentK[v] = k[v];
            variatorNode[v] = -1;
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i) == node) {
                    variatorNode[v] = i;
                    break;
                }
            }
        }
        while (chains.size() < nodes.size()) {
            chains.add(null);
        }
        effective();
        double theta = 0;
        for (int i = 0; i < nodes.size(); i++) {
            double p = nodes.get(i).lastPhase(), r = eff[i];
            if (Double.isFinite(p) && r != 0) {
                theta = p / r;
                break;
            }
        }
        phases = new double[nodes.size()];
        for (int i = 0; i < phases.length; i++) {
            phases[i] = wrap(eff[i] * theta);
        }
        carry(now);
    }

    /**
     * 0.20.46 (audit) : si tous les noeuds ont deja envoye le meme etat (meme instant, meme blocage, vitesses au rapport pres : un seul ancien
     * reseau, sans piece nouvelle), les clients extrapolent deja des angles accordes entre eux : le reseau reprend cet etat au lieu de tout
     * renvoyer au premier tick (piece cassee, embrayage ouvert, voisin change...). Sinon, envoi complet comme avant.
     */
    private void carry(long now) {
        if (nodes.isEmpty() || !(nodes.get(0) instanceof KineticTile first) || !first.hasSentState()) {
            return;
        }
        double s0 = first.sentSpeedValue();
        long t0 = first.sentTimeValue();
        boolean j0 = first.sentJamValue();
        for (int i = 1; i < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof KineticTile tile) || !tile.hasSentState() || tile.sentTimeValue() != t0 || tile.sentJamValue() != j0) {
                return;
            }
            double expected = eff[i] * s0;
            if (Math.abs(tile.sentSpeedValue() - expected) > 1e-6 * Math.max(1, Math.abs(expected))) {
                return;
            }
        }
        carried = true;
        sentSpeed = s0;
        sentJam = j0;
        sentAge = (int) -Math.min(Integer.MAX_VALUE / 2, Math.max(0, now - t0));
    }

    private static double wrap(double degrees) {
        double a = degrees % 360.0;
        return a < 0 ? a + 360.0 : a;
    }

    public int size() {
        return nodes.size();
    }

    public int sources() {
        return sources;
    }

    public int machines() {
        return machines;
    }

    /** Puissance transmise aux machines (W). */
    public double power() {
        return power;
    }

    /** Puissance maximale que les sources peuvent fournir (W, a mi-vitesse). */
    public double capacity() {
        return capacity;
    }

    /** Charge : couple demande / couple disponible a l'arret (0..1+). */
    public double loadRatio() {
        return Math.abs(drive) < 1e-6 ? (load > 0 ? 1 : 0) : load / Math.abs(drive);
    }

    /** Couple transmis rapporte a la reference (N.m). */
    public double referenceTorque() {
        return referenceTorque;
    }

    List<BlockPos> positions() {
        List<BlockPos> list = new ArrayList<>(nodes.size());
        for (KineticNode node : nodes) {
            list.add(node.nodePos());
        }
        return list;
    }

    /** Banc d'essai (PhaseBench) : tick sans monde (rien n'est envoye). */
    void tick() {
        tick(null);
    }

    void tick(@Nullable ServerLevel level) {
        effective();
        double d = 0, s = 0, l = 0, f = 0, j = 0, eta = 1, cap = 0, gl = 0, gw = 0;
        int src = 0, mac = 0, gens = 0;
        for (int i = 0; i < nodes.size(); i++) {
            KineticNode node = nodes.get(i);
            double r = eff[i], r2 = r * r;
            double tau0 = node.sourceTorque();
            if (tau0 != 0) {
                double free = Math.max(0.01, node.sourceFreeSpeed());
                d += r * tau0;
                s += r2 * Math.abs(tau0) / free;
                cap += Math.abs(tau0) * 0.25 * free * 2 * Math.PI / 60.0;
                src++;
            }
            double tauC = node.loadTorque();
            if (tauC > 0) {
                l += Math.abs(r) * tauC;
                mac++;
            }
            f += r2 * node.friction();
            double k = node.governorStiffness();
            if (k > 0 && Math.abs(r * speed) > node.governorTarget()) {
                f += r2 * k;                                 //Regulateur : freinage proportionnel a l'exces de vitesse (traite implicitement)
                gl += Math.abs(r) * k * node.governorTarget();
            }
            double rating = node.generatorRating();
            if (rating > 0) {
                gw += rating;
                gens++;
            }
            j += r2 * node.inertia();
            eta *= node.efficiency();
        }
        //1.12.4 (lot F4) : generateurs a excitation adaptee (dynamos) : amortissement total = pente des sources + frottements (puissance maximale
        //transferee : pour D - S w - L = (F + c) w, la puissance c w^2 est maximale a c = S + F), partage au prorata des puissances, borne par
        //la puissance et le couple nominaux de chaque generateur ; rien si les sources ne poussent pas
        double cg = 0;
        if (gens > 0) {
            double match = s + f, omega = Math.abs(speed);
            for (int i = 0; i < nodes.size(); i++) {
                KineticNode node = nodes.get(i);
                double rating = node.generatorRating();
                if (rating <= 0) {
                    continue;
                }
                double r = eff[i], r2 = Math.max(1e-12, r * r);
                double c = match * rating / gw * Math.max(0, Math.min(1, node.generatorExcitation()));
                if (omega > 1e-3) {
                    c = Math.min(c, rating / (omega * omega * RAD));
                    c = Math.min(c, node.generatorTorque() * Math.abs(r) / omega);
                }
                c = Math.max(0, c);
                cg += c;
                node.generatorDamping(c / r2);
            }
            f += cg;
        }
        eta = Math.max(0.3, eta);
        l /= eta;
        drive = d;
        double lt = l - gl;
        load = l;
        sources = src;
        machines = mac + gens;
        capacity = cap;
        if (jammed) {
            speed = 0;
            referenceTorque = Math.min(Math.abs(d), Math.max(l, Math.abs(d)));
        } else {
            double moving = Math.abs(speed) > REST ? Math.signum(speed) : 0;
            if (moving == 0 && Math.abs(d) <= lt) {
                speed = 0;                                   //Calage : les sources ne vainquent pas la charge
            } else {
                double sign = moving != 0 ? moving : Math.signum(d);
                double jc = Math.max(1e-3, j) * C;
                double next = (jc * speed + d - sign * lt) / (jc + s + f);
                if (moving != 0 && Math.signum(next) != moving) {
                    next = 0;                                //On s'arrete a zero : le prochain tick decide du redemarrage
                }
                speed = Math.abs(next) < REST ? 0 : next;
            }
            referenceTorque = Math.abs(speed) > REST ? l + f * Math.abs(speed) : Math.min(Math.abs(d), l);
        }
        generated = cg * speed * speed * RAD;
        power = load * eta * Math.abs(speed) * RAD + generated;
        //Charge affichee (dynamometre) : la charge electrique compte comme un couple demande
        load += cg * Math.abs(speed);
        boolean ratioMoved = !variators.isEmpty() && regulate();
        if (Double.isFinite(sentSpeed)) {
            drift += (speed - sentSpeed) * KineticTile.DEG_PER_TICK;
        }
        boolean sync = age == 0 && !carried || jammed != sentJam || speed == 0 && sentSpeed != 0
                       || Math.abs(speed - sentSpeed) > Math.max(0.02, Math.abs(sentSpeed) * 0.03) && age - sentAge >= 4
                       || speed != 0 && Math.abs(drift) * maxRatio > DRIFT && age - sentAge >= DRIFT_TICKS || ratioMoved && age - sentAge >= 4;
        if (sync) {
            sentSpeed = speed;
            sentJam = jammed;
            sentAge = age;
            drift = 0;
            System.arraycopy(k, 0, sentK, 0, k.length);
        }
        age++;
        for (int i = 0; i < nodes.size(); i++) {
            double r = eff[i];
            if (i < phases.length) {
                phases[i] = wrap(phases[i] + r * speed * KineticTile.DEG_PER_TICK);
                nodes.get(i).networkPhase(phases[i], sync);
            }
            nodes.get(i).onNetworkTick(this, r * speed, referenceTorque / Math.max(1e-6, Math.abs(r)), jammed);
        }
        if (sync && level != null) {
            KineticSync.send(level, this);                          //0.20.46 (audit) : un paquet par joueur pour tout le reseau
        }
    }

    /**
     * 1.12.4 (lot F4) : choix du rapport de chaque variateur apres le calcul de la vitesse : k = consigne / vitesse d'entree, borne a
     * [1/64, 1] ; la descente est immediate, la remontee limitee a +5 % par tick. Si la reference du reseau est du cote de la sortie (le
     * rapport ne change alors pas la vitesse de sortie), la vitesse du reseau est ramenee a la consigne (glissement de la friction).
     * Rend vrai si un rapport a bouge de plus de 2 % depuis le dernier envoi aux clients.
     */
    private boolean regulate() {
        boolean moved = false;
        for (int v = 0; v < variators.size(); v++) {
            int ni = variatorNode[v];
            if (ni < 0 || ni >= eff.length) {
                continue;
            }
            KineticNode node = variators.get(v);
            double target = Math.max(0.01, node.variatorTarget());
            double in = Math.abs(eff[ni] * speed);
            double want = in < 1e-6 ? 1 : Math.max(VARIATOR_MIN, Math.min(1, target / in));
            double kv = want < k[v] ? want : Math.min(want, k[v] * VARIATOR_RISE + 0.002);
            k[v] = kv;
            effective();
            double out = Math.abs(kv * eff[ni] * speed);
            if (out > target * 1.0001 && in > 1e-6) {
                speed *= target / out;                       //reference cote sortie : on glisse jusqu'a la consigne
            }
            node.variatorRatioSet(kv);
            moved |= Math.abs(kv - sentK[v]) > 0.02 * Math.max(0.05, sentK[v]);
        }
        return moved;
    }

    /** 1.12.4 (lot F4) : puissance prise par les generateurs electriques (W). */
    public double generated() {
        return generated;
    }

    /** 1.12.4 (lot F4) : rapport courant d'un variateur du reseau (1 s'il n'y est pas). */
    public double variatorRatio(KineticNode node) {
        for (int v = 0; v < variators.size(); v++) {
            if (variators.get(v) == node) {
                return k[v];
            }
        }
        return 1;
    }
}
