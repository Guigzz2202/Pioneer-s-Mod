package fr.pioneers.core.tooltip;

import fr.pioneers.core.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0.4-beta (lot 28, Guillaume : « tooltips des equipements unifies : meme structure, memes couleurs, memes unites pour armures, outils,
 * equipements speciaux ») : FORMAT COMMUN DES EQUIPEMENTS, pose sur les sections de {@link Tooltips} (planche avant / apres :
 * scratchpad lot 28, {@code tools/gear/GearBench.java}). Chaque equipement ecrit, dans cet ordre (lignes absentes si sans objet) :
 * <ol>
 *     <li>DESC : une phrase courte (gris) ;</li>
 *     <li>STATS, « Libelle : valeur » (libelle gris, valeur blanche) : {@link #material Materiau} (equipement de metal : nom · profil),
 *     {@link #mining Minage} (niveau, vitesse), {@link #armor Protection} (points de la piece · ensemble · robustesse), caracteristiques
 *     propres de l'objet (memes unites partout : pts, FE, FE/s, mB, mB/s, L, blocs, m, %), {@link #energy Energie} (jaune, rouge a sec),
 *     {@link #tank reservoirs} (hydrogene, oxygene, air : aqua, rouge a sec), puis en dernier {@link #durability Durabilite} (restant /
 *     total, rouge sous 10 %) ;</li>
 *     <li>TIER : une seule ligne {@link #rank « Tier : ... »} dans la couleur de son rang : age mecanique (beige), age du bronze
 *     (bronze), MKI vert, MKII bleu, MKIII rouge, MKIV bordeaux ; equipement a echelle propre (ensembles modulaires, combinaisons
 *     spatiales) : son nom, dans la couleur du rang correspondant ;</li>
 *     <li>STATE (aqua) : mode, chargement ; NOTES : effets (vert = atout, or = particularite, rouge = manque) ; HINTS : aide d'usage.</li>
 * </ol>
 * La durabilite des outils et des armures du mod est ecrite par l'evenement d'infobulle commun ({@code J:tech/materials/ToolProfiles}), sauf en
 * infobulle avancee (F3 + H : Minecraft l'affiche deja). Code commun, sans classe du client.
 */
public final class GearTips {

    private GearTips() {
    }

    /** Rangs de l'echelle des equipements = tier des materiaux d'outils (T:materials/materials.py TIERS : 0 silex ... 5 ultime). */
    public static final int MECHANICAL = 0, BRONZE = 1, MK1 = 2, MK2 = 3, MK3 = 4, MK4 = 5;
    /** Couleurs des deux ages d'avant les MK (lisibles sur le fond d'une infobulle) ; les MK : {@link Tooltips#tierColor}. */
    private static final int[] AGE_RGB = {0xC8B898, 0xE09A50};
    /** Sous ce reste, la durabilite passe en rouge. */
    public static final float LOW = 0.10F;

    // ------------------------------------------------------------------ tier

    /** « Tier : MKII » (rang 0..5) dans la couleur du rang. */
    public static Tooltips rank(Tooltips tips, int rank) {
        return named(tips, rankName(rank));
    }

    /** « Tier : ... » avec un nom deja colore (echelle propre a l'objet). */
    public static Tooltips named(Tooltips tips, Component value) {
        return tips.add(Tooltips.Section.TIER, Component.translatable("tooltip.pioneers.stat", Component.translatable("tooltip.pioneers.tier")
              .withStyle(Tooltips.LABEL), value).withStyle(Tooltips.LABEL));
    }

    /** Nom du rang : age mecanique, age du bronze, MKI ... MKIV, dans sa couleur. */
    public static MutableComponent rankName(int rank) {
        int r = Math.max(0, Math.min(MK4, rank));
        if (r < MK1) {
            return Component.translatable("tooltip.pioneers.gear.rank." + r).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(AGE_RGB[r])));
        }
        return Tooltips.tierName(r - MK1);
    }

    /** Couleur d'un rang 0..5. */
    public static int rankColor(int rank) {
        int r = Math.max(0, Math.min(MK4, rank));
        return r < MK1 ? AGE_RGB[r] : Tooltips.tierColor(r - MK1);
    }

    /** Texte colore dans la couleur d'un rang (nom d'un ensemble modulaire, niveau d'une combinaison). */
    public static MutableComponent inRank(Component text, int rank) {
        return text.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rankColor(rank))));
    }

    // ------------------------------------------------------------------ caracteristiques

    /** « Materiau : Acier · polyvalent » (cles {@code tooltip.pioneers.gear.material.<id>} et {@code tooltip.pioneers.tool_profile.<p>}). */
    public static Tooltips material(Tooltips tips, String material, String profile) {
        Component name = Component.translatable("tooltip.pioneers.gear.material." + material);
        Component value = profile == null ? name : Component.translatable("tooltip.pioneers.gear.dot", name,
              Component.translatable("tooltip.pioneers.tool_profile." + profile));
        return tips.stat("tooltip.pioneers.gear.material", value);
    }

    /** « Minage : niveau 3 (diamant) · vitesse 7,6 » (niveau de minage vanilla 0..4). */
    public static Tooltips mining(Tooltips tips, int level, float speed) {
        int l = Math.max(0, Math.min(4, level));
        return tips.stat("tooltip.pioneers.gear.mining", Component.translatable("tooltip.pioneers.gear.mining_value", l,
              Component.translatable("tooltip.pioneers.gear.mining_level." + l), number(speed)));
    }

    /** « Protection : 6 pts · ensemble 20 pts · robustesse 2 » d'une piece d'armure (ensemble = les quatre pieces du meme materiau). */
    public static Tooltips armor(Tooltips tips, ArmorItem armor) {
        int set = 0;
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            set += armor.getMaterial().getDefenseForType(type);
        }
        return protection(tips, armor.getDefense(), set, armor.getToughness());
    }

    public static Tooltips protection(Tooltips tips, int piece, int set, float toughness) {
        MutableComponent v = Component.translatable("tooltip.pioneers.gear.points", piece);
        if (set > piece) {
            v = Component.translatable("tooltip.pioneers.gear.dot", v, Component.translatable("tooltip.pioneers.gear.set", set));
        }
        if (toughness > 0) {
            v = Component.translatable("tooltip.pioneers.gear.dot", v, Component.translatable("tooltip.pioneers.gear.toughness", number(toughness)));
        }
        return tips.stat("tooltip.pioneers.gear.protection", v);
    }

    /** Protection d'une armure electrique : sous tension, a sec (piece puis ensemble). */
    public static Tooltips poweredProtection(Tooltips tips, int powered, int drained, int setPowered, int setDrained) {
        return tips.stat("tooltip.pioneers.gear.protection", Component.translatable("tooltip.pioneers.gear.powered_points", powered, drained,
              setPowered, setDrained));
    }

    /** « Durabilite : 1 980 / 2 400 » (rouge sous 10 %) ; rien pour un objet qui ne s'use pas. */
    public static Tooltips durability(Tooltips tips, ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return tips;
        }
        int max = stack.getMaxDamage(), left = max - stack.getDamageValue();
        ChatFormatting color = left <= max * LOW ? Tooltips.WARN : Tooltips.VALUE;
        return tips.stat("tooltip.pioneers.gear.durability", Component.translatable("tooltip.pioneers.gear.of", Texts.grouped(left),
              Texts.grouped(max)), color);
    }

    /** « Energie : 12,5 kFE / 50,0 kFE » (jaune, rouge a sec). */
    public static Tooltips energy(Tooltips tips, long energy, long capacity) {
        return tips.stat("tooltip.pioneers.stat.energy", Component.translatable("tooltip.pioneers.gear.of", Texts.energy(energy),
              Texts.energy(capacity)), energy > 0 ? Tooltips.ENERGY : Tooltips.WARN);
    }

    /** Reservoir de l'equipement : « Hydrogene : 300 / 1 000 mB » (aqua, rouge a sec ; unite : cle {@code tooltip.pioneers.gear.unit.*}). */
    public static Tooltips tank(Tooltips tips, String labelKey, long amount, long capacity, String unit) {
        return tips.stat(labelKey, Component.translatable("tooltip.pioneers.gear.tank", Texts.grouped(amount), Texts.grouped(capacity),
              Component.translatable("tooltip.pioneers.gear.unit." + unit)), amount > 0 ? Tooltips.STATE : Tooltips.WARN);
    }

    /** Nombre court : entier tel quel, sinon une decimale avec le separateur de la langue (cle {@code tooltip.pioneers.gear.decimal}). */
    public static Component number(float v) {
        int tenths = Math.round(v * 10);
        if (tenths % 10 == 0) {
            return Component.literal(String.valueOf(tenths / 10));
        }
        return Component.translatable("tooltip.pioneers.gear.decimal", (tenths < 0 ? "-" : "") + Math.abs(tenths) / 10, Math.abs(tenths) % 10);
    }
}
