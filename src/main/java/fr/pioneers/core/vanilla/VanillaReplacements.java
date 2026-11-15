package fr.pioneers.core.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Remplacement total des materiaux et outils vanilla (1.0, decision de Guillaume) : table {@code data/pioneers/vanilla_replacements.json}
 * (ecrite par tools/gc/core/devanilla.py depuis vanilla_map.py). Tout objet vanilla de la table qui entre dans l'inventaire d'un joueur,
 * tombe au sol, sort d'un butin ou d'un echange est converti en l'objet du mod ({@link VanillaEvents}, {@link ReplaceVanillaModifier}) ;
 * les objets vanilla sont caches de JEI et des onglets creatifs.
 */
public final class VanillaReplacements {

    private static Map<Item, Item> table;
    /**
     * 1.0.5-beta (lot 40, retour 50 de Guillaume : « la redstone ne s'obtient qu'avec la foreuse tier 1 ou superieure ») : objets RETIRES des
     * butins naturels (groupe {@code removed} de la table : redstone, bloc de redstone), au lieu d'etre convertis ({@link #filterLoot}).
     */
    private static Set<Item> removed;

    private VanillaReplacements() {
    }

    private static synchronized Map<Item, Item> table() {
        if (table == null) {
            Map<Item, Item> map = new IdentityHashMap<>();
            Map<String, String> ids = new HashMap<>();
            JsonObject root = null;
            try (InputStream in = VanillaReplacements.class.getResourceAsStream("/data/pioneers/vanilla_replacements.json")) {
                if (in != null) {
                    root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String group : new String[]{"materials", "tools", "farm"}) {
                        if (root.has(group)) {
                            root.getAsJsonObject(group).entrySet().forEach(e -> ids.put(e.getKey(), e.getValue().getAsString()));
                        }
                    }
                }
            } catch (Exception e) {
                Pioneers.LOGGER.error("Table de remplacement des objets vanilla illisible", e);
            }
            ids.forEach((from, to) -> {
                Item a = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(from));
                Item b = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(to));
                if (a != null && b != null && b != Items.AIR) {
                    map.put(a, b);
                }
            });
            // la liste « removed » peut nommer l'objet vanilla ou son remplacant (devanilla reecrit les ids du fichier) : les deux sont retires
            Set<Item> gone = Collections.newSetFromMap(new IdentityHashMap<>());
            if (root != null && root.has("removed")) {
                root.getAsJsonArray("removed").forEach(e -> {
                    Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(e.getAsString()));
                    if (it != null && it != Items.AIR) {
                        gone.add(it);
                        Item to = map.get(it);
                        if (to != null) {
                            gone.add(to);
                        }
                        map.forEach((a, b) -> {
                            if (b == it) {
                                gone.add(a);
                            }
                        });
                    }
                });
            }
            removed = Collections.unmodifiableSet(gone);
            table = Collections.unmodifiableMap(map);
        }
        return table;
    }

    /** 1.0.5-beta (lot 40) : objets retires des butins naturels (redstone : seulement par les foreuses). */
    public static Set<Item> removedFromLoot() {
        table();
        return removed;
    }

    /**
     * 1.0.5-beta (lot 40, retour 50) : un butin NATUREL (coffres des structures, creatures, cadeaux, peche, archeologie, troc, et le minerai
     * vanilla : tables {@code blocks/*_ore}) perd les objets retires ; jamais le butin d'un bloc pose (fil de redstone, bloc de redstone : le
     * joueur reprend ce qu'il a pose). Table inconnue : naturel.
     */
    public static boolean naturalLoot(@Nullable ResourceLocation table) {
        if (table == null) {
            return true;
        }
        String path = table.getPath();
        return !path.startsWith("blocks/") || path.endsWith("_ore");
    }

    /**
     * 1.0.5-beta (lot 40) : convertit un butin, puis retire d'un butin naturel les objets retires (vanilla ou remplacant : la table les
     * contient tous les deux ; appele par {@link ReplaceVanillaModifier}).
     */
    public static void filterLoot(List<ItemStack> loot, @Nullable ResourceLocation table) {
        loot.replaceAll(VanillaReplacements::convert);
        if (naturalLoot(table) && !removedFromLoot().isEmpty()) {
            loot.removeIf(stack -> !stack.isEmpty() && removed.contains(stack.getItem()));
        }
    }

    /** Objets vanilla remplaces. */
    public static Set<Item> replaced() {
        return table().keySet();
    }

    /** 1.0.1 : objet du mod qui remplace cet objet vanilla, ou null. */
    @Nullable
    public static Item replacementOf(Item item) {
        return table().get(item);
    }

    public static boolean isReplaced(ItemStack stack) {
        return !stack.isEmpty() && table().containsKey(stack.getItem());
    }

    /** Equivalent du mod (meme nombre, NBT gardes : enchantements, nom...), ou la pile elle-meme. */
    public static ItemStack convert(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        Item to = table().get(stack.getItem());
        if (to == null) {
            return stack;
        }
        ItemStack out = new ItemStack(to, stack.getCount());
        if (stack.hasTag()) {
            out.setTag(stack.getTag().copy());
            if (out.isDamageableItem() && stack.isDamageableItem()) {
                //Usure proportionnelle (les durabilites different)
                out.setDamageValue(Math.min(out.getMaxDamage() - 1, (int) Math.round((double) stack.getDamageValue() / stack.getMaxDamage() * out.getMaxDamage())));
            }
        }
        return out;
    }
}
