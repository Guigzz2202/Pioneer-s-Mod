package fr.pioneers.core.side;

import fr.pioneers.core.RelativeSide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration des faces d'une machine : un {@link SideMode} par face relative (avant, arriere, gauche, droite, dessus, dessous), vue de
 * la facade de la machine (une machine sans facade est vue comme tournee vers le nord), et l'ejection automatique par les faces de sortie.
 */
public class SideConfig {

    /** Actions par ressource : {@code TYPED + canal * 16 + face} (reglage suivant), {@code + 8} (precedent). */
    public static final int TYPED = 1000;
    /** Action : bascule l'ejection automatique du canal {@code action - EJECT_TYPED}. */
    public static final int EJECT_TYPED = 2000;
    /** Action : ferme toutes les faces (non verrouillees) du canal {@code action - CLEAR_TYPED}. */
    public static final int CLEAR_TYPED = 3000;

    /** Un canal (reglages des 6 faces) par ressource declaree ; sans ressource declaree, un seul canal commun. */
    private final List<SideType> types = new ArrayList<>();
    private final List<SideMode[]> channels = new ArrayList<>();
    private final Set<SideMode> allowed;
    private final Supplier<Direction> facing;
    private final Runnable listener;
    /** Ejection automatique par canal (meme indice que {@link #channels}). */
    private final List<Boolean> ejects = new ArrayList<>();
    /** Ressources que la machine sait ejecter (vide : toutes, si {@link #canEject}). */
    private final Set<SideType> ejectable = EnumSet.noneOf(SideType.class);
    /** Faces verrouillees (toujours fermees : ecran, facade). */
    private final boolean[] locked = new boolean[6];
    /** La machine sait-elle ejecter (sinon le bouton n'est pas propose). */
    public final boolean canEject;

    public SideConfig(Set<SideMode> allowed, SideMode initial, boolean canEject, Supplier<Direction> facing, Runnable listener) {
        this.allowed = EnumSet.copyOf(allowed);
        this.facing = facing;
        this.listener = listener;
        this.canEject = canEject;
        SideMode[] modes = new SideMode[6];
        Arrays.fill(modes, initial);
        channels.add(modes);
        ejects.add(false);
    }

    /**
     * Declare une ressource reglable a part (appele a la construction ; les ressources gardent l'ordre de {@link SideType}). La premiere
     * reprend le canal commun, les suivantes en copient les reglages.
     */
    public SideConfig addType(SideType type) {
        if (types.contains(type)) {
            return this;
        }
        if (!types.isEmpty()) {
            channels.add(channels.get(0).clone());
            ejects.add(ejects.get(0));
        }
        types.add(type);
        //Garder l'ordre de l'enumeration (les reglages suivent leur ressource)
        for (int i = 1; i < types.size(); i++) {
            for (int j = i; j > 0 && types.get(j).ordinal() < types.get(j - 1).ordinal(); j--) {
                Collections.swap(types, j, j - 1);
                Collections.swap(channels, j, j - 1);
                Collections.swap(ejects, j, j - 1);
            }
        }
        return this;
    }

    /** Ressources dont les faces ne suivent pas les reglages generaux ({@link #set(RelativeSide, SideMode)}) : l'energie (1.0). */
    private final EnumSet<SideType> independent = EnumSet.noneOf(SideType.class);

    /**
     * 1.0 (demande de Guillaume) : l'energie entre par toutes les faces d'une machine et sort par toutes celles d'un generateur, quel que soit
     * le placement des autres ressources. Les reglages generaux poses ensuite par la machine ne touchent plus ce canal.
     */
    public SideConfig independent(SideType type, SideMode mode) {
        addType(type);
        independent.add(type);
        if (allowed.contains(mode)) {
            SideMode[] modes = channel(type);
            for (int i = 0; i < 6; i++) {
                if (!locked[i]) {
                    modes[i] = mode;
                }
            }
        }
        return this;
    }

    /**
     * API Machine (lot 2) : reglage par defaut d'une ressource (a la construction, sans prevenir la machine) : toutes ses faces non
     * verrouillees passent a {@code mode}.
     */
    public SideConfig fill(SideType type, SideMode mode) {
        if (types.contains(type) && allowed.contains(mode)) {
            SideMode[] modes = channel(type);
            for (int i = 0; i < 6; i++) {
                if (!locked[i]) {
                    modes[i] = mode;
                }
            }
        }
        return this;
    }

    /** Verrouille des faces : toujours fermees, aucun reglage possible. */
    public SideConfig lock(RelativeSide... sides) {
        for (RelativeSide side : sides) {
            locked[side.ordinal()] = true;
            for (SideMode[] modes : channels) {
                modes[side.ordinal()] = SideMode.NONE;
            }
        }
        return this;
    }

    public boolean isLocked(RelativeSide side) {
        return locked[side.ordinal()];
    }

    /** Ressources reglables (vide : un seul canal commun). */
    public List<SideType> types() {
        return types;
    }

    public int channelCount() {
        return channels.size();
    }

    private SideMode[] channel(@Nullable SideType type) {
        int index = type == null ? -1 : types.indexOf(type);
        return channels.get(Math.max(0, index));
    }

    public SideMode get(RelativeSide side) {
        return channels.get(0)[side.ordinal()];
    }

    public SideMode get(SideType type, RelativeSide side) {
        return channel(type)[side.ordinal()];
    }

    /** Reglage de la face du monde {@code side} pour le premier canal (null : acces interne, tout permis). */
    public SideMode mode(@Nullable Direction side) {
        return mode(null, side);
    }

