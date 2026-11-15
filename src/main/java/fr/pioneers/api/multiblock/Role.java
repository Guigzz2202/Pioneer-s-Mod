package fr.pioneers.api.multiblock;

import fr.pioneers.Pioneers;
import fr.pioneers.industry.construction.CleanroomGlassBlock;
import fr.pioneers.industry.construction.CleanroomPanelBlock;
import fr.pioneers.industry.construction.FireboxWallBlock;
import fr.pioneers.industry.construction.HeatingCoilBlock;
import fr.pioneers.industry.construction.PiConstruction;
import fr.pioneers.industry.construction.StackedColumnBlock;
import fr.pioneers.industry.construction.TwinConnectedBlock;
import fr.pioneers.industry.multi.CasingBlock;
import fr.pioneers.industry.multi.ConcreteShellBlock;
import fr.pioneers.industry.multi.ControllerBlock;
import fr.pioneers.industry.multi.IndustrialGlassBlock;
import fr.pioneers.industry.multi.ModuleBlock;
import fr.pioneers.industry.multi.PartBlock;
import fr.pioneers.industry.multi.PortBlock;
import fr.pioneers.industry.multi.SheetMetalBlock;
import fr.pioneers.industry.multi.StructureFrameBlock;
import fr.pioneers.nuclear.ReactorBlock;
import fr.pioneers.registries.PiBlocks;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * API Multibloc (lot 5) : ROLE d'une case de multibloc et les blocs qu'il accepte : par la CLASSE du bloc (ce qu'acceptait la validation
 * d'avant : aucun monde existant ne perd sa structure) ET par le TAG {@code pioneers:multiblock/<role>} (un pack de donnees ou un addon
 * peut y ajouter ses blocs). Un bloc accepte par le tag seulement compte comme une piece passive du role, au tier 0 (MKI).
 * <p>
 * Les tags sont GENERES ({@code tools/gc/api/gen_api.py}) depuis l'instantane des roles du banc ({@code tools/api/snapshots/
 * multiblock_roles.txt} : les blocs que la classe accepte), donc tag = classe a la livraison.
 */
public record Role(String name, TagKey<Block> tag, Predicate<BlockState> byClass) {

    public static final Role CONTROLLER = of("controller", s -> s.getBlock() instanceof ControllerBlock);
    public static final Role CASING = of("casing", s -> s.getBlock() instanceof CasingBlock);
    public static final Role GLASS = of("glass", s -> s.getBlock() instanceof IndustrialGlassBlock);
    public static final Role PORT = of("port", s -> s.getBlock() instanceof PortBlock);
    public static final Role PART = of("part", s -> s.getBlock() instanceof PartBlock);
    public static final Role FRAME = of("frame", s -> s.getBlock() instanceof StructureFrameBlock);
    public static final Role SHEET = of("sheet", s -> s.getBlock() instanceof SheetMetalBlock);
    public static final Role CONCRETE = of("concrete", s -> s.getBlock() instanceof ConcreteShellBlock);
    public static final Role MODULE = of("module", s -> s.getBlock() instanceof ModuleBlock);
    public static final Role REACTOR = of("reactor", s -> s.getBlock() instanceof ReactorBlock);
    /**
     * 1.0.4-beta (lot 24) : roles des structures de Guillaume (gabarits NBT, {@code industry.multi.NbtRoles}) : parois de foyer, bobines de
     * chauffe, blocs empilables (rotor, train de forage, pompe a colonne), salle blanche (paroi, vitre, plafond filtrant), substrat.
     */
    public static final Role FIREBOX = of("firebox", s -> s.getBlock() instanceof FireboxWallBlock);
    public static final Role COIL = of("coil", s -> s.getBlock() instanceof HeatingCoilBlock);
    public static final Role STACKED = of("stacked", s -> s.getBlock() instanceof StackedColumnBlock c && c.tier != null);
    public static final Role CLEANROOM = of("cleanroom", s -> s.getBlock() instanceof CleanroomPanelBlock || s.getBlock() instanceof CleanroomGlassBlock
                                                             || s.getBlock() == PiConstruction.CLEANROOM_FILTER.getBlock());
    public static final Role SUBSTRATE = of("substrate", s -> s.getBlock() == PiBlocks.HYDROPONIC_SUBSTRATE.getBlock());
    /** Roles du moteur industriel, dans l'ordre du decompte de l'onglet Structure (lot 24 : roles des gabarits NBT a la fin). */
    public static final List<Role> INDUSTRIAL = List.of(CONTROLLER, CASING, GLASS, PORT, PART, FRAME, SHEET, CONCRETE, MODULE, REACTOR, FIREBOX, COIL,
          STACKED, CLEANROOM, SUBSTRATE);

    private static Role of(String name, Predicate<BlockState> test) {
        return new Role(name, TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Pioneers.MODID, "multiblock/" + name)), test);
    }

    /**
     * Role des modules d'une sorte : classe ModuleBlock de cette sorte, ou tag {@code pioneers:multiblock/module/<sorte>}. 1.0.4-beta (lot 23) :
     * plus de bloc « Colonne montante » : la colonne montante de la plate-forme offshore est une POMPE A COLONNE (bloc empilable du lot 22).
     */
    public static Role module(fr.pioneers.industry.IndustryModule module) {
        return new Role("module", TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Pioneers.MODID, "multiblock/module/" + module.id())),
              s -> s.getBlock() instanceof ModuleBlock m && m.module == module
                   || module == fr.pioneers.industry.IndustryModule.RISER && s.getBlock() instanceof StackedColumnBlock c && c.family.equals("column_pump"));
    }

    /** Le bloc est-il accepte (classe, ou tag une fois les tags charges) ? */
    public boolean accepts(BlockState state) {
        return byClass.test(state) || state.is(tag);
    }

    /** Accepte par le tag seulement (piece passive, tier 0) ? */
    public boolean tagOnly(BlockState state) {
        return !byClass.test(state) && state.is(tag);
    }

    /** Role d'un bloc pose (premier role industriel qui l'accepte), ou null. */
    public static Role roleOf(BlockState state) {
        for (Role r : INDUSTRIAL) {
            if (r.accepts(state)) {
                return r;
            }
        }
        return null;
    }

    /** Tier porte par une piece a tier (carcasse, tole, cadre, vitre ; lot 24 : foyer, bobine, bloc empilable) ; 0 sinon (y compris un bloc accepte par tag seulement). */
    public static int tierOf(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CasingBlock c) {
            return c.tier;
        }
        if (b instanceof SheetMetalBlock s) {
            return s.tier;
        }
        if (b instanceof StructureFrameBlock f) {
            return f.tier;
        }
        if (b instanceof IndustrialGlassBlock g) {
            return g.tier;
        }
        //1.0.4-beta (lot 24) : parois de foyer, bobines, blocs empilables MKI a MKIV
        if (b instanceof TwinConnectedBlock t) {
            return t.tier.ordinal();
        }
        if (b instanceof StackedColumnBlock c && c.tier != null) {
            return c.tier.ordinal();
        }
        return 0;
    }
}
