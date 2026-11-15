package fr.pioneers.client.kinetics;

import fr.pioneers.bronze.steam.SteamEngineBlock;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticMaterial;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.kinetics.kitchen.KitchenMachineBlock;
import fr.pioneers.kinetics.kitchen.KitchenMachineTile;
import fr.pioneers.kinetics.machines.MechanicalMachineBlock;
import fr.pioneers.kinetics.machines.MechanicalMachineTile;
import fr.pioneers.kinetics.parts.CogBlock;
import fr.pioneers.kinetics.parts.FlywheelBlock;
import fr.pioneers.kinetics.parts.GaugeBlock;
import fr.pioneers.kinetics.parts.GearboxBlock;
import fr.pioneers.kinetics.parts.GovernorBlock;
import fr.pioneers.kinetics.parts.HandCrankBlock;
import fr.pioneers.kinetics.parts.InlineBlock;
import fr.pioneers.kinetics.parts.ShaftBlock;
import fr.pioneers.kinetics.rope.PulleyBlock;
import fr.pioneers.kinetics.rope.PulleyTile;
import fr.pioneers.kinetics.rope.RopeRules;
import fr.pioneers.kinetics.rope.WinchBlock;
import fr.pioneers.kinetics.rope.WinchTile;
import fr.pioneers.kinetics.sources.BeamEngineBlock;
import fr.pioneers.kinetics.sources.HorseMillBlock;
import fr.pioneers.kinetics.sources.HorseMillTile;
import fr.pioneers.kinetics.sources.WaterWheelBlock;
import fr.pioneers.kinetics.sources.WeatherVaneBlock;
import fr.pioneers.kinetics.sources.WindmillBlock;
import fr.pioneers.kinetics.sources.WindmillTile;
import fr.pioneers.warehouse.LineNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Dessin des pieces tournantes, commun au monde (rendu du bloc-entite) et a l'inventaire (icone). Toutes les pieces sont dessinees
 * verticales (axe local Y) puis orientees. Style : chene huile cercle de fer noir, fer forge, jamais de couleurs plates.
 */
public final class KineticVisuals {

    private KineticVisuals() {
    }

    /** Dessine la partie mobile du bloc ; {@code tile} est null pour une icone (angle donne). */
    /** Fraction du tick de l'image en cours (posee par le rendu du bloc avant chaque dessin). */
    static float partial;

