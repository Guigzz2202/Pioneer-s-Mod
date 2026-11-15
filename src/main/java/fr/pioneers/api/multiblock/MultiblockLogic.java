package fr.pioneers.api.multiblock;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.nuclear.ReactorView;
import java.util.function.LongPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 6) : COEUR PROPRE d'un multibloc du moteur commun (reacteur a fission {@code FissionCore}, fusion {@code FusionCore},
 * calcul {@code ComputeCore}, plate-forme offshore {@code OffshoreRisers}), DECLARE PAR LA FICHE ({@link MultiblockSpec#process()} =
 * {@code reactor}, {@code fusion}, {@code compute}, {@code offshore}) et fabrique par {@code CoreLogics.create} : le controleur generique
 * n'aiguille plus sur le genre de la machine ({@code if (machine.kind == REACTOR...)}), il appelle ces crochets dans l'ordre d'avant le
 * lot 6. Tout a une valeur par defaut neutre (« le coeur ne s'en occupe pas » : le controleur fait comme pour un multibloc a procede).
 * <p>
 * Les memes cles de sauvegarde qu'avant ({@link #saveKey()} : {@code reactor}, {@code fusion}, {@code compute}, {@code offshore}), les
 * memes indices de valeurs d'interface ({@link #value(int)}, nommes par {@link MultiblockValues}), les memes vues client.
 */
public interface MultiblockLogic {

    /** Statuts rendus par {@link #work} (memes nombres que {@code ControllerTile.IDLE / WORKING / OVERHEAT}). */
    int IDLE = 1, WORKING = 2, OVERHEAT = 6;

    // ------------------------------------------------------------------ sauvegarde

    /** Cle du coeur dans la sauvegarde du controleur (inchangee). */
    String saveKey();

    CompoundTag save();

    void load(CompoundTag tag);

    // ------------------------------------------------------------------ structure

    /** Apres chaque validation reussie. */
    void onStructure(Structure.Result result, Level level);

    /** La structure vient de se defaire. */
    default void onUnformed(Level level) {
    }

    /** Synchroniser le client quand la formation change (rendus du coeur). */
    default boolean syncOnFormChange() {
        return false;
    }

    // ------------------------------------------------------------------ fonctionnement

    /**
     * Le coeur mene-t-il le fonctionnement (statut, travail) au lieu du procede du multibloc ? Faux : le procede commun tourne
     * ({@code MultiProcess}), le coeur n'ajoute que {@link #structureTick}.
     */
    default boolean drivesWork() {
        return true;
    }

    /** Structure formee : travail du tick ; rend le statut ({@link #IDLE}, {@link #WORKING}, {@link #OVERHEAT}). */
    default int work(LongPredicate use, boolean enabled) {
        return IDLE;
    }

    /** Structure formee, avant le travail (plate-forme : descente des tubes). */
    default void structureTick(Level level) {
    }

    /** Structure defaite (reacteur en marche qui continue, plasma qui se perd). */
    default void unformedTick() {
    }

    /** Le coeur gere sa chaleur lui-meme : pas d'arret ni de degat de surchauffe commun. */
    default boolean ownsHeat() {
        return false;
    }

    /** La machine chauffe en travaillant (le refroidissement passe d'abord). */
    default boolean heats() {
        return true;
    }

    /** Capacite thermique imposee au corps (formee ou non) ; negatif : celle de la structure. */
    default double heatCapacity(boolean formed) {
        return -1;
    }

    /** Reserve d'energie minimale du controleur forme (FE). */
    default long reserve() {
        return 0;
    }

    /** Signal du comparateur, ou -1 (regle commune). */
    default int comparator() {
        return -1;
    }

    // ------------------------------------------------------------------ entrees

    /** Le coeur choisit les objets qui entrent (sinon : les recettes). */
    default boolean ownsItems() {
        return false;
    }

    default boolean acceptsItem(ItemStack stack) {
        return false;
    }

    /** Ingredients-objets distincts (cases gardees par les ports), ou -1 (regle commune). */
    default int itemKinds() {
        return -1;
    }

    default boolean ownsFluids() {
        return false;
    }

    default boolean acceptsFluid(FluidStack stack) {
        return false;
    }

    default boolean ownsGases() {
        return false;
    }

    /** Gaz du reservoir d'entree {@code tank} ({@code level} : monde du controleur, null hors du jeu). */
    default boolean acceptsGas(int tank, GasStack stack, @Nullable Level level) {
        return false;
    }

    /** Ports d'objets en entree : gestionnaire propre (tubes de la plate-forme), ou null. */
    @Nullable
    default IItemHandler inputItems() {
        return null;
    }

    // ------------------------------------------------------------------ interface

    /** Valeur d'interface a l'indice d'avant le lot 5 ({@code machineValue}), 0 si le coeur ne la tient pas. */
    default long value(int index) {
        return 0;
    }

    /** Commande de l'interface ({@code MachineGui.CUSTOM} retire) ; vrai si le coeur l'a prise. */
    default boolean action(ServerPlayer player, int action) {
        return false;
    }

    /** Vue des rendus du coeur (reacteurs, fusion), cote serveur, structure formee. */
    @Nullable
    default ReactorView reactorView() {
        return null;
    }

    /** Ajoute la vue client des rendus a la synchro du controleur. */
    default void writeClient(CompoundTag tag, boolean formed) {
    }

    // ------------------------------------------------------------------ cycle de vie de la tile

    default void chunkUnloaded(Level level) {
    }

    /** Controleur retire ({@code unloading} : dechargement du chunk, sinon casse). */
    default void removed(Level level, boolean unloading) {
    }
}
