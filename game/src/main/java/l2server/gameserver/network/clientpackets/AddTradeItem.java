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

package l2server.gameserver.network.clientpackets;

import l2server.gameserver.model.TradeList;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.network.serverpackets.TradeOtherAdd;
import l2server.gameserver.network.serverpackets.TradeOwnAdd;

/**
 * This class ...
 *
 * @version $Revision: 1.5.2.2.2.5 $ $Date: 2005/03/27 15:29:29 $
 */
public final class AddTradeItem extends L2GameClientPacket {

	private int tradeId;
	private int objectId;
	private long count;

	public AddTradeItem() {
	}

	@Override
	protected void readImpl() {
		tradeId = readD();
		objectId = readD();
		count = readQ();
	}

	@Override
	protected void runImpl() {
		final Player player = getClient().getActiveChar();
		if (player == null) {
			return;
		}

		final TradeList trade = player.getActiveTradeList();
		if (trade == null) {
			log.warn("Character: " + player.getName() + " requested item:" + objectId + " add without active tradelist:" + tradeId);
			return;
		}

		final Player partner = trade.getPartner();
		if (partner == null || World.getInstance().getPlayer(partner.getObjectId()) == null || partner.getActiveTradeList() == null) {
			// Trade partner not found, cancel trade
			if (partner != null) {
				log.warn("Character:" + player.getName() + " requested invalid trade object: " + objectId);
			}
			SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
			player.sendPacket(msg);
			player.cancelActiveTrade();
			return;
		}

		if (trade.isConfirmed() || partner.getActiveTradeList().isConfirmed()) {
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_ADJUST_ITEMS_AFTER_TRADE_CONFIRMED));
			return;
		}

		if (player.getEvent() != null) {
			player.sendMessage("You cannot trade while being involved in an event!");
			player.cancelActiveTrade();
			return;
		}

		if (player.getOlympiadGameId() > -1) {
			player.sendMessage("You cannot trade while being involved in the Grand Olympiad!");
			player.cancelActiveTrade();
			return;
		}

		if (!player.getAccessLevel().allowTransaction()) {
			player.sendMessage("Transactions are disable for your Access Level");
			player.cancelActiveTrade();
			return;
		}

		if (!player.validateItemManipulation(objectId, "trade")) {
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOTHING_HAPPENED));
			return;
		}

		final TradeList.TradeItem item = trade.addItem(objectId, count);
		if (item != null) {
			player.sendPacket(new TradeOwnAdd(item));
			trade.getPartner().sendPacket(new TradeOtherAdd(item));
		}
	}
}
