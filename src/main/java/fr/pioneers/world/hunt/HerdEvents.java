package fr.pioneers.world.hunt;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.world.fauna.FaunaCounter;
import fr.pioneers.world.fauna.FaunaPools;
import fr.pioneers.world.fauna.FaunaRegistry;
import fr.pioneers.world.fauna.FaunaSpawner;
import fr.pioneers.world.fauna.FaunaSpecies;
import fr.pioneers.world.fauna.FaunaZones;
import fr.pioneers.world.pet.HuntEvents;
import fr.pioneers.world.pet.Pet;
import fr.pioneers.world.pet.PetMode;
import fr.pioneers.world.pet.PetRegistry;
import fr.pioneers.world.wild.PiWild;
import fr.pioneers.world.wild.WildAnimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.1 et §5.4) : l'EVENEMENT « TROUPEAU », sur le principe des patrouilles de pillards.
 * <ul>
 *   <li>de temps en temps (1 a 2 jours de jeu par joueur, {@link HuntSettings}), pres d'un joueur de l'Overworld, un troupeau de 4 a 10 betes
 *   apparait a 48-96 blocs, HORS DE SA VUE, hors claim et loin des villages (memes regles de lieu que le repeuplement : {@link FaunaSpawner}),
 *   d'une espece du biome et de la saison ({@link HuntRules#KINDS}) ; jamais deux troupeaux a moins de {@link HuntRules#HERD_SPACING}
 *   blocs ;</li>
 *   <li>les betes portent la marque {@link FaunaCounter#HERD_TAG} : suivies mais jamais comptees (repeuplement) ni parents (reproduction
 *   sauvage) ;</li>
 *   <li>personne a moins de {@link HuntRules#HERD_APPROACH} blocs en un jour : le troupeau se DISPERSE (les betes s'eloignent) puis
 *   DISPARAIT (seule exception a « ne disparaissent jamais ») ; approche : le troupeau est TROUVE (XP de Chasse « hunting.herd ») ; un jour
 *   plus tard, les survivants s'en vont quand aucun joueur n'est a {@link HuntRules#HERD_LEAVE_DISTANCE} blocs ;</li>
 *   <li>signes : traces au sol (particules de terre, discretes) sur une piste qui part du troupeau vers le joueur, cris au loin, et le CHIEN :
 *   crochets de {@link HuntEvents} (le chien autonome grogne puis va chercher son maitre ; bouton Chasse mis en avant ; le chien en mode
 *   Chasse suit la piste ; fin du troupeau -> {@link HuntEvents#endHunt}).</li>
 * </ul>
 * Commande (operateurs) : {@code /pioneer hunt herd [espece]} (troupeau force pres de soi), {@code /pioneer hunt herds} (liste).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class HerdEvents {

    private HerdEvents() {
    }

    /** Heure de la marque (donnees persistantes de la bete). */
    public static final String HERD_TIME = "pioneers_herd_t";

    /** Branche les crochets du chien (appele par {@link PiHunt#init}). */
    public static void hooks() {
        //1.0.5-beta (lot 31, retour 43) : bouton Chasse allume SEULEMENT si un troupeau est a portee du flair de CE chien (piste comprise)
        HuntEvents.highlight = (dog, master) -> dog.level() instanceof ServerLevel level && huntInReach(level, dog.position(), scentOf(dog, master));
        //1.0.6 : n'importe quel troupeau actif pas encore trouve (avant : le PREMIER troupeau a portee seulement ; s'il etait deja trouve, un
        //troupeau neuf plus loin ne donnait plus l'alerte)
        HuntEvents.herdNear = dog -> dog.level() instanceof ServerLevel level && unfoundNear(level, dog.position(), HuntRules.DOG_ALERT) != null;
        HuntEvents.trail = (dog, range) -> dog.level() instanceof ServerLevel level ? nearestMember(level, dog.position(), range) : null;
        HuntEvents.scentBonus = HuntSkill::scentBonus;
    }

    // ------------------------------------------------------------------ requetes

    /** Portee du flair d'un chien (race et savoir-faire Chasse du maitre ; = {@code PetGoals.Hunt.range}). */
    public static double scentOf(net.minecraft.world.entity.TamableAnimal dog, @Nullable Player master) {
        float scent = dog instanceof Pet p && !p.isCat() ? fr.pioneers.world.pet.GeneratedPets.DOGS[Math.max(0, Math.min(fr.pioneers.world.pet.GeneratedPets.DOGS.length - 1,
              p.breed()))].scent() : 0F;
        return fr.pioneers.world.pet.PetRules.scentRange(scent, HuntEvents.bonus(master));
    }

    /** 1.0.5-beta (lot 31) : une chasse est-elle a portee du flair ({@link HuntRules#huntInReach}) depuis {@code pos} ? */
    public static boolean huntInReach(ServerLevel level, Vec3 pos, double scentRange) {
        if (level.dimension() != Level.OVERWORLD || level.getServer() == null) {
            return false;
        }
        for (HerdData.Herd h : HerdData.get(level.getServer()).herds()) {
            if (HuntRules.huntInReach(h.disperse >= 0, h.members.size(), h.center.getX() + 0.5 - pos.x, h.center.getZ() + 0.5 - pos.z, scentRange)) {
                return true;
            }
        }
        return false;
    }

    /** 1.0.6 : troupeau actif (pas en dispersion) et pas encore trouve dont le centre est a {@code range} blocs, ou null. */
    @Nullable
    public static HerdData.Herd unfoundNear(ServerLevel level, Vec3 pos, double range) {
        if (level.dimension() != Level.OVERWORLD || level.getServer() == null) {
            return null;
        }
        for (HerdData.Herd h : HerdData.get(level.getServer()).herds()) {
            if (h.disperse < 0 && h.found < 0 && Vec3.atCenterOf(h.center).distanceToSqr(pos) < range * range) {
                return h;
            }
        }
        return null;
    }

    /** Troupeau actif (pas en dispersion) dont le centre est a {@code range} blocs, ou null. */
    @Nullable
    public static HerdData.Herd activeNear(ServerLevel level, Vec3 pos, double range) {
        if (level.dimension() != Level.OVERWORLD || level.getServer() == null) {
            return null;
        }
        for (HerdData.Herd h : HerdData.get(level.getServer()).herds()) {
            if (h.disperse < 0 && Vec3.atCenterOf(h.center).distanceToSqr(pos) < range * range) {
                return h;
            }
        }
        return null;
    }

    /** Bete de troupeau vivante la plus proche a {@code range} blocs. */
    @Nullable
    public static LivingEntity nearestMember(ServerLevel level, Vec3 pos, double range) {
        LivingEntity best = null;
        double bd = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(range, 24, range),
              e -> e.isAlive() && FaunaCounter.herd(e))) {
            double d = e.position().distanceToSqr(pos);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    public static boolean isMember(Entity e) {
        return FaunaCounter.herd(e);
    }

    // ------------------------------------------------------------------ boucle

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
            || level.getServer() == null) {
            return;
        }
        long now = level.getGameTime();
        HuntSettings s = HuntSettings.current();
        if (now % HuntRules.HERD_CHECK == 0 && s.herds()) {
            schedule(level, s, now);
        }
        if (now % 20 == 0) {
            update(level, now);
        }
        if (now % HuntRules.TRACK_PERIOD == 7) {
            signs(level, now);
        }
    }

    /** Planification : chaque joueur a son heure ; a l'heure, un troupeau est tente (sinon nouvel essai plus tard). */
    public static void schedule(ServerLevel level, HuntSettings s, long now) {
        HerdData data = HerdData.get(level.getServer());
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            long next = data.next(p.getUUID());
            if (next == Long.MIN_VALUE) {
                data.setNext(p.getUUID(), now + HuntRules.nextDelay(s.minDays(), s.maxDays(), level.random.nextDouble()));
                continue;
            }
            if (now < next || data.of(p.getUUID()) != null || activeNear(level, p.position(), HuntRules.HERD_SPACING) != null) {
                continue;
            }
            HerdData.Herd h = spawnNear(level, p, null, s);
            data.setNext(p.getUUID(), now + (h != null ? HuntRules.nextDelay(s.minDays(), s.maxDays(), level.random.nextDouble())
                                                       : HuntRules.HERD_RETRY));
        }
    }

    /**
     * Fait apparaitre un troupeau pres de {@code p} (lieu tire a {@code minDistance..maxDistance} blocs, hors de vue, hors claim et loin des
     * villages) ; {@code species} force l'espece (commande, GameTests), sinon tiree dans la table du biome et de la saison. Null si aucun lieu.
     */
    @Nullable
    public static HerdData.Herd spawnNear(ServerLevel level, ServerPlayer p, @Nullable String species, HuntSettings s) {
        RandomSource r = level.random;
        for (int t = 0; t < HuntRules.HERD_TRIES; t++) {
            double ang = r.nextDouble() * Math.PI * 2, d = HuntRules.distance(s.minDistance(), s.maxDistance(), r.nextDouble());
            int x = Mth.floor(p.getX() + Math.cos(ang) * d), z = Mth.floor(p.getZ() + Math.sin(ang) * d);
            HerdData.Herd h = spawnAt(level, p.getUUID(), x, z, species, s, FaunaSpawner.Limits.repopulation(level,
                  fr.pioneers.world.fauna.FaunaSettings.current()), Math.min(s.minDistance(), fr.pioneers.world.fauna.FaunaSettings.current().playerDistance()));
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    /**
     * Troupeau dans la colonne (x, z) : biome, saison, espece, lieu permis (distance {@code minDistance} de tout regard, vue, claims,
     * villages), regles du type. Null si la colonne ne convient pas.
     */
    @Nullable
    public static HerdData.Herd spawnAt(ServerLevel level, UUID player, int x, int z, @Nullable String species, HuntSettings s,
          FaunaSpawner.Limits limits, int minDistance) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        RandomSource r = level.random;
        String id = species;
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z));
        if (id == null) {
            Set<String> tags = new HashSet<>();
            biome.tags().forEach(tk -> tags.add(tk.location().toString()));
            String bid = biome.unwrapKey().map(k -> k.location().toString()).orElse("");
            HuntRules.HerdKind k = HuntRules.pickHerd(bid, tags, FaunaZones.season(level), r.nextDouble());
            if (k == null) {
                return null;
            }
            id = k.species();
        }
        FaunaSpecies sp = FaunaRegistry.byId(id.contains(":") ? id : "pioneers:" + id);
        EntityType<?> type = sp == null ? null : FaunaRegistry.type(sp);
        if (type == null) {
            return null;
        }
        FaunaPools.Entry e = new FaunaPools.Entry(sp, type, FaunaSpecies.Where.LAND, 1, 1, 1);
        BlockPos spot = FaunaSpawner.spot(level, FaunaSpecies.Where.LAND, type, x, z, r);
        FaunaSpawner.Limits l = new FaunaSpawner.Limits(limits.viewers(), minDistance, limits.villageDistance(), true, limits.viewRange());
        if (spot == null || !FaunaSpawner.allowed(level, spot, sp, l) || !FaunaSpawner.fits(level, e, spot, MobSpawnType.NATURAL, r)) {
            return null;
        }
        int n = HuntRules.size(s.minSize(), s.maxSize(), r.nextDouble());
        HerdData data = HerdData.get(level.getServer());
        HerdData.Herd h = data.add(player, sp.id, spot, level.getGameTime());
        List<Entity> out = new ArrayList<>();
        int placed = FaunaSpawner.spawnBand(level, e, new FaunaSpawner.Area(x - 14, z - 14, x + 14, z + 14), spot, n, l, MobSpawnType.EVENT, r, out,
              ent -> mark(ent, h));
        if (placed <= 0) {
            data.remove(h.id);
            return null;
        }
        for (Entity ent : out) {
            h.members.add(ent.getUUID());
            if (ent instanceof net.minecraft.world.entity.AgeableMob am && am.isBaby()) {
                am.setAge(0);
            }
        }
        h.nextCall = level.getGameTime() + 100;
        data.setDirty();
        return h;
    }

    static void mark(Entity e, HerdData.Herd h) {
        e.getPersistentData().putInt(FaunaCounter.HERD_TAG, h.id);
        e.getPersistentData().putLong(HERD_TIME, h.start);
    }

    /** Mise a jour (chaque seconde) : decouverte, dispersion, fin. */
    public static void update(ServerLevel level, long now) {
        MinecraftServer server = level.getServer();
        HerdData data = HerdData.get(server);
        for (Iterator<HerdData.Herd> it = data.herds.values().iterator(); it.hasNext(); ) {
            HerdData.Herd h = it.next();
            List<LivingEntity> loaded = new ArrayList<>();
            for (Iterator<UUID> mi = h.members.iterator(); mi.hasNext(); ) {
                Entity e = level.getEntity(mi.next());
                if (e instanceof LivingEntity le) {
                    if (!le.isAlive()) {
                        mi.remove();
                        data.setDirty();
                    } else {
                        loaded.add(le);
                    }
                }
            }
            boolean near = false;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator()) {
                    continue;
                }
                for (LivingEntity m : loaded) {
                    double d2 = m.distanceToSqr(p);
                    if (d2 < HuntRules.HERD_LEAVE_DISTANCE * HuntRules.HERD_LEAVE_DISTANCE) {
                        near = true;
                    }
                    if (d2 < HuntRules.HERD_APPROACH * HuntRules.HERD_APPROACH && h.disperse < 0 && h.finders.add(p.getUUID())) {
                        if (h.found < 0) {
                            h.found = now;
                        }
                        data.setDirty();
                        PioneerApi.gain(p, "hunting.herd");
                        p.displayClientMessage(Component.translatable("hunt.pioneers.herd.found",
                              Component.translatable(type(h) == null ? "entity.pioneers." + path(h.species) : type(h).getDescriptionId())), true);
                    }
                }
            }
            int state = h.disperse >= 0 ? HuntRules.DISPERSING : HuntRules.state(h.start, h.found, now, h.members.size(), near, HuntRules.HERD_LIFE);
            if (state == HuntRules.OVER) {
                it.remove();
                data.setDirty();
                ended(level, h);
                continue;
            }
            if (state == HuntRules.DISPERSING) {
                if (h.disperse < 0) {
                    h.disperse = now;
                    data.setDirty();
                    ended(level, h);
                }
                for (LivingEntity m : loaded) {
                    if (now - h.disperse >= HuntRules.DISPERSE_TICKS) {
                        m.discard();
                    } else {
                        flee(m, Vec3.atCenterOf(h.center), now - h.disperse < 20);
                    }
                }
                if (now - h.disperse >= HuntRules.DISPERSE_TICKS) {
                    //betes non chargees : retirees a leur arrivee (join), le troupeau est fini
                    it.remove();
                    data.setDirty();
                }
                continue;
            }
            //cris au loin
            if (now >= h.nextCall && !loaded.isEmpty() && h.found < 0) {
                h.nextCall = now + HuntRules.CALL_MIN + level.random.nextInt(HuntRules.CALL_MAX - HuntRules.CALL_MIN);
                LivingEntity m = loaded.get(level.random.nextInt(loaded.size()));
                call(level, m);
            }
        }
    }

    /** Cri d'une bete du troupeau, entendu de loin. */
    static void call(ServerLevel level, LivingEntity m) {
        SoundEvent s = m instanceof WildAnimal w ? PiWild.sound(w.kind, "ambient") : null;
        if (s != null) {
            level.playSound(null, m.getX(), m.getY(), m.getZ(), s, SoundSource.NEUTRAL, HuntRules.CALL_VOLUME, 0.9F + level.random.nextFloat() * 0.2F);
        }
    }

    /** Une bete qui s'en va : elle fuit loin du centre du troupeau. */
    static void flee(LivingEntity m, Vec3 from, boolean first) {
        if (m instanceof WildAnimal w) {
            w.alarm(from, HuntRules.DISPERSE_TICKS, false);
        } else if (m instanceof PathfinderMob pm && (first || pm.getNavigation().isDone())) {
            Vec3 to = DefaultRandomPos.getPosAway(pm, 24, 8, from);
            if (to != null) {
                pm.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
            }
        }
    }

    /** Fin du troupeau (dispersion ou plus aucune bete) : les chiens en mode Chasse du joueur et des chasseurs reviennent en autonome. */
    static void ended(ServerLevel level, HerdData.Herd h) {
        MinecraftServer server = level.getServer();
        Set<UUID> masters = new HashSet<>(h.finders);
        masters.add(h.player);
        //chiens proches (apprivoises, avec ou sans maitre au registre)
        for (TamableAnimal dog : level.getEntitiesOfClass(TamableAnimal.class, new net.minecraft.world.phys.AABB(h.center).inflate(
              HuntRules.HIGHLIGHT_DISTANCE, 64, HuntRules.HIGHLIGHT_DISTANCE), t -> t instanceof Pet pet && pet.mode() == PetMode.HUNT
                                                                                 && masters.contains(t.getOwnerUUID()))) {
            HuntEvents.endHunt(dog);
        }
        PetRegistry reg = PetRegistry.get(server);
        for (UUID m : masters) {
            for (PetRegistry.Entry e : reg.list(m)) {
                ServerLevel lvl = server.getLevel(ResourceKey.create(Registries.DIMENSION, e.dim()));
                Entity ent = lvl == null ? null : lvl.getEntity(e.id());
                if (ent instanceof TamableAnimal dog && ent instanceof Pet pet && pet.mode() == PetMode.HUNT) {
                    HuntEvents.endHunt(dog);
                }
            }
        }
    }

    /** Signes : traces au sol (particules de terre) sur la piste du troupeau, pres des joueurs. */
    static void signs(ServerLevel level, long now) {
        HerdData data = HerdData.get(level.getServer());
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
        for (HerdData.Herd h : data.herds()) {
            if (h.found >= 0 || h.disperse >= 0) {
                continue;
            }
            Vec3 c = Vec3.atBottomCenterOf(h.center);
            for (ServerPlayer p : level.players()) {
                Vec3 to = p.position().subtract(c).multiply(1, 0, 1);
                double len = to.length();
                if (len < 1 || len > HuntRules.TRACK_LENGTH + HuntRules.TRACK_SEEN) {
                    continue;
                }
                Vec3 dir = to.scale(1 / len), side = new Vec3(-dir.z, 0, dir.x);
                for (int i = 4; i <= Math.min(HuntRules.TRACK_LENGTH, len + 8); i += 2) {
                    Vec3 at = c.add(dir.scale(i));
                    if (at.distanceToSqr(p.position()) > HuntRules.TRACK_SEEN * HuntRules.TRACK_SEEN) {
                        continue;
                    }
                    //empreintes en quinconce, posees sur le sol
                    Vec3 foot = at.add(side.scale((i / 2) % 2 == 0 ? 0.35 : -0.35));
                    int gx = Mth.floor(foot.x), gz = Mth.floor(foot.z);
                    int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                    level.sendParticles(p, dirt, false, foot.x, gy + 0.05, foot.z, 2, 0.08, 0.0, 0.08, 0.0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ betes a leur arrivee, morts

    /** Une bete de troupeau qui arrive alors que son troupeau est fini (ou disperse) : elle n'entre pas (elle s'est dispersee). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getServer() == null || !event.loadedFromDisk() || !isMember(event.getEntity())) {
            return;
        }
        int id = event.getEntity().getPersistentData().getInt(FaunaCounter.HERD_TAG);
        HerdData.Herd h = HerdData.get(level.getServer()).herd(id);
        if (h == null || h.disperse >= 0) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level() instanceof ServerLevel level && level.getServer() != null && isMember(e)) {
            HerdData.Herd h = HerdData.get(level.getServer()).herd(e.getPersistentData().getInt(FaunaCounter.HERD_TAG));
            if (h != null && h.members.remove(e.getUUID())) {
                HerdData.get(level.getServer()).setDirty();
            }
        }
    }

    @Nullable
    static EntityType<?> type(HerdData.Herd h) {
        FaunaSpecies sp = FaunaRegistry.byId(h.species);
        return sp == null ? null : FaunaRegistry.type(sp);
    }

    static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    // ------------------------------------------------------------------ commande

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("hunt")
                    .then(Commands.literal("herd").executes(c -> herd(c, null))
                          .then(Commands.argument("species", StringArgumentType.word()).executes(c -> herd(c, StringArgumentType.getString(c, "species")))))
                    .then(Commands.literal("herds").executes(HerdEvents::list))));
    }

    private static int herd(CommandContext<CommandSourceStack> ctx, @Nullable String species) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (p.level().dimension() != Level.OVERWORLD) {
            ctx.getSource().sendFailure(Component.translatable("hunt.pioneers.cmd.overworld"));
            return 0;
        }
        HerdData.Herd h = spawnNear((ServerLevel) p.level(), p, species, HuntSettings.current());
        if (h == null) {
            ctx.getSource().sendFailure(Component.translatable("hunt.pioneers.cmd.none"));
            return 0;
        }
        BlockPos c = h.center;
        ctx.getSource().sendSuccess(() -> Component.translatable("hunt.pioneers.cmd.herd", h.members.size(), h.species,
              c.getX() + " " + c.getY() + " " + c.getZ()), true);
        return h.members.size();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        long now = level.getGameTime();
        int n = 0;
        for (HerdData.Herd h : HerdData.get(level.getServer()).herds()) {
            BlockPos c = h.center;
            long age = (now - h.start) / 20;
            String st = h.disperse >= 0 ? "dispersing" : h.found >= 0 ? "found" : "active";
            ctx.getSource().sendSuccess(() -> Component.translatable("hunt.pioneers.cmd.line", h.id, h.species, h.members.size(),
                  c.getX() + " " + c.getY() + " " + c.getZ(), age, Component.translatable("hunt.pioneers.cmd.state." + st)), false);
            n++;
        }
        if (n == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("hunt.pioneers.cmd.empty"), false);
        }
        return n;
    }
}
