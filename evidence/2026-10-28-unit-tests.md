# Concordance unit-test run — 2026-10-28

Environment: Windows 11, Gradle wrapper, Android unit-test task.

Before the fix, the suite reported 388 tests with one failure:

```text
NavigationStateTest > primaryNavigationIsCappedAtFive FAILED
    expected:<2> but was:<3>
```

The assertion was stale, not the app: `NavigationDestination.secondary` has
three entries. The test now expects three and additionally asserts that the
primary and secondary lists together cover every enum value.

Command:

```bash
cd "eco/Concordance-main"
sh gradlew :app:testDebugUnitTest --console=plain
```

Result:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 41s
33 actionable tasks: 1 executed, 32 up-to-date
```

JUnit XML totals:

```text
tests=388 failures=0 errors=0
```

Limitation: JVM unit tests only. No device install, no App Link tapped on a
device, and release signing remains a placeholder.
