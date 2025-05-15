# Developer Notes

## Deploying a New Version

Deploying a new version requires a new version number, named `$VERSION` in this
document.

**Legend:**
- magenta rectangle: manual step
- green hexagon: automated

```mermaid
flowchart TB
    classDef manual    fill:#f8f,color:black
    classDef automatic fill:#8f8,color:black

    subgraph deploy-coatjava.sh
        deployScript[deploy-coatjava.sh $VERSION]:::manual
        bump1{{bump version to $VERSION}}:::automatic
        deployMaven{{deploy to Maven repo}}:::automatic
        deployTarball{{deploy tarball to clasweb}}:::automatic
        bump2{{bump version to $VERSION-SNAPSHOT}}:::automatic
        gitCommit{{new git branch and commit}}:::automatic
    end
    gitPush[git push]:::manual
    pullRequest[pull request and merge]:::manual
    gitTag[git tag and release]:::manual
    subgraph "Continuous Integration (CI)"
        bump3{{bump version to $VERSION}}:::automatic
        deployGit{{deploy git release tarball}}:::automatic
    end

    deployScript ==> bump1
    bump1 ==> deployMaven
    bump1 ==> deployTarball
    bump1 ==> bump2 ==> gitCommit
    gitCommit ==> gitPush ==> pullRequest ==> gitTag
    gitTag ==> bump3 ==> deployGit
```
