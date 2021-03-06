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

package handlers.voicedcommandhandlers;

import l2server.Config;
import l2server.DatabasePool;
import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.TimeController;
import l2server.gameserver.ai.CtrlIntention;
import l2server.gameserver.datatables.SkillTable;
import l2server.gameserver.handler.IVoicedCommandHandler;
import l2server.gameserver.instancemanager.CastleSiegeManager;
import l2server.gameserver.instancemanager.CoupleManager;
import l2server.gameserver.instancemanager.GrandBossManager;
import l2server.gameserver.model.Skill;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.CreatureZone;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.*;
import l2server.gameserver.stats.VisualEffect;
import l2server.gameserver.util.Broadcast;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * @author evill33t
 */
public class Wedding implements IVoicedCommandHandler {
	private static final String[] voicedCommands = {"divorce", "engage", "gotolove"};

	/**
	 * @see l2server.gameserver.handler.IVoicedCommandHandler#useVoicedCommand(java.lang.String, Player, java.lang.String)
	 */
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String params) {
		if (activeChar == null) {
			return false;
		}
		if (command.startsWith("engage")) {
			return engage(activeChar);
		} else if (command.startsWith("divorce")) {
			return divorce(activeChar);
		} else if (command.startsWith("gotolove")) {
			return goToLove(activeChar);
		}
		return false;
	}

	public boolean divorce(Player activeChar) {
		if (activeChar.getPartnerId() == 0) {
			return false;
		}

		int partnerId = activeChar.getPartnerId();
		int coupleId = activeChar.getCoupleId();
		long AdenaAmount = 0;

		if (activeChar.isMarried()) {
			activeChar.sendMessage("You are now divorced.");

			AdenaAmount = activeChar.getAdena() / 100 * Config.L2JMOD_WEDDING_DIVORCE_COSTS;
			activeChar.getInventory().reduceAdena("Wedding", AdenaAmount, activeChar, null);
		} else {
			activeChar.sendMessage("You have broken up as a couple.");
		}

		final Player partner = World.getInstance().getPlayer(partnerId);
		if (partner != null) {
			partner.setPartnerId(0);
			if (partner.isMarried()) {
				partner.sendMessage("Your spouse has decided to divorce you.");
			} else {
				partner.sendMessage("Your fiance has decided to break the engagement with you.");
			}

			// give adena
			if (AdenaAmount > 0) {
				partner.addAdena("WEDDING", AdenaAmount, null, false);
			}
		}

		CoupleManager.getInstance().deleteCouple(coupleId);
		return true;
	}

	public boolean engage(Player activeChar) {
		if (activeChar.getTarget() == null) {
			activeChar.sendMessage("You have no one targeted.");
			return false;
		} else if (!(activeChar.getTarget() instanceof Player)) {
			activeChar.sendMessage("You can only ask another player to engage you.");
			return false;
		} else if (activeChar.getPartnerId() != 0) {
			activeChar.sendMessage("You are already engaged.");
			if (Config.L2JMOD_WEDDING_PUNISH_INFIDELITY) {
				activeChar.startVisualEffect(VisualEffect.BIG_HEAD); // give player a Big Head
				// lets recycle the sevensigns debuffs
				int skillId;

				int skillLevel = 1;

				if (activeChar.getLevel() > 40) {
					skillLevel = 2;
				}

				if (activeChar.isMageClass()) {
					skillId = 4362;
				} else {
					skillId = 4361;
				}

				final Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);

				if (activeChar.getFirstEffect(skill) == null) {
					skill.getEffects(activeChar, activeChar);
					final SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
					sm.addSkillName(skill);
					activeChar.sendPacket(sm);
				}
			}
			return false;
		}

		final Player ptarget = (Player) activeChar.getTarget();
		// check if player target himself
		if (ptarget.getObjectId() == activeChar.getObjectId()) {
			activeChar.sendMessage("Is there something wrong with you, are you trying to go out with youself?");
			return false;
		} else if (ptarget.isMarried()) {
			activeChar.sendMessage("Player already married.");
			return false;
		} else if (ptarget.isEngageRequest()) {
			activeChar.sendMessage("Player already asked by someone else.");
			return false;
		} else if (ptarget.getPartnerId() != 0) {
			activeChar.sendMessage("Player already engaged with someone else.");
			return false;
		} else if (ptarget.getAppearance().getSex() == activeChar.getAppearance().getSex() && !Config.L2JMOD_WEDDING_SAMESEX) {
			activeChar.sendMessage("Gay marriage is not allowed on this server!");
			return false;
		}

		// check if target has player on friendlist
		boolean FoundOnFriendList = false;
		int objectId;
		java.sql.Connection con = null;
		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("SELECT friendId FROM character_friends WHERE charId=?");
			statement.setInt(1, ptarget.getObjectId());
			ResultSet rset = statement.executeQuery();

			while (rset.next()) {
				objectId = rset.getInt("friendId");
				if (objectId == activeChar.getObjectId()) {
					FoundOnFriendList = true;
				}
			}
			statement.close();
		} catch (Exception e) {
			log.warn("could not read friend data:" + e);
		} finally {
			DatabasePool.close(con);
		}

		if (!FoundOnFriendList) {
			activeChar.sendMessage(
					"The player you want to ask is not on your friends list, you must first be on each others friends list before you choose to engage.");
			return false;
		}

		ptarget.setEngageRequest(true, activeChar.getObjectId());
		// $s1
		ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.S1.getId()).addString(
				activeChar.getName() + " is asking to engage you. Do you want to start a new relationship?");
		ptarget.sendPacket(dlg);
		return true;
	}

	public boolean goToLove(Player activeChar) {
		if (!activeChar.isMarried()) {
			activeChar.sendMessage("You're not married.");
			return false;
		}

		if (activeChar.getPartnerId() == 0) {
			activeChar.sendMessage("Couldn't find your fiance in the Database - Inform a Gamemaster.");
			log.error("Married but couldn't find parter for " + activeChar.getName());
			return false;
		} else if (activeChar.getIsInsideGMEvent()) {
			return false;
		} else if (GrandBossManager.getInstance().getZone(activeChar) != null) {
			activeChar.sendMessage("You are inside a Boss Zone.");
			return false;
		} else if (!activeChar.canEscape()) {
			activeChar.sendMessage("You cannot escape!");
			return false;
		} else if (activeChar.isCombatFlagEquipped()) {
			activeChar.sendMessage("While you are holding a Combat Flag or Territory Ward you can't go to your love!");
			return false;
		}
		// This might prevent use .gotolove when immobilized (stun, sleep, medusa, paralize...) (Soul)
		else if (activeChar.isImmobilized()) {
			activeChar.sendMessage("You cannot go to your partner if you are Immobilized.");
		} else if (activeChar.isCursedWeaponEquipped()) {
			activeChar.sendMessage("While you are holding a Cursed Weapon you can't go to your love!");
			return false;
		} else if (GrandBossManager.getInstance().getZone(activeChar) != null) {
			activeChar.sendMessage("You are inside a Boss Zone.");
			return false;
		} else if (activeChar.isInJail()) {
			activeChar.sendMessage("You are in Jail!");
			return false;
		} else if (activeChar.isInOlympiadMode()) {
			activeChar.sendMessage("You are in the Olympiad now.");
			return false;
		} else if (activeChar.isInDuel()) {
			activeChar.sendMessage("You are in a duel!");
			return false;
		} else if (activeChar.inObserverMode()) {
			activeChar.sendMessage("You are in the observation.");
			return false;
		} else if (CastleSiegeManager.getInstance().getSiege(activeChar) != null && CastleSiegeManager.getInstance().getSiege(activeChar).getIsInProgress()) {
			activeChar.sendMessage("You are in a siege, you cannot go to your partner.");
			return false;
		}
		// Thanks nbd
		if (activeChar.getEvent() != null && !activeChar.getEvent().onEscapeUse(activeChar.getObjectId())) {
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		} else if (activeChar.isInsideZone(CreatureZone.ZONE_NOSUMMONFRIEND)) {
			activeChar.sendMessage("You are in area which blocks summoning.");
			return false;
		}

		final Player partner = World.getInstance().getPlayer(activeChar.getPartnerId());
		if (partner == null || !partner.isOnline()) {
			activeChar.sendMessage("Your partner is not online.");
			return false;
		} else if (partner.getIsInsideGMEvent()) {
			return false;
		} else if (activeChar.getInstanceId() != partner.getInstanceId()) {
			activeChar.sendMessage("Your partner is in another World!");
			return false;
		} else if (partner.isCursedWeaponEquipped()) {
			activeChar.sendMessage("You cant releport to your partner while he have a cursed weapon!");
			return false;
		} else if (partner.isInJail()) {
			activeChar.sendMessage("Your partner is in Jail.");
			return false;
		} else if (GrandBossManager.getInstance().getZone(partner) != null) {
			activeChar.sendMessage("Your partner is inside a Boss Zone.");
			return false;
		} else if (partner.isInOlympiadMode()) {
			activeChar.sendMessage("Your partner is in the Olympiad now.");
			return false;
		} else if (partner.isInDuel()) {
			activeChar.sendMessage("Your partner is in a duel.");
			return false;
		} else if (partner.inObserverMode()) {
			activeChar.sendMessage("Your partner is in the observation.");
			return false;
		} else if (CastleSiegeManager.getInstance().getSiege(partner) != null && CastleSiegeManager.getInstance().getSiege(partner).getIsInProgress()) {
			activeChar.sendMessage("Your partner is in a siege, you cannot go to your partner.");
			return false;
		}
		if (partner.getEvent() != null && !partner.getEvent().onEscapeUse(partner.getObjectId())) {
			activeChar.sendMessage("Your partner is in an event.");
			return false;
		}

		final int teleportTimer = Config.L2JMOD_WEDDING_TELEPORT_DURATION * 1000;

		activeChar.sendMessage("After " + teleportTimer / 60000 + " min. you will be teleported to your partner.");
		activeChar.getInventory().reduceAdena("Wedding", Config.L2JMOD_WEDDING_TELEPORT_PRICE, activeChar, null);

		activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		//SoE Animation section
		activeChar.setTarget(activeChar);
		activeChar.disableAllSkills();

		final MagicSkillUse msk = new MagicSkillUse(activeChar, 1050, 1, teleportTimer, 0);
		Broadcast.toSelfAndKnownPlayersInRadius(activeChar, msk, 810000/*900*/);
		final SetupGauge sg = new SetupGauge(0, teleportTimer);
		activeChar.sendPacket(sg);
		//End SoE Animation section

		final EscapeFinalizer ef = new EscapeFinalizer(activeChar, partner.getX(), partner.getY(), partner.getZ());
		// continue execution later
		activeChar.setSkillCast(ThreadPoolManager.getInstance().scheduleGeneral(ef, teleportTimer));
		activeChar.forceIsCasting(TimeController.getGameTicks() + teleportTimer / TimeController.MILLIS_IN_TICK);

		return true;
	}

	static class EscapeFinalizer implements Runnable {
		private final Player activeChar;
		private final int partnerx;
		private final int partnery;
		private final int partnerz;

		EscapeFinalizer(Player activeChar, int x, int y, int z) {
			this.activeChar = activeChar;
			partnerx = x;
			partnery = y;
			partnerz = z;
		}

		@Override
		public void run() {
			if (activeChar.isDead()) {
				return;
			}

			if (CastleSiegeManager.getInstance().getSiege(partnerx, partnery, partnerz) != null &&
					CastleSiegeManager.getInstance().getSiege(partnerx, partnery, partnerz).getIsInProgress()) {
				activeChar.sendMessage("Your partner is in siege, you can't go to your partner.");
				return;
			}

			activeChar.enableAllSkills();
			activeChar.setCastingNow(false);

			try {
				activeChar.teleToLocation(partnerx, partnery, partnerz);
			} catch (Exception e) {
				log.error("", e);
			}
		}
	}

	/**
	 * @see l2server.gameserver.handler.IVoicedCommandHandler#getVoicedCommandList()
	 */
	@Override
	public String[] getVoicedCommandList() {
		return voicedCommands;
	}
}
