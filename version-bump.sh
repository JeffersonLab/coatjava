#!/usr/bin/env bash
set -euo pipefail

main_branch=development

if [ $# -lt 1 ]; then
  echo """USAGE: $0 [NEW_VERSION_NUMBER] [OPTIONS]...

  BEFORE RUNNING:
  - [ ] be on branch '$main_branch' before doing this, and it should
        be up-to-date with the remote (run 'git pull')
  - [ ] make sure you don't have any changes (run 'git status')
  - [ ] choose a new vesion number; by default, '-SNAPSHOT' will automatically
        be appended (otherwise use option '--no-snap')

  EFFECT:
  - a new git branch for this version bump will be created, and a commit will be added
  - at the end, all you have to do is run 'git push' and open a pull request

  OPTIONS:
    --no-git     do not involve 'git' in any way, just change the version number
    --no-snap    do not append '-SNAPSHOT' to the version number
  """
  exit 2
fi
use_git=true
use_snap=true
ver=''
for arg in "$@"; do
  case $arg in
    --no-git) use_git=false ;;
    --no-snap) use_snap=false ;;
    -*) echo "ERROR: unknown option '$arg'" >&2 && exit 1 ;;
    *) ver=$arg ;;
  esac
done
[ -z "$ver" ] && echo "ERROR: version number not specified" >&2 && exit 1

# snapshot version
if $use_snap; then
  ver=$(echo $ver | sed 's;-SNAPSHOT;;g')-SNAPSHOT
fi
echo "bumping version number to: $ver"

# if using git, make a new branch
new_branch=version/$ver
if $use_git; then
  # verify user is on the main branch
  current_branch=$(git rev-parse --abbrev-ref HEAD)
  if [ "$current_branch" != "$main_branch" ]; then
    echo """ERROR: you are currently on branch '$current_branch', but you should be on branch '$main_branch'.
    Please switch to branch '$main_branch' and (preferably) run 'git pull'.""" >&2
    exit 1
  fi
  # switch to a new branch for this new version
  git switch -c $new_branch
fi

# bump the POM project version
mvn versions:set -DnewVersion=$ver -DprocessAllModules
mvn versions:commit -DprocessAllModules

# commit to git (or not)
if $use_git; then
  echo """
  ============================================"""
  git commit -am "build: bump version number to $ver"
  echo """Done.
  Currently on branch $new_branch
  Now run your usual 'git push' command, which is probably:

    git push -u origin $new_branch

  ============================================
  """
else
  echo "Done bumping version number; no git commit was created"
fi
