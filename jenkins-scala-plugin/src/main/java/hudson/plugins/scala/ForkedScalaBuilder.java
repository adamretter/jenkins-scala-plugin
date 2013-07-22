/**
 * Copyright (c) 2013, Adam Retter <adam.retter@googlemail.com>
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

import hudson.*;
import hudson.model.*;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import hudson.plugins.scala.executer.ForkedScalaExecutor;

public class ForkedScalaBuilder extends AbstractScalaBuilder {
    private String parameters;
    private boolean debug;
    private boolean suspend;
    private String port;

    public static String DEFAULT_PORT = "4000";
    
    @DataBoundConstructor
    public ForkedScalaBuilder(final String scalaName, final ScriptSource scriptSource, final String parameters, final String classpath, final String scriptParameters, final boolean debug, final boolean suspend, final String port) {
        super(scalaName, scriptSource, classpath, scriptParameters);
        this.parameters = parameters;
        this.debug = debug;
        this.suspend = suspend;
        this.port = port;
    }
    
    //<editor-fold desc="getter/setter">
    public String getParameters() {
        return parameters;
    }

    public void setParameters(final String parameters) {
        this.parameters = parameters;
    }
    
    public boolean isDebug() {
        return debug;
    }

    public void setDebug(final boolean debug) {
        this.debug = debug;
    }

    public boolean isSuspend() {
        return suspend;
    }

    public void setSuspend(final boolean suspend) {
        this.suspend = suspend;
    }

    public String getPort() {
        return port;
    }

    public void setPort(final String port) {
        this.port = port;
    }
    //</editor-fold>

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener, final String scalaHome, final String scalaExecutable, final FilePath script) throws InterruptedException, IOException {
        return new ForkedScalaExecutor().execute(build, launcher, listener, scalaHome, scalaExecutable, script, getParameters(), getClasspath(), getScriptParameters(), debug, suspend, Integer.parseInt(port));
    }
    
    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }
    
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractScalaDescriptor {

        public DescriptorImpl() {
            super(ForkedScalaBuilder.class);
            load();
        }
        
        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Execute Scala script (Forked)";
        }
        
        public FormValidation doCheckPort(final StaplerRequest req,
            @AncestorInPath final AbstractProject context,
            @QueryParameter final String value) {
            
            FormValidation validationResult;
            try {
                final int port = Integer.parseInt(value);
                if(port < 1 || port > 65535) {
                    validationResult = FormValidation.error("The entered TCP Port must be between 1 and 65,535 inclusive! Please enter a valid TCP Port number...");
                } else {
                    validationResult = FormValidation.ok();
                }
            } catch(final NumberFormatException nfe) {
                validationResult = FormValidation.error("The entered TCP Port is not a valid number! Please enter a valid TCP Port number...");
            }
            return validationResult;
        }
        
        @Override
        public Builder newInstance(final StaplerRequest req, final JSONObject data) throws FormException {
            final ScriptSource source = getScriptSource(req, data);
            final String scalaName = data.getString("scalaName");
            final String params = data.getString("parameters");
            final String classpath = data.getString("classPath").trim();
            final String scriptParameters = data.getString("scriptParameters");
            final boolean debug = data.has("debug");
            final boolean suspend;
            final String port;
            if(debug) {
                final JSONObject joDebug = data.getJSONObject("debug");
                suspend = joDebug.getBoolean("suspend");
                port = joDebug.getString("port");
            } else {
                suspend = false;
                port = DEFAULT_PORT;
            }
            
            return new ForkedScalaBuilder(scalaName, source, params, classpath, scriptParameters, debug, suspend, port);
        }
        
        @Override
        public boolean configure(final StaplerRequest req, final JSONObject json) throws hudson.model.Descriptor.FormException {
            save();
            return true;
        }
    }
}