    public static void draw(KDraw d, BlockState state, @Nullable KineticTile tile, float angle, @Nullable BlockPos pos) {
        Block block = state.getBlock();
        if (block instanceof GearboxBlock || block instanceof InlineBlock || block instanceof GaugeBlock) {
            stubs(d, state, tile, angle);
        }
        if (block instanceof ShaftBlock shaft) {
            d.push().align(AxisKineticBlock.axis(state)).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            shaft(d, shaft.material, 0, 16);
            d.pop();
        } else if (block instanceof CogBlock cog) {
            boolean odd = pos != null && ((pos.getX() + pos.getY() + pos.getZ()) & 1) == 1;
            Direction.Axis axis = AxisKineticBlock.axis(state);
            boolean framed = fr.pioneers.core.block.Covers.framed(state);
            //1.0.5-beta (lot 23) : axe seulement vers un vrai bout d'arbre voisin ; icone et engrenage encadre : sans axe
            boolean neg = !framed && axleTo(tile, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE));
            boolean pos2 = !framed && axleTo(tile, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
            cogAt(d, axis, cog.large, cog.material, angle, odd, neg, pos2);
            if (framed) {
                d.push().align(axis);
                cogFrame(d);
                d.pop();
            }
        } else if (block instanceof FlywheelBlock flywheel) {
            d.push().align(AxisKineticBlock.axis(state)).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            flywheel(d, flywheel.iron);
            d.pop();
        } else if (block instanceof GaugeBlock) {
            float needle = tile instanceof GaugeBlock.Tile gauge ? gauge.needle() : 0.35F;
            dials(d, AxisKineticBlock.axis(state), needle, tile instanceof GaugeBlock.Tile gauge ? gauge.dial() : null);
        } else if (block instanceof GovernorBlock) {
            double target = tile instanceof GovernorBlock.Tile governor ? governor.targetSpeed() : 30;
            double ratio = tile == null ? 0.5 : Math.min(1, Math.abs(tile.getSpeed()) / target);
            d.push().align(AxisKineticBlock.axis(state)).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            governor(d, ratio);
            d.pop();
        } else if (block instanceof WindmillBlock) {
            int sails = tile instanceof WindmillTile w ? w.sails() : 4, type = tile instanceof WindmillTile w ? w.sailType() : 1;
            int radius = tile instanceof WindmillTile w ? w.radius() : 3;
            d.push().face(state.getValue(WindmillBlock.FACING)).rotate(Direction.Axis.Z, angle, 8, 8, 8);
            windmill(d, sails, type, radius);
            d.pop();
        } else if (block instanceof WaterWheelBlock wheel) {
            d.push().align(AxisKineticBlock.axis(state)).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            waterWheel(d, wheel.large);
            d.pop();
        } else if (block instanceof HorseMillBlock) {
            float arm = tile instanceof HorseMillTile mill ? mill.armAngle(partial) : angle;
            d.push().rotate(Direction.Axis.Y, -arm, 8, 8, 8);
            horseMill(d);
            d.pop();
        } else if (block instanceof WeatherVaneBlock) {
            vane(d, (float) (tile == null ? 35 : 0), angle);
        } else if (block instanceof MechanicalMachineBlock machine) {
            float progress = tile instanceof MechanicalMachineTile m ? m.progress() : 0;
            d.push().face(state.getValue(MechanicalMachineBlock.FACING));
            MachineVisuals.machine(d, machine.type, angle, progress, true);
            d.pop();
        } else if (block instanceof KitchenMachineBlock kitchen) {
            //1.6 : postes mecaniques de la cuisine
            d.push().face(state.getValue(KitchenMachineBlock.FACING));
            KitchenVisuals.machine(d, kitchen.type, angle, tile instanceof KitchenMachineTile k ? KitchenVisuals.look(k, partial) : KitchenVisuals.ICON);
            d.pop();
        } else if (block instanceof PulleyBlock pulley) {
            Direction.Axis axis = AxisKineticBlock.axis(state);
            PulleyTile p = tile instanceof PulleyTile t ? t : null;
            //1.0.4-beta (lot 2) : axe vertical sans arbre branche dessous : l'arbre s'arrete sous la roue (les colis passent dessous)
            boolean bare = axis == Direction.Axis.Y && p != null && p.getLevel() != null
                           && !(p.getLevel().getBlockEntity(p.getBlockPos().below()) instanceof fr.pioneers.kinetics.KineticNode n && n.shaftFactor(Direction.UP) != 0);
            //1.0.4-beta (lot 27) : petite poulie a axe vertical (roue des stations) montee de STATION_LIFT px ; plus de poteau d'entrepot
            float lift = axis == Direction.Axis.Y && pulley.size == 1 && p != null ? PulleyTile.STATION_LIFT : 0;
            d.push().align(axis).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            pulleyWheel(d, pulley.radiusPx(), bare, lift, axis == Direction.Axis.Y && p != null);
            if (p != null) {
                //1.0.4-beta (lots 2 et 27) : corde(s) enroulee(s) dans leur gorge (tournent avec la jante) ; une gorge par corde, en alternance
                for (int i = 0; i < PulleyTile.MAX_ROPES; i++) {
                    BlockPos to = p.partner(i);
                    if (to != null && (axis == Direction.Axis.Y ? !p.crossed(i) : p.ropeCount() > 1)) {
                        WarehouseVisuals.groove(d, axis == Direction.Axis.Y ? pulley.radiusPx() : pulley.radiusPx() - 1.2F, p.ropeYPx(i),
                              p.link(i).texture);
                    }
                }
            }
            d.pop();
            if (p != null && p.getLevel() != null) {
                for (int i = 0; i < PulleyTile.MAX_ROPES; i++) {
                    BlockPos to = p.partner(i);
                    if (to == null || !p.master(i) || !(p.getLevel().getBlockState(to).getBlock() instanceof PulleyBlock other)) {
                        continue;
                    }
                    BlockPos o = to.subtract(p.getBlockPos());
                    float yb = p.getLevel().getBlockEntity(to) instanceof PulleyTile q ? q.ropeYPx(p.getBlockPos()) : 8;
                    if (axis == Direction.Axis.Y && !p.crossed(i)) {
                        //1.0.4-beta (lot 2) : corde de ligne d'entrepot (axe vertical) : au rayon primitif, celui des colis et de la cinetique
                        //1.0.5-beta (lot 13) : la corde defile a la cadence des colis (LineNode.CADENCE) : le colis reste accroche a sa corde
                        float travel = p.ropeTravel(angle, pulley.radiusPx() * LineNode.CADENCE);
                        WarehouseVisuals.line(d, o.getX(), o.getY(), o.getZ(), pulley.radiusPx(), other.radiusPx(), p.ropeYPx(i), yb, p.link(i), travel);
                        continue;
                    }
                    //1.0.3-beta (lot 11) : defilement = longueur d'arc de la gorge (rayon - 1,2 px), cumule d'une image a l'autre par la tuile
                    //(avant : un nombre entier de motifs de 16 px par tour, vitesse fausse sur les petites poulies) ; 1.0.4-beta (lot 27) : chaque
                    //corde dans sa gorge (decalee le long de l'axe quand la poulie en porte plusieurs)
                    float ra = pulley.radiusPx() - 1.2F;
                    float travel = p.ropeTravel(angle, ra);
                    //1.0.5-beta (lot 15) : poulie decalee le long de l'axe (RopeRules) : la corde va en oblique jusqu'a la gorge de l'autre poulie
                    float along = 16F * RopeRules.along(axis, o.getX(), o.getY(), o.getZ());
                    MachineVisuals.rope(d, axis, o.getX(), o.getY(), o.getZ(), ra, other.radiusPx() - 1.2F, p.crossed(i), p.link(i).texture, travel,
                          p.ropeYPx(i) - 8, yb - 8 + along);
                }
            }
        } else if (block instanceof WinchBlock) {
            d.push().align(AxisKineticBlock.axis(state)).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            MachineVisuals.winchDrum(d, 0);
            d.pop();
            if (tile instanceof WinchTile winch) {
                MachineVisuals.winchLoad(d, (float) (winch.displayDepth(0) * 16), winch.mode() == WinchTile.Mode.WELL, winch.clientFull());
            }
        } else if (block instanceof SteamEngineBlock) {
            d.push().face(state.getValue(SteamEngineBlock.FACING));
            MachineVisuals.steamEngine(d, angle);
            d.pop();
        } else if (block instanceof BeamEngineBlock) {
            d.push().face(state.getValue(BeamEngineBlock.FACING));
            MachineVisuals.beamEngine(d, angle);
            d.pop();
        } else if (block instanceof HandCrankBlock) {
            Direction facing = state.getValue(HandCrankBlock.FACING);
            d.push().align(facing.getAxis());
            if (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                d.rotate(Direction.Axis.X, 180, 8, 8, 8);
            }
            d.rotate(Direction.Axis.Y, angle, 8, 8, 8);
            crank(d);
            d.pop();
        }
    }

