package rover_logic;

import java.util.*;
import common.Coord;
import common.MapTile;
import enums.RoverDriveType;
import enums.Terrain;

//*Original code implemented by Daniel Beard at
//*https://github.com/daniel-beard/DStarLiteJava/blob/master/DStarLite.java
//*Thank you!
//*Code modified for swarmBot purposes

public class DStarLite implements java.io.Serializable{

	private static final long serialVersionUID = 1L;
	
	//Private Member variables
	private List<State> path = new ArrayList<State>();
	private double C1;
	private final int OBSTACLE_COST = -1;
	private double k_m;
	private State s_start = new State();
	private State s_goal  = new State();
	private State s_last  = new State();
	private int maxSteps;
	private PriorityQueue<State> openList = new PriorityQueue<State>();
	//Change back to private****
	public HashMap<State, CellInfo>	cellHash = new HashMap<State, CellInfo>();
	private HashMap<State, Float> openHash = new HashMap<State, Float>();

	//Constants
	private double M_SQRT2 = Math.sqrt(2.0);

	//Rover-Specific considerations
	RoverDriveType rdt;

	//Default constructor
	public DStarLite()
	{
		maxSteps	= 80000; //how many steps to update the map with
		C1			= 1; //cost constant
	}
	
	public DStarLite(RoverDriveType rdt){
		maxSteps = 80000;
		C1 = 1;
		this.rdt = rdt;
	}

	//Calculate Keys
	public void CalculateKeys()
	{
			
	}

	/*
	 * Initialise Method
	 * @params start and goal coordinates
	 */
	public void init(Coord start, Coord goal)
	{
		cellHash.clear();
		path.clear();
		openHash.clear();
		while(!openList.isEmpty()) openList.poll();

		k_m = 0;

		s_start.setCoord(start);
		s_goal.setCoord(goal);

		//rhs, g, and cost to goal should all be low, or zero
		CellInfo tmp = new CellInfo();
		tmp.g   = 0;
		tmp.rhs = 0;
		tmp.cost = C1;
		//add it to hash table
		cellHash.put(s_goal, tmp);
		//the start of cell's cost and rhs value will be manhattan distance to goal. 
		//Recall, algorithm finds cell path...backwards, i.e heuristic costs should decrease moving forward. 
		tmp = new CellInfo();
		tmp.g = tmp.rhs = heuristicManhattan(s_start,s_goal);
		tmp.cost = C1;
		cellHash.put(s_start, tmp);
		s_start = calculateKey(s_start);

		s_last = s_start;

	}

	/*
	 * CalculateKey(state u)
	 * As per [S. Koenig, 2002]
	 */
	private State calculateKey(State u)
	{
		double val = Math.min(getRHS(u), getG(u));

		u.getK().setFirst (val + heuristicManhattan(u,s_start) + k_m);  
		u.getK().setSecond(val); //tie breaker when choosing nodes/states

		return u;
	}

	/*
	 * Returns the rhs value for state u.
	 */
	private double getRHS(State u)
	{
		if (u == s_goal) return 0;

		//if the cellHash doesn't contain the State u
		if (cellHash.get(u) == null)
			return heuristicManhattan(u, s_goal);
		return cellHash.get(u).rhs;
	}

	/*
	 * Returns the g value for the state u.
	 */
	private double getG(State u)
	{
		//if the cellHash doesn't contain the State u, use Manhattan distance
		if (cellHash.get(u) == null)
			return heuristicManhattan(u,s_goal);
		return cellHash.get(u).g;
	}

	/*
	 * Pretty self explanatory, the heuristic we use is the 8-way distance
	 * scaled by a constant C1 (should be set to <= min cost)
	 */
	@SuppressWarnings("unused")
	private double heuristic(State a, State b)
	{
		return eightCondist(a,b)*C1;
	}
	
	//better for four way graph system
	private double heuristicManhattan(State a, State b){
		double h = Math.abs(a.getCoord().xpos - b.getCoord().xpos) + Math.abs(a.getCoord().ypos - b.getCoord().ypos);
		return h;
	}

