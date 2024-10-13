#!/usr/bin/env ruby
# run this script from the TOP level directory

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
  '<Noemie Pilleux-LOCAL>'   => 'N-Plx',
  '<Peter EJ Davies>'        => '',
  '<Pierre Chatagnon>'       => 'PChatagnon',
  '<Rafayel Paremuzyan>'     => 'rafopar',
  '<Raffaella De Vita>'      => 'raffaelladevita',
  '<Reynier Cruz Torres>'    => '',
  '<Rong Wang>'              => '',
  '<Silvia Nicolai>'         => '',
  '<Sylvester Joosten>'      => 'sly2j',
  '<Tongtong Cao>'           => 'tongtongcao',
  '<Vardan Gyurjyan>'        => 'gurjyan',
  '<Veronique Ziegler>'      => 'zieglerv',
  '<ajhobart>'               => 'ajhobart',
  '<colesmith>'              => 'forcar',
  '<cqplatt>'                => 'cqplatt',
  '<dcpayette>'              => 'dcpayette',
  '<dependabot[bot]>'        => '',
  '<efuchey>'                => 'efuchey',
  '<hattawy>'                => 'Hattawy',
  '<huckb>'                  => 'huckb',
  '<jwgibbs>'                => 'jwgibbs',
  '<mariangela-bondi>'       => 'mariangela-bondi',
  '<marmstr4>'               => 'marmstr4',
  '<mcontalb>'               => 'mcontalb',
  '<mpaolone>'               => 'mpaolone',
  '<rtysonCLAS12>'           => 'rtysonCLAS12',
  '<tongtongcao>'            => 'tongtongcao',
  '<veronique>'              => 'zieglerv',
}

codeowners      = File.open('CODEOWNERS', 'w')
unknown_authors = []

File.readlines('.github/codeowners/main_dirs.txt').map(&:chomp).each do |line|
  if line.match? /^#/ or line.empty?
    codeowners.puts line
    next
  end

  if line == '*'
    codeowners.puts [line, '@baltzell', '@raffaelladevita', '@c-dilks'].join(' ')
    next
  end

  puts line
  these_authors = []
  file_list = `git ls-files #{line.sub /\/\*$/, ''}`.split "\n"
  file_list.each do |file|
    puts " - #{file}"
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
    end.reject{|h|h.match?(/dependabot/)}
    these_authors += handles
  end
  codeowners.puts "#{line.gsub(' ','\ ')} #{these_authors.uniq.join(' ')}"

end

unless unknown_authors.empty?
  $stderr.puts "WARNING: the following authors have unknown GitHub handles:"
  $stderr.puts unknown_authors.uniq.sort
end

codeowners.close
