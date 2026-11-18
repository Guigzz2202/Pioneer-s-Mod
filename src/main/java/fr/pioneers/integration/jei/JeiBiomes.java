package fr.pioneers.integration.jei;

import fr.pioneers.world.geology.DepositType;
import fr.pioneers.space.SolarSystem;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.0.3-beta (correctif, Guillaume : « dans JEI, pour les pages Gisements, il faut prendre les noms de biomes exacts en francais quand le jeu
 * est en francais, pareil en anglais ») : les gisements visent des FAMILLES de biomes (tags {@code #minecraft:is_forest}…) ; la page
 * affichait le chemin du tag (« forest »). Ici les tags sont deplies en biomes reels du monde charge (registre des biomes du client, mods
 * compris) et chaque biome prend son nom traduit ({@code biome.<ns>.<id>}), trie dans la langue du jeu ; la dimension prend le nom de
 * son astre. Calcule une fois par gisement et par monde.
 */
final class JeiBiomes {

    private static final Map<Object, List<Component>> CACHE = new IdentityHashMap<>();
    private static Object cacheLevel;

    private JeiBiomes() {
    }

    /** Biomes reels d'une liste de poids (tags deplies, sans doublon, tries par nom affiche). */
    static List<Component> names(Object owner, List<DepositType.Weighted> weights) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != cacheLevel) {
            CACHE.clear();
            cacheLevel = mc.level;
        }
        List<Component> cached = CACHE.get(owner);
        if (cached != null) {
            return cached;
        }
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        Registry<Biome> registry = mc.level == null ? null : mc.level.registryAccess().registryOrThrow(Registries.BIOME);
        for (DepositType.Weighted w : weights) {
            if (w.biome() != null) {
                ids.add(w.biome().location());
            } else if (w.tag() != null && registry != null) {
                registry.getTag(w.tag()).ifPresent(set -> {
                    for (Holder<Biome> h : set) {
                        h.unwrapKey().ifPresent(k -> ids.add(k.location()));
                    }
                });
            }
        }
        List<Component> out = new ArrayList<>();
        for (ResourceLocation id : ids) {
            out.add(Component.translatable(Util.makeDescriptionId("biome", id)));
        }
        if (out.isEmpty()) {
            //hors monde (ou tag inconnu) : le nom du tag, faute de mieux
            for (DepositType.Weighted w : weights) {
                ResourceLocation id = w.tag() != null ? w.tag().location() : w.biome() != null ? w.biome().location() : null;
                if (id != null) {
                    out.add(Component.literal(id.getPath().replace("is_", "").replace('_', ' ')));
                }
            }
        }
        out.sort((a, b) -> a.getString().compareToIgnoreCase(b.getString()));
        CACHE.put(owner, out);
        return out;
    }

    /** « Forêt, Plaines, Taïga » (separateur de la langue : la virgule). */
    static Component joined(List<Component> names) {
        StringBuilder s = new StringBuilder();
        for (Component c : names) {
            s.append(s.length() == 0 ? "" : ", ").append(c.getString());
        }
        return Component.literal(s.toString());
    }

    /** Infobulle : « Biomes » puis un biome par ligne. */
    static List<Component> tooltip(Object owner, List<DepositType.Weighted> weights) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("jei.pioneers.deposit.biomes"));
        for (Component c : names(owner, weights)) {
            out.add(Component.literal("  ").append(c).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    /** Nom de la dimension : son astre (Terre, Lune…), sinon le Nether ou l'End de Minecraft, sinon son identifiant. */
    static Component dimension(ResourceKey<Level> dim) {
        if (dim == Level.NETHER) {
            return Component.translatable("advancements.nether.root.title");
        }
        if (dim == Level.END) {
            return Component.translatable("advancements.end.root.title");
        }
        SolarSystem.Body body = SolarSystem.fromDimension(dim);
        if (body != null) {
            return Component.translatable(body.getTranslationKey());
        }
        return Component.literal(dim.location().getPath().replace('_', ' '));
    }
}
