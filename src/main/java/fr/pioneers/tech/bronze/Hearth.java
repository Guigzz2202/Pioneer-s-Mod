package fr.pioneers.tech.bronze;

import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeHooks;

/**
 * Foyer commun des fours de l'age du bronze : brule le combustible de sa case (duree vanilla), annonce sa qualite (le charbon de terre chauffe
 * plus que le bois) et laisse de la cendre de bois quand on brule du bois.
 */
public class Hearth {

    private int burn, burnMax;
    /** Bonus de temperature du combustible en cours (degres C). */
    private int bonus;
    private boolean woody;

    public boolean lit() {
        return burn > 0;
    }

    public double ratio() {
        return burnMax <= 0 ? 0 : burn / (double) burnMax;
    }

    /** 1.0.3-beta (lot 14) : ticks de combustion restants de l'objet en cours. */
    public int remaining() {
        return Math.max(0, burn);
    }

    public int bonus() {
        return bonus;
    }

    /**
     * Un tick du foyer : {@code wanted} = la machine a besoin de feu (sinon on n'allume pas de nouveau combustible) ; {@code speed} = cadence de
     * combustion (tirage, soufflet). Renvoie vrai si de la cendre de bois tombe (le bois brule s'est consume).
     */
    public boolean tick(ItemSlot fuel, boolean wanted, double speed) {
        return burnStep(fuel, wanted, Math.max(1, (int) Math.round(speed)));
    }

    /**
     * 1.0.3-beta (lot 14) : combustion a cadence EXACTE (chaudiere : {@code speed} = tirage, fractionnaire) : le reste d'un tick est reporte
     * au suivant. Avec l'arrondi de {@link #tick}, un tirage de 1,6 brulait 2 et un tirage de 0,4 brulait 1 : la chaleur par objet changeait
     * avec la cheminee (cheminee de 8 = 25 % de perte, sans cheminee = 60 %) ; ici un objet donne toujours la meme chaleur.
     */
    public boolean tickExact(ItemSlot fuel, boolean wanted, double speed) {
        carry += Math.max(0, speed);
        int whole = (int) Math.floor(carry);
        carry -= whole;
        return burnStep(fuel, wanted, whole);
    }

    /** Report de {@link #tickExact} (non sauve : moins d'un tick de combustion). */
    private double carry;

    /** 1.0.3-beta (lot 14) : bonus de chaleur d'un combustible (%) : charbon 60, charbon de bois 30, lave 80, le reste 0 (lu aussi par JEI). */
    public static int bonusOf(ItemStack stack) {
        return stack.is(PiVanillaMaterials.COAL.get()) || stack.is(PiVanillaMaterials.COAL_BLOCK.asItem()) ? 60
              : stack.is(PiVanillaMaterials.CHARCOAL.get()) || stack.is(PiVanillaMaterials.CHARCOAL_BLOCK.asItem()) ? 30 : stack.is(Items.LAVA_BUCKET) ? 80 : 0;
    }

    private boolean burnStep(ItemSlot fuel, boolean wanted, int step) {
        boolean ash = false;
        if (burn > 0) {
            burn -= step;
            if (burn <= 0) {
                ash = woody;
                burn = 0;
            }
        }
        if (burn <= 0 && wanted && !fuel.isEmpty()) {
            ItemStack stack = fuel.getStack();
            int time = ForgeHooks.getBurnTime(stack, null);
            if (time > 0) {
                burn = burnMax = time;
                bonus = bonusOf(stack);
                woody = stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS) || stack.is(Items.STICK);
                ItemStack rest = stack.getCraftingRemainingItem();
                stack = stack.copy();
                stack.shrink(1);
                fuel.setStack(stack.isEmpty() ? rest : stack);
            }
        }
        return ash;
    }

    public void save(CompoundTag tag) {
        tag.putInt("burn", burn);
        tag.putInt("burnMax", burnMax);
        tag.putInt("bonus", bonus);
        tag.putBoolean("woody", woody);
    }

    public void load(CompoundTag tag) {
        burn = tag.getInt("burn");
        burnMax = tag.getInt("burnMax");
        bonus = tag.getInt("bonus");
        woody = tag.getBoolean("woody");
    }
}
