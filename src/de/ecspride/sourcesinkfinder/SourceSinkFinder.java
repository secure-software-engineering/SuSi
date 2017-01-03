package de.ecspride.sourcesinkfinder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import de.ecspride.sourcesinkfinder.IFeature.Type;
import de.ecspride.sourcesinkfinder.features.AbstractSootFeature;
import de.ecspride.sourcesinkfinder.features.BaseNameOfClassPackageName;
import de.ecspride.sourcesinkfinder.features.IsThreadRunFeature;
import de.ecspride.sourcesinkfinder.features.MethodAnonymousClassFeature;
import de.ecspride.sourcesinkfinder.features.MethodBodyContainsObjectFeature;
import de.ecspride.sourcesinkfinder.features.MethodCallsMethodFeature;
import de.ecspride.sourcesinkfinder.features.MethodClassConcreteNameFeature;
import de.ecspride.sourcesinkfinder.features.MethodClassContainsNameFeature;
import de.ecspride.sourcesinkfinder.features.MethodClassEndsWithNameFeature;
import de.ecspride.sourcesinkfinder.features.MethodClassModifierFeature;
import de.ecspride.sourcesinkfinder.features.MethodClassModifierFeature.ClassModifier;
import de.ecspride.sourcesinkfinder.features.MethodHasParametersFeature;
import de.ecspride.sourcesinkfinder.features.MethodIsRealSetterFeature;
import de.ecspride.sourcesinkfinder.features.MethodModifierFeature;
import de.ecspride.sourcesinkfinder.features.MethodModifierFeature.Modifier;
import de.ecspride.sourcesinkfinder.features.MethodNameContainsFeature;
import de.ecspride.sourcesinkfinder.features.MethodNameEndsWithFeature;
import de.ecspride.sourcesinkfinder.features.MethodNameStartsWithFeature;
import de.ecspride.sourcesinkfinder.features.MethodReturnsConstantFeature;
import de.ecspride.sourcesinkfinder.features.ParameterContainsTypeOrNameFeature;
import de.ecspride.sourcesinkfinder.features.ParameterInCallFeature;
import de.ecspride.sourcesinkfinder.features.ParameterInCallFeature.CheckType;
import de.ecspride.sourcesinkfinder.features.ParameterIsInterfaceFeature;
import de.ecspride.sourcesinkfinder.features.PermissionNameFeature;
import de.ecspride.sourcesinkfinder.features.ReturnTypeFeature;
import de.ecspride.sourcesinkfinder.features.VoidOnMethodFeature;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.android.data.AndroidMethod.CATEGORY;
import soot.jimple.infoflow.android.data.AndroidMethodCategoryComparator;
import soot.jimple.infoflow.android.data.parsers.CSVPermissionMethodParser;
import soot.jimple.infoflow.android.data.parsers.PScoutPermissionMethodParser;
import soot.jimple.infoflow.android.data.parsers.PermissionMethodParser;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.rifl.RIFLDocument;
import soot.jimple.infoflow.rifl.RIFLDocument.Assignable;
import soot.jimple.infoflow.rifl.RIFLDocument.Category;
import soot.jimple.infoflow.rifl.RIFLDocument.DomainSpec;
import soot.jimple.infoflow.rifl.RIFLDocument.SourceSinkSpec;
import soot.jimple.infoflow.rifl.RIFLDocument.SourceSinkType;
import soot.jimple.infoflow.rifl.RIFLWriter;
import soot.jimple.infoflow.source.data.ISourceSinkDefinitionProvider;
import soot.jimple.infoflow.source.data.SourceSinkDefinition;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.BayesNet;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.SMO;
import weka.classifiers.rules.JRip;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Range;
import weka.core.converters.ArffSaver;

/**
 * Finds possible sources and sinks in a given set of Android system methods using
 * a probabilistic algorithm trained on a previously annotated sample set.
 *
 * @author Steven Arzt
 *
 */
public class SourceSinkFinder {
	
	private final static boolean ENABLE_PERMISSION = true;

	private final static boolean LOAD_ANDROID = true;
	private final static boolean DIFF = false;
	
	private final static boolean CLASSIFY_CATEGORY = true;

	private final Set<IFeature> featuresSourceSink = initializeFeaturesSourceSink();
	private final Set<IFeature> featuresCategories = initializeFeaturesCategories();
	
	private static String ANDROID;
	
	private static final HashSet<AndroidMethod> methodsWithPermissions = new HashSet<AndroidMethod>();
			
	private final static String WEKA_LEARNER_ALL = "SMO";
	private final static String WEKA_LEARNER_CATEGORIES = "SMO";
	
//	private final static double THRESHOLD = 0.1;
	private final static double THRESHOLD = 0.0; 
	
	private long startSourceSinkAnalysisTime;
	private long sourceSinkAnalysisTime;
	
	private long startCatSourcesTime;
	private long catSourcesTime;
	
	private long startCatSinksTime;
	private long catSinksTime;

