package org.jetel.ctl;

import static org.jetel.ctl.TransformLangParserTreeConstants.JJTVARIABLEDECLARATION;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jetel.ctl.ASTnode.CLVFAddNode;
import org.jetel.ctl.ASTnode.CLVFAnd;
import org.jetel.ctl.ASTnode.CLVFArguments;
import org.jetel.ctl.ASTnode.CLVFArrayAccessExpression;
import org.jetel.ctl.ASTnode.CLVFAssignment;
import org.jetel.ctl.ASTnode.CLVFBlock;
import org.jetel.ctl.ASTnode.CLVFBreakStatement;
import org.jetel.ctl.ASTnode.CLVFBreakpointNode;
import org.jetel.ctl.ASTnode.CLVFCaseStatement;
import org.jetel.ctl.ASTnode.CLVFComparison;
import org.jetel.ctl.ASTnode.CLVFConditionalExpression;
import org.jetel.ctl.ASTnode.CLVFContinueStatement;
import org.jetel.ctl.ASTnode.CLVFDateField;
import org.jetel.ctl.ASTnode.CLVFDivNode;
import org.jetel.ctl.ASTnode.CLVFDoStatement;
import org.jetel.ctl.ASTnode.CLVFEvalNode;
import org.jetel.ctl.ASTnode.CLVFFieldAccessExpression;
import org.jetel.ctl.ASTnode.CLVFForStatement;
import org.jetel.ctl.ASTnode.CLVFForeachStatement;
import org.jetel.ctl.ASTnode.CLVFFunctionCall;
import org.jetel.ctl.ASTnode.CLVFFunctionDeclaration;
import org.jetel.ctl.ASTnode.CLVFIIfNode;
import org.jetel.ctl.ASTnode.CLVFIdentifier;
import org.jetel.ctl.ASTnode.CLVFIfStatement;
import org.jetel.ctl.ASTnode.CLVFImportSource;
import org.jetel.ctl.ASTnode.CLVFInFunction;
import org.jetel.ctl.ASTnode.CLVFIsNullNode;
import org.jetel.ctl.ASTnode.CLVFListOfLiterals;
import org.jetel.ctl.ASTnode.CLVFLiteral;
import org.jetel.ctl.ASTnode.CLVFLogLevel;
import org.jetel.ctl.ASTnode.CLVFLookupNode;
import org.jetel.ctl.ASTnode.CLVFMemberAccessExpression;
import org.jetel.ctl.ASTnode.CLVFModNode;
import org.jetel.ctl.ASTnode.CLVFMulNode;
import org.jetel.ctl.ASTnode.CLVFNVL2Node;
import org.jetel.ctl.ASTnode.CLVFNVLNode;
import org.jetel.ctl.ASTnode.CLVFOr;
import org.jetel.ctl.ASTnode.CLVFParameters;
import org.jetel.ctl.ASTnode.CLVFPostfixExpression;
import org.jetel.ctl.ASTnode.CLVFPrintErrNode;
import org.jetel.ctl.ASTnode.CLVFPrintLogNode;
import org.jetel.ctl.ASTnode.CLVFPrintStackNode;
import org.jetel.ctl.ASTnode.CLVFRaiseErrorNode;
import org.jetel.ctl.ASTnode.CLVFReturnStatement;
import org.jetel.ctl.ASTnode.CLVFSequenceNode;
import org.jetel.ctl.ASTnode.CLVFStart;
import org.jetel.ctl.ASTnode.CLVFStartExpression;
import org.jetel.ctl.ASTnode.CLVFSubNode;
import org.jetel.ctl.ASTnode.CLVFSwitchStatement;
import org.jetel.ctl.ASTnode.CLVFType;
import org.jetel.ctl.ASTnode.CLVFUnaryExpression;
import org.jetel.ctl.ASTnode.CLVFVariableDeclaration;
import org.jetel.ctl.ASTnode.CLVFWhileStatement;
import org.jetel.ctl.ASTnode.CastNode;
import org.jetel.ctl.ASTnode.SimpleNode;
import org.jetel.ctl.data.TLType;
import org.jetel.ctl.data.TLTypePrimitive;
import org.jetel.ctl.data.TLType.TLTypeList;
import org.jetel.ctl.data.TLType.TLTypeMap;
import org.jetel.ctl.data.TLType.TLTypeRecord;
import org.jetel.ctl.data.TLType.TLTypeSymbol;
import org.jetel.ctl.extensions.TLFunctionDescriptor;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;

public class TypeChecker extends NavigatingVisitor {

	// functions being checked for handling
	private Map<String,List<CLVFFunctionDeclaration>> declaredFunctions;
	private TreeSet<String> functionInProgress = new TreeSet<String>();
	private ProblemReporter problemReporter;
	private CLVFFunctionDeclaration activeFunction = null;
	private Map<String, List<TLFunctionDescriptor>> externalFunctions;
	private final HashMap<String,TLType> typeVarMapping = new HashMap<String, TLType>();
	
	public TypeChecker(ProblemReporter problemReporter, Map<String, List<CLVFFunctionDeclaration>> declaredFunctions, Map<String, List<TLFunctionDescriptor>> externalFunctions) {
		this.problemReporter = problemReporter;
		this.declaredFunctions = declaredFunctions;
		this.externalFunctions = externalFunctions;
	}
	
	public void check(CLVFStart ast) {
		visit(ast, null);
	}
	
	public void check(CLVFStartExpression ast) {
		visit(ast, null);
	}

	@Override
	public Object visit(CLVFAddNode node, Object data) {
		super.visit(node, data);

		// propagate error
		if (!checkChildren(node)) {
			return data;
		}

		SimpleNode lhs = (SimpleNode) node.jjtGetChild(0);
		SimpleNode rhs = (SimpleNode) node.jjtGetChild(1);

		// string concatenation
		if (lhs.getType().isString() || rhs.getType().isString()) {
			castIfNeeded(node,0,TLTypePrimitive.STRING);
			castIfNeeded(node,1,TLTypePrimitive.STRING);
			node.setType(TLTypePrimitive.STRING);
			return data;
		}
		
		// (mixed) list concatenation
		if (lhs.getType().isList()) {
			if (lhs.getType().canAssign(rhs.getType())) {
				// list + list concatenation
				node.setType(lhs.getType());
				return data;
			}
			
			final TLType elemType = ((TLTypeList)lhs.getType()).getElementType();
			if (elemType.canAssign(rhs.getType())) {
				// list + element concatenation
				node.setType(lhs.getType());
				return data;
			}
			
			// invalid concatenation
			node.setType(TLType.ERROR);
			error(node,"Operator '+' is not defined for types '" + lhs.getType().name() + "' and '" + rhs.getType().name() + "'");
			return data;
		}
		
		// map concatenation
		if (lhs.getType().isMap()) {
			if (lhs.getType().canAssign(rhs.getType())) {
				node.setType(lhs.getType());
				return data;
			}
			node.setType(TLType.ERROR);
			return data;
		}
		
		// general (mixed-type) addition
		TLType result = checkArithmeticOperator(lhs, rhs);
		if (result.isError()) {
			error(node, "Operator '+' is not defined for types: " + "'" + lhs.getType().name() + "' and '" + rhs.getType().name() + "'");
		} else {
			// check if any explicit type-casting is needed
			castIfNeeded(node,0,result);
			castIfNeeded(node,1,result);
		}
		

		node.setType(result);
		return data;
	}

