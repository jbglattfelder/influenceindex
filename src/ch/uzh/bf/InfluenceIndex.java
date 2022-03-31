package ch.uzh.bf;

import java.text.DecimalFormat;

// Neo4j Version 2.2.5
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.tooling.GlobalGraphOperations;

import cern.colt.function.DoubleDoubleFunction;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.SparseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import ch.uzh.bf.Main.DynamicConfig;

public class InfluenceIndex {

	private DynamicConfig cfg;
	private DecimalFormat myFormatter;

	public InfluenceIndex(DynamicConfig cfg) {
		myFormatter = new DecimalFormat("###.##########");
		this.cfg = cfg;
	}

	// *** Output results to console
	// Influence Index
	public void evaluate() {
		System.out.println("\n### Influence Index (by node name)");
		double ii = 0.0;
		for (Node node : GlobalGraphOperations.at(MyNeo4j.gdbs).getAllNodes()) {
			ii += (double) node.getProperty(StaticConfig.nodeInfluenceIndex);
			System.out.print(node.getProperty(StaticConfig.nodeName) + ": " + myFormatter.format(node.getProperty(StaticConfig.nodeInfluenceIndex)) + "; ");
		}
		System.out.println("\nTotal Influence Index value:\t" + ii);
	}

	// Cumulative Influence Index
	// Note this will be one value representing the cumulative Influence Index
	// for a certain set of tagged nodes; this value will be assigned to all
	// tagged nodes
	public void evaluateCumulative() {
		System.out.println("\n### Cumulative Influence Index");
		double ii = 0.0;
		int cnt = 0;
		double val = 0.0;
		for (Node node : GlobalGraphOperations.at(MyNeo4j.gdbs).getAllNodes()) {
			val += (double) node.getProperty(StaticConfig.nodeValue);
			if (node.getProperty(StaticConfig.nodeTag).equals(StaticConfig.NodeTag.YES.toString())) {
				double nii = (double) node.getProperty(StaticConfig.nodeInfluenceIndex);
				if (nii > 0) {
					ii += (double) node.getProperty(StaticConfig.nodeInfluenceIndex);
					node.setProperty(StaticConfig.nodeTag, StaticConfig.NodeTag.NO.toString());
				}
				cnt++;
			}
		}
		System.out.println("For the set of " + cnt + " " + cfg.cumulativeTarget + " nodes: " + ii + "\t" + ii/val*100.0 + "%");
	}

	// *** Tag nodes for cumulative Influence Index
	public void tag() {
		System.out.println("\n### Tagging");
		int cnt = 0;
		for (Node sink : GlobalGraphOperations.at(MyNeo4j.gdbs).getAllNodes()) {
			if (sink.getProperty(StaticConfig.nodeBT).equals(cfg.cumulativeTarget.toString())) {
				sink.setProperty(StaticConfig.nodeTag, StaticConfig.NodeTag.YES.toString());
				cnt++;
			}
		}
		System.out.println("Restrict to " + cfg.cumulativeTarget + " nodes, found " + cnt);
	}

	// *** Main Influence Index code
	// See Algorithm 1 and 2 in Supplementary Materials of publication
	public void wrapper() {
		// Loop through whole graph
		for (Node n : GlobalGraphOperations.at(MyNeo4j.gdbs).getAllNodes()) {

			// ### Cumulative
			if (cfg.cumulativeInfluenceIndex) {
				if (n.getProperty(StaticConfig.nodeTag).equals(StaticConfig.NodeTag.NO.toString())) {
					continue;
				}
			}
			// ### 

			// Initialize
			double influenceIndex = 0.0;
			// Get all outgoing relationships of node and iterate
			Iterable<Relationship> iterableRel = n.getRelationships(cfg.out);
			for (Relationship relationship : iterableRel) {
				// Mark node as active
				n.setProperty(StaticConfig.nodeSwitch, StaticConfig.NodeSwitch.ON.toString());
				// Recursive algorithm
				influenceIndex = crawlDownstream(relationship, 1.0, influenceIndex);
				// Deactivate if active
				if (n.getProperty(StaticConfig.nodeSwitch).equals(StaticConfig.NodeSwitch.ON.toString())) {
					n.setProperty(StaticConfig.nodeSwitch, StaticConfig.NodeSwitch.OFF.toString());
				}
			}
			// Assign Influence Index
			n.setProperty(StaticConfig.nodeInfluenceIndex, influenceIndex);
		}
	}

