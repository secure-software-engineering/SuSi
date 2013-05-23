package de.ecspride.sourcesinkfinder;

import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;

public class CutoffClassifier extends Classifier {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2903136632626071812L;
	
	private final Classifier baseClassifier;
	private final double threshold;
	private final int fallbackCategory;
	
	public CutoffClassifier(Classifier baseClassifier, double threshold,
			int fallbackCategory) {
		super();
		this.baseClassifier = baseClassifier;
		this.threshold = threshold;
		this.fallbackCategory = fallbackCategory;
	}
	
	@Override
	public double[] distributionForInstance(Instance instance) throws Exception {
		double[] orgDist = baseClassifier.distributionForInstance(instance);
		for (double d : orgDist)
			if (Math.abs(d) > threshold)
				return orgDist;
		double[] newDist = new double[orgDist.length];
		for (int i = 0; i < newDist.length; i++)
			if (i == fallbackCategory)
				newDist[i] = 1.0;
			else
				newDist[i] = 0.0;
		return newDist;
	}

	@Override
	public void buildClassifier(Instances data) throws Exception {
		baseClassifier.buildClassifier(data);
	}

	public Classifier getBaseClassifier() {
		return baseClassifier;
	}
}
