/* Generated By:JJTree: Do not edit this line. CLVFIffNode.java */

package org.jetel.ctl.ASTnode;

import org.jetel.ctl.ExpParser;
import org.jetel.ctl.TransformLangParserVisitor;

public class CLVFIIfNode extends SimpleNode {
	public CLVFIIfNode(int id) {
		super(id);
	}

	public CLVFIIfNode(ExpParser p, int id) {
		super(p, id);
	}

	public CLVFIIfNode(CLVFIIfNode node) {
		super(node);
	}

	/** Accept the visitor. * */
	public Object jjtAccept(TransformLangParserVisitor visitor, Object data) {
		return visitor.visit(this, data);
	}
	
	@Override
	public SimpleNode duplicate() {
		return new CLVFIIfNode(this);
	}
}