    /** Reglage de la face du monde {@code side} pour la ressource {@code type} (canal commun si elle n'a pas le sien). */
    public SideMode mode(@Nullable SideType type, @Nullable Direction side) {
        if (side == null) {
            return SideMode.IO;
        }
        return channel(type)[RelativeSide.fromDirections(facing.get(), side).ordinal()];
    }

    /** Regle la face pour toutes les ressources (reglages par defaut des machines). */
    public void set(RelativeSide side, SideMode mode) {
        if (locked[side.ordinal()]) {
            return;
        }
        boolean changed = false;
        for (int c = 0; c < channels.size(); c++) {
            SideMode[] modes = channels.get(c);
            if (!types.isEmpty() && independent.contains(types.get(c))) {
                continue;
            }
            if (allowed.contains(mode) && modes[side.ordinal()] != mode) {
                modes[side.ordinal()] = mode;
                changed = true;
            }
        }
        if (changed) {
            listener.run();
        }
    }

    public void set(SideType type, RelativeSide side, SideMode mode) {
        SideMode[] modes = channel(type);
        if (!locked[side.ordinal()] && allowed.contains(mode) && modes[side.ordinal()] != mode) {
            modes[side.ordinal()] = mode;
            listener.run();
        }
    }

    /** Reglage suivant (ou precedent) parmi les reglages permis, sur le premier canal. */
    public void cycle(RelativeSide side, boolean back) {
        cycle(0, side, back);
    }

    public void cycle(int channel, RelativeSide side, boolean back) {
        if (channel < 0 || channel >= channels.size() || locked[side.ordinal()]) {
            return;
        }
        SideMode[] modes = channels.get(channel);
        SideMode mode = modes[side.ordinal()];
        for (int i = 0; i < 4; i++) {
            mode = SideMode.byIndex(mode.ordinal() + (back ? -1 : 1));
            if (allowed.contains(mode)) {
                if (modes[side.ordinal()] != mode) {
                    modes[side.ordinal()] = mode;
                    listener.run();
                }
                return;
            }
        }
    }

    /** Declare les ressources que la machine sait ejecter (par defaut : toutes, si la machine ejecte). */
    public SideConfig ejects(SideType... types) {
        ejectable.addAll(Arrays.asList(types));
        return this;
    }

    public boolean canEject(@Nullable SideType type) {
        return canEject && (ejectable.isEmpty() || type == null || ejectable.contains(type));
    }

    /** Ejection du canal 0 (machines a un seul canal). */
    /** Ferme toutes les faces du canal (bouton « tout fermer »). */
    public void clear(int channel) {
        if (channel < 0 || channel >= channels.size()) {
            return;
        }
        SideMode[] modes = channels.get(channel);
        boolean changed = false;
        for (int i = 0; i < 6; i++) {
            if (!locked[i] && modes[i] != SideMode.NONE) {
                modes[i] = SideMode.NONE;
                changed = true;
            }
        }
        if (changed) {
            listener.run();
        }
    }

    public boolean isEject() {
        return ejects.get(0);
    }

    public boolean isEject(@Nullable SideType type) {
        int index = type == null ? -1 : types.indexOf(type);
        if (index < 0) {
            //Ressource sans canal propre : le canal commun, si la machine sait l'ejecter
            return canEject(type) && ejects.get(0);
        }
        return canEject(type) && ejects.get(index);
    }

    public void setEject(boolean eject) {
        setEjectChannel(0, eject);
    }

    public void setEjectChannel(int channel, boolean eject) {
        if (channel < 0 || channel >= ejects.size()) {
            return;
        }
        SideType type = types.isEmpty() ? null : types.get(channel);
        if (canEject(type) && ejects.get(channel) != eject) {
            ejects.set(channel, eject);
            listener.run();
        }
    }

    public boolean isEjectChannel(int channel) {
        return channel >= 0 && channel < ejects.size() && ejects.get(channel);
    }

    public Set<SideMode> allowed() {
        return allowed;
    }

    // ------------------------------------------------------------------
    // Synchronisation (un entier par canal : 2 bits par face ; ejection au bit 12 du premier) et sauvegarde
    // ------------------------------------------------------------------

    public int pack() {
        return pack(0);
    }

    public int pack(int channel) {
        int index = Math.min(channel, channels.size() - 1);
        int packed = ejects.get(index) ? 1 << 12 : 0;
        SideMode[] modes = channels.get(index);
        for (int i = 0; i < 6; i++) {
            packed |= modes[i].ordinal() << i * 2;
        }
        return packed;
    }

    private static void unpackInto(SideMode[] modes, int packed) {
        for (int i = 0; i < 6; i++) {
            modes[i] = SideMode.byIndex(packed >> i * 2 & 3);
        }
    }

    public void unpack(int packed) {
        unpackInto(channels.get(0), packed);
        ejects.set(0, (packed >> 12 & 1) != 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("sides", pack());
        for (int i = 0; i < types.size(); i++) {
            tag.putInt("sides_" + types.get(i).key(), pack(i));
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("sides")) {
            int common = tag.getInt("sides");
            unpack(common);
            //Anciennes sauvegardes (un seul canal) : chaque ressource reprend le reglage commun
            for (int i = 0; i < types.size(); i++) {
                String key = "sides_" + types.get(i).key();
                int packed = tag.contains(key) ? tag.getInt(key) : common;
                unpackInto(channels.get(i), packed);
                //Anciennes sauvegardes : l'ejection commune vaut pour chaque ressource
                ejects.set(i, (packed >> 12 & 1) != 0);
            }
            for (SideMode[] modes : channels) {
                for (int i = 0; i < 6; i++) {
                    if (!allowed.contains(modes[i]) || locked[i]) {
                        modes[i] = SideMode.NONE;
                    }
                }
            }
        }
    }
}
