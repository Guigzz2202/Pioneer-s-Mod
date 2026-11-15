package fr.pioneers.gear;

import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.15 : etat d'un objet modulaire, dans le NBT de sa pile :
 * <ul>
 *     <li>{@code Energy} (long) : energie stockee (FE) ;</li>
 *     <li>{@code Modules} : niveau installe par module ({@code id -> octet}) ;</li>
 *     <li>{@code Off} : modules coupes par le joueur (ils restent installes).</li>
 * </ul>
 * La reserve vaut celle du tier, +50 % par niveau de cellule auxiliaire. L'objet expose sa reserve en {@link IEnergyStorage} (chargeurs
 * des autres mods compris).
 */
public final class Modules {

    private Modules() {
    }

    public static final String ENERGY = "Energy", MODULES = "Modules", OFF = "Off";

    // ------------------------------------------------------------------ modules

    /** Niveau installe (meme coupe), 0 si absent. */
    public static int installed(ItemStack stack, GearModule module) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getCompound(MODULES).getByte(module.id());
    }

    /** Modules installes (ordre de l'enum) et leur niveau. */
    public static Map<GearModule, Integer> all(ItemStack stack) {
        Map<GearModule, Integer> out = new EnumMap<>(GearModule.class);
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return out;
        }
        CompoundTag mods = tag.getCompound(MODULES);
        for (GearModule m : GearModule.values()) {
            int level = mods.getByte(m.id());
            if (level > 0) {
                out.put(m, level);
            }
        }
        return out;
    }

    /** Liste ordonnee des modules installes (index des boutons de l'etabli et de l'ecran des modules). */
    public static List<GearModule> list(ItemStack stack) {
        return new ArrayList<>(all(stack).keySet());
    }

    public static boolean isOff(ItemStack stack, GearModule module) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getCompound(OFF).getBoolean(module.id());
    }

    public static void setOff(ItemStack stack, GearModule module, boolean off) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag offs = tag.getCompound(OFF);
        if (off) {
            offs.putBoolean(module.id(), true);
        } else {
            offs.remove(module.id());
        }
        if (offs.isEmpty()) {
            tag.remove(OFF);
        } else {
            tag.put(OFF, offs);
        }
    }

    /** Emplacements occupes (somme des niveaux). */
    public static int used(ItemStack stack) {
        int n = 0;
        for (int level : all(stack).values()) {
            n += level;
        }
        return n;
    }

    /**
     * Niveau actif d'un module : installe, en marche, plafonne par le tier ; 0 sans energie (sauf modules passifs). C'est la valeur que lisent
     * tous les effets.
     */
    public static int level(ItemStack stack, GearModule module) {
        if (!(stack.getItem() instanceof ModularGear gear)) {
            return 0;
        }
        int level = Math.min(installed(stack, module), module.cap(gear.tier()));
        if (level <= 0 || isOff(stack, module) || !module.passive() && energy(stack) <= 0) {
            return 0;
        }
        return level;
    }

    /** Pourquoi le module ne peut pas etre ajoute (cle de langue), null s'il peut l'etre. */
    @Nullable
    public static String refuse(ItemStack stack, GearModule module) {
        if (!(stack.getItem() instanceof ModularGear gear)) {
            return "gear.pioneers.refuse.no_gear";
        }
        if (!module.fits(gear.kind())) {
            return "gear.pioneers.refuse.kind";
        }
        if (gear.tier().ordinal() < module.minTier) {
            return "gear.pioneers.refuse.tier";
        }
        if (installed(stack, module) >= module.cap(gear.tier())) {
            return "gear.pioneers.refuse.max";
        }
        if (used(stack) >= gear.tier().slots) {
            return "gear.pioneers.refuse.slots";
        }
        return null;
    }

    /** Ajoute un niveau du module (verifier {@link #refuse} avant). */
    public static void install(ItemStack stack, GearModule module) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag mods = tag.getCompound(MODULES);
        mods.putByte(module.id(), (byte) (installed(stack, module) + 1));
        tag.put(MODULES, mods);
    }

    /** Retire un niveau du module ; vrai si un niveau a ete retire. L'energie au-dela de la nouvelle reserve est perdue. */
    public static boolean uninstall(ItemStack stack, GearModule module) {
        int level = installed(stack, module);
        if (level <= 0) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag mods = tag.getCompound(MODULES);
        if (level == 1) {
            mods.remove(module.id());
            setOff(stack, module, false);
        } else {
            mods.putByte(module.id(), (byte) (level - 1));
        }
        tag.put(MODULES, mods);
        setEnergy(stack, energy(stack));
        return true;
    }

    // ------------------------------------------------------------------ energie

    public static long capacity(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularGear gear)) {
            return 0;
        }
        int cells = Math.min(installed(stack, GearModule.BATTERY), GearModule.BATTERY.cap(gear.tier()));
        return gear.tier().energy + gear.tier().energy * cells / 2;
    }

    public static long energy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getLong(ENERGY));
    }

    public static void setEnergy(ItemStack stack, long energy) {
        long value = Math.max(0, Math.min(capacity(stack), energy));
        if (value != energy(stack)) {
            stack.getOrCreateTag().putLong(ENERGY, value);
        }
    }

    /** Preleve exactement {@code amount} FE ; faux (et rien preleve) si la reserve ne suffit pas. */
    public static boolean use(ItemStack stack, long amount) {
        long energy = energy(stack);
        if (amount <= 0) {
            return true;
        }
        if (energy < amount) {
            return false;
        }
        setEnergy(stack, energy - amount);
        return true;
    }

    /** Preleve au plus {@code amount} FE ; renvoie ce qui a ete preleve. */
    public static long drain(ItemStack stack, long amount) {
        long taken = Math.min(energy(stack), Math.max(0, amount));
        if (taken > 0) {
            setEnergy(stack, energy(stack) - taken);
        }
        return taken;
    }

    /** Ajoute de l'energie ; renvoie ce qui est entre. */
    public static long charge(ItemStack stack, long amount) {
        long room = capacity(stack) - energy(stack);
        long given = Math.max(0, Math.min(room, amount));
        if (given > 0) {
            setEnergy(stack, energy(stack) + given);
        }
        return given;
    }

    public static float ratio(ItemStack stack) {
        long cap = capacity(stack);
        return cap <= 0 ? 0 : (float) energy(stack) / cap;
    }

    // ------------------------------------------------------------------ objet (barre, infobulle, capacite)

    public static int barWidth(ItemStack stack) {
        return Math.round(13F * ratio(stack));
    }

    public static int barColor(ItemStack stack) {
        return stack.getItem() instanceof ModularGear gear ? gear.tier().color : 0x55FF55;
    }

    /** Infobulle commune : tier, energie, emplacements, modules (niveau, coupe). */
    public static void tooltip(ItemStack stack, List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        if (!(stack.getItem() instanceof ModularGear gear)) {
            return;
        }
        ModularTier tier = gear.tier();
        //1.0.4-beta (lot 28) : format commun des equipements (J:core/tooltip/GearTips) : « Tier : Graviton · MKI » (nom de l'ensemble dans
        //sa couleur, MK dans la couleur du tier), energie au format commun ; avant : « Tier Graviton (MKI) » hors du gabarit « Libelle : valeur »
        GearTips.named(tips, Component.translatable("tooltip.pioneers.gear.dot", Component.translatable("gear.pioneers.tier." + tier.id())
              .withStyle(s -> s.withColor(tier.color)), Tooltips.tierName(tier.ordinal())));
        GearTips.energy(tips, energy(stack), capacity(stack));
        tips.spec("gear.pioneers.slots", used(stack), tier.slots);
        for (Map.Entry<GearModule, Integer> e : all(stack).entrySet()) {
            GearModule m = e.getKey();
            int cap = m.cap(tier);
            MutableComponent line = Component.literal("  ").append(m.displayName()).append(" " + roman(Math.min(e.getValue(), Math.max(cap, 1))));
            if (isOff(stack, m)) {
                line.append(Component.translatable("gear.pioneers.off"));
                line.withStyle(ChatFormatting.DARK_GRAY);
            } else {
                line.withStyle(ChatFormatting.AQUA);
            }
            tips.custom(line);
        }
        if (all(stack).isEmpty()) {
            tips.hint("gear.pioneers.no_modules");
        }
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    /** Capacite d'energie de la pile (chargeurs, etabli). */
    public static ICapabilityProvider provider(ItemStack stack) {
        return new Provider(stack);
    }

    private static final class Provider implements ICapabilityProvider, IEnergyStorage {

        private final ItemStack stack;
        private final LazyOptional<IEnergyStorage> holder = LazyOptional.of(() -> this);

        Provider(ItemStack stack) {
            this.stack = stack;
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return ForgeCapabilities.ENERGY.orEmpty(cap, holder);
        }

        private int rate() {
            return stack.getItem() instanceof ModularGear gear ? gear.tier().transfer() : 0;
        }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            long room = capacity(stack) - energy(stack);
            int given = (int) Math.max(0, Math.min(room, Math.min(max, rate())));
            if (!simulate && given > 0) {
                setEnergy(stack, energy(stack) + given);
            }
            return given;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energy(stack));
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, capacity(stack));
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
