package fr.pioneers.rail;

import fr.pioneers.Pioneers;
import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.rail.block.ConductorBlock;
import fr.pioneers.rail.block.SubstationBlock;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.logistics.RailLibrary;
import fr.pioneers.rail.logistics.StationLogistics;
import fr.pioneers.rail.logistics.StationStock;
import fr.pioneers.rail.logistics.WorldStationStock;
import fr.pioneers.rail.net.Electrification;
import fr.pioneers.rail.net.Pathfinder;
import fr.pioneers.rail.net.Piece;
import fr.pioneers.rail.net.PortKey;
import fr.pioneers.rail.net.RailNetwork;
import fr.pioneers.rail.net.Step;
import fr.pioneers.rail.track.TrackBlock;
import fr.pioneers.rail.track.TrackPath;
import fr.pioneers.rail.track.TrackPlacement;
import fr.pioneers.rail.track.TrackQuality;
import fr.pioneers.rail.train.Car;
import fr.pioneers.rail.train.CarType;
import fr.pioneers.rail.train.RailCarEntity;
import fr.pioneers.rail.train.RailFuels;
import fr.pioneers.rail.train.RailItems;
import fr.pioneers.rail.train.Schedule;
import fr.pioneers.rail.train.Train;
import fr.pioneers.registries.PiRail;
import fr.pioneers.robotics.works.WorksData;
import fr.pioneers.robotics.works.WorksPlan;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : le chemin de fer d'une dimension : reseau ({@link RailNetwork}) et trains, simules a chaque tick meme dans les chunks non
 * charges. Les vehicules n'apparaissent comme entites ({@link RailCarEntity}) que la ou le monde est charge.
 * <p>
 * <b>Automatisme</b> (aucune redstone) : chaque train en automatique cherche un chemin vers la prochaine gare de son horaire
 * ({@link Pathfinder}), reserve devant lui les cantons (sections separees par les signaux) et pose lui-meme les aiguillages ; il s'arrete
 * toujours avant un autre train, signal ou pas. Sans signal, le reseau entier est un seul canton : un seul train y roule a la fois.
 * <p>
 * <b>Physique</b> : effort de traction des locomotives (combustible et eau pour la vapeur), masse (charge comprise), pente, frottement,
 * vitesse limite des voies et des courbes (freinage anticipe), charge tiree au-dela de laquelle la vitesse baisse.
 */
public final class RailWorld extends SavedData {

    public static final String NAME = Pioneers.MODID + "_rail";
    /** Degats du train (cree a la demande : le banc d'essai tourne sans les registres du jeu). */
    private static ResourceKey<DamageType> trainDamage;

    public static ResourceKey<DamageType> trainDamage() {
        if (trainDamage == null) {
            trainDamage = ResourceKey.create(Registries.DAMAGE_TYPE, Pioneers.rl("train"));
        }
        return trainDamage;
    }
    /** Freinage de service (px/tick^2) et freinage d'urgence. */
    public static final float BRAKE = 0.11F, EMERGENCY = 0.35F;
    /** Pesanteur dans les pentes (px/tick^2 par unite de sinus). */
    private static final float GRAVITY = 0.06F;
    /** Marge d'arret devant un obstacle (px). */
    private static final float MARGIN = 1.5F;

    public final RailNetwork net = new RailNetwork();
    /** 0.20.5 : groupes de trains et interruptions. */
    public final RailLibrary library = new RailLibrary();
    /** 0.20.5 : repartiteur (livraisons entre gares logistiques). */
    public final RailDispatcher dispatcher = new RailDispatcher(this);
    /** 0.20.5 : ce que le moteur sait des gares dans le monde (le jeu le branche, le banc le simule). */
    public StationStock stocks = StationStock.NONE;
    /** 0.20.5 : signaux nommes (ecrits par PioScript, lus par les conditions SIGNAL). */
    public final Map<String, Integer> signals = new HashMap<>();
    /** 0.20.6 : plaques tournantes et ponts transbordeurs. */
    private final Map<UUID, Train> trains = new LinkedHashMap<>();
    private final Map<UUID, Train> carOwner = new HashMap<>();
    //Etat de travail (non sauvegarde)
    private final Long2ObjectOpenHashMap<List<Train>> pieceOcc = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<Train>> sectionOcc = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<UUID> reservedBy = new Int2ObjectOpenHashMap<>();
    private final Map<UUID, RailCarEntity> views = new HashMap<>();
    private final LongOpenHashSet routeDirty = new LongOpenHashSet();
    private final LongOpenHashSet detectorDirty = new LongOpenHashSet();
    private final Map<Long, Integer> weighLevel = new HashMap<>();
    /** 0.20.5 : derniere pesee de chaque pont-bascule. */
    private final Map<Long, Weigh> lastWeigh = new HashMap<>();
    private final Map<UUID, Integer> cutAt = new HashMap<>();
    private int lastSections = -1, lastVersion = -1;
    @Nullable
    private ServerLevel level;

    public static RailWorld get(ServerLevel level) {
        RailWorld w = level.getDataStorage().computeIfAbsent(RailWorld::load, RailWorld::new, NAME);
        w.level = level;
        if (w.stocks == StationStock.NONE) {
            w.stocks = new WorldStationStock(level, w.net::stations);
        }
        return w;
    }

    // ------------------------------------------------------------------
    // Trains
    // ------------------------------------------------------------------

    public Iterable<Train> trains() {
        return trains.values();
    }

    @Nullable
    public Train train(UUID id) {
        return trains.get(id);
    }

    @Nullable
    public Train trainOf(UUID car) {
        return carOwner.get(car);
    }

    private void addTrain(Train t) {
        trains.put(t.id, t);
        for (Car c : t.cars) {
            carOwner.put(c.id, t);
        }
        t.computeOccupied();
        setDirty();
    }

    private void removeTrain(Train t) {
        removeTrain(t, false);
    }

    /** Retire un train ; {@code keepViews} : ses vehicules continuent dans un autre train (attelage, coupe) : leurs entites et passagers restent. */
    private void removeTrain(Train t, boolean keepViews) {
        release(t, true);
        trains.remove(t.id);
        for (Car c : t.cars) {
            carOwner.remove(c.id);
            if (!keepViews) {
                RailCarEntity e = views.remove(c.id);
                if (e != null) {
                    e.ejectSafely();
                    e.discard();
                }
            }
        }
        setDirty();
    }

    /** Pose d'un vehicule sur une voie (clic avec son objet) ; il s'attelle au train voisin s'il le touche. */
    public boolean placeCar(BlockPos pos, CarType type, int tier, Direction facing) {
        return placeCar(pos, type, tier, facing, null);
    }

    /** Pose un vehicule ({@code stores} : reserves gardees dans l'objet d'un vehicule ramasse). */
    public boolean placeCar(BlockPos pos, CarType type, int tier, Direction facing, @Nullable CompoundTag stores) {
        Piece piece = net.at(pos);
        if (piece == null) {
            return false;
        }
        //Chemin le mieux aligne sur le regard du joueur
        int bestPath = 0;
        boolean bestFwd = true;
        double best = -2;
        float[] tan = new float[3];
        for (int i = 0; i < piece.paths().size(); i++) {
            TrackPath p = piece.path(i);
            p.tangent(p.length / 2, tan);
            double dot = tan[0] * facing.getStepX() + tan[2] * facing.getStepZ();
            if (Math.abs(dot) > best) {
                best = Math.abs(dot);
                bestPath = i;
                bestFwd = dot >= 0;
            }
        }
        Step step = new Step(piece.key(), bestPath, bestFwd);
        float len = piece.path(bestPath).length;
        Train t = new Train();
        Car fresh = new Car(type, tier);
        if (stores != null) {
            fresh.loadStores(stores);
        }
        t.cars.add(fresh);
        //Centre du vehicule au milieu du chemin : 8 px devant, 8 px derriere (repousse si la voie s'arrete)
        Train.Router router = defaultRouter();
        t.place(step, len / 2);
        float h = t.extendHead(net, Train.CAR / 2, router);
        float b = t.extendTail(net, Train.CAR - h, router);
        if (h + b < Train.CAR - 0.01F && h + b + t.extendHead(net, Train.CAR - h - b, router) < Train.CAR - 0.01F) {
            return false;
        }
        if (overlaps(t, null)) {
            return false;
        }
        addTrain(t);
        couple(t);
        return true;
    }

