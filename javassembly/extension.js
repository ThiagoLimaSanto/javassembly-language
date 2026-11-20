const path = require('node:path');
const vscode = require('vscode');

const MAIN_PATTERN = /\bpublic\s+static\s+func\s+void\s+main\s*\(\s*(?:(?:String|jvs\.lang\.String)\s*\[\s*\]\s+[A-Za-z_]\w*)?\s*\)/g;

class MainCodeLensProvider {
  provideCodeLenses(document) {
    const source = maskCommentsAndStrings(document.getText());
    const lenses = [];
    MAIN_PATTERN.lastIndex = 0;

    for (const match of source.matchAll(MAIN_PATTERN)) {
      const methodOffset = match.index + match[0].lastIndexOf('main');
      const position = document.positionAt(methodOffset);
      const range = new vscode.Range(position, position);
      lenses.push(new vscode.CodeLens(range, {
        title: '▶ Run',
        command: 'javassembly.runMain',
        arguments: [document.uri]
      }));
      lenses.push(new vscode.CodeLens(range, {
        title: '🐞 Debug (GDB)',
        command: 'javassembly.debugMain',
        arguments: [document.uri]
      }));
    }

    return lenses;
  }
}

function maskCommentsAndStrings(text) {
  const chars = text.split('');
  let state = 'code';

  for (let index = 0; index < chars.length; index++) {
    const current = chars[index];
    const next = chars[index + 1];

    if (state === 'line-comment') {
      if (current === '\n' || current === '\r') {
        state = 'code';
      } else {
        chars[index] = ' ';
      }
      continue;
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        chars[index] = ' ';
        chars[index + 1] = ' ';
        index++;
        state = 'code';
      } else if (current !== '\n' && current !== '\r') {
        chars[index] = ' ';
      }
      continue;
    }
    if (state === 'string') {
      if (current === '\\') {
        chars[index] = ' ';
        if (next !== undefined && next !== '\n' && next !== '\r') {
          chars[index + 1] = ' ';
          index++;
        }
      } else if (current === '"') {
        chars[index] = ' ';
        state = 'code';
      } else if (current !== '\n' && current !== '\r') {
        chars[index] = ' ';
      }
      continue;
    }

    if (current === '/' && next === '/') {
      chars[index] = ' ';
      chars[index + 1] = ' ';
      index++;
      state = 'line-comment';
    } else if (current === '/' && next === '*') {
      chars[index] = ' ';
      chars[index + 1] = ' ';
      index++;
      state = 'block-comment';
    } else if (current === '"') {
      chars[index] = ' ';
      state = 'string';
    }
  }

  return chars.join('');
}

async function chooseTarget() {
  const choices = process.platform === 'win32'
    ? [{ label: 'Windows x86-64', description: '-w', flag: '-w' }]
    : [
      { label: 'Linux x86-64', description: '-l', flag: '-l' },
      { label: 'Windows x86-64 (Wine necessário para executar)', description: '-w', flag: '-w' }
    ];
  const selected = await vscode.window.showQuickPick(choices, {
    placeHolder: 'Escolha o destino para compilar'
  });
  return selected?.flag;
}

function projectRootFor(sourceFile) {
  let directory = path.dirname(sourceFile);
  while (directory !== path.dirname(directory)) {
    if (path.basename(directory).toLowerCase() === 'src') {
      return path.dirname(directory);
    }
    directory = path.dirname(directory);
  }
  return path.dirname(sourceFile);
}

function shellQuote(value) {
  if (process.platform === 'win32') {
    return `'${value.replaceAll("'", "''")}'`;
  }
  return `'${value.replaceAll("'", "'\\''")}'`;
}

function terminalFor(sourceFile, title) {
  const options = {
    name: title,
    cwd: projectRootFor(sourceFile)
  };
  if (process.platform === 'win32') {
    options.shellPath = 'powershell.exe';
  }
  return vscode.window.createTerminal(options);
}

async function runMain(uri) {
  const sourceFile = uri.fsPath;
  const target = await chooseTarget();
  if (!target) return;

  const source = shellQuote(sourceFile);
  const terminal = terminalFor(sourceFile, 'JavaAssembly: Run');
  const command = process.platform === 'win32'
    ? `& jvsc ${target} ${source}; if ($LASTEXITCODE -eq 0) { & jvs ${target} ${source} }`
    : `jvsc ${target} ${source} && jvs ${target} ${source}`;
  terminal.show(true);
  terminal.sendText(command);
}

async function debugMain(uri) {
  const sourceFile = uri.fsPath;
  const target = process.platform === 'win32' ? '-w' : '-l';

  const root = projectRootFor(sourceFile);
  const baseName = path.basename(sourceFile, path.extname(sourceFile));
  const isWindowsTarget = target === '-w';
  const outputDirectory = isWindowsTarget
    ? path.join(root, 'out', 'windows-x64')
    : path.join(root, 'out');
  const executable = path.join(outputDirectory, baseName + (isWindowsTarget ? '.exe' : ''));
  const terminal = terminalFor(sourceFile, 'JavaAssembly: Debug (GDB)');
  const source = shellQuote(sourceFile);
  const program = shellQuote(executable);
  const command = process.platform === 'win32'
    ? `& jvsc ${target} ${source}; if ($LASTEXITCODE -eq 0) { gdb --args ${program} }`
    : `jvsc ${target} ${source} && gdb --args ${program}`;

  vscode.window.showInformationMessage(
    'O GDB depura o executável/assembly. O compilador ainda não gera símbolos para breakpoints em linhas .jvs.'
  );
  terminal.show(true);
  terminal.sendText(command);
}

function activate(context) {
  context.subscriptions.push(
    vscode.languages.registerCodeLensProvider(
      { language: 'javassembly' },
      new MainCodeLensProvider()
    ),
    vscode.commands.registerCommand('javassembly.runMain', runMain),
    vscode.commands.registerCommand('javassembly.debugMain', debugMain)
  );
}

function deactivate() {}

module.exports = { activate, deactivate };
