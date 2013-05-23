package de.ecspride.sourcesinkfinder.features;

import java.util.HashSet;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.data.AndroidMethod;

/**
 * Feature that checks whether a method starting with a specific string is
 * invoked on one of the parameters
 *
 * @author Steven Arzt, Siegfried Rasthofer
 *
 */
public class MethodInvocationOnParameterFeature extends AbstractSootFeature {
	
	private final String methodName;

	public MethodInvocationOnParameterFeature(String androidJAR, String methodName) {
		super(androidJAR);
		this.methodName = methodName;
	}
	
	@Override
	public Type appliesInternal(AndroidMethod method) {
		SootMethod sm = getSootMethod(method);
		
		if (sm == null) {
			System.err.println("Method not declared: " + method);
			return Type.NOT_SUPPORTED;
		}
		
		// We are only interested in setters
		if (!sm.isConcrete())
			return Type.NOT_SUPPORTED;

		try {
			Set<Value> paramVals = new HashSet<Value>();
			for (Unit u : sm.retrieveActiveBody().getUnits()) {
				// Collect the parameters
				if (u instanceof IdentityStmt) {
					IdentityStmt id = (IdentityStmt) u;
					if (id.getRightOp() instanceof ParameterRef)
						paramVals.add(id.getLeftOp());
				}
				
				// Check for invocations
				if (u instanceof Stmt) {
					Stmt stmt = (Stmt) u;
					if (stmt.containsInvokeExpr())
						if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
							InstanceInvokeExpr iinv = (InstanceInvokeExpr) stmt.getInvokeExpr();
							if (paramVals.contains(iinv.getBase()))
								if (iinv.getMethod().getName().startsWith(methodName))
									return Type.TRUE;
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
		return "<Method " + methodName + "invoked on parameter object>";
	}

}
