package fr.pioneers.farm.fish;

import fr.pioneers.farm.PiFishing;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Bouchon des cannes du mod et combat a tension du fil. Etats : lance, flotte (attente d'une touche), touche (fenetre pour ferrer), combat,
 * fini. Pendant le combat (serveur) : clic droit maintenu = mouliner (la tension monte, la distance baisse), relache = le poisson prend du fil ;
 * fil casse si la tension reste au rouge, poisson decroche si le fil reste mou, pris quand il arrive au bord. Chaque espece a sa force et son
 * comportement (calme, rushs, sauts, imprevisible, coureur) ; la canne a sa resistance et sa vitesse de moulinet. Tension et distance sont
 * synchronisees pour la jauge (client/farm/fish/FishingHud) et le fil.
 * <p>
 * 1.6 (Guillaume : « plus on maintient le clic plus la canne va loin » et « rendre plus fluide l'animation quand on lance ») : le lancer est
 * arme ({@link PiRodItem}) ; sa force ({@link #power}) fixe la vitesse de depart, bornee par canne ({@link #SPEED}), et la longueur du fil
 * ({@link #LINE}) borne la portee. Le vol suit le meme calcul des deux cotes ({@link #flight}) : le client le simule lui-meme (aucun a-coup
 * reseau) et ne fait que corriger un ecart avec le serveur ; dans les autres etats il interpole les positions recues en douceur.
 */
public class PiBobber extends Projectile {

    public static final int FLYING = 0, FLOATING = 1, BITE = 2, FIGHT = 3;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TENSION = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DISTANCE = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> JUMP = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.INT);
    /** 1.0.2-beta : zone verte de la tension pendant le combat (bornes 0..1), propre au poisson ferre ({@link #greenZone}). */
    private static final EntityDataAccessor<Float> GREEN_LO = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GREEN_HI = SynchedEntityData.defineId(PiBobber.class, EntityDataSerializers.FLOAT);

    // ------------------------------------------------------------------ lancer (1.6)

    /** Ticks pour armer un lancer a pleine force. */
    public static final int CHARGE_TICKS = 20;
    /** Longueur du fil (portee maximale, blocs) : bambou, bronze, acier. */
    public static final float[] LINE = {10F, 15F, 22F};
    /** Vitesse de depart a pleine force (blocs par tick), et vitesse d'un lancer tout juste arme. */
    public static final float[] SPEED = {0.75F, 0.95F, 1.2F};
    public static final float MIN_SPEED = 0.28F;
    /** Physique du vol (identique serveur, client et prevision de la jauge). */
    public static final double GRAVITY = 0.05, DRAG = 0.99;

    /** Bouchons en jeu par joueur (serveur et client). */
    private static final Map<UUID, PiBobber> SERVER = new HashMap<>();
    private static final Map<Integer, PiBobber> CLIENT = new HashMap<>();
    /** Client : dernier lancer de chaque joueur (temps de jeu, part d'armement), pour le geste du bras et le fil. */
    private static final Map<Integer, Cast> CASTS = new HashMap<>();

    private record Cast(long time, float windUp) {
    }

    private int tier;
    private boolean bait;
    private int wait, window, life, species = -1, fightTime, red, slack, surge, phase;
    private float pull = 0.4F, angle;
    /** 1.0.2-beta : taille de l'individu ferre (0,8 a 1,2 de l'espece), tiree a la touche : elle fixe sa difficulte et sa taille prise. */
    private float specimen = 1;
    /** Point de depart du lancer : le fil se deroule a partir d'ici. */
    private Vec3 origin;

    // client : vol simule, positions recentes, interpolation
    private boolean landed;
    private final Vec3[] history = new Vec3[8];
    private int historyAt;
    private double lerpX, lerpY, lerpZ;
    private int lerpSteps;
    /** Client : affaissement affiche du fil (lisse d'une image a l'autre par le rendu). */
    public float shownSag = -1;

    public PiBobber(EntityType<? extends PiBobber> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    /** Force d'un lancer arme pendant {@code ticks} (courbe de l'arc, de 0,15 a 1). */
    public static float power(float ticks) {
        float f = Mth.clamp(ticks / CHARGE_TICKS, 0, 1);
        return Math.max(0.15F, (f * f + 2 * f) / 3);
    }

    /** Part d'armement du geste (0..1, courbe adoucie) apres {@code ticks} : pose du bras, et point de depart du fouet au lacher. */
    public static float windUp(float ticks) {
        float f = Mth.clamp(ticks / CHARGE_TICKS, 0, 1);
        return f * f * (3 - 2 * f);
    }

    /** Vitesse de depart d'un lancer de cette force avec cette canne. */
    public static float speed(int tier, float power) {
        return MIN_SPEED + (SPEED[Mth.clamp(tier, 0, 2)] - MIN_SPEED) * Mth.clamp(power, 0, 1);
    }

    /** Main qui tient la canne (la principale si elle en tient une). */
    public static InteractionHand rodHand(Player player) {
        return player.getMainHandItem().getItem() instanceof PiRodItem || !(player.getOffhandItem().getItem() instanceof PiRodItem)
               ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    /** Cote de la canne : 1 a droite, -1 a gauche. */
    public static int rodSide(Player player) {
        HumanoidArm arm = rodHand(player) == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    /** Point de depart du bouchon : au bout de la canne, au-dessus de l'epaule du cote de la canne. */
    public static Vec3 start(Player player) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        int side = rodSide(player);
        Vec3 p = player.getEyePosition().add(fx * 0.5 - fz * 0.3 * side, 0.3, fz * 0.5 + fx * 0.3 * side);
        return player.level().noCollision(player, PiFishing.BOBBER.get().getDimensions().makeBoundingBox(p)) ? p : player.getEyePosition();
    }

    /**
     * Vitesse de depart : dans la direction du regard, toujours un peu vers le haut (30 degres a l'horizontale, entre 8 et 60 selon que l'on
     * vise plus haut ou plus bas), d'autant plus vite que le lancer est arme.
     */
    public static Vec3 launch(Player player, int tier, float power) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float elevation = Mth.clamp(30 - 0.35F * player.getXRot(), 8, 60) * Mth.DEG_TO_RAD;
        double v = speed(tier, power);
        return new Vec3(-Mth.sin(yaw) * Mth.cos(elevation) * v, Mth.sin(elevation) * v, Mth.cos(yaw) * Mth.cos(elevation) * v);
    }

    /** Un tick de vol : trainee, pesanteur, puis le fil arrive au bout (plus rien ne l'eloigne du point de depart). */
    public static Vec3 flight(Vec3 position, Vec3 velocity, Vec3 origin, float line) {
        Vec3 v = velocity.scale(DRAG).add(0, -GRAVITY, 0);
        double dx = position.x - origin.x, dz = position.z - origin.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d >= line && d > 1e-4) {
            double ux = dx / d, uz = dz / d, out = v.x * ux + v.z * uz;
            if (out > 0) {
                v = new Vec3((v.x - ux * out) * 0.6, v.y, (v.z - uz * out) * 0.6);
            }
        }
        return v;
    }

    public static PiBobber cast(Player player, int tier, boolean bait, float power) {
        PiBobber b = new PiBobber(PiFishing.BOBBER.get(), player.level());
        b.setOwner(player);
        b.entityData.set(OWNER, player.getId());
        b.entityData.set(TIER, tier);
        b.tier = tier;
        b.bait = bait;
        Vec3 p = start(player);
        b.moveTo(p.x, p.y, p.z, player.getYRot(), player.getXRot());
        b.origin = p;
        b.setDeltaMovement(launch(player, tier, power));
        SERVER.put(player.getUUID(), b);
        return b;
    }

    /** Client : un joueur vient de lancer, arme a {@code windUp} (geste du bras ; un second appel rapproche n'est pas compte). */
    public static void markCast(Player player, float windUp) {
        long now = player.level().getGameTime();
        Cast last = CASTS.get(player.getId());
        if (last == null || now - last.time() > 15 || now < last.time()) {
            CASTS.put(player.getId(), new Cast(now, windUp));
        }
    }

    /** Client : lancer recent de ce joueur {temps depuis le lacher en ticks, part d'armement}, ou null. */
    @Nullable
    public static float[] recentCast(Player player, float partial) {
        Cast c = CASTS.get(player.getId());
        if (c == null) {
            return null;
        }
        float age = player.level().getGameTime() - c.time() + partial;
        if (age < 0 || age > 20) {
            return null;
        }
        return new float[]{age, c.windUp()};
    }

    @Nullable
    public static PiBobber of(Player player) {
        if (player.level().isClientSide) {
            PiBobber b = CLIENT.get(player.getId());
            return b != null && b.isAlive() ? b : null;
        }
        PiBobber b = SERVER.get(player.getUUID());
        return b != null && b.isAlive() ? b : null;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, FLYING);
        entityData.define(OWNER, -1);
        entityData.define(TENSION, 0F);
        entityData.define(DISTANCE, 0F);
        entityData.define(JUMP, 0);
        entityData.define(TIER, 0);
        entityData.define(GREEN_LO, GREEN_CENTER - 0.28F);
        entityData.define(GREEN_HI, GREEN_CENTER + 0.28F);
    }

    public int state() {
        return entityData.get(STATE);
    }

    public float tension() {
        return entityData.get(TENSION);
    }

    public float distance() {
        return entityData.get(DISTANCE);
    }

    /** 1.0.2-beta : bornes de la zone verte de la tension (0..1) du poisson en combat. */
    public float greenLow() {
        return entityData.get(GREEN_LO);
    }

    public float greenHigh() {
        return entityData.get(GREEN_HI);
    }

    /** Ticks restants d'un saut du poisson (animation). */
    public int jump() {
        return entityData.get(JUMP);
    }

    public int rodTier() {
        return Mth.clamp(entityData.get(TIER), 0, 2);
    }

    @Nullable
    public Player player() {
        Entity e = level().getEntity(entityData.get(OWNER));
        return e instanceof Player p ? p : null;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level().isClientSide) {
            origin = position();
            int owner = getOwner() != null ? getOwner().getId() : entityData.get(OWNER);
            CLIENT.put(owner, this);
            if (getOwner() instanceof Player p) {
                markCast(p, 0.8F);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (OWNER.equals(key) && level().isClientSide) {
            CLIENT.put(entityData.get(OWNER), this);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level().isClientSide) {
            CLIENT.remove(entityData.get(OWNER), this);
        } else if (getOwner() instanceof Player p) {
            SERVER.remove(p.getUUID(), this);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 4096;
    }

    // ------------------------------------------------------------------ vie

    private boolean inWater() {
        return isInWater() || level().getFluidState(blockPosition()).is(FluidTags.WATER);
    }

    /** Vol libre (serveur et client) : meme calcul des deux cotes. */
    private void fly() {
        Vec3 v = flight(position(), getDeltaMovement(), origin == null ? position() : origin, LINE[rodTier()]);
        if (onGround()) {
            v = v.multiply(0.55, 1, 0.55);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        Player owner = getOwner() instanceof Player p ? p : null;
        if (owner == null || !owner.isAlive() || owner.level() != level() || !(owner.getMainHandItem().getItem() instanceof PiRodItem)
                                                                                && !(owner.getOffhandItem().getItem() instanceof PiRodItem) || distanceToSqr(owner) > 1600) {
            discard();
            return;
        }
        int st = state();
        if (st == FLYING) {
            if (inWater()) {
                splashDown();
            } else {
                fly();
                if (inWater()) {
                    splashDown();
                } else if (onGround() && ++life > 60) {
                    discard();
                }
            }
            return;
        }
        boolean water = inWater();
        if (st == FLOATING || st == BITE) {
            floatOnSurface(st == BITE ? -0.18 : 0);
            if (!water && !onGround()) {
                setDeltaMovement(getDeltaMovement().add(0, -0.03, 0));
            }
            if (st == FLOATING && water && --wait <= 0) {
                bite();
            } else if (st == BITE && --window <= 0) {
                entityData.set(STATE, FLOATING);
                wait = newWait();
                owner.displayClientMessage(Component.translatable("farm.pioneers.fishing.missed").withStyle(ChatFormatting.GRAY), true);
            } else if (st == BITE && random.nextInt(3) == 0) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 3, 0.1, 0, 0.1, 0.05);
            }
        } else if (st == FIGHT) {
            fight(owner);
            if (!isAlive()) {
                return;
            }
        }
        move(MoverType.SELF, getDeltaMovement());
        if (st != FIGHT) {
            setDeltaMovement(getDeltaMovement().multiply(0.9, 0.9, 0.9));
        }
    }

    /** Le bouchon touche l'eau : il flotte, eclaboussure proportionnee a la vitesse. */
    private void splashDown() {
        double speed = getDeltaMovement().length();
        entityData.set(STATE, FLOATING);
        setDeltaMovement(getDeltaMovement().multiply(0.3, 0.2, 0.3));
        wait = newWait();
        level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, (float) Mth.clamp(0.15 + speed * 0.3, 0.15, 0.6),
              1.3F - (float) Math.min(0.4, speed * 0.3));
        ((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 4 + (int) (speed * 10), 0.15, 0.05, 0.15, 0.1);
    }

    // ------------------------------------------------------------------ client : vol simule et interpolation

    private void clientTick() {
        if (origin == null) {
            origin = position();
        }
        if (state() == FLYING && !landed) {
            fly();
            if (inWater()) {
                //En attendant l'etat du serveur : le bouchon s'arrete a la surface
                landed = true;
                setDeltaMovement(getDeltaMovement().multiply(0.3, 0.2, 0.3));
            }
            history[historyAt] = position();
            historyAt = (historyAt + 1) % history.length;
            return;
        }
        if (lerpSteps > 0) {
            setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps, getZ() + (lerpZ - getZ()) / lerpSteps);
            lerpSteps--;
        }
    }

    /**
     * Position envoyee par le serveur. En vol simule : on compare a la position simulee la plus proche des derniers ticks (le paquet peut
     * avoir un tick d'avance ou de retard) et on ne corrige qu'un vrai ecart, en douceur. Sinon : interpolation sur 3 ticks au moins.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        setRot(yRot, xRot);
        if (state() == FLYING && !landed) {
            Vec3 target = new Vec3(x, y, z), best = null;
            double bestD = Double.MAX_VALUE;
            for (Vec3 h : history) {
                if (h != null && h.distanceToSqr(target) < bestD) {
                    bestD = h.distanceToSqr(target);
                    best = h;
                }
            }
            if (best == null || bestD > 16) {
                setPos(x, y, z);
                Arrays.fill(history, null);
            } else if (bestD > 0.01) {
                Vec3 err = target.subtract(best).scale(0.3);
                setPos(getX() + err.x, getY() + err.y, getZ() + err.z);
            }
            lerpSteps = 0;
            return;
        }
        lerpSteps = Math.max(3, steps);
    }

    /** En vol simule, la vitesse est celle du client (le serveur n'envoie que la vitesse de depart utile). */
    @Override
    public void lerpMotion(double x, double y, double z) {
        if (level().isClientSide && tickCount > 0 && state() == FLYING && !landed) {
            return;
        }
        super.lerpMotion(x, y, z);
    }

    // ------------------------------------------------------------------ attente et touche

    private void floatOnSurface(double dip) {
        BlockPos p = blockPosition();
        if (!level().getFluidState(p).is(FluidTags.WATER) && level().getFluidState(p.below()).is(FluidTags.WATER)) {
            p = p.below();
        }
        int top = p.getY();
        while (level().getFluidState(new BlockPos(p.getX(), top + 1, p.getZ())).is(FluidTags.WATER)) {
            top++;
        }
        double surface = top + 0.88 - 0.12 + dip + Mth.sin(tickCount * 0.15F) * 0.02;
        if (level().getFluidState(p).is(FluidTags.WATER)) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.85, (surface - getY()) * 0.25, v.z * 0.85);
        }
    }

    private int newWait() {
        int w = 100 + random.nextInt(400);
        w = Math.round(w * (bait ? 0.55F : 1F) * (1F - 0.12F * tier));
        //1.9 (MAJ RP) : skill Peche, touches plus rapides
        if (getOwner() instanceof Player p) {
            w = (int) Math.round(w * fr.pioneers.core.profile.PioneerApi.factor(p, "fishing.bite_delay"));
        }
        //Eau trop petite : touches plus rares
        int n = 0;
        BlockPos c = blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 0, 2))) {
            if (level().getFluidState(q).is(FluidTags.WATER)) {
                n++;
            }
        }
        return n < 12 ? w * 3 : w;
    }

    private void bite() {
        species = FishSpawns.pick(level(), blockPosition(), random, bait);
        if (species < 0) {
            species = FishSpawns.pickIn(FishSpawns.FRESH_TEMPERATE, random, bait);
        }
        entityData.set(STATE, BITE);
        window = 22 + 5 * tier;
        level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F + (random.nextFloat() - 0.5F) * 0.4F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 12, 0.15, 0.1, 0.15, 0.1);
        if (getOwner() instanceof Player p) {
            p.displayClientMessage(Component.translatable("farm.pioneers.fishing.bite").withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Clic pendant la touche : le poisson est ferre. */
    public boolean strike(Player player) {
        if (state() != BITE) {
            return false;
        }
        entityData.set(STATE, FIGHT);
        entityData.set(TENSION, 0.35F);
        //1.0.2-beta : l'individu (sa taille) est tire au ferrage ; sa zone verte en decoule (plus difficile = plus etroite)
        specimen = 0.8F + random.nextFloat() * 0.4F;
        float[] zone = greenZone(GeneratedFish.SPECIES[Math.max(0, species)], specimen, tier);
        entityData.set(GREEN_LO, zone[0]);
        entityData.set(GREEN_HI, zone[1]);
        Vec3 d = position().subtract(player.position());
        angle = (float) Math.atan2(d.z, d.x);
        entityData.set(DISTANCE, (float) Math.max(3, Math.sqrt(d.x * d.x + d.z * d.z)));
        fightTime = red = slack = surge = phase = 0;
        level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1F, 0.6F);
        return true;
    }

    public void retrieve() {
        level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
        discard();
    }

    // ------------------------------------------------------------------ combat

    private static final float[] TOLERANCE = {0.8F, 1.0F, 1.25F};
    private static final float[] REEL = {0.10F, 0.13F, 0.17F};

    // ------------------------------------------------------------------ 1.0.2-beta : zone verte selon le poisson
    /**
     * Guillaume : « les jauges de tension du mini-jeu varient selon la taille et la difficulte du poisson ; plus il est difficile a attraper,
     * plus la zone verte est petite ». La zone verte est centree sur {@link #GREEN_CENTER} ; sa largeur va de {@link #GREEN_EASY} (poisson
     * facile) a {@link #GREEN_HARD} (le plus difficile), elargie par une meilleure canne ({@link #GREEN_ROD}). En combat, moulinet dans le vert
     * : le poisson approche a plein regime ; hors du vert (fil mou ou trop tendu), il n'approche qu'a {@link #OFF_GREEN_REEL} du regime.
     */
    public static final float GREEN_CENTER = 0.45F, GREEN_EASY = 0.56F, GREEN_HARD = 0.16F, OFF_GREEN_REEL = 0.35F;
    public static final float[] GREEN_ROD = {0.9F, 1.0F, 1.12F};
    /** Poids de chaque comportement (calme, rushs, sauts, imprevisible, coureur) dans la difficulte. */
    public static final float[] BEHAVIOUR_DIFFICULTY = {0F, 0.6F, 0.4F, 1F, 0.7F};

    /**
     * Difficulte (0 facile .. 1 la plus dure) d'un poisson : force de l'espece (moitie), taille de l'espece, taille de l'individu
     * ({@code specimen} 0,8 a 1,2), comportement, legendaire.
     */
    public static float difficulty(FishDef def, float specimen) {
        float strength = (def.strength() - 1) / 9F;
        float size = Mth.clamp((def.size() - 0.4F) / 1.6F, 0, 1);
        float individual = Mth.clamp((specimen - 0.8F) / 0.4F, 0, 1);
        float behaviour = BEHAVIOUR_DIFFICULTY[Mth.clamp(def.behaviour(), 0, BEHAVIOUR_DIFFICULTY.length - 1)];
        return Mth.clamp(0.5F * strength + 0.2F * size + 0.15F * individual + 0.15F * behaviour + (def.legendary() ? 0.1F : 0), 0, 1);
    }

    /** Zone verte {bas, haut} de la tension pour ce poisson, cet individu et cette canne (memes chiffres sur la planche Python). */
    public static float[] greenZone(FishDef def, float specimen, int rodTier) {
        float w = (GREEN_EASY + (GREEN_HARD - GREEN_EASY) * difficulty(def, specimen)) * GREEN_ROD[Mth.clamp(rodTier, 0, 2)];
        w = Mth.clamp(w, 0.12F, 0.62F);
        return new float[]{GREEN_CENTER - w / 2, GREEN_CENTER + w / 2};
    }

    private void fight(Player owner) {
        FishDef def = GeneratedFish.SPECIES[Math.max(0, species)];
        fightTime++;
        //Force du poisson a cet instant selon son comportement
        switch (def.behaviour()) {
            case 1 -> {                                          //rushs
                if (surge > 0) {
                    surge--;
                    pull = 1.0F;
                } else {
                    pull = 0.3F;
                    if (random.nextInt(70) == 0) {
                        surge = 18 + random.nextInt(10);
                        splash(4);
                    }
                }
            }
            case 2 -> {                                          //sauts
                int j = entityData.get(JUMP);
                if (j > 0) {
                    entityData.set(JUMP, j - 1);
                    pull = 0.15F;
                } else {
                    pull = 0.45F;
                    if (random.nextInt(80) == 0) {
                        entityData.set(JUMP, 14);
                        splash(10);
                        level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 1F, 0.8F);
                    }
                }
            }
            case 3 -> {                                          //imprevisible
                if (fightTime % 5 == 0) {
                    pull = Mth.clamp(pull + (random.nextFloat() - 0.5F) * 0.5F, 0.05F, 1.0F);
                }
            }
            case 4 -> pull = (fightTime / 40) % 2 == 0 ? 0.8F : 0.3F;            //coureur
            default -> pull = 0.45F + 0.1F * Mth.sin(fightTime * 0.1F);        //calme
        }
        float force = pull * (0.4F + def.strength() * 0.06F);
        boolean reeling = owner.isUsingItem() && owner.getUseItem().getItem() instanceof PiRodItem;
        float t = tension(), dist = distance();
        float tol = TOLERANCE[Mth.clamp(tier, 0, 2)];
        if (reeling) {
            //1.0.2-beta : dans la zone verte, le moulinet ramene a plein regime ; hors du vert (fil mou ou trop tendu), il patine
            boolean green = t >= greenLow() && t <= greenHigh();
            t += (0.012F + 0.03F * force + (jump() > 0 ? 0.04F : 0)) / tol;
            dist -= REEL[Mth.clamp(tier, 0, 2)] * (1.1F - force * 0.7F) * (green ? 1 : OFF_GREEN_REEL)
                    * (float) fr.pioneers.core.profile.PioneerApi.factor(owner, "fishing.reel_speed");
            if (fightTime % 4 == 0) {
                level().playSound(null, owner.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.25F, 1.6F + t * 0.4F);
            }
        } else {
            t -= 0.035F;
            dist += force * 0.06F;
        }
        t = Mth.clamp(t, 0, 1);
        red = t >= 0.97F ? red + 1 : Math.max(0, red - 2);
        slack = t <= 0.05F ? slack + 1 : 0;
        entityData.set(TENSION, t);
        entityData.set(DISTANCE, dist);
        if (red > 18) {
            end(owner, "farm.pioneers.fishing.snap", 5);
            level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
            return;
        }
        if (slack > 70 || dist > 40) {
            end(owner, "farm.pioneers.fishing.escaped", 0);
            return;
        }
        if (dist <= 1.3F) {
            land(owner, def);
            return;
        }
        //Le poisson tire le bouchon : a la distance voulue, en se deplacant de cote
        if (def.behaviour() == 3 || def.behaviour() == 4) {
            angle += Mth.sin(fightTime * 0.05F + phase) * 0.012F * (1 + force);
        } else {
            angle += Mth.sin(fightTime * 0.03F) * 0.004F;
        }
        Vec3 target = new Vec3(owner.getX() + Math.cos(angle) * dist, getY(), owner.getZ() + Math.sin(angle) * dist);
        Vec3 v = target.subtract(position()).scale(0.3);
        floatOnSurface(jump() > 0 ? 0.6 * Mth.sin((14 - jump()) / 14F * Mth.PI) : -0.1);
        setDeltaMovement(v.x, getDeltaMovement().y, v.z);
        if (fightTime % 3 == 0) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 2 + Math.round(force * 4), 0.15, 0.02, 0.15, 0.08);
        }
    }

    private void splash(int n) {
        ((ServerLevel) level()).sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), n * 3, 0.3, 0.1, 0.3, 0.15);
    }

    private void end(Player owner, String message, int damage) {
        owner.displayClientMessage(Component.translatable(message).withStyle(ChatFormatting.RED), true);
        damageRod(owner, damage);
        discard();
    }

    private void damageRod(Player owner, int damage) {
        if (damage <= 0) {
            return;
        }
        ItemStack rod = owner.getMainHandItem().getItem() instanceof PiRodItem ? owner.getMainHandItem() : owner.getOffhandItem();
        rod.hurtAndBreak(damage, owner, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
    }

    private void land(Player owner, FishDef def) {
        //1.0.2-beta : la taille est celle de l'individu ferre (tiree au ferrage, elle a fixe sa zone verte)
        float ind = specimen;
        int cm = Math.round(def.cmMin() + (def.cmMax() - def.cmMin()) * (ind - 0.8F) / 0.4F);
        ItemStack fish = FishItems.of(species, cm);
        //1.0.5-beta (lot 31, retour 42) : la prise va DIRECTEMENT dans un panier de peche de l'inventaire (son discret) ; sinon elle vole
        //jusqu'au joueur comme avant (et le ramassage la range encore dans un panier qui a de la place)
        ItemStack rest = fr.pioneers.building.home.bag.BagEvents.deliver(owner, fish);
        if (!rest.isEmpty()) {
            ItemEntity it = new ItemEntity(level(), getX(), getY() + 0.3, getZ(), rest);
            double dx = owner.getX() - getX(), dy = owner.getY() - getY(), dz = owner.getZ() - getZ();
            it.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level().addFreshEntity(it);
        }
        level().addFreshEntity(new ExperienceOrb(level(), owner.getX(), owner.getY() + 0.5, owner.getZ() + 0.5, 1 + random.nextInt(3) + def.strength() / 2));
        owner.awardStat(Stats.FISH_CAUGHT, 1);
        if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
            fr.pioneers.core.profile.PioneerApi.gain(sp, "fishing.catch", 1 + (def.legendary() ? 4 : def.strength() / 6.0), null);
        }
        splash(8);
        Component name = Component.translatable("farm.pioneers.fish." + def.id());
        if (def.legendary()) {
            Component msg = Component.translatable("farm.pioneers.fish.legend", name, cm).withStyle(ChatFormatting.GOLD);
            for (Player p : level().players()) {
                p.sendSystemMessage(owner.getDisplayName().copy().append(" : ").append(msg));
            }
        } else {
            owner.displayClientMessage(Component.translatable("farm.pioneers.fish.caught", name, cm).withStyle(ChatFormatting.AQUA), true);
        }
        if ("electric".equals(def.prop())) {
            owner.hurt(level().damageSources().magic(), 3);
            ((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.getX(), owner.getY() + 1, owner.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
            owner.displayClientMessage(Component.translatable("farm.pioneers.fish.shock").withStyle(ChatFormatting.YELLOW), true);
        }
        level().playSound(null, owner.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.6F, 1.2F);
        damageRod(owner, 1);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
