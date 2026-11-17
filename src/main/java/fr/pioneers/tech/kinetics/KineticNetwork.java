package fr.pioneers.tech.kinetics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Reseau de force rotative : ensemble de noeuds relies par des arbres, des engrenages et des cordes. Chaque noeud i tourne a r_i x Omega
 * (Omega = vitesse de reference, celle du premier noeud). 1.0.6 (revision de la mecanique, demande de Guillaume du 17/11/2026 : « rendre plus
 * simple et comprehensible le fonctionnement ») : SOURCES REGULEES.
 * <ul>
 *     <li>chaque source a un couple de demarrage tau0 et un REGIME (vitesse de regime, {@link KineticNode#sourceFreeSpeed}) ; sa puissance
 *     nominale vaut tau0 x regime / 4 ; le reseau tourne au regime de sa source la plus lente (repere de reference) ;</li>
 *     <li>CHARGE = couple demande (machines, frottements) / couple nominal des sources : jusqu'a 100 % la vitesse reste au regime ; au-dela la
 *     puissance reste constante et la vitesse baisse d'autant (regime / charge) ; le reseau cale quand la demande depasse le couple de
 *     demarrage (vers 400 %) ;</li>
 *     <li>les dynamos ne prennent que le SURPLUS au regime (jamais de ralentissement), rien si leur reserve est pleine.</li>
 * </ul>
 * Regime transitoire : J dOmega/dt = sources(Omega) - sgn(Omega) L - F Omega - freins(Omega), Euler implicite resolu exactement (dichotomie).
 * Tout est rapporte a l'arbre de reference : la puissance se conserve d'un rapport a l'autre (aux rendements pres).
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
    private double referenceTorque, power, capacity, generated, nominal, demand, capacityTorque;
    private int sources, machines;
    /**
     * 1.0.6 (revision de la mecanique, Guillaume du 17/11/2026 : « rendre plus simple et comprehensible le fonctionnement ») : part de la
     * puissance nominale d'une source : P = couple de demarrage x regime x {@value #NOMINAL_SHARE} (le meme maximum que l'ancienne droite
     * couple / vitesse a mi-vitesse) ; son couple nominal au regime vaut donc le quart de son couple de demarrage.
     */
    public static final double NOMINAL_SHARE = 0.25;
    /** Bande du regulateur des sources : leur couple tombe a zero entre le regime x (1 - {@value #BAND}) et le regime (a vide : le regime). */
    public static final double BAND = 0.02;
    // Sources et regulateurs du tick (tableaux gardes d'un tick a l'autre : aucune allocation dans le tick)
    private int[] srcNode = new int[0];
    private double[] srcDir = new double[0], srcStall = new double[0], srcV = new double[0], srcP = new double[0];
    private double[] govK = new double[0], govT = new double[0];
    private int nSrc, nGov;
    private double sigma;
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

    /** Puissance nominale des sources (W, au regime du reseau). */
    public double capacity() {
        return capacity;
    }

    /** 1.0.6 : regime du reseau (tr/min, repere de reference) : celui de sa source la plus lente ; 0 sans source. */
    public double nominalSpeed() {
        return nominal;
    }

    /**
     * 1.0.6 (Guillaume : « des fois j'atteins 100 % de charge alors que rien n'est connecte ») : CHARGE = couple demande par les machines (et
     * les frottements) / couple nominal des sources au regime. Jusqu'a 100 % le reseau tourne au regime ; au-dela il ralentit d'autant
     * (vitesse = regime / charge) ; il cale vers 400 %. Les dynamos n'y comptent pas (elles ne prennent que le surplus). NaN : aucune source
     * ne pousse (rien a rapporter : la fiche dit « aucune source »).
     */
    public double loadRatio() {
        return capacityTorque > 1e-9 ? demand / capacityTorque : Double.NaN;
    }

    /** Couple rendu par la source q a la vitesse u (repere de reference, dans son sens) : courbe des sources regulees. */
    private double curve(int q, double u) {
        double stall = srcStall[q];
        if (u <= 0) {
            return stall;
        }
        double v = srcDir[q] == sigma && nominal > 0 ? nominal : srcV[q];
        return band(stall, srcP[q], v, u);
    }

    /** Courbe d'une source : couple de demarrage, puissance constante, puis bande du regulateur juste SOUS le regime (a vide : le regime). */
    private static double band(double stall, double p, double v, double u) {
        double knee = v * (1 - BAND);
        if (u <= knee) {
            return Math.min(stall, p / u);
        }
        return u >= v ? 0 : Math.min(stall, p / knee) * (v - u) / (v - knee);
    }

    /**
     * 1.0.6 : couple d'une source REGULEE (son repere) : {@code stall} a l'arret, puissance constante {@code stall x regime x NOMINAL_SHARE}
     * jusqu'a la bande du regulateur ({@link #BAND}), puis plus rien au regime. Pour les sources qui comptent elles-memes leur puissance.
     */
    public static double sourceCurve(double stall, double regime, double u) {
        stall = Math.abs(stall);
        regime = Math.max(0.01, regime);
        if (u <= 0) {
            return stall;
        }
        return band(stall, stall * regime * NOMINAL_SHARE, regime, u);
    }

    /** Somme signee des couples des sources a la vitesse de reference w. */
    private double sourcesAt(double w) {
        double t = 0;
        for (int q = 0; q < nSrc; q++) {
            t += srcDir[q] * curve(q, srcDir[q] * w);
        }
        return t;
    }

    /** Freinage des regulateurs a boules a la vitesse |w| (repere de reference). */
    private double brake(double w) {
        double t = 0;
        for (int g = 0; g < nGov; g++) {
            t += govK[g] * Math.max(0, w - govT[g]);
        }
        return t;
    }

    /** Residu de l'equation implicite du tick (croissant en w). */
    private double residual(double w, double jc, double m, double lt, double f) {
        return jc * (w - speed) - sourcesAt(w) + m * lt + f * w + Math.signum(w) * brake(Math.abs(w));
    }

    /**
     * Vitesse du tick suivant (Euler implicite, exact sur la courbe des sources) : J (w - w0) / dt = sources(w) - m x charge - F w - freins(w),
     * resolue par dichotomie (le residu croit avec w) : stable quelle que soit la raideur du regulateur des sources.
     */
    private double solve(double jc, double m, double lt, double f) {
        double span = 1;
        for (int q = 0; q < nSrc; q++) {
            span += srcStall[q];
        }
        span = (span + lt) / jc + 1;
        double lo = speed - span, hi = speed + span;
        for (int it = 0; it < 40 && residual(hi, jc, m, lt, f) < 0; it++) {
            hi += span;
            span *= 2;
        }
        for (int it = 0; it < 40 && residual(lo, jc, m, lt, f) > 0; it++) {
            lo -= span;
            span *= 2;
        }
        for (int it = 0; it < 60; it++) {
            double mid = 0.5 * (lo + hi);
            if (residual(mid, jc, m, lt, f) > 0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return 0.5 * (lo + hi);
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
        int n = nodes.size();
        if (srcNode.length < n) {
            srcNode = new int[n];
            srcDir = new double[n];
            srcStall = new double[n];
            srcV = new double[n];
            srcP = new double[n];
            govK = new double[n];
            govT = new double[n];
        }
        double push = 0, l = 0, f = 0, j = 0, eta = 1, gw = 0;
        int ns = 0, ng = 0, mac = 0, gens = 0;
        for (int i = 0; i < n; i++) {
            KineticNode node = nodes.get(i);
            double r = eff[i], r2 = r * r, ar = Math.abs(r);
            double tau0 = node.sourceTorque();
            if (tau0 != 0 && ar > 1e-9) {
                double free = Math.max(0.01, node.sourceFreeSpeed());
                srcNode[ns] = i;
                srcDir[ns] = Math.signum(r * tau0);
                srcStall[ns] = ar * Math.abs(tau0);
                srcV[ns] = free / ar;
                srcP[ns] = Math.abs(tau0) * free * NOMINAL_SHARE;
                push += r * tau0;
                ns++;
            } else {
                node.sourceDelivered(0);
            }
            double tauC = node.loadTorque();
            if (tauC > 0) {
                l += Math.abs(r) * tauC;
                mac++;
            }
            f += r2 * node.friction();
            double gk = node.governorStiffness();
            if (gk > 0 && ar > 1e-9) {
                govK[ng] = r2 * gk;
                govT[ng] = node.governorTarget() / ar;
                ng++;
            }
            if (node.generatorRating() > 0) {
                gw += node.generatorRating();
                gens++;
            }
            j += r2 * node.inertia();
            eta *= node.efficiency();
        }
        nSrc = ns;
        nGov = ng;
        eta = Math.max(0.3, eta);
        l /= eta;
        //1.0.6 (revision de la mecanique) : sources REGULEES : le reseau tourne a la vitesse de regime de sa source la plus lente (repere de
        //reference) ; chaque source y donne sa puissance nominale ; surcharge : puissance constante (vitesse = regime / charge), calage au-dela
        //du couple de demarrage (4 fois le couple nominal d'une source a son regime)
        sigma = push != 0 ? Math.signum(push) : Math.signum(speed);
        double vNet = 0;
        for (int q = 0; q < ns; q++) {
            if (srcDir[q] == sigma) {
                vNet = vNet <= 0 ? srcV[q] : Math.min(vNet, srcV[q]);
            }
        }
        nominal = vNet;
        double capTorque = 0;
        if (vNet > 0) {
            for (int q = 0; q < ns; q++) {
                if (srcDir[q] == sigma) {
                    capTorque += Math.min(srcStall[q], srcP[q] / vNet);
                }
            }
        }
        //Dynamos : elles ne prennent que le SURPLUS des sources au regime (les machines passent d'abord, la vitesse ne baisse pas), au
        //prorata de leur puissance, bornees par leur puissance et leur couple ; rien si leur reserve est pleine (puissance 0)
        double omega = Math.abs(speed), tg = 0;
        if (gens > 0) {
            double surplus = Math.max(0, capTorque - l - f * vNet - brake(vNet));
            for (int i = 0; i < n; i++) {
                KineticNode node = nodes.get(i);
                double rating = node.generatorRating();
                if (rating <= 0) {
                    continue;
                }
                double ar = Math.max(1e-9, Math.abs(eff[i]));
                double t = surplus * rating / gw * Math.max(0, Math.min(1, node.generatorExcitation()));
                t = Math.min(t, rating / (Math.max(omega, 1) * RAD));
                t = Math.min(t, node.generatorTorque() * ar);
                t = Math.max(0, t);
                tg += t;
                node.generatorDamping(omega > REST ? t / (ar * ar * omega) : 0);
            }
        }
        double lt = l + tg;
        machines = mac + gens;
        sources = ns;
        capacity = capTorque * vNet * RAD;
        demand = l + f * vNet;
        capacityTorque = capTorque;
        if (jammed) {
            speed = 0;
            referenceTorque = Math.min(Math.abs(push), Math.max(lt, Math.abs(push)));
        } else {
            double moving = Math.abs(speed) > REST ? Math.signum(speed) : 0;
            double start = sourcesAt(0);
            if (moving == 0 && Math.abs(start) <= lt) {
                speed = 0;                                   //Calage : les sources ne vainquent pas la charge
            } else {
                double m = moving != 0 ? moving : Math.signum(start);
                double next = solve(Math.max(1e-3, j) * C, m, lt, f);
                if (moving != 0 && Math.signum(next) != moving) {
                    next = 0;                                //On s'arrete a zero : le prochain tick decide du redemarrage
                }
                speed = Math.abs(next) < REST ? 0 : next;
            }
            referenceTorque = Math.abs(speed) > REST ? lt + f * Math.abs(speed) + brake(Math.abs(speed)) : Math.min(Math.abs(start), lt);
        }
        //Couple rendu par chaque source (son repere, dans son sens) : lu par les sources qui paient leur energie (vapeur, electricite, chaleur)
        for (int q = 0; q < ns; q++) {
            int i = srcNode[q];
            double u = srcDir[q] * speed;
            nodes.get(i).sourceDelivered(curve(q, u) / Math.abs(eff[i]));
        }
        generated = tg * Math.abs(speed) * RAD;
        power = l * eta * Math.abs(speed) * RAD + generated;
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
