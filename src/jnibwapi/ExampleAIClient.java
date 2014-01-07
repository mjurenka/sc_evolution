package jnibwapi;

import java.awt.print.Book;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sun.org.glassfish.external.arc.Taxonomy;

import jnibwapi.model.Unit;
import jnibwapi.types.UnitType;
import jnibwapi.types.UnitType.UnitTypes;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.rule.Variable;

public class ExampleAIClient implements BWAPIEventListener {
	private JNIBWAPI bwapi;
	private int[] spawnTile0 = {11, 32};
	private int[] spawnTile1 = {48, 31};
	private Unit targetUnit;
	private FIS fuzzy;
	private int spawnCount = 2;
	
	public static void main(String[] args) {
		new ExampleAIClient();
	}

	public ExampleAIClient() {
		bwapi = new JNIBWAPI(this);
		bwapi.start();
	}
	
	public double getDistanceToTarget(Unit unit) {
		return bwapi.getMap().getGroundDistance(unit.getTileX(), unit.getTileY(), targetUnit.getTileX(), targetUnit.getTileY());
	}
	
	public Unit getClosestUnit(Unit origin, int uType) {
		List<Unit> units = new ArrayList<Unit>();
		double distance = -1;
		int closestIndex = -1;
		int i = 0;
		
		for(Unit unit : bwapi.getAllUnits()) {
			if(unit.getTypeID() == uType) {
				units.add(unit);
				double d = getDistanceToUnit(origin, unit);
				
				if(distance == -1)
					distance = d;
				if(d < distance) {
					distance = d;
					closestIndex = i;
				}
				i++;
			}
		}
		return units.get(closestIndex);
	}
	
	public Unit getClosestUnitToTargetBySpawn(int spawnNumber) {
		List<Unit> units = new ArrayList<Unit>();
		double distance = -1;
		int closestIndex = -1;
		int i = 0;

		for(Unit unit : getEnemiesNearSpawn(spawnNumber)) {
			units.add(unit);
			double d = getDistanceToUnit(targetUnit, unit);
				
			if(distance == -1)
				distance = d;
			if(d < distance) {
				distance = d;
				closestIndex = i;
			}
			i++;
		}
		return units.get(closestIndex);
		
	}
	
	public Unit getClosestEnemyToTarget() {
		return getClosestUnit(targetUnit, UnitTypes.Zerg_Zergling.getID());
	}
	
	public double getDistanceToSpawn(Unit unit, int spawnNumber) {
		int tileX = 0;
		int tileY = 0;
		
		if(spawnNumber == 0) {
			tileX = spawnTile0[0];
			tileY = spawnTile0[1];
		} else if(spawnNumber == 1) {
			tileX = spawnTile1[0];
			tileY = spawnTile1[1];
		}
		return bwapi.getMap().getGroundDistance(unit.getTileX(), unit.getTileY(), tileX, tileY);
	}
	
	public Unit getClosestEnemy(Unit unit) {
		return getClosestUnit(unit, UnitTypes.Zerg_Zergling.getID());
	}
	
	public double getDistanceToUnit(Unit start, Unit end) {
		return bwapi.getMap().getGroundDistance(
				start.getTileX(),
				start.getTileY(),
				end.getTileX(),
				end.getTileY()
				);
	}
	
	public List<Unit> getEnemiesNearSpawn(int spawnNumber) {
		List<Unit> enemies = new ArrayList<Unit>();
		for(Unit unit : bwapi.getEnemyUnits()) {
			double unitToTarget = getDistanceToTarget(unit);
			double spawnToTarget = getDistanceToSpawn(targetUnit, spawnNumber);
			if(unitToTarget <= spawnToTarget) {
				enemies.add(unit);
			}
		}
		return enemies;
	}
	
	public void orderAttack(Unit attacker, Unit victim) {
		bwapi.attack(attacker.getID(), victim.getX(), victim.getY());
	}
	
	@Override
	public void connected() {
		bwapi.loadTypeData();
		
	}

	@Override
	public void gameStarted() {
		System.out.println("Game Started");
		bwapi.enableUserInput();
		bwapi.enablePerfectInformation();
		bwapi.setGameSpeed(30); // default SC value
		bwapi.loadMapData(true);
		for (Unit unit : bwapi.getMyUnits()) {
			if(unit.getTypeID() == UnitTypes.Terran_Bunker.getID())
				targetUnit = unit;
		}
		
		// Load from 'FCL' file
        String fileName = "fuzzy/group.fcl";
        fuzzy = FIS.load(fileName,true);

        // Error while loading?
        if( fuzzy == null ) { 
            System.err.println("Can't load file: '" + fileName + "'");
            return;
        }

        
	}
	
