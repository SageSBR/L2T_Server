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

import l2server.Config;
import l2server.gameserver.cache.CrestCache;

/**
 * sample
 * 0000: 84 6d 06 00 00 36 05 00 00 42 4d 36 05 00 00 00	.m...6...BM6....
 * 0010: 00 00 00 36 04 00 00 28 00 00 00 10 00 00 00 10	...6...(........
 * 0020: 00 00 00 01 00 08 00 00 00 00 00 00 01 00 00 c4	................
 * 0030: ...
 * 0530: 10 91 00 00 00 60 9b d1 01 e4 6e ee 52 97 dd	   .....`....n.R..
 * <p>
 * <p>
 * <p>
 * format   dd x...x
 *
 * @version $Revision: 1.3.2.1.2.4 $ $Date: 2005/03/27 15:29:57 $
 */
public final class PledgeCrest extends L2GameServerPacket {
	private final int crestId;
	private final byte[] data;
	
	public PledgeCrest(int crestId) {
		this.crestId = crestId;
		data = CrestCache.getInstance().getPledgeCrest(crestId);
	}
	
	public PledgeCrest(int crestId, byte[] data) {
		this.crestId = crestId;
		this.data = data;
	}
	
	@Override
	protected final void writeImpl() {
		writeD(Config.SERVER_ID); // server id?
		writeD(crestId);
		if (data != null) {
			writeD(data.length);
			writeB(data);
		} else {
			writeD(0);
		}
	}
}
