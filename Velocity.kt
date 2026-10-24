/*
Cloverpixel NoXZ Velocity（Mode Click）
拉弓包Velocity Bypasssssssss By kboxcbh1337
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.exploit.Disabler
import net.ccbluex.liquidbounce.features.module.modules.movement.LongJump
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.movement.Stuck
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isLookingOnEntities
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.isOnGround
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.speed
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils.runWithModifiedRaycastResult
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.block.BlockAir
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.*
import net.minecraft.network.play.server.S00PacketKeepAlive
import net.minecraft.network.play.server.S02PacketChat
import net.minecraft.network.play.server.S03PacketTimeUpdate
import net.minecraft.network.play.server.S06PacketUpdateHealth
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S0BPacketAnimation
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S14PacketEntity
import net.minecraft.network.play.server.S19PacketEntityStatus
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.network.play.server.S29PacketSoundEffect
import net.minecraft.network.play.server.S2EPacketCloseWindow
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import net.minecraft.network.play.server.S3EPacketTeams
import net.minecraft.network.play.server.S40PacketDisconnect
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.potion.Potion
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing.DOWN
import net.minecraft.util.Vec3
import org.lwjgl.input.Keyboard
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

object Velocity : Module("Velocity", Category.COMBAT, hideModule = false) {

    /**
     * OPTIONS
     */
    private val mode by choices(
        "Mode", arrayOf(
            "Simple", "AAC", "AACPush", "AACZero", "AACv4",
            "Reverse", "SmoothReverse", "Jump", "Glitch", "Legit",
            "GhostBlock", "Vulcan", "S32Packet", "MatrixReduce",
            "IntaveReduce", "Delay", "GrimC03", "Hypixel", "HypixelAir",
            "Click", "Smart", "BlocksMC",
            "JumpReset", "NoXZ",
            "HitFrame",
            "Vanilla", "Prediction"
        ), "Simple"
    )

    /**
     * LEADER (Velocity.txt) MODES - Vanilla / Prediction
     */
    private val leaderMode
        get() = mode == "Vanilla" || mode == "Prediction"

    // Vanilla options
    private val leaderChance by int("Chance", 100, 0..100) { mode == "Vanilla" }
    private val leaderHorizontal by int("Horizontal", 100, 0..100) { mode == "Vanilla" }
    private val leaderVertical by int("Vertical", 100, 0..100) { mode == "Vanilla" }
    private val leaderExplosionHorizontal by int("ExplosionsHorizontal", 100, 0..100) { mode == "Vanilla" }
    private val leaderExplosionVertical by int("ExplosionsVertical", 100, 0..100) { mode == "Vanilla" }
    private val leaderFakeCheck by boolean("FakeCheck", true) { mode == "Vanilla" }
    private val leaderDebug by boolean("Debug", false) { mode == "Vanilla" }

    // Prediction options
    private val leaderReduce by boolean("Reduce", true) { mode == "Prediction" }
    val leaderReduceMode by choices(
        "ReduceMode", arrayOf("Attack", "ReleaseWhenCanAttack", "ReleaseBeforeCanAttack"), "Attack"
    ) { mode == "Prediction" && leaderReduce }
    private val leaderExtraAttack by boolean("ExtraAttack", false) {
        mode == "Prediction" && leaderReduce && leaderReduceMode != "Attack"
    }
    private val leaderReduceWhenCanAttack by boolean("ReduceWhenCanAttack", true) {
        mode == "Prediction" && leaderReduce && leaderReduceMode == "Attack"
    }
    private val leaderOnlySprinting by boolean("OnlySprinting", true) {
        mode == "Prediction" && leaderReduce && leaderReduceMode == "Attack"
    }
    private val leaderSmartTimes by boolean("SmartTimes", true) {
        mode == "Prediction" && leaderReduce && leaderReduceMode == "Attack"
    }
    private val leaderAttackTimes by int("AttackTimes", 1, 1..5) {
        mode == "Prediction" && leaderReduce && leaderReduceMode == "Attack" && !leaderSmartTimes
    }
    private val leaderTestMode by boolean("TestMode", false) {
        mode == "Prediction" && leaderReduce && leaderReduceMode == "Attack"
    }
    private val leaderStopBlockHurtTime by int("StopBlockHurtTime", 2, 0..10) {
        mode == "Prediction" && leaderReduce && leaderReduceMode == "Attack" && leaderTestMode
    }
    private val leaderJump by boolean("Jump", true) { mode == "Prediction" }
    private val leaderDelay by boolean("Delay", false) { mode == "Prediction" }
    private val leaderDelayTicks by int("DelayTicks", 1, 1..5) {
        mode == "Prediction" && leaderDelay && !leaderAirBuffer
    }
    private val leaderAirBuffer by boolean("DelayTillOnGround", true) { mode == "Prediction" && leaderDelay }
    private val leaderGroundDelay by boolean("GroundDelay", false) {
        mode == "Prediction" && leaderDelay && !leaderAirBuffer
    }
    private val leaderRotate by boolean("Rotate", false) { mode == "Prediction" }
    private val leaderRotateTick by int("RotateTicks", 3, 1..12) { mode == "Prediction" && leaderRotate }
    private val leaderAutoMove by boolean("AutoMove", false) { mode == "Prediction" && leaderRotate }

    private val horizontal by float("Horizontal", 0F, 0F..1F) { mode in arrayOf("Simple", "AAC", "Legit") }
    private val vertical by float("Vertical", 0F, 0F..1F) { mode in arrayOf("Simple", "Legit") }

    // Reverse
    private val reverseStrength by float("ReverseStrength", 1F, 0.1F..1F) { mode == "Reverse" }
    private val reverse2Strength by float("SmoothReverseStrength", 0.05F, 0.02F..0.1F) { mode == "SmoothReverse" }

    private val onLook by boolean("onLook", false) { mode in arrayOf("Reverse", "SmoothReverse") }
    private val range by float("Range", 3.0F, 1F..5.0F) {
        onLook && mode in arrayOf("Reverse", "SmoothReverse")
    }
    private val maxAngleDifference by float("MaxAngleDifference", 45.0f, 5.0f..90f) {
        onLook && mode in arrayOf("Reverse", "SmoothReverse")
    }

    // AAC Push
    private val aacPushXZReducer by float("AACPushXZReducer", 2F, 1F..3F) { mode == "AACPush" }
    private val aacPushYReducer by boolean("AACPushYReducer", true) { mode == "AACPush" }

    // AAC v4
    private val aacv4MotionReducer by float("AACv4MotionReducer", 0.62F, 0F..1F) { mode == "AACv4" }

    // Legit
    private val legitDisableInAir by boolean("DisableInAir", true) { mode == "Legit" }

    // Chance
    private val chance by int("Chance", 100, 0..100) { mode == "Jump" || mode == "Legit" }

    // Jump
    private val jumpCooldownMode by choices("JumpCooldownMode", arrayOf("Ticks", "ReceivedHits"), "Ticks")
    { mode == "Jump" }
    private val ticksUntilJump by int("TicksUntilJump", 4, 0..20)
    { jumpCooldownMode == "Ticks" && mode == "Jump" }
    private val hitsUntilJump by int("ReceivedHitsUntilJump", 2, 0..5)
    { jumpCooldownMode == "ReceivedHits" && mode == "Jump" }

    // Ghost Block
    private val maxHurtTime: IntegerValue = object : IntegerValue("MaxHurtTime", 9, 1..10) {
        override fun isSupported() = mode == "GhostBlock"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minHurtTime.get())
    }

    private val minHurtTime: IntegerValue = object : IntegerValue("MinHurtTime", 1, 1..10) {
        override fun isSupported() = mode == "GhostBlock" && !maxHurtTime.isMinimal()
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceIn(0, maxHurtTime.get())
    }

    // Delay
    private val spoofDelay by int("SpoofDelay", 500, 0..5000) { mode == "Delay" }
    var delayMode = false

    // IntaveReduce
    private val reduceFactor by float("Factor", 0.6f, 0.6f..1f) { mode == "IntaveReduce" }
    private val hurtTime by int("HurtTime", 9, 1..10) { mode == "IntaveReduce" }

    private val pauseOnExplosion by boolean("PauseOnExplosion", true)
    private val ticksToPause by int("TicksToPause", 20, 1..50) { pauseOnExplosion }

    // TODO: Could this be useful in other modes? (Jump?)
    // Limits
    private val limitMaxMotionValue = boolean("LimitMaxMotion", false) { mode == "Simple" }
    private val maxXZMotion by float("MaxXZMotion", 0.4f, 0f..1.9f) { limitMaxMotionValue.isActive() }
    private val maxYMotion by float("MaxYMotion", 0.36f, 0f..0.46f) { limitMaxMotionValue.isActive() }
    //0.00075 is added silently

    // Vanilla XZ limits
    // Non-KB: 0.4 (no sprint), 0.9 (sprint)
    // KB 1: 0.9 (no sprint), 1.4 (sprint)
    // KB 2: 1.4 (no sprint), 1.9 (sprint)
    // Vanilla Y limits
    // 0.36075 (no sprint), 0.46075 (sprint)

    private val clicks by intRange("Clicks", 3..5, 1..20) { mode == "Click" }
    private val hurtTimeToClick by int("HurtTimeToClick", 10, 0..10) { mode == "Click" }
    private val whenFacingEnemyOnly by boolean("WhenFacingEnemyOnly", true) { mode == "Click" }
    private val ignoreBlocking by boolean("IgnoreBlocking", false) { mode == "Click" }
    private val clickRange by float("ClickRange", 3f, 1f..6f) { mode == "Click" }
    private val swingMode by choices("SwingMode", arrayOf("Off", "Normal", "Packet"), "Normal") { mode == "Click" }

    /**
     * 受击射线找实体：开启后不再依赖准星，受击瞬间主动用射线（含扇形补扫）搜索实体并攻击，
     * 打不到再退化为"范围内最近的合法实体"。
     */
    private val hurtRaycast by boolean("HurtRaycast", false) { mode == "Click" }

    /** 一瞬间的爆发点击次数（CPS）：开启受击射线后，受击那一瞬间打这么多次 */
    private val instantCps by int("InstantCPS", 10, 1..20) { mode == "Click" && hurtRaycast }

    /** 扇形补扫半角：正前方射线打不到时，左右各扫这么多度找实体；0 = 只打一条射线 */
    private val hurtRaycastFov by float("HurtRaycastFov", 45f, 0f..180f) { mode == "Click" && hurtRaycast }

    /**
     * 可见秒速转头：开启后除静默设定发包朝向外，还会把本地视角直接怼过去。
     *
     * 默认关闭 —— 默认就是纯静默转头：只把朝向发给服务端，本地相机完全不动
     * （与 KillAura2 的静默旋转体系一致，靠 RotationSettings.applyServerSide 实现）。
     */
    private val hurtSnapRotation by boolean("HurtSnapRotation", false) { mode == "Click" && hurtRaycast }

    /**
     * 受击转头的静默旋转配置。
     *
     * 学习 KillAura2：全局只保留一份 RotationSettings 实例并复用它，
     * 而不是每次受击都在热路径里 new 一个（那会反复向模块注册同一批配置项）。
     *
     * applyServerSide 默认 true = 只改服务端朝向、不动本地相机；
     * immediate 必须为 true，否则本次攻击发出的包用的仍是旧朝向。
     */
    private val hurtRotationOptions = RotationSettings(this).withoutKeepRotation().apply {
        prioritizeRequest = true
        immediate = true
    }

    // Smart（智能）模式：CPS 减少 + JumpReset + 延迟击退包到地面（超时限定）
    private val smartCpsReduce by boolean("CPSReduce", true) { mode == "Smart" }
    private val smartReducedCps by intRange("ReducedCPS", 1..2, 1..20) { mode == "Smart" && smartCpsReduce }
    private val smartJumpReset by boolean("JumpReset", true) { mode == "Smart" }
    private val smartDelay by boolean("Delay", true) { mode == "Smart" }
    private val smartDelayTimeout by int("DelayTimeout", 20, 5..60) { mode == "Smart" && smartDelay }

    // Nilore AntiKB（JumpReset / NoXZ）——移植自 OpenNilore AntiKB 模块
    private val niloreRotate by boolean("Rotate", false) { mode == "JumpReset" }
    private val niloreFollowDirection by boolean("FollowDirection", false) { mode == "JumpReset" && niloreRotate }
    private val niloreRotateTicks by int("RotateTicks", 12, 3..20) { mode == "JumpReset" && (niloreRotate || niloreFollowDirection) }
    private val niloreAutoAttackCount by boolean("AutoAttackCount", true) { mode == "NoXZ" }
    private val niloreAttackAmount by int("AttackAmount", 5, 1..20) { mode == "NoXZ" && !niloreAutoAttackCount }
    private val niloreInstantAttack by boolean("InstantAttack", false) { mode == "NoXZ" }
    private val niloreSprintCheck by boolean("SprintStateCheck", true) { mode == "NoXZ" }
    private val niloreJumpReset by boolean("JumpReset", false) { mode == "NoXZ" }
    private val niloreDebug by boolean("Debug", false) { mode in arrayOf("JumpReset", "NoXZ") }

    // HitFrame（受击帧攻击伪造）：收到击退的瞬间以 FakeCPS 高频【真实原版攻击】目标。
    // 只有冲刺攻击才会触发原版 0.6x 击退减速（playerController.attackEntity 内部的 vanilla 行为），
    // Grim 的 Simulation 检测认可原版物理，因此不再本地修改 motion（本地改 motion 会被判违规）。
    // 高频只持续 AttackWindow tick（默认 3 tick），避免触发 Grim AutoClicker。
    private val hitFrameFakeCps by int("FakeCPS", 20, 5..40) { mode == "HitFrame" }
    private val hitFrameWindow by int("AttackWindow", 3, 1..10) { mode == "HitFrame" }
    private val hitFrameOnlyTarget by boolean("OnlyWhenTarget", true) { mode == "HitFrame" }
    private val hitFrameSprintCheck by boolean("SprintCheck", true) { mode == "HitFrame" }
    private val hitFrameRange by float("Range", 3.5F, 1F..6F) { mode == "HitFrame" }
    private val hitFrameDebug by boolean("Debug", false) { mode == "HitFrame" }

    /**
     * VALUES
     */
    private val velocityTimer = MSTimer()
    private var hasReceivedVelocity = false

    // SmoothReverse
    private var reverseHurt = false

    // AACPush
    private var jump = false

    // Jump
    private var limitUntilJump = 0

    // IntaveReduce
    private var intaveTick = 0
    private var lastAttackTime = 0L
    private var intaveDamageTick = 0

    // Delay
    private val packets = LinkedHashMap<Packet<*>, Long>()

    // Smart（智能）状态
    private val smartDelayedPackets = mutableListOf<Packet<*>>()
    private var smartKnockbackActive = false
    private var smartTimeoutTicks = 0
    private var smartLastJump = 0L

    // Nilore AntiKB（JumpReset / NoXZ）状态
    private val nilorePacketQueue = ArrayDeque<Packet<*>>()
    private val niloreMoveQueue = ArrayDeque<Packet<*>>()
    private var niloreSuspending = false
    private var niloreSuspendTicks = 0
    private var niloreKnockback: S12PacketEntityVelocity? = null
    private var nilorePhase = 0          // 0=IDLE 1=AIR 2=GROUND
    private var niloreDelayTicks = 0
    private var niloreJumpTicks = 0
    private var niloreHitCounter = 0
    private var niloreAttackCooldown = 0
    private var niloreAttacksRemaining = 0
    private var niloreAttackTarget: Entity? = null
    private var niloreShouldJump = false
    private var niloreSprintBoost = 0
    private var niloreInstantProgress = 0f
    private var niloreInstantAttacking = false
    private var niloreFlushMotion = false
    private var niloreJumpResetQueued = false
    private var niloreJumpResetHold = 0
    private var niloreTargetRotation: Rotation? = null
    private var niloreRotationHeldTicks = 0
    private var niloreFlagCooldown = 0

    // HitFrame 状态
    private val hitFrameTimer = MSTimer()
    private var hitFrameActive = false
    private var hitFrameTicks = 0
    private var hitFrameTarget: Entity? = null

    // Grim
    private var timerTicks = 0

    // Vulcan
    private var transaction = false

    // Hypixel
    private var absorbedVelocity = false

    // Pause On Explosion
    private var pauseTicks = 0

    // Leader (Velocity.txt) state
    private var leaderKnockback = false
    private var leaderChanceCounter = 0
    private var leaderRotatoTickCounter = 0
    private var leaderPendingExplosion = false
    private var leaderAllowNext = true
    private var leaderDelayFlag = false
    private var leaderJumpFlag = false
    private var leaderHasReceivedVelocity = false
    private var leaderTicksSinceVelocity = -1
    private var leaderKnockbackX = 0.0
    private var leaderKnockbackZ = 0.0
    private var leaderTargetRotation: Rotation? = null
    private var leaderReduceTick = -1
    private var leaderHitCount = 0
    var leaderExtraAttacked = false
    private var leaderVelocityAttacked = false
    var leaderStoppedBlock = false
    private val leaderDelayedPackets = mutableListOf<Packet<*>>()

    override val tag
        get() = if (mode == "Simple" || mode == "Legit") {
            val horizontalPercentage = (horizontal * 100).toInt()
            val verticalPercentage = (vertical * 100).toInt()

            "$horizontalPercentage% $verticalPercentage%"
        } else if (mode == "Vanilla") {
            "$leaderHorizontal% $leaderVertical%"
        } else mode

    override fun onDisable() {
        pauseTicks = 0
        mc.thePlayer?.speedInAir = 0.02F
        timerTicks = 0
        reset()
        leaderReset()
        cleanupSmart()
        niloreReset()
        hitFrameReset()
    }

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        // Smart（智能）模式：CPS 减少 + JumpReset + 延迟击退包
        updateSmart()

        // Nilore AntiKB（JumpReset / NoXZ）
        updateNilore()

        // HitFrame 受击帧攻击伪造
        updateHitFrame()

        if (thePlayer.isInLiquid || thePlayer.isInWeb || thePlayer.isDead)
            return@handler

        when (mode.lowercase()) {
            "glitch" -> {
                thePlayer.noClip = hasReceivedVelocity

                if (thePlayer.hurtTime == 7)
                    thePlayer.motionY = 0.4

                hasReceivedVelocity = false
            }

            "reverse" -> {
                val nearbyEntity = getNearestEntityInRange()

                if (!hasReceivedVelocity)
                    return@handler

                if (nearbyEntity != null) {
                    if (!thePlayer.onGround) {
                        if (onLook && !isLookingOnEntities(nearbyEntity, maxAngleDifference.toDouble())) {
                            return@handler
                        }

                        speed *= reverseStrength
                    } else if (velocityTimer.hasTimePassed(80))
                        hasReceivedVelocity = false
                }
            }

            "smoothreverse" -> {
                val nearbyEntity = getNearestEntityInRange()

                if (hasReceivedVelocity) {
                    if (nearbyEntity == null) {
                        thePlayer.speedInAir = 0.02F
                        reverseHurt = false
                    } else {
                        if (onLook && !isLookingOnEntities(nearbyEntity, maxAngleDifference.toDouble())) {
                            hasReceivedVelocity = false
                            thePlayer.speedInAir = 0.02F
                            reverseHurt = false
                        } else {
                            if (thePlayer.hurtTime > 0) {
                                reverseHurt = true
                            }

                            if (!thePlayer.onGround) {
                                thePlayer.speedInAir = if (reverseHurt) reverse2Strength else 0.02F
                            } else if (velocityTimer.hasTimePassed(80)) {
                                hasReceivedVelocity = false
                                thePlayer.speedInAir = 0.02F
                                reverseHurt = false
                            }
                        }
                    }
                }
            }

            "aac" -> if (hasReceivedVelocity && velocityTimer.hasTimePassed(80)) {
                thePlayer.motionX *= horizontal
                thePlayer.motionZ *= horizontal
                //mc.thePlayer.motionY *= vertical ?
                hasReceivedVelocity = false
            }

            "aacv4" ->
                if (thePlayer.hurtTime > 0 && !thePlayer.onGround) {
                    val reduce = aacv4MotionReducer
                    thePlayer.motionX *= reduce
                    thePlayer.motionZ *= reduce
                }

            "aacpush" -> {
                if (jump) {
                    if (thePlayer.onGround)
                        jump = false
                } else {
                    // Strafe
                    if (thePlayer.hurtTime > 0 && thePlayer.motionX != 0.0 && thePlayer.motionZ != 0.0)
                        thePlayer.onGround = true

                    // Reduce Y
                    if (thePlayer.hurtResistantTime > 0 && aacPushYReducer && !Speed.handleEvents())
                        thePlayer.motionY -= 0.014999993
                }

                // Reduce XZ
                if (thePlayer.hurtResistantTime >= 19) {
                    val reduce = aacPushXZReducer

                    thePlayer.motionX /= reduce
                    thePlayer.motionZ /= reduce
                }
            }

            "aaczero" ->
                if (thePlayer.hurtTime > 0) {
                    if (!hasReceivedVelocity || thePlayer.onGround || thePlayer.fallDistance > 2F)
                        return@handler

                    thePlayer.motionY -= 1.0
                    thePlayer.isAirBorne = true
                    thePlayer.onGround = true
                } else
                    hasReceivedVelocity = false

            "legit" -> {
                if (legitDisableInAir && !isOnGround(0.5))
                    return@handler

                if (mc.thePlayer.maxHurtResistantTime != mc.thePlayer.hurtResistantTime || mc.thePlayer.maxHurtResistantTime == 0)
                    return@handler

                if (nextInt(endExclusive = 100) < chance) {
                    val horizontal = horizontal / 100f
                    val vertical = vertical / 100f

                    thePlayer.motionX *= horizontal.toDouble()
                    thePlayer.motionZ *= horizontal.toDouble()
                    thePlayer.motionY *= vertical.toDouble()
                }
            }

            "intavereduce" -> {
                if (!hasReceivedVelocity) return@handler
                intaveTick++

                if (mc.thePlayer.hurtTime == 2) {
                    intaveDamageTick++
                    if (thePlayer.onGround && intaveTick % 2 == 0 && intaveDamageTick <= 10) {
                        thePlayer.tryJump()
                        intaveTick = 0
                    }
                    hasReceivedVelocity = false
                }
            }

            "hypixel" -> {
                if (hasReceivedVelocity && thePlayer.onGround) {
                    absorbedVelocity = false
                }
            }

            "hypixelair" -> {
                if (hasReceivedVelocity) {
                    if (thePlayer.onGround) {
                        thePlayer.tryJump()
                    }
                    hasReceivedVelocity = false
                }
            }
        }
    }

    /**
     * LEADER (Velocity.txt) - Vanilla / Prediction modes
     */
    private fun leaderReset() {
        leaderPendingExplosion = false
        leaderStoppedBlock = false
        leaderAllowNext = true
        leaderHasReceivedVelocity = false
        leaderKnockback = false
        leaderDelayFlag = false
        leaderJumpFlag = false
        leaderRotatoTickCounter = 0
        leaderTargetRotation = null
        leaderKnockbackX = 0.0
        leaderKnockbackZ = 0.0
        leaderReduceTick = -1
        leaderTicksSinceVelocity = -1
        synchronized(leaderDelayedPackets) {
            if (leaderDelayedPackets.isNotEmpty()) {
                // 客户端数据包需本地处理，见 releaseSmartPackets 注释
                PacketUtils.schedulePacketProcess(leaderDelayedPackets)
                leaderDelayedPackets.clear()
            }
        }
    }

    private fun leaderInLiquidOrWeb(): Boolean {
        val player = mc.thePlayer ?: return false
        return player.isInLiquid || player.isInWeb
    }

    private fun leaderComputeReduceTicks(motionX: Int, motionZ: Int): Int {
        val kb = Math.hypot(motionX.toDouble(), motionZ.toDouble())
        val ticksExact = 6.43153527E-4 * kb + 2.9419087136
        return Math.round(ticksExact).toInt().coerceIn(1, 10)
    }

    /**
     * Can we attack right now to reduce knockback? (Leader reduceMode "Attack")
     */
    private fun leaderCanAttack(): Boolean {
        if (!leaderReduceWhenCanAttack) return true
        return when (KillAura.autoBlock) {
            "Off", "Packet", "Vanilla", "Fake" -> true
            "OldHypixel", "HypixelNoSlow", "Legit" -> KillAura.blockTick == 0
            "HypixelCustom" -> KillAura.blockTick == KillAura.leaderAttackTick
            "HypixelTest", "HypixelLag" -> KillAura.blockTick == 0 || KillAura.blockTick == 2
            else -> false
        }
    }

    /**
     * Release when can attack (Leader reduceMode "ReleaseWhenCanAttack")
     */
    private fun leaderCanReleaseWhen(): Boolean {
        val killAura = KillAura
        if (!killAura.shouldAutoBlock()) return false
        return when (killAura.autoBlock) {
            "Off", "Packet", "Vanilla", "Fake" -> true
            "OldHypixel", "HypixelNoSlow", "Legit" -> killAura.blockTick == 0
            "HypixelCustom" -> killAura.blockTick == killAura.leaderAttackTick % max(1, killAura.leaderMaxTick - 1)
            "HypixelTest", "HypixelLag" -> killAura.blockTick == 0
            else -> false
        }
    }

    /**
     * Release before can attack (Leader reduceMode "ReleaseBeforeCanAttack")
     */
    private fun leaderCanReleaseBefore(): Boolean {
        val killAura = KillAura
        if (!killAura.shouldAutoBlock()) return false
        return when (killAura.autoBlock) {
            "Off", "Packet", "Vanilla", "Fake" -> true
            "OldHypixel", "HypixelNoSlow", "HypixelLag" -> killAura.blockTick == 2
            "HypixelCustom" ->
                killAura.blockTick == (killAura.leaderAttackTick - 2 + killAura.leaderMaxTick - 1) % max(
                    1, killAura.leaderMaxTick - 1
                )

            "HypixelTest" -> killAura.blockTick == 1
            "Legit" -> killAura.blockTick == 1
            else -> false
        }
    }

    private fun leaderAttack(entity: Entity) {
        val player = mc.thePlayer ?: return
        EventManager.call(AttackEvent(entity))
        sendPacket(C0APacketAnimation())
        sendPacket(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))
        player.motionX *= 0.6
        player.motionZ *= 0.6
        player.setSprinting(false)
    }

    val onLeaderUpdate = handler<UpdateEvent> {
        if (!handleEvents() || mode != "Prediction") return@handler

        val thePlayer = mc.thePlayer ?: return@handler

        // --- PRE phase ---

        // Rotate away from knockback direction
        if (leaderRotate) {
            val maxTick = leaderRotateTick
            if (leaderRotatoTickCounter > 0 && leaderRotatoTickCounter <= maxTick) {
                if (leaderRotatoTickCounter == 1) {
                    val deltaX = -leaderKnockbackX
                    val deltaZ = -leaderKnockbackZ
                    leaderTargetRotation = toRotation(
                        Vec3(thePlayer.posX + deltaX, thePlayer.posY, thePlayer.posZ + deltaZ),
                        predict = false
                    )
                }
                if (leaderTargetRotation != null && !Scaffold.handleEvents()) {
                    val options = RotationSettings(this).apply {
                        prioritizeRequest = true
                        immediate = true
                    }
                    setTargetRotation(leaderTargetRotation!!, options, 1)
                }
            }
            if (leaderRotatoTickCounter > 0 && leaderRotatoTickCounter <= maxTick) {
                leaderRotatoTickCounter++
                if (leaderRotatoTickCounter > maxTick) {
                    leaderRotatoTickCounter = 0
                    leaderTargetRotation = null
                    leaderKnockbackX = 0.0
                    leaderKnockbackZ = 0.0
                }
            }
        }

        // Reduce knockback by attacking
        if (leaderReduce && leaderReduceMode == "Attack") {
            if (leaderVelocityAttacked) {
                val killAuraTarget = KillAura.target
                if (killAuraTarget != null && KillAura.handleEvents() && thePlayer.isSprinting) {
                    leaderAttack(killAuraTarget)
                } else {
                    leaderExtraAttacked = false
                }
                leaderVelocityAttacked = false
            }

            if (leaderHasReceivedVelocity) {
                if (leaderSmartTimes) {
                    if (leaderReduceTick >= leaderHitCount) {
                        leaderReduceTick = 0
                        leaderHasReceivedVelocity = false
                        leaderStoppedBlock = false
                    }
                } else if (leaderReduceTick >= leaderAttackTimes) {
                    leaderReduceTick = 0
                    leaderHasReceivedVelocity = false
                    leaderStoppedBlock = false
                }

                val rotation = currentRotation ?: thePlayer.rotation
                val targetEntity = raycastEntity(3.0, rotation.yaw, rotation.pitch) {
                    it is EntityPlayer && it != thePlayer
                }
                if (targetEntity is EntityPlayer && targetEntity != thePlayer &&
                    (thePlayer.isSprinting || !leaderOnlySprinting)
                ) {
                    val killAuraTarget = KillAura.target
                    if (killAuraTarget != null) {
                        if (leaderCanAttack()) {
                            leaderAttack(killAuraTarget)
                        }
                    } else {
                        leaderAttack(targetEntity)
                    }
                }
                leaderReduceTick++
            }
        }

        // --- POST phase ---

        val delayCondition = leaderDelayFlag && (
            (leaderDelay && (leaderInLiquidOrWeb() || (leaderTicksSinceVelocity >= leaderDelayTicks && !leaderAirBuffer)))
                || (thePlayer.onGround && !leaderGroundDelay && !leaderAirBuffer)
                || (leaderAirBuffer && thePlayer.onGround && leaderDelayFlag)
            ) || (leaderReduceMode == "ReleaseWhenCanAttack" && leaderCanReleaseWhen())
            || (leaderReduceMode == "ReleaseBeforeCanAttack" && leaderCanReleaseBefore())

        if (delayCondition) {
            leaderTicksSinceVelocity = 0

            val killAura = KillAura
            if (killAura.target != null && leaderExtraAttack && leaderReduce && leaderReduceMode != "Attack" && !leaderExtraAttacked) {
                leaderExtraAttacked = true
                leaderVelocityAttacked = true
            }

            if (!leaderTestMode) {
                leaderHasReceivedVelocity = true
            }

            synchronized(leaderDelayedPackets) {
                if (leaderDelayedPackets.isNotEmpty()) {
                    // 客户端数据包需本地处理，见 releaseSmartPackets 注释
                    PacketUtils.schedulePacketProcess(leaderDelayedPackets)
                    leaderDelayedPackets.clear()
                }
            }

            leaderDelayFlag = false

            if (leaderJump && mode == "Prediction") {
                leaderJumpFlag = true
            }
        }

        // Auto jump after releasing the velocity (Leader onLivingUpdate)
        if (leaderJumpFlag) {
            if (thePlayer.onGround && mc.gameSettings.keyBindForward.isKeyDown && !thePlayer.isPotionActive(Potion.jump) &&
                !leaderInLiquidOrWeb() && thePlayer.isSprinting
            ) {
                thePlayer.movementInput.jump = true
            }
            leaderJumpFlag = false
        }
    }

    val onLeaderTick = handler<GameTickEvent> {
        if (!handleEvents() || mode != "Prediction") return@handler

        if (leaderTestMode && leaderReduce && leaderReduceMode == "Attack" && leaderTicksSinceVelocity >= leaderStopBlockHurtTime) {
            leaderHasReceivedVelocity = true
            leaderStoppedBlock = true
        }

        if (leaderTicksSinceVelocity >= 0) {
            leaderTicksSinceVelocity++
        }
        if (leaderTicksSinceVelocity >= 10) {
            leaderTicksSinceVelocity = -1
        }
    }

    val onLeaderMove = handler<MoveEvent> {
        if (!handleEvents() || mode != "Prediction") return@handler

        if (leaderAutoMove && leaderRotatoTickCounter > 0 && leaderRotatoTickCounter <= leaderRotateTick) {
            mc.thePlayer?.movementInput?.moveStrafe = 1.0f
        }
    }

    val onLeaderPacket = handler<PacketEvent> { event ->
        if (!handleEvents()) return@handler

        val packet = event.packet

        if (event.eventType != EventState.RECEIVE || event.isCancelled) return@handler

        when (packet) {
            is S19PacketEntityStatus -> {
                if (packet.getOpCode() == 2.toByte()) {
                    val entity = mc.theWorld?.getEntityByID(packet.entityId)
                    if (entity != null && entity == mc.thePlayer) {
                        leaderAllowNext = false
                    }
                }
            }

            is S27PacketExplosion -> {
                if (mode == "Vanilla" && (packet.field_149152_f != 0.0f || packet.field_149153_g != 0.0f || packet.field_149159_h != 0.0f)) {
                    leaderPendingExplosion = true
                    if (leaderExplosionHorizontal == 0 || leaderExplosionVertical == 0) {
                        event.cancelEvent()
                    }
                }
            }

            is S12PacketEntityVelocity -> {
                val thePlayer = mc.thePlayer ?: return@handler
                if (packet.entityID != thePlayer.entityId) return@handler

                if (mode != "Vanilla" && mode != "Prediction") return@handler

                // onPacket: mark velocity as received
                if (!leaderTestMode) {
                    if (!leaderDelay) leaderHasReceivedVelocity = true
                    if (leaderDelay && !leaderGroundDelay && thePlayer.onGround) leaderHasReceivedVelocity = true
                }

                // onPacket: delay / buffer decision (Prediction)
                if (mode == "Prediction") {
                    val longJumpActive = LongJump.handleEvents() && LongJump.jumped
                    val shouldDelay = !(
                        mode != "Prediction" || leaderDelayFlag || leaderInLiquidOrWeb() || leaderPendingExplosion ||
                            Stuck.handleEvents() || leaderAllowNext && leaderFakeCheck || longJumpActive ||
                            !((leaderAirBuffer && !thePlayer.onGround) || (leaderDelay && !thePlayer.onGround)) &&
                            (!leaderDelay || !leaderGroundDelay || leaderAirBuffer)
                        )

                    if (shouldDelay) {
                        event.cancelEvent()
                        synchronized(leaderDelayedPackets) {
                            leaderDelayedPackets += packet
                        }
                        leaderDelayFlag = true
                    }
                }

                // onKnockback: reduction / setup
                if (!leaderAllowNext || !leaderFakeCheck) {
                    leaderAllowNext = true

                    if (leaderPendingExplosion) {
                        if (mode == "Vanilla") {
                            leaderPendingExplosion = false
                            if (leaderExplosionHorizontal > 0) {
                                packet.motionX = (packet.motionX * leaderExplosionHorizontal / 100).toInt()
                                packet.motionZ = (packet.motionZ * leaderExplosionHorizontal / 100).toInt()
                            } else {
                                packet.motionX = (thePlayer.motionX * 8000).toInt()
                                packet.motionZ = (thePlayer.motionZ * 8000).toInt()
                            }
                            if (leaderExplosionVertical > 0) {
                                packet.motionY = (packet.motionY * leaderExplosionVertical / 100).toInt()
                            } else {
                                packet.motionY = (thePlayer.motionY * 8000).toInt()
                            }
                        }
                    } else {
                        if (!handleEvents() || event.isCancelled) {
                            leaderPendingExplosion = false
                            leaderAllowNext = true
                            return@handler
                        }

                        if (mode == "Prediction") {
                            if (leaderRotate && packet.motionY > 0) {
                                leaderKnockbackX = packet.motionX / 8000.0
                                leaderKnockbackZ = packet.motionZ / 8000.0
                                if (Math.abs(leaderKnockbackX) > 0.01 || Math.abs(leaderKnockbackZ) > 0.01) {
                                    leaderRotatoTickCounter = 1
                                }
                            }
                            if (leaderSmartTimes) {
                                leaderHitCount = leaderComputeReduceTicks(packet.motionX, packet.motionZ)
                            }
                            if (leaderDelay && !leaderGroundDelay && thePlayer.onGround) {
                                if (leaderJump) leaderJumpFlag = true
                                leaderTicksSinceVelocity = 0
                            }
                            if (!leaderDelay) leaderTicksSinceVelocity = 0
                        }

                        if (mode == "Vanilla") {
                            leaderChanceCounter = leaderChanceCounter % 100 + leaderChance
                            if (leaderChanceCounter >= 100) {
                                if (leaderHorizontal > 0) {
                                    packet.motionX = (packet.motionX * leaderHorizontal / 100).toInt()
                                    packet.motionZ = (packet.motionZ * leaderHorizontal / 100).toInt()
                                } else {
                                    packet.motionX = (thePlayer.motionX * 8000).toInt()
                                    packet.motionZ = (thePlayer.motionZ * 8000).toInt()
                                }
                                if (leaderVertical > 0) {
                                    packet.motionY = (packet.motionY * leaderVertical / 100).toInt()
                                } else {
                                    packet.motionY = (thePlayer.motionY * 8000).toInt()
                                }
                            }
                        }
                    }
                }

                leaderKnockback = true
            }
        }
    }

    /**
     * @see net.minecraft.entity.player.EntityPlayer.attackTargetEntityWithCurrentItem
     * Lines 1035 and 1058
     *
     * Minecraft only applies motion slow-down when you are sprinting and attacking, once per tick.
     * An example scenario: If you perform a mouse double-click on an entity, the game will only accept the first attack.
     *
     * This is where we come in clutch by making the player always sprint before dropping
     *
     * [clicks] amount of hits on the target [entity]
     *
     * We also explicitly-cast the player as an [Entity] to avoid triggering any other things caused from setting new sprint status.
     *
     * @see net.minecraft.client.entity.EntityPlayerSP.setSprinting
     * @see net.minecraft.entity.EntityLivingBase.setSprinting
     */
    val onGameTick = handler<GameTickEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        mc.theWorld ?: return@handler

        if (mode != "Click" || thePlayer.hurtTime != hurtTimeToClick || ignoreBlocking && (thePlayer.isBlocking || KillAura.blockStatus))
            return@handler

        // 受击射线找实体：开启后主动搜索（不依赖准星），优先于原来的"准星/最近实体"逻辑
        var entity = if (hurtRaycast) findHurtRaycastEntity(thePlayer) else null

        if (entity == null) {
            entity = mc.objectMouseOver?.entityHit
        }

        if (entity == null) {
            if (whenFacingEnemyOnly) {
                var result: Entity? = null

                runWithModifiedRaycastResult(
                    currentRotation ?: thePlayer.rotation,
                    clickRange.toDouble(),
                    0.0
                ) {
                    result = it.entityHit?.takeIf { isSelected(it, true) }
                }

                entity = result
            } else getNearestEntityInRange(clickRange)?.takeIf { isSelected(it, true) }
        }

        val targetEntity = entity ?: return@handler

        // 受击射线模式：静默转头对准目标。
        // 复用 hurtRotationOptions（学习 KillAura2 的 rotations.options），
        // applyServerSide 保证只改服务端朝向、不动本地相机。
        if (hurtRaycast) {
            val rotation = toRotation(targetEntity.hitBox.center, predict = false)
            setTargetRotation(rotation, hurtRotationOptions, 1)

            // 只有显式开启 HurtSnapRotation 才会改变本地视角
            if (hurtSnapRotation) {
                thePlayer.rotationYaw = rotation.yaw
                thePlayer.rotationPitch = rotation.pitch
                thePlayer.prevRotationYaw = rotation.yaw
                thePlayer.prevRotationPitch = rotation.pitch
            }
        }

        val swingHand = {
            when (swingMode.lowercase()) {
                "normal" -> thePlayer.swingItem()
                "packet" -> sendPacket(C0APacketAnimation())
            }
        }

        // 受击射线模式用固定的一瞬间 CPS，否则维持原来的 Clicks 区间随机
        val burst = if (hurtRaycast) instantCps else clicks.random()

        repeat(burst) {
            thePlayer.attackEntityWithModifiedSprint(targetEntity, true) { swingHand() }
        }
    }

    /**
     * 受击射线搜索实体：
     *  1. 先按当前朝向打一条射线（[clickRange]）；
     *  2. 打不到则在 [hurtRaycastFov] 范围内左右扇形补扫；
     *  3. 仍然没有，退化为"范围内最近的合法实体"。
     */
    private fun findHurtRaycastEntity(player: EntityPlayer): Entity? {
        val range = clickRange.toDouble()

        val filter = { entity: Entity -> isSelected(entity, true) }

        val rotation = currentRotation ?: player.rotation

        raycastEntity(range, rotation.yaw, rotation.pitch, filter)?.let { return it }

        val fov = hurtRaycastFov

        if (fov > 0f) {
            val step = 15f
            var offset = step

            while (offset <= fov) {
                for (sign in intArrayOf(-1, 1)) {
                    raycastEntity(range, rotation.yaw + offset * sign, rotation.pitch, filter)?.let { return it }
                }

                offset += step
            }
        }

        return getNearestEntityInRange(clickRange)?.takeIf { isSelected(it, true) }
    }

    val onAttack = handler<AttackEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mode != "IntaveReduce" || !hasReceivedVelocity) return@handler

        if (player.hurtTime == hurtTime && System.currentTimeMillis() - lastAttackTime <= 8000) {
            player.motionX *= reduceFactor
            player.motionZ *= reduceFactor
        }

        lastAttackTime = System.currentTimeMillis()
    }

    private fun checkAir(blockPos: BlockPos): Boolean {
        val world = mc.theWorld ?: return false

        if (!world.isAirBlock(blockPos)) {
            return false
        }

        timerTicks = 20

        sendPackets(
            C03PacketPlayer(true),
            C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, DOWN)
        )

        world.setBlockToAir(blockPos)

        return true
    }

    // TODO: Recode
    private fun getDirection(): Double {
        var moveYaw = mc.thePlayer.rotationYaw
        when {
            mc.thePlayer.moveForward != 0f && mc.thePlayer.moveStrafing == 0f -> {
                moveYaw += if (mc.thePlayer.moveForward > 0) 0 else 180
            }

            mc.thePlayer.moveForward != 0f && mc.thePlayer.moveStrafing != 0f -> {
                if (mc.thePlayer.moveForward > 0) moveYaw += if (mc.thePlayer.moveStrafing > 0) -45 else 45 else moveYaw -= if (mc.thePlayer.moveStrafing > 0) -45 else 45
                moveYaw += if (mc.thePlayer.moveForward > 0) 0 else 180
            }

            mc.thePlayer.moveStrafing != 0f && mc.thePlayer.moveForward == 0f -> {
                moveYaw += if (mc.thePlayer.moveStrafing > 0) -90 else 90
            }
        }
        return Math.floorMod(moveYaw.toInt(), 360).toDouble()
    }

    val onPacket = handler<PacketEvent>(priority = 1) { event ->
        val thePlayer = mc.thePlayer ?: return@handler

        val packet = event.packet

        if (!handleEvents())
            return@handler

        if (pauseTicks > 0) {
            pauseTicks--
            return@handler
        }

        if (event.isCancelled)
            return@handler

        if ((packet is S12PacketEntityVelocity && thePlayer.entityId == packet.entityID && packet.motionY > 0 && (packet.motionX != 0 || packet.motionZ != 0))
            || (packet is S27PacketExplosion && (thePlayer.motionY + packet.field_149153_g) > 0.0
                    && ((thePlayer.motionX + packet.field_149152_f) != 0.0 || (thePlayer.motionZ + packet.field_149159_h) != 0.0))
        ) {
            velocityTimer.reset()

            if (pauseOnExplosion && packet is S27PacketExplosion && (thePlayer.motionY + packet.field_149153_g) > 0.0
                && ((thePlayer.motionX + packet.field_149152_f) != 0.0 || (thePlayer.motionZ + packet.field_149159_h) != 0.0)
            ) {
                pauseTicks = ticksToPause
            }

            when (mode.lowercase()) {
                "simple" -> handleVelocity(event)

                "aac", "reverse", "smoothreverse", "aaczero", "ghostblock", "intavereduce" -> hasReceivedVelocity = true

                "jump" -> {
                    // TODO: Recode and make all velocity modes support velocity direction checks
                    var packetDirection = 0.0
                    when (packet) {
                        is S12PacketEntityVelocity -> {
                            if (packet.entityID != thePlayer.entityId) return@handler

                            val motionX = packet.motionX.toDouble()
                            val motionZ = packet.motionZ.toDouble()

                            packetDirection = atan2(motionX, motionZ)
                        }

                        is S27PacketExplosion -> {
                            val motionX = thePlayer.motionX + packet.field_149152_f
                            val motionZ = thePlayer.motionZ + packet.field_149159_h

                            packetDirection = atan2(motionX, motionZ)
                        }
                    }
                    val degreePlayer = getDirection()
                    val degreePacket = Math.floorMod(packetDirection.toDegrees().toInt(), 360).toDouble()
                    var angle = abs(degreePacket + degreePlayer)
                    val threshold = 120.0
                    angle = Math.floorMod(angle.toInt(), 360).toDouble()
                    val inRange = angle in 180 - threshold / 2..180 + threshold / 2
                    if (inRange)
                        hasReceivedVelocity = true
                }

                "glitch" -> {
                    if (!thePlayer.onGround)
                        return@handler

                    hasReceivedVelocity = true
                    event.cancelEvent()
                }

                "matrixreduce" -> {
                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        packet.motionX = (packet.getMotionX() * 0.33).toInt()
                        packet.motionZ = (packet.getMotionZ() * 0.33).toInt()

                        if (thePlayer.onGround) {
                            packet.motionX = (packet.getMotionX() * 0.86).toInt()
                            packet.motionZ = (packet.getMotionZ() * 0.86).toInt()
                        }
                    }
                }

                // Credit: @LiquidSquid / Ported from NextGen
                "blocksmc" -> {
                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        hasReceivedVelocity = true
                        event.cancelEvent()

                        sendPacket(C0BPacketEntityAction(thePlayer, START_SNEAKING))
                        sendPacket(C0BPacketEntityAction(thePlayer, STOP_SNEAKING))
                    }
                }

                "smart" -> {
                    // Delay 反击退：空中收到击退包先缓存，落地后释放（超时限定）
                    smartKnockbackActive = true
                    smartTimeoutTicks = 0
                    if (smartDelay && !thePlayer.onGround) {
                        event.cancelEvent()
                        synchronized(smartDelayedPackets) {
                            smartDelayedPackets += packet
                        }
                    }
                }

                "grimc03" -> {
                    // Checks to prevent from getting flagged (BadPacketsE)
                    if (thePlayer.isMoving) {
                        hasReceivedVelocity = true
                        event.cancelEvent()
                    }
                }

                "hypixel" -> {
                    hasReceivedVelocity = true
                    if (!thePlayer.onGround) {
                        if (!absorbedVelocity) {
                            event.cancelEvent()
                            absorbedVelocity = true
                            return@handler
                        }
                    }

                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        packet.motionX = (thePlayer.motionX * 8000).toInt()
                        packet.motionZ = (thePlayer.motionZ * 8000).toInt()
                    }
                }

                "hypixelair" -> {
                    hasReceivedVelocity = true
                    event.cancelEvent()
                }

                "hitframe" -> hitFrameStart(packet)

                "vulcan" -> {
                    event.cancelEvent()
                }

                "s32packet" -> {
                    hasReceivedVelocity = true
                    event.cancelEvent()
                }
            }
        }

        if (mode == "BlocksMC" && hasReceivedVelocity) {
            if (packet is C0BPacketEntityAction) {
                hasReceivedVelocity = false
                event.cancelEvent()
            }
        }

        if (mode == "Vulcan") {
            if (Disabler.handleEvents() && Disabler.verusCombat && (!Disabler.onlyCombat || Disabler.isOnCombat)) return@handler

            if (packet is S32PacketConfirmTransaction) {
                event.cancelEvent()
                sendPacket(
                    C0FPacketConfirmTransaction(
                        if (transaction) 1 else -1,
                        if (transaction) -1 else 1,
                        transaction
                    ), false
                )
                transaction = !transaction
            }
        }

        if (mode == "S32Packet" && packet is S32PacketConfirmTransaction) {

            if (!hasReceivedVelocity)
                return@handler

            event.cancelEvent()
            hasReceivedVelocity = false
        }
    }

    /**
     * Tick Event (Abuse Timer Balance)
     */
    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mode != "GrimC03")
            return@handler

        // Timer Abuse (https://github.com/CCBlueX/LiquidBounce/issues/2519)
        if (timerTicks > 0 && mc.timer.timerSpeed <= 1) {
            val timerSpeed = 0.8f + (0.2f * (20 - timerTicks) / 20)
            mc.timer.timerSpeed = timerSpeed.coerceAtMost(1f)
            --timerTicks
        } else if (mc.timer.timerSpeed <= 1) {
            mc.timer.timerSpeed = 1f
        }

        if (hasReceivedVelocity) {
            val pos = BlockPos(player.posX, player.posY, player.posZ)

            if (checkAir(pos))
                hasReceivedVelocity = false
        }
    }

    /**
     * Delay Mode
     */
    val onDelayPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (event.isCancelled)
            return@handler

        if (mode == "Delay") {
            if (packet is S32PacketConfirmTransaction || packet is S12PacketEntityVelocity) {

                event.cancelEvent()

                // Delaying packet like PingSpoof
                synchronized(packets) {
                    packets[packet] = System.currentTimeMillis()
                }
            }
            delayMode = true
        } else {
            delayMode = false
        }
    }

    /**
     * Reset on world change
     */
    val onWorld = handler<WorldEvent> {
        packets.clear()
        // 世界切换 / 重连时丢弃 Smart 缓存的击退包，避免跨世界释放旧包（实体已失效）
        synchronized(smartDelayedPackets) {
            smartDelayedPackets.clear()
        }
        if (smartKnockbackActive) {
            smartKnockbackActive = false
            smartTimeoutTicks = 0
            KillAura.setCpsReduction(false)
        }
        hitFrameReset()
    }

    val onGameLoop = handler<GameLoopEvent> {
        if (mode == "Delay")
            sendPacketsByOrder(false)
    }

    private fun sendPacketsByOrder(velocity: Boolean) {
        synchronized(packets) {
            packets.entries.removeAll { (packet, timestamp) ->
                if (velocity || timestamp <= System.currentTimeMillis() - spoofDelay) {
                    PacketUtils.schedulePacketProcess(packet)
                    true
                } else false
            }
        }
    }

    private fun reset() {
        sendPacketsByOrder(true)

        packets.clear()
    }

    /**
     * Smart（智能）模式每 tick 更新：
     * 1) 击退期间智能降低 KillAura CPS（CPSReduce / ReducedCPS）；
     * 2) 落地瞬间 JumpReset 起跳抵消击退（JumpReset）；
     * 3) 空中缓存的击退包在落地或超时后统一释放（Delay / DelayTimeout）。
     */
    private fun updateSmart() {
        val player = mc.thePlayer ?: return

        if (mode != "Smart") {
            if (smartKnockbackActive || KillAura.cpsReductionActive) cleanupSmart()
            return
        }

        // 击退期间智能分配 CPS
        if (smartKnockbackActive && smartCpsReduce) {
            KillAura.setCpsReduction(true, smartReducedCps.first, smartReducedCps.last)
        } else {
            KillAura.setCpsReduction(false)
        }

        if (!smartKnockbackActive) return

        smartTimeoutTicks++

        // JumpReset：落地瞬间起跳抵消水平击退
        if (smartJumpReset && player.onGround && player.hurtTime > 0 && !player.isInLiquid && !player.isInWeb) {
            if (System.currentTimeMillis() - smartLastJump > 150) {
                player.tryJump()
                smartLastJump = System.currentTimeMillis()
            }
        }

        // 释放：已落地 或 超过超时限定
        if (player.onGround || smartTimeoutTicks >= smartDelayTimeout) {
            releaseSmartPackets()
        }
    }

    /**
     * 释放所有缓存的击退包并复位 Smart 状态。
     */
    private fun releaseSmartPackets() {
        synchronized(smartDelayedPackets) {
            if (smartDelayedPackets.isNotEmpty()) {
                // 缓存的 S12/S27 是客户端数据包，不能走 outbound 编码器（会抛
                // "Can't serialize unregistered packet"），必须按接收方式本地处理。
                PacketUtils.schedulePacketProcess(smartDelayedPackets)
                smartDelayedPackets.clear()
            }
        }
        smartKnockbackActive = false
        smartTimeoutTicks = 0
        KillAura.setCpsReduction(false)
    }

    /**
     * 清理 Smart 状态（模块关闭 / 切换模式时调用）。
     */
    private fun cleanupSmart() {
        if (smartKnockbackActive) {
            releaseSmartPackets()
        } else {
            KillAura.setCpsReduction(false)
        }
        smartDelayedPackets.clear()
    }

    /**
     * ================= HitFrame（受击帧攻击伪造）=================
     * 收到击退（S12/S27）瞬间开启攻击注入窗口：
     *  - 窗口内以 FakeCPS 间隔调用 playerController.attackEntity（真实原版攻击），
     *    冲刺攻击时 vanilla 会自动对自身 motionX/Z 应用 0.6x 减速（击退衰减的来源）；
     *  - 全程不修改本地 motion，Grim Simulation 检测认可原版物理，不会判违规；
     *  - 高频只持续 AttackWindow tick，避免触发 Grim AutoClicker。
     */

    private fun hitFrameStart(packet: Packet<*>) {
        hitFrameActive = true
        hitFrameTicks = hitFrameWindow
        hitFrameTarget = hitFrameFindTarget()
        hitFrameTimer.reset()

        if (hitFrameDebug) {
            val kb = packet as? S12PacketEntityVelocity
            val info = if (kb != null) "X=${kb.motionX} Y=${kb.motionY} Z=${kb.motionZ}" else "explosion"
            val targetName = (hitFrameTarget as? EntityLivingBase)?.name ?: "none"
            chat("§8[§bHitFrame§8] §7KB ($info) → §a$targetName")
        }
    }

    private fun hitFrameFindTarget(): Entity? {
        val player = mc.thePlayer ?: return null

        KillAura.target?.let { target ->
            if (!target.isDead && player.getDistanceToEntityBox(target) <= hitFrameRange) return target
        }

        val hit = mc.objectMouseOver?.entityHit
        if (hit is EntityLivingBase && hit !== player && !hit.isDead &&
            player.getDistanceToEntityBox(hit) <= hitFrameRange
        ) return hit

        return mc.theWorld?.loadedEntityList?.filter {
            it is EntityLivingBase && it !== player && !it.isDead &&
                player.getDistanceToEntityBox(it) <= hitFrameRange
        }?.minByOrNull { player.getDistanceToEntityBox(it) }
    }

    private fun updateHitFrame() {
        val player = mc.thePlayer ?: return

        if (mode != "HitFrame") {
            if (hitFrameActive) hitFrameReset()
            return
        }
        if (!hitFrameActive) return

        // 窗口倒数（不修改本地 motion，避免触发 Grim Simulation 检测）
        hitFrameTicks--
        if (hitFrameTicks < 0) {
            hitFrameReset()
            return
        }

        // 目标失效则重新寻找
        val current = hitFrameTarget
        if (current == null || current.isDead || player.getDistanceToEntityBox(current) > hitFrameRange) {
            hitFrameTarget = hitFrameFindTarget()
        }

        val target = hitFrameTarget ?: run {
            if (hitFrameDebug) chat("§8[§bHitFrame§8] §7no target in range")
            hitFrameReset()
            return
        }
        if (hitFrameOnlyTarget && player.getDistanceToEntityBox(target) > hitFrameRange) {
            hitFrameReset()
            return
        }

        // 冲刺检查：原版 0.6x 减速只在冲刺攻击时触发，非冲刺攻击无意义
        if (hitFrameSprintCheck && !player.isSprinting) {
            if (hitFrameDebug) chat("§8[§bHitFrame§8] §7not sprinting, skip")
            return
        }

        // FakeCPS 攻击注入：走真实原版攻击路径 playerController.attackEntity，
        // 内部会触发 vanilla 冲刺攻击的 motionX/Z *= 0.6 减速，与 Grim Simulation 预期一致
        val interval = (1000.0 / hitFrameFakeCps).toLong()
        if (hitFrameTimer.hasTimePassed(interval)) {
            EventManager.call(AttackEvent(target))
            mc.playerController.attackEntity(player, target)
            hitFrameTimer.reset()
            if (hitFrameDebug) chat("§8[§bHitFrame§8] §7attack ${(target as? EntityLivingBase)?.name ?: "entity"}")
        }
    }

    private fun hitFrameReset() {
        hitFrameActive = false
        hitFrameTicks = 0
        hitFrameTarget = null
    }

    /**
     * ================= Nilore AntiKB（JumpReset / NoXZ）=================
     * 移植自 OpenNilore 的 AntiKB 模块：
     *  - JumpReset：收到击退包后挂起，空中保持跳跃 / 落地后转向击退方向起跳，落地时释放。
     *  - NoXZ：挂起击退包与移动包，落地时通过快速攻击抵消水平击退（可 Instant Attack 加速）。
     */

    /**
     * Nilore 数据包处理（优先于主 onPacket 执行）。
     */
    val onNilorePacket = handler<PacketEvent>(priority = 0) { event ->
        if (mode != "JumpReset" && mode != "NoXZ") return@handler
        if (!handleEvents()) return@handler
        val player = mc.thePlayer ?: return@handler
        if (event.isCancelled) return@handler
        val packet = event.packet

        // 发送方向：NoXZ 挂起时拦截 C03 移动包，稍后统一释放
        if (event.eventType == EventState.SEND) {
            if (mode == "NoXZ" && niloreSuspending && packet is C03PacketPlayer) {
                event.cancelEvent()
                synchronized(niloreMoveQueue) { niloreMoveQueue += packet }
            }
            return@handler
        }

        // ---- 接收方向 ----
        if (niloreShouldIgnore()) {
            if (niloreSuspending) niloreRelease()
            return@handler
        }

        // NoXZ：挂起时拦截实体移动包
        if (mode == "NoXZ" && niloreSuspending && packet is S14PacketEntity) {
            event.cancelEvent()
            synchronized(nilorePacketQueue) { nilorePacketQueue += packet }
            return@handler
        }

        // 拉回包 → 释放并复位（被服务器拉回说明触发反作弊）
        if (packet is S08PacketPlayerPosLook) {
            if (niloreSuspending) niloreRelease()
            niloreReset()
            niloreFlagCooldown = 2
            if (niloreDebug) chat("§8[§cAntiKB§8] §7Flag detected, released")
            return@handler
        }

        if (niloreFlagCooldown > 0) return@handler

        if (niloreSuspending) {
            if (niloreIsAllowedPacket(packet)) return@handler
            event.cancelEvent()
            synchronized(nilorePacketQueue) { nilorePacketQueue += packet }
            return@handler
        }

        if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
            val dx = -packet.motionX.toDouble()
            val dz = -packet.motionZ.toDouble()
            if (abs(dx) > 0.01 || abs(dz) > 0.01) niloreHitCounter = 1

            if (packet.motionY > 0) {
                if (mode == "NoXZ" && niloreJumpReset && player.onGround && !niloreShouldIgnore()) {
                    niloreJumpResetQueued = true
                    niloreJumpResetHold = 2
                }
                niloreSprintBoost = (niloreSprintBoost % 100) + 100
                if (niloreSprintBoost >= 100) niloreShouldJump = true

                val target = niloreGetTarget()
                val canAttack = niloreIsValidTarget(target) && player.isSprinting

                if (mode == "NoXZ") {
                    if (!player.onGround) {
                        niloreStartSuspension(packet, event, 1)
                    } else if (canAttack) {
                        niloreAttackTarget = target
                        niloreAttacksRemaining = niloreGetAttackCount(packet)
                    } else {
                        niloreStartSuspension(packet, event, 1)
                        if (niloreDebug) chat("§8[§cAntiKB§8] §7NoXZ wait")
                    }
                } else { // JumpReset
                    niloreStartSuspension(packet, event, if (player.onGround) 2 else 1)
                }
            }
        }
    }

    private fun niloreStartSuspension(packet: S12PacketEntityVelocity, event: PacketEvent, phase: Int) {
        niloreSuspending = true
        nilorePhase = phase
        niloreSuspendTicks = 0
        niloreKnockback = packet
        niloreDelayTicks = if (phase == 2) 10 else 20
        event.cancelEvent()
        synchronized(nilorePacketQueue) { nilorePacketQueue += packet }

        // 朝向击退方向（JumpReset Rotate / Follow Direction）
        if (mode == "JumpReset" && (niloreRotate || niloreFollowDirection)) {
            val yaw = Math.toDegrees(atan2(packet.motionX.toDouble(), -packet.motionZ.toDouble())).toFloat()
            val rot = Rotation(yaw, mc.thePlayer!!.rotationPitch)
            niloreTargetRotation = rot
            val options = RotationSettings(this).apply { prioritizeRequest = true; immediate = true }
            setTargetRotation(rot, options, 1)
        }
    }

    private fun niloreIsAllowedPacket(packet: Packet<*>): Boolean = when (packet) {
        is S00PacketKeepAlive, is S03PacketTimeUpdate, is S32PacketConfirmTransaction,
        is S12PacketEntityVelocity, is S06PacketUpdateHealth, is S08PacketPlayerPosLook,
        is S29PacketSoundEffect, is S02PacketChat, is S2EPacketCloseWindow,
        is S45PacketTitle, is S3EPacketTeams, is S40PacketDisconnect, is S0BPacketAnimation
        -> true

        else -> false
    }

    private fun niloreShouldIgnore(): Boolean {
        val player = mc.thePlayer ?: return true
        if (mc.theWorld == null || player.isDead || player.health <= 0f) return true
        if (player.capabilities.isFlying || player.capabilities.allowFlying) return true
        if (player.isInLava || player.isBurning || player.isInWater ||
            player.isOnLadder || player.isPlayerSleeping() || player.isInWeb
        ) return true
        if (Stuck.handleEvents()) return true
        return false
    }

    private fun niloreGetTarget(): Entity? {
        KillAura.target?.let { return it }
        val hit = mc.objectMouseOver?.entityHit
        if (hit is EntityLivingBase && hit !== mc.thePlayer && !hit.isDead) return hit
        return null
    }

    private fun niloreIsValidTarget(entity: Entity?): Boolean {
        if (entity == null || entity.isDead) return false
        if (entity is EntityLivingBase && (entity.isDead || entity.health <= 0f)) return false
        val player = mc.thePlayer ?: return false
        return player.getDistanceToEntityBox(entity) <= 3.7
    }

    private fun niloreGetAttackCount(kb: S12PacketEntityVelocity?): Int {
        if (!niloreAutoAttackCount || kb == null) return niloreAttackAmount
        val velocity = sqrt(kb.motionX.toDouble() * kb.motionX + kb.motionY.toDouble() * kb.motionY)
        return when {
            velocity < 1000.0 -> 0
            velocity < 2000.0 -> 3
            velocity < 10000.0 -> 4
            else -> 5
        }
    }

    private fun niloreClearTarget() {
        niloreAttackTarget = null
        niloreAttacksRemaining = 0
    }

    private fun niloreSendMovePackets() {
        synchronized(niloreMoveQueue) {
            if (niloreMoveQueue.isNotEmpty()) {
                sendPackets(*niloreMoveQueue.toTypedArray(), triggerEvents = false)
                niloreMoveQueue.clear()
            }
        }
    }

    private fun niloreApplyKnockback() {
        val kb = niloreKnockback ?: return
        PacketUtils.schedulePacketProcess(listOf(kb))
        niloreKnockback = null
    }

    private fun niloreFlushQueuedPackets() {
        synchronized(nilorePacketQueue) {
            if (nilorePacketQueue.isNotEmpty()) {
                PacketUtils.schedulePacketProcess(nilorePacketQueue.toList())
                nilorePacketQueue.clear()
            }
        }
    }

    private fun niloreRelease() {
        niloreSendMovePackets()
        niloreApplyKnockback()
        niloreFlushQueuedPackets()
        niloreSuspending = false
        nilorePhase = 0
        niloreSuspendTicks = 0
        niloreKnockback = null
        niloreInstantProgress = 0f
        niloreInstantAttacking = false
        if (mc.timer.timerSpeed != 1f) mc.timer.timerSpeed = 1f
    }

    private fun niloreDoAttack(entity: Entity): Boolean {
        val player = mc.thePlayer ?: return false
        if (niloreSprintCheck && !player.isSprinting) {
            if (niloreDebug) chat("§8[§cAntiKB§8] §7not sprinting")
            return false
        }
        val wasSprinting = player.isSprinting
        if (wasSprinting) player.setSprinting(false)
        mc.playerController.attackEntity(player, entity)
        player.swingItem()
        if (wasSprinting) {
            player.motionX *= 0.6
            player.motionZ *= 0.6
        }
        return true
    }

    private fun niloreDoAttackSequence() {
        val target = niloreAttackTarget
        if (target == null || target.isDead) {
            niloreClearTarget()
            return
        }
        val player = mc.thePlayer ?: return
        if (player.getDistanceToEntityBox(target) > 3.7) {
            niloreClearTarget()
            return
        }
        niloreAttacksRemaining--
        niloreAttackCooldown = 2
        niloreDoAttack(target)
        if (niloreAttacksRemaining <= 0) niloreClearTarget()
    }

    private fun niloreReset() {
        synchronized(nilorePacketQueue) { nilorePacketQueue.clear() }
        synchronized(niloreMoveQueue) { niloreMoveQueue.clear() }
        niloreSuspending = false
        niloreSuspendTicks = 0
        niloreKnockback = null
        nilorePhase = 0
        niloreDelayTicks = 0
        niloreJumpTicks = 0
        niloreHitCounter = 0
        niloreAttackCooldown = 0
        niloreAttacksRemaining = 0
        niloreAttackTarget = null
        niloreShouldJump = false
        niloreSprintBoost = 0
        niloreInstantProgress = 0f
        niloreInstantAttacking = false
        niloreFlushMotion = false
        niloreJumpResetQueued = false
        niloreJumpResetHold = 0
        niloreTargetRotation = null
        niloreRotationHeldTicks = 0
        niloreFlagCooldown = 0
        if (mc.timer.timerSpeed != 1f) mc.timer.timerSpeed = 1f
    }

    /**
     * Nilore 每 tick 更新。
     */
    private fun updateNilore() {
        if (mode != "JumpReset" && mode != "NoXZ") {
            if (niloreSuspending || niloreInstantAttacking || niloreFlushMotion) niloreReset()
            return
        }
        val player = mc.thePlayer ?: return

        // ============ JumpReset 专属 tick 逻辑 ============
        if (mode == "JumpReset") {
            if (niloreSuspending) {
                when (nilorePhase) {
                    1 -> { // AIR：落地或超时后释放
                        if (player.onGround) {
                            niloreRelease()
                            niloreReset()
                        } else if (niloreDelayTicks > 0) {
                            niloreDelayTicks--
                        } else {
                            niloreRelease()
                            niloreReset()
                        }
                        mc.gameSettings.keyBindJump.pressed = true
                        return
                    }

                    2 -> { // GROUND：延迟后释放 + 转向 + 起跳
                        if (niloreDelayTicks > 0) {
                            niloreDelayTicks--
                        } else {
                            niloreRelease()
                            if (niloreTargetRotation != null && (niloreRotate || niloreFollowDirection)) {
                                val options = RotationSettings(this).apply { prioritizeRequest = true; immediate = true }
                                setTargetRotation(niloreTargetRotation!!, options, 1)
                            }
                            niloreTargetRotation = null
                            niloreReset()
                            niloreJumpTicks = 1
                        }
                        mc.gameSettings.keyBindJump.pressed = true
                        return
                    }
                }
            }
            if (niloreJumpTicks > 0) {
                mc.gameSettings.keyBindJump.pressed = true
                niloreJumpTicks--
                return
            }
            // 复位跳跃键为物理按键状态
            mc.gameSettings.keyBindJump.pressed = Keyboard.isKeyDown(mc.gameSettings.keyBindJump.keyCode)

            if (niloreTargetRotation != null) niloreRotationHeldTicks++
            if (player.hurtTime == 0 || niloreRotationHeldTicks > niloreRotateTicks) {
                niloreTargetRotation = null
                niloreRotationHeldTicks = 0
            }
            return
        }

        // ============ NoXZ 专属 tick 逻辑 ============
        if (niloreJumpResetQueued) {
            if (niloreJumpResetHold > 0) {
                if (player.onGround && !niloreShouldIgnore() && !niloreSuspending)
                    mc.gameSettings.keyBindJump.pressed = true
                niloreJumpResetHold--
            } else {
                niloreJumpResetQueued = false
                mc.gameSettings.keyBindJump.pressed = Keyboard.isKeyDown(mc.gameSettings.keyBindJump.keyCode)
            }
        }

        if (niloreShouldIgnore()) {
            niloreClearTarget()
            if (niloreSuspending) niloreRelease()
            if (niloreInstantAttacking) {
                niloreInstantAttacking = false
                niloreInstantProgress = 0f
                mc.timer.timerSpeed = 1f
            }
            return
        }

        if (niloreFlagCooldown > 0) {
            niloreFlagCooldown--
            niloreClearTarget()
        }
        if (niloreAttackCooldown > 0) {
            niloreAttackCooldown--
            if (niloreAttackCooldown <= 0) niloreClearTarget()
        }
        if (niloreHitCounter > 0) {
            niloreHitCounter++
            if (niloreHitCounter > 2) niloreHitCounter = 0
        }

        if (niloreSuspending) {
            niloreSuspendTicks++
            if (niloreInstantAttack && niloreInstantProgress < 3f) {
                mc.timer.timerSpeed = 0.5f
                niloreInstantProgress = minOf(niloreInstantProgress + 0.5f, 3f)
            }
            val onGround = player.onGround
            val timeout = niloreSuspendTicks >= 12
            if (onGround || timeout) {
                if (niloreInstantAttack) mc.timer.timerSpeed = 1f
                val target = niloreGetTarget()
                val canAttack = niloreIsValidTarget(target)
                val sprinting = player.isSprinting
                if (onGround && canAttack && sprinting) {
                    niloreAttackTarget = target
                    niloreAttacksRemaining = niloreGetAttackCount(niloreKnockback)
                    niloreSendMovePackets()
                    niloreApplyKnockback()
                    if (niloreInstantAttack && niloreInstantProgress > 0f) {
                        niloreAttacksRemaining = niloreInstantProgress.toInt()
                        niloreFlushMotion = true
                        niloreSuspending = false
                        niloreSuspendTicks = 0
                        niloreInstantAttacking = true
                        mc.timer.timerSpeed = 4f
                    } else {
                        niloreDoAttackSequence()
                        niloreFlushMotion = true
                        niloreSuspending = false
                        niloreSuspendTicks = 0
                    }
                } else {
                    niloreRelease()
                    if (niloreInstantAttack) niloreInstantProgress = 0f
                    if (onGround && player.isSprinting) player.setSprinting(false)
                }
            }
            return
        }

        if (niloreInstantAttacking) {
            niloreInstantProgress -= 1f
            if (niloreInstantProgress <= 0f) {
                niloreInstantProgress = 0f
                niloreInstantAttacking = false
                mc.timer.timerSpeed = 1f
                if (niloreDebug) chat("§8[§cAntiKB§8] §7instant done")
            }
        }

        if (niloreAttacksRemaining > 0 && niloreAttackTarget != null) niloreDoAttackSequence()

        if (niloreFlushMotion) {
            niloreFlushMotion = false
            niloreFlushQueuedPackets()
        }
    }

    val onJump = handler<JumpEvent> { event ->
        val thePlayer = mc.thePlayer

        if (thePlayer == null || thePlayer.isInLiquid || thePlayer.isInWeb)
            return@handler

        when (mode.lowercase()) {
            "aacpush" -> {
                jump = true

                if (!thePlayer.isCollidedVertically)
                    event.cancelEvent()
            }

            "aaczero" ->
                if (thePlayer.hurtTime > 0)
                    event.cancelEvent()
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mode == "Jump" && hasReceivedVelocity) {
            if (!player.isJumping && nextInt(endExclusive = 100) < chance && shouldJump() && player.isSprinting && player.onGround && player.hurtTime == 9) {
                player.tryJump()
                limitUntilJump = 0
            }
            hasReceivedVelocity = false
            return@handler
        }

        when (jumpCooldownMode.lowercase()) {
            "ticks" -> limitUntilJump++
            "receivedhits" -> if (player.hurtTime == 9) limitUntilJump++
        }
    }

    val onBlockBB = handler<BlockBBEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (mode == "GhostBlock") {
            if (hasReceivedVelocity) {
                if (player.hurtTime in minHurtTime.get()..maxHurtTime.get()) {
                    // Check if there is air exactly 1 level above the player's Y position
                    if (event.block is BlockAir && event.y == mc.thePlayer.posY.toInt() + 1) {
                        event.boundingBox = AxisAlignedBB(
                            event.x.toDouble(),
                            event.y.toDouble(),
                            event.z.toDouble(),
                            event.x + 1.0,
                            event.y + 1.0,
                            event.z + 1.0
                        )
                    }
                } else if (player.hurtTime == 0) {
                    hasReceivedVelocity = false
                }
            }
        }
    }

    private fun shouldJump() = when (jumpCooldownMode.lowercase()) {
        "ticks" -> limitUntilJump >= ticksUntilJump
        "receivedhits" -> limitUntilJump >= hitsUntilJump
        else -> false
    }

    private fun handleVelocity(event: PacketEvent) {
        val packet = event.packet

        if (packet is S12PacketEntityVelocity) {
            // Always cancel event and handle motion from here
            event.cancelEvent()

            if (horizontal == 0f && vertical == 0f)
                return

            // Don't modify player's motionXZ when horizontal value is 0
            if (horizontal != 0f) {
                var motionX = packet.realMotionX
                var motionZ = packet.realMotionZ

                if (limitMaxMotionValue.get()) {
                    val distXZ = sqrt(motionX * motionX + motionZ * motionZ)

                    if (distXZ > maxXZMotion) {
                        val ratioXZ = maxXZMotion / distXZ

                        motionX *= ratioXZ
                        motionZ *= ratioXZ
                    }
                }

                mc.thePlayer.motionX = motionX * horizontal
                mc.thePlayer.motionZ = motionZ * horizontal
            }

            // Don't modify player's motionY when vertical value is 0
            if (vertical != 0f) {
                var motionY = packet.realMotionY

                if (limitMaxMotionValue.get())
                    motionY = motionY.coerceAtMost(maxYMotion + 0.00075)

                mc.thePlayer.motionY = motionY * vertical
            }
        } else if (packet is S27PacketExplosion) {
            // Don't cancel explosions, modify them, they could change blocks in the world
            if (horizontal != 0f && vertical != 0f) {
                packet.field_149152_f = 0f
                packet.field_149153_g = 0f
                packet.field_149159_h = 0f

                return
            }

            // Unlike with S12PacketEntityVelocity explosion packet motions get added to player motion, doesn't replace it
            // Velocity might behave a bit differently, especially LimitMaxMotion
            packet.field_149152_f *= horizontal // motionX
            packet.field_149153_g *= vertical // motionY
            packet.field_149159_h *= horizontal // motionZ

            if (limitMaxMotionValue.get()) {
                val distXZ =
                    sqrt(packet.field_149152_f * packet.field_149152_f + packet.field_149159_h * packet.field_149159_h)
                val distY = packet.field_149153_g
                val maxYMotion = maxYMotion + 0.00075f

                if (distXZ > maxXZMotion) {
                    val ratioXZ = maxXZMotion / distXZ

                    packet.field_149152_f *= ratioXZ
                    packet.field_149159_h *= ratioXZ
                }

                if (distY > maxYMotion) {
                    packet.field_149153_g *= maxYMotion / distY
                }
            }
        }
    }

    private fun getNearestEntityInRange(range: Float = this.range): Entity? {
        val player = mc.thePlayer ?: return null

        return mc.theWorld.loadedEntityList.filter {
            isSelected(it, true) && player.getDistanceToEntityBox(it) <= range
        }.minByOrNull { player.getDistanceToEntityBox(it) }
    }
}