	/*
	 * Returns the 8-way distance between state a and state b
	 */

	private double eightCondist(State a, State b)
	{
		double temp;
		double min = Math.abs(a.getCoord().xpos - b.getCoord().xpos);
		double max = Math.abs(a.getCoord().ypos - b.getCoord().ypos);
		if (min > max)
		{
			temp = min;
			min = max;
			max = temp;
		}
		return ((M_SQRT2-1.0)*min + max);

	}
	

	public boolean replan()
	{
		path.clear();
		//like A* - finds the shortest path using priority queue - returns -1 if no path/exceeds max steps
		int res = computeShortestPath();
		if (res < 0)
		{
			System.out.println("No Path to Goal");
			return false;
		}

		LinkedList<State> n = new LinkedList<State>();
		State cur = s_start;

		if (getG(s_start) == Double.POSITIVE_INFINITY)
		{
			System.out.println("No Path to Goal");
			return false;
		}

		while (cur.neq(s_goal))
		{
			path.add(cur);
			n = new LinkedList<State>();
			n = getSucc(cur);

			if (n.isEmpty())
			{
				System.out.println("No Path to Goal");
				return false;
			}

			double cmin = Double.POSITIVE_INFINITY;
			double tmin = 0;   
			State smin = new State();

			for (State i : n)
			{
				double val  = calculateCost(cur, i); //Changed cost here to account for obstacles
				double val2 = trueDist(i,s_goal) + trueDist(s_start, i);
				val += getG(i);

				if (close(val,cmin)) {
					if (tmin > val2) {
						tmin = val2;
						cmin = val;
						smin = i;
					}
				} else if (val < cmin) {
					tmin = val2;
					cmin = val;
					smin = i;
				}
			}
			n.clear();
			cur = new State(smin);
			//cur = smin;
		}
		path.add(s_goal);
		return true;
	}

	/*
	 * As per [S. Koenig,2002] except for two main modifications:
	 * 1. We stop planning after a number of steps, 'maxsteps' we do this
	 *    because this algorithm can plan forever if the start is surrounded  by obstacles
	 * 2. We lazily remove states from the open list so we never have to iterate through it.
	 */
	private int computeShortestPath()
	{
		LinkedList<State> s = new LinkedList<State>();

		if (openList.isEmpty()) return 1;

		int k=0;
		while ((!openList.isEmpty()) &&
			   (openList.peek().lt(s_start = calculateKey(s_start))) ||
			   (getRHS(s_start) != getG(s_start))) {//inconsistent state

			if (k++ > maxSteps) {
				System.out.println("At maxsteps");
				return -1;
			}

			State u;
			//check state for inconsistency, a consistent state (no changes) --> g == rhs
			boolean test = (getRHS(s_start) != getG(s_start));

			//lazy remove
			while(true) {
				if (openList.isEmpty()) return 1;
				u = openList.poll();

				if (!isValid(u)) continue;
				//if u key is less than current node, and its consistent
				if (!(u.lt(s_start)) && (!test)) return 2;
				break;
			}

			openHash.remove(u);

			State k_old = new State(u);

			if (k_old.lt(calculateKey(u))) { //u is out of date
				insert(u);
			} else if (getG(u) > getRHS(u)) { //needs update (got better)
				setG(u,getRHS(u));
				s = getPred(u);
				for (State i : s) {
					updateVertex(i);
				}
			} else {						 // g <= rhs, state has got worse
				setG(u, Double.POSITIVE_INFINITY);
				s = getPred(u);

				for (State i : s) {
					updateVertex(i);
				}
				updateVertex(u);
			}
		} //while
		return 0;
	}

	/*
	 * Returns a list of successor states for state u, since this is an
	 * 8-way graph this list contains all of a cells neighbours. Unless
	 * the cell is occupied, in which case it has no successors.
	 */

