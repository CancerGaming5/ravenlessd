package keystrokesmod.module.impl.combat;

import akka.japi.Pair;
import keystrokesmod.Client;
import keystrokesmod.event.client.MouseEvent;
import keystrokesmod.event.client.PreTickEvent;
import keystrokesmod.event.network.SendPacketEvent;
import keystrokesmod.event.player.PostMotionEvent;
import keystrokesmod.event.player.PreUpdateEvent;
import keystrokesmod.event.player.RotationEvent;
import keystrokesmod.event.network.ReceivePacketEvent;
import keystrokesmod.event.render.Render3DEvent;
import keystrokesmod.eventbus.annotations.EventListener;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.autoclicker.DragClickAutoClicker;
import keystrokesmod.module.impl.combat.autoclicker.IAutoClicker;
import keystrokesmod.module.impl.combat.autoclicker.NormalAutoClicker;
import keystrokesmod.module.impl.combat.autoclicker.RecordAutoClicker;
import keystrokesmod.module.impl.other.RecordClick;
import keystrokesmod.module.impl.other.RotationHandler;
import keystrokesmod.module.impl.other.SlotHandler;
import keystrokesmod.module.impl.player.Blink;
import keystrokesmod.module.impl.player.antivoid.HypixelAntiVoid;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.*;
import keystrokesmod.module.setting.utils.ModeOnly;
import keystrokesmod.script.classes.Vec3;
import keystrokesmod.utility.*;
import keystrokesmod.utility.aim.AimSimulator;
import keystrokesmod.utility.aim.RotationData;
import keystrokesmod.utility.render.Animation;
import keystrokesmod.utility.render.Easing;
import keystrokesmod.utility.render.RenderUtils;
import lombok.Getter;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.util.EnumFacing.DOWN;

public class KillAura extends IAutoClicker {
    public static EntityLivingBase target;
    public final SliderSetting attackRange;
    private final ModeValue clickMode;
    private final ModeSetting attackMode;
    private final ButtonSetting smartBlock;
    private final ButtonSetting blockOnlyWhileSwinging;
    private final ButtonSetting blockOnlyWhileHurt;
    private final SliderSetting fov;
    private final SliderSetting swingRange;
    private final SliderSetting blockRange;
    private final SliderSetting preAimRange;
    private final ModeSetting rotationMode;
    private final ModeSetting moveFixMode;
    private final ModeSetting rayCastMode;
    private final SliderSetting minRotationSpeed;
    private final SliderSetting maxRotationSpeed;
    private final SliderSetting minRotationAccuracy;
    private final SliderSetting maxRotationAccuracy;
    private final ButtonSetting nearest;
    private final ButtonSetting lazy;
    private final ButtonSetting constant;
    private final ButtonSetting constantOnlyIfNotMoving;
    private final ButtonSetting noise;
    private final SliderSetting noiseHorizontal;
    private final SliderSetting noiseVertical;
    private final SliderSetting noiseAimSpeed;
    private final SliderSetting noiseDelay;
    private final ButtonSetting delayAim;
    private final SliderSetting delayAimAmount;
    private final ButtonSetting scale;
    private final SliderSetting scaleHorizontal;
    private final SliderSetting scaleVertical;
    private final SliderSetting scaleChance;
    private final ButtonSetting offset;
    private final SliderSetting offsetHorizontal;
    private final SliderSetting offsetVertical;
    private final ModeSetting offsetTiming;
    private final ButtonSetting gcd;
    private final SliderSetting gcdMultiplier;
    private final SliderSetting gcdOffset;
    private final ModeSetting sortMode;
    private final SliderSetting switchDelay;
    private final SliderSetting targets;
    private final ButtonSetting targetInvisible;
    private final ButtonSetting targetPlayer;
    private final ButtonSetting targetEntity;
    private final ButtonSetting disableInInventory;
    private final ButtonSetting disableWhileBlocking;
    private final ButtonSetting disableWhileMining;
    private final ButtonSetting fixSlotReset;
    private final ButtonSetting fixNoSlowFlag;
    private final SliderSetting postDelay;
    private final ButtonSetting hitThroughBlocks;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting requireMouseDown;
    private final ButtonSetting silentSwing;
    private final ButtonSetting weaponOnly;
    private final ButtonSetting dot;
    private final SliderSetting dotSize;
    private final String[] rotationModes = new String[]{"None", "Silent", "Lock view"};
    private final List<EntityLivingBase> availableTargets = new ArrayList<>();
    private final ConcurrentLinkedQueue<Packet<?>> blinkedPackets = new ConcurrentLinkedQueue<>();
    private final AimSimulator aimSimulator = new AimSimulator();
    public ModeSetting autoBlockMode;
    public SliderSetting slowdown;
    public ButtonSetting manualBlock;
    public AtomicBoolean block = new AtomicBoolean();
    public boolean blinking;
    public boolean lag;
    public boolean rmbDown;
    private long lastSwitched = System.currentTimeMillis();
    private boolean switchTargets;
    private byte entityIndex;
    private boolean swing;
    @Getter
    private boolean attack;
    private boolean blocking;
    private boolean swapped;
    private float[] rotations = new float[]{0, 0};
    private EntityLivingBase lastAttackTarget = null;
    private int blockingTime = 0;
    private @Nullable Animation animationX;
    private @Nullable Animation animationY;
    private @Nullable Animation animationZ;
    private final ModeSetting attackTiming;
    private final ButtonSetting switchRandomize;
    private boolean switchState;

