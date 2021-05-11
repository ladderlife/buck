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

package com.facebook.buck.android.toolchain.impl;

import com.facebook.buck.android.AndroidBuckConfig;
import com.facebook.buck.android.toolchain.common.BaseAndroidToolchainResolver;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.util.types.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;

/** Utility class used for resolving the location of Android specific directories. */
public class AndroidSdkDirectoryResolver extends BaseAndroidToolchainResolver {
  @VisibleForTesting
  static final String SDK_NOT_FOUND_MESSAGE =
      "Android SDK could not be found. Make sure to set "
          + "one of these environment variables: ANDROID_SDK, ANDROID_HOME, ANDROID_SDK_ROOT, "
          + "or android.sdk_path in your .buckconfig";

  private Optional<String> sdkErrorMessage;
  private final Optional<Path> sdk;

  public AndroidSdkDirectoryResolver(
      FileSystem fileSystem, ImmutableMap<String, String> environment, AndroidBuckConfig config) {
    super(fileSystem, environment);

    this.sdkErrorMessage = Optional.empty();

    this.sdk = findSdk(config);
  }

  public Path getSdkOrThrow() {
    if (!sdk.isPresent() && sdkErrorMessage.isPresent()) {
      throw new HumanReadableException(sdkErrorMessage.get());
    }
    return sdk.get();
  }

  private Optional<Path> findSdk(AndroidBuckConfig config) {
    Optional<Path> sdkPath;
    try {
      ImmutableList<Pair<String, Optional<String>>> paths = getSdkPathsFromConfig(config);
      sdkPath = findFirstDirectory(paths);
    } catch (RuntimeException e) {
      sdkErrorMessage = Optional.of(e.getMessage());
      return Optional.empty();
    }

    if (!sdkPath.isPresent()) {
      sdkErrorMessage = Optional.of(SDK_NOT_FOUND_MESSAGE);
    }
    return sdkPath;
  }

  private ImmutableList<Pair<String, Optional<String>>> getSdkPathsFromConfig(
      AndroidBuckConfig config) {
    ImmutableList.Builder<Pair<String, Optional<String>>> paths = ImmutableList.builder();
    for (String searchOrderEntry : config.getSdkPathSearchOrder()) {
      Optional<String> sdkPathConfigOption =
          config.getSdkPathConfigOptionFromSearchOrderEntry(searchOrderEntry);
      if (sdkPathConfigOption.isPresent()) {
        paths.add(new Pair<>(sdkPathConfigOption.get(), config.getSdkPath()));
      } else {
        paths.add(getEnvironmentVariable(searchOrderEntry));
      }
    }
    return paths.build();
  }

  @Override
  public String toString() {
    return String.format(
        "%s AndroidSdkDir=%s", super.toString(), sdk.isPresent() ? sdk.get() : "SDK not available");
  }
}
