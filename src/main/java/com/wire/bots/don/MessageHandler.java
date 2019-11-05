package com.wire.bots.don;

import com.wire.bots.don.commands.Command;
import com.wire.bots.don.commands.DefaultCommand;
import com.wire.bots.don.db.Database;
import com.wire.bots.don.db.User;
import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.models.EditedTextMessage;
import com.wire.bots.sdk.models.TextMessage;
import com.wire.bots.sdk.server.model.Member;
import com.wire.bots.sdk.server.model.NewBot;
import com.wire.bots.sdk.server.model.SystemMessage;
import com.wire.bots.sdk.tools.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHandler extends MessageHandlerBase {
    private final Database db;
    private final ConcurrentHashMap<UUID, Command> commands = new ConcurrentHashMap<>(); // <botId, command>

    MessageHandler(DonConfig config) {
        db = new Database(config.getPostgres());
    }

    @Override
    public boolean onNewBot(NewBot newBot) {
        try {
            Logger.info(String.format("onNewBot: botId: %s, user: %s/%s, locale: %s",
                    newBot.id,
                    newBot.origin.id,
                    newBot.origin.name,
                    newBot.locale));

            for (Member member : newBot.conversation.members) {
                if (member.service != null) {
                    Logger.warning("Rejecting NewBot. Provider: %s service: %s",
                            member.service.providerId,
                            member.service.id);
                    return false; // we don't want to be in a conv if other bots are there.
                }
            }

            User user = db.getUser(newBot.origin.id);
            if (user == null)
                db.insertUser(newBot.origin.id, newBot.origin.name);
            return true;
        } catch (Exception e) {
            Logger.error("onNewBot: %s", e);
            return false;
        }
    }

    @Override
    public void onMemberJoin(WireClient client, SystemMessage message) {
        for (UUID userId : message.users) {
            try {
                User user = db.getUser(userId);
                if (user == null) {
                    db.insertUser(userId, client.getUser(userId).name);
                }
            } catch (Exception e) {
                Logger.error("onMemberJoin: %s", e);
            }
        }
    }

    @Override
    public void onNewConversation(WireClient client, SystemMessage message) {
        try {
            client.sendText("Some day, and that day may never come," +
                    " I will call upon you to do a service for me. But until that day accept these bots as a " +
                    "gift from me to you.");
        } catch (Exception e) {
            Logger.error("onNewConversation: %s", e);
        }
    }

    @Override
    public void onText(WireClient client, TextMessage msg) {
        try {
            UUID bot = client.getId();
            Command command = commands.computeIfAbsent(bot, k -> new DefaultCommand(client, msg.getUserId(), db));

            commands.put(bot, command.onMessage(client, msg.getText()));
        } catch (Exception e) {
            Logger.error("onText: %s", e);
            try {
                client.sendText(e.getMessage());
            } catch (Exception e1) {
                Logger.error("sendText: %s", e1);
            }
        }
    }

    @Override
    public void onEditText(WireClient client, EditedTextMessage msg) {
        onText(client, msg);
    }

}
