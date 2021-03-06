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

package l2server.gameserver.model.actor.instance;

import l2server.gameserver.model.InstanceType;
import l2server.Config;
import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.ai.CtrlIntention;
import l2server.gameserver.datatables.DoorTable;
import l2server.gameserver.instancemanager.FourSepulchersManager;
import l2server.gameserver.model.Item;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.quest.Quest;
import l2server.gameserver.network.clientpackets.Say2;
import l2server.gameserver.network.serverpackets.*;
import l2server.gameserver.templates.chars.NpcTemplate;
import l2server.gameserver.util.Util;
import l2server.util.Rnd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Future;

/**
 * @author sandman
 */
public class SepulcherNpcInstance extends Npc {
	private static Logger log = LoggerFactory.getLogger(SepulcherNpcInstance.class.getName());

	protected Future<?> closeTask = null;
	protected Future<?> spawnNextMysteriousBoxTask = null;
	protected Future<?> spawnMonsterTask = null;
	
	private static final String HTML_FILE_PATH = "SepulcherNpc/";
	private static final int HALLS_KEY = 7260;
	
	public SepulcherNpcInstance(int objectID, NpcTemplate template) {
		super(objectID, template);
		setInstanceType(InstanceType.L2SepulcherNpcInstance);
		setShowSummonAnimation(true);
		
		if (closeTask != null) {
			closeTask.cancel(true);
		}
		if (spawnNextMysteriousBoxTask != null) {
			spawnNextMysteriousBoxTask.cancel(true);
		}
		if (spawnMonsterTask != null) {
			spawnMonsterTask.cancel(true);
		}
		closeTask = null;
		spawnNextMysteriousBoxTask = null;
		spawnMonsterTask = null;
	}
	
	@Override
	public void onSpawn() {
		super.onSpawn();
		setShowSummonAnimation(false);
	}
	
	@Override
	public void deleteMe() {
		if (closeTask != null) {
			closeTask.cancel(true);
			closeTask = null;
		}
		if (spawnNextMysteriousBoxTask != null) {
			spawnNextMysteriousBoxTask.cancel(true);
			spawnNextMysteriousBoxTask = null;
		}
		if (spawnMonsterTask != null) {
			spawnMonsterTask.cancel(true);
			spawnMonsterTask = null;
		}
		super.deleteMe();
	}
	
