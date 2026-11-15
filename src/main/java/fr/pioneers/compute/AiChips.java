package fr.pioneers.compute;

import fr.pioneers.Pioneers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Modeles d'IA entraines (0.17.2) : puces du catalogue {@code ai_model_expert}, {@code ai_model_neural}, {@code ai_model_foundation}, et puce
 * vierge {@code neural_chip_blank}. Niveaux : 0 aucun, 1 systeme expert, 2 reseau de neurones, 3 modele de fondation.
 * <p>
 * Puce installee dans un controleur de multibloc (ou bonus d'un noyau d'usine) : expert -10 % d'energie ; reseau -20 % et +20 % de
 * vitesse ; fondation -30 % et +40 %.
 */
public final class AiChips {

    private AiChips() {
    }

    public static final int NONE = 0, EXPERT = 1, NEURAL = 2, FOUNDATION = 3;
    public static final String[] IDS = {"neural_chip_blank", "ai_model_expert", "ai_model_neural", "ai_model_foundation"};
    /** Cle de langue courte de chaque modele (personnalite du noyau d'usine). */
    public static final String[] KEYS = {"blank", "expert", "neural", "foundation"};

    /** Niveau du modele de cette pile (0 si ce n'est pas un modele entraine). */
    public static int level(ItemStack stack) {
        if (stack.isEmpty()) {
            return NONE;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !Pioneers.MODID.equals(id.getNamespace())) {
            return NONE;
        }
        for (int i = 1; i < IDS.length; i++) {
            if (IDS[i].equals(id.getPath())) {
                return i;
            }
        }
        return NONE;
    }

    public static boolean isModel(ItemStack stack) {
        return level(stack) > NONE;
    }

    public static boolean isBlank(ItemStack stack) {
        ResourceLocation id = stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && Pioneers.MODID.equals(id.getNamespace()) && IDS[0].equals(id.getPath());
    }

    /** Puce de ce niveau (0 : puce vierge). */
    public static Item item(int level) {
        return ForgeRegistries.ITEMS.getValue(Pioneers.rl(IDS[Math.max(0, Math.min(3, level))]));
    }

    /** Multiplicateur d'energie : 1 ; 0,9 ; 0,8 ; 0,7. */
    public static double energyFactor(int level) {
        return switch (level) {
            case EXPERT -> 0.9;
            case NEURAL -> 0.8;
            case FOUNDATION -> 0.7;
            default -> 1;
        };
    }

    /** Multiplicateur de vitesse : 1 ; 1 ; 1,2 ; 1,4. */
    public static double speedFactor(int level) {
        return switch (level) {
            case NEURAL -> 1.2;
            case FOUNDATION -> 1.4;
            default -> 1;
        };
    }
}