    /**
     * 0.20.42 : bouts d'arbre des boitiers (renvoi, boite de vitesses, embrayage, inverseur, frein, cadrans, barillet) : ils tournent dans les
     * paliers perces du modele fixe, a l'angle de l'arbre qui sort par cette face (facteur de la face x angle du boitier), donc exactement
     * comme l'arbre voisin. Une face desaccouplee (embrayage ouvert) reste vide.
     */
    static void stubs(KDraw d, BlockState state, @Nullable KineticTile tile, float angle) {
        boolean all = state.getBlock() instanceof GearboxBlock;
        Direction.Axis axis = all ? null : AxisKineticBlock.axis(state);
        for (Direction face : Direction.values()) {
            if (!all && face.getAxis() != axis) {
                continue;
            }
            double k = tile != null ? tile.shaftFactor(face) : 1;
            if (k == 0) {
                continue;
            }
            stub(d, face, (float) (k * angle));
        }
    }

    /** Bout d'arbre de 2,5 px qui affleure la face {@code face}, tourne de {@code angle} autour du sens positif de son axe. */
    static void stub(KDraw d, Direction face, float angle) {
        d.push().align(face.getAxis()).rotate(Direction.Axis.Y, angle, 8, 8, 8);
        boolean up = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        d.octa(8, 8, AXLE, up ? 13.5F : 0, up ? 16 : 2.5F, "iron_bright", "iron_bright");
        d.pop();
    }

    // ------------------------------------------------------------------
    // Pieces (axe local Y, centre 8,8)
    // ------------------------------------------------------------------

    static String side(KineticMaterial m) {
        return m == KineticMaterial.WOOD ? "oak" : "iron_bright";
    }

    static String end(KineticMaterial m) {
        return m == KineticMaterial.WOOD ? "oak_end" : "iron";
    }

    /**
     * 0.20.42 : rayon commun de tous les arbres (octogone de 2 px) : bois, fer, volant, roue, regulateur, arbres des machines. Deux pieces
     * voisines tenues en phase par le reseau se raccordent donc sans marche ni decalage.
     */
    public static final float AXLE = 2.0F;

    /**
     * 1.0.4-beta (lot 27) : roue d'une poulie de base dans son repere aligne (axe local Y, deja tournee) : {@code bare} = pas d'arbre branche
     * dessous (l'arbre s'arrete sous la roue) ; {@code lift} = roue et gorges montees de tant de px (petite poulie a axe vertical : station),
     * l'arbre allant toujours jusqu'au haut du bloc ; {@code line} = joue basse en flasque de fer (ligne d'entrepot). Meme code au jeu et au
     * banc ({@code tools/kinetics/ExtraScenes} « spulley »).
     */
    public static void pulleyWheel(KDraw d, float radius, boolean bare, float lift, boolean line) {
        if (lift != 0) {
            if (!bare) {
                d.octa(8, 8, AXLE, 0, 5.6F + lift, "oak", "oak_end");
            }
            d.push().translate(0, lift, 0);
            MachineVisuals.pulley(d, radius, 5.6F, 16 - lift, true);
            d.pop();
        } else {
            MachineVisuals.pulley(d, radius, bare ? 5.6F : 0, line);
        }
    }

