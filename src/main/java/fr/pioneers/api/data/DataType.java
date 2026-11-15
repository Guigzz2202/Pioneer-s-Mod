package fr.pioneers.api.data;

import fr.pioneers.api.PioneersAPI;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Un type de donnees : la troisieme matiere des machines, a cote des fluides et des produits chimiques de Mekanism.
 * <p>
 * Les donnees se mesurent en megaoctets (Mo), se stockent dans des {@link IDataTank} et circulent par les cables de fibre optique
 * entre les faces configurees des machines ({@link IDataHandler}, {@link IDataConfigurable}).
 * <p>
 * Les types sont des entrees du registre {@link PioneersAPI#DATA_TYPE_REGISTRY_NAME} : un autre mod (ou un addon KubeJS) peut en
 * declarer de nouveaux avec un {@code DeferredRegister} sur cette cle.
 */
public class DataType {

    /** Type nul, celui des piles vides (enregistre sous {@code pioneers:empty}). */
    public static final DataType EMPTY = new DataType(0x000000);

    private final int color;
    @Nullable
    private String translationKey;

    /**
     * @param color couleur RGB (jauges, cables, rendu des prises).
     */
    public DataType(int color) {
        this.color = color & 0xFFFFFF;
    }

    public int getColor() {
        return color;
    }

    public boolean isEmptyType() {
        return this == EMPTY;
    }

    @Nullable
    public ResourceLocation getRegistryName() {
        return PioneersAPI.dataTypeRegistry().getKey(this);
    }

    /** Cle de traduction : {@code data_type.<namespace>.<chemin>}. */
    @NotNull
    public String getTranslationKey() {
        if (translationKey == null) {
            translationKey = Util.makeDescriptionId("data_type", getRegistryName());
        }
        return translationKey;
    }

    public Component getName() {
        return Component.translatable(getTranslationKey());
    }

    @Override
    public String toString() {
        ResourceLocation name = getRegistryName();
        return "DataType[" + (name == null ? "unregistered" : name) + "]";
    }
}
