#!/usr/bin/env ruby

GithubHandles = {
  '<Adam Hobart>'            => 'ajhobart',
  '<Alessandra Filippi>'     => 'afilippi67',
  '<Andrey Kim>'             => 'drewkenjo',
  '<Blake Huck>'             => 'huckb',
  '<Bruno Benkel>'           => 'bleaktwig',
  '<Christopher Dilks>'      => 'c-dilks',
  '<Cole Smith>'             => 'forcar',
  '<Connor Pecar>'           => 'cpecar',
  '<David Heddle>'           => 'heddle',
  '<David Payette>'          => 'dpayette',
  '<Efrain Patrick Segarra>' => '',
  '<Efrain Segarra>'         => '',
  '<Florian Hauenstein>'     => 'hauenst',
  '<Francesco Bossu>'        => 'fbossu',
  '<Francois-Xavier Girod>'  => 'fxgirod',
  '<Gagik Gavalian>'         => 'gavalian',
  '<Giovanni Angelini>'      => 'gangel85',
  '<Guillaume Christiaens>'  => 'Guillaum-C',
  '<Joseph Newton>'          => 'josnewton',
  '<Justin Goodwill>'        => '',
  '<L Smith>'                => 'forcar',
  '<Latif Kabir>'            => 'latifkabir',
  '<Marco Contalbrigo>'      => 'mcontalb',
  '<Mathieu Ouillon>'        => 'mathieuouillon',
  '<Maurik Holtrop>'         => 'mholtrop',
  '<Maxime Defurne>'         => 'mdefurne',
  '<Michael Hoffer>'         => 'miho',
  '<Nathan Baltzell>'        => 'baltzell',
  '<Nathan Harrison>'        => 'naharrison',
  '<Nick Markov>'            => 'markovnick',
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
  '<veronique>'              => 'zieglerv',
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
