package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

/**
 * Common class for all features that have to do with the method name
 *
 * @author Siegfried Rasthofer
 *
 */
public class MethodNameEndsWithFeature implements IFeature {

	private final String endsWith;
	
	public MethodNameEndsWithFeature(String endsWith) {
		this.endsWith = endsWith;
	}

	@Override
	public Type applies(AndroidMethod method) {
		String methodNameLowerCase = method.getMethodName().toLowerCase();
		String endsWithLowerCase = endsWith.toLowerCase();
		return (methodNameLowerCase.endsWith(endsWithLowerCase)? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Method name ends with " + this.endsWith + ">";
	}

}
