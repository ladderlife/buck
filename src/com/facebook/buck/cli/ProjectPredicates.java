/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.cli;

import com.facebook.buck.apple.XcodeProjectConfigDescription;
import com.facebook.buck.apple.XcodeWorkspaceConfigDescription;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AssociatedTargetNodePredicate;
import com.facebook.buck.rules.ProjectConfigDescription;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.util.immutables.BuckStyleImmutable;

import com.google.common.base.Predicate;

import java.util.Set;

import org.immutables.value.Value;

/**
 * Value type containing predicates to identify nodes associated
 * with an IDE project.
 */
@Value.Immutable
@BuckStyleImmutable
public abstract class ProjectPredicates {
  private static final Logger LOG = Logger.get(ProjectPredicates.class);

  /**
   * {@link Predicate} returning nodes that represent roots of the IDE
   * project.
   */
  @Value.Parameter
  public abstract Predicate<TargetNode<?>> getProjectRootsPredicate();

  /**
   * {@link AssociatedTargetNodePredicate} returning nodes associated
   * with the IDE project.
   */
  @Value.Parameter
  public abstract AssociatedTargetNodePredicate getAssociatedProjectPredicate();

  /**
   * Creates a {@link ProjectPredicates} value type configured for
   * the specified IDE.
   */
  public static ProjectPredicates forIde(
      ProjectCommandOptions.Ide targetIde,
      final Set<BuildTarget> passedInTargetsSet,
      final Set<String> defaultExcludePaths) {
    Predicate<TargetNode<?>> projectRootsPredicate;
    AssociatedTargetNodePredicate associatedProjectPredicate;

    // Prepare the predicates to create the project graph based on the IDE.
    switch (targetIde) {
      case INTELLIJ:
        projectRootsPredicate = new Predicate<TargetNode<?>>() {
          @Override
          public boolean apply(TargetNode<?> input) {
            return input.getType() == ProjectConfigDescription.TYPE;
          }
        };
        associatedProjectPredicate = new AssociatedTargetNodePredicate() {
          @Override
          public boolean apply(TargetNode<?> targetNode, TargetGraph targetGraph) {
            ProjectConfigDescription.Arg projectArg;
            if (targetNode.getType() == ProjectConfigDescription.TYPE) {
              projectArg = (ProjectConfigDescription.Arg) targetNode.getConstructorArg();
            } else {
              return false;
            }

            BuildTarget projectTarget = null;
            if (projectArg.srcTarget.isPresent()) {
              projectTarget = projectArg.srcTarget.get();
            } else if (projectArg.testTarget.isPresent()) {
              projectTarget = projectArg.testTarget.get();
            }
            return (projectTarget != null && targetGraph.get(projectTarget) != null);
          }
        };
        break;
      case XCODE:
        projectRootsPredicate = new Predicate<TargetNode<?>>() {
          @Override
          public boolean apply(TargetNode<?> input) {
            if (XcodeWorkspaceConfigDescription.TYPE != input.getType()) {
              return false;
            }

            String targetName = input.getBuildTarget().getFullyQualifiedName();
            for (String prefix : defaultExcludePaths) {
              if (targetName.startsWith("//" + prefix) &&
                  !passedInTargetsSet.contains(input.getBuildTarget())) {
                LOG.debug(
                    "Ignoring build target %s (exclude_paths contains %s)",
                    input.getBuildTarget(),
                    prefix);
                return false;
              }
            }
            return true;
          }
        };
        associatedProjectPredicate = new AssociatedTargetNodePredicate() {
          @Override
          public boolean apply(
              TargetNode<?> targetNode, TargetGraph targetGraph) {
            XcodeProjectConfigDescription.Arg projectArg;
            if (targetNode.getType() == XcodeProjectConfigDescription.TYPE) {
              projectArg = (XcodeProjectConfigDescription.Arg) targetNode.getConstructorArg();
            } else {
              return false;
            }

            for (BuildTarget includedBuildTarget : projectArg.rules) {
              if (targetGraph.get(includedBuildTarget) != null) {
                return true;
              }
            }

            return false;
          }
        };
        break;
      default:
        // unreachable
        throw new IllegalStateException("'ide' should always be of type 'INTELLIJ' or 'XCODE'");
    }

    return ImmutableProjectPredicates.of(
        projectRootsPredicate,
        associatedProjectPredicate);
  }
}
