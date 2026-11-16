package fr.pioneers.tech.machine;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.machine.PiMachineKit;
import fr.pioneers.logistics.transport.CableTier;

/**
 * Bloc d'une machine du kit (genre + tier). Etats : {@code facing} (facade vers le joueur a la pose) et {@code active} (elle travaille).
 * API Machine (lot 2) : le kit est fondu dans l'atelier : bloc standard ({@link MachineBlock} : interface, objets rendus a la casse, verrou
 * de recette garde dans l'objet, commande redstone), tile generique {@link AtelierTile} (procede {@code api.machine.unit.KitProcess}) sur
 * le type de tile {@code kit_machine} garde (mondes existants). Ids des blocs inchanges.
 */
public class KitMachineBlock extends MachineBlock {

    public final MachineKind kind;
    public final CableTier tier;

    public KitMachineBlock(MachineKind kind, CableTier tier, Properties properties) {
        super(properties, PiMachineKit.PROCESSING_TILE, AtelierTile::create);
        this.kind = kind;
        this.tier = tier;
    }

    // API Machine (lot 1) : l'infobulle de l'objet est tiree de la fiche de la machine (api.machine.MachineTooltips, tools/gc/api).
}
