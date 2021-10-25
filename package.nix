{ lib
, releaseTools
, fetchFromGitHub
, jdk8_headless
, buckJDK ? jdk8_headless
, adoptopenjdk-hotspot-bin-15
, runtimeJDK ? adoptopenjdk-hotspot-bin-15
, python3
, watchman
, makeWrapper
, ensureNewerSourcesForZipFilesHook
, bash
}:
with lib;
let
  python = python3.withPackages (pythonPackages: with pythonPackages; [
    pywatchman
  ]);
in
releaseTools.antBuild {
  name = "buck";

  src = ./.;

  jre = buckJDK;

  buildInputs = [
    python
    ensureNewerSourcesForZipFilesHook
    makeWrapper
  ];

  propagatedBuildInputs = [
    buckJDK
    runtimeJDK
  ];

  postBuild = ''
    pex=$(bin/buck build buck --show-output | awk '{print $2}')
  '';

  installPhase = ''
    install -D -m644 $pex $out/bin/.buck-unwrapped
    echo '#!${bash}/bin/bash' > $out/bin/buck
    echo 'export PATH="${makeBinPath [ watchman ]}:$PATH"' >> $out/bin/buck
    echo 'export JAVA_HOME="${runtimeJDK}"' >> $out/bin/buck
    echo 'exec ${python}/bin/python3 '"$out"'/bin/.buck-unwrapped "$@"' >> $out/bin/buck
    chmod 755 $out/bin/buck
  '';

  dontPatchShebangs = true;

  meta = {
    homepage = "https://buck.build/";
    description = "A high-performance build tool";
    license = licenses.asl20;
    platforms = platforms.all;
  };
}
