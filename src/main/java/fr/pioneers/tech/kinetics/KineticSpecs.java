package fr.pioneers.tech.kinetics;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.kinetics.parts.CogBlock;
import fr.pioneers.tech.kinetics.parts.ShaftBlock;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** Lignes techniques des infobulles (limites, puissance) des pieces de l'age mecanique. */
public final class KineticSpecs {

    private KineticSpecs() {
    }

    public static void describe(Block block, List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        KineticMaterial material = block instanceof ShaftBlock shaft ? shaft.material : block instanceof CogBlock cog ? cog.material : null;
        if (material != null) {
            tips.spec("kinetics.pioneers.spec.limits", KineticTile.fmt(material.maxTorque), KineticTile.fmt(material.maxSpeed));
        }
        if (block instanceof Specified specified) {
            specified.specs(tooltip);
        }
    }

    /** Bloc qui ajoute ses propres lignes techniques (puissance d'une source, couple d'une machine...). */
    public interface Specified {

        void specs(List<Component> tooltip);
    }
}
