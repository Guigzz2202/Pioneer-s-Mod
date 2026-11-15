package fr.pioneers.guide;

import fr.pioneers.Pioneers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.3-beta (lot 2, « chaque etape du Periple a une condition de reussite exacte et verifiable, jamais cochee trop tot ni oubliee ») : le
 * suivi des etapes, cote serveur. Chaque etape a son progres {@code pioneers:journey/<chapitre>_<nn>} (critere « impossible » : rien d'autre
 * ne le decerne) ; ce suivi le decerne quand la condition de l'etape ({@link JourneySteps}) est remplie :
 * <ul>
 *     <li>etape par evenement ({@link JourneySteps.Step#byEvent()}) : son progres d'evenement exact est fait (multibloc forme, machine en
 *     marche, lancement, divergence...) ;</li>
 *     <li>sinon : un objet du but est dans l'inventaire du joueur (cases, armure, seconde main, objet tenu au curseur d'un ecran).</li>
 * </ul>
 * Quand : a la connexion (un monde d'avant la 1.0.3 retrouve d'un coup ses etapes deja faites par leurs anciens progres d'evenement ou par ce
 * qu'il porte), toutes les 2 secondes, et des le tick suivant un objet fabrique, ramasse ou cuit, ou un progres obtenu. Une etape faite le
 * reste (progres sauve). Aucun paquet : le client lit ces progres comme les autres ({@code client/guide/GuideProgress}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class JourneyTracker {

    /** Intervalle de la verification reguliere (ticks). */
    public static final int PERIOD = 40;
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Map<JourneySteps.Step, Set<Item>> GOALS = new HashMap<>();

    private JourneyTracker() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (DIRTY.remove(player.getUUID()) || player.tickCount % PERIOD == 0) {
            check(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            check(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DIRTY.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        dirty(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        dirty(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        dirty(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!event.getAdvancement().getId().getPath().startsWith("journey/")) {
            dirty(event.getEntity().getUUID());
        }
    }

    private static void dirty(UUID id) {
        DIRTY.add(id);
    }

    /** Decerne toutes les etapes dont la condition est remplie ; renvoie combien. */
    public static int check(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        PlayerAdvancements progress = player.getAdvancements();
        Set<Item> held = null;
        int awarded = 0;
        for (JourneySteps.Step step : JourneySteps.ALL) {
            Advancement adv = server.getAdvancements().getAdvancement(ResourceLocation.parse(step.advancement()));
            if (adv == null || progress.getOrStartProgress(adv).isDone()) {
                continue;
            }
            boolean met;
            if (step.byEvent()) {
                Advancement legacy = server.getAdvancements().getAdvancement(ResourceLocation.parse(step.legacy()));
                met = legacy != null && progress.getOrStartProgress(legacy).isDone();
            } else {
                if (held == null) {
                    held = held(player);
                }
                met = false;
                for (Item item : goal(step)) {
                    if (held.contains(item)) {
                        met = true;
                        break;
                    }
                }
            }
            if (met && progress.award(adv, "done")) {
                awarded++;
            }
        }
        return awarded;
    }

    /** Vrai si l'etape est faite pour ce joueur (progres sauve). */
    public static boolean done(ServerPlayer player, JourneySteps.Step step) {
        MinecraftServer server = player.getServer();
        Advancement adv = server == null ? null : server.getAdvancements().getAdvancement(ResourceLocation.parse(step.advancement()));
        return adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    /** Objets du but d'une etape (resolus une fois). */
    public static Set<Item> goal(JourneySteps.Step step) {
        return GOALS.computeIfAbsent(step, s -> {
            Set<Item> out = new HashSet<>();
            for (String id : s.goal()) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
                if (item != null) {
                    out.add(item);
                }
            }
            return out;
        });
    }

    /** Objets que le joueur porte : inventaire (cases, armure, seconde main) et objet tenu au curseur d'un ecran ouvert. */
    private static Set<Item> held(ServerPlayer player) {
        Set<Item> out = new HashSet<>();
        Inventory inv = player.getInventory();
        for (List<ItemStack> part : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack stack : part) {
                if (!stack.isEmpty()) {
                    out.add(stack.getItem());
                }
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            out.add(carried.getItem());
        }
        return out;
    }
}
