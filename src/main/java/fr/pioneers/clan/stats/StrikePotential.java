package fr.pioneers.clan.stats;

import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerDefs;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * 1.9 (MAJ RP, bloc 4) : potentiel de frappe d'un joueur (decision no 5 : P = 10 C + 20 A + 10 R + V), lu sur son equipement et son profil.
 * Arme : la meilleure de la main, de la seconde main et de la barre d'objets ; tier par les degats de l'objet (seuils de {@code stats.json}),
 * arcs, arbaletes et fusil modulaire a part. Armure : moyenne des quatre emplacements (vide = 0), tier par protection et robustesse
 * rapportees a la reference de l'emplacement. Calcule a la connexion et au changement d'equipement, une fois par minute au plus.
 */
public final class StrikePotential {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private StrikePotential() {
    }

    /** Calcule et range le potentiel du joueur (et sa vocation, son niveau moyen) ; renvoie P. */
    public static int compute(ServerPlayer player, ClanStatsData.Player into, long now) {
        StatsRules.Values v = StatsRules.get();
        int c = PioneerApi.level(player, "combat");
        int a = 0;
        a = Math.max(a, weaponTier(v, player.getMainHandItem()));
        a = Math.max(a, weaponTier(v, player.getOffhandItem()));
        for (int i = 0; i < 9; i++) {
            a = Math.max(a, weaponTier(v, player.getInventory().getItem(i)));
        }
        double r = 0;
        for (int i = 0; i < ARMOR.length; i++) {
            r += armorTier(v, i, player.getItemBySlot(ARMOR[i]), ARMOR[i]);
        }
        r /= ARMOR.length;
        String voc = PioneerCap.get(player).vocation;
        int p = StatsRules.potential(v, c, a, r, v.scoutVocation().equals(voc));
        into.potential = p;
        into.combat = c;
        into.weapon = a;
        into.armor10 = (int) Math.round(r * 10);
        into.vocation = voc == null ? "" : voc;
        into.avgSkill10 = averageSkill10(player);
        into.potentialAt = now;
        //1.9 (MAJ RP, bloc 4) : le plus haut potentiel du jour, pour la courbe de l'onglet Clan
        if (player.getServer() != null) {
            StatsBucket b = into.today(PioneerApi.day(player.getServer()));
            b.potential = Math.max(b.potential, p);
        }
        return p;
    }

    /** Niveau moyen des skills actifs (x10, pour un chiffre a une decimale). */
    static int averageSkill10(ServerPlayer player) {
        Collection<PioneerDefs.Skill> skills = PioneerDefs.get().skills.values();
        int n = 0, sum = 0;
        for (PioneerDefs.Skill s : skills) {
            if (PioneerApi.hasFeature(s.feature())) {
                sum += PioneerApi.level(player, s.id());
                n++;
            }
        }
        return n == 0 ? 0 : Math.round(sum * 10F / n);
    }

    /** Tier 0..4 d'une arme (0 : pas une arme). */
    static int weaponTier(StatsRules.Values v, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem() instanceof fr.pioneers.gear.ModularRifleItem) {
            return v.rifle();
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return v.crossbow();
        }
        if (stack.getItem() instanceof BowItem) {
            return v.bow();
        }
        boolean weapon = stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem
                         || stack.getItem() instanceof fr.pioneers.gear.ModularBladeItem || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
        if (!weapon) {
            return 0;
        }
        double dmg = 0;
        for (AttributeModifier m : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                dmg += m.getAmount();
            }
        }
        return StatsRules.tierOf(dmg, v.weaponDamage());
    }

    /** Tier 0..4 d'une piece d'armure portee a son emplacement. */
    static int armorTier(StatsRules.Values v, int index, ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return 0;
        }
        double armor = 0, tough = 0;
        var mods = stack.getAttributeModifiers(slot);
        for (AttributeModifier m : mods.get(Attributes.ARMOR)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                armor += m.getAmount();
            }
        }
        for (AttributeModifier m : mods.get(Attributes.ARMOR_TOUGHNESS)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                tough += m.getAmount();
            }
        }
        if (armor <= 0 && tough <= 0) {
            return 0;
        }
        return StatsRules.tierOf(StatsRules.armorScore(v, index, armor, tough), v.armorScore());
    }
}
