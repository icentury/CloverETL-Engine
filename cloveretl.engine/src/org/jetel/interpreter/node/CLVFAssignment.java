/* Generated By:JJTree: Do not edit this line. CLVFAssignment.java */

package org.jetel.interpreter.node;

import org.jetel.interpreter.ExpParser;
import org.jetel.interpreter.TransformLangParserVisitor;
public class CLVFAssignment extends SimpleNode {
  public CLVFAssignment(int id) {
    super(id);
  }

  public CLVFAssignment(ExpParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(TransformLangParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
