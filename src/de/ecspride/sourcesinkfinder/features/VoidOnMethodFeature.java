package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

/**
 * Feature that matches whever a method returns void and the method name starts
 * with "on".
 * 
 * @author Steven Arzt
 *
 */
public class VoidOnMethodFeature implements IFeature {

	public VoidOnMethodFeature() {
	}
	

	@Override
	public Type applies(AndroidMethod method) {
		return (method.getMethodName().startsWith("on") && 
				(method.getReturnType().toString().equals("void")
						|| method.getReturnType().toString().equals("boolean"))? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Method starts with on and has void/bool return type>";
	}

}
