Scala plugin for Jenkins
========================

[![Build Status](https://travis-ci.org/adamretter/jenkins-scala-plugin.png?branch=master)](https://travis-ci.org/adamretter/jenkins-scala-plugin)

The plugin offers the facility to Install and execute Scala scripts as a build step:

* Scala Installer (available in 'Manage Jenkins')
* Scala Forked Executer (available as a Build Step in Jobs)
* Scala In-VM Executer (available as a Build Step in Jobs)

The In-VM Executer allows you access to various Hudson Objects so that you can extract information about the build, set properties of the build and also control the build to a certain extent.

Most of the plugin is written in Java, as I could never get Jenkins to pick up it's annotations from Scala classes. However some parts of the plugin require tight integration with NSC (New Scala Compiler) and as such these are implemented in Scala and invoked from Java.

This project is available under the BSD Simplified License. Enjoy!

The Scala plugin is based on the groovy-plugin https://github.com/jenkinsci/groovy-plugin

Install Instructions
====================
Until the plugin is published to the Jenkins repository, you may build and install it by:

$ git clone git@github.com:adamretter/jenkins-scala-plugin.git

$ cd jenkins-scala-plugin

$ mvn clean install

You can then upload the HPI file jenkins-scala-plugin/target/jenkins-scala-plugin.hpi to Jenkins through the 'Advanced' panel of its 'Manage Plugins' Web UI.

Example In-Vm Executer Use
==========================
Using the following Script it is possible to add a release number based on the the buildnumber to a build parameter:

```scala
import hudson.model.Executor
import hudson.model.Run
import hudson.model.ParametersAction
import hudson.model.StringParameterValue
import scala.collection.JavaConversions._

val executable = Thread.currentThread.asInstanceOf[Executor].getCurrentExecutable()
val run : Run[_, _] = executable.asInstanceOf[Run[_, _]]
val release = "x.y." + run.getNumber

println(s"Setting RELEASE_NO as: $release")
run.addAction(new ParametersAction(List(new StringParameterValue("RELEASE_NO", release))))
```

The above code is an indirect port to Scala of the Groovy example given here: http://www.agitech.co.uk/implementing-a-deployment-pipeline-with-jenkins/