    public static void shaft(KDraw d, KineticMaterial m, float y0, float y1) {
        //1.0.4-beta (lot 27, audit de fluidite) : arbre rigide (l'angle est dans la pose) : en cache
        d.rigid(new ShaftPart(m, y0, y1), r -> shaftBody(r, m, y0, y1));
    }

    /** Cles des parties rigides en cache (KDraw.rigid). */
    private record ShaftPart(KineticMaterial m, float y0, float y1) {
    }

    private record Teeth(KineticMaterial m, boolean large) {
    }

    private static void shaftBody(KDraw d, KineticMaterial m, float y0, float y1) {
        if (m == KineticMaterial.WOOD) {
            d.octa(8, 8, AXLE, y0, y1, "oak", "oak_end");
            if (y1 - y0 > 10) {
                d.octa(8, 8, AXLE + 0.35F, y0 + 2, y0 + 3, "iron", "iron");
                d.octa(8, 8, AXLE + 0.35F, y1 - 3, y1 - 2, "iron", "iron");
            }
        } else {
            d.octa(8, 8, AXLE, y0, y1, "iron_bright", "iron");
        }
    }

    /**
     * Engrenage pose (monde et banc d'essai) : l'arbre tourne a l'angle du reseau, comme ses voisins ; seule la denture recoit le decalage
     * d'engrenement (demi-dent sur une case sur deux pour les pignons, demi-dent pour les roues).
     */
    static void cogAt(KDraw d, Direction.Axis axis, boolean large, KineticMaterial m, float angle, boolean odd) {
        cogAt(d, axis, large, m, angle, odd, true, true);
    }

    /**
     * 1.0.5-beta (lot 23, Guillaume : « par defaut les engrenages n'ont plus d'axe au centre ; les textures se connectent si besoin ») : bout
     * d'arbre dessine seulement vers la face qui a un vrai voisin a bout d'arbre ({@code neg} : face negative de l'axe, {@code pos} : positive),
     * du bord du bloc jusque dans le moyeu ({@link #AXLE_IN}) ; sans voisin, le moyeu reste plein (pas d'axe qui depasse dans le vide).
     */
    static void cogAt(KDraw d, Direction.Axis axis, boolean large, KineticMaterial m, float angle, boolean odd, boolean neg, boolean pos) {
        float phase = phase(large, odd);
        d.push().align(axis).rotate(Direction.Axis.Y, angle, 8, 8, 8);
        if (neg && pos) {
            shaft(d, m, 0, 16);
        } else if (neg) {
            shaft(d, m, 0, AXLE_IN);
        } else if (pos) {
            shaft(d, m, 16 - AXLE_IN, 16);
        }
        d.pop();
        d.push().align(axis).rotate(Direction.Axis.Y, angle + phase, 8, 8, 8);
        d.rigid(new Teeth(m, large), r -> {                                                   //1.0.4-beta (lot 27) : denture en cache
            if (large) {
                cogwheelTeeth(r, m);
            } else {
                cogTeeth(r, m);
            }
        });
        d.pop();
    }

    /**
     * Decalage de la denture (degres) : pignon d'une case impaire une demi-dent (22,5) ; rouet : 11,25 sur une case paire, 0 sur une case
     * impaire. 1.0.5-beta (lot 15) : avant, le rouet avait toujours 11,25 : en diagonale (pignon de meme parite que le rouet, rapport -2), il
     * fallait 2 x decalage du rouet + decalage du pignon = 22,5 (mod 45) : vrai seulement sur les cases paires ; sur les cases impaires les
     * dents tombaient dent contre dent (banc tools/kinetics/gear_mesh.py).
     */
    public static float phase(boolean large, boolean odd) {
        return large ? odd ? 0F : 11.25F : odd ? 22.5F : 0F;
    }

    static void cog(KDraw d, KineticMaterial m) {
        shaft(d, m, 0, 16);
        cogTeeth(d, m);
    }

    /**
     * 1.0.5-beta (lot 15, Guillaume : « textures connectees des engrenages en bois et en fer largement ameliorees ») : PIGNON de 8 dents.
     * Corps en disque a 16 pans ({@link #disc} : quatre pavés tournes de 22,5 degres, couvercles decales : aucune face confondue), moyeu
     * de fer, dents a pied large et tete etroite qui ENGRENENT vraiment : rayon primitif ~7,5 px (deux pignons voisins a 16 px, ou un
     * pignon et un rouet en diagonale a 22,6 px : dents dans les creux du voisin, jeu >= 0,5 px a tous les angles, banc
     * {@code T:kinetics/gear_mesh.py}). Avant : dents de 5 a 7,4 px qui ne se touchaient pas (1,2 px de vide entre deux pignons).
     */
    static void cogTeeth(KDraw d, KineticMaterial m) {
        boolean wood = m == KineticMaterial.WOOD;
        //1.0.5-beta (lot 15) : fer lisible : dents d'acier clair, faces d'acier, flanc du corps en fer sombre (contraste)
        String body = wood ? "oak" : "iron", face = wood ? "cog_face" : "steel_plate", tooth = wood ? "oak_dark" : "steel";
        disc(d, COG_BODY, 6.0F, 10.0F, body, face);
        hub(d, 3.0F, 4.6F, 11.4F, wood ? "iron" : "iron_bright", "iron");
        for (int k = 0; k < 8; k++) {
            d.push().rotate(Direction.Axis.Y, 45 * k, 8, 8, 8);
            d.box(6.85F, 6.3F, 8 + COG_ROOT, 9.15F, 9.7F, 8 + COG_PITCH + 0.4F, tooth, end(m));
            d.box(7.2F, 6.5F, 8 + COG_PITCH + 0.4F, 8.8F, 9.5F, 8 + COG_TIP, tooth, end(m));
            d.pop();
        }
    }

