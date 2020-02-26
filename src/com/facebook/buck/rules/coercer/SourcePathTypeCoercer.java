/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.rules.coercer;

import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.model.BuildTargetWithOutputs;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnconfiguredBuildTargetWithOutputs;
import com.facebook.buck.core.path.ForwardRelativePath;
import com.facebook.buck.core.sourcepath.DefaultBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.reflect.TypeToken;
import java.nio.file.Path;

/** Coerce to {@link com.facebook.buck.core.sourcepath.SourcePath}. */
public class SourcePathTypeCoercer extends LeafTypeNewCoercer<String, SourcePath> {
  private final TypeCoercer<UnconfiguredBuildTargetWithOutputs, BuildTargetWithOutputs>
      buildTargetWithOutputsTypeCoercer;
  private final TypeCoercer<Path, Path> pathTypeCoercer;

  public SourcePathTypeCoercer(
      TypeCoercer<UnconfiguredBuildTargetWithOutputs, BuildTargetWithOutputs>
          buildTargetWithOutputsTypeCoercer,
      TypeCoercer<Path, Path> pathTypeCoercer) {
    this.buildTargetWithOutputsTypeCoercer = buildTargetWithOutputsTypeCoercer;
    this.pathTypeCoercer = pathTypeCoercer;
  }

  @Override
  public TypeToken<SourcePath> getOutputType() {
    return TypeToken.of(SourcePath.class);
  }

  @Override
  public TypeToken<String> getUnconfiguredType() {
    return TypeToken.of(String.class);
  }

  @Override
  public String coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelativePath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (!(object instanceof String)) {
      throw CoerceFailedException.simple(object, getOutputType());
    }

    return (String) object;
  }

  @Override
  public SourcePath coerce(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelativePath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfiguration hostConfiguration,
      String object)
      throws CoerceFailedException {
    if ((object.contains("//") || object.startsWith(":"))) {
      BuildTargetWithOutputs buildTargetWithOutputs =
          buildTargetWithOutputsTypeCoercer.coerceBoth(
              cellRoots,
              filesystem,
              pathRelativeToProjectRoot,
              targetConfiguration,
              hostConfiguration,
              object);
      return DefaultBuildTargetSourcePath.of(buildTargetWithOutputs);
    } else {
      Path path =
          pathTypeCoercer.coerceBoth(
              cellRoots,
              filesystem,
              pathRelativeToProjectRoot,
              targetConfiguration,
              hostConfiguration,
              object);
      if (path.isAbsolute()) {
        throw CoerceFailedException.simple(
            object, getOutputType(), "SourcePath cannot contain an absolute path");
      }
      return PathSourcePath.of(filesystem, path);
    }
  }
}
