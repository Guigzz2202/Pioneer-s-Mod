package fr.pioneers.api.machine.unit;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.tech.energy.EnergyKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * API Machine (lot 3) : UNITE DES GENERATEURS RENOUVELABLES ({@link EnergyKind}, code de l'ancienne {@code GeneratorTile}). Toutes les
 * {@link #SCAN} ticks, la logique propre mesure son environnement ({@link #survey}) et en tire un facteur 0..1 (ou plus) de la production
 * maximale ; chaque tick, elle produit {@link #produce} FE et pousse la reserve dans les voisins (energie en sortie par toutes les faces).
 * Logiques : {@code energy.SolarLogic}, {@code WindLogic}, {@code HydroLogic}, {@code GeothermalLogic}, {@code MicrobialLogic}.
 * <p>
 * Valeurs nommees (indices d'avant le lot 3) : 4 genre, 5 production FE/t x100, 6 production maximale, 7 facteur x1000, 8..11 mesures
 * propres ({@link #detail}). Sauvegarde : {@code factor} ; client : {@code factor} (rendus : rotor...).
 */
public abstract class RenewableUnit extends GeneratorUnit {

    public static final int SCAN = 20;

    public final EnergyKind kind;
    /** Facteur de la production maximale (dernier releve). */
    protected double factor;
    /** Facteur vu par le client (rendus : rotor...). */
    protected float clientFactor;
    /** 1.0.4-beta (lot 5) : production perdue reserve pleine. */
    public final Surplus surplus = new Surplus();

    protected RenewableUnit(MachineTile.Access machine, int tier, EnergyKind kind) {
        super(machine, tier);
        this.kind = kind;
    }

    public long peak() {
        return kind.peak[tier];
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if ((server.getGameTime() + pos().asLong()) % SCAN == 0) {
            double before = factor;
            factor = Math.max(0, survey(server));
            if (Math.abs(before - factor) > 0.02) {
                machine.markSync();
            }
        }
        //1.0.4-beta (lot 5) : produit aussi reserve pleine (la pile microbienne mange toujours) ; ce qui ne rentre pas est perdu (Surplus)
        long made = machine.canWork() ? produce(server) : 0;
        long kept = made > 0 ? machine.energy().insert(made, false) : 0;
        surplus.note(server.getGameTime(), made, kept);
        produced.add(made);                                      //la production du lieu (le surplus perdu compris)
        share(server);
        machine.pushEnergy(machine.energy().getStored());
        machine.markWorking(made > 0);
    }

    /** Mesure l'environnement : facteur de la production maximale. */
    protected abstract double survey(ServerLevel level);

    /** Production de ce tick (FE) ; par defaut, la production maximale x facteur. */
    protected long produce(ServerLevel level) {
        return Math.round(peak() * factor);
    }

    /** Partage avec les generateurs voisins (panneaux solaires) ; rien par defaut. */
    protected void share(ServerLevel level) {
    }

    /** Mesures propres a l'interface (0..3). */
    protected abstract long detail(int index);

    public float clientFactor() {
        return clientFactor;
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.GEN_KIND, kind::ordinal).add(UiValues.OUTPUT, () -> Math.round(produced.get() * 100)).add(UiValues.PEAK, this::peak)
              .add(UiValues.FACTOR, () -> Math.round(factor * 1000));
        for (int i = 0; i < 4; i++) {
            int n = i;
            values.add(UiValues.detail(n), () -> detail(n));
        }
        //1.0.4-beta (lot 5) : apres les valeurs d'avant (leurs indices ne bougent pas)
        values.add(UiValues.SURPLUS, () -> level() != null && surplus.active(level().getGameTime()) ? 1 : 0);
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putFloat("factor", (float) factor);
    }

    @Override
    public void readClient(CompoundTag tag) {
        clientFactor = tag.getFloat("factor");
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putDouble("factor", factor);
    }

    @Override
    public void load(CompoundTag tag) {
        factor = tag.getDouble("factor");
        clientFactor = (float) factor;
    }

    protected static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
