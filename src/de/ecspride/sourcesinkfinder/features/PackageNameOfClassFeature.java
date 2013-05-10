package de.ecspride.sourcesinkfinder.features;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

/**
 * This feature checks the name of the package
 * @author Siegfried Rasthofer
 */
public class PackageNameOfClassFeature implements IFeature {
	private final String packageNameOfClass;
	
	public PackageNameOfClassFeature(String packageNameOfClass, float weight){
		this.packageNameOfClass = packageNameOfClass;
	}

	@Override
	public Type applies(AndroidMethod method) {
		String otherPackageNameOfClass = method.getClassName().substring(0, method.getClassName().lastIndexOf("."));
		
		return (otherPackageNameOfClass.equals(packageNameOfClass) ? Type.TRUE : Type.FALSE);
	}
	
	@Override
	public String toString() {
		return "<Package path of method class-name is: " + packageNameOfClass + ">";
	}
}
