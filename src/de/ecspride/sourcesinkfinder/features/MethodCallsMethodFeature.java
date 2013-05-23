package de.ecspride.sourcesinkfinder.features;

import java.util.ArrayList;
import java.util.List;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.data.AndroidMethod;

/**
 * Feature which checks whether the current method (indirectly) calls another
 * one
 *
 * @author Steven Arzt, Siegfried Rasthofer
 *
 */
public class MethodCallsMethodFeature extends AbstractSootFeature {
	
	private final String className;
	private final String methodName;
	private final boolean substringMatch;
	
	public MethodCallsMethodFeature(String androidJAR, String methodName) {
		this(androidJAR, "", methodName);
	}

	public MethodCallsMethodFeature(String androidJAR, String className,
			String methodName) {
		this(androidJAR, className, methodName, false);
	}
	
	public MethodCallsMethodFeature(String androidJAR, String className,
			String methodName, boolean substringMatch) {
		super(androidJAR);
		this.className = className;
		this.methodName = methodName;
		this.substringMatch = substringMatch;
	}

	@Override
	public Type appliesInternal(AndroidMethod method) {
		try {
			SootMethod sm = getSootMethod(method);
			if (sm == null) {
				System.err.println("Method not declared: " + method);
				return Type.NOT_SUPPORTED;
			}
			return checkMethod(sm, new ArrayList<SootMethod>());
		}catch (Exception ex) {
			System.err.println("Something went wrong:");
			ex.printStackTrace();
			return Type.NOT_SUPPORTED;
		}
	}
	
	public Type checkMethod(SootMethod method, List<SootMethod> doneList) {
		if (doneList.contains(method))
			return Type.NOT_SUPPORTED;
		if (!method.isConcrete())
			return Type.NOT_SUPPORTED;
		doneList.add(method);

		try {
			Body body = null;
			try{
				body = method.retrieveActiveBody();
			}catch(Exception ex){
				return Type.NOT_SUPPORTED;
			}
			
			for (Unit u : body.getUnits()) {
				if (!(u instanceof Stmt))
					continue;
				Stmt stmt = (Stmt) u;
				if (!stmt.containsInvokeExpr())
					continue;
				
				InvokeExpr inv = stmt.getInvokeExpr();
				if ((substringMatch &&  inv.getMethod().getName().contains(this.methodName))
						|| inv.getMethod().getName().startsWith(this.methodName)) {
					if (this.className.isEmpty() || this.className.equals
							(inv.getMethod().getDeclaringClass().getName()))
					return Type.TRUE;
				}
				else
					if (checkMethod(inv.getMethod(), doneList) == Type.TRUE)
						return Type.TRUE;
			}
			return Type.FALSE;
		}
		catch (Exception ex) {
			System.err.println("Oops: " + ex);
			return Type.NOT_SUPPORTED;
		}
	}
	

	@Override
	public String toString() {
		return "Method starting with '" + this.methodName + "' invoked";
	}

}
