#!/usr/bin/env ruby
#
# map dependency to the repository from which it was obtained
#

require 'yaml'
require 'fileutils'

TOP_DIR             = File.realpath "#{__dir__}/.."
OUT_DIR             = "#{TOP_DIR}/target/info"
CLASSPATH_FILE_NAME = 'dependency-classpath.txt'
TREE_FILE_NAME      = 'dependency-tree.txt'
REPO_LOCAL          = 'local'
REPO_CENTRAL        = 'maven-central'

# get classpath for each module
system [
  'mvn',
  'dependency:build-classpath',
  "-Dmdep.outputFile='${project.build.directory}/#{CLASSPATH_FILE_NAME}'",
  '--quiet',
  "--file #{TOP_DIR}",
].join ' '
raise "dependency:build-classpath goal failed" unless $?.success?

# get the list of unique dependency JARs
jars = []
Dir.glob("#{TOP_DIR}/**/target/#{CLASSPATH_FILE_NAME}").each do |classpath_file|
  File.readlines(classpath_file, chomp: true).each do |line|
    jars << line.split(':')
  end
end
jars.flatten!.uniq!.sort!

# get the remote repos
repos = {}
jars.each do |jar_path|
  # SNAPSHOT normalizer: replace timestamp with 'SNAPSHOT'
  def normalize(name)
    name.sub(/\d{8}\.\d{6}-\d+/, 'SNAPSHOT')
  end
  # names
  jar_dir   = File.dirname  jar_path
  jar_name  = normalize(File.basename jar_path)
  repo_file = "#{jar_dir}/_remote.repositories"
  # check if remote repo description exists
  unless File.exist? repo_file
    warn "WARNING: #{repo_file} does not exist"
    next
  end
  # get info for this JAR
  repo_lines = File.readlines(repo_file, chomp: true)
    .reject { |l| l.start_with?('#') }
    .select { |l| normalize(l.split('>', 2)[0]) == jar_name }
  if repo_lines.empty?
    warn "WARNING: could not find #{jar_name} in #{repo_file}"
    next
  end
  # fill output hash
  repo_lines.each do |repo_line|
    repo_id = repo_line.split('>')[1].split('=')[0]
    repo_id = repo_id.sub /-\h{32,}\z/, '' # drop the trailing digest (from GitHub `setup-java`'s `settings.xml`)
    repo = case repo_id
           when /code.jlab.org-.*/ then 'code.jlab.org'
           when nil                then REPO_LOCAL
           when 'central'          then REPO_CENTRAL
           else repo_id
           end
    (repos[repo] ||= []) << jar_path.sub(/^.*.m2\/repository\//,'')
  end
end

# dump output as YAML
FileUtils.mkdir_p OUT_DIR
OUT_REPOS = "#{OUT_DIR}/repos.yaml"
File.open(OUT_REPOS, 'w') do |o|
  o.puts repos
    .sort_by{ |k,_| [ [REPO_LOCAL, REPO_CENTRAL].index(k) || 2, k] }
    .to_h
    .to_yaml
end
puts "Wrote #{OUT_REPOS}"

# collect all dependency trees
OUT_TREES = "#{OUT_DIR}/trees.txt"
File.open(OUT_TREES, 'w') do |o|
  Dir.glob("#{TOP_DIR}/**/target/#{TREE_FILE_NAME}").sort.each do |tree_file|
    o.puts `cat #{tree_file}`
    o.puts ''
  end
end
puts "Wrote #{OUT_TREES}"
