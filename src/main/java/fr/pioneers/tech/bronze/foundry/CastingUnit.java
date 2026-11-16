package fr.pioneers.tech.bronze.foundry;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 8b) : UNITE de la table de coulee, code de l'ancienne {@code CastingTableTile} qui ne fait plus que la porter (bloc,
 * rendu). Sans energie motrice : le metal est verse par le fourneau a creuset voisin (ou le fondeur d'alliages a vapeur), il rougeoie, fige,
 * et la piece attend dans la sortie ; le moule ne s'use pas (1.0.5-beta, lot 46 : outillage incassable en machine). Disposition de procede ({@link StandardLayout#of} : moule a gauche, fleche du figeage,
 * piece a droite). Sauvegarde au format d'avant : {@code slots} (tile), {@code metal}, {@code amount}, {@code cool}, {@code coolMax},
 * {@code pending} ; client : metal, quantite, figeage, moule ({@code mold}) et piece ({@code out}).
 */
public class CastingUnit extends MachineUnit {

    public final StandardLayout layout = StandardLayout.of(new StandardLayout.Counts(1, 1, 0, 0, 0, 0));
    final ItemSlot mold, output;
    private String metal = "";
    private int amount, cool, coolMax;
    private ItemStack pending = ItemStack.EMPTY;

    public CastingUnit(UnitHost machine) {
        super(machine);
        int[] m = layout.itemIn.get(0), o = layout.itemOut.get(0);
        mold = machine.slot(new ItemSlot(() -> 1, s -> s.getItem() instanceof MoldItem mi && tableMold(mi), (s, a) -> amount == 0,
              (s, a) -> amount == 0 && !a.external(), this::changed, m[0], m[1]));
        output = machine.slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, o[0], o[1]));
    }

    /** Moule qui se pose sur la table : ni le creuset, ni (1.0.2-beta) le moule d'enclume (il se pose au sol, bloc complet). */
    public static boolean tableMold(MoldItem mold) {
        return !mold.shape.equals("crucible") && !mold.shape.equals("anvil");
    }

    private void changed() {
        machine.changed();
        machine.markSync();
    }

    public String metal() {
        return metal;
    }

    public int amount() {
        return amount;
    }

    /** 0 en fusion -> 1 fige. */
    public float solid() {
        return coolMax <= 0 ? 1 : Math.min(1, cool / (float) coolMax);
    }

    public boolean busy() {
        return amount > 0 || !output.isEmpty();
    }

    /** Recette de coulee pour ce moule et ce metal (ou null). */
    @Nullable
    public CastingRecipe recipeFor(String metalId) {
        Level level = level();
        if (level == null || mold.isEmpty()) {
            return null;
        }
        for (CastingRecipe recipe : PiRecipes.sorted(level, PiBronze.CASTING.get())) {
            if (recipe.mold() == mold.getStack().getItem() && recipe.metal().equals(metalId)) {
                return recipe;
            }
        }
        return null;
    }

    /** Le fourneau verse : {@code mix} = composition versee (1.0.2-beta : plus de lingot impur, seul un metal ou un alliage se verse). */
    public void fill(CastingRecipe recipe, Map<String, Integer> mix) {
        metal = recipe.metal();
        amount = recipe.amount();
        cool = 0;
        coolMax = recipe.coolTicks();
        pending = recipe.result().copy();
        machine.markSync();
        machine.changed();
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level) || amount <= 0) {
            return;
        }
        BlockPos pos = pos();
        cool++;
        if (cool % 10 == 0 && cool < coolMax) {
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.15, 0, 0.15, 0.01);
        }
        if (cool >= coolMax && output.insert(pending.copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
            output.insert(pending.copy(), Action.EXECUTE, Automation.INTERNAL);
            //1.0.5-beta (lot 46, retour 62 bis : « les moules doivent etre incassables dans les machines ») : le moule de la table ne s'use plus
            //(il ne s'use que pose au sol, geste a la main : PlacedMoldTile)
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
            BronzeEvents.award(level, pos, "cast");
            amount = 0;
            pending = ItemStack.EMPTY;
            metal = "";
            machine.changed();
            machine.markSync();
        } else if (cool % 20 == 0) {
            machine.markSync();
        }
    }

    /** Client : le metal fige entre deux envois. */
    void clientTick() {
        if (amount > 0 && cool < coolMax) {
            cool++;
        }
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putString("metal", metal);
        tag.putInt("amount", amount);
        tag.putInt("cool", cool);
        tag.putInt("coolMax", coolMax);
        tag.put("mold", mold.getStack().save(new CompoundTag()));
        tag.put("out", output.getStack().save(new CompoundTag()));
    }

    @Override
    public void readClient(CompoundTag tag) {
        metal = tag.getString("metal");
        amount = tag.getInt("amount");
        cool = tag.getInt("cool");
        coolMax = tag.getInt("coolMax");
        mold.setStack(ItemStack.of(tag.getCompound("mold")));
        output.setStack(ItemStack.of(tag.getCompound("out")));
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("metal", metal);
        tag.putInt("amount", amount);
        tag.putInt("cool", cool);
        tag.putInt("coolMax", coolMax);
        tag.put("pending", pending.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        metal = tag.getString("metal");
        amount = tag.getInt("amount");
        cool = tag.getInt("cool");
        coolMax = tag.getInt("coolMax");
        pending = ItemStack.of(tag.getCompound("pending"));
    }
}
