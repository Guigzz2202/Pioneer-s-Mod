package fr.pioneers.api.machine.unit;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.machine.MachineTile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * API Machine (lot 3) : BASE DES UNITES portees par une {@link MachineTile} (composition, conception § 0.4) : la tile prete ses operations
 * ({@link MachineTile.Access}) ; l'unite tourne, sauve ses cles (celles d'avant sa migration), publie ses valeurs d'interface nommees et ce
 * que le client doit savoir. Unites : {@link GeneratorUnit} (production d'energie) ;
 * {@link ProcessUnit} (lot 2, raccrochee a cette base au lot 4) ; {@link StorageUnit} (buffers, portes par une tile hors
 * {@code MachineTile}) a la meme forme.
 * <p>
 * Lot 7 : l'hote est un {@link UnitHost} : {@link MachineTile.Access} pour une machine electrique, l'adaptateur d'une tile de l'age
 * mecanique ou du bronze ({@code kinetics.KineticMachineTile}, {@code bronze.BronzeTile}) qui prete aussi son energie motrice
 * ({@link PowerSource}).
 */
public abstract class MachineUnit {

    protected final UnitHost machine;

    protected MachineUnit(UnitHost machine) {
        this.machine = machine;
    }

    /** Un tick de travail (serveur). */
    public abstract void tick();

    /** Valeurs d'interface (apres les 4 communes de la tile). */
    public void declareValues(UiValues.Builder values) {
    }

    public void save(CompoundTag tag) {
    }

    public void load(CompoundTag tag) {
    }

    public void writeClient(CompoundTag tag) {
    }

    public void readClient(CompoundTag tag) {
    }

    protected Level level() {
        return machine.level();
    }

    protected BlockPos pos() {
        return machine.pos();
    }

    /** 1.0.2-beta : reglage a valeur recu de l'interface (curseur) ; la valeur vient du client : a borner. */
    public void setting(int setting, int value) {
    }

    /** Lot 7 : donnees propres du client changees (renvoi isole d'une tile cinetique : objet travaille, avancement). */
    public boolean clientDirty() {
        return false;
    }
}
