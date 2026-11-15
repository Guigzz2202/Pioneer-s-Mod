package fr.pioneers.tech.bronze.autofoundry;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.registries.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4) : UNITE du moule automatique (API Machine, portee par {@link AutoMoldTile}). L'empreinte choisie ({@link #form()}, etat
 * {@code form} du bloc) donne la recette de coulee ({@code pioneers:casting} du moule d'argile de meme forme) pour chaque metal. Le moule est
 * LIBRE ({@link #accepts}) quand il est vide et que sa sortie peut prendre la piece : la fonderie y verse alors la quantite de la recette
 * ({@link #receive}) ; plein, il fige pendant {@link #coolTicks} (duree de la recette x la chaleur du metal : l'etain fige deux fois plus vite
 * que le cuivre, le fer plus lentement), puis la piece passe dans la sortie (entonnoir, cable : par le dessous ou les cotes). Le moule ne
 * s'use pas. On change d'empreinte seulement moule vide.
 * <p>
 * Sauvegarde : {@code slots} (tile), {@code metal}, {@code mb}, {@code need}, {@code cool}, {@code coolMax}, {@code pending} ; client :
 * metal, quantite, besoin, figeage.
 */
public class AutoMoldUnit extends MachineUnit {

    /** Interface v2 : 6 boutons d'empreinte (3 x 2), fleche du figeage, piece. */
    public static final int FORMS_X = 30, FORMS_Y = StandardLayout.TOP + 8, BUTTON = 18, STEP = 20, OUT_X = 148, OUT_Y = StandardLayout.TOP + 22;
    public static final StandardLayout.Rect FORMS = new StandardLayout.Rect(FORMS_X - 1, FORMS_Y - 1, 3 * STEP, 2 * STEP);
    public static final StandardLayout.Rect ARROW = new StandardLayout.Rect(106, StandardLayout.TOP + 26, 28, 8);
    /** Valeurs nommees : etat (0 attend, 1 se remplit, 2 fige, 3 sortie pleine), quantite demandee, empreinte. */
    public static final UiValues.Key NEED = UiValues.Key.of("mold_need"), FORM = UiValues.Key.of("mold_form");
    public static final int WAITING = 0, FILLING = 1, COOLING = 2, BLOCKED = 3;

    final ItemSlot output;
    private String metal = "";
    private int mb, need, cool, coolMax;
    private ItemStack pending = ItemStack.EMPTY;

    public AutoMoldUnit(UnitHost machine) {
        super(machine);
        output = machine.slot(new ItemSlot(() -> 64, s -> true, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::changed, OUT_X, OUT_Y));
    }

    private void changed() {
        machine.changed();
        machine.markSync();
    }

    // ------------------------------------------------------------------
    // Etat
    // ------------------------------------------------------------------

    public AutoMoldBlock.Form form() {
        BlockState s = machine.state();
        return s != null && s.hasProperty(AutoMoldBlock.FORM) ? s.getValue(AutoMoldBlock.FORM) : AutoMoldBlock.Form.INGOT;
    }

    public String metal() {
        return metal;
    }

    public int amount() {
        return mb;
    }

    public int need() {
        return need;
    }

    /** 0 en fusion (ou vide) -> 1 fige. */
    public float solid() {
        return coolMax <= 0 ? 0 : Math.min(1, cool / (float) coolMax);
    }

    public boolean cooling() {
        return need > 0 && mb >= need;
    }

    public int status() {
        if (mb <= 0) {
            return WAITING;
        }
        if (mb < need) {
            return FILLING;
        }
        return cool >= coolMax ? BLOCKED : COOLING;
    }

    /** Recette de coulee de l'empreinte pour ce metal (ou null). */
    @Nullable
    public CastingRecipe recipeFor(String metalId) {
        Level level = level();
        if (level == null || metalId.isEmpty()) {
            return null;
        }
        var mold = form().moldItem();
        for (CastingRecipe recipe : PiRecipes.sorted(level, PiBronze.CASTING.get())) {
            if (recipe.mold() == mold && recipe.metal().equals(metalId)) {
                return recipe;
            }
        }
        return null;
    }

    /** Duree du figeage : celle de la recette x la chaleur du metal (fusion / 1 085 °C, bornee 0,5 .. 1,6). */
    public static int coolTicks(CastingRecipe recipe, String metalId) {
        double factor = Math.max(0.5, Math.min(1.6, Metals.meltingC(metalId) / 1085.0));
        return (int) Math.round(recipe.coolTicks() * factor);
    }

    /**
     * Ce que le moule demande de ce metal maintenant (mB ; 0 : il n'est pas libre) : vide, une recette et la place pour la piece dans la
     * sortie -> la quantite de la recette ; entame par le meme metal (coulee interrompue) -> ce qui manque.
     */
    public int wants(String metalId) {
        if (mb > 0) {
            return metal.equals(metalId) && !cooling() ? need - mb : 0;
        }
        CastingRecipe recipe = recipeFor(metalId);
        return recipe != null && output.insert(recipe.result().copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty() ? recipe.amount() : 0;
    }

    /** Le moule est-il libre pour une coulee de ce metal ? */
    public boolean accepts(String metalId) {
        return wants(metalId) > 0;
    }

    /** Casse : le metal d'une coulee entamee ou qui fige rend des lingots et des pepites (rien ne se perd). */
    public java.util.List<ItemStack> spill() {
        java.util.List<ItemStack> out = mb > 0 && !metal.isEmpty() ? fr.pioneers.tech.forge.ForgeMetals.asIngotsAndNuggets(metal, mb) : java.util.List.of();
        metal = "";
        mb = need = cool = coolMax = 0;
        pending = ItemStack.EMPTY;
        return out;
    }

    /**
     * La fonderie verse : {@code amount} mB de {@code metalId} (le debut fixe la recette, la suite complete) ; renvoie ce qui est pris. Plein, le
     * moule commence a figer.
     */
    public int receive(String metalId, int amount) {
        if (amount <= 0 || mb > 0 && !metal.equals(metalId) || cooling()) {
            return 0;
        }
        if (mb == 0) {
            CastingRecipe recipe = recipeFor(metalId);
            if (recipe == null) {
                return 0;
            }
            metal = metalId;
            need = recipe.amount();
            pending = recipe.result().copy();
            coolMax = coolTicks(recipe, metalId);
            cool = 0;
        }
        int take = Math.min(amount, need - mb);
        mb += take;
        changed();
        return take;
    }

    /** Change l'empreinte (moule vide seulement) ; vrai si c'est fait. */
    public boolean setForm(AutoMoldBlock.Form form) {
        Level level = level();
        BlockState s = machine.state();
        if (level == null || s == null || mb > 0 || !s.hasProperty(AutoMoldBlock.FORM) || s.getValue(AutoMoldBlock.FORM) == form) {
            return false;
        }
        level.setBlock(pos(), s.setValue(AutoMoldBlock.FORM, form), 3);
        changed();
        return true;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level) || !cooling()) {
            return;
        }
        BlockPos pos = pos();
        if (cool < coolMax) {
            cool++;
            if (cool % 10 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + form().top() / 16.0 + 0.05, pos.getZ() + 0.5, 1, 0.2, 0, 0.2, 0.01);
            }
            if (cool % 20 == 0) {
                machine.markSync();
            }
            return;
        }
        if (output.insert(pending.copy(), Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
            output.insert(pending.copy(), Action.EXECUTE, Automation.INTERNAL);
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
            BronzeEvents.award(level, pos, "auto_cast");
            BronzeEvents.award(level, pos, "mold_cast_" + metal);
            metal = "";
            mb = need = cool = coolMax = 0;
            pending = ItemStack.EMPTY;
            changed();
        }
    }

    /** Client : le metal fige entre deux envois. */
    void clientTick() {
        if (cooling() && cool < coolMax) {
            cool++;
        }
    }

    // ------------------------------------------------------------------
    // Interface, client, sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.PROGRESS_X1000, () -> Math.round(solid() * 1000)).add(UiValues.STATUS, this::status).add(UiValues.MOLTEN, () -> mb)
              .add(NEED, () -> need).add(FORM, () -> form().ordinal()).add(UiValues.DURATION, () -> coolMax);
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putString("metal", metal);
        tag.putInt("mb", mb);
        tag.putInt("need", need);
        tag.putInt("cool", cool);
        tag.putInt("coolMax", coolMax);
    }

    @Override
    public void readClient(CompoundTag tag) {
        metal = tag.getString("metal");
        mb = tag.getInt("mb");
        need = tag.getInt("need");
        cool = tag.getInt("cool");
        coolMax = tag.getInt("coolMax");
    }

    @Override
    public void save(CompoundTag tag) {
        writeClient(tag);
        tag.put("pending", pending.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        readClient(tag);
        pending = ItemStack.of(tag.getCompound("pending"));
    }
}
