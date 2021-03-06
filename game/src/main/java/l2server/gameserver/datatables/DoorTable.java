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
import l2server.gameserver.idfactory.IdFactory;
import l2server.gameserver.instancemanager.ClanHallManager;
import l2server.gameserver.instancemanager.InstanceManager;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.instance.DoorInstance;
import l2server.gameserver.model.entity.ClanHall;
import l2server.gameserver.pathfinding.AbstractNodeLoc;
import l2server.gameserver.templates.StatsSet;
import l2server.gameserver.templates.chars.DoorTemplate;
import l2server.util.loader.annotations.Load;
import l2server.util.loader.annotations.Reload;
import l2server.util.xml.XmlDocument;
import l2server.util.xml.XmlNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class DoorTable {
	private static Logger log = LoggerFactory.getLogger(DoorTable.class.getName());

	private static final Map<Integer, Set<Integer>> groups = new HashMap<>();

	private final Map<Integer, DoorInstance> doors = new HashMap<>();
	private final Map<Integer, ArrayList<DoorInstance>> regions = new HashMap<>();

	public static DoorTable getInstance() {
		return SingletonHolder.instance;
	}

	private DoorTable() {
	}

	@Reload("doors")
	@Load(dependencies = {World.class, MapRegionTable.class, ClanHallManager.class})
	public void parseData() {
		doors.clear();
		regions.clear();
		groups.clear();
		File file = new File(Config.DATAPACK_ROOT, Config.DATA_FOLDER + "doorData.xml");
		XmlDocument doc = new XmlDocument(file);

		for (XmlNode d : doc.getChildren()) {
			if (d.getName().equalsIgnoreCase("door")) {
				int id = d.getInt("id");
				StatsSet set = new StatsSet();
				set.set("id", id);
				for (XmlNode bean : d.getChildren()) {
					if (bean.getName().equalsIgnoreCase("set")) {
						String name = bean.getString("name");
						String value = bean.getString("val");
						set.set(name, value);
					}
				}
				makeDoor(id, set);
			}
		}
		log.info("DoorTable: Loaded " + doors.size() + " Door Templates for " + regions.size() + " regions.");
	}

	public void insertCollisionData(StatsSet set) {
		int posX, posY, nodeX, nodeY, height;
		height = set.getInteger("height");
		String[] pos = set.getString("node1").split(",");
		nodeX = Integer.parseInt(pos[0]);
		nodeY = Integer.parseInt(pos[1]);
		pos = set.getString("node2").split(",");
		posX = Integer.parseInt(pos[0]);
		posY = Integer.parseInt(pos[1]);
		int collisionRadius; // (max) radius for movement checks
		if (Math.abs(nodeX - posX) > Math.abs(nodeY - posY)) {
			collisionRadius = Math.abs(nodeY - posY);
		} else {
			collisionRadius = Math.abs(nodeX - posX);
		}

		set.set("collisionRadius", collisionRadius);
		set.set("collisionHeight", height);
	}

	private void insertStatsData(StatsSet set) {
		set.set("STR", set.getInteger("STR", 40));
		set.set("CON", set.getInteger("CON", 40));
		set.set("DEX", set.getInteger("DEX", 40));
		set.set("INT", set.getInteger("INT", 40));
		set.set("WIT", set.getInteger("WIT", 40));
		set.set("MEN", set.getInteger("MEN", 40));
		set.set("hpMax", set.getInteger("hpMax", 40));
		set.set("cpMax", set.getInteger("cpMax", 40));
		set.set("mpMax", set.getInteger("mpMax", 40));
		set.set("hpReg", set.getInteger("hpReg", 40));
		set.set("mpReg", set.getInteger("mpReg", 40));
		set.set("pAtk", set.getInteger("pAtk", 40));
		set.set("mAtk", set.getInteger("mAtk", 40));
		set.set("pDef", set.getInteger("pDef", 40));
		set.set("mDef", set.getInteger("mDef", 40));
		set.set("pAtkSpd", set.getInteger("pAtkSpd", 40));
		set.set("mAtkSpd", set.getInteger("mAtkSpd", 40));
		set.set("shldDef", set.getInteger("shldDef", 40));
		set.set("atkRange", set.getInteger("atkRange", 40));
		set.set("shldRate", set.getInteger("shldRate", 40));
		set.set("pCritRate", set.getInteger("pCritRate", 40));
		set.set("walkSpd", set.getInteger("walkSpd", 40));
		set.set("runSpd", set.getInteger("runSpd", 40));

		if (Config.isServer(Config.TENKAI)) {
			set.set("hpMax", set.getInteger("hpMax", 40) * 30);
			set.set("cpMax", set.getInteger("cpMax", 40) * 30);
			set.set("mpMax", set.getInteger("mpMax", 40) * 30);
			set.set("hpReg", set.getInteger("hpReg", 40) * 30);
			set.set("mpReg", set.getInteger("mpReg", 40) * 30);
			set.set("pAtk", set.getInteger("pAtk", 40) * 30);
			set.set("mAtk", set.getInteger("mAtk", 40) * 30);
			set.set("pDef", set.getInteger("pDef", 40) * 30);
			set.set("mDef", set.getInteger("mDef", 40) * 30);
		}
	}

	private void makeDoor(int id, StatsSet set) {
		insertCollisionData(set);
		insertStatsData(set);
		DoorTemplate template = new DoorTemplate(set);
		DoorInstance door = new DoorInstance(IdFactory.getInstance().getNextId(), template, set);
		door.setCurrentHp(door.getMaxHp());
		door.spawnMe(template.posX, template.posY, template.posZ);
		putDoor(door, MapRegionTable.getInstance().getMapRegion(door.getX(), door.getY()));
		ClanHall clanhall = ClanHallManager.getInstance().getNearbyClanHall(door.getX(), door.getY(), 500);
		if (clanhall != null) {
			clanhall.getDoors().add(door);
			door.setClanHall(clanhall);
			//Logozo.info("door " + door.getDoorName() + " attached to ch " + clanhall.getName());
		}
	}

	public DoorTemplate getDoorTemplate(int doorId) {
		return doors.get(doorId).getTemplate();
	}

	public DoorInstance getDoor(int doorId) {
		return doors.get(doorId);
	}

	public void putDoor(DoorInstance door, int region) {
		doors.put(door.getDoorId(), door);

		if (regions.containsKey(region)) {
			regions.get(region).add(door);
		} else {
			final ArrayList<DoorInstance> list = new ArrayList<>();
			list.add(door);
			regions.put(region, list);
		}
	}

	public DoorInstance[] getDoors() {
		return doors.values().toArray(new DoorInstance[doors.values().size()]);
	}

	public boolean checkIfDoorsBetween(AbstractNodeLoc start, AbstractNodeLoc end, int instanceId) {
		return checkIfDoorsBetween(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ(), instanceId);
	}

	public boolean checkIfDoorsBetween(int x, int y, int z, int tx, int ty, int tz, int instanceId) {
		return checkIfDoorsBetween(x, y, z, tx, ty, tz, instanceId, false);
	}

	public boolean checkIfDoorsBetween(int x, int y, int z, int tx, int ty, int tz, int instanceId, boolean doubleFaceCheck) {
		ArrayList<DoorInstance> allDoors;
		if (instanceId > 0 && InstanceManager.getInstance().getInstance(instanceId) != null) {
			allDoors = InstanceManager.getInstance().getInstance(instanceId).getDoors();
		} else {
			allDoors = regions.get(MapRegionTable.getInstance().getMapRegion(x, y));
		}

		if (allDoors == null) {
			return false;
		}

		for (DoorInstance doorInst : allDoors) {
			//check dead and open
			if (doorInst.isDead() || doorInst.getOpen() || !doorInst.checkCollision() || doorInst.getX(0) == 0) {
				continue;
			}

			boolean intersectFace = false;
			for (int i = 0; i < 4; i++) {
				int j = i + 1 < 4 ? i + 1 : 0;
				// lower part of the multiplier fraction, if it is 0 we avoid an error and also know that the lines are parallel
				int denominator = (ty - y) * (doorInst.getX(i) - doorInst.getX(j)) - (tx - x) * (doorInst.getY(i) - doorInst.getY(j));
				if (denominator == 0) {
					continue;
				}

				// multipliers to the equations of the lines. If they are lower than 0 or bigger than 1, we know that segments don't intersect
				float multiplier1 = (float) ((doorInst.getX(j) - doorInst.getX(i)) * (y - doorInst.getY(i)) -
						(doorInst.getY(j) - doorInst.getY(i)) * (x - doorInst.getX(i))) / denominator;
				float multiplier2 = (float) ((tx - x) * (y - doorInst.getY(i)) - (ty - y) * (x - doorInst.getX(i))) / denominator;
				if (multiplier1 >= 0 && multiplier1 <= 1 && multiplier2 >= 0 && multiplier2 <= 1) {
					int intersectZ = Math.round(z + multiplier1 * (tz - z));
					// now checking if the resulting point is between door's min and max z
					if (intersectZ > doorInst.getZMin() && intersectZ < doorInst.getZMax()) {
						if (!doubleFaceCheck || intersectFace) {
							return true;
						}
						intersectFace = true;
					}
				}
			}
		}
		return false;
	}

	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder {
		protected static final DoorTable instance = new DoorTable();
	}

	public static void addDoorGroup(String groupName, int doorId) {
		Set<Integer> set = groups.get(groupName.hashCode());
		if (set == null) {
			set = new HashSet<>();
			set.add(doorId);
			groups.put(groupName.hashCode(), set);
		} else {
			set.add(doorId);
		}
	}

	public static Set<Integer> getDoorsByGroup(String groupName) {
		return groups.get(groupName.hashCode());
	}
}