	private LinkedList<State> getSucc(State u)
	{
		LinkedList<State> s = new LinkedList<State>();
		State tempState;

		if (occupied(u)) return s;

		//Generate the successors, starting at the immediate right,
		//Moving in a clockwise manner
		
		//EAST
		tempState = new State(u.getCoord().xpos + 1, u.getCoord().ypos, new Pair<Double, Double>(-1.0,-1.0));
		s.addFirst(tempState);
		//SOUTH
		tempState = new State(u.getCoord().xpos, u.getCoord().ypos + 1, new Pair<Double, Double>(-1.0,-1.0));
		s.addFirst(tempState);
		//WEST
		tempState = new State(u.getCoord().xpos - 1, u.getCoord().ypos, new Pair<Double, Double>(-1.0,-1.0));
		s.addFirst(tempState);
		//NORTH
		tempState = new State(u.getCoord().xpos, u.getCoord().ypos - 1, new Pair<Double, Double>(-1.0,-1.0));
		s.addFirst(tempState);

		return s;
	}

	/*
	 * Returns a list of all the predecessor states for state u. Since
	 * this is for an 4-way connected graph, the list contains all the
	 * neighbours for state u. Occupied neighbours are not added to the list
	 */
	private LinkedList<State> getPred(State u)
	{
		LinkedList<State> s = new LinkedList<State>();
		State tempState;

		//EAST
		tempState = new State(u.getCoord().xpos + 1, u.getCoord().ypos, new Pair<Double, Double>(-1.0,-1.0));
		if (!occupied(tempState)) s.addFirst(tempState);
		//SOUTH
		tempState = new State(u.getCoord().xpos, u.getCoord().ypos + 1, new Pair<Double, Double>(-1.0,-1.0));
		if (!occupied(tempState)) s.addFirst(tempState);
		//WEST
		tempState = new State(u.getCoord().xpos - 1, u.getCoord().ypos, new Pair<Double, Double>(-1.0,-1.0));
		if (!occupied(tempState)) s.addFirst(tempState);
		//NORTH
		tempState = new State(u.getCoord().xpos, u.getCoord().ypos - 1, new Pair<Double, Double>(-1.0,-1.0));
		if (!occupied(tempState)) s.addFirst(tempState);
		
		return s;
	}


	/*
	 * Update the position of the agent/robot.
	 * This does not force a replan.
	 */
	public void updateStart(int x, int y)
	{
		Coord newStart = new Coord(x,y);
		s_start.setCoord(newStart);

		//k_m += heuristic(s_last,s_start);
		k_m += heuristicManhattan(s_last, s_start);

		s_start = calculateKey(s_start);
		s_last = s_start;

	}

	/*
	 * This is somewhat of a hack, to change the position of the goal we
	 * first save all of the non-empty nodes on the map, clear the map, move the
	 * goal and add re-add all of the non-empty cells. Since most of these cells
	 * are not between the start and goal this does not seem to hurt performance
	 * too much. Also, it frees up a good deal of memory we are probably not
	 * going to use.
	 */
	//@SuppressWarnings("unchecked")
	public void updateGoal(int x, int y) //****************************************************************************
	{
		List<Pair<ipoint2, Double> > toAdd = new ArrayList<Pair<ipoint2, Double> >();
		Pair<ipoint2, Double> tempPoint;

		for (Map.Entry<State,CellInfo> entry : cellHash.entrySet()) {
			if (!close(entry.getValue().cost, C1)) {
				tempPoint = new Pair<ipoint2, Double>(
							new ipoint2(entry.getKey().getCoord().xpos, entry.getKey().getCoord().ypos),
							entry.getValue().cost);
				toAdd.add(tempPoint);
			}
		}

		cellHash.clear();
		openHash.clear();

		while(!openList.isEmpty())
			openList.poll();

		k_m = 0;

		s_goal.setCoord(new Coord(x,y));

		CellInfo tmp = new CellInfo();
		tmp.g = tmp.rhs = 0;
		tmp.cost = C1;

		cellHash.put(s_goal, tmp);
		//* Double check new cell here, should actual costs be evaluated?
		tmp = new CellInfo();
		tmp.g = tmp.rhs = heuristicManhattan(s_start, s_goal);
		tmp.cost = C1;
		cellHash.put(s_start, tmp);
		s_start = calculateKey(s_start);

		s_last = s_start;

		Iterator<Pair<ipoint2,Double> > iterator = toAdd.iterator();
		while(iterator.hasNext()) {
			tempPoint = iterator.next();
			updateCell(tempPoint.first().x, tempPoint.first().y, tempPoint.second());
		}


	}