    public KillAura() {
        super("KillAura", category.combat);
        this.registerSetting(clickMode = new ModeValue("Click mode", this)
                .add(new NormalAutoClicker("Normal", this, true, true))
                .add(new DragClickAutoClicker("Drag Click", this, true, true))
                .add(new RecordAutoClicker("Record", this, true, true))
                .setDefaultValue("Normal")
        );
        this.registerSetting(attackMode = new ModeSetting("Attack mode", new String[]{"Legit", "Packet"}, 1));
        String[] autoBlockModes = new String[]{"Manual", "Vanilla", "Post", "Swap", "Interact A", "Interact B", "Fake", "Partial", "QuickMacro", "Hypixel", "HypixelTest"};
        this.registerSetting(autoBlockMode = new ModeSetting("Autoblock", autoBlockModes, 0));
        final ModeOnly autoBlock = new ModeOnly(autoBlockMode, 0).reserve();
        this.registerSetting(smartBlock = new ButtonSetting("Smart block", false, autoBlock));
        this.registerSetting(blockOnlyWhileSwinging = new ButtonSetting("Block only while swinging", false, autoBlock));
        this.registerSetting(blockOnlyWhileHurt = new ButtonSetting("Block only while hurt", false, autoBlock));
        this.registerSetting(slowdown = new SliderSetting("Slowdown", 1, 0.2, 1, 0.01, autoBlock));
        this.registerSetting(new DescriptionSetting("Range"));
        this.registerSetting(attackRange = new SliderSetting("Attack range", 3.0, 3.0, 6.0, 0.1));
        this.registerSetting(swingRange = new SliderSetting("Swing range", 3.0, 3.0, 8.0, 0.1));
        this.registerSetting(blockRange = new SliderSetting("Block range", 6.0, 3.0, 12.0, 0.1));
        this.registerSetting(preAimRange = new SliderSetting("PreAim range", 6.0, 3.0, 12.0, 0.1));
        this.registerSetting(fov = new SliderSetting("FOV", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(new DescriptionSetting("Rotation"));
        this.registerSetting(rotationMode = new ModeSetting("Rotation", rotationModes, 1));
        final ModeOnly doRotation = new ModeOnly(rotationMode, 1, 2);
        this.registerSetting(minRotationSpeed = new SliderSetting("Min rotation speed", 180, 0, 180, 1, doRotation));
        this.registerSetting(maxRotationSpeed = new SliderSetting("Max rotation speed", 180, 0, 180, 1, doRotation));
        this.registerSetting(minRotationAccuracy = new SliderSetting("Min rotation accuracy", 180, 0, 180, 1, doRotation));
        this.registerSetting(maxRotationAccuracy = new SliderSetting("Max rotation accuracy", 180, 0, 180, 1, doRotation));
        this.registerSetting(moveFixMode = new ModeSetting("Move fix", RotationHandler.MoveFix.MODES, 0, new ModeOnly(rotationMode, 1)));
        this.registerSetting(rayCastMode = new ModeSetting("Ray cast", new String[]{"None", "Normal", "Strict"}, 1, doRotation));
        this.registerSetting(nearest = new ButtonSetting("Nearest", false, doRotation));
        this.registerSetting(lazy = new ButtonSetting("Lazy", false, doRotation));
        this.registerSetting(constant = new ButtonSetting("Constant", false, doRotation));
        this.registerSetting(constantOnlyIfNotMoving = new ButtonSetting("Constant only if not moving", false, doRotation.extend(constant)));
        this.registerSetting(noise = new ButtonSetting("Noise", false, doRotation));
        this.registerSetting(noiseHorizontal = new SliderSetting("Noise horizontal", 0.35, 0.01, 1, 0.01, doRotation.extend(noise)));
        this.registerSetting(noiseVertical = new SliderSetting("Noise vertical", 0.5, 0.01, 1.5, 0.01, doRotation.extend(noise)));
        this.registerSetting(noiseAimSpeed = new SliderSetting("Noise aim speed", 0.35, 0.01, 1, 0.01, doRotation.extend(noise)));
        this.registerSetting(noiseDelay = new SliderSetting("Noise delay", 100, 50, 500, 10, doRotation.extend(noise)));
        this.registerSetting(delayAim = new ButtonSetting("Delay aim", false, doRotation));
        this.registerSetting(delayAimAmount = new SliderSetting("Delay aim amount", 5, 5, 150, 1, doRotation.extend(delayAim)));
        this.registerSetting(scale = new ButtonSetting("Scale", false, doRotation));
        this.registerSetting(scaleHorizontal = new SliderSetting("Scale horizontal", 1, 0.5, 1.5, 0.1, doRotation.extend(scale)));
        this.registerSetting(scaleVertical = new SliderSetting("Scale vertical", 1, 0.5, 1.5, 0.1, doRotation.extend(scale)));
        this.registerSetting(scaleChance = new SliderSetting("Scale chance", 100, 0, 100, 1, "%", doRotation.extend(scale)));
        this.registerSetting(offset = new ButtonSetting("Offset", false, doRotation));
        this.registerSetting(offsetHorizontal = new SliderSetting("Offset horizontal", 0, -1, 1, 0.05, doRotation.extend(offset)));
        this.registerSetting(offsetVertical = new SliderSetting("Offset vertical", -0.5, -1.5, 1, 0.05, doRotation.extend(offset)));
        this.registerSetting(offsetTiming = new ModeSetting("Offset timing", new String[]{"Pre", "Post"}, 0, doRotation.extend(offset)));
        this.registerSetting(gcd = new ButtonSetting("GCD", false, doRotation));
        this.registerSetting(gcdMultiplier = new SliderSetting("GCD multiplier", 1, 0.1, 3, 0.1, doRotation.extend(gcd)));
        this.registerSetting(gcdOffset = new SliderSetting("GCD Offset", 0, -5, 5, 0.1, doRotation.extend(gcd)));
        this.registerSetting(new DescriptionSetting("Targets"));
        this.registerSetting(attackTiming = new ModeSetting("Attack timing", new String[]{"Pre", "Post", "Switch"}, 0));
        this.registerSetting(switchRandomize = new ButtonSetting("Randomize switch", false, () -> attackTiming.getInput() == 2));        String[] sortModes = new String[]{"Health", "HurtTime", "Distance", "Yaw"};
        this.registerSetting(sortMode = new ModeSetting("Sort mode", sortModes, 0));
        this.registerSetting(targets = new SliderSetting("Targets", 1.0, 1.0, 10.0, 1.0));
        this.registerSetting(switchDelay = new SliderSetting("Switch delay", 200.0, 50.0, 1000.0, 25.0, "ms", () -> targets.getInput() > 1));
        this.registerSetting(targetInvisible = new ButtonSetting("Target invisible", true));
        this.registerSetting(targetPlayer = new ButtonSetting("Target player", true));
        this.registerSetting(targetEntity = new ButtonSetting("Target entity", false));
        this.registerSetting(new DescriptionSetting("Miscellaneous"));
        this.registerSetting(disableInInventory = new ButtonSetting("Disable in inventory", true));
        this.registerSetting(disableWhileBlocking = new ButtonSetting("Disable while blocking", false));
        this.registerSetting(disableWhileMining = new ButtonSetting("Disable while mining", false));
        this.registerSetting(fixSlotReset = new ButtonSetting("Fix slot reset", false));
        this.registerSetting(fixNoSlowFlag = new ButtonSetting("Fix NoSlow flag", false));
        this.registerSetting(postDelay = new SliderSetting("Post delay", 10, 1, 20, 1, fixNoSlowFlag::isToggled));
        this.registerSetting(hitThroughBlocks = new ButtonSetting("Hit through blocks", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(manualBlock = new ButtonSetting("Manual block", false));
        this.registerSetting(requireMouseDown = new ButtonSetting("Require mouse down", false));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing while blocking", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(new DescriptionSetting("Visual"));
        this.registerSetting(dot = new ButtonSetting("Dot", false));
        this.registerSetting(dotSize = new SliderSetting("Dot size", 0.1, 0.05, 0.2, 0.05, dot::isToggled));
    }

    public static boolean behindBlocks(float[] rotations, EntityLivingBase target) {
        try {
            Vec3 eyePos = Utils.getEyePos();
            MovingObjectPosition hitResult = RotationUtils.rayCast(
                    RotationUtils.getNearestPoint(target.getEntityBoundingBox(), eyePos).distanceTo(eyePos) - 0.05,
                    rotations[0], rotations[1]
            );
            return hitResult != null;
        } catch (NullPointerException ignored) {
        }
        return false;
    }

    public void onEnable() {
        clickMode.enable();
        this.rotations = new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(minRotationSpeed, maxRotationSpeed);
        Utils.correctValue(minRotationAccuracy, maxRotationAccuracy);
        Utils.correctValue(attackRange, swingRange);
        Utils.correctValue(swingRange, preAimRange);
    }

    public void onDisable() {
        switchState = false;
        clickMode.disable();
        resetVariables();
        if (Utils.nullCheck()) mc.thePlayer.stopUsingItem();
    }

    private float[] getRotations() {
        aimSimulator.setNearest(nearest.isToggled());
        aimSimulator.setLazy(lazy.isToggled());
        aimSimulator.setNoise(noise.isToggled(),
                new Pair<>((float) noiseHorizontal.getInput(), (float) noiseVertical.getInput()),
                noiseAimSpeed.getInput(), (long) noiseDelay.getInput());
        aimSimulator.setDelay(delayAim.isToggled(), (int) delayAimAmount.getInput());
        if (scale.isToggled() && Math.random() > scaleChance.getInput()) {
            aimSimulator.setScale(scale.isToggled(), scaleHorizontal.getInput(), scaleVertical.getInput());
        } else {
            aimSimulator.setScale(scale.isToggled(), 1, 1);
        }
        aimSimulator.setOffset(offset.isToggled(), offsetHorizontal.getInput(), offsetVertical.getInput(), offsetTiming.getInput() == 0);

        if (constant.isToggled() && !noAimToEntity() && !(constantOnlyIfNotMoving.isToggled() && (MoveUtil.isMoving() || MoveUtil.isMoving(target))))
            return rotations;

        Pair<Float, Float> result = aimSimulator.getRotation(target);

        Double gcdValue = null;

        if (gcd.isToggled()) {
            gcdValue = AimSimulator.getGCD();
            gcdValue *= gcdMultiplier.getInput();
            gcdValue += gcdOffset.getInput();
        }

        float rotationSpeed = (float) Utils.randomizeDouble(minRotationSpeed.getInput(), maxRotationSpeed.getInput());
        double rotationAccuracy = Utils.randomizeDouble(minRotationAccuracy.getInput(), maxRotationAccuracy.getInput());

        return new float[]{
                AimSimulator.rotMove(result.first(), rotations[0], rotationSpeed, gcdValue, rotationAccuracy),
                AimSimulator.rotMove(result.second(), rotations[1], rotationSpeed, gcdValue, rotationAccuracy)
        };
    }

    @EventListener
    public void onPreTick(PreTickEvent ev) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (target != null) {
            rotations = getRotations();
            if (rotationMode.getInput() == 2) {
                mc.thePlayer.rotationYaw = rotations[0];
                mc.thePlayer.rotationPitch = rotations[1];
            }
        }
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        Vec3 hitPos = aimSimulator.getHitPos();
        if (target != null) {
            if (rotations != null && dot.isToggled()) {
                if (animationX == null || animationY == null || animationZ == null) {
                    animationX = new Animation(Easing.EASE_OUT_CIRC, 100);
                    animationY = new Animation(Easing.EASE_OUT_CIRC, 100);
                    animationZ = new Animation(Easing.EASE_OUT_CIRC, 100);

                    animationX.setValue(hitPos.x);
                    animationY.setValue(hitPos.y);
                    animationZ.setValue(hitPos.z);
                }
                animationX.run(hitPos.x);
                animationY.run(hitPos.y);
                animationZ.run(hitPos.z);
                RenderUtils.drawDot(new Vec3(animationX.getValue(), animationY.getValue(), animationZ.getValue()), dotSize.getInput(), 0xFF0670BE);
            }
        } else {
            animationX = animationY = animationZ = null;
        }
    }

    @EventListener
    public void onPreUpdate(PreUpdateEvent event) {
        if (gameNoAction() || playerNoAction()) {
            resetVariables();
            return;
        }

        block();

        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isEnabled() && !ModuleManager.bedAura.allowAura.isToggled() && ModuleManager.bedAura.currentBlock != null) {
            resetBlinkState(true);
            return;
        }

        if ((mc.thePlayer.isBlocking() || block.get()) && disableWhileBlocking.isToggled()) {
            resetBlinkState(true);
            return;
        }

        boolean swingWhileBlocking = !silentSwing.isToggled() || !block.get();
        if (swing && attack && HitSelect.canSwing()) {
            if (swingWhileBlocking) {
                mc.thePlayer.swingItem();
                RecordClick.click();
            } else {
                PacketUtils.sendPacket(new C0APacketAnimation());
                RecordClick.click();
            }
        }

        int input = (int) autoBlockMode.getInput();
        if (block.get() && (input == 3 || input == 4 || input == 5 || input == 8 || input == 9 || input == 10)
                && Utils.holdingSword()) {
            setBlockState(block.get(), false, false);
            if (ModuleManager.bedAura.stopAutoblock) {
                resetBlinkState(false);
                ModuleManager.bedAura.stopAutoblock = false;
                return;
            }
            switch (input) {
                case 3:
                    if (lag) {
                        blinking = true;
                        if (Client.badPacketsHandler.playerSlot != mc.thePlayer.inventory.currentItem % 8 + 1) {
                            PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
                            Client.badPacketsHandler.playerSlot = mc.thePlayer.inventory.currentItem % 8 + 1;
                            swapped = true;
                        }
                        lag = false;
                    } else {
                        PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        Client.badPacketsHandler.playerSlot = mc.thePlayer.inventory.currentItem;
                        swapped = false;
                        attackAndInteract(target, true);
                        sendBlock();
                        releasePackets();
                        lag = true;
                    }
                    break;
                case 4:
                case 5:
                    if (lag) {
                        blinking = true;
                        unBlock();
                        lag = false;
                    } else {
                        attackAndInteract(target, autoBlockMode.getInput() == 5);
                        releasePackets();
                        sendBlock();
                        lag = true;
                    }
                    break;
                case 8:
                    lag = false;
                    releasePackets();
                    attack(target);
                    if (SlotHandler.getHeldItem() != null && SlotHandler.getHeldItem().getItem() instanceof ItemSword) {
                        PacketUtils.sendPacket(new C0FPacketConfirmTransaction(Utils.randomizeInt(0, 2147483647), (short) Utils.randomizeInt(0, -32767), true));
                        PacketUtils.sendPacket(new C0APacketAnimation());
                        sendBlock();
                    }
                    break;
                case 9:
                    if (lag) {
                        blinking = true;
                        unBlock();
                        lag = false;
                    } else {
                        if (!attackAndInteract(target, true, Utils.getEyePos(target))) {
                            break;
                        }
                        releasePackets();
                        blinking = false;
                        sendBlock();
                        lag = true;
                    }
                    break;
                case 10:
                    if (lag) {
                        blinking = true;
                        Vec3 hitVec = Utils.getEyePos();
                        PacketUtils.sendPacket(new C02PacketUseEntity(target, new Vec3(
                                hitVec.x - target.posX,
                                hitVec.y - target.posY,
                                hitVec.z - target.posZ
                        ).toVec3()));
                        PacketUtils.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
                        unBlock();
                        lag = false;
                    } else {
                        if (!attackAndInteract(target, true, Utils.getEyePos(target))) {
                            break;
                        }
                        releasePackets();
                        blinking = false;
                        sendBlock();
                        lag = true;
                    }
                    break;
            }
            return;
        } else if (blinking || lag) {
            resetBlinkState(true);
        }

        if (target == null) {
            return;
        }

        int timing = (int) attackTiming.getInput();
        boolean isPreAttack = timing == 0 || (timing == 2 && !switchState);
        boolean isPostAttack = timing == 1 || (timing == 2 && switchState);

        if (attack && isPreAttack) {
            doAttackTimed();
            if (timing == 2) {
                if (switchRandomize.isToggled()) {
                    switchState = Math.random() > 0.5;
                } else {
                    switchState = !switchState;
                }
            }
        }
    }

    @EventListener(priority = -1)
    public void onRotation(@NotNull RotationEvent event) {
        RotationData data = doRotationAction(new RotationData(event.getYaw(), event.getPitch()));
        if (data != null) {
            event.setYaw(data.getYaw());
            event.setPitch(data.getPitch());
            event.setMoveFix(RotationHandler.MoveFix.values()[(int) moveFixMode.getInput()]);
        }
    }

    private @Nullable RotationData doRotationAction(RotationData e) {
        if (gameNoAction() || playerNoAction()) {
            return null;
        }
        setTarget(new float[]{e.getYaw(), e.getPitch()});
        if (target != null && rotationMode.getInput() == 1) {
            return new RotationData(rotations[0], rotations[1]);
        } else {
            this.rotations = new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
        }
        if (autoBlockMode.getInput() == 2 && block.get() && Utils.holdingSword()) {
            PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
            PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        }
        return null;
    }

    @EventListener
    public void onPostMotion(PostMotionEvent e) {
        if (autoBlockMode.getInput() == 2 && block.get() && Utils.holdingSword()) {
            mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(SlotHandler.getHeldItem()));
        }

        int timing = (int) attackTiming.getInput();
        boolean isPostAttack = timing == 1 || (timing == 2 && switchState);

        if (attack && isPostAttack) {
            doAttackTimed();
            if (timing == 2) {
                if (switchRandomize.isToggled()) {
                    switchState = Math.random() > 0.5;
                } else {
                    switchState = !switchState;
                }
            }
        }
    }

    @EventListener(priority = 1)
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck() || !blinking) {
            return;
        }
        Packet<?> packet = e.getPacket();
        if (packet.getClass().getSimpleName().startsWith("S")) {
            return;
        }
        blinkedPackets.add(e.getPacket());
        e.cancel();
    }

    @EventListener
    public void onReceivePacket(ReceivePacketEvent e) {
        if (gameNoAction() || !fixSlotReset.isToggled()) {
            return;
        }
        if (Utils.holdingSword() && (mc.thePlayer.isBlocking() || block.get())) {
            if (e.getPacket() instanceof S2FPacketSetSlot) {
                if (mc.thePlayer.inventory.currentItem == ((S2FPacketSetSlot) e.getPacket()).func_149173_d() - 36 && mc.currentScreen == null) {
                    if (((S2FPacketSetSlot) e.getPacket()).func_149174_e() == null || (((S2FPacketSetSlot) e.getPacket()).func_149174_e().getItem() != mc.thePlayer.getHeldItem().getItem())) {
                        return;
                    }
                    e.cancel();
                }
            }
        }
    }

    @EventListener
    public void onMouse(final @NotNull MouseEvent mouseEvent) {
        if (mouseEvent.getButton() == 0 && mouseEvent.isButtonstate()) {
            if (target != null || swing) {
                mouseEvent.cancel();
            }
        } else if (mouseEvent.getButton() == 1) {
            rmbDown = mouseEvent.isButtonstate();
            if (autoBlockMode.getInput() >= 1 && Utils.holdingSword() && block.get() && autoBlockMode.getInput() != 7) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                if (target == null && mc.objectMouseOver != null) {
                    if (mc.objectMouseOver.entityHit != null && AntiBot.isBot(mc.objectMouseOver.entityHit)) {
                        return;
                    }
                    final BlockPos getBlockPos = mc.objectMouseOver.getBlockPos();
                    if (getBlockPos != null && (BlockUtils.check(getBlockPos, Blocks.chest) || BlockUtils.check(getBlockPos, Blocks.ender_chest))) {
                        return;
                    }
                }
                mouseEvent.cancel();
            }
        }
    }

    @Override
    public String getInfo() {
        return rotationModes[(int) rotationMode.getInput()];
    }

    public boolean noAimToEntity() {
        if (target == null) return true;
        if (rotationMode.getInput() == 0) return false;

        boolean noAim = false;
        switch ((int) rayCastMode.getInput()) {
            default:
            case 2:
                MovingObjectPosition hitResult = RotationUtils.rayCastStrict(RotationHandler.getRotationYaw(), RotationHandler.getRotationPitch(), attackRange.getInput());
                noAim = hitResult.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || hitResult.entityHit != target;
            case 1:
                if (noAim) break;
                noAim = RotationUtils.rayCast(
                        Utils.getEyePos().distanceTo(RotationUtils.getNearestPoint(target.getEntityBoundingBox(), Utils.getEyePos())),
                        rotations[0], rotations[1]
                ) != null;
                break;
            case 0:
                return false;
        }

        return noAim;
    }

    private void resetVariables() {
        target = lastAttackTarget = null;
        availableTargets.clear();

        block.set(false);
        swing = false;
        rmbDown = false;
        attack = false;
        block();
        resetBlinkState(true);
        swapped = false;
        blockingTime = 0;
    }

    private void block() {
        if (!block.get() && !blocking) {
            return;
        }
        if (manualBlock.isToggled() && !rmbDown) {
            block.set(false);
        }
        if (!Utils.holdingSword()) {
            block.set(false);
        }
        switch ((int) autoBlockMode.getInput()) {
            case 0:
                setBlockState(false, false, true);
                break;
            case 8:
            case 1:
                setBlockState(block.get(), true, true);
                break;
            case 2:
                setBlockState(block.get(), false, true);
                break;
            case 3:
            case 4:
            case 5:
            case 9:
                setBlockState(block.get(), false, false);
                break;
            case 10:
                setBlockState(block.get(), false, false);
                break;
            case 6:
                setBlockState(block.get(), false, false);
                break;
            case 7:
                boolean down = (target == null || target.hurtTime >= 5) && block.get();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), down);
                Reflection.setButton(1, down);
                blocking = down;
                break;
        }
        if (block.get()) {
            blockingTime++;
        } else {
            blockingTime = 0;
        }
    }

    private void setBlockState(boolean state, boolean sendBlock, boolean sendUnBlock) {
        if (Utils.holdingSword()) {
            if (sendBlock && !blocking && state && Utils.holdingSword() && !Client.badPacketsHandler.C07) {
                sendBlock();
            } else if (sendUnBlock && blocking && !state) {
                unBlock();
            }
        }
        blocking = Reflection.setBlocking(state);
    }

    private boolean canBlock(EntityLivingBase target) {
        if (smartBlock.isToggled()) {
            if (target == null)
                return false;
            if (lastAttackTarget != target && target.hurtTime == 0)
                return false;
            if (!Utils.inFov(140, target, mc.thePlayer))
                return false;
            Vec3 predTargetPos = MoveUtil.predictedPos(target, new Vec3(target.motionX, target.motionY, target.motionZ), Utils.getEyePos(), 1);
            if (RotationUtils.getNearestPoint(mc.thePlayer.getEntityBoundingBox(), predTargetPos).distanceTo(predTargetPos) > 3)
                return false;
        }
        if (blockOnlyWhileSwinging.isToggled()) {
            if (!mc.thePlayer.isSwingInProgress)
                return false;
        }
        if (blockOnlyWhileHurt.isToggled()) {
            return mc.thePlayer.hurtTime != 0;
        }
        return true;
    }

    private void setTarget(float[] rotations) {
        availableTargets.clear();
        block.set(false);
        swing = false;

        final Vec3 eyePos = Utils.getEyePos();
        mc.theWorld.loadedEntityList.parallelStream()
                .filter(Objects::nonNull)
                .filter(entity -> entity != mc.thePlayer)
                .filter(entity -> entity instanceof EntityLivingBase)
                .map(entity -> (EntityLivingBase) entity)
                .filter(entity -> {
                    if (entity instanceof EntityArmorStand) return false;
                    if (entity instanceof EntityPlayer) {
                        if (!targetPlayer.isToggled()) return false;
                        if (Utils.isFriended((EntityPlayer) entity)) {
                            return false;
                        }
                        if (entity.deathTime != 0) {
                            return false;
                        }
                        return !AntiBot.isBot(entity) && !(ignoreTeammates.isToggled() && Utils.isTeamMate(entity));
                    } else return targetEntity.isToggled();
                })
                .filter(entity -> targetInvisible.isToggled() || !entity.isInvisible())
                .filter(entity -> hitThroughBlocks.isToggled() || !behindBlocks(rotations, entity))
                .filter(entity -> fov.getInput() == 360 || Utils.inFov((float) fov.getInput(), entity))
                .map(entity -> new Pair<>(entity, eyePos.distanceTo(RotationUtils.getNearestPoint(entity.getEntityBoundingBox(), eyePos))))
                .forEach(pair -> {
                    // need a more accurate distance check as this can ghost on hypixel
                    if (pair.second() <= blockRange.getInput() && autoBlockMode.getInput() > 0 && canBlock(pair.first())) {
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                        block.set(true);
                    }
                    if (pair.second() <= swingRange.getInput()) {
                        swing = true;
                    }
                    if (pair.second() <= preAimRange.getInput()) {
                        availableTargets.add(pair.first());
                    }
                });

        if (Math.abs(System.currentTimeMillis() - lastSwitched) > switchDelay.getInput() && switchTargets) {
            switchTargets = false;
            if (entityIndex < availableTargets.size() - 1) {
                entityIndex++;
            } else {
                entityIndex = 0;
            }
            lastSwitched = System.currentTimeMillis();
        }
        if (!availableTargets.isEmpty()) {
            Comparator<EntityLivingBase> comparator = null;
            switch ((int) sortMode.getInput()) {
                case 0:
                    comparator = Comparator.comparingDouble(entityPlayer -> (double) entityPlayer.getHealth());
                    break;
                case 1:
                    comparator = Comparator.comparingDouble(entityPlayer2 -> (double) entityPlayer2.hurtTime);
                    break;
                case 2:
                    comparator = Comparator.comparingDouble(entity -> mc.thePlayer.getDistanceSqToEntity(entity));
                    break;
                case 3:
                    comparator = Comparator.comparingDouble(entity2 -> RotationUtils.distanceFromYaw(entity2, false));
                    break;
            }
            availableTargets.sort(comparator);
            if (entityIndex > (int) targets.getInput() - 1 || entityIndex > availableTargets.size() - 1) {
                entityIndex = 0;
            }
            target = availableTargets.get(entityIndex);
        } else {
            target = null;
        }
    }

    private boolean gameNoAction() {
        if (!Utils.nullCheck()) {
            return true;
        }
        if (ModuleManager.bedAura.isEnabled() && !ModuleManager.bedAura.allowAura.isToggled() && ModuleManager.bedAura.currentBlock != null) {
            return true;
        }
        if (Blink.isBlinking()) return true;
        if (HypixelAntiVoid.getInstance() != null && HypixelAntiVoid.getInstance().blink.isEnabled()) return true;
        return mc.thePlayer.isDead;
    }

    private boolean playerNoAction() {
        if (!Mouse.isButtonDown(0) && requireMouseDown.isToggled()) {
            return true;
        } else if (!Utils.holdingWeapon() && weaponOnly.isToggled()) {
            return true;
        } else if (isMining() && disableWhileMining.isToggled()) {
            return true;
        } else if (fixNoSlowFlag.isToggled() && blockingTime > (int) postDelay.getInput()) {
            unBlock();
            blockingTime = 0;
        } else if (ModuleManager.scaffold.isEnabled()) {
            return true;
        }
        return mc.currentScreen != null && disableInInventory.isToggled();
    }

    private void attackAndInteract(EntityLivingBase target, boolean sendInteractAt) {
        attackAndInteract(target, sendInteractAt, aimSimulator.getHitPos());
    }

    private boolean attackAndInteract(EntityLivingBase target, boolean sendInteractAt, Vec3 hitVec) {
        if (target != null && attack) {
            if (!attack(target)) return false;
            if (sendInteractAt) {
                if (hitVec != null) {
                    hitVec = new Vec3(hitVec.x - target.posX, hitVec.y - target.posY, hitVec.z - target.posZ);
                    PacketUtils.sendPacket(new C02PacketUseEntity(target, hitVec.toVec3()));
                }
            }
            PacketUtils.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
            return true;
        } else if (ModuleManager.antiFireball != null && ModuleManager.antiFireball.isEnabled() && ModuleManager.antiFireball.fireball != null && ModuleManager.antiFireball.attack) {
            doAttack(ModuleManager.antiFireball.fireball, !ModuleManager.antiFireball.silentSwing.isToggled());
            PacketUtils.sendPacket(new C02PacketUseEntity(ModuleManager.antiFireball.fireball, C02PacketUseEntity.Action.INTERACT));
            return true;
        }

        return false;
    }

    private boolean attack(EntityLivingBase target) {
        attack = false;
        if (noAimToEntity()) {
            return false;
        }
        if (ModuleManager.bedAura.rotate) {
            return false;
        }
        switchTargets = true;
        doAttack(target, !silentSwing.isToggled());
        return true;
    }

    private void sendBlock() {
        PacketUtils.sendPacket(new C08PacketPlayerBlockPlacement(SlotHandler.getHeldItem()));
    }

    private boolean isMining() {
        return Mouse.isButtonDown(0) && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private void unBlock() {
        if (!Utils.holdingSword()) {
            return;
        }
        PacketUtils.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, DOWN));
        blockingTime = 0;
    }

    public void resetBlinkState(boolean unblock) {
        if (!Utils.nullCheck()) return;
        releasePackets();
        blocking = false;
        if (Client.badPacketsHandler.playerSlot != mc.thePlayer.inventory.currentItem && swapped) {
            PacketUtils.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            Client.badPacketsHandler.playerSlot = mc.thePlayer.inventory.currentItem;
            swapped = false;
        }
        if (lag && unblock) {
            unBlock();
        }
        lag = false;
    }

    private void releasePackets() {
        try {
            synchronized (blinkedPackets) {
                for (Packet<?> packet : blinkedPackets) {
                    if (packet instanceof C09PacketHeldItemChange) {
                        Client.badPacketsHandler.playerSlot = ((C09PacketHeldItemChange) packet).getSlotId();
                    }
                    PacketUtils.sendPacketNoEvent(packet);
                }
            }
        } catch (Exception e) {
            Utils.sendModuleMessage(this, "&cThere was an error releasing blinked packets");
        }
        blinkedPackets.clear();
        blinking = false;
    }

    @Override
    public boolean click() {
        if (swing)
            attack = true;
        return swing;
    }

    private void doAttack(Entity target, boolean swingWhileBlocking) {
        switch ((int) attackMode.getInput()) {
            case 0:
                Utils.sendClick(0, true);
                Utils.sendClick(0, false);
                break;
            case 1:
                Utils.attackEntity(target, swingWhileBlocking);
                break;
        }
        if (target == KillAura.target)
            lastAttackTarget = KillAura.target;
    }

    private void doAttackTimed() {
        if (target == null || !isTargetValid()) {
            attack = false;
            return;
        }

        double distance = mc.thePlayer.getDistanceToEntity(target);
        if (distance > attackRange.getInput()) {
            attack = false;
            return;
        }

        resetBlinkState(true);
        if (noAimToEntity()) {
            attack = false;
            return;
        }

        switchTargets = true;
        boolean swingWhileBlocking = !silentSwing.isToggled() || !block.get();
        doAttack(target, swingWhileBlocking);
        attack = false;
    }

    private boolean isTargetValid() {
        return target.isEntityAlive() &&
                !target.isDead &&
                target.hurtResistantTime <= 10 &&
                mc.theWorld.loadedEntityList.contains(target);
    }
}
