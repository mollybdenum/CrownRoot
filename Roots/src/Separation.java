// Import statements

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

/**
 *  Root Separation Version 1
 *  Separation.java
 *  Purpose: Main class that separates each individual root.
 *  
 *  @param allCoords: All the voxels in a root system.
 *  @param currentCircle: The voxels currently being explored
 *  @param currentLevel: All voxels in the current cross section
 *  @param allRoots: All roots that are part of this system.
 */
public class Separation {
	ArrayList<Coord> allCoords = new ArrayList<Coord>();
	ArrayList<Coord> currentCircle = new ArrayList<Coord>();
	ArrayList<Coord> currentLevel = new ArrayList<Coord>();
	ArrayList<Root> allRoots = new ArrayList<Root>();
	ArrayList<Root> highTouchingCircs = new ArrayList<Root>();
	
	/**
	 * Read in a file with all voxels from a root system (with
	 * two lines of header).
	 * 
	 * @param fileName: Name of file 
	 * @throws FileNotFoundException
	 */
	
	
	public void readIn(String fileName) throws FileNotFoundException {
		Scanner scanner = new Scanner(new FileInputStream(fileName));
		try {
			scanner.nextLine();  // Skip line 1 (header)
			scanner.nextLine();  // Skip line 2 (header)
			while (scanner.hasNextLine()){
				process(scanner.nextLine());
			}
			Collections.sort(allCoords); //Sort allCoords by z-value
		}
		finally{
			scanner.close();
		}
	}

	/**
	 * Reads in each voxel and adds it to allCoords
	 * 
	 * @param str: input "x y z"
	 */
	private void process(String str) {
		String[] coords = str.split("\\s+");
		int x = Integer.parseInt(coords[0]);
		int y = Integer.parseInt(coords[1]);
		int z = Integer.parseInt(coords[2]);
		Coord c = new Coord (x,y,z);
		allCoords.add(c);
	}

	/**
	 * Split all voxels by z-value, then call splitIntoRoots() on 
	 * each group of coordinates.
	 */
	private void splitLevels() {
		int first = 0;
		for(int i = 1; i<allCoords.size(); i++) {
			if (allCoords.get(i).z != allCoords.get(i-1).z) {
				for(int j=first; j<i; j++) {
					currentLevel.add(allCoords.get(j));
				}
				splitIntoRoots(); // automatically splits the cross section into roots
				currentLevel.clear();
				first = i;
			}
		}
	}

	/**
	 * First, finds all blobs (circles) of connected voxels in each
	 * cross sections, then combine these blobs with previous roots.
	 */
	private void splitIntoRoots() {
		ArrayList<ArrayList<Coord>> circ = findCircles();
		combinePastRoots(circ);
	}

	/**
	 * Determine how many previous roots each blob of connected voxels
	 * touches, and either create a new root or combine with best fit.
	 * 
	 * @param circ: All blobs that were found in findCircles().
	 */
	private void combinePastRoots(ArrayList<ArrayList<Coord>> circ) {
		//		 Find how many roots each blob is touching.
		for (ArrayList<Coord> circs:circ) {
			ArrayList<Root> touchRoots = new ArrayList<Root>();
			for(Coord c1: circs) {
				for (Root r: allRoots) {
					for (Coord c2:r.lastLevel){
						if (isTouching(c1,c2)&&!touchRoots.contains(r)) {
							touchRoots.add(r);
							break;
						}
					}
				}
			}

			/*
			 * If it does not touch any previous roots, create new root.
			 */
			if(touchRoots.size() == 0) {
				allRoots.add(new Root(circs));
			}

			/*
			 * If it only touches one root, combine with that one.
			 */
			else if(touchRoots.size()==1) {
				Root t = touchRoots.remove(0);
				allRoots.remove(t);
				t.addCoords(circs);
				allRoots.add(t);
			}

			/*
			 * If it touches >1 root, compare the roots. 
			 */
			else if (touchRoots.size() >=8){ 
				Root r = new Root(circs);
				r.isSeed = true;
				allRoots.add(r);
				for(Root touch: touchRoots) {
					touch.touchseed = true;
				}
			}
			else{
				Root best = compareRoots(touchRoots, circs);
				allRoots.remove(best);
				best.addCoords(circs);
				allRoots.add(best);
			}
		}

	}

