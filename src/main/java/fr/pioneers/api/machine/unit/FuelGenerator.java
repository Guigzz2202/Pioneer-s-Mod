package fr.pioneers.api.machine.unit;

import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.industry.GeneratorOutput;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.Tiers;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 3) : UNITE DE GENERATEUR A COMBUSTIBLE ({@link GeneratorUnit}), sortie de {@link IndustryProcess} (generateur a
 * combustion des ateliers) : le combustible est pris au debut d'une combustion (la recette, ses entrees et ses sous-produits restent au
 * procede : {@link Fuel}) et l'energie produite pendant toute la duree, poussee par les faces de sortie. Chiffres : SOURCE UNIQUE
 * {@link GeneratorOutput} (production x4 par tier, combustible brule 4 fois plus vite : meme energie par combustible ; MAJ simplification :
 * combustion x2 ; sortie a la mesure du rendement de la machine). Cles de sauvegarde d'avant : {@code burn}, {@code burnPower}.
 */
public class FuelGenerator extends GeneratorUnit {

    /** Ce que le procede fournit : la recette de combustible prise (entrees consommees, sous-produits rendus), ou null. */
    public interface Fuel {

        @Nullable
        IndustrialRecipe take();

        boolean inputsEmpty();
    }

    private final IndustryMachine kind;
    private final Fuel fuel;
    /** Ticks de combustion restants, FE/t produits, duree de la combustion en cours. */
    private int burnLeft, duration = 1, progress;
    private long burnPower;
    private int status = ProcessUnit.IDLE;
    /** 1.0.4-beta (lot 5) : production perdue reserve pleine (interface : « Surplus perdu »). */
    public final Surplus surplus = new Surplus();

    public FuelGenerator(MachineTile.Access machine, IndustryMachine kind, int tier, Fuel fuel) {
        super(machine, tier);
        this.kind = kind;
        this.fuel = fuel;
    }

    @Override
    public void tick() {
        var energy = machine.energy();
        boolean working = false;
        //1.0.4-beta (lot 5) : plus de « reserve pleine » qui arrete le feu : le combustible brule toujours, le surplus est perdu (Surplus)
        if (burnLeft <= 0 && machine.canWork()) {
            IndustrialRecipe r = fuel.take();
            if (r != null) {
                burnLeft = GeneratorOutput.duration(r.time(), tier);
                duration = burnLeft;
                burnPower = GeneratorOutput.perTick(r, tier, false);
            }
        }
        long made = 0;
        if (burnLeft > 0) {
            burnLeft--;
            made = energy.insert(Math.min(burnPower, energy.getNeeded()), false);
            surplus.note(machine.level().getGameTime(), burnPower, made);
            working = true;
            status = ProcessUnit.WORKING;
            progress = duration - burnLeft;
        } else {
            status = fuel.inputsEmpty() ? ProcessUnit.IDLE : ProcessUnit.NO_RECIPE;
            progress = 0;
        }
        produced.add(made);
        //MAJ simplification : sortie a la mesure du rendement de la machine (combustion x2), sinon l'excedent serait perdu
        machine.pushEnergy(Math.round(Tiers.power(tier) * 4 * GeneratorOutput.machineYield(kind)));
        machine.markWorking(working);
        //1.0.5-beta (lot 29) : sauvegarde demandee seulement si quelque chose de sauve a change (avant : a chaque tick, meme a l'arret :
        //setChanged = chunk a sauver + mise a jour des comparateurs voisins, 20 fois par seconde par generateur inactif)
        long stored = energy.getStored();
        if (working || burnLeft != savedBurn || stored != savedEnergy) {
            savedBurn = burnLeft;
            savedEnergy = stored;
            machine.changed();
        }
    }

    /** 1.0.5-beta (lot 29) : etat sauve au dernier {@code changed()} (combustion restante, energie stockee). */
    private int savedBurn = Integer.MIN_VALUE;
    private long savedEnergy = Long.MIN_VALUE;

    /** 1.0.4-beta (lot 5) : une partie de la production est perdue (reserve pleine). */
    public boolean wasting() {
        return machine.level() != null && surplus.active(machine.level().getGameTime());
    }

    /** Production en cours (FE/t), pour l'interface. */
    public long production() {
        return burnLeft > 0 ? burnPower : 0;
    }

    public int status() {
        return status;
    }

    /** Avancement de la combustion (ticks brules), 0 a l'arret. */
    public int progress() {
        return progress;
    }

    public int duration() {
        return duration;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("burn", burnLeft);
        tag.putLong("burnPower", burnPower);
    }

    @Override
    public void load(CompoundTag tag) {
        burnLeft = tag.getInt("burn");
        burnPower = tag.getLong("burnPower");
    }
}
