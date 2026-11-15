package fr.pioneers.space.simulation;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Planete simulee et ses etapes (0.20.34, chaine de Guillaume : « compacter des roches / gaz de planete -> enrichir avec la data -> ajouter
 * l'atmosphere -> convertir en planete simulee complete ») :
 * <ol>
 *     <li>{@link Stage#COMPACTED} : matiere planetaire compactee (compresseur gravitationnel : roches ou gaz de la planete) ;</li>
 *     <li>{@link Stage#EMPTY} : planete simulee vide (synthetiseur : + donnees) ;</li>
 *     <li>{@link Stage#PROTO} : proto-planete simulee (synthetiseur : + gaz d'atmosphere) ;</li>
 *     <li>{@link Stage#FILLED} : planete simulee complete (synthetiseur : conversion), prete pour le Simulateur spatial.</li>
 * </ol>
 */
public class ItemSimulatedPlanet extends Item {

    public enum Stage {
        COMPACTED("compacted_", "compacted_planet", Rarity.UNCOMMON),
        EMPTY("empty_simulated_", "empty_simulated_planet", Rarity.RARE),
        PROTO("proto_simulated_", "proto_simulated_planet", Rarity.RARE),
        FILLED("simulated_", "simulated_planet", Rarity.EPIC);

        /** Prefixe de l'id de l'objet ({@code <prefixe><planete>}) et cle de langue du nom. */
        public final String prefix, key;
        final Rarity rarity;

        Stage(String prefix, String key, Rarity rarity) {
            this.prefix = prefix;
            this.key = key;
            this.rarity = rarity;
        }
    }

    private final SimulatedPlanet planet;
    private final Stage stage;

    public ItemSimulatedPlanet(SimulatedPlanet planet, Stage stage) {
        super(new Properties().stacksTo(stage == Stage.COMPACTED ? 16 : 1).rarity(stage.rarity));
        this.planet = planet;
        this.stage = stage;
    }

    public SimulatedPlanet getPlanet() {
        return planet;
    }

    public Stage getStage() {
        return stage;
    }

    /** Planete simulee complete (seule acceptee par le Simulateur spatial). */
    public boolean isFilled() {
        return stage == Stage.FILLED;
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return stage == Stage.FILLED;
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        return Component.translatable("item.pioneers." + stage.key, Component.translatable(planet.body.getTranslationKey()));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers." + stage.key + ".desc");
    }
}