    /** Le train touche-t-il un autre train (pieces partagees, a moins de 2 px) ? */
    private boolean overlaps(Train t, @Nullable Train ignore) {
        double[] a = new double[3];
        for (Train u : trains.values()) {
            if (u == t || u == ignore) {
                continue;
            }
            for (int i = 0; i < t.cars.size(); i++) {
                Vec3 c = t.pose(net, i).center();
                for (int j = 0; j < u.cars.size(); j++) {
                    if (u.pose(net, j).center().distanceToSqr(c) < (15 / 16.0) * (15 / 16.0)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ramasse un vehicule (coup du joueur) : objet et contenu au sol, le train est coupe en deux. */
    public void pickUp(ServerPlayer player, RailCarEntity view) {
        Train t = view.train == null ? null : trains.get(view.train);
        if (t == null || view.car == null) {
            view.discard();
            return;
        }
        int index = -1;
        for (int i = 0; i < t.cars.size(); i++) {
            if (t.cars.get(i).id.equals(view.car)) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        Car car = t.cars.get(index);
        Vec3 at = view.position();
        if (!player.getAbilities().instabuild) {
            ItemStack item = RailItems.stack(car.type, car.tier);
            if (car.hasStores()) {
                //0.20.2 : citerne, batterie, carburant... gardes dans l'objet et rendus a la pose
                item.addTagElement(RailItems.STORE_TAG, car.saveStores());
            }
            Containers.dropItemStack(level, at.x, at.y + 0.4, at.z, item);
        }
        for (int i = 0; i < car.items.getSlots(); i++) {
            ItemStack s = car.items.getStackInSlot(i);
            if (!s.isEmpty()) {
                Containers.dropItemStack(level, at.x, at.y + 0.4, at.z, s.copy());
            }
        }
        view.ejectSafely();
        split(t, index, true);
        RailCarEntity gone = views.remove(car.id);
        if (gone != null) {
            gone.discard();
        }
    }

    /** Coupe le train : retire le vehicule {@code index} si {@code drop}, sinon coupe derriere lui. */
    public void split(Train t, int index, boolean drop) {
        removeTrain(t, true);
        List<Car> frontCars = new ArrayList<>(t.cars.subList(0, index));
        List<Car> backCars = new ArrayList<>(t.cars.subList(drop ? index + 1 : index + 1, t.cars.size()));
        if (!drop) {
            frontCars.add(t.cars.get(index));
        }
        Set<Long> guide = new HashSet<>();
        t.guide(guide);
        if (!frontCars.isEmpty()) {
            Train a = new Train(t.id);
            a.name = t.name;
            a.cars.addAll(frontCars);
            a.schedule = t.schedule;
            a.stopIndex = t.stopIndex;
            a.group = t.group;
            a.interrupts.addAll(t.interrupts);
            a.logistic = t.logistic;
            a.auto = false;
            a.rebuild(net, t.head(), t.headOffset(), a.length(), guide);
            addTrain(a);
        }
        if (!backCars.isEmpty()) {
            float[] along = new float[1];
            float cut = (frontCars.size() + (drop ? 1 : 0)) * Train.CAR;
            Step s = t.stepAt(net, cut, along);
            if (s != null) {
                Train b = new Train();
                b.cars.addAll(backCars);
                float headAlong = s.forward() ? along[0] : net.length(s) - along[0];
                b.rebuild(net, s, headAlong, b.length(), guide);
                b.schedule = t.schedule.copy();
                addTrain(b);
            }
        }
    }

    /** Attelage : un train arrete contre un autre s'y accroche (tampon contre tampon). */
    private boolean couple(Train t) {
        for (Train u : new ArrayList<>(trains.values())) {
            if (u == t) {
                continue;
            }
            int contact = contact(t, u);
            if (contact >= 0) {
                merge(t, u, contact);
                return true;
            }
        }
        return false;
    }

    /**
     * Ends en contact : bit 0 = bout de t (0 tete, 1 queue), bit 1 = bout de u ; -1 sinon.
     */
    private int contact(Train t, Train u) {
        double[] p = new double[3];
        double[][] te = new double[2][3], ue = new double[2][3];
        t.locate(net, 0, te[0], null);
        t.locate(net, t.length(), te[1], null);
        u.locate(net, 0, ue[0], null);
        u.locate(net, u.length(), ue[1], null);
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                double dx = te[a][0] - ue[b][0], dy = te[a][1] - ue[b][1], dz = te[a][2] - ue[b][2];
                if (dx * dx + dy * dy + dz * dz < (2.5 / 16) * (2.5 / 16)) {
                    return a | b << 1;
                }
            }
        }
        return -1;
    }

    private void merge(Train t, Train u, int contact) {
        boolean tHead = (contact & 1) == 0, uHead = (contact & 2) == 0;
        Set<Long> guide = new HashSet<>();
        t.guide(guide);
        u.guide(guide);
        //Ordre : de l'autre bout de u jusqu'au contact, puis de t depuis le contact jusqu'a son autre bout
        List<Car> cars = new ArrayList<>();
        List<Car> uc = new ArrayList<>(u.cars);
        if (uHead) {
            Collections.reverse(uc);
            uc.forEach(c -> c.flipped = !c.flipped);
        }
        List<Car> tc = new ArrayList<>(t.cars);
        if (!tHead) {
            Collections.reverse(tc);
            tc.forEach(c -> c.flipped = !c.flipped);
        }
        cars.addAll(uc);
        cars.addAll(tc);
        //Tete du train fusionne : bout libre de u
        Step head;
        float headAlong;
        if (uHead) {
            head = u.tail().reverse();
            headAlong = net.length(u.tail()) - u.tailOffset();
        } else {
            head = u.head();
            headAlong = u.headOffset();
        }
        Train keep = t.hasLocomotive() || !u.hasLocomotive() ? t : u;
        Train merged = new Train(keep.id);
        merged.name = keep.name;
        merged.schedule = keep.schedule;
        merged.stopIndex = keep.stopIndex;
        merged.group = keep.group;
        merged.interrupts.addAll(keep.interrupts);
        merged.logistic = keep.logistic;
        merged.cars.addAll(cars);
        //0.20.31 : une locomotive a chaque bout : celle de queue se retourne vers l'exterieur (rame reversible)
        merged.orientEnds();
        removeTrain(t, true);
        removeTrain(u, true);
        if (!merged.rebuild(net, head, headAlong, merged.length(), guide)) {
            //Repli : on garde les deux trains separes
            merged.rebuild(net, head, headAlong, merged.length(), guide);
        }
        addTrain(merged);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public void tick(ServerLevel level) {
        this.level = level;
        long now = level.getGameTime();
        simulate(now);
        syncViews(level, now);
        if (now % 4 == 0) {
            updateSignals(level);
        }
        applyVisuals(level);
        if (now % 10 == 5) {
            layTracks(level);
        }
        if (now % 100 == 0 && !trains.isEmpty()) {
            setDirty();
        }
    }

    /** Un tick de simulation (sans le monde : aussi utilise par le banc d'essai hors jeu). */
    public void simulate(long now) {
        int sections = net.sections();
        if (net.version() != lastVersion) {
            lastVersion = net.version();
            //Les numeros de canton ont pu changer : les reservations repartent de zero
            reservedBy.clear();
            for (Train t : trains.values()) {
                t.reserved.clear();
                t.entered.clear();
                t.planVersion = -1;
            }
        }
        lastSections = sections;
        rebuildOccupancy();
        for (Train t : new ArrayList<>(trains.values())) {
            if (!trains.containsKey(t.id)) {
                continue;
            }
            vacate(t);
            tickTrain(t, now);
            t.computeOccupied();
            occupy(t);
            releasePassed(t);
        }
        for (Map.Entry<UUID, Integer> e : cutAt.entrySet()) {
            Train t = trains.get(e.getKey());
            if (t != null && e.getValue() < t.cars.size() - 1) {
                cutAtUncoupler(t, e.getValue(), now);
            }
        }
        cutAt.clear();
        dispatcher.tick(now);
    }

    // ------------------------------------------------------------------
    // 0.20.5 : pont-bascule et rail de detelage
    // ------------------------------------------------------------------

    /** Pesee d'un train : masse (t), vehicules, remplissage moyen (0..1), nom. */
    public record Weigh(float mass, int cars, float fill, String train, long time) {

        public int comparator() {
            return Math.max(1, Math.min(15, Math.round(fill * 15)));
        }

        public Component text() {
            return Component.translatable("rail.pioneers.weigh", train, String.format(Locale.ROOT, "%.1f", mass), cars, Math.round(fill * 100));
        }
    }

    /** Pesee du train sur ce pont-bascule (ou la derniere). */
    @Nullable
    public Weigh weigh(BlockPos pos) {
        Piece p = net.at(pos);
        if (p == null) {
            p = net.piece(pos.asLong());
        }
        if (p == null || p.kind != Piece.Kind.WEIGHBRIDGE) {
            return null;
        }
        List<Train> on = pieceOcc.get(p.key());
        if (on == null || on.isEmpty()) {
            return lastWeigh.get(p.key());
        }
        Train t = on.get(0);
        float fill = 0;
        int n = 0;
        for (Car c : t.cars) {
            if (cargo(c)) {
                fill += c.fill();
                n++;
            }
        }
        Weigh w = new Weigh(t.mass(), t.cars.size(), n == 0 ? 0 : fill / n, t.name.isEmpty() ? t.id.toString().substring(0, 8) : t.name,
              level == null ? 0 : level.getGameTime());
        lastWeigh.put(p.key(), w);
        return w;
    }

    /** Train arrete a quai : un attelage au-dessus d'un rail de detelage ? (la coupe se fait apres la boucle) */
    private void uncouple(Train t) {
        if (t.uncoupled || t.cars.size() < 2) {
            return;
        }
        t.uncoupled = true;
        double[] pos = new double[3];
        for (int i = 0; i < t.cars.size() - 1; i++) {
            if (!t.locate(net, (i + 1) * Train.CAR, pos, null)) {
                continue;
            }
            Piece p = net.at(BlockPos.containing(pos[0], pos[1] + 0.2, pos[2]));
            if (p != null && p.kind == Piece.Kind.UNCOUPLER) {
                cutAt.put(t.id, i);
                return;
            }
        }
    }

    /** Coupe derriere le vehicule {@code index} : l'avant garde son horaire et son arret, l'arriere reste en manuel. */
    private void cutAtUncoupler(Train t, int index, long now) {
        boolean auto = t.auto;
        Train.Status status = t.status;
        long dest = t.destination;
        long arrivedAt = t.arrivedAt, activity = t.lastActivity;
        List<Schedule.Stop> temp = new ArrayList<>(t.temp);
        int tempIndex = t.tempIndex;
        Train.TempKind kind = t.tempKind;
        String tempName = t.tempName;
        UUID delivery = t.delivery;
        split(t, index, false);
        Train front = trains.get(t.id);
        if (front == null) {
            return;
        }
        front.auto = auto && front.canRunAuto();
        if (front.auto) {
            front.status = status;
            front.destination = dest;
            front.arrivedAt = arrivedAt;
            front.lastActivity = activity;
            front.temp.addAll(temp);
            front.tempIndex = tempIndex;
            front.tempKind = kind;
            front.tempName = tempName;
            front.delivery = delivery;
        }
        front.uncoupled = true;
        setDirty();
    }

    /** Banc d'essai : trains et reservations visibles. */
    public Collection<Train> allTrains() {
        return trains.values();
    }

    private void rebuildOccupancy() {
        pieceOcc.clear();
        sectionOcc.clear();
        for (Train t : trains.values()) {
            occupy(t);
        }
    }

    private void occupy(Train t) {
        for (long a : t.occupied) {
            pieceOcc.computeIfAbsent(a, k -> new ArrayList<>(1)).add(t);
            Piece p = net.piece(a);
            if (p != null) {
                List<Train> list = sectionOcc.computeIfAbsent(p.section(), k -> new ArrayList<>(1));
                if (!list.contains(t)) {
                    list.add(t);
                }
            }
            if (p != null && (p.kind == Piece.Kind.DETECTOR || p.kind == Piece.Kind.WEIGHBRIDGE)) {
                detectorDirty.add(a);
            }
        }
    }

    private void vacate(Train t) {
        for (long a : t.occupied) {
            List<Train> list = pieceOcc.get(a);
            if (list != null) {
                list.remove(t);
                if (list.isEmpty()) {
                    pieceOcc.remove(a);
                }
            }
            Piece p = net.piece(a);
            if (p != null) {
                List<Train> sl = sectionOcc.get(p.section());
                if (sl != null) {
                    sl.remove(t);
                    if (sl.isEmpty()) {
                        sectionOcc.remove(p.section());
                    }
                }
                if (p.kind == Piece.Kind.DETECTOR || p.kind == Piece.Kind.WEIGHBRIDGE) {
                    detectorDirty.add(a);
                }
            }
        }
    }

    private boolean pieceTakenByOther(long anchor, Train t) {
        List<Train> list = pieceOcc.get(anchor);
        if (list == null) {
            return false;
        }
        for (Train u : list) {
            if (u != t) {
                return true;
            }
        }
        return false;
    }

    private boolean sectionFreeFor(int section, Train t) {
        List<Train> list = sectionOcc.get(section);
        if (list != null) {
            for (Train u : list) {
                if (u != t) {
                    return false;
                }
            }
        }
        UUID r = reservedBy.get(section);
        return r == null || r.equals(t.id);
    }

    public boolean occupied(long anchor) {
        return pieceOcc.containsKey(anchor);
    }

    private void reserve(Train t, int section) {
        reservedBy.put(section, t.id);
        if (!t.reserved.contains(section)) {
            t.reserved.add(section);
        }
    }

    /** Libere les cantons deja quittes (et tous, si {@code all}). */
    private void release(Train t, boolean all) {
        for (int i = t.reserved.size() - 1; i >= 0; i--) {
            int s = (int) t.reserved.getLong(i);
            if (all || !t.entered.contains(s)) {
                if (t.id.equals(reservedBy.get(s))) {
                    reservedBy.remove(s);
                }
                t.reserved.removeLong(i);
                t.entered.remove(s);
            }
        }
    }

    private void releasePassed(Train t) {
        LongOpenHashSet mine = new LongOpenHashSet();
        for (long a : t.occupied) {
            Piece p = net.piece(a);
            if (p != null) {
                mine.add(p.section());
            }
        }
        for (int i = t.reserved.size() - 1; i >= 0; i--) {
            int s = (int) t.reserved.getLong(i);
            if (mine.contains(s)) {
                t.entered.add(s);
            } else if (t.entered.contains(s)) {
                if (t.id.equals(reservedBy.get(s))) {
                    reservedBy.remove(s);
                }
                t.reserved.removeLong(i);
                t.entered.remove(s);
            }
        }
    }

    // ------------------------------------------------------------------
    // Un train
    // ------------------------------------------------------------------

    private void tickTrain(Train t, long now) {
        if (t.trail().isEmpty() || t.cars.isEmpty()) {
            removeTrain(t);
            return;
        }
        if (!t.intact(net)) {
            t.status = Train.Status.DERAILED;
            t.speed = 0;
            t.effort = 0;
            return;
        }
        readDriver(t);
        if (t.auto && !t.canRunAuto()) {
            t.auto = false;
        }
        t.effort = 0;
        t.brakeLoss = 0;
        float mass = t.mass();
        float grade = t.grade(net);
        //--- puissance disponible et vitesse propre
        float tractive = 0, vmax = Float.MAX_VALUE;
        int haul = 0, cargo = 0;
        boolean powered = false;
        for (Car c : t.cars) {
            if (c.type.locomotive) {
                powered = true;
                vmax = Math.min(vmax, c.type.maxSpeed(c.tier));
                haul += c.type.haul(c.tier);
                tractive += c.type.tractive(c.tier) * power(t, c, now);
            } else {
                cargo++;
            }
        }
        if (!powered) {
            vmax = 34 * 0.8F;
        }
        if (powered && cargo > haul && haul > 0) {
            vmax *= Math.max(0.3F, haul / (float) cargo);
        }
        vmax = Math.min(vmax, Math.max(0.4F, t.trackLimit(net)));
        float accel = powered ? tractive / mass : 0;
        t.accel = accel;
        float resist = 0.0012F + 0.00002F * t.speed * t.speed;
        float gravity = -GRAVITY * grade;
        Train.Router router = routerFor(t);
        float authority = Float.MAX_VALUE, ahead = Float.MAX_VALUE;
        //--- conduite
        if (t.auto) {
            boolean go = autopilot(t, now);
            if (!go) {
                t.speed = 0;
                return;
            }
            float[] look = lookAhead(t, vmax);
            authority = look[0];
            ahead = look[1];
            float vt = Math.min(vmax, Math.min(ahead, (float) Math.sqrt(2 * BRAKE * Math.max(0, authority))));
            if (t.speed < vt) {
                float gain = Math.max(0, accel + gravity - resist);
                if (accel <= 0) {
                    t.status = Train.Status.NO_FUEL;
                }
                t.speed = Math.min(vt, t.speed + gain);
                t.effort = accel > 0 ? Math.min(1, (gain + resist) / Math.max(1e-4F, accel) + 0.15F) : 0;
            } else {
                float before = t.speed;
                t.speed = Math.max(vt, t.speed - (t.speed - vt > BRAKE ? EMERGENCY : BRAKE));
                t.brakeLoss = Math.max(0, before - t.speed);
            }
            if (authority < 1 && t.speed < 0.05F) {
                t.speed = 0;
            }
            t.speed = Math.max(0, Math.min(t.speed, authority));
            if (t.speed <= 0.001F && t.status == Train.Status.RUNNING) {
                t.blockedTicks++;
                t.status = Train.Status.WAIT_SIGNAL;
            } else if (t.speed > 0.001F) {
                t.blockedTicks = 0;
                if (t.status == Train.Status.WAIT_SIGNAL || t.status == Train.Status.WAIT_TRAIN || t.status == Train.Status.NO_FUEL) {
                    t.status = Train.Status.RUNNING;
                }
            }
            if (t.blockedTicks == 100 || t.blockedTicks > 0 && t.blockedTicks % 200 == 0) {
                //Bloque 5 s : on cherche un autre chemin
                t.planVersion = -1;
            }
        } else {
            t.status = t.driverIdle < 40 ? Train.Status.MANUAL : Train.Status.IDLE;
            boolean driven = t.driverIdle < 5 && powered;
            float dir = Math.signum(t.speed);
            if (driven) {
                vmax *= t.driverBoost;
                drivenXp(t);
            }
            if (driven && t.throttle > 0.1F) {
                if (t.speed < -0.01F) {
                    float before = t.speed;
                    t.speed = Math.min(0, t.speed + BRAKE);
                    t.brakeLoss = t.speed - before;
                } else {
                    t.speed = Math.min(vmax, t.speed + Math.max(0, accel + gravity - resist));
                    t.effort = accel > 0 ? 1 : 0;
                }
            } else if (driven && t.throttle < -0.1F) {
                if (t.speed > 0.01F) {
                    float before = t.speed;
                    t.speed = Math.max(0, t.speed - BRAKE);
                    t.brakeLoss = before - t.speed;
                } else {
                    t.speed = Math.max(-vmax * 0.5F, t.speed - Math.max(0, accel - gravity - resist));
                    t.effort = accel > 0 ? 1 : 0;
                }
            } else {
                //Roue libre : pente, frottement ; freins de stationnement des trains moteurs
                float v = t.speed + gravity;
                v -= Math.signum(v) * Math.min(Math.abs(v), resist + (powered ? 0.01F : 0.0008F));
                if (powered && Math.abs(v) < 0.05F && Math.abs(t.speed) < 0.05F) {
                    v = 0;
                }
                t.speed = v;
                applyTrackEffects(t, powered);
            }
            if (Math.abs(t.speed) < 0.001F) {
                t.speed = 0;
            }
            if (dir != 0 && Math.signum(t.speed) != dir && Math.signum(t.speed) != 0) {
                //Changement de sens : on s'arrete d'abord
                t.speed = 0;
            }
            if (t.speed != 0) {
                float room = manualRoom(t, t.speed > 0, Math.abs(t.speed) + 2, router);
                if (room < Math.abs(t.speed)) {
                    float contactSpeed = Math.abs(t.speed);
                    t.speed = Math.signum(t.speed) * Math.max(0, room);
                    float moved = t.move(net, t.speed, router);
                    t.speed = 0;
                    if (contactSpeed < 3.2F) {
                        couple(t);
                    }
                    consume(t, now);
                    return;
                }
            }
        }
        float moved = t.move(net, t.speed, router);
        if (Math.abs(moved) + 1e-3F < Math.abs(t.speed)) {
            //Bout de voie ou butoir
            t.speed = 0;
        }
        consume(t, now);
        if (t.auto) {
            arrived(t, now);
        }
    }

    /** Freins et propulseurs de voie (vehicules sans locomotive). */
    private void applyTrackEffects(Train t, boolean powered) {
        if (powered) {
            return;
        }
        for (long a : t.occupied) {
            Piece p = net.piece(a);
            if (p == null) {
                continue;
            }
            if (p.kind == Piece.Kind.BRAKE) {
                float s = Math.abs(t.speed);
                if (s > 0.4F) {
                    t.speed = Math.signum(t.speed) * Math.max(0.4F, s - 0.08F);
                }
            } else if (p.kind == Piece.Kind.BOOSTER) {
                float dir = t.speed == 0 ? 1 : Math.signum(t.speed);
                if (Math.abs(t.speed) < 6.4F) {
                    t.speed = dir * Math.min(6.4F, Math.abs(t.speed) + 0.06F);
                }
            }
        }
    }

    /** Place libre devant (ou derriere) un train en conduite manuelle : bout de voie et autres trains (tampon contre tampon). */
    private float manualRoom(Train t, boolean forward, float budget, Train.Router router) {
        //On simule l'avancee sur une copie de la trace
        Train probe = new Train(t.id);
        probe.cars.addAll(t.cars);
        Set<Long> guide = new HashSet<>();
        t.guide(guide);
        probe.rebuild(net, t.head(), t.headOffset(), t.length(), guide);
        float room = Math.abs(probe.move(net, forward ? budget : -budget, router));
        //Autres trains : distance au plus proche de leurs bouts
        double[] end = new double[3];
        probe.locate(net, forward ? 0 : probe.length(), end, null);
        double[] mine = new double[3];
        t.locate(net, forward ? 0 : t.length(), mine, null);
        float best = room;
        for (Train u : trains.values()) {
            if (u == t) {
                continue;
            }
            for (float d : new float[]{0, u.length()}) {
                double[] ue = new double[3];
                u.locate(net, d, ue, null);
                double ox = ue[0] - mine[0], oy = ue[1] - mine[1], oz = ue[2] - mine[2];
                double dist = Math.sqrt(ox * ox + oy * oy + oz * oz) * 16;
                if (dist > budget + 1) {
                    continue;
                }
                //Le bout de l'autre est-il sur notre chemin ? (au bout de notre avancee, on le traverserait)
                double px = ue[0] - end[0], py = ue[1] - end[1], pz = ue[2] - end[2];
                double past = Math.sqrt(px * px + py * py + pz * pz) * 16;
                if (dist + past <= room + 0.5 && onTrail(probe, u)) {
                    best = (float) Math.min(best, Math.max(0, dist - 0.3));
                }
            }
        }
        return best;
    }

    private boolean onTrail(Train probe, Train u) {
        for (Step s : probe.trail()) {
            for (Step o : u.trail()) {
                if (o.anchor() == s.anchor()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Automatisme
    // ------------------------------------------------------------------

    /** Horaire, recherche de gare et de chemin, arret a quai ; renvoie faux si le train doit rester arrete. */
    private boolean autopilot(Train t, long now) {
        Schedule sch = t.schedule;
        Schedule.Stop stop = t.currentStop();
        //Un train logistique a son depot attend le repartiteur, meme sans horaire
        boolean depotHold = t.logistic && !t.inTemp() && atDepot(t);
        if (stop == null && !depotHold) {
            t.status = Train.Status.NO_SCHEDULE;
            return false;
        }
        if (!sch.isEmpty()) {
            t.stopIndex = Math.floorMod(t.stopIndex, sch.stops.size());
        }
        //0.20.5 : sans chemin ni place depuis 10 s : interruptions, puis abandon d'une livraison
        if (t.status == Train.Status.NO_PATH || t.status == Train.Status.DEST_FULL) {
            t.stuckTicks++;
            if (t.stuckTicks == 200 && checkInterrupts(t, now)) {
                t.retry = 0;
            }
            dispatcher.stuck(t, now);
            //Interruption ou envoi impossible depuis une minute : le train reprend son horaire
            if (t.stuckTicks >= RailDispatcher.STUCK && (t.tempKind == Train.TempKind.INTERRUPT || t.tempKind == Train.TempKind.ORDER)) {
                cancelTemp(t);
                t.stuckTicks = 0;
            }
        } else {
            t.stuckTicks = 0;
        }
        if (t.status == Train.Status.AT_STATION) {
            boolean touched = false;
            for (Car c : t.cars) {
                touched |= c.touched;
                c.touched = false;
            }
            if (touched) {
                t.lastActivity = now;
            }
            uncouple(t);
            //Train logistique a son depot, sans tache : il attend le repartiteur
            if (depotHold) {
                return false;
            }
            boolean ready = t.inTemp() && t.tempKind == Train.TempKind.DELIVERY ? dispatcher.ready(t, now)
                  : Schedule.ready(stop.conditions, context(t, now));
            if (!ready) {
                return false;
            }
            depart(t, now);
            if (t.currentStop() == null) {
                t.status = Train.Status.NO_SCHEDULE;
                return false;
            }
        }
        if (t.planVersion != net.version() || t.destination == 0) {
            if (t.retry > 0) {
                t.retry--;
                return false;
            }
            if (!plan(t)) {
                return false;
            }
        }
        if (t.status != Train.Status.WAIT_SIGNAL && t.status != Train.Status.WAIT_TRAIN && t.status != Train.Status.NO_FUEL) {
            t.status = Train.Status.RUNNING;
        }
        return true;
    }

    /** Depart d'un arret : arret suivant (temporaire ou de l'horaire), puis interruptions. */
    private void depart(Train t, long now) {
        if (t.inTemp()) {
            if (t.tempKind == Train.TempKind.DELIVERY) {
                dispatcher.departed(t);
            }
            t.tempIndex++;
            if (!t.inTemp()) {
                t.clearTemp();
            }
        } else if (!t.schedule.isEmpty()) {
            t.stopIndex = (t.stopIndex + 1) % t.schedule.stops.size();
        }
        t.uncoupled = false;
        checkInterrupts(t, now);
        t.destination = 0;
        t.plan.clear();
        t.planVersion = -1;
        t.status = Train.Status.RUNNING;
    }

    /** Interruptions activees du train (ou de son groupe). */
    public List<String> interruptsOf(Train t) {
        RailLibrary.Group g = library.group(t.group);
        return g != null ? g.interrupts : t.interrupts;
    }

    /**
     * 0.20.5 : la premiere interruption activee dont les conditions sont vraies remplace les arrets a venir par les siens (jokers remplaces
     * par les ressources du train) ; jamais pendant une livraison, ni pendant une autre interruption sauf si elle le permet.
     */
    boolean checkInterrupts(Train t, long now) {
        if (t.inTemp() && t.tempKind == Train.TempKind.DELIVERY) {
            return false;
        }
        Schedule.Context ctx = null;
        for (String name : interruptsOf(t)) {
            RailLibrary.Interrupt i = library.interrupt(name);
            if (i == null || i.when.isEmpty() || i.stops.isEmpty() || t.inTemp() && t.tempKind == Train.TempKind.INTERRUPT && (!i.inside
                  || i.name.equalsIgnoreCase(t.tempName))) {
                continue;
            }
            if (ctx == null) {
                ctx = context(t, now);
            }
            if (!Schedule.ready(i.when, ctx)) {
                continue;
            }
            ResourceLocation item = mainItem(t), fluid = mainContent(t, false), gas = mainContent(t, true);
            List<Schedule.Stop> stops = new ArrayList<>();
            boolean ok = true;
            for (Schedule.Stop st : i.stops) {
                Schedule.Stop c = st.copy();
                c.station = Schedule.substitute(c.station, item, fluid, gas);
                ok &= !Schedule.hasJoker(c.station);
                stops.add(c);
            }
            if (!ok) {
                continue;
            }
            setTemp(t, stops, Train.TempKind.INTERRUPT, i.name);
            return true;
        }
        return false;
    }

    /** Objet le plus abondant du train (joker {objet}). */
    @Nullable
    private static ResourceLocation mainItem(Train t) {
        Map<ResourceLocation, Integer> count = new HashMap<>();
        for (Car c : t.cars) {
            if (!cargo(c)) {
                continue;
            }
            for (int i = 0; i < c.items.getSlots(); i++) {
                ItemStack s = c.items.getStackInSlot(i);
                if (!s.isEmpty()) {
                    count.merge(Cargo.itemId(s), s.getCount(), Integer::sum);
                }
            }
        }
        return count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /** Fluide (ou gaz) le plus abondant des citernes (jokers {fluide}, {gaz}). */
    @Nullable
    private static ResourceLocation mainContent(Train t, boolean gas) {
        Map<String, Integer> count = new HashMap<>();
        for (Car c : t.cars) {
            if (!gas && c.type == CarType.TANK && c.fluidAmount > 0) {
                count.merge(c.fluid, c.fluidAmount, Integer::sum);
            } else if (gas && c.type == CarType.GAS_TANK && c.gasAmount > 0) {
                count.merge(c.gas, c.gasAmount, Integer::sum);
            }
        }
        return count.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> ResourceLocation.tryParse(e.getKey())).orElse(null);
    }

    /** Arrets temporaires (interruption, livraison, envoi) : le train y va tout de suite, puis reprend son horaire. */
    public void setTemp(Train t, List<Schedule.Stop> stops, Train.TempKind kind, String name) {
        t.temp.clear();
        t.temp.addAll(stops);
        t.tempIndex = 0;
        t.tempKind = stops.isEmpty() ? Train.TempKind.NONE : kind;
        t.tempName = name;
        t.destination = 0;
        t.plan.clear();
        t.planVersion = -1;
        t.retry = 0;
        release(t, false);
        if (t.status == Train.Status.AT_STATION || t.status == Train.Status.NO_SCHEDULE || t.status == Train.Status.NO_PATH
              || t.status == Train.Status.DEST_FULL) {
            t.status = Train.Status.RUNNING;
        }
        setDirty();
    }

    /** Envoie un train a une gare (carte, PioScript) : un arret temporaire sans condition, puis il reprend son horaire. */
    public boolean sendTo(Train t, String station) {
        if (!t.auto || t.inTemp() && t.tempKind == Train.TempKind.DELIVERY || net.stationsNamed(station).isEmpty()) {
            return false;
        }
        Schedule.Stop st = new Schedule.Stop();
        st.station = station;
        Schedule.Condition c = new Schedule.Condition();
        c.type = Schedule.Type.INACTIVITY;
        c.value = 5;
        st.conditions.add(c);
        setTemp(t, List.of(st), Train.TempKind.ORDER, "");
        return true;
    }

    /** Le train est-il arrete a une gare depot ? */
    boolean atDepot(Train t) {
        if (t.status != Train.Status.AT_STATION) {
            return false;
        }
        RailNetwork.Station s = net.station(t.destination);
        return s != null && s.logistics.role == StationLogistics.Role.DEPOT;
    }

    private boolean plan(Train t) {
        Schedule.Stop stop = t.currentStop();
        if (stop == null) {
            return false;
        }
        List<RailNetwork.Station> named;
        if (stop.target != 0) {
            RailNetwork.Station st = net.station(stop.target);
            named = st == null ? List.of() : List.of(st);
        } else {
            named = net.stationsNamed(stop.station);
        }
        if (named.isEmpty()) {
            t.status = Train.Status.NO_PATH;
            t.retry = 40;
            return false;
        }
        List<Pathfinder.Goal> goals = new ArrayList<>();
        for (RailNetwork.Station s : named) {
            if (s.limit == 0) {
                continue;
            }
            if (s.limit > 0 && s.pos.asLong() != t.destination && assigned(s, t) >= s.limit) {
                continue;
            }
            goals.add(new Pathfinder.Goal(s.port, s.heading, s.pos.asLong()));
        }
        if (goals.isEmpty()) {
            t.status = Train.Status.DEST_FULL;
            t.retry = 20;
            return false;
        }
        List<Step> starts = new ArrayList<>(List.of(t.head()));
        List<Boolean> rev = new ArrayList<>(List.of(false));
        if (t.orientEnds()) {
            setDirty();                                                   //trains d'avant 0.20.31 : locomotive de queue retournee
        }
        if (t.bidirectional() && Math.abs(t.speed) < 0.01F) {
            starts.add(t.tail().reverse());
            rev.add(true);
        }
        boolean mag = maglev(t);
        //Maglev : seulement la voie MKIV
        Pathfinder.Result r = Pathfinder.find(net, starts, rev, goals, p -> mag && p.quality != TrackQuality.ULTIMATE ? Double.POSITIVE_INFINITY
              : pieceTakenByOther(p.key(), t) ? 300 : 0);
        if (r == null) {
            t.status = Train.Status.NO_PATH;
            t.retry = 40;
            return false;
        }
        release(t, false);
        if (r.reversed()) {
            t.flip(net);
        }
        t.plan.clear();
        t.plan.addAll(r.steps());
        t.planIndex = 0;
        t.planVersion = net.version();
        t.destination = r.goal().id();
        t.blockedTicks = 0;
        return true;
    }

    private int assigned(RailNetwork.Station s, Train self) {
        int n = 0;
        for (Train u : trains.values()) {
            if (u != self && u.auto && u.destination == s.pos.asLong()) {
                n++;
            }
        }
        return n;
    }

    private void arrived(Train t, long now) {
        if (t.planIndex < t.plan.size() || t.destination == 0) {
            return;
        }
        if (Math.abs(net.length(t.head()) - t.headOffset()) < 0.05F && Math.abs(t.speed) < 0.01F) {
            t.status = Train.Status.AT_STATION;
            t.arrivedAt = now;
            t.lastActivity = now;
            t.speed = 0;
            t.stuckTicks = 0;
            for (Car c : t.cars) {
                c.touched = false;
            }
            if (t.inTemp() && t.tempKind == Train.TempKind.DELIVERY) {
                dispatcher.arrived(t, now);
            }
        }
    }

    /**
     * Regarde devant le train sur son chemin : reserve les cantons des signaux franchis, pose les aiguillages, s'arrete avant un autre train ou
     * un canton pris. Renvoie {distance d'autorite (px), vitesse permise par les limites a venir}.
     */
    private float[] lookAhead(Train t, float vmax) {
        float budget = vmax * vmax / (2 * BRAKE) + 40;
        Step cur = t.head();
        float dist = net.length(cur) - t.headOffset();
        float limitAhead = Float.MAX_VALUE;
        int idx = t.planIndex;
        while (true) {
            if (dist > budget) {
                return new float[]{dist, limitAhead};
            }
            if (idx >= t.plan.size()) {
                //Fin du chemin : la gare (arret pile au point)
                return new float[]{Math.max(0, dist), limitAhead};
            }
            PortKey port = net.exitPort(cur);
            TrackPath.End end = net.exit(cur);
            if (port == null || end == null) {
                return new float[]{Math.max(0, dist - MARGIN), limitAhead};
            }
            Step nx = t.plan.get(idx);
            Piece np = net.piece(nx.anchor());
            if (np == null) {
                t.planVersion = -1;
                return new float[]{Math.max(0, dist - MARGIN), limitAhead};
            }
            RailNetwork.Signal sig = net.signalFor(port, end.face());
            if (sig != null) {
                if (!reserveFrom(t, idx, sig.type)) {
                    t.status = Train.Status.WAIT_SIGNAL;
                    return new float[]{Math.max(0, dist - MARGIN), limitAhead};
                }
            }
            if (pieceTakenByOther(np.key(), t)) {
                t.status = Train.Status.WAIT_TRAIN;
                return new float[]{Math.max(0, dist - MARGIN), limitAhead};
            }
            if (np.shape.isSwitch() && np.route != nx.path()) {
                np.route = nx.path();
                routeDirty.add(np.key());
            }
            float lim = np.limit(nx.path());
            limitAhead = Math.min(limitAhead, (float) Math.sqrt(lim * lim + 2 * BRAKE * Math.max(0, dist)));
            dist += np.path(nx.path()).length;
            cur = nx;
            idx++;
        }
    }

    /** Reserve le canton qui suit un signal (et, pour un signal d'entree, tous ceux jusqu'apres le prochain signal de canton). */
    private boolean reserveFrom(Train t, int idx, RailNetwork.SignalType type) {
        List<Integer> need = new ArrayList<>();
        int i = idx;
        boolean chain = type == RailNetwork.SignalType.CHAIN;
        int section = -1;
        while (i < t.plan.size()) {
            Piece p = net.piece(t.plan.get(i).anchor());
            if (p == null) {
                return false;
            }
            if (p.section() != section) {
                section = p.section();
                if (!need.contains(section)) {
                    need.add(section);
                }
            }
            if (!chain) {
                break;
            }
            //Signal suivant sur le chemin
            Step s = t.plan.get(i);
            PortKey port = net.exitPort(s);
            TrackPath.End end = net.exit(s);
            if (port != null && end != null) {
                RailNetwork.Signal next = net.signalFor(port, end.face());
                if (next != null) {
                    if (next.type == RailNetwork.SignalType.BLOCK) {
                        //Le canton apres ce signal de canton compte aussi
                        if (i + 1 < t.plan.size()) {
                            Piece q = net.piece(t.plan.get(i + 1).anchor());
                            if (q != null && !need.contains(q.section())) {
                                need.add(q.section());
                            }
                        }
                        break;
                    }
                }
            }
            i++;
        }
        for (int s : need) {
            if (!sectionFreeFor(s, t)) {
                return false;
            }
        }
        for (int s : need) {
            reserve(t, s);
        }
        return true;
    }

    private Schedule.Context context(Train t, long now) {
        return new Schedule.Context() {
            @Override
            public int secondsAtStop() {
                return (int) ((now - t.arrivedAt) / 20);
            }

            @Override
            public int secondsIdle() {
                return (int) ((now - t.lastActivity) / 20);
            }

            @Override
            public boolean full() {
                for (Car c : t.cars) {
                    if (RailWorld.cargo(c) && !c.isFull()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean empty() {
                for (Car c : t.cars) {
                    if (RailWorld.cargo(c) && !c.isEmpty()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public int count(Item item) {
                int n = 0;
                for (Car c : t.cars) {
                    for (int i = 0; i < c.items.getSlots(); i++) {
                        ItemStack s = c.items.getStackInSlot(i);
                        if (s.is(item)) {
                            n += s.getCount();
                        }
                    }
                }
                return n;
            }

            @Override
            public boolean passenger() {
                for (Car c : t.cars) {
                    RailCarEntity e = views.get(c.id);
                    if (e != null && !e.isRemoved() && e.getPassengers().stream().anyMatch(p -> p instanceof Player)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public int fuel() {
                return fuelPercent(t);
            }

            @Override
            public long fluid(@Nullable ResourceLocation id, boolean gas) {
                Cargo c = id == null ? new Cargo(gas ? Cargo.Kind.GAS : Cargo.Kind.FLUID, null) : gas ? Cargo.gas(id) : Cargo.fluid(id);
                return RailDispatcher.carried(t, c);
            }

            @Override
            public boolean cargo() {
                return !empty();
            }

            @Override
            public long stock(int kind, @Nullable ResourceLocation id, boolean network) {
                RailNetwork.Station st = t.status == Train.Status.AT_STATION ? net.station(t.destination) : null;
                if (st == null) {
                    return 0;
                }
                Cargo c = new Cargo(kind == Schedule.FLUID ? Cargo.Kind.FLUID : kind == Schedule.GAS ? Cargo.Kind.GAS : Cargo.Kind.ITEM, id);
                if (network) {
                    return Math.max(0, stocks.network(st, c));
                }
                long n = 0;
                for (Map.Entry<Cargo, Long> e : stocks.stock(st).entrySet()) {
                    if (c.covers(e.getKey())) {
                        n += e.getValue();
                    }
                }
                return n;
            }

            @Override
            public int signal(String name) {
                return signals.getOrDefault(name.trim().toLowerCase(Locale.ROOT), 0);
            }

            @Override
            public int redstone() {
                RailNetwork.Station st = t.status == Train.Status.AT_STATION ? net.station(t.destination) : null;
                return st == null ? 0 : stocks.redstone(st);
            }

            @Override
            public boolean destFree() {
                return nextStopFree(t);
            }
        };
    }

    /** L'arret suivant (temporaire ou de l'horaire) a-t-il une gare libre ? */
    private boolean nextStopFree(Train t) {
        Schedule.Stop next;
        if (t.inTemp() && t.tempIndex + 1 < t.temp.size()) {
            next = t.temp.get(t.tempIndex + 1);
        } else if (!t.schedule.isEmpty()) {
            next = t.schedule.stops.get(t.inTemp() ? Math.floorMod(t.stopIndex, t.schedule.stops.size())
                  : (t.stopIndex + 1) % t.schedule.stops.size());
        } else {
            return true;
        }
        List<RailNetwork.Station> named = next.target != 0 && net.station(next.target) != null ? List.of(net.station(next.target))
              : net.stationsNamed(next.station);
        for (RailNetwork.Station st : named) {
            if (st.limit < 0 || assigned(st, t) < st.limit) {
                return true;
            }
        }
        return false;
    }

    /** 0.20.5 : reserve de la locomotive la moins pourvue (%) ; 100 sans locomotive a combustible ou sous catenaire. */
    public int fuelPercent(Train t) {
        float min = 1;
        for (Car loco : t.cars) {
            if (!loco.type.locomotive || loco.type == CarType.DRAISINE) {
                continue;
            }
            float f = switch (loco.type) {
                case STEAM -> {
                    float items = 0;
                    int slots = 0;
                    for (Car c : t.cars) {
                        if (c == loco || c.type == CarType.TENDER) {
                            for (int i = 0; i < c.items.getSlots(); i++) {
                                ItemStack st = c.items.getStackInSlot(i);
                                items += st.isEmpty() ? 0 : st.getCount() / (float) st.getMaxStackSize();
                                slots++;
                            }
                        }
                    }
                    yield slots == 0 ? 1 : items / slots;
                }
                case FIRELESS, GAS_LOCO -> loco.gasAmount / (float) Math.max(1, loco.gasCapacity());
                case DIESEL -> loco.fluidAmount / (float) Math.max(1, loco.fluidCapacity());
                case NUCLEAR -> {
                    ItemStack st = loco.items.getSlots() > 0 ? loco.items.getStackInSlot(0) : ItemStack.EMPTY;
                    yield st.isEmpty() ? (loco.heat > 0 ? 0.05F : 0) : st.getCount() / (float) st.getMaxStackSize();
                }
                case ELECTRIC, MAGLEV -> loco.electrified ? 1 : loco.energy / (float) Math.max(1, loco.energyCapacity());
                default -> 1;
            };
            min = Math.min(min, f);
        }
        return Math.round(min * 100);
    }

    /** 0.20.5 : travail d'un poste de quai ; {@code unload} : vider le vehicule, sinon le remplir. */
    public record PostPlan(boolean unload, @Nullable Cargo filter, long limit) {

        public static final PostPlan IDLE = new PostPlan(false, null, 0);

        public boolean idle() {
            return limit <= 0;
        }

        public boolean allows(Cargo c) {
            return filter == null || filter.covers(c);
        }
    }

    /**
     * Travail d'un poste pour le vehicule devant lui : null quand la gare n'est pas logistique (le reglage du joueur s'applique), IDLE quand
     * le poste ne doit pas toucher ce train (train logistique sans livraison ici).
     */
    @Nullable
    public PostPlan postPlan(BlockPos post, Car car) {
        //Ravitaillement (locomotives, tenders) : jamais pilote par la logistique
        if (car.type.locomotive || car.type == CarType.TENDER) {
            return null;
        }
        long station = stocks.stationOf(post);
        if (station == 0) {
            return null;
        }
        Train t = trainOf(car.id);
        if (t == null || t.status != Train.Status.AT_STATION || t.destination != station) {
            return null;
        }
        return dispatcher.plan(station, t);
    }

    /** Wagon de marchandises (conditions plein / vide de l'horaire). */
    static boolean cargo(Car c) {
        return !c.type.locomotive && c.type != CarType.TENDER && c.type != CarType.PASSENGER
              && (c.items.getSlots() > 0 || c.bulkStore());
    }

    // ------------------------------------------------------------------
    // Routage
    // ------------------------------------------------------------------

    private Train.Router defaultRouter() {
        return (train, from, options, ahead) -> {
            Piece p = net.piece(options.get(0).anchor());
            if (p != null && p.shape.isSwitch() && options.size() > 1) {
                for (Step o : options) {
                    if (o.anchor() == p.key() && o.path() == p.route) {
                        return o;
                    }
                }
            }
            return options.get(0);
        };
    }

    private Train.Router routerFor(Train t) {
        Train.Router fallback = defaultRouter();
        return (train, from, options, ahead) -> {
            Step chosen = null;
            if (ahead && train.auto && train.planIndex < train.plan.size()) {
                Step want = train.plan.get(train.planIndex);
                if (options.contains(want)) {
                    chosen = want;
                    train.planIndex++;
                } else {
                    train.planVersion = -1;
                }
            }
            if (chosen == null && !train.auto && options.size() > 1 && train.driverIdle < 40) {
                chosen = steer(from, options, train.steer, ahead);
            }
            if (chosen == null) {
                chosen = fallback.choose(train, from, options, ahead);
            }
            if (chosen != null) {
                Piece p = net.piece(chosen.anchor());
                if (p != null && p.shape.isSwitch() && p.route != chosen.path()) {
                    p.route = chosen.path();
                    routeDirty.add(p.key());
                }
            }
            return chosen;
        };
    }

    /** Choix d'aiguille du conducteur : branche la plus a gauche (-1), la plus a droite (1), ou la plus droite (0). */
    @Nullable
    private Step steer(Step from, List<Step> options, int steer, boolean ahead) {
        TrackPath.End e = net.exit(ahead ? from : from.reverse());
        if (e == null) {
            return null;
        }
        Direction heading = e.face();
        Step best = null;
        double bestScore = -1e9;
        float[] tan = new float[3];
        for (Step o : options) {
            Piece p = net.piece(o.anchor());
            if (p == null) {
                continue;
            }
            TrackPath path = p.path(o.path());
            Step dirStep = ahead ? o : o.reverse();
            path.tangent(dirStep.forward() ? path.length : 0, tan);
            if (!dirStep.forward()) {
                tan[0] = -tan[0];
                tan[2] = -tan[2];
            }
            //Produit vectoriel (cap d'entree x cap de sortie) : > 0 = a droite (x vers l'est, z vers le sud)
            double cross = heading.getStepX() * tan[2] - heading.getStepZ() * tan[0];
            double straight = heading.getStepX() * tan[0] + heading.getStepZ() * tan[2];
            double score = steer == 0 ? straight : steer * cross;
            if (score > bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Energie
    // ------------------------------------------------------------------

    /** 0.20.2 : electrification (catenaire, troisieme rail, voie MKIV, sous-stations). */
    public final Electrification power = new Electrification();

    /** Part de sa puissance que la locomotive peut donner ce tick (0..1) : 1 avec du combustible ; electrique : fil et batterie. */
    private float power(Train t, Car loco, long now) {
        if (!loco.type.electric()) {
            return hasFuel(t, loco) ? 1 : 0;
        }
        if (!hasFuel(t, loco)) {
            return 0;
        }
        int grid = gridOf(t, loco);
        long can = loco.energy + (grid >= 0 ? power.drawable(grid, now) : 0);
        return Math.min(1, can / (float) Math.max(1, loco.type.fuelUse(loco.tier)));
    }

    /** La locomotive peut-elle tirer ce tick ? (calcule aussi sa mise sous tension) */
    private boolean hasFuel(Train t, Car loco) {
        switch (loco.type) {
            case STEAM -> {
                if (loco.heat <= 0 && !hasBurnable(t, loco)) {
                    return false;
                }
                return loco.water > 0 || tenderWater(t) > 0;
            }
            case FIRELESS -> {
                return loco.heat > 0 || loco.gasAmount > 0;
            }
            case DIESEL -> {
                return loco.heat > 0 || loco.fluidAmount > 0 && RailFuels.liquid(loco.fluid) > 0;
            }
            case GAS_LOCO -> {
                return loco.heat > 0 || loco.gasAmount > 0 && RailFuels.gas(loco.gas) > 0;
            }
            case NUCLEAR -> {
                return loco.heat > 0 || !loco.items.getStackInSlot(0).isEmpty();
            }
            case ELECTRIC, MAGLEV -> {
                int grid = gridOf(t, loco);
                loco.electrified = grid >= 0 && power.available(grid) > 0;
                if (loco.type == CarType.MAGLEV && !onUltimate(t, loco)) {
                    //Le maglev ne tient que sur la voie MKIV
                    return false;
                }
                return loco.electrified || loco.energy > 0;
            }
            default -> {
                return true;
            }
        }
    }

    /** Grille electrique sous une locomotive (-1 : hors tension). */
    private int gridOf(Train t, Car loco) {
        int i = t.cars.indexOf(loco);
        Train.Pose pose = t.pose(net, Math.max(0, i));
        Vec3 c = pose.center();
        Piece p = net.at(BlockPos.containing(c.x, c.y + 0.2, c.z));
        return power.gridAt(net, c.x, c.y, c.z, p, loco.type == CarType.MAGLEV);
    }

    private boolean onUltimate(Train t, Car loco) {
        int i = t.cars.indexOf(loco);
        Vec3 c = t.pose(net, Math.max(0, i)).center();
        Piece p = net.at(BlockPos.containing(c.x, c.y + 0.2, c.z));
        return p != null && p.quality == TrackQuality.ULTIMATE;
    }

    /** Le train comprend-il un maglev (il ne roule alors que sur la voie MKIV) ? */
    static boolean maglev(Train t) {
        for (Car c : t.cars) {
            if (c.type == CarType.MAGLEV) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBurnable(Train t, Car loco) {
        for (int i = 0; i < loco.items.getSlots(); i++) {
            if (!loco.items.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        for (Car c : t.cars) {
            if (c.type == CarType.TENDER) {
                for (int i = 0; i < c.items.getSlots(); i++) {
                    if (!c.items.getStackInSlot(i).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int tenderWater(Train t) {
        int w = 0;
        for (Car c : t.cars) {
            if (c.type == CarType.TENDER) {
                w += c.water;
            }
        }
        return w;
    }

    private final Map<UUID, Float> waterDebt = new HashMap<>();

    /** Energie depensee par l'effort de traction de ce tick, prise selon la traction de chaque locomotive. */
    private void consume(Train t, long now) {
        for (Car loco : t.cars) {
            if (!loco.type.locomotive || loco.type == CarType.DRAISINE) {
                continue;
            }
            long need = (long) (loco.type.fuelUse(loco.tier) * t.effort);
            if (loco.type.electric()) {
                electric(t, loco, need, now);
                continue;
            }
            if (need <= 0) {
                continue;
            }
            if (loco.heat < need) {
                refuel(t, loco, need);
            }
            loco.heat = Math.max(0, loco.heat - need);
            if (loco.type == CarType.STEAM) {
                steamWater(t, loco);
            }
        }
    }

    /** Recharge la reserve d'energie (heat) d'une locomotive a combustible. */
    private void refuel(Train t, Car loco, long need) {
        switch (loco.type) {
            case STEAM -> burn(t, loco);
            case FIRELESS -> {
                int mb = (int) Math.min(loco.gasAmount, Math.max(1, (need * 8 - loco.heat) / RailFuels.STEAM_FE + 1));
                loco.drainGas(mb, false);
                loco.heat += (long) mb * RailFuels.STEAM_FE;
            }
            case DIESEL -> {
                int per = RailFuels.liquid(loco.fluid);
                if (per > 0) {
                    int mb = (int) Math.min(loco.fluidAmount, Math.max(1, (need * 8 - loco.heat) / per + 1));
                    loco.drainFluid(mb, false);
                    loco.heat += (long) mb * per;
                }
            }
            case GAS_LOCO -> {
                int per = RailFuels.gas(loco.gas);
                if (per > 0) {
                    int mb = (int) Math.min(loco.gasAmount, Math.max(1, (need * 8 - loco.heat) / per + 1));
                    loco.drainGas(mb, false);
                    loco.heat += (long) mb * per;
                }
            }
            case NUCLEAR -> {
                ItemStack fuel = loco.items.getStackInSlot(0);
                ItemStack spentSlot = loco.items.getStackInSlot(1);
                long fe = RailFuels.nuclear(fuel);
                ItemStack spent = spentOf(fuel);
                //L'assemblage use doit trouver sa place (meme sorte, case non pleine)
                boolean room = spent.isEmpty() || spentSlot.isEmpty()
                      || ItemStack.isSameItemSameTags(spentSlot, spent) && spentSlot.getCount() < spentSlot.getMaxStackSize();
                if (fe > 0 && room) {
                    loco.items.extractItem(0, 1, false);
                    if (!spent.isEmpty()) {
                        if (spentSlot.isEmpty()) {
                            loco.items.setStackInSlot(1, spent);
                        } else {
                            spentSlot.grow(1);
                            loco.items.setStackInSlot(1, spentSlot);
                        }
                    }
                    loco.heat += fe;
                }
            }
            default -> {
            }
        }
    }

    /** Assemblage use rendu par un assemblage neuf. */
    private static ItemStack spentOf(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String spent = RailFuels.NUCLEAR_SPENT.get(BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString());
        if (spent == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(spent));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Traction electrique : la catenaire (ou la voie MKIV) d'abord, sinon la batterie ; sous tension, la batterie se recharge ; au freinage,
     * une part de l'energie revient a la batterie.
     */
    private void electric(Train t, Car loco, long need, long now) {
        int grid = gridOf(t, loco);
        long cap = loco.energyCapacity();
        if (grid >= 0) {
            long recharge = Math.min(cap - loco.energy, loco.type.fuelUse(loco.tier) / 2L);
            long got = power.draw(grid, need + Math.max(0, recharge), now);
            long used = Math.min(need, got);
            loco.energy = Math.min(cap, loco.energy + got - used);
            need -= used;
            loco.electrified = got > 0 || power.available(grid) > 0;
        } else {
            loco.electrified = false;
        }
        if (need > 0) {
            loco.energy = Math.max(0, loco.energy - need);
        }
        if (t.brakeLoss > 0 && t.accel > 0) {
            //Freinage par recuperation : 30 % de l'energie qu'il faudrait pour regagner la vitesse perdue (jamais un gain net)
            long regen = (long) (loco.type.fuelUse(loco.tier) * Math.min(1, t.brakeLoss / t.accel) * 0.3);
            loco.energy = Math.min(cap, loco.energy + regen);
        }
    }

    private void steamWater(Train t, Car loco) {
        float w = waterDebt.getOrDefault(loco.id, 0F) + loco.type.waterUse(loco.tier) * t.effort / 1000F;
        int whole = (int) w;
        waterDebt.put(loco.id, w - whole);
        for (int k = 0; k < whole; k++) {
            if (loco.water > 0) {
                loco.water--;
            } else {
                for (Car c : t.cars) {
                    if (c.type == CarType.TENDER && c.water > 0) {
                        c.water--;
                        break;
                    }
                }
            }
        }
        //La locomotive refait son plein d'eau depuis les tenders
        if (loco.water < loco.waterCapacity() / 2) {
            for (Car c : t.cars) {
                if (c.type == CarType.TENDER && c.water > 0) {
                    int move = Math.min(c.water, loco.waterCapacity() - loco.water);
                    c.water -= move;
                    loco.water += move;
                }
            }
        }
    }

    private void burn(Train t, Car loco) {
        List<Car> sources = new ArrayList<>();
        sources.add(loco);
        for (Car c : t.cars) {
            if (c.type == CarType.TENDER) {
                sources.add(c);
            }
        }
        for (Car c : sources) {
            for (int i = 0; i < c.items.getSlots(); i++) {
                ItemStack s = c.items.getStackInSlot(i);
                int burn = s.isEmpty() ? 0 : ForgeHooks.getBurnTime(s, RecipeType.SMELTING);
                if (burn > 0) {
                    ItemStack rest = s.getCraftingRemainingItem();
                    c.items.extractItem(i, 1, false);
                    if (!rest.isEmpty() && c.items.getStackInSlot(i).isEmpty()) {
                        c.items.setStackInSlot(i, rest);
                    }
                    loco.heat += burn * 1_024L;
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Conducteur
    // ------------------------------------------------------------------

    private void readDriver(Train t) {
        t.driverIdle = Math.min(1000, t.driverIdle + 1);
        for (Car c : t.cars) {
            if (!c.type.locomotive) {
                continue;
            }
            RailCarEntity e = views.get(c.id);
            if (e == null || e.isRemoved()) {
                continue;
            }
            for (Entity p : e.getPassengers()) {
                if (p instanceof Player player) {
                    if (t.auto) {
                        continue;
                    }
                    float fwd = player.zza * (c.flipped ? -1 : 1);
                    t.throttle = fwd;
                    t.driver = player.getUUID();
                    t.driverBoost = (float) Math.min(1.5, fr.pioneers.core.profile.PioneerApi.factor(player, "logistics.driven_speed"));
                    t.steer = player.xxa > 0.1F ? -1 : player.xxa < -0.1F ? 1 : 0;
                    if (c.flipped) {
                        t.steer = -t.steer;
                    }
                    t.driverIdle = 0;
                    if (c.type == CarType.DRAISINE && Math.abs(fwd) < 0.1F) {
                        t.throttle = 0;
                    }
                    return;
                }
            }
        }
        if (t.driverIdle > 4) {
            t.throttle = 0;
            t.steer = 0;
            t.driver = null;
            t.driverBoost = 1;
        }
    }

    /** 1.9 (MAJ RP) : XP de Logistique tous les 100 blocs conduits a la main (16 px par bloc). */
    private void drivenXp(Train t) {
        if (t.driver == null || level == null) {
            return;
        }
        t.drivenPx += Math.abs(t.speed);
        if (t.drivenPx >= 1600) {
            t.drivenPx -= 1600;
            if (level.getPlayerByUUID(t.driver) instanceof ServerPlayer sp) {
                fr.pioneers.core.profile.PioneerApi.gain(sp, "logistics.rail100", 1, null);
            }
        }
    }

    /** Un joueur pousse un vehicule (wagonnet, draisine...) : il roule s'il n'a pas de locomotive. */
    public void pushedBy(UUID trainId, @Nullable UUID carId, Player player) {
        Train t = trains.get(trainId);
        if (t == null || t.hasLocomotive() && t.cars.stream().anyMatch(c -> c.type != CarType.DRAISINE && c.type.locomotive) || t.auto) {
            return;
        }
        int index = 0;
        for (int i = 0; i < t.cars.size(); i++) {
            if (t.cars.get(i).id.equals(carId)) {
                index = i;
            }
        }
        Train.Pose pose = t.pose(net, index);
        Vec3 dir = pose.dir();
        Car car = t.cars.get(index);
        if (car.flipped) {
            dir = dir.scale(-1);
        }
        Vec3 push = pose.center().subtract(player.position());
        double along = push.x * dir.x + push.z * dir.z;
        if (Math.abs(along) < 0.05) {
            return;
        }
        float add = (float) Math.signum(along) * 0.12F;
        if (Math.abs(t.speed) < 2.4F || Math.signum(t.speed) != Math.signum(add)) {
            t.speed = Math.max(-2.4F, Math.min(2.4F, t.speed + add));
        }
    }

    // ------------------------------------------------------------------
    // Entites
    // ------------------------------------------------------------------

    private void syncViews(ServerLevel level, long now) {
        for (Train t : trains.values()) {
            for (int i = 0; i < t.cars.size(); i++) {
                Car c = t.cars.get(i);
                Train.Pose pose = t.pose(net, i);
                RailCarEntity e = views.get(c.id);
                if (!pose.valid()) {
                    //0.20.4 : trace incomplete (voie cassee) : la vue reste ou elle est, jamais envoyee a l'origine du monde
                    if (e != null && !e.isRemoved()) {
                        e.keepAlive(now);
                    }
                    continue;
                }
                BlockPos bp = BlockPos.containing(pose.center());
                boolean loaded = level.isLoaded(bp) && level.areEntitiesLoaded(ChunkPos.asLong(bp)) && level.isPositionEntityTicking(bp);
                if (!loaded) {
                    if (e != null) {
                        e.discard();
                        views.remove(c.id);
                    }
                    continue;
                }
                if (e == null || e.isRemoved()) {
                    e = PiRail.CAR_ENTITY.get().create(level);
                    if (e == null) {
                        continue;
                    }
                    e.sync(t, c, pose, now, loadId(c));
                    e.setOldPosAndRot();
                    if (level.addFreshEntity(e)) {
                        views.put(c.id, e);
                    }
                } else {
                    e.sync(t, c, pose, now, loadId(c));
                }
                if (Math.abs(t.speed) > 0.8F) {
                    hit(level, t, e, pose);
                }
            }
        }
    }

    private static int loadId(Car c) {
        if (c.type != CarType.HOPPER && c.type != CarType.MINE_WAGON && c.type != CarType.FLAT) {
            return 0;
        }
        for (int i = 0; i < c.items.getSlots(); i++) {
            ItemStack s = c.items.getStackInSlot(i);
            if (!s.isEmpty()) {
                return BuiltInRegistries.ITEM.getId(s.getItem());
            }
        }
        return 0;
    }

    /** Heurt : les entites sur la voie devant un vehicule en marche prennent des degats et sont projetees. */
    private void hit(ServerLevel level, Train t, RailCarEntity e, Train.Pose pose) {
        Vec3 c = pose.center();
        AABB box = new AABB(c.x - 0.5, c.y, c.z - 0.5, c.x + 0.5, c.y + 0.9, c.z + 0.5);
        float bps = Math.abs(t.speed) / 0.8F;
        Vec3 dir = pose.dir().scale(Math.signum(t.speed) * (t.cars.get(0).flipped ? -1 : 1));
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (victim.getVehicle() instanceof RailCarEntity || victim.isSpectator() || !victim.isAlive()) {
                continue;
            }
            DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(trainDamage()), e);
            if (victim.hurt(src, 2 + bps * 0.9F)) {
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                Vec3 rel = victim.position().subtract(c);
                if (side.dot(rel) < 0) {
                    side = side.scale(-1);
                }
                Vec3 kick = dir.scale(0.25 + bps * 0.04).add(side.scale(0.6 + bps * 0.02)).add(0, 0.35 + bps * 0.01, 0);
                victim.setDeltaMovement(kick);
                victim.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------
    // Interactions
    // ------------------------------------------------------------------

    public InteractionResult interact(ServerPlayer player, RailCarEntity view, InteractionHand hand) {
        Train t = view.train == null ? null : trains.get(view.train);
        if (t == null) {
            return InteractionResult.PASS;
        }
        Car car = null;
        int index = 0;
        for (int i = 0; i < t.cars.size(); i++) {
            if (t.cars.get(i).id.equals(view.car)) {
                car = t.cars.get(i);
                index = i;
            }
        }
        if (car == null) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        //Remplir le tender ou la locomotive avec un seau d'eau
        if ((car.type == CarType.TENDER || car.type == CarType.STEAM) && Buckets.isWaterBucket(held)) {
            if (car.water + Buckets.VOLUME <= car.waterCapacity() && Buckets.drainHeld(player, hand, Fluids.WATER)) {
                car.water += Buckets.VOLUME;
            }
            return InteractionResult.CONSUME;
        }
        //0.20.2 : voiture voyageurs : on s'assoit (le train garde son mode)
        if (car.type == CarType.PASSENGER && !player.isShiftKeyDown()) {
            if (view.getPassengers().size() < car.type.seats()) {
                player.startRiding(view, false);
            }
            return InteractionResult.CONSUME;
        }
        //0.20.1 : clic = inventaire du vehicule (combustible et eau de la locomotive aussi) ; Maj + clic, ou rien a ranger = fiche du train
        if (!player.isShiftKeyDown() && (car.items.getSlots() > 0 || CarMenu.hasLine(car.type, car.tier)) && openInventory(player, t, index)) {
            return InteractionResult.CONSUME;
        }
        RailNetworking.openTrain(player, this, t, index);
        return InteractionResult.CONSUME;
    }

    /** Ouvre l'inventaire d'un vehicule (vue chargee pres du joueur). */
    public boolean openInventory(ServerPlayer player, Train t, int index) {
        if (index < 0 || index >= t.cars.size()) {
            return false;
        }
        Car car = t.cars.get(index);
        RailCarEntity view = view(car.id);
        if (car.items.getSlots() <= 0 && !CarMenu.hasLine(car.type, car.tier) || view == null || view.distanceToSqr(player) > 64) {
            return false;
        }
        NetworkHooks.openScreen(player, new CarMenu.Provider(t, car, view), buf -> {
            buf.writeVarInt(view.getId());
            buf.writeVarInt(car.items.getSlots());
            buf.writeByte(car.type.ordinal());
            buf.writeByte(car.tier);
            buf.writeUUID(t.id);
            buf.writeVarInt(index);
        });
        return true;
    }

    /** Monter a bord (bouton « Conduire » de la fiche). */
    public void board(ServerPlayer player, Train t, int index) {
        if (index < 0 || index >= t.cars.size()) {
            return;
        }
        RailCarEntity e = views.get(t.cars.get(index).id);
        if (e != null && !e.isRemoved() && e.distanceToSqr(player) < 64) {
            t.auto = false;
            release(t, true);
            t.plan.clear();
            player.startRiding(e, false);
        }
    }

    @Nullable
    public RailCarEntity view(UUID car) {
        RailCarEntity e = views.get(car);
        return e == null || e.isRemoved() ? null : e;
    }

    /** Vehicule arrete dont le centre est dans ce bloc (postes de quai). */
    @Nullable
    public Car carAt(BlockPos pos) {
        Piece p = net.at(pos);
        if (p == null) {
            return null;
        }
        List<Train> list = pieceOcc.get(p.key());
        if (list == null) {
            return null;
        }
        for (Train t : list) {
            if (Math.abs(t.speed) > 0.001F) {
                continue;
            }
            for (int i = 0; i < t.cars.size(); i++) {
                Vec3 c = t.pose(net, i).center();
                if (BlockPos.containing(c.x, c.y + 0.2, c.z).equals(pos) || BlockPos.containing(c.x, c.y - 0.3, c.z).equals(pos)) {
                    return t.cars.get(i);
                }
            }
        }
        return null;
    }

    public void setMode(Train t, boolean auto) {
        if (auto) {
            t.orientEnds();
        }
        if (auto && !t.canRunAuto()) {
            return;
        }
        t.auto = auto;
        release(t, true);
        t.plan.clear();
        t.planVersion = -1;
        t.destination = 0;
        t.status = auto ? Train.Status.RUNNING : Train.Status.IDLE;
        if (auto) {
            for (Car c : t.cars) {
                RailCarEntity e = views.get(c.id);
                if (e != null) {
                    e.ejectSafely();
                }
            }
        }
        setDirty();
    }

    public void setSchedule(Train t, Schedule s, int stop) {
        //0.20.5 : un train de groupe change l'horaire de tout le groupe (chacun garde son arret, s'il existe encore)
        RailLibrary.Group g = library.group(t.group);
        if (g != null) {
            g.schedule = s.copy();
            for (Train u : trains.values()) {
                if (u != t && g.name.equalsIgnoreCase(u.group)) {
                    u.schedule = s.copy();
                    u.stopIndex = s.isEmpty() ? 0 : Math.min(u.stopIndex, s.stops.size() - 1);
                    if (!u.inTemp()) {
                        u.plan.clear();
                        u.planVersion = -1;
                        u.destination = u.status == Train.Status.AT_STATION ? u.destination : 0;
                    }
                }
            }
        }
        t.schedule = s;
        t.stopIndex = Math.max(0, stop);
        t.plan.clear();
        t.planVersion = -1;
        t.destination = 0;
        release(t, false);
        if (t.status == Train.Status.AT_STATION) {
            t.status = Train.Status.RUNNING;
        }
        setDirty();
    }

    /** 0.20.5 : met le train dans un groupe (cree au besoin, avec l'horaire du train s'il est neuf) ; nom vide : hors groupe. */
    public void setGroup(Train t, String name) {
        String old = t.group;
        name = RailLibrary.clip(name);
        if (name.isEmpty()) {
            t.group = "";
        } else {
            boolean fresh = library.group(name) == null;
            RailLibrary.Group g = library.groupOrCreate(name);
            if (g == null) {
                return;
            }
            t.group = g.name;
            if (fresh) {
                g.schedule = t.schedule.copy();
                g.interrupts.clear();
                g.interrupts.addAll(t.interrupts);
            } else {
                t.schedule = g.schedule.copy();
                t.stopIndex = 0;
                t.interrupts.clear();
                t.interrupts.addAll(g.interrupts);
                if (!t.inTemp()) {
                    t.destination = t.status == Train.Status.AT_STATION ? t.destination : 0;
                    t.plan.clear();
                    t.planVersion = -1;
                }
            }
        }
        pruneGroup(old);
        setDirty();
    }

    /** Interruptions activees du train (et de son groupe). */
    public void setInterrupts(Train t, List<String> names) {
        t.interrupts.clear();
        for (String n : names) {
            if (library.interrupt(n) != null && t.interrupts.size() < 16 && !t.interrupts.contains(library.interrupt(n).name)) {
                t.interrupts.add(library.interrupt(n).name);
            }
        }
        RailLibrary.Group g = library.group(t.group);
        if (g != null) {
            g.interrupts.clear();
            g.interrupts.addAll(t.interrupts);
            for (Train u : trains.values()) {
                if (g.name.equalsIgnoreCase(u.group) && u != t) {
                    u.interrupts.clear();
                    u.interrupts.addAll(t.interrupts);
                }
            }
        }
        setDirty();
    }

    /**
     * Ecrit une interruption de la bibliotheque ; {@code old} : son ancien nom (renommage : les trains et groupes qui l'activaient suivent).
     */
    public boolean putInterrupt(String old, RailLibrary.Interrupt i) {
        i.name = RailLibrary.clip(i.name);
        if (i.name.isEmpty()) {
            return false;
        }
        boolean rename = !old.isBlank() && !RailLibrary.key(old).equals(RailLibrary.key(i.name));
        if (rename && library.interrupt(i.name) != null) {
            return false;
        }
        if (!library.putInterrupt(i)) {
            return false;
        }
        if (rename) {
            library.removeInterrupt(old);
            for (Train t : trains.values()) {
                t.interrupts.replaceAll(n -> n.equalsIgnoreCase(old) ? i.name : n);
            }
            for (RailLibrary.Group g : library.groups()) {
                g.interrupts.replaceAll(n -> n.equalsIgnoreCase(old) ? i.name : n);
            }
        }
        setDirty();
        return true;
    }

    public void removeInterrupt(String name) {
        library.removeInterrupt(name);
        for (Train t : trains.values()) {
            t.interrupts.removeIf(n -> n.equalsIgnoreCase(name));
        }
        for (RailLibrary.Group g : library.groups()) {
            g.interrupts.removeIf(n -> n.equalsIgnoreCase(name));
        }
        setDirty();
    }

    /** Annule les arrets temporaires (interruption, envoi ; une livraison est abandonnee). */
    public void cancelTemp(Train t) {
        if (!t.inTemp()) {
            return;
        }
        t.clearTemp();
        t.delivery = null;
        t.destination = 0;
        t.plan.clear();
        t.planVersion = -1;
        release(t, false);
        if (t.status == Train.Status.AT_STATION) {
            t.status = Train.Status.RUNNING;
        }
        setDirty();
    }

    /** Train logistique (au service du repartiteur) ou non ; un train qui quitte le service abandonne sa livraison. */
    public void setLogistic(Train t, boolean on) {
        t.logistic = on;
        if (!on && t.tempKind == Train.TempKind.DELIVERY) {
            t.clearTemp();
            t.delivery = null;
            t.destination = 0;
            t.planVersion = -1;
        }
        setDirty();
    }

    /** Groupe sans train : supprime. */
    private void pruneGroup(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        for (Train u : trains.values()) {
            if (name.equalsIgnoreCase(u.group)) {
                return;
            }
        }
        library.removeGroup(name);
    }

    /** 0.20.5 : signal nomme (PioScript). */
    public void setSignal(String name, int value) {
        String k = name.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty() || k.length() > 32) {
            return;
        }
        if (value == 0) {
            signals.remove(k);
        } else if (signals.size() < 256 || signals.containsKey(k)) {
            signals.put(k, value);
        }
        setDirty();
    }

    // ------------------------------------------------------------------
    // Blocs : voies, signaux, gares
    // ------------------------------------------------------------------

    public void trackPlaced(Piece piece) {
        Piece old = net.piece(piece.key());
        if (old != null && old.same(piece)) {
            //Meme piece (lames ou detecteur peints par le reseau) : rien ne change
            return;
        }
        net.add(piece);
        setDirty();
    }

    /**
     * 0.20.6 : wagons de pose. Un train arrete qui en porte un regarde le bout de la voie devant sa tete (au plus 8 pieces plus loin) : si
     * un chantier (planificateur de travaux) y prevoit une voie, le wagon pose le bloc qui la porte puis la voie, un bloc a la fois, avec les
     * materiaux de ses cases.
     */
    private void layTracks(ServerLevel level) {
        Collection<WorksPlan> plans = null;
        for (Train t : trains.values()) {
            if (Math.abs(t.speed) > 0.05F || t.trail().isEmpty()) {
                continue;
            }
            List<Car> layers = new ArrayList<>();
            for (Car c : t.cars) {
                if (c.type == CarType.LAYER) {
                    layers.add(c);
                }
            }
            if (layers.isEmpty()) {
                continue;
            }
            //Bout de la voie devant la tete
            Step s = t.head();
            for (int i = 0; i < 8; i++) {
                List<Step> nx = net.next(s);
                if (nx.isEmpty()) {
                    break;
                }
                s = nx.get(0);
            }
            if (!net.next(s).isEmpty()) {
                continue;
            }
            Piece p = net.piece(s.anchor());
            if (p == null) {
                continue;
            }
            TrackPath.End e = s.forward() ? p.path(s.path()).b : p.path(s.path()).a;
            BlockPos beyond = p.anchor.offset(e.dx(), e.dy(), e.dz()).relative(e.face());
            if (!level.isLoaded(beyond)) {
                continue;
            }
            if (plans == null) {
                plans = WorksData.get(level).all();
            }
            for (BlockPos q : new BlockPos[]{beyond, beyond.below(), beyond.above()}) {
                if (layAt(level, plans, q, layers)) {
                    break;
                }
            }
        }
    }

    /** Pose (un bloc) : d'abord ce qui porte la voie, puis la voie. Vrai si un chantier prevoit une voie ici. */
    private boolean layAt(ServerLevel level, Collection<WorksPlan> plans, BlockPos pos, List<Car> layers) {
        for (WorksPlan plan : plans) {
            BlockState target = plan.target(pos);
            if (target == null || !(target.getBlock() instanceof TrackBlock)) {
                continue;
            }
            BlockState cur = level.getBlockState(pos);
            if (WorksPlan.matches(cur, target)) {
                return true;
            }
            BlockPos below = pos.below();
            BlockState under = plan.target(below);
            if (under != null && !under.isAir() && !WorksPlan.matches(level.getBlockState(below), under)
                  && level.getBlockState(below).canBeReplaced()) {
                place(level, below, under, layers);
                return true;
            }
            if (cur.canBeReplaced()) {
                place(level, pos, target, layers);
            }
            return true;
        }
        return false;
    }

    private void place(ServerLevel level, BlockPos pos, BlockState state, List<Car> layers) {
        ItemStack need = WorksPlan.requiredItem(level, pos, state);
        if (!need.isEmpty()) {
            boolean taken = false;
            for (Car c : layers) {
                for (int i = 0; i < c.items.getSlots() && !taken; i++) {
                    if (ItemStack.isSameItem(c.items.getStackInSlot(i), need)) {
                        c.items.extractItem(i, 1, false);
                        taken = true;
                    }
                }
            }
            if (!taken) {
                return;
            }
        }
        level.setBlock(pos, WorksPlan.placedState(state), Block.UPDATE_ALL);
        if (state.getBlock() instanceof TrackBlock) {
            Collection<WorksPlan> plans = WorksData.get(level).all();
            TrackPlacement.worksChanged(level, pos, false, p -> {
                for (WorksPlan pl : plans) {
                    if (pl.target(p) != null) {
                        return true;
                    }
                }
                return false;
            });
        }
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1, 0.9F);
        setDirty();
    }

    /** 0.20.6 : un train occupe-t-il la voie a moins de r blocs (barriere de passage a niveau) ? */
    public boolean trainNear(BlockPos pos, int r) {
        for (Train t : trains.values()) {
            var it = t.occupied.iterator();
            while (it.hasNext()) {
                long a = it.nextLong();
                if (Math.abs(BlockPos.getX(a) - pos.getX()) <= r && Math.abs(BlockPos.getZ(a) - pos.getZ()) <= r && Math.abs(BlockPos.getY(a) - pos.getY()) <= 3) {
                    return true;
                }
            }
        }
        return false;
    }

    public void trackRemoved(BlockPos anchor) {
        if (net.remove(anchor) != null) {
            setDirty();
        }
    }

    /** Cases de voie d'un chunk tout juste charge : la copie logique suit le monde. */
    public void validate(ServerLevel level, ChunkPos chunk) {
        //0.20.2 : conducteurs de l'electrification dont le bloc a disparu
        List<BlockPos> gone = new ArrayList<>();
        for (Electrification.Conductor c : power.conductors()) {
            if (c.pos().getX() >> 4 == chunk.x && c.pos().getZ() >> 4 == chunk.z) {
                Block b = level.getBlockState(c.pos()).getBlock();
                boolean ok = c.kind() == Electrification.Kind.SUBSTATION ? b instanceof SubstationBlock
                      : b instanceof ConductorBlock;
                if (!ok) {
                    gone.add(c.pos());
                }
            }
        }
        gone.forEach(power::remove);
        if (!gone.isEmpty()) {
            setDirty();
        }
        for (long a : net.inChunk(chunk.toLong())) {
            Piece p = net.piece(a);
            //Seulement les ancres de ce chunk (lire un autre chunk le chargerait)
            if (p == null || p.anchor.getX() >> 4 != chunk.x || p.anchor.getZ() >> 4 != chunk.z) {
                continue;
            }
            BlockState st = level.getBlockState(p.anchor);
            Piece live = TrackBlocks.pieceOf(st, p.anchor);
            if (live == null) {
                net.remove(p.anchor);
                setDirty();
            } else if (!live.same(p)) {
                live.route = p.route;
                net.add(live);
                setDirty();
            } else {
                routeDirty.add(a);
            }
        }
    }

    private void applyVisuals(ServerLevel level) {
        if (!routeDirty.isEmpty()) {
            for (long a : routeDirty) {
                Piece p = net.piece(a);
                if (p == null || !level.isLoaded(p.anchor)) {
                    continue;
                }
                BlockState st = level.getBlockState(p.anchor);
                if (st.hasProperty(TrackBlocks.ROUTE) && st.getValue(TrackBlocks.ROUTE) != Math.min(2, p.route)) {
                    level.setBlock(p.anchor, st.setValue(TrackBlocks.ROUTE, Math.min(2, p.route)), Block.UPDATE_CLIENTS);
                }
            }
            routeDirty.clear();
        }
        if (!detectorDirty.isEmpty()) {
            for (long a : detectorDirty) {
                Piece p = net.piece(a);
                if (p == null || !level.isLoaded(p.anchor)) {
                    continue;
                }
                BlockState st = level.getBlockState(p.anchor);
                boolean on = pieceOcc.containsKey(a);
                if (st.hasProperty(TrackBlocks.POWERED) && st.getValue(TrackBlocks.POWERED) != on) {
                    level.setBlock(p.anchor, st.setValue(TrackBlocks.POWERED, on), Block.UPDATE_ALL);
                }
                if (p.kind == Piece.Kind.WEIGHBRIDGE) {
                    //Comparateur : seulement quand la pesee change de cran
                    Weigh w = weigh(p.anchor);
                    int lvl = w == null ? 0 : w.comparator();
                    if (weighLevel.getOrDefault(a, 0) != lvl) {
                        weighLevel.put(a, lvl);
                        level.updateNeighbourForOutputSignal(p.anchor, st.getBlock());
                    }
                }
            }
            detectorDirty.clear();
        }
    }

    /** Aspect des signaux (vert libre, orange reserve, rouge occupe) ; l'etat du bloc ne change que dans les chunks charges. */
    private void updateSignals(ServerLevel level) {
        for (RailNetwork.Signal s : net.signals()) {
            int aspect = 0;
            for (RailNetwork.Link l : net.links(s.port)) {
                if (l.end().face() != s.heading.getOpposite()) {
                    continue;
                }
                int sec = l.piece().section();
                if (sectionOcc.containsKey(sec)) {
                    aspect = Math.max(aspect, 2);
                } else if (reservedBy.containsKey(sec)) {
                    aspect = Math.max(aspect, 1);
                }
            }
            if (s.type == RailNetwork.SignalType.CHAIN && aspect == 1) {
                aspect = 3;
            }
            if (aspect != s.aspect) {
                s.aspect = aspect;
                if (level.isLoaded(s.pos)) {
                    BlockState st = level.getBlockState(s.pos);
                    if (st.hasProperty(TrackBlocks.ASPECT)) {
                        level.setBlock(s.pos, st.setValue(TrackBlocks.ASPECT, aspect), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    public Component statusText(Train t) {
        return Component.translatable("rail.pioneers.status." + t.status.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.put("net", net.save());
        ListTag list = new ListTag();
        for (Train t : trains.values()) {
            list.add(t.save());
        }
        tag.put("trains", list);
        tag.put("power", power.save());
        tag.put("library", library.save());
        tag.put("deliveries", dispatcher.save());
        CompoundTag sig = new CompoundTag();
        signals.forEach(sig::putInt);
        tag.put("signals", sig);
        return tag;
    }

    /**
     * 1.9.2 : mondes d'avant : pont de chaque table sauvee (5 voies droites autour du centre, le long de l'axe du pont a sa position ;
     * transbordeur : decale de la voie choisie) retire du reseau. Une table en manoeuvre n'avait plus son pont dans le reseau. Les trains
     * restes dessus gardent une trace incomplete (pose invalide : vue figee, voir syncViews) jusqu'a ce que le joueur les ramasse.
     */
    static void dropTables(RailWorld w, ListTag list) {
        for (Tag tag : list) {
            CompoundTag c = (CompoundTag) tag;
            if (c.getLong("start") >= 0) {
                continue;
            }
            BlockPos center = BlockPos.of(c.getLong("c"));
            boolean turn = c.getByte("type") == 0;
            int pos = c.getByte("pos");
            Direction.Axis axis = c.getByte("axis") == 0 ? Direction.Axis.X : Direction.Axis.Z;
            Direction.Axis bridge = turn ? (pos % 2 == 0 ? Direction.Axis.Z : Direction.Axis.X) : axis;
            BlockPos base = center;
            if (!turn) {
                base = center.relative(axis == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH, pos - 2);
            }
            Direction along = bridge == Direction.Axis.Z ? Direction.SOUTH : Direction.EAST;
            for (int k = -2; k <= 2; k++) {
                w.net.remove(base.relative(along, k));
            }
        }
    }

    public static RailWorld load(CompoundTag tag) {
        RailWorld w = new RailWorld();
        w.net.load(tag.getCompound("net"));
        //1.9.2 (retours de Guillaume) : plaques tournantes et transbordeurs supprimes ; les tables sauvees sont ignorees et les 5 pieces
        //de leur pont (sans bloc de voie) retirees du reseau tout de suite
        dropTables(w, tag.getList("tables", Tag.TAG_COMPOUND));
        w.power.load(tag.getList("power", Tag.TAG_COMPOUND));
        for (Tag t : tag.getList("trains", Tag.TAG_COMPOUND)) {
            Train train = Train.load((CompoundTag) t);
            if (!train.cars.isEmpty() && !train.trail().isEmpty()) {
                w.addTrain(train);
            }
        }
        w.library.load(tag.getCompound("library"));
        w.dispatcher.load(tag.getList("deliveries", Tag.TAG_COMPOUND));
        CompoundTag sig = tag.getCompound("signals");
        for (String k : sig.getAllKeys()) {
            w.signals.put(k, sig.getInt(k));
        }
        return w;
    }
}
