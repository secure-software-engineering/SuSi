package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

public class MethodClassEndsWithNameFeature implements IFeature {

	private final String partOfName;
	
	public MethodClassEndsWithNameFeature(String partOfName){
		this.partOfName = partOfName;
	}
	
	@Override
	public Type applies(AndroidMethod method) {
		return (method.getClassName().endsWith(partOfName)? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Method is part of class that ends with " + partOfName + ">";
	}
}
