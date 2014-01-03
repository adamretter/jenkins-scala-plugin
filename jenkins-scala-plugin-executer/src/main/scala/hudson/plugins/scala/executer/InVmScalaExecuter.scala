/**
 * Copyright (c) 2013, Adam Retter <adam.retter@googlemail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *   Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 *
 *   Neither the name of the {organization} nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package hudson.plugins.scala.executer

import hudson.model.{BuildListener, AbstractBuild}
import jenkins.model.{Jenkins}
import hudson.{FilePath, Launcher}
import java.io.PrintWriter
import scala.tools.nsc.{CommonRunner, GenericRunnerSettings}
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.io._
import scalax.file.Path
import scala.tools.reflect.ReflectGlobal
import scala.Some
import java.net.URLClassLoader

trait InVmRunner extends CommonRunner {

  import scala.tools.nsc.util.ScalaClassLoader
  import scala.tools.nsc.util.Exceptional.unwrap

  def run(cl: ClassLoader, objectName: String, arguments: Seq[String]) {
    ScalaClassLoader(cl).run(objectName, arguments)
  }

  def runAndCatch(cl: ClassLoader, objectName: String, arguments: Seq[String]): Either[Throwable, Boolean] = {
    try   { run(cl, objectName, arguments) ; Right(true) }
    catch { case e: Throwable => Left(unwrap(e)) }
  }
}

object ObjectInVmRunner extends InVmRunner {}

class InVmScalaExecuter extends ScalaExecuter {

  def execute(build: AbstractBuild[_, _], launcher: Launcher, listener: BuildListener, scalaHome: String, script: FilePath, classpath: String, scriptParameters: String) : Boolean = {

    def errorFn(message: String) {
      listener.fatalError(message)
    }

    def jenkinsReporter(settings: GenericRunnerSettings) = new ConsoleReporter(settings, Console.in, new PrintWriter(listener.getLogger))

    def compile(settings: GenericRunnerSettings, script: FilePath) {
      val reporter = jenkinsReporter(settings)

      val compiler = new ReflectGlobal(settings, reporter, Jenkins.getInstance.getPluginManager.uberClassLoader)
      val run = new compiler.Run
      run.compile(List(script.getRemote))
    }

    def execute(settings: GenericRunnerSettings, scriptParameters: Seq[String] = Seq.empty) : Boolean = {
      val cp = File(settings.outdir.value).toURL +: settings.classpathURLs

      //redirect stdout and stderr
      Console.setOut(listener.getLogger)
      Console.setErr(listener.getLogger)

      val runnerClasspath = new URLClassLoader(Array(File(settings.outdir.value).toURL), Jenkins.getInstance.getPluginManager.uberClassLoader)
      ObjectInVmRunner.runAndCatch(runnerClasspath, settings.script.value, scriptParameters) match {
        case Left(ex) => {
          ex.printStackTrace(listener.fatalError(ex.getMessage))
          false
        }
        case Right(result) =>
          result
      }
    }

    def logProcess[T](processName: String, process: => T): T = {
      listener.getLogger.println(s"Starting $processName...")
      val result = process //exec process
      listener.getLogger.println(s"Complete $processName.")

      result
    }

    //TEMP
    listener.getLogger.println("app.class.path=" + Jenkins.getInstance.getPluginManager.uberClassLoader.getResource("app.class.path"))
    listener.getLogger.println("boot.class.path=" + Jenkins.getInstance.getPluginManager.uberClassLoader.getResource("boot.class.path"))
    //END TEMP


    val settings = new GenericRunnerSettings(errorFn)
    settings.termConflict.tryToSetColon(List("object")) //"-Yresolve-term-conflict:object" needed as Jenkins uses packages and objects of the same name
    listener.getLogger.println(s"Using boot classpath: ${settings.bootclasspath.toString}")

    //set the user classpath
    nonEmptyString(classpath) match {
      case Some(classpath) => {
        val classpathEntries = if(launcher.isUnix) {
          classpath.split(':')
        } else {
          classpath.split(';')
        }

        for(classpathEntry <- classpathEntries) {
          settings.classpath.append(classpathEntry)
        }
      }
      case None =>
    }

    listener.getLogger.println(s"Using classpath: ${settings.classpath.toString}")

    listener.getLogger.println(s"classpathURLs: ${settings.classpathURLs}")

    //set script parameters
    val sParams : Seq[String] = nonEmptyString(scriptParameters) match {
      case Some(scriptParameters) => {
        scriptParameters.split("""\s""")
      }
      case None => Seq.empty
    }

    //this tells the compiler that we are a script and not a valid scala compilation unit, so we set a default name for the class
    settings.script.value = "MainInVmScalaScript" + System.nanoTime //TODO must be unique, need a unique class name depending on when the script changes (hash of script) or file changes (last modified time?)

    //set directory for compilation
    val workspace = build.getWorkspace
    val compilationDirectory = workspace.createTempDir("scala-plugin", ".compilation").getRemote
    settings.outdir.value = compilationDirectory
    listener.getLogger.println(s"Using temporary directory for compilation: $compilationDirectory")

    listener.getLogger.println(s"Using Settings: ${settings.toConciseString}")

    Option(script) match {
      case Some(script) => {
        logProcess("compilation", compile(settings, script))
        logProcess("Execution", execute(settings, sParams))
        //TODO clean up the compilationDirectory? //or use VirtualDirectory like ScalaScriptEngine?
      }
      case None => {
        listener.fatalError("Could not process Scala Script")
        false
      }
    }
  }

  /*
  class JenkinsReporter(settings: GenericRunnerSettings, listener: BuildListener) extends AbstractReporter {

    override def display(pos: Position, msg: String, severity: Severity) {

      def printMessage(label: String) {
        pos match {
          case FakePos(fakePos) =>
            listener.getLogger.println(s"$label $fakePos $msg")
          case NoPosition =>
            listener.getLogger.println(s"$label $msg")
          case _ =>
            listener.getLogger.println(s"label ${pos.source.file.path}:[${pos.line}:${pos.column}]: $msg")
        }
      }

      val label : String = severity match {
        case WARNING =>
          "warning"
        case ERROR =>
          "error"
        case _ =>
          ""
      }
      printMessage(label)
    }

    override def displayPrompt() {
      //Unknown use, possibly to pause the process of the Job awaiting user response?
    }
  }*/
}
