package fr.pioneers.materials;

import fr.pioneers.Pioneers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.TierSortingRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Materiaux d'outils (fiches {@code TIERS} du generateur) : tiers 0 a 5, plusieurs materiaux par tier, chacun avec son profil (rapide,
 * durable, polyvalent...). Niveaux de minage : 1 = pierre, 2 = fer, 3 = diamant, 4 = netherite. Classes dans l'ordre de Forge
 * ({@link TierSortingRegistry}) : silex entre bois et pierre, cuivre entre pierre et fer, fer et bronze entre fer et diamant, tier 2 entre
 * diamant et netherite, tiers 3 a 5 apres la netherite.
 */
public final class PiTiers {

    private PiTiers() {
    }

    private static final Map<String, Tier> TIERS = new HashMap<>();
    private static final Map<String, GeneratedMaterials.TierDef> DEFS = new HashMap<>();

    static {
        for (GeneratedMaterials.TierDef def : GeneratedMaterials.TIERS) {
            TagKey<Block> needs = switch (def.level()) {
                case 0 -> BlockTags.create(Pioneers.rl("needs_" + def.id() + "_tool"));
                case 1 -> BlockTags.NEEDS_STONE_TOOL;
                case 2 -> BlockTags.NEEDS_IRON_TOOL;
                case 3 -> BlockTags.NEEDS_DIAMOND_TOOL;
                default -> Tags.Blocks.NEEDS_NETHERITE_TOOL;
            };
            Ingredient repair = Ingredient.EMPTY;
            Tier tier = new ForgeTier(def.level(), def.uses(), def.speed(), def.damage(), def.enchant(), needs, () -> repair);
            List<Object> after;
            List<Object> before;
            if (def.id().equals("flint")) {
                after = List.of(Tiers.WOOD);
                before = List.of(Tiers.STONE);
            } else if (def.level() <= 1) {
                after = List.of(Tiers.STONE);
                before = List.of(Tiers.IRON);
            } else if (def.level() == 2) {
                after = List.of(Tiers.IRON);
                before = List.of(Tiers.DIAMOND);
            } else if (def.level() == 3) {
                after = List.of(Tiers.DIAMOND);
                before = List.of(Tiers.NETHERITE);
            } else {
                after = List.of(Tiers.NETHERITE);
                before = List.of();
            }
            TierSortingRegistry.registerTier(tier, Pioneers.rl(def.id()), after, before);
            TIERS.put(def.id(), tier);
            DEFS.put(def.id(), def);
        }
    }

    public static Tier tier(String id) {
        Tier tier = TIERS.get(id);
        return tier == null ? Tiers.STONE : tier;
    }

    /** Fiche du materiau d'outil (tier, profil, vitesse de geste), ou null. */
    @Nullable
    public static GeneratedMaterials.TierDef def(String id) {
        return DEFS.get(id);
    }

    /** Portee du marteau de prospecteur : 64 blocs jusqu'au tier 2, puis 128, 160, 192. */
    public static int prospectorRange(String id) {
        GeneratedMaterials.TierDef def = DEFS.get(id);
        int tier = def == null ? 0 : def.tier();
        return tier <= 2 ? 64 : 64 + 32 * (tier - 1);
    }

    static void init() {
    }
}
