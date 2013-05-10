package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

/**
 * This feature checks the name of the package
 * @author Siegfried Rasthofer
 */
public class BaseNameOfClassPackageName implements IFeature {
	private final String baseNameOfClassPackageName;
	
	public BaseNameOfClassPackageName(String baseNameOfClassPackageName){
		this.baseNameOfClassPackageName = baseNameOfClassPackageName;
	}
	
	@Override
	public Type applies(AndroidMethod method) {
		String[] classNameParts = method.getClassName().split("\\.");
		String otherBaseNameOfClassPackageName = classNameParts[classNameParts.length -2];
		
		return (otherBaseNameOfClassPackageName.equals(baseNameOfClassPackageName) ? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Base name of class package name: " + baseNameOfClassPackageName + ">";
	}
}
