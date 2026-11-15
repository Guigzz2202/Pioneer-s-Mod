package fr.pioneers.ruins.sentinel;

import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.gear.EnergyBoltEntity;
import fr.pioneers.ruins.RuinApi;
import fr.pioneers.ruins.RuinEvents;
import fr.pioneers.ruins.RuinSites;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * 1.9 (MAJ RP, bloc 1) : base des Sentinelles, anciens Serviteurs qui gardent leur site a la lettre. Machine a etats {@link SentinelBrain}
 * (Dormant, Alerte, Combat, Retour) ; IA a la main (aucun Goal) : endormie, la Sentinelle est immobile (ni recherche de chemin, ni
 * recherche de cible, ni poussee, ni deplacement au sol), seule une verification d'eveil tous les {@code wake_interval} ticks regarde les
 * joueurs proches.
 * <p>
 * Regles : hostile aux seuls joueurs (hors creatif et spectateur), jamais en Veille ni en paisible ni si les Sentinelles sont coupees
 * ({@link RpConfig#SENTINELS}) ; laisse de {@code leash} blocs autour du poste (stricte : position ramenee) ; un combat eveille tout le site ;
 * un choc electrique (tir du fusil a plasma, foudre) etourdit {@code stun_ticks} ticks ; persistante, jamais d'apparition naturelle.
 * Poste, site, tier et etat sont sauves dans le NBT ({@code SentinelTier} sert aux tables de butin).
 */
public abstract class SentinelEntity extends PathfinderMob {

    private static final EntityDataAccessor<Byte> DATA_TIER = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_LIGHT = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_AUX = SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.INT);

    /** Drapeaux synchronises : etourdie, sous bouclier, au travail (soin, charge du laser, charge du Contremaitre), surchauffe. */
    public static final int FLAG_STUN = 1, FLAG_SHIELD = 2, FLAG_WORK = 4, FLAG_HEAT = 8;
    /** Voyants : eteints, orange (alerte et combat), bleu pale (Veille). */
    private static final String[] MK_KEYS = {"sentinel.pioneers.mk1", "sentinel.pioneers.mk2", "sentinel.pioneers.mk3"};
    public static final int LIGHT_OFF = 0, LIGHT_ALERT = 1, LIGHT_STANDBY = 2;
    protected static final DustParticleOptions SHOT = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.12F), 0.7F);

    protected final SentinelBrain brain = new SentinelBrain();
    protected long site;
    protected BlockPos post = BlockPos.ZERO;
    protected boolean posted;
    protected float postYaw;
    protected boolean summoned;
    protected boolean standby;
    protected int attackCooldown;
    private int stun;
    private int shield;
    private float shieldReduction;
    private int lostSight;
    private int phraseCooldown;
    private int repath;
    private int lastSiteWake = -1000;
    private boolean statsApplied;
    private SentinelRules cachedRules;
    private int cachedTier;
    private SentinelRules.Stats cachedStats;

    // ------------------------------------------------------------------ client (animations)
    public float fold = 1, oFold = 1;
    /** Tick client du dernier eveil (sequence d'allumage des voyants). */
    public int clientWake = -1000;
    /** Ticks client passes au travail ({@link #FLAG_WORK}). */
    public int workTicks;
    private boolean clientInit;
    private int clientState;

    protected SentinelEntity(EntityType<? extends SentinelEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 5;
    }

    public abstract SentinelRole role();

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TIER, (byte) 1);
        entityData.define(DATA_STATE, (byte) 0);
        entityData.define(DATA_LIGHT, (byte) LIGHT_OFF);
        entityData.define(DATA_FLAGS, (byte) 0);
        entityData.define(DATA_AUX, -1);
    }

    // ------------------------------------------------------------------ lecture
    public int tier() {
        return entityData.get(DATA_TIER);
    }

    /** Etat synchronise (ordinal de {@link SentinelBrain.State}). */
    public int stateId() {
        return entityData.get(DATA_STATE);
    }

    public boolean asleep() {
        return stateId() == SentinelBrain.State.DORMANT.ordinal();
    }

    public int light() {
        return entityData.get(DATA_LIGHT);
    }

    public boolean flag(int f) {
        return (entityData.get(DATA_FLAGS) & f) != 0;
    }

    public int aux() {
        return entityData.get(DATA_AUX);
    }

    public long site() {
        return site;
    }

    public BlockPos post() {
        return post;
    }

    public boolean summoned() {
        return summoned;
    }

    public float fold(float partial) {
        return Mth.lerp(partial, oFold, fold);
    }

    /** Nom avec le tier (« Gardien MKII ») : messages de mort, barre de boss. */
    @Override
    protected @NotNull Component getTypeName() {
        return Component.translatable("sentinel.pioneers.named", super.getTypeName(), Component.translatable(MK_KEYS[Math.max(1, Math.min(3, tier())) - 1]));
    }

    protected void setFlag(int f, boolean on) {
        byte b = entityData.get(DATA_FLAGS);
        byte n = (byte) (on ? b | f : b & ~f);
        if (n != b) {
            entityData.set(DATA_FLAGS, n);
        }
    }

    protected void setAux(int v) {
        if (entityData.get(DATA_AUX) != v) {
            entityData.set(DATA_AUX, v);
        }
    }

    protected SentinelRules rules() {
        return SentinelData.rules();
    }

    /** Valeurs du role a son tier (cache tant que les donnees ne changent pas). */
    protected SentinelRules.Stats stats() {
        SentinelRules r = SentinelData.rules();
        if (r != cachedRules || cachedTier != tier() || cachedStats == null) {
            cachedRules = r;
            cachedTier = tier();
            cachedStats = r.stats(role(), cachedTier);
        }
        return cachedStats;
    }

    /** Valeur du role (ou de son tier), sinon regle commune du meme nom. */
    protected double param(String key, double fallback) {
        SentinelRules.Stats s = stats();
        return s.has(key) ? s.get(key, fallback) : rules().common(key, fallback);
    }

    // ------------------------------------------------------------------ mise en place
    /** Pose une Sentinelle endormie a son poste (generation, appel du Contremaitre). */
    public void setup(int tier, long site, BlockPos post, float yaw) {
        entityData.set(DATA_TIER, (byte) role().clampTier(tier));
        this.site = site;
        this.post = post.immutable();
        this.posted = true;
        this.postYaw = yaw;
        moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, yaw, 0);
        yHeadRot = yBodyRot = yaw;
        applyStats(true);
        statsApplied = true;
        brain.sleep();
        syncState();
    }

    /** Attributs du tier depuis les donnees ({@code health}, {@code damage}, {@code speed}, {@code armor}...). */
    protected void applyStats(boolean heal) {
        SentinelRules.Stats s = stats();
        base(Attributes.MAX_HEALTH, s.get("health", 40));
        base(Attributes.ATTACK_DAMAGE, s.get("damage", 4));
        base(Attributes.MOVEMENT_SPEED, s.get("speed", 0.22));
        base(Attributes.FLYING_SPEED, s.get("speed", 0.3));
        base(Attributes.ARMOR, s.get("armor", 2));
        base(Attributes.KNOCKBACK_RESISTANCE, s.get("knockback_resistance", 0.5));
        base(Attributes.FOLLOW_RANGE, s.get("follow_range", 24));
        xpReward = (int) Math.round(s.get("xp", 5));
        if (heal || getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    private void base(Attribute attribute, double value) {
        AttributeInstance a = getAttribute(attribute);
        if (a != null && a.getBaseValue() != value) {
            a.setBaseValue(value);
        }
    }

    protected void syncState() {
        byte s = (byte) brain.state().ordinal();
        if (entityData.get(DATA_STATE) != s) {
            entityData.set(DATA_STATE, s);
        }
        int l = brain.awake() ? LIGHT_ALERT : standby ? LIGHT_STANDBY : LIGHT_OFF;
        if (entityData.get(DATA_LIGHT) != l) {
            entityData.set(DATA_LIGHT, (byte) l);
        }
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel sl) {
            if (isAlive()) {
                serverThink(sl);
            }
        } else {
            clientThink();
        }
        super.aiStep();
        if (!level().isClientSide && isAlive() && posted) {
            enforceLeash();
        }
    }

    private void serverThink(ServerLevel sl) {
        SentinelRules r = rules();
        if (!statsApplied) {
            statsApplied = true;
            applyStats(false);
            if (!posted) {
                post = blockPosition();
                posted = true;
                postYaw = getYRot();
            }
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (phraseCooldown > 0) {
            phraseCooldown--;
        }
        if (shield > 0 && --shield == 0) {
            setFlag(FLAG_SHIELD, false);
        }
        boolean check = (tickCount + getId()) % r.wakeInterval() == 0;
        if (check) {
            refreshStandby(sl);
        }
        if (stun > 0) {
            if (--stun == 0) {
                setFlag(FLAG_STUN, false);
            }
            getNavigation().stop();
            serverExtra(sl);
            return;
        }
        if (brain.dormant()) {
            if (check && canFight(sl)) {
                Player p = detect(sl, param("wake_radius", 6), param("wake_radius_sneaking", 3), true);
                if (p != null) {
                    wake(sl, p);
                }
            }
            if (brain.dormant()) {
                dormantTick(sl);
                serverExtra(sl);
                return;
            }
        }
        awakeTick(sl, r);
        serverExtra(sl);
    }

    private void awakeTick(ServerLevel sl, SentinelRules r) {
        boolean fight = canFight(sl);
        LivingEntity t = getTarget();
        if (t != null && !validTarget(t)) {
            setTarget(null);
            t = null;
        }
        if (t == null && fight && brain.state() != SentinelBrain.State.DORMANT && (tickCount + getId()) % 5 == 0) {
            t = detect(sl, param("awake_radius", 16), param("awake_radius_sneaking", 10), true);
            setTarget(t);
            lostSight = 0;
        }
        if (t != null && tickCount % 5 == 0) {
            if (getSensing().hasLineOfSight(t)) {
                lostSight = 0;
            } else if ((lostSight += 5) > r.commonTicks("lost_sight", 100)) {
                setTarget(null);
                t = null;
                lostSight = 0;
            }
        }
        double leash = r.leash();
        boolean beyond = distanceToAnchorSqr(position()) > leash * leash;
        SentinelBrain.Event e = brain.tick(r.alertTicks(), r.sleepAfter(), r.giveUp(), standby || !fight, t != null, isHome(), beyond);
        switch (e) {
            case ENGAGED -> onEngage(sl, t);
            case DISENGAGED, LEASH -> {
                setTarget(null);
                getNavigation().stop();
            }
            case SLEPT -> onSleep(sl);
            case GAVE_UP -> {
                teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
                onSleep(sl);
            }
            default -> {
            }
        }
        syncState();
        switch (brain.state()) {
            case ALERT -> alertTick(sl, getTarget());
            case COMBAT -> {
                LivingEntity target = getTarget();
                if (target != null) {
                    combatTick(sl, target);
                }
            }
            case RETURN -> returnTick(sl);
            default -> {
            }
        }
    }

    /** Laisse stricte : jamais plus loin que la laisse de son ancre (horizontalement). */
    private void enforceLeash() {
        Vec3 a = leashAnchor();
        double[] c = SentinelBrain.clamp(getX(), 0, getZ(), a.x, 0, a.z, rules().leash());
        if (c != null) {
            setPos(c[0], getY(), c[2]);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            getNavigation().stop();
        }
    }

    private void clientThink() {
        int st = stateId();
        if (!clientInit) {
            clientInit = true;
            fold = oFold = st == 0 ? 1 : 0;
            clientState = st;
        }
        if (st != 0 && clientState == 0) {
            clientWake = tickCount;
        }
        clientState = st;
        oFold = fold;
        float target = st == 0 ? 1 : 0;
        fold = target > fold ? Math.min(target, fold + 1 / 40F) : Math.max(target, fold - 1 / 30F);
        workTicks = flag(FLAG_WORK) ? workTicks + 1 : 0;
        if (flag(FLAG_STUN) && random.nextInt(3) == 0) {
            spark(ParticleTypes.ELECTRIC_SPARK, 0.6);
        }
        if (deathTime > 0) {
            spark(ParticleTypes.ELECTRIC_SPARK, 0.3);
            if (random.nextInt(2) == 0) {
                spark(ParticleTypes.SMOKE, 0.05);
            }
        }
    }

    private void spark(ParticleOptions type, double speed) {
        double w = getBbWidth() * 0.6;
        level().addParticle(type, getX() + (random.nextDouble() - 0.5) * w * 2, getY() + random.nextDouble() * getBbHeight(),
              getZ() + (random.nextDouble() - 0.5) * w * 2, (random.nextDouble() - 0.5) * speed, random.nextDouble() * speed,
              (random.nextDouble() - 0.5) * speed);
    }

    // ------------------------------------------------------------------ crochets des roles
    /** Endormie : rien (le Patrouilleur fait sa ronde). */
    protected void dormantTick(ServerLevel sl) {
    }

    /** Scene de reveil : immobile, regarde la cible. */
    protected void alertTick(ServerLevel sl, @Nullable LivingEntity target) {
        getNavigation().stop();
        if (target != null) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }

    /** Combat (par defaut : melee). */
    protected void combatTick(ServerLevel sl, LivingEntity target) {
        chase(target, rules().common("chase_speed", 1.0));
        strike(target, stats().ticks("attack_interval", 25));
    }

    /** Retour au poste. */
    protected void returnTick(ServerLevel sl) {
        if (!isHome()) {
            if (--repath <= 0 || getNavigation().isDone()) {
                repath = 20;
                getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, rules().common("return_speed", 0.8));
            }
        } else {
            getNavigation().stop();
            setYRot(Mth.approachDegrees(getYRot(), postYaw, 6));
            yBodyRot = getYRot();
        }
    }

    /** Chaque tick serveur, apres l'IA (barre de boss...). */
    protected void serverExtra(ServerLevel sl) {
    }

    protected void onWake(ServerLevel sl) {
        playSound(RpSounds.SERVO_WAKE.get(), 1.0F, wakePitch());
        say(sl, wakePhrase(), true);
    }

    protected void onEngage(ServerLevel sl, @Nullable LivingEntity target) {
        say(sl, random.nextInt(3) == 0 ? "denied" : "combat", false);
        lastSiteWake = tickCount;
        Sentinels.wakeSite(sl, site, blockPosition());
    }

    protected void onSleep(ServerLevel sl) {
        getNavigation().stop();
        setTarget(null);
        if (brain.fought() && !standby) {
            say(sl, "return", true);
        }
        setYRot(postYaw);
        yBodyRot = yHeadRot = postYaw;
    }

    protected String wakePhrase() {
        return "wake";
    }

    protected float wakePitch() {
        return 1.0F;
    }

    /** Degats apres les defenses propres au role (bouclier frontal, esquive) ; 0 = evite. */
    protected float absorb(DamageSource source, float amount) {
        return amount;
    }

    /** A son poste (le Patrouilleur : sur son anneau). */
    protected boolean isHome() {
        double h = rules().home();
        return distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) <= h * h + 0.5;
    }

    /** Centre de la laisse (le poste ; le centre de l'anneau pour le Patrouilleur). */
    protected Vec3 leashAnchor() {
        return Vec3.atBottomCenterOf(post);
    }

    protected double distanceToAnchorSqr(Vec3 p) {
        Vec3 a = leashAnchor();
        double dx = p.x - a.x, dz = p.z - a.z;
        return dx * dx + dz * dz;
    }

    /** Endormie et immobile (vrai sauf le Patrouilleur, qui fait sa ronde). */
    protected boolean dormantImmobile() {
        return true;
    }

    // ------------------------------------------------------------------ eveil et cibles
    /** Eveil demande de l'exterieur (bloc delabre casse, alarme, combat sur le site). */
    public void wakeUp(@Nullable Player cause) {
        if (level() instanceof ServerLevel sl && isAlive()) {
            refreshStandby(sl);
            wake(sl, cause);
        }
    }

    /** Veille posee sur le site : la Sentinelle lache sa cible et se rendort a son poste. */
    public void standbyNow() {
        standby = true;
        setTarget(null);
        getNavigation().stop();
        syncState();
    }

    protected boolean wake(ServerLevel sl, @Nullable Player cause) {
        if (!canFight(sl)) {
            return false;
        }
        if (brain.wake(standby)) {
            getNavigation().stop();
            if (cause != null && validTarget(cause)) {
                setTarget(cause);
            }
            syncState();
            onWake(sl);
            return true;
        }
        if (brain.awake() && cause != null && getTarget() == null && validTarget(cause)) {
            setTarget(cause);
        }
        return false;
    }

    private void refreshStandby(ServerLevel sl) {
        standby = site != 0 && RuinApi.standby(sl, site);
        syncState();
    }

    /** Peut combattre : pas en Veille, pas en paisible, Sentinelles actives dans la configuration. */
    protected boolean canFight(ServerLevel sl) {
        return !standby && sl.getDifficulty() != Difficulty.PEACEFUL && PiCommonConfig.get(RpConfig.SENTINELS, true);
    }

    protected static boolean targetable(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    protected boolean validTarget(LivingEntity t) {
        if (!(t instanceof Player p) || !targetable(p) || p.level() != level() || standby) {
            return false;
        }
        double leash = rules().leash();
        double range = Math.max(param("awake_radius", 16), getAttributeValue(Attributes.FOLLOW_RANGE)) * 1.5;
        return distanceToAnchorSqr(p.position()) <= leash * leash && distanceToSqr(p) <= range * range;
    }

    /** Joueur repere le plus proche (rayon reduit en s'accroupissant et par la discretion du joueur), dans la laisse. */
    @Nullable
    protected Player detect(ServerLevel sl, double radius, double sneakRadius, boolean sight) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        double leash = rules().leash();
        for (Player p : sl.players()) {
            if (!targetable(p)) {
                continue;
            }
            double d = distanceToSqr(p);
            double max = Math.max(radius, sneakRadius);
            if (d > max * max || d >= bestD) {
                continue;
            }
            double rad = (p.isCrouching() ? sneakRadius : radius) * Math.max(0.1, p.getVisibilityPercent(this));
            if (d > rad * rad || distanceToAnchorSqr(p.position()) > leash * leash) {
                continue;
            }
            if (sight && d > 4 && !hasLineOfSight(p)) {
                continue;
            }
            best = p;
            bestD = d;
        }
        return best;
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        return target instanceof Player && super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(@NotNull Entity other) {
        return other instanceof SentinelEntity || super.isAlliedTo(other);
    }

    // ------------------------------------------------------------------ combat
    protected void chase(LivingEntity t, double speed) {
        getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (--repath <= 0 || getNavigation().isDone()) {
            repath = 8 + random.nextInt(5);
            getNavigation().moveTo(t, speed);
        }
    }

    protected boolean strike(LivingEntity t, int interval) {
        if (attackCooldown > 0 || !isWithinMeleeAttackRange(t) || !getSensing().hasLineOfSight(t)) {
            return false;
        }
        attackCooldown = interval;
        swing(InteractionHand.MAIN_HAND);
        playSound(RpSounds.PISTON.get(), 1.0F, 0.8F);
        doHurtTarget(t);
        return true;
    }

    /** Tir instantane (trainee visible) : vrai si la cible est touchee, faux si un bloc arrete le tir. */
    protected boolean shoot(ServerLevel sl, LivingEntity t, Vec3 from, float damage, @Nullable DustParticleOptions trail) {
        Vec3 to = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ());
        BlockHitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blocked = hit.getType() != HitResult.Type.MISS;
        Vec3 end = blocked ? hit.getLocation() : to;
        if (trail != null) {
            Vec3 d = end.subtract(from);
            int n = Math.min(60, (int) (d.length() / 0.35));
            for (int i = 1; i <= n; i++) {
                Vec3 p = from.add(d.scale(i / (double) n));
                sl.sendParticles(trail, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.2);
        if (!blocked) {
            t.hurt(damageSources().mobAttack(this), damage);
        }
        return !blocked;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source) || !isAlive()) {
            return super.hurt(source, amount);
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING) || source.getDirectEntity() instanceof EnergyBoltEntity) {
            stun(rules().stunTicks());
        }
        if (shield > 0) {
            amount *= 1 - shieldReduction;
        }
        amount = absorb(source, amount);
        if (amount <= 0) {
            return false;
        }
        if (source.getEntity() instanceof Player p && level() instanceof ServerLevel sl) {
            if (standby && site != 0 && !p.isCreative() && !p.isSpectator()) {
                RuinSites.Site s = RuinApi.site(sl, site);
                if (s != null) {
                    Sentinels.breakStandby(sl, s, blockPosition());
                }
            }
            if (brain.dormant()) {
                wakeUp(p);
            } else if (getTarget() == null && validTarget(p)) {
                setTarget(p);
            }
            if (canFight(sl) && brain.awake() && tickCount - lastSiteWake > 40) {
                lastSiteWake = tickCount;
                Sentinels.wakeSite(sl, site, blockPosition());
            }
        }
        return super.hurt(source, amount);
    }

    /** Choc electrique : etourdie (immobile, voyants qui grillent). */
    public void stun(int ticks) {
        if (ticks <= 0) {
            return;
        }
        if (stun == 0) {
            playSound(RpSounds.SHUTDOWN.get(), 0.7F, 1.7F);
        }
        stun = Math.max(stun, ticks);
        setFlag(FLAG_STUN, true);
        getNavigation().stop();
    }

    public boolean stunned() {
        return level().isClientSide ? flag(FLAG_STUN) : stun > 0;
    }

    /** Bulle de bouclier du Reparateur : degats reduits pendant {@code ticks}. */
    public void giveShield(int ticks, float reduction) {
        shield = Math.max(shield, ticks);
        shieldReduction = Mth.clamp(reduction, 0, 0.95F);
        setFlag(FLAG_SHIELD, true);
    }

    /** Phrase de procedure en barre d'action aux joueurs proches (cle {@code sentinel.pioneers.say.<cle>}). */
    protected void say(ServerLevel sl, String key, boolean force) {
        if (!force && phraseCooldown > 0) {
            return;
        }
        phraseCooldown = rules().commonTicks("phrase_cooldown", 60);
        double rad = rules().common("phrase_radius", 16);
        Component c = Component.translatable("sentinel.pioneers.say." + key).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) <= rad * rad) {
                p.displayClientMessage(c, true);
            }
        }
    }

    @Override
    public void die(@NotNull DamageSource source) {
        boolean was = dead;
        super.die(source);
        if (!was && dead && level() instanceof ServerLevel sl) {
            ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp : lastHurtByPlayer instanceof ServerPlayer sp2 ? sp2 : null;
            if (killer != null) {
                MinecraftForge.EVENT_BUS.post(new RuinEvents.SentinelKilled(killer, site, tier(), blockPosition(), sl, role().id()));
            }
            say(sl, "death", true);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.5, getZ(), 24, getBbWidth() * 0.4,
                  getBbHeight() * 0.3, getBbWidth() * 0.4, 0.3);
        }
    }

    // ------------------------------------------------------------------ regles d'entite
    @Override
    protected boolean isImmobile() {
        if (super.isImmobile()) {
            return true;
        }
        if (level().isClientSide) {
            return flag(FLAG_STUN) || asleep() && dormantImmobile();
        }
        return stun > 0 || brain.dormant() && dormantImmobile();
    }

    @Override
    public void travel(@NotNull Vec3 input) {
        //Endormie au sol : aucun deplacement a calculer (une passe tous les 10 ticks pour la gravite)
        if (!level().isClientSide && brain.dormant() && dormantImmobile() && onGround() && getDeltaMovement().horizontalDistanceSqr() < 1.0E-7
            && (tickCount + getId()) % 10 != 0) {
            return;
        }
        super.travel(input);
    }

    @Override
    protected void pushEntities() {
        if (!asleep()) {
            super.pushEntities();
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean canBeAffected(@NotNull MobEffectInstance effect) {
        return effect.getEffect() != MobEffects.POISON && effect.getEffect() != MobEffects.WITHER && effect.getEffect() != MobEffects.REGENERATION
               && effect.getEffect() != MobEffects.HUNGER && super.canBeAffected(effect);
    }

    @Override
    protected boolean shouldDropLoot() {
        return !summoned && super.shouldDropLoot();
    }

    @Override
    public int getExperienceReward() {
        return summoned ? 0 : super.getExperienceReward();
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RpSounds.SHUTDOWN.get();
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        playSound(RpSounds.METAL_STEP.get(), 0.45F, 1.15F - getBbHeight() * 0.12F);
    }

    // ------------------------------------------------------------------ sauvegarde
    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SentinelTier", tier());
        tag.putLong("SentinelSite", site);
        if (posted) {
            tag.putLong("SentinelPost", post.asLong());
        }
        tag.putFloat("SentinelYaw", postYaw);
        tag.putByte("SentinelState", (byte) brain.state().ordinal());
        tag.putBoolean("SentinelSummoned", summoned);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_TIER, (byte) role().clampTier(tag.contains("SentinelTier") ? tag.getInt("SentinelTier") : 1));
        site = tag.getLong("SentinelSite");
        if (tag.contains("SentinelPost")) {
            post = BlockPos.of(tag.getLong("SentinelPost"));
            posted = true;
        }
        postYaw = tag.getFloat("SentinelYaw");
        summoned = tag.getBoolean("SentinelSummoned");
        brain.restore(SentinelBrain.State.of(tag.getByte("SentinelState")));
        statsApplied = false;
        syncState();
    }
}
