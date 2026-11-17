# Instructions for agents

This is a fork of [RomRaider](https://github.com/RomRaider/RomRaider). `master` follows upstream untouched; work
goes on the branch `unified`.

## Build and test

Java 25 and Apache Ant 1.10. Neither needs to be on PATH:

```
set JAVA_HOME=C:\Tools\jdk-25.0.5+8
C:\Tools\apache-ant-1.10.15\bin\ant build-windows     (build\windows\lib\RomRaider.jar)
C:\Tools\apache-ant-1.10.15\bin\ant unittest          (93 tests; XDFConversionLayerTest is left out, it opens the editor)
```

JavaFX is optional: it draws the diagrams in the table documentation panel ([docs/table_docs.md](docs/table_docs.md)).
To build that view in, add `-Djavafx.sdk=C:\Tools\javafx-sdk-25.0.5` to ant (or set `JAVAFX_SDK`); without it the
build leaves `com.romraider.docs.fx` out. Both builds and the tests must pass with and without it. JavaFX itself is
never committed.

To run a build from this folder:

```
java --enable-native-access=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED ^
     --add-exports java.desktop/sun.awt.windows=ALL-UNNAMED -Djava.library.path=lib/windows/64 ^
     -cp "build/windows/lib/RomRaider.jar;lib/common/*" com.romraider.ECUExec [image.bin | -logger]
```

The editor and the logger open as two views of one main window (`com.romraider.shell`; Ctrl+1 and Ctrl+2 switch).
`-separate` gives upstream's two windows; `-logger.fullscreen` and `-logger.touch` also start as upstream does.
The third view, Flash (Ctrl+3, `com.romraider.flash`), reads and writes an ECU ([docs/flashing.md](docs/flashing.md)).
It is tested only against the simulated ECU in `src/test/java/com/romraider/flash/sim`; never run a write against a
real ECU from an agent session, and never commit a kernel or an ECU image.

With JavaFX, add `--module-path C:\Tools\javafx-sdk-25.0.5\lib --add-modules javafx.swing,javafx.web` and
`--enable-native-access=javafx.graphics,javafx.web,javafx.media,ALL-UNNAMED`. RomRaider reads a `settings.xml` in the
working folder before the user's own: for a test run, start it from a scratch folder holding a `settings.xml` and a
copy of `i18n/`. Add `-Duser.home=<scratch>` so the log (`user.home\.RomRaider\rr_system.log`, with
`-Dlog4j.configuration=file:/<fork>/lib/log4j.properties`) and anything else stays there too.

The logger falls back to a serial port after a failed J2534 init and picks the first port present when the saved one
is missing, then writes SSM bytes to it. On a computer where a serial port drives something else (a relay board),
set `<serial ... fallback="false"/>` in the logger settings and name the J2534 DLL in
`<protocol ... library="<path>"/>`: the logger then stays on that DLL and never opens a serial port.

## Packaging

`ant -f package.xml` makes `build\package\image\RomRaider` (`RomRaider.exe` with its own Java runtime, so users need
no Java) and a ZIP of it in `build\dist`. It uses jlink and jpackage of the JDK; nothing else is needed. With
`-Djavafx.sdk=C:\Tools\javafx-sdk-25.0.5 -Djavafx.jmods=C:\Tools\javafx-jmods-25.0.5` (or `JAVAFX_SDK` and
`JAVAFX_JMODS`) JavaFX is built in. The packaged program starts through `com.romraider.shell.PackagedStart`, which
starts it in its own folder. CI builds the package with JavaFX on every push and keeps it as the artifact
`RomRaider-windows-x64`. Upstream's launch4j and IzPack targets in `build.xml` are left as they are and not used.

## Rules

* Stay mergeable with upstream: new code goes into new packages and new files; edits to existing files are small;
  no reformatting, no renaming, no moving. Keep each file's line endings (most are CRLF).
* A change upstream could use (the build, a library update, a fix) is a commit of its own.
* Libraries are jars under `lib/`; there is no Maven or Gradle.
* The repository is public. Nothing that is not ours to publish goes in: no ECU images, no vendor code or
  firmware, no service-manual text.
* Before a push, search the branch (files and commit messages) for anything of a private project: names, IDs,
  local paths. Tests use only what the fork has or small made-up fixtures.
