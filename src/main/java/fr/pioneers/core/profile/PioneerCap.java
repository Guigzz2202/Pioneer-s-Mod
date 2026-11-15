package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 1.9 (MAJ RP) : capacite {@link PioneerProfile} des joueurs (attache, enregistrement, copie a la mort et au changement de dimension). */
public final class PioneerCap {

    public static final Capability<PioneerProfile> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });

    private PioneerCap() {
    }

    /**
     * Profil du joueur (jamais nul pour un joueur). 1.0.4-beta (lot 8, bug « a la mort le jeu redemande l'identite ») : un joueur MORT reste
     * dans la liste des joueurs jusqu'a sa reapparition, mais {@code LivingEntity.tickDeath} le retire du monde 20 ticks apres sa mort, et
     * {@code Entity.remove} invalide ses capacites : {@code getCapability} ne rendait plus rien et l'on rendait un profil VIDE partage
     * (« non initie »). Le serveur rouvrait alors l'initiation (controle toutes les 100 ticks) par-dessus l'ecran de mort, et ce que le
     * joueur y tapait s'ecrivait dans ce profil partage. Desormais on relit la VRAIE capacite d'un joueur retire (comme le fait
     * {@code PlayerEvent.Clone}) ; seul un joueur sans capacite du tout recoit un profil jetable, jamais partage.
     */
    public static PioneerProfile get(Player player) {
        PioneerProfile p = find(player);
        return p != null ? p : new PioneerProfile();
    }

    /**
     * Profil REEL du joueur, ou {@code null} s'il n'en a pas (aucune capacite attachee). Lit aussi la capacite d'un joueur retire du monde
     * (mort en attente de reapparition, changement de dimension en cours) : ses capacites sont seulement invalidees, pas perdues.
     */
    @Nullable
    public static PioneerProfile find(Player player) {
        PioneerProfile p = player.getCapability(CAP).resolve().orElse(null);
        if (p == null && player.isRemoved()) {
            player.reviveCaps();
            try {
                p = player.getCapability(CAP).resolve().orElse(null);
            } finally {
                player.invalidateCaps();
            }
        }
        return p;
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            event.register(PioneerProfile.class);
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class ForgeBus {

        private ForgeBus() {
        }

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(Pioneers.rl("pioneer"), new Provider());
            }
        }

        @SubscribeEvent
        public static void clone(PlayerEvent.Clone event) {
            //Mort comme retour de l'End : le personnage suit le joueur, entier (aucune perte d'XP a la mort)
            Player old = event.getOriginal();
            old.reviveCaps();
            PioneerProfile from = old.getCapability(CAP).resolve().orElse(null);
            PioneerProfile to = event.getEntity().getCapability(CAP).resolve().orElse(null);
            if (from != null && to != null) {
                to.copyFrom(from);
            }
            old.invalidateCaps();
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private final PioneerProfile data = new PioneerProfile();
        private final LazyOptional<PioneerProfile> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.deserializeNBT(tag);
        }
    }
}
