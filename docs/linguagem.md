# Guia da linguagem JavaAssembly

JavaAssembly usa arquivos `.jvs`. A sintaxe é inspirada em Java, mas não é compatível
com arquivos `.java`: operações aritméticas e comparações usam palavras-chave próprias.
Este guia apresenta os recursos disponíveis e como compilar um programa.

## Primeiro programa

Salve como `Ola.jvs`:

```java
public class Ola {
    public static func void main(String[] args) {
        printLn "Olá, mundo!";
        return;
    }
}
```

Compile e execute para Linux:

```bash
jvsc -l Ola.jvs
jvs -l Ola.jvs
```

Para gerar um executável Windows, use `-w`:

```bash
jvsc -w Ola.jvs
```

O executável recebe os argumentos passados na linha de comando por meio de
`String[] args`. Também é possível declarar `main()` sem parâmetros. As ferramentas,
instalação e pastas de saída estão descritas no [README](../README.md).

## Tipos e variáveis

Os tipos básicos são `int` (32 bits), `long` (64 bits), `double`, `boolean` e `String`.
Classes também podem ser usadas como tipos. Booleanos são escritos `True` e `False`.

```java
int quantidade = 3;
long populacao = 3000000002L;
double preco = 12.5;
boolean disponivel = True;
String nome = "Ana";
```

O sufixo `L` indica um literal `long`. A linguagem verifica tipos e conversões antes
de gerar o executável.

## Operações e condições

As operações são escritas em forma prefixada. Os nomes dos operandos podem ser
variáveis, literais ou outras expressões:

```java
int total = add 10, 5;
int diferenca = sub total, 2;
int produto = mul total, 3;
int resto = mod produto, 4;
int negativo = neg total;

if (cmp.gt total, 10) {
    printLn "maior que dez";
} else {
    printLn "dez ou menos";
}
```

Operações disponíveis: `add`, `sub`, `mul`, `div`, `mod`, `neg`, `and` e `or`.
Comparações usam `cmp.eq`, `cmp.ne`, `cmp.lt`, `cmp.le`, `cmp.gt` e `cmp.ge`.
Condições de `if` e `while` precisam resultar em `boolean`.

## Funções e fluxo de controle

Funções são declaradas com `func`, o tipo de retorno e uma lista de parâmetros. A
chamada usa parênteses:

```java
public static func int somar(int a, int b) {
    return add a, b;
}

public static func void exemplo() {
    int resultado = somar(4, 8);
    int contador = 0;

    while (cmp.lt contador, 3) {
        printLn contador;
        contador = add contador, 1;
    }
    return;
}
```

Também há `for` e os comandos `if`/`else`, `while` e `return`. Cada declaração e
comando termina com `;`, exceto blocos delimitados por `{` e `}`.

## Classes e objetos

Uma classe pode declarar atributos, métodos e construtores. O construtor tem o mesmo
nome da classe e não usa `func`:

```java
public class Pessoa {
    private String nome;
    private int idade;

    public Pessoa(String nome, int idade) {
        this.nome = nome;
        this.idade = idade;
    }

    public func String obterNome() {
        return nome;
    }
}

public class ExemploPessoa {
    public static func void main(String[] args) {
        Pessoa pessoa = new Pessoa("Lia", 21);
        printLn pessoa.obterNome();
        free(pessoa);
        return;
    }
}
```

O acesso a atributos e métodos usa `.`. `this` se refere ao objeto atual e `super`
permite acessar o construtor ou membros da classe pai. A herança usa `extends` e as
interfaces usam `implements`. Métodos sobrescritos podem ser chamados virtualmente.

Modificadores disponíveis incluem `public`, `private`, `protected`, `static`, `final`
e `abstract`. A visibilidade e as combinações permitidas são verificadas pelo
compilador.

## Interfaces

Interfaces declaram métodos que as classes implementadoras devem fornecer. Também
podem ter métodos `default` com corpo e métodos `static`:

```java
public interface Nomeavel {
    public func String nome();

    default func String saudacao() {
        return this.nome();
    }
}

public class Usuario implements Nomeavel {
    public func String nome() {
        return "Rui";
    }
}
```

