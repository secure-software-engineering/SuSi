package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

/**
 * Common class for all features that have to do with the method name
 *
 * @author Steven Arzt
 *
 */
public class MethodNameStartsWithFeature implements IFeature {

	private final String startsWith;
	
	public MethodNameStartsWithFeature(String startsWith, float weight) {
		this.startsWith = startsWith;
	}
	
	@Override
	public Type applies(AndroidMethod method) {
		return (method.getMethodName().startsWith(this.startsWith)? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Method name starts with " + this.startsWith + ">";
	}

}
