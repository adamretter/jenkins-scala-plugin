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

import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescriptorList;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractScalaBuilder extends Builder {

    private String scalaName;
    private ScriptSource scriptSource;
    private String parameters;
    private String classpath;
    private String scriptParameters;

    public AbstractScalaBuilder(final String scalaName, final ScriptSource scriptSource, final String parameters, final String classpath, final String scriptParameters) {
        this.scalaName = scalaName;
        this.scriptSource = scriptSource;
        this.parameters = parameters;
        this.classpath = classpath;
        this.scriptParameters = scriptParameters;
    }

    //<editor-fold desc="getter/setter">
    public ScriptSource getScriptSource() {
        return scriptSource;
    }

    public void setScriptSource(final ScriptSource scriptSource) {
        this.scriptSource = scriptSource;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(final String parameters) {
        this.parameters = parameters;
    }

    public String getClasspath() {
        return classpath;
    }

    public void setClasspath(final String classpath) {
        this.classpath = classpath;
    }

    public String getScriptParameters() {
        return scriptParameters;
    }

    public void setScriptParameters(final String scriptParameters) {
        this.scriptParameters = scriptParameters;
    }

    //</editor-fold>

    protected ScalaInstallation getScalaInstallation() {
        for(final ScalaInstallation scalaInstallation : Hudson.getInstance().getDescriptorByType(ScalaInstallation.DescriptorImpl.class).getInstallations()) {
            if(scalaInstallation.getName().equals(scalaName)) {
                return scalaInstallation;
            }
        }
        return null;
    }

    public static abstract class AbstractScalaDescriptor extends BuildStepDescriptor<Builder> {
        
        public AbstractScalaDescriptor(final Class<? extends Builder> clazz) {
            super(clazz);
        }

        //@CopyOnWrite
        //private volatile List<ScalaInstallation> installations2 = new ArrayList<ScalaInstallation>();
        
        protected ScriptSource getScriptSource(final StaplerRequest req, final JSONObject data) throws FormException {
            final Object scriptSourceObject = data.get("scriptSource");

            if(scriptSourceObject instanceof JSONArray) {
                // Dunno why this happens. Let's fix the JSON object so that
                // newInstanceFromRadioList() doesn't go mad.

                final JSONArray scriptSourceJSONArray = (JSONArray) scriptSourceObject;
                final JSONObject scriptSourceJSONObject = new JSONObject();
                final Object nestedObject = scriptSourceJSONArray.get(1);

                if(nestedObject instanceof JSONObject) {
                    // command/file path
                    scriptSourceJSONObject.putAll((JSONObject) nestedObject);

                    // selected radio button index
                    scriptSourceJSONObject.put("value", scriptSourceJSONArray.get(0));

                    data.put("scriptSource", scriptSourceJSONObject);
                }
            }

            return ScriptSource.SOURCES.newInstanceFromRadioList(data, "scriptSource");
        }

        public static DescriptorList<ScriptSource> getScriptSources() {
            return ScriptSource.SOURCES;
        }

        // Used for grouping radio buttons together
        private AtomicInteger instanceCounter = new AtomicInteger(0);

        public int nextInstanceID() {
            return instanceCounter.incrementAndGet();
        }
        
        public ScalaInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(ScalaInstallation.DescriptorImpl.class).getInstallations();
        }
    }
}
