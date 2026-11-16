# Instructions for agents

This is a fork of [RomRaider](https://github.com/RomRaider/RomRaider). `master` follows upstream untouched; work
goes on the branch `unified`.

## Build and test

Java 25 and Apache Ant 1.10. Neither needs to be on PATH:

```
set JAVA_HOME=C:\Tools\jdk-25.0.5+8
C:\Tools\apache-ant-1.10.15\bin\ant build-windows     (build\windows\lib\RomRaider.jar)
C:\Tools\apache-ant-1.10.15\bin\ant unittest          (9 tests; XDFConversionLayerTest is left out, it opens the editor)
```

To run a build from this folder:

```
java --enable-native-access=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED ^
     --add-exports java.desktop/sun.awt.windows=ALL-UNNAMED -Djava.library.path=lib/windows/64 ^
     -cp "build/windows/lib/RomRaider.jar;lib/common/*" com.romraider.ECUExec [image.bin | -logger]
```

## Rules

* Stay mergeable with upstream: new code goes into new packages and new files; edits to existing files are small;
  no reformatting, no renaming, no moving. Keep each file's line endings (most are CRLF).
* A change upstream could use (the build, a library update, a fix) is a commit of its own.
* Libraries are jars under `lib/`; there is no Maven or Gradle.
* The repository is public. Nothing that is not ours to publish goes in: no ECU images, no vendor code or
  firmware, no service-manual text.
* Don't push without the owner's word.
