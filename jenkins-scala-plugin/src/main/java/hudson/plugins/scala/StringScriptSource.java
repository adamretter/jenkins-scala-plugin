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
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.URL;

public class StringScriptSource implements ScriptSource {

    private String script;

    @DataBoundConstructor
    public StringScriptSource(final String script) {
        this.script = script;
    }

    /**
     * In the end, every script is a file...
     *
     * @param projectWorkspace Project workspace (useful when the source has to create temporary file)
     * @return Path to the executed script file
     */
    @Override
    public FilePath getScriptFile(final FilePath projectWorkspace) throws IOException, InterruptedException {
        return projectWorkspace.createTextTempFile("hudson", ".scala", script, true);
    }

    /**
     * Able to load script when script path contains parameters
     *
     * @param projectWorkspace Project workspace to create tmp file
     * @param build The build is used to obtain environment variables
     * @param listener build listener needed by Environment
     * @return Path to the executed script file
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    @Override
    public FilePath getScriptFile(final FilePath projectWorkspace, final AbstractBuild<?,?> build, final BuildListener listener) throws IOException, InterruptedException {
        return getScriptFile(projectWorkspace);
    }

    //<editor-fold desc="getter/setter">
    public String getScript() {
        return script;
    }

    public void setScript(final String script) {
        this.script = script;
    }
    //</editor-fold>

    @Override
    public Descriptor<ScriptSource> getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<ScriptSource> {

        public DescriptorImpl() {
            super(StringScriptSource.class);
        }

        @Override
        public String getDisplayName() {
            return "Scala script";
        }

        @Override
        public ScriptSource newInstance(final StaplerRequest req, final JSONObject formData) {
            return req.bindJSON(StringScriptSource.class, formData);
        }
    }
}
