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

import com.facebook.buck.core.description.BaseDescription;
import com.facebook.buck.core.description.arg.ConstructorArg;
import com.facebook.buck.core.description.impl.DescriptionCache;
import com.facebook.buck.core.path.ForwardRelativePath;
import com.facebook.buck.rules.coercer.ParamInfo;
import com.facebook.buck.rules.coercer.ParamsInfo;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.rules.param.CommonParamNames;
import com.facebook.buck.rules.param.ParamName;
import com.facebook.buck.skylark.parser.context.ParseContext;
import com.facebook.buck.skylark.parser.context.RecordedRule;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.Dict;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.FunctionSignature;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.devtools.build.lib.syntax.Tuple;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Responsible for creating instances of Skylark functions based on Buck's {@link BaseDescription}s.
 *
 * <p>For example for a {@link com.facebook.buck.jvm.java.JavaLibraryDescription} instance, a
 * Skylark function using snake case of its name prefix will be created - {@code java_library}.
 *
 * <p>Callers can setup created functions in the {@link
 * com.google.devtools.build.lib.syntax.StarlarkThread}.
 */
public class RuleFunctionFactory {

  private static final String BUCK_RULE_DOC_URL_PREFIX = "https://buck.build/rule/";

  private final TypeCoercerFactory typeCoercerFactory;

  public RuleFunctionFactory(TypeCoercerFactory typeCoercerFactory) {
    this.typeCoercerFactory = typeCoercerFactory;
  }

  /**
   * Create a Skylark function definition for the {@code ruleClass} rule.
   *
   * <p>This creates functions like @{code java_library}. All they do is capture passed attribute
   * values in a map and records them in a {@link ParseContext}. They can be queried using {@link
   * ParseContext#getRecordedRules()}.
   *
   * @param ruleClass The name of the rule to to define.
   * @return Skylark function to handle the Buck rule.
   */
  BaseFunction create(BaseDescription<?> ruleClass) {
    String name = DescriptionCache.getRuleType(ruleClass).getName();
    return new BaseFunction() {

      @Override
      public FunctionSignature getSignature() {
        return FunctionSignature.KWARGS;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Object call(
          StarlarkThread thread, Location loc, Tuple<Object> args, Dict<String, Object> kwargs)
          throws EvalException, InterruptedException {

        // sanity check; Starlark has already validated signature
        Preconditions.checkArgument(args.isEmpty());

        ParseContext parseContext = ParseContext.getParseContext(thread, loc, name);
        String basePath =
            parseContext
                .getPackageContext()
                .getPackageIdentifier()
                .getPackageFragment()
                .getPathString();
        RecordedRule recordedRule = populateAttributes(ruleClass, getName(), basePath, kwargs, loc);
        parseContext.recordRule(recordedRule, loc);
        return Starlark.NONE;
      }
    };
  }

  /**
   * Validates attributes passed to the rule and in case any required attribute is not provided,
   * throws an {@link IllegalArgumentException}.
   *
   * @param kwargs The keyword arguments passed to the rule.
   * @param allParamInfo The mapping from build rule attributes to their information.
   * @param name The build rule name. (e.g. {@code java_library}).
   * @param loc Location of the invocation
   */
  private void throwOnMissingRequiredAttribute(
      Map<String, Object> kwargs,
      ImmutableMap<ParamName, ParamInfo<?>> allParamInfo,
      String name,
      Location loc)
      throws EvalException {
    ImmutableList<ParamInfo<?>> missingAttributes =
        allParamInfo.values().stream()
            .filter(
                param -> !param.isOptional() && !kwargs.containsKey(param.getName().getSnakeCase()))
            .collect(ImmutableList.toImmutableList());
    if (!missingAttributes.isEmpty()) {
      throw new EvalException(
          loc,
          name
              + " requires "
              + missingAttributes.stream()
                  .map(p -> p.getName().getSnakeCase())
                  .sorted(ParamInfo.NAME_COMPARATOR)
                  .collect(Collectors.joining(" and "))
              + " but they are not provided.\n"
              + "Need help? See "
              + BUCK_RULE_DOC_URL_PREFIX
              + name);
    }
  }

  /**
   * Populates provided {@code builder} with values from {@code kwargs} assuming {@code ruleClass}
   * as the target {@link BaseDescription} class.
   *
   * @param kwargs The keyword arguments and their values passed to rule function in build file.
   */
  private RecordedRule populateAttributes(
      BaseDescription<?> ruleClass,
      String name,
      String basePath,
      Map<String, Object> kwargs,
      Location loc)
      throws EvalException {

    TwoArraysImmutableHashMap.Builder<ParamName, Object> builder =
        TwoArraysImmutableHashMap.builder();

    ParamsInfo allParamInfo =
        typeCoercerFactory
            .getNativeConstructorArgDescriptor(
                (Class<? extends ConstructorArg>) ruleClass.getConstructorArgType())
            .getParamsInfo();

    ImmutableList<String> visibility = ImmutableList.of();
    ImmutableList<String> withinView = ImmutableList.of();

    for (Map.Entry<String, Object> kwargEntry : kwargs.entrySet()) {
      ParamName paramName = ParamName.bySnakeCase(kwargEntry.getKey());
      if (kwargEntry.getKey().equals(CommonParamNames.VISIBILITY.getSnakeCase())) {
        visibility = toListOfString(kwargEntry.getKey(), kwargEntry.getValue());
        continue;
      }
      if (kwargEntry.getKey().equals(CommonParamNames.WITHIN_VIEW.getSnakeCase())) {
        withinView = toListOfString(kwargEntry.getKey(), kwargEntry.getValue());
        continue;
      }
      if (!allParamInfo.getParamInfosByName().containsKey(paramName)) {
        throw new EvalException(loc, kwargEntry.getKey() + " is not a recognized attribute");
      }
      if (Starlark.NONE.equals(kwargEntry.getValue())) {
        continue;
      }
      builder.put(paramName, kwargEntry.getValue());
    }

    throwOnMissingRequiredAttribute(kwargs, allParamInfo.getParamInfosByName(), name, loc);
    return RecordedRule.of(
        ForwardRelativePath.of(basePath), name, visibility, withinView, builder.build());
  }

  private static ImmutableList<String> toListOfString(String attrName, Object value) {
    if (value == Starlark.NONE) {
      return ImmutableList.of();
    } else if (value instanceof List<?>) {
      List<?> list = (List<?>) value;
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      for (Object o : list) {
        if (!(o instanceof String)) {
          throw new IllegalArgumentException(
              "argument for " + attrName + " must be a list of string, it is " + value);
        }
        builder.add((String) o);
      }
      return builder.build();
    } else {
      throw new IllegalArgumentException(
          "argument for " + attrName + " must be a list of string, it is " + value);
    }
  }
}
