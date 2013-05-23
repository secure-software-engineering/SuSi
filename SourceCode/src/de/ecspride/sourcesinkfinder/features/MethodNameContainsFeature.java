package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

/**
 * Common class for all features that have to do with the method name
 *
 * @author Steven Arzt
 */
public class MethodNameContainsFeature implements IFeature {

	private final String endsWith;
	
	public MethodNameContainsFeature(String endsWith) {
		this.endsWith = endsWith.toLowerCase();
	}

	@Override
	public Type applies(AndroidMethod method) {
		return (method.getMethodName().toLowerCase().contains(endsWith) ? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Method name contains " + this.endsWith + ">";
	}

}
