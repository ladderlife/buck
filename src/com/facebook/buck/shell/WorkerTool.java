/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.shell;

import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BinaryBuildRule;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;

public class WorkerTool extends NoopBuildRule implements HasRuntimeDeps {

  private final BinaryBuildRule exe;
  private final String args;
  private final ImmutableMap<String, String> env;

  protected WorkerTool(
      BuildRuleParams ruleParams,
      SourcePathResolver resolver,
      BinaryBuildRule exe,
      String args,
      ImmutableMap<String, String> env) {
    super(ruleParams, resolver);
    this.exe = exe;
    this.args = args;
    this.env = env;
  }

  public Path getTempDir() {
    return BuildTargets.getScratchPath(
        getProjectFilesystem(), getBuildTarget(), "%s__worker");
  }

  public BinaryBuildRule getBinaryBuildRule() {
    return this.exe;
  }

  public String getArgs() {
    return this.args;
  }

  public ImmutableMap<String, String> getEnv() {
    return this.env;
  }

  @Override
  public ImmutableSortedSet<BuildRule> getRuntimeDeps() {
    return getDeps();
  }
}
