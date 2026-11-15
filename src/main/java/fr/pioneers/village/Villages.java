package fr.pioneers.village;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.economy.Values;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.reputation.ReputationApi;
import fr.pioneers.ruins.Restoration;
import fr.pioneers.ruins.RuinEvents;
import fr.pioneers.ruins.RuinSites;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 3) : les villages des peuples. Decouverte (joueur a moins de 96 blocs d'une cloche, controle toutes les 100 ticks),
 * peuple tire de la graine et de la position (reference au registre des peuples), decor de place pose une fois sur des blocs libres,
 * services lus sur les ruines restaurees (bloc 1), evenements reproductibles (penurie, surplus, caravane), Tableau des besoins, ouvriers
 * de chantier, golems et villageois selon l'affinite du village et la reputation du peuple. Aucun calcul par tick.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Villages {

    public static final double DISCOVERY = 96, SERVICE_RANGE = 400, WORKSITE_RANGE = 100;
    public static final String[] SERVICES = {"water", "energy", "workshop", "storage", "archives"};
    public static final Set<String> WORKER_PROFESSIONS = Set.of("minecraft:mason", "minecraft:toolsmith", "minecraft:weaponsmith", "minecraft:armorer");
    public static final int MAX_WORKERS = 4;

    /** Arrivee et depart d'une caravane (branches par les Colporteurs). */
    public interface Caravans {

        boolean start(ServerLevel level, BlockPos board, long village, long untilGameTime);

        void end(ServerLevel level, long village);
    }

    public static Caravans caravans = new Caravans() {
        @Override
        public boolean start(ServerLevel level, BlockPos board, long village, long untilGameTime) {
            return false;
        }

        @Override
        public void end(ServerLevel level, long village) {
        }
    };

    /** 1.9 (MAJ RP, bloc 4) : avancement d'un ralliement a afficher sur le Tableau des besoins (branche par les peuples de joueurs). */
    public static java.util.function.BiFunction<ServerLevel, VillageData.Village, CompoundTag> boardRally = (level, v) -> null;
    /** 1.9 (MAJ RP, bloc 4) : qui paie le salaire du jour d'un ouvrier avant le joueur (tresor federal d'une faction qui l'a decide). */
    @FunctionalInterface
    public interface WorkerPayer {

        boolean pay(ServerLevel level, VillageData.Village village, ServerPlayer employer, long wage);
    }

    public static WorkerPayer workerPayer = (level, village, employer, wage) -> false;
    /** 1.9 (MAJ RP, bloc 4) : motifs de banniere d'un peuple de joueurs (branche par les peuples de joueurs ; null = motif par defaut). */
    public static java.util.function.BiFunction<ServerLevel, String, ListTag> playerBanner = (level, id) -> null;

    private static int clock;
    /** Centres des villages qui ont le service Energie, par dimension (apparitions de monstres). */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, List<BlockPos>> LIT = new HashMap<>();

    private Villages() {
    }

    static void init() {
        RpNet.onServer(RpActions.BOARD_REQUEST, Villages::boardRequest);
        RpNet.onServer(RpActions.BOARD_HIRE, Villages::boardHire);
        RpNet.onServer(RpActions.REPUTATION_REQUEST, (p, t) -> reputationView(p));
        VillageRepairs.init();
        Restoration.workers = Villages::workerFraction;
        caravans = new Caravans() {
            @Override
            public boolean start(ServerLevel level, BlockPos board, long village, long untilGameTime) {
                return fr.pioneers.economy.peddler.PeddlerService.startCaravan(level, board, village, untilGameTime);
            }

            @Override
            public void end(ServerLevel level, long village) {
                fr.pioneers.economy.peddler.PeddlerService.endCaravan(level, village);
            }
        };
    }

    // ------------------------------------------------------------------ decouverte
    /** Village dont la cloche est a moins de 96 blocs (cree s'il ne l'est pas encore), ou null. */
    @Nullable
    public static VillageData.Village villageFor(ServerLevel level, BlockPos pos) {
        VillageData data = VillageData.get(level);
        //1.0.3-beta (lot 11b) : le village fonde par un joueur dont le rayon contient ce point passe avant le plus proche
        VillageData.Village owned = data.owned(pos);
        if (owned != null) {
            return owned;
        }
        VillageData.Village known = data.nearest(pos, DISCOVERY);
        if (known != null) {
            return known;
        }
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), pos, (int) DISCOVERY, PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) {
            return null;
        }
        return create(level, bell.get());
    }

    static VillageData.Village create(ServerLevel level, BlockPos bell) {
        VillageData data = VillageData.get(level);
        VillageData.Village v = data.village(bell.asLong());
        if (v == null) {
            long h = level.getSeed() * 31L + bell.asLong() * 0x9E3779B97F4A7C15L;
            h ^= h >>> 29;
            v = data.create(bell, PeopleApi.pick(h));
        }
        return v;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock % 100 != 0 || !Villagers.active()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            Set<VillageData.Village> seen = new HashSet<>();
            for (ServerPlayer p : level.players()) {
                VillageData.Village v = villageFor(level, p.blockPosition());
                if (v != null && seen.add(v)) {
                    visit(level, v);
                }
            }
        }
    }

    /** Un village pres d'un joueur : decor la premiere fois, services, evenement, etat des fontaines et lampadaires. */
    static void visit(ServerLevel level, VillageData.Village v) {
        VillageData data = VillageData.get(level);
        //1.9.1 (retours de Guillaume) : place d'un seul tenant ; une place posee par l'ancienne regle est refaite une fois
        if ((!v.decor || v.decorVersion < VillageDecor.VERSION) && PiCommonConfig.get(RpConfig.VILLAGE_DECOR, true)) {
            if (VillageDecor.refresh(level, v)) {
                v.decor = true;
                v.decorVersion = VillageDecor.VERSION;
                data.changed();
            }
        } else if (!v.banners.isEmpty() && !v.people.equals(v.bannerPeople) && VillageDecor.redress(level, v)) {
            //1.9 (MAJ RP, bloc 4) : bannieres refaites apres un ralliement fait loin des joueurs
            data.changed();
        }
        Set<String> services = services(level, v);
        if (!services.equals(v.services)) {
            v.services.clear();
            v.services.addAll(services);
            data.changed();
        }
        List<BlockPos> lit = LIT.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        lit.remove(v.center);
        if (v.services.contains("energy")) {
            lit.add(v.center);
        }
        long day = PioneerApi.day(level);
        drawEvent(level, v, day);
        boolean night = level.isNight();
        for (BlockPos f : v.fountains) {
            BlockState s = level.isLoaded(f) ? level.getBlockState(f) : null;
            if (s != null && s.getBlock() instanceof FountainBlock && s.getValue(FountainBlock.ACTIVE) != v.services.contains("water")) {
                level.setBlock(f, s.setValue(FountainBlock.ACTIVE, v.services.contains("water")), Block.UPDATE_CLIENTS);
            }
        }
        for (BlockPos l : v.lamps) {
            if (level.isLoaded(l)) {
                LamppostBlock.setLit(level, l, v.services.contains("energy") && night);
            }
        }
        int notices = Math.min(2, (activeEvent(v, day) != null ? 1 : 0) + (!v.event2.isEmpty() && day < v.event2Until ? 1 : 0));
        for (BlockPos b : v.boards) {
            BlockState s = level.isLoaded(b) ? level.getBlockState(b) : null;
            if (s != null && s.getBlock() instanceof NeedsBoardBlock && s.getValue(NeedsBoardBlock.NOTICES) != notices) {
                level.setBlock(b, s.setValue(NeedsBoardBlock.NOTICES, notices), Block.UPDATE_CLIENTS);
            }
            if (s != null && level.getBlockEntity(b) instanceof NeedsBoardBlock.Tile tile) {
                tile.update(v.id, v.services);
            }
        }
    }

    /** Services du village : ceux des ruines restaurees a moins de 400 blocs dont c'est le village le plus proche. */
    static Set<String> services(ServerLevel level, VillageData.Village v) {
        Set<String> out = new HashSet<>();
        VillageData data = VillageData.get(level);
        for (RuinSites.Site s : RuinSites.get(level).all()) {
            if (s.state != RuinSites.State.RESTORED || s.project == null || s.controller().distSqr(v.center) > SERVICE_RANGE * SERVICE_RANGE) {
                continue;
            }
            VillageData.Village nearest = data.nearest(s.controller(), SERVICE_RANGE);
            if (nearest == v) {
                String svc = s.project.getString("service");
                if (!svc.isEmpty()) {
                    out.add(svc);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ evenements (penurie, surplus, caravane)
    @Nullable
    public static String activeEvent(VillageData.Village v, long day) {
        if (!v.event.isEmpty() && day < v.eventUntil) {
            return v.event;
        }
        return null;
    }

    /** Objet de l'evenement de ce type en cours (penurie ou surplus) ou "". */
    public static String eventItem(VillageData.Village v, String type, long day) {
        if (type.equals(v.event) && day < v.eventUntil) {
            return v.eventItem;
        }
        if (type.equals(v.event2) && day < v.event2Until) {
            return v.event2Item;
        }
        return "";
    }

    /**
     * Tirage reproductible (identifiant du village, numero du tirage) : au plus un tous les trois jours, chance du reglage, un seul actif
     * (deux avec le service Entrepot). Caravane deux fois plus probable dans un village-etape.
     */
    static void drawEvent(ServerLevel level, VillageData.Village v, long day) {
        if (!v.event.isEmpty() && day >= v.eventUntil) {
            if ("caravan".equals(v.event)) {
                caravans.end(level, v.id);
            }
            v.event = "";
            v.eventItem = "";
            VillageData.get(level).changed();
        }
        if (!v.event2.isEmpty() && day >= v.event2Until) {
            if ("caravan".equals(v.event2)) {
                caravans.end(level, v.id);
            }
            v.event2 = "";
            v.event2Item = "";
            VillageData.get(level).changed();
        }
        if (v.lastDraw >= 0 && day - v.lastDraw < 3) {
            return;
        }
        boolean slot1 = v.event.isEmpty(), slot2 = v.event2.isEmpty() && v.services.contains("storage");
        if (!slot1 && !slot2) {
            return;
        }
        v.lastDraw = day;
        long h = v.id * 0x2545F4914F6CDD1DL + (day / 3) * 0x9E3779B97F4A7C15L + level.getSeed();
        java.util.Random r = new java.util.Random(h);
        if (r.nextDouble() >= PiCommonConfig.get(RpConfig.EVENT_CHANCE, 0.25)) {
            VillageData.get(level).changed();
            return;
        }
        VillageCatalog.Doctrine d = VillageCatalog.doctrine(PeopleApi.doctrine(level.getServer(), v.people));
        double wc = d.caravanFactor();
        double roll = r.nextDouble() * (2 + wc);
        String type = roll < 1 ? "shortage" : roll < 2 ? "surplus" : "caravan";
        String item = "";
        int days;
        if ("caravan".equals(type)) {
            BlockPos board = v.boards.isEmpty() ? v.center : v.boards.get(0);
            if (!caravans.start(level, board, v.id, level.getGameTime() + 3L * 24000L)) {
                VillageData.get(level).changed();
                return;
            }
            days = 3;
        } else {
            item = pickEventItem(d, type, r);
            if (item.isEmpty()) {
                return;
            }
            days = 5;
        }
        if (slot1) {
            v.event = type;
            v.eventItem = item;
            v.eventUntil = day + days;
        } else {
            v.event2 = type;
            v.event2Item = item;
            v.event2Until = day + days;
        }
        VillageData.get(level).changed();
        for (BlockPos b : v.boards) {
            level.playSound(null, b, RpSounds.PAPER_PIN.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    /** Penurie : ce que le peuple achete (brut ou fabrique jusqu'au Bronze) ; surplus : ce qu'il vend. */
    private static String pickEventItem(VillageCatalog.Doctrine d, String type, java.util.Random r) {
        List<String> pool = new ArrayList<>();
        for (String prof : Villagers.KNOWN) {
            VillageCatalog.Profession p = VillageCatalog.profession(prof);
            if (p == null) {
                continue;
            }
            VillageCatalog.Version ver = p.version(d.version());
            if ("shortage".equals(type)) {
                for (Item it : ver.buys()) {
                    Values.Entry e = Values.entry(it);
                    if (e != null && e.age() <= 1 && !"component".equals(e.quota())) {
                        pool.add(BuiltInRegistries.ITEM.getKey(it).toString());
                    }
                }
            } else {
                for (ItemStack s : ver.sells()) {
                    pool.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                }
            }
        }
        pool.sort(Comparator.naturalOrder());
        return pool.isEmpty() ? "" : pool.get(r.nextInt(pool.size()));
    }

    // ------------------------------------------------------------------ peuple des villageois (calque client)
    static void syncPeople(Villager v) {
        if (v.level() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(v) < 64 * 64) {
                    sendPeople(p, v);
                }
            }
        }
    }

    /**
     * 1.9 (MAJ RP, bloc 4) : le village a change de peuple (ralliement, retour) : ses villageois charges prennent le nouveau peuple (les
     * autres a leur chargement), leur marque est renvoyee, les bannieres de la place sont refaites (des que leurs chunks sont charges).
     * Noms, metiers et affinites ne changent pas.
     */
    public static void peopleChanged(ServerLevel level, VillageData.Village v) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(v.center).inflate(DISCOVERY);
        for (Villager x : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
            VillagerLore lore = VillagerLore.get(x);
            if (lore.identified && lore.village == v.id) {
                lore.people = v.people;
                syncPeople(x);
            }
        }
        VillageDecor.redress(level, v);
        VillageData.get(level).changed();
    }

    static void sendPeople(ServerPlayer p, Villager v) {
        VillagerLore lore = VillagerLore.get(v);
        if (!lore.identified) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("entity", v.getId());
        //1.9 (MAJ RP, bloc 4) : la marque d'un peuple de joueurs est celle du peuple modele de sa doctrine
        t.putString("people", PeopleApi.model(p.getServer(), lore.people));
        RpNet.send(p, RpActions.VILLAGER_PEOPLE, t);
    }

    // ------------------------------------------------------------------ actions comptees par le village
    /** Ce qu'un joueur a fait pour le village (Tableau : les cinq premiers). */
    public static void deed(ServerLevel level, VillageData.Village v, ServerPlayer player, long amount) {
        v.deeds.merge(player.getUUID(), amount, Long::sum);
        v.deedNames.put(player.getUUID(), EconomyApi.name(player));
        VillageData.get(level).changed();
    }

    public static boolean farmProduct(Item item) {
        for (String f : new String[]{"crop", "seed", "meat_raw", "fish", "egg", "wool", "milk", "honey", "leather", "hide", "fodder", "straw"}) {
            if (Values.has(item, f)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onRestored(RuinEvents.Restored e) {
        RuinSites.Site s = fr.pioneers.ruins.RuinApi.site(e.level, e.site);
        if (s == null) {
            return;
        }
        for (VillageData.Village v : VillageData.get(e.level).within(s.controller(), SERVICE_RANGE)) {
            boolean rural = VillageCatalog.doctrine(PeopleApi.doctrine(e.level.getServer(), v.people)).rural();
            for (UUID u : s.contributors.keySet()) {
                ServerPlayer p = e.level.getServer().getPlayerList().getPlayer(u);
                if (p != null) {
                    Villagers.changeVillage(e.level, v, p, rural ? -10 : 10, "restore");
                    v.deeds.merge(u, 1000L, Long::sum);
                    v.deedNames.put(u, EconomyApi.name(p));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onStandby(RuinEvents.SiteStandby e) {
        if (e.player == null) {
            return;
        }
        long day = PioneerApi.day(e.level);
        for (VillageData.Village v : VillageData.get(e.level).within(e.pos, SERVICE_RANGE)) {
            if (v.standbyDay != day) {
                v.standbyDay = day;
                Villagers.changeVillage(e.level, v, e.player, 3, "standby");
            }
        }
    }

    // ------------------------------------------------------------------ golems, apparitions (fuite et appels : VillageHostility)
    @SubscribeEvent
    public static void onGolemJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof IronGolem golem) || golem.isPlayerCreated()) {
            return;
        }
        golem.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(golem, Player.class, 10, true, false,
              p -> p instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator() && VillageHostility.golemHostile(golem, sp)));
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem) || !(event.getNewTarget() instanceof ServerPlayer p) || !(golem.level() instanceof ServerLevel level)
            || !Villagers.active()) {
            return;
        }
        VillageData.Village v = VillageData.get(level).nearest(golem.blockPosition(), DISCOVERY);
        //1.9.1 (retours de Guillaume) : sous le drapeau blanc, les golems du village laissent le joueur
        if (v != null && VillageHostility.truce(level, v, p)) {
            event.setCanceled(true);
            return;
        }
        boolean hostile = VillageHostility.golemHostile(golem, p);
        //a +60 ou plus, les golems du village ne reagissent plus aux coups du joueur
        if (v != null && v.average(p.getUUID()) >= 60 && !hostile) {
            event.setCanceled(true);
            return;
        }
        if (hostile) {
            //1.9.1 : l'hostilite se lit (message discret et cloche, une fois par village toutes les 30 s)
            VillageHostility.targeted(golem, p);
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        //... et viennent l'aider au combat
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().getEntity() instanceof Enemy && Villagers.active()
            && event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            ServerLevel level = p.serverLevel();
            VillageData.Village v = VillageData.get(level).nearest(p.blockPosition(), DISCOVERY);
            if (v != null && v.average(p.getUUID()) >= 60) {
                for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(16), g -> g.getTarget() == null)) {
                    g.setTarget(attacker);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        //service Energie : plus aucune creature hostile n'apparait a moins de 48 blocs de la place, en surface
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getEntity() instanceof Enemy) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockPos> lit = LIT.get(level.getLevel().dimension());
        if (lit == null || lit.isEmpty()) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (!level.canSeeSky(pos)) {
            return;
        }
        for (BlockPos c : lit) {
            if (c.distSqr(pos) <= 48 * 48) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ Tableau des besoins
    static void boardRequest(ServerPlayer player, CompoundTag t) {
        boardRequest(player, t, null);
    }

    /** 1.9.1 (retours de Guillaume) : vue du Tableau, avec la section « Reparations » et un message (reponse a une action). */
    static void boardRequest(ServerPlayer player, CompoundTag t, @Nullable Component msg) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.of(t.getLong("pos"));
        if (player.distanceToSqr(pos.getCenter()) > 64) {
            return;
        }
        VillageData.Village owned = VillageData.get(level).owned(pos);
        VillageData.Village v = owned != null ? owned : VillageData.get(level).nearest(pos, DISCOVERY);
        CompoundTag out = new CompoundTag();
        out.putLong("pos", pos.asLong());
        if (v == null) {
            out.putBoolean("none", true);
            RpNet.send(player, RpActions.BOARD_VIEW, out);
            return;
        }
        long day = PioneerApi.day(level);
        out.putString("people", v.people);
        //1.9 (MAJ RP, bloc 4) : icone du peuple modele (peuple de joueurs) et pourparlers de ralliement en cours
        out.putString("icon", PeopleApi.model(level.getServer(), v.people));
        CompoundTag rally = boardRally.apply(level, v);
        if (rally != null) {
            out.put("rally", rally);
        }
        out.putString("peopleName", Component.Serializer.toJson(ReputationApi.peopleName(level.getServer(), v.people)));
        //1.0.3-beta (lot 11b) : village fonde par un joueur : son nom et son fondateur
        Component founded = VillageFounding.name(v);
        if (founded != null) {
            out.putString("villageName", Component.Serializer.toJson(founded));
            out.putString("owner", v.ownerName);
            out.putBoolean("mine", VillageFounding.isFounder(v, player.getUUID()));
        }
        ListTag events = new ListTag();
        for (String[] e : new String[][]{{v.event, v.eventItem, String.valueOf(v.eventUntil)}, {v.event2, v.event2Item, String.valueOf(v.event2Until)}}) {
            if (!e[0].isEmpty() && day < Long.parseLong(e[2])) {
                CompoundTag c = new CompoundTag();
                c.putString("type", e[0]);
                c.putString("item", e[1]);
                c.putLong("days", Long.parseLong(e[2]) - day);
                List<String> lines = VillageCatalog.lines("common/board/" + e[0]);
                if (!lines.isEmpty()) {
                    c.putString("line", lines.get((int) Math.floorMod(v.id + day, lines.size())));
                }
                events.add(c);
            }
        }
        out.put("events", events);
        ListTag svc = new ListTag();
        v.services.forEach(s -> svc.add(StringTag.valueOf(s)));
        out.put("services", svc);
        ListTag top = new ListTag();
        v.deeds.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(5).forEach(e -> {
            //nomme seulement a +40 ou plus d'affinite du village
            if (v.average(e.getKey()) >= 40) {
                top.add(StringTag.valueOf(v.deedNames.getOrDefault(e.getKey(), "?")));
            }
        });
        out.put("top", top);
        out.putInt("affinity", v.average(player.getUUID()));
        //ouvriers : chantier du joueur a moins de 100 blocs
        RuinSites.Site site = worksite(level, v, player);
        if (site != null) {
            CompoundTag w = new CompoundTag();
            ListTag hired = site.project.getList("workers", 10);
            w.putInt("hired", hired.size());
            w.putInt("candidates", candidates(level, v, player, site).size());
            w.putLong("wage", wage(v, player.getUUID()));
            w.putBoolean("allowed", VillageCatalog.doctrine(PeopleApi.doctrine(level.getServer(), v.people)).workers());
            out.put("workers", w);
        }
        //1.9.1 (retours de Guillaume) : amende honorable, taches de reparation, emissaire en route
        out.put("repairs", VillageRepairs.section(level, v, player));
        if (msg != null) {
            out.putString("msg", Component.Serializer.toJson(msg));
        }
        RpNet.send(player, RpActions.BOARD_VIEW, out);
    }

    @Nullable
    static RuinSites.Site worksite(ServerLevel level, VillageData.Village v, ServerPlayer player) {
        for (RuinSites.Site s : RuinSites.get(level).all()) {
            if (s.state == RuinSites.State.RESTORING && s.project != null && s.controller().distSqr(v.center) <= WORKSITE_RANGE * WORKSITE_RANGE
                && s.contributors.containsKey(player.getUUID())) {
                return s;
            }
        }
        return null;
    }

    /** Prix d'un ouvrier par jour (jetons) : 20, ou 10 quand l'affinite du village atteint +40. */
    static long wage(VillageData.Village v, UUID player) {
        double centi = v.average(player) >= 40 ? Values.setting("worker_daily_friend", 1000) : Values.setting("worker_daily", 2000);
        //1.9 (MAJ RP, bloc 4) : clan Allie du peuple du village : ouvriers a -20 %
        centi *= fr.pioneers.clan.rep.ClanReputation.workerFactor(player, v.people);
        return Math.max(1, Math.round(centi / 100));
    }

    static List<Villager> candidates(ServerLevel level, VillageData.Village v, ServerPlayer player, RuinSites.Site site) {
        List<Villager> out = new ArrayList<>();
        int trust = ReputationApi.tier(player, v.people).workersTrust();
        int need = trust * 20 - 20;
        ListTag hired = site.project.getList("workers", 10);
        Set<UUID> taken = new HashSet<>();
        for (int i = 0; i < hired.size(); i++) {
            taken.add(hired.getCompound(i).getUUID("v"));
        }
        for (UUID id : v.register.keySet()) {
            Entity e = level.getEntity(id);
            if (e instanceof Villager w && w.isAlive() && WORKER_PROFESSIONS.contains(Villagers.profession(w)) && !taken.contains(id)
                && v.affinity(id, player.getUUID()) >= need) {
                out.add(w);
            }
        }
        return out;
    }

    static void boardHire(ServerPlayer player, CompoundTag t) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.of(t.getLong("pos"));
        if (player.distanceToSqr(pos.getCenter()) > 64) {
            return;
        }
        VillageData.Village v = VillageData.get(level).nearest(pos, DISCOVERY);
        RuinSites.Site site = v == null ? null : worksite(level, v, player);
        if (v == null || site == null) {
            return;
        }
        ListTag hired = site.project.getList("workers", 10);
        boolean allowed = VillageCatalog.doctrine(PeopleApi.doctrine(level.getServer(), v.people)).workers();
        String line = null;
        Entity speaker = null;
        if (t.getBoolean("release")) {
            for (int i = 0; i < hired.size(); i++) {
                Entity e = level.getEntity(hired.getCompound(i).getUUID("v"));
                if (e != null) {
                    speaker = e;
                }
            }
            site.project.put("workers", new ListTag());
            line = pick("common/workers/leave", v.id);
        } else if (!allowed) {
            List<Villager> any = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16));
            speaker = any.isEmpty() ? null : any.get(0);
            line = pick("common/workers/refuse_bare_hands", v.id);
        } else if (hired.size() < MAX_WORKERS) {
            List<Villager> c = candidates(level, v, player, site);
            if (!c.isEmpty()) {
                Villager w = c.get(0);
                CompoundTag h = new CompoundTag();
                h.putUUID("v", w.getUUID());
                h.putUUID("e", player.getUUID());
                hired.add(h);
                site.project.put("workers", hired);
                speaker = w;
                line = pick("common/workers/hire", w.getUUID().getLeastSignificantBits());
            }
        }
        RuinSites.get(level).changed();
        CompoundTag again = new CompoundTag();
        again.putLong("pos", pos.asLong());
        //1.9.1 (retours de Guillaume) : plus de bulle ; la replique de l'ouvrier s'ecrit dans le Tableau, avec son nom
        boardRequest(player, again, speaker != null && line != null ? Component.translatable("village.pioneers.dialogue.said", speaker.getDisplayName(),
              Component.translatable(line)) : null);
    }

    private static String pick(String path, long seed) {
        List<String> l = VillageCatalog.lines(path);
        return l.isEmpty() ? null : l.get((int) Math.floorMod(seed, l.size()));
    }

    /**
     * Ouvriers d'un chantier pour un jour : chacun retire 10 % de la duree restante (40 % au plus, 60 % avec le skill du joueur qui a ouvert
     * le chantier) et coute son salaire, preleve au passage du jour sur le solde de son employeur. Solde insuffisant ou employeur absent : il
     * ne travaille pas ce jour-la, sans cout.
     */
    static double workerFraction(ServerLevel level, RuinSites.Site site, long day) {
        if (site.project == null || !site.project.contains("workers")) {
            return 0;
        }
        ListTag hired = site.project.getList("workers", 10);
        if (hired.isEmpty() || site.project.getLong("wday") >= day) {
            return site.project.getLong("wday") == day ? site.project.getDouble("wfrac") : 0;
        }
        VillageData.Village v = VillageData.get(level).nearest(site.controller(), WORKSITE_RANGE);
        int working = 0;
        for (int i = 0; i < hired.size(); i++) {
            CompoundTag h = hired.getCompound(i);
            ServerPlayer employer = level.getServer().getPlayerList().getPlayer(h.getUUID("e"));
            Entity w = level.getEntity(h.getUUID("v"));
            boolean alive = w == null || w.isAlive();
            if (employer == null || !alive || v == null) {
                continue;
            }
            long wage = wage(v, employer.getUUID());
            //1.9 (MAJ RP, bloc 4) : la faction de l'employeur paie d'abord, si elle l'a decide (tresor federal)
            if (workerPayer.pay(level, v, employer, wage) || EconomyApi.debit(employer, wage, EconomyApi.Entry.of("worker")).ok()) {
                working++;
            }
        }
        double cut = site.project.getDouble("cut");
        double max = Math.max(0, Math.min(0.4, 1 - 0.4 / Math.max(0.4, 1 - cut)));
        double frac = Math.min(max, 0.1 * working);
        site.project.putLong("wday", day);
        site.project.putDouble("wfrac", frac);
        return frac;
    }

    // ------------------------------------------------------------------ onglet Reputation
    static void reputationView(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        CompoundTag t = new CompoundTag();
        ListTag peoples = new ListTag();
        for (PeopleApi.People p : PeopleApi.all(level.getServer())) {
            CompoundTag c = new CompoundTag();
            c.putString("id", p.id());
            //1.9 (MAJ RP, bloc 4) : icone du peuple modele pour un peuple de joueurs
            c.putString("icon", PeopleApi.model(level.getServer(), p.id()));
            c.putInt("color", p.color());
            c.putString("name", Component.Serializer.toJson(ReputationApi.peopleName(level.getServer(), p.id())));
            c.putString("tier", ReputationApi.tier(player, p.id()).id());
            //1.0.5-beta (lot 16) : role, origine et attraits : doctrine, peuple de joueurs, actes REELS tels que ce peuple les juge
            c.putString("doctrine", p.doctrine());
            c.putBoolean("player", p.player());
            List<String> names = new ArrayList<>();
            List<int[]> judged = ReputationApi.judged(p.player() ? PeopleApi.model(level.getServer(), p.id()) : p.id(), names);
            ListTag acts = new ListTag();
            for (int i = 0; i < judged.size() && i < 16; i++) {
                CompoundTag a = new CompoundTag();
                a.putString("a", names.get(i));
                a.putIntArray("r", judged.get(i));
                acts.add(a);
            }
            c.put("acts", acts);
            peoples.add(c);
        }
        t.put("peoples", peoples);
        t.putInt("cap", PiCommonConfig.get(RpConfig.REPUTATION_DAILY_CAP, 30));
        ListTag tiers = new ListTag();
        for (int v : new int[]{-200, -60, 59, 149, 300}) {
            CompoundTag c = new CompoundTag();
            ReputationApi.Tier tier = ReputationApi.tierOf(v);
            c.putString("id", tier.id());
            c.putInt("max", tier.max());
            tiers.add(c);
        }
        t.put("tiers", tiers);
        ListTag villages = new ListTag();
        List<VillageData.Village> list = new ArrayList<>(VillageData.get(level).all());
        list.removeIf(v -> v.register.values().stream().noneMatch(m -> m.containsKey(player.getUUID()))
                           && v.center.distSqr(player.blockPosition()) > SERVICE_RANGE * SERVICE_RANGE);
        list.sort(Comparator.comparingDouble(v -> v.center.distSqr(player.blockPosition())));
        for (VillageData.Village v : list.subList(0, Math.min(10, list.size()))) {
            CompoundTag c = new CompoundTag();
            c.putString("people", v.people);
            //1.9 (MAJ RP, bloc 4) : un village rallie porte le nom de son peuple de joueurs
            c.putString("icon", PeopleApi.model(level.getServer(), v.people));
            c.putString("peopleName", Component.Serializer.toJson(ReputationApi.peopleName(level.getServer(), v.people)));
            //1.0.3-beta (lot 11b) : un village fonde par un joueur montre son nom
            Component founded = VillageFounding.name(v);
            if (founded != null) {
                c.putString("villageName", Component.Serializer.toJson(founded));
            }
            c.putInt("affinity", v.average(player.getUUID()));
            c.putInt("distance", (int) Math.sqrt(v.center.distSqr(player.blockPosition())));
            double dx = v.center.getX() - player.getX(), dz = v.center.getZ() - player.getZ();
            String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
            c.putString("dir", dirs[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8)]);
            c.putInt("count", v.register.size());
            villages.add(c);
        }
        t.put("villages", villages);
        RpNet.send(player, RpActions.REPUTATION_VIEW, t);
    }
}
