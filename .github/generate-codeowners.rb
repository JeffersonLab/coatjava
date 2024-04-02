#!/usr/bin/env ruby

GithubHandles = {
  '<Adam Hobart>'            => '',
  '<Alessandra Filippi>'     => '',
  '<Andrey Kim>'             => '',
  '<Blake Huck>'             => '',
  '<Bruno Benkel>'           => '',
  '<Christopher Dilks>'      => 'c-dilks',
  '<Cole Smith>'             => '',
  '<Connor Pecar>'           => '',
  '<David Heddle>'           => '',
  '<David Payette>'          => '',
  '<Efrain Patrick Segarra>' => '',
  '<Efrain Segarra>'         => '',
  '<Florian Hauenstein>'     => '',
  '<Francesco Bossu>'        => '',
  '<Francois-Xavier Girod>'  => '',
  '<Gagik Gavalian>'         => '',
  '<Giovanni Angelini>'      => '',
  '<Guillaume Christiaens>'  => '',
  '<Joseph Newton>'          => '',
  '<Justin Goodwill>'        => '',
  '<L Smith>'                => '',
  '<Latif Kabir>'            => '',
  '<Marco Contalbrigo>'      => '',
  '<Mathieu Ouillon>'        => '',
  '<Maurik Holtrop>'         => '',
  '<Maxime Defurne>'         => '',
  '<Michael Hoffer>'         => '',
  '<Nathan Baltzell>'        => 'baltzell',
  '<Nathan Harrison>'        => '',
  '<Nick Markov>'            => '',
  '<Noemie Pilleux-LOCAL>'   => '',
  '<Peter EJ Davies>'        => '',
  '<Pierre Chatagnon>'       => '',
  '<Rafayel Paremuzyan>'     => '',
  '<Raffaella De Vita>'      => 'raffaelladevita',
  '<Reynier Cruz Torres>'    => '',
  '<Rong Wang>'              => '',
  '<Silvia Nicolai>'         => '',
  '<Sylvester Joosten>'      => '',
  '<Tongtong Cao>'           => '',
  '<Vardan Gyurjyan>'        => '',
  '<Veronique Ziegler>'      => '',
  '<ajhobart>'               => '',
  '<colesmith>'              => '',
  '<cqplatt>'                => '',
  '<dcpayette>'              => '',
  '<dependabot[bot]>'        => '',
  '<efuchey>'                => '',
  '<hattawy>'                => '',
  '<huckb>'                  => '',
  '<jwgibbs>'                => '',
  '<mariangela-bondi>'       => '',
  '<marmstr4>'               => '',
  '<mcontalb>'               => '',
  '<mpaolone>'               => '',
  '<rtysonCLAS12>'           => '',
  '<tongtongcao>'            => '',
  '<veronique>'              => '',
}

codeowners      = File.open('CODEOWNERS', 'w')
unknown_authors = []

file_list = `git ls-files`.split("\n")
file_list.each_with_index do |file,i|
  puts "#{i+1} / #{file_list.size}"
  authors = `git shortlog -s -n -- '#{file}'`.split("\n").map do |line|
    "<" + line.split(' ')[1..-1].join(' ') + ">"
  end
  handles = authors.map do |author|
    handle = GithubHandles[author]
    if handle != ''
      "@#{handle}"
    else
      unknown_authors << author
      author
    end
  end
  codeowners.puts "#{file.gsub(' ','\ ')} #{handles.join(' ')}"
end

unless unknown_authors.empty?
  $stderr.puts "WARNING: the following authors have unknown GitHub handles:"
  $stderr.puts unknown_authors.uniq.sort
end

codeowners.close
