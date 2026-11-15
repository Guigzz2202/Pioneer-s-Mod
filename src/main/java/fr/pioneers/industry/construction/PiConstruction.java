package fr.pioneers.industry.construction;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.transport.CableTier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 1.0.4-beta (lot 21) : BLOCS DE CONSTRUCTION DE MULTIBLOC (demande de Guillaume du 08/11/2026 : « implemente juste », il donnera les
 * nouvelles structures ensuite). Aucune integration dans les multiblocs : ni gabarit, ni role, ni bonus, ni formation ; ce ne sont pas des
 * {@code CasingBlock} (une structure ne les compte pas comme carcasse).
 * <ul>
 *     <li>parois de foyer MKI a MKIV {@code firebox_<tier>} (grille sur les 4 cotes, textures connectees, lueur des braises) ;</li>
 *     <li>bobines de chauffe MKI a MKIV {@code heating_coil_<tier>} (toujours verticales, textures connectees) ;</li>
 *     <li>salle blanche (MKII) : paroi et vitre connectees, plafond filtrant lumineux, porte de deux blocs (porte vanilla).</li>
 * </ul>
 * Ressources, recettes et langue : {@code tools/gc/industry/building_blocks.py} (source unique) ; controle {@code tools/check_p104_21.py}.
 */
public final class PiConstruction {

    /** Lumiere d'une paroi de foyer ALLUMEE (meme valeur que building_blocks.FIREBOX_LIGHT) ; 1.0.4-beta (lot 23) : eteinte, aucune. */
    public static final int FIREBOX_LIGHT = 10;
    /** Lumiere des rampes de LED du plafond filtrant (building_blocks.FILTER_LIGHT). */
    public static final int FILTER_LIGHT = 10;

    private PiConstruction() {
    }

    /** Charge la classe (registres differes). */
    public static void init() {
    }

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final Map<CableTier, BlockEntry<FireboxWallBlock>> FIREBOXES = new EnumMap<>(CableTier.class);
    public static final Map<CableTier, BlockEntry<HeatingCoilBlock>> HEATING_COILS = new EnumMap<>(CableTier.class);

    static {
        for (CableTier tier : CableTier.values()) {
            FIREBOXES.put(tier, PiCoreRegistries.block("firebox_" + tier.id(), () -> new FireboxWallBlock(tier, metal(MapColor.TERRACOTTA_ORANGE)
                  .lightLevel(state -> state.getValue(FireboxWallBlock.LIT) ? FIREBOX_LIGHT : 0))));
        }
        for (CableTier tier : CableTier.values()) {
            HEATING_COILS.put(tier, PiCoreRegistries.block("heating_coil_" + tier.id(), () -> new HeatingCoilBlock(tier, metal(MapColor.METAL))));
        }
    }

    public static final BlockEntry<CleanroomPanelBlock> CLEANROOM_CASING = PiCoreRegistries.block("cleanroom_casing",
          () -> new CleanroomPanelBlock(metal(MapColor.SNOW).strength(3.0F, 6.0F)));
    public static final BlockEntry<CleanroomGlassBlock> CLEANROOM_GLASS = PiCoreRegistries.block("cleanroom_glass",
          () -> new CleanroomGlassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(3.0F, 6.0F).sound(SoundType.GLASS)
                .noOcclusion().requiresCorrectToolForDrops().isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final BlockEntry<Block> CLEANROOM_FILTER = PiCoreRegistries.block("cleanroom_filter",
          () -> new Block(metal(MapColor.SNOW).strength(3.0F, 6.0F).lightLevel(state -> FILTER_LIGHT)));
    //1.0.4-beta (lot 23) : porte de salle blanche retiree (demande de Guillaume ; remap -> paroi, PiRemaps.p104Lot23Block)

    /** 1.0.4-beta (lot 22) : familles des blocs empilables (stacked_blocks.FAMILIES), dans l'ordre. */
    public static final List<String> STACKED_FAMILIES = List.of("turbine_rotor", "drill_string", "column_pump");
    /** Lot 22 : blocs empilables par famille puis par tier (rotor de turbine, train de forage, pompe a colonne). */
    public static final Map<String, Map<CableTier, BlockEntry<StackedColumnBlock>>> STACKED = new LinkedHashMap<>();

    static {
        for (String family : STACKED_FAMILIES) {
            Map<CableTier, BlockEntry<StackedColumnBlock>> byTier = new EnumMap<>(CableTier.class);
            for (CableTier tier : CableTier.values()) {
                byTier.put(tier, PiCoreRegistries.block(family + "_" + tier.id(), () -> new StackedColumnBlock(family, tier,
                      metal(MapColor.METAL).noOcclusion().isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                            .isViewBlocking((s, l, p) -> false))));
            }
            STACKED.put(family, byTier);
        }
    }

    /**
     * 1.0.4-beta (lot 23) : train de forage en BRONZE (age du bronze : le centre de la foreuse a vapeur), meme bloc empilable que les
     * trains MKI a MKIV, palette bronze, sans tier MK ({@link StackedColumnBlock#tier} nul) ; hors de {@link #STACKED} (familles x tiers MK).
     */
    public static final BlockEntry<StackedColumnBlock> DRILL_STRING_BRONZE = PiCoreRegistries.block("drill_string_bronze",
          () -> new StackedColumnBlock("drill_string", null, metal(MapColor.COLOR_ORANGE).noOcclusion().isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** Lot 22 : les 12 blocs empilables, dans l'ordre de stacked_blocks.IDS. */
    public static List<BlockEntry<? extends Block>> stacked() {
        List<BlockEntry<? extends Block>> out = new ArrayList<>();
        STACKED.values().forEach(m -> out.addAll(m.values()));
        return out;
    }

    /** Les 11 blocs, dans l'ordre de building_blocks.IDS (1.0.4-beta lot 23 : sans la porte). */
    public static List<BlockEntry<? extends Block>> all() {
        List<BlockEntry<? extends Block>> out = new ArrayList<>(FIREBOXES.values());
        out.addAll(HEATING_COILS.values());
        out.addAll(List.of(CLEANROOM_CASING, CLEANROOM_GLASS, CLEANROOM_FILTER));
        return out;
    }
}
