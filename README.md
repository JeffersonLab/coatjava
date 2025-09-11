# COATJAVA
[![Build Status](https://github.com/jeffersonlab/coatjava/workflows/Coatjava-CI/badge.svg)](https://github.com/jeffersonlab/coatjava/actions)
[![Validation Status](https://github.com/JeffersonLab/coatjava/actions/workflows/validation.yml/badge.svg)](https://github.com/JeffersonLab/coatjava/actions/workflows/validation.yml)
[![Coverage](https://badgen.net/static/JaCoCo/coverage/purple)](https://jeffersonlab.github.io/coatjava/jacoco)

- [**Documentation Homepage**](https://jeffersonlab.github.io/coatjava)
- [API Documentation (Javadoc)](https://jeffersonlab.github.io/coatjava/javadoc)
- [Developer notes](/docs/dev_notes.md)

----

The original repository for COATJAVA was named "clas12-offline-software" and is [now archived and read-only](https://github.com/JeffersonLab/clas12-offline-software).  On May 17, 2023, this new repository was created by running BFG Repo Cleaner to get rid of old, large data files and things that should never have been in the repository, giving 10x reduction in repository size, clone time, etc, and renamed `coatjava`.  The most critical, GitHub-specific aspects have been transferred to this new repository:

* Open issues
* Branch protection rules
* User access permission

But some things remain only in the original repository:

* Release notes up to 9.0.1 (probably worth transferring)
* Closed issues (probably not worth transferring)
* Wiki (never really utilized and probably worth restarting from scratch)

Due to the cleanup, previously existing forks and local copies of the old repository will not be automatically mergeable.

----

If you just want to use the software without modifying/building it, you can download a pre-built package from the [GitHub releases](https://github.com/JeffersonLab/coatjava/releases) page or the corresponding repo at [JLab](https://clasweb.jlab.org/clas12offline/distribution/coatjava/).  Builds on JLab machines are also available, see the [general software wiki](https://clasweb.jlab.org/wiki/index.php/CLAS12_Software_Center) for setting up your environment to use them.

For anything more, see the "General Developer Documentation" link on that software wiki, which points [here](https://clasweb.jlab.org/wiki/index.php/COATJAVA_Developer_Docs).

The [troubleshooting](https://github.com/JeffersonLab/clas12-offline-software/wiki/Troubleshooting) wiki page may also still be useful but likely outdated.
