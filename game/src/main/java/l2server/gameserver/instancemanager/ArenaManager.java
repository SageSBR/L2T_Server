package l2server.gameserver.instancemanager;

import l2server.gameserver.instancemanager.arena.Fight;
import l2server.gameserver.instancemanager.arena.Fighter;
import l2server.gameserver.instancemanager.arena.Rule;
import l2server.gameserver.model.actor.instance.Player;

import java.util.Vector;

public class ArenaManager {
	public static Vector<Fight> arenaFights = new Vector<Fight>();

	public void createFight(Rule rules) {

		Fight fight = new Fight(rules);
		if (fight == null || arenaFights.contains(fight)) {
			return;
		}
		arenaFights.add(fight);
	}

	public void deleteFight(Fight fight) {

		for (Fight f : arenaFights) {
			if (f == null) {
				continue;
			}
			if (f == fight) {
				f.destructMe();
				arenaFights.remove(f);
			}
		}
	}

	public Fight getFight(Player player) {

		if (player == null) {
			return null;
		}
		for (Fight f : arenaFights) {
			if (f == null) {
				continue;
			}
			Vector<Fighter> fighters = f.getFighters();
			for (Fighter p : fighters) {
				if (p != null) {
					if (p.getPlayerInstance() == player) {
						return f;
					}
				}
			}
		}
		return null;
	}

	public Fight getFight(int id) {

		for (Fight f : arenaFights) {
			if (f == null) {
				continue;
			}
			if (f.getId() == id) {
				return f;
			}
		}
		return null;
	}

	;

	public boolean isInFight(Player player) {
		if (getFight(player) != null) {
			return true;
		}
		return false;
	}

	public Fighter getFighter(Player player) {

		Fight fight = getFight(player);
		if (fight == null) {
			return null;
		}
		Vector<Fighter> fighters = fight.getFighters();
		for (Fighter f : fighters) {
			if (f.getPlayerInstance() == player) {
				return f;
			}
		}
		return null;
	}

	public static ArenaManager getInstance() {
		return SingletonHolder.instance;
	}

	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder {
		protected static final ArenaManager instance = new ArenaManager();
	}
}
