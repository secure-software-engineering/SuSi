package de.ecspride.sourcesinkfinder.features;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Constant;
import soot.jimple.ReturnStmt;
import soot.jimple.infoflow.android.data.AndroidMethod;

/**
 * Feature that checks whether the current method returns a constant value
 *
 * @author Steven Arzt, Siegfried Rasthofer
 *
 */
public class MethodReturnsConstantFeature extends AbstractSootFeature {

	public MethodReturnsConstantFeature(String mapsJAR, String androidJAR) {
		super(mapsJAR, androidJAR);
	}
	
	@Override
	public Type appliesInternal(AndroidMethod method) {
		SootMethod sm = getSootMethod(method);
		if (sm == null || !sm.isConcrete())
			return Type.NOT_SUPPORTED;		
		try {
			Body body = null;
			try{
				body = sm.retrieveActiveBody();
			}catch(Exception ex){
				return Type.NOT_SUPPORTED;
			}
			
			for (Unit u : body.getUnits())
				if (u instanceof ReturnStmt) {
					ReturnStmt ret = (ReturnStmt) u;
					if (ret.getOp() instanceof Constant)
						return Type.TRUE;
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
		return "<Method returns constant>";
	}

}
