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

package l2server.gameserver.network.serverpackets;

import l2server.gameserver.model.actor.instance.Player;

/**
 * Format: ch Sddddddddd
 *
 * @author KenM
 */
public class ExDuelUpdateUserInfo extends L2GameServerPacket {
	private Player activeChar;
	
	public ExDuelUpdateUserInfo(Player cha) {
		activeChar = cha;
	}
	
	@Override
	protected final void writeImpl() {
		writeS(activeChar.getName());
		writeD(activeChar.getObjectId());
		writeD(activeChar.getCurrentClass().getId());
		writeD(activeChar.getLevel());
		writeD((int) activeChar.getCurrentHp());
		writeD(activeChar.getMaxVisibleHp());
		writeD((int) activeChar.getCurrentMp());
		writeD(activeChar.getMaxMp());
		writeD((int) activeChar.getCurrentCp());
		writeD(activeChar.getMaxCp());
	}
}
