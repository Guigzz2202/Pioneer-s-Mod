package fr.pioneers.equipment.gear;

import fr.pioneers.Pioneers;
import fr.pioneers.equipment.gear.electric.HydrogenJetpackItem;
import fr.pioneers.space.giant.GiantEvents;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.20.15 : effets des modules de l'armure modulaire (cote serveur, sauf le saut et la poussee des reacteurs qui se jouent aussi chez le
 * client : {@code client/gear/GearClient}). L'energie est prelevee sur la piece qui porte le module :
 * <ul>
 *     <li>chaque tick : vol stellaire (80 FE/t en vol), reacteurs dorsaux (40 FE/t par niveau en poussee), aimant (tous les 2 ticks) ;</li>
 *     <li>toutes les 10 ticks : vision nocturne (8 FE/t), recycleur sous l'eau (20 FE/t), detecteur (10 FE/t par niveau), synthetiseur
 *     (1 500 FE par point de faim), capteurs solaires (+25 FE/t par niveau), nanites (1 000 FE par point de vie), servomoteurs et
 *     hydropropulseurs en mouvement (6 FE/t par niveau), generateur cinetique (+40 FE par bloc parcouru et par niveau), revetement thermique ;</li>
 *     <li>evenements : bouclier (400 FE par point absorbe), amortisseurs (300 FE par point de chute), ressorts (200 FE par saut et par niveau),
 *     feu annule (revetement thermique, 200 FE), vampirisme de la lame.</li>
 * </ul>
 * Vol : le serveur accorde le vol libre ({@code mayfly}) avec le module de vol ; avec les seuls reacteurs, il l'accorde pendant la poussee
 * et 3 s apres (sinon le serveur expulse pour vol interdit) mais coupe le vol libre a chaque tick. 1.0.3-beta (lot 9) : le jetpack a
 * hydrogene ({@link HydrogenJetpackItem}, case du plastron) passe par les memes garde-fous (poussee et vol stationnaire signales par le
 * client, hydrogene et energie preleves a chaque tick ici).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class GearEvents {

    private GearEvents() {
    }

    private static final String FLIGHT_TAG = "pioneers_gear_flight", WALK_TAG = "pioneers_gear_walk";
    public static final float BASE_FLY_SPEED = 0.05F;
    /** Joueurs dont les reacteurs poussent (signale par le client, {@code PacketGear}). */
    private static final Set<UUID> THRUSTING = ConcurrentHashMap.newKeySet();
    /** 1.0.3-beta (lot 9) : parmi eux, ceux dont le jetpack a hydrogene est en vol stationnaire. */
    private static final Set<UUID> HOVERING = ConcurrentHashMap.newKeySet();
    /** Dernier tick de poussee (le serveur tolere le joueur en l'air encore 3 s apres). */
    private static final Map<UUID, Long> LAST_THRUST = new ConcurrentHashMap<>();
    private static final int THRUST_GRACE = 60;

    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ------------------------------------------------------------------ requetes (aussi pour la survie et les geantes)

    /** Le module de vol est actif (et alimente) sur le plastron porte. */
    public static boolean grantsFlight(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return Modules.level(chest, GearModule.FLIGHT) > 0 && Modules.energy(chest) >= 80;
    }

    /**
     * Vol libre, ou reacteurs en poussee (et 3 s apres) : le serveur doit tolerer le joueur en l'air. Hors poussee, les reacteurs n'accordent
     * rien (sinon plus aucun degat de chute).
     */
    public static boolean allowsAir(Player player) {
        if (grantsFlight(player)) {
            return true;
        }
        if (Modules.level(player.getItemBySlot(EquipmentSlot.CHEST), GearModule.JETPACK) <= 0 && !HydrogenJetpackItem.is(player.getItemBySlot(EquipmentSlot.CHEST))) {
            return false;
        }
        Long last = LAST_THRUST.get(player.getUUID());
        return THRUSTING.contains(player.getUUID()) || last != null && player.level().getGameTime() - last < THRUST_GRACE;
    }

    /** Respiration par le recycleur d'air (une seconde, 400 FE) ; faux sans recycleur ou sans energie. */
    public static boolean breathe(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return Modules.level(head, GearModule.RESPIRATOR) > 0 && Modules.use(head, 400);
    }

    /** Reserve du recycleur d'air pour l'affichage de survie (pour mille), -1 sans recycleur. */
    public static int breathReserve(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return Modules.installed(head, GearModule.RESPIRATOR) > 0 ? Math.round(1000 * Modules.ratio(head)) : -1;
    }

    public static void setThrusting(ServerPlayer player, boolean thrusting) {
        setThrusting(player, thrusting, false);
    }

    /**
     * 1.0.3-beta (lot 9) : poussee signalee par le client ; {@code hovering} = jetpack a hydrogene en vol stationnaire (consommation reduite).
     * Accepte avec les reacteurs dorsaux du plastron modulaire ou le jetpack a hydrogene porte.
     */
    public static void setThrusting(ServerPlayer player, boolean thrusting, boolean hovering) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (thrusting && (Modules.level(chest, GearModule.JETPACK) > 0 || HydrogenJetpackItem.fueled(chest, hovering))) {
            THRUSTING.add(player.getUUID());
            if (hovering) {
                HOVERING.add(player.getUUID());
            } else {
                HOVERING.remove(player.getUUID());
            }
        } else {
            THRUSTING.remove(player.getUUID());
            HOVERING.remove(player.getUUID());
        }
    }

    /** Le joueur pousse (reacteurs ou jetpack ; pour les tests et le HUD des autres). */
    public static boolean thrusting(Player player) {
        return THRUSTING.contains(player.getUUID());
    }

    /** Recharge les pieces portees (casque d'abord) ; renvoie ce qui est entre. */
    public static long chargeWorn(Player player, long amount) {
        long given = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (amount - given <= 0) {
                break;
            }
            if (ModularGear.is(stack)) {
                given += Modules.charge(stack, amount - given);
            }
        }
        return given;
    }

    // ------------------------------------------------------------------ tick du joueur

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        flight(player);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (THRUSTING.contains(player.getUUID()) && HydrogenJetpackItem.is(chest)) {
            //1.0.3-beta (lot 9) : jetpack a hydrogene : hydrogene ET energie a chaque tick ; reservoir ou batterie vide = arret
            boolean hovering = HOVERING.contains(player.getUUID());
            if (player.isCreative() || player.isSpectator() || !HydrogenJetpackItem.burn(chest, hovering)) {
                THRUSTING.remove(player.getUUID());
                HOVERING.remove(player.getUUID());
            } else {
                LAST_THRUST.put(player.getUUID(), player.level().getGameTime());
                player.fallDistance = 0;
                if (player.tickCount % 2 == 0) {
                    hydrogenExhaust(player, hovering);
                }
                if (player.tickCount % 6 == 0) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS,
                          hovering ? 0.2F : 0.35F, hovering ? 1.3F : 1.7F);
                }
            }
        } else if (THRUSTING.contains(player.getUUID())) {
            int jet = Modules.level(chest, GearModule.JETPACK);
            if (jet <= 0 || !Modules.use(chest, 40L * jet)) {
                THRUSTING.remove(player.getUUID());
            } else {
                LAST_THRUST.put(player.getUUID(), player.level().getGameTime());
                player.fallDistance = 0;
                if (player.tickCount % 2 == 0) {
                    exhaust(player);
                }
            }
        }
        if (player.tickCount % 2 == 0) {
            magnet(player, chest);
        }
        if (player.tickCount % 10 == 0) {
            periodic(player);
        }
    }

    private static void flight(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        Abilities ab = player.getAbilities();
        boolean exempt = player.isCreative() || player.isSpectator();
        boolean flight = grantsFlight(player);
        if (allowsAir(player) && !exempt) {
            boolean changed = false;
            if (!ab.mayfly) {
                ab.mayfly = true;
                changed = true;
            }
            data.putBoolean(FLIGHT_TAG, true);
            if (!flight && ab.flying) {
                ab.flying = false;
                changed = true;
            }
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            float speed = flight ? BASE_FLY_SPEED * (1 + 0.6F * (Modules.level(chest, GearModule.FLIGHT) - 1)) : BASE_FLY_SPEED;
            if (Math.abs(ab.getFlyingSpeed() - speed) > 1e-4) {
                ab.setFlyingSpeed(speed);
                changed = true;
            }
            if (changed) {
                player.onUpdateAbilities();
            }
            if (flight && ab.flying) {
                Modules.drain(chest, 80);
                player.fallDistance = 0;
            }
        } else if (data.getBoolean(FLIGHT_TAG)) {
            data.remove(FLIGHT_TAG);
            if (!exempt && !GiantEvents.canSuitFly(player)) {
                ab.mayfly = false;
                ab.flying = false;
            }
            ab.setFlyingSpeed(BASE_FLY_SPEED);
            player.onUpdateAbilities();
        }
    }

    /** Flammes des tuyeres (sous le sac dorsal), visibles de tous. */
    private static void exhaust(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double bx = Mth.sin(yaw) * 0.32, bz = -Mth.cos(yaw) * 0.32;
        double sx = Mth.cos(yaw) * 0.16, sz = Mth.sin(yaw) * 0.16;
        for (int s = -1; s <= 1; s += 2) {
            double x = player.getX() + bx + sx * s, y = player.getY() + 0.75, z = player.getZ() + bz + sz * s;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.02, 0.05, 0.02, 0.01);
            level.sendParticles(ParticleTypes.SMOKE, x, y - 0.1, z, 1, 0.02, 0.05, 0.02, 0.01);
        }
    }

    /** 1.0.3-beta (lot 9) : flammes jaunes des deux tuyeres du jetpack a hydrogene et vapeur d'eau (l'hydrogene brule en eau). */
    private static void hydrogenExhaust(ServerPlayer player, boolean hovering) {
        ServerLevel level = player.serverLevel();
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double bx = Mth.sin(yaw) * 0.36, bz = -Mth.cos(yaw) * 0.36;
        double sx = Mth.cos(yaw) * 0.18, sz = Mth.sin(yaw) * 0.18;
        for (int s = -1; s <= 1; s += 2) {
            double x = player.getX() + bx + sx * s, y = player.getY() + 0.7, z = player.getZ() + bz + sz * s;
            level.sendParticles(ParticleTypes.FLAME, x, y, z, hovering ? 1 : 2, 0.02, 0.06, 0.02, 0.01);
            level.sendParticles(ParticleTypes.CLOUD, x, y - 0.25, z, 1, 0.03, 0.05, 0.03, 0.01);
        }
    }

    private static void magnet(ServerPlayer player, ItemStack chest) {
        int level = Modules.level(chest, GearModule.MAGNET);
        if (level <= 0 || player.isShiftKeyDown()) {
            return;
        }
        double r = 4.0 * level;
        AABB box = player.getBoundingBox().inflate(r);
        Vec3 target = player.position().add(0, 0.6, 0);
        int moved = 0;
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive()
              && !e.getPersistentData().getBoolean("PreventRemoteMovement"))) {
            pull(item, target);
            moved++;
        }
        for (ExperienceOrb orb : player.level().getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive)) {
            pull(orb, target);
            moved++;
        }
        if (moved > 0) {
            Modules.drain(chest, 4L * Math.min(moved, 32));
        }
    }

    private static void pull(Entity e, Vec3 target) {
        Vec3 d = target.subtract(e.position());
        double len = d.length();
        if (len < 0.4) {
            return;
        }
        e.setDeltaMovement(d.scale(Math.min(0.45, 0.25 + 0.4 / len) / len));
        e.hurtMarked = true;
    }

    private static void periodic(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD), chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS), feet = player.getItemBySlot(EquipmentSlot.FEET);
        if (!ModularGear.is(head) && !ModularGear.is(chest) && !ModularGear.is(legs) && !ModularGear.is(feet)) {
            player.getPersistentData().remove(WALK_TAG);
            return;
        }
        //Casque
        if (Modules.level(head, GearModule.NIGHT_VISION) > 0 && Modules.use(head, 80)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
        }
        if (player.isUnderWater() && Modules.level(head, GearModule.RESPIRATOR) > 0 && Modules.use(head, 200)) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        int scanner = Modules.level(head, GearModule.SCANNER);
        if (scanner > 0 && player.tickCount % 20 == 0 && Modules.use(head, 200L * scanner)) {
            double r = 12.0 * scanner;
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r), e -> e != player
                  && e.isAlive())) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
            }
        }
        if (Modules.level(head, GearModule.NUTRITION) > 0 && player.getFoodData().getFoodLevel() < 18 && Modules.use(head, 1500)) {
            player.getFoodData().eat(1, 0.3F);
        }
        int solar = Modules.level(head, GearModule.SOLAR);
        if (solar > 0) {
            BlockPos eye = BlockPos.containing(player.getEyePosition());
            if (player.level().dimensionType().hasSkyLight() && player.level().isDay() && !player.level().isRaining() && player.level().canSeeSky(eye)) {
                chargeWorn(player, 250L * solar);
            }
        }
        //Plastron
        int nanites = Modules.level(chest, GearModule.NANITES);
        if (nanites > 0 && player.getHealth() < player.getMaxHealth() && player.tickCount % (80 >> (nanites - 1)) == 0 && Modules.use(chest, 1000)) {
            player.heal(1.0F);
        }
        if (player.isOnFire() && Modules.level(chest, GearModule.THERMAL) > 0 && Modules.use(chest, 200)) {
            player.clearFire();
        }
        //Jambieres et bottes : mouvement
        CompoundTag data = player.getPersistentData();
        float walked = data.contains(WALK_TAG) ? Math.max(0, Math.min(20, player.walkDist - data.getFloat(WALK_TAG))) : 0;
        data.putFloat(WALK_TAG, player.walkDist);
        int servo = Modules.level(legs, GearModule.SERVO);
        if (servo > 0 && walked > 0.05F) {
            Modules.drain(legs, 60L * servo);
        }
        int hydro = Modules.level(legs, GearModule.HYDRO);
        if (hydro > 0 && player.isInWater() && walked > 0.05F) {
            Modules.drain(legs, 60L * hydro);
        }
        int kinetic = Modules.level(feet, GearModule.KINETIC);
        if (kinetic > 0 && walked > 0.05F && player.onGround()) {
            chargeWorn(player, Math.round(40.0 * kinetic * walked));
        }
    }

    // ------------------------------------------------------------------ evenements

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        THRUSTING.remove(event.getEntity().getUUID());
        HOVERING.remove(event.getEntity().getUUID());
        LAST_THRUST.remove(event.getEntity().getUUID());
    }

    /** Ressorts de saut : le client donne l'elan (le mouvement du joueur est calcule chez lui), le serveur preleve l'energie. */
    @SubscribeEvent
    public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isShiftKeyDown()) {
            return;
        }
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        int jump = Modules.level(feet, GearModule.JUMP);
        if (jump <= 0 || Modules.energy(feet) < 200L * jump) {
            return;
        }
        if (player.level().isClientSide) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.1 * jump, 0));
        } else {
            Modules.use(feet, 200L * jump);
        }
    }

    /** Amortisseurs : la chute ne blesse pas tant que l'energie suit (300 FE par point evite). */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        if (Modules.level(feet, GearModule.DAMPERS) <= 0) {
            return;
        }
        float points = Math.max(0, event.getDistance() - 3) * event.getDamageMultiplier();
        if (points <= 0) {
            return;
        }
        long afford = Modules.energy(feet) / 300;
        float saved = Math.min(points, afford);
        Modules.drain(feet, (long) Math.ceil(saved) * 300);
        event.setDamageMultiplier(event.getDamageMultiplier() * (points - saved) / points);
    }

    /** Revetement thermique : le feu ne touche pas. */
    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide || !event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (Modules.level(chest, GearModule.THERMAL) > 0 && Modules.use(chest, 200)) {
            player.clearFire();
            event.setCanceled(true);
        }
    }

    /** Bouclier energetique : absorbe 20 % des degats par niveau (400 FE par point). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.DROWN)) {
            return;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int shield = Modules.level(chest, GearModule.SHIELD);
        if (shield <= 0 || event.getAmount() <= 0) {
            return;
        }
        float wanted = event.getAmount() * Math.min(0.8F, 0.2F * shield);
        float absorbed = Math.min(wanted, Modules.energy(chest) / 400F);
        if (absorbed > 0) {
            Modules.drain(chest, (long) Math.ceil(absorbed * 400));
            event.setAmount(event.getAmount() - absorbed);
        }
    }

    /** Vampirisme : la lame rend une part des degats infliges au corps a corps. */
    @SubscribeEvent
    public static void damage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || event.getSource().getDirectEntity() != player || player.level().isClientSide) {
            return;
        }
        int vampirism = Modules.level(player.getMainHandItem(), GearModule.VAMPIRISM);
        if (vampirism > 0 && event.getAmount() > 0) {
            player.heal(event.getAmount() * 0.08F * vampirism);
        }
    }
}
