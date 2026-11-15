package fr.pioneers.industry.atelier;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;

/** Bloc d'une machine d'atelier de l'age industriel : une machine et un tier (FACING + ACTIVE, interface standard). */
public class AtelierBlock extends MachineBlock {

    public final IndustryMachine machine;
    public final CableTier tier;

    public AtelierBlock(IndustryMachine machine, CableTier tier, Properties properties) {
        super(properties, PiIndustry.ATELIER_TILE, AtelierTile::create);
        this.machine = machine;
        this.tier = tier;
    }

    // API Machine (lot 1) : l'infobulle de l'objet est tiree de la fiche de la machine (api.machine.MachineTooltips, tools/gc/api).
}
