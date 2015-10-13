SuSi
====

SuSi - our tool to automatically discover and categorize sources and sinks in the Android framework

Running SuSi
-------------

In order to run SuSi, you need two different types on input files: First, a JAR file containing a full implementation
of the Android OS that you want to analyze. Second, a set of hand-annotated input files to use as ground truth for
the machine learning algorithm.

The fully-implemented Android JAR files must be extracted from an emulator or a real phone. The platform JAR files shipped
with Google's Android SDK are not suitable for SuSi since they only contain method stubs, but not actual implementations.
In these stubbed files, every method simply throws a NotImplementedException without carrying out any actual behavior.
For some versions of the Android OS, there are [pre-generated JAR files](https://github.com/Sable/android-platforms)
available on Github. If you want to run SuSi on another version, you need to generate the respective JAR file on your own.

For the hand-annotated ground truth, our own permissionMethodWithLabel.pscout file is a good starting point. You can
either use it as-is to reproduce the results from our paper, or extend it to meet your own needs.

Finally, start the machine learner:

```
java -cp lib/weka.jar:soot-trunk.jar:soot-infoflow.jar:soot-infoflow-android.jar:SuSi.jar de.ecspride.sourcesinkfinder.SourceSinkFinder android.jar permissionMethodWithLabel.pscout out.pscout 
```