Uma interface pode estender outras interfaces com `extends`. Métodos privados de
interface são auxiliares e só podem ser chamados dentro da própria interface.

## Arrays e memória

Arrays podem ser criados com `new`, inicializados com `{...}` e acessados por índice.
O índice começa em zero e cada array possui a propriedade `length`.

```java
int[] notas = new int[3];
notas[0] = 8;
notas[1] = 9;
notas[2] = 10;

int[][] matriz = new int[2][3];
int[] valores = {4, 5, 6};
printLn matriz.length;
printLn valores[1];

free(notas);
free(matriz[0]);
free(matriz[1]);
free(matriz);
free(valores);
```

Arrays multidimensionais são arrays de arrays; uma criação parcial como `new int[2][]`
deixa as posições internas sem array até que sejam inicializadas. A memória de objetos
e arrays é manual: chame `free(referencia)` quando terminar de usá-la. Depois de liberar
uma referência, não a acesse nem a libere novamente. `free(null)` é permitido.

## Saída

`print` e `printf` não acrescentam quebra de linha. `printLn` e `printfLn` acrescentam.
`print` recebe uma expressão; `printf` recebe uma string de formato e zero ou mais
argumentos:

```java
print "Carregando...";
printLn " pronto";
printfLn "Nome: %s, idade: %d, saldo: %lf, ativo: %b", "Lia", 21, 25.5, True;
```

Especificadores aceitos: `%d`, `%i` e `%u` para `int`; `%ld` e `%li` para `long`;
`%f` e `%lf` para `double`; `%s` para `String`; `%b` para `boolean`; e `%%` para
imprimir `%` literal. Os tipos e a quantidade dos argumentos são verificados pelo
frontend.

## Entrada pelo Scanner

Importe `jvs.io.Scanner` para ler valores do terminal. As leituras aguardam a entrada
do usuário:

```java
import jvs.io.Scanner;

public class Leitura {
    public static func void main(String[] args) throws IOException {
        print "Nome: ";
        String nome = Scanner.readLine();
        print "Idade: ";
        int idade = Scanner.readInt();
        printLn nome;
        printLn idade;
        return;
    }
}
```

O Scanner oferece `readInt()`, `readLong()`, `readDouble()`, `readBoolean()`,
`readString()` (um token) e `readLine()` (a linha inteira). Os métodos podem lançar
`IOException` ou `InputMismatchException` para entradas inválidas.

## Exceções

Uma classe de exceção deve estender `Throwable`. `throw` lança uma instância; `try`,
`catch` e `finally` tratam a falha e executam a limpeza:

```java
class Throwable {}
class ErroDeEntrada extends Throwable {}

public class Excecoes {
    public static func void verificar(int valor) throws ErroDeEntrada {
        if (cmp.lt valor, 0) {
            throw new ErroDeEntrada();
        }
    }

    public static func void main(String[] args) {
        try {
            verificar(-1);
        } catch (ErroDeEntrada erro) {
            printLn "valor inválido";
        } finally {
            printLn "verificação concluída";
        }
        return;
    }
}
```

Exceções verificadas precisam aparecer em `throws` ou ser capturadas. Erros de runtime
como índice inválido, referência nula e divisão por zero também podem ser capturados
por tipos compatíveis.

## Registradores globais

`register` declara um dos registradores globais `R0` a `R9`. O tipo é inferido na
inicialização e não pode mudar:

```java
register R0 = 10;

public class RegistradorGlobal {
    public static func void main(String[] args) {
        R0 = add R0, 5;
        printLn R0;
        return;
    }
}
```

Apesar do nome, esses registradores são variáveis globais da linguagem; não são uma
promessa de alocação em registradores físicos da CPU.

## Packages e imports

`package` aparece no início do arquivo. `import` disponibiliza classes de outro arquivo
ou da biblioteca padrão:

```java
package exemplo;

import jvs.io.Scanner;
```

Organize arquivos de acordo com o package e use imports para referenciar classes de
outros diretórios. O carregador de projeto procura os fontes relacionados ao arquivo
principal; a estrutura e a saída de build estão detalhadas no [README](../README.md).
