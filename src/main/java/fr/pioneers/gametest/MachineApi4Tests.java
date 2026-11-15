package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.DataBankUnit;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.data.PiDataTypes;
import fr.pioneers.registries.PiMachines;
import fr.pioneers.registries.PiSpaceMachines;
import fr.pioneers.tile.TileEntityChunkLoader;
import fr.pioneers.tile.TileEntityDataBank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Machine (lot 4, GameTests) : la banque de donnees sur son unite {@link DataBankUnit} (stockage DATA de la couche ressource) :
 * aller-retour de sauvegarde (cle {@code dataTank} d'avant, faces gardees), valeurs nommees aux indices d'avant, capacite interne et
 * creatif ; une petite machine spatiale a l'interface v2 (valeurs nommees, taille du panneau, batterie a la place commune).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineApi4Tests {

    private MachineApi4Tests() {
    }

    private static TileEntityDataBank bank(GameTestHelper h, BlockPos rel, TileEntityDataBank.Tier tier) {
        Block block = PiMachines.DATA_BANKS.get(tier).getBlock();
        h.setBlock(rel, block.defaultBlockState());
        Gt.check(h.getBlockEntity(rel) instanceof TileEntityDataBank, "pas de tuile de banque de donnees " + tier);
        return (TileEntityDataBank) h.getBlockEntity(rel);
    }

    // ================================================================== banque de donnees : sauvegarde et valeurs
    @GameTest(template = Gt.SMALL, batch = "pioneers_api4_data_bank", timeoutTicks = 40)
    public static void api4_banque_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        TileEntityDataBank a = bank(h, new BlockPos(2, 2, 2), TileEntityDataBank.Tier.ADVANCED);
        IDataHandler inner = a.getCapability(PioneersAPI.DATA_HANDLER, null).resolve().orElse(null);
        Gt.check(inner != null, "capacite de donnees interne absente");
        DataStack rest = inner.insertData(DataStack.of(PiDataTypes.TELEMETRY.get(), 12_345), Action.EXECUTE);
        Gt.check(rest.isEmpty(), "les donnees n'entrent pas par la capacite interne");
        Gt.equal(12_345L, a.getTank().getStored(), "donnees stockees");
        CompoundTag tag = a.saveWithoutMetadata();
        Gt.check(tag.contains("dataTank"), "cle « dataTank » manquante (format d'avant le lot 4)");
        Gt.check(tag.contains("config"), "cle « config » manquante");
        TileEntityDataBank b = bank(h, new BlockPos(5, 2, 2), TileEntityDataBank.Tier.ADVANCED);
        b.load(tag.copy());
        Gt.equal(12_345L, b.getTank().getStored(), "donnees relues");
        Gt.equal(tag, b.saveWithoutMetadata(), "sauvegarde identique apres relecture");
        //Valeurs nommees aux indices d'avant le lot 4 (4 contenu, 5 capacite, 6 type, 7 debit)
        UiValues ui = b.uiValues();
        Gt.check(ui != null, "la banque n'a pas de valeurs nommees");
        Gt.equal(8, ui.size(), "nombre de valeurs d'interface");
        Gt.equal(4, ui.index(UiValues.STORED), "contenu a l'indice 4");
        Gt.equal(5, ui.index(UiValues.STORED_MAX), "capacite a l'indice 5");
        Gt.equal(6, ui.index(DataBankUnit.DATA_TYPE), "type a l'indice 6");
        Gt.equal(7, ui.index(DataBankUnit.RATE), "debit a l'indice 7");
        Gt.equal(TileEntityDataBank.Tier.ADVANCED.rate, b.guiValue(7), "debit du tier");
        //Le creatif reste plein de son type
        TileEntityDataBank c = bank(h, new BlockPos(2, 2, 5), TileEntityDataBank.Tier.CREATIVE);
        c.getTank().insert(DataStack.of(PiDataTypes.TELEMETRY.get(), 1), Action.EXECUTE, Automation.INTERNAL);
        Gt.check(c.getTank().getStored() > 1_000_000_000L, "la banque creative n'est pas pleine");
        Gt.equal(StandardLayout.ZONE, c.guiHeight(), "interface v2 : zone de la banque");
        s.done();
    }

    // ================================================================== chunkloader : interface v2, valeurs nommees
    @GameTest(template = Gt.SMALL, batch = "pioneers_api4_space", timeoutTicks = 40)
    public static void api4_chunkloader_v2(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, PiSpaceMachines.CHUNK_LOADERS.get(1).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(rel) instanceof TileEntityChunkLoader, "pas de chunkloader");
        TileEntityChunkLoader t = (TileEntityChunkLoader) h.getBlockEntity(rel);
        UiValues ui = t.uiValues();
        Gt.check(ui != null, "le chunkloader n'a pas de valeurs nommees");
        Gt.equal(7, ui.size(), "7 valeurs d'interface (4 communes + rayon, rayon charge, cout)");
        Gt.equal(4, ui.index(TileEntityChunkLoader.RADIUS), "rayon a l'indice 4");
        Gt.equal(StandardLayout.WIDTH, t.guiWidth(), "largeur du panneau v2");
        Gt.equal(StandardLayout.INVENTORY, t.guiInventory(), "inventaire dans le cadre");
        Gt.check(t.guiSlots().stream().anyMatch(sl -> sl.x == StandardLayout.BATTERY_X && sl.y == StandardLayout.BATTERY_Y),
              "batterie hors de la place commune");
        Gt.check(t instanceof MachineTile, "le chunkloader n'est pas une machine du socle");
        s.done();
    }
}
