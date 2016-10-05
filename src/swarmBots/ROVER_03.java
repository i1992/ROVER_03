package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.RoverDriveType;
import enums.Terrain;
import rover_logic.DStarLite;

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
	static final int PORT_ADDRESS = 9537;
	//Keep personal map when traversing - upload for each movement
	public static Map<Coord, MapTile> globalMap;
	String cardinals[] = {"N", "E", "S", "W"};
	List<String>equipment;
	//Rover has it's own logic class
	public static DStarLite dsl;
	

	public ROVER_03() {
		// constructor
		System.out.println("ROVER_03 rover object constructed");
		rovername = "ROVER_03";
		SERVER_ADDRESS = "localhost";
		// this should be a safe but slow timer value
		sleepTime = 300; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}
	
	public ROVER_03(String serverAddress) {
		// constructor
		System.out.println("ROVER_03 rover object constructed");
		rovername = "ROVER_03";
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException, InterruptedException {

		// Make connection to SwarmServer and initialize streams
		Socket socket = null;
		try {
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

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
	
			// ********* Rover logic setup *********
			
			String line = "";
			Coord rovergroupStartPosition = null;
			Coord targetLocation = null;
			
			/**
			 *  Get initial values that won't change
			 */
			// **** get equipment listing ****			
			equipment = new ArrayList<String>();
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");
			
			// **** Request START_LOC Location from SwarmServer ****
			out.println("START_LOC");
			line = in.readLine();
            if (line == null) {
            	System.out.println(rovername + " check connection to server");
            	line = "";
            }
			if (line.startsWith("START_LOC")) {
				rovergroupStartPosition = extractLocationFromString(line);
			}
			System.out.println(rovername + " START_LOC " + rovergroupStartPosition);
			
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			out.println("TARGET_LOC");
			line = in.readLine();
            if (line == null) {
            	System.out.println(rovername + " check connection to server");
            	line = "";
            }
			if (line.startsWith("TARGET_LOC")) {
				targetLocation = extractLocationFromString(line);
			}
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
			
			//movement logic?
			move(line, rovergroupStartPosition, targetLocation);
		
		// This catch block closes the open socket connection to the server
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println("ROVER_03 problem closing socket");
	            }
	        }
	    }

	} // END of Rover main control loop
	
	// ####################### Support Methods #############################
	
	public int getRandom(int length){
		Random random = new Random();
		return random.nextInt(length);
	}
	
	public void move(String line, Coord start, Coord target) throws Exception{

			dsl = new DStarLite(RoverDriveType.getEnum(equipment.get(0)));
			dsl.init(start, target);
		    //boolean goingForward = true;
			boolean stuck = false; // just means it did not change locations between requests,						
			boolean blocked = false;// could be velocity limit or obstruction etc.
	
			int currentDirection = getRandom(cardinals.length);
			Coord currentLoc = null;
			Coord previousLoc = null;
			int stepCount = 0;
			int stuckCount = 0;
	
			/**
			 *  ####  Rover controller process loop  ####
			 */
			while (true) {			
				// **** Request Rover Location from SwarmServer ****
				out.println("LOC");
				line = in.readLine();
	            if (line == null) {
	            	System.out.println(rovername + " check connection to server");
	            	line = "";
	            }
				if (line.startsWith("LOC")) {
					// loc = line.substring(4);
					currentLoc = extractLocationFromString(line);
					
				}
				dsl.updateStart(currentLoc);
				System.out.println(rovername + " currentLoc at start: " + currentLoc);
				// after getting location set previous equal current to be able to check for stuckness and blocked later
				previousLoc = currentLoc;		
				
				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current location
				doScan(); 
				// prints the scanMap to the Console output for debug purposes
				scanMap.debugPrintMap();
				
				// ***** get TIMER remaining *****
				checkTime(line);
				
				// ***** MOVING *****
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				//update/add new mapTiles to dsl hashMaps
				System.out.println("Updting tiles");
				updateScannedStates(scanMapTiles, currentLoc);
				//Thread.sleep(300);
				//find path from current node to goal
				dsl.replan();
				//TODO: Get path and move to new location
				//TODO: create methods to find cardinals from path...

						
//						if (scanMapTiles[centerIndex][centerIndex -1].getHasRover() 
//								|| scanMapTiles[centerIndex][centerIndex -1].getTerrain() == Terrain.ROCK
//								|| scanMapTiles[centerIndex][centerIndex -1].getTerrain() == Terrain.SAND
//								|| scanMapTiles[centerIndex][centerIndex -1].getTerrain() == Terrain.NONE) {
//							blocked = true;
//							stepCount = 5;  //side stepping
//						} else {
//							// request to server to move
//							out.println("MOVE " + cardinals[currentDirection]);
//							System.out.println("ROVER_03 request move forward  " + cardinals[currentDirection]);
//						}					
					
				
	
				// another call for current location
				out.println("LOC");
				line = in.readLine();
				if(line == null){
					System.out.println("ROVER_03 check connection to server");
					line = "";
				}
				if (line.startsWith("LOC")) {
					currentLoc = extractLocationFromString(line);
					
				}
	
	
				// test for stuckness - if stuck for too long try switching positions
				stuck = currentLoc.equals(previousLoc);
				if(stuck)
					stuckCount +=1;
				else
					stuckCount = 0;
				if(stuckCount >= 10)
					currentDirection = getRandom(cardinals.length);
				
				//System.out.println("ROVER_03 stuck test " + stuck);
				System.out.println("ROVER_03 blocked test " + blocked);
	
				// TODO - logic to calculate where to move next
	
				
				// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop
				Thread.sleep(sleepTime);
				
				System.out.println("ROVER_03 ------------ bottom process control --------------"); 
			}
	}

	
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
		int indexOf;
		indexOf = sStr.indexOf(" ");
		sStr = sStr.substring(indexOf +1);
		if (sStr.lastIndexOf(" ") != -1) {
			String xStr = sStr.substring(0, sStr.lastIndexOf(" "));
			//System.out.println("extracted xStr " + xStr);

			String yStr = sStr.substring(sStr.lastIndexOf(" ") + 1);
			//System.out.println("extracted yStr " + yStr);
			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
		}
		return null;
	}
	
	public void checkTime(String line) throws IOException{
		out.println("TIMER");
		line = in.readLine();
        if (line == null) {
        	System.out.println(rovername + " check connection to server");
        	line = "";
        }
		if (line.startsWith("TIMER")) {
			String timeRemaining = line.substring(6);
			System.out.println(rovername + " timeRemaining: " + timeRemaining);
		}
	}
	
	/*
	 * This method feeds maptils from scan to DStarLite object for updating states/nodes
	 * have to find coordinates for each tile, given that the center is current location
	 */
	public void updateScannedStates(MapTile[][] tiles, Coord current){
		int centerRow = (tiles.length-1)/2;
		int centerCol = (tiles[0].length - 1)/2;
		//System.out.println("rows: " + tiles.length + " cols: " + tiles[0].length + " centers: " + centerRow);
		for(int row = 0; row < tiles.length; row ++){
			for( int col = 0; col < tiles[0].length; col++){
					int xPos = findCoordinate(col, current.xpos, centerCol);
					int yPos = findCoordinate(row, current.ypos, centerRow);
					Coord newCoord = new Coord(xPos, yPos);
					//updateCell also adds new cells if they're not already in tables
					if(newCoord.equals(current))
						continue;
					dsl.updateCell(newCoord, tiles[col][row]);
			}
		}
		System.out.println();
	}
	
	public int findCoordinate(int n, int pivot, int centerIndex ){
		int pos;
		int diff = Math.abs(n - centerIndex);
		if(n > centerIndex)
			pos = pivot + diff;
		else if(n < centerIndex)
			pos = pivot - diff;
		else
			pos = pivot;
		//System.out.println("Calculated position: " + pos);
		return pos;
	}
	

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_03 client;
    	// if a command line argument is included it is used as the map filename
		// if present uses an IP address instead of localhost 
		
		if(!(args.length == 0)){
			client = new ROVER_03(args[0]);
		} else {
			client = new ROVER_03();
		}
		
		client.run();
	}
}