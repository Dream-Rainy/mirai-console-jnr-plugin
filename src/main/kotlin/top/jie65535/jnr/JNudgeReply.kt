package top.jie65535.jnr

import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.EventPriority
import net.mamoe.mirai.event.events.NudgeEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.isUploaded
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.info
import java.time.LocalDateTime
import kotlin.random.Random

object JNudgeReply : KotlinPlugin(
    JvmPluginDescription(
        id = "me.jie65535.mirai-console-jnr-plugin",
        name = "J Nudge Reply",
        version = "1.3.0",
    ) {
        author("jie65535")
        info("""自定义戳一戳回复插件""")
    }
) {
    private val groupCoolDownTime = mutableMapOf<Long, LocalDateTime>()
    private var jnrCount = 1
    private var coolDownTime = (5..12).random()
    private var isReply = true //是否回复

    override fun onEnable() {
        JNRPluginConfig.reload()
        JNRCommand.register()

        globalEventChannel().subscribeAlways<NudgeEvent>(priority = JNRPluginConfig.priority) {
            if (target.id == bot.id && target.id != from.id && JNRPluginConfig.replyMessageList.isNotEmpty()) {
                val replyList: List<ReplyMessage> = JNRPluginConfig.replyMessageList //获取回复消息
                val now = LocalDateTime.now() //判断间隔
                if (subject is Group) {
                    val randomNumber = (0..10).random()
                    if (groupCoolDownTime[subject.id] == null)
                        groupCoolDownTime[subject.id] = now
                    if (!isReply && (groupCoolDownTime[subject.id]?.plusMinutes(coolDownTime.toLong())!! > now)){
                        logger.info("cd中，跳过")
                    }else if ((randomNumber >= 6 && jnrCount >= 5) || (jnrCount >= 12)){
                            groupCoolDownTime[subject.id] = now
                            isReply = false
                            jnrCount = 1
                            val s = "呜呜，被戳傻了。休息"+coolDownTime.toString()+"分钟"
                            sendRecordMessage(this.subject,s.deserializeMiraiCode())
                    } else {
                        jnrCount += 1
                        coolDownTime = (5..12).random()
                        isReply = true
                    }
                    /*if ((from as Member).permission.level >= (subject as Group).botPermission.level) {
                        replyList = replyList.filter { !it.message.startsWith("#group.mute:") }
                    }*/

                // 判断间隔
                val isIgnored = if (isReply) {
                    val totalWeight = replyList.sumOf { it.weight }
                    var w = Random.nextInt(totalWeight)
                    for (msg in replyList) {
                        if (w < msg.weight) {
                            doReply(msg, this)
                            break
                        } else {
                            w -= msg.weight
                        }
                    }
                    false
                } else {
                    logger.info("正在CD中，本次已忽略")
                    true
                }

                // 拦截事件
                if (JNRPluginConfig.priority != EventPriority.MONITOR && JNRPluginConfig.isIntercept
                ) {
                    // 在被忽略的情况下判断是否拦截
                    if (!isIgnored || JNRPluginConfig.interceptAtInterval)
                        intercept()
                }
            }
        }

        logger.info { "Plugin loaded. https://github.com/jie65535/mirai-console-jnr-plugin" }
    }
    }

    private suspend fun doReply(message: ReplyMessage, event: NudgeEvent) {
        if (message.message.startsWith("#")) {
            when {
                // 戳回去
                RegexMatches.main(message.message) -> {
                    event.from.nudge().sendTo(event.subject)
                    val messageTemp = message.message.substring(6)
                    sendRecordMessage(event.subject, messageTemp.deserializeMiraiCode())
                    logger.info("已尝试戳回发送者")
                }

                // 禁言
               /* message.message.startsWith("#group.mute:") -> {
                    val duration = message.message.substringAfter(':').toIntOrNull()
                    if (duration == null) {
                        logger.warning("戳一戳禁言失败：\"${message.message}\" 格式不正确")
                    } else {
                        val member: Member = event.from as Member
                        try {
                            member.mute(duration)
                            logger.info("戳一戳禁言目标 ${member.nameCardOrNick}(${member.id}) $duration 秒")
                        } catch (e: Throwable) {
                            logger.warning("戳一戳禁言失败", e)
                        }
                    }
                }*/

                // 忽略
                /* message.message == "#ignore" -> {
                    logger.info("已忽略本次戳一戳回复")
                }
                */
                // 其它
                else -> sendRecordMessage(event.subject, message.message.deserializeMiraiCode())
            }
        } else {
            sendRecordMessage(event.subject, message.message.deserializeMiraiCode())
        }
    }

    private suspend fun sendRecordMessage(subject: Contact, message: MessageChain) {
        for (it in message) {
            if (it is Image) {
                if (!it.isUploaded(subject.bot)) {
                    val imgFile = resolveDataFile("images/" + it.imageId)
                    if (imgFile.exists()) {
                        imgFile.uploadAsImage(subject)
                    } else {
                        logger.warning(
                            "图片的服务器缓存已失效，本地缓存已丢失，请重新设置该消息内的图片！" +
                                    "消息内容：" + message.serializeToMiraiCode()
                        )
                    }
                }
            } else if (it is Audio) {
                // TODO
            }
        }
        subject.sendMessage(message)
    }

    object RegexMatches {
        @JvmStatic
        fun main(args: String): Boolean {
            val regex = "#nudge".toRegex()
            return regex.containsMatchIn(input = args)
        }
    }
}
