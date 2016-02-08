/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.apple;

import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.js.ReactNativeFlavors;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Either;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.model.HasTests;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.Hint;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.MetadataProvidingDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;
import java.util.Map;

public class AppleBundleDescription implements Description<AppleBundleDescription.Arg>,
    Flavored,
    ImplicitDepsInferringDescription<AppleBundleDescription.Arg>,
    MetadataProvidingDescription<AppleBundleDescription.Arg> {
  public static final BuildRuleType TYPE = BuildRuleType.of("apple_bundle");
  public static final ImmutableSet<Flavor> SUPPORTED_LIBRARY_FLAVORS = ImmutableSet.of(
      CxxDescriptionEnhancer.STATIC_FLAVOR,
      CxxDescriptionEnhancer.SHARED_FLAVOR);

  private static final Flavor WATCH = ImmutableFlavor.of("watch");

  private final AppleBinaryDescription appleBinaryDescription;
  private final AppleLibraryDescription appleLibraryDescription;
  private final FlavorDomain<CxxPlatform> cxxPlatformFlavorDomain;
  private final ImmutableMap<Flavor, AppleCxxPlatform> platformFlavorsToAppleCxxPlatforms;
  private final CxxPlatform defaultCxxPlatform;
  private final CodeSignIdentityStore codeSignIdentityStore;
  private final ProvisioningProfileStore provisioningProfileStore;
  private final AppleDebugFormat defaultDebugInfoFormat;

  public AppleBundleDescription(
      AppleBinaryDescription appleBinaryDescription,
      AppleLibraryDescription appleLibraryDescription,
      FlavorDomain<CxxPlatform> cxxPlatformFlavorDomain,
      Map<Flavor, AppleCxxPlatform> platformFlavorsToAppleCxxPlatforms,
      CxxPlatform defaultCxxPlatform,
      CodeSignIdentityStore codeSignIdentityStore,
      ProvisioningProfileStore provisioningProfileStore,
      AppleDebugFormat defaultDebugInfoFormat) {
    this.appleBinaryDescription = appleBinaryDescription;
    this.appleLibraryDescription = appleLibraryDescription;
    this.cxxPlatformFlavorDomain = cxxPlatformFlavorDomain;
    this.platformFlavorsToAppleCxxPlatforms =
        ImmutableMap.copyOf(platformFlavorsToAppleCxxPlatforms);
    this.defaultCxxPlatform = defaultCxxPlatform;
    this.codeSignIdentityStore = codeSignIdentityStore;
    this.provisioningProfileStore = provisioningProfileStore;
    this.defaultDebugInfoFormat = defaultDebugInfoFormat;
  }

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public boolean hasFlavors(final ImmutableSet<Flavor> flavors) {
    if (appleLibraryDescription.hasFlavors(flavors)) {
      return true;
    }
    ImmutableSet.Builder<Flavor> flavorBuilder = ImmutableSet.builder();
    for (Flavor flavor : flavors) {
      if (flavor.equals(ReactNativeFlavors.DO_NOT_BUNDLE) ||
          flavor.equals(AppleDebugFormat.DWARF_AND_DSYM.getFlavor()) ||
          flavor.equals(AppleDebugFormat.NONE.getFlavor())) {
        continue;
      }
      flavorBuilder.add(flavor);
    }
    return appleBinaryDescription.hasFlavors(flavorBuilder.build());
  }


  @Override
  public <A extends Arg> BuildRuleWithAppleBundle createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) throws NoSuchBuildTargetException {
    AppleDebugFormat flavoredDebugInfoFormat = AppleDebugFormat.FLAVOR_DOMAIN
        .getValue(params.getBuildTarget())
        .or(defaultDebugInfoFormat);
    Flavor debugFormatFlavor = flavoredDebugInfoFormat.getFlavor();
    if (!params.getBuildTarget().getFlavors().contains(debugFormatFlavor)) {
      return (BuildRuleWithAppleBundle) resolver.requireRule(
          BuildTarget.builder(params.getBuildTarget())
              .addFlavors(debugFormatFlavor)
              .build());
    }
    if (!AppleDescriptions.INCLUDE_FRAMEWORKS.getValue(params.getBuildTarget()).isPresent()) {
      return (BuildRuleWithAppleBundle) resolver.requireRule(
          BuildTarget.builder(params.getBuildTarget())
              .addFlavors(AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR)
              .build());
    }
    AppleBundle appleBundle = AppleDescriptions.createAppleBundle(
        cxxPlatformFlavorDomain,
        defaultCxxPlatform,
        platformFlavorsToAppleCxxPlatforms,
        targetGraph,
        params,
        resolver,
        codeSignIdentityStore,
        provisioningProfileStore,
        args.binary,
        args.extension,
        args.productName,
        args.infoPlist,
        args.infoPlistSubstitutions,
        args.deps.get(),
        args.getTests());
    if (flavoredDebugInfoFormat.getFlavor() == AppleDebugFormat.NONE.getFlavor() ||
        !appleBundle.getBinary().isPresent()) {
      return appleBundle;
    }
    AppleDsym appleDsym = AppleDescriptions.createAppleDsym(
        cxxPlatformFlavorDomain,
        defaultCxxPlatform,
        platformFlavorsToAppleCxxPlatforms,
        params,
        resolver,
        appleBundle);
    return AppleDescriptions.createAppleBundleWithDsym(appleBundle, appleDsym, params, resolver);
  }

  /**
   * Propagate the bundle's platform flavors to its dependents.
   */
  @Override
  public ImmutableSet<BuildTarget> findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      Function<Optional<String>, Path> cellRoots,
      AppleBundleDescription.Arg constructorArg) {
    if (!constructorArg.deps.isPresent()) {
      return ImmutableSet.of();
    }

    if (!cxxPlatformFlavorDomain.containsAnyOf(buildTarget.getFlavors())) {
      buildTarget = BuildTarget.builder(buildTarget).addAllFlavors(
          ImmutableSet.of(defaultCxxPlatform.getFlavor())).build();
    }

    Optional<FatBinaryInfo> fatBinaryInfo =
        FatBinaryInfos.create(platformFlavorsToAppleCxxPlatforms, buildTarget);
    CxxPlatform cxxPlatform;
    if (fatBinaryInfo.isPresent()) {
      AppleCxxPlatform appleCxxPlatform = fatBinaryInfo.get().getRepresentativePlatform();
      cxxPlatform = appleCxxPlatform.getCxxPlatform();
    } else {
      cxxPlatform = ApplePlatforms.getCxxPlatformForBuildTarget(
          cxxPlatformFlavorDomain,
          defaultCxxPlatform,
          buildTarget);
    }

    String platformName = cxxPlatform.getFlavor().getName();
    final Flavor actualWatchFlavor;
    if (ApplePlatform.isSimulator(platformName)) {
      actualWatchFlavor = ImmutableFlavor.builder().name("watchsimulator-i386").build();
    } else if (platformName.startsWith(ApplePlatform.IPHONEOS.getName()) ||
        platformName.startsWith(ApplePlatform.WATCHOS.getName())) {
      actualWatchFlavor = ImmutableFlavor.builder().name("watchos-armv7k").build();
    } else {
      actualWatchFlavor = ImmutableFlavor.builder().name(platformName).build();
    }

    FluentIterable<BuildTarget> depsExcludingBinary = FluentIterable.from(constructorArg.deps.get())
        .filter(Predicates.not(Predicates.equalTo(constructorArg.binary)));

    FluentIterable<BuildTarget> targetsWithPlatformFlavors = depsExcludingBinary.filter(
        BuildTargets.containsFlavors(cxxPlatformFlavorDomain));

    FluentIterable<BuildTarget> targetsWithoutPlatformFlavors = depsExcludingBinary.filter(
        Predicates.not(BuildTargets.containsFlavors(cxxPlatformFlavorDomain)));

    FluentIterable<BuildTarget> watchTargets = targetsWithoutPlatformFlavors
        .filter(BuildTargets.containsFlavor(WATCH))
        .transform(
            new Function<BuildTarget, BuildTarget>() {
              @Override
              public BuildTarget apply(BuildTarget input) {
                return BuildTarget.builder(
                    input.withoutFlavors(ImmutableSet.of(WATCH)))
                    .addFlavors(actualWatchFlavor)
                    .build();
              }
            });

    targetsWithoutPlatformFlavors = targetsWithoutPlatformFlavors
        .filter(Predicates.not(BuildTargets.containsFlavor(WATCH)));

    return ImmutableSet.<BuildTarget>builder()
        .addAll(targetsWithPlatformFlavors)
        .addAll(watchTargets)
        .addAll(
            BuildTargets.propagateFlavorDomains(
                buildTarget,
                ImmutableSet.<FlavorDomain<?>>of(cxxPlatformFlavorDomain),
                targetsWithoutPlatformFlavors))
        .build();
  }

  @Override
  public <A extends Arg, U> Optional<U> createMetadata(
      BuildTarget buildTarget,
      BuildRuleResolver resolver,
      A args,
      Class<U> metadataClass) throws NoSuchBuildTargetException {
    return resolver.requireMetadata(args.binary, metadataClass);
  }

  @SuppressFieldNotInitialized
  public static class Arg implements HasAppleBundleFields, HasTests {
    public Either<AppleBundleExtension, String> extension;
    public BuildTarget binary;
    public SourcePath infoPlist;
    public Optional<ImmutableMap<String, String>> infoPlistSubstitutions;
    @Hint(isDep = false) public Optional<ImmutableSortedSet<BuildTarget>> deps;
    @Hint(isDep = false) public Optional<ImmutableSortedSet<BuildTarget>> tests;
    public Optional<String> xcodeProductType;
    public Optional<String> productName;

    @Override
    public Either<AppleBundleExtension, String> getExtension() {
      return extension;
    }

    @Override
    public SourcePath getInfoPlist() {
      return infoPlist;
    }

    @Override
    public ImmutableSortedSet<BuildTarget> getTests() {
      return tests.get();
    }

    @Override
    public Optional<String> getXcodeProductType() {
      return xcodeProductType;
    }
  }
}
