# Setup Guide

<br>

## Obtaining the Software

If you just want to use the software without modifying/building it, you can download a pre-built package from the [GitHub releases](https://github.com/JeffersonLab/coatjava/releases) page or the corresponding repo at [JLab](https://clasweb.jlab.org/clas12offline/distribution/coatjava/).  Builds on JLab machines are also available, see the [general software wiki](https://clasweb.jlab.org/wiki/index.php/CLAS12_Software_Center) for setting up your environment to use them.

If you would rather build and install it yourself, `git clone` [the repository](https://github.com/JeffersonLab/coatjava), then skip to the next section below.

For anything more, see the "General Developer Documentation" link on that software wiki, which points [here](https://clasweb.jlab.org/wiki/index.php/COATJAVA_Developer_Docs).

The [troubleshooting](https://github.com/JeffersonLab/clas12-offline-software/wiki/Troubleshooting) wiki page may also still be useful but likely outdated.

<br>

## Dependencies

- Java
- Maven

Maven will automatically obtain all other dependencies.

<br>

## Building and Installing

Run the installation script:
```
./build-coatjava.sh
```

For more usage guidance, run:
```
./build-coatjava.sh --help
```

The software will then be installed _within_ the top-level repository directory, in a subdirectory named `coatjava/`, which contains:

| Directory | Description |
| --- | --- |
| `bin` | Executables for the user, such as `recon-util` |
| `etc` | Various supplementary files, such as bank schema and magnetic field maps |
| `lib` | JAR files |
| `libexec` | Internal scripts |

<br>

## Troubleshooting

If you want to _cleanly_ rebuild, use the `--clean` option for `build-coatjava.sh`.

If you need to clean your Maven cache, which by default is stored in `~/.m2/repository`, you can try either removing that directory, or renaming it, so that it is recreated. Then try to build `coatjava` again.
