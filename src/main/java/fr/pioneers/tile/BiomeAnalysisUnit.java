package fr.pioneers.tile;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.data.BasicDataTank;
import fr.pioneers.data.ItemBiomeDataCell;
import fr.pioneers.data.PiDataTypes;
import fr.pioneers.registries.PiItems;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 8c) : LOGIQUE DE L'ANALYSEUR DE BIOME (code d'avant de {@link TileEntityBiomeAnalyzer}, deplace) : 9 cellules et un
 * reservoir de donnees sous chacune (declares par l'unite dans le meme ordre qu'avant), production de chaque cellule enregistree dans un
 * biome d'astre. Rien de plus a sauver (cases et reservoirs : cles de la machine).
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : cellules en production, cout d'une cellule, puis type et contenu de
 * chaque reservoir.
 */
public final class BiomeAnalysisUnit extends MachineUnit {

    private static final int CELL_SLOTS = TileEntityBiomeAnalyzer.CELL_SLOTS;
    private static final long DATA_PER_TICK = TileEntityBiomeAnalyzer.DATA_PER_TICK, ENERGY_PER_CELL_FE = TileEntityBiomeAnalyzer.ENERGY_PER_CELL_FE;

    private final List<ItemSlot> cellSlots = new ArrayList<>();
    final List<BasicDataTank> tanks = new ArrayList<>();
    private int producing;

    BiomeAnalysisUnit(TileEntityBiomeAnalyzer tile, MachineTile.Access machine) {
        super(machine);
        for (int i = 0; i < CELL_SLOTS; i++) {
            cellSlots.add(machine.slot(ItemSlot.of(TileEntityBiomeAnalyzer::isUsableCell, machine::changed, TileEntityBiomeAnalyzer.SLOTS_X + i * 18,
                  TileEntityBiomeAnalyzer.SLOTS_Y).ghost(PiItems.BIOME_DATA_CELL.get())));
        }
        for (int i = 0; i < CELL_SLOTS; i++) {
            int index = i;
            tanks.add(tile.declareData(BasicDataTank.of(TileEntityBiomeAnalyzer.TANK_CAPACITY, t -> t == cellType(index),
                  (t, automation) -> automation != Automation.EXTERNAL, (t, automation) -> true, () -> {
                      machine.changed();
                      machine.markSync();
                  })));
        }
    }

    /** Type de donnees produit par la cellule d'une case (null si vide). */
    @Nullable
    DataType cellType(int index) {
        if (index >= cellSlots.size()) {
            return null;
        }
        String biome = ItemBiomeDataCell.getBiome(cellSlots.get(index).getStack());
        return biome == null ? null : PiDataTypes.biome(biome);
    }

    @Override
    public void tick() {
        int active = 0;
        if (machine.canWork()) {
            for (int i = 0; i < CELL_SLOTS; i++) {
                DataType type = cellType(i);
                BasicDataTank tank = tanks.get(i);
                if (type == null || tank.getNeeded() < DATA_PER_TICK || !tank.isEmpty() && tank.getType() != type) {
                    continue;
                }
                if (!machine.use(ENERGY_PER_CELL_FE)) {
                    break;
                }
                tank.insert(DataStack.of(type, DATA_PER_TICK), Action.EXECUTE, Automation.INTERNAL);
                active++;
            }
        }
        producing = active;
        machine.markWorking(active > 0);
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.CELLS, () -> producing).add(UiValues.CELL_COST, () -> ENERGY_PER_CELL_FE);
        for (int i = 0; i < CELL_SLOTS; i++) {
            BasicDataTank tank = tanks.get(i);
            values.add(UiValues.dataType(i), () -> DataMachineTile.typeId(tank.isEmpty() ? null : tank.getType())).add(UiValues.dataStored(i), tank::getStored);
        }
    }
}