	// Recursive depth first search (DFS) computing the Influence Index contributions from
	// the value of all downstream nodes
	private double crawlDownstream(Relationship relationship, double indirectWeight, double influenceIndex) {
		// Initialize
		Node successor = relationship.getEndNode();

		// ### Cumulative
		if (cfg.cumulativeInfluenceIndex) {
			// Found other tagged (sink) node, go back
			if (successor.getProperty(StaticConfig.nodeTag).equals(StaticConfig.NodeTag.YES.toString())) {
				return influenceIndex;
			}
		}
		// ###

		// Been here, go back up
		if (successor.getProperty(StaticConfig.nodeSwitch).equals(StaticConfig.NodeSwitch.ON.toString())) {
			return influenceIndex;
		}

		// Get node value that will flow up through trail to sink as influence
		double initValue = (double) (successor.getProperty(StaticConfig.nodeValue));

		// Compute indirect weight
		double w = (double) relationship.getProperty(StaticConfig.weight);
		double newWeight = w * indirectWeight;

		// Compute Influence Index 
		double currentInfluenceIndex = newWeight * initValue;

		// Update Influence Index and activate
		double newInfluenceIndex = currentInfluenceIndex + influenceIndex;
		successor.setProperty(StaticConfig.nodeSwitch, StaticConfig.NodeSwitch.ON.toString());

		// Continue recursively along trails (DFS): Get successors at next level
		Iterable<Relationship> iterableRel = successor.getRelationships(cfg.out);

		// Reached leaf node, go back up
		if (!iterableRel.iterator().hasNext()) {
			// Deactivate if active
			if (successor.getProperty(StaticConfig.nodeSwitch).equals(StaticConfig.NodeSwitch.ON.toString())) {
				successor.setProperty(StaticConfig.nodeSwitch, StaticConfig.NodeSwitch.OFF.toString());
			}
			return newInfluenceIndex;
		}

		// Iterate through relationships
		for (Relationship newRelationship : iterableRel) {
			// Retrieve Influence Index from recursive call
			newInfluenceIndex = crawlDownstream(newRelationship, newWeight, newInfluenceIndex);
		}
		// Deactivate if active
		if (successor.getProperty(StaticConfig.nodeSwitch).equals(StaticConfig.NodeSwitch.ON.toString())) {
			successor.setProperty(StaticConfig.nodeSwitch, StaticConfig.NodeSwitch.OFF.toString());
		}
		// No more relationships, go back up
		return newInfluenceIndex;
	}
	
	// Analytical computation
	/*
	 *  Eigenvector centrality variant with no cycle correction
	 *  See Equation (2) in the Supporting Information to:
	 *     Vitali S, Glattfelder JB, Battiston S (2011) The Network of Global Corporate Control
	 *     PLoS ONE 6(10): e25995. doi:10.1371/journal.pone.0025995
	 *     http://journals.plos.org/plosone/article?id=10.1371/journal.pone.0025995
	 */
	public void analyticalComp(DoubleMatrix2D adj, DoubleMatrix1D val, MyBowtieNetwork nw) {
		// Linear algebra support from COLT http://acs.lbl.gov/ACSSoftware/colt/
		DoubleMatrix2D identity = DoubleFactory2D.sparse.identity(val.size());
		DoubleDoubleFunction minus = new DoubleDoubleFunction() {
			public double apply(double a, double b) { return a-b; } // Deduct
		};
		//DoubleDoubleFunction plus = new DoubleDoubleFunction() {
		//	public double apply(double a, double b) { return a+b; } // Add
		//};
		//DoubleFunction inv = new DoubleFunction() {
		//	public double apply(double a) { return 1/a; } // Invert
		//};

		// Centrality
		DoubleMatrix2D ima = identity.assign(adj, minus); // (I-A)
		identity = DoubleFactory2D.sparse.identity(val.size());
		Algebra a = new Algebra();
		
		DoubleMatrix2D imaInv = a.inverse(ima); // (I-A)^{-1}
		DoubleMatrix2D A_tilde = new SparseDoubleMatrix2D(val.size(), val.size());
		imaInv.zMult(adj, A_tilde); // A_tilde = (I-A)^{-1}b * A
		DoubleMatrix1D c = new SparseDoubleMatrix1D(val.size());
		A_tilde.zMult(val, c); // A_tilde * val
		
		System.out.println("\n### Eigenvector centrality variant with no cycle correction (by node id)");
		for (int i = 0; i < c.size(); i++) {
			System.out.print(nw.getIdToName().get(i) + ": " + myFormatter.format(c.get(i)) + "; ");
		}
		System.out.println("\nTotal centrality value:\t" + c.zSum());
	}
}
