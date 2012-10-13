<html>
<body>

Large scale regularized logistic regression code (including Hadoop implementation):
  see: http://www.win-vector.com/blog/2010/12/large-data-logistic-regression-with-example-hadoop-code/
       http://www.win-vector.com/blog/2011/09/the-simpler-derivation-of-logistic-regression/
       http://www.win-vector.com/blog/2010/11/learn-a-powerful-machine-learning-tool-logistic-regression-and-beyond/

The experimental class LogisticTrainPlus allows useful encoding of an arbitrary number of categorical levels.  See:
    http://www.win-vector.com/blog/2012/08/a-bit-more-on-impact-coding/

All material copyright Win-Vector LLC and distributed with license: GPLv3 (see: http//www.gnu.org/copyleft/gpl.html ).  This is demonstration/experimental code.  If you just want to try out standard logistic regression without Hadoop use R ( http://cran.r-project.org ).  You may also want to consider Apache's Mahout which does do logistic regression:  ( see: https://cwiki.apache.org/MAHOUT/logistic-regression.html and http://imiloainf.wordpress.com/2011/11/02/mahout-logistic-regression/ ).

See also: http://www.win-vector.com/blog/2012/10/added-worked-example-to-logistic-regression-project/ for the purpose of this project.



This Logistic codebase is designed to support experimentation on variations of logistic regression including:

<ul>
<li>A pure Java implementation (thus directly usable in Java server environments).</li>
<li>A simple multinomial implementation (that allows more than two possible result categories).</li>
<li>The ability to work with too large for memory data-sets and directly from files or database tables.</li>
<li><a target="_blank" href="http://www.win-vector.com/blog/2010/12/large-data-logistic-regression-with-example-hadoop-code/">A demonstration of the steps needed to use standard Newton-Raphson in Hadoop.</a></li>
<li>Ability to work with <a target="_blank" href="http://www.win-vector.com/blog/2012/08/a-bit-more-on-impact-coding/">arbitrarily large categorical inputs.</a></li>
<li>Provide <a target="_blank" href="http://www.win-vector.com/blog/2010/11/learn-a-powerful-machine-learning-tool-logistic-regression-and-beyond/">explicit L2 model regularization.</a></li>
<li>Implement safe optimization methods (like conjugate gradient, line-search and <a target="_blank" href="http://www.win-vector.com/blog/2012/10/rudie-cant-fail-if-majorized/">majorization</a>) for situations where the standard Iteratively-re-Weighted-Least-Squares/Newton-Raphson fails.</li>
<li>Provide an overall framework to quickly try <em>implementation</em> experiments (as opposed to novel usage experiments).</li>
</ul>

What we mean by this code being "experimental" is that it has capabilities that many standard implementations do not.   In fact most of the items in the above list are not usually made available to the logistic regression user.  But our project is also stand-alone and not as well integrated into existing workflows as standard production systems.  Before trying our code you may want to try <a target="_blank" href="http://cran.r-project.org">R</a> or <a target="_blank" href="https://cwiki.apache.org/MAHOUT/logistic-regression.html">Mahout</a>.

In principle running the code is easy: all you do is supply a training file as a TSV (tab separated file) or CSV (comma separated file), write down the column you want to predict as a schematic formula of the columns you wish to use in your model.  In practice it is a bit harder: you have to have already set up your Java or Hadoop environment to bring in all required dependencies.   

Setting up a Hadoop configuration can range from simple (like the single machine tests in our projects JUnit test suite) to complicated.   Also, for non-trivial clusters you often do not control the configuration (i.e. somebody else supplies the cluster environment).  So we really can't tell you how to set up your non-trivial Hadoop environment (just too many variables).  Some time ago we supplied <a target="_blank" href="http://www.win-vector.com/blog/2010/12/large-data-logistic-regression-with-example-hadoop-code/">a complete example of how to run an example on Amazon's Elastic Map Reduce</a> (but that was in 2010, so the environment may have changed a bit.  However the current code runs at least on Hadoop versions 0.20.0, 1.0.0 and 1.0.3 without modification).

Our intent was never to depend on Hadoop except in the case of very large data (and even then there are other options like sub-sampling and Mahout).  So we supplied direct Java command line options.   Below is a simple example of using these options ( see also: <a target="_blank" href="http://www.win-vector.com/blog/2012/10/added-worked-example-to-logistic-regression-project/">added example blog post</a> ).   We are assuming a command-line environment (for example the Bash shell on OSX or Linux, but this can be done on Windows using either CMD or Cygwin).

We show what works for us in our environment, you will have to adapt to your environment as it differs.

Example of running at the command line (using some Apache support classes, but not running under Hadoop):

<ol>
<li>
   Get a data file.  For our example we took the data file from the <a target="_blank" href="http://archive.ics.uci.edu/ml/datasets/Iris">UCI Iris data example</a> saved it and added a header line of the form "SepalLength,SepalWidth,PetalLength,PetalWidth,TrainingClass" to make the file machine readable.  The edited file is available here: <a target="_blank" href="https://github.com/WinVector/Logistic/blob/master/iris.data.txt">iris.data.txt</a> .

</li>
<li>
   Get all the supporting code you need and set your Java CLASSPATH.  To run this you need all of the classes from:
<ul>
<li>
   <a target="_blank" href="https://github.com/WinVector/Logistic">https://github.com/WinVector/Logistic</a>
</li>
<li>
   <a target="_blank" href="https://github.com/WinVector/SQLScrewdriver">https://github.com/WinVector/SQLScrewdriver</a>
</li>
<li>
   <a target="_blank" href="http://acs.lbl.gov/software/colt/">http://acs.lbl.gov/software/colt/</a>
</li>
<li>
    Some of the Apache commons files (command line parsing and logging).  We got these from the Hadoop-1.0.3 lib directory: <a target="_blank" href="http://hadoop.apache.org/releases.html#Download">http://hadoop.apache.org/releases.html#Download</a>.  It turns out we only need hadoop-1.0.3/lib/commons-logging-1.1.1.jar , hadoop-1.0.3/lib/commons-logging-api-1.0.4.jar and hadoop-1.0.3/lib/commons-cli-1.2.jar from the Hadoop package if we are not running a Map Reduce.
</li>
</ul>

This is complicated- but it is one time set up cost.  In practice you would not manipulate classes directly at the command line by use an IDE like Eclipse or a build manager like Maven to do all of the work.  But not everybody uses the same tools and tools bring in even more dependencies and complications; so we show how to set up the class paths directly.

In our shell (bash on OSX) we set our class variable as follows:

<blockquote>
<code>
  CLASSES="/Users/johnmount/project/hadoop-1.0.3/lib/commons-logging-1.1.1.jar:/Users/johnmount/project/hadoop-1.0.3/lib/commons-logging-api-1.0.4.jar:/Users/johnmount/project/hadoop-1.0.3/lib/commons-cli-1.2.jar:/Users/johnmount/project/Logistic/bin:/Users/johnmount/project/Colt-1.2.0/bin:/Users/johnmount/project/SQLScrewdriver/bin"
</code>
</blockquote>
We are using where we put the files "/Users/johnmount/project/" and the path separator ":" (separator is ";" on Windows).   The path you would use would depend on where you put the files you downloaded.

</li>
<li>
  In the directory you saved the training data file run the logistic training procedure:

<blockquote>
<code>
  java -cp $CLASSES com.winvector.logistic.LogisticTrain -trainURI file:iris.data.txt -sep , -formula "TrainingClass ~ SepalLength + SepalWidth + PetalLength + PetalWidth" -resultSer iris_model.ser
</code>
</blockquote>

This  produces iris_model.ser , the trained model.  The diagnostic printouts show the confusion matrix (tabulation of training class versus predicted class) and show a high degree of training accuracy.  

<pre>
INFO: Consfusion matrix:
prediction	actual	actual	actual
index:outcome	0:Iris-setosa	1:Iris-versicolor	2:Iris-virginica
0:Iris-setosa	50	0	0
1:Iris-versicolor	0	47	1
2:Iris-virginica	0	3	49
</pre>

Notice that there are only 4 training errors (1 Iris-verginica classified as Iris-versicolor and 3 iris-versicolor classified as Irs-virginica).


The model coefficients are also printed as part of the diagnostics.

<pre>
INFO: soln details:
outcome	outcomegroup	variable	kind	level	value
Iris-setosa	0		Const		0.774522294889561
Iris-setosa	0	PetalLength	Numeric		-5.192578560594749
Iris-setosa	0	PetalWidth	Numeric		-2.357410090972314
Iris-setosa	0	SepalLength	Numeric		1.69382466234698
Iris-setosa	0	SepalWidth	Numeric		3.9224697382723903
Iris-versicolor	1		Const		1.8730846627542541
Iris-versicolor	1	PetalLength	Numeric		-0.3747220505092903
Iris-versicolor	1	PetalWidth	Numeric		-2.839314336523609
Iris-versicolor	1	SepalLength	Numeric		1.1786843497402208
Iris-versicolor	1	SepalWidth	Numeric		0.2589801610139257
Iris-virginica	2		Const		-2.6476069576668615
Iris-virginica	2	PetalLength	Numeric		5.567416774143793
Iris-virginica	2	PetalWidth	Numeric		5.196786825681218
Iris-virginica	2	SepalLength	Numeric		-2.8725550015586947
Iris-virginica	2	SepalWidth	Numeric		-4.1815354814927215
</pre>


</li>
<li>
   In the same directory run the logistic scoring procedure:

<blockquote>
<code>
   java -cp $CLASSES com.winvector.logistic.LogisticScore -dataURI file:iris.data.txt -sep , -modelFile iris_model.ser -resultFile iris.scored.tsv
</code>
</blockquote>

This  produces iris.scored.tsv , the final result.  The scored file is essentially the input file (in this case iris.data.txt) copied over with a few prediction columns (predicted category, confidence in predicted category and probability for each possible outcome category) prepended.
</li>
</ol>

For the Hadoop demonstrations of training and scoring the commands are as follows (though obviously some of the details depend on your Hadoop set-up):

Get or build a jar containing the Logistic code, SQLScrewdriver code and free portions of the COLT library (pre built: WinVectorLogistic.Hadoop0.20.2.jar).
Make sure the training file is tab-separated (instead of comma separated). For example the iris data in such a format is here: iris.data.tsv
Run the Hadoop version of the trainer:

/Users/johnmount/project/hadoop-1.0.3/bin/hadoop jar /Users/johnmount/project/Logistic/WinVectorLogistic.Hadoop0.20.2.jar logistictrain iris.data.tsv "TrainingClass ~ SepalLength + SepalWidth + PetalLength + PetalWidth" iris_model.ser

Run the Hadoop version of the scorring function:

/Users/johnmount/project/hadoop-1.0.3/bin/hadoop jar /Users/johnmount/project/Logistic/WinVectorLogistic.Hadoop0.20.2.jar logisticscore iris_model.ser iris.data.tsv scoreDir

Scored output is left in Hadoop format in the user specified scoreDir (slightly different format than the stand-alone programs). The passes take quite a long time due to the overhead of setting up and tearing down a Hadoop environment for such a small problem. Also if you are running in a serious Hadoop environment (like elastic map-reduce) you will have to change certain file names to the type of URI type the system is using. In our elastic map reduce example we used S3 containers which had forms like: “s3n://bigModel.ser” and so on.

The Hadoop code can also be entered using the <code>main()</code>s found in <code>com.winvector.logistic.demo.MapReduceLogisticTrain</code> and <code>com.winvector.logistic.demo.MapReduceScore</code> .  This allows interactive debugging through an IDE (like Eclispe) without having go use the "hadoop" command.

Again: this is experimental code. It can do things other code bases can not. If you need one of its features or capabilities it is very much worth the trouble. But if you can make do with a standard package like R you have less trouble and are more able to interact with others work.

</body>
</html>
