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

package l2server.gameserver.datatables;

import l2server.Config;
import l2server.gameserver.model.actor.instance.Player;
import l2server.util.loader.annotations.Load;
import l2server.util.xml.XmlDocument;
import l2server.util.xml.XmlNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Pere
 */
public class AbilityTable {
	private static Logger log = LoggerFactory.getLogger(AbilityTable.class.getName());


	public class Ability {
		private int type;
		private int skillId;
		private int maxLevel;
		private int reqPoints;
		private int reqSkill;
		private int reqSkillLvl;

		public Ability(int type, int skillId, int maxLevel, int reqPoints, int reqSkill, int reqSkillLvl) {
			this.type = type;
			this.skillId = skillId;
			this.maxLevel = maxLevel;
			this.reqPoints = reqPoints;
			this.reqSkill = reqSkill;
			this.reqSkillLvl = reqSkillLvl;
		}

		public int getType() {
			return type;
		}

		public int getSkillId() {
			return skillId;
		}

		public int getMaxLevel() {
			return maxLevel;
		}

		public int getReqPoints() {
			return reqPoints;
		}

		public int getReqSkill() {
			return reqSkill;
		}

		public int getReqSkillLvl() {
			return reqSkillLvl;
		}
	}

	private Map<Integer, Ability> abilities = new HashMap<>();

	private static AbilityTable instance;

	public static AbilityTable getInstance() {
		if (instance == null) {
			instance = new AbilityTable();
		}

		return instance;
	}

	private AbilityTable() {
	}

	@Load
	public void readAbilities() {
		if (Config.IS_CLASSIC) {
			return;
		}
		File file = new File(Config.DATAPACK_ROOT, Config.DATA_FOLDER + "abilities.xml");
		XmlDocument doc = new XmlDocument(file);
		for (XmlNode abilityNode : doc.getChildren()) {
			if (abilityNode.getName().equalsIgnoreCase("ability")) {
				int type = abilityNode.getInt("type");
				int skillId = abilityNode.getInt("skillId");
				int maxLevel = abilityNode.getInt("maxLevel");
				int reqPoints = abilityNode.getInt("reqPoints");

				int reqSkill = 0;
				int reqSkillLvl = 0;
				if (abilityNode.hasAttribute("reqSkill")) {
					reqSkill = abilityNode.getInt("reqSkill");
					reqSkillLvl = abilityNode.getInt("reqSkillLvl");
				}

				abilities.put(skillId, new Ability(type, skillId, maxLevel, reqPoints, reqSkill, reqSkillLvl));
			}
		}

		log.info("AbilityTable: Loaded " + abilities.size() + " abilities.");
	}

	public Ability getAbility(int skillId) {
		return abilities.get(skillId);
	}

	public int getMaxPoints() {
		return 16;
	}

	public long getAdenaCostForReset() {
		return 1;
	}

	public long getSpCostPerPoint(int curPoints) {
		if (curPoints < 4) {
			return 250000000;
		}
		if (curPoints < 8) {
			return 500000000;
		}
		if (curPoints < 12) {
			return 750000000;
		}

		return 1000000000;
	}

	public void manageHiddenSkill(Player player, int skillId, int level, boolean isAdd) {
		if (player == null) {
			return;
		}

		int hiddenSkillId = 0;
		switch (skillId) {
			//Revelation Skills level 2
			case 19124://Noble Knight Buffs
			case 19142://Noble Warrior Buffs
			case 19156://Noble Wizard Buffs
			{
				if (level == 2) {
					int[] revelationSkills = {1904, 1907, 1912, 1914, 1917, 1920, 1922, 1925, 1996, 1997};
					for (int id : revelationSkills) {
						if (player.getSkillLevelHash(id) != -1) {
							if (isAdd) {
								player.addSkill(SkillTable.getInstance().getInfo(id, player.getSkillLevelHash(id) + 1), true);
							} else {
								player.addSkill(SkillTable.getInstance().getInfo(id, 1), true);
							}
						}
					}
				}
			}

			//Hidden skills
			case 19127://Noble Guidance
			case 19172://Noble Eagle Eye
			case 19176://Noble Vision
			{
				if (level == 2) {
					if (!isAdd) {
						for (int i = 19186; i <= 19193; i++) {
							if (player.getSkillLevelHash(i) != -1) {
								player.removeSkill(i, true);
							}
						}
					} else {

						int classId = player.getClassId();
						if (classId > 146 && classId < 188) {
							classId = player.getCurrentClass().getParent().getAwakeningClassId();
						}
						switch (classId) {
							case 139://Sigel Knight
								hiddenSkillId = 19186;//Rampage Shield
								break;
							case 140://Tyrr Warrior
								hiddenSkillId = 19187;//Blockade
								break;
							case 141://Othell Rogue
								hiddenSkillId = 19188;//Snicker
								break;
							case 142://Yul Archer
								hiddenSkillId = 19189;//Retreating Shot
								break;
							case 143://Feoh Wizard
								hiddenSkillId = 19190;//Brilliant Magic
								break;
							case 144://Iss Enchanter
								hiddenSkillId = 19191;//Mist Armor
								break;
							case 145://Wynn Summoner
								hiddenSkillId = 19192;//Mass Servitor Berserk
								break;
							case 146://Aeore Healer
								hiddenSkillId = 19193;//Divine Blessing
								break;
						}

						//Ertheia case
						switch (classId) {
							case 188://Eviscerator
								hiddenSkillId = 19187;//Blockade
								break;
							case 189://Sayha's Seer
								hiddenSkillId = 19190;//Brilliant Magic
								break;
						}

						if (hiddenSkillId == 0) {
							log.warn("AbilityTable: Can't locate the hidden skill id for the class " +
									PlayerClassTable.getInstance().getClassNameById(classId) + "(" + classId + ") player " + player.getName());
						} else {
							player.addSkill(SkillTable.getInstance().getInfo(hiddenSkillId, 1), true);
						}
					}
				}
			}
			break;
		}
		player.sendSkillList();
	}
}
