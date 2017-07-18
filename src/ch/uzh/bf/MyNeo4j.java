package ch.uzh.bf;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

// Neo4j Version 2.2.5
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpanders;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.index.ReadableIndex;
import org.neo4j.tooling.GlobalGraphOperations;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import ch.uzh.bf.Main.DynamicConfig;

public class MyNeo4j {
	
	/*
	 * Based on Neo4j 2.2.5
	 */

	public static GraphDatabaseService gdbs;

	private DynamicConfig cfg;
	private MyBowtieNetwork nw;

	public MyNeo4j(DynamicConfig cfg){
		this.cfg = cfg;
	}

	// Create
	public void create() {
		nw = new MyBowtieNetwork();
		nw.build();
	}

	// Simple stuff
	public void listAllNodesAndRels() {
		System.out.println("\n### List all nodes and rels");
		System.out.print("\t");
		Transaction tx = gdbs.beginTx();
		try{
			for (Node node : GlobalGraphOperations.at(gdbs).getAllNodes()) {
				System.out.print(node.getProperty(StaticConfig.nodeName) + "; ");
			}
			System.out.println("");
			for (Relationship rel : GlobalGraphOperations.at(gdbs).getAllRelationships()) {
				System.out.println(rel.getStartNode().getProperty(StaticConfig.nodeName) + " --[ " + rel.getProperty(StaticConfig.weight) + " ]-> " + rel.getEndNode().getProperty(StaticConfig.nodeName));
			}
			tx.success();
		}
		finally {
			tx.close();
		}
	}

	public Node getNodeByName(String name) {
		Node node = null;
		Transaction tx = gdbs.beginTx();
		try{
			ReadableIndex<Node> autoNodeIndex = gdbs.index().getNodeAutoIndexer().getAutoIndex();
			try {
				node = autoNodeIndex.get(StaticConfig.nodeName, name).getSingle();
			}
			catch (Exception e) {
				System.out.println(e.getMessage());
			}
			if (node == null) {
				System.out.println("Didn't find " + name);
			}
			tx.success();
		}
		finally {
			tx.close();
		}
		return node;
	}

	// Console output
	public void listBowtieComponent(String name) {
		System.out.println("\n### List bowtie components");
		ResourceIterator<Node> nodes = null;
		Transaction tx = gdbs.beginTx();
		try{
			ReadableIndex<Node> autoNodeIndex = gdbs.index().getNodeAutoIndexer().getAutoIndex();
			try {
				nodes = autoNodeIndex.get(StaticConfig.nodeBT, name).iterator();
			}
			catch (Exception e) {
				System.out.println(e.getMessage());
			}
			System.out.println(StaticConfig.nodeBT + " = " + name);
			System.out.print("\t");
			while (nodes.hasNext()) {
				Node n = nodes.next();
				System.out.print(n.getProperty(StaticConfig.nodeName) + "; ");
			}
			System.out.println("");
			tx.success();
		}
		finally {
			tx.close();
		}
	}

	public void shortestPath(Node start, Node end) {
		Transaction tx = gdbs.beginTx();
		try{
			System.out.println("\n### Shortest path between " + start.getProperty(StaticConfig.nodeName) + " and " + end.getProperty(StaticConfig.nodeName));

			int maxlength = 100;

			// Set up 
			PathFinder<Path> finder = GraphAlgoFactory.shortestPath(PathExpanders.forTypeAndDirection(StaticConfig.relType, StaticConfig.outDir), maxlength);
			// Find
			Iterator<Path> paths = finder.findAllPaths(start, end).iterator();
			int i = 1;
			while (paths.hasNext()) {
				Path path = paths.next();
				printPath(path, i);
				i++;
			}
			tx.success();
		}
		finally {
			tx.close();
		}
	}

	// Bowtie
	public void bowtie() {
		Transaction tx = gdbs.beginTx();
		try{
			DetectBowtie bt = new DetectBowtie();
			// SCC analysis and LCC
			ArrayList<Integer> lscc = bt.sccAnalysis();

			// Bowtie
			if (lscc.size() > 0)
				bt.bowTie(lscc, tx);
			tx.success();
		}
		finally {
			tx.close();
		}
	}

	// Influence Index
	public void influenceIndex() {
		Transaction tx = gdbs.beginTx();
		try{
			InfluenceIndex ii = new InfluenceIndex(cfg);
			ii.wrapper();
			ii.evaluate();
			tx.success();
		}
		finally {
			tx.close();
		}
	}

	// Cumulative Influence Index
	public void computeCumulativeInfluenceIndex() {
		Transaction tx = gdbs.beginTx();
		try{
			InfluenceIndex ii = new InfluenceIndex(cfg);
			ii.tag();
			ii.wrapper();
			ii.evaluateCumulative();
			tx.success();
		}
		finally {
			tx.close();
		}
	}
	
	// Analytical calculation
	// Eigenvector centrality variant with no cycle correction
	// Uses COLT library http://acs.lbl.gov/ACSSoftware/colt/
	public void analyticalComputation() {
		InfluenceIndex ii = new InfluenceIndex(cfg);
		DoubleMatrix2D adj = nw.getAdjMatrix();
		DoubleMatrix1D val = nw.getValueVector();
		ii.analyticalComp(adj, val, nw);
	}

	// Remove aux properties
	public void reset() {
		System.out.println("\n*** Reset");
		Transaction tx = gdbs.beginTx();
		try{
			for (Node n : GlobalGraphOperations.at(gdbs).getAllNodes()) {
				n.setProperty(StaticConfig.nodeInfluenceIndex, 0.0);
				n.setProperty(StaticConfig.nodeSwitch, StaticConfig.NodeSwitch.OFF.toString());
				n.setProperty(StaticConfig.nodeTag, StaticConfig.NodeTag.NO.toString());
			}
			tx.success();
		}
		finally {
			tx.close();
		}
	}

	// Drop database
	public void dropDatabase() {
		System.out.println("Dropping db in " + cfg.dbPath);
		shutdown();
		dropDatabase(new File(cfg.dbPath));
		startup();
	}

	private void dropDatabase(final File file) {
		if (file.exists()) {
			if (file.isDirectory()) {
				for (File child : file.listFiles()) {
					dropDatabase(child);
				}
			}
			file.delete();
		}
	}

	// Console output
	private void printPath(Path myLinks, int no) {
		System.out.println("Path " + no + ":");
		Iterator<Relationship> rels = myLinks.relationships().iterator();
		while (rels.hasNext()) {
			Relationship rel = rels.next();
			System.out.println(rel.getStartNode().getProperty(StaticConfig.nodeName) + " --[ " + rel.getProperty(StaticConfig.weight) + " ]-> " + rel.getEndNode().getProperty(StaticConfig.nodeName));
		}
	}

	// Set up Neo4j database
	// Version 2.2.5
	public void startup() {
		System.out.println("*** Starting on " + cfg.dbPath + "\n");
		String i1 = StaticConfig.nodeBT;
		String i2 = StaticConfig.nodeName;
		String myIndeables = i1 + "," + i2; 
		gdbs = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(cfg.dbPath).
				setConfig(GraphDatabaseSettings.node_keys_indexable, myIndeables).
				setConfig(GraphDatabaseSettings.node_auto_indexing, "true").
				setConfig(GraphDatabaseSettings.cache_type, "none").
				setConfig(GraphDatabaseSettings.keep_logical_logs, "false").
				newGraphDatabase();
	}

	public void shutdown() {
		gdbs.shutdown();
		System.out.println("*** Shutdown");
	}
}
