#!/usr/bin/env bash
set -euo pipefail

main_branch=development

if [ $# -lt 1 ]; then
  echo """USAGE: $0 [NEW_VERSION_NUMBER]

  BEFORE RUNNING:
  - [ ] be on branch '$main_branch' before doing this, and it should
        be up-to-date with the remote (run 'git pull')
  - [ ] make sure you don't have any changes (run 'git status')

  EFFECT:
  - a new git branch for this version bump will be created, and a commit will be added
  - at the end, all you have to do is run 'git push' and open a pull request

  OPTION: if you want to just run the version bump without involving 'git' in any way,
          add the argument '--no-git'; this will ONLY bump the version numbers
  """
  exit 2
fi
ver_arg=$1
[ $# -ge 2 -a "${2-}" = "--no-git" ] && use_git=false || use_git=true

# parse version number argument
ver_num=$(echo $ver_arg|sed 's/-SNAPSHOT//g')  # remove '-SNAPSHOT', if the user included it
ver_pom=$ver_num-SNAPSHOT                      # append '-SNAPSHOT' for POM files

# print arguments
echo """>>>>>
ver_num: $ver_num
ver_pom: $ver_pom
use_git: $use_git
<<<<<"""

# if using git, make a new branch
new_branch=version/$ver_num
if $use_git; then
  # verify user is on the main branch
  current_branch=$(git rev-parse --abbrev-ref HEAD)
  if [ "$current_branch" != "$main_branch" ]; then
    echo """ERROR: you are currently on branch '$current_branch', but you should be on branch '$main_branch'.
    Please switch to branch '$main_branch' and (preferrably) run 'git pull'.""" >&2
    exit 1
  fi
  # switch to a new branch for this new version
  git switch -c $new_branch
fi

# bump the POM project version
mvn versions:set -DnewVersion=$ver_pom -DprocessAllModules
mvn versions:commit -DprocessAllModules

# bump `deployDistribution.sh`'s version
sed -i "s/^VERSION=.*/VERSION=$ver_num/g" common-tools/coat-lib/deployDistribution.sh

# bump `install-clara`'s version
sed -i "s/^coatjava=.*/coatjava=$ver_num/g" install-clara

# commit to git (or not)
if $use_git; then
  echo """
  ============================================"""
  git commit -am "build: bump version number to $ver_num"
  echo """Done.
  Currently on branch $new_branch
  Now run your usual 'git push' command, which is probably:

    git push -u origin $new_branch

  ============================================
  """
else
  echo "Done bumping version number; no git commit was created"
fi
