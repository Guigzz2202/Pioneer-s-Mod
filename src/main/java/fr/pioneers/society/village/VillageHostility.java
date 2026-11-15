package fr.pioneers.society.village;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.society.reputation.ReputationApi;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9.1 (retours de Guillaume : « quand ma reputation baisse, les villageois ne me fuient pas et les golems ne m'attaquent pas »).
 * <p>
 * Ce qui etait casse : la panique vanilla d'un villageois ({@code VillagerPanicTrigger}) ne demarre que sur la memoire {@code HURT_BY} (une
 * source de degats) ou {@code NEAREST_HOSTILE} ; l'ancienne fuite ne posait que {@code HURT_BY_ENTITY} : elle ne se declenchait jamais. La
 * fuite personnelle (affinite de -60 ou moins) n'existait pas hors de Parler, et les golems n'attaquaient qu'a une moyenne de village de -60,
 * presque impossible a atteindre.
 * <p>
 * Maintenant (file toutes les 20 ticks, seulement pres d'un village connu) :
 * <ul>
 *   <li>un villageois a -60 ou moins (ou tout le village quand le peuple vous tient pour Hostile) panique et s'enfuit vraiment : memoires
 *       {@code HURT_BY} + {@code HURT_BY_ENTITY} (la panique vanilla) et un but de marche loin du joueur ; de -20 a -59, il garde ses
 *       distances (ne s'approche pas, s'ecarte a moins de 4 blocs) ;</li>
 *   <li>un villageois hostile qui vous voit appelle les golems a portee ; un coup porte a un villageois devant un golem le fait charger ;
 *       les golems attaquent a vue des que la moyenne du village est a -40 ou moins, ou que le peuple vous tient pour Hostile ;</li>
 *   <li>le drapeau blanc (banniere blanche en main) calme les golems pres du Tableau des besoins ou pendant une tache de reparation du
 *       village ; frapper un villageois ou un golem rompt la treve pour la journee ;</li>
 *   <li>un message discret et un son rendent l'hostilite lisible (au plus un par village et par sorte toutes les 30 s).</li>
 * </ul>
 * Rien ne remonte avec le temps.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class VillageHostility {

    private static int clock;
    /** Golems appeles : golem -> (joueur vise, fin de l'appel en temps de jeu). */
    private static final Map<UUID, Call> CALLED = new HashMap<>();
    /** Messages deja montres : joueur -> (village et sorte -> prochain temps de jeu permis). */
    private static final Map<UUID, Map<String, Long>> SHOWN = new HashMap<>();

    record Call(UUID player, long until) {
    }

    private VillageHostility() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock % 20 != 0 || !Villagers.active()) {
            return;
        }
        RepairRules.Settings s = RepairRules.get();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            long now = level.getGameTime();
            for (Iterator<Map.Entry<UUID, Call>> it = CALLED.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue().until() < now) {
                    it.remove();
                }
            }
            for (ServerPlayer p : level.players()) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                    continue;
                }
                VillageData.Village v = VillageData.get(level).nearest(p.blockPosition(), Villages.DISCOVERY);
                if (v != null) {
                    visit(level, v, p, s);
                }
            }
        }
    }

    static void visit(ServerLevel level, VillageData.Village v, ServerPlayer p, RepairRules.Settings s) {
        boolean people = ReputationApi.tier(p, v.people).refuse();
        Villager first = null;
        int fled = 0;
        for (Villager w : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(s.hostility().fleeRange()), Villager::isAlive)) {
            int a = Villagers.affinity(level, w, p.getUUID());
            int fear = RepairRules.fear(s, a, people);
            if (fear == 2) {
                panic(w, p);
                fled++;
                if (first == null) {
                    first = w;
                }
                //un temoin hostile qui voit le joueur appelle les golems
                if (a <= s.hostility().golemCallAffinity() && w.hasLineOfSight(p) && call(level, w, p, s) > 0) {
                    notify(p, v, "call", Component.translatable("village.pioneers.hostile.call", w.getDisplayName()), level, s);
                    level.playSound(null, w.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.8F);
                }
            } else if (fear == 1) {
                keepDistance(w, p, s);
            }
        }
        if (fled > 0 && first != null) {
            Component msg = people ? Component.translatable("village.pioneers.hostile.flee_people", ReputationApi.peopleName(level.getServer(), v.people))
                  : Component.translatable("village.pioneers.hostile.flee", first.getDisplayName());
            if (notify(p, v, "flee", msg, level, s)) {
                level.playSound(null, first.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
            }
        }
        //drapeau blanc : les golems du village laissent le joueur
        if (truce(level, v, p)) {
            boolean calmed = false;
            for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(32), g -> g.getTarget() == p)) {
                g.setTarget(null);
                g.stopBeingAngry();
                CALLED.remove(g.getUUID());
                calmed = true;
            }
            if (calmed || RepairRules.golemsHostile(s, v.average(p.getUUID()), people)) {
                notify(p, v, "truce", Component.translatable("village.pioneers.truce.on"), level, s);
            }
        }
    }

    /** Panique vanilla (memoires HURT_BY + HURT_BY_ENTITY) et but de marche loin du joueur, plus loin que la fuite vanilla de 6 blocs. */
    static void panic(Villager w, ServerPlayer p) {
        Brain<Villager> b = w.getBrain();
        b.setMemory(MemoryModuleType.HURT_BY, p.damageSources().playerAttack(p));
        b.setMemory(MemoryModuleType.HURT_BY_ENTITY, (LivingEntity) p);
        if (w.distanceToSqr(p) < 100) {
            Vec3 away = LandRandomPos.getPosAway(w, 16, 7, p.position());
            if (away != null) {
                b.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, 0.75F, 0));
            }
        }
    }

    /** Meprisant : il ne vient pas vers le joueur et s'ecarte s'il est trop pres. */
    static void keepDistance(Villager w, ServerPlayer p, RepairRules.Settings s) {
        if (w.isTrading()) {
            return;
        }
        Brain<Villager> b = w.getBrain();
        if (b.getMemory(MemoryModuleType.INTERACTION_TARGET).filter(e -> e == p).isPresent()) {
            b.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        }
        int k = s.hostility().keepDistance();
        if (w.distanceToSqr(p) < k * k) {
            Vec3 away = LandRandomPos.getPosAway(w, 8, 4, p.position());
            if (away != null) {
                b.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, 0.5F, 0));
            }
        }
    }

    /** Appelle les golems a portee du temoin contre le joueur ; renvoie combien ont repondu. */
    static int call(ServerLevel level, LivingEntity witness, ServerPlayer p, RepairRules.Settings s) {
        VillageData.Village v = VillageData.get(level).nearest(witness.blockPosition(), Villages.DISCOVERY);
        if (v != null && truce(level, v, p)) {
            return 0;
        }
        int n = 0;
        long until = level.getGameTime() + 20L * s.hostility().callSeconds();
        for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, witness.getBoundingBox().inflate(s.hostility().callRange()),
              g -> g.isAlive() && !g.isPlayerCreated())) {
            Call old = CALLED.put(g.getUUID(), new Call(p.getUUID(), until));
            if (g.getTarget() == null || !(g.getTarget() instanceof net.minecraft.world.entity.player.Player)) {
                g.setTarget(p);
            }
            n += old == null || !old.player().equals(p.getUUID()) ? 1 : 0;
        }
        return n;
    }

    /** Ce golem a ete appele contre ce joueur (et l'appel court encore). */
    static boolean called(IronGolem g, ServerPlayer p) {
        Call c = CALLED.get(g.getUUID());
        return c != null && c.player().equals(p.getUUID()) && c.until() >= g.level().getGameTime();
    }

    /** Coup porte a un villageois : les golems qui voient la scene chargent ; la treve du drapeau blanc est rompue pour la journee. */
    static void struck(ServerLevel level, LivingEntity victim, ServerPlayer p) {
        RepairRules.Settings s = RepairRules.get();
        VillageData.Village v = VillageData.get(level).nearest(victim.blockPosition(), Villages.DISCOVERY);
        if (v != null) {
            breakTruce(level, v, p, s);
        }
        int n = 0;
        long until = level.getGameTime() + 20L * s.hostility().callSeconds();
        for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, victim.getBoundingBox().inflate(s.hostility().hitWitnessRange()),
              g -> g.isAlive() && !g.isPlayerCreated() && (g.hasLineOfSight(victim) || g.hasLineOfSight(p)))) {
            CALLED.put(g.getUUID(), new Call(p.getUUID(), until));
            g.setTarget(p);
            n++;
        }
        if (n > 0 && v != null) {
            notify(p, v, "golem", Component.translatable("village.pioneers.hostile.golem"), level, s);
        }
    }

    static void breakTruce(ServerLevel level, VillageData.Village v, ServerPlayer p, RepairRules.Settings s) {
        RepairData.Entry e = RepairData.get(level.getServer()).of(p.getUUID());
        long day = PioneerApi.day(level);
        Long old = e.truceBroken.put(v.id, day + s.hostility().truceBreakDays() - 1);
        RepairData.get(level.getServer()).changed();
        if ((old == null || old < day) && VillageRepairs.whiteFlag(p)) {
            p.displayClientMessage(Component.translatable("village.pioneers.truce.broken").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onGolemHurt(LivingHurtEvent event) {
        //frapper un golem du village rompt aussi la treve
        if (event.getEntity() instanceof IronGolem g && !g.isPlayerCreated() && event.getSource().getEntity() instanceof ServerPlayer p
            && g.level() instanceof ServerLevel level && Villagers.active()) {
            VillageData.Village v = VillageData.get(level).nearest(g.blockPosition(), Villages.DISCOVERY);
            if (v != null) {
                breakTruce(level, v, p, RepairRules.get());
            }
        }
    }

    /**
     * Treve du drapeau blanc : banniere blanche en main, pres du Tableau des besoins du village ou avec une tache de reparation de ce village
     * en cours, et pas de coup porte a ce village aujourd'hui.
     */
    static boolean truce(ServerLevel level, VillageData.Village v, ServerPlayer p) {
        if (!VillageRepairs.whiteFlag(p)) {
            return false;
        }
        RepairRules.Settings s = RepairRules.get();
        RepairData.Entry e = RepairData.get(level.getServer()).peek(p.getUUID());
        long day = PioneerApi.day(level);
        if (e != null && e.truceBroken.getOrDefault(v.id, Long.MIN_VALUE) >= day) {
            return false;
        }
        if (e != null && e.task != null && e.task.village == v.id) {
            return true;
        }
        int r = s.hostility().truceBoardRange();
        BlockPos at = p.blockPosition();
        if (v.boards.isEmpty()) {
            return v.center.distSqr(at) <= (double) r * r;
        }
        for (BlockPos b : v.boards) {
            if (b.distSqr(at) <= (double) r * r) {
                return true;
            }
        }
        return false;
    }

    /** Golems du village agressifs envers ce joueur : appel en cours, moyenne du village trop basse, ou peuple Hostile ; jamais sous treve. */
    static boolean golemHostile(IronGolem golem, ServerPlayer p) {
        if (!(golem.level() instanceof ServerLevel level) || !Villagers.active()) {
            return false;
        }
        VillageData.Village v = VillageData.get(level).nearest(golem.blockPosition(), Villages.DISCOVERY);
        if (v != null && truce(level, v, p)) {
            return false;
        }
        if (called(golem, p)) {
            return true;
        }
        return v != null && RepairRules.golemsHostile(RepairRules.get(), v.average(p.getUUID()), ReputationApi.tier(p, v.people).refuse());
    }

    /** Message discret (barre d'action), au plus un par village et par sorte toutes les 30 s ; vrai s'il est montre. */
    static boolean notify(ServerPlayer p, @Nullable VillageData.Village v, String kind, Component msg, ServerLevel level, RepairRules.Settings s) {
        String key = (v == null ? 0 : v.id) + ":" + kind;
        Map<String, Long> m = SHOWN.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        long now = level.getGameTime();
        if (m.getOrDefault(key, Long.MIN_VALUE) > now) {
            return false;
        }
        m.put(key, now + 20L * s.hostility().messageSeconds());
        p.displayClientMessage(msg.copy().withStyle(kind.equals("truce") ? ChatFormatting.GRAY : ChatFormatting.RED), true);
        if (kind.equals("golem") || kind.equals("call")) {
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.5F, 0.7F);
        }
        return true;
    }

    /** Le golem prend le joueur pour cible : message lisible (« le golem te tient pour ennemi »). */
    static void targeted(IronGolem g, ServerPlayer p) {
        if (g.level() instanceof ServerLevel level) {
            VillageData.Village v = VillageData.get(level).nearest(g.blockPosition(), Villages.DISCOVERY);
            notify(p, v, "golem", Component.translatable("village.pioneers.hostile.golem"), level, RepairRules.get());
        }
    }

    static void forget(UUID player) {
        SHOWN.remove(player);
    }
}
