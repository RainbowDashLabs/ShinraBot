/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) 2022 RainbowDashLabs and Contributor
 */

package de.chojo.shinra.commands;

import de.chojo.jdautil.command.CommandMeta;
import de.chojo.jdautil.command.SimpleArgument;
import de.chojo.jdautil.command.SimpleCommand;
import de.chojo.jdautil.conversation.builder.ConversationBuilder;
import de.chojo.jdautil.conversation.elements.Result;
import de.chojo.jdautil.conversation.elements.Step;
import de.chojo.jdautil.pagination.PageService;
import de.chojo.jdautil.wrapper.SlashCommandContext;
import de.chojo.shinra.configuration.Configuration;
import de.chojo.shinra.configuration.elements.messages.TimedRoleMessage;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class RoleMessage extends SimpleCommand {
    private final PageService pageService;
    private final Configuration configuration;

    public RoleMessage(PageService pageService, Configuration configuration) {
        super(CommandMeta.builder("rolemessage", "Add role messages")
                        .addSubCommand("add", "Add a role message", argsBuilder()
                                .add(SimpleArgument.role("role", "the role").asRequired())
                                .add(SimpleArgument.integer("minutes", "minutes"))
                                .add(SimpleArgument.integer("hours", "hours"))
                                .add(SimpleArgument.integer("days", "days")))
                .addSubCommand("remove", "Remove a role message", argsBuilder()
                        .add(SimpleArgument.integer("id", "id").asRequired()))
                .addSubCommand("edit", "Edit a role message", argsBuilder()
                        .add(SimpleArgument.integer("id", "id").asRequired()))
                .addSubCommand("list", "List a role message")
                .addSubCommand("show", "Show a role message", argsBuilder()
                        .add(SimpleArgument.integer("id", "id")))
                .withPermission());
        this.pageService = pageService;
        this.configuration = configuration;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, SlashCommandContext context) {
        var label = event.getSubcommandName();
        if ("add".equals(label)) {
            context.startDialog(
                    ConversationBuilder.builder(Step.message("Please enter the text", conversationContext -> {
                        var duration = Duration.ofDays(event.getOption("days", 0, OptionMapping::getAsInt));
                        duration = duration.plus(event.getOption("hours", 0, OptionMapping::getAsInt), ChronoUnit.HOURS);
                        duration = duration.plus(event.getOption("minutes", 0, OptionMapping::getAsInt), ChronoUnit.MINUTES);
                        var message = new TimedRoleMessage(duration, conversationContext.getContentRaw(), event.getOption("role").getAsRole().getIdLong());
                        configuration.autoMessages().registerRoleMessage(message);
                        conversationContext.getContentRaw();
                        configuration.saveConfig();
                        return Result.finish();
                    }).build()).build());
            return;
        }

        if ("remove".equals(label)) {
            var message = configuration.autoMessages().roleMessageById(event.getOption("id", 0, OptionMapping::getAsInt));
            if (message.isEmpty()) {
                event.reply("Unkown message id").setEphemeral(true).queue();
                return;
            }
            configuration.autoMessages().roleMessages().removeIf(mes -> mes.id() == message.get().id());
            event.reply("Removed.").queue();
            configuration.saveConfig();
            return;
        }

        if ("edit".equals(label)) {
            var message = configuration.autoMessages().roleMessageById(event.getOption("id", 0, OptionMapping::getAsInt));
            if (message.isEmpty()) {
                event.reply("Unkown message id").setEphemeral(true).queue();
                return;
            }

            context.startDialog(ConversationBuilder.builder(Step.message("Please enter the text", conversationContext -> {
                        message.get().message(conversationContext.getContentRaw());
                        configuration.saveConfig();
                        return Result.finish();
                    }).build()).build());
            return;
        }

        if ("list".equals(label)) {
            pageService.registerPage(event, new RoleMessagePage(configuration.autoMessages().roleMessages()));
            return;
        }

        if ("show".equals(label)) {
            var page = new RoleMessagePage(configuration.autoMessages().roleMessages());
            if (event.getOption("id") != null) {
                var message = configuration.autoMessages().roleMessageById(event.getOption("id").getAsInt());
                message.ifPresent(m -> page.current(configuration.autoMessages().roleMessages().indexOf(m)));
            }
            pageService.registerPage(event, page);
        }
    }
}
