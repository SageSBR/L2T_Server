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

import l2server.gameserver.datatables.DoorTable;
import l2server.gameserver.handler.IAdminCommandHandler;
import l2server.gameserver.instancemanager.CastleManager;
import l2server.gameserver.model.WorldObject;
import l2server.gameserver.model.actor.instance.DoorInstance;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.model.entity.Castle;

/**
 * This class handles following admin commands:
 * - open1 = open coloseum door 24190001
 * - open2 = open coloseum door 24190002
 * - open3 = open coloseum door 24190003
 * - open4 = open coloseum door 24190004
 * - openall = open all coloseum door
 * - close1 = close coloseum door 24190001
 * - close2 = close coloseum door 24190002
 * - close3 = close coloseum door 24190003
 * - close4 = close coloseum door 24190004
 * - closeall = close all coloseum door
 * <p>
 * - open = open selected door
 * - close = close selected door
 *
 * @version $Revision: 1.2.4.5 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminDoorControl implements IAdminCommandHandler {
	private static DoorTable doorTable = DoorTable.getInstance();
	private static final String[] ADMIN_COMMANDS = {"admin_open", "admin_close", "admin_openall", "admin_closeall"};

	@Override
	public boolean useAdminCommand(String command, Player activeChar) {
		try {
			if (command.startsWith("admin_open ")) {
				int doorId = Integer.parseInt(command.substring(11));
				if (doorTable.getDoor(doorId) != null) {
					doorTable.getDoor(doorId).openMe();
				} else {
					for (Castle castle : CastleManager.getInstance().getCastles()) {
						if (castle.getDoor(doorId) != null) {
							castle.getDoor(doorId).openMe();
						}
					}
				}
			} else if (command.startsWith("admin_close ")) {
				int doorId = Integer.parseInt(command.substring(12));
				if (doorTable.getDoor(doorId) != null) {
					doorTable.getDoor(doorId).closeMe();
				} else {
					for (Castle castle : CastleManager.getInstance().getCastles()) {
						if (castle.getDoor(doorId) != null) {
							castle.getDoor(doorId).closeMe();
						}
					}
				}
			}
			if (command.equals("admin_closeall")) {
				for (DoorInstance door : doorTable.getDoors()) {
					door.closeMe();
				}
				for (Castle castle : CastleManager.getInstance().getCastles()) {
					for (DoorInstance door : castle.getDoors()) {
						door.closeMe();
					}
				}
			}
			if (command.equals("admin_openall")) {
				for (DoorInstance door : doorTable.getDoors()) {
					door.openMe();
				}
				for (Castle castle : CastleManager.getInstance().getCastles()) {
					for (DoorInstance door : castle.getDoors()) {
						door.openMe();
					}
				}
			}
			if (command.equals("admin_open")) {
				WorldObject target = activeChar.getTarget();
				if (target instanceof DoorInstance) {
					((DoorInstance) target).openMe();
				} else {
					activeChar.sendMessage("Incorrect target.");
				}
			}

			if (command.equals("admin_close")) {
				WorldObject target = activeChar.getTarget();
				if (target instanceof DoorInstance) {
					((DoorInstance) target).closeMe();
				} else {
					activeChar.sendMessage("Incorrect target.");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public String[] getAdminCommandList() {
		return ADMIN_COMMANDS;
	}
}
