package fr.pioneers.materials;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.fish.PiRodItem;
import fr.pioneers.forge.casting.CrucibleTongsItem;
import fr.pioneers.forge.casting.FireStarterItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Profils des materiaux d'outils : infobulle commune des equipements (1.0.4-beta lot 28, {@link ToolClientEvents}), et
 * auto-reparation du nitinol (alliage a memoire de forme : 1 point toutes les 10 s dans l'inventaire).
 */
public final class ToolProfiles {

    private ToolProfiles() {
    }

    private static final int REPAIR_EVERY = 200;
    private static Map<Item, GeneratedMaterials.TierDef> byItem;

    /** Fiche du materiau de l'outil (outils, cles et outils de minage des fiches), ou null. */
    @Nullable
    public static GeneratedMaterials.TierDef of(Item item) {
        if (byItem == null) {
            Map<Item, GeneratedMaterials.TierDef> map = new HashMap<>();
            for (GeneratedMaterials.Entry entry : GeneratedMaterials.ENTRIES) {
                if (entry.kind() == GeneratedMaterials.Kind.ITEM || entry.kind() == GeneratedMaterials.Kind.BLOCK) {
                    continue;
                }
                GeneratedMaterials.TierDef def = PiTiers.def(entry.material());
                Item tool = PiMaterials.item(entry.id());
                if (def != null && tool != null) {
                    map.put(tool, def);
                }
            }
            byItem = map;
        }
        return byItem.get(item);
    }

    /** Le materiau se repare-t-il tout seul ? */
    public static boolean selfRepairing(GeneratedMaterials.TierDef def) {
        return "self_repairing".equals(def.profile());
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ToolEvents {

        private ToolEvents() {
        }

        @SubscribeEvent
        public static void repair(TickEvent.PlayerTickEvent event) {
            Player player = event.player;
            if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount % REPAIR_EVERY != 0) {
                return;
            }
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !stack.isDamaged()) {
                    continue;
                }
                GeneratedMaterials.TierDef def = of(stack.getItem());
                if (def != null && selfRepairing(def)) {
                    stack.setDamageValue(stack.getDamageValue() - 1);
                }
            }
        }
    }

    /**
     * 1.0.4-beta (lot 28) : infobulle commune des equipements ({@code J:core/tooltip/GearTips}) : outils des fiches (minage, combat,
     * atelier, zone, couteaux, cles) : « Materiau : acier · polyvalent », « Minage : niveau 3 (diamant) · vitesse 7,6 » (outils de
     * minage), « Durabilite », « Tier : MKI » (rang = tier du materiau) ; armures, cannes a peche, pinces et allume-feu du mod : leur
     * durabilite (le reste est ecrit par l'objet). Avant : « Tier n · profil » sur les outils seuls.
     */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class ToolClientEvents {

        private ToolClientEvents() {
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            Item item = stack.getItem();
            GeneratedMaterials.TierDef def = of(item);
            boolean gear = def != null || item instanceof WorkshopToolItem || item instanceof PiRodItem || item instanceof CrucibleTongsItem
                  || item instanceof FireStarterItem || item instanceof ArmorItem && item.getClass().getName().startsWith("fr.pioneers.");
            if (!gear) {
                return;
            }
            Tooltips tips = Tooltips.event(event.getToolTip());
            //outils d'atelier sans fiche de tier (pierre : marteau, mortier) : materiau seul, age mecanique
            boolean stone = def == null && item instanceof WorkshopToolItem w && "stone".equals(w.entry().material());
            if (def != null) {
                GearTips.material(tips, def.id(), def.profile());
                if (item instanceof DiggerItem) {
                    GearTips.mining(tips, def.level(), def.speed());
                }
            } else if (stone) {
                GearTips.material(tips, "stone", null);
            }
            if (!event.getFlags().isAdvanced()) {
                GearTips.durability(tips, stack);
            }
            if (def != null || stone) {
                GearTips.rank(tips, def == null ? GearTips.MECHANICAL : def.tier());
            }
        }
    }
}
