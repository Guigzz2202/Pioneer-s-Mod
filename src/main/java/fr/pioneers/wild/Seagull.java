package fr.pioneers.wild;

import fr.pioneers.common.PiCommonConfig;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 9) : la MOUETTE (plages, cotes, ports ; s'installe aussi pres des constructions de la cote). Oiseau du lot 7
 * ({@link WildBird} : vol, fuite, pose). De temps en temps, elle fond sur un joueur proche et VOLE UN ALIMENT de sa barre d'action, jamais
 * l'objet tenu en main ni l'armure ({@link Wild9Rules#stealSlot}) ; reglage serveur {@code fauna.seagullTheft} (actif par defaut :
 * decision 14). Elle garde son butin au bec (il tombe si on l'abat) et l'avale plus tard.
 */
public class Seagull extends WildBird {

    /** Aliment vole (au bec), delai avant le prochain vol, vols reussis. */
    public ItemStack loot = ItemStack.EMPTY;
    public int theftCooldown, thefts;
    int lootAge;

    public Seagull(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild9Rules.SEAGULL);
        theftCooldown = Wild9Rules.THEFT_MIN / 2 + random.nextInt(Wild9Rules.THEFT_MIN);
    }

    @Override
    protected void addOwnGoals() {
        goalSelector.addGoal(1, new Thief(this));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }

    public static boolean theftEnabled() {
        return PiCommonConfig.get(PiCommonConfig.FAUNA_SEAGULL_THEFT, true);
    }

    /** Case de la barre d'action qui tente la mouette (un aliment, jamais la case tenue en main), -1 sinon. */
    public int tempting(Player p, double roll) {
        Inventory inv = p.getInventory();
        boolean[] food = new boolean[Wild9Rules.HOTBAR];
        for (int i = 0; i < Wild9Rules.HOTBAR; i++) {
            ItemStack s = inv.items.get(i);
            food[i] = !s.isEmpty() && s.isEdible();
        }
        return Wild9Rules.stealSlot(food, inv.selected, roll);
    }

    /** Vol : UN aliment d'une case de la barre d'action (pas l'objet en main, pas l'armure), message au joueur, puis fuite. */
    public boolean trySteal(Player p) {
        if (p.isCreative() || p.isSpectator() || !Wild9Rules.seagullSteals(theftEnabled(), !loot.isEmpty(), theftCooldown, distanceTo(p))) {
            return false;
        }
        int slot = tempting(p, random.nextDouble());
        if (slot < 0) {
            return false;
        }
        Inventory inv = p.getInventory();
        ItemStack taken = inv.items.get(slot).split(1);
        inv.setChanged();
        loot = taken;
        lootAge = 0;
        thefts++;
        theftCooldown = Wild9Rules.THEFT_MIN + random.nextInt(Wild9Rules.THEFT_MAX - Wild9Rules.THEFT_MIN);
        p.displayClientMessage(Component.translatable("wild.pioneers.seagull.stole", taken.getHoverName()), true);
        playOwn("steal");
        alarm(p.position(), WildRules.ALARM_TICKS * 2, false);
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (theftCooldown > 0) {
                theftCooldown--;
            }
            if (!loot.isEmpty() && ++lootAge > 2400 && onGround()) {
                loot = ItemStack.EMPTY;                                  //avale (ou emporte au loin)
                playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.4F, 1.6F);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        if (!loot.isEmpty()) {
            spawnAtLocation(loot.copy());
            loot = ItemStack.EMPTY;
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("gull_loot", loot.save(new CompoundTag()));
        tag.putInt("gull_theft", theftCooldown);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loot = ItemStack.of(tag.getCompound("gull_loot"));
        theftCooldown = tag.getInt("gull_theft");
    }

    @Override
    protected int pickVariant(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.util.RandomSource random) {
        return random.nextInt(10) < 3 ? 1 : 0;                       //mouette rieuse : 3 sur 10
    }

    /** But : fondre sur un joueur proche qui a un aliment dans sa barre d'action, et le lui voler. */
    static final class Thief extends Goal {

        final Seagull g;
        @Nullable
        Player mark;
        int left;

        Thief(Seagull g) {
            this.g = g;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!theftEnabled() || !g.loot.isEmpty() || g.theftCooldown > 0 || g.alarmed() || g.getRandom().nextInt(20) != 0) {
                return false;
            }
            mark = g.level().getNearestPlayer(g.getX(), g.getY(), g.getZ(), Wild9Rules.THEFT_RANGE,
                  e -> WildAnimal.scary(e) && e instanceof Player p && g.tempting(p, 0) >= 0);
            return mark != null;
        }

        @Override
        public void start() {
            left = 200;
            g.cling(null, null);
            g.setAction(Wild7Rules.FLY);
            g.getNavigation().moveTo(mark, 1.4);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && mark != null && mark.isAlive() && g.loot.isEmpty() && g.distanceTo(mark) < Wild9Rules.THEFT_RANGE * 2;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            left--;
            //1.0.5-beta (lot 21) : but tenu a chaque tick : un joueur deconnecte (retire) ou mort n'est plus vole (sinon : objet double,
            //l'inventaire du joueur parti est deja sauve)
            if (mark == null || !mark.isAlive() || left < 0) {
                return;
            }
            g.getLookControl().setLookAt(mark);
            if (g.tickCount % 10 == 0) {
                g.getNavigation().moveTo(mark.getX(), mark.getEyeY() - 0.4, mark.getZ(), 1.4);
            }
            if (g.trySteal(mark)) {
                left = 0;
            }
        }

        @Override
        public void stop() {
            mark = null;
        }
    }
}
