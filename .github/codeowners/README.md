This directory contains automation that helped us generate the [`CODEOWNERS`](/CODEOWNERS) file.

- `main_dirs.txt` is a list of the "main" directories for which we want a list of code owners; this was generated with `find -d`, and manually choosing the "main" directories
- `generate-codeowners.rb` reads `main_dirs.txt` and uses `git` logs to find the unique set of authors for all of the files within each directory; it then writes a new `CODEOWNERS` file
- finally, we go through the `CODEOWNERS` file manually to decide which contributors to keep; those contributors will be notified in PR reviews, so for example we may not want to include contributors who are not involved in the project any more
