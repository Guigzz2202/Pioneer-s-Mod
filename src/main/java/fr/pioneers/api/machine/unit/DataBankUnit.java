package fr.pioneers.api.machine.unit;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.resource.ResourceStore;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.data.BasicDataTank;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ForgeRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 4) : LOGIQUE DE LA BANQUE DE DONNEES (fiche BUFFER, ressource DATA de la couche ressource), sortie de
 * {@code TileEntityDataBank} qui ne fait plus que la porter (memes type de tile, cles et methodes pour le rendu, le bloc, la table de
 * butin). Un stockage {@link ResourceStore.Data} : capacite de donnees face par face (vue bornee au reglage de la face, cachee jusqu'au
 * prochain changement des faces), interne dans les deux sens ; ejection par les faces en SORTIE quand l'ejection des donnees est allumee
 * (debit du tier par face et par tick) ; sauvegarde {@code dataTank} (format d'avant, copie dans l'objet par la table de butin) ; client :
 * {@code dataTank} et {@code dataConfig}.
 * <p>
 * Valeurs d'interface (apres les 4 communes, memes indices qu'avant) : contenu, contenance, type (id du registre, -1 vide), debit du tier.
 */
public final class DataBankUnit extends MachineUnit {

    /** Type de donnees contenu (id du registre des types, -1 : vide) ; debit d'ejection du tier (Mo/t par face). */
    public static final UiValues.Key DATA_TYPE = UiValues.Key.of("data_type"), RATE = UiValues.Key.of("tier_rate");

    public final ResourceStore.Data store;
    public final long rate;
    private final Map<Direction, LazyOptional<IDataHandler>> caps = new EnumMap<>(Direction.class);
    @Nullable
    private LazyOptional<IDataHandler> internal;

    public DataBankUnit(MachineTile.Access machine, BasicDataTank tank, long rate) {
        super(machine);
        this.store = new ResourceStore.Data(tank);
        this.rate = rate;
    }

    public BasicDataTank tank() {
        return store.tank;
    }

    @Override
    public void tick() {
        if (level() == null || !machine.sides().isEject(SideType.DATA)) {
            return;
        }
        store.eject(level(), pos(), machine.sides(), rate);
    }

    /** Capacite de donnees : interne (face null), sinon la vue de la face reglee (rien sur une face fermee). */
    public LazyOptional<IDataHandler> capability(@Nullable Direction side) {
        if (side == null) {
            if (internal == null) {
                IDataHandler handler = (IDataHandler) store.handler();
                internal = LazyOptional.of(() -> handler);
            }
            return internal;
        }
        SideMode mode = machine.sides().mode(SideType.DATA, side);
        if (mode == SideMode.NONE) {
            return LazyOptional.empty();
        }
        return caps.computeIfAbsent(side, d -> {
            IDataHandler handler = (IDataHandler) store.sided(mode);
            return LazyOptional.of(() -> handler);
        });
    }

    /** Les faces ont change (ou la tile est retiree) : les vues des faces sont refaites a la demande. */
    public void invalidate(boolean all) {
        caps.values().forEach(LazyOptional::invalidate);
        caps.clear();
        if (all && internal != null) {
            internal.invalidate();
            internal = null;
        }
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        BasicDataTank tank = store.tank;
        values.add(UiValues.STORED, tank::getStored).add(UiValues.STORED_MAX, tank::getCapacity)
              .add(DATA_TYPE, () -> tank.isEmpty() ? -1 : ((ForgeRegistry<DataType>) PioneersAPI.dataTypeRegistry()).getID(tank.getType()))
              .add(RATE, () -> rate);
    }

    @Override
    public void save(CompoundTag tag) {
        //Copie a part du reservoir : la table de butin la reporte sur l'objet (mekData.dataTank)
        tag.put("dataTank", store.tank.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.getBoolean("pioneers") && tag.contains("dataTank", Tag.TAG_COMPOUND)) {
            store.tank.deserializeNBT(tag.getCompound("dataTank"));
        }
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.put("dataTank", store.tank.serializeNBT());
        tag.put("dataConfig", machine.sides().save());
    }

    @Override
    public void readClient(CompoundTag tag) {
        if (tag.contains("dataTank", Tag.TAG_COMPOUND)) {
            store.tank.deserializeNBT(tag.getCompound("dataTank"));
        }
        if (tag.contains("dataConfig", Tag.TAG_COMPOUND)) {
            machine.sides().load(tag.getCompound("dataConfig"));
        }
    }
}
