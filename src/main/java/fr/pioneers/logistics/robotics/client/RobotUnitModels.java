package fr.pioneers.logistics.robotics.client;

import fr.pioneers.Pioneers;
import fr.pioneers.logistics.robotics.UnitKind;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Maquettes des unites robotiques.
 * FICHIER GENERE (scratchpad gc/robotics/units.py) : la meme description produit ce code et les textures entity/robotics/*.png.
 */
public final class RobotUnitModels {

    private RobotUnitModels() {
    }

    public static ModelLayerLocation layer(UnitKind kind) {
        return new ModelLayerLocation(Pioneers.rl("unit_" + kind.id()), "main");
    }

    public static LayerDefinition create(UnitKind kind) {
        return switch (kind) {
            case DRONE -> drone();
            case ROBOT -> robot();
            case ROVER -> rover();
            case TURTLE -> turtle();
        };
    }

    /** Rover minier de l'avant-poste (rover + bras de forage). */
    public static final ModelLayerLocation ROVER_DRILL = new ModelLayerLocation(Pioneers.rl("unit_rover_drill"), "main");

    public static LayerDefinition roverDrillLayer() {
        return roverDrill();
    }

    /** Equipement du toit du hub robotique. */
    public static final ModelLayerLocation HUB_TOP = new ModelLayerLocation(Pioneers.rl("robotic_hub_top"), "main");

    public static LayerDefinition hubTopLayer() {
        return hubTop();
    }

