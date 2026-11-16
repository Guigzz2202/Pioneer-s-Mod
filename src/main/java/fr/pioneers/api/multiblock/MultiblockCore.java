package fr.pioneers.api.multiblock;

import fr.pioneers.core.common.block.Connections;
import fr.pioneers.tech.industry.construction.FireboxWallBlock;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.multi.BigInterior;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.IndustrialGlassBlock;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.multi.ModuleBlock;
import fr.pioneers.tech.industry.multi.NbtAnims;
import fr.pioneers.tech.industry.multi.PartBlock;
import fr.pioneers.tech.industry.multi.PortTile;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.tech.industry.multi.Structures;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : CYCLE DE VIE commun d'un multibloc a controleur (sorti de {@code ControllerTile}) : file des verifications
 * (demande a la pose / a la casse d'un bloc de la structure, sinon toutes les 40 ticks ; reprise apres chargement : la formation n'est
 * jamais sauvee comme une verite, elle est revalidee au premier tick), formation (inscription au registre des structures formees par
 * monde {@link Structures}, peinture de l'habillage et du tier, pieces tournees vers l'exterieur, machine interieure, ports rafraichis),
 * rupture (depeinture, modules de nouveau visibles, ports liberes), etat de marche peint sur les modules et les pieces, casse du controleur.
 * <p>
 * Le controleur garde ce qui lui est propre par les crochets {@link Hooks} (reserve d'energie, reservoirs, coeurs des reacteurs...).
 * Les cles de sauvegarde ne changent pas : seule la vue de la machine interieure ({@code big}) est sauvee, par le controleur.
 */
public final class MultiblockCore {

    /** Ce que le cycle de vie demande a son controleur. */
    public interface Host {

        @Nullable
        Level level();

        BlockPos pos();

        /** Habillage de la famille peint a la formation. */
        IndustryStyle style();

        /** Machine interieure dessinee (null : aucune) et sa teinte. */
        @Nullable
        BigInterior.Kind interior();

        int interiorTint(BigInterior.Kind kind);

        /**
         * 1.0.4-beta (lot 25) : cle du catalogue (structure de Guillaume : boite de la machine interieure et colonnes tournantes de
         * {@code GeneratedNbtModels}), null pour un hote sans catalogue.
         */
        @Nullable
        default String machineKey() {
            return null;
        }

        boolean removed();

        void changed();

        void markSync();
    }

    /** Crochets propres au controleur, appeles aux etapes du cycle de vie (dans l'ordre d'avant le lot 5). */
    public interface Hooks {

        /** Structure (re)formee : la structure est posee, avant l'inscription au registre et la peinture. */
        void formed(Structure.Result result);

        /** Premiere formation (apres la peinture et les ports). */
        void firstFormed();

        /** Structure defaite (apres la depeinture et les ports liberes). */
        void unformed();

        /** L'etat forme / defait a change (sauvegarde, synchro). */
        void formChanged();
    }

    /** Verification periodique (ticks) : la structure est revalidee au moins aussi souvent. */
    public static final int PERIOD = 40;

    private final Host host;
    @Nullable
    private Structure.Result structure;
    private Structure.Error error = Structure.Error.TOO_SMALL;
    @Nullable
    private BlockPos errorAt;
    private int errorDetail, check;
    private boolean checkRequested = true;
    /** Etat de marche peint sur les modules et les pieces (null : pas encore peint depuis la formation). */
    @Nullable
    private Boolean paintedActive;
    @Nullable
    private BigInterior.View big;
    /** 1.0.4-beta (lot 25) : colonnes empilables deja repeintes depuis le chargement (formation, rupture ou premiere verification). */
    private boolean stackedChecked;

    public MultiblockCore(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ etat

    public boolean formed() {
        return structure != null;
    }

    @Nullable
    public Structure.Result structure() {
        return structure;
    }

    public Structure.Error error() {
        return error;
    }

    @Nullable
    public BlockPos errorAt() {
        return errorAt;
    }

    public int errorDetail() {
        return errorDetail;
    }

    /** Machine interieure (serveur : vue de la structure formee ; null sinon). */
    @Nullable
    public BigInterior.View big() {
        return big;
    }

    /** Relecture de la sauvegarde (cle {@code big}). */
    public void loadBig(@Nullable BigInterior.View view) {
        big = view;
    }

    /** Banc des multiblocs et GameTests : structure formee posee sans monde (aller-retour de sauvegarde d'un controleur forme). */
    public void benchForm(Structure.Result result) {
        structure = result;
        error = Structure.Error.NONE;
    }

    @Nullable
    private long[] census;
    private long censusAt;

    /**
     * Onglet Structure : decompte par role ({@link StructureMatcher#census}) refait au plus toutes les secondes, seulement quand
     * l'interface le demande (serveur ; 0 chez le client).
     */
    public long census(int i, java.util.function.Supplier<long[]> compute) {
        Level level = host.level();
        if (level == null || level.isClientSide) {
            return 0;
        }
        if (census == null || level.getGameTime() - censusAt >= 20) {
            censusAt = level.getGameTime();
            census = compute.get();
        }
        return i < census.length ? census[i] : 0;
    }

    // ------------------------------------------------------------------ file des verifications

    /** Un bloc a ete pose ou casse dans les cases de la structure : la revalider au plus tot. */
    public void requestCheck() {
        checkRequested = true;
    }

    /**
     * A appeler a chaque tick serveur : vrai quand la structure doit etre revalidee ({@code event[0]} : a la demande d'un evenement, ce qui
     * repeint la structure meme inchangee).
     */
    public boolean due(boolean[] event) {
        if (checkRequested || --check <= 0) {
            event[0] = checkRequested;
            checkRequested = false;
            check = PERIOD;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ formation et rupture

    /** Applique le resultat d'une validation ({@link StructureMatcher}) : formation, mise a jour ou rupture. */
    public void apply(Structure.Result result, boolean event, Hooks hooks) {
        Level level = host.level();
        boolean was = structure != null;
        if (result.formed()) {
            boolean changed = structure == null || !structure.min().equals(result.min()) || !structure.max().equals(result.max())
                              || structure.tier() != result.tier() || structure.modules() != result.modules() || !structure.ports().equals(result.ports());
            structure = result;
            error = Structure.Error.NONE;
            errorAt = null;
            hooks.formed(result);
            Structures.put(level, host.pos(), result);
            errorDetail = 0;
            if (changed || event) {
                //Habillage de la famille (et modules ou pieces poses depuis : etat de marche)
                paintStyle(result, host.style());
                orientParts(result);
                paintStacked(true);
                if (paintedActive != null) {
                    paintActive(result, paintedActive);
                }
                formBig(result);
            }
            if (changed) {
                refreshPorts(result.ports());
                if (!was) {
                    hooks.firstFormed();
                }
            }
        } else {
            if (structure != null) {
                List<BlockPos> old = structure.ports();
                paintStyle(structure, IndustryStyle.NEUTRAL);
                paintActive(structure, false);
                paintedActive = null;
                structure = null;
                unformBig();
                paintStacked(false);
                Structures.remove(level, host.pos());
                refreshPorts(old);
                hooks.unformed();
            }
            if (big != null) {
                //Structure perdue pendant le dechargement : modules demasques
                unformBig();
            }
            if (!stackedChecked) {
                //1.0.4-beta (lot 25) : premiere verification apres le chargement, structure perdue : colonnes empilables de nouveau dessinees
                paintStacked(false);
            }
            error = result.error();
            errorAt = result.at();
            errorDetail = result.detail();
        }
        if (was != (structure != null)) {
            hooks.formChanged();
        }
    }

    /** Etat de marche du controleur : peint sur les modules et les pieces quand il change. */
    public void working(boolean on) {
        Structure.Result s = structure;
        if (s != null && (paintedActive == null || paintedActive != on)) {
            paintedActive = on;
            paintActive(s, on);
        }
    }

    /**
     * Controleur casse ou decharge : retire du registre ; casse (pas au dechargement : on ne recharge pas les chunks voisins) : structure
     * depeinte, modules visibles, ports liberes.
     */
    public void removed(boolean unloading) {
        Level level = host.level();
        Structures.remove(level, host.pos());
        Structure.Result s = structure;
        if (s != null && !unloading) {
            paintStyle(s, IndustryStyle.NEUTRAL);
            paintActive(s, false);
            unformBig();
            paintStacked(false);
            for (BlockPos p : s.ports()) {
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof PortTile port) {
                    port.refresh();
                }
            }
        }
    }

    // ------------------------------------------------------------------ peinture

    /** 1.0.4-beta (lot 25) : facade du controleur (etat du bloc), null si elle n'est pas lisible. */
    @Nullable
    private Direction front() {
        Level level = host.level();
        if (level == null || !level.isLoaded(host.pos())) {
            return null;
        }
        BlockState st = level.getBlockState(host.pos());
        return st.hasProperty(ControllerBlock.FACING) ? st.getValue(ControllerBlock.FACING) : null;
    }

    /**
     * 1.0.4-beta (lot 25) : colonnes de blocs empilables qui tournent (train de forage, pompes a colonne : {@code GeneratedNbtModels}) :
     * masquees a la formation (le controleur les dessine, tournantes en marche), de nouveau dessinees a la rupture ou a la casse ; sans
     * prevenir les voisins.
     */
    private void paintStacked(boolean hidden) {
        stackedChecked = true;
        Level level = host.level();
        String key = host.machineKey();
        Direction front = front();
        if (level == null || key == null || front == null) {
            return;
        }
        for (BlockPos p : NbtAnims.stackedCells(key, host.pos(), front)) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof StackedColumnBlock && st.getValue(StackedColumnBlock.HIDDEN) != hidden) {
                level.setBlock(p, st.setValue(StackedColumnBlock.HIDDEN, hidden), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 0.19.14 : structure formee : vue de la machine interieure, modules masques. */
    private void formBig(Structure.Result s) {
        Level level = host.level();
        BigInterior.Kind kind = host.interior();
        if (kind == null || s.members() == null || level == null) {
            return;
        }
        String key = host.machineKey();
        BigInterior.View view = key == null ? BigInterior.compute(level, kind, s.members(), host.interiorTint(kind))
              : BigInterior.compute(p -> level.isLoaded(p) ? level.getBlockState(p) : null, kind, key, host.pos(), front(), s.members(),
                    host.interiorTint(kind));
        if (big != null && (view == null || !big.min().equals(view.min()) || !big.max().equals(view.max()))) {
            paintHidden(big, false);
        }
        big = view;
        if (view != null) {
            paintHidden(view, true);
        }
        host.changed();
        host.markSync();
    }

    /** 0.19.14 : structure defaite ou controleur casse : modules de nouveau visibles. */
    private void unformBig() {
        if (big != null) {
            paintHidden(big, false);
            big = null;
            host.changed();
            host.markSync();
        }
    }

    private void paintHidden(BigInterior.View view, boolean hidden) {
        Level level = host.level();
        if (level == null) {
            return;
        }
        for (BlockPos p : BlockPos.betweenClosed(view.min(), view.max())) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ModuleBlock && state.getValue(ModuleBlock.HIDDEN) != hidden) {
                BlockState next = state.setValue(ModuleBlock.HIDDEN, hidden);
                if (!hidden) {
                    //0.20.46 (audit) : un module masque n'est plus repeint en marche/arret (paintActive) : il reprend l'etat actuel en reapparaissant
                    next = next.setValue(PartBlock.ACTIVE, shownActive());
                }
                level.setBlock(p, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Habillage des carcasses, verres et hublots de la structure (sans prevenir les voisins : aucune revalidation en chaine). */
    private void paintStyle(Structure.Result s, IndustryStyle style) {
        Level level = host.level();
        if (s.members() == null || level == null) {
            return;
        }
        //Tier peint (0 = libre) : equerres de couleur du verre, des hublots, des ports et du controleur
        int t = style == IndustryStyle.NEUTRAL ? 0 : Math.max(0, Math.min(3, s.tier())) + 1;
        //1.0.4-beta (lot 30) : ports poses a la place d'une tole : peau de tole (structure formee seulement)
        Set<BlockPos> sheet = style == IndustryStyle.NEUTRAL || s.sheetPorts().isEmpty() ? Set.of() : new HashSet<>(s.sheetPorts());
        for (BlockPos p : s.members()) {
            if (level.isLoaded(p)) {
                paintAt(level, p, style, t, sheet.contains(p));
            }
        }
        paintAt(level, host.pos(), style, t, false);
    }

    private static void paintAt(Level level, BlockPos p, IndustryStyle style, int t, boolean onSheet) {
        BlockState state = level.getBlockState(p);
        BlockState next = state;
        if (next.hasProperty(IndustryStyle.STYLE) && next.getValue(IndustryStyle.STYLE) != style) {
            next = next.setValue(IndustryStyle.STYLE, style);
        }
        if (next.hasProperty(IndustryStyle.TIER) && next.getValue(IndustryStyle.TIER) != t) {
            next = next.setValue(IndustryStyle.TIER, t);
        }
        if (next.hasProperty(Connections.ON_SHEET) && next.getValue(Connections.ON_SHEET) != onSheet) {
            next = next.setValue(Connections.ON_SHEET, onSheet);
        }
        //Textures connectees : recalculees ici aussi (mondes d'avant la 0.16.4 : six cotes non connectes au chargement)
        if (next.getBlock() instanceof CasingBlock) {
            next = Connections.connect(next, level, p, Connections.INDUSTRIAL);
        } else if (next.getBlock() instanceof IndustrialGlassBlock) {
            //0.20.28 : la vitre garde la couleur de son propre tier (1.0.6 : fixee par son bloc, plus dans l'etat)
            next = Connections.connect(next, level, p, Connections.INDUSTRIAL_GLASS);
        }
        if (next != state) {
            level.setBlock(p, next, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * 0.19.13 : chaque bloc metier se tourne vers l'exterieur de la structure (la face qui ne touche aucune case du gabarit ; dans un angle,
     * celle qui s'eloigne le plus du centre) ; les pieces de toit regardent toujours le ciel.
     */
    private void orientParts(Structure.Result s) {
        Level level = host.level();
        if (s.members() == null || level == null) {
            return;
        }
        double cx = (s.min().getX() + s.max().getX() + 1) / 2.0, cz = (s.min().getZ() + s.max().getZ() + 1) / 2.0;
        for (BlockPos p : s.members()) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof PartBlock part)) {
                continue;
            }
            Direction to = part.kind.roof() ? Direction.UP : outward(s.members(), p, cx, cz);
            if (to != null && state.getValue(PartBlock.FACING) != to) {
                level.setBlock(p, state.setValue(PartBlock.FACING, to), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Nullable
    private static Direction outward(Set<BlockPos> members, BlockPos p, double cx, double cz) {
        Direction best = null;
        double score = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (members.contains(p.relative(d))) {
                continue;
            }
            double sc = d.getStepX() * (p.getX() + 0.5 - cx) + d.getStepZ() * (p.getZ() + 0.5 - cz);
            if (best == null || sc > score) {
                best = d;
                score = sc;
            }
        }
        if (best != null) {
            return best;
        }
        return !members.contains(p.above()) ? Direction.UP : !members.contains(p.below()) ? Direction.DOWN : null;
    }

    /**
     * Etat de marche des modules et des pieces signature (modeles animes, particules). 0.20.46 (audit) : les modules masques (machine
     * interieure d'un seul tenant) sont sautes, sauf si leur lumiere en depend.
     */
    private void paintActive(Structure.Result s, boolean on) {
        Level level = host.level();
        if (s.members() == null || level == null) {
            return;
        }
        for (BlockPos p : s.members()) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            //1.0.4-beta (lot 23) : les parois de foyer de la structure s'allument (feu anime, lumiere) quand le multibloc travaille
            if (state.getBlock() instanceof FireboxWallBlock && state.getValue(FireboxWallBlock.LIT) != on) {
                level.setBlock(p, state.setValue(FireboxWallBlock.LIT, on), Block.UPDATE_CLIENTS);
                continue;
            }
            if (state.hasProperty(PartBlock.ACTIVE) && state.getValue(PartBlock.ACTIVE) != on) {
                BlockState next = state.setValue(PartBlock.ACTIVE, on);
                if (state.hasProperty(ModuleBlock.HIDDEN) && state.getValue(ModuleBlock.HIDDEN) && next.getLightEmission() == state.getLightEmission()) {
                    continue;
                }
                level.setBlock(p, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 0.20.46 (audit) : etat de marche que doit montrer un module qui reapparait (structure formee, controleur en place et en marche). */
    private boolean shownActive() {
        return structure != null && !host.removed() && Boolean.TRUE.equals(paintedActive);
    }

    private void refreshPorts(List<BlockPos> ports) {
        Level level = host.level();
        for (BlockPos p : ports) {
            if (level.getBlockEntity(p) instanceof PortTile port) {
                port.refresh();
            }
        }
    }

    // ================================================================== lot 8a : multiblocs maison

    /**
     * API Multibloc (lot 8a) : CYCLE DE VIE COMMUN des MULTIBLOCS MAISON (fin des moteurs propres : stabilisateur, simulateur, absorbeur,
     * bases, complexe de lancement, halls, port, chaudiere, haut fourneau, foreuse), meme grammaire que celui des industriels :
     * <ul>
     *   <li>FILE DES VERIFICATIONS : demande ({@link #requestCheck} : pose / casse d'un bloc dans les cases de la structure, signalee par
     *   {@link Structures} a tout hote {@link MultiblockPort}, ou par la file propre des pieces), sinon toutes les {@code period} ticks ;</li>
     *   <li>REVALIDATION AU CHARGEMENT : la formation n'est jamais sauvee comme une verite (premier tick apres le chargement : verification) ;</li>
     *   <li>FORMATION / RUPTURE : la validation propre de la maison (celle de {@link HouseShapes}, la meme que le banc) donne un
     *   {@link Survey} ; le cycle tient l'etat (forme, erreur, case fautive), inscrit les cases de la structure formee au registre des hotes
     *   ({@link Structures#putHost} : les ports y retrouvent leur hote par {@link MultiblockPort#find}) et appelle les crochets du controleur
     *   (peinture des etats {@code regime} / {@code PART} / {@code HEAT} / {@code COVERED}..., pieces rattachees ou liberees) ;</li>
     *   <li>ERREUR COMMUNE : quoi (cle de la maison), ou (case relative au controleur, {@link StructureError#where(int[])}), ATTENDU (le bloc
     *   du PLAN de la fiche a cette case : {@link HouseShapes#fault}) ; synchronisee au client ({@link #write} / {@link #read}, sous-cle
     *   {@code house} des donnees du client : aucune cle de SAUVEGARDE ne change) ;</li>
     *   <li>CASSE du controleur : registre vide, crochet de rupture (pas au dechargement : on ne relit pas les chunks voisins).</li>
     * </ul>
     */
    public static final class House {

        /** Ce que le cycle demande au controleur maison. */
        public interface Host {

            @Nullable
            Level level();

            BlockPos pos();

            /** Facade du controleur (repere du « ou » : droite, haut, profondeur). */
            Direction front();

            /** Validation propre de la maison sur le monde reel ; null : illisible (chunk voisin non charge), rien ne change. */
            @Nullable
            Survey survey();

            /** Apres chaque verification reussie ({@code first} : la structure vient de se former) : pieces rattachees, peinture. */
            default void formed(Survey survey, boolean first) {
            }

            /** Apres chaque verification echouee ({@code broken} : la structure etait formee : rupture) : pieces liberees, depeinture. */
            default void unformed(Survey survey, boolean broken) {
            }

            /** Forme ou erreur changee : sauvegarde et synchro du client. */
            void houseChanged();

            /**
             * 1.0.5-beta (lot 3) : cle du bloc ATTENDU a la case fautive quand la maison le sait mieux que son plan (serre de forme libre : la
             * vitre de sa matiere, ou rien) ; {@link #FROM_PLAN} (par defaut) : lu sur le plan de la fiche ({@link HouseShapes#fault}).
             */
            @Nullable
            default String expected(Survey survey) {
                return FROM_PLAN;
            }

            /** Valeur de {@link #expected} : l'attendu vient du plan de la fiche. */
            String FROM_PLAN = "#plan";
        }

        /** Les controleurs qui portent ce cycle (vues de l'interface v2, banc, GameTests). */
        public interface Holder {

            House house();
        }

        /**
         * Resultat d'une validation maison : forme, cle de langue de l'erreur (quoi), case fautive, cases de la structure (registre des hotes ;
         * vide : aucune), donnees propres de la maison (son resultat de validation, lu par les crochets).
         */
        public record Survey(boolean formed, @Nullable String error, @Nullable BlockPos at, Collection<BlockPos> cells, @Nullable Object data) {

            public static Survey ok(Collection<BlockPos> cells, @Nullable Object data) {
                return new Survey(true, null, null, cells, data);
            }

            public static Survey fail(@Nullable String error, @Nullable BlockPos at, @Nullable Object data) {
                return new Survey(false, error, at, List.of(), data);
            }

            /** Rupture par la casse du controleur (crochet {@code unformed}) : la tile s'en va, rien d'autre a verifier. */
            public boolean removal() {
                return this == REMOVED;
            }
        }

        /** Releve passe aux crochets quand le controleur est casse. */
        public static final Survey REMOVED = new Survey(false, "gui.pioneers.multiblock.house.unformed", null, List.of(), null);

        /** Cle de l'erreur quand la maison n'en donne aucune (bases : plan incomplet). */
        public static final String UNFORMED = "gui.pioneers.multiblock.house.unformed";

        private final Host host;
        private final int period;
        private boolean formed, requested = true, everChecked;
        private int countdown;
        @Nullable
        private String error = UNFORMED;
        @Nullable
        private BlockPos at;
        @Nullable
        private String expected;
        /** Taille de la structure formee (L x l x H, bornes des cases) ; 0 : inconnue. */
        private int sizeX, sizeY, sizeZ;
        private boolean registered;

        public House(Host host, int period) {
            this.host = host;
            this.period = Math.max(1, period);
        }

        // -------------------------------------------------------------- etat

        public boolean formed() {
            return formed;
        }

        /** Cle de l'erreur (quoi), null si formee. */
        @Nullable
        public String error() {
            return formed ? null : error;
        }

        /** Case fautive dans le monde, ou null. */
        @Nullable
        public BlockPos at() {
            return formed ? null : at;
        }

        /** Cle du bloc attendu a la case fautive (plan de la fiche), ou null. */
        @Nullable
        public String expected() {
            return formed ? null : expected;
        }

        public int[] size() {
            return new int[]{sizeX, sizeY, sizeZ};
        }

        /** Erreur au format des ecrans d'avant ({@code cle|x y z}, vide si formee) : messages au joueur. */
        public String legacyError() {
            if (formed || error == null) {
                return "";
            }
            return at == null ? error : error + "|" + at.getX() + " " + at.getY() + " " + at.getZ();
        }

        /** OU : case fautive relative au controleur {droite, haut, profondeur}, ou null. */
        @Nullable
        public int[] where() {
            BlockPos a = at();
            return a == null ? null : StructureMatcher.relative(host.pos(), host.front(), a);
        }

        // -------------------------------------------------------------- file des verifications

        /** Un bloc a ete pose ou casse dans la structure (ou une piece s'est chargee) : la revalider au prochain tick. */
        public void requestCheck() {
            requested = true;
        }

        /** Chargement de la tile : la formation sera revalidee au premier tick (jamais crue sur parole). */
        public void load() {
            requested = true;
        }

        /** A appeler a chaque tick serveur : verifie la structure si une demande est en attente ou si la periode est ecoulee. */
        public void tick() {
            Level level = host.level();
            if (level == null || level.isClientSide) {
                return;
            }
            if (!everChecked && countdown == 0) {
                //Etale les verifications periodiques des controleurs d'un meme monde
                countdown = 1 + Math.floorMod(host.pos().asLong(), period);
            }
            if (requested || --countdown <= 0) {
                requested = false;
                countdown = period;
                checkNow();
            }
        }

        /** Verification immediate (pose d'un noyau, interaction...) ; vrai si la structure est formee. */
        public boolean checkNow() {
            Level level = host.level();
            if (level == null || level.isClientSide) {
                return formed;
            }
            Survey s = host.survey();
            if (s != null) {
                apply(s);
            }
            return formed;
        }

        /** Applique une validation : formation, mise a jour ou rupture (crochets du controleur), erreur commune, registre des hotes. */
        public void apply(Survey s) {
            Level level = host.level();
            everChecked = true;
            boolean was = formed;
            String oldError = error;
            BlockPos oldAt = at;
            formed = s.formed();
            if (formed) {
                error = null;
                at = null;
                expected = null;
                measure(s.cells());
                if (level != null && !s.cells().isEmpty()) {
                    Set<BlockPos> cells = new java.util.HashSet<>(s.cells());
                    cells.add(host.pos());
                    Structures.putHost(level, host.pos(), cells);
                    registered = true;
                }
                host.formed(s, !was);
            } else {
                error = s.error() == null ? UNFORMED : s.error();
                at = s.at() == null ? null : s.at().immutable();
                if (level != null && registered) {
                    Structures.removeHost(level, host.pos());
                    registered = false;
                }
                String own = host.expected(s);
                if (!Host.FROM_PLAN.equals(own)) {
                    //1.0.5-beta (lot 3) : la maison connait elle-meme le bloc attendu (serre de forme libre)
                    expected = own;
                } else if (!java.util.Objects.equals(oldAt, at) || !java.util.Objects.equals(oldError, error) || was || expected == null) {
                    //Bloc ATTENDU (et case fautive si la maison n'en donne pas) : lu sur le plan de la fiche
                    HouseShapes.Fault f = level == null ? null : HouseShapes.fault(Structure.View.of(level), host.pos(), level.getBlockState(host.pos()), at);
                    if (f != null) {
                        if (at == null) {
                            at = f.at();
                        }
                        expected = f.expected();
                    } else {
                        expected = null;
                    }
                }
                sizeX = sizeY = sizeZ = 0;
                host.unformed(s, was);
            }
            if (was != formed || !java.util.Objects.equals(oldError, error) || !java.util.Objects.equals(oldAt, at)) {
                host.houseChanged();
            }
        }

        private void measure(Collection<BlockPos> cells) {
            if (cells.isEmpty()) {
                sizeX = sizeY = sizeZ = 0;
                return;
            }
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (BlockPos p : cells) {
                x0 = Math.min(x0, p.getX());
                y0 = Math.min(y0, p.getY());
                z0 = Math.min(z0, p.getZ());
                x1 = Math.max(x1, p.getX());
                y1 = Math.max(y1, p.getY());
                z1 = Math.max(z1, p.getZ());
            }
            sizeX = x1 - x0 + 1;
            sizeY = y1 - y0 + 1;
            sizeZ = z1 - z0 + 1;
        }

        /** Controleur casse ou decharge : registre des hotes vide ; casse (pas au dechargement) : crochet de rupture. */
        public void removed(boolean unloading) {
            Level level = host.level();
            if (level == null || level.isClientSide) {
                return;
            }
            Structures.removeHost(level, host.pos());
            registered = false;
            if (formed && !unloading) {
                formed = false;
                host.unformed(REMOVED, true);
            }
        }

        /** Banc et GameTests : etat forme pose sans monde. */
        public void benchForm(boolean on) {
            formed = on;
            everChecked = true;
            error = on ? null : UNFORMED;
        }

        // -------------------------------------------------------------- client

        /** Donnees du client (sous-cle {@code house}) : forme, erreur, case fautive, attendu, taille. */
        public void write(net.minecraft.nbt.CompoundTag tag) {
            net.minecraft.nbt.CompoundTag h = new net.minecraft.nbt.CompoundTag();
            h.putBoolean("formed", formed);
            if (!formed && error != null) {
                h.putString("error", error);
            }
            if (!formed && at != null) {
                h.putLong("at", at.asLong());
            }
            if (!formed && expected != null) {
                h.putString("expected", expected);
            }
            h.putIntArray("size", new int[]{sizeX, sizeY, sizeZ});
            tag.put("house", h);
        }

        public void read(net.minecraft.nbt.CompoundTag tag) {
            if (!tag.contains("house")) {
                return;
            }
            net.minecraft.nbt.CompoundTag h = tag.getCompound("house");
            formed = h.getBoolean("formed");
            error = h.contains("error") ? h.getString("error") : formed ? null : UNFORMED;
            at = h.contains("at") ? BlockPos.of(h.getLong("at")) : null;
            expected = h.contains("expected") ? h.getString("expected") : null;
            int[] s = h.getIntArray("size");
            sizeX = s.length > 0 ? s[0] : 0;
            sizeY = s.length > 1 ? s[1] : 0;
            sizeZ = s.length > 2 ? s[2] : 0;
        }
    }
}
