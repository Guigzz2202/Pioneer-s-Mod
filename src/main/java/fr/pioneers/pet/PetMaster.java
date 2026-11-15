package fr.pioneers.pet;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification §5.2, decision 6) : le MAITRE d'un animal = le joueur qui l'a NOMME A L'ETIQUETTE (chiens, chats et
 * montures : chevaux, anes, mulets, lamas, chameaux, dromadaires, du mod et vanilla). Sans nom, l'animal apprivoise obeit a celui qui l'a
 * apprivoise (proprietaire vanilla) mais n'a pas de maitre : ni rappel, ni protection.
 * <ul>
 *   <li>Donnees : NBT persistant de l'entite {@code pioneers_pet} (maitre, son nom, case « Autoriser les autres a l'adopter », migration
 *   faite) : suit les conversions de race (NBT recopie) et les changements de dimension.</li>
 *   <li>Etiquette : le proprietaire (ou n'importe qui si l'adoption est permise) devient maitre ; le proprietaire vanilla suit (il obeit au
 *   maitre) ; l'adoption se decoche. Un autre joueur ne peut pas renommer un animal qui a un maitre (message).</li>
 *   <li>Protection : un animal qui a un maitre n'est pas blesse par les autres joueurs, sauf si leurs clans sont en guerre ouverte
 *   ({@code ClanWar.pvpAllowed}, la regle du PvP de guerre du mod) ; reglage serveur {@code pets.protection}.</li>
 *   <li>Interface : Maj + clic droit main vide sur un animal apprivoise ({@link PetMenu}) ; modifiable par le maitre (ou le proprietaire s'il
 *   n'y a pas de maitre), lisible par les autres.</li>
 *   <li>Mondes existants (§8) : animal apprivoise ET deja nomme = son proprietaire en devient le maitre (aucune perte pour les joueurs).</li>
 *   <li>Registre des animaux de chaque maitre : {@link PetRegistry} (arrivee, dechargement, mort, rappel, nom).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PetMaster {

    private PetMaster() {
    }

    public static final String KEY = "pioneers_pet";
    public static final int DOG = 0, CAT = 1, MOUNT = 2;
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    /** 1.0.5-beta (lot 12) : vide a l'arret du serveur (appele par {@link PetRecall#stopping}). */
    static void clearWarnings() {
        WARNED.clear();
    }

    /** Animal concerne : chien, chat, ou monture (pas les chevaux squelettes ni zombies). */
    public static boolean concerned(@Nullable Entity e) {
        return e instanceof Pet || e instanceof AbstractHorse && !(e instanceof SkeletonHorse) && !(e instanceof ZombieHorse);
    }

    public static int kind(Entity e) {
        return e instanceof Pet p ? (p.isCat() ? CAT : DOG) : MOUNT;
    }

    public static boolean tamed(Entity e) {
        return e instanceof TamableAnimal t ? t.isTame() : e instanceof AbstractHorse h && h.isTamed();
    }

    /** Proprietaire vanilla (celui qui l'a apprivoise, ou le maitre depuis qu'il l'a nomme). */
    @Nullable
    public static UUID tamer(Entity e) {
        return e instanceof TamableAnimal t ? t.getOwnerUUID() : e instanceof AbstractHorse h ? h.getOwnerUUID() : null;
    }

    static CompoundTag data(Entity e) {
        return e.getPersistentData().getCompound(KEY);
    }

    static void put(Entity e, CompoundTag t) {
        e.getPersistentData().put(KEY, t);
    }

    @Nullable
    public static UUID master(Entity e) {
        CompoundTag t = data(e);
        return t.hasUUID("master") ? t.getUUID("master") : null;
    }

    public static String masterName(Entity e) {
        return data(e).getString("master_name");
    }

    public static boolean adoptable(Entity e) {
        return data(e).getBoolean("adopt");
    }

    public static void setAdoptable(Entity e, boolean adopt) {
        CompoundTag t = data(e);
        t.putBoolean("adopt", adopt);
        put(e, t);
    }

    public static boolean isMaster(Entity e, Player p) {
        return p.getUUID().equals(master(e));
    }

    /** Peut-il donner des ordres (interface, niche) ? Le maitre ; sans maitre, le proprietaire. */
    public static boolean canCommand(Entity e, Player p) {
        if (!tamed(e)) {
            return false;
        }
        UUID m = master(e);
        return m != null ? m.equals(p.getUUID()) : p.getUUID().equals(tamer(e));
    }

    /** Fait de ce joueur le maitre (et le proprietaire vanilla) ; l'adoption se decoche ; registre a jour. */
    public static void setMaster(Entity e, Player p) {
        CompoundTag t = data(e);
        t.putUUID("master", p.getUUID());
        t.putString("master_name", p.getGameProfile().getName());
        t.putBoolean("adopt", false);
        t.putBoolean("init", true);
        put(e, t);
        if (e instanceof TamableAnimal a) {
            a.setTame(true);
            a.setOwnerUUID(p.getUUID());
        } else if (e instanceof AbstractHorse h) {
            h.setTamed(true);
            h.setOwnerUUID(p.getUUID());
        }
        if (e.getServer() != null) {
            PetRegistry.get(e.getServer()).update(e);
        }
    }

    // ------------------------------------------------------------------ etiquette et interface

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        Entity e = event.getTarget();
        if (!concerned(e) || !tamed(e)) {
            return;
        }
        Player p = event.getEntity();
        ItemStack held = p.getItemInHand(event.getHand());
        boolean client = p.level().isClientSide;
        if (held.getItem() instanceof NameTagItem && held.hasCustomHoverName()) {
            UUID m = master(e), t = tamer(e);
            boolean adopt = adoptable(e);
            net.minecraft.network.chat.MutableComponent refuse = null;
            if (m != null && !m.equals(p.getUUID()) && !adopt) {
                refuse = Component.translatable("pet.pioneers.msg.has_master", masterName(e));
            } else if (m == null && t != null && !t.equals(p.getUUID()) && !adopt) {
                refuse = Component.translatable("pet.pioneers.msg.not_owner");
            }
            if (refuse != null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!client) {
                    p.displayClientMessage(refuse.withStyle(ChatFormatting.GOLD), true);
                }
                return;
            }
            if (!client) {
                boolean adopted = adopt && m != null && !m.equals(p.getUUID());
                setMaster(e, p);
                p.displayClientMessage(Component.translatable(adopted ? "pet.pioneers.msg.adopted" : "pet.pioneers.msg.master",
                      held.getHoverName()).withStyle(ChatFormatting.GREEN), true);
            }
            return;   //le vanilla pose le nom
        }
        if (event.getHand() == InteractionHand.MAIN_HAND && held.isEmpty() && p.isSecondaryUseActive()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(client));
            if (!client && p instanceof ServerPlayer sp && e instanceof LivingEntity le) {
                open(sp, le);
            }
        }
    }

    /** Ouvre l'interface de l'animal (gabarit sombre du mod). */
    public static void open(ServerPlayer sp, LivingEntity e) {
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, pl) -> new PetMenu(id, inv, e, sp), e.getDisplayName()),
              buf -> PetMenu.write(buf, e, sp));
    }

    // ------------------------------------------------------------------ protection

    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !concerned(e) || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        UUID m = master(e);
        if (m == null || m.equals(attacker.getUUID()) || !PiCommonConfig.get(PiCommonConfig.PET_PROTECTION, true) || attacker.isCreative()) {
            return;
        }
        if (atWar(attacker.getServer(), attacker.getUUID(), m)) {
            return;
        }
        event.setCanceled(true);
        long now = e.level().getGameTime();
        Long last = WARNED.get(attacker.getUUID());
        if (!PetRules.waiting(last, now, 61)) {
            WARNED.put(attacker.getUUID(), now);
            attacker.displayClientMessage(Component.translatable("pet.pioneers.msg.protected", masterName(e)).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** PvP de guerre du mod : les clans des deux joueurs sont en guerre ouverte. */
    static boolean atWar(@Nullable MinecraftServer server, UUID a, UUID b) {
        if (server == null || !fr.pioneers.clan.ClanApi.enabled()) {
            return false;
        }
        fr.pioneers.clan.ClanData d = fr.pioneers.clan.ClanData.get(server);
        return fr.pioneers.clan.war.ClanWar.pvpAllowed(d.clanOf(a), d.clanOf(b));
    }

    // ------------------------------------------------------------------ mondes existants, registre

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide || event.isCanceled() || !concerned(e)) {
            return;
        }
        CompoundTag t = data(e);
        if (!t.getBoolean("init")) {
            t.putBoolean("init", true);
            UUID owner = tamer(e);
            if (tamed(e) && e.hasCustomName() && owner != null && !t.hasUUID("master")) {
                t.putUUID("master", owner);
                MinecraftServer server = e.getServer();
                String name = server == null || server.getProfileCache() == null ? "" : server.getProfileCache().get(owner).map(
                      com.mojang.authlib.GameProfile::getName).orElse("");
                t.putString("master_name", name);
            }
            put(e, t);
        }
        if (t.hasUUID("master") && e.getServer() != null) {
            PetRegistry.get(e.getServer()).update(e);
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide || !concerned(e) || master(e) == null || e.getServer() == null) {
            return;
        }
        Entity.RemovalReason r = e.getRemovalReason();
        if (r == Entity.RemovalReason.UNLOADED_TO_CHUNK || r == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            PetRegistry.get(e.getServer()).update(e);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !concerned(e) || e.getServer() == null) {
            return;
        }
        UUID m = master(e);
        if (m != null) {
            PetRegistry.get(e.getServer()).remove(e.getUUID());
        }
    }
}