    private static LayerDefinition drone() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -2F, -4F, 8F, 2F, 8F).texOffs(32, 0).addBox(-3F, -3F, -3F, 6F, 1F, 6F).texOffs(56, 0).addBox(-1F, -4F, -1F, 2F, 1F, 2F).texOffs(0, 10).addBox(-1.5F, -2F, -5F, 3F, 2F, 1F).texOffs(8, 10).addBox(-2.5F, 0F, -2.5F, 5F, 1F, 5F).texOffs(28, 10).addBox(4F, -2F, -1F, 3F, 1F, 2F).texOffs(38, 10).addBox(-7F, -2F, -1F, 3F, 1F, 2F).texOffs(48, 10).addBox(-1F, -2F, 4F, 2F, 1F, 3F).texOffs(0, 16).addBox(-1F, -2F, -7F, 2F, 1F, 3F), PartPose.offset(0F, 19F, 0F)));
        parts.put("duct0", parts.get("body").addOrReplaceChild("duct0", CubeListBuilder.create().texOffs(10, 16).addBox(-3F, -1F, -3F, 6F, 2F, 1F).texOffs(24, 16).addBox(-3F, -1F, 2F, 6F, 2F, 1F).texOffs(38, 16).addBox(-3F, -1F, -2F, 1F, 2F, 4F).texOffs(48, 16).addBox(2F, -1F, -2F, 1F, 2F, 4F).texOffs(58, 16).addBox(-0.5F, 0.5F, -0.5F, 1F, 1F, 1F).texOffs(0, 22).addBox(3F, 0F, -0.5F, 1F, 1F, 1F), PartPose.offset(9F, -1F, 0F)));
        parts.put("rotor0", parts.get("body").addOrReplaceChild("rotor0", CubeListBuilder.create().texOffs(4, 22).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F).texOffs(16, 22).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F), PartPose.offset(9F, -1.4F, 0F)));
        parts.put("duct1", parts.get("body").addOrReplaceChild("duct1", CubeListBuilder.create().texOffs(28, 22).addBox(-3F, -1F, -3F, 6F, 2F, 1F).texOffs(42, 22).addBox(-3F, -1F, 2F, 6F, 2F, 1F).texOffs(0, 27).addBox(-3F, -1F, -2F, 1F, 2F, 4F).texOffs(10, 27).addBox(2F, -1F, -2F, 1F, 2F, 4F).texOffs(20, 27).addBox(-0.5F, 0.5F, -0.5F, 1F, 1F, 1F).texOffs(24, 27).addBox(-4F, 0F, -0.5F, 1F, 1F, 1F), PartPose.offset(-9F, -1F, 0F)));
        parts.put("rotor1", parts.get("body").addOrReplaceChild("rotor1", CubeListBuilder.create().texOffs(28, 27).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F).texOffs(40, 27).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F), PartPose.offset(-9F, -1.4F, 0F)));
        parts.put("duct2", parts.get("body").addOrReplaceChild("duct2", CubeListBuilder.create().texOffs(0, 33).addBox(-3F, -1F, -3F, 6F, 2F, 1F).texOffs(14, 33).addBox(-3F, -1F, 2F, 6F, 2F, 1F).texOffs(28, 33).addBox(-3F, -1F, -2F, 1F, 2F, 4F).texOffs(38, 33).addBox(2F, -1F, -2F, 1F, 2F, 4F).texOffs(48, 33).addBox(-0.5F, 0.5F, -0.5F, 1F, 1F, 1F).texOffs(52, 33).addBox(-0.5F, 0F, 3F, 1F, 1F, 1F), PartPose.offset(0F, -1F, 9F)));
        parts.put("rotor2", parts.get("body").addOrReplaceChild("rotor2", CubeListBuilder.create().texOffs(0, 39).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F).texOffs(12, 39).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F), PartPose.offset(0F, -1.4F, 9F)));
        parts.put("duct3", parts.get("body").addOrReplaceChild("duct3", CubeListBuilder.create().texOffs(24, 39).addBox(-3F, -1F, -3F, 6F, 2F, 1F).texOffs(38, 39).addBox(-3F, -1F, 2F, 6F, 2F, 1F).texOffs(52, 39).addBox(-3F, -1F, -2F, 1F, 2F, 4F).texOffs(0, 45).addBox(2F, -1F, -2F, 1F, 2F, 4F).texOffs(10, 45).addBox(-0.5F, 0.5F, -0.5F, 1F, 1F, 1F).texOffs(14, 45).addBox(-0.5F, 0F, -4F, 1F, 1F, 1F), PartPose.offset(0F, -1F, -9F)));
        parts.put("rotor3", parts.get("body").addOrReplaceChild("rotor3", CubeListBuilder.create().texOffs(18, 45).addBox(-2.5F, 0F, -0.5F, 5F, 0F, 1F).texOffs(30, 45).addBox(-0.5F, 0F, -2.5F, 1F, 0F, 5F), PartPose.offset(0F, -1.4F, -9F)));
        parts.put("claw", parts.get("body").addOrReplaceChild("claw", CubeListBuilder.create().texOffs(42, 45).addBox(-1F, 1F, -1F, 2F, 1F, 2F).texOffs(50, 45).addBox(-2F, 2F, -0.5F, 1F, 2F, 1F).texOffs(54, 45).addBox(1F, 2F, -0.5F, 1F, 2F, 1F), PartPose.offset(0F, 1F, 0F)));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition robot() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -2F, -2F, 7F, 2F, 4F).texOffs(22, 0).addBox(-4.5F, -10F, -2.5F, 9F, 8F, 5F).texOffs(0, 13).addBox(-3.5F, -9F, -3.5F, 7F, 5F, 1F).texOffs(16, 13).addBox(-1F, -7F, -4F, 2F, 2F, 1F).texOffs(22, 13).addBox(-3F, -10F, 2.5F, 6F, 7F, 3F).texOffs(40, 13).addBox(-2F, -8F, 5.5F, 4F, 3F, 1F), PartPose.offset(0F, 11F, 0F)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 23).addBox(-3F, -5F, -3F, 6F, 5F, 6F).texOffs(24, 23).addBox(-2.5F, -3.5F, -3.5F, 5F, 2F, 1F).texOffs(36, 23).addBox(-3.5F, -3F, -1F, 1F, 2F, 2F).texOffs(42, 23).addBox(2.5F, -3F, -1F, 1F, 2F, 2F).texOffs(48, 23).addBox(1F, -8F, 0F, 1F, 3F, 1F).texOffs(52, 23).addBox(0.5F, -9F, -0.5F, 2F, 1F, 2F), PartPose.offset(0F, -10F, 0F)));
        parts.put("right_arm", parts.get("body").addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 34).addBox(-2F, -1F, -2F, 4F, 4F, 4F).texOffs(16, 34).addBox(-1F, 2F, -1F, 2F, 8F, 2F).texOffs(24, 34).addBox(-1.5F, 10F, -1.5F, 3F, 2F, 3F), PartPose.offset(-6F, -9F, 0F)));
        parts.put("left_arm", parts.get("body").addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(36, 34).addBox(-2F, -1F, -2F, 4F, 4F, 4F).texOffs(52, 34).addBox(-1F, 2F, -1F, 2F, 8F, 2F).texOffs(0, 44).addBox(-1.5F, 10F, -1.5F, 3F, 2F, 3F), PartPose.offset(6F, -9F, 0F)));
        parts.put("right_leg", root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(12, 44).addBox(-1.5F, 0F, -1.5F, 3F, 6F, 3F).texOffs(24, 44).addBox(-2F, 6F, -2F, 4F, 5F, 4F).texOffs(40, 44).addBox(-2F, 11F, -3F, 4F, 2F, 5F).texOffs(0, 53).addBox(-1.5F, 7F, -2.5F, 3F, 2F, 1F), PartPose.offset(-2F, 11F, 0F)));
        parts.put("left_leg", root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(8, 53).addBox(-1.5F, 0F, -1.5F, 3F, 6F, 3F).texOffs(20, 53).addBox(-2F, 6F, -2F, 4F, 5F, 4F).texOffs(36, 53).addBox(-2F, 11F, -3F, 4F, 2F, 5F).texOffs(54, 53).addBox(-1.5F, 7F, -2.5F, 3F, 2F, 1F), PartPose.offset(2F, 11F, 0F)));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static LayerDefinition rover() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("chassis", root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -3F, -10F, 10F, 3F, 20F).texOffs(60, 0).addBox(-6F, -4F, -9F, 12F, 1F, 18F).texOffs(0, 23).addBox(-6F, -3F, -11F, 12F, 2F, 1F).texOffs(26, 23).addBox(-6F, -3F, 10F, 12F, 2F, 1F).texOffs(52, 23).addBox(-5F, -3F, -11.5F, 2F, 1F, 1F).texOffs(58, 23).addBox(3F, -3F, -11.5F, 2F, 1F, 1F).texOffs(64, 23).addBox(-5F, -3F, 10.5F, 2F, 1F, 1F).texOffs(70, 23).addBox(3F, -3F, 10.5F, 2F, 1F, 1F), PartPose.offset(0F, 19F, 0F)));
        parts.put("cab", parts.get("chassis").addOrReplaceChild("cab", CubeListBuilder.create().texOffs(76, 23).addBox(-5F, -5F, -3F, 10F, 5F, 6F).texOffs(108, 23).addBox(-4F, -4F, -3.5F, 8F, 3F, 1F).texOffs(0, 34).addBox(-5F, -1F, -3F, 10F, 1F, 6F).texOffs(32, 34).addBox(-3F, -6F, -2F, 6F, 1F, 2F).texOffs(48, 34).addBox(-4F, -6F, 0F, 8F, 1F, 3F), PartPose.offset(0F, -4F, -6F)));
        parts.put("antenna", parts.get("cab").addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(70, 34).addBox(-0.5F, -5F, -0.5F, 1F, 5F, 1F).texOffs(74, 34).addBox(-1F, -6F, -1F, 2F, 1F, 2F), PartPose.offset(4F, -5F, 2F)));
        parts.put("bed", parts.get("chassis").addOrReplaceChild("bed", CubeListBuilder.create().texOffs(82, 34).addBox(-5.5F, -1F, -5F, 11F, 1F, 11F).texOffs(0, 46).addBox(-6F, -3F, -5F, 1F, 3F, 11F).texOffs(24, 46).addBox(5F, -3F, -5F, 1F, 3F, 11F).texOffs(48, 46).addBox(-6F, -3F, 6F, 12F, 3F, 1F), PartPose.offset(0F, -4F, 4F)));
        parts.put("boom", parts.get("chassis").addOrReplaceChild("boom", CubeListBuilder.create().texOffs(74, 46).addBox(-1F, -4F, -1F, 2F, 4F, 2F).texOffs(82, 46).addBox(-1F, -5F, -1F, 2F, 2F, 8F).texOffs(102, 46).addBox(-1.5F, -3F, 6F, 3F, 2F, 3F).texOffs(114, 46).addBox(-0.5F, -1F, 7F, 1F, 1F, 1F), PartPose.offset(0F, -5F, -2F)));
        parts.put("wheel0", root.addOrReplaceChild("wheel0", CubeListBuilder.create().texOffs(0, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(18, 60).addBox(-2F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(-6.5F, 21F, -7F)));
        parts.put("wheel1", root.addOrReplaceChild("wheel1", CubeListBuilder.create().texOffs(28, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(46, 60).addBox(-1F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(6.5F, 21F, -7F)));
        parts.put("wheel2", root.addOrReplaceChild("wheel2", CubeListBuilder.create().texOffs(56, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(74, 60).addBox(-2F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(-6.5F, 21F, 0F)));
        parts.put("wheel3", root.addOrReplaceChild("wheel3", CubeListBuilder.create().texOffs(84, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(102, 60).addBox(-1F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(6.5F, 21F, 0F)));
        parts.put("wheel4", root.addOrReplaceChild("wheel4", CubeListBuilder.create().texOffs(0, 72).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(18, 72).addBox(-2F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(-6.5F, 21F, 7F)));
        parts.put("wheel5", root.addOrReplaceChild("wheel5", CubeListBuilder.create().texOffs(28, 72).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(46, 72).addBox(-1F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(6.5F, 21F, 7F)));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static LayerDefinition turtle() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("body", root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -2F, -7F, 12F, 2F, 14F).texOffs(52, 0).addBox(-7F, -5F, -6F, 14F, 3F, 12F).texOffs(0, 16).addBox(-6F, -7F, -5F, 12F, 2F, 10F).texOffs(44, 16).addBox(-4.5F, -8F, -3.5F, 9F, 1F, 7F).texOffs(76, 16).addBox(-7.5F, -5F, -2F, 1F, 2F, 4F).texOffs(86, 16).addBox(6.5F, -5F, -2F, 1F, 2F, 4F).texOffs(96, 16).addBox(-5F, -3.5F, -6.5F, 10F, 1F, 1F).texOffs(0, 28).addBox(-2F, -7F, 5F, 4F, 2F, 2F), PartPose.offset(0F, 20F, 0F)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(12, 28).addBox(-2.5F, -2F, -3F, 5F, 4F, 4F).texOffs(30, 28).addBox(-2F, -1F, -3.5F, 4F, 2F, 1F).texOffs(40, 28).addBox(-0.5F, -4F, -1F, 1F, 2F, 1F).texOffs(44, 28).addBox(-0.5F, -5F, -1F, 1F, 1F, 1F), PartPose.offset(0F, -2F, -7F)));
        parts.put("leg0", root.addOrReplaceChild("leg0", CubeListBuilder.create().texOffs(48, 28).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F).texOffs(60, 28).addBox(-2F, 3F, -2F, 4F, 1F, 4F), PartPose.offset(-5F, 20F, -4F)));
        parts.put("leg1", root.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(76, 28).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F).texOffs(88, 28).addBox(-2F, 3F, -2F, 4F, 1F, 4F), PartPose.offset(5F, 20F, -4F)));
        parts.put("leg2", root.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(104, 28).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F).texOffs(0, 36).addBox(-2F, 3F, -2F, 4F, 1F, 4F), PartPose.offset(-5F, 20F, 4F)));
        parts.put("leg3", root.addOrReplaceChild("leg3", CubeListBuilder.create().texOffs(16, 36).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F).texOffs(28, 36).addBox(-2F, 3F, -2F, 4F, 1F, 4F), PartPose.offset(5F, 20F, 4F)));
        parts.put("tool", parts.get("body").addOrReplaceChild("tool", CubeListBuilder.create().texOffs(44, 36).addBox(-0.5F, -0.5F, -4F, 1F, 1F, 4F).texOffs(54, 36).addBox(-1F, -1F, -5F, 2F, 2F, 1F), PartPose.offset(3.5F, -1F, -6F)));
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static LayerDefinition roverDrill() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("chassis", root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -3F, -10F, 10F, 3F, 20F).texOffs(60, 0).addBox(-6F, -4F, -9F, 12F, 1F, 18F).texOffs(0, 23).addBox(-6F, -3F, -11F, 12F, 2F, 1F).texOffs(26, 23).addBox(-6F, -3F, 10F, 12F, 2F, 1F).texOffs(52, 23).addBox(-5F, -3F, -11.5F, 2F, 1F, 1F).texOffs(58, 23).addBox(3F, -3F, -11.5F, 2F, 1F, 1F).texOffs(64, 23).addBox(-5F, -3F, 10.5F, 2F, 1F, 1F).texOffs(70, 23).addBox(3F, -3F, 10.5F, 2F, 1F, 1F), PartPose.offset(0F, 19F, 0F)));
        parts.put("cab", parts.get("chassis").addOrReplaceChild("cab", CubeListBuilder.create().texOffs(76, 23).addBox(-5F, -5F, -3F, 10F, 5F, 6F).texOffs(108, 23).addBox(-4F, -4F, -3.5F, 8F, 3F, 1F).texOffs(0, 34).addBox(-5F, -1F, -3F, 10F, 1F, 6F).texOffs(32, 34).addBox(-3F, -6F, -2F, 6F, 1F, 2F).texOffs(48, 34).addBox(-4F, -6F, 0F, 8F, 1F, 3F), PartPose.offset(0F, -4F, -6F)));
        parts.put("bed", parts.get("chassis").addOrReplaceChild("bed", CubeListBuilder.create().texOffs(70, 34).addBox(-5.5F, -1F, -5F, 11F, 1F, 11F).texOffs(0, 46).addBox(-6F, -3F, -5F, 1F, 3F, 11F).texOffs(24, 46).addBox(5F, -3F, -5F, 1F, 3F, 11F).texOffs(48, 46).addBox(-6F, -3F, 6F, 12F, 3F, 1F), PartPose.offset(0F, -4F, 4F)));
        parts.put("wheel0", root.addOrReplaceChild("wheel0", CubeListBuilder.create().texOffs(74, 46).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(92, 46).addBox(-2F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(-6.5F, 21F, -7F)));
        parts.put("wheel1", root.addOrReplaceChild("wheel1", CubeListBuilder.create().texOffs(102, 46).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(0, 60).addBox(-1F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(6.5F, 21F, -7F)));
        parts.put("wheel2", root.addOrReplaceChild("wheel2", CubeListBuilder.create().texOffs(10, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(28, 60).addBox(-2F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(-6.5F, 21F, 0F)));
        parts.put("wheel3", root.addOrReplaceChild("wheel3", CubeListBuilder.create().texOffs(38, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(56, 60).addBox(-1F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(6.5F, 21F, 0F)));
        parts.put("wheel4", root.addOrReplaceChild("wheel4", CubeListBuilder.create().texOffs(66, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(84, 60).addBox(-2F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(-6.5F, 21F, 7F)));
        parts.put("wheel5", root.addOrReplaceChild("wheel5", CubeListBuilder.create().texOffs(94, 60).addBox(-1.5F, -3F, -3F, 3F, 6F, 6F).texOffs(112, 60).addBox(-1F, -1F, -1F, 3F, 2F, 2F), PartPose.offset(6.5F, 21F, 7F)));
        parts.put("drill", parts.get("chassis").addOrReplaceChild("drill", CubeListBuilder.create().texOffs(0, 72).addBox(-1F, -1F, -7F, 2F, 2F, 7F).texOffs(18, 72).addBox(-2F, -2F, -9F, 4F, 4F, 2F), PartPose.offset(0F, -3F, -10F)));
        parts.put("bit", parts.get("drill").addOrReplaceChild("bit", CubeListBuilder.create().texOffs(30, 72).addBox(-1.5F, -1.5F, -2F, 3F, 3F, 2F).texOffs(40, 72).addBox(-1F, -1F, -4F, 2F, 2F, 2F).texOffs(48, 72).addBox(-0.5F, -0.5F, -6F, 1F, 1F, 2F), PartPose.offset(0F, 0F, -9F)));
        parts.put("cargo", parts.get("chassis").addOrReplaceChild("cargo", CubeListBuilder.create().texOffs(54, 72).addBox(-5F, -2F, -4F, 10F, 2F, 9F), PartPose.offset(0F, -5F, 4F)));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static LayerDefinition hubTop() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("pad", root.addOrReplaceChild("pad", CubeListBuilder.create().texOffs(0, 0).addBox(-20F, -0.4F, -20F, 40F, 0.4F, 3F).texOffs(0, 4).addBox(-20F, -0.4F, 17F, 40F, 0.4F, 3F).texOffs(0, 8).addBox(-20F, -0.4F, -17F, 3F, 0.4F, 34F).texOffs(0, 43).addBox(17F, -0.4F, -17F, 3F, 0.4F, 34F).texOffs(74, 43).addBox(-9F, -0.2F, -9F, 18F, 0.2F, 1F).texOffs(0, 78).addBox(-9F, -0.2F, 8F, 18F, 0.2F, 1F).texOffs(38, 78).addBox(-9F, -0.2F, -8F, 1F, 0.2F, 16F).texOffs(72, 78).addBox(8F, -0.2F, -8F, 1F, 0.2F, 16F), PartPose.offset(0F, 24F, 0F)));
        parts.put("pylon0", root.addOrReplaceChild("pylon0", CubeListBuilder.create().texOffs(106, 78).addBox(-1.5F, -5F, -1.5F, 3F, 5F, 3F).texOffs(0, 95).addBox(-2F, -1F, -2F, 4F, 1F, 4F).texOffs(16, 95).addBox(-1F, -6.5F, -1F, 2F, 1.5F, 2F), PartPose.offset(-21.5F, 24F, -21.5F)));
        parts.put("pylon1", root.addOrReplaceChild("pylon1", CubeListBuilder.create().texOffs(24, 95).addBox(-1.5F, -5F, -1.5F, 3F, 5F, 3F).texOffs(36, 95).addBox(-2F, -1F, -2F, 4F, 1F, 4F).texOffs(52, 95).addBox(-1F, -6.5F, -1F, 2F, 1.5F, 2F), PartPose.offset(21.5F, 24F, -21.5F)));
        parts.put("pylon2", root.addOrReplaceChild("pylon2", CubeListBuilder.create().texOffs(60, 95).addBox(-1.5F, -5F, -1.5F, 3F, 5F, 3F).texOffs(72, 95).addBox(-2F, -1F, -2F, 4F, 1F, 4F).texOffs(88, 95).addBox(-1F, -6.5F, -1F, 2F, 1.5F, 2F), PartPose.offset(-21.5F, 24F, 21.5F)));
        parts.put("pylon3", root.addOrReplaceChild("pylon3", CubeListBuilder.create().texOffs(96, 95).addBox(-1.5F, -5F, -1.5F, 3F, 5F, 3F).texOffs(108, 95).addBox(-2F, -1F, -2F, 4F, 1F, 4F).texOffs(0, 103).addBox(-1F, -6.5F, -1F, 2F, 1.5F, 2F), PartPose.offset(21.5F, 24F, 21.5F)));
        parts.put("mast", root.addOrReplaceChild("mast", CubeListBuilder.create().texOffs(8, 103).addBox(-2F, -2F, -2F, 4F, 2F, 4F).texOffs(24, 103).addBox(-1F, -12F, -1F, 2F, 10F, 2F).texOffs(32, 103).addBox(-1.5F, -7F, -1.5F, 3F, 1F, 3F), PartPose.offset(19.5F, 24F, 0F)));
        parts.put("dish", parts.get("mast").addOrReplaceChild("dish", CubeListBuilder.create().texOffs(44, 103).addBox(-0.5F, -1F, -0.5F, 1F, 1F, 1F).texOffs(48, 103).addBox(-4F, -2F, -4F, 8F, 1F, 8F).texOffs(80, 103).addBox(-3F, -3F, -3F, 6F, 1F, 6F).texOffs(104, 103).addBox(-0.5F, -6F, -0.5F, 1F, 4F, 1F).texOffs(108, 103).addBox(-1F, -7F, -1F, 2F, 1F, 2F), PartPose.offset(0F, -12F, 0F)));
        parts.put("beacon", root.addOrReplaceChild("beacon", CubeListBuilder.create().texOffs(0, 115).addBox(-2F, -2F, -2F, 4F, 2F, 4F).texOffs(16, 115).addBox(-1.5F, -5F, -1.5F, 3F, 3F, 3F).texOffs(28, 115).addBox(-2F, -6F, -2F, 4F, 1F, 4F), PartPose.offset(-19.5F, 24F, 0F)));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
