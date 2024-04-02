#!/usr/bin/env ruby

codeowners          = File.open('CODEOWNERS', 'w')
unique_authors_file = File.open('unique_authors.txt', 'w')

unique_authors = []

file_list = `git ls-files`.split("\n")
file_list.each_with_index do |file,i|
  puts "#{i+1} / #{file_list.size}"
  authors = `git shortlog -s -n -- '#{file}'`.split("\n").map do |line|
    "<" + line.split(' ')[1..-1].join(' ') + ">"
  end
  unique_authors += authors
  codeowners.puts "#{file} #{authors.join(' ')}"
end

unique_authors.uniq.each do |name|
  unique_authors_file.puts name
end

[ codeowners, unique_authors_file ].each &:close
