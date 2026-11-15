package fr.pioneers.industry.multi;

import fr.pioneers.industry.construction.StackedColumnBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 0.19.14 : interieur d'un seul tenant. Quand la structure est formee, les modules disparaissent ({@link ModuleBlock#HIDDEN}) et le
 * controleur dessine A LEUR PLACE une seule grande machine animee a la taille de l'interieur (rendu client
 * {@code client/industry/big/RenderBigMachine}). Ce fichier est la partie commune : quelle machine pour quel multibloc, et la vue envoyee au
 * client (boite interieure, axe long, positions des cages).
 * <p>
 * Montee et descente en regime progressives (jamais de tout ou rien) : {@link Kind#up} et {@link Kind#down} en ticks ; sans operation
 * (ACTIVE faux depuis 1 s), la machine ralentit jusqu'a l'arret (veille).
 */
public final class BigInterior {

    /** Familles de machines interieures (une par archetype valide sur apercu). */
    public enum Kind {
        /** Rotor a etages (turbines). */
        TURBINE(160, 240),
        /** Train de laminoir : cages quarto aux portiques, table a rouleaux, bande qui avance de l'entree a la sortie. */
        ROLLING(60, 80),
        /** Serpentins de tubes d'algues, panneaux LED, bulles. */
        PHOTOBIO(40, 80),
        /** Concasseur a deux cylindres dentes, tremie, minerai qui tombe. */
        CRUSHER(50, 70),
        /** Meme concasseur, copeaux de bois au lieu du minerai. */
        CHIPPER(50, 70),
        /** 0.19.15 (lot 2) : four de fusion : creuset de briques, bain, bobine d'induction, electrodes et arc. */
        FURNACE(80, 160),
        /** Four tubulaire : serpentins rougeoyants, bruleurs a la sole. */
        HEATER(60, 120),
        /** Four a cuve : couches de minerai et de coke qui descendent, tuyeres, fonte et laitier. */
        SHAFT(100, 200),
        /** Four rotatif : tambour sur galets, couronne dentee, charge qui rougit vers le bruleur. */
        KILN(120, 160),
        /** Broyeur a boulets : tambour, boulets en cascade, trommel de sortie. */
        MILL(80, 100),
        /** Cuve agitee : moteur, arbre et mobiles, serpentin, chicanes, bulles, liquide teinte. */
        TANK(40, 60),
        /** Colonne a plateaux : plateaux perfores, deversoirs, liquide qui mousse (lourd en bas, leger en haut). */
        COLUMN(60, 100),
        /** Lit catalytique : lits de granules sur grilles, distributeurs qui gouttent. */
        PACKED(60, 100),
        /** Cellule d'electrolyse : anodes et cathodes suspendues aux barres, bain, bulles. */
        CELL(40, 60),
        /** 0.19.17 (lot 3) : foreuse : tete d'entrainement qui descend le long des rails, tige qui tourne, table rotative, deblais. */
        DRILL(80, 120),
        /** Pompe de puits : moteur, arbre, colonne de production ou le fluide monte, tete de puits a volants rouges. */
        WELL(60, 100),
        /** Pompe a balancier : cables de la tete de cheval, traverse et tige polie qui montent et descendent. */
        JACK(80, 120),
        /** Cascade de centrifugeuses : rangees de rotors a chapeau tournant, collecteurs, voyants d'etat. */
        CASCADE(160, 240),
        /** Centrifugeuse : bol a douves qui tourne, frettes, cone de sortie, moteur. */
        CENTRIFUGE(100, 160),
        /** Chaudiere a tubes de fumee : virole rivetee, foyer ondule et sa flamme, faisceau de tubes, ebullition. */
        BOILER(80, 160),
        /** Machine a vis : moteur, reducteur, vis visible sous la tremie, fourreau a colliers chauffants, filiere, extrudat. */
        SCREW(40, 60),
        /** 0.19.18 (lot 4) : presse a portique : coulisseau sur colonnes, verin, piece qui entre, est ecrasee et ressort. */
        PRESS(30, 50),
        /** Presse hydraulique : deux verins, flexibles, lopin rougeoyant forge. */
        HPRESS(30, 50),
        /** Presse plieuse : poincon et matrice en V, tole qui se plie. */
        BRAKE(30, 50),
        /** Presse a granules : filiere annulaire et galets qui tournent, granules qui tombent dans le bac. */
        PELLET(40, 60),
        /** Chaines d'usinage : bande a transfert pas a pas, postes suspendus aux portiques (tour, robots d'assemblage...). */
        LATHE(30, 40),
        ASSEMBLY(30, 40),
        WELDER(30, 40),
        PICKPLACE(30, 40),
        SAWLINE(40, 60),
        MOLDER(30, 40),
        SORTER(30, 40),
        CASTER(30, 40),
        BOTTLER(30, 40),
        /** 0.19.19 (lot 5) : bassins. Crible vibrant : toiles inclinees sur ressorts, balourds, minerai qui saute et descend. */
        SIEVE(30, 50),
        /** Table a secousses : plateau a riffles qui va et vient, film d'eau, bandes de mineraux. */
        TABLE(30, 50),
        /** Cellules de flottation : rotors, pulpe, tapis de mousse qui deborde dans la goulotte. */
        FLOTATION(40, 60),
        /** Bac de trempe : pont roulant, panier de pieces rougeoyantes plonge dans l'eau, vapeur. */
        QUENCH(30, 50),
        /** Galvanisation : bain de flux puis bain de zinc fondu, poutrelles plongees, fumees. */
        GALVANIZE(30, 50),
        /** Gravure acide : trois bains (acide, rincage, acide), panier de plaques. */
        ETCH(30, 50),
        /** Decanteur : pont racleur qui va et vient, boues, flocs, deversoir crenele. */
        CLARIFIER(60, 80),
        /** Melangeurs-decanteurs : emulsion brassee puis deux phases separees. */
        SETTLER(40, 60),
        /** Ferme aquacole : cages a filets, poissons qui tournent, nourrisseur. */
        FISHFARM(40, 60),
        /** Piscine de combustible use : alveoles, assemblages, lueur bleue, pont de manutention. */
        FUELPOOL(60, 80),
        /** 0.19.20 (lot 6) : salles blanches : scies a wafers (lame, table, arrosage), photolithographie (platine pas a pas, eclairs UV),
         *  fonderie (outils, transport aerien de boites de plaquettes) ; robot mobile dans l'allee. */
        WAFERSAW(40, 60),
        LITHO(40, 60),
        FAB(40, 60),
        /** Separation isotopique par laser : chambre a vide, vapeur, faisceaux en zigzag. */
        LASERSEP(60, 80),
        /** Ferme hydroponique : etageres, bacs, rampes LED roses, plants qui poussent. */
        HYDRO(40, 60),
        /** Plantation : arbres qui grandissent sous les rampes d'eclairage. */
        TREES(40, 60),
        /** Tirage de cristaux : creuset, bain de silicium, lingot qui monte en tournant. */
        PULLER(80, 120),
        /** Retraitement : colonnes pulsees, gouttes organiques qui montent. */
        PULSED(60, 80),
        /** Four de diffusion : tubes de quartz, spires rougeoyantes, nacelle de plaquettes qui entre et sort. */
        DIFFUSION(80, 120),
        /** Separateur magnetique : tambours, particules magnetiques entrainees. */
        MAGSEP(40, 60),
        /** Dessaleur electrostatique : grilles, decharges, gouttes d'eau qui tombent. */
        DESALTER(40, 60),
        /** Separateur a membranes : batterie de tubes, un tube ouvert sur sa membrane spiralee. */
        MEMBRANE(40, 60),
        /** Autoclave : compartiments agites, porte a fermeture rapide, vapeur. */
        AUTOCLAVE(60, 80),
        /** 0.19.21 (lot 7) : cyclone : cuve translucide qui epouse le ventre, poussieres en spirale, tourbillon de gaz propre. */
        CYCLONE(40, 60),
        /**
         * Tour de refroidissement : garnissage (les modules), rampes et separateur, pluie jusqu'au bassin entre les pieds, brume qui monte
         * dans la coque ; 0.20.26 : le panache au-dessus est fait de particules ({@code client/particle/SteamPlumeParticle}). Cages : une
         * seule valeur, la hauteur de la coque (px) au-dessus du bas du garnissage.
         */
        COOLTOWER(120, 200),
        /** Sphere d'ergols : liquide cryogenique qui monte et descend, evaporation, chicanes. */
        CRYOSPHERE(60, 80),
        /** Colonne du melangeur a cuves jumelles : helice de melange, filets des deux fluides. */
        MIXCOLUMN(30, 50),
        /** Echangeur a plaques : paquet de plaques chaudes / froides, tirants, ecoulements. */
        PLATEHX(40, 60),
        /** Cellule chaude : murs de beton, verre au plomb, telemanipulateurs sur l'echantillon. */
        HOTCELL(30, 50),
        /** Forge : foyer et braises, marteau-pilon qui frappe le lopin, etincelles. */
        FORGE(40, 80),
        /** Groupe froid : evaporateur qui givre, compresseurs, ventilateurs de la batterie. */
        CHILLER(60, 80),
        /** 0.19.23 : batterie de fours a coke : fours entre piedroits, defourneuse, enfourneuse, wagon d'extinction. */
        COKEBATTERY(100, 160),
        /** 0.20.11 : chantier naval : navire en construction dans la cale seche, portique, soudure. */
        DRYDOCK(80, 120),
        /** 0.20.34 : compresseur gravitationnel : pylones a gravitons, coeur qui pulse, anneaux gyroscopiques, matiere aspiree en spirale. */
        GRAVITY(100, 160),
        /** Synthetiseur : berceau, projecteurs, planete miniature qui tourne, atmosphere, paquets de donnees, satellites. */
        SYNTH(80, 120),
        /**
         * 1.9.2 (retours de Guillaume) : Ateliers ferroviaires (halle de depot) : voie sur fosse, vrais vehicules qui entrent, sont
         * assembles et ressortent, pont roulant, portail a rideau. Cages : hauteur de l'egout du toit et du faite (px au-dessus du bas
         * des modules), 1 si la porte est au bout de l'axe (sinon au debut).
         */
        DEPOT(40, 60);

        /** Duree de la montee en regime et de l'arret (ticks). */
        public final int up, down;

        Kind(int up, int down) {
            this.up = up;
            this.down = down;
        }
    }

    /**
     * Machine interieure par multibloc (cle du catalogue) ; les autres gardent leurs modules visibles. 1.0.4-beta (lot 14, dette des lots 11
     * a 13) : plus aucune cle de machine absorbee ou supprimee par la fusion (ni des machines fondues en 1.0.3-beta, lot 5) : seulement des
     * cles du catalogue ({@code tools/check_fusion.py}, registres de forme et de taille).
     * <p>
     * 1.0.4-beta (lot 25) : seulement les multiblocs SANS structure de Guillaume (gabarit d'avant, boite des cases interieures) ; ceux a NBT
     * prennent leur machine (famille ET boite) dans {@link GeneratedNbtModels} ({@code T:industry/nbt_models.py MODELS}, transfert des
     * modeles d'avant ; modeles enfermes retires : {@code RETIRED}).
     */
    private static final Map<String, Kind> BY_MACHINE = Map.ofEntries(
          Map.entry("rail_works", Kind.DEPOT), Map.entry("shipyard", Kind.DRYDOCK),
          Map.entry("cooling_tower", Kind.COOLTOWER), Map.entry("propellant_plant", Kind.CRYOSPHERE),
          Map.entry("gravity_compressor", Kind.GRAVITY), Map.entry("synthesizer", Kind.SYNTH));

    /**
     * 0.19.15 : teinte du liquide (cuves, colonnes, cellules) ou de la charge par multibloc (RGB) ; sinon celle de la famille. 1.0.4-beta (lot
     * 25) : teintes des machines dont le modele interieur est retire enlevees.
     */
    private static final Map<String, Integer> TINT = Map.ofEntries(
          Map.entry("electrolyzer", 0x5A9AB0),
          Map.entry("casting_machine", 0x8C949E), Map.entry("bottler", 0xC08A2E),
          Map.entry("ore_separator", 0x8C7A64),
          Map.entry("chemical_bath", 0xB4BCC0),
          Map.entry("aquafarm", 0x3C8CA0),
          Map.entry("spent_fuel_pool", 0x3C7CC8),
          Map.entry("cooling_tower", 0x5A8CB4), Map.entry("propellant_plant", 0xB8DCF8),
          Map.entry("mixer", 0x8CA0B4), Map.entry("gravity_compressor", 0xFF8A1F), Map.entry("synthesizer", 0x6C7CFF));

    /** Teinte (RGB) de la machine interieure d'un multibloc. */
    public static int tint(String machineKey, Kind kind) {
        Integer t = TINT.get(machineKey);
        if (t != null) {
            return t;
        }
        return switch (kind) {
            case TANK -> 0x6EA046;
            case COLUMN -> 0xC48428;
            case CELL -> 0x5A9AB0;
            case WELL -> 0x9A6A2A;
            case BOILER -> 0x8CB4C8;
            case SCREW -> 0x5A82B4;
            default -> 0xFFFFFF;
        };
    }

    private BigInterior() {
    }

    /** Machine interieure d'un multibloc : celle de sa structure de Guillaume (lot 25), sinon celle du gabarit d'avant ; null : aucune. */
    @Nullable
    public static Kind kind(String machineKey) {
        if (GeneratedNbtShapes.of(machineKey) != null) {
            GeneratedNbtModels.Anim a = GeneratedNbtModels.of(machineKey);
            return a == null || a.model() == null ? null : a.model().kind();
        }
        return BY_MACHINE.get(machineKey);
    }

    /**
     * Vue client : boite des modules (coordonnees du monde, bornes incluses), axe long (vrai = le long de X), cages (centres en px le long
     * de l'axe, depuis le bord bas de la boite).
     */
    public record View(Kind kind, BlockPos min, BlockPos max, boolean alongX, int[] stands, int[] profile, int tint) {

        /** Largeur (px) en travers de l'axe. */
        public int width() {
            return 16 * (alongX ? max.getZ() - min.getZ() + 1 : max.getX() - min.getX() + 1);
        }

        public int height() {
            return 16 * (max.getY() - min.getY() + 1);
        }

        /** Longueur (px) le long de l'axe. */
        public int length() {
            return 16 * (alongX ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1);
        }

        /**
         * Boite de rendu de la machine interieure (rendu du controleur ; deplace de ControllerTile au lot 25 de la 1.0.4-beta) : tour de
         * refroidissement du bassin (sous les pieds) au col (0.20.26), atelier ferroviaire jusqu'au faite (1.9.2), sinon l'interieur (la
         * tremie et les cages depassent un peu, 0.19.14).
         */
        public AABB renderBox() {
            if (kind == Kind.COOLTOWER && stands.length > 0) {
                return new AABB(min.getX(), min.getY() - IndustryShape.HYPER_LEGS - 1, min.getZ(), max.getX() + 1, min.getY() + stands[0] / 16.0 + 1,
                      max.getZ() + 1).inflate(2, 0, 2);
            }
            if (kind == Kind.DEPOT && stands.length > 1) {
                return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, min.getY() + stands[1] / 16.0 + 1, max.getZ() + 1)
                      .inflate(2, 0, 2);
            }
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1).inflate(2);
        }

        public CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putByte("k", (byte) kind.ordinal());
            t.putLong("a", min.asLong());
            t.putLong("b", max.asLong());
            t.putBoolean("x", alongX);
            t.putIntArray("s", stands);
            t.putIntArray("p", profile);
            t.putInt("c", tint);
            return t;
        }

        @Nullable
        public static View read(CompoundTag t) {
            int k = t.getByte("k");
            if (k < 0 || k >= Kind.values().length) {
                return null;
            }
            return new View(Kind.values()[k], BlockPos.of(t.getLong("a")), BlockPos.of(t.getLong("b")), t.getBoolean("x"), t.getIntArray("s"),
                  t.getIntArray("p"), t.contains("c") ? t.getInt("c") : 0xFFFFFF);
        }

        /** Signature de la geometrie (le maillage n'est refait que si elle change). */
        public long signature() {
            long h = kind.ordinal();
            h = h * 31 + min.asLong();
            h = h * 31 + max.asLong();
            h = h * 31 + (alongX ? 1 : 0);
            for (int s : stands) {
                h = h * 31 + s;
            }
            for (int p : profile) {
                h = h * 31 + p;
            }
            return h * 31 + tint;
        }
    }

    /** Calcule la vue d'une structure formee (null si aucun module). */
    @Nullable
    public static View compute(Level level, Kind kind, Set<BlockPos> members) {
        return compute(level, kind, members, 0xFFFFFF);
    }

    /**
     * 1.0.4-beta (lot 25) : vue de la machine interieure d'un multibloc forme : structure de Guillaume (NBT) : la BOITE de la table
     * ({@link NbtAnims#view}, transfert des modeles d'avant) ; sinon le gabarit d'avant (boite des cases interieures).
     */
    @Nullable
    public static View compute(Function<BlockPos, BlockState> states, Kind kind, String machineKey, BlockPos controller, @Nullable Direction front,
          Set<BlockPos> members, int tint) {
        GeneratedNbtShapes.Shape shape = GeneratedNbtShapes.of(machineKey);
        if (shape != null) {
            GeneratedNbtModels.Anim a = GeneratedNbtModels.of(machineKey);
            if (a == null || a.model() == null || front == null) {
                return null;
            }
            return NbtAnims.view(controller, front, shape, a.model(), tint);
        }
        return compute(states, kind, members, tint);
    }

    /**
     * 0.19.15 : avec la teinte et le profil (par rangee en y, du bas vers le haut : 10 x largeur en cases de la section + 1 si la rangee est
     * pleine, coins compris ; 0 = rangee vide). Les machines rondes s'y inscrivent (domes, ventres, futs).
     */
    @Nullable
    public static View compute(Level level, Kind kind, Set<BlockPos> members, int tint) {
        return compute(p -> level.isLoaded(p) ? level.getBlockState(p) : null, kind, members, tint);
    }

    /** Meme calcul, cases lues par {@code states} (null : case non chargee). */
    @Nullable
    public static View compute(Function<BlockPos, BlockState> states, Kind kind, Set<BlockPos> members, int tint) {
        return compute(p -> {
            BlockState st = states.apply(p);
            return st != null && inside(st);
        }, p -> {
            BlockState st = states.apply(p);
            return st != null && st.getBlock() instanceof StructureFrameBlock;
        }, kind, members, tint);
    }

    /**
     * Meme calcul d'apres deux questions sur les cases : interieure (la machine y est dessinee) et cadre de structure (portiques) ; le banc
     * {@code tools/bigprev/EnvelopeBench} (1.0.4-beta, lot 25) l'appelle hors du jeu sur les gabarits.
     */
    @Nullable
    public static View compute(Predicate<BlockPos> insideAt, Predicate<BlockPos> frameAt, Kind kind, Set<BlockPos> members, int tint) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        List<BlockPos> modules = new ArrayList<>();
        for (BlockPos p : members) {
            if (insideAt.test(p)) {
                modules.add(p);
                x0 = Math.min(x0, p.getX());
                y0 = Math.min(y0, p.getY());
                z0 = Math.min(z0, p.getZ());
                x1 = Math.max(x1, p.getX());
                y1 = Math.max(y1, p.getY());
                z1 = Math.max(z1, p.getZ());
            }
        }
        if (modules.isEmpty()) {
            return null;
        }
        int dx = x1 - x0, dz = z1 - z0;
        boolean alongX;
        if (dx != dz) {
            alongX = dx > dz;
        } else {
            //Egalite : l'axe d'un fut est celui dont la section est ronde (coins de la section vides)
            alongX = hasCorner(modules, x0, y0, true) && !hasCorner(modules, z0, y0, false);
        }
        //Cages : cases de l'axe ou un cadre de structure longe l'interieur (portiques des chaines)
        List<Integer> stands = new ArrayList<>();
        int a0 = alongX ? x0 : z0, a1 = alongX ? x1 : z1, c0 = alongX ? z0 : x0, c1 = alongX ? z1 : x1;
        for (int a = a0; a <= a1; a++) {
            BlockPos lo = alongX ? new BlockPos(a, y0, c0 - 1) : new BlockPos(c0 - 1, y0, a);
            BlockPos hi = alongX ? new BlockPos(a, y0, c1 + 1) : new BlockPos(c1 + 1, y0, a);
            if (frameAt.test(lo) || frameAt.test(hi)) {
                stands.add((a - a0) * 16 + 8);
            }
        }
        if (kind == Kind.COOLTOWER) {
            //0.20.26 : tour de refroidissement : pas de cage ; la seule valeur est la hauteur de la coque au-dessus du bas du garnissage (px),
            //pour la brume interieure et le panache
            int top = y1;
            for (BlockPos p : members) {
                top = Math.max(top, p.getY());
            }
            stands.clear();
            stands.add((top + 1 - y0) * 16);
        }
        if (kind == Kind.DEPOT) {
            //1.9.2 : atelier ferroviaire : egout (haut du mur qui longe les modules), faite (haut de la structure), cote de la porte
            int eave = y1, top = y1, mid = (c0 + c1) / 2, mida = (a0 + a1) / 2;
            boolean doorAtStart = true;
            for (BlockPos p : members) {
                int pa = alongX ? p.getX() : p.getZ(), pc = alongX ? p.getZ() : p.getX();
                top = Math.max(top, p.getY());
                if (pc == c0 - 1 && pa == mida) {
                    eave = Math.max(eave, p.getY());
                }
                if (pc == mid && p.getY() == y0 + 1 && pa == a0 - 1) {
                    doorAtStart = false;
                }
            }
            stands.clear();
            stands.add((eave + 1 - y0) * 16);
            stands.add((top + 1 - y0) * 16);
            stands.add(doorAtStart ? 0 : 1);
        }
        int[] profile = new int[y1 - y0 + 1];
        for (int y = y0; y <= y1; y++) {
            int ax0 = Integer.MAX_VALUE, ax1 = Integer.MIN_VALUE, az0 = Integer.MAX_VALUE, az1 = Integer.MIN_VALUE, n = 0;
            for (BlockPos p : modules) {
                if (p.getY() == y) {
                    ax0 = Math.min(ax0, p.getX());
                    ax1 = Math.max(ax1, p.getX());
                    az0 = Math.min(az0, p.getZ());
                    az1 = Math.max(az1, p.getZ());
                    n++;
                }
            }
            if (n > 0) {
                int wx = ax1 - ax0 + 1, wz = az1 - az0 + 1;
                profile[y - y0] = Math.min(wx, wz) * 10 + (n == wx * wz ? 1 : 0);
            }
        }
        return new View(kind, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1), alongX, stands.stream().mapToInt(Integer::intValue).toArray(),
              profile, tint);
    }

    /**
     * 1.0.4-beta (lot 23) : case INTERIEURE de la structure (la machine y est dessinee) : air, eau d'un bassin, module (baie de serveurs) ou
     * bloc empilable ; avant : les seuls modules (retires).
     */
    static boolean inside(BlockState state) {
        return state.isAir() || state.getBlock() instanceof ModuleBlock || state.getBlock() instanceof StackedColumnBlock
               || state.getBlock() instanceof LiquidBlock;
    }

    /** Vrai si une case de la rangee basse du bord (x = edge, ou z = edge) existe ; vide = section ronde perpendiculaire a ce bord. */
    private static boolean hasCorner(List<BlockPos> modules, int edge, int y0, boolean xEdge) {
        for (BlockPos p : modules) {
            if (p.getY() == y0 && (xEdge ? p.getX() == edge : p.getZ() == edge)) {
                return true;
            }
        }
        return false;
    }
}
