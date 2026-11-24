# Etapa 3.4.3 — validação estrutural por geração

Este experimento compara duas variantes temporárias da topologia compartilhada:

- **Chave completa:** percorre as instruções e recalcula a assinatura estrutural
  antes de cada tentativa de reutilização.
- **Geração explícita:** consulta uma geração numérica em O(1) e invalida a
  topologia quando um passe confirma que alterou a estrutura.

As duas variantes são clones gerados em diretório temporário. Nenhum arquivo de
produção é alterado. Ambas iniciam cada chamada medida de `optimize()` sem uma
topologia em cache; dentro do ciclo, podem reutilizar a topologia enquanto ela
for válida. Isso mantém iguais as condições iniciais das duas variantes.

## Reprodução

Na raiz do repositório, execute os dois modos com os mesmos tamanhos e opções:

```sh
benchmarks/optimizer/run-structural-validation-experiment.sh time 250 500 \
  > benchmarks/optimizer/structural-validation-time.csv
benchmarks/optimizer/run-structural-validation-experiment.sh profile 250 500 \
  > benchmarks/optimizer/structural-validation-profile.csv
```

Cada modo usa cinco aquecimentos e nove amostras, e grava ambiente/opções da
JVM no CSV. Para passar opções da JVM, use `JAVA_OPTS` com o mesmo valor nos
dois comandos.

## Auditoria de mutações estruturais

### Comparação da integração em produção

O snapshot `reference/IROptimizer.pre-generation.java` mantém a implementação
anterior disponível. Para a comparação oficial, `production-generation-before.csv`
foi medido antes da integração e `production-generation-after.csv` depois dela,
com o mesmo harness e parâmetros descritos acima. No CSV posterior, a variante
geracional é a classe de produção; os clones instrumentados não participam do
modo de tempo.

As 28 combinações de cenário/tamanho terminaram com IR final idêntica entre a
referência e a geração (`final_ir_equal=true`). Comparando a vantagem relativa
da geração contra a referência em cada execução, a mediana foi 4,57% antes e
7,54% depois; a mediana da mudança nessa vantagem foi -0,10 ponto percentual.
Em alocações por operação, a redução mediana contra a referência foi 7,22%
antes e 6,86% depois. Nos casos de regressão observados anteriormente, `loops`
continuou 1,21% mais rápido que a referência na mediana posterior, e `copies`
2,03% mais rápido. Esses resultados não indicam regressão significativa da
integração, embora variações isoladas de tempo entre processos sejam visíveis.

Os CSVs guardam mediana e MAD por combinação; diferenças pequenas devem ser
interpretadas com cautela. Para repetir as medições oficiais (sobrescrevendo os
arquivos de resultados), use os dois comandos na seção Reprodução intitulada
“Integração de geração explícita em produção”.

A revisão do `IROptimizer` identificou três passes que podem mudar a topologia:

1. `removeUnreachable()` remove blocos inteiros.
2. `simplifyConstantBranches()` troca um branch condicional por salto ou o
   remove, alterando terminadores e fronteiras de blocos.
3. `removeRedundantJumps()` remove um salto que aponta para o próximo label.

O clone de geração marca uma invalidação quando cada uma dessas operações
realmente altera a estrutura. Os outros passes reescrevem operandos/destinos de
resultados, stores ou temporários, mas não criam/removem labels ou terminadores
nem alteram destinos de controle. `PUSH_EXCEPTION_HANDLER` é preservado; o caso
`handler_edges` verifica que suas arestas fazem parte da topologia inicial.

Os cenários extras exercitam branch tomado, branch removido por queda ao
fallthrough, blocos inalcançáveis, saltos redundantes e arestas de handlers.
Um preflight também reutiliza a mesma instância dos otimizadores com vários IRs
e com um IR de entrada acrescido de instruções entre chamadas, procurando
topologia obsoleta entre programas.

## Investigação de `loops` e `copies`

O primeiro resultado de `loops/250` mostrou regressão quando o harness sempre
media a chave completa antes da geração e usava nove amostras. Essa regressão
não se repetiu no teste focado com ordem alternada e 31 amostras. A rotina
reproduzível para esse diagnóstico executa três processos independentes por
cenário/tamanho, alterna a ordem e fixa o heap da JVM:

```sh
python3 benchmarks/optimizer/repeat_structural_regressions.py \
  --repeats 3 --samples 31 --heap 3g
```

Nas três repetições, a geração ficou mais rápida em cada par medido:

| Cenário | Melhora mediana entre repetições | Intervalo observado |
| --- | ---: | ---: |
| loops, 250 | 5,89% | 5,88% a 8,44% |
| loops, 500 | 4,06% | 4,02% a 4,09% |
| copies, 250 | 1,21% | 0,88% a 3,53% |
| copies, 500 | 2,87% | 2,05% a 3,36% |

As MADs dentro de alguns processos continuam altas, principalmente em `copies`;
portanto o ganho pequeno desse cenário deve ser tratado como inconclusivo.
O benchmark original não demonstrou uma regressão de execução reproduzível.

O perfil encontrou 14 chamadas de validação em cada variante nesses casos.
Com a chave, houve dois builds e doze refreshes; com geração, um build e treze
refreshes. As invalidações explícitas foram zero, como esperado: a remoção de
temporários aritméticos desloca índices de instrução, mas não altera blocos ou
arestas. A chave atual inclui índices absolutos e, por isso, reconstrói o CFG
após esse deslocamento. O refresh da geração toca exatamente o conjunto de
blocos/instruções que essa reconstrução adicional teria recriado.

Os clones foram comparados por diff. Fora nomes/campos temporários, a única
diferença executável é o experimento: o clone completo recalcula e compara a
chave; o clone de geração compara números e marca mudanças em
`removeUnreachable()`, `simplifyConstantBranches()` e
`removeRedundantJumps()`. Ambos usam o mesmo código de montagem/refresh de
blocos e arestas. O perfil também valida que o número de blocos continua igual
durante um refresh; a IR final permaneceu idêntica nos 12 casos isolados.

Os tempos internos de refresh do modo `profile` variaram de forma contraditória
entre execuções, mesmo quando os volumes de blocos e instruções eram
reproduzíveis. Como o relógio envolve pausas da JVM ocorridas dentro do trecho
medido, esses números não permitem atribuir o tempo a `clear()` ou à
reatribuição de instruções; use os contadores de trabalho e o modo `time` para
essa comparação.

## Perfil e limitações

O modo `time` não compila contadores nem timers internos. O modo `profile` usa
clones instrumentados para medir chave/comparação, checks da geração,
invalidações explícitas, refresh da lista de instruções, construção de
topologia e tempo inclusivo dos passes. A medição dos passes sem manutenção da
topologia é uma subtração pareada e aproximada; o perfilador adiciona overhead,
por isso esses tempos não são comparação de desempenho total.

A geração depende da auditoria de que somente os três passes indicados alteram
estrutura. Uma futura transformação que insira/remova labels, mude terminadores
ou altere destinos de controle precisa emitir invalidação. O teste de IR final
idêntica em todos os cenários ajuda a detectar omissões, mas não substitui essa
obrigação arquitetural para novos passes.
