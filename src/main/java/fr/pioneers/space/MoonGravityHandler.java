package fr.pioneers.space;

import fr.pioneers.Pioneers;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gravite des astres, coherente avec la gravite reelle (Lune 0,17 g, Mars 0,38 g, Venus 0,9 g...) : on saute plus haut,
 * on tombe plus lentement et les chutes font moins mal. Appliquee cote serveur ; l'attribut est synchronise avec le client.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class MoonGravityHandler {

    private MoonGravityHandler() {
    }

    private static final UUID MODIFIER_ID = UUID.fromString("6f1d3c2a-8b4e-4f7a-9c1d-2e5b7a9c0d13");

    /** Gravite appliquee des l'arrivee dans un monde (apparition, changement de dimension, chargement). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && !event.getLevel().isClientSide) {
            update(entity);
        }
    }

    /** Verification de securite, une fois toutes les 2 s par entite (decalees pour etaler le cout). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || (entity.tickCount + entity.getId()) % 40 != 0) {
            return;
        }
        update(entity);
    }

    private static void update(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) {
            return;
        }
        double factor = PiDimensions.gravity(entity.level());
        AttributeModifier current = gravity.getModifier(MODIFIER_ID);
        double wanted = factor - 1;
        if (current != null && (factor == 1 || Math.abs(current.getAmount() - wanted) > 1.0E-4)) {
            gravity.removeModifier(MODIFIER_ID);
            current = null;
        }
        if (factor != 1 && current == null) {
            gravity.addTransientModifier(new AttributeModifier(MODIFIER_ID, "pioneers:low_gravity", wanted, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        double factor = PiDimensions.gravity(event.getEntity().level());
        if (factor != 1) {
            event.setDistance((float) (event.getDistance() * factor));
        }
    }
}
