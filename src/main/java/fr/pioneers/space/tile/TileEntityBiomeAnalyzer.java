package fr.pioneers.space.tile;

import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.data.ItemBiomeDataCell;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.core.registries.PiScienceMachines;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Analyseur de biomes (socle ; remplace le data drive) : chaque cellule de biome enregistree (un biome d'astre, pas un biome terrestre)
 * produit en continu les donnees de ce biome, contre de l'energie. Les cellules ne s'usent pas. Les donnees sortent par cable optique vers
 * une table de recherche, ou {@value TileEntityResearchTable#BIOME_SCAN_COST} Mo d'un biome valident son exploration (recherche de surface).
 * <p>
 * Interface : 4 cellules en production, 5 cout par cellule (FE/t) ; puis pour chaque reservoir : type (-1 vide), contenu.
 * <p>
 * API Machine (lot 8c) : logique dans {@link BiomeAnalysisUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class TileEntityBiomeAnalyzer extends DataMachineTile {

    public static final int CELL_SLOTS = 9;
    /** Lot 8b (vraie v2) : les 9 cellules en rangee a droite de la reserve d'energie, un reservoir sous chacune, carte, ecran. */
    public static final int SLOTS_X = 26, SLOTS_Y = StandardLayout.TOP + 2;
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(26, StandardLayout.TOP + 32, 162, 28);
    public static final int GUI_HEIGHT = StandardLayout.ZONE;
    /** Production par cellule (Mo/t) et son cout (FE/t). */
    public static final long DATA_PER_TICK = 25, ENERGY_PER_CELL_FE = 200;
    public static final long ENERGY_STORAGE_FE = 400_000, TANK_CAPACITY = 20_000, EJECT_RATE = 2_000;

    /** API Machine (lot 8c) : la logique (cellules, reservoirs, production) est dans l'unite portee. */
    private final BiomeAnalysisUnit unit;

    public TileEntityBiomeAnalyzer(BlockPos pos, BlockState state) {
        super(PiScienceMachines.BIOME_ANALYZER_TILE.get(), pos, state, ENERGY_STORAGE_FE, (int) (ENERGY_PER_CELL_FE * CELL_SLOTS * 2));
        unit = carry(new BiomeAnalysisUnit(this, access()));
        sides.lock(RelativeSide.FRONT);
        sides.set(SideType.ENERGY, RelativeSide.BOTTOM, SideMode.INPUT);
        sides.set(SideType.ENERGY, RelativeSide.BACK, SideMode.INPUT);
        dataDirections(false, true);
        dataSide(SideMode.OUTPUT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BOTTOM);
        dataEject(EJECT_RATE);
    }

    /** Lot 8c : reservoir de donnees declare pour l'unite. */
    BasicDataTank declareData(BasicDataTank tank) {
        return dataTank(tank);
    }

    /** Cellule enregistree dans un biome d'astre (les biomes terrestres ne donnent pas de donnees). */
    public static boolean isUsableCell(ItemStack stack) {
        String biome = ItemBiomeDataCell.getBiome(stack);
        return biome != null && PiDataTypes.biome(biome) != null;
    }

    /** Type de donnees produit par la cellule d'une case (null si vide). */
    @Nullable
    public DataType cellType(int index) {
        return unit.cellType(index);
    }

    @Override
    protected void tick() {
    }

    public List<BasicDataTank> getOutputTanks() {
        return unit.tanks;
    }

    @Override
    public int guiHeight() {
        return GUI_HEIGHT;
    }

    @Override
    public String guiTier() {
        return "biome_analyzer";
    }

    /** API Machine (lot 4) : cadre de l'interface standard v2 (inventaire DANS le cadre, onglets a droite) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        List<Object> column = new ArrayList<>();
        for (int i = 0; i < CELL_SLOTS; i++) {
            column.add(new StandardLayout.Part("bar", new StandardLayout.Rect(SLOTS_X - 1 + i * 18, SLOTS_Y + 19, 18, 6)));
        }
        column.add(new StandardLayout.Part("card", CARD));
        return StandardLayout.serviceParts(false, column.toArray());
    }
}