	/*
	 * As per [S. Koenig, 2002]
	 */
	private void updateVertex(State u)
	{
		LinkedList<State> s = new LinkedList<State>();

		if (u.neq(s_goal)) {
			s = getSucc(u);
			double tmp = Double.POSITIVE_INFINITY;
			double tmp2;

			for (State i : s) {
				tmp2 = getG(i) + calculateCost(u,i); //cost now takes into account obstacles. 
				if (tmp2 < tmp) tmp = tmp2;
			}
			if (!close(getRHS(u),tmp)) setRHS(u,tmp);
		}

		if (!close(getG(u),getRHS(u))) insert(u);
	}

	/*
	 * Returns true if state u is on the open list or not by checking if
	 * it is in the hash table. -uses close, shouldn't be more than .00001 difference
	 */
	private boolean isValid(State u)
	{
		if (openHash.get(u) == null) return false;
		if (!close(keyHashCode(u),openHash.get(u))) return false;
		return true;
	}

	/*
	 * Sets the G value for state u
	 */
	private void setG(State u, double g)
	{
		makeNewCell(u);
		cellHash.get(u).g = g;
	}

	/*
	 * Sets the rhs value for state u
	 */
	private void setRHS(State u, double rhs)
	{
		makeNewCell(u);
		cellHash.get(u).rhs = rhs;
	}

	/*
	 * Checks if a cell is in the hash table, if not it adds it in.
	 * all new cells have temporary C1 costs and Manhattan g/heuristic values. 
	 */
	private void makeNewCell(State u)
	{
		if (cellHash.get(u) != null) return;
		CellInfo tmp = new CellInfo();
		tmp.g = tmp.rhs = heuristicManhattan(u,s_goal);
		tmp.cost = C1;
		cellHash.put(u, tmp);
	}

	/*
	 * updateCell as per [S. Koenig, 2002]
	 */
	public void updateCell(int x, int y, double val)
	{
		State u = new State();
		u.setCoord(new Coord(x,y));

		if ((u.eq(s_start)) || (u.eq(s_goal))) return;

		makeNewCell(u);
		cellHash.get(u).cost = val;
		updateVertex(u);
	}
	
	/*
	 * updateCell, given a mapTile to calculate new cost
	 */
	public void updateCell(Coord cellCoordinate, MapTile mt){
		State u = new State();
		u.setCoord(cellCoordinate);
		//don't update start or target cell?
		if ((u.eq(s_start)) || (u.eq(s_goal))) return;
		
		makeNewCell(u);
		if(isObstacle(mt))
			cellHash.get(u).cost = OBSTACLE_COST;
		else
			cellHash.get(u).cost = C1;
	}

	/*
	 * Inserts state u into openList and openHash
	 */
	private void insert(State u)
	{
		//iterator cur
		float csum;

		u = calculateKey(u);
		//cur = openHash.find(u);
		csum = keyHashCode(u);

		// return if cell is already in list. TODO: this should be
		// uncommented except it introduces a bug, I suspect that there is a
		// bug somewhere else and having duplicates in the openList queue
		// hides the problem...
		//if ((cur != openHash.end()) && (close(csum,cur->second))) return;

		openHash.put(u, csum);
		openList.add(u);
	}

	/*
	 * Returns the key hash code for the state u, this is used to compare
	 * a state that has been updated
	 */
	private float keyHashCode(State u)
	{
		return (float)(u.getK().first() + 1193 * u.getK().second());
	}

