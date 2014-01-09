package jnibwapi;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import javax.swing.text.AbstractDocument.Content;

import org.apache.commons.io.FileUtils;

import net.sourceforge.jFuzzyLogic.FIS;
import jnibwapi.model.Unit;
import jnibwapi.types.UnitType;
import jnibwapi.types.UnitType.UnitTypes;
import jnibwapi.util.BWColor;

public class ScEvoBot implements BWAPIEventListener {
	private JNIBWAPI bwapi;
	private int[] spawnTile0 = {11, 32};
	private int[] spawnTile1 = {48, 31};
	private int[] spawn0 = {250, 1025};
	private int[] spawn1 = {1665, 1025};
	private Unit targetUnit;
	private FIS fuzzy;
	private FIS individual;
	private int spawnCount = 2;
	private int gameSpeed = 0;
	private List<Unit> groupedUnits1 = new ArrayList<Unit>();
	private List<Unit> groupedUnits2 = new ArrayList<Unit>();
	private List<Integer> groupSize = new ArrayList<Integer>();
	private boolean areParametersSet = false;
	private ArrayList<Integer> parameters = new ArrayList<Integer>();
	private int fitness = 0;
	private int currentIndex = 0;
	private Random random = new Random();
	
	private ArrayList<ArrayList<Integer>> population = new ArrayList<ArrayList<Integer>>();
	private ArrayList<Integer> evaluated = new ArrayList<Integer>();
	
	public static void main(String[] args) {
		ScEvoBot bot = new ScEvoBot();
		bot.setParameters(bot.parseParametersFromString(args[0]));
		bot.start();
	}
	
	public ScEvoBot() {
		this.setDefaultParameters();
		this.start();
	}
	
	public void setParameters(ArrayList<Integer> par) {
		this.parameters = par;
		this.areParametersSet = true;
	}
	
	public void start() {
		parameterToString();		
		bwapi = new JNIBWAPI(this);
		bwapi.start();
	}
	
	private String parameterToString() {
		String out = "";
		for(Integer i : this.parameters) {
			out += Integer.toString(i) + ",";
		}
		out = out.substring(0, out.length()-1);
		System.out.println(out.toString());
		return out.toString();
	}

	private ArrayList<Integer> parseParametersFromString(String parametersString) {
		ArrayList<Integer> p = new ArrayList<Integer>();
		for(String s : parametersString.split(",") ) {
			p.add(Integer.parseInt(s));
		}
		return p;
	}
	
//	private void loadParameters() throws IOException {
//		String parametersString = readFile("parameters.txt", Charset.defaultCharset());
//		this.parameters = this.parseParametersFromString(parametersString);
//	}
	
//	public void saveEvolutionData() throws IOException {
//		PrintWriter out = new PrintWriter("fitness.txt");
//		out.println(parameterToString());
//		out.print("\n" + fitness);
//		out.close();
//	}
	
