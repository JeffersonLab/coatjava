#!/usr/bin/env ruby
#
# Dumps the bank names and ID information for all JSON files in specified directory
#

require 'json'
require 'fileutils'

IguanaGroupNum = 30000
CommonBanks = [
  'REC::Particle',
  'RUN::config',
  'REC::Calorimeter',
  'REC::Traj',
] # FIXME: add more, or take inspiration from DST schema

unless ARGV.size == 2
  puts """
  USAGE: #{$0} [INPUT_JSON_DIR] [OUTPUT_DIR]

  Dumps the bank names and ID information for all JSON files in [INPUT_JSON_DIR]
  Output files will appear in [OUTPUT_DIR]
  """
  exit 2
end
InputJsonDir = ARGV[0]
OutputDir    = ARGV[1]

# parse the JSON files
specs = Dir.glob(File.join InputJsonDir, '*.json').map do |spec_file_name|
  JSON.parse File.read(spec_file_name)
end.flatten

# check if each spec has the required keys
specs.each do |spec|
  ['name', 'group', 'item', 'info'].each do |key|
    unless spec.has_key? key
      $stderr.puts "ERROR: missing key '#{key}' in one of the JSON files"
      exit 1
    end
  end
end

# group the specs according to group ID
specs_grouped = Hash.new
specs.each do |spec|
  group_num = spec['group'].to_i
  specs_grouped[group_num] = Array.new unless specs_grouped.has_key? group_num
  specs_grouped[group_num] << spec
end

# sort by group ID
specs_grouped_sorted = specs_grouped.sort_by{|k,v|k}.to_h
# then sort each group's item IDs
specs_fully_sorted = Hash.new
specs_grouped_sorted.each do |group_id, spec_list|
  raise "do not define a bank with group ID #{IguanaGroupNum}, since that is reserved for Iguana" if group_id==IguanaGroupNum
  specs_fully_sorted[group_id] = spec_list.sort do |spec_a, spec_b|
    spec_a['item'].to_i <=> spec_b['item'].to_i
  end
end

# functions to give bank details markdown file name and link
BanksSubDir = 'banks'
def bank_md_name(name)
  File.join(BanksSubDir, name.gsub(/::/,'_')) + '.md'
end
def bank_md_link(name)
  "[`#{name}`](#{bank_md_name name})"
end

# data type hash
TypeHash = {
  'B' => 'byte',
  'D' => 'double',
  'F' => 'float',
  'I' => 'int',
  'L' => 'long',
  'S' => 'short',
}

# output tables
FileUtils.mkdir_p OutputDir
outMain = File.open File.join(OutputDir, "banks.md"), 'w'
outMain.puts """# HIPO Banks

## Common Banks

"""
CommonBanks.each do |name|
  outMain.puts "- #{bank_md_link name}"
end

outMain.puts """
## Full List of Banks

Organized by group and item ID

> **NOTE:**  Iguana banks, which are defined in the Iguana repository, use group number #{IguanaGroupNum}.
"""

def table_row(out, cols)
  out.puts "| #{cols.join ' | '} |"
end
specs_fully_sorted.each do |group_id, spec_list|
  outMain.puts "\n## Group #{group_id}\n\n"
  table_row outMain, ['Item ID', 'Name', 'Description']
  table_row outMain, ['---', '---', '---']
  spec_list.each do |spec|

    # clean up description
    desc = spec['info'].split.map do |word|
      if word.include? '::'
        "`#{word}`"
          .gsub(')`','`)')
          .gsub('`(','(`')
      else
        word
      end
    end.join(' ')

    # output main table row
    table_row outMain, [
      spec['item'],
      bank_md_link(spec['name']),
      desc,
    ]

    # generate detailed table
    outBank = File.open File.join(OutputDir, bank_md_name(spec['name'])), 'w'
    outBank.puts """# `#{spec['name']}` Bank Details

#{desc}

[Return to main tables](../banks.md)

"""
    table_row outBank, ['Item Name', 'Type', 'Description']
    table_row outBank, ['---', '---', '---']
    spec['entries'].each do |entry|
      datatype = TypeHash[entry['type']]
      raise "unknown datatype '#{datatype}'" if datatype.nil?
      table_row outBank, [
        "`#{entry['name']}`",
        "`#{datatype}`",
        entry['info']
      ]
    end
    outBank.close
  end
end
outMain.close

puts "OUTPUT FILES WRITTEN TO #{OutputDir}"
