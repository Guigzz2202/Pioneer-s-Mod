package fr.pioneers.api.machine.unit;

import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 7) : CE QU'UNE TILE PRETE A SES UNITES, quelle que soit sa base. Une machine electrique ({@link MachineTile}) la prete par
 * {@link MachineTile.Access} (energie FE, faces, batterie...) ; une machine de l'age mecanique ou du bronze la prete par son adaptateur
 * ({@code kinetics.KineticMachineTile} : la tile reste un noeud du reseau de rotation ; {@code bronze.BronzeTile} : machines a vapeur, au feu)
 * et donne son energie motrice par {@link #power()} ({@link PowerSource} : rotation, vapeur, feu). Les operations propres au FE ont une
 * valeur neutre par defaut : une unite d'une machine sans FE ne les appelle pas.
 */
public interface UnitHost {

    @Nullable
    Level level();

    BlockPos pos();

    /** Etat du bloc de la machine. */
    default BlockState state() {
        Level level = level();
        return level == null ? null : level.getBlockState(pos());
    }

    /** Le contenu a change (sauvegarde du chunk). */
    void changed();

    /** Envoi au client au prochain passage (etat visible, rendu, interface). */
    void markSync();

    /** Envoi immediat au client (choix fait dans l'interface). */
    void syncNow();

    /** Etat actif (travail) de la machine. */
    default void markWorking(boolean working) {
    }

    /** Declare une case (dans l'ordre d'affichage et de sauvegarde) ; renvoie la case. */
    ItemSlot slot(ItemSlot slot);

    /** Declare un reservoir de fluide ; renvoie le reservoir. */
    default FluidTank fluidTank(FluidTank tank) {
        return tank;
    }

    /** Declare un reservoir de gaz ; renvoie le reservoir. */
    default BasicGasTank gasTank(BasicGasTank tank) {
        return tank;
    }

    /** La commande redstone permet-elle le travail ? */
    default boolean canWork() {
        return true;
    }

    /** Energie motrice de la machine (lot 7) : rotation, vapeur, feu ; null : FE (energie de {@link MachineTile}). */
    @Nullable
    default PowerSource power() {
        return null;
    }

    // ------------------------------------------------------------------
    // FE : seulement les machines electriques (MachineTile.Access)
    // ------------------------------------------------------------------

    /** La tile electrique (null : machine sans FE). */
    @Nullable
    default MachineTile tile() {
        return null;
    }

    @Nullable
    default EnergyBuffer energy() {
        return null;
    }

    @Nullable
    default SideConfig sides() {
        return null;
    }

    default ItemSlot batterySlot(int x, int y) {
        throw new UnsupportedOperationException("pas de batterie : machine sans FE");
    }

    default boolean use(long amount) {
        return true;
    }

    default void discharge(ItemSlot battery, long maxPerTick) {
    }

    default void pushEnergy(long budget) {
    }

    /** Lot 8c : recharge l'objet d'une case (generateurs a case de recharge). */
    default void charge(ItemSlot slot, long maxPerTick) {
    }

    /** Lot 8c : tier de la machine (0 = MKI ; 0 pour une machine sans tier). */
    default int machineTier() {
        return 0;
    }
}
