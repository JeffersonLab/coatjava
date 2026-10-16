#!/usr/bin/env ruby
#
# Dumps the bank names and ID information for all JSON files in specified directory
#

require 'json'
require 'fileutils'

# iguana-created banks have claimed this group ID
IguanaGroupNum = 30000

# list of banks to put at the top
# FIXME: adapted from <https://clasweb.jlab.org/wiki/index.php/CLAS12_DSTs>, but maybe we can use schema dirs to automate
CommonBanks = {
  'Event banks' => [
    'RUN::config',
    'REC::Event',
  ],
  'Physics Banks' => [
    'REC::Particle',
    'RECFT::Particle',
    'REC::Calorimeter',
    'REC::Scintillator',
    'REC::Cherenkov',
    'REC::Track',
    'REC::Traj',
    'REC::CovMat',
    'REC::ScintExtras',
  ],
  'Special & Tagged Banks' => [
    'HEL::flip',
    'RAW::scaler',
    'RUN::scaler',
    'HEL::scaler',
    'RAW::epics',
    'HEL::online',
    'HEL::decoder',
  ],
  'Simulation Banks' => [
    'MC::Header',
    'MC::Event',
    'MC::Lund',
    'MC::Particle',
    'MC::True',
    'MC::GenMatch',
    'MC::RecMatch',
    'MC::User',
  ],
}

# usage and args
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

# make a table row
def table_row(out, cols)
  out.puts "| #{cols.join ' | '} |"
end

# start output markdown
FileUtils.mkdir_p OutputDir
FileUtils.mkdir_p File.join(OutputDir, BanksSubDir)
outMain = File.open File.join(OutputDir, "banks.md"), 'w'
outMain.puts """# HIPO Banks

The banks are listed in tables below, organized by group and item ID. Click on a bank name for its details.
"""

# common banks table
outMain.puts """
## Common DST Banks

For convenience, here are commonly used DST banks:

"""
table_row outMain, ['Group', 'Banks']
table_row outMain, ['---', '---']
CommonBanks.each do |group_name, bank_list|
  table_row outMain, [
    group_name,
    bank_list.map{ |bank_name| bank_md_link bank_name }.join(', ')
  ]
end

# all other bank tables
specs_fully_sorted.each do |group_id, spec_list|

  # get list of unique bank-name prefixes
  uniq_prefixes = spec_list.map do |spec|
    "`#{spec['name'].gsub /::.*/, ''}`"
  end.uniq

  outMain.puts "\n## #{uniq_prefixes.join ', '} Banks"
  outMain.puts "**Group ID:** #{group_id}\n\n"
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