	@Override
	public Object visit(CLVFAnd node, Object data) {
		super.visit(node, data);
		TLType lhs = ((SimpleNode) node.jjtGetChild(0)).getType();
		TLType rhs = ((SimpleNode) node.jjtGetChild(1)).getType();

		if (!checkChildren(node)) {
			return data;
		}

		if (lhs.isBoolean() && rhs.isBoolean()) {
			node.setType(TLTypePrimitive.BOOLEAN);
			return data;
		}

		// any other configuration is incorrect
		node.setType(TLType.ERROR);
		error(node, "Operator '&&' is not defined for types: " + "'" + lhs.name() + "' and '" + rhs.name() + "'");

		return data;
	}

	@Override
	public Object visit(CLVFArguments node, Object data) {
		super.visit(node, data);
		node.setType(TLType.VOID);
		checkChildren(node);
		
		return data;
	}
	
	@Override
	public Object visit(CLVFArrayAccessExpression node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}
		
		SimpleNode composite = (SimpleNode)node.jjtGetChild(0);
		if (composite.getType().isList()) {
			SimpleNode index = (SimpleNode)node.jjtGetChild(1);
			if (! index.getType().isInteger()) {
				error(index,"Cannot convert from '" + index.getType().name() + "' to '" + TLTypePrimitive.INTEGER.name() + "'");
				node.setType(TLType.ERROR);
				return data;
			}
			node.setType(((TLTypeList)composite.getType()).getElementType());
			return data;
		}
		
		if (composite.getType().isMap()) {
			// map can accept any type as its index
			TLTypeMap mapComposite = (TLTypeMap)composite.getType();
			SimpleNode key = (SimpleNode)node.jjtGetChild(1);
			if (! mapComposite.getKeyType().canAssign(key.getType())) {
				error(key,"Cannot convert from '" + key.getType().name() + "' to " + mapComposite.getKeyType().name());
				node.setType(TLType.ERROR);
				return data;
			}
			node.setType(mapComposite.getValueType());
			return data;
		}
		
