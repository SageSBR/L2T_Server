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

import l2server.DatabasePool;
import l2server.gameserver.handler.IAdminCommandHandler;
import l2server.gameserver.model.actor.instance.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * This class handles following admin commands: - delete = deletes target
 *
 * @version $Revision: 1.1.2.6.2.3 $ $Date: 2005/04/11 10:05:59 $
 */
public class AdminRepairChar implements IAdminCommandHandler {

	private static final String[] ADMIN_COMMANDS = {"admin_restore", "admin_repair"};

	@Override
	public boolean useAdminCommand(String command, Player activeChar) {
		handleRepair(command);
		return true;
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}

	private void handleRepair(String command) {
		String[] parts = command.split(" ");
		if (parts.length != 2) {
			return;
		}

		String cmd = "UPDATE characters SET x=-114462, y=253179, z=-1544 WHERE char_name=?";
		java.sql.Connection connection = null;
		try {
			connection = DatabasePool.getInstance().getConnection();
			PreparedStatement statement = connection.prepareStatement(cmd);
			statement.setString(1, parts[1]);
			statement.execute();
			statement.close();

			statement = connection.prepareStatement("SELECT charId FROM characters WHERE char_name=?");
			statement.setString(1, parts[1]);
			ResultSet rset = statement.executeQuery();
			int objId = 0;
			if (rset.next()) {
				objId = rset.getInt(1);
			}

			rset.close();
			statement.close();

			if (objId == 0) {
				connection.close();
				return;
			}

			//connection = DatabasePool.getInstance().getConnection();
			statement = connection.prepareStatement("DELETE FROM character_shortcuts WHERE charId=?");
			statement.setInt(1, objId);
			statement.execute();
			statement.close();

			//connection = DatabasePool.getInstance().getConnection();
			statement = connection.prepareStatement("UPDATE items SET loc=\"INVENTORY\" WHERE owner_id=?");
			statement.setInt(1, objId);
			statement.execute();
			statement.close();
			connection.close();
		} catch (Exception e) {
			log.warn("could not repair char:", e);
		} finally {
			try {
				connection.close();
			} catch (Exception e) {
			}
		}
	}
}
