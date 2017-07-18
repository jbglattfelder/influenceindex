/* This a code example of the Influence Index algorithm introduced in the publication:
 * XYZ
 * 
 * Coordinator:
 * Stefano Battiston
 * Department of Banking and Finance
 * University of Zurich 
 * Andreasstrasse 15, 8050 Zurich
 * http://www.bf.uzh.ch/
 * 
 * Author: 
 * James B. Glattfelder
 * james.glattfelder@uzh.ch
 * 
 * 2017
 *
 * Neo4j Version 2.2.5
 */

package ch.uzh.bf;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;

public class Main {

	private MyNeo4j neo;
	private DynamicConfig cfg;

	// Main method
	public static void main(String[] args) {
		Main inst = new Main();
		inst.Run();
	}

	// Instance method
	public void Run() {
		cfg = new DynamicConfig();
		neo = new MyNeo4j(cfg);

		// Neo4j infrastructure
		neo.startup();

		// Create sample bowtie network
		neo.dropDatabase();
		neo.create();

		// Find bowtie components
		neo.bowtie();

		// Basic Neo4j stuff
		neo.listAllNodesAndRels();
		Node start = neo.getNodeByName("i1");
		Node end = neo.getNodeByName("o6");
		neo.shortestPath(start, end);
		neo.listBowtieComponent("SCC");

		// Influence Index
		neo.influenceIndex();
		neo.analyticalComputation();
		neo.reset();

		// Cumulative Influence Index
		cfg.cumulativeInfluenceIndex = true;
		neo.computeCumulativeInfluenceIndex();
		neo.reset();

		//
		neo.shutdown();
	}

	// *** Inner class for dynamic config
	public class DynamicConfig {
		
		public String dbPath;
		public boolean cumulativeInfluenceIndex;
		public String cumulativeTarget;
		public  Direction out;

		public DynamicConfig () {
			// Neo4j infrastructure
			// ### Set this to where you want the database to be located at
			dbPath = "/home/username/sw/neo4j-community/data/graph.db";

			// Relations
			out = Direction.OUTGOING;
			
			// Cumulative Influence Index:
			// Set to IN section of bowtie
			cumulativeTarget = StaticConfig.BowTie.IN.toString();
		}
	}
}