    /** 1.0.5-beta (lot 23) : un bout d'arbre seul s'arrete dans le moyeu (moyeux de 4,6 a 11,4 px : bout de 0 a 6, ou de 10 a 16). */
    public static final float AXLE_IN = 6F;

    /**
     * 1.0.5-beta (lot 23) : y a-t-il un vrai bout d'arbre voisin sur la face {@code face} de l'engrenage (meme regle que les liens :
     * {@code KineticNode.shaftFactor} non nul des deux cotes) ? Faux pour une icone (pas de tuile).
     */
    static boolean axleTo(@Nullable KineticTile tile, Direction face) {
        return tile instanceof fr.pioneers.kinetics.parts.CogTile cog && cog.axleTowards(face);
    }

    /** PUR (banc) : la regle de {@link #axleTo} sur les deux facteurs de face (le mien, celui du voisin ; NaN : pas de voisin). */
    public static boolean axleRule(double mine, double theirs) {
        return mine != 0 && !Double.isNaN(theirs) && theirs != 0;
    }

    /**
     * 1.0.5-beta (lot 23) : CADRE de l'engrenage (cadre de cache en bois pose dessus), fixe, dans le repere aligne (axe local Y) : sur chaque
     * face de l'axe, une flasque carree de chene (quatre traverses de 2 px au bord, comme le cadre de cache), deux echarpes en croix qui
     * tiennent un palier de fer, et le pivot de fer qui entre dans le moyeu. Rien dans le plan de la denture (y 2 a 14 libres hors pivot) :
     * les engrenements avec les voisins restent libres.
     */
    static void cogFrame(KDraw d) {
        float t = CogBlock.FRAME_T;
        for (int side = 0; side < 2; side++) {
            float y0 = side == 0 ? 0 : 16 - t, y1 = side == 0 ? t : 16;
            d.box(0, y0, 0, 16, y1, 2, "oak", "oak_end");                        //traverses du bord
            d.box(0, y0, 14, 16, y1, 16, "oak", "oak_end");
            //traverses laterales 0,2 px plus minces : jamais dans le plan d'une autre (le gonflement de KDraw va jusqu'a 0,165 px)
            d.box(0, y0 + 0.2F, 2, 2, y1 - 0.2F, 14, "oak", "oak_end");
            d.box(14, y0 + 0.2F, 2, 16, y1 - 0.2F, 14, "oak", "oak_end");
            for (int k = 0; k < 2; k++) {                                       //echarpes en croix (d'angle a angle)
                d.push().rotate(Direction.Axis.Y, 45 + 90 * k, 8, 8, 8);
                d.box(7.1F, y0 + 0.3F + 0.05F * k, -2.2F, 8.9F, y1 - 0.3F - 0.05F * k, 18.2F, "oak_dark", "oak_end");
                d.pop();
            }
            d.box(5.5F, side == 0 ? y0 + 0.2F : y0 - 0.4F, 5.5F, 10.5F, side == 0 ? y1 + 0.4F : y1 - 0.2F, 10.5F, "iron", "iron_bright");  //palier
            for (float[] c : new float[][]{{0.5F, 0.5F}, {14.5F, 0.5F}, {0.5F, 14.5F}, {14.5F, 14.5F}}) {   //equerres de fer aux angles
                d.box(c[0] - 0.2F, side == 0 ? y1 : y0 - 0.25F, c[1] - 0.2F, c[0] + 1.2F, side == 0 ? y1 + 0.25F : y0, c[1] + 1.2F, "iron", "iron");
            }
        }
        d.octa(8, 8, 1.4F, t + 0.4F, 16 - t - 0.4F, "iron_bright", "iron");    //pivot fixe (traverse le moyeu)
    }

    /** 1.0.5-beta (lot 15) : rayons du pignon (px) : corps, pied et tete des dents ; rayon primitif ~7,5. */
    public static final float COG_BODY = 5.6F, COG_ROOT = 5.3F, COG_PITCH = 7.5F, COG_TIP = 8.8F;
    /** 1.0.5-beta (lot 15) : rayons du rouet (px) : jante, pied et tete des dents ; rayon primitif ~15,1 (rapport 2 au pignon). */
    public static final float WHEEL_RIM_IN = 11.6F, WHEEL_RIM = 13.0F, WHEEL_ROOT = 12.8F, WHEEL_PITCH = 15.0F, WHEEL_TIP = 16.0F;

