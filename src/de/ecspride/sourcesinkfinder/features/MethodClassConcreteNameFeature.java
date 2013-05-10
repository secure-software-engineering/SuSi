package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

public class MethodClassConcreteNameFeature implements IFeature {

	private final String className;
	
	public MethodClassConcreteNameFeature(String className){
		this.className = className;
	}

	@Override
	public Type applies(AndroidMethod method) {
		return (method.getClassName().equals(className)? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Method is part of class " + className + ">";
	}

}
