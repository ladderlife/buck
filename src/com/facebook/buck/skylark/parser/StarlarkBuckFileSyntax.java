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

package com.facebook.buck.skylark.parser;

import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.syntax.Argument;
import com.google.devtools.build.lib.syntax.CallExpression;
import com.google.devtools.build.lib.syntax.DefStatement;
import com.google.devtools.build.lib.syntax.ForStatement;
import com.google.devtools.build.lib.syntax.IfStatement;
import com.google.devtools.build.lib.syntax.Node;
import com.google.devtools.build.lib.syntax.NodeVisitor;
import com.google.devtools.build.lib.syntax.StarlarkFile;

/** Validate {@code BUCK} file syntax, which is more restrictive than {@code .bzl}. */
class StarlarkBuckFileSyntax {

  public static boolean checkBuildSyntax(StarlarkFile file, final EventHandler eventHandler) {
    final boolean[] success = {true};
    NodeVisitor checker =
        new NodeVisitor() {
          private void error(Node node, String message) {
            eventHandler.handle(Event.error(node.getLocation(), message));
            success[0] = false;
          }

          // We prune the traversal if we encounter def/if/for,
          // as we have already reported the root error and there's
          // no point reporting more.

          @Override
          public void visit(DefStatement node) {
            error(
                node,
                "function definitions are not allowed in BUILD files. You may move the function to "
                    + "a .bzl file and load it.");
          }

          @Override
          public void visit(ForStatement node) {
            error(
                node,
                "for statements are not allowed in BUILD files. You may inline the loop, move it "
                    + "to a function definition (in a .bzl file), or as a last resort use a list "
                    + "comprehension.");
          }

          @Override
          public void visit(IfStatement node) {
            error(
                node,
                "if statements are not allowed in BUILD files. You may move conditional logic to a "
                    + "function definition (in a .bzl file), or for simple cases use an if "
                    + "expression.");
          }

          @Override
          public void visit(CallExpression node) {
            for (Argument arg : node.getArguments()) {
              if (arg instanceof Argument.StarStar) {
                error(
                    node,
                    "**kwargs arguments are not allowed in BUILD files. Pass the arguments in "
                        + "explicitly.");
              } else if (arg instanceof Argument.Star) {
                error(
                    node,
                    "*args arguments are not allowed in BUILD files. Pass the arguments in "
                        + "explicitly.");
              }
            }

            // Continue traversal so as not to miss nested calls
            // like cc_binary(..., f(**kwargs), ...).
            super.visit(node);
          }
        };
    checker.visit(file);
    return success[0];
  }
}
