package de.ecspride.sourcesinkfinder.analysis;

import soot.Scene;
import soot.options.Options;

public class InitializeSoot {
	
	public InitializeSoot(){
		
	}
	
	private String[] buildArgs(String path){
		String[] result = {
			"-w",
			"-no-bodies-for-excluded",
			"-include-all",
			"-p",
			"cg.spark",
			"on",
			"-cp",
			path,
			"-p",
			"jb",
			"use-original-names:true",
			"-f",
			"n",
			//do not merge variables (causes problems with PointsToSets)
			"-p",
			"jb.ulp",
			"off"
		};
		
		return result;
	}
	
	public void initialize(String path){
		String[] args = buildArgs(path);

		Options.v().set_allow_phantom_refs(true);
		Options.v().set_prepend_classpath(true);
		Options.v().set_output_format(Options.output_format_none);
		Options.v().parse(args);
		
		Options.v().set_whole_program(true);
		Scene.v().addBasicClass(Object.class.getName());
		Scene.v().loadNecessaryClasses();
	}

}
