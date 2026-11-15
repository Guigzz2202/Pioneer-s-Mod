package fr.pioneers.core.recipe;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

/**
 * MAJ Recettes (lot 7) : verrou de recette d'une machine ({@link RecipeLockable}) : l'identifiant de la recette choisie, sauve en NBT
 * ({@link #NBT}), envoye au client, garde dans l'objet quand on casse la machine ({@link #stampDrops} : {@code BlockEntityTag}, relu par
 * Minecraft a la pose) et retrouve parmi les recettes de la machine sans recherche (resultat garde tant que la liste est la meme).
 */
public final class RecipeLock {

    /** Cle NBT du verrou (dans la machine et sous {@code BlockEntityTag} dans l'objet). */
    public static final String NBT = "lockedRecipe";

    @Nullable
    private ResourceLocation id;
    @Nullable
    private List<RecipeChoice> resolvedIn;
    @Nullable
    private ResourceLocation resolvedId;
    @Nullable
    private RecipeChoice resolved;

    @Nullable
    public ResourceLocation id() {
        return id;
    }

    public boolean isSet() {
        return id != null;
    }

    /** Pose ({@code id}) ou retire ({@code null}) le verrou ; vrai s'il a change. */
    public boolean set(@Nullable ResourceLocation next) {
        if (next == null ? id == null : next.equals(id)) {
            return false;
        }
        id = next;
        return true;
    }

    /** La recette verrouillee parmi ces recettes (null : aucun verrou, ou recette inconnue de la machine). Sans recherche si rien n'a change. */
    @Nullable
    public RecipeChoice resolve(List<RecipeChoice> choices) {
        if (id == null) {
            return null;
        }
        if (choices != resolvedIn || !id.equals(resolvedId)) {
            resolvedIn = choices;
            resolvedId = id;
            resolved = RecipeLockable.find(choices, id);
        }
        return resolved;
    }

    // ------------------------------------------------------------------
    // Sauvegarde, synchronisation
    // ------------------------------------------------------------------

    public void save(CompoundTag tag) {
        if (id != null) {
            tag.putString(NBT, id.toString());
        }
    }

    public void load(CompoundTag tag) {
        id = tag.contains(NBT, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(NBT)) : null;
        //1.0.4-beta (lot 23) : verrou pose avant le renommage des cles de machine (dossiers de recettes renommes)
        if (id != null) {
            id = fr.pioneers.core.common.PiRemaps.recipeId(id);
        }
    }

    /** Toujours ecrit (texte vide = aucun verrou) : un verrou retire se voit aussi chez le client. */
    public void writeClient(CompoundTag tag) {
        tag.putString(NBT, id == null ? "" : id.toString());
    }

    public void readClient(CompoundTag tag) {
        if (tag.contains(NBT, Tag.TAG_STRING)) {
            String s = tag.getString(NBT);
            id = s.isEmpty() ? null : ResourceLocation.tryParse(s);
        }
    }

    // ------------------------------------------------------------------
    // Objet de la machine cassee
    // ------------------------------------------------------------------

    /** Verrou garde dans l'objet (null : aucun). */
    @Nullable
    public static ResourceLocation of(ItemStack stack) {
        CompoundTag be = BlockItem.getBlockEntityData(stack);
        return be != null && be.contains(NBT, Tag.TAG_STRING) ? ResourceLocation.tryParse(be.getString(NBT)) : null;
    }

    /** Copie le verrou dans l'objet ({@code BlockEntityTag}, relu par Minecraft quand on repose la machine). */
    public static void stamp(ItemStack stack, ResourceLocation id) {
        CompoundTag be = stack.getOrCreateTagElement(BlockItem.BLOCK_ENTITY_TAG);
        be.putString(NBT, id.toString());
    }

    /**
     * Objets d'une machine cassee : le bloc lui-meme garde le verrou de sa machine (comme les reglages conserves des buffers). Appele par
     * {@code getDrops} des blocs a verrou : le code couvre toutes les familles, quelle que soit la table de butin generee.
     */
    public static List<ItemStack> stampDrops(List<ItemStack> drops, BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RecipeLockable lockable) {
            ResourceLocation locked = lockable.recipeLock().id();
            if (locked != null) {
                for (ItemStack stack : drops) {
                    if (stack.getItem() == state.getBlock().asItem()) {
                        stamp(stack, locked);
                    }
                }
            }
        }
        return drops;
    }
}