    /**
     * 1.0.5-beta (lot 15) : VRAI disque a 16 pans d'axe Y (apotheme {@code r}) : huit bandes passant par le centre, tournees de 22,5 degres,
     * larges d'un cote du polygone (2 r tan 11,25) : leurs bouts sont exactement les cotes, rien ne depasse (un carre tourne aurait des
     * pointes en etoile a r x 1,41) ; chaque bande 0,03 px plus courte a chaque bout que la precedente (couvercles jamais confondus).
     */
    static void disc(KDraw d, float r, float y0, float y1, String side, String ends) {
        float a = r * (float) Math.tan(Math.PI / 16) + 0.02F;
        for (int k = 0; k < 8; k++) {
            d.push().rotate(Direction.Axis.Y, 22.5F * k, 8, 8, 8);
            d.box(8 - a, y0 + 0.03F * k, 8 - r, 8 + a, y1 - 0.03F * k, 8 + r, side, ends);
            d.pop();
        }
    }

    /** Moyeu octogonal sans couvercles confondus (le prisme tourne a 45 degres est 0,12 px plus court a chaque bout). */
    static void hub(KDraw d, float r, float y0, float y1, String side, String ends) {
        d.box(8 - r, y0, 8 - r, 8 + r, y1, 8 + r, side, ends);
        d.push().rotate(Direction.Axis.Y, 45, 8, 8, 8);
        d.box(8 - r, y0 + 0.12F, 8 - r, 8 + r, y1 - 0.12F, 8 + r, side, ends);
        d.pop();
    }

    static void cogwheel(KDraw d, KineticMaterial m) {
        shaft(d, m, 0, 16);
        cogwheelTeeth(d, m);
    }

    /**
     * 1.0.5-beta (lot 15) : ROUET de 16 dents (engrene en diagonale avec un pignon, rapport 2) : moyeu, quatre rayons, jante en 16 segments
     * (un sur deux 0,1 px plus mince : leurs dessus ne sont jamais confondus), dents a pied large et tete etroite ; jante du bois bandee de fer
     * sur ses deux faces. Avant : jante et dents dans les memes plans (coutures), dents trop courtes pour toucher le pignon.
     */
    static void cogwheelTeeth(KDraw d, KineticMaterial m) {
        boolean wood = m == KineticMaterial.WOOD;
        String s = side(m), e = wood ? "oak_planks" : "steel_plate", tooth = wood ? "oak_dark" : "steel";
        hub(d, 3.4F, 4.8F, 11.2F, wood ? "iron" : "iron_bright", "iron");
        for (int k = 0; k < 4; k++) {                               //rayons
            d.push().rotate(Direction.Axis.Y, 45 + 90 * k, 8, 8, 8);
            d.box(7.0F, 6.8F, 8 + 3.0F, 9.0F, 9.2F, 8 + WHEEL_RIM_IN + 0.4F, s, e);
            d.pop();
        }
        float tan = (float) Math.tan(Math.PI / 16);
        for (int k = 0; k < 16; k++) {                              //jante et dents (alluchons)
            float t = (k & 1) == 0 ? 0 : 0.1F;
            d.push().rotate(Direction.Axis.Y, 22.5F * k, 8, 8, 8);
            float h = WHEEL_RIM * tan + 0.05F;
            d.box(8 - h, 6.2F + t, 8 + WHEEL_RIM_IN, 8 + h, 9.8F - t, 8 + WHEEL_RIM, s, e);
            if (wood) {                                             //bandes de fer sur les deux faces de la jante (pas sur le pourtour : jeu du pignon)
                float g = (WHEEL_RIM - 0.3F) * tan + 0.05F;
                d.box(8 - g, 9.8F, 8 + WHEEL_RIM_IN + 0.4F, 8 + g, 10.0F - t, 8 + WHEEL_RIM - 0.3F, "iron", "iron");
                d.box(8 - g, 6.0F + t, 8 + WHEEL_RIM_IN + 0.4F, 8 + g, 6.2F, 8 + WHEEL_RIM - 0.3F, "iron", "iron");
            }
            d.pop();
            d.push().rotate(Direction.Axis.Y, 22.5F * k, 8, 8, 8);
            d.box(6.85F, 6.4F, 8 + WHEEL_ROOT, 9.15F, 9.6F, 8 + WHEEL_PITCH + 0.4F, tooth, end(m));
            d.box(7.2F, 6.6F, 8 + WHEEL_PITCH + 0.4F, 8.8F, 9.4F, 8 + WHEEL_TIP, tooth, end(m));
            d.pop();
        }
    }

