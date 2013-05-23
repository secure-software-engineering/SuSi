package de.ecspride.sourcesinkfinder.features;

import java.util.HashSet;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.data.AndroidMethod;

/**
 * Feature that checks whether the current method begins with "get", and there
 * is a corresponding "set" method in the class.
 *
 * @author Steven Arzt, Siegfried Rasthofer
 *
 */
public class MethodIsRealSetterFeature extends AbstractSootFeature {

	public MethodIsRealSetterFeature(String androidJAR) {
		super(androidJAR);
	}
	
	@Override
	public Type appliesInternal(AndroidMethod method) {
		SootMethod sm = getSootMethod(method);
		
		if (sm == null) {
			System.err.println("Method not declared: " + method);
			return Type.NOT_SUPPORTED;
		}
		
		// We are only interested in setters
		if (!sm.getName().startsWith("set"))
			return Type.FALSE;
		if (!sm.isConcrete())
			return Type.NOT_SUPPORTED;

		try {
			Set<Value> paramVals = new HashSet<Value>();
			for (Unit u : sm.retrieveActiveBody().getUnits()) {
				if (u instanceof IdentityStmt) {
					IdentityStmt id = (IdentityStmt) u;
					if (id.getRightOp() instanceof ParameterRef)
						paramVals.add(id.getLeftOp());
				}
				else if (u instanceof AssignStmt) {
					AssignStmt assign = (AssignStmt) u;
					if (paramVals.contains(assign.getRightOp()))
						if (assign.getLeftOp() instanceof InstanceFieldRef)
							return Type.TRUE;
				}
				
				if (u instanceof Stmt) {
					Stmt stmt = (Stmt) u;
					if (stmt.containsInvokeExpr()) {
						if (stmt.getInvokeExpr().getMethod().getName().startsWith("get"))
							for (Value arg: stmt.getInvokeExpr().getArgs())
								if (paramVals.contains(arg))
									return Type.FALSE;
					}
				}
			}
			return Type.FALSE;
		}catch (Exception ex) {
			System.err.println("Something went wrong:");
			ex.printStackTrace();
			return Type.NOT_SUPPORTED;
		}
	}
	
	@Override
	public String toString() {
		return "<Method is lone getter or setter>";
	}

}
