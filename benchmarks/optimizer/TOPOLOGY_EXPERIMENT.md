# Etapa 3.4.2 — experimento de topologia compartilhada

O experimento compara a implementação de referência (`IROptimizer` compilado
do código atual) com um clone temporário que reutiliza a topologia do CFG.
Os clones e classes do harness são criados em um diretório temporário e
removidos ao terminar. Nenhum fonte de produção é editado. A topologia
compartilhada continua sendo uma implementação experimental: ela reutiliza
blocos e arestas, mas atualiza as instruções e limpa os estados de constantes
em cada reutilização.

## Uma única forma de reproduzir

Execute os dois comandos abaixo na raiz do repositório. Eles usam os mesmos
cenários na mesma ordem, tamanhos `250` e `500`, cinco aquecimentos, nove
amostras e a mesma JVM/opções do ambiente. Os CSVs 3.4.2 são novos e não
substituem os resultados de experimentos anteriores.

```sh
benchmarks/optimizer/run-topology-experiment.sh time 250 500 \
  > benchmarks/optimizer/topology-sharing-time.csv
benchmarks/optimizer/run-topology-experiment.sh profile 250 500 \
  > benchmarks/optimizer/topology-sharing-profile.csv
```

Use `JAVA_OPTS` para passar opções à JVM. Para uma comparação válida, mantenha
o mesmo valor nos dois comandos. Os CSVs registram versão Java, sistema,
arquitetura, opções da JVM, aquecimentos e amostras.

## Modo `time`

Este modo compila o clone compartilhado sem contadores nem cronômetros internos.
Ele mede diretamente o ciclo completo de `optimize()` para referência e clone,
incluindo as chamadas reflexivas em ambos, e mede bytes alocados pela thread.
Relata mediana e MAD de tempo e de bytes alocados. O harness
compara as instruções da IR final após cada par de execuções e aborta em caso
de divergência. A coluna `cfg_profile` fica em `not_instrumented` por projeto.

## Modo `profile`

Este modo usa clones temporários instrumentados e não serve para comparar o
tempo total de desempenho. O clone de referência conta chamadas reais ao
`buildControlFlowGraph()` e mede sua duração. O clone compartilhado decompõe
cálculo da chave, comparação, atualização das instruções/limpeza de estados e
construção inicial ou reconstrução. Mede também o tempo inclusivo dos passes.
Os campos `pass_minus_cfg` são estimativas pareadas de tempo dos passes sem a
manutenção do CFG; não são uma métrica de ciclo total.

Cada amostra profile começa com cache vazio para medir a construção inicial.
Dentro de cada `optimize()`, os usos subsequentes podem reutilizar ou reconstruir
a topologia conforme a chave estrutural. O total de builds é contado apenas
para construções reais. O número de rodadas vem do clone temporário.

Ambos os modos comparam a IR final em cada amostra e falham imediatamente se
houver diferença. Os perfis e as medições de tempo são processos separados,
portanto a instrumentação não aparece no modo `time`.

## Limites da medição

- As entradas são IR sintéticas do `IROptimizerBenchmark`, com cenários CFG,
  temporários, cópias, cadeia de cópias, constantes, loops, aritmética, stores
  mortos e chamadas.
- A comparação é feita para a versão do código-fonte e ambiente registrados
  pelo CSV; mudanças no otimizador exigem gerar novos arquivos.
- A soma dos cronômetros de passes inclui chamadas de CFG ocorridas dentro dos
  passes. O relatório subtrai os tempos de manutenção medidos no mesmo sample
  para apresentar uma estimativa separada; essa decomposição não é aditiva ao
  tempo externo e contém overhead do próprio perfilador.
