package fr.pioneers.farm.leisure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.5 : outils communs aux pipes, cigares et cendrier : trouver du tabac ou de quoi allumer (main gauche d'abord, puis l'inventaire), souffler
 * la fumee (bouffee, ou rond de fumee accroupi) et la volute qui monte de l'objet tenu. Purement cosmetique.
 */
public final class Smoking {

    /** Derniere heure du monde vue cote client (barre du cigare allume, dessinee sans le monde). */
    public static volatile long clientNow;

    private Smoking() {
    }

    public static long now(Level level) {
        if (level.isClientSide) {
            clientNow = level.getGameTime();
        }
        return level.getGameTime();
    }

    /** Premier objet du tag : main gauche, puis l'inventaire. */
    public static ItemStack find(Player player, TagKey<Item> tag) {
        if (player.getOffhandItem().is(tag)) {
            return player.getOffhandItem();
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(tag)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Brule une allumette (ou use le briquet) : faux sans rien pour allumer. Cote serveur. */
    public static boolean strike(Player player, double x, double y, double z) {
        ItemStack lighter = find(player, PiLeisure.LIGHTERS);
        if (lighter.isEmpty()) {
            return false;
        }
        if (!player.getAbilities().instabuild && lighter.isDamageableItem()) {
            lighter.hurtAndBreak(1, player, p -> {
            });
        }
        player.level().playSound(null, x, y, z, PiLeisure.MATCH.get(), SoundSource.PLAYERS, 0.8F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        return true;
    }

    /** Bouche du joueur (un peu devant les yeux). */
    public static Vec3 mouth(LivingEntity entity) {
        return entity.getEyePosition().add(entity.getLookAngle().scale(0.3)).add(0, -0.12, 0);
    }

    /** Souffle la fumee : rond de fumee (accroupi) ou bouffee qui s'etale ; {@code strength} 0..1 selon la duree de la bouffee. */
    public static void exhale(ServerLevel level, Player player, float strength) {
        Vec3 m = mouth(player);
        Vec3 look = player.getLookAngle();
        RandomSource random = player.getRandom();
        if (player.isShiftKeyDown()) {
            level.sendParticles(PiLeisure.SMOKE_RING.get(), m.x, m.y, m.z, 0, look.x, look.y + 0.05, look.z, 0.07);
        } else {
            int n = 4 + Math.round(strength * 6);
            for (int i = 0; i < n; i++) {
                double s = 0.03 + random.nextDouble() * 0.05;
                level.sendParticles(PiLeisure.PIPE_SMOKE.get(), m.x, m.y, m.z, 0, look.x + random.nextGaussian() * 0.25, look.y + 0.15
                      + random.nextGaussian() * 0.15, look.z + random.nextGaussian() * 0.25, s);
            }
        }
        level.playSound(null, m.x, m.y, m.z, PiLeisure.EXHALE.get(), SoundSource.PLAYERS, 0.5F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** Position de l'objet tenu (approchee), cote client : la volute part du fourneau ou du bout du cigare. */
    public static Vec3 heldTip(LivingEntity entity, boolean mainHand) {
        if (entity.isUsingItem()) {
            return mouth(entity).add(entity.getLookAngle().scale(0.25)).add(0, 0.12, 0);
        }
        float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
        boolean right = mainHand == (entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        double side = right ? -0.38 : 0.38;
        double fwd = 0.45;
        double x = entity.getX() + Math.cos(yaw) * side - Math.sin(yaw) * fwd;
        double z = entity.getZ() + Math.sin(yaw) * side + Math.cos(yaw) * fwd;
        return new Vec3(x, entity.getY() + entity.getBbHeight() * 0.62, z);
    }

    /** Volute qui monte d'un objet tenu (cote client, une fois sur {@code chance}). */
    public static void wisp(Level level, Entity entity, ItemStack stack, int chance) {
        if (!(entity instanceof LivingEntity living) || level.random.nextInt(chance) != 0) {
            return;
        }
        boolean main = living.getMainHandItem() == stack;
        if (!main && living.getOffhandItem() != stack) {
            return;
        }
        Vec3 p = heldTip(living, main);
        level.addParticle(PiLeisure.PIPE_SMOKE.get(), p.x, p.y, p.z, (level.random.nextDouble() - 0.5) * 0.004, 0.012, (level.random.nextDouble() - 0.5)
              * 0.004);
    }
}
