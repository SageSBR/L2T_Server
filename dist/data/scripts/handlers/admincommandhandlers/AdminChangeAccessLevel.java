/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package handlers.admincommandhandlers;

import l2server.Config;
import l2server.DatabasePool;
import l2server.gameserver.handler.IAdminCommandHandler;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.SystemMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * This class handles following admin commands:
 * - changelvl = change a character's access level
 * Can be used for character ban (as opposed to regular //ban that affects accounts)
 * or to grant mod/GM privileges ingame
 *
 * @version $Revision: 1.1.2.2.2.3 $ $Date: 2005/04/11 10:06:00 $
 * con.close() change by Zoey76 24/02/2011
 */
public class AdminChangeAccessLevel implements IAdminCommandHandler {
	private static final String[] ADMIN_COMMANDS = {"admin_changelvl"};

	@Override
	public boolean useAdminCommand(String command, Player activeChar) {
		handleChangeLevel(command, activeChar);
		return true;
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}

	/**
	 * If no character name is specified, tries to change GM's target access
	 * level. Else if a character name is provided, will try to reach it either
	 * from World or from a database connection.
	 *
	 */
	private void handleChangeLevel(String command, Player activeChar) {
		String[] parts = command.split(" ");
		if (parts.length == 2) {
			try {
				int lvl = Integer.parseInt(parts[1]);
				if (activeChar.getTarget() instanceof Player) {
					onLineChange(activeChar, (Player) activeChar.getTarget(), lvl);
				} else {
					activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
				}
			} catch (Exception e) {
				activeChar.sendMessage("Usage: //changelvl <target_new_level> | <player_name> <new_level>");
			}
		} else if (parts.length == 3) {
			String name = parts[1];
			int lvl = Integer.parseInt(parts[2]);
			Player player = World.getInstance().getPlayer(name);
			if (player != null) {
				onLineChange(activeChar, player, lvl);
			} else {
				Connection con = null;
				try {
					con = DatabasePool.getInstance().getConnection();
					PreparedStatement statement = con.prepareStatement("UPDATE characters SET accesslevel=? WHERE char_name=?");
					statement.setInt(1, lvl);
					statement.setString(2, name);
					statement.execute();
					int count = statement.getUpdateCount();
					statement.close();
					if (count == 0) {
						activeChar.sendMessage("Character not found or access level unaltered.");
					} else {
						activeChar.sendMessage("Character's access level is now set to " + lvl);
					}
				} catch (SQLException se) {
					activeChar.sendMessage("SQLException while changing character's access level");
					if (Config.DEBUG) {
						se.printStackTrace();
					}
				} finally {
					DatabasePool.close(con);
				}
			}
		}
	}

	private void onLineChange(Player activeChar, Player player, int lvl) {
		player.setAccessLevel(lvl);
		if (lvl >= 0) {
			player.sendMessage("Your access level has been changed to " + lvl);
		} else {
			player.sendMessage("Your character has been banned. Bye.");
			player.logout();
		}
		activeChar.sendMessage("Character's access level is now set to " + lvl + ". Effects won't be noticeable until next session.");
	}
}
