package fr.pioneers.space.base;

import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.core.common.projector.Projections;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** Plans projetables des bases spatiales construites (Maj + clic droit main vide sur le controleur) : 4 tailles chacune. */
public final class BaseBlueprint implements Blueprint {

    private static final Map<BaseLayout.Kind, BaseBlueprint> ALL = new LinkedHashMap<>();

    private final BaseLayout.Kind kind;

    private BaseBlueprint(BaseLayout.Kind kind) {
        this.kind = kind;
    }

    public static BaseBlueprint of(BaseLayout.Kind kind) {
        return ALL.computeIfAbsent(kind, BaseBlueprint::new);
    }

    /** Fournisseur pour {@link Projections#addProvider}. */
    public static List<? extends Blueprint> forBlock(Block block) {
        for (BaseLayout.Kind kind : BaseLayout.Kind.values()) {
            if (BaseStructure.suggested(kind, BaseLayout.Role.CONTROLLER) == block) {
                return List.of(of(kind));
            }
        }
        return List.of();
    }

    @Override
    public int variants() {
        return BaseLayout.Kind.SIZES;
    }

    @Override
    public Map<BlockPos, ProjectedStructure.Ghost> layout(int variant, BlockPos anchor, Direction forward) {
        Map<BlockPos, ProjectedStructure.Ghost> out = new LinkedHashMap<>();
        int size = Math.floorMod(variant, variants());
        for (Map.Entry<BlockPos, BaseLayout.Role> e : BaseLayout.cells(kind, size).entrySet()) {
            BlockPos l = e.getKey();
            Block block = BaseStructure.suggested(kind, e.getValue());
            out.put(BaseStructure.at(anchor, forward, l.getX(), l.getY(), l.getZ()),
                  new ProjectedStructure.Ghost(block == null ? null : block.defaultBlockState(), accepted(e.getValue())));
        }
        return out;
    }

    private Set<Block> accepted(BaseLayout.Role role) {
        Set<Block> out = new HashSet<>();
        switch (role) {
            case CASING -> {
                PiIndustry.CASINGS.values().forEach(b -> out.add(b.getBlock()));
                port(out);
            }
            case FRAME -> PiIndustry.FRAMES.values().forEach(b -> out.add(b.getBlock()));
            case SHEET -> {
                PiIndustry.SHEETS.values().forEach(b -> out.add(b.getBlock()));
                PiIndustry.GLASSES.values().forEach(b -> out.add(b.getBlock()));
                port(out);
            }
            default -> {
            }
        }
        return out;
    }

    private void port(Set<Block> out) {
        if (kind == BaseLayout.Kind.GREENHOUSE) {
            out.add(PiBlocks.GREENHOUSE_PORT.getBlock());
        } else if (kind == BaseLayout.Kind.GARAGE) {
            out.add(PiBlocks.MINING_PORT.getBlock());
        }
    }

    @Override
    public Block controller() {
        return BaseStructure.suggested(kind, BaseLayout.Role.CONTROLLER);
    }

    @Override
    public String getTranslationKey() {
        return "projector.pioneers.base." + kind.name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    @Override
    public String getVariantKey(int variant) {
        return "projector.pioneers.base." + kind.name().toLowerCase(Locale.ROOT) + ".size." + Math.floorMod(variant, variants());
    }
}