	private InputStream loadFuzzy(String filename, int part) {
        String fName = "fuzzy/" + filename;
        String fileContent = "";
        try {
			fileContent = readFile(fName, Charset.defaultCharset());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        int start, end = 0;
        if(part == 0) {
        	start = 0;
        	end = 33;
        } else {
        	start = 33;
        	end = 69;
        }
        for(int i = start; i < end; i++) {
        	String newContent = fileContent.replaceFirst(Pattern.quote("ARG" + Integer.toString(i)), Integer.toString(this.parameters.get(i)));
        	fileContent = newContent;
        }

//		System.out.print(fileContent);
        return new ByteArrayInputStream( fileContent.getBytes( Charset.defaultCharset() ) );
	}
	
	@Override
	public void gameStarted() {
		
		System.out.println("Population:" + Integer.toString(this.population.size()));
		bwapi.enableUserInput();
		bwapi.enablePerfectInformation();
		bwapi.setGameSpeed(this.gameSpeed);
		bwapi.loadMapData(true);
		for (Unit unit : bwapi.getMyUnits()) {
			if(unit.getTypeID() == UnitTypes.Terran_Bunker.getID())
				targetUnit = unit;
		}
		
		InputStream groupStream = this.loadFuzzy("group-template.fcl", 0);
		InputStream indiStream = this.loadFuzzy("indi-template.fcl", 1);
		fuzzy = FIS.load(groupStream,false);
		individual = FIS.load(indiStream,false);
		
        // Error while loading?
        if( fuzzy == null || individual == null) { 
            System.err.println("Can't load fuzzy system");
            return;
        }
                
	}
	
	public double getDistanceToTarget(Unit unit) {
		return bwapi.getMap().getGroundDistance(unit.getTileX(), unit.getTileY(), targetUnit.getTileX(), targetUnit.getTileY());
	}
	
	public int countMyUnits() {
		int c = 0;
		for (Unit unit : bwapi.getMyUnits()) {
			if(unit.getTypeID() == UnitTypes.Terran_Marine.getID()) {
				c++;
			}
		}
		return c;
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
	
	public Unit getClosestEnemyUnitToTargetBySpawn(int spawnNumber) {
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
	
	public Unit getClosestEnemyUnitBySpawn(Unit unit, int spawnNumber) {
		List<Unit> units = new ArrayList<Unit>();
		double distance = -1;
		int closestIndex = -1;
		int i = 0;
		for(Unit enemyUnit : getEnemiesNearSpawn(spawnNumber)) {
			units.add(unit);
			double d = getDistanceToUnit(unit, enemyUnit);
				
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
	
	public void attackSpawn(Unit attacker, int spawnNumber) {
		if(spawnNumber == 0) {
			bwapi.attack(attacker.getID(), this.spawn0[0], this.spawn0[1]);
		} else if(spawnNumber == 1) {
			bwapi.attack(attacker.getID(), this.spawn1[0], this.spawn1[1]);
		}
	}
	
	public void goToDefaultLocation(Unit unit, int spawnNumber) {
		if(spawnNumber == 0) {
			bwapi.move(unit.getID(), 863, 1025);
		} else {
			bwapi.move(unit.getID(), 1024, 1025);
		}
	}
	
	@Override
	public void connected() {
		bwapi.loadTypeData();
	}

	public void takeAction(Unit unit, int action, int spawnNumber) {
		if(action == 1) { 
			// frontLines
			Unit enemyUnit = this.getClosestEnemyUnitBySpawn(unit, spawnNumber);
			bwapi.attack(unit.getID(), enemyUnit.getX(), enemyUnit.getY());		
		} else if (action == 2) { 
			// holdPosition
			Unit enemy = this.getClosestEnemy(unit);
			bwapi.attack(unit.getID(), enemy.getX(), enemy.getY());	
		} else if (action == 3) { 
			// keepBack
			Unit enemy = this.getClosestEnemyUnitToTargetBySpawn(spawnNumber);
			bwapi.attack(unit.getID(), enemy.getX(), enemy.getY());
		} else if (action == 4) { 
			// defend
			bwapi.move(unit.getID(), targetUnit.getX(), targetUnit.getY());
		} else {
			bwapi.stop(unit.getID());
		}
		
	}
		
	@Override
	public void gameUpdate() {
		this.fitness = bwapi.getFrameCount();
		int sumSize = 0;
		if(bwapi.getFrameCount() % 10 == 0) {
			List<Integer> newSize = new ArrayList<Integer>();
			for(int i = 0; i < spawnCount; i++) {
				// calculate group size via fuzzy for each direction
				fuzzy.setVariable("enemyCount", getEnemiesNearSpawn(i).size());
		        fuzzy.setVariable("distanceToTarget", getDistanceToTarget(getClosestEnemyUnitToTargetBySpawn(i)));
		        fuzzy.setVariable("targetHealth", targetUnit.getHitPoints());
		        fuzzy.evaluate();
		        newSize.add(i, (int)fuzzy.getVariable("groupSize").getValue());
		        sumSize += newSize.get(i);
			}
			
			
//			System.out.println("S0:" + Integer.toString(newSize.get(0)) + " S1:" + Integer.toString(newSize.get(1)));
			if(newSize != groupSize) {
				groupSize = newSize;
				// add units to groups based on its size
				// reset first
				groupedUnits1.clear();
				groupedUnits2.clear();
				for(int i = 0; i < spawnCount; i++) {
					double percentage = (double)groupSize.get(i) / sumSize;
					List<Unit> myUnits = bwapi.getMyUnits();
					int iterateTo = (int)(myUnits.size() * percentage);
					int iterator = 0;
					for (Unit unit : bwapi.getMyUnits()) {
						if(iterator < iterateTo) {
							if(unit.getTypeID() == UnitTypes.Terran_Marine.getID()
									&& !groupedUnits1.contains(unit)
									&& !groupedUnits2.contains(unit)) {
								if(i == 0) {
									groupedUnits1.add(unit);
								} else {
									groupedUnits2.add(unit);
								}
								iterator++;
							}
						} else {
							break;
						}
					}
				}
			}
		}
		
		if(bwapi.getFrameCount() % 2 == 0) {
			for (Unit unit : groupedUnits1) {
//				bwapi.drawCircle(unit.getX(), unit.getY(), 10, BWColor.CYAN, true, false);
				individual.setVariable("nearestEnemyDistance", this.getDistanceToUnit(unit, this.getClosestEnemyUnitBySpawn(unit, 0)));
				individual.setVariable("selfHealth", unit.getHitPoints());
				individual.setVariable("targetHealth", targetUnit.getHitPoints());
				individual.evaluate();
				int actionToTake = (int)individual.getVariable("actionToTake").getValue();
				this.takeAction(unit, actionToTake, 0);
				bwapi.drawText(unit.getX(), unit.getY(), Integer.toString(actionToTake), false);
			}
		}
		
		if(bwapi.getFrameCount() % 2 == 1) {
			for (Unit unit : groupedUnits2) {
//				bwapi.drawCircle(unit.getX(), unit.getY(), 10, BWColor.BLUE, true, false);
				individual.setVariable("nearestEnemyDistance", this.getDistanceToUnit(unit, this.getClosestEnemyUnitBySpawn(unit, 1)));
				individual.setVariable("selfHealth", unit.getHitPoints());
				individual.setVariable("targetHealth", targetUnit.getHitPoints());
				individual.evaluate();
				int actionToTake = (int)individual.getVariable("actionToTake").getValue();
				this.takeAction(unit, actionToTake, 1);
				bwapi.drawText(unit.getX(), unit.getY(), Integer.toString(actionToTake), false);
			}
		}
	}
	
	static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return encoding.decode(ByteBuffer.wrap(encoded)).toString();
	}
	
	public void setDefaultParameters() {
		ArrayList<Integer> p = new ArrayList<Integer>();
		p.add(0);
		p.add(100);
		p.add(90);
		p.add(130);
		p.add(170);
		p.add(200);
		p.add(190);
		p.add(300);
		p.add(0);
		p.add(35);
		p.add(30);
		p.add(35);
		p.add(60);
		p.add(65);
		p.add(60);
		p.add(100);
		p.add(0);
		p.add(100);
		p.add(90);
		p.add(130);
		p.add(170);
		p.add(200);
		p.add(190);
		p.add(300);
		p.add(0);
		p.add(17);
		p.add(34);
		p.add(17);
		p.add(25);
		p.add(66);
		p.add(25);
		p.add(38);
		p.add(100);
		p.add(0);
		p.add(100);
		p.add(80);
		p.add(90);
		p.add(210);
		p.add(220);
		p.add(200);
		p.add(320);
		p.add(0);
		p.add(35);
		p.add(30);
		p.add(35);
		p.add(60);
		p.add(65);
		p.add(60);
		p.add(100);
		p.add(0);
		p.add(2);
		p.add(1);
		p.add(2);
		p.add(3);
		p.add(4);
		p.add(4);
		p.add(5);
		p.add(0);
		p.add(1);
		p.add(2);
		p.add(1);
		p.add(2);
		p.add(3);
		p.add(2);
		p.add(3);
		p.add(4);
		p.add(3);
		p.add(4);
		p.add(5);
		this.parameters = p;
		this.areParametersSet = true;
	}

	public void gameEnded() {
		System.exit(this.fitness);
	}
	
	public void keyPressed(int keyCode) {
		bwapi.leaveGame();
	}
	public void matchEnded(boolean winner) {
		
		this.evaluated.set(currentIndex, this.fitness);
		
		
		
	}
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
