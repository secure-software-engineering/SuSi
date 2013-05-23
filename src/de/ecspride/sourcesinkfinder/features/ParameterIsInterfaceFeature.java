package de.ecspride.sourcesinkfinder.features;

import soot.Scene;
import soot.SootClass;
import soot.jimple.infoflow.android.data.AndroidMethod;

/**
 * Feature which checks whether the current method gets an interface as a
 * parameter
 *
 * @author Steven Arzt
 *
 */
public class ParameterIsInterfaceFeature extends AbstractSootFeature {
	
	public ParameterIsInterfaceFeature(String androidJAR) {
		super(androidJAR);
	}
	
	@Override
	public Type appliesInternal(AndroidMethod method) {
		for (String paramType : method.getParameters()) {
			SootClass sc = Scene.v().forceResolve(paramType, SootClass.HIERARCHY);
			if (sc == null)
				return Type.NOT_SUPPORTED;
			return sc.isInterface() ? Type.TRUE : Type.FALSE;
		}
		// No interface type found
		return Type.FALSE;
	}
	
	@Override
	public String toString() {
		return "<Parameter is interface>";
	}

}
