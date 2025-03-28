#!/usr/bin/env bash
set -euo pipefail

main_branch=development

if [ $# -ne 1 ]; then
  echo """USAGE: $0 [NEW_VERSION_NUMBER]
  BEFORE RUNNING:
  - [ ] be on branch '$main_branch' before doing this, and it should
        be up-to-date with the remote (run 'git pull')
  - [ ] make sure you don't have any changes (run 'git status')

  EFFECT:
  - a new git branch for this version bump will be created, and a commit will be added
  - at the end, all you have to do is run 'git push' and open a pull request
  """
  exit 2
fi

# parse argument
ver_num=$(echo $1|sed 's/-SNAPSHOT//g')  # remove '-SNAPSHOT', if the user included it
ver_pom=$ver_num-SNAPSHOT                # append '-SNAPSHOT' for POM files

# verify user is on the main branch
current_branch=$(git rev-parse --abbrev-ref HEAD)
if [ "$current_branch" != "$main_branch" ]; then
  echo """ERROR: you are currently on branch '$current_branch', but you should be on branch '$main_branch'.
  Please switch to branch '$main_branch' and (preferrably) run 'git pull'.""" >&2
  exit 1
fi

# switch to a new branch for this new version
new_branch=version/$ver_num
git switch -c $new_branch

# bump the POM project version
mvn --batch-mode release:update-versions -DdevelopmentVersion=$ver_pom

# bump `deployDistribution.sh`'s version
sed -i "s/^VERSION=.*/VERSION=$ver_num/g" common-tools/coat-lib/deployDistribution.sh

# commit to git
echo """
============================================"""
git commit -am "build: bump version number to $ver_num"
echo """Done.
Now run your usual 'git push' command, which is probably:

  git push -u origin $new_branch

============================================
"""
