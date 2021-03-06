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

import l2server.Config;
import l2server.gameserver.Shutdown;
import l2server.gameserver.datatables.CharNameTable;
import l2server.gameserver.instancemanager.AntiFeedManager;
import l2server.gameserver.model.CharSelectInfoPackage;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.L2GameClient.GameClientState;
import l2server.gameserver.network.serverpackets.CharSelected;
import l2server.gameserver.network.serverpackets.ExSubjobInfo;
import l2server.gameserver.network.serverpackets.NpcHtmlMessage;
import org.slf4j.LoggerFactory;

/**
 * This class ...
 *
 * @version $Revision: 1.5.2.1.2.5 $ $Date: 2005/03/27 15:29:30 $
 */
public class CharacterSelect extends L2GameClientPacket {
	private static org.slf4j.Logger log = LoggerFactory.getLogger(CharacterSelect.class.getName());

	// cd
	private int charSlot;

	@SuppressWarnings("unused")
	private int unk1; // new in C4
	@SuppressWarnings("unused")
	private int unk2; // new in C4
	@SuppressWarnings("unused")
	private int unk3; // new in C4
	@SuppressWarnings("unused")
	private int unk4; // new in C4

	@Override
	protected void readImpl() {
		charSlot = readD();
		unk1 = readH();
		unk2 = readD();
		unk3 = readD();
		unk4 = readD();
	}

	@Override
	protected void runImpl() {
		if (!getClient().getFloodProtectors().getCharacterSelect().tryPerformAction("CharacterSelect") || Shutdown.getInstance().isShuttingDown()) {
			return;
		}

		if (Config.SECOND_AUTH_ENABLED && !getClient().getSecondaryAuth().isAuthed()) {
			getClient().getSecondaryAuth().openDialog();
			return;
		}

		// we should always be able to acquire the lock
		// but if we can't lock then nothing should be done (ie repeated packet)
		if (getClient().getActiveCharLock().tryLock()) {
			try {
				// should always be null
				// but if not then this is repeated packet and nothing should be done here
				if (getClient().getActiveChar() == null) {
					if (Config.L2JMOD_DUALBOX_CHECK_MAX_PLAYERS_PER_IP > 0 && !AntiFeedManager.getInstance()
							.tryAddClient(AntiFeedManager.GAME_ID, getClient(), Config.L2JMOD_DUALBOX_CHECK_MAX_PLAYERS_PER_IP)) {
						final CharSelectInfoPackage info = getClient().getCharSelection(charSlot);
						if (info == null) {
							return;
						}

						final NpcHtmlMessage msg = new NpcHtmlMessage(0);
						msg.setFile(null, "mods/IPRestriction.htm");
						msg.replace("%max%",
								String.valueOf(AntiFeedManager.getInstance().getLimit(getClient(), Config.L2JMOD_DUALBOX_CHECK_MAX_PLAYERS_PER_IP)));
						getClient().sendPacket(msg);
						return;
					}

					// The Player must be created here, so that it can be attached to the L2GameClient
					if (Config.DEBUG) {
						log.debug("selected slot:" + charSlot);
					}

					//load up character from disk
					Player cha = getClient().loadCharFromDisk(charSlot);
					if (cha == null) {
						return; // handled in L2GameClient
					}

					if (cha.getAccessLevel().getLevel() < 0) {
						cha.logout();
						return;
					}

					CharNameTable.getInstance().addName(cha);

					cha.setClient(getClient());
					getClient().setActiveChar(cha);
					cha.setOnlineStatus(true, true);

					//sendPacket(new SSQInfo());
					sendPacket(new ExSubjobInfo(cha));

					getClient().setState(GameClientState.IN_GAME);
					CharSelected cs = new CharSelected(cha, getClient().getSessionId().playOkID1);
					sendPacket(cs);
				}
			} finally {
				getClient().getActiveCharLock().unlock();
			}
		}
	}
}
