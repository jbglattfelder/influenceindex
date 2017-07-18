package ch.uzh.bf;

import java.util.HashMap;

// Neo4j Version 2.2.5
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.SparseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

public class MyBowtieNetwork {

	private HashMap<Integer, Integer> nameToId;
	private HashMap<Integer, Integer> idToName;

	private int numNodes;

	public MyBowtieNetwork() {
		System.out.println("### Buillding network: ");
	}

	public void build() {
		Transaction trs = MyNeo4j.gdbs.beginTx();
		nameToId  = new HashMap<Integer, Integer>();
		idToName  = new HashMap<Integer, Integer>();

		try {
			// Create nodes
			numNodes = myVals.length;
			for (int i = 0; i < numNodes; i++) {
				Node n = null;
				n = MyNeo4j.gdbs.createNode(StaticConfig.NodeLabel.MYNODE);
				int id = (int) n.getId();
				int name = i+1;
				String sName = String.valueOf(name);
				sName = customLabels(name);
				n.setProperty(StaticConfig.nodeName, sName);
				n.setProperty(StaticConfig.nodeValue, myVals[i]);
				n.setProperty(StaticConfig.nodeInfluenceIndex, 0.0);
				n.setProperty(StaticConfig.nodeSwitch, StaticConfig.NodeSwitch.OFF.toString());
				n.setProperty(StaticConfig.nodeTag, StaticConfig.NodeTag.NO.toString());
				nameToId.put(name, id);
				idToName.put(id, name);
			}

			// Create rels
			for (int row = 0; row < myRels.length; row++) {
				int from = (int) myRels[row][0];
				int to = (int) myRels[row][1];
				double w = myRels[row][2];
				Node one = MyNeo4j.gdbs.getNodeById(nameToId.get(from));
				Node two = MyNeo4j.gdbs.getNodeById(nameToId.get(to));
				Relationship rel = one.createRelationshipTo(two, StaticConfig.relType);
				rel.setProperty(StaticConfig.weight, w);
			}
			System.out.println("Created");
			trs.success();
		}
		finally {
			trs.close();
		}
	}

	private double[] myVals = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
	private double[][] myRels = {
			//{start, end, weight}
			// IN
			{1, 5, 0.5},
			{2, 5, 0.5},
			{3, 6, 1.0},
			{4, 7, 1.0},
			{5, 8, 1.0},
			{6, 9, 1.0},
			{7, 11, 0.5},
			{8, 10, 0.3333},
			{9, 10, 0.3333},
			// SCC
			{10, 11, 0.5},
			{11, 13, 1.0},
			{11, 14, 0.5},
			{12, 10, 0.3333},
			{13, 16, 1.0},
			{14, 12, 1.0},
			{14, 15, 1.0},
			{15, 17, 1.0},
			{16, 18, 0.5},
			{16, 19, 1.0},
			{17, 18, 0.5},
			{17, 21, 1.0},
			{18, 20, 1.0},
			{18, 14, 0.5},
			// OUT
			{19, 22, 0.5},
			{20, 23, 1.0},
			{21, 24, 0.5},
			{22, 25, 1.0},
			{23, 26, 1.0},
			{23, 27, 1.0},
			{4, 28, 1.0},
			{29, 22, 0.5},
			{6, 30, 1.0},
			// TT
			{30, 31, 1.0},
			{31, 32, 1.0},
			{32, 33, 1.0},
			{33, 24, 0.5},
	};

	// Label nodes
	private String customLabels(int name) {
		String sName = "";
		if (name <= 9) {
			sName = "i" + String.valueOf(name);
		}
		else if (name <= 18) {
			sName = "s" + String.valueOf(name - 9);
		}
		else if (name <= 27) {
			sName = "o" + String.valueOf(name - 18);
		}
		else if (name <= 33) {
			sName = "t" + String.valueOf(name - 27);
		}
		return sName;
	}
	
	// Getters
	public HashMap<Integer, Integer> nameToId() {
		return nameToId;
	}
	
	public HashMap<Integer, Integer> getIdToName() {
		return idToName;
	}
	
	// Matrix manipulations
	// Uses Colt library
	public DoubleMatrix1D getValueVector() {
		return new SparseDoubleMatrix1D(myVals);
	}

	public DoubleMatrix2D getAdjMatrix() {
		if (myRels == null)
			return null;
		int row = myRels.length;
		double[][] myAdj = new double[numNodes][numNodes];
		for (int i=0; i<row; i++) {
			if (myRels[i][2] > 0.0) {
				int myI = nameToId.get((int)myRels[i][0]);
				int myJ = nameToId.get((int)myRels[i][1]);
				myAdj[myI][myJ] = myRels[i][2];
			}
		}
		DoubleMatrix2D ad = new SparseDoubleMatrix2D(myAdj);
		return ad;
	}
}
