package fr.pioneers.space.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.space.research.PiScienceMachines;
import fr.pioneers.space.research.ProbeDestination;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * Antenne de reception (socle) : accordee sur une destination, elle recoit les donnees des sondes en orbite
 * et les pousse par ses faces en sortie (cables optiques, ou machine collee) vers la table de recherche.
 * Plusieurs antennes sur la meme destination se partagent le flux. Trois blocs de haut (blocs fantomes du socle au-dessus).
 * <p>
 * Interface : 4 destination, 5 sondes, 6 debit de la derniere seconde, 7 cable branche, 8 en ecoute.
 * <p>
 * API Machine (lot 8c) : logique dans {@link AntennaUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class TileEntityDataAntenna extends DataMachineTile {

    public static final long BUFFER_CAPACITY = 64_000;
    /** Debit maximal envoye sur le reseau de cables (Mo/t). */
    public static final long OUTPUT_RATE = 2_000;
    /** Debit maximal d'une antenne (mB/t). */
    public static final long MAX_RATE = 400;
    public static final long ENERGY_PER_TICK_FE = 1_000;
    public static final long ENERGY_STORAGE_FE = 200_000;
    public static final int GUI_HEIGHT = StandardLayout.ZONE;
    /** Lot 8b (vraie v2) : reservoir de donnees a droite de la reserve d'energie, carte, ecran raccourci, rangee de la destination. */
    public static final StandardLayout.Rect BUFFER = new StandardLayout.Rect(26, StandardLayout.TOP, 18, StandardLayout.GAUGE_H),
          CARD = new StandardLayout.Rect(50, StandardLayout.TOP, StandardLayout.WIDTH - 8 - 50, StandardLayout.GAUGE_H);
    public static final int PREVIOUS = CUSTOM + 1, NEXT = CUSTOM;

    /** API Machine (lot 8c) : la logique (ecoute, flux, cable, debit) est dans l'unite portee. */
    private final AntennaUnit unit;

    public TileEntityDataAntenna(BlockPos pos, BlockState state) {
        super(PiScienceMachines.DATA_ANTENNA_TILE.get(), pos, state, ENERGY_STORAGE_FE, (int) (ENERGY_PER_TICK_FE * 4));
        unit = carry(new AntennaUnit(this, access()));
        //Le caisson : quatre faces et le dessous, en sortie par defaut ; le dessus porte le mat
        sides.lock(RelativeSide.TOP);
        for (RelativeSide side : new RelativeSide[]{RelativeSide.BOTTOM, RelativeSide.BACK}) {
            sides.set(SideType.ENERGY, side, SideMode.INPUT);
        }
        dataDirections(false, true);
        dataSide(SideMode.OUTPUT, RelativeSide.FRONT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BOTTOM);
        dataEject(OUTPUT_RATE);
    }

    /** Lot 8c : reservoir de donnees declare pour l'unite. */
    BasicDataTank declareData(BasicDataTank tank) {
        return dataTank(tank);
    }

    @Override
    public int guiHeight() {
        return GUI_HEIGHT;
    }

    @Override
    public String guiTier() {
        return "data_antenna";
    }

    @Override
    protected void tick() {
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        unit.step(action == PREVIOUS);
    }

    public ProbeDestination getDestination() {
        return unit.getDestination();
    }

    public BasicDataTank getBuffer() {
        return unit.buffer;
    }

    public boolean hasMismatchedData() {
        return !unit.buffer.isEmpty() && unit.buffer.getType() != PiDataTypes.of(getDestination());
    }

    /** Cote client : la parabole tourne quand l'antenne ecoute. */
    public boolean isClientActive() {
        return unit.clientActive;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 4, 2));
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
        List<StandardLayout.Part> out = new java.util.ArrayList<>(StandardLayout.serviceParts(false, StandardLayout.framed("gauge", BUFFER),
              new StandardLayout.Part("card", CARD)));
        out.removeIf(p -> p.role().equals("screen"));
        out.add(new StandardLayout.Part("screen", StandardLayout.shortScreen(StandardLayout.WIDTH)));
        out.add(new StandardLayout.Part("setting", StandardLayout.settingRow(StandardLayout.WIDTH)));
        return out;
    }
}