	/**
	 * Compares a variety of roots with a given blob of connected voxels
	 * in a certain z-cross section to determine which root the blob best 
	 * fits with.
	 * 
	 * @param touchRoots: All roots the touch the blob.
	 * @param circs: Voxels in the blob
	 * @return: best fit root.
	 */
	private Root compareRoots(ArrayList<Root> touchRoots, ArrayList<Coord> circs) {
		for (Root r: touchRoots) {
			if (r.isSeed) {
				for(Root touch:touchRoots) {
					touch.touchseed = true;
				}
				return r;
			}
		}

		/*
		 * Determine if one of the roots started at a very close z-value
		 * and do not choose this one (this would occur with lateral roots).
		 */
		int z = circs.get(0).z;
		ArrayList<Root> smallRoot =  new ArrayList<Root>();
		for (Root r:touchRoots) {
			if (z-r.firstZ < 10) {
				smallRoot.add(r);
			}
		}
		if (touchRoots.size()-smallRoot.size() == 1) {
			touchRoots.removeAll(smallRoot);
			return touchRoots.get(0);
		}
		else if(touchRoots.size() > smallRoot.size()) {
			touchRoots.removeAll(smallRoot);
		}

		/*
		 * If there is still more than one root that is touching, score
		 * each root based on the amount of voxels in the root that touch
		 * the voxels in the blob. Return the one with the highest score.
		 * 
		 */
		int maxScore = 0;
		ArrayList<Root> best = new ArrayList<Root>();
		for (Root r:touchRoots) {
			int rootScore = 0;
			for (Coord c1:r.lastLevel) {
				for (Coord c2:circs){
					if (isTouching(c1,c2)) {
						rootScore++;
					}
				}
			}
			if (rootScore > maxScore) {
				maxScore = rootScore;
				best.clear();
				best.add(r);
			}
			else if(rootScore == maxScore) {
				best.add(r);
			}
		}

		if(best.size() == 1) {
			return best.get(0);
		}
		/*
		 * If two roots have the same score (very unlikely) return the 
		 * larger root.
		 */
		else{
			int maxSize = 0;
			Root ro = best.get(0);
			for(Root r:best) {
				if(r.lastLevel.size() > maxSize) {
					maxSize = r.lastLevel.size();
					ro = r;
				}
			}
			return ro;
		}
	}

	/**
	 * Finds all the blobs (circles) of connected voxels at a certain z-value.
	 * @return: all blobs.
	 */
	private ArrayList<ArrayList<Coord>> findCircles() {
		ArrayList<ArrayList<Coord>> circs = new ArrayList<ArrayList<Coord>>();
		while(currentLevel.size()>0) {
			currentCircle.add(currentLevel.remove(0));
			recurseCircle(currentCircle.get(0));
			ArrayList<Coord> adds = new ArrayList<Coord>();
			for(Coord c:currentCircle){
				adds.add(c);
			}
			circs.add(adds);
			currentLevel.removeAll(currentCircle);
			currentCircle.clear();
		}
		return circs;
	}



	/**
	 * Starting from a certain voxel, add its touching voxels
	 * and then call the method on each added voxel.
	 * 
	 * @param c: input voxel
	 */
	private void recurseCircle(Coord c) {
		for(Coord c1:currentLevel) {
			if(isTouching(c,c1) && !currentCircle.contains(c1)){
				currentCircle.add(c1);
				recurseCircle(c1);
			}
		}

	}

	/**
	 * Determines if 2 voxels are touching.
	 * 
	 * @param c1: voxel 1
	 * @param c2: voxel 2
	 * @return: true if touching, false if not touching.
	 */
	private boolean isTouching(Coord c1, Coord c2) {
		if (Math.abs(c1.x-c2.x) <=2 && Math.abs(c1.y-c2.y) <=2) {
			return true;
		}
		else
			return false;
	}


	/**
	 * If there are multiple seeds, combines into one seed
	 */
	private void combineSeeds() {
		
		// Find all seeds
		ArrayList<Root> allSeeds = new ArrayList<Root>();
		for (Root r: allRoots) {
			if (r.isSeed) {
				allSeeds.add(r);
			}
		}
		
		// combine all seed voxels into one seed "root"
		ArrayList<Coord> seed = new ArrayList<Coord>();
		for (Root s: allSeeds){
			allRoots.remove(s);
			seed.addAll(s.voxels);
		}
		Root s = new Root(seed);
		s.isSeed = true;
		//allRoots.add(s); If you want seed included in list of roots, uncomment this line
	}
	
	/**
	 * Remove all roots that are not a given size (threshold)
	 * 
	 * @param threshold - cutoff number of voxels (roots with less will not be included)
	 */
	private void removeSmallRoots(int threshold) {
		ArrayList<Root> smallRoots = new ArrayList<Root>();
		for (Root r: allRoots) {
			if (r.volume() < threshold) {
				smallRoots.add(r);
			}
		}
		for (Root r: smallRoots) {
			allRoots.remove(r);
		}
	}
	
	/**
	 * Call to actually separate the roots of a file 
	 * 
	 * @param fileName - reconstruction file of root system
	 * @return ArrayList of separate roots
	 * @throws FileNotFoundException - if file does not exist
	 */
	public ArrayList<Root> separateRoots(String fileName) throws FileNotFoundException {
		//TODO: Make this a constructor so that roots are automatically separated when a Separation object is created.
		readIn(fileName);
		splitLevels();
		combineSeeds();
		removeSmallRoots(20); //Change this number to change size of "small" root
		return allRoots;
	}

}