		error(node,"Expression is not a composite type but is resolved to '" + composite.getType().name() + "'",
				"Expression must be a list or map");
		node.setType(TLType.ERROR);
		return data;
	}

	@Override
	public Object visit(CLVFAssignment node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}

		TLType lhs = ((SimpleNode) node.jjtGetChild(0)).getType();
		TLType rhs = ((SimpleNode) node.jjtGetChild(1)).getType();
		
		/*
		 * Resulting type of assignment expression is LHS of assignment
		 * Java example:
		 * int i;
		 * float f;
		 * t = f = 3; // results in type error, value of assignment is the LHS (not RHS)
		 */
		if (lhs.canAssign(rhs)) {
			castIfNeeded(node, 1, lhs);
			node.setType(lhs);
		} else {
			error(node, "Type mismatch: cannot convert from " + "'" + rhs.name() + "' to '" + lhs.name() + "'");
			node.setType(TLType.ERROR);
		}

		return data;
	}
	
	@Override
	public Object visit(CLVFBlock node, Object data) {
		super.visit(node, data);
		node.setType(TLType.VOID);
		checkChildren(node); // this will overwrite VOID with ERROR if children have errors
		return data;
	}

	@Override
	public Object visit(CLVFBreakpointNode node, Object data) {
		node.setType(TLType.VOID);
		return data;
	}

	@Override
	public Object visit(CLVFBreakStatement node, Object data) {
		node.setType(TLType.VOID);
		return data;
	}

	@Override
	public Object visit(CLVFCaseStatement node, Object data) {
		super.visit(node, data);

		node.setType(TLType.VOID);
		// check children for errors and possibly set error type
		checkChildren(node);
		
		// nothing to check for the 'default' clause
		if (node.isDefaultCase()) {
			return data;
		}
		
		// check case condition type if it is valid
		SimpleNode caseExp = (SimpleNode)node.jjtGetChild(0);
		if (caseExp.getType().isError()) {
			return data;
		}
		
		if (!caseExp.getType().isPrimitive()) {
			error(caseExp, "Illegal type of case expression '" + caseExp.getType().name() + "'");
			node.setType(TLType.ERROR);
		}

		return data;

	}

	@Override
	public Object visit(CLVFComparison node, Object data) {
		super.visit(node, data);
		if (!checkChildren(node)) {
			return data;
		}

		TLType lhs = ((SimpleNode) node.jjtGetChild(0)).getType();
		TLType rhs = ((SimpleNode) node.jjtGetChild(1)).getType();

		switch (node.getOperator()) {
		case TransformLangParserConstants.REGEX_CONTAINS:
		case TransformLangParserConstants.REGEX_EQUAL:
			// regular expression operators
			if (lhs.isString()) {
				if (rhs.isString()) {
					node.setType(TLTypePrimitive.BOOLEAN);
					node.setOperationType(TLTypePrimitive.STRING);
				}
			} else {
				node.setType(TLType.ERROR);
				error(node, "Incompatible types '" + lhs.name() + "' and '" + rhs.name() + "' for regexp operator", "Both expressions must be of type 'string'");
			}
			break;
		case TransformLangParserConstants.EQUAL:
		case TransformLangParserConstants.NON_EQUAL:
		case TransformLangParserConstants.GREATER_THAN:
		case TransformLangParserConstants.GREATER_THAN_EQUAL:
		case TransformLangParserConstants.LESS_THAN:
		case TransformLangParserConstants.LESS_THAN_EQUAL:
			// arithmetic operators
			TLType ret = checkLogicalOperator(lhs, rhs);
			if (ret.isError()) {
				node.setType(ret);
				error(node, "Incompatible types '" + lhs.name() + "' and '" + rhs.name() + "' for binary operator");
			} else {
				node.setOperationType(ret);
				node.setType(TLTypePrimitive.BOOLEAN);
				castIfNeeded(node,0,ret);
				castIfNeeded(node,1,ret);
			}
			break;
		default:
			throw new IllegalArgumentException("Unknown operator type: Token kind (" + node.getOperator() + ")");
		}

		return data;
	}

	@Override
	public Object visit(CLVFConditionalExpression node, Object data) {
		super.visit(node, data);

		// if condition/expressions in error we propagate error
		if (!checkChildren(node)) {
			return data;
		}

		// condition must return boolean
		SimpleNode condition = (SimpleNode) node.jjtGetChild(0);
		TLType condType = condition.getType();
		if (!condType.isBoolean()) {
			error(condition, "Type mismatch: cannot convert '" + condType.name() + "' to '" + TLTypePrimitive.BOOLEAN.name() +"'");
			node.setType(TLType.ERROR);
			return data;
		}

		TLType thenType = ((SimpleNode) node.jjtGetChild(1)).getType();
		TLType elseType = ((SimpleNode) node.jjtGetChild(2)).getType();

		TLType ret = thenType.promoteWith(elseType);
		if (ret.isError()) {
			error(node, "Types of expressions mismatch: '" + thenType.name() + "' and '" + elseType.name() + "'");
		} else {
			castIfNeeded(node,1,ret);
			castIfNeeded(node,2,ret);
		}

		node.setType(ret);
		return data;
	}

	@Override
	public Object visit(CLVFContinueStatement node, Object data) {
		node.setType(TLType.VOID);
		return data;
	}

	@Override
	public Object visit(CLVFDateField node, Object data) {
		// nothing to do, type calculation done in ASTBuilder
		return data;
	}
	
	@Override
	public Object visit(CLVFDivNode node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}
		
		SimpleNode lhs = (SimpleNode) node.jjtGetChild(0);
		SimpleNode rhs = (SimpleNode) node.jjtGetChild(1);
		
		TLType result = checkArithmeticOperator(lhs, rhs);
		if (result.isError()) {
			error(node, "Operator '/' is not defined for types: " + "'" + lhs.getType().name() + "' and '" + rhs.getType().name() + "'");
		} else {
			castIfNeeded(node,0,result);
			castIfNeeded(node,1,result);
		}

		node.setType(result);
		return data;
	}

	@Override
	public Object visit(CLVFDoStatement node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}

		SimpleNode exp = (SimpleNode) node.jjtGetChild(1);
		if (!exp.getType().isBoolean()) {
			error(exp, "Cannot convert '" + exp.getType().name() + "' to '" + TLTypePrimitive.BOOLEAN.name() + "'");
			node.setType(TLType.ERROR);
			return data;
		}

		node.setType(TLType.VOID);
		return data;
	}
	
	
	@Override
	public Object visit(CLVFFieldAccessExpression node, Object data) {
		// nothing to do, type calculation done in ASTBuilder
		return data;
	}
	
	@Override
	public Object visit(CLVFEvalNode node, Object data) {
		node.setType(TLType.VOID);
		return data;
	}
	
	@Override
	public Object visit(CLVFForStatement node, Object data) {
		super.visit(node, data);
		
		// let's assume the node is valid and only set error if we find one
		node.setType(TLType.VOID);
		checkChildren(node);
		
		/*
		 * For-loop control expressions are optional so we cannot
		 * use the jjtGetChild() to reliably distinguish between them
		 */
		SimpleNode forInit = node.getForInit();
		if (forInit != null) {
			if (!forInit.getType().isError()) {
				/* 
				 * for init can be a variable declaration or an expression
				 * in case the variable is declared, it MUST be initialized
				 * (since there can be tests referencing it)
				 */
				if (forInit.getId() == JJTVARIABLEDECLARATION) {
					if (forInit.jjtGetNumChildren() < 2) {
						// missing initializer
						error(forInit,"Loop control variable must be initialized");
						node.setType(TLType.ERROR);
					}
				}
			}
			
		}

		// the final-test expression must be obviously of boolean type
		SimpleNode forFinal = node.getForFinal();
		if (forFinal != null) {
			if (!forFinal.getType().isError()) {
				if (!forFinal.getType().isBoolean()) {
					error(forFinal,"Cannot convert '" + forFinal.getType().name() + "' to '" + TLTypePrimitive.BOOLEAN.name() + "'");
					node.setType(TLType.ERROR);
				}
			}
 		}
		
		// node type was set to VOID already
		return data;
	}
	
	@Override
	public Object visit(CLVFForeachStatement node, Object data) {
		super.visit(node, data);

		// let's presume the node is OK and set the ERROR whenever we hit an error
		node.setType(TLType.VOID);
		
		// will set the ERROR flag if children have errors
		checkChildren(node); 
		

		CLVFVariableDeclaration loopVar = (CLVFVariableDeclaration) node.jjtGetChild(0);
		TLType loopVarType = loopVar.getType();

		SimpleNode iterable = (SimpleNode) node.jjtGetChild(1);

		// we cannot validate anything if variable or expression are in error 
		if (loopVarType.isError() || iterable.getType().isError()) {
			return data;
		}
		
		if (iterable.getType().isList()) {
			TLType elemType = ((TLTypeList)iterable.getType()).getElementType();
			if (!loopVarType.canAssign(elemType)) {
				error(iterable,"Cannot convert '" + elemType.name() + "' to '" + loopVarType.name() + "'");
				node.setType(TLType.ERROR);
			} 
			// type-cast will be generated when rewriting to Java code
		} else if (iterable.getType().isRecord()) {
			// we need to derive the list of fields that are safe to assign to the loopVar
			DataRecordMetadata meta = ((TLTypeRecord) iterable.getType()).getMetadata();
			LinkedList<Integer> typeSafeFields = new LinkedList<Integer>();
			for (int i = 0; i < meta.getNumFields(); i++) {
				TLType fieldType = TLTypePrimitive.fromCloverType(meta.getField(i));
				
				if (loopVarType.equals(fieldType)) {
					typeSafeFields.add(i);
				}
			}
			if (typeSafeFields.size() == 0) {
				warn(iterable,"'" + iterable.getType().name() + "' does not contain any fields of type '" 
						+ loopVar.getType().name() + "'");
			}
			int[] fields = new int[typeSafeFields.size()];
			int i = 0;
			for (Integer f : typeSafeFields) {
				fields[i++] = f;
			}
			node.setTypeSafeFields(fields);
		} else {
			error(iterable, "Cannot iterate over the expression of type '" + iterable.getType().name() + "'",
					"Can only iterate over list, map or record");
			node.setType(TLType.ERROR);
		}

		return data;
	}

	
	@Override
	public Object visit(CLVFFunctionCall node, Object data) {
		// infer types of function arguments
		super.visit(node, null);

		if (!checkChildren(node)) {
			return data;
		}
		
		CLVFFunctionDeclaration localCandidate = null;
		int minResult = Integer.MAX_VALUE;

		// infer actual parameter types
		CLVFArguments args = (CLVFArguments) node.jjtGetChild(0);
		TLType[] actual = new TLType[args.jjtGetNumChildren()];
		for (int i = 0; i < actual.length; i++) {
			actual[i] = ((SimpleNode) args.jjtGetChild(i)).getType();
		}
		node.setActualParameters(actual);
		
		// scan local function declarations for (best) match
		final List<CLVFFunctionDeclaration> local = declaredFunctions.get(node.getName());
		if (local != null) {
			// local function with such name exists
			for (CLVFFunctionDeclaration fd : local) {
				final int distance = functionDistance(actual, fd.getFormalParameters(), false);
				if (distance < minResult) {
					minResult = distance;
					localCandidate = fd;
					if (distance == 0) {
						// best possible match
						break;
					}
				}
			}
			
		}
		
		// if minResult==0 and we found a match we have the best possible match
		// thus we do not need to scan the external functions...
		if (localCandidate != null && minResult == 0) {
			node.setCallTarget(localCandidate);
			node.setType(localCandidate.getType());
			return data;
		}
		
		
		/*
		 * None or not-the-best match with local functions yet - scan external
		 */
		final List<TLFunctionDescriptor> external = externalFunctions.get(node.getName());
		TLFunctionDescriptor extCandidate = null;
		if (external != null) {
			for (TLFunctionDescriptor fd : external) {
				int distance = functionDistance(actual, fd.getFormalParameters(), fd.isVarArg());
				
				if (distance < minResult) {
					minResult = distance;
					extCandidate = fd;
					if (distance == 0) {
						// best possible match
						break;
					}
				}
			}
		}
		
		// if extCandidate != null we found even better match in external functions
		if (extCandidate != null) {
			node.setCallTarget(extCandidate);
			castIfNeeded(args,extCandidate.getFormalParameters());
			if (!extCandidate.isGeneric()) {
				node.setType(extCandidate.getReturnType());
			} else {
				//infer the real return type (if necessary) by binding the type variables
				node.setType(bindType(extCandidate.getReturnType()));
			}
			return data;
		}
		
		
		// no better external candidate found, fall back to any local function
		if (localCandidate != null) {
			node.setCallTarget(localCandidate);
			castIfNeeded(args,localCandidate.getFormalParameters());
			node.setType(localCandidate.getType());
		} else {
			// no match found - if we have any candidates, report with hints
			if (local != null && local.size() > 0) {
				error(node, functionErrorMessage(
						local.get(0).getName(), 
						local.get(0).getFormalParameters(), 
						actual));
			} else if (external != null && external.size() > 0){
				error(node, functionErrorMessage(
						external.get(0).getName(),
						external.get(0).getFormalParameters(), 
						actual));
			} else {
				error(node, "Function '" + node.getName() + "' is not declared");
			}
			node.setType(TLType.ERROR);
		}
		
		return data;
	}

	

	

	@Override
	public Object visit(CLVFFunctionDeclaration node, Object data) {
		if (functionInProgress.contains(node.getName())) {
			// we are already resolving this function (inside recursive call)
			return data;
		}

		functionInProgress.add(node.getName());
		CLVFFunctionDeclaration prevFunc = activeFunction;
		activeFunction = node;
		// we handled return type and arguments in AST builder pass
		// let's take care of function body now
		CLVFBlock body = (CLVFBlock) node.jjtGetChild(2);
		body.jjtAccept(this, data);
		functionInProgress.remove(node.getName());
		activeFunction = prevFunc;
		return data;
	}

	
	@Override
	public Object visit(CLVFIdentifier node, Object data) {
		node.setType(node.getVariable().getType());
		return data;
	}
	
	/**
	 * Checks iif(condition,trueExp,falseExp) node the same way 
	 * as ternary conditional (?:)expression
	 */
	@Override
	public Object visit(CLVFIIfNode node, Object data) {
		super.visit(node, data);
		
		// if condition/expressions in error we propagate error
		if (!checkChildren(node)) {
			return data;
		}


		if (!isFunctionNodeValid(node,"iif",new TLType[]{TLTypePrimitive.BOOLEAN,TLType.OBJECT,TLType.OBJECT})) {
			return data;
		}
		
		TLType thenType = ((SimpleNode) node.jjtGetChild(0).jjtGetChild(1)).getType();
		TLType elseType = ((SimpleNode) node.jjtGetChild(0).jjtGetChild(2)).getType();

		TLType ret = thenType.promoteWith(elseType);
		if (ret.isError()) {
			error(node, "Types of expressions mismatch: '" + thenType.name() + "' and '" + elseType.name() + "'");
			node.setType(TLType.ERROR);
		}
		
		// generate type casts if necessary
		castIfNeeded((SimpleNode)node.jjtGetChild(0),1,ret);
		castIfNeeded((SimpleNode)node.jjtGetChild(0),2,ret);

		node.setType(ret);
		return data;
		
	}
	
	@Override
	public Object visit(CLVFIfStatement node, Object data) {
		super.visit(node, data);
		
		node.setType(TLType.VOID);
		checkChildren(node);
		
		SimpleNode expression = (SimpleNode)node.jjtGetChild(0);
		if (!expression.getType().isError()) {
			if (!TLTypePrimitive.BOOLEAN.canAssign(expression.getType())) {
				error(expression,"Cannot convert '" + expression.getType().name() + 
						"' to '" + TLTypePrimitive.BOOLEAN.name() + "'");
				node.setType(TLType.ERROR);
			}
		}
		
		return data;
	}
	
	@Override
	public Object visit(CLVFImportSource node, Object data) {
		super.visit(node, data);
		node.setType(TLType.VOID);
		checkChildren(node);
		return data;
	}
	
	@Override
	public Object visit(CLVFInFunction node, Object data) {
		super.visit(node, data);
		
		// if condition/expressions in error we propagate error
		if (!checkChildren(node)) {
			return data;
		}

		CLVFArguments args = (CLVFArguments)node.jjtGetChild(0);
		TLType[] actual = new TLType[args.jjtGetNumChildren()];
		for (int i=0; i<args.jjtGetNumChildren(); i++) {
			actual[i] = ((SimpleNode)args.jjtGetChild(i)).getType();
		}
		
		if (actual.length != 2) {
			error(node,functionErrorMessage("in", new TLType[]{TLType.OBJECT,TLType.createList(null)},actual));
			node.setType(TLType.ERROR);
			return data;
		}

		if (!actual[1].isList() && !actual[1].isMap()) {
			node.setType(TLType.ERROR);
			error(node,functionErrorMessage("in", new TLType[]{TLType.OBJECT,TLType.createList(null)},actual));
		}
		
		TLType elemType = actual[1].isList() ? ((TLTypeList)actual[1]).getElementType() : ((TLTypeMap)actual[1]).getKeyType();
		TLType ret = checkLogicalOperator(actual[0], elemType);
		if (ret.isError()) {
			node.setType(ret);
			error(node,functionErrorMessage("in", new TLType[]{TLType.OBJECT,TLType.createList(null)},actual));
		} else {
			/*
			 * in situation like:
			 * int i=3;
			 * double[] l = [ 3.1, 3.0, 4.3 ];
			 * boolean b = i .in. l;
			 * 
			 *  we may need to upcast the LHS to type of the list.
			 *  We cannot do upcasting of RHS, because List<Integer> is not assignable
			 *  to List<Long> although Integer is assignable to Long.
			 */
			castIfNeeded(args,0,elemType);
			node.setType(TLTypePrimitive.BOOLEAN);
		}
		
		return data;
	}
	
	
	@Override
	public Object visit(CLVFIsNullNode node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}
		
		if (!isFunctionNodeValid(node, "isnull", new TLType[]{TLTypePrimitive.OBJECT})) {
			return data;
		}
		
		node.setType(TLTypePrimitive.BOOLEAN);
		return data;
		
	}
	
	@Override
	public Object visit(CLVFListOfLiterals node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}

		/*
		 * We iterate over values and compute the closure of all element types
		 * Later we check this computed type against declared array type.
		 * In case we receive TLType.ERROR as closure the expressions themselves are incompatible
		 */
		TLType closure = null;
		TLType lastClosure  = null;
		for (int i=0; i<node.jjtGetNumChildren(); i++) {
			SimpleNode child = (SimpleNode)node.jjtGetChild(i);
			if ( ( closure = checkListElements(lastClosure = closure,child.getType())).isError()) {
				error(child,"Cannot convert from '" + child.getType().name() + "' to '" + lastClosure.name());
				node.setType(TLType.ERROR);
			return data;
		}

			
//			switch (child.getId()) {
//			case TransformLangParserTreeConstants.JJTLITERAL:
//				if ( ( closure = checkListElements(lastClosure = closure,child.getType())).isError()) {
//					error(child,"Cannot convert from '" + child.getType().name() + "' to '" + lastClosure.name());
//					node.setType(TLType.ERROR);
//					return data;
//				}
//				break;
//			case TransformLangParserTreeConstants.JJTUNARYEXPRESSION:
//				if ( ( closure = checkListElements(lastClosure = closure,child.getType())).isError()) {
//					error(child,"Cannot convert from '" + child.getType().name() + "' to '" + lastClosure.name());
//					node.setType(TLType.ERROR);
//					return data;
//				}
//				
//				CLVFUnaryExpression expr = (CLVFUnaryExpression)child;
//				if (expr.getOperator() == TransformLangParserConstants.MINUS) {
//					break;
//				}
//				// no break here on purpose: handles unary expression other than MINUS !
//			default:
//				error(child,"Illegal expression in the list initializer",
//						"Only literals and negative expressions are allowed to be in the list initializer");
//				node.setType(TLType.ERROR);
//				return data;
//			}
		}
		
		// the closure is known: generate type-casts to the closure type if necessary
		for (int i=0; i<node.jjtGetNumChildren(); i++) {
			castIfNeeded(node, i, closure);
		}
		
		// we were able to compute the closure for all literals within the list
		// use it as element type for the list
		node.setType(TLType.createList(closure));
		return data;
	}
	
	private TLType checkListElements(TLType closure, TLType elem) {
		if (closure == null) {
			return elem;
		}
		return closure.promoteWith(elem);
	}
	
	
	@Override
	public Object visit(CLVFLiteral node, Object data) {
		// nothing to do - type set during parsing
		return data;
	}
	
	@Override
	public Object visit(CLVFLogLevel node, Object data) {
		// nothing to do - type set during parsing
		return data;
	}

	
	
	@Override
	public Object visit(CLVFLookupNode node, Object data) {
		super.visit(node,data);
		
		CLVFArguments args = null;
		TLType[] actual = null;
		String opName = null;
		switch (node.getOperation()) {
		case CLVFLookupNode.OP_COUNT:
			opName = "count";
			// no break here - key validation continues
		case CLVFLookupNode.OP_GET:
			opName = opName == null ? "get" : opName;
			TLType[] formal = node.getFormalParameters();
			args = (CLVFArguments)node.jjtGetChild(0);
			
			actual = new TLType[args.jjtGetNumChildren()];
			for (int i=0; i<actual.length; i++) {
				actual[i] = ((SimpleNode)args.jjtGetChild(i)).getType();
			}

			if (formal.length != actual.length) {
				error(node,functionErrorMessage(opName, formal, actual));
				node.setType(TLType.ERROR);
				return data;
			}
			
			for (int i=0; i<formal.length; i++) {
				if (! formal[i].canAssign(actual[i])) {
					error(node,functionErrorMessage(opName, formal, actual));
					node.setType(TLType.ERROR);
					return data;
				} else {
					castIfNeeded(args, i, formal[i]);
				}
			}
			
			// return type already set in AST builder
			break;
		
		case CLVFLookupNode.OP_NEXT:
			opName = "next";
		case CLVFLookupNode.OP_INIT:
			opName = opName == null ? "init" : opName;  //can be flow from above
		case CLVFLookupNode.OP_FREE:
			opName = opName == null ? "free" : opName; // can be flow from above
			args = (CLVFArguments)node.jjtGetChild(0);
			if (args.jjtGetNumChildren()> 0) {
				actual = new TLType[args.jjtGetNumChildren()];
				error(node,functionErrorMessage(opName, new TLType[0], actual));
				node.setType(TLType.ERROR);
				return data;
			}
			
			// return type already set in AST builder
			break;
		}
		
		return data;
	}
	
	
	/**
	 * Checks if field exists within metadata
	 */
	@Override
	public Object visit(CLVFMemberAccessExpression node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}
		
		TLType compositeType = ((SimpleNode)node.jjtGetChild(0)).getType();
		
		// check if the field exists within record's metadata
		if (compositeType.isRecord()) {
			if (node.isWildcard()) {
				// wildcard access allows manipulation with complete record
				// and changes type semantics from reference to value
				final DataRecordMetadata metadata = ((TLTypeRecord)compositeType).getMetadata();
				node.setType(TLType.forRecord(metadata,false));
			} else {
				DataRecordMetadata metadata = ((TLTypeRecord)compositeType).getMetadata();
				DataFieldMetadata field = metadata.getField(node.getName());
				if (field == null) {
					error(node,"Field '" + node.getName() + "' does not exist in record '" + metadata.getName() + "'");
					node.setType(TLType.ERROR);
					return data;
				}
				
				node.setFieldId(metadata.getFieldPosition(node.getName()));
				node.setType(TLTypePrimitive.fromCloverType(field));
			}
			
			return data;
		}
		
		// anything else is an error
		error(node,"Argument is not a record");
		node.setType(TLType.ERROR);
		return data;
		
	}
	
	@Override
	public Object visit(CLVFModNode node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}

		SimpleNode lhs = (SimpleNode) node.jjtGetChild(0);
		SimpleNode rhs = (SimpleNode) node.jjtGetChild(1);

		TLType result = checkArithmeticOperator(lhs, rhs);
		if (result.isError()) {
			error(node, "Operator '%' is not defined for types: " + "'" + lhs.getType().name() + "' and '" + rhs.getType().name() + "'");
		} else {
			castIfNeeded(node,0,result);
			castIfNeeded(node,1,result);
		}
		
		node.setType(result);
		return data;
	}
	
	@Override
	public Object visit(CLVFMulNode node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}

		SimpleNode lhs = (SimpleNode) node.jjtGetChild(0);
		SimpleNode rhs = (SimpleNode) node.jjtGetChild(1);

		// general (mixed-type) multiplication
		TLType result = checkArithmeticOperator(lhs, rhs);
		if (result.isError()) {
			error(lhs, "Operator '*' is not defined for types: " + "'" + lhs.getType().name() + "' and '" + rhs.getType().name() + "'");
		} else {
			/* 
			 * NOTE: 
			 * Decimal-decimal multiplication where operands have different precision/scale
			 * is handled by BigDecimal implementation so no explicit type-casting is needed
			 */
			castIfNeeded(node,0,result);
			castIfNeeded(node,1,result);
		}

		node.setType(result);
		return data;
	}
	
	
	
	@Override
	public Object visit(CLVFNVLNode node, Object data) {
		super.visit(node, data);

		// if condition/expressions in error we propagate error
		if (!checkChildren(node)) {
			return data;
		}

		if (!isFunctionNodeValid(node,"nvl", new TLType[]{TLType.OBJECT,TLType.OBJECT})) {
			return data;
		}
		
		// argument types must match
		TLType thenType = ((SimpleNode) node.jjtGetChild(0).jjtGetChild(0)).getType();
		TLType elseType = ((SimpleNode) node.jjtGetChild(0).jjtGetChild(1)).getType();

		TLType ret = thenType.promoteWith(elseType);
		if (ret.isError()) {
			error(node, "Types of expressions mismatch: '" + thenType.name() + "' and '" + elseType.name() + "'");
			node.setType(TLType.ERROR);
		}
		
		// generate type casts if necessary
		castIfNeeded((SimpleNode)node.jjtGetChild(0),0,ret);
		castIfNeeded((SimpleNode)node.jjtGetChild(0),1,ret);

		node.setType(ret);
		
		return data;
	}
	
	/**
	 * We will check this similarly as for the ternary conditional expression.
	 * Return types from NVL must be consistent
	 */
	@Override
	public Object visit(CLVFNVL2Node node, Object data) {
		super.visit(node, data);

		// if condition/expressions in error we propagate error
		if (!checkChildren(node)) {
			return data;
		}

		if (!isFunctionNodeValid(node,"nvl2",new TLType[]{TLType.OBJECT,TLType.OBJECT,TLType.OBJECT})) {
			return data;
		}

		TLType thenType = ((SimpleNode) node.jjtGetChild(0).jjtGetChild(1)).getType();
		TLType elseType = ((SimpleNode) node.jjtGetChild(0).jjtGetChild(2)).getType();

		TLType ret = thenType.promoteWith(elseType);
		if (ret.isError()) {
			error(node, "Types of expressions mismatch: '" + thenType.name() + "' and '" + elseType.name() + "'");
			node.setType(TLType.ERROR);
		}
		
		// generate type casts if necessary
		castIfNeeded((SimpleNode)node.jjtGetChild(0),1,ret);
		castIfNeeded((SimpleNode)node.jjtGetChild(0),2,ret);

		node.setType(ret);
		return data;
	}
	
	
	@Override
	public Object visit(CLVFOr node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}

		TLType lhs = ((SimpleNode) node.jjtGetChild(0)).getType();
		TLType rhs = ((SimpleNode) node.jjtGetChild(1)).getType();

		if (lhs.isBoolean() && rhs.isBoolean()) {
			node.setType(TLTypePrimitive.BOOLEAN);
			return data;
		}

		// any other configuration is incorrect
		node.setType(TLType.ERROR);
		error(node, "Operator '||' is not defined for types: " + "'" + lhs.name() + "' and '" + rhs.name() + "'");

		return data;
	}
	

	@Override
	public Object visit(CLVFParameters node, Object data) {
		super.visit(node, data);
		node.setType(TLType.VOID);
		checkChildren(node);
		return data;
	}
	
	@Override
	public Object visit(CLVFPostfixExpression node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}

		SimpleNode operand = (SimpleNode) node.jjtGetChild(0);
		/*
		 * Postfix (as well as prefix) operator cannot be applied onto 
		 * field-access expression as we are not able to determine if it
		 * is an input or output field. Writing (increment) to input field
		 * would be prohibited, while reading (original value) from output
		 * field would be prohibited as well.
		 */
		if (operand.getId() != TransformLangParserTreeConstants.JJTIDENTIFIER) {
			error(node, "Illegal argument to ++/-- operator");
			node.setType(TLType.ERROR);
			return data;
		}
		if (operand.getType().isNumeric()) {
			node.setType(operand.getType());
		} else {
			error(node, "Expression does not have a numeric type");
			node.setType(TLType.ERROR);
		}

		return data;

	}

	
	@Override
	public Object visit(CLVFPrintErrNode node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}
		
		CLVFArguments args = (CLVFArguments)node.jjtGetChild(0);
		TLType[] formal = new TLType[]{TLType.OBJECT,TLTypePrimitive.BOOLEAN};
		TLType[] actual = new TLType[args.jjtGetNumChildren()];
		for (int i=0; i<actual.length; i++) {
			actual[i] = ((SimpleNode)args.jjtGetChild(i)).getType();
		}
		if (actual.length >= 1 && actual.length <= 2) {
			if (formal[0].canAssign(actual[0])) {
				if (actual.length > 1) {
					if (formal[1].canAssign(actual[1])) {
						node.setType(TLType.VOID);
						return data;
					}
				} else {
					node.setType(TLType.VOID);
					return data;
				}
			}
		}
		
		error(node,functionErrorMessage("print_err", formal, actual));
		node.setType(TLType.ERROR);
		return data;
	}

	@Override
	public Object visit(CLVFPrintLogNode node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}
		
		CLVFArguments args = (CLVFArguments)node.jjtGetChild(0);
		// let's create a dummy log level formal parameter
		TLType[] formal = new TLType[] {
				TLType.createTypeSymbol(TransformLangParserConstants.LOGLEVEL_INFO),
				TLType.OBJECT
		};
		TLType[] actual = new TLType[args.jjtGetNumChildren()];
		for (int i=0; i<actual.length; i++) {
			actual[i] = ((SimpleNode)args.jjtGetChild(i)).getType();
		}
		if (actual.length == 2) {
			// first parameter must be a log level 
			if (actual[0].isTypeSymbol() && ((TLTypeSymbol)actual[0]).isLogLevel()) {
				// second parameter can be any object
				if (formal[1].canAssign(actual[1])) {
					node.setType(TLType.VOID);
					return data;
				}
			}
		} else if (actual.length == 1) {
			if (formal[0].canAssign(actual[0])) {
				// the argument can be any type
				node.setType(TLType.VOID);
				return data;
			}
		}
		
		error(node,functionErrorMessage("print_log", formal, actual));
		node.setType(TLType.ERROR);
		return data;
	}
	
	@Override
	public Object visit(CLVFPrintStackNode node, Object data) {
		super.visit(node, data);
		
		// print_stack has no parameters
		CLVFArguments args = (CLVFArguments)node.jjtGetChild(0);
		if (args.jjtHasChildren()) {
			TLType[] actual = new TLType[args.jjtGetNumChildren()];
			for (int i=0; i<actual.length; i++) {
				actual[i] = ((SimpleNode)args.jjtGetChild(i)).getType();
			}
			error(node,functionErrorMessage("print_stack", new TLType[0], actual));
			node.setType(TLType.ERROR);
			return data;
		}
		
		node.setType(TLType.VOID);
		return data;
	}	
	
	@Override
	public Object visit(CLVFRaiseErrorNode node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}
		
		if (!isFunctionNodeValid(node, "raise_error", new TLType[]{TLTypePrimitive.STRING})) {
			return data;
		}
		
		
		node.setType(TLType.VOID);
		return data;
	}
	

	@Override
	public Object visit(CLVFReturnStatement node, Object data) {
		super.visit(node, data);
		
		if (!checkChildren(node)) {
			return data;
		}
		
		if (activeFunction == null) {
			error(node,"Misplaced return statement","Return statement can only appear inside function declaration");
			node.setType(TLType.ERROR);
			return data;
		}
		
		TLType funcType = activeFunction.getType();
		if (node.jjtHasChildren()) {
			// return statement has an expression for return value -> check it
			TLType retType = ((SimpleNode)node.jjtGetChild(0)).getType();
			if (! funcType.canAssign(retType)) {
				error(node,"Can't convert from '" + retType.name() + "' to '" + funcType.name() + "'");
				node.setType(TLType.ERROR);
				return data;
			} else {
				castIfNeeded(node,0,funcType);
			}
			
			node.setType(funcType);
		} else {
			// return without expression -> function must return void
			if (!funcType.isVoid()) {
				error(node, "Function must return a value of type '" + funcType.name() + "'");
				node.setType(TLType.ERROR);
				return data;
			}
			
			node.setType(TLType.VOID);
		}

		return data;
	}
	
	@Override
	public Object visit(CLVFSequenceNode node, Object data) {
		// nothing to do
		return data;
	}
	
	@Override
	public Object visit(CLVFStart node, Object data) {
		super.visit(node, data);
		node.setType(TLType.VOID);
		checkChildren(node);
		return data;
	}
	
	@Override
	public Object visit(CLVFStartExpression node, Object data) {
		super.visit(node, data);
		node.setType(((SimpleNode)node.jjtGetChild(0)).getType());
		return data;
	}
	
	@Override
	public Object visit(CLVFSubNode node, Object data) {
		super.visit(node, data);

		// propagate error
		if (!checkChildren(node)) {
			return data;
		}

		SimpleNode lhs = (SimpleNode) node.jjtGetChild(0);
		SimpleNode rhs = (SimpleNode) node.jjtGetChild(1);

		// numeric addition
		TLType result = checkArithmeticOperator(lhs, rhs);
		if (result.isError()) {
			error(node, "Operator '-' is not defined for types: " + "'" + lhs.getType().name() 
					+ "' and '" + rhs.getType().name() + "'");
		} else {
			castIfNeeded(node,0,result);
			castIfNeeded(node,1,result);
		}

		node.setType(result);
		return data;
	}

	@Override
	public Object visit(CLVFSwitchStatement node, Object data) {
		super.visit(node, data);
		
		node.setType(TLType.VOID);
		checkChildren(node);

		// if the switch expression is in error, we can't check further
		final TLType switchType = ((SimpleNode)node.jjtGetChild(0)).getType();
		if (switchType.isError()) {
			return data;
		} 
		
		if (!switchType.isPrimitive()) {
			error((SimpleNode)node.jjtGetChild(0),"Illegal type of switch expression '" + switchType.name() + "'");
			node.setType(TLType.ERROR);
			return data;
		}
		
		// we visit each case statement separately and check its expression type
		for (int i=1; i<node.jjtGetNumChildren(); i++) {
			SimpleNode child = (SimpleNode)node.jjtGetChild(i);
			if (child.getId() != TransformLangParserTreeConstants.JJTCASESTATEMENT) {
				continue;
			}
			
			final CLVFCaseStatement caseStm = (CLVFCaseStatement)child;
			
			// nothing to check for 'default' statement
			if (caseStm.isDefaultCase()) {
				continue;
			}
			
			// check case expression type if it is not in error
			TLType caseType = ((SimpleNode)caseStm.jjtGetChild(0)).getType();
			if (caseType.isError()) {
				continue;
			}
			
			if (!switchType.canAssign(caseType)) {
				error((SimpleNode)caseStm.jjtGetChild(0),"Cannot convert from '" 
						+ caseType.name() + "' to '" + switchType.name() + "'");
				node.setType(TLType.ERROR);
			} else {
				castIfNeeded(caseStm,0,switchType);
			}
		}
		
		// node type was set to void already (or contains error)
		return data;
		
	}
	
	
	@Override
	public Object visit(CLVFType node, Object data) {
		// nothing to do
		return data;
	}
	
	
	@Override
	public Object visit(CLVFWhileStatement node, Object data) {
		super.visit(node, data);
		node.setType(TLType.VOID);
		checkChildren(node);
		
		SimpleNode expr = (SimpleNode)node.jjtGetChild(0);
		if (expr.getType().isError()) {
			return data;
		}
		
		if (! expr.getType().isBoolean()) {
			error(expr,"Cannot convert from '" + expr.getType().name() + 
					"' to '" + TLTypePrimitive.BOOLEAN.name() + "'");
		}
		
		return data;
	}

	
	
	@Override
	public Object visit(CLVFUnaryExpression node, Object data) {
		super.visit(node, data);

		if (!checkChildren(node)) {
			return data;
		}

		SimpleNode operand = (SimpleNode) node.jjtGetChild(0);
		switch (node.getOperator()) {
		case TransformLangParserConstants.INCR:
		case TransformLangParserConstants.DECR:
			/*
			 * Postfix (as well as prefix) operator cannot be applied onto 
			 * field-access expression as we are not able to determine if it
			 * is an input or output field. Writing (increment) to input field
			 * would be prohibited, while reading (original value) from output
			 * field would be prohibited as well.
			 */
			if (operand.getId() != TransformLangParserTreeConstants.JJTIDENTIFIER) {
				error(node, "Illegal argument to ++/-- operator");
				node.setType(TLType.ERROR);
				return data;
			}
		case TransformLangParserConstants.MINUS:
			if (operand.getType().isNumeric()) {
				node.setType(operand.getType());
			} else {
				error(node, "Expression does not have a numeric type");
				node.setType(TLType.ERROR);
			}
			break;
		case TransformLangParserConstants.NOT:
			if (TLTypePrimitive.BOOLEAN.canAssign(operand.getType())) {
				node.setType(TLTypePrimitive.BOOLEAN);
			} else {
				error(node, "Operator '!' is not defined for type '" + operand.getType().name() + "'");
				node.setType(TLType.ERROR);
			}
			break;
		default:
			error(node, "Unknown prefix operator (" + node.getOperator() + ")");
			throw new IllegalArgumentException("Unknown prefix operator (" + node.getOperator() + ")");
		}

		return data;
	}

	@Override
	public Object visit(CLVFVariableDeclaration node, Object data) {
		super.visit(node, data);
		if (node.jjtGetNumChildren() < 2) {
			// no initializer - no work to do
			return data;
		}

		// check if initializer type matches the variable type
		TLType lhs = node.getType();
		TLType rhs = ((SimpleNode) node.jjtGetChild(1)).getType();

		// initializer is in error -> can't check anything further
		if (rhs.isError()) {
			node.setType(TLType.ERROR);
			return data;
		} else {
			castIfNeeded(node,1,lhs);
		}

		// check if initializer return type matches declared variable type
		if (!lhs.canAssign(rhs)) {
			error(node, "Type mismatch: cannot convert from " + "'" + rhs.name() + "' to '" + lhs.name() + "'");
		}
		return data;
	}

	
	/**
	 * Generates node representing a type cast operation.
	 * This is only necessary for decimal and string types as Java will handle
	 * primitive type casts for us.
	 * 
	 * @param parent	parent of the node to wrap into cast node
	 * @param index		index of the node to wrap
	 * @param toType	target type for cast
	 */
	private void castIfNeeded(SimpleNode parent, int index, TLType toType) {
		final SimpleNode child = (SimpleNode)parent.jjtGetChild(index);
		
		if (child.getType().equals(toType) || child.getType().isNull()) {
			return;
		}
		
		final CastNode c = new CastNode(CAST_NODE_ID,child.getType(),toType);
		c.jjtAddChild(child, 0);
		c.jjtSetParent(parent);
		parent.jjtAddChild(c, index);
	}
	
	
	/**
	 * Specialization of {@link #castIfNeeded(SimpleNode, int, TLType)} for
	 * function call node parameters
	 * @param node
	 * @param formalParameters
	 * @param varArg
	 */
	private void castIfNeeded(CLVFArguments node , TLType[] formal) {
		int i=0;
		while (i < formal.length) {
			// do not cast parameterized types or type symbols
			if (formal[i].isParameterized() || formal[i].isTypeSymbol()) {
				i++;
				continue;
			}
			
			castIfNeeded(node,i,formal[i]);
			i++;
		}
		
		if (formal.length == node.jjtGetNumChildren()) {
			return;
		}
		
		// process possible variable arguments
		final TLType varArgType = formal[formal.length-1];
		
		// no need to cast when parameterized
		if (! varArgType.isParameterized()) {
			while (i < node.jjtGetNumChildren()) {
				castIfNeeded(node,i,varArgType);
				i++;
			}
		}
		
	}
	
	
	/**
	 * Iterates over children types for errors and propagates the error on node
	 * 
	 * @param node
	 *            to check
	 * @return true when children are OK, false otherwise
	 */
	private boolean checkChildren(SimpleNode node) {
		for (int i = 0; i < node.jjtGetNumChildren(); i++) {
			TLType type = ((SimpleNode) node.jjtGetChild(i)).getType();
			if (type.isError()) {
				node.setType(TLType.ERROR);
				break;
			}
		}

		// the node type can still be null - can't use .isError()
		return node.getType() != TLType.ERROR;
	}

	private TLType checkLogicalOperator(TLType lhs, TLType rhs) {
		if (lhs.isString()) {
			if (rhs.isString()) {
				return TLTypePrimitive.STRING;
			}

			return TLType.ERROR;
		}

		if (lhs.isDate()) {
			if (rhs.isDate()) {
				return TLTypePrimitive.DATETIME;
			}

			return TLType.ERROR;
		}

		if (lhs.isBoolean()) {
			if (rhs.isBoolean()) {
				return TLTypePrimitive.BOOLEAN;
			}

			return TLType.ERROR;
		}

		if (lhs.isNumeric()) {
			if (rhs.isNumeric()) {
				return lhs.promoteWith(rhs);
			}

			return TLType.ERROR;
		}

		return TLType.ERROR;

	}
	
	private boolean isBindingValid(TLType formal, TLType actual) {
		if (!formal.isParameterized()) {
			return true;
		}
		
		if (formal.isTypeVariable()) {
			TLType bound = typeVarMapping.get(formal.name()); 
			if (bound == null) {
				// formal is a type variable without binding: bind it to actual
				typeVarMapping.put(formal.name(),actual);
				return true;
			} else if (bound.equals(actual)) {
				// existing equivalent binding found: ok
				return true;
			}
		} else if (formal.isList()) {
			return isBindingValid(((TLTypeList)formal).getElementType(), ((TLTypeList)actual).getElementType());
		
		} else  if (formal.isMap()) {
			TLTypeMap f = (TLTypeMap)formal;
			TLTypeMap a = (TLTypeMap)actual;
			return isBindingValid(f.getKeyType(), a.getKeyType()) && isBindingValid(f.getValueType(), a.getValueType());
		}
		
		return false;
 	}
	
	private int functionDistance(TLType[] actual, TLType[] formal, boolean allowVarArg) {
		// get ready to check type variable bindings
		typeVarMapping.clear(); 
		
		if (actual.length < formal.length) {
			// completely incorrect number of parameters
			return Integer.MAX_VALUE;
		}
		
		if (formal.length == 0) {
			return actual.length == 0 ? 0 : Integer.MAX_VALUE;
		}
		
		// here: actual.length >= formal.length, both > 0
		int result = 0;
		int sum = 0;
		int i = 0;

		while (i < formal.length) {
			
			result = TLType.distance(actual[i], formal[i]);
			
			// illegal conversion: fail quickly
			if (result == Integer.MAX_VALUE) {
				return result;
			}

			if (!isBindingValid(formal[i], actual[i])) {
				return Integer.MAX_VALUE;
			}

			// valid type conversion and no colliding bindings - let's continue
			sum += result;
			i++;
		}

		// the same number of arguments and everything was matched
		if (formal.length == actual.length) {
			return sum;
		}
		
		// actual.length > formal.length: process with varArgs
		if (allowVarArg) {
		
			// here: process possible varArgs, case: actual.length > formal.length
			final TLType varArgType = formal[formal.length-1];
			
			// penalize variable argument variants to so that non-vararg are chosen first
			sum++;
			
			while (i < actual.length) {
				result = TLType.distance(actual[i], varArgType);
				if (result == Integer.MAX_VALUE) {
					return result;
				}
				
				// no need to check for binding here, because the var-arg argument can't change
				// the binding. we would have found it already with the last non-vararg argument
				
				
				sum += result;
				i++;
			}
			
			return sum;
		} 
		
		// actual.length > formal.length, but varArg is false: error
		return Integer.MAX_VALUE;
	}
	
	
	private TLType bindType(TLType returnType) {
		if (! returnType.isParameterized()) {
			return returnType;
		}
		
		// if the return type is type variable itself  - return it's mapping
		if (returnType.isTypeVariable()) {
			return typeVarMapping.get(returnType.name());
		}

		if (returnType.isList()) {
			return TLType.createList(bindType(((TLTypeList)returnType).getElementType()));
		}
		
		// if map or list, return corresponding type with correct binding
		if (returnType.isMap()) {
			TLType keyType = bindType(((TLTypeMap)returnType).getKeyType());
			TLType valueType = bindType(((TLTypeMap)returnType).getValueType());
			return TLType.createMap(keyType,valueType); 
		}
		
		throw new IllegalArgumentException("Unreachable code");
	}

	private String functionErrorMessage(String name, TLType[] formal, TLType[] actual) {
		StringBuffer msg = new StringBuffer("Function ").append(name).append("(");
		for (int i = 0; i < formal.length; i++) {
			msg.append(formal[i].name());
			if (i < formal.length - 1) {
				msg.append(',');
			}
		}
		
		msg.append(") is not applicable for the arguments (");
		for (int i = 0; i < actual.length; i++) {
			msg.append(actual[i].name());
			if (i < actual.length - 1) {
				msg.append(',');
			}
		}
		msg.append(')');

		return msg.toString();
	}

	/**
	 * Checks if given node is a reference to one of the following 1. local variable reference (function parameter) 2.
	 * list field reference
	 * 
	 * @return true if the node represents a direct variable reference
	 */
	private boolean isVariableReference(SimpleNode node) {
		switch (node.getId()) {
		case TransformLangParserTreeConstants.JJTMEMBERACCESSEXPRESSION:
		case TransformLangParserTreeConstants.JJTFIELDACCESSEXPRESSION:
		case TransformLangParserTreeConstants.JJTIDENTIFIER:
			return true;
		default:
			return false;
		}
	}

	private TLType checkArithmeticOperator(SimpleNode lhs, SimpleNode rhs) {
		if (lhs.getType().isNumeric()) {
			if (rhs.getType().isNumeric()) {
				return lhs.getType().promoteWith(rhs.getType());
			}
		}

		return TLType.ERROR;

	}
	
	
	private boolean isFunctionNodeValid(SimpleNode node, String name, TLType[] formal) {
		SimpleNode args = (SimpleNode)node.jjtGetChild(0);
		TLType[] actual = new TLType[args.jjtGetNumChildren()];
		for (int i = 0; i<args.jjtGetNumChildren(); i++) {
			actual[i] = ((SimpleNode)args.jjtGetChild(i)).getType();
		}
		
		if (formal.length != actual.length) {
			error(node,functionErrorMessage(name, formal, actual));
			node.setType(TLType.ERROR);
			return false;
		}
		
		for (int i=0; i<formal.length; i++) {
			if (!formal[i].canAssign(actual[i])) {
				error(node,functionErrorMessage(name, formal,actual));
				node.setType(TLType.ERROR);
				return false;
			}
		}
		
		return true;

	}
	
	private TLType[] getActualTypes(SimpleNode node ) {
		SimpleNode args = (SimpleNode)node.jjtGetChild(0);
		TLType[] actual = new TLType[args.jjtGetNumChildren()];
		for (int i = 0; i<args.jjtGetNumChildren(); i++) {
			actual[i] = ((SimpleNode)args.jjtGetChild(i)).getType();
		}
		
		return actual;
	}

	
	
	// ----------------- Error Reporting --------------------------

	private void error(SimpleNode node, String error) {
		problemReporter.error(node.getBegin(), node.getEnd(), error, null);
	}

	private void error(SimpleNode node, String error, String hint) {
		problemReporter.error(node.getBegin(), node.getEnd(), error, hint);
	}

	private void warn(SimpleNode node, String error) {
		problemReporter.warn(node.getBegin(), node.getEnd(), error, null);
	}

	private void warn(SimpleNode node, String error, String hint) {
		problemReporter.warn(node.getBegin(), node.getEnd(), error, hint);
	}

	
}