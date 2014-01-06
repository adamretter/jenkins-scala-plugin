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
import hudson.{FilePath, Launcher}
import hudson.remoting.VirtualChannel
import jenkins.model.{Jenkins}
import java.io.{Closeable, File => JFile, IOException, ObjectInputStream, ObjectOutputStream, PrintWriter}
import java.net.URLClassLoader
import java.util.concurrent.locks.{Lock, ReentrantReadWriteLock}
import scala.Console
import scala.tools.nsc.{CommonRunner, GenericRunnerSettings}
import scala.tools.nsc.io.File
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.reflect.ReflectGlobal


trait InVmRunner extends CommonRunner {

  import scala.tools.nsc.util.ScalaClassLoader
  import scala.tools.nsc.util.Exceptional.unwrap

  def run(cl: ClassLoader, objectName: String, arguments: Seq[String]) {
    ScalaClassLoader(cl).run(objectName, arguments)
  }

  def runAndCatch(cl: ClassLoader, objectName: String, arguments: Seq[String]): Either[Throwable, Boolean] = {
    try {
      run(cl, objectName, arguments);
      Right(true)
    } catch {
      case e: Throwable => Left(unwrap(e))
    }
  }
}

object ObjectInVmRunner extends InVmRunner {}

class FilePathExtractor extends FilePath.FileCallable[JFile] {
  def invoke(f: JFile, channel: VirtualChannel) = f
}

class InVmScalaExecuter extends ScalaExecuter {

