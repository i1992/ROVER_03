package rover_logic;

import common.Coord;
import common.MapTile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import enums.RoverDriveType;
import enums.Terrain;

/**
 * Created by samskim on 5/12/16.
 * Modified by MKwon on 10/5/16.
 */
public class SearchLogic {
    // ******* Search Methods

    public List<String> Astar(Coord current, Coord dest, MapTile[][] scanMapTiles, RoverDriveType drive, Map<Coord, MapTile> globalMap) {
        PriorityQueue<Node> open = new PriorityQueue<>();	// open:   The set of Nodes to be evaluated
        Set<Node> closed = new HashSet<>();					// closed: The set of Nodes already evaluated

        // for back tracing
        Map<Node, Double> distanceMemory = new HashMap<>();
        Map<Node, Node> parentMemory = new LinkedHashMap<>();

        open.add(new Node(current, 0));		// Add start coordinate as a Node object to Open list
        Node destNode = new Node(dest, 0);	// Destination Node

        // While the Open list is not empty
        Node node = null;
        while (!open.isEmpty()) {

            node = open.poll(); 	// Poll the closest node to evaluate

            // If the current node IS the destination, break
            if (node.getCoord().equals(dest)) {
                destNode = node;
                break;
            }

            // Loop through adjacent coordinates of current Node
            for (Coord coord : getAdjacentCoordinates(node.getCoord(), scanMapTiles, current)) {
                // Check 3 conditions: 
            	// 1) Has node already been visited? (is it in the closed list)
            	// 2) Does the coordinate exist on the global map?
            	// 3) Can the rover travel on this terrain?
                if (!closed.contains(new Node(coord, 0)) && globalMap.get(coord) != null && validateTile(globalMap.get(coord), drive)) {

                    // TODO: Add movement costs
                	// gCost: The distance from the starting node
                	// hCost: The distance from the end node
                	// fCost: Sum of gCost and hCost. The total heuristic of this neighbor coordinate.
                    double gCost = node.getData() + 1; // each move cost is 1, for now
                    double hCost = getDistance(coord, dest);
                    double fCost = hCost + gCost;
                    Node n = new Node(coord, fCost);	//Initialize new Node with the fCost heuristic data

                    // for back tracing, store in hashmap
                    if (distanceMemory.containsKey(n)) {

                        // if distance of this neighboring node is less than memory, update
                        // else, leave as it is
                        if (distanceMemory.get(n) > fCost) {
                            distanceMemory.put(n, fCost);
                            open.remove(n);  // also update from open list
                            open.add(n);
                            parentMemory.put(n, node); // add in parent
                        }


                    } else {
                        // if this neighbor node is new, then add to memory
                        distanceMemory.put(n, fCost);
                        parentMemory.put(n, node);
                        open.add(n);
                    }
                closed.add(node); 		// ***Put the Node into closed list, since it was evaluated
                }

            }

        }

        List<String> moves = getTrace(destNode, parentMemory);
        return moves;
    }

    private List<String> getTrace(Node dest, Map<Node, Node> parents) {
        Node backTrack = dest;
        double mindist = Double.MAX_VALUE;
        for (Node n : parents.keySet()) {
            if (n.equals(dest)) {
                backTrack = dest;
                break;
            } else {
                double distance = getDistance(dest.getCoord(), n.getCoord());
                if (distance < mindist) {
                    mindist = distance;
                    backTrack = n;
                }

            }
        }

        List<String> moves = new ArrayList<>();

        while (backTrack != null) {
            Node parent = parents.get(backTrack);
            if (parent != null) {
                int parentX = parent.getCoord().xpos;
                int parentY = parent.getCoord().ypos;
                int currentX = backTrack.getCoord().xpos;
                int currentY = backTrack.getCoord().ypos;
                if (currentX == parentX) {
                    if (parentY < currentY) {
                        moves.add(0, "S");
                    } else {
                        moves.add(0, "N");
                    }

                } else {
                    if (parentX < currentX) {
                        moves.add(0, "E");
                    } else {
                        moves.add(0, "W");
                    }
                }
            }
            backTrack = parent;

        }
        return moves;
    }

    // Returns list of adjacent coordinates
    public List<Coord> getAdjacentCoordinates(Coord coord, MapTile[][] scanMapTiles, Coord current) {
        List<Coord> list = new ArrayList<>();

        // coordinates
        int west = coord.xpos - 1;
        int east = coord.xpos + 1;
        int north = coord.ypos - 1;
        int south = coord.ypos + 1;

        Coord s = new Coord(coord.xpos, south); // S
        Coord e = new Coord(east, coord.ypos); // E
        Coord w = new Coord(west, coord.ypos); // W
        Coord n = new Coord(coord.xpos, north); // N

        list.add(e);
        list.add(w);
        list.add(s);
        list.add(n);

        return list;
    }

    public static double getDistance(Coord current, Coord dest) {
        double dx = current.xpos - dest.xpos;
        double dy = current.ypos - dest.ypos;
        return Math.sqrt((dx * dx) + (dy * dy)) * 100;
    }

    public boolean validateTile(MapTile maptile, RoverDriveType drive) {
        Terrain terrain = maptile.getTerrain();
        boolean hasRover = maptile.getHasRover();

        if (hasRover || terrain == Terrain.NONE) {
            return false;
        }

        if (terrain == Terrain.SAND) {
            if (drive == RoverDriveType.WALKER || drive == RoverDriveType.WHEELS) return false;
        }

        if (terrain == Terrain.ROCK) {
            if (drive == RoverDriveType.TREADS || drive == RoverDriveType.WHEELS) return false;
        }
        return true;
    }

    public boolean targetVisible(Coord currentLoc, Coord target){
        int dx = Math.abs(currentLoc.xpos - target.xpos);
        int dy = Math.abs(currentLoc.ypos - target.ypos);
        if (dx <= 3 && dy <= 3) return true;
        return false;
    }


}
