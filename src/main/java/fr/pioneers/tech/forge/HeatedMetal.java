package fr.pioneers.tech.forge;

import fr.pioneers.tech.materials.GeneratedMaterials;
import fr.pioneers.core.registries.PiForge;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * API du metal chauffe ({@code pioneers:heated_metal}, objet generique) : lingot sorti du foyer, piece en cours de forge, piece forgee a
 * tremper. Tout passe par ces methodes statiques : ne jamais lire les cles NBT a la main.
 * <p>
 * NBT (racine de l'objet) :
 * <ul>
 * <li>{@code metal} (String) : id du metal ({@link ForgeMetals}) ;</li>
 * <li>{@code mb} (int) : quantite de matiere (1 lingot = 144 mB = 32 pixels) ;</li>
 * <li>{@code temp} (float) : temperature en degres C a l'instant {@code stamp} (long, heure du monde) ; la temperature actuelle est calculee
 * a la lecture (refroidissement exponentiel vers {@link ForgeMetals#AMBIENT_C}, constante {@link #tauTicks}) : aucun tick necessaire ;</li>
 * <li>{@code work} (Compound, facultatif) : forge en cours : {@code mask} (long[4], {@link SmithingGrid}), {@code recipe} (String, id de la
 * recette {@code pioneers:smithing}), {@code hits} (int, coups donnes), {@code scrap} (int, pixels coupes) ;</li>
 * <li>{@code shape} (Compound, facultatif) : la piece finie ({@link ItemStack#save}) que donne la trempe ;</li>
 * <li>{@code hand} (byte, facultatif) : vrai si forgee a la main (bonus « Forge main », pose par le minijeu, lu par la trempe).</li>
 * </ul>
 */
public final class HeatedMetal {

    public static final String METAL = "metal", MB = "mb", TEMP = "temp", STAMP = "stamp", WORK = "work", SHAPE = "shape", HAND = "hand";
    public static final String MASK = "mask", RECIPE = "recipe", HITS = "hits", SCRAP = "scrap";
    /** Constante de temps du refroidissement a l'air d'un lingot (ticks) : de 900 a 400 degres en ~25 s. */
    public static final float TAU_TICKS = 7200F; //1.1.1 : 6 min (30 s avant : on n'avait pas le temps de forger)

    private HeatedMetal() {
    }

    /** Refroidissement exponentiel vers l'ambiante. */
    public static float cool(float tempC, long elapsedTicks, float tauTicks) {
        if (elapsedTicks <= 0 || tempC <= ForgeMetals.AMBIENT_C) {
            return tempC;
        }
        return ForgeMetals.AMBIENT_C + (tempC - ForgeMetals.AMBIENT_C) * (float) Math.exp(-elapsedTicks / (double) tauTicks);
    }

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.is(PiForge.HEATED_METAL.get());
    }

    /** Metal chauffe brut ({@code level} null = heure 0). */
    public static ItemStack create(String metal, int mb, float tempC, @Nullable Level level) {
        ItemStack stack = new ItemStack(PiForge.HEATED_METAL.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(METAL, metal);
        tag.putInt(MB, mb);
        heat(stack, tempC, level);
        return stack;
    }

    /**
     * Metal chauffe tire d'un objet de metal (lingot, ou tout objet de la table de fusion) ; vide si l'objet n'est pas un metal. Un metal
     * chauffe est simplement recopie et rechauffe (la forge en cours est gardee).
     */
    public static ItemStack from(ItemStack source, float tempC, @Nullable Level level) {
        if (is(source)) {
            ItemStack copy = source.copyWithCount(1);
            heat(copy, tempC, level);
            return copy;
        }
        GeneratedMaterials.Melt melt = ForgeMetals.meltOf(source);
        return melt == null ? ItemStack.EMPTY : create(melt.metal(), melt.mb(), tempC, level);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    public static String metal(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(METAL);
    }

    /** Quantite de matiere (mB). */
    public static int amount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(MB);
    }

    /** Matiere en pixels de la grille (32 par lingot). */
    public static int pixels(ItemStack stack) {
        return amount(stack) * ForgeMetals.PIXELS_PER_INGOT / ForgeMetals.INGOT_MB;
    }

    /** Constante de temps du refroidissement : plus de matiere, plus lent (racine cubique de la masse). */
    /** 1.9 : facteur de la constante de refroidissement pose par le forgeron (skill Forge et metallurgie). */
    public static final String TAU_FACTOR = "pioneers_tau";

    public static void setTauFactor(ItemStack stack, float k) {
        if (HeatedMetal.is(stack)) {
            stack.getOrCreateTag().putFloat(TAU_FACTOR, Math.max(0.5F, Math.min(2F, k)));
        }
    }

    public static float tauTicks(ItemStack stack) {
        //1.9 (skill Forge) : facteur du forgeron qui a travaille la piece en dernier (NBT pioneers_tau, 1 par defaut)
        CompoundTag tag = stack.getTag();
        float k = tag != null && tag.getFloat(TAU_FACTOR) > 0 ? tag.getFloat(TAU_FACTOR) : 1F;
        return k * TAU_TICKS * (float) Math.cbrt(Math.max(16, amount(stack)) / (double) ForgeMetals.INGOT_MB);
    }

    /** Temperature a l'heure donnee (degres C). */
    public static float temperatureAt(ItemStack stack, long gameTime) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return ForgeMetals.AMBIENT_C;
        }
        return cool(tag.getFloat(TEMP), gameTime - tag.getLong(STAMP), tauTicks(stack));
    }

    /** Temperature actuelle ({@code level} null : la temperature enregistree, sans refroidissement). */
    public static float temperatureNow(ItemStack stack, @Nullable Level level) {
        if (level == null) {
            CompoundTag tag = stack.getTag();
            return tag == null ? ForgeMetals.AMBIENT_C : tag.getFloat(TEMP);
        }
        return temperatureAt(stack, level.getGameTime());
    }

    /** Vrai au-dessus de {@link ForgeMetals#COLD_C}. */
    public static boolean isHot(ItemStack stack, @Nullable Level level) {
        return temperatureNow(stack, level) > ForgeMetals.COLD_C;
    }

    /** Vrai si les coups de marteau font effet (temperature de travail du metal atteinte). */
    public static boolean isWorkable(ItemStack stack, @Nullable Level level) {
        return temperatureNow(stack, level) >= ForgeMetals.workingC(metal(stack));
    }

    /** Vrai si la piece brule la main qui la tient sans pince (au-dessus de {@link ForgeMetals#BURN_C}). */
    public static boolean burnsHand(ItemStack stack, @Nullable Level level) {
        return temperatureNow(stack, level) > ForgeMetals.BURN_C;
    }

    // ------------------------------------------------------------------
    // Ecriture
    // ------------------------------------------------------------------

    /** Fixe la temperature maintenant (chauffe au foyer, ou refroidissement force). */
    public static void heat(ItemStack stack, float tempC, @Nullable Level level) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putFloat(TEMP, tempC);
        tag.putLong(STAMP, level == null ? 0 : level.getGameTime());
    }

    /** Fige la temperature actuelle dans le NBT (a faire avant de changer de constante de temps ou de comparer deux piles). */
    public static void settle(ItemStack stack, @Nullable Level level) {
        if (level != null) {
            heat(stack, temperatureNow(stack, level), level);
        }
    }

    /** Fait perdre {@code deltaC} degres maintenant (un coup de marteau). */
    public static void loseHeat(ItemStack stack, @Nullable Level level, float deltaC) {
        heat(stack, Math.max(ForgeMetals.AMBIENT_C, temperatureNow(stack, level) - deltaC), level);
    }

    public static void setAmount(ItemStack stack, int mb) {
        stack.getOrCreateTag().putInt(MB, Math.max(0, mb));
    }

    // ------------------------------------------------------------------
    // Forge en cours (minijeu, marteau-pilon)
    // ------------------------------------------------------------------

    public static boolean hasWork(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(WORK, Tag.TAG_COMPOUND);
    }

    /** Grille en cours ; null s'il n'y a pas de forge en cours. */
    @Nullable
    public static SmithingGrid work(ItemStack stack) {
        if (!hasWork(stack)) {
            return null;
        }
        return SmithingGrid.of(stack.getTag().getCompound(WORK).getLongArray(MASK));
    }

    /** Recette visee par la forge en cours ; null sinon. */
    @Nullable
    public static ResourceLocation workRecipe(ItemStack stack) {
        if (!hasWork(stack)) {
            return null;
        }
        String id = stack.getTag().getCompound(WORK).getString(RECIPE);
        return id.isEmpty() ? null : ResourceLocation.tryParse(id);
    }

    /** Donnees brutes de la forge en cours ({@code hits}, {@code scrap}...) ; compose vide sinon (lecture seule). */
    public static CompoundTag workTag(ItemStack stack) {
        return hasWork(stack) ? stack.getTag().getCompound(WORK) : new CompoundTag();
    }

    /** Ecrit la forge en cours (grille et recette) ; garde coups et chute deja comptes. */
    public static void setWork(ItemStack stack, SmithingGrid grid, @Nullable ResourceLocation recipe) {
        CompoundTag work = stack.getOrCreateTag().getCompound(WORK);
        work.putLongArray(MASK, grid.toArray());
        work.putString(RECIPE, recipe == null ? "" : recipe.toString());
        stack.getTag().put(WORK, work);
    }

    /** Ajoute un coup (et des pixels coupes) a la forge en cours. */
    public static void countHit(ItemStack stack, int scrapPixels) {
        if (!hasWork(stack)) {
            return;
        }
        CompoundTag work = stack.getTag().getCompound(WORK);
        work.putInt(HITS, work.getInt(HITS) + 1);
        work.putInt(SCRAP, work.getInt(SCRAP) + Math.max(0, scrapPixels));
    }

    public static void clearWork(ItemStack stack) {
        if (stack.getTag() != null) {
            stack.getTag().remove(WORK);
        }
    }

    // ------------------------------------------------------------------
    // Piece finie (a tremper)
    // ------------------------------------------------------------------

    /** Piece finie que donnera la trempe ; vide s'il n'y en a pas. */
    public static ItemStack shape(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(SHAPE, Tag.TAG_COMPOUND) ? ItemStack.EMPTY : ItemStack.of(tag.getCompound(SHAPE));
    }

    public static void setShape(ItemStack stack, ItemStack result) {
        if (result.isEmpty()) {
            if (stack.getTag() != null) {
                stack.getTag().remove(SHAPE);
            }
        } else {
            stack.getOrCreateTag().put(SHAPE, result.save(new CompoundTag()));
        }
    }

    public static boolean handForged(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(HAND);
    }

    public static void setHandForged(ItemStack stack, boolean hand) {
        stack.getOrCreateTag().putBoolean(HAND, hand);
    }

    // ------------------------------------------------------------------
    // Trempe et refroidissement
    // ------------------------------------------------------------------

    /**
     * Trempe (eau, chaudron, bac de trempe) : une piece finie donne sa piece ({@code shape}) ; un lingot chauffe sans forge redevient lingots
     * (et pepites) ; une piece en cours de forge reste du metal chauffe, refroidi (a rechauffer). Toujours une pile d'UN objet au plus : les
     * pepites en plus sont dans {@link #quenchExtras}. Ne consomme rien ({@code hot} n'est pas modifie).
     */
    public static ItemStack quench(ItemStack hot, @Nullable Level level) {
        ItemStack part = shape(hot);
        if (!part.isEmpty()) {
            return part;
        }
        if (!hasWork(hot)) {
            List<ItemStack> raw = ForgeMetals.asIngotsAndNuggets(metal(hot), amount(hot));
            if (!raw.isEmpty()) {
                return raw.get(0);
            }
        }
        ItemStack cold = hot.copyWithCount(1);
        heat(cold, ForgeMetals.AMBIENT_C, level);
        return cold;
    }

    /** Ce que la trempe donne en plus de {@link #quench} (pepites du reste d'un lingot chauffe brut). */
    public static List<ItemStack> quenchExtras(ItemStack hot) {
        if (!shape(hot).isEmpty() || hasWork(hot)) {
            return List.of();
        }
        List<ItemStack> raw = ForgeMetals.asIngotsAndNuggets(metal(hot), amount(hot));
        return raw.size() > 1 ? raw.subList(1, raw.size()) : List.of();
    }

    /**
     * 1.0.5-beta (lot 15, Guillaume : « pieces de forge chaudes : vraie texture de l'objet forge avec un aspect rougeatre, plus l'aspect de
     * billette ») : l'objet dont la piece chaude prend l'apparence (le rendu le teinte selon sa chaleur) : la piece finie a tremper, sinon le
     * resultat de la recette en cours de forge (pour ce metal), sinon - lingot chauffe sans forge - le lingot du metal (ou sa pepite) ; vide
     * pour une forge sans recette choisie ou un metal inconnu (le rendu garde alors la billette). Toujours une pile d'UN objet.
     */
    public static ItemStack lookOf(ItemStack stack, @Nullable Level level) {
        ItemStack part = shape(stack);
        if (!part.isEmpty()) {
            return part.copyWithCount(1);
        }
        if (hasWork(stack)) {
            ResourceLocation id = workRecipe(stack);
            if (id != null && level != null) {
                Recipe<?> recipe = level.getRecipeManager().byKey(id).orElse(null);
                if (recipe instanceof SmithingRecipe smithing) {
                    ItemStack result = smithing.result(metal(stack));
                    if (!result.isEmpty()) {
                        return result.copyWithCount(1);
                    }
                }
            }
            return ItemStack.EMPTY;
        }
        List<ItemStack> raw = ForgeMetals.asIngotsAndNuggets(metal(stack), amount(stack));
        return raw.isEmpty() ? ItemStack.EMPTY : raw.get(0).copyWithCount(1);
    }

    /**
     * Ce que devient une piece refroidie a l'air : un lingot chauffe brut redevient lingot ; une piece forgee ou en cours reste du metal chauffe
     * froid (« brut de forge », a rechauffer). Renvoie {@code stack} lui-meme si rien ne change.
     */
    public static ItemStack coldForm(ItemStack stack) {
        if (hasWork(stack) || !shape(stack).isEmpty()) {
            return stack;
        }
        List<ItemStack> raw = ForgeMetals.asIngotsAndNuggets(metal(stack), amount(stack));
        return raw.size() == 1 ? raw.get(0) : stack;
    }
}
