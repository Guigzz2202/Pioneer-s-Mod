package fr.pioneers.space.assembly;

import fr.pioneers.common.projector.Blueprint;
import fr.pioneers.common.projector.ProjectedStructure;
import fr.pioneers.registries.PiIndustry;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** 1.9.2 : plans projetables des multiblocs d'assemblage (Maj + clic droit main vide sur le controleur) ; une seule taille. */
public final class AssemblyHallBlueprint implements Blueprint {

    private static final Map<AssemblyHall.Kind, AssemblyHallBlueprint> ALL = new EnumMap<>(AssemblyHall.Kind.class);

    private final AssemblyHall.Kind kind;

    private AssemblyHallBlueprint(AssemblyHall.Kind kind) {
        this.kind = kind;
    }

    public static AssemblyHallBlueprint of(AssemblyHall.Kind kind) {
        return ALL.computeIfAbsent(kind, AssemblyHallBlueprint::new);
    }

    /** Fournisseur pour {@code Projections.addProvider}. */
    public static List<? extends Blueprint> forBlock(Block block) {
        for (AssemblyHall.Kind kind : AssemblyHall.Kind.values()) {
            if (AssemblyHallStructure.suggested(kind, AssemblyHall.Role.CONTROLLER) == block) {
                return List.of(of(kind));
            }
        }
        return List.of();
    }

    @Override
    public int variants() {
        return 1;
    }

    @Override
    public Map<BlockPos, ProjectedStructure.Ghost> layout(int variant, BlockPos anchor, Direction forward) {
        Map<BlockPos, ProjectedStructure.Ghost> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, AssemblyHall.Role> e : AssemblyHall.cells(kind).entrySet()) {
            BlockPos l = e.getKey();
            Block block = AssemblyHallStructure.suggested(kind, e.getValue());
            out.put(AssemblyHall.at(anchor, forward, l.getX(), l.getY(), l.getZ()),
                  new ProjectedStructure.Ghost(block == null ? null : block.defaultBlockState(), accepted(e.getValue())));
        }
        return out;
    }

    private static Set<Block> accepted(AssemblyHall.Role role) {
        Set<Block> out = new HashSet<>();
        switch (role) {
            case CASING -> {
                PiIndustry.CASINGS.values().forEach(b -> out.add(b.getBlock()));
                ports(out);
            }
            case FRAME -> PiIndustry.FRAMES.values().forEach(b -> out.add(b.getBlock()));
            case SHEET -> {
                PiIndustry.SHEETS.values().forEach(b -> out.add(b.getBlock()));
                PiIndustry.GLASSES.values().forEach(b -> out.add(b.getBlock()));
                ports(out);
            }
            case GLASS -> PiIndustry.GLASSES.values().forEach(b -> out.add(b.getBlock()));
            default -> {
            }
        }
        return out;
    }

    private static void ports(Set<Block> out) {
        PiIndustry.PORTS.values().forEach(b -> {
            if (AssemblyHallStructure.isPort(b.getBlock())) {
                out.add(b.getBlock());
            }
        });
    }

    @Override
    public Block controller() {
        return AssemblyHallStructure.suggested(kind, AssemblyHall.Role.CONTROLLER);
    }

    @Override
    public String getTranslationKey() {
        return "projector.pioneers.assembly_hall." + kind.id();
    }

    @Nullable
    @Override
    public String getVariantKey(int variant) {
        return null;
    }
}
