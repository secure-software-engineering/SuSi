package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

public class MethodHasParametersFeature implements IFeature {

	public MethodHasParametersFeature(float weight) {
	}
	
	@Override
	public Type applies(AndroidMethod method) {
		return (method.getParameters().size() > 0 ? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Method has parameters>";
	}

}
