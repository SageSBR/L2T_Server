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

import l2server.gameserver.model.Item;
import l2server.gameserver.model.L2TradeList;
import l2server.gameserver.model.L2TradeList.L2TradeItem;
import l2server.gameserver.model.actor.instance.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ShanSoft
 */
public class ExSellList extends L2ItemListPacket {

	private List<L2TradeItem> buyList = new ArrayList<>();
	private Item[] sellList = null;
	private Item[] refundList = null;
	private boolean done;

	public ExSellList(Player player, L2TradeList list, double taxRate, boolean done) {
		for (L2TradeItem item : list.getItems()) {
			if (item.hasLimitedStock() && item.getCurrentCount() <= 0) {
				continue;
			}
			buyList.add(item);
		}
		sellList = player.getInventory().getAvailableItems(false, true);
		if (player.hasRefund()) {
			refundList = player.getRefund().getItems();
		}
		this.done = done;
	}

	@Override
	protected final void writeImpl() {
		writeD(0x00); // GoD ???

		if (sellList != null && sellList.length > 0) {
			writeH(sellList.length);
			for (Item item : sellList) {
				writeItem(item);

				writeQ(item.getItem().getSalePrice());
			}
		} else {
			writeH(0x00);
		}

		if (refundList != null && refundList.length > 0) {
			writeH(refundList.length);
			int itemIndex = 0;
			for (Item item : refundList) {
				writeItem(item);

				writeD(itemIndex++); // Index
				writeQ(item.getItem().getSalePrice() * item.getCount());
			}
		} else {
			writeH(0x00);
		}

		writeC(done ? 0x01 : 0x00);

		buyList.clear();
	}
}