	@Override
	public void onAction(Player player, boolean interact) {
		if (!canTarget(player)) {
			return;
		}
		
		// Check if the Player already target the NpcInstance
		if (this != player.getTarget()) {
			if (Config.DEBUG) {
				log.info("new target selected:" + getObjectId());
			}
			
			// Set the target of the Player player
			player.setTarget(this);
			
			// Check if the player is attackable (without a forced attack)
			if (isAutoAttackable(player)) {
				// Send a Server->Client packet MyTargetSelected to the
				// Player player
				// The player.getLevel() - getLevel() permit to display the
				// correct color in the select window
				MyTargetSelected my = new MyTargetSelected(getObjectId(), player.getLevel() - getLevel());
				player.sendPacket(my);
				
				// Send a Server->Client packet StatusUpdate of the
				// NpcInstance to the Player to update its HP bar
				StatusUpdate su = new StatusUpdate(this);
				su.addAttribute(StatusUpdate.CUR_HP, (int) getStatus().getCurrentHp());
				su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
				player.sendPacket(su);
			} else {
				// Send a Server->Client packet MyTargetSelected to the
				// Player player
				MyTargetSelected my = new MyTargetSelected(getObjectId(), 0);
				player.sendPacket(my);
			}
			
			// Send a Server->Client packet ValidateLocation to correct the
			// NpcInstance position and heading on the client
			player.sendPacket(new ValidateLocation(this));
		} else if (interact) {
			// Check if the player is attackable (without a forced attack) and
			// isn't dead
			if (isAutoAttackable(player) && !isAlikeDead()) {
				// Check the height difference
				if (Math.abs(player.getZ() - getZ()) < 400) // this max heigth
				// difference might
				// need some tweaking
				{
					// Set the Player Intention to AI_INTENTION_ATTACK
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
				} else {
					// Send a Server->Client packet ActionFailed (target is out
					// of attack range) to the Player player
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
			}
			
			if (!isAutoAttackable(player)) {
				// Calculate the distance between the Player and the
				// NpcInstance
				if (!canInteract(player)) {
					// Notify the Player AI with AI_INTENTION_INTERACT
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
				} else {
					// Send a Server->Client packet SocialAction to the all
					// Player on the knownPlayer of the NpcInstance
					// to display a social action of the NpcInstance on their
					// client
					SocialAction sa = new SocialAction(getObjectId(), Rnd.get(8));
					broadcastPacket(sa);
					
					doAction(player);
				}
			}
			// Send a Server->Client ActionFailed to the Player in order
			// to avoid that the client wait another packet
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	private void doAction(Player player) {
		if (isDead()) {
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		switch (getNpcId()) {
			case 31468:
			case 31469:
			case 31470:
			case 31471:
			case 31472:
			case 31473:
			case 31474:
			case 31475:
			case 31476:
			case 31477:
			case 31478:
			case 31479:
			case 31480:
			case 31481:
			case 31482:
			case 31483:
			case 31484:
			case 31485:
			case 31486:
			case 31487:
				setInvul(false);
				reduceCurrentHp(getMaxHp() + 1, player, null);
				if (spawnMonsterTask != null) {
					spawnMonsterTask.cancel(true);
				}
				spawnMonsterTask = ThreadPoolManager.getInstance().scheduleEffect(new SpawnMonster(getNpcId()), 3500);
				break;
			
			case 31455:
			case 31456:
			case 31457:
			case 31458:
			case 31459:
			case 31460:
			case 31461:
			case 31462:
			case 31463:
			case 31464:
			case 31465:
			case 31466:
			case 31467:
				setInvul(false);
				reduceCurrentHp(getMaxHp() + 1, player, null);
				if (player.getParty() != null && !player.getParty().isLeader(player)) {
					player = player.getParty().getLeader();
				}
				player.addItem("Quest", HALLS_KEY, 1, player, true);
				break;
			
			default: {
				Quest[] qlsa = getTemplate().getEventQuests(Quest.QuestEventType.QUEST_START);
				if (qlsa != null && qlsa.length > 0) {
					player.setLastQuestNpcObject(getObjectId());
				}
				Quest[] qlst = getTemplate().getEventQuests(Quest.QuestEventType.ON_FIRST_TALK);
				if (qlst != null && qlst.length == 1) {
					qlst[0].notifyFirstTalk(this, player);
				} else {
					showChatWindow(player, 0);
				}
			}
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val) {
		String pom = "";
		if (val == 0) {
			pom = "" + npcId;
		} else {
			pom = npcId + "-" + val;
		}
		
		return HTML_FILE_PATH + pom + ".htm";
	}
	
	@Override
	public void showChatWindow(Player player, int val) {
		String filename = getHtmlPath(getNpcId(), val);
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(player.getHtmlPrefix(), filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command) {
		if (isBusy()) {
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile(player.getHtmlPrefix(), "npcbusy.htm");
			html.replace("%busymessage%", getBusyMessage());
			html.replace("%npcname%", getName());
			html.replace("%playername%", player.getName());
			player.sendPacket(html);
		} else if (command.startsWith("Chat")) {
			int val = 0;
			try {
				val = Integer.parseInt(command.substring(5));
			} catch (IndexOutOfBoundsException | NumberFormatException ignored) {
			}
			showChatWindow(player, val);
		} else if (command.startsWith("open_gate")) {
			Item hallsKey = player.getInventory().getItemByItemId(HALLS_KEY);
			if (hallsKey == null) {
				showHtmlFile(player, "Gatekeeper-no.htm");
			} else if (FourSepulchersManager.getInstance().isAttackTime()) {
				switch (getNpcId()) {
					case 31929:
					case 31934:
					case 31939:
					case 31944:
						FourSepulchersManager.getInstance().spawnShadow(getNpcId());
					default: {
						openNextDoor(getNpcId());
						if (player.getParty() != null) {
							for (Player mem : player.getParty().getPartyMembers()) {
								if (mem != null && mem.getInventory().getItemByItemId(HALLS_KEY) != null) {
									mem.destroyItemByItemId("Quest", HALLS_KEY, mem.getInventory().getItemByItemId(HALLS_KEY).getCount(), mem, true);
								}
							}
						} else {
							player.destroyItemByItemId("Quest", HALLS_KEY, hallsKey.getCount(), player, true);
						}
					}
				}
			}
		} else {
			super.onBypassFeedback(player, command);
		}
	}
	
	public void openNextDoor(int npcId) {
		int doorId = FourSepulchersManager.getInstance().getHallGateKeepers().get(npcId);
		DoorTable doorTable = DoorTable.getInstance();
		doorTable.getDoor(doorId).openMe();
		
		if (closeTask != null) {
			closeTask.cancel(true);
		}
		closeTask = ThreadPoolManager.getInstance().scheduleEffect(new CloseNextDoor(doorId), 10000);
		if (spawnNextMysteriousBoxTask != null) {
			spawnNextMysteriousBoxTask.cancel(true);
		}
		spawnNextMysteriousBoxTask = ThreadPoolManager.getInstance().scheduleEffect(new SpawnNextMysteriousBox(npcId), 0);
	}
	
	private static class CloseNextDoor implements Runnable {
		final DoorTable DoorTable = this.DoorTable.getInstance();
		
		private int DoorId;
		
		public CloseNextDoor(int doorId) {
			this.DoorId = DoorId;
		}
		
		@Override
		public void run() {
			try {
				DoorTable.getDoor(DoorId).closeMe();
			} catch (Exception e) {
				log.warn(e.getMessage());
			}
		}
	}
	
	private static class SpawnNextMysteriousBox implements Runnable {
		private int NpcId;
		
		public SpawnNextMysteriousBox(int npcId) {
			this.NpcId = NpcId;
		}
		
		@Override
		public void run() {
			FourSepulchersManager.getInstance().spawnMysteriousBox(NpcId);
		}
	}
	
	private static class SpawnMonster implements Runnable {
		private int NpcId;
		
		public SpawnMonster(int npcId) {
			this.NpcId = NpcId;
		}
		
		@Override
		public void run() {
			FourSepulchersManager.getInstance().spawnMonster(NpcId);
		}
	}
	
	public void sayInShout(String msg) {
		if (msg == null || msg.isEmpty()) {
			return;// wrong usage
		}
		Collection<Player> knownPlayers = World.getInstance().getAllPlayers().values();
		if (knownPlayers == null || knownPlayers.isEmpty()) {
			return;
		}
		CreatureSay sm = new CreatureSay(0, Say2.SHOUT, getName(), msg);
		for (Player player : knownPlayers) {
			if (player == null) {
				continue;
			}
			if (Util.checkIfInRange(15000, player, this, true)) {
				player.sendPacket(sm);
			}
		}
	}
	
	public void showHtmlFile(Player player, String file) {
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(player.getHtmlPrefix(), "SepulcherNpc/" + file);
		html.replace("%npcname%", getName());
		player.sendPacket(html);
	}
}
