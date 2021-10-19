{ lib
, releaseTools
, fetchFromGitHub
, jdk8_headless
, buckJDK ? jdk8_headless
, python3
, watchman
, makeWrapper
, ensureNewerSourcesForZipFilesHook
, bash
}:

let
  python = python3.withPackages (pythonPackages: with pythonPackages; [
    pywatchman
  ]);
in
releaseTools.antBuild rec {
  name = "buck";

  src = ./.;

  jre = buckJDK;

  buildInputs = [
    python
    ensureNewerSourcesForZipFilesHook
    makeWrapper
  ];

  postBuild = ''
    pex=$(bin/buck build buck --show-output | awk '{print $2}')
  '';

  installPhase = ''
    install -D -m644 $pex $out/bin/.buck-unwrapped
    echo '#!${bash}/bin/bash' > $out/bin/buck
    echo 'export PATH="${lib.makeBinPath [ watchman ]}:$PATH"' >> $out/bin/buck
    echo 'export JAVA_HOME="${jre}"' >> $out/bin/buck
    echo 'exec ${python}/bin/python3 '"$out"'/bin/.buck-unwrapped "$@"' >> $out/bin/buck
    chmod 755 $out/bin/buck
  '';

  dontPatchShebangs = true;

  meta = with lib; {
    homepage = "https://buck.build/";
    description = "A high-performance build tool";
    license = licenses.asl20;
    platforms = platforms.all;
  };
}
