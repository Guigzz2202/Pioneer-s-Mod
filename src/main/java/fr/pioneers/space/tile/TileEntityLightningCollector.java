package fr.pioneers.space.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.registries.PiSpaceMachines;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Paratonnerre (socle, multibloc vertical) : le collecteur, au pied, porte un mat de 4 a 24 conducteurs surmonte d'un paratonnerre en cuivre
 * (a ciel ouvert). Pendant un orage, la foudre frappe la pointe et charge le collecteur ; une supertempete des geantes gazeuses declenche
 * bien plus d'eclairs, et leur atmosphere en produit meme par temps calme. L'energie sort par les faces de sortie (toutes sauf le dessus
 * par defaut) et charge l'objet de la case de recharge.
 * <p>
 * Interface : 4 hauteur du mat (0 : incomplet), 5 ciel ouvert, 6 meteo (0 calme, 1 orage, 2 geante, 3 supertempete), 7 eclairs captes,
 * 8 energie du dernier eclair (FE), 9 production moyenne attendue (FE/t x100).
 * <p>
 * API Machine (lot 8c) : logique dans {@link LightningUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class TileEntityLightningCollector extends MachineTile {

    public static final int MIN_MAST = 4, MAX_MAST = 24;
    /** 1.0 : mat le plus haut compte par tier (MKI a MKIV) et debit de sortie. */
    public static final int[] TIER_MAST = {8, 12, 18, 24};
    public static final long[] TIER_OUTPUT_FE = {250_000, 500_000, 1_000_000, 2_000_000};
    public static final long ENERGY_STORAGE_FE = 100_000_000;
    /** Debit maximal de sortie (FE/t). */
    public static final long OUTPUT_FE = 1_000_000;
    public static final int CHARGE_X = StandardLayout.BATTERY_X, CHARGE_Y = StandardLayout.BATTERY_Y;

    /** API Machine (lot 8c) : la logique (mat, meteo, eclairs, sortie) est dans l'unite portee. */
    private final LightningUnit unit;

    public TileEntityLightningCollector(BlockPos pos, BlockState state) {
        super(PiSpaceMachines.LIGHTNING_COLLECTOR_TILE.get(), pos, state, ENERGY_STORAGE_FE, (int) TIER_OUTPUT_FE[TieredMachineBlock.tierOf(state)]);
        unit = carry(new LightningUnit(access()));
        sides.set(RelativeSide.TOP, SideMode.NONE);
    }

    /** Ecran haut : 4 lignes d'infos sous la barre. */
    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.serviceParts(true, new StandardLayout.Part("card", StandardLayout.panel(StandardLayout.WIDTH)));
    }

    @Override
    protected boolean producesEnergy() {
        return true;
    }

    @Override
    protected void tick() {
    }

    @Override
    protected Set<SideMode> allowedModes() {
        return EnumSet.of(SideMode.NONE, SideMode.OUTPUT);
    }

    /** Energie d'un eclair (FE) : la supertempete charge davantage chaque decharge. */
    public static long getStrikeEnergy(int mast, boolean superstorm) {
        return (long) (PiCommonConfig.get(PiCommonConfig.LIGHTNING_ENERGY, 1_000_000L) * (1 + mast / 8.0) * (superstorm ? 1.5 : 1));
    }

    /** Production moyenne attendue (FE/t) dans la meteo actuelle. */
    public double getAverageOutput() {
        return unit.getAverageOutput();
    }

    public boolean isFormed() {
        return unit.isFormed();
    }

    /** API Machine (lot 4) : valeurs NOMMEES (lot 8c : declarees par l'unite, memes cles et indices). */
    public static final UiValues.Key MAST = LightningUnit.MAST, SKY = LightningUnit.SKY, WEATHER = LightningUnit.WEATHER, STRIKES = LightningUnit.STRIKES,
          LAST_STRIKE = LightningUnit.LAST_STRIKE, AVERAGE = LightningUnit.AVERAGE;
}