	/*
	 * Returns true if the cell is occupied (non-traversable), false
	 * otherwise. Non-traversable are marked with a cost < 0
	 */
	private boolean occupied(State u)
	{
		//if the cellHash does not contain the State u
		if (cellHash.get(u) == null)
			return false;
		return (cellHash.get(u).cost < 0);
	}

	/*
	 * Euclidean cost between state a and state b
	 */
	private double trueDist(State a, State b)
	{
		float x = a.getCoord().xpos - b.getCoord().xpos;
		float y = a.getCoord().ypos - b.getCoord().ypos;
		return Math.sqrt(x*x + y*y);
	}

	/*
	 * Returns the cost of moving from state a to state b. This could be
	 * either the cost of moving off state a or onto state b, we went with the
	 * former. This is also the 8-way cost.
	 */
	@SuppressWarnings("unused")
	private double cost(State a, State b)
	{
		int xd = Math.abs(a.getCoord().xpos - b.getCoord().xpos);
		int yd = Math.abs(a.getCoord().ypos - b.getCoord().ypos);
		double scale = 1;

		if (xd+yd > 1) scale = M_SQRT2;

		if (cellHash.containsKey(a)==false) return scale*C1; 
		return scale*cellHash.get(a).cost;
	}

	//this method gives the cost of moving from state a to state b
	private double calculateCost(State a, State b){ 
		double accum = 0;
		if(b == s_goal)
			return accum;
		//check  terrain type and wheel type, add 10,000 to cost if it's an obstacle, including if there's another rover there
		if(isObstacle(b.mapTile)){
			accum = OBSTACLE_COST;
		}else
			accum += C1;
		return accum;
	}

	//true if there's an obstacle i.e another rover, wrong terrain type...
	private boolean isObstacle(MapTile mt){
		boolean obstacle = false;
		if(mt.getHasRover()){
			obstacle = true;
		}else if(rdt == RoverDriveType.WHEELS){
			if (mt.getTerrain() == Terrain.SAND || mt.getTerrain() == Terrain.ROCK)
				obstacle = true;
		}else if(rdt == RoverDriveType.TREADS){
			if(mt.getTerrain() == Terrain.ROCK)
				obstacle = true;
		}else if(rdt == RoverDriveType.WALKER){
			if(mt.getTerrain() == Terrain.SAND)
				obstacle = true;
		}
		return obstacle;
	}

	/*
	 * Returns true if x and y are within 10E-5, false otherwise
	 */
	private boolean close(double x, double y)
	{
		if (x == Double.POSITIVE_INFINITY && y == Double.POSITIVE_INFINITY) return true;
		return (Math.abs(x-y) < 0.00001);
	}

	public List<State> getPath()
	{
		return path;
	}


	
	public static void main(String[] args)
	{
		DStarLite pf = new DStarLite();
		pf.init(new Coord(1,1),new Coord(40, 60));
		pf.updateCell(2, 1, -1);
		pf.updateCell(2, 0, -1);
		pf.updateCell(2, 2, -1);
		pf.updateCell(3, 0, -1);

		System.out.println("Start node: (0,1)");
		System.out.println("End node: (3,1)");

		//Time the replanning
		long begin = System.currentTimeMillis();
		pf.replan();
		pf.updateGoal(3, 2);
		long end = System.currentTimeMillis();

		System.out.println("Time: " + (end-begin) + "ms");

		List<State> path = pf.getPath();
		for (State i : path)
		{
			System.out.println("x: " + i.getCoord().xpos + " y: " + i.getCoord().ypos);
		}

	}
}

class CellInfo implements java.io.Serializable
{
	private static final long serialVersionUID = 1L;
	
	public double g=0;
	public double rhs=0;
	public double cost=0;
}

class ipoint2
{
	public int x=0;
	public int y=0;

	//default constructor
	public ipoint2()
	{

	}

	//overloaded constructor
	public ipoint2(int x, int y)
	{
		this.x = x;
		this.y = y;
	}
}
