package com.jagrosh.jdautilities.menu

import com.jagrosh.jdautilities.waiter.EventWaiter
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.core.requests.RestAction
import xyz.gnarbot.gnar.utils.embed
import xyz.gnarbot.gnar.utils.ln
import java.awt.Color
import java.util.concurrent.TimeUnit

class Paginator(val waiter: EventWaiter,
                val user: User?,
                val title: String,
                val description: String?,
                val color: Color?,
                val list: List<List<String>>,
                val timeout: Long,
                val unit: TimeUnit,
                val finally: (Message?) -> Unit) {
    val LEFT = "\u25C0"
    val STOP = "\u23F9"
    val RIGHT = "\u25B6"

    fun display(channel: TextChannel) {
        if (!channel.guild.selfMember.hasPermission(channel, Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS)) {
            channel.sendMessage(embed("Error") {
                description {
                    buildString {
                        append("The bot requires the permission `${Permission.MESSAGE_ADD_REACTION.getName()}`, ")
                        append("`${Permission.MESSAGE_MANAGE.getName()}` and ")
                        append("`${Permission.MESSAGE_EMBED_LINKS.getName()}` for pagination menus.")
                    }
                }
            }.build()).queue()
            finally(null)
            return
        }

        paginate(channel, 1)
    }

    fun display(message: Message) {
        if (!message.textChannel.guild.selfMember.hasPermission(message.textChannel, Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_MANAGE, Permission.MESSAGE_EMBED_LINKS)) {
            message.channel.sendMessage(embed("Error") {
                description {
                    buildString {
                        append("The bot requires the permission `${Permission.MESSAGE_ADD_REACTION.getName()}`, ")
                        append("`${Permission.MESSAGE_MANAGE.getName()}` and ")
                        append("`${Permission.MESSAGE_EMBED_LINKS.getName()}` for pagination menus.")
                    }
                }
            }.build()).queue()
            finally(null)
            return
        }

        paginate(message, 1)
    }

    fun paginate(channel: TextChannel, page: Int) {
        val pageNum = page.coerceIn(1, list.size)
        val msg = renderPage(page)
        initialize(channel.sendMessage(msg), pageNum)
    }

    fun paginate(message: Message, page: Int) {
        val pageNum = page.coerceIn(1, list.size)
        val msg = renderPage(page)
        initialize(message.editMessage(msg), pageNum)
    }

    private fun initialize(action: RestAction<Message>, page: Int) {
        action.queue { message ->
            if (list.size > 1) {
                message.addReaction(LEFT).queue()
                message.addReaction(STOP).queue()
                message.addReaction(RIGHT).queue {
                    waiter.waitFor(MessageReactionAddEvent::class.java) {
                        val pageNew = when (it.reactionEmote.name) {
                            LEFT -> page - 1
                            RIGHT -> page + 1
                            STOP -> {
                                finally(message)
                                return@waitFor
                            }
                            else -> {
                                finally(message)
                                error("Internal pagination error")
                            }
                        }

                        it.reaction.removeReaction(it.user).queue()

                        if (pageNew != page) {
                            message?.editMessage(renderPage(pageNew))?.queue {
                                paginate(it, pageNew)
                            }
                        }
                    }.predicate {
                        when {
                            it.messageIdLong != message?.idLong -> false
                            it.user.isBot -> false
                            user != null && it.user != user -> {
                                it.reaction.removeReaction(it.user).queue()
                                false
                            }
                            else -> when (it.reactionEmote.name) {
                                LEFT, STOP, RIGHT -> true
                                else -> false
                            }
                        }
                    }.timeout(timeout, unit) {
                        finally(message)
                    }
                }
            }
        }
    }

    private fun renderPage(page: Int): Message {
        val pageNum = page.coerceIn(1, list.size)

        return MessageBuilder().setEmbed(embed(title) {
            setColor(color)
            val items = list[pageNum - 1]
            description {
                buildString {
                    description?.let { append(it).ln() }
                    items.forEachIndexed { index, s ->
                        append("**").append(index + (pageNum - 1) * list[0].size).append("** ")
                        append(s).ln()
                    }
                }
            }
            setFooter("Page $pageNum/${list.size}", null)
        }.build()).build()
    }
}