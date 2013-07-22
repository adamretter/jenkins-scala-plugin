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
package uk.org.adamretter.hudson.plugins.scala.builder

import hudson.plugins.scala.{StringScriptSource, ScalaInstallation, ScriptSource}
import hudson.model.{BuildListener, AbstractBuild, Computer}
import hudson.{EnvVars, FilePath, Launcher, Util}
import hudson.remoting.VirtualChannel
import java.io.IOException

class ForkedScalaExecutor {

  def execute(build: AbstractBuild[_, _], launcher: Launcher, listener: BuildListener, scalaInstallation: ScalaInstallation, scriptSource: ScriptSource, scalaParameters: String, classpath: String, scriptParameters: String, debug: Boolean, suspend: Boolean, jdwpPort: Integer) : Boolean = {

    val DEFAULT_CMD = "scala";

    def scalaCmdExecutable(env: EnvVars, channel: VirtualChannel) : String = {
      Option(scalaInstallation) match {
        case Some(installation) => {
          Option(scalaInstallation
            .forNode(Computer.currentComputer().getNode(), listener)
            .forEnvironment(env)
            .getExecutable(launcher, channel))
          .getOrElse({
            listener.getLogger().println("[SCALA WARNING] Scala executable is null, please check your Jenkins Scala configuration, trying fallback 'scala' instead.");
            DEFAULT_CMD
          })
        }
        case None => DEFAULT_CMD
      }
    }

    //TODO add checkbox options for "-nocompdaemon" and "-savecompiled" make nocompdaemon on by default
    def scalaCmdParameters : Option[String] = Some(("-nocompdaemon" :: Option(scalaParameters).toList).flatten.mkString(" "))

    def javaDebugParameters : Option[String] = {
      def booleanToChar(boolean: Boolean) = if(boolean) 'y' else 'n'
      debug match {
        case true => Some(s"-J-debug -J-Xrunjdwp:transport=dt_socket,server=y,suspend=${booleanToChar(suspend)},address=$jdwpPort")
        case false => None
      }
    }

    def scalaClassPathParameter : Option[String] = Option(classpath).map(cl => "-cp " + cl)

    def execCommand(env: EnvVars, script: FilePath) : String = {
      val cmdParts: List[Option[String]] = List(Some(scalaCmdExecutable(env, script.getChannel)), scalaCmdParameters, javaDebugParameters, scalaClassPathParameter, Option(script.getRemote), Option(scriptParameters))
      cmdParts.flatten.mkString(" ")
    }

    def executeScript(env: EnvVars, workspace: FilePath, script: FilePath) : Boolean = {
      try {
        val cmd = execCommand(env, script)
        //val shell = new Shell(scala_launch_cmd)
        listener.getLogger().println("Scala command is: " + cmd)

        val result = launcher.launch().cmds(cmd).envs(env).stdout(listener).pwd(workspace).join()
        //shell.perform(build, launcher, listener);
        result == 0
      } finally {
        //try nd delete the script source
        if(scriptSource.isInstanceOf[StringScriptSource]) {
          try {
            script.delete()
          } catch {
            case ioe: IOException => {
              Util.displayIOException(ioe, listener);
              ioe.printStackTrace(listener.fatalError("Unable to delete script file: " + script))
            }
          }
        }
      }
    }

    val env = build.getEnvironment(listener)
    if(scalaInstallation != null) {
      env.put("SCALA_HOME", scalaInstallation.getHome())
    }

    try {
      val workspace = build.getWorkspace()
      Option(scriptSource.getScriptFile(workspace, build, listener)) match {
        case Some(script) => {
          executeScript(env, workspace, script)
        }
        case None => {
          listener.fatalError("Could not process Scala Script")
          false
        }
      }
    } catch {
      case ioe: IOException => {
        Util.displayIOException(ioe, listener);
        ioe.printStackTrace(listener.fatalError("command execution failed"))
      }
      false
    }
  }
}
