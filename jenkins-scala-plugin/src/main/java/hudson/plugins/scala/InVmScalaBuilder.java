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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.Builder;
import java.io.IOException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import hudson.plugins.scala.executer.InVmScalaExecuter;

public class InVmScalaBuilder extends AbstractScalaBuilder {

    @DataBoundConstructor
    public InVmScalaBuilder(final String scalaName, final ScriptSource scriptSource, final String classpath, final String scriptParameters) {
        super(scalaName, scriptSource, classpath, scriptParameters);
    }
    
    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener, final String scalaHome, final String scalaExecutable, final FilePath script) throws InterruptedException, IOException {
        return new InVmScalaExecuter().execute(build, launcher, listener, scalaHome, script, getClasspath(), getScriptParameters());
    }
    
    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }
    
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractScalaDescriptor {

        public DescriptorImpl() {
            super(InVmScalaBuilder.class);
            load();
        }
        
        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            final Authentication authentication = Jenkins.getAuthentication();
            return Hudson.getInstance().getACL().hasPermission(authentication, Jenkins.RUN_SCRIPTS);
        }
        
        @Override
        public String getDisplayName() {
            return "Execute Scala script (inside Job VM)";
        }
        
        @Override
        public Builder newInstance(final StaplerRequest req, final JSONObject data) throws FormException {
            final ScriptSource source = getScriptSource(req, data);
            final String scalaName = data.getString("scalaName");
            final String classpath = data.getString("classPath").trim();
            final String scriptParameters = data.getString("scriptParameters");
            return new InVmScalaBuilder(scalaName, source, classpath, scriptParameters);
        }
        
        @Override
        public boolean configure(final StaplerRequest req, final JSONObject json) throws hudson.model.Descriptor.FormException {
            save();
            return true;
        }
    }
}
