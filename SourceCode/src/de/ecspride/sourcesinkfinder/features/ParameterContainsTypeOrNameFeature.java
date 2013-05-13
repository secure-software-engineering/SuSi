package de.ecspride.sourcesinkfinder.features;

import java.util.List;

import soot.jimple.infoflow.android.data.AndroidMethod;
import de.ecspride.sourcesinkfinder.IFeature;

/**
 * Common class for all features that have to do with the method name
 *
 * @author Steven Arzt
 *
 */
public class ParameterContainsTypeOrNameFeature implements IFeature {

	private final String insideName;
	
	public ParameterContainsTypeOrNameFeature(String insideName) {
		this.insideName = insideName.toLowerCase();
	}
	
	@Override
	public Type applies(AndroidMethod method) {
		List<String> paramList = method.getParameters();
		for(String param : paramList){
			if(param.toLowerCase().contains(this.insideName))
				return Type.TRUE;
		}
		return Type.FALSE;
	}
	
	@Override
	public String toString() {
		return "<Parameter type contains " + this.insideName + ">";
	}

}
