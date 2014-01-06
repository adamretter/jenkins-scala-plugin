/**
 * Copyright (c) 2014, Adam Retter <adam.retter@googlemail.com>
 * All rights reserved.
 *
 * This software includes code from: groovy-plugin https://github.com/jenkinsci/groovy-plugin,
 * Copyright (c) <2007> <Red Hat, Inc.>.
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
package hudson.plugins.scala;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

public class ScalaInstallation extends ToolInstallation implements EnvironmentSpecific<ScalaInstallation>, NodeSpecific<ScalaInstallation> {

    @DataBoundConstructor
    public ScalaInstallation(final String name, final String home, final List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    /**
     * Gets the executable path of this groovy installation on the given target system.
     */
    public String getExecutable(final Launcher launcher, final VirtualChannel channel) throws IOException, InterruptedException {
        return channel.call(new Callable<String, IOException>() {
            @Override
            public String call() throws IOException {
                final File exe = getExePath("scala", launcher.isUnix());
                if(exe.exists()) {
                    return exe.getPath();
                }
                return null;
            }
        });
    }

    private File getExePath(final String execName, final boolean unix) {
        final String scalaHome = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);
        final File binDir = new File(scalaHome, "bin");
        final File path;
        if(unix) {
            path = new File(binDir, execName);
        } else {
            path = new File(binDir, execName + ".bat");
        }
        return path;
    }
    
    @Override
    public ScalaInstallation forEnvironment(final EnvVars environment) {
        return new ScalaInstallation(getName(), environment.expand(getHome()), getProperties().toList());  
    }

    @Override
    public ScalaInstallation forNode(final Node node, final TaskListener log) throws IOException, InterruptedException {
        return new ScalaInstallation(getName(), translateFor(node, log), getProperties().toList());
    }
 
    @Extension
    public static class DescriptorImpl extends ToolDescriptor<ScalaInstallation> {                                                                                                                                                                                                 
        
        private volatile ScalaInstallation[] installations = new ScalaInstallation[0];
        
        public DescriptorImpl() {
            load();
        }
        
        @Override                                                                                                                              
        public String getDisplayName() {                                                                                                       
            return "Scala";                                                                                           
        }                                                                                                                                      
                                                                                                                                               
        @Override                                                                                                                              
        public List<? extends ToolInstaller> getDefaultInstallers() {                                                                          
            return Collections.singletonList(new ScalaInstaller(null));                                                                       
        }                                                                                                                                                                                                                                                                                                                                       
                                                                                                                                              
        @Override                                                                                                                              
        public ScalaInstallation[] getInstallations() {                                                                                       
            return installations;
        }                                                                                                                                      
                                                                                                                                               
        @Override                                                                                                                              
        public void setInstallations(final ScalaInstallation... installations) {                                                                    
            this.installations = installations;
            save();
        }                                                                                                                                      
    }       

}
