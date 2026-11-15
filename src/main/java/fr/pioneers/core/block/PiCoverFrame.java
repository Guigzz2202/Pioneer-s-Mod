package fr.pioneers.core.block;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * 1.0.4-beta (lot 30, Guillaume : « pour les covers de cables et de shafts, on implemente un block de frame (en bois) c'est le seul avec
 * lequel on peut clic droit pour ajouter le cover ») : le CADRE DE CACHE ({@code cover_frame}). Clic droit avec lui sur un cable logistique
 * ou un arbre de rotation : il s'installe autour ({@link Covers}) ; seul ce cadre recoit ensuite une cache (bloc plein). Pose seul, c'est
 * un cadre de bois decoratif. Fabrication : etabli du charpentier, scie, batons et planches ({@code tools/gc/logistics/cover_frame.py}).
 * Onglet Logistique (section des cables).
 */
public final class PiCoverFrame {

    public static final String ID = "cover_frame";
    public static final BlockEntry<CoverFrameBlock> FRAME = PiCoreRegistries.block(ID, () -> new CoverFrameBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(1.5F, 2.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion()));

    private PiCoverFrame() {
    }

    public static void init() {
    }
}
