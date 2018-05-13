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

package l2server.gameserver.model.actor;

import l2server.Config;
import l2server.DatabasePool;
import l2server.gameserver.ai.CreatureAI;
import l2server.gameserver.ai.CtrlIntention;
import l2server.gameserver.ai.SummonAI;
import l2server.gameserver.datatables.SkillTable;
import l2server.gameserver.model.*;
import l2server.gameserver.model.actor.Attackable.AggroInfo;
import l2server.gameserver.model.actor.instance.*;
import l2server.gameserver.model.actor.knownlist.SummonKnownList;
import l2server.gameserver.model.actor.stat.SummonStat;
import l2server.gameserver.model.actor.status.SummonStatus;
import l2server.gameserver.model.base.Experience;
import l2server.gameserver.model.itemcontainer.PetInventory;
import l2server.gameserver.model.olympiad.OlympiadGameManager;
import l2server.gameserver.model.quest.Quest;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.*;
import l2server.gameserver.network.serverpackets.StatusUpdate.StatusUpdateDisplay;
import l2server.gameserver.taskmanager.DecayTaskManager;
import l2server.gameserver.templates.chars.NpcTemplate;
import l2server.gameserver.templates.item.WeaponTemplate;
import l2server.gameserver.templates.skills.SkillTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class Summon extends Playable {
	
	private static Logger log = LoggerFactory.getLogger(Summon.class.getName());
	
	protected Player owner;
	private int attackRange = 36; //Melee range
	private boolean follow = true;
	private boolean previousFollowStatus = true;

	private double chargedSoulShot;
	private double chargedSpiritShot;

	//  /!\ BLACK MAGIC /!\
	// we dont have walk speed in pet data so for now use runspd / 3
	public static final int WALK_SPEED_MULTIPLIER = 3;

	public Summon(int objectId, NpcTemplate template, Player owner) {
		super(objectId, template);
		setInstanceType(InstanceType.L2Summon);

		setInstanceId(owner.getInstanceId()); // set instance to same as owner

		showSummonAnimation = true;
		this.owner = owner;
		getAI();

		setXYZInvisible(owner.getX() + 20, owner.getY() + 20, owner.getZ() + 100);
	}

	@Override
	public void onSpawn() {
		super.onSpawn();
		if (!(this instanceof MerchantSummonInstance) && !(this instanceof CloneInstance)) {
			setFollowStatus(true);
			updateAndBroadcastStatus(0);
			getOwner().sendPacket(new RelationChanged(this, getOwner().getRelation(getOwner()), false));
			for (Player player : getOwner().getKnownList().getKnownPlayersInRadius(800)) {
				player.sendPacket(new RelationChanged(this, getOwner().getRelation(player), isAutoAttackable(player)));
			}
			L2Party party = getOwner().getParty();
			if (party != null) {
				party.broadcastToPartyMembers(getOwner(), new ExPartyPetWindowAdd(this));
			}

			if (getOwner().isPlayingEvent()) {
				NpcBufferInstance.buff(this);
			}
		}
		setShowSummonAnimation(false); // addVisibleObject created the info packets with summon animation
		// if someone comes into range now, the animation shouldnt show any more

		if (getTemplate().getEventQuests(Quest.QuestEventType.ON_SPAWN) != null) {
			for (Quest quest : getTemplate().getEventQuests(Quest.QuestEventType.ON_SPAWN)) {
				quest.notifySpawn(this);
			}
		}

		getOwner().setIsSummonsInDefendingMode(getOwner().getIsSummonsInDefendingMode());
	}

	@Override
	public final SummonKnownList getKnownList() {
		return (SummonKnownList) super.getKnownList();
	}

	@Override
	public SummonKnownList initialKnownList() {
		return new SummonKnownList(this);
	}

	@Override
	public SummonStat getStat() {
		return (SummonStat) super.getStat();
	}

	@Override
	public void initCharStat() {
		setStat(new SummonStat(this));
	}

	@Override
	public SummonStatus getStatus() {
		return (SummonStatus) super.getStatus();
	}

	@Override
	public void initCharStatus() {
		setStatus(new SummonStatus(this));
	}

	@Override
	protected CreatureAI initAI() {
		return new SummonAI(this);
	}

	@Override
	public NpcTemplate getTemplate() {
		return (NpcTemplate) super.getTemplate();
	}

	// this defines the action buttons, 1 for Summon, 2 for Pets
	public abstract int getSummonType();

	@Override
	public final void stopAllEffects() {
		super.stopAllEffects();
		updateAndBroadcastStatus(1);
	}

	@Override
	public final void stopAllEffectsExceptThoseThatLastThroughDeath() {
		super.stopAllEffectsExceptThoseThatLastThroughDeath();
		updateAndBroadcastStatus(1);
	}

	@Override
	public void updateAbnormalEffect() {
		if (!isVisible()) {
			return;
		}

		Collection<Player> plrs = getKnownList().getKnownPlayers().values();
		//synchronized (getKnownList().getKnownPlayers())
		{
			for (Player player : plrs) {
				if (player == null) {
					continue;
				}
				if (!player.isGM() && getOwner() != null && getOwner().getAppearance().getInvisible()) {
					continue;
				}

				if (this instanceof PetInstance) {
					player.sendPacket(new ExPetInfo((PetInstance) this, player, 1));
				} else {
					player.sendPacket(new ExSummonInfo((SummonInstance) this, player, 1));
				}
			}
		}

		if (!(this instanceof CloneInstance)) {
			getOwner().sendPacket(new PetInfo(this, 1));
		}
	}

	/**
	 * @return Returns the mountable.
	 */
	public boolean isMountable() {
		return false;
	}

	public long getExpForThisLevel() {
		if (getLevel() > Config.MAX_LEVEL) {
			return 0;
		}

		return Experience.getAbsoluteExp(getLevel());
	}

	public long getExpForNextLevel() {
		if (getLevel() >= Config.MAX_LEVEL) {
			return 0;
		}

		return Experience.getAbsoluteExp(getLevel() + 1);
	}

	@Override
	public final int getReputation() {
		return getOwner() != null ? getOwner().getReputation() : 0;
	}

	@Override
	public final byte getPvpFlag() {
		return getOwner() != null ? getOwner().getPvpFlag() : 0;
	}

	public final int getTeam() {
		return getOwner() != null ? getOwner().getTeam() : 0;
	}

	public final Player getOwner() {
		return owner;
	}

	public final int getNpcId() {
		return getTemplate().NpcId;
	}

	public int getMaxLoad() {
		return 0;
	}

	public short getSoulShotsPerHit() {
		if (getTemplate().getAIData().getSoulShot() > 0) {
			return (short) getTemplate().getAIData().getSoulShot();
		} else {
			return 1;
		}
	}

	public short getSpiritShotsPerHit() {
		if (getTemplate().getAIData().getSpiritShot() > 0) {
			return (short) getTemplate().getAIData().getSpiritShot();
		} else {
			return 1;
		}
	}

	public void setChargedSoulShot(double shotType) {
		chargedSoulShot = shotType;
	}

	public void setChargedSpiritShot(double shotType) {
		chargedSpiritShot = shotType;
	}

	public void followOwner() {
		setFollowStatus(true);
	}

	@Override
	public boolean doDie(Creature killer) {
		if (!super.doDie(killer)) {
			return false;
		}

		if (this instanceof MerchantSummonInstance) {
			return true;
		}

		Player owner = getOwner();
		if (owner != null) {
			Collection<Creature> KnownTarget = getKnownList().getKnownCharacters();
			for (Creature TgMob : KnownTarget) {
				// get the mobs which have aggro on the this instance
				if (TgMob instanceof Attackable) {
					if (TgMob.isDead()) {
						continue;
					}

					AggroInfo info = ((Attackable) TgMob).getAggroList().get(this);
					if (info != null) {
						((Attackable) TgMob).addDamageHate(owner, info.getDamage(), info.getHate());
					}
				}
			}
		}

		if (isPhoenixBlessed() && getOwner() != null) {
			getOwner().reviveRequest(getOwner(), null, true);
		}

		DecayTaskManager.getInstance().addDecayTask(this);
		return true;
	}

	public boolean doDie(Creature killer, boolean decayed) {
		if (!super.doDie(killer)) {
			return false;
		}
		if (!decayed) {
			DecayTaskManager.getInstance().addDecayTask(this);
		}
		return true;
	}

	public void stopDecay() {
		DecayTaskManager.getInstance().cancelDecayTask(this);
	}

	@Override
	public void onDecay() {
		deleteMe(owner);
	}

	@Override
	public void broadcastStatusUpdate() {
		super.broadcastStatusUpdate();
		updateAndBroadcastStatus(1);
	}

	@Override
	public void broadcastStatusUpdate(Creature causer, StatusUpdateDisplay display) {
		// Send the Server->Client packet StatusUpdate with current HP and MP to all Player that must be informed of HP/MP updates of this Player
		//super.broadcastStatusUpdate(causer, display);

		// Send the Server->Client packet StatusUpdate with current HP, MP and CP to this Player
		StatusUpdate su = new StatusUpdate(this, causer, display);
		su.addAttribute(StatusUpdate.CUR_HP, (int) getCurrentHp());
		su.addAttribute(StatusUpdate.CUR_MP, (int) getCurrentMp());
		sendPacket(su);

		final boolean needHpUpdate = needHpUpdate(352);

		// Check if a party is in progress and party window update is usefull
		L2Party party = owner.getParty();
		if (party != null && needHpUpdate) {
			// Send the Server->Client packet PartySmallWindowUpdate with current HP, MP and Level to all other Player of the Party
			//PartySmallWindowUpdate update = new PartySmallWindowUpdate(this);
			//party.broadcastToPartyMembers(owner, update);
			party.broadcastToPartyMembers(owner, su);
		}

		owner.sendPacket(su);
	}

	public void deleteMe(Player owner) {
		if (!(this instanceof CloneInstance)) {
			owner.sendPacket(new PetDelete(getSummonType(), getObjectId()));

			//pet will be deleted along with all his items
			if (getInventory() != null) {
				getInventory().destroyAllItems("pet deleted", getOwner(), this);
			}
		}

		decayMe();
		getKnownList().removeAllKnownObjects();
		if (this instanceof SummonInstance) {
			if (!(this instanceof CloneInstance)) {
				owner.removeSummon((SummonInstance) this);
			}
		} else {
			owner.setPet(null);
		}

		super.deleteMe();
	}

	public void unSummon(Player owner) {
		if (isVisible() && !isDead()) {
			getAI().stopFollow();

			L2Party party;
			if (!(this instanceof CloneInstance)) {
				if ((party = owner.getParty()) != null) {
					party.broadcastToPartyMembers(owner, new ExPartyPetWindowDelete(this));
				}

				if (getInventory() != null && getInventory().getSize() > 0) {
					getOwner().setPetInvItems(true);
					getOwner().sendPacket(SystemMessageId.ITEMS_IN_PET_INVENTORY);
				} else {
					getOwner().setPetInvItems(false);
				}

				store();
				if (this instanceof SummonInstance) {
					owner.removeSummon((SummonInstance) this);
				} else {
					owner.setPet(null);
				}
			}

			// Stop AI tasks
			if (hasAI()) {
				getAI().stopAITask();
			}

			stopAllEffects();
			WorldRegion oldRegion = getWorldRegion();
			decayMe();

			if (oldRegion != null) {
				oldRegion.removeFromZones(this);
			}

			getKnownList().removeAllKnownObjects();
			setTarget(null);

			if (!(this instanceof CloneInstance)) {
				owner.disableAutoShot(2); // Beast soulshot
				owner.disableAutoShot(3); // Beast spiritshot

				if (owner.getSummons().size() > 0) {
					for (SummonInstance summon : owner.getSummons()) {
						owner.sendPacket(new PetInfo(summon, 2));
					}

					SummonInstance selected = owner.getSummon(owner.getSummons().size() - 1);
					owner.setActiveSummon(selected);
					owner.sendPacket(new PetStatusShow(selected));
				}

				owner.sendPacket(new PetDelete(getSummonType(), getObjectId()));
			}
		}
	}

	public int getAttackRange() {
		return attackRange;
	}

	public void setAttackRange(int range) {
		if (range < 36) {
			range = 36;
		}
		attackRange = range;
	}

	public void setFollowStatus(boolean state) {
		follow = state;
		if (follow) {
			getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, getOwner());
		} else {
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE, null);
		}
	}

	public boolean getFollowStatus() {
		return follow;
	}

	@Override
	public boolean isAutoAttackable(Creature attacker) {
		return owner.isAutoAttackable(attacker);
	}

	public double getChargedSoulShot() {
		return chargedSoulShot;
	}

	public double getChargedSpiritShot() {
		return chargedSpiritShot;
	}

	public int getControlObjectId() {
		return 0;
	}

	public WeaponTemplate getActiveWeapon() {
		return null;
	}

	@Override
	public PetInventory getInventory() {
		return null;
	}

	public void doPickupItem(WorldObject object) {
	}

	public void store() {
	}

	@Override
	public Item getActiveWeaponInstance() {
		return null;
	}

	@Override
	public WeaponTemplate getActiveWeaponItem() {
		return null;
	}

	@Override
	public Item getSecondaryWeaponInstance() {
		return null;
	}

	@Override
	public WeaponTemplate getSecondaryWeaponItem() {
		return null;
	}

	/**
	 * Return True if the Summon is invulnerable or if the summoner is in spawn protection.<BR><BR>
	 */
	@Override
	public boolean isInvul() {
		return super.isInvul() || getOwner().isSpawnProtected();
	}

	/**
	 * Return the L2Party object of its Player owner or null.<BR><BR>
	 */
	@Override
	public L2Party getParty() {
		if (owner == null) {
			return null;
		} else {
			return owner.getParty();
		}
	}

	/**
	 * Return True if the Creature has a Party in progress.<BR><BR>
	 */
	@Override
	public boolean isInParty() {
		if (owner == null) {
			return false;
		} else {
			return owner.getParty() != null;
		}
	}

	/**
	 * Check if the active Skill can be casted.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Check if the target is correct </li>
	 * <li>Check if the target is in the skill cast range </li>
	 * <li>Check if the summon owns enough HP and MP to cast the skill </li>
	 * <li>Check if all skills are enabled and this skill is enabled </li><BR><BR>
	 * <li>Check if the skill is active </li><BR><BR>
	 * <li>Notify the AI with AI_INTENTION_CAST and target</li><BR><BR>
	 *
	 * @param skill    The Skill to use
	 * @param forceUse used to force ATTACK on players
	 * @param dontMove used to prevent movement, if not in range
	 */
	@Override
	public boolean useMagic(Skill skill, boolean forceUse, boolean dontMove) {
		if (skill == null || isDead()) {
			return false;
		}

		// Check if the skill is active
		if (skill.isPassive()) {
			// just ignore the passive skill request. why does the client send it anyway ??
			return false;
		}

		//************************************* Check Casting in Progress *******************************************

		// If a skill is currently being used
		if (isCastingNow()) {
			return false;
		}

		// Set current pet skill
		getOwner().setCurrentPetSkill(skill, forceUse, dontMove);

		//************************************* Check Target *******************************************

		// Get the target for the skill
		WorldObject target = null;

		switch (skill.getTargetType()) {
			// OWNER_PET should be cast even if no target has been found
			case TARGET_OWNER_PET:
				target = getOwner();
				break;
			// PARTY, AURA, SELF should be cast even if no target has been found
			case TARGET_PARTY:
			case TARGET_AURA:
			case TARGET_FRONT_AURA:
			case TARGET_BEHIND_AURA:
			case TARGET_SELF:
				target = this;
				break;
			default:
				// Get the first target of the list
				if (skill.isUseableWithoutTarget()) {
					target = this;
				} else {
					target = skill.getFirstOfTargetList(this);
				}
				break;
		}

		// Check the validity of the target
		if (target == null) {
			if (getOwner() != null) {
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_CANT_FOUND));
			}
			return false;
		}

		//************************************* Check skill availability *******************************************

		// Check if this skill is enabled (e.g. reuse time)
		if (isSkillDisabled(skill)) {
			if (getOwner() != null) {
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.PET_SKILL_CANNOT_BE_USED_RECHARCHING));
			}
			return false;
		}

		//************************************* Check Consumables *******************************************

		// Check if the summon has enough MP
		if (getCurrentMp() < getStat().getMpConsume(skill) + getStat().getMpInitialConsume(skill)) {
			// Send a System Message to the caster
			if (getOwner() != null) {
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_MP));
			}
			return false;
		}

		// Check if the summon has enough HP
		if (getCurrentHp() <= skill.getHpConsume()) {
			// Send a System Message to the caster
			if (getOwner() != null) {
				getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.NOT_ENOUGH_HP));
			}
			return false;
		}

		//************************************* Check Summon State *******************************************

		// Check if this is offensive magic skill
		if (skill.isOffensive()) {
			if (isInsidePeaceZone(this, target) && getOwner() != null && !getOwner().getAccessLevel().allowPeaceAttack()) {
				// If summon or target is in a peace zone, send a system message TARGET_IN_PEACEZONE
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IN_PEACEZONE));
				return false;
			}

			if (getOwner() != null && getOwner().isInOlympiadMode() && !getOwner().isOlympiadStart()) {
				// if Player is in Olympia and the match isn't already start, send a Server->Client packet ActionFailed
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}

			if (target.getActingPlayer() != null && getOwner().getSiegeState() > 0 && getOwner().isInsideZone(CreatureZone.ZONE_SIEGE) &&
					target.getActingPlayer().getSiegeState() == getOwner().getSiegeState() && target.getActingPlayer() != getOwner() &&
					target.getActingPlayer().getSiegeSide() == getOwner().getSiegeSide()) {
				sendPacket(SystemMessage.getSystemMessage(SystemMessageId.FORCED_ATTACK_IS_IMPOSSIBLE_AGAINST_SIEGE_SIDE_TEMPORARY_ALLIED_MEMBERS));
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}

			// Check if the target is attackable
			if (target instanceof DoorInstance) {
				if (!((DoorInstance) target).isAttackable(getOwner())) {
					return false;
				}
			} else {
				if (!target.isAttackable() && getOwner() != null && !getOwner().getAccessLevel().allowPeaceAttack()) {
					return false;
				}

				// Check if a Forced ATTACK is in progress on non-attackable target
				if (!target.isAutoAttackable(this) && !forceUse && skill.getTargetType() != SkillTargetType.TARGET_AURA &&
						skill.getTargetType() != SkillTargetType.TARGET_FRONT_AURA &&
						skill.getTargetType() != SkillTargetType.TARGET_BEHIND_AURA && skill.getTargetType() != SkillTargetType.TARGET_CLAN &&
						skill.getTargetType() != SkillTargetType.TARGET_ALLY && skill.getTargetType() != SkillTargetType.TARGET_PARTY &&
						skill.getTargetType() != SkillTargetType.TARGET_SELF && skill.getTargetType() != SkillTargetType.TARGET_AROUND_CASTER) {
					return false;
				}
			}
		}
		// Notify the AI with AI_INTENTION_CAST and target
		getAI().setIntention(CtrlIntention.AI_INTENTION_CAST, skill, target);

		owner.disableSkill(skill, skill.getReuseDelay());
		return true;
	}

	@Override
	public void setIsImmobilized(boolean value) {
		super.setIsImmobilized(value);

		if (value) {
			previousFollowStatus = getFollowStatus();
			// if immobilized temporarly disable follow mode
			if (previousFollowStatus) {
				setFollowStatus(false);
			}
		} else {
			// if not more immobilized restore previous follow mode
			setFollowStatus(previousFollowStatus);
		}
	}

	public void setOwner(Player newOwner) {
		owner = newOwner;
	}

	@Override
	public void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
		if (miss || getOwner() == null) {
			return;
		}

		// Prevents the double spam of system messages, if the target is the owning player.
		if (target.getObjectId() != getOwner().getObjectId()) {
			if (pcrit || mcrit) {
				if (this instanceof SummonInstance) {
					getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CRITICAL_HIT_BY_SUMMONED_MOB));
				} else {
					getOwner().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CRITICAL_HIT_BY_PET));
				}
			}

			if (getOwner().isInOlympiadMode() && target instanceof Player && ((Player) target).isInOlympiadMode() &&
					((Player) target).getOlympiadGameId() == getOwner().getOlympiadGameId()) {
				OlympiadGameManager.getInstance().notifyCompetitorDamage(getOwner(), damage);
			}

			final SystemMessage sm;

			if (target.isInvul(getOwner()) && !(target instanceof NpcInstance)) {
				sm = SystemMessage.getSystemMessage(SystemMessageId.ATTACK_WAS_BLOCKED);
			} else {
				sm = SystemMessage.getSystemMessage(SystemMessageId.C1_GAVE_C2_DAMAGE_OF_S3);
				sm.addNpcName(this);
				sm.addCharName(target);
				sm.addNumber(damage);
			}

			getOwner().sendPacket(sm);
		}
	}

	@Override
	public void reduceCurrentHp(double damage, Creature attacker, Skill skill) {
		super.reduceCurrentHp(damage, attacker, skill);
		if (getOwner() != null && attacker != null) {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.C1_RECEIVED_DAMAGE_OF_S3_FROM_C2);
			sm.addNpcName(this);
			sm.addCharName(attacker);
			sm.addNumber((int) damage);
			getOwner().sendPacket(sm);
		}
	}

	@Override
	public void doCast(Skill skill) {
		final Player actingPlayer = getActingPlayer();

		if (!actingPlayer.checkPvpSkill(getTarget(), skill, true) && !actingPlayer.getAccessLevel().allowPeaceAttack()) {
			// Send a System Message to the Player
			actingPlayer.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IS_INCORRECT));

			// Send a Server->Client packet ActionFailed to the Player
			actingPlayer.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		super.doCast(skill);

		setTarget(owner.getTarget());
		if (getTarget() != null) {
			getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, getTarget());
		}
	}

	@Override
	public boolean isInCombat() {
		return getOwner() != null && getOwner().isInCombat();
	}

	@Override
	public Player getActingPlayer() {
		return getOwner();
	}

	@Override
	public final void broadcastPacket(L2GameServerPacket mov) {
		if (!isVisible()) {
			return;
		}

		if (getOwner() != null) {
			mov.setInvisibleCharacter(getOwner().getAppearance().getInvisible() ? getOwner().getObjectId() : 0);
		}

		super.broadcastPacket(mov);
	}

	@Override
	public final void broadcastPacket(L2GameServerPacket mov, int radiusInKnownlist) {
		if (!isVisible()) {
			return;
		}

		if (getOwner() != null) {
			mov.setInvisibleCharacter(getOwner().getAppearance().getInvisible() ? getOwner().getObjectId() : 0);
		}

		super.broadcastPacket(mov, radiusInKnownlist);
	}

	public void updateAndBroadcastStatus(int val) {
		if (getOwner() == null || !isVisible()) {
			return;
		}

		if (!(this instanceof CloneInstance)) {
			getOwner().sendPacket(new PetInfo(this, val));
			getOwner().sendPacket(new PetStatusUpdate(this));
		}

		if (isVisible()) {
			broadcastNpcInfo(val);
		}

		if (!(this instanceof CloneInstance)) {
			L2Party party = getOwner().getParty();
			if (party != null) {
				party.broadcastToPartyMembers(getOwner(), new ExPartyPetWindowUpdate(this));
			}
		}

		updateEffectIcons(true);
	}

	public void broadcastNpcInfo(int val) {
		Collection<Player> plrs = getKnownList().getKnownPlayers().values();
		for (Player player : plrs) {
			if (player == null || player == getOwner() && !(this instanceof MerchantSummonInstance)) {
				continue;
			}

			if (!player.isGM() && getOwner() != null && getOwner().getAppearance().getInvisible()) {
				continue;
			}

			if (this instanceof PetInstance) {
				player.sendPacket(new ExPetInfo((PetInstance) this, player, 1));
			} else if (this instanceof CloneInstance) {
				player.sendPacket(new NpcInfo((CloneInstance) this));
			} else {
				player.sendPacket(new ExSummonInfo((SummonInstance) this, player, 1));
			}
		}
	}

	public void storeEffects() {
		Connection con = null;
		try {
			con = DatabasePool.getInstance().getConnection();

			// Delete all current stored effects for char to avoid dupe
			PreparedStatement statement = con.prepareStatement("DELETE FROM character_skills_save WHERE charId=? AND class_index=-1");

			statement.setInt(1, getOwner().getObjectId());
			statement.execute();
			statement.close();

			int buff_index = 0;

			final List<Integer> storedSkills = new ArrayList<>();

			// Store all effect data along with calulated remaining
			// reuse delays for matching skills. 'restore_type'= 0.
			statement = con.prepareStatement(
					"INSERT INTO character_skills_save (charId,skill_id,skill_level,effect_count,effect_cur_time,reuse_delay,systime,restore_type,class_index,buff_index) VALUES (?,?,?,?,?,?,?,?,?,?)");

			for (Abnormal effect : getAllEffects()) {
				if (effect == null) {
					continue;
				}

				switch (effect.getType()) {
					case HEAL_OVER_TIME:
						continue;
				}

				Skill skill = effect.getSkill();
				if (storedSkills.contains(skill.getReuseHashCode())) {
					continue;
				}

				storedSkills.add(skill.getReuseHashCode());

				if (!effect.isHerbEffect() && effect.getInUse() && !skill.isToggle()) {

					statement.setInt(1, getOwner().getObjectId());
					statement.setInt(2, skill.getId());
					statement.setInt(3, skill.getLevelHash());
					statement.setInt(4, effect.getCount());
					statement.setInt(5, effect.getTime());
					statement.setInt(6, 0);
					statement.setInt(7, 0);
					statement.setInt(8, 0);
					statement.setInt(9, -1);
					statement.setInt(10, ++buff_index);
					statement.execute();
				}
			}
			statement.close();
		} catch (Exception e) {
			log.warn("Could not store summon effect data: ", e);
		} finally {
			DatabasePool.close(con);
		}
	}

	public void restoreEffects() {
		Connection con = null;

		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement;
			ResultSet rset;

			statement = con.prepareStatement(
					"SELECT skill_id,skill_level,effect_count,effect_cur_time FROM character_skills_save WHERE charId=? AND class_index=-1 ORDER BY buff_index ASC");
			statement.setInt(1, getOwner().getObjectId());

			rset = statement.executeQuery();

			while (rset.next()) {
				int effectCount = rset.getInt("effect_count");
				int effectCurTime = rset.getInt("effect_cur_time");

				final Skill skill = SkillTable.getInstance().getInfo(rset.getInt("skill_id"), rset.getInt("skill_level"));
				if (skill == null) {
					continue;
				}

				for (Abnormal effect : skill.getEffects(this, this)) {
					effect.setCount(effectCount);
					effect.setFirstTime(effectCurTime);
				}
			}

			rset.close();
			statement.close();

			statement = con.prepareStatement("DELETE FROM character_skills_save WHERE charId=? AND class_index=-1");
			statement.setInt(1, getOwner().getObjectId());
			statement.executeUpdate();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not restore " + this + " active effect data: " + e.getMessage(), e);
		} finally {
			DatabasePool.close(con);
		}
	}

	public boolean isHungry() {
		return false;
	}

	@Override
	public final boolean isAttackingNow() {
		return isInCombat();
	}

	public int getWeapon() {
		return getTemplate().RHand;
	}

	public int getArmor() {
		return 0;
	}

	@Override
	public void sendInfo(Player activeChar) {
		if (!(this instanceof CloneInstance)) {
			// Check if the Player is the owner of the Pet
			if (activeChar.equals(getOwner()) && !(this instanceof MerchantSummonInstance)) {
				activeChar.sendPacket(new PetInfo(this, 0));
				// The PetInfo packet wipes the PartySpelled (list of active  spells' icons).  Re-add them
				updateEffectIcons(true);
				if (this instanceof PetInstance) {
					activeChar.sendPacket(new PetItemList((PetInstance) this));
				}
			} else if (activeChar.isGM() || !getOwner().getAppearance().getInvisible()) {
				if (this instanceof PetInstance) {
					activeChar.sendPacket(new ExPetInfo((PetInstance) this, activeChar, 1));
				} else {
					activeChar.sendPacket(new ExSummonInfo((SummonInstance) this, activeChar, 1));
				}
			}
		} else {
			activeChar.sendPacket(new NpcInfo((CloneInstance) this));
		}
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.model.actor.Creature#onTeleported()
	 */
	@Override
	public void onTeleported() {
		super.onTeleported();
		getOwner().sendPacket(new TeleportToLocation(this,
				getPosition().getX(),
				getPosition().getY(),
				getPosition().getZ(),
				getPosition().getHeading()));
	}

	@Override
	public String toString() {
		return super.toString() + "(" + getNpcId() + ") Owner: " + getOwner();
	}

	@Override
	public boolean isUndead() {
		return getTemplate().isUndead();
	}

	public void onOwnerGotAttacked(Creature attacker) {
		if (attacker == null || getOwner() == null) {
			return;
		}

		if (getOwner().getIsSummonsInDefendingMode()) {
			if (attacker != getOwner() && !isDead()) {
				setTarget(attacker);

				if (getAI() != null) {
					getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, attacker);
				}
			}
		}
	}
}
