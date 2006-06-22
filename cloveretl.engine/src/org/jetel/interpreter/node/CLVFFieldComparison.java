/* Generated By:JJTree: Do not edit this line. CLVFFieldComparison.java */

package org.jetel.interpreter.node;

import org.jetel.interpreter.ExpParser;
import org.jetel.interpreter.TransformLangParserConstants;
import org.jetel.interpreter.TransformLangParserVisitor;


public class CLVFFieldComparison extends SimpleNode implements TransformLangParserConstants {
	
	int cmpType;
	
	public CLVFFieldComparison(int id) {
		super(id);
	}
	
	public CLVFFieldComparison(ExpParser p, int id) {
		super(p, id);
	}
	
	/** Accept the visitor. **/
	public Object jjtAccept(TransformLangParserVisitor visitor, Object data) {
		return visitor.visit(this, data);
	}
	
}
