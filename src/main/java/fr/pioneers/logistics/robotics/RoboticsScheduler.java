package fr.pioneers.logistics.robotics;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.network.PacketRobotics;
import fr.pioneers.logistics.rail.RailConfigs;
import fr.pioneers.logistics.robotics.hub.RoboticHubData;
import fr.pioneers.logistics.robotics.logistics.LogisticNetwork;
import fr.pioneers.logistics.robotics.logistics.LogisticPoint;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import fr.pioneers.logistics.robotics.logistics.MarkerRole;
import fr.pioneers.logistics.robotics.logistics.PointConfig;
import fr.pioneers.logistics.robotics.works.WorksData;
import fr.pioneers.logistics.robotics.works.WorksPlan;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Ordonnanceur robotique (inspire de la robotique de Factorio), reseau par reseau. Deux fois par seconde :
 * <ol>
 *     <li>il recense les demandes : ordres (transports, zones), points demandeurs et tampons a qui il manque des ressources, points a
 *     vider — par priorite ;</li>
 *     <li>il les confie aux meilleures unites disponibles : celles qui attendent deja dehors (pres d'un relais), puis celles des hubs ;
 *     le score tient compte du genre, du tier, de la distance, de la batterie et de la capacite d'emport ;</li>
 *     <li>il regroupe les livraisons qui partent d'une meme source en <b>tournees</b> (plusieurs demandeurs servis d'un coup).</li>
 * </ol>
 * Les quantites en route sont comptees pour ne jamais livrer deux fois. Une unite qui a fini enchaine directement le travail en attente
 * le plus proche ({@link #next}), sans repasser par son hub.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoboticsScheduler {

    private RoboticsScheduler() {
    }

    /** Sorties par hub et par passage (deux passages par seconde). */
    private static final int LAUNCHES_PER_HUB = 2;
    private static final int UNLOAD_UNITS = 2, REQUEST_UNITS = 3, BATCH_RADIUS = 24, MAX_BATCH = 6;
    /** Duree (ticks) pendant laquelle un point inaccessible au sol est reserve aux drones. */
    private static final long GROUND_BLOCK_TICKS = 6_000;

    private static final Map<ResourceKey<Level>, State> STATES = new HashMap<>();

    /** Etat de travail d'une dimension (non sauvegarde : les unites ne le sont pas non plus). */
    private static final class State {

        final Map<Integer, ZoneScanner> scanners = new HashMap<>();
        /** Demande -> unites en route. */
        final Long2IntOpenHashMap units = new Long2IntOpenHashMap();
        /** Demande -> quantite en route. */
        final Long2LongOpenHashMap inflight = new Long2LongOpenHashMap();
        /** Genre d'unite -> unites occupees par le reseau passif. */
        final Int2IntOpenHashMap passive = new Int2IntOpenHashMap();
        /** Cases en cours de reparation (une seule unite par case). */
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet repairing = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        /** Limites du reseau passif par genre (RoboticsOrders). */
        int[] passiveLimits = new int[0];
        /** Ordre -> travaux en cours (unites occupees dessus). */
        final Int2IntOpenHashMap orderUnits = new Int2IntOpenHashMap();
        /** Unite -> ordre auquel elle appartient (de son premier travail jusqu'a son retour au port ou un autre travail). */
        final Map<RobotUnitEntity, Integer> member = new IdentityHashMap<>();
        /** Ordre -> unites qui lui appartiennent (jamais plus que le nombre demande). */
        final Int2IntOpenHashMap members = new Int2IntOpenHashMap();
        /** Positions que les unites au sol n'atteignent pas : position -> fin de l'interdiction. */
        final Long2LongOpenHashMap groundBlocked = new Long2LongOpenHashMap();
        /** Unites qui attendent dehors, pretes a repartir. */
        final Set<RobotUnitEntity> idle = Collections.newSetFromMap(new IdentityHashMap<>());
        /** Mode machine : dernier objet vu dans chaque entree (bloc -> case -> objet), pour continuer a livrer quand la machine a tout consomme. */
        final Map<Long, Map<Integer, ItemStack>> machineMemory = new HashMap<>();
        /** Tournees en attente, par reseau (prises par les unites qui finissent un travail). */
        final Map<Long, List<Job>> pending = new HashMap<>();
        /** Alertes : cle -> alerte. */
        final Map<String, RoboticsAlert> alerts = new LinkedHashMap<>();
        /** Livraisons sans unite libre : demande -> debut de l'attente. */
        final Map<Long, Long> waiting = new HashMap<>();
        /** Alertes effacees par un joueur : cle -> fin du silence. */
        final Map<String, Long> muted = new HashMap<>();
        /** Alertes resolues : cle -> {fin, retours} (pour reconnaitre une alerte recurrente). */
        final Map<String, long[]> history = new HashMap<>();
        boolean dirty;

        void bind(RobotUnitEntity unit, int order) {
            Integer old = member.put(unit, order);
            if (old != null && old == order) {
                return;
            }
            if (old != null) {
                unbindCount(old);
            }
            members.addTo(order, 1);
        }

        void unbind(RobotUnitEntity unit) {
            Integer old = member.remove(unit);
            if (old != null) {
                unbindCount(old);
            }
        }

        private void unbindCount(int order) {
            if (members.addTo(order, -1) <= 1) {
                members.remove(order);
            }
        }

        boolean isMember(@Nullable RobotUnitEntity unit, int order) {
            Integer current = unit == null ? null : member.get(unit);
            return current != null && current == order;
        }

        /** Il reste de la place sur l'ordre pour cette unite (deja membre, ou une place libre). */
        boolean room(@Nullable RobotUnitEntity unit, RoboticOrder order) {
            //Nombre demande baisse : les unites en trop finissent leur travail puis rentrent
            return isMember(unit, order.id) ? members.get(order.id) <= order.maxUnits : members.get(order.id) < order.maxUnits;
        }

        ZoneScanner scanner(int id) {
            return scanners.computeIfAbsent(id, k -> new ZoneScanner());
        }
    }

    private static State state(ServerLevel level) {
        State state = STATES.computeIfAbsent(level.dimension(), key -> new State());
        state.passiveLimits = RoboticsOrders.get(level).passiveLimits();
        return state;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        LogisticsRegistry.clearServer();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        RoboticsOrders orders = RoboticsOrders.get(level);
        LogisticsRegistry registry = LogisticsRegistry.get(level);
        long now = level.getGameTime();
        List<LogisticNetwork> networks = registry.networks(now);
        if (networks.isEmpty() && orders.all().isEmpty()) {
            PacketRobotics.flush(level);
            return;
        }
        State state = state(level);
        List<Integer> finished = new ArrayList<>();
        for (RoboticOrder order : orders.all()) {
            if (order.type == RoboticOrder.Type.ZONE && !order.paused && order.isValid()) {
                ZoneScanner scanner = state.scanner(order.id);
                scanner.tick(level, order);
                //Travail en une fois (terrassement, minage) termine : l'ordre disparait
                if (now % 20 == 0 && scanner.isFinished(order.task, order.zoneBox(level.getMinBuildHeight()))) {
                    finished.add(order.id);
                }
            }
        }
        for (int id : finished) {
            RoboticOrder done = orders.get(id);
            if (done != null && done.task == RobotTask.BUILDER) {
                finishWorks(level, orders, id);
            }
            orders.remove(id);
            state.scanners.remove(id);
            state.dirty = true;
        }
        if (now % 100 == 50) {
            audit(level, state);
        }
        if (now % 200 == 0) {
            state.scanners.keySet().removeIf(id -> orders.get(id) == null);
            //Plans de travaux sans ordre (termine, supprime)
            WorksData works = WorksData.get(level);
            works.retain(id -> orders.get(id) != null);
            state.orderUnits.keySet().removeIf(id -> orders.get(id) == null);
            state.groundBlocked.long2LongEntrySet().removeIf(entry -> entry.getLongValue() < now);
            state.idle.removeIf(unit -> !unit.isAlive());
            state.waiting.values().removeIf(since -> now - since > 12_000);
            for (RobotUnitEntity unit : new ArrayList<>(state.member.keySet())) {
                if (!unit.isAlive() || orders.get(state.member.get(unit)) == null) {
                    state.unbind(unit);
                }
            }
        }
        if (now % 40 == 0) {
            for (LogisticNetwork network : networks) {
                computeTotals(level, network);
                checkHubs(state, network, now);
            }
            //Alertes plus signalees : resolues d'elles-memes
            var iterator = state.alerts.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (now - entry.getValue().time() > entry.getValue().type().quiet()) {
                    resolved(state, entry.getKey(), entry.getValue(), now);
                    iterator.remove();
                }
            }
            state.history.values().removeIf(h -> now - h[0] > RECUR_WINDOW);
        }
        if (now % 10 == 0) {
            state.pending.clear();
            for (LogisticNetwork network : networks) {
                dispatch(level, state, registry, orders, network, now);
            }
            //Ordres hors de tout reseau
            for (RoboticOrder order : orders.all()) {
                if (!order.paused && order.isValid() && registry.networkAt(order.anchor(), now) == null) {
                    order.status = RoboticOrder.Status.NO_HUB;
                }
            }
        }
        if (state.dirty) {
            state.dirty = false;
            PacketRobotics.broadcast(level);
        }
        //0.20.46 (audit) : l'etat part au plus une fois par seconde, aux seuls joueurs qui l'ont demande
        PacketRobotics.flush(level);
    }

    // ------------------------------------------------------------------
    // Stocks et etat du reseau
    // ------------------------------------------------------------------

    private static void computeTotals(ServerLevel level, LogisticNetwork network) {
        Map<LogisticResource, Long> totals = new HashMap<>();
        for (LogisticPoint point : network.points) {
            if (!point.isPointRemoved() && point.getConfig().role.source) {
                for (Map.Entry<LogisticResource, Long> entry : RoboticsWorld.contents(level, point.endpoint()).entrySet()) {
                    totals.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
        }
        for (RoboticHubData hub : network.hubs) {
            for (var slot : hub.getBuffer()) {
                if (!slot.isEmpty()) {
                    totals.merge(LogisticResource.of(slot.getStack()), (long) slot.getCount(), Long::sum);
                }
            }
        }
        network.totals = totals;
    }

    private static void checkHubs(State state, LogisticNetwork network, long now) {
        for (RoboticHubData hub : network.hubs) {
            if (hub.getStatus() == RoboticHubData.Status.NO_ENERGY) {
                alert(state, RoboticsAlert.Type.NO_ENERGY, hub.getCenter(), now);
            } else {
                String key = RoboticsAlert.Type.NO_ENERGY.name() + "@" + hub.getCenter().asLong();
                RoboticsAlert alert = state.alerts.remove(key);
                if (alert != null) {
                    resolved(state, key, alert, now);
                }
            }
        }
    }

    public static void alert(ServerLevel level, RoboticsAlert.Type type, BlockPos pos) {
        alert(state(level), type, pos, level.getGameTime());
    }

    /** Une alerte qui revient moins de 10 minutes apres sa resolution est recurrente. */
    private static final long RECUR_WINDOW = 12_000;

    private static void alert(State state, RoboticsAlert.Type type, BlockPos pos, long now) {
        String key = type.name() + "@" + pos.asLong();
        Long until = state.muted.get(key);
        if (until != null && until > now) {
            return;
        }
        RoboticsAlert old = state.alerts.get(key);
        if (old != null) {
            state.alerts.put(key, new RoboticsAlert(type, pos, now, old.first(), old.returns()));
            return;
        }
        long[] past = state.history.remove(key);
        int returns = past != null && now - past[0] <= RECUR_WINDOW ? (int) past[1] + 1 : 0;
        state.alerts.put(key, new RoboticsAlert(type, pos, now, now, returns));
        state.dirty = true;
    }

    private static void resolved(State state, String key, RoboticsAlert alert, long now) {
        state.history.put(key, new long[]{now, alert.returns()});
        state.dirty = true;
    }

    /** Le probleme d'un endroit est regle (livraison faite...) : ses alertes de ce type disparaissent. */
    public static void resolve(ServerLevel level, @Nullable BlockPos pos, RoboticsAlert.Type... types) {
        State state = STATES.get(level.dimension());
        if (state == null || state.alerts.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        var iterator = state.alerts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            RoboticsAlert alert = entry.getValue();
            if ((pos == null || alert.pos().equals(pos)) && Arrays.asList(types).contains(alert.type())) {
                resolved(state, entry.getKey(), alert, now);
                iterator.remove();
            }
        }
    }

    public static List<RoboticsAlert> alerts(ServerLevel level) {
        return new ArrayList<>(state(level).alerts.values());
    }

    /** Silence d'une alerte effacee (30 minutes) : elle ne revient pas tant que le probleme est le meme. */
    private static final long MUTE_TICKS = 36_000;

    /** Efface les alertes d'un endroit (ou toutes si {@code pos} est null) et les fait taire un moment. */
    public static void dismiss(ServerLevel level, @Nullable BlockPos pos) {
        State state = state(level);
        long now = level.getGameTime();
        var iterator = state.alerts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (pos == null || entry.getValue().pos().equals(pos)) {
                state.muted.put(entry.getKey(), now + MUTE_TICKS);
                iterator.remove();
            }
        }
        state.muted.values().removeIf(until -> until < now);
        state.dirty = true;
    }

    // ------------------------------------------------------------------
    // Candidats
    // ------------------------------------------------------------------

    /** Une unite qui peut partir : deja dehors (au repos) ou dans un emplacement de hub. */
    private static final class Candidate {

        final RoboticHubData hub;
        @Nullable
        final RobotUnitEntity unit;
        final int slot;
        final UnitTier tier;
        boolean used;

        Candidate(RoboticHubData hub, @Nullable RobotUnitEntity unit, int slot, UnitTier tier) {
            this.hub = hub;
            this.unit = unit;
            this.slot = slot;
            this.tier = tier;
        }

        Vec3 position() {
            return unit != null ? unit.position() : Vec3.atCenterOf(hub.getCenter());
        }

        UnitKind kind() {
            return hub.getKind();
        }
    }

    private static List<Candidate> candidates(State state, LogisticNetwork network) {
        List<Candidate> list = new ArrayList<>();
        for (RobotUnitEntity unit : state.idle) {
            RoboticHubData hub = unit.isAlive() && unit.isIdle() ? unit.getHubData() : null;
            if (hub != null && network.hubs.contains(hub) && !hub.isRecalling() && unit.batteryRatio() > MIN_BATTERY) {
                list.add(new Candidate(hub, unit, unit.getSlot(), unit.getTier()));
            } else if (unit.isAlive() && unit.getJob() == null) {
                //Pas choisissable (batterie, hub en rappel) : elle ne garde pas sa place sur son ordre
                state.unbind(unit);
            }
        }
        for (RoboticHubData hub : network.hubs) {
            List<Integer> slots = hub.availableUnits();
            for (int i = 0; i < Math.min(LAUNCHES_PER_HUB, slots.size()); i++) {
                UnitTier tier = hub.tierAt(slots.get(i));
                if (tier != null) {
                    list.add(new Candidate(hub, null, slots.get(i), tier));
                }
            }
        }
        return list;
    }

    /** Batterie minimale d'une unite dehors pour recevoir un travail. */
    public static final double MIN_BATTERY = 0.35;

    /** Capacite d'emport d'une unite pour une ressource (objets, mB, FE, litres d'air). */
    public static long capacity(UnitKind kind, UnitTier tier, UnitStats stats, @Nullable LogisticResource res) {
        double factor = tier.capacity * stats.cargo();
        if (res == null || res.type == Resource.ITEMS) {
            return Math.max(1, Math.round(kind.stacks * factor)) * 64L;
        }
        if (res.type == Resource.ENERGY) {
            return Math.round(kind.fluid * 100L * factor);
        }
        if (res.type == Resource.PRESSURE) {
            //Bouteille d'air : deux litres par mB de reservoir (UnitCargo.airCapacity)
            return Math.round(kind.fluid * 2L * factor);
        }
        return Math.round(kind.fluid * factor);
    }

    /**
     * Travaux en attente par unite au-dela desquels une zone fait sortir une unite de plus. Blocs (recolte, abattage, minage, travaux) : des
     * qu'il y a un travail en file pour elle, toutes les unites demandees sortent. Betes et objets (elevage, chasse, ramassage, peche) :
     * l'unite sur place enchaine les sous-taches, une autre ne sort que si la file deborde.
     */
    private static int backlogPerUnit(RobotTask task) {
        return switch (task) {
            case BREEDER, APIARIST, HUNTER, COLLECTOR, FISHER -> 3;
            default -> 1;
        };
    }

    private static boolean allowed(State state, Candidate candidate, Job job, @Nullable RoboticOrder order, long now) {
        if (candidate.used || order != null && !order.assignable(candidate.hub)) {
            return false;
        }
        //Jamais plus d'unites sur un ordre que le nombre demande
        if (order != null && job.group == order.id && !state.room(candidate.unit, order)) {
            return false;
        }
        //Tortue endormie sur sa zone (mode repos) : seulement les travaux de cette zone
        if (candidate.unit != null && candidate.unit.getRestOrder() >= 0 && job.group != candidate.unit.getRestOrder()) {
            return false;
        }
        //Reseau passif : jamais plus d'unites de ce hub que sa limite
        int kind = candidate.kind().ordinal();
        if (passiveJob(job) && kind < state.passiveLimits.length && state.passiveLimits[kind] < RoboticsOrders.PASSIVE_ALL
            && state.passive.get(kind) >= state.passiveLimits[kind]) {
            return false;
        }
        if (!candidate.kind().flying) {
            if (job.airOnly) {
                return false;
            }
            for (BlockPos pos : job.points()) {
                if (state.groundBlocked.get(pos.asLong()) > now) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Interet d'envoyer ce candidat : debit pour le transport, aptitude pour le reste ; la distance penalise. */
    private static double score(Candidate candidate, Job job) {
        UnitKind kind = candidate.kind();
        UnitStats stats = candidate.unit != null ? candidate.unit.getStats() : candidate.hub.stats();
        double distance = Math.sqrt(candidate.position().distanceToSqr(job.anchor()));
        double speed = kind.speed * candidate.tier.speed * stats.speed();
        double bonus = candidate.unit != null ? 1.5 : 1;
        if (job.type == Job.Type.TRIP) {
            //Debit reel : quantite livree / temps total estime (aller, allers-retours si la charge depasse la soute, manutention).
            //Petite charge : le plus rapide (drone) ; gros volume : la plus grosse soute (rover)
            long planned = job.planned();
            long cap = Math.max(1, capacity(kind, candidate.tier, stats, job.mainResource()));
            long amount = planned > 0 ? planned : cap;
            long trips = Math.min(8, (amount + cap - 1) / cap);
            double delivered = Math.min(amount, cap * trips);
            double trip = 0;
            for (int i = 1; i < job.stops.size(); i++) {
                trip += job.stops.get(i - 1).at().accessPoint().distanceTo(job.stops.get(i).at().accessPoint());
            }
            double time = (distance + trip * (2 * trips - 1)) / Math.max(0.05, speed) + 30 * trips + 40;
            return bonus * kind.affinity(RobotTask.LOGISTICS) * delivered / time;
        }
        return bonus * kind.affinity(job.task) * kind.work * candidate.tier.work * stats.work() * speed / (distance + 8);
    }

    @Nullable
    private static Candidate best(State state, List<Candidate> candidates, Job job, @Nullable RoboticOrder order, long now) {
        Candidate best = null;
        double bestScore = 0;
        for (Candidate candidate : candidates) {
            if (!allowed(state, candidate, job, order, now)) {
                continue;
            }
            double score = score(candidate, job);
            if (order != null && state.isMember(candidate.unit, order.id)) {
                //Une unite deja sur l'ordre passe avant une nouvelle
                score *= 4;
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * Fin d'un chantier : les reglages logistiques copies sont appliques aux points poses (ou deja la), et les zones de travail copiees
     * sont creees.
     */
    private static void finishWorks(ServerLevel level, RoboticsOrders orders, int id) {
        WorksPlan plan = WorksData.get(level).plan(id);
        if (plan == null) {
            return;
        }
        for (WorksPlan.Op op : plan.ops) {
            for (Map.Entry<BlockPos, CompoundTag> entry : op.configs.entrySet()) {
                applyConfig(level, entry.getKey(), entry.getValue());
            }
        }
        AABB bounds = plan.bounds();
        for (CompoundTag tag : plan.zones()) {
            RoboticOrder zone = RoboticOrder.load(tag);
            if (bounds == null || zone.min == null || !bounds.inflate(16).contains(Vec3.atCenterOf(zone.anchor()))) {
                continue;
            }
            zone.id = 0;
            zone.type = RoboticOrder.Type.ZONE;
            zone.paused = false;
            orders.put(zone);
        }
    }

    /** Reglages logistiques copies, appliques au point de cette position (balise ou coffre). */
    public static void applyConfig(ServerLevel level, BlockPos pos, CompoundTag tag) {
        if (RailConfigs.isRail(tag)) {
            RailConfigs.apply(level, pos, tag);
            return;
        }
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LogisticPoint point) {
            point.setConfig(PointConfig.load(tag));
        }
    }

    /** Pourquoi aucune unite ne prend le travail d'un ordre (affichee dans les interfaces). */
    private static RoboticOrder.Status noUnitReason(State state, LogisticNetwork network, RoboticOrder order, Job job, long now) {
        boolean units = false, powered = false, charging = false, flyer = false;
        for (RoboticHubData hub : network.hubs) {
            if (!order.assignable(hub)) {
                continue;
            }
            flyer |= hub.getKind().flying;
            for (int slot = 0; slot < hub.capacity(); slot++) {
                if (hub.tierAt(slot) != null) {
                    units = true;
                    if (!hub.isOut(slot) && hub.chargeRatio(slot) < RoboticHubData.LAUNCH_CHARGE) {
                        charging = true;
                    }
                }
            }
            powered |= hub.isPowered();
        }
        if (!units) {
            return RoboticOrder.Status.NO_UNITS;
        }
        if (!powered) {
            return RoboticOrder.Status.HUB_NO_ENERGY;
        }
        if (!flyer) {
            boolean blocked = job.airOnly;
            for (BlockPos pos : job.points()) {
                blocked |= state.groundBlocked.get(pos.asLong()) > now;
            }
            if (blocked) {
                return RoboticOrder.Status.UNREACHABLE;
            }
        }
        if (state.members.get(order.id) >= order.maxUnits) {
            return RoboticOrder.Status.ACTIVE;
        }
        return charging ? RoboticOrder.Status.UNITS_CHARGING : RoboticOrder.Status.UNITS_BUSY;
    }

    /** Une unite de l'ordre attend sur place, libre ? */
    private static boolean idleMember(State state, List<Candidate> candidates, RoboticOrder order) {
        for (Candidate candidate : candidates) {
            if (!candidate.used && candidate.unit != null && state.isMember(candidate.unit, order.id)) {
                return true;
            }
        }
        return false;
    }

    /** Confie le travail au candidat ; false si la sortie echoue. */
    private static boolean give(ServerLevel level, State state, Candidate candidate, Job job) {
        candidate.used = true;
        if (candidate.unit != null) {
            if (!candidate.unit.isAlive() || !candidate.unit.isIdle()) {
                return false;
            }
            state.idle.remove(candidate.unit);
            claim(state, job, candidate.unit);
            candidate.unit.assign(job);
            return true;
        }
        RobotUnitEntity launched = candidate.hub.launch(level, candidate.slot, job);
        if (launched == null) {
            return false;
        }
        claim(state, job, launched);
        return true;
    }

    // ------------------------------------------------------------------
    // Repartition
    // ------------------------------------------------------------------

    private static void dispatch(ServerLevel level, State state, LogisticsRegistry registry, RoboticsOrders orders, LogisticNetwork network, long now) {
        List<Candidate> candidates = candidates(state, network);
        //1. Ordres, par priorite puis anciennete
        List<RoboticOrder> list = new ArrayList<>();
        for (RoboticOrder order : orders.all()) {
            if (network.covers(order.anchor())) {
                list.add(order);
            }
        }
        list.sort(Comparator.comparingInt((RoboticOrder o) -> -o.priority).thenComparingInt(o -> o.id));
        for (RoboticOrder order : list) {
            if (order.paused) {
                order.status = RoboticOrder.Status.PAUSED;
                continue;
            }
            if (!order.isValid()) {
                order.status = RoboticOrder.Status.INVALID;
                continue;
            }
            //Occupees (travail en cours) et membres (sur l'ordre, occupees ou en attente sur place)
            int active = state.orderUnits.get(order.id);
            order.activeUnits = Math.max(active, state.members.get(order.id));
            if (order.condition != null && !order.condition.test(network.stock(order.condition.res()))) {
                order.status = active > 0 ? RoboticOrder.Status.ACTIVE : RoboticOrder.Status.CONDITION;
                continue;
            }
            if (network.hubs.stream().noneMatch(order::assignable)) {
                order.status = RoboticOrder.Status.NO_HUB_MATCH;
                continue;
            }
            order.status = active > 0 ? RoboticOrder.Status.ACTIVE : RoboticOrder.Status.WAITING;
            order.statusItem = "";
            for (int guard = 0; guard < order.maxUnits; guard++) {
                if (state.members.get(order.id) >= order.maxUnits && !idleMember(state, candidates, order)) {
                    break;
                }
                //Sous-taches d'une zone (nourrir puis eliminer le surplus, recolter puis replanter...) : l'unite deja sur place les
                //enchaine elle-meme ; une unite de plus ne sort que si le travail en attente depasse ce qu'elle peut absorber
                if (order.type == RoboticOrder.Type.ZONE && active > 0 && state.scanner(order.id).backlog() < backlogPerUnit(order.task) * active
                    && !idleMember(state, candidates, order)) {
                    break;
                }
                Job job = order.type == RoboticOrder.Type.ZONE ? zoneJob(level, state, registry, network, order, network::covers)
                                                               : transferJob(level, state, registry, network, order);
                if (job == null) {
                    //Pourquoi rien ne part : la raison donnee par la recherche du travail, sinon « rien a faire »
                    if (active == 0 && order.status == RoboticOrder.Status.WAITING) {
                        //Du travail trouve, mais hors de portee du reseau : il manque un relais
                        order.status = order.type == RoboticOrder.Type.ZONE && state.scanner(order.id).backlog() > 0 ? RoboticOrder.Status.NO_HUB
                                                                                                                       : RoboticOrder.Status.NOTHING_TO_DO;
                    }
                    break;
                }
                job.priority = order.priority;
                Candidate candidate = best(state, candidates, job, order, now);
                if (candidate == null || !give(level, state, candidate, job)) {
                    if (active == 0) {
                        order.status = noUnitReason(state, network, order, job, now);
                    }
                    discard(level, job);
                    if (job.type == Job.Type.TRIP) {
                        state.pending.computeIfAbsent(network.id, k -> new ArrayList<>()).add(job);
                    }
                    break;
                }
                active++;
            }
            int units = state.members.get(order.id);
            if (active > 0 || units > 0 && !order.status.problem()) {
                order.status = RoboticOrder.Status.ACTIVE;
            }
            order.activeUnits = Math.max(active, units);
        }
        //2. Demandeurs et tampons, puis 3. points a vider
        List<Delivery> deliveries = new ArrayList<>();
        collectRequests(level, state, registry, network, deliveries, now);
        collectUnloads(level, state, registry, network, deliveries);
        deliveries.sort(Comparator.comparingInt((Delivery d) -> -d.priority));
        planTrips(level, state, network, deliveries, candidates, now);
        //4. Reparations des degats (intendants)
        if (orders.repairs()) {
            repairs(level, state, registry, network, candidates, now);
        }
    }

    private static final int REPAIR_SCAN = 256, REPAIR_GROUP = 16;
    private static final double REPAIR_RADIUS = 4.5;

    /**
     * Reparations : les blocs detruits par une explosion dans la couverture sont reposes par les intendants, regroupes par materiau
     * (jusqu'a 16 blocs proches), le materiau pris dans le reseau. Deux chantiers au plus par passage.
     */
    private static void repairs(ServerLevel level, State state, LogisticsRegistry registry, LogisticNetwork network, List<Candidate> candidates, long now) {
        RobotRepairs data = RobotRepairs.get(level);
        if (data.damaged.isEmpty()) {
            return;
        }
        int scanned = 0, started = 0;
        List<Long> stale = new ArrayList<>();
        Job open = null;
        ItemStack openItem = ItemStack.EMPTY;
        for (var entry : data.damaged.long2ObjectEntrySet()) {
            if (scanned++ > REPAIR_SCAN || started >= 2) {
                break;
            }
            long key = entry.getLongKey();
            BlockPos pos = BlockPos.of(key);
            if (state.repairing.contains(key) || !network.covers(pos) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState target = entry.getValue();
            BlockState current = level.getBlockState(pos);
            if (WorksPlan.matches(current, target) || !ZoneScanner.needsPlacement(current, target)
                || !target.canSurvive(level, pos) && !level.getBlockState(pos.below()).isAir()) {
                //Deja remis, ou la place est prise : rien a reparer
                stale.add(key);
                continue;
            }
            ItemStack item = WorksPlan.requiredItem(level, pos, target, current);
            if (item.isEmpty()) {
                stale.add(key);
                continue;
            }
            if (open != null && ItemStack.isSameItemSameTags(openItem, item) && open.extra.size() + 1 < REPAIR_GROUP && open.pos.closerThan(pos, REPAIR_RADIUS)) {
                open.extra.add(pos);
                open.states.add(target);
                continue;
            }
            if (open != null && startRepair(level, state, registry, network, candidates, open, openItem, now)) {
                started++;
            }
            open = Job.block(RobotTask.BUILDER, pos);
            open.key = key ^ 0x0F00_0000_0000_0000L;
            open.place = target;
            open.states.add(target);
            open.material = item;
            open.repair = true;
            open.priority = -1;
            openItem = item;
        }
        if (open != null && started < 2) {
            startRepair(level, state, registry, network, candidates, open, openItem, now);
        }
        for (long key : stale) {
            data.forget(key);
        }
    }

    private static boolean startRepair(ServerLevel level, State state, LogisticsRegistry registry, LogisticNetwork network, List<Candidate> candidates,
          Job job, ItemStack item, long now) {
        LogisticResource res = LogisticResource.of(item);
        LogisticPoint source = registry.findSource(level, network, res, Vec3.atCenterOf(job.pos), null);
        if (source == null) {
            return false;
        }
        job.stops.add(new Job.Stop(source.endpointFor(res), true, res, 1 + job.extra.size(), null, 0));
        Candidate candidate = best(state, candidates, job, null, now);
        if (candidate == null || !give(level, state, candidate, job)) {
            return false;
        }
        for (BlockPos pos : ZoneScanner.placements(job)) {
            state.repairing.add(pos.asLong());
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Demandes des points
    // ------------------------------------------------------------------

    /** Livraison a faire : de la source a la cible, une quantite, pour une demande. */
    private record Delivery(Endpoint from, Endpoint to, LogisticResource res, long amount, long demand, int priority, boolean acceptCheck) {}

    private static long demandKey(BlockPos pos, int index) {
        return pos.asLong() * 31 + index + 1;
    }

    private static void collectRequests(ServerLevel level, State state, LogisticsRegistry registry, LogisticNetwork network, List<Delivery> out,
          long now) {
        List<RoboticOrder> transfers = new ArrayList<>();
        for (RoboticOrder order : RoboticsOrders.get(level).all()) {
            //Seul un ordre qui travaille vraiment (unites en route) prend le relais du reseau ; un ordre bloque (source vide, pas de
            //port...) ne doit jamais empecher le reseau de livrer
            if (order.type == RoboticOrder.Type.TRANSFER && !order.paused && order.target != null && order.isValid()
                && order.status == RoboticOrder.Status.ACTIVE) {
                transfers.add(order);
            }
        }
        for (LogisticPoint point : network.points) {
            PointConfig config = point.getConfig();
            if (point.isPointRemoved() || !config.role.requests) {
                continue;
            }
            if (config.condition != null && !config.condition.test(network.stock(config.condition.res()))) {
                continue;
            }
            Endpoint target = point.endpoint();
            List<PointConfig.Entry> entries = config.machineMode ? machineEntries(level, state, target) : config.entries;
            for (int i = 0; i < entries.size(); i++) {
                PointConfig.Entry entry = entries.get(i);
                if (entry.res().isFamily() || entry.amount() <= 0) {
                    continue;
                }
                //Un ordre s'occupe deja de livrer cette ressource ici : le reseau ne fait pas doublon (ni concurrence aux unites choisies)
                if (coveredByOrder(transfers, target.pos(), entry.res())) {
                    continue;
                }
                long demand = demandKey(target.pos(), config.machineMode ? 100 + i : i);
                if (state.units.get(demand) >= REQUEST_UNITS) {
                    continue;
                }
                //Face propre a la ressource (la redstone par l'arriere, le fer par le dessus...), sinon celle du point
                Endpoint at = entry.side() != null ? target.withSide(entry.side()) : target;
                long deficit = entry.amount() - RoboticsWorld.count(level, at, entry.res()) - state.inflight.get(demand);
                if (deficit <= 0) {
                    continue;
                }
                deficit = Math.min(deficit, RoboticsWorld.room(level, at, entry.res(), deficit));
                if (deficit <= 0) {
                    continue;
                }
                LogisticPoint source = registry.findSource(level, network, entry.res(), target.accessPoint(), target.pos());
                if (source == null) {
                    alert(state, RoboticsAlert.Type.NO_SOURCE, point.getPointPos(), now);
                    continue;
                }
                //Un tampon ne donne que son surplus : jamais plus que ce qu'il peut ceder
                long give = Math.min(deficit, LogisticsRegistry.available(level, source, entry.res()));
                if (give <= 0) {
                    continue;
                }
                out.add(new Delivery(source.endpointFor(entry.res()), at, entry.res(), give, demand, config.priority, false));
            }
        }
    }

    private static boolean coveredByOrder(List<RoboticOrder> transfers, BlockPos target, LogisticResource res) {
        for (RoboticOrder order : transfers) {
            if (order.target.pos().equals(target) && order.res.covers(res)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mode machine : garder pleine l'entree avec ce qu'elle a deja recu (une pile par objet). Chaque entree retient le dernier objet vu :
     * si la machine travaille plus vite que les livraisons et vide sa case, la demande continue (la recette n'est pas oubliee).
     */
    private static List<PointConfig.Entry> machineEntries(ServerLevel level, State state, Endpoint target) {
        List<PointConfig.Entry> list = new ArrayList<>();
        IItemHandler handler = RoboticsWorld.items(level, target);
        if (handler == null) {
            return list;
        }
        Map<Integer, ItemStack> memory = state.machineMemory.computeIfAbsent(target.pos().asLong() * 31 + (target.side() == null ? 7
                                                                                                                                 : target.side().ordinal()),
              k -> new HashMap<>());
        for (int slot = 0; slot < handler.getSlots() && list.size() < PointConfig.MAX_ENTRIES; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                //Case vide : le dernier objet qu'elle a recu, s'il y entre toujours
                ItemStack remembered = memory.get(slot);
                if (remembered == null || handler.insertItem(slot, remembered.copyWithCount(1), true).getCount() > 0) {
                    continue;
                }
                stack = remembered;
            } else if (handler.insertItem(slot, stack.copyWithCount(1), true).getCount() > 0) {
                continue;
            } else {
                memory.put(slot, stack.copyWithCount(1));
            }
            LogisticResource res = LogisticResource.of(stack);
            if (list.stream().noneMatch(entry -> entry.res().equals(res))) {
                list.add(new PointConfig.Entry(res, stack.getMaxStackSize()));
            }
        }
        return list;
    }

    private static void collectUnloads(ServerLevel level, State state, LogisticsRegistry registry, LogisticNetwork network, List<Delivery> out) {
        for (LogisticPoint point : network.points) {
            PointConfig config = point.getConfig();
            if (point.isPointRemoved() || config.role != MarkerRole.UNLOAD) {
                continue;
            }
            Endpoint from = point.endpoint();
            long demand = from.pos().asLong() ^ 0x5DEECE66DL;
            if (state.units.get(demand) >= UNLOAD_UNITS) {
                continue;
            }
            LogisticResource res = RoboticsWorld.firstExtractable(level, from, r -> !config.isFiltered() || config.accepts(r));
            if (res == null) {
                continue;
            }
            Endpoint to = null;
            //D'abord un demandeur a qui ca manque, sinon un stockage
            for (LogisticPoint other : network.points) {
                PointConfig c = other.getConfig();
                if (other == point || other.isPointRemoved() || !c.role.requests) {
                    continue;
                }
                for (PointConfig.Entry entry : c.entries) {
                    if (entry.res().covers(res) && RoboticsWorld.count(level, other.endpointFor(res), res) < entry.amount()) {
                        to = other.endpointFor(res);
                        break;
                    }
                }
                if (to != null) {
                    break;
                }
            }
            if (to == null) {
                LogisticPoint storage = registry.findStorage(level, network, res, 1, from.accessPoint());
                to = storage == null ? null : storage.endpointFor(res);
            }
            if (to == null || to.pos().equals(from.pos())) {
                if (to == null) {
                    alert(state, RoboticsAlert.Type.STORAGE_FULL, point.getPointPos(), level.getGameTime());
                }
                continue;
            }
            out.add(new Delivery(from, to, res, 0, demand, config.priority, true));
        }
        //Tampons : vides tranquillement de tout ce qu'ils ne demandent pas (ou au-dela du niveau demande), vers les stockages
        for (LogisticPoint point : network.points) {
            PointConfig config = point.getConfig();
            if (point.isPointRemoved() || config.role != MarkerRole.BUFFER) {
                continue;
            }
            Endpoint from = point.endpoint();
            long demand = from.pos().asLong() ^ 0x3C6EF372FE94F82BL;
            if (state.units.get(demand) >= 1) {
                continue;
            }
            LogisticResource res = RoboticsWorld.firstExtractable(level, from, r -> LogisticsRegistry.available(level, point, r) > 0);
            if (res == null) {
                continue;
            }
            LogisticPoint storage = registry.findStorage(level, network, res, 1, from.accessPoint());
            if (storage == null || storage.getPointPos().equals(point.getPointPos())) {
                continue;
            }
            long amount = LogisticsRegistry.available(level, point, res);
            out.add(new Delivery(point.endpointFor(res), storage.endpointFor(res), res, amount, demand, config.priority - 2, true));
        }
    }

    /** Regroupe les livraisons d'une meme source en tournees et les confie. */
    private static void planTrips(ServerLevel level, State state, LogisticNetwork network, List<Delivery> deliveries, List<Candidate> candidates,
          long now) {
        Map<String, List<Delivery>> groups = new LinkedHashMap<>();
        //1.9 (MAJ RP, bloc 4) : aucune livraison logistique a travers une frontiere de claim refusee
        deliveries.removeIf(d -> !ClanApi.canTransfer(level, d.from().pos(), d.to().pos()));
        for (Delivery delivery : deliveries) {
            String key = delivery.res().type == Resource.ITEMS && delivery.amount() > 0 ? delivery.from().pos().asLong() + ":" + delivery.from().side()
                                                                                          : "solo:" + groups.size();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(delivery);
        }
        for (List<Delivery> group : groups.values()) {
            while (!group.isEmpty()) {
                Delivery first = group.remove(0);
                Job probe = Job.transfer(first.from(), first.to(), first.res(), first.amount(), first.acceptCheck(), first.demand());
                Candidate candidate = best(state, candidates, probe, null, now);
                if (candidate == null) {
                    //Signale une livraison qui attend une unite depuis plus de 30 s
                    long since = state.waiting.computeIfAbsent(first.demand(), k -> now);
                    if (now - since > 600) {
                        alert(state, RoboticsAlert.Type.NO_CARRIER, first.to().pos(), now);
                    }
                    state.pending.computeIfAbsent(network.id, k -> new ArrayList<>()).add(probe);
                    for (Delivery rest : group) {
                        state.pending.get(network.id).add(Job.transfer(rest.from(), rest.to(), rest.res(), rest.amount(), rest.acceptCheck(),
                              rest.demand()));
                    }
                    break;
                }
                UnitStats stats = candidate.unit != null ? candidate.unit.getStats() : candidate.hub.stats();
                long stacksLeft = capacity(candidate.kind(), candidate.tier, stats, null) / 64;
                List<Delivery> batch = new ArrayList<>();
                batch.add(first);
                if (first.res().type == Resource.ITEMS && first.amount() > 0) {
                    stacksLeft -= stacks(first);
                    var iterator = group.iterator();
                    while (iterator.hasNext() && batch.size() < MAX_BATCH && stacksLeft > 0) {
                        Delivery next = iterator.next();
                        if (next.to().pos().closerThan(first.to().pos(), BATCH_RADIUS) && stacks(next) <= stacksLeft) {
                            batch.add(next);
                            stacksLeft -= stacks(next);
                            iterator.remove();
                        }
                    }
                }
                Job job = batch.size() == 1 ? probe : tour(batch);
                job.priority = first.priority();
                if (!give(level, state, candidate, job)) {
                    break;
                }
                for (Delivery done : batch) {
                    state.waiting.remove(done.demand());
                }
            }
        }
    }

    private static long stacks(Delivery delivery) {
        int max = Math.max(1, delivery.res().item().getMaxStackSize());
        return Math.max(1, (delivery.amount() + max - 1) / max);
    }

    /** Tournee : une prise par ressource a la source, puis les livraisons de proche en proche. */
    private static Job tour(List<Delivery> batch) {
        Job job = Job.trip();
        Endpoint from = batch.get(0).from();
        Map<LogisticResource, Long> loads = new LinkedHashMap<>();
        for (Delivery delivery : batch) {
            loads.merge(delivery.res(), delivery.amount(), Long::sum);
        }
        for (Map.Entry<LogisticResource, Long> entry : loads.entrySet()) {
            job.stops.add(new Job.Stop(from, true, entry.getKey(), entry.getValue(), null, 0));
        }
        List<Delivery> left = new ArrayList<>(batch);
        Vec3 at = from.accessPoint();
        while (!left.isEmpty()) {
            Vec3 here = at;
            Delivery next = Collections.min(left, Comparator.comparingDouble(d -> d.to().accessPoint().distanceToSqr(here)));
            left.remove(next);
            job.stops.add(new Job.Stop(next.to(), false, next.res(), next.amount(), null, next.demand()));
            job.claims.add(new Job.Claim(next.demand(), next.amount()));
            at = next.to().accessPoint();
        }
        return job;
    }

    // ------------------------------------------------------------------
    // Ordres
    // ------------------------------------------------------------------

    private static long demandOf(RoboticOrder order) {
        return 0x7000_0000_0000_0000L | order.id;
    }

    /** Travail de transport pour un ordre, ou null (rien a faire pour l'instant ; l'etat de l'ordre dit pourquoi). */
    @Nullable
    private static Job transferJob(ServerLevel level, State state, LogisticsRegistry registry, LogisticNetwork network, RoboticOrder order) {
        Endpoint target = order.target;
        if (target == null) {
            return null;
        }
        long demand = demandOf(order);
        long limit;
        if (!order.recurring) {
            long remaining = order.amount - order.delivered - state.inflight.get(demand);
            if (remaining <= 0) {
                return null;
            }
            limit = remaining;
        } else if (order.amount > 0 && !order.res.isFamily()) {
            long deficit = order.amount - RoboticsWorld.count(level, target, order.res) - state.inflight.get(demand);
            if (deficit <= 0) {
                order.status = RoboticOrder.Status.TARGET_OK;
                return null;
            }
            limit = deficit;
        } else {
            limit = 0;
        }
        Endpoint source = order.source;
        if (source == null) {
            LogisticPoint point = registry.findSource(level, network, order.res, target.accessPoint(), target.pos());
            if (point == null) {
                order.status = RoboticOrder.Status.NO_SOURCE;
                return null;
            }
            source = point.endpoint();
        }
        UnitCargo probe = new UnitCargo(27, Integer.MAX_VALUE / 2, Long.MAX_VALUE / 4);
        //1.9 (MAJ RP, bloc 4) : aucun ordre de transport a travers une frontiere de claim refusee
        if (!ClanApi.canTransfer(level, source.pos(), target.pos())) {
            order.status = RoboticOrder.Status.NO_SOURCE;
            return null;
        }
        if (RoboticsWorld.take(level, source, order.res, limit, probe, target, true) <= 0) {
            boolean empty = RoboticsWorld.extractable(level, source, order.res) <= 0;
            order.status = empty ? RoboticOrder.Status.NO_SOURCE : RoboticOrder.Status.TARGET_FULL;
            return null;
        }
        Job job = Job.transfer(source, target, order.res, limit, true, demand);
        if (limit <= 0) {
            job.claims.clear();
            job.claims.add(new Job.Claim(demand, 0));
        }
        job.group = order.id;
        return job;
    }

    /** Travail de zone (reserve), avec ses fournitures ; null si rien a faire ou s'il manque des fournitures. */
    @Nullable
    private static Job zoneJob(ServerLevel level, State state, LogisticsRegistry registry, LogisticNetwork network, RoboticOrder order,
          Predicate<BlockPos> reachable) {
        ZoneScanner scanner = state.scanner(order.id);
        if (order.task == RobotTask.BUILDER) {
            return worksJob(level, state, network, order, scanner, reachable);
        }
        Job job = scanner.poll(level, reachable);
        //Champs : semer demande des graines ; sans graine dans le reseau, on remet a plus tard et on essaie les suivants
        for (int attempt = 0; job != null && order.task == RobotTask.FARMER && job.farm > 0 && attempt < 8; attempt++) {
            if (job.material.isEmpty()) {
                break;
            }
            ItemStack seed = job.material;
            LogisticPoint supply = null;
            double best = Double.MAX_VALUE;
            for (LogisticPoint point : network.points) {
                double score = supplyScore(point, order);
                if (score < best && !RoboticsWorld.peekItem(level, point.endpoint(), stack -> ItemStack.isSameItem(stack, seed)).isEmpty()) {
                    supply = point;
                    best = score;
                }
            }
            if (supply != null) {
                job.stops.add(new Job.Stop(supply.endpoint(), true, LogisticResource.of(seed), 16, null, 0));
                break;
            }
            if (job.farm == 1) {
                //Rebecher reste utile sans graine
                break;
            }
            scanner.postpone(job.key, level.getGameTime() + 600);
            order.status = RoboticOrder.Status.NO_SUPPLIES;
            order.statusItem = String.valueOf(ForgeRegistries.ITEMS.getKey(seed.getItem()));
            job = scanner.poll(level, reachable);
        }
        if (job == null) {
            return null;
        }
        if (job == null) {
            return null;
        }
        job.group = order.id;
        job.priority = order.priority;
        if (order.task == RobotTask.APIARIST && job.type == Job.Type.BLOCK) {
            //Fioles vides pour la part de recolte en fioles (sans fioles : des rayons)
            if (order.honeycomb < 100) {
                LogisticPoint supply = null;
                double best = Double.MAX_VALUE;
                for (LogisticPoint point : network.points) {
                    double score = supplyScore(point, order);
                    if (score < best && !RoboticsWorld.peekItem(level, point.endpoint(), stack -> stack.is(Items.GLASS_BOTTLE))
                          .isEmpty()) {
                        supply = point;
                        best = score;
                    }
                }
                if (supply != null) {
                    job.stops.add(new Job.Stop(supply.endpoint(), true, LogisticResource.of(new ItemStack(Items.GLASS_BOTTLE)), 8,
                          null, 0));
                }
            }
        } else if ((order.task == RobotTask.BREEDER || order.task == RobotTask.APIARIST) && !job.cull) {
            if (!(level.getEntity(job.entityId) instanceof Animal animal)) {
                scanner.release(job.key);
                return null;
            }
            LogisticPoint supply = null;
            ItemStack food = ItemStack.EMPTY;
            double best = Double.MAX_VALUE;
            for (LogisticPoint point : network.points) {
                double score = supplyScore(point, order);
                if (score < best) {
                    ItemStack found = RoboticsWorld.peekItem(level, point.endpoint(), animal::isFood);
                    if (!found.isEmpty()) {
                        supply = point;
                        food = found;
                        best = score;
                    }
                }
            }
            if (supply == null) {
                scanner.release(job.key);
                order.status = RoboticOrder.Status.NO_SUPPLIES;
                return null;
            }
            job.stops.add(new Job.Stop(supply.endpoint(), true, LogisticResource.of(food), 8, null, 0));
        } else if (order.task == RobotTask.LUMBERJACK && job.pos != null && order.replant) {
            ItemStack chosen = order.plantItem();
            LogisticPoint supply = null;
            ItemStack sapling = ItemStack.EMPTY;
            double best = Double.MAX_VALUE;
            for (LogisticPoint point : network.points) {
                double score = supplyScore(point, order);
                if (score < best) {
                    ItemStack found = RoboticsWorld.peekItem(level, point.endpoint(), stack -> chosen.isEmpty() ? stack.is(ItemTags.SAPLINGS)
                                                                                                           : ItemStack.isSameItem(stack, chosen));
                    if (!found.isEmpty()) {
                        supply = point;
                        sapling = found;
                        best = score;
                    }
                }
            }
            if (supply != null) {
                job.stops.add(new Job.Stop(supply.endpoint(), true, LogisticResource.of(sapling), 4, null, 0));
            }
        }
        return job;
    }

    /**
     * Travaux : le prochain retrait, ou la prochaine pose avec ses materiaux (pris a la source la plus proche du chantier). Une pose
     * sans materiau disponible attend un peu (on essaie les suivantes) et l'ordre le signale.
     */
    @Nullable
    private static Job worksJob(ServerLevel level, State state, LogisticNetwork network, RoboticOrder order, ZoneScanner scanner,
          Predicate<BlockPos> reachable) {
        long now = level.getGameTime();
        for (int attempt = 0; attempt < 8; attempt++) {
            Job job = scanner.poll(level, reachable);
            if (job == null) {
                return null;
            }
            job.group = order.id;
            job.priority = order.priority;
            if (job.place == null || job.material.isEmpty()) {
                return job;
            }
            ItemStack material = job.material;
            LogisticPoint supply = null;
            double best = Double.MAX_VALUE;
            for (LogisticPoint point : network.points) {
                double score = supplyScore(point, order);
                if (score < best && !RoboticsWorld.peekItem(level, point.endpoint(), stack -> ItemStack.isSameItem(stack, material)).isEmpty()) {
                    supply = point;
                    best = score;
                }
            }
            if (supply == null) {
                scanner.postpone(job.key, now + 200);
                order.status = RoboticOrder.Status.NO_SUPPLIES;
                order.statusItem = String.valueOf(ForgeRegistries.ITEMS.getKey(material.getItem()));
                alert(state, RoboticsAlert.Type.NO_SOURCE, job.pos, now);
                continue;
            }
            int count = 1 + job.extra.size();
            job.stops.add(new Job.Stop(supply.endpoint(), true, LogisticResource.of(material), count, null, 0));
            return job;
        }
        return null;
    }

    /**
     * Interet d'une source pour les fournitures d'une zone (plus petit = mieux) : la plus proche de la zone (stockage, tampon), les
     * fournisseurs seulement en dernier. MAX_VALUE si ce n'est pas une source.
     */
    private static double supplyScore(LogisticPoint point, RoboticOrder order) {
        if (point.isPointRemoved() || !point.getConfig().role.source || point.getConfig().role == MarkerRole.UNLOAD) {
            return Double.MAX_VALUE;
        }
        double distance = Math.sqrt(point.getPointPos().distSqr(order.anchor()));
        //Les fournisseurs en dernier (la reserve mise a disposition) ; sinon le plus proche : un tampon pres de la zone sert sa zone
        return distance + (point.getConfig().role == MarkerRole.PROVIDER ? 100_000 : 0);
    }

    // ------------------------------------------------------------------
    // Reservations
    // ------------------------------------------------------------------

    /** Livraison du reseau passif (demandes des points, vidages) : aucun ordre derriere. */
    private static boolean passiveJob(Job job) {
        return job.group < 0 && (job.type == Job.Type.TRIP || job.repair);
    }

    /** Unites de ce genre occupees par le reseau passif (carte). */
    public static int passiveBusy(ServerLevel level, UnitKind kind) {
        State state = STATES.get(level.dimension());
        return state == null ? 0 : state.passive.get(kind.ordinal());
    }

    private static void claim(State state, Job job, RobotUnitEntity unit) {
        if (passiveJob(job) && job.passiveKind < 0) {
            job.passiveKind = unit.getKind().ordinal();
            state.passive.addTo(job.passiveKind, 1);
        }
        for (Job.Claim claim : job.claims) {
            state.units.addTo(claim.demand(), 1);
            state.inflight.addTo(claim.demand(), claim.amount());
            state.waiting.remove(claim.demand());
        }
        if (job.group >= 0) {
            state.orderUnits.addTo(job.group, 1);
            state.bind(unit, job.group);
        } else {
            //Un autre travail : l'unite quitte son ordre
            state.unbind(unit);
        }
    }

    /**
     * Recompte les reservations d'apres les travaux reellement en cours : une reservation perdue (unite dechargee, travail abandonne en
     * route...) ne doit jamais bloquer une demande pour toujours.
     */
    private static void audit(ServerLevel level, State state) {
        Long2IntOpenHashMap units = new Long2IntOpenHashMap();
        Long2LongOpenHashMap inflight = new Long2LongOpenHashMap();
        Int2IntOpenHashMap busy = new Int2IntOpenHashMap();
        Int2IntOpenHashMap passive = new Int2IntOpenHashMap();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet repairing = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RobotUnitEntity unit && unit.isAlive() && unit.getJob() != null) {
                Job job = unit.getJob();
                if (job.passiveKind >= 0) {
                    passive.addTo(job.passiveKind, 1);
                }
                if (job.repair) {
                    for (BlockPos pos : ZoneScanner.placements(job)) {
                        repairing.add(pos.asLong());
                    }
                }
                for (Job.Claim claim : job.claims) {
                    units.addTo(claim.demand(), 1);
                    inflight.addTo(claim.demand(), claim.amount());
                }
                if (job.group >= 0 && !unit.isIdle()) {
                    busy.addTo(job.group, 1);
                }
            }
        }
        state.units.clear();
        state.units.putAll(units);
        state.inflight.clear();
        state.inflight.putAll(inflight);
        state.orderUnits.clear();
        state.orderUnits.putAll(busy);
        state.passive.clear();
        state.passive.putAll(passive);
        state.repairing.clear();
        state.repairing.addAll(repairing);
    }

    /** L'unite rentre au port ou disparait : elle libere sa place sur son ordre. */
    public static void leave(ServerLevel level, RobotUnitEntity unit) {
        State state = STATES.get(level.dimension());
        if (state != null) {
            state.unbind(unit);
        }
    }

    /** Le travail est fini (ou abandonne) : on libere ce qu'il reservait. */
    public static void release(ServerLevel level, Job job) {
        State state = state(level);
        for (Job.Claim claim : job.claims) {
            if (state.units.addTo(claim.demand(), -1) <= 1) {
                state.units.remove(claim.demand());
            }
            if (state.inflight.addTo(claim.demand(), -claim.amount()) <= claim.amount()) {
                state.inflight.remove(claim.demand());
            }
        }
        job.claims.clear();
        if (job.repair) {
            for (BlockPos pos : ZoneScanner.placements(job)) {
                state.repairing.remove(pos.asLong());
            }
        }
        if (job.passiveKind >= 0) {
            if (state.passive.addTo(job.passiveKind, -1) <= 1) {
                state.passive.remove(job.passiveKind);
            }
            job.passiveKind = -1;
        }
        if (job.group >= 0) {
            if (state.orderUnits.addTo(job.group, -1) <= 1) {
                state.orderUnits.remove(job.group);
            }
            ZoneScanner scanner = state.scanners.get(job.group);
            if (scanner != null && job.type != Job.Type.TRIP) {
                scanner.release(job.key);
            }
        }
    }

    /** Travail prepare mais jamais confie : on rend sa cle a la zone. */
    private static void discard(ServerLevel level, Job job) {
        ZoneScanner scanner = job.group >= 0 ? state(level).scanners.get(job.group) : null;
        if (scanner != null && job.type != Job.Type.TRIP) {
            scanner.release(job.key);
        }
    }

    /** Travail inaccessible : libere, et la zone n'y revient pas avant une minute. */
    public static void abandon(ServerLevel level, Job job) {
        int group = job.group;
        long key = job.key;
        Job.Type type = job.type;
        release(level, job);
        ZoneScanner scanner = state(level).scanners.get(group);
        if (scanner != null && type != Job.Type.TRIP) {
            scanner.abandon(key, level.getGameTime());
        }
    }

    /** Une unite au sol n'atteint pas ce point : pendant 5 minutes, il est reserve aux drones. */
    public static void groundUnreachable(ServerLevel level, BlockPos pos) {
        State state = state(level);
        state.groundBlocked.put(pos.asLong(), level.getGameTime() + GROUND_BLOCK_TICKS);
        alert(state, RoboticsAlert.Type.UNREACHABLE, pos, level.getGameTime());
    }

    /** Livraison d'une tournee : avancement des ordres ponctuels (un ordre accompli disparait). */
    public static void delivered(ServerLevel level, Job job, long amount) {
        if (job.group < 0 || amount <= 0) {
            return;
        }
        RoboticsOrders orders = RoboticsOrders.get(level);
        RoboticOrder order = orders.get(job.group);
        if (order == null) {
            return;
        }
        order.delivered += amount;
        orders.setDirty();
        if (!order.recurring && order.delivered >= order.amount) {
            orders.remove(order.id);
            state(level).dirty = true;
        }
    }

    // ------------------------------------------------------------------
    // Unites qui ont fini, ou qui attendent
    // ------------------------------------------------------------------

    /** L'unite attend dehors (pres d'un relais) : elle peut recevoir un travail directement. */
    public static void setIdle(ServerLevel level, RobotUnitEntity unit, boolean idle) {
        if (idle) {
            state(level).idle.add(unit);
        } else {
            state(level).idle.remove(unit);
        }
    }

    /**
     * Travail suivant pour une unite dehors (son travail precedent deja libere) : la suite de sa zone si elle a de la place, sinon le
     * travail en attente le plus proche du reseau. Null s'il n'y a rien.
     */
    @Nullable
    public static Job next(ServerLevel level, RobotUnitEntity unit, @Nullable Job finished, RoboticHubData hub) {
        return next(level, unit, finished, hub, -1);
    }

    /** {@code resume} : zone sur laquelle l'unite attend (chantier), reprise en priorite. */
    @Nullable
    public static Job next(ServerLevel level, RobotUnitEntity unit, @Nullable Job finished, RoboticHubData hub, int resume) {
        State state = state(level);
        long now = level.getGameTime();
        LogisticsRegistry registry = LogisticsRegistry.get(level);
        LogisticNetwork network = registry.networkOf(hub, now);
        if (network == null || hub.isRecalling()) {
            return null;
        }
        RoboticsOrders orders = RoboticsOrders.get(level);
        Candidate self = new Candidate(hub, unit, unit.getSlot(), unit.getTier());
        //La suite de la meme zone
        int zone = finished != null && finished.type != Job.Type.TRIP ? finished.group : resume;
        if (zone >= 0 && !unit.getCargo().isFull()) {
            RoboticOrder order = orders.get(zone);
            if (order != null && !order.paused && order.isValid() && order.assignable(hub) && state.room(unit, order)) {
                Job job = zoneJob(level, state, registry, network, order, network::covers);
                if (job != null) {
                    if (allowed(state, self, job, order, now)) {
                        claim(state, job, unit);
                        return job;
                    }
                    discard(level, job);
                }
            }
        }
        //Un transport : l'unite reste sur son ordre tant qu'il y a a faire (le reseau ne la detourne pas)
        if (finished != null && finished.group >= 0 && finished.type == Job.Type.TRIP && unit.getCargo().isEmpty()) {
            RoboticOrder order = orders.get(finished.group);
            if (order != null && order.type == RoboticOrder.Type.TRANSFER && !order.paused && order.isValid() && order.assignable(hub)
                && state.room(unit, order)) {
                Job job = transferJob(level, state, registry, network, order);
                if (job != null) {
                    job.priority = order.priority;
                    if (allowed(state, self, job, order, now)) {
                        claim(state, job, unit);
                        return job;
                    }
                    discard(level, job);
                }
            }
        }
        if (!unit.getCargo().isEmpty()) {
            return null;
        }
        //Le travail en attente le plus proche
        List<Job> pending = state.pending.get(network.id);
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        Job best = null;
        double bestScore = 0;
        for (Job job : pending) {
            RoboticOrder order = job.group >= 0 ? orders.get(job.group) : null;
            if (job.group >= 0 && (order == null || !state.room(unit, order))) {
                continue;
            }
            if (!allowed(state, self, job, order, now)) {
                continue;
            }
            double score = score(self, job);
            if (score > bestScore) {
                best = job;
                bestScore = score;
            }
        }
        if (best == null) {
            return null;
        }
        pending.remove(best);
        //Toujours d'actualite ?
        Job.Stop first = best.stops.isEmpty() ? null : best.stops.get(0);
        if (first != null && first.load() && RoboticsWorld.extractable(level, first.at(), first.res()) <= 0) {
            return null;
        }
        for (Job.Claim claim : best.claims) {
            if (claim.demand() != 0 && state.units.get(claim.demand()) >= REQUEST_UNITS) {
                return null;
            }
        }
        claim(state, best, unit);
        return best;
    }

    /** Reveil d'une tortue endormie (mode repos) : un travail de sa zone, s'il y en a un et qu'il reste de la place sur l'ordre. */
    @Nullable
    public static Job wake(ServerLevel level, RobotUnitEntity unit, RoboticHubData hub, int orderId) {
        State state = state(level);
        long now = level.getGameTime();
        LogisticsRegistry registry = LogisticsRegistry.get(level);
        LogisticNetwork network = registry.networkOf(hub, now);
        RoboticOrder order = RoboticsOrders.get(level).get(orderId);
        if (network == null || order == null || order.paused || !order.isValid() || !state.room(unit, order)) {
            return null;
        }
        Job job = zoneJob(level, state, registry, network, order, network::covers);
        if (job == null) {
            return null;
        }
        if (!allowed(state, new Candidate(hub, unit, unit.getSlot(), unit.getTier()), job, order, now)) {
            discard(level, job);
            return null;
        }
        state.idle.remove(unit);
        claim(state, job, unit);
        return job;
    }

    /** Un ordre a change (menu) : on oublie l'avancement de sa zone. */
    public static void forget(ServerLevel level, int id) {
        state(level).scanners.remove(id);
    }
}