    static void flywheel(KDraw d, boolean iron) {
        String s = iron ? "iron" : "stone", e = iron ? "iron_plate" : "stone";
        d.octa(8, 8, AXLE, 0, 16, "iron_bright", "iron");
        d.octa(8, 8, 4.0F, 4, 12, s, e);
        for (int k = 0; k < 6; k++) {
            d.push().rotate(Direction.Axis.Y, 60 * k, 8, 8, 8);
            d.box(6.8F, 6.0F, 10.0F, 9.2F, 10.0F, 19.0F, s, e);
            d.pop();
        }
        for (int k = 0; k < 16; k++) {
            d.push().rotate(Direction.Axis.Y, 22.5F * k, 8, 8, 8);
            d.box(4.9F, 4.5F, 18.5F, 11.1F, 11.5F, 22.5F, s, e);
            d.pop();
        }
    }

    static void governor(KDraw d, double ratio) {
        d.octa(8, 8, AXLE, 0, 2.5F, "iron_bright", "iron");               //bouts d'arbre au rayon commun
        d.octa(8, 8, AXLE, 14.5F, 16, "iron_bright", "iron");
        d.octa(8, 8, 1.2F, 2.5F, 14.5F, "iron_bright", "iron");
        d.octa(8, 8, 2.2F, 12.5F, 14.5F, "copper", "copper");          //tete articulee
        d.octa(8, 8, 2.0F, 3.0F, 4.5F, "copper", "copper");
        double theta = Math.toRadians(12 + 48 * ratio);
        float bx = (float) (Math.sin(theta) * 6.5), by = (float) (13.5 - Math.cos(theta) * 6.5);
        for (int side = -1; side <= 1; side += 2) {
            float x = 8 + side * bx;
            d.beam(8 + side * 1.5F, 13.5F, 8, x, by, 8, 0.8F, "iron");
            d.beam(x, by, 8, 8 + side * 1.0F, 4.0F + (float) (ratio * 4), 8, 0.6F, "iron");
            d.box(x - 1.8F, by - 1.8F, 6.2F, x + 1.8F, by + 1.8F, 9.8F, "iron_bright", "iron_bright");
        }
    }

    /** Rotor du moulin : axe local Z (vers l'avant), ailes dans le plan XY. */
    static void windmill(KDraw d, int sails, int type, int radius) {
        d.box(5.5F, 5.5F, 9.0F, 10.5F, 10.5F, 15.0F, "iron", "iron");                    //moyeu
        d.box(6.5F, 6.5F, 15.0F, 9.5F, 9.5F, 16.5F, "iron_bright", "iron_bright");
        float len = radius * 16F;
        for (int k = 0; k < sails; k++) {
            d.push().rotate(Direction.Axis.Z, k * 360F / Math.max(4, sails <= 4 ? 4 : 8), 8, 8, 8);
            d.box(7.0F, 9.0F, 11.5F, 9.0F, 8 + len, 13.5F, "oak", "oak_end");          //vergue
            float w = Math.min(22, len * 0.32F), y0 = 8 + Math.min(18, len * 0.22F);
            if (type == 2) {
                for (float y = y0; y < 8 + len - 2; y += 6) {                             //planches
                    d.box(9.0F, y, 12.0F, 9.0F + w, y + 5.2F, 13.0F, "oak_planks", "oak_planks");
                }
            } else {
                d.box(9.0F, y0, 12.4F, 9.0F + w, 8 + len - 1, 12.8F, "canvas", "canvas");  //toile
                for (float y = y0; y < 8 + len; y += 10) {                                //lattes
                    d.box(9.0F, y, 12.8F, 9.0F + w, y + 1.2F, 13.6F, "oak_dark", "oak_dark");
                }
                d.box(8.6F + w, y0, 12.6F, 9.8F + w, 8 + len - 1, 13.6F, "oak_dark", "oak_dark");
            }
            d.pop();
        }
    }

    /** Roue a aubes : axe local Y, largeur 14 px. */
    static void waterWheel(KDraw d, boolean large) {
        float r = large ? 38F : 22F;
        int seg = large ? 24 : 16, spokes = large ? 8 : 6, paddles = large ? 16 : 10;
        d.octa(8, 8, AXLE, 0, 16, "oak", "oak_end");
        d.octa(8, 8, 4.0F, 2, 14, "iron", "iron");
        for (int side = 0; side < 2; side++) {
            float y0 = side == 0 ? 1.0F : 13.0F;
            for (int k = 0; k < seg; k++) {                                              //jantes
                d.push().rotate(Direction.Axis.Y, 360F / seg * k, 8, 8, 8);
                d.box(8 - r * (float) Math.tan(Math.PI / seg) - 0.6F, y0, 8 + r - 2.5F, 8 + r * (float) Math.tan(Math.PI / seg) + 0.6F, y0 + 2, 8 + r,
                      "oak_dark", "oak_dark");
                d.pop();
            }
            for (int k = 0; k < spokes; k++) {                                           //rayons
                d.push().rotate(Direction.Axis.Y, 360F / spokes * k + (side == 0 ? 0 : 180F / spokes), 8, 8, 8);
                d.box(7.0F, y0 + 0.2F, 11.0F, 9.0F, y0 + 1.8F, 8 + r - 2, "oak", "oak");
                d.pop();
            }
        }
        for (int k = 0; k < paddles; k++) {                                              //aubes
            d.push().rotate(Direction.Axis.Y, 360F / paddles * k, 8, 8, 8);
            d.box(7.2F, 1.5F, 8 + r - 9, 8.8F, 14.5F, 8 + r + 2.5F, "oak_planks", "oak_planks");
            d.pop();
        }
    }

