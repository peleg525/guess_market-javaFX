# Guess Market - Exercise 2 (JavaFX Application)

By Peleg Wurzel and Ben Lutenberg

![Windows smoke test](https://github.com/peleg525/guess_market-javaFX/actions/workflows/windows-smoke-test.yml/badge.svg)

A JavaFX implementation of *Guess Market*, evolving the Exercise 1 console engine: real user
accounts, both trading methods (LMSR and a central limit order book), and a graphical UI.

> Our actual submission readme - our details, the assumptions we made, and a walkthrough of the
> classes - is `README.docx`, since that's the format the assignment requires. This file here is
> just the regular repo readme.

## Modules

- **gm-engine** - the passive engine. Loads/validates the XML (events + users), runs both trading
  methods, exposes everything through `GmEngine` using immutable DTOs. No JavaFX dependency.
- **gm-ui** - the JavaFX UI. Talks to the engine only through `GmEngine` and its DTOs.

## Build

Requires JDK 25.

```
mvn clean package
```

Produces `gm-engine/target/gm-engine.jar` and `gm-ui/target/gm-ui.jar` (plus `gm-ui/target/lib/`
with the JAXB runtime and Windows-native JavaFX jars).

## Run

```
java -jar gm-ui/target/gm-ui.jar
```

or, from an assembled distribution folder (`gm-ui.jar` + `lib/` sitting next to each other, as in
the submission zip):

```
run.bat
```

For local development/testing on a Mac instead of Windows: `mvn -Pmac-dev package`, or
`mvn -Pmac-dev -pl gm-ui org.openjfx:javafx-maven-plugin:0.0.8:run -Djavafx.mainClass=gm.ui.Main`.

Sample XML event files for manual testing are under `testfiles/`.

## Tests

`gm-engine` has JUnit tests covering file loading/validation, the LMSR appendix example, and a
step-by-step replay of the lecturer's order-book reference simulation:

```
mvn -pl gm-engine -am test
```

## CI

`.github/workflows/windows-smoke-test.yml` runs the engine's unit tests, builds the project, and
launches the packaged jar on a real `windows-latest` GitHub Actions runner on every push, as an
extra check on top of testing it manually on Windows ourselves.
