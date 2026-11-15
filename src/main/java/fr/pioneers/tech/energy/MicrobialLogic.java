package fr.pioneers.tech.energy;

import fr.pioneers.api.machine.unit.RenewableUnit;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * 0.20.45 : pile microbienne (energie bio). Des bacteries digerent la matiere organique entre deux electrodes et rendent du courant, sans
 * combustion. Elle mange tout ce qui se composte (graines, feuilles, recoltes, plats...) et les residus du vivant du tag
 * {@code pioneers:organic_matter} (copeaux, bagasse, tourteau, microalgues...). Un objet vaut sa chance de compostage en unites
 * (les residus du tag : 0,65), chaque unite rendant {@link EnergyKind#MICROBIAL_UNIT} FE ; la pile produit au plus sa puissance tant
 * qu'elle a a manger. L'objet entre par toutes les faces.
 * <p>
 * Interface : 8 matiere en cours (FE restants), 9 objets digeres, 10 energie de l'objet en cours (FE).
 * <p>
 * API Machine (lot 3) : logique ({@link fr.pioneers.api.machine.unit.RenewableUnit}), code repris de l'ancienne {@code MicrobialCellTile}.
 */
public class MicrobialLogic extends RenewableUnit {

    private final ItemSlot input;
    private long fuel, lastValue;
    private int eaten;

    public MicrobialLogic(MachineTile.Access machine, int tier) {
        super(machine, tier, EnergyKind.MICROBIAL);
        input = machine.slot(new ItemSlot(() -> 64, s -> MicrobialCellTile.units(s) > 0, (s, a) -> true, (s, a) -> !a.external(), machine::changed,
              MicrobialCellTile.SLOT_X, MicrobialCellTile.SLOT_Y));
        for (RelativeSide side : RelativeSide.values()) {
            machine.sides().set(SideType.ITEMS, side, SideMode.INPUT);
        }
    }

    @Override
    protected double survey(ServerLevel level) {
        return fuel > 0 || !input.isEmpty() ? 1 : 0;
    }

    @Override
    protected long produce(ServerLevel level) {
        long want = peak();
        if (fuel < want && !input.isEmpty()) {
            ItemStack one = input.extract(1, Action.EXECUTE, Automation.INTERNAL);
            if (!one.isEmpty()) {
                lastValue = Math.round(MicrobialCellTile.units(one) * EnergyKind.MICROBIAL_UNIT[tier]);
                fuel += lastValue;
                eaten++;
                machine.changed();
            }
        }
        long made = Math.min(want, fuel);
        fuel -= made;
        if (fuel <= 0 && input.isEmpty()) {
            factor = 0;
        }
        return made;
    }

    @Override
    protected long detail(int index) {
        return switch (index) {
            case 0 -> fuel;
            case 1 -> eaten;
            case 2 -> lastValue;
            default -> 0;
        };
    }

    @Override
    public void save(CompoundTag tag) {
        super.save(tag);
        tag.putLong("fuel", fuel);
        tag.putInt("eaten", eaten);
        tag.putLong("last", lastValue);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fuel = tag.getLong("fuel");
        eaten = tag.getInt("eaten");
        lastValue = tag.getLong("last");
    }
}
