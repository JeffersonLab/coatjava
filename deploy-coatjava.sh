#!/usr/bin/env bash
# deploy a new release of coatjava

set -euo pipefail

# constants
src_dir=$(cd $(dirname ${BASH_SOURCE[0]:-$0}) && pwd -P)
deploy_dir=$src_dir/myLocalMvnRepo
main_branch=development
deployment_user=clas12
deployment_host=jlabl1

# printouts for this script (different from Maven printouts)
log() { echo ">>> $@"; }

# arguments
ver_deploy=''
snap_deploy=false
dry_run=false
use_git=true

# usage guide
usage() {
  echo """
  USAGE: $0 [OPTIONS]...

  CHECKLIST BEFORE RUNNING:
  - [ ] be on git branch '$main_branch'
  - [ ] be up-to-date ('git pull')
  - [ ] have no local changes ('git status')

  REQUIRED OPTIONS:
    -v VERSION   set the version number to deploy

  OPTIONAL OPTIONS:
    --no-git     do not involve 'git' and ignore CHECKLIST satisfaction
    --dry-run    do not deploy to remote servers; applies '--no-git'
    --snap       deploy as 'VERSION-SNAPSHOT'; applies '--no-git'
    -h,--help    show this usage guide

  EFFECT (default):
  - deploys new release as version 'VERSION'
  - bumps repo version number to 'VERSION-SNAPSHOT'
  - creates git branch and commit for bump

  """
}

# parse arguments
if [ $# -eq 0 ]; then
  usage
  exit 2
fi
while getopts "v:h-:" opt; do
  case $opt in
    v)
      ver_deploy=$OPTARG
      ;;
    h)
      usage
      exit 2
      ;;
    -)
      case $OPTARG in
        no-git)
          use_git=false
          ;;
        snap)
          snap_deploy=true
          use_git=false
          ;;
        dry-run)
          dry_run=true
          use_git=false
          ;;
        help)
          usage
          exit 2
          ;;
        *)
          echo "ERROR: unknown option '$OPTARG'" >&2
          exit 1
      esac
      ;;
    *)
      exit 1
      ;;
  esac
done

# be in the top-level source directory
cd $src_dir

# make sure the deployment directory is empty
if [ -d "$deploy_dir" ]; then
  echo "ERROR: deployment directory already exists: $deploy_dir" >&2
  echo "       please remove it (so the deployment will be clean)" >&2
  exit 1
fi

# handle version number
[ -z "$ver_deploy" ] && echo "ERROR: version number not specified" >&2 && exit 1
ver_deploy=$(echo $ver_deploy | sed 's;-SNAPSHOT;;g')
ver_snapshot=$ver_deploy-SNAPSHOT
if $snap_deploy; then ver_deploy=$ver_snapshot; fi
ver_current=$($src_dir/libexec/version.sh)
log "========================"
log "CURRENT VERSION  = $ver_current"
log "SNAPSHOT VERSION = $ver_snapshot"
log "DEPLOY VERSION   = $ver_deploy"
log "========================"

# if using git, make a new branch
new_branch=version/$ver_deploy
if $use_git; then
  log "create git branch '$new_branch'"
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

# change the version number, if different
# NOTE: `maven-release-plugin` could be used to better automate the versioning
# here, but since the deployment publishes a single coordinate
# (org.jlab.coat:coat-libs:<ver>) and we also want to make a tarball with the
# install tree at the correct version number, we may as well do the version
# bump here
if [ "$ver_current" != "$ver_deploy" ]; then
  log "change version number $ver_current -> $ver_deploy"
  $src_dir/libexec/version-bump.sh $ver_deploy
fi

# rebuild coatjava, cleanly, to be sure we deploy the correct version
log "cleanly rebuild coatjava"
$src_dir/build-coatjava.sh --clean
$src_dir/build-coatjava.sh

# deploy locally; no need to `clean deploy`, since we have already cleaned and re-built.
#
# Post-merge (T19), the project is a single root POM that produces:
#   - target/coatjava-<ver>.jar              (primary thin jar; NOT deployed)
#   - target/coatjava-<ver>-coat-libs.jar    (shaded uber-jar, attached via
#                                             maven-shade-plugin classifier)
#   - target/.flattened-pom.xml              (clean, external-deps-only POM
#                                             from flatten-maven-plugin)
#
# The default `mvn deploy` lifecycle is suppressed in the root POM (see
# maven-deploy-plugin <skip>true</skip>) because it would publish the wrong
# GAV (org.jlab.coat:coatjava with a `coat-libs` classifier) rather than the
# freestanding GAV org.jlab.coat:coat-libs that downstream consumers depend
# on. Instead, we explicitly invoke `deploy:deploy-file` to publish the
# shaded jar under the correct standalone artifactId, with the flattened POM
# as its published .pom.
log "local deployment of coat-libs version $ver_deploy"
mvn deploy:deploy-file \
  -Dmaven.test.skip=true \
  -DrepositoryId=coat-libs \
  -Durl=file://$deploy_dir \
  -DgroupId=org.jlab.coat \
  -DartifactId=coat-libs \
  -Dversion=$ver_deploy \
  -Dpackaging=jar \
  -Dfile=$src_dir/target/coatjava-${ver_deploy}-coat-libs.jar \
  -DgeneratePom=true

# make a tarball too
deploy_tarball=coatjava-${ver_deploy}.tar.gz
tar czf $deploy_tarball coatjava

# say what we did
print_deployment() {
  log "========================"
  log "local deployments:"
  log "  $deploy_dir"
  log "  $deploy_tarball"
  log "========================"
}
print_deployment

# deploy remotely
if ! $dry_run; then
  log "now deploying..."
  scp -r $deploy_dir/org/jlab/coat/coat-libs/* $deployment_user@$deployment_host:/group/clas/www/clasweb/html/clas12maven/org/jlab/coat/coat-libs/.
  scp $deploy_tarball $deployment_user@$deployment_host:/group/clas/www/clasweb/html/clas12offline/distribution/coatjava/.
  log "...done"
else
  log "dry run, not doing remote deployment"
fi

# change the version number to snapshot version
if ! $dry_run; then
  if [ "$ver_deploy" != "$ver_snapshot" ]; then
    log "change version number $ver_deploy -> $ver_snapshot"
    $src_dir/libexec/version-bump.sh $ver_snapshot
  fi
else # revert the version number, if this was a dry run
  # FIXME: it won't really be a dry run if some step above failed...
  #        just use git to fix that
  if [ "$ver_deploy" != "$ver_current" ]; then
    log "since this is a dry run, revert version number $ver_deploy -> $ver_current"
    $src_dir/libexec/version-bump.sh $ver_current
  fi
fi

# print what was done, and remove your local deployment directory so the next
# deployment doesn't clobber old deployments
print_deployment
if $dry_run; then
  log "this was just a dry run"
  log " - nothing was deployed remotely"
  log " - the version number was NOT bumped"
else
  log "version $ver_deploy has been deployed!"
fi

# git commit
if $use_git; then
  echo """
  ============================================"""
  git commit -am "build: bump version number to $ver_deploy"
  echo """Done.
  Currently on branch $new_branch
  Now run your usual 'git push' command, which is probably:

    git push -u origin $new_branch

  ============================================
  """
elif ! $dry_run; then
  log "Option '--no-git' was used and this is not a dry run;"
  log "the version number may have been changed (run 'git status')."
  log "Use 'libexec/version-bump.sh' if you need to revert the version number."
fi
