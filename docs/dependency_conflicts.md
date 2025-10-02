# Handling Dependency Conflicts

We have a `dependencyManagement` section in [the top-level `pom.xml`](/pom.xml); some of the dependencies within are used to set versions of certain transitive dependencies. For example, let's say we have dependencies `dep:A`, `dep:B`, and `dep:C`.

- `dep:A` depends on version 1.1 of `dep:C`
- `dep:B` depends on version 1.5 of `dep:C`

Coatjava depends on `dep:A` and `dep:B`, and so the `maven-enforcer-plugin` will complain about a "dependency convergence error" of `dep:C`, since the version numbers are different (1.1 vs. 1.5).

To resolve this conflict, a typical strategy is to choose the _later_ version of the two, in this case, `dep:C` version 1.5. We can do this by explicitly defining dependency `dep:C` in a `dependencyManagement` section as such:

```xml
<dependency>
  <groupId>dep</groupId>
  <artifactId>C</artifactId>
  <version>1.5</version>
</dependency>
```

Coatjava will then use 1.5 as needed.

Dependabot, however, will routinely try to update the `dep:C` version, to the _latest_ available version of `dep:C`. When this happens, please do the following:

1. Comment out the `dependency` specification
2. Rebuild coatjava, which will cause `maven-enforcer-plugin` to complain; that will tell you the versions
   - alternatively, run `mvn enforcer:enforce -Drules=dependencyConvergence`, but that may not exclude dependencies that we _don't_ want to enforce convergence on (_e.g._, `com.google.protobuf:protobuf-java`)
3. Update the version number, if needed, by choosing the _later_ of the two conflicting versions
4. Unless `dep:A` or `dep:B` are keeping _their_ version of `dep:C` dependency up-to-date, you will likely find that Dependabot is suggesting a version that is _too_ new; in that case, just close Dependabot's PR and await updates of `dep:A` or `dep:B`