  def execute(build: AbstractBuild[_, _], launcher: Launcher, listener: BuildListener, scalaHome: String, script: FilePath, classpath: String, scriptParameters: String) : Boolean = {

    lazy val workspace = build.getWorkspace

    def errorFn(message: String) {
      listener.fatalError(message)
    }

    def jenkinsReporter(settings: GenericRunnerSettings) = new ConsoleReporter(settings, Console.in, new PrintWriter(listener.getLogger))

    /**
     * @return the path to the directory containing the
     *         compiled class files
     */
    def compile(settings: GenericRunnerSettings) : FilePath = {
      val compilationDirectory = workspace.createTempDir("scala-plugin", ".compilation")
      listener.getLogger.println(s"Using temporary directory for compilation: $compilationDirectory")

      val compilationDirectoryFile = compilationDirectory.act(new FilePathExtractor)
      settings.outdir.value = compilationDirectoryFile.getAbsolutePath

      logProcess("compilation") {
        val reporter = jenkinsReporter(settings)
        val compiler = new ReflectGlobal(settings, reporter, Jenkins.getInstance.getPluginManager.uberClassLoader)
        val run = new compiler.Run

        val scriptFile = script.act(new FilePathExtractor)

        run.compile(List(scriptFile.getAbsolutePath))
      }

      compilationDirectory
    }

    def execute(settings: GenericRunnerSettings, compilationDir: JFile, scriptParameters: Seq[String] = Seq.empty) : Boolean = {
      val cp = File(settings.outdir.value).toURL +: settings.classpathURLs

      //redirect stdout and stderr
      Console.setOut(listener.getLogger)
      Console.setErr(listener.getLogger)

      val runnerClasspath = new URLClassLoader(Array(File(compilationDir).toURL), Jenkins.getInstance.getPluginManager.uberClassLoader)
      ObjectInVmRunner.runAndCatch(runnerClasspath, settings.script.value, scriptParameters) match {
        case Left(ex) => {
          ex.printStackTrace(listener.fatalError(ex.getMessage))
          false
        }
        case Right(result) =>
          result
      }
    }

    def logProcess[T](processName: String)(process: => T): T = {
      listener.getLogger.println(s"Starting $processName...")
      val result = process //exec process
      listener.getLogger.println(s"Complete $processName.")

      result
    }

    /**
     * Sets up the user defined classpath
     */
    def setupUserDefinedClasspath(settings: GenericRunnerSettings) {
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
    }

    def extractScriptParameters() : Seq[String] = {
      nonEmptyString(scriptParameters) match {
        case Some(scriptParameters) => {
          scriptParameters.split("""\s""")
        }
        case None => Seq.empty
      }
    }

    @throws(classOf[IOException])
    def using[C <: Closeable, T](is: C)(f: C => T): T = {
      try {
        f(is)
      } finally {
        is.close()
      }
    }

    type Filename = String
    type Hash = String
    type CompilationDirectory = FilePath

    val cacheFile = new FilePath(workspace, "invmscalaexecuter.cache")
    val cacheLock = new ReentrantReadWriteLock

    def locked[T](lock: => Lock, f: => T) : T = {
      lock.lock()
      try {
        f
      } finally {
        lock.unlock()
      }
    }

    def readLocked[T](f: => T) : T = locked(cacheLock.readLock, f)

    def writeLocked[T](f: => T) : T = locked(cacheLock.writeLock, f)

    def getCache() : Map[Filename, (Hash, CompilationDirectory)] = {
      if(cacheFile.exists) {
        //read the cache from the file
        readLocked {
          using(new ObjectInputStream(cacheFile.read)) {
            is =>
              is.readObject().asInstanceOf[Map[Filename, (Hash, FilePath)]]
          }
        }
      } else {
        //no cache
        Map.empty[Filename, (Hash, CompilationDirectory)]
      }
    }

    /**
     * @return Some(cachedCompilationDirectory or None
     */
    def findCachedCompilation : Option[FilePath] = {
      val cache = getCache()
      if(cache.isEmpty) {
        //no cache, therefore this is a new script that needs compiling
        None
      } else {

        //calculate the hash of the current script
        val currentHash = script.digest

        //check currentHash against prevHash of the script
        cache.get(script.getName()) match {
          case Some((prevHash, cachedCompilationDirectory)) if(prevHash == currentHash) =>
            //no change to script, can used cached version

            //check cached compilation still exists
            if(cachedCompilationDirectory.exists) {
              listener.getLogger.println("Using cached compilation: " + cachedCompilationDirectory.getRemote)
              Some(cachedCompilationDirectory)
            } else {
              None
            }
          case _ =>
            //script has changed, or this is a new script, therefore we need to re-compile
            None
        }
      }
    }

    def updateCacheRecord(newCompilationDirectory: FilePath) {
      val cache = getCache()

      val updatedCache = cache + (script.getName -> (script.digest, newCompilationDirectory))

      writeLocked {
        if(cacheFile.exists) {
          cacheFile.deleteContents
        }
        using(new ObjectOutputStream(cacheFile.write())) {
          os =>
            os.writeObject(updatedCache)
        }
      }
    }

    def compileAndExecute = {
      val settings = new GenericRunnerSettings(errorFn)
      settings.termConflict.tryToSetColon(List("object")) //"-Yresolve-term-conflict:object" needed as Jenkins uses packages and objects of the same name
      listener.getLogger.println(s"Using boot classpath: ${settings.bootclasspath.toString}")

      setupUserDefinedClasspath(settings)

      listener.getLogger.println(s"Using classpath: ${settings.classpath.toString}")
      listener.getLogger.println(s"classpathURLs: ${settings.classpathURLs}")

      //set script parameters
      val sParams = extractScriptParameters

      //this tells the compiler that we are a script and not a valid scala compilation unit, so we set a default name for the class
      //also used as the classname for the executer to execute
      //settings.script.value = "MainInVmScalaScript" + System.nanoTime //TODO must be unique, need a unique class name depending on when the script changes (hash of script) or file changes (last modified time?)
      settings.script.value = script.getBaseName

      listener.getLogger.println(s"Using Settings: ${settings.toConciseString}")

      //compiler or get cached compiled
      val compilationDirectory = findCachedCompilation getOrElse {
        val newCompilationDirectory = compile(settings)
        updateCacheRecord(newCompilationDirectory)
        newCompilationDirectory
      }

      //execute
      val compilationDirectoryFile = compilationDirectory.act(new FilePathExtractor)
      logProcess("Execution") {
        execute(settings, compilationDirectoryFile, sParams)
      }
    }

    //business time!
    Option(script) match {
      case Some(script) =>
        compileAndExecute
        true

      case None =>
        listener.fatalError("Could not process Scala Script, no script provided!")
        false
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