    /** Bras du manege (rayon 3 blocs), pivote autour de Y. */
    static void horseMill(KDraw d) {
        d.octa(8, 8, 3.0F, 10, 16, "oak", "oak_end");
        for (int k = 0; k < 4; k++) {
            d.push().rotate(Direction.Axis.Y, 90 * k, 8, 8, 8);
            d.box(6.8F, 11.0F, 10.0F, 9.2F, 13.4F, 56.0F, "oak", "oak_end");                  //bras
            d.box(6.4F, 12.6F, 10.0F, 9.6F, 13.2F, 14.0F, "iron", "iron");
            d.box(5.0F, 7.0F, 50.0F, 11.0F, 11.0F, 53.0F, "leather", "leather");              //collier
            d.beam(8, 11.0F, 46.0F, 8, 6.0F, 54.0F, 1.2F, "rope");
            d.pop();
        }
    }

    /** Girouette : fleche a l'angle du vent, coupelles qui tournent ({@code spin}). */
    static void vane(KDraw d, float direction, float spin) {
        d.push().rotate(Direction.Axis.Y, direction, 8, 8, 8);
        d.box(7.4F, 13.0F, 1.0F, 8.6F, 14.0F, 15.0F, "iron", "iron");
        d.box(7.6F, 12.0F, 12.0F, 8.4F, 15.0F, 16.0F, "copper", "copper");                   //empennage
        d.box(7.0F, 12.6F, 0.0F, 9.0F, 14.4F, 2.0F, "copper", "copper");                     //pointe
        d.pop();
        d.push().rotate(Direction.Axis.Y, spin, 8, 8, 8);
        for (int k = 0; k < 3; k++) {
            d.push().rotate(Direction.Axis.Y, 120 * k, 8, 8, 8);
            d.box(7.6F, 15.4F, 8.0F, 8.4F, 15.9F, 14.0F, "iron", "iron");
            d.box(6.6F, 14.6F, 13.0F, 9.4F, 16.6F, 15.6F, "copper", "copper");
            d.pop();
        }
        d.pop();
    }

    static void crank(KDraw d) {
        d.octa(8, 8, AXLE, 9, 16, "oak", "oak_end");
        d.octa(8, 8, 2.6F, 13, 14.5F, "iron", "iron");
        d.box(6.8F, 9.0F, 6.8F, 9.2F, 11.0F, 15.0F, "iron", "iron");        //bras
        d.octa(8, 14.0F, 1.1F, 2.0F, 11.0F, "oak", "oak_end");              //poignee
        d.octa(8, 14.0F, 1.5F, 1.4F, 2.4F, "iron", "iron");
    }

    /** Cadrans sur les deux flancs perpendiculaires a l'axe ; aiguille de 0 (gauche) a 1 (droite). */
    static void dials(KDraw d, Direction.Axis axis, float needle) {
        dials(d, axis, needle, null);
    }

    /** 1.0.2-beta : cadrans sur la face {@code dial} (tournee vers le joueur a la pose) et son dos ; null : faces d'avant. */
    static void dials(KDraw d, Direction.Axis axis, float needle, @org.jetbrains.annotations.Nullable Direction dial) {
        Direction[] faces = dial != null ? new Direction[]{dial, dial.getOpposite()}
                            : axis == Direction.Axis.Z ? new Direction[]{Direction.EAST, Direction.WEST} : new Direction[]{Direction.NORTH, Direction.SOUTH};
        for (Direction face : faces) {
            d.push().face(face);
            //Face avant locale = +Z (z = 16)
            d.box(2.5F, 2.5F, 16.0F, 13.5F, 13.5F, 16.15F, KDraw.sprite("dial"), KDraw.sprite("dial"));
            d.push().rotate(Direction.Axis.Z, 135 - 270 * Math.max(0, Math.min(1, needle)), 8, 8, 16.3F);
            d.box(7.6F, 7.6F, 16.15F, 8.4F, 12.6F, 16.35F, "needle", "needle");
            d.pop();
            d.box(7.2F, 7.2F, 16.15F, 8.8F, 8.8F, 16.45F, "iron", "iron");
            d.pop();
        }
    }
}
