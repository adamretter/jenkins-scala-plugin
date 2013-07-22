jenkins-scala-plugin
====================

Scala plugin for Jenkins

The plugin offers the facility to Install and execute Scala scripts as a build step:

* Scala Installer (available in 'Manage Jenkins')
* Scala Forked Executer (available as a Build Step in Jobs)
* Scala In-VM Executer (available as a Build Step in Jobs)

The In-VM Executer allows you access to various Hudson Objects so that you can extract information about the build, set properties of the build and also control the build to a certain extent.

Most of the plugin is written in Java, as I could never get Jenkins to pick up it's annotations from Scala classes. However some parts of the plugin require tight integration with NSC (New Scala Compiler) and as such these are implemented in Scala and invoked from Java.

This project is available under the BSD Simplified License. Enjoy!

The Scala plugin is based on the groovy-plugin https://github.com/jenkinsci/groovy-plugin
