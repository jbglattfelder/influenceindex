package ch.uzh.bf;

// Neo4j Version 2.2.5
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

public final class StaticConfig {

	private  StaticConfig() {}

	// *** Relations
	public static enum RelType implements RelationshipType {OWNS};
	public static RelType relType = RelType.OWNS;

	public static enum RelProps {WEIGHT_MERGED};
	public static String weight = RelProps.WEIGHT_MERGED.toString();

	public static Direction outDir = Direction.OUTGOING;
	public static Direction inDir = Direction.INCOMING;
	public static Direction bothDir = Direction.BOTH;

	// *** Nodes
	public static enum NodeLabel implements Label {MYNODE};

	public static enum NodeProps {NAME, VALUE, BOWTIE, II_VAL, NODESWITCH, TAG};
	public static String nodeName = NodeProps.NAME.toString();
	public static String nodeBT = NodeProps.BOWTIE.toString();
	public static String nodeValue = NodeProps.VALUE.toString();
	public static String nodeInfluenceIndex = NodeProps.II_VAL.toString();

	// * Influence Index
	public static enum NodeSwitch {ON, OFF};
	public static String nodeSwitch = NodeProps.NODESWITCH.toString();

	// * Node tagginf for cumulative Influence Index
	public static enum NodeTag {YES, NO};
	public static String nodeTag = NodeProps.TAG.toString();

	// *** Bowtie
	public static enum BowTie {IN, OUT, SCC, TT, OCC};
}