	/**
	 * @param args  *First parameter: ANDROID platform jar path
	 * 				*Third - x parameter: names of input
	 * 				*Last parameter: file name of output
	 */
	public static void main(String[] args) {
		try {
			if (args.length < 3) {
				System.out.println("Usage: java de.ecspride.sourcesinkfinder.SourceSinkFinder "
						+ "<androidJAR> <input1>...<inputN> <outputFile>");
				return;
			}
			
			String[] inputs = Arrays.copyOfRange(args, 1, args.length-1);
			
			//set Android paths
			ANDROID = args[0];

			SourceSinkFinder sourceSinkFinder = new SourceSinkFinder();
			sourceSinkFinder.run(inputs, args[args.length - 1]);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void run(String[] inputFiles, String outputFile) throws IOException {
		Set<AndroidMethod> methods = loadMethodsFromFile(inputFiles);
		
		// Prefilter the interfaces
		methods = PrefilterInterfaces(methods);
		
		// Create the custom annotations for derived methods
		createSubclassAnnotations(methods);

		if (LOAD_ANDROID) {
			Set<AndroidMethod> newMethods = new HashSet<AndroidMethod>();
			for (AndroidMethod am : methods)
				if (am.isAnnotated() || am.getCategory() != null)
					newMethods.add(am);
			methods = newMethods;

			// Load the Android stuff
			loadMethodsFromAndroid(methods);
			createSubclassAnnotations(methods);
		}
		
		printStatistics(methods);
		methods = sanityCheck(methods);
		
		// Classify the methods into sources, sinks and neither-nor entries
		startSourceSinkAnalysisTime = System.currentTimeMillis();
		analyzeSourceSinkWeka(methods, outputFile);
		sourceSinkAnalysisTime = System.currentTimeMillis() - startSourceSinkAnalysisTime;
		System.out.println("Time to classify sources/sinks/neither: " + sourceSinkAnalysisTime + " ms");
		
		// Classify the categories
		if(CLASSIFY_CATEGORY){
			//source
			startCatSourcesTime = System.currentTimeMillis();
			analyzeCategories(methods, outputFile, true, false);
			catSourcesTime = System.currentTimeMillis() - startCatSourcesTime;
			System.out.println("Time to categorize sources: " + catSourcesTime + " ms");
			
			//sink
			startCatSinksTime = System.currentTimeMillis();
			analyzeCategories(methods, outputFile, false, true);
			catSinksTime = System.currentTimeMillis() - startCatSinksTime;
			System.out.println("Time to categorize sinks: " + catSinksTime + " ms");
		}
		writeRIFLSpecification(outputFile, methods);
	}
	
	/**
	 * Checks whether there are semantic errors in the given set of Android
	 * methods and tries to filter out duplicates by merging.
	 * @param methods The set of method definitions to check
	 * @return The purged set of Android methods without duplicates
	 */
	private Set<AndroidMethod> sanityCheck(Set<AndroidMethod> methods) {
		Map<String, AndroidMethod> signatureToMethod = new HashMap<>(methods.size());
		
		for (AndroidMethod m1 : methods) {
			String sig = m1.getSignature();
			AndroidMethod m2 = signatureToMethod.get(sig);
			if (m2 == null)
				signatureToMethod.put(sig, m1);
			else {
				if (!m1.equals(m2)) {
					// Merge the annotations
					if (!m1.isAnnotated() && m2.isAnnotated()) {
						m1.setSource(m2.isSource());
						m1.setSink(m2.isSink());
						m1.setNeitherNor(m2.isNeitherNor());
						for (String permission : m2.getPermissions())
							m1.addPermission(permission);
					}
					
					// Merge the permissions
					if (!m2.getPermissions().isEmpty()) {
						for (String permission : m2.getPermissions())
							m1.addPermission(permission);
					}
					
					// Merge the categories
					if (m1.getCategory() == null && m2.getCategory() != null)
						m1.setCategory(m2.getCategory());
				}
			}
		}

		System.out.println("Merged " + (methods.size() - signatureToMethod.size()) + " entries");
		return new HashSet<>(signatureToMethod.values());
	}

	private void printStatistics(Set<AndroidMethod> methods) {
		int sources = 0;
		int sinks = 0;
		int neither = 0;
		for (AndroidMethod am : methods)
			if (am.isSource())
				sources++;
			else if (am.isSink())
				sinks++;
			else if (am.isNeitherNor())
				neither++;
		System.out.println("Annotated sources: " + sources + ", sinks: " + sinks
				+ ", neither: " + neither);
	}

	private void writeResultsToFiles(String targetFileName,
			Set<AndroidMethod> methods, boolean diff) throws IOException {
		// Dump the stuff
		BufferedWriter wr = null;
		try {
			wr = new BufferedWriter(new FileWriter(targetFileName));
			for (AndroidMethod am : methods)
				wr.write(am.toString() + "\n");
			wr.flush();
			wr.close();

			wr = new BufferedWriter(new FileWriter(appendFileName(targetFileName, "_Sources")));
			for (AndroidMethod am : methods)
				if (am.isSource()){
					if(diff && !methodsWithPermissions.contains(am))
						wr.write(am.toString() + "\n");
					else if(!diff)
						wr.write(am.toString() + "\n");
				}
			wr.flush();
			wr.close();
			
			wr = new BufferedWriter(new FileWriter(appendFileName(targetFileName, "_Sinks")));
			for (AndroidMethod am : methods)
				if (am.isSink()){
					if(diff && !methodsWithPermissions.contains(am))
						wr.write(am.toString() + "\n");
					else if(!diff)
						wr.write(am.toString() + "\n");
				}
			wr.flush();
			wr.close();			

			wr = new BufferedWriter(new FileWriter(appendFileName(targetFileName, "_NeitherNor")));
			for (AndroidMethod am : methods)
				if (am.isNeitherNor()){
					if(diff && !methodsWithPermissions.contains(am))
						wr.write(am.toString() + "\n");
					else if(!diff)
						wr.write(am.toString() + "\n");
				}
			wr.flush();
			wr.close();
		}
		finally {
			if (wr != null)
				wr.close();
		}
	}

	
	private void writeCategoryResultsToFiles(String targetFileName,
			Set<AndroidMethod> methods, boolean source, boolean sink, boolean diff) throws IOException {
		// Dump the stuff
		BufferedWriter wr = null;
		ArrayList<AndroidMethod> methodsAsList = new ArrayList<AndroidMethod>(methods);
		Comparator<AndroidMethod> catComparator = new AndroidMethodCategoryComparator();
		Collections.sort(methodsAsList, catComparator);
		try {
			if(source && !sink){
				wr = new BufferedWriter(new FileWriter(appendFileName(targetFileName, "_CatSources")));
				AndroidMethod.CATEGORY currentCat = null;
				for (AndroidMethod am : methodsAsList){
					if (am.isSource()){
						if(currentCat == null || currentCat != am.getCategory()){
							currentCat = am.getCategory();
							if (currentCat == null)
								throw new RuntimeException("NULL category detected");
							wr.write("\n" + currentCat.toString() + ":\n");
						}
						
						if(diff && !methodsWithPermissions.contains(am))
							wr.write(am.getSignatureAndPermissions() + " (" + currentCat.toString() + ")\n");
						else if(!diff)
							wr.write(am.getSignatureAndPermissions() + " (" + currentCat.toString() + ")\n");
					}
				}				
				wr.flush();
				wr.close();
			}
			else if(sink && !source){
				wr = new BufferedWriter(new FileWriter(appendFileName(targetFileName, "_CatSinks")));
				AndroidMethod.CATEGORY currentCat = null;
				for (AndroidMethod am : methodsAsList){
					if (am.isSink()){
						if(currentCat == null || currentCat != am.getCategory()){
							currentCat = am.getCategory();
							wr.write("\n" + currentCat.toString() + ":\n");
						}
					
						if(diff && !methodsWithPermissions.contains(am))
							wr.write(am.getSignatureAndPermissions() + " (" + currentCat.toString() + ")\n");
						else if(!diff)
							wr.write(am.getSignatureAndPermissions() + " (" + currentCat.toString() + ")\n");
					}	
				}
				wr.flush();
				wr.close();	
			}
			else
				throw new RuntimeException("Woops, wrong settings");
		}
		finally {
			if (wr != null)
				wr.close();
		}
	}
	
	private void writeRIFLSpecification(String targetFileName,
			Set<AndroidMethod> methods) throws IOException {
		RIFLDocument doc = new RIFLDocument();
		
		DomainSpec topDomain = doc.new DomainSpec("top");
		DomainSpec bottomDomain = doc.new DomainSpec("bottom");
		
		doc.getDomains().add(topDomain);
		doc.getDomains().add(bottomDomain);
		
		doc.getFlowPolicy().add(doc.new FlowPair(topDomain, topDomain));
		doc.getFlowPolicy().add(doc.new FlowPair(bottomDomain, bottomDomain));
		doc.getFlowPolicy().add(doc.new FlowPair(bottomDomain, topDomain));
		
		Map<String, Category> categoryMap = new HashMap<String, Category>();
		Map<CATEGORY, Assignable> assignableMap = new HashMap<CATEGORY, Assignable>();
		
		for (AndroidMethod am : methods) {
			// Parse the class and package names
			SootMethodAndClass smac = SootMethodRepresentationParser.v().parseSootMethodString
					(am.getSignature());
			String halfSignature = am.getSubSignature().substring(am.getSubSignature().indexOf(" ") + 1);
			
			// Generate the category if it does not already exist
			if (am.getCategory() != null) {
				if (am.isSource()) {
					String catName = am.getCategory().toString() + "_src";
					if (!categoryMap.containsKey(catName)) {
						Category riflCat = doc.new Category(catName);
						categoryMap.put(catName, riflCat);
						
						Assignable riflAssignable = doc.new Assignable(catName, riflCat);
						assignableMap.put(am.getCategory(), riflAssignable);
						
						// Add the domain to the model
						doc.getInterfaceSpec().getSourcesSinks().add(riflAssignable);
						
						// Place the new category in the hierarchy
						doc.getDomainAssignment().add(doc.new DomainAssignment(riflAssignable, topDomain));
					}
				}
				else if (am.isSink()) {
					String catName = am.getCategory().toString() + "_snk";
					if (!categoryMap.containsKey(catName)) {
						Category riflCat = doc.new Category(catName);
						categoryMap.put(catName, riflCat);
						
						Assignable riflAssignable = doc.new Assignable(catName, riflCat);
						assignableMap.put(am.getCategory(), riflAssignable);
						
						// Add the domain to the model
						doc.getInterfaceSpec().getSourcesSinks().add(riflAssignable);
						
						// Place the new category in the hierarchy
						doc.getDomainAssignment().add(doc.new DomainAssignment(riflAssignable, bottomDomain));
					}
				}
			}

			// Add the source/sink specification
			if (am.getCategory() != null && (am.isSource() || am.isSink())) {
				String catName = am.getCategory().toString() + (am.isSource() ? "_src" : "_snk");
				Category riflCat = categoryMap.get(catName);
				if (riflCat == null)
					throw new RuntimeException("Could not get category " + catName);
				
				if (am.isSource()) {
					// Taint the return value
					SourceSinkSpec sourceSinkSpec = doc.new JavaReturnValueSpec(SourceSinkType.Source,
							smac.getClassName(), halfSignature);
					riflCat.getElements().add(sourceSinkSpec);
				}
				else if (am.isSink()) {
					// Annotate all parameters
					for (int i = 0; i < am.getParameters().size(); i++) {
						SourceSinkSpec sourceSinkSpec = doc.new JavaParameterSpec(SourceSinkType.Sink,
								smac.getClassName(), halfSignature, i + 1);
						riflCat.getElements().add(sourceSinkSpec);
					}
				}
			}
		}
		
		RIFLWriter writer = new RIFLWriter(doc);
		String fileName = appendFileName(targetFileName, "_rifl");
		PrintWriter wr = new PrintWriter(fileName);
		wr.print(writer.write());
		wr.flush();
		wr.close();
	}
	
	private Set<AndroidMethod> loadMethodsFromFile(String[] sourceFileName)
			throws IOException {
		// Read in the source file
		Set<AndroidMethod> methods = new HashSet<AndroidMethod>();
		for (String fileName : sourceFileName) {
			ISourceSinkDefinitionProvider pmp = createParser(fileName);
			Set<SourceSinkDefinition> methDefs = pmp.getAllMethods();
			
			for (SourceSinkDefinition ssd : methDefs) {
				AndroidMethod am = (AndroidMethod) ssd.getMethod();
				if (methods.contains(am)) {
					// Merge the methods
					for (AndroidMethod amOrig : methods)
						if (am.equals(amOrig)) {
							// Merge the classification
							if (am.isSource())
								amOrig.setSource(true);
							if (am.isSink())
								amOrig.setSink(true);
							if (am.isNeitherNor())
								amOrig.setNeitherNor(true);
							
							// Merge the permissions and parameters
							amOrig.getPermissions().addAll(am.getPermissions());
							amOrig.getParameters().addAll(am.getParameters());
														
							break;
						}
				}
				else{
					methods.add(am);
					if(fileName.endsWith(".pscout"))
						methodsWithPermissions.add(am);
				}
			}
		}
		
		// Create features for the permissions
		if(ENABLE_PERMISSION){
			createPermissionFeatures(methods, this.featuresSourceSink);
			createPermissionFeatures(methods, this.featuresCategories);
		}
		System.out.println("Running with " + featuresSourceSink.size() + " features on "
				+ methods.size() + " methods");
		
		return methods;
	}

	private void analyzeSourceSinkWeka(Set<AndroidMethod> methods, String targetFileName) throws IOException {			
		FastVector ordinal = new FastVector();
		ordinal.addElement("true");
		ordinal.addElement("false");
		
		FastVector classes = new FastVector();
		classes.addElement("source");
		classes.addElement("sink");
		classes.addElement("neithernor");

		// Collect all attributes and create the instance set
		Map<IFeature, Attribute> featureAttribs = new HashMap<IFeature, Attribute>(this.featuresSourceSink.size());
		FastVector attributes = new FastVector();
		for (IFeature f : this.featuresSourceSink) {
			Attribute attr = new Attribute(f.toString(), ordinal);
			featureAttribs.put(f, attr);
			attributes.addElement(attr);
		}
		Attribute classAttr = new Attribute("class", classes);
		
		FastVector methodStrings = new FastVector();
		for (AndroidMethod am : methods)
			methodStrings.addElement(am.getSignature());
		attributes.addElement(classAttr);
		Attribute idAttr = new Attribute("id", methodStrings);
		attributes.addElement(idAttr);
		
		Instances trainInstances = new Instances("trainingmethods", attributes, 0);
		Instances testInstances = new Instances("allmethods", attributes, 0);
		trainInstances.setClass(classAttr);
		testInstances.setClass(classAttr);

		// Create one instance object per data row
		int sourceTraining = 0;
		int sinkTraining = 0;
		int nnTraining = 0;
		int instanceId = 0;
		Map<String, AndroidMethod> instanceMethods = new HashMap<String, AndroidMethod>(methods.size());
		Map<Integer, AndroidMethod> instanceIndices = new HashMap<Integer, AndroidMethod>(methods.size());
		for (AndroidMethod am : methods) {
			Instance inst = new Instance(attributes.size());
			inst.setDataset(trainInstances);

			for (Entry<IFeature, Attribute> entry : featureAttribs.entrySet()){
				switch(entry.getKey().applies(am)){
					case TRUE: inst.setValue(entry.getValue(), "true"); break;
					case FALSE: inst.setValue(entry.getValue(), "false"); break;
					default: inst.setMissing(entry.getValue());
				}
			}
			inst.setValue(idAttr, am.getSignature());
			instanceMethods.put(am.getSignature(), am);
			instanceIndices.put(instanceId++, am);
			
			// Set the known classifications
			if (am.isSource()) {
				inst.setClassValue("source");
				sourceTraining++;
			}
			else if (am.isSink()) {
				inst.setClassValue("sink");
				sinkTraining++;
			}
			else if (am.isNeitherNor()) {
				inst.setClassValue("neithernor");
				nnTraining++;
			}
			else
				inst.setClassMissing();

			if (am.isAnnotated())
				trainInstances.add(inst);
			else
				testInstances.add(inst);
		}
		
		try {
//			instances.randomize(new Random(1337));
			Classifier classifier = null;
			if(WEKA_LEARNER_ALL.equals("BayesNet"))			// (IBK / kNN) vs. SMO vs. (J48 vs. JRIP) vs. NaiveBayes // MultiClassClassifier für ClassifierPerformanceEvaluator
				classifier = new BayesNet();
			else if(WEKA_LEARNER_ALL.equals("NaiveBayes"))
				classifier = new NaiveBayes();
			else if(WEKA_LEARNER_ALL.equals("J48"))
				classifier = new J48();
			else if(WEKA_LEARNER_ALL.equals("SMO"))
				classifier = new SMO();
			else if(WEKA_LEARNER_ALL.equals("JRip"))
				classifier = new JRip();
			else
				throw new Exception("Wrong WEKA learner!");
			
			ArffSaver saver = new ArffSaver();
			saver.setInstances(trainInstances);
			saver.setFile(new File("SourcesSinks_Train.arff"));
			saver.writeBatch();
			
			Evaluation eval = new Evaluation(trainInstances);
			StringBuffer sb = new StringBuffer();
			eval.crossValidateModel(classifier, trainInstances, 10, new Random(1337), sb, new Range(attributes.indexOf(idAttr) + 1 + ""/* "1-" + (attributes.size() - 1)*/), true);
			System.out.println(sb.toString());
			System.out.println("Class details: " + eval.toClassDetailsString());
			System.out.println("Ran on a training set of " + sourceTraining + " sources, "
					+ sinkTraining + " sinks, and " + nnTraining + " neither-nors");

			classifier.buildClassifier(trainInstances);
			if(WEKA_LEARNER_ALL.equals("J48")){
				System.out.println(((J48)(classifier)).graph());
			}
			for (int instIdx = 0; instIdx < testInstances.numInstances(); instIdx++) {
				Instance inst = testInstances.instance(instIdx);
				assert inst.classIsMissing();
				AndroidMethod meth = instanceMethods.get(inst.stringValue(idAttr));
				double d = classifier.classifyInstance(inst);
				String cName = testInstances.classAttribute().value((int) d);
				if (cName.equals("source")) {
					inst.setClassValue("source");
					meth.setSource(true);
				}
				else if (cName.equals("sink")) {
					inst.setClassValue("sink");
					meth.setSink(true);
				}
				else if (cName.equals("neithernor")) {
					inst.setClassValue("neithernor");
					meth.setNeitherNor(true);
				}
				else
					System.err.println("Unknown class name");
			}
		}
		catch (Exception ex) {
			System.err.println("Something went all wonky: " + ex);
			ex.printStackTrace();
		}
		
		if(DIFF)
			writeResultsToFiles(targetFileName, methods, true);
		else
			writeResultsToFiles(targetFileName, methods, false);
		
		Runtime.getRuntime().gc();
	}

	private void analyzeCategories(Set<AndroidMethod> methods, String targetFileName,
			boolean sources, boolean sinks) throws IOException {			
		FastVector ordinal = new FastVector();
		ordinal.addElement("true");
		ordinal.addElement("false");
		
		// We are only interested in sources and sinks
		{
			Set<AndroidMethod> newMethods = new HashSet<AndroidMethod>(methods.size());
			for (AndroidMethod am : methods) {
				// Make sure that we run after source/sink classification
				assert am.isAnnotated();
				if (am.isSink() == sinks && am.isSource() == sources)
					newMethods.add(am);
			}
			methods = newMethods;
		}
		System.out.println("We have a set of " + methods.size() + " sources and sinks.");
		
		// Build the class attribute, one possibility for every category
		FastVector classes = new FastVector();
		for (AndroidMethod.CATEGORY cat : AndroidMethod.CATEGORY.values()) {
			// Only add the class if it is actually used
			for (AndroidMethod am : methods)
				if (am.isSource() == sources
						&& am.isSink() == sinks
						&& am.getCategory() == cat) {
					classes.addElement(cat.toString());
					break;
				}
		}

		// Collect all attributes and create the instance set
		Map<IFeature, Attribute> featureAttribs = new HashMap<IFeature, Attribute>(this.featuresCategories.size());
		FastVector attributes = new FastVector();
		for (IFeature f : this.featuresCategories) {
			Attribute attr = new Attribute(f.toString(), ordinal);
			featureAttribs.put(f, attr);
			attributes.addElement(attr);
		}
		Attribute classAttr = new Attribute("class", classes);
		
		FastVector methodStrings = new FastVector();
		for (AndroidMethod am : methods)
			methodStrings.addElement(am.getSignature());
		attributes.addElement(classAttr);
		Attribute idAttr = new Attribute("id", methodStrings);
		attributes.addElement(idAttr);
		
		Instances trainInstances = new Instances("trainingmethodsCat", attributes, 0);
		Instances testInstances = new Instances("allmethodsCat", attributes, 0);
		trainInstances.setClass(classAttr);
		testInstances.setClass(classAttr);

		// Create one instance object per data row
		int instanceId = 0;
		Map<String, AndroidMethod> instanceMethods = new HashMap<String, AndroidMethod>(methods.size());
		Map<Integer, AndroidMethod> instanceIndices = new HashMap<Integer, AndroidMethod>(methods.size());
		for (AndroidMethod am : methods) {
			Instance inst = new Instance(attributes.size());
			inst.setDataset(trainInstances);

			for (Entry<IFeature, Attribute> entry : featureAttribs.entrySet()){
				switch(entry.getKey().applies(am)){
					case TRUE: inst.setValue(entry.getValue(), "true"); break;
					case FALSE: inst.setValue(entry.getValue(), "false"); break;
					default: inst.setMissing(entry.getValue());
				}
			}
			inst.setValue(idAttr, am.getSignature());
			instanceMethods.put(am.getSignature(), am);
			instanceIndices.put(instanceId++, am);

			// Set the known classifications
			if (am.getCategory() == null) {
				inst.setClassMissing();
				testInstances.add(inst);
			}
			else {
				inst.setClassValue(am.getCategory().toString());
				trainInstances.add(inst);
			}
		}
		System.out.println("Running category classifier on "
				+ trainInstances.numInstances() + " instances with "
				+ attributes.size() + " attributes...");

		ArffSaver saver = new ArffSaver();
		saver.setInstances(trainInstances);
		if (sources)
			saver.setFile(new File("CategoriesSources_Train.arff"));
		else
			saver.setFile(new File("CategoriesSinks_Train.arff"));
		saver.writeBatch();

		try {
//			instances.randomize(new Random(1337));
			int noCatIdx = classes.indexOf("NO_CATEGORY");
			if (noCatIdx < 0)
				throw new RuntimeException("Could not find NO_CATEGORY index");

			Classifier classifier = null;
			if(WEKA_LEARNER_CATEGORIES.equals("BayesNet"))			// (IBK / kNN) vs. SMO vs. (J48 vs. JRIP) vs. NaiveBayes // MultiClassClassifier für ClassifierPerformanceEvaluator
				classifier = new CutoffClassifier(new BayesNet(), THRESHOLD, noCatIdx);
			else if(WEKA_LEARNER_CATEGORIES.equals("NaiveBayes"))
				classifier = new CutoffClassifier(new NaiveBayes(), THRESHOLD, noCatIdx);
			else if(WEKA_LEARNER_CATEGORIES.equals("J48"))
				classifier = new CutoffClassifier(new J48(), THRESHOLD, noCatIdx);
			else if(WEKA_LEARNER_CATEGORIES.equals("SMO"))
//				classifier = new CutoffClassifier(new SMO(), THRESHOLD, noCatIdx);
				classifier = new SMO();
			else if(WEKA_LEARNER_CATEGORIES.equals("JRip"))
				classifier = new CutoffClassifier(new JRip(), THRESHOLD, noCatIdx);
			else
				throw new Exception("Wrong WEKA learner!");
			
			Evaluation eval = new Evaluation(trainInstances);
			/*for (int foldNum = 0; foldNum < 10; foldNum++) {
				Instances train = trainInstances.trainCV(10, foldNum, new Random(1337));
				Instances test = trainInstances.testCV(10, foldNum);
				
				Classifier clsCopy = Classifier.makeCopy(classifier);
				clsCopy.buildClassifier(train);

				eval.evaluateModel(clsCopy, test);
			}*/
			
			
			StringBuffer sb = new StringBuffer();
			eval.crossValidateModel(classifier, trainInstances, 10, new Random(1337), sb, new Range(attributes.indexOf(idAttr) + 1 + ""), true);
			System.out.println(sb.toString());
			
			System.out.println("Class details: " + eval.toClassDetailsString());

			classifier.buildClassifier(trainInstances);
			if(WEKA_LEARNER_CATEGORIES.equals("J48")){
				Classifier baseClassifier = ((CutoffClassifier)classifier).getBaseClassifier();
				System.out.println(((J48)(baseClassifier)).graph());
			}
			System.out.println("Record\tSource\tSink\tNN");
			for (int instNum = 0; instNum < testInstances.numInstances(); instNum++) {
				Instance inst = testInstances.instance(instNum);
				assert inst.classIsMissing();
				AndroidMethod meth = instanceMethods.get(inst.stringValue(idAttr));
				double d = classifier.classifyInstance(inst);
				String cName = trainInstances.classAttribute().value((int) d);
				meth.setCategory(AndroidMethod.CATEGORY.valueOf(cName));
			}
		}
		catch (Exception ex) {
			System.err.println("Something went all wonky: " + ex);
			ex.printStackTrace();
		}
		
		if(DIFF)
			writeCategoryResultsToFiles(targetFileName, methods, sources, sinks, true);
		else
			writeCategoryResultsToFiles(targetFileName, methods, sources, sinks, false);
	}

	private void loadMethodsFromAndroid(final Set<AndroidMethod> methods) {
		int methodCount = methods.size();
		new AbstractSootFeature(ANDROID) {
			
			@Override
			public Type appliesInternal(AndroidMethod method) {
				for (SootClass sc : Scene.v().getClasses())
					if (!sc.isInterface()
							&& !sc.isPrivate())
//							&& sc.getName().startsWith("android.")
//							&& sc.getName().startsWith("com."))
					for (SootMethod sm : sc.getMethods())
						if (sm.isConcrete()
								&& !sm.isPrivate()) {
							AndroidMethod newMethod = new AndroidMethod(sm);
							methods.add(newMethod);
						}
				return Type.NOT_SUPPORTED;
			}
			
		}.applies(new AndroidMethod("a", "void", "x.y"));
		System.out.println("Loaded " + (methods.size() - methodCount) + " methods from Android JAR");
	}

	/**
	 * Creates artificial annotations for non-overridden methods in subclasses.
	 * If the class A implements some method foo() which is marked as e.g. a
	 * source and class B extends A, but does not overwrite foo(), B.foo()
	 * must also be a source.
	 * @param methods The list of method for which to create subclass
	 * annotations
	 */
	private void createSubclassAnnotations(final Set<AndroidMethod> methods) {
		int copyCount = -1;
		int totalCopyCount = 0;
		while (copyCount != 0) {
			copyCount = 0;
			for (AndroidMethod am : methods) {
				// Check whether one of the parent classes is already annotated
				AbstractSootFeature asf = new AbstractSootFeature(ANDROID) {
					
					@Override
					public Type appliesInternal(AndroidMethod method) {
						// This already searches up the class hierarchy until we
						// find a match for the requested method.
						SootMethod parentMethod = getSootMethod(method);
						if (parentMethod == null)
							return Type.NOT_SUPPORTED;
						
						// If we have found the method in a base class and not in
						// the current one, we can copy our current method's
						// annotation to this base class. (copy-down)
						boolean copied = false;
						if (!parentMethod.getDeclaringClass().getName().equals(method.getClassName())) {
							// Get the data object for the parent method
							AndroidMethod parentMethodData = findAndroidMethod(parentMethod);
							if (parentMethodData == null)
								return Type.NOT_SUPPORTED;
							
							// If we have annotations for both methods, they must match
							if (parentMethodData.isAnnotated() && method.isAnnotated())
								if (parentMethodData.isSource() != method.isSource()
										|| parentMethodData.isSink() != method.isSink()
										|| parentMethodData.isNeitherNor() != method.isNeitherNor())
									throw new RuntimeException("Annotation mismatch for "
											+ parentMethodData + " and " + method);
							if (parentMethodData.getCategory() != null && method.getCategory() != null)
								if (parentMethodData.getCategory() != method.getCategory())
									throw new RuntimeException("Category mismatch for "
											+ parentMethod + " and " + method);

							// If we only have annotations for the parent method, but not for
							// the current one, we copy it down
							if (parentMethodData.isAnnotated() && !method.isAnnotated()) {
								method.setSource(parentMethodData.isSource());
								method.setSink(parentMethodData.isSink());
								method.setNeitherNor(parentMethodData.isNeitherNor());
								copied = true;
							}
							if (parentMethodData.getCategory() != null && method.getCategory() == null)
								method.setCategory(parentMethodData.getCategory());
							
							// If we only have annotations for the current method, but not for
							// the parent one, we can copy it up
							if (!parentMethodData.isAnnotated() && method.isAnnotated()) {
								parentMethodData.setSource(method.isSource());
								parentMethodData.setSink(method.isSink());
								parentMethodData.setNeitherNor(method.isNeitherNor());
								copied = true;
							}
							if (parentMethodData.getCategory() == null && method.getCategory() != null)
								parentMethodData.setCategory(method.getCategory());
						}
						return copied ? Type.TRUE : Type.FALSE;
					}
					
					private AndroidMethod findAndroidMethod(SootMethod sm) {
						AndroidMethod smData = new AndroidMethod(sm);
						for (AndroidMethod am : methods)
							if (am.equals(smData))
								return am;
						return null;
					}
				};
				if (asf.applies(am) == Type.TRUE) {
					copyCount++;
					totalCopyCount++;
				}
			}
		}
		System.out.println("Created automatic annotations starting from "
				+ totalCopyCount + " methods");
	}

	/**
	 * Removes all interfaces from the given set of methods and returns the
	 * purged set.
	 * @param methods The set of methods from which to remove the interfaces.
	 * @return The purged set of methods.
	 */
	private Set<AndroidMethod> PrefilterInterfaces(Set<AndroidMethod> methods) {
		Set<AndroidMethod> purgedMethods = new HashSet<AndroidMethod>(methods.size());
		for (AndroidMethod am : methods) {
			AbstractSootFeature asf = new AbstractSootFeature(ANDROID) {
			
				@Override
				public Type appliesInternal(AndroidMethod method) {
					SootMethod sm = getSootMethod(method);
					if (sm == null)
						return Type.NOT_SUPPORTED;
					
					if (sm.isAbstract() || sm.getDeclaringClass().isInterface()
							|| sm.isPrivate())
						return Type.FALSE;
					else
						return Type.TRUE;
				}
			};
			
			if (asf.applies(am) == Type.TRUE)
				purgedMethods.add(am);
		}
		System.out.println(methods.size() + " methods purged down to " + purgedMethods.size());
		return purgedMethods;
	}

	/**
	 * Creates one feature for every unique permission required by a method
	 * @param methods The list of methods and permissions
	 */
	private void createPermissionFeatures(Set<AndroidMethod> methods,
			Set<IFeature> featureSet) {
		for (AndroidMethod am : methods)
			for (String perm : am.getPermissions())
				featureSet.add(new PermissionNameFeature(perm));
	}

	private String appendFileName(String targetFileName, String string) {
		int pos = targetFileName.lastIndexOf(".");
		return targetFileName.substring(0, pos) + string
				+ targetFileName.substring(pos);
	}

	private ISourceSinkDefinitionProvider createParser(String fileName) throws IOException {
		String fileExt = fileName.substring(fileName.lastIndexOf("."));
		if (fileExt.equalsIgnoreCase(".txt"))
			return PermissionMethodParser.fromFile(fileName);
		if (fileExt.equalsIgnoreCase(".csv"))
			return new CSVPermissionMethodParser(fileName);
		if (fileExt.equalsIgnoreCase(".pscout"))
			return new PScoutPermissionMethodParser(fileName);
		throw new RuntimeException("Unknown source file format");
	}
	
	/**
	 * Initializes the set of features for classifying methods as sources,
	 * sinks or neither-nor entries.
	 * @return The generated feature set.
	 */
	private Set<IFeature> initializeFeaturesSourceSink() {
		Set<IFeature> features = new HashSet<IFeature>();
		
		/* Method name indications */
		IFeature nameStartsWithGet = new MethodNameStartsWithFeature("get", 0.2f);
		features.add(nameStartsWithGet);
		IFeature nameStartsWithDo = new MethodNameStartsWithFeature("do", 0.2f);
		features.add(nameStartsWithDo);
		IFeature nameStartsWithNotify = new MethodNameStartsWithFeature("notify", 0.2f);
		features.add(nameStartsWithNotify);
		IFeature nameStartsWithUpdate = new MethodNameStartsWithFeature("update", 0.2f);
		features.add(nameStartsWithUpdate);
		IFeature nameStartsWithIs = new MethodNameStartsWithFeature("is", 0.2f);
		features.add(nameStartsWithIs);
		IFeature nameStartsWithSend = new MethodNameStartsWithFeature("send", 0.1f);
		features.add(nameStartsWithSend);
		IFeature nameStartsWithSet = new MethodNameStartsWithFeature("set", 0.2f);
		features.add(nameStartsWithSet);
		IFeature nameStartsWithFinish = new MethodNameStartsWithFeature("finish", 0.1f);
		features.add(nameStartsWithFinish);
		IFeature nameStartsWithStart = new MethodNameStartsWithFeature("start", 0.1f);
		features.add(nameStartsWithStart);
		IFeature nameStartsWithHandle = new MethodNameStartsWithFeature("handle", 0.1f);
		features.add(nameStartsWithHandle);
		IFeature nameStartsWithClear = new MethodNameStartsWithFeature("clear", 0.3f);
		features.add(nameStartsWithClear);
		/* Does not change anything
		IFeature nameStartsWithShow = new MethodNameStartsWithFeature("show", 0.2f);
		features.add(nameStartsWithShow);
		IFeature nameStartsWithMove = new MethodNameStartsWithFeature("move", 0.1f);
		features.add(nameStartsWithMove);
		IFeature nameStartsWithConnect = new MethodNameStartsWithFeature("connect", 0.1f);
		features.add(nameStartsWithConnect);
		IFeature nameStartsWithEnforce = new MethodNameStartsWithFeature("enforce", 0.1f);
		features.add(nameStartsWithEnforce);
		IFeature nameStartsWithDisplay = new MethodNameStartsWithFeature("display", 0.1f);
		features.add(nameStartsWithDisplay);
		IFeature nameStartsWithPull = new MethodNameStartsWithFeature("pull", 0.1f);
		features.add(nameStartsWithPull);

		IFeature nameStartsWithPresent = new MethodNameStartsWithFeature("present", 0.1f);
		features.add(nameStartsWithPresent);
		IFeature nameStartsWithSave = new MethodNameStartsWithFeature("save", 0.3f);
		features.add(nameStartsWithSave);
		IFeature nameStartsWithWrite = new MethodNameStartsWithFeature("write", 0.3f);
		features.add(nameStartsWithWrite);
		IFeature nameStartsWithBroadcast = new MethodNameStartsWithFeature("broadcast", 0.1f);
		features.add(nameStartsWithBroadcast);
		IFeature nameEndsWithUpdated = new MethodNameEndsWithFeature("Updated");
		features.add(nameEndsWithUpdated);
		*/
		IFeature nameStartsWithRemove = new MethodNameStartsWithFeature("remove", 0.3f);
		features.add(nameStartsWithRemove);
		IFeature nameStartsWithRelease = new MethodNameStartsWithFeature("release", 0.3f);
		features.add(nameStartsWithRelease);
		IFeature nameStartsWithNote = new MethodNameStartsWithFeature("note", 0.1f);
		features.add(nameStartsWithNote);
		IFeature nameStartsWithUnregister = new MethodNameStartsWithFeature("unregister", 0.1f);
		features.add(nameStartsWithUnregister);
		IFeature nameStartsWithRegister = new MethodNameStartsWithFeature("register", 0.1f);
		features.add(nameStartsWithRegister);
		IFeature nameStartsWithPut = new MethodNameStartsWithFeature("put", 0.3f);
		features.add(nameStartsWithPut);
		IFeature nameStartsWithAdd = new MethodNameStartsWithFeature("add", 0.3f);
		features.add(nameStartsWithAdd);
		IFeature nameStartsWithSupply = new MethodNameStartsWithFeature("supply", 0.1f);
		features.add(nameStartsWithSupply);
		IFeature nameStartsWithDelete = new MethodNameStartsWithFeature("delete", 0.3f);
		features.add(nameStartsWithDelete);
		IFeature nameStartsWithInit = new MethodNameStartsWithFeature("<init>", 0.1f);
		features.add(nameStartsWithInit);
		IFeature nameStartsWithOpen = new MethodNameStartsWithFeature("open", 0.1f);
		features.add(nameStartsWithOpen);
		IFeature nameStartsWithClose = new MethodNameStartsWithFeature("close", 0.1f);
		features.add(nameStartsWithClose);
		IFeature nameStartsWithEnable = new MethodNameStartsWithFeature("enable", 0.1f);
		features.add(nameStartsWithEnable);
		IFeature nameStartsWithDisable = new MethodNameStartsWithFeature("disable", 0.1f);
		features.add(nameStartsWithDisable);
		IFeature nameStartsWithQuery = new MethodNameStartsWithFeature("query", 0.1f);
		features.add(nameStartsWithQuery);
		IFeature nameStartsWithProcess = new MethodNameStartsWithFeature("process", 0.1f);
		features.add(nameStartsWithProcess);
		IFeature nameStartsWithRun = new MethodNameStartsWithFeature("run", 0.1f);
		features.add(nameStartsWithRun);		
		IFeature nameStartsWithPerform = new MethodNameStartsWithFeature("perform", 0.1f);
		features.add(nameStartsWithPerform);
		IFeature nameStartsWithToggle = new MethodNameStartsWithFeature("toggle", 0.1f);
		features.add(nameStartsWithToggle);
		IFeature nameStartsWithBind = new MethodNameStartsWithFeature("bind", 0.1f);
		features.add(nameStartsWithBind);
		IFeature nameStartsWithDispatch = new MethodNameStartsWithFeature("dispatch", 0.1f);
		features.add(nameStartsWithDispatch);
		IFeature nameStartsWithApply = new MethodNameStartsWithFeature("apply", 0.1f);
		features.add(nameStartsWithApply);
		IFeature nameStartsWithLoad = new MethodNameStartsWithFeature("load", 0.1f);
		features.add(nameStartsWithLoad);
		IFeature nameStartsWithDump = new MethodNameStartsWithFeature("dump", 0.1f);
		features.add(nameStartsWithDump);
		IFeature nameStartsWithRequest = new MethodNameStartsWithFeature("request", 0.1f);
		features.add(nameStartsWithRequest);
		IFeature nameStartsWithRestore = new MethodNameStartsWithFeature("restore", 0.1f);
		features.add(nameStartsWithRestore);		
		IFeature nameStartsWithInsert = new MethodNameStartsWithFeature("insert", 0.1f);
		features.add(nameStartsWithInsert);
		IFeature nameStartsWithOnClick = new MethodNameStartsWithFeature("onClick", 0.1f);
		features.add(nameStartsWithOnClick);
		IFeature nameEndWithMessenger = new MethodNameEndsWithFeature("Messenger");
		features.add(nameEndWithMessenger);
		
		IFeature parameterStartsWithJavaIo = new ParameterContainsTypeOrNameFeature("java.io.");
		features.add(parameterStartsWithJavaIo);		
		IFeature parameterStartsWithCursor = new ParameterContainsTypeOrNameFeature("android.database.Cursor");
		features.add(parameterStartsWithCursor);
		IFeature parameterStartsWithContenResolver = new ParameterContainsTypeOrNameFeature("android.content.ContentResolver");
		features.add(parameterStartsWithContenResolver);
		IFeature parameterTypeURI = new ParameterContainsTypeOrNameFeature("android.net.Uri");
		features.add(parameterTypeURI);
		IFeature parameterTypeHasGoogleIO = new ParameterContainsTypeOrNameFeature("com.google.common.io");
		features.add(parameterTypeHasGoogleIO);		
		IFeature parameterTypeHasContext = new ParameterContainsTypeOrNameFeature("android.content.Context");
		features.add(parameterTypeHasContext);
		IFeature parameterEndsWithObserver = new ParameterContainsTypeOrNameFeature("Observer");
		features.add(parameterEndsWithObserver);
		IFeature parameterTypeHasWriter = new ParameterContainsTypeOrNameFeature("Writer");
		features.add(parameterTypeHasWriter);		
		IFeature parameterTypeHasEvent = new ParameterContainsTypeOrNameFeature("Event");
		features.add(parameterTypeHasEvent);
		
//		IFeature parameterTypeHasOutputStream = new ParameterHasTypeFeature("OutputStream", 0.2f);
//		features.add(parameterTypeHasOutputStream);
//		
//		IFeature parameterTypeHasInputStream = new ParameterHasTypeFeature("InputStream", 0.2f);
//		features.add(parameterTypeHasInputStream);
		
		
		/* DOES NOT CHANGE ANYTHING */
		// (unfortunately there are also some neithernors that contains an IBinder!!)
		/*
		IFeature parameterTypeHasIBinder = new ParameterContainsTypeOrNameFeature("android.os.IBinder", 0.2f);
		features.add(parameterTypeHasIBinder);
		*/
		
		IFeature parameterTypeHasKey = new ParameterContainsTypeOrNameFeature("com.android.inputmethod.keyboard.Key");
		features.add(parameterTypeHasKey);

		IFeature parameterTypeHasIntend = new ParameterContainsTypeOrNameFeature("android.content.Intent");
		features.add(parameterTypeHasIntend);
		
		IFeature parameterTypeHasFileDescriptor = new ParameterContainsTypeOrNameFeature("java.io.FileDescriptor");
		features.add(parameterTypeHasFileDescriptor);
		
		IFeature parameterTypeHasFilterContext = new ParameterContainsTypeOrNameFeature("android.filterfw.core.FilterContext");
		features.add(parameterTypeHasFilterContext);
		
		IFeature parameterTypeHasString = new ParameterContainsTypeOrNameFeature("java.lang.String");
		features.add(parameterTypeHasString);

		IFeature voidReturnType = new ReturnTypeFeature(ANDROID, "void");
		features.add(voidReturnType);
		IFeature booleanReturnType = new ReturnTypeFeature(ANDROID, "boolean");
		features.add(booleanReturnType);
		IFeature intReturnType = new ReturnTypeFeature(ANDROID, "int");
		features.add(intReturnType);
		IFeature byteArrayReturnType = new ReturnTypeFeature(ANDROID, "byte[]");
		features.add(byteArrayReturnType);
		IFeature cursorReturnType = new ReturnTypeFeature(ANDROID, "android.database.Cursor");
		features.add(cursorReturnType);
		/* Does not change anything
		IFeature mergeCursorReturnType = new ReturnTypeFeature(MAPS, ANDROID, "android.database.MergeCursor");
		features.add(mergeCursorReturnType);
		IFeature webviewReturnType = new ReturnTypeFeature(MAPS, ANDROID, "android.webkit.WebView");
		features.add(webviewReturnType);
		*/
		IFeature uriReturnType = new ReturnTypeFeature(ANDROID, "android.net.Uri");
		features.add(uriReturnType);
		IFeature connectionReturnType = new ReturnTypeFeature(ANDROID, "com.android.internal.telephony.Connection");
		features.add(connectionReturnType);
		IFeature returnTypeImplementsInterfaceList = new ReturnTypeFeature(ANDROID, "java.util.List");
		features.add(returnTypeImplementsInterfaceList);
		IFeature returnTypeImplementsInterfaceMap = new ReturnTypeFeature(ANDROID, "java.util.Map");
		features.add(returnTypeImplementsInterfaceMap);
		IFeature returnTypeImplementsInterfaceParcelable = new ReturnTypeFeature(ANDROID, "android.os.Parcelable");
		features.add(returnTypeImplementsInterfaceParcelable);
		
		IFeature hasParamsPerm = new MethodHasParametersFeature(0.2f);
		features.add(hasParamsPerm);

		IFeature voidOnMethod = new VoidOnMethodFeature();
		features.add(voidOnMethod);

		/* Method modifiers */
		IFeature isStaticMethod = new MethodModifierFeature(ANDROID, Modifier.STATIC);
		features.add(isStaticMethod);
		IFeature isPublicMethod = new MethodModifierFeature(ANDROID, Modifier.PUBLIC);
		features.add(isPublicMethod);
		/* Does not change anything
		IFeature isNativeMethod = new MethodModifierFeature(MAPS, ANDROID, Modifier.NATIVE);
		features.add(isNativeMethod);
		IFeature isAbstraceMethod = new MethodModifierFeature(MAPS, ANDROID, Modifier.ABSTRACT);
		features.add(isAbstraceMethod);
		IFeature isPrivateMethod = new MethodModifierFeature(MAPS, ANDROID, Modifier.PRIVATE);
		features.add(isPrivateMethod);
		*/
		IFeature isProtectedMethod = new MethodModifierFeature(ANDROID, Modifier.PROTECTED);
		features.add(isProtectedMethod);
		IFeature isFinalMethod = new MethodModifierFeature(ANDROID, Modifier.FINAL);
		features.add(isFinalMethod);
		
		/* Class modifiers */
		IFeature isPrivateClassOfMethod = new MethodClassModifierFeature(ANDROID, ClassModifier.PRIVATE);
		features.add(isPrivateClassOfMethod);
		IFeature isPublicClassOfMethod = new MethodClassModifierFeature(ANDROID, ClassModifier.PUBLIC);
		features.add(isPublicClassOfMethod);
		IFeature isProtectedClassOfMethod = new MethodClassModifierFeature(ANDROID, ClassModifier.PROTECTED);
		features.add(isProtectedClassOfMethod);
		IFeature isStaticClassOfMethod = new MethodClassModifierFeature(ANDROID, ClassModifier.STATIC);
		features.add(isStaticClassOfMethod);
		IFeature isFinalClassOfMethod = new MethodClassModifierFeature(ANDROID, ClassModifier.FINAL);
		features.add(isFinalClassOfMethod);
		IFeature isAbstractClassOfMethod = new MethodClassModifierFeature(ANDROID, ClassModifier.ABSTRACT);
		features.add(isAbstractClassOfMethod);

		/* Specific class properties */
		/* Does not change anything
		IFeature isInnerClassOfMethod = new MethodInnerClassFeature(MAPS, ANDROID, true);
		features.add(isInnerClassOfMethod);
		*/
		IFeature isAnonymousClassOfMethod = new MethodAnonymousClassFeature(true);
		features.add(isAnonymousClassOfMethod);
		
//		IFeature connectivityManagerClass = new MethodClassConcreteNameFeature("android.net.ConnectivityManager", 0.2f);
//		features.add(connectivityManagerClass);
//		
//		IFeature telephonyManagerClass = new MethodClassConcreteNameFeature("android.telephony.TelephonyManager", 0.4f);
//		features.add(telephonyManagerClass);
//		
//		IFeature locationManagerClass = new MethodClassConcreteNameFeature("android.location.LocationManager", 0.3f);
//		features.add(locationManagerClass);
//		
//		IFeature accountManagerClass = new MethodClassConcreteNameFeature("android.accounts.AccountManager", 0.3f);
//		features.add(accountManagerClass);
//			
//		IFeature smsManagerClass = new MethodClassConcreteNameFeature("android.telephony.gsm.SmsManager", 0.4f);
//		features.add(smsManagerClass);

		/* Class name ends with - gives a hint about the type of operation handled in the class */
		IFeature managerClasses = new MethodClassEndsWithNameFeature("Manager");
		features.add(managerClasses);
		IFeature factoryClasses = new MethodClassEndsWithNameFeature("Factory");
		features.add(factoryClasses);
		IFeature serviceClasses = new MethodClassEndsWithNameFeature("Service");
		features.add(serviceClasses);
		IFeature viewClasses = new MethodClassEndsWithNameFeature("View");
		features.add(viewClasses);
		IFeature ioClasses = new MethodClassContainsNameFeature("java.io.");
		features.add(ioClasses);
		IFeature loaderClasses = new MethodClassEndsWithNameFeature("Loader");
		features.add(loaderClasses);
		IFeature handlerClasses = new MethodClassEndsWithNameFeature("Handler");
		features.add(handlerClasses);
		IFeature contextClasses = new MethodClassEndsWithNameFeature("Context");
		features.add(contextClasses);

		/* SLIGHTLY INCREASES FP FOR SINKS */
		/*
		IFeature providerClasses = new MethodClassEndsWithNameFeature("Provider", 0.4f);
		features.add(providerClasses);
		*/
		
		IFeature googleIOClasses = new MethodClassContainsNameFeature("com.google.common.io");
		features.add(googleIOClasses);
		IFeature contentResolverClass = new MethodClassConcreteNameFeature("android.content.ContentResolver");
		features.add(contentResolverClass);
		IFeature contextClass = new MethodClassConcreteNameFeature("android.content.Context");
		features.add(contextClass);
		IFeature activityClass = new MethodClassConcreteNameFeature("android.app.Activity");
		features.add(activityClass);
		IFeature serviceClass = new MethodClassConcreteNameFeature("android.app.Service");
		features.add(serviceClass);
		IFeature contentProviderClass = new MethodClassConcreteNameFeature("android.app.ContentProvider");
		features.add(contentProviderClass);
		IFeature broadcastReceiverClass = new MethodClassConcreteNameFeature("android.app.BroadcastReceiver");
		features.add(broadcastReceiverClass);
 
		//not in Testcase!
//		IFeature locationListenerClass = new MethodClassConcreteNameFeature("android.location.LocationListener", 0.2f);
//		features.add(locationListenerClass);
		
		//not in Testcase!
//		IFeature phoneStateListenerClass = new MethodClassConcreteNameFeature("android.telephony.PhoneStateListener", 0.2f);
//		features.add(phoneStateListenerClass);
		
//		IFeature dataFlowQuerySource = new DataFlowSourceFeature(ANDROID, "query", 0.6f);
//		features.add(dataFlowQuerySource);
//		
//		IFeature dataFlowCreateTypedArrayListSource = new DataFlowSourceFeature(ANDROID, "createTypedArrayList", 0.6f);
//		features.add(dataFlowCreateTypedArrayListSource);		
		

		
//		IFeature parameterInSink = new ParameterInCallFeature(MAPS, ANDROID, "", CheckType.CheckSink);
//		features.add(parameterInSink);
		IFeature parameterInRemove = new ParameterInCallFeature(ANDROID, "remove", CheckType.CheckSink);
		features.add(parameterInRemove);
		IFeature parameterInSync = new ParameterInCallFeature(ANDROID, "sync", CheckType.CheckSink);
		features.add(parameterInSync);
		IFeature parameterInClear = new ParameterInCallFeature(ANDROID, "clear", CheckType.CheckSink);
		features.add(parameterInClear);
		IFeature parameterInOnCreate = new ParameterInCallFeature(ANDROID, "onCreate", CheckType.CheckSink);
		features.add(parameterInOnCreate);
		IFeature parameterInDelete = new ParameterInCallFeature(ANDROID, "delete", CheckType.CheckSink);
		features.add(parameterInDelete);
		IFeature parameterInSet = new ParameterInCallFeature(ANDROID, "set", -1, false /*true*/, CheckType.CheckSink);
		features.add(parameterInSet);
		IFeature parameterInEnable = new ParameterInCallFeature(ANDROID, "enable", CheckType.CheckSink);
		features.add(parameterInEnable);
		IFeature parameterInDisable = new ParameterInCallFeature(ANDROID, "disable", CheckType.CheckSink);
		features.add(parameterInDisable);
		IFeature parameterInPut = new ParameterInCallFeature(ANDROID, "put", -1, false /*true*/, CheckType.CheckSink);
		features.add(parameterInPut);
		IFeature parameterInStart = new ParameterInCallFeature(ANDROID, "start", CheckType.CheckSink);
		features.add(parameterInStart);
		IFeature parameterInSave = new ParameterInCallFeature(ANDROID, "save", CheckType.CheckSink);
		features.add(parameterInSave);
		IFeature parameterInSend = new ParameterInCallFeature(ANDROID, "send", CheckType.CheckSink);
		features.add(parameterInSend);
		IFeature parameterInDump = new ParameterInCallFeature(ANDROID, "dump", CheckType.CheckSink);
		features.add(parameterInDump);
		IFeature parameterInDial = new ParameterInCallFeature(ANDROID, "dial", CheckType.CheckSink);
		features.add(parameterInDial);
		IFeature parameterInBroadcast = new ParameterInCallFeature(ANDROID, "broadcast", CheckType.CheckSink);
		features.add(parameterInBroadcast);
		IFeature parameterInBind = new ParameterInCallFeature(ANDROID, "bind", CheckType.CheckSink);
		features.add(parameterInBind);
		IFeature parameterInTransact = new ParameterInCallFeature(ANDROID, "transact", CheckType.CheckSink);
		features.add(parameterInTransact);
		IFeature parameterInWrite = new ParameterInCallFeature(ANDROID, "write", CheckType.CheckSink);
		features.add(parameterInWrite);
		IFeature parameterInUpdate = new ParameterInCallFeature(ANDROID, "update", CheckType.CheckSink);
		features.add(parameterInUpdate);
		IFeature parameterInPerform = new ParameterInCallFeature(ANDROID, "perform", CheckType.CheckSink);
		features.add(parameterInPerform);
		IFeature parameterInNotify = new ParameterInCallFeature(ANDROID, "notify", CheckType.CheckSink);
		features.add(parameterInNotify);
		IFeature parameterInInsert = new ParameterInCallFeature(ANDROID, "insert", CheckType.CheckSink);
		features.add(parameterInInsert);
		IFeature parameterInEnqueue = new ParameterInCallFeature(ANDROID, "enqueue", CheckType.CheckSink);
		features.add(parameterInEnqueue);
		IFeature parameterInReplace = new ParameterInCallFeature(ANDROID, "replace", CheckType.CheckSink);
		features.add(parameterInReplace);
		IFeature parameterInShow = new ParameterInCallFeature(ANDROID, "show", CheckType.CheckSink);
		features.add(parameterInShow);
		IFeature parameterInDispatch = new ParameterInCallFeature(ANDROID, "dispatch", CheckType.CheckSink);
		features.add(parameterInDispatch);
		/* Does not change anything
		IFeature parameterInPrint = new ParameterInCallFeature(MAPS, ANDROID, "print", CheckType.CheckSink);
		features.add(parameterInPrint);
		*/
		IFeature parameterInPrintln = new ParameterInCallFeature(ANDROID, "println", CheckType.CheckSink);
		features.add(parameterInPrintln);
		IFeature parameterInCreate = new ParameterInCallFeature(ANDROID, "create", CheckType.CheckSink);
		features.add(parameterInCreate);
		IFeature parameterInAdjust = new ParameterInCallFeature(ANDROID, "adjust", CheckType.CheckSink);
		features.add(parameterInAdjust);
		IFeature parameterInSetup = new ParameterInCallFeature(ANDROID, "setup", CheckType.CheckSink);
		features.add(parameterInSetup);
		IFeature parameterInRestore = new ParameterInCallFeature(ANDROID, "restore", CheckType.CheckSink);
		features.add(parameterInRestore);
		IFeature parameterInConnect = new ParameterInCallFeature(ANDROID, "connect", CheckType.CheckSink);
		features.add(parameterInConnect);
		
		IFeature parameterInAbstract = new ParameterInCallFeature(ANDROID, "", CheckType.CheckSinkInAbstract);
		features.add(parameterInAbstract);
		IFeature parameterInCI = new ParameterInCallFeature(ANDROID,
				"com.android.internal.telephony.CommandsInterface", CheckType.CheckSink);
		features.add(parameterInCI);

		IFeature getInReturn = new ParameterInCallFeature(ANDROID, "get", CheckType.CheckSource);
		features.add(getInReturn);
		IFeature queryInReturn = new ParameterInCallFeature(ANDROID, "query", CheckType.CheckSource);
		features.add(queryInReturn);
//		IFeature ctalInReturn = new ParameterInCallFeature("createTypedArrayList", CheckType.CheckSource);
//		features.add(ctalInReturn);
		IFeature createInReturn = new ParameterInCallFeature(ANDROID, "create", CheckType.CheckSource);
		features.add(createInReturn);
		IFeature obtainMessageInReturn = new ParameterInCallFeature(ANDROID, "obtainMessage", CheckType.CheckSource);
		features.add(obtainMessageInReturn);
		IFeature isInReturn = new ParameterInCallFeature(ANDROID, "is", CheckType.CheckSource);
		features.add(isInReturn);
		
		IFeature parameterInNative = new ParameterInCallFeature(ANDROID, "", CheckType.CheckFromParamToNative);
		features.add(parameterInNative);
//		IFeature parameterInNative = new ParameterInCallFeature(MAPS, ANDROID, "", CheckType.CheckFromParamToInterface);
//		features.add(parameterInNative);

		/* Does not change anything
		IFeature transactArgument2InReturn = new ParameterInCallFeature(MAPS, ANDROID, "transact", 2, false, CheckType.CheckSource);
		features.add(transactArgument2InReturn);
		*/
		IFeature wtParcelArgument1InReturn = new ParameterInCallFeature(ANDROID, "writeToParcel", 0, false, CheckType.CheckSource);
		features.add(wtParcelArgument1InReturn);
		
		IFeature getToSink = new ParameterInCallFeature(ANDROID, "get", CheckType.CheckFromMethodToSink);
		features.add(getToSink);

		IFeature threadRun = new IsThreadRunFeature(ANDROID);
		features.add(threadRun);
		IFeature methodReturnsConstant = new MethodReturnsConstantFeature(ANDROID);
		features.add(methodReturnsConstant);

		//sink feature
//		IFeature parameterFlowsToSpecificMethod = new DataFlowFromParameterToSpecificMethodFeature(ANDROID, WRAPPER_FILE, 0.2f);
//		features.add(parameterFlowsToSpecificMethod);
		
		IFeature classNameContainsTelephony = new BaseNameOfClassPackageName("telephony");
		features.add(classNameContainsTelephony);
		IFeature classNameContainsIO = new BaseNameOfClassPackageName("io");
		features.add(classNameContainsIO);
		IFeature classNameContainsAccounts = new BaseNameOfClassPackageName("accounts");
		features.add(classNameContainsAccounts);
		IFeature classNameContainsWebkit = new BaseNameOfClassPackageName("webkit");
		features.add(classNameContainsWebkit);
		IFeature classNameContainsMusik = new BaseNameOfClassPackageName("music");
		features.add(classNameContainsMusik);
		
//		IFeature notifyCalled = new MethodCallsMethodFeature(MAPS, ANDROID, "notify");
//		features.add(notifyCalled);
		IFeature notifyCalled = new MethodCallsMethodFeature(ANDROID, "android.database.sqlite.SQLiteDatabase", "insert");
		features.add(notifyCalled);
		
		IFeature interfaceParameter = new ParameterIsInterfaceFeature(ANDROID);
		features.add(interfaceParameter);

		/* Does not change anything
		IFeature binderCalled = new MethodCallsMethodFeature(MAPS, ANDROID, "android.os.IBinder", "transact");
		features.add(binderCalled);
		*/
		
		IFeature isSetter = new MethodIsRealSetterFeature(ANDROID);
		features.add(isSetter);

//		IFeature invokeAdd = new MethodInvocationOnParameterFeature(MAPS, "ANDROID", "add");
//		features.add(invokeAdd);

		return features;
	}

	/**
	 * Initializes the set of features for classifying sources and sinks into
	 * categories.
	 * @return The generated feature set.
	 */
	private Set<IFeature> initializeFeaturesCategories() {
		Set<IFeature> features = new HashSet<IFeature>();
		
		IFeature nameContainsProvider = new MethodNameContainsFeature("Provider");
		features.add(nameContainsProvider);
		IFeature nameContainsPreferred = new MethodNameContainsFeature("Preferred");
		features.add(nameContainsPreferred);
		IFeature nameContainsType = new MethodNameContainsFeature("Type");
		features.add(nameContainsType);
		IFeature nameContainsPreference = new MethodNameContainsFeature("Preference");
		features.add(nameContainsPreference);
		IFeature nameContainsNdef = new MethodNameContainsFeature("ndef");
		features.add(nameContainsNdef);
		IFeature nameContainsRoute = new MethodNameContainsFeature("Route");
		features.add(nameContainsRoute);
		IFeature nameContainsDtmf = new MethodNameContainsFeature("dtmf");
		features.add(nameContainsDtmf);
		IFeature nameContainsConnect = new MethodNameContainsFeature("connect");
		features.add(nameContainsConnect);
		IFeature nameContainsMobile = new MethodNameContainsFeature("mobile");
		features.add(nameContainsMobile);
		IFeature nameContainsNotify = new MethodNameContainsFeature("notify");
		features.add(nameContainsNotify);
		IFeature nameContainsConfiguration = new MethodNameContainsFeature("Configuration");
		features.add(nameContainsConfiguration);
		IFeature nameContainsConnectivity = new MethodNameContainsFeature("Connectivity");
		features.add(nameContainsConnectivity);
		IFeature nameContainsMailbox = new MethodNameContainsFeature("Mailbox");
		features.add(nameContainsMailbox);
		IFeature nameContainsSubmit = new MethodNameContainsFeature("submit");
		features.add(nameContainsSubmit);
		IFeature nameContainsSend = new MethodNameContainsFeature("send");
		features.add(nameContainsSend);
		IFeature nameContainsAuth = new MethodNameContainsFeature("auth");
		features.add(nameContainsAuth);
		IFeature nameContainsToken = new MethodNameContainsFeature("token");
		features.add(nameContainsToken);
		IFeature nameContainsSync = new MethodNameContainsFeature("sync");
		features.add(nameContainsSync);
		IFeature nameContainsPassword = new MethodNameContainsFeature("password");
		features.add(nameContainsPassword);
		IFeature nameContainsTethering = new MethodNameContainsFeature("Tethering");
		features.add(nameContainsTethering);
		IFeature nameContainsConnection = new MethodNameContainsFeature("Connection");
		features.add(nameContainsConnection);
		IFeature nameContainsUrl = new MethodNameContainsFeature("url");
		features.add(nameContainsUrl);
		IFeature nameContainsUri = new MethodNameContainsFeature("uri");
		features.add(nameContainsUri);
		IFeature nameContainsCell = new MethodNameContainsFeature("cell");
		features.add(nameContainsCell);
		IFeature mnameContainsLocation = new MethodNameContainsFeature("Location");
		features.add(mnameContainsLocation);
		IFeature mnameContainsSyncable = new MethodNameContainsFeature("syncable");
		features.add(mnameContainsSyncable);
		IFeature mnameContainsFile = new MethodNameContainsFeature("file");
		features.add(mnameContainsFile);
		IFeature mnameContainsSim = new MethodNameContainsFeature("sim");
		features.add(mnameContainsSim);
		IFeature mnameContainsId = new MethodNameContainsFeature("id");
		features.add(mnameContainsId);
		IFeature mnameContainsSerial = new MethodNameContainsFeature("serial");
		features.add(mnameContainsSerial);
		IFeature mnameContainsNumber = new MethodNameContainsFeature("number");
		features.add(mnameContainsNumber);
		IFeature mnameContainsDump = new MethodNameContainsFeature("dump");
		features.add(mnameContainsDump);
		IFeature mnameContainsSystem = new MethodNameContainsFeature("system");
		features.add(mnameContainsSystem);
		IFeature mnameContainsNeighbor = new MethodNameContainsFeature("Neighbor");
		features.add(mnameContainsNeighbor);
		IFeature mnameContainsFeature = new MethodNameContainsFeature("feature");
		features.add(mnameContainsFeature);
		IFeature mnameContainsUsing = new MethodNameContainsFeature("using");
		features.add(mnameContainsUsing);
		IFeature mnameContainsGSM = new MethodNameContainsFeature("GSM");
		features.add(mnameContainsGSM);
		IFeature mnameContainsNetwork = new MethodNameContainsFeature("Network");
		features.add(mnameContainsNetwork);
		IFeature mnameContainsIcc = new MethodNameContainsFeature("Icc");
		features.add(mnameContainsIcc);
		IFeature mnameContainsBluetooth = new MethodNameContainsFeature("bluetooth");
		features.add(mnameContainsBluetooth);
		IFeature mnameContainsUser = new MethodNameContainsFeature("user");
		features.add(mnameContainsUser);
		IFeature mnameContainsDial = new MethodNameContainsFeature("dial");
		features.add(mnameContainsDial);
		IFeature mnameContainsPolicy = new MethodNameContainsFeature("Policy");
		features.add(mnameContainsPolicy);
		IFeature mnameContainsAccount = new MethodNameContainsFeature("Account");
		features.add(mnameContainsAccount);
		IFeature mnameContainsWidth = new MethodNameContainsFeature("width"); //no-category
		features.add(mnameContainsWidth);
		IFeature mnameContainsHeight = new MethodNameContainsFeature("heigth"); //no-category
		features.add(mnameContainsHeight);
		features.add(mnameContainsWidth);
		IFeature mnameContainsHost = new MethodNameContainsFeature("host"); //network-information
		features.add(mnameContainsHost);
		IFeature mnameContainsString = new MethodNameContainsFeature("string"); //network-information
		features.add(mnameContainsString);
		IFeature mnameContainsImei = new MethodNameContainsFeature("imei"); //unique-identifier
		features.add(mnameContainsImei);
		IFeature mnameContainsLog = new MethodNameContainsFeature("log"); 
		features.add(mnameContainsLog);
		IFeature mnameContainsWrite = new MethodNameContainsFeature("write"); 
		features.add(mnameContainsWrite);
		IFeature mnameContainsBattery = new MethodNameContainsFeature("battery"); 
		features.add(mnameContainsBattery);
		IFeature mnameContainsOpen = new MethodNameContainsFeature("open"); 
		features.add(mnameContainsOpen);
		IFeature mnameContainsImage = new MethodNameContainsFeature("image"); 
		features.add(mnameContainsImage);
		IFeature mnameContainsBitmap = new MethodNameContainsFeature("bitmap"); 
		features.add(mnameContainsBitmap);
		IFeature mnameContainsTimeZone = new MethodNameContainsFeature("TimeZone"); 
		features.add(mnameContainsTimeZone);
		IFeature mnameContainsEnabled = new MethodNameContainsFeature("Enabled"); 
		features.add(mnameContainsEnabled);
		IFeature mnameContainsDisabled = new MethodNameContainsFeature("Disabled"); 
		features.add(mnameContainsDisabled);
		
		IFeature nameContainsService = new MethodClassContainsNameFeature("Service");
		features.add(nameContainsService);
		IFeature nameContainsLocation = new MethodClassContainsNameFeature("Location");
		features.add(nameContainsLocation);
		IFeature nameContainsNfc = new MethodClassContainsNameFeature("Nfc");
		features.add(nameContainsNfc);
		IFeature nameContainsTelephony = new MethodClassContainsNameFeature("Telephony");
		features.add(nameContainsTelephony);
		IFeature nameContainsDcma = new MethodClassContainsNameFeature("dcma");
		features.add(nameContainsDcma);
		IFeature nameContainsPhone = new MethodClassContainsNameFeature("Phone");
		features.add(nameContainsPhone);
		IFeature nameContainsPhoneBase = new MethodClassContainsNameFeature("PhoneBase");
		features.add(nameContainsPhoneBase);
		IFeature nameContainsGSM = new MethodClassContainsNameFeature("GSM");
		features.add(nameContainsGSM);
		IFeature cnameContainsConnection = new MethodClassContainsNameFeature("Connection");
		features.add(cnameContainsConnection);
		IFeature nameContainsData = new MethodClassContainsNameFeature("Data");
		features.add(nameContainsData);
		IFeature cnameContainsTethering = new MethodClassContainsNameFeature("Tethering");
		features.add(cnameContainsTethering);
		IFeature cnameContainsConnectivity = new MethodClassContainsNameFeature("Connectivity");
		features.add(cnameContainsConnectivity);
		IFeature nameContainsSip = new MethodClassContainsNameFeature("Sip");
		features.add(nameContainsSip);
		IFeature nameContainsSystem = new MethodClassContainsNameFeature("System");
		features.add(nameContainsSystem);
		IFeature nameContainsSettings = new MethodClassContainsNameFeature("Settings");
		features.add(nameContainsSettings);
		IFeature nameContainsMail = new MethodClassContainsNameFeature("Mail");
		features.add(nameContainsMail);
		IFeature nameContainsMMS = new MethodClassContainsNameFeature("MMS");
		features.add(nameContainsMMS);
		IFeature nameContainsSMS = new MethodClassContainsNameFeature("SMS");
		features.add(nameContainsSMS);
		IFeature nameContainsCalendar = new MethodClassContainsNameFeature("Calendar");
		features.add(nameContainsCalendar);
		IFeature nameContainsAccount = new MethodClassContainsNameFeature("Account");
		features.add(nameContainsAccount);
		IFeature nameContainsManager = new MethodClassContainsNameFeature("Manager");
		features.add(nameContainsManager);
		IFeature nameContainsAudio = new MethodClassContainsNameFeature("Audio");
		features.add(nameContainsAudio);
		IFeature nameContainsVideo = new MethodClassContainsNameFeature("Video");
		features.add(nameContainsVideo);
		IFeature nameContainsMedia = new MethodClassContainsNameFeature("Media");
		features.add(nameContainsMedia);
		IFeature nameContainsEncoder = new MethodClassContainsNameFeature("Encoder");
		features.add(nameContainsEncoder);
		IFeature nameContainsDevice = new MethodClassContainsNameFeature("Device");
		features.add(nameContainsDevice);
		IFeature nameContainsBluetooth = new MethodClassContainsNameFeature("Bluetooth");
		features.add(nameContainsBluetooth);
		IFeature nameContainsWifi = new MethodClassContainsNameFeature("Wifi");
		features.add(nameContainsWifi);
		IFeature cnameContainsSync = new MethodClassContainsNameFeature("Sync");
		features.add(cnameContainsSync);
		IFeature cnameContainsContact = new MethodClassContainsNameFeature("Contact");
		features.add(cnameContainsContact);
		IFeature cnameContainsBrowser = new MethodClassContainsNameFeature("Browser");
		features.add(cnameContainsBrowser);
		IFeature cnameContainsBookmarks = new MethodClassContainsNameFeature("Bookmarks");
		features.add(cnameContainsBookmarks);
		IFeature cnameContainsProfile = new MethodClassContainsNameFeature("Profile");
		features.add(cnameContainsProfile);
		IFeature cnameContainsVcard = new MethodClassContainsNameFeature("VCard");
		features.add(cnameContainsVcard);
		IFeature cnameContainsGallery = new MethodClassContainsNameFeature("Gallery");
		features.add(cnameContainsGallery);
		IFeature cnameContainsLayout = new MethodClassContainsNameFeature("layout"); //no-category
		features.add(cnameContainsLayout);
		IFeature packageNameSignal = new MethodClassContainsNameFeature("Signal"); //network-information
		features.add(packageNameSignal);
		IFeature packageNameCdma = new MethodClassContainsNameFeature("cdma"); //network-information
		features.add(packageNameCdma);
		IFeature packageNamePaint = new MethodClassContainsNameFeature("paint"); //image
		features.add(packageNamePaint);
		IFeature packageNameSSL = new MethodClassContainsNameFeature("ssl"); //network-information
		features.add(packageNameSSL);
		IFeature packageNameScroll = new MethodClassContainsNameFeature("scroll"); //no-category
		features.add(packageNameScroll);
		//IFeature packageNameVideo = new MethodClassContainsNameFeature("video"); //video (duplicate)
		//features.add(packageNameVideo);
		IFeature packageNameBattery = new MethodClassContainsNameFeature("battery"); //system-settings
		features.add(packageNameBattery);
		IFeature packageNameNetwork = new MethodClassContainsNameFeature("network"); //system-settings
		features.add(packageNameNetwork);
		/*
		IFeature cnameContainsInternal = new MethodClassContainsNameFeature("internal");
		features.add(cnameContainsInternal);
		*/
		IFeature cnameContainsContactsContract = new MethodClassContainsNameFeature("ContactsContract");
		features.add(cnameContainsContactsContract);
		IFeature cnameContainsEmail = new MethodClassContainsNameFeature("Email");
		features.add(cnameContainsEmail);
		IFeature cnameContainsThrottle = new MethodClassContainsNameFeature("Throttle");
		features.add(cnameContainsThrottle);
		IFeature cnameContainsTab = new MethodClassContainsNameFeature("Tab");
		features.add(cnameContainsTab);
		IFeature cnameContainsPassword = new MethodClassContainsNameFeature("Password");
		features.add(cnameContainsPassword);
		IFeature cnameContainsPhoneBook = new MethodClassContainsNameFeature("PhoneBook");
		features.add(cnameContainsPhoneBook);
		IFeature cNameContainsFactory = new MethodClassEndsWithNameFeature("Factory");
		features.add(cNameContainsFactory);
		
		IFeature packageNameTelephony = new MethodClassContainsNameFeature("android.telephony.");
		features.add(packageNameTelephony);
		IFeature packageNameIntTelephony = new MethodClassContainsNameFeature("android.internal.telephony.");
		features.add(packageNameIntTelephony);
		IFeature packageNameLocation = new MethodClassContainsNameFeature("android.location");
		features.add(packageNameLocation);
		IFeature packageNameLocationManager = new MethodClassContainsNameFeature("android.location.LocationManager");
		features.add(packageNameLocationManager);
		IFeature packageNameAccounts = new MethodClassContainsNameFeature("android.accounts");
		features.add(packageNameAccounts);
		IFeature packageNameNet = new MethodClassContainsNameFeature("android.net");
		features.add(packageNameNet);
		IFeature packageNameOs = new MethodClassContainsNameFeature("android.os");
		features.add(packageNameOs);
		IFeature nameContainsAndroidBluetooth = new MethodClassContainsNameFeature("android.bluetooth");
		features.add(nameContainsAndroidBluetooth);
		IFeature nameContainsAndroidNFC = new MethodClassContainsNameFeature("android.nfc.");
		features.add(nameContainsAndroidNFC);
		IFeature packageNameExchange = new MethodClassContainsNameFeature("com.android.exchange.");
		features.add(packageNameExchange);
		IFeature packageNameJavaString = new MethodClassContainsNameFeature("java.lang.String");
		features.add(packageNameJavaString);
		IFeature packageNameApacheHttp = new MethodClassContainsNameFeature("org.apache.http");
		features.add(packageNameApacheHttp);
		IFeature packageNameLauncher = new MethodClassContainsNameFeature("com.android.launcher2");
		features.add(packageNameLauncher);
		IFeature packageNameDatabase = new MethodClassContainsNameFeature("android.database");
		features.add(packageNameDatabase);
		IFeature packageNameCrypto = new MethodClassContainsNameFeature("javax.crypto");
		features.add(packageNameCrypto);
		IFeature packageNameXML = new MethodClassContainsNameFeature("org.apache.harmony.xml");
		features.add(packageNameXML);
		IFeature packageNameNIO = new MethodClassContainsNameFeature("java.nio");
		features.add(packageNameNIO);
		IFeature packageNameLog = new MethodClassContainsNameFeature("android.util.Log");
		features.add(packageNameLog);
		IFeature packageNameURL = new MethodClassContainsNameFeature("java.net.URL");
		features.add(packageNameURL);
		IFeature packageNameSip = new MethodClassContainsNameFeature("com.android.internal.telephony.sip");
		features.add(packageNameSip);
		
		IFeature packageNameServerLocation = new MethodClassContainsNameFeature("com.android.server.location");
		features.add(packageNameServerLocation);
		IFeature packageNameServerNet = new MethodClassContainsNameFeature("com.android.server.net");
		features.add(packageNameServerNet);
		IFeature packageNameServerConnectivity = new MethodClassContainsNameFeature("com.android.server.connectivity");
		features.add(packageNameServerConnectivity);
		IFeature packageNameServerSip = new MethodClassContainsNameFeature("com.android.server.sip");
		features.add(packageNameServerSip);
		IFeature packageNameServerUSB = new MethodClassContainsNameFeature("com.android.server.usb");
		features.add(packageNameServerUSB);
		IFeature packageNameServerDisplay = new MethodClassContainsNameFeature("com.android.server.display");
		features.add(packageNameServerDisplay);
		IFeature packageNameServerPower = new MethodClassContainsNameFeature("com.android.server.power");
		features.add(packageNameServerPower);
		IFeature packageNameInternalOs = new MethodClassContainsNameFeature("com.android.internal.os");
		features.add(packageNameInternalOs);
		
		IFeature packageNameAndroidContacts = new MethodClassContainsNameFeature("com.android.contacts");
		features.add(packageNameAndroidContacts);
		IFeature packageNameTelephonyManager = new MethodClassContainsNameFeature("android.telephony.TelephonyManager");
		features.add(packageNameTelephonyManager);
		IFeature packageNameNfc = new MethodClassContainsNameFeature("com.android.nfc");
		features.add(packageNameNfc);
		IFeature packageNameBrowser = new MethodClassContainsNameFeature("com.android.browser");
		features.add(packageNameBrowser);
		IFeature packageNameMMS = new MethodClassContainsNameFeature("com.android.mms");
		features.add(packageNameMMS);
		IFeature packageNameEmail = new MethodClassContainsNameFeature("com.android.email");
		features.add(packageNameEmail);
		IFeature packageNameWifiService = new MethodClassContainsNameFeature("com.android.server.WifiService");
		features.add(packageNameWifiService);
		IFeature packageNameWifi = new MethodClassContainsNameFeature("android.net.wifi");
		features.add(packageNameWifi);
		IFeature packageNameHardwareInput = new MethodClassContainsNameFeature("android.hardware.input");
		features.add(packageNameHardwareInput);
		IFeature packageNameInternalWidget = new MethodClassContainsNameFeature("com.android.internal.widget");
		features.add(packageNameInternalWidget);
		IFeature packageNameGraphics = new MethodClassContainsNameFeature("android.graphics");
		features.add(packageNameGraphics);
		IFeature packageNameParcel = new MethodClassContainsNameFeature("android.os.Parcel");
		features.add(packageNameParcel);
		IFeature packageNameCalendar = new MethodClassContainsNameFeature("com.android.calendar");
		features.add(packageNameCalendar);
		IFeature packageNameServerAm = new MethodClassContainsNameFeature("com.android.server.am");
		features.add(packageNameServerAm);
		IFeature packageNameSyncManager = new MethodClassContainsNameFeature("android.content.SyncManager");
		features.add(packageNameSyncManager);
		

		IFeature paramNdefMessage = new ParameterContainsTypeOrNameFeature("android.nfc.NdefMessage");
		features.add(paramNdefMessage);
		IFeature paramOSMessage = new ParameterContainsTypeOrNameFeature("android.os.Message");
		features.add(paramOSMessage);
		IFeature paramConfiguration = new ParameterContainsTypeOrNameFeature("android.content.res.Configuration");
		features.add(paramConfiguration);
		IFeature paramAccount = new ParameterContainsTypeOrNameFeature("android.accounts.Account");
		features.add(paramAccount);
		IFeature paramContentValues = new ParameterContainsTypeOrNameFeature("android.content.ContentValues");
		features.add(paramContentValues);
		IFeature paramBluetooth = new ParameterContainsTypeOrNameFeature("android.bluetooth.BluetoothDevice");
		features.add(paramBluetooth);
		IFeature paramSip = new ParameterContainsTypeOrNameFeature(".Sip");
		features.add(paramSip);

		IFeature parameterInDial = new ParameterInCallFeature(ANDROID, "dial", CheckType.CheckSink);
		features.add(parameterInDial);
		IFeature parameterInSend = new ParameterInCallFeature(ANDROID, "send", CheckType.CheckSink);
		features.add(parameterInSend);
		IFeature parameterInBroadcast = new ParameterInCallFeature(ANDROID, "broadcast", CheckType.CheckSink);
		features.add(parameterInBroadcast);

		IFeature returnTypeCellLocation = new ReturnTypeFeature(ANDROID, "android.telephony.CellLocation");
		features.add(returnTypeCellLocation);
		IFeature returnTypeCountry = new ReturnTypeFeature(ANDROID, "android.location.Country");
		features.add(returnTypeCountry);
		IFeature returnTypeString = new ReturnTypeFeature(ANDROID, "java.lang.String");
		features.add(returnTypeString);
		IFeature returnTypeBitmap = new ReturnTypeFeature(ANDROID, "android.graphics.Bitmap");
		features.add(returnTypeBitmap);
		IFeature returnTypeAccountArray = new ReturnTypeFeature(ANDROID, "android.accounts.Account[]");
		features.add(returnTypeAccountArray);
		IFeature returnTypeIterator = new ReturnTypeFeature(ANDROID, "java.util.Iterator");
		features.add(returnTypeIterator);
		IFeature returnTypeDrawable = new ReturnTypeFeature(ANDROID, "android.graphics.drawable.Drawable");
		features.add(returnTypeDrawable);
		IFeature returnTypeNFCTag = new ReturnTypeFeature(ANDROID, "android.nfc.Tag");
		features.add(returnTypeNFCTag);
		IFeature returnTypeSIPHeader = new ReturnTypeFeature(ANDROID, "gov.nist.javax.sip.header.SIPHeader");
		features.add(returnTypeSIPHeader);
		IFeature returnTypeSocket = new ReturnTypeFeature(ANDROID, "java.net.Socket");
		features.add(returnTypeSocket);
		IFeature returnTypeURL = new ReturnTypeFeature(ANDROID, "java.net.URLConnection");
		features.add(returnTypeURL);

		IFeature parameterTypeHasFileDescriptor = new ParameterContainsTypeOrNameFeature("java.io.FileDescriptor");
		features.add(parameterTypeHasFileDescriptor);
		
		IFeature callsMessageMethod = new MethodCallsMethodFeature(ANDROID, "", "Icc", true);
		features.add(callsMessageMethod);
		IFeature callsSystemProperties = new MethodCallsMethodFeature(ANDROID, "android.os.SystemProperties", "get");
		features.add(callsSystemProperties);
		IFeature callsSystemSettings = new MethodCallsMethodFeature(ANDROID, "android.provider.Settings", "put");
		features.add(callsSystemSettings);
		IFeature callsCellLocation = new MethodCallsMethodFeature(ANDROID, "", "getCellLocation");
		features.add(callsCellLocation);
		
		IFeature methodBodyContainsAccounts = new MethodBodyContainsObjectFeature(ANDROID, "android.accounts.Account");
		features.add(methodBodyContainsAccounts);
		IFeature methodBodyContainsAccountManger = new MethodBodyContainsObjectFeature(ANDROID, "android.accounts.AccountManager");
		features.add(methodBodyContainsAccountManger);
		IFeature methodBodyContainsPhoneSubInfo = new MethodBodyContainsObjectFeature(ANDROID, "com.android.internal.telephony.IPhoneSubInfo");
		features.add(methodBodyContainsPhoneSubInfo);
		IFeature methodBodyContainsPhone = new MethodBodyContainsObjectFeature(ANDROID, "com.android.internal.telephony.Phone");
		features.add(methodBodyContainsPhone);
		IFeature methodBodyContainsSMSManager = new MethodBodyContainsObjectFeature(ANDROID, "android.telephony.SmsManager");
		features.add(methodBodyContainsSMSManager);
		IFeature methodBodyContainsILocationManager = new MethodBodyContainsObjectFeature(ANDROID, "android.location.ILocationManager");
		features.add(methodBodyContainsILocationManager);
		IFeature methodBodyContainsLocationRequest = new MethodBodyContainsObjectFeature(ANDROID, "android.location.LocationRequest");
		features.add(methodBodyContainsLocationRequest);
		IFeature methodBodyContainsLocationListener = new MethodBodyContainsObjectFeature(ANDROID, "android.location.LocationListener");
		features.add(methodBodyContainsLocationListener);
		IFeature methodBodyContainsCellLocation = new MethodBodyContainsObjectFeature(ANDROID, "android.telephony.CellLocation");
		features.add(methodBodyContainsCellLocation);
		IFeature methodBodyContainsBitmap = new MethodBodyContainsObjectFeature(ANDROID, "android.graphics.Bitmap");
		features.add(methodBodyContainsBitmap);
		IFeature methodBodyContainsMediaSet = new MethodBodyContainsObjectFeature(ANDROID, "com.android.gallery3d.data.MediaSet");
		features.add(methodBodyContainsMediaSet);
		IFeature methodBodyContainsISMS = new MethodBodyContainsObjectFeature(ANDROID, "com.android.internal.telephony.ISms");
		features.add(methodBodyContainsISMS);
		IFeature methodBodyContainsSmsRawData = new MethodBodyContainsObjectFeature(ANDROID, "com.android.internal.telephony.SmsRawData");
		features.add(methodBodyContainsSmsRawData);
		IFeature methodBodyContainsView = new MethodBodyContainsObjectFeature(ANDROID, "android.view.View");
		features.add(methodBodyContainsView);
		IFeature methodBodyContainsViewGroup = new MethodBodyContainsObjectFeature(ANDROID, "android.view.ViewGroup");
		features.add(methodBodyContainsViewGroup);
		IFeature methodBodyContainsSnapshotProvider = new MethodBodyContainsObjectFeature(ANDROID, "com.android.browser.provider.SnapshotProvider");
		features.add(methodBodyContainsSnapshotProvider);
		IFeature methodBodyContainsFileWriter = new MethodBodyContainsObjectFeature(ANDROID, "java.io.FileWriter");
		features.add(methodBodyContainsFileWriter);
		IFeature methodBodyContainsFileDescriptor = new MethodBodyContainsObjectFeature(ANDROID, "java.io.FileDescriptor");
		features.add(methodBodyContainsFileDescriptor);
		IFeature methodBodyContainsFile = new MethodBodyContainsObjectFeature(ANDROID, "java.io.File");
		features.add(methodBodyContainsFile);
		IFeature methodBodyContainsLog = new MethodBodyContainsObjectFeature(ANDROID, "android.util.Log");
		features.add(methodBodyContainsLog);
		IFeature methodBodyContainsBattery = new MethodBodyContainsObjectFeature(ANDROID, "com.android.internal.os.BatteryStatsImpl");
		features.add(methodBodyContainsBattery);

		return features;
	}

}
