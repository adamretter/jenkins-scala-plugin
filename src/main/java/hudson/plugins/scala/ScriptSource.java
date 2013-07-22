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

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.util.DescriptorList;

import java.io.IOException;

public interface ScriptSource extends Describable<ScriptSource> {

    /**
     * In the end, every script is a file...
     *
     * @param projectWorkspace Project workspace (useful when the source has to create temporary file)
     * @return Path to the executed script file
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public FilePath getScriptFile(final FilePath projectWorkspace) throws IOException, InterruptedException;

    /**
     * Able to load script when script path contains parameters
     *
     * @param projectWorkspace Project workspace to create tmp file
     * @param build needed to obtain environment variables
     * @param listener The build listener needed by Environment
     * @return Path to the executed script file
     * @throws IOException
     * @throws InterruptedException
     */
    public FilePath getScriptFile(final FilePath projectWorkspace, AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException;

    public static final DescriptorList<ScriptSource> SOURCES = new DescriptorList<ScriptSource>(ScriptSource.class);
}