	@Override
	public void gameUpdate() {
		
			
		
		if((bwapi.getFrameCount() % 15) == 0) {
			List<Integer> groupSize = new ArrayList<Integer>();
			int sumSize = 0;
			for(int i = 0; i < spawnCount; i++) {
				// calculate group size via fuzzy for each direction
//				System.out.println("Near " + Integer.toString(i) + ": " + Integer.toString(getEnemiesNearSpawn(i).size()));
				fuzzy.setVariable("enemyCount", getEnemiesNearSpawn(i).size());
		        fuzzy.setVariable("distanceToTarget", getDistanceToTarget(getClosestUnitToTargetBySpawn(i)));
		        fuzzy.setVariable("targetHealth", targetUnit.getHitPoints());
		        fuzzy.evaluate();
		        groupSize.add(i, (int)fuzzy.getVariable("groupSize").getValue());
		        sumSize += groupSize.get(i);
			}
//			System.out.println("0:" + Integer.toString(groupSize.get(0)));
//			System.out.println("1:" + Integer.toString(groupSize.get(1)));
				
			// add units to groups based on its size
			Map<Integer, List<Unit>> group = new HashMap<Integer, List<Unit>>();
			for(int i = 0; i < spawnCount; i++) {
				double percentage = (double)groupSize.get(i) / sumSize;

				List<Unit> myUnits = bwapi.getMyUnits();
				List<Unit> groupedUnits = new ArrayList<Unit>();
				
				int iterateTo = (int)(myUnits.size() * percentage);
				System.out.println(Integer.toString(iterateTo));
				for(int j = 0; j < iterateTo; j++) {
					if(myUnits.get(0).getTypeID() == UnitTypes.Terran_Marine.getID()) {
						groupedUnits.add(myUnits.get(0));
						myUnits.remove(0);
					} else {
						myUnits.remove(0);
					}
				}
				group.put(i, groupedUnits);
				
			}
			
			
			// move groups to spawn points
			for(int i = 0; i < spawnCount; i++) {
				for(Unit unit : group.get(i)) {
					bwapi.drawCircle(unit.getX(), unit.getY(), 10, 1, true, false);
					bwapi.attack(unit.getID(), 1000, 300);
				}
			}
			System.out.println("1:" + Integer.toString(group.get(1).size()));
		}
		
		List<Unit> defenders = new ArrayList<Unit>();
		
		
//		
		for (Unit unit : bwapi.getMyUnits()) {
			bwapi.drawCircle(unit.getX(), unit.getY(), 10, 1, true, false);
		}
//			double distanceToEnemy;
//			double distanceToTarget = getDistanceToTarget(unit);
//			double distanceToSpawn1 = getDistanceToSpawn(unit, 1);
//			double distanceToSpawn2 = getDistanceToSpawn(unit, 2);
//			
//			if(!unit.isAttacking() && !unit.isMoving()) {
//				Unit closestToTarget = getClosestEnemyToTarget();
//				
//				if(getDistanceToTarget(closestToTarget) <distanceToTarget) {
//					orderAttack(unit, targetUnit);
//				} else if(distanceToTarget > distanceToSpawn1) {
//					bwapi.attack(unit.getID(), targetUnit.getX(), targetUnit.getY());
//				} else if (distanceToTarget > distanceToSpawn2) {
//					bwapi.attack(unit.getID(), targetUnit.getX(), targetUnit.getY());
//				} else {
//					Unit closestEnemy = getClosestEnemy(unit);
//					bwapi.attack(unit.getID(), closestEnemy.getX(), closestEnemy.getY());
//				}
//
//			}
//		}		
	}

	public void gameEnded() {}
	public void keyPressed(int keyCode) {}
	public void matchEnded(boolean winner) {}
	public void sendText(String text) {}
	public void receiveText(String text) {}
	public void nukeDetect(int x, int y) {}
	public void nukeDetect() {}
	public void playerLeft(int playerID) {}
	public void unitCreate(int unitID) {}
	public void unitDestroy(int unitID) {}
	public void unitDiscover(int unitID) {}
	public void unitEvade(int unitID) {}
	public void unitHide(int unitID) {}
	public void unitMorph(int unitID) {}
	public void unitShow(int unitID) {}
	public void unitRenegade(int unitID) {}
	public void saveGame(String gameName) {}
	public void unitComplete(int unitID) {}
	public void playerDropped(int playerID) {}

}
