/*
 * $Header: WorldObjectSet.java, 22/07/2005 14:11:29 luisantonioa Exp $
 *
 * $Author: luisantonioa $
 * $Date: 22/07/2005 14:11:29 $
 * $Revision: 1 $
 * $Log: WorldObjectSet.java,v $
 * Revision 1  22/07/2005 14:11:29  luisantonioa
 * Added copyright notice
 *
 *
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

package l2server.gameserver.util;

import l2server.gameserver.model.WorldObject;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class ...
 *
 * @version $Revision: 1.2 $ $Date: 2004/06/27 08:12:59 $
 */

public class WorldObjectSet<T extends WorldObject> extends L2ObjectSet<T> {
	private Map<Integer, T> objectMap;

	public WorldObjectSet() {
		objectMap = new ConcurrentHashMap<>();
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectSet#size()
	 */
	@Override
	public int size() {
		return objectMap.size();
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectSet#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return objectMap.isEmpty();
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectSet#clear()
	 */
	@Override
	public void clear() {
		objectMap.clear();
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectSet#put(T)
	 */
	@Override
	public void put(T obj) {
		objectMap.put(obj.getObjectId(), obj);
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectSet#remove(T)
	 */
	@Override
	public void remove(T obj) {
		objectMap.remove(obj.getObjectId());
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectSet#contains(T)
	 */
	@Override
	public boolean contains(T obj) {
		return objectMap.containsKey(obj.getObjectId());
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectSet#iterator()
	 */
	@Override
	public Iterator<T> iterator() {
		return objectMap.values().iterator();
	}
}
