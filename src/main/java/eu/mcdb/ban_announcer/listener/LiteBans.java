/*
 * Copyright (C) 2019  OopsieWoopsie
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package eu.mcdb.ban_announcer.listener;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import eu.mcdb.ban_announcer.BAPunishment;
import eu.mcdb.ban_announcer.BAPunishment.Type;
import eu.mcdb.ban_announcer.BAUnpunishment;
import eu.mcdb.ban_announcer.BanAnnouncer;
import eu.mcdb.ban_announcer.config.Config;
import litebans.api.Database;
import litebans.api.Entry;
import litebans.api.Events;
import litebans.api.Events.Listener;

public final class LiteBans {

    private Events events;
    private Database database;
    private BanAnnouncer ba;

    public LiteBans(BanAnnouncer ba) {
        this.ba = ba;
        this.events = Events.get();
        this.database = Database.get();

        events.register(new LiteBansListener());
    }

    public String getName(String uuid) {
        String sql = "SELECT name FROM {history} WHERE uuid = ? LIMIT 1";

        try(PreparedStatement stmt = database.prepareStatement(sql)) {
            stmt.setString(1, uuid);

            ResultSet rs = stmt.executeQuery();

            if (rs.first())
                return rs.getString(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class LiteBansListener extends Listener {

        @Override
        public void entryAdded(Entry entry) {
            if (!entry.isActive() && !entry.getType().equals("kick"))
                return;

            if (entry.isSilent() && Config.getInstance().isIgnoreSilent())
                return;

            BAPunishment punishment = new BAPunishment();

            switch (entry.getType()) {
            case "ban":
                if (entry.isIpban()) {
                    punishment.setType(entry.isPermanent() ? Type.BANIP : Type.TEMPBANIP);
                } else {
                    punishment.setType(entry.isPermanent() ? Type.BAN : Type.TEMPBAN);
                }
                break;
            case "mute":
                punishment.setType(entry.isPermanent() ? Type.MUTE : Type.TEMPMUTE);
                break;
            case "warn":
                punishment.setType(entry.isPermanent() ? Type.WARN : Type.TEMPWARN);
                break;
            case "kick":
                punishment.setType(Type.KICK);
                break;
            default:
                ba.getLogger().severe("Unknown event '" + entry.getType() + "'.");
                return;
            }

            String name = LiteBans.this.getName(entry.getUuid());

            if (name == null) {
                ba.getLogger().severe("Couldn't fetch player name from UUID '" + entry.getUuid() + "'. The message was not sent.");
                return;
            }

            punishment.setPlayer(name);
            punishment.setOperator(entry.getExecutorName() == null ? "Console" : entry.getExecutorName());
            punishment.setPermanent(entry.isPermanent());
            punishment.setReason(entry.getReason());
            punishment.setDuration(entry.getDurationString());

            ba.handlePunishment(punishment);
        }

        @Override
        public void entryRemoved(Entry entry) {

            BAUnpunishment punishment = new BAUnpunishment();

            switch (entry.getType()) {
                case "ban":
                    punishment.setType(BAUnpunishment.Type.UNBAN);
                    break;
                case "mute":
                    punishment.setType(BAUnpunishment.Type.UNMUTE);
                    break;
                case "warn":
                    punishment.setType(BAUnpunishment.Type.UNWARN);
                    break;
                default:
                    ba.getLogger().severe("Unknown event '" + entry.getType() + "'.");
                    return;
            }

            String name = LiteBans.this.getName(entry.getUuid());

            if (name == null) {
                ba.getLogger().severe("Couldn't fetch player name from UUID '" + entry.getUuid() + "'. The message was not sent.");
                return;
            }

            punishment.setPlayer(name);
            punishment.setOperator(entry.getExecutorName() == null ? "Console" : entry.getExecutorName());
            punishment.setReason(entry.getReason());

            ba.handleUnpunishment(punishment);
        }

    }
}
