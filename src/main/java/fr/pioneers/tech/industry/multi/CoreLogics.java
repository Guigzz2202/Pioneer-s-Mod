package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.api.multiblock.MultiblockRegistry;
import fr.pioneers.api.multiblock.MultiblockSpec;
import fr.pioneers.tech.compute.ComputeCore;
import fr.pioneers.tech.fusion.FusionCore;
import fr.pioneers.tech.fusion.FusionType;
import fr.pioneers.tech.nuclear.FissionCore;
import fr.pioneers.tech.nuclear.ReactorType;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 6) : FABRIQUE des coeurs propres des controleurs, d'apres l'unite DECLAREE PAR LA FICHE ({@link MultiblockSpec#process()}) :
 * {@code reactor} = {@link FissionCore}, {@code fusion} = {@link FusionCore}, {@code compute} = {@link ComputeCore}, {@code offshore} =
 * {@link OffshoreRisers}, {@code silo} = {@link SiloCore} (lot 22) ; les autres unites ({@code batch}, {@code generator}, {@code well}...) n'ont pas de coeur : le procede commun
 * ({@link MultiProcess}) les fait tourner. Le controleur n'aiguille plus sur le genre de la machine.
 */
public final class CoreLogics {

    private CoreLogics() {
    }

    /** Le coeur declare par la fiche du controleur, ou null. */
    @Nullable
    static MultiblockLogic create(ControllerTile tile) {
        MultiblockSpec spec = MultiblockRegistry.of(tile.machine);
        if (spec == null) {
            return null;
        }
        String key = tile.machine.key;
        return switch (spec.process()) {
            case "reactor" -> {
                ReactorType type = ReactorType.of(key);
                yield type == null ? null : new FissionCore(tile, type);
            }
            case "fusion" -> {
                FusionType type = FusionType.of(key);
                yield type == null ? null : new FusionCore(tile, type);
            }
            case "compute" -> ComputeCore.create(tile);
            case "offshore" -> new OffshoreRisers(tile);
            //1.0.4-beta (lot 22) : silo a objets (stock en vrac d'un seul objet)
            case "silo" -> new SiloCore(tile);
            default -> null;
        };
    }
}
