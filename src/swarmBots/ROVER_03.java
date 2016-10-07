package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

import common.Communication;
import common.Coord;
import common.MapTile;
import common.PlanetMap;
import common.ScanMap;
import enums.Terrain;
import enums.RoverToolType;
import enums.Science;
import enums.RoverDriveType;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_03 {

	BufferedReader in;
	PrintWriter out;
	String rovername;
	ScanMap scanMap;
	int sleepTime;
	String SERVER_ADDRESS = "localhost";
	static final int SWARM_SERVER_PORT_ADDRESS = 9537;

	ArrayList<String> radioactiveLocations = new ArrayList<String>();

	public ROVER_03() {
		System.out.println("ROVER_03 rover object constructed");
		rovername = "ROVER_03";
		SERVER_ADDRESS = "localhost";
		// in milliseconds - smaller is faster, but the server will cut connection if too small
		sleepTime = 300; 
	}
	
	public ROVER_03(String serverAddress) {
		// constructor
		System.out.println("ROVER_03 rover object constructed");
		rovername = "ROVER_03";
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}

	/**
	 * Connects to the swarm server then enters the processing loop.
	 */
	private void run() throws IOException, InterruptedException {

		
	Socket socket = new Socket(SERVER_ADDRESS, SWARM_SERVER_PORT_ADDRESS);
		
	in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		
	out = new PrintWriter(socket.getOutputStream(), true);
	// Process all messages from server, wait until server requests Rover ID
	// name - Return Rover Name to complete connection
			
		while (true) {
			String line = in.readLine();
			if (line.startsWith("SUBMITNAME")) {
				out.println(rovername); // This sets the name of this instance
										// of a swarmBot for identifying the
										// thread to the server
				break;
			}
		}
		
		
		out.println("TARGET_LOC");
		String line = in.readLine();
		Coord targetLoc = null;
		if (line.startsWith("TARGET_LOC")) {
			targetLoc = extractLocationFromString(line);
//			targetLoc = new Coord(12, 18);
		}
		
		out.println("LOC");
		line = in.readLine();
		if (line == null) {
			System.out.println("ROVER_03 check connection to server");
			line = "";
		}
		// Get start loc.
		out.println("START_LOC");
		line = in.readLine();
		Coord startLoc = null;
		
		if (line.startsWith("START_LOC")) {
			startLoc = extractLocationFromString(line);			
		}
		
		Astar astar = new Astar(1000, 1000, startLoc, targetLoc);
		
		Coord currentLoc = null;
		boolean destReached = false;
		char dir = ' ';
		int counter = 1;

		char[] dirs = new char[]
				
				{'N', 'E', 'S', 'W'};
		
		Random rand = new Random();
		
		while (true) {
			
			// **** location call ****
			out.println("LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println("ROVER_03 check connection to server");
				line = "";
			}
			if (line.startsWith("LOC")) {
				currentLoc = extractLocationFromString(line);
			}
			
			if (currentLoc.equals(targetLoc)) {
				destReached = true;
			}
			
			System.out.println("Current Loc: " + currentLoc.toString());

			// **** get equipment listing ****
			ArrayList<String> equipment = new ArrayList<String>();
			equipment = getEquipment();
			System.out.println("ROVER_03 equipment list results " + equipment + "\n");

			this.doScan();
			astar.addScanMap(scanMap, currentLoc, RoverToolType.RADIATION_SENSOR, RoverToolType.RANGE_BOOTER); // this																										
			astar.debugPrintRevealCounts(currentLoc, RoverToolType.RADIATION_SENSOR, RoverToolType.RANGE_BOOTER);
			scanMap.debugPrintMap();
			
			//codition if reached
			//implement findpath
			
			if (!destReached) {
				dir = astar.findPath(currentLoc, targetLoc, RoverDriveType.WHEELS);
			} else {
				if (counter % 20 == 0) {
					List<String> dirsCons = new ArrayList<>();
					char dirOpposite = getOpposite(dir);
					for (int i = 0; i < dirs.length; i++) {
						if (dirs[i] != dirOpposite) {
							dirsCons.add(String.valueOf(dirs[i]));
						}
					}
					dir = dirsCons.get(rand.nextInt(3)).charAt(0);					
				}
				counter++;
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				int centerIndex = (scanMap.getEdgeSize() - 1) / 2;
				System.out.println(dir);
				switch (dir) {
				case 'N':
					if (northBlocked(scanMapTiles, centerIndex)) {
						dir = resolveNorth(scanMapTiles, centerIndex);
					}
					break;
				case 'S':
					if (southBlocked(scanMapTiles, centerIndex)) {
						dir = resolveSouth(scanMapTiles, centerIndex);
					}
					break;
				case 'E':
					System.out.println("E");
					if (eastBlocked(scanMapTiles, centerIndex)) {
						dir = resolveEast(scanMapTiles, centerIndex);
					}
					break;
				case 'W':
					System.out.println("W");
					if (westBlocked(scanMapTiles, centerIndex)) {
						dir = resolveWest(scanMapTiles, centerIndex);
					}
					break;
				}
				System.out.println("Going: " + dir);
			}
			if (dir != 'U') {
				out.println("MOVE " + dir);
			}
				
			Thread.sleep(sleepTime);
			System.out.println("ROVER_03 ------------ bottom process control --------------");
		}
	}
	
	public char getOpposite(char dir) {
		char opposite = ' ';
		switch (dir) {
		case 'N':
			opposite = 'S';
			break;
		case 'S':
			opposite = 'N';
			break;
		case 'E':
			opposite = 'W';
			break;
		case 'W':
			opposite = 'E';
			break;
		}
		System.out.println("Opposite of " + dir + " is " + opposite);
		return opposite;
	}
	
	
	
	//for north
	
	public char resolveNorth(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "N";
		if (!eastBlocked(scanMapTiles, centerIndex))
			currentDir = "E";
		else if (!westBlocked(scanMapTiles, centerIndex))
			currentDir = "W";
		else
			currentDir = "S";
		return currentDir.charAt(0);
	}
	//for south

	public char resolveSouth(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "S";
		if (!westBlocked(scanMapTiles, centerIndex))
			currentDir = "W";
		else if (!eastBlocked(scanMapTiles, centerIndex))
			currentDir = "E";
		else {
			currentDir = "N";
		}
		return currentDir.charAt(0);
	}

	//east
	public char resolveEast(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "E";
		if (!southBlocked(scanMapTiles, centerIndex))
			currentDir = "S";
		else if (!northBlocked(scanMapTiles, centerIndex))
			currentDir = "N";
		else
			currentDir = "W";
		return currentDir.charAt(0);
	}

	//west
	public char resolveWest(MapTile[][] scanMapTiles, int centerIndex) {
		String currentDir = "W";
		if (!northBlocked(scanMapTiles, centerIndex))
			currentDir = "N";
		else if (!southBlocked(scanMapTiles, centerIndex))
			currentDir = "S";
		else
			currentDir = "E";
		return currentDir.charAt(0);
	}
	
	
	//for northblocked
	
	public boolean northBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex][centerIndex - 1].getHasRover()
				|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.SAND);
	}

	
	//for southblocked
	
	public boolean southBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex][centerIndex + 1].getHasRover()
				|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.SAND);
	}

	//for eastblocked
	
	public boolean eastBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex + 1][centerIndex].getHasRover()
				|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.SAND);
	}

	//for westblocked
	
	
	public boolean westBlocked(MapTile[][] scanMapTiles, int centerIndex) {
		return (scanMapTiles[centerIndex - 1][centerIndex].getHasRover()
				|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.ROCK
				|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.NONE
				|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.SAND);
	}
	

	
	// ####################### Support Methods #############################
	
	
	
	private void clearReadLineBuffer() throws IOException{
		while(in.ready()){
			//System.out.println("ROVER_03 clearing readLine()");
			in.readLine();	
		}
	}
	

	// method to retrieve a list of the rover's EQUIPMENT from the server
	private ArrayList<String> getEquipment() throws IOException {
		//System.out.println("ROVER_03 method getEquipment()");
		Gson gson = new GsonBuilder()
    			.setPrettyPrinting()
    			.enableComplexMapKeySerialization()
    			.create();
		out.println("EQUIPMENT");
		
		String jsonEqListIn = in.readLine(); //grabs the string that was returned first
		if(jsonEqListIn == null){
			jsonEqListIn = "";
		}
		StringBuilder jsonEqList = new StringBuilder();
		//System.out.println("ROVER_03 incomming EQUIPMENT result - first readline: " + jsonEqListIn);
		
		if(jsonEqListIn.startsWith("EQUIPMENT")){
			while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
				if(jsonEqListIn == null){
					break;
				}
				//System.out.println("ROVER_03 incomming EQUIPMENT result: " + jsonEqListIn);
				jsonEqList.append(jsonEqListIn);
				jsonEqList.append("\n");
				//System.out.println("ROVER_03 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return null; // server response did not start with "EQUIPMENT"
		}
		
		String jsonEqListString = jsonEqList.toString();		
		ArrayList<String> returnList;		
		returnList = gson.fromJson(jsonEqListString, new TypeToken<ArrayList<String>>(){}.getType());		
		//System.out.println("ROVER_03 returnList " + returnList);
		
		return returnList;
	}
	

	// sends a SCAN request to the server and puts the result in the scanMap array
	public void doScan() throws IOException {
		//System.out.println("ROVER_03 method doScan()");
		Gson gson = new GsonBuilder()
    			.setPrettyPrinting()
    			.enableComplexMapKeySerialization()
    			.create();
		out.println("SCAN");

		String jsonScanMapIn = in.readLine(); //grabs the string that was returned first
		if(jsonScanMapIn == null){
			System.out.println("ROVER_03 check connection to server");
			jsonScanMapIn = "";
		}
		StringBuilder jsonScanMap = new StringBuilder();
		System.out.println("ROVER_03 incomming SCAN result - first readline: " + jsonScanMapIn);
		
		if(jsonScanMapIn.startsWith("SCAN")){	
			while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
				//System.out.println("ROVER_03 incomming SCAN result: " + jsonScanMapIn);
				jsonScanMap.append(jsonScanMapIn);
				jsonScanMap.append("\n");
				//System.out.println("ROVER_03 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return; // server response did not start with "SCAN"
		}
		//System.out.println("ROVER_03 finished scan while");

		String jsonScanMapString = jsonScanMap.toString();
		// debug print json object to a file
		//new MyWriter( jsonScanMapString, 0);  //gives a strange result - prints the \n instead of newline character in the file

		//System.out.println("ROVER_03 convert from json back to ScanMap class");
		// convert from the json string back to a ScanMap object
		scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);		
	}
	

	// this takes the server response string, parses out the x and x values and
	// returns a Coord object
	public static Coord extractLocationFromString(String sStr) {
		String[] subStrs = sStr.split(" ");
		if (subStrs.length > 2) {
			String xStr = subStrs[subStrs.length - 2];
			// System.out.println("extracted xStr " + xStr);

			String yStr = subStrs[subStrs.length - 1];
			// System.out.println("extracted yStr " + yStr);
			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
		}
		return null;
	}

	

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_03 client = new ROVER_03();
		
	client.run();
		
	}
}