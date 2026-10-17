# Java setup completed

Installed **Eclipse Temurin JDK 17.0.20.1** for the `mustafa` account. This is a full development kit, including the Java runtime, compiler, JAR tools, JShell, documentation tools and debugging tools.

The installation is permanent and independent of the project's temporary files:

```text
Installation: /home/mustafa/.local/share/java/temurin-17.0.20.1
JAVA_HOME:    /home/mustafa/.local/share/java/current
Commands:     /home/mustafa/.local/bin
```

Shell configuration loads Java automatically in new terminals. Desktop applications receive `JAVA_HOME` through the account's environment configuration. Existing terminal windows may need to be reopened to pick up the new variable. The Java commands are also available through the existing local command directory.

## Open the project

Double-click **Service Placement Lab** on your desktop, or find it in the application menu. You can also run:

```sh
server-placement-lab
```

## Verify or compile Java

```sh
java -version
javac -version
jshell --version
```

To rebuild the optimization project and check it:

```sh
cd /home/mustafa/Documents/Codex/2026-10-17/the-dr-godar-ask-us-we/outputs/ServerPlacementLab
./build.sh
./run.sh --self-test
```

Both Java and the compiler report version `17.0.20.1`. The project builds with the installed JDK and passes its automated model, search, playback and GUI checks.

For an IDE's JDK setting, choose `/home/mustafa/.local/share/java/current`. No administrator password is needed for this account installation.
