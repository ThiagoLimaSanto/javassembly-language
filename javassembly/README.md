# JavaAssembly Language Support

Suporte básico da linguagem JavaAssembly (`.jvs`) para o Visual Studio Code.

## Recursos

- Coloração de sintaxe, comentários e indentação.
- Ações **Run** e **Debug (GDB)** acima de `public static func void main(...)`.
- **Run** compila com `jvsc` e executa com `jvs` no terminal integrado.
- **Debug (GDB)** compila para o sistema atual e abre o executável no GDB.

## Requisitos

- `jvsc` e `jvs` instalados e disponíveis no `PATH` para executar.
- `gdb` disponível no `PATH` para usar a ação de debug.
- Para executar um destino Windows a partir do Linux, é necessário Wine.

## Limitação do debug

O backend ainda não gera símbolos que associem instruções de máquina às linhas
`.jvs`. O GDB permite depurar o executável em baixo nível, mas breakpoints e execução
passo a passo por linha do fonte ainda não estão disponíveis.
