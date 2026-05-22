import * as vscode from 'vscode';
import * as os from 'os';
import { SCHEME, JarTreeProvider, JarNode } from './JarTreeProvider';
import { JarFileSystemProvider } from './JarFileSystemProvider';
import { JAR_LISTING_SCHEME, JarContentProvider } from './JarContentProvider';

function normalizePath(fsPath: string): string {
    // 只在 Linux/WSL 下才需要转换 Windows 路径
    if (os.platform() !== 'linux') return fsPath;

    // C:\Users\... 或 C:/Users/...
    let match = fsPath.match(/^([A-Za-z]):[\\\/](.*)/s);
    if (match) {
        return `/mnt/${match[1].toLowerCase()}/${match[2].replace(/\\/g, '/')}`;
    }
    // /C:/Users/... （VS Code WSL 下 fsPath 有时带前缀 /）
    match = fsPath.match(/^\/([A-Za-z]):\/?(.*)/s);
    if (match) {
        return `/mnt/${match[1].toLowerCase()}/${match[2]}`;
    }
    return fsPath;
}

export function activate(context: vscode.ExtensionContext): void {
    const treeProvider = new JarTreeProvider();
    const fsProvider = new JarFileSystemProvider(treeProvider);

    // Java LS 就绪后自动刷新树视图
    vscode.extensions.getExtension('redhat.java')?.activate().then((javaApi) => {
        const onModeChange = javaApi?.onDidServerModeChange;
        if (onModeChange) {
            context.subscriptions.push(
                onModeChange((mode: string) => {
                    if (mode === 'Standard') treeProvider.refresh();
                })
            );
        }
    });

    context.subscriptions.push(
        vscode.workspace.registerFileSystemProvider(SCHEME, fsProvider, {
            isCaseSensitive: true,
            isReadonly: true
        })
    );

    context.subscriptions.push(
        vscode.workspace.registerTextDocumentContentProvider(
            JAR_LISTING_SCHEME,
            new JarContentProvider()
        )
    );


    const treeView = vscode.window.createTreeView('decompile-java.jarExplorer', {
        treeDataProvider: treeProvider,
        showCollapseAll: true
    });
    context.subscriptions.push(treeView);

    treeView.onDidExpandElement(e => treeProvider.setNodeCollapsed(e.element, false), undefined, context.subscriptions);
    treeView.onDidCollapseElement(e => treeProvider.setNodeCollapsed(e.element, true), undefined, context.subscriptions);

    const openJar = async (uri: vscode.Uri) => {
        treeProvider.addJar(normalizePath(uri.fsPath));
        await vscode.commands.executeCommand('workbench.view.explorer');
        await vscode.commands.executeCommand('decompile-java.jarExplorer.focus');
    };

    context.subscriptions.push(
        vscode.commands.registerCommand('decompile-java.openJar', openJar)
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('decompile-java.closeJar', (node: JarNode) => {
            treeProvider.removeJar(node.jarId);
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('decompile-java.closeAllJars', () => {
            treeProvider.removeAllJars();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('decompile-java.collapseNode', async (node: JarNode) => {
            await treeProvider.collapseNode(node);
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('decompile-java.expandAllNode', async (node: JarNode) => {
            await treeProvider.expandAllUnder(node);
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('decompile-java.openEntry', async (uri: vscode.Uri, innerSuffix?: string) => {
            const doc = await vscode.workspace.openTextDocument(uri);
            let position: vscode.Position | undefined;

            if (innerSuffix) {
                if (/^\d+$/.test(innerSuffix)) {
                    const n = parseInt(innerSuffix, 10);
                    // 1. 优先从字节码 LineNumberTable 拿原始行号
                    const id = parseInt(uri.authority, 10);
                    const jarPath = treeProvider.getJarPath(id);
                    if (jarPath) {
                        const fullPath = uri.path.replace(/^\//, '');
                        const bangIdx = fullPath.indexOf('!/');
                        try {
                            let lineNum: number | undefined;
                            if (bangIdx !== -1) {
                                const nestedJar = fullPath.slice(0, bangIdx);
                                const classEntry = fullPath.slice(bangIdx + 2).replace(/\.java$/, '.class');
                                lineNum = await vscode.commands.executeCommand<number>(
                                    'java.execute.workspaceCommand',
                                    'decompile.getNestedInnerClassLine', jarPath, nestedJar, classEntry
                                );
                            } else {
                                const classEntry = fullPath.replace(/\.java$/, '.class');
                                lineNum = await vscode.commands.executeCommand<number>(
                                    'java.execute.workspaceCommand',
                                    'decompile.getInnerClassLine', jarPath, classEntry
                                );
                            }
                            if (lineNum && lineNum > 0) {
                                position = new vscode.Position(lineNum - 1, 0);
                            }
                        } catch (_) { /* ignore */ }
                    }
                    // 2. 回退：在文本中计数第 N 个匿名类体 "new Xxx(...) {"
                    if (!position) {
                        let count = 0;
                        for (let i = 0; i < doc.lineCount; i++) {
                            const text = doc.lineAt(i).text;
                            if (/\bnew\s+\w/.test(text) && /\)\s*\{/.test(text)) {
                                if (++count === n) { position = new vscode.Position(i, 0); break; }
                            }
                        }
                    }
                } else if (/^[A-Za-z_]\w*$/.test(innerSuffix)) {
                    // 命名内部类：搜索 class/interface/enum/record 声明
                    const re = new RegExp(`\\b(?:class|interface|enum|record)\\s+${innerSuffix}\\b`);
                    for (let i = 0; i < doc.lineCount; i++) {
                        if (re.test(doc.lineAt(i).text)) { position = new vscode.Position(i, 0); break; }
                    }
                }
            }

            await vscode.window.showTextDocument(doc, {
                preview: false,
                selection: position ? new vscode.Range(position, position) : undefined
            });
        })
    );

    context.subscriptions.push(
        vscode.window.registerCustomEditorProvider(
            'decompile-java.jarViewer',
            {
                openCustomDocument(uri) { return { uri, dispose() {} }; },
                async resolveCustomEditor(document, webviewPanel) {
                    webviewPanel.webview.html = '<html><body></body></html>';
                    const normalizedPath = normalizePath(document.uri.fsPath);
                    const listingUri = vscode.Uri.from({
                        scheme: JAR_LISTING_SCHEME,
                        path: normalizedPath
                    });
                    try {
                        const doc = await vscode.workspace.openTextDocument(listingUri);
                        await vscode.window.showTextDocument(doc, {
                            preview: false,
                            viewColumn: webviewPanel.viewColumn
                        });
                    } catch (e) {
                        vscode.window.showErrorMessage(`Failed to open JAR listing: ${e}`);
                    } finally {
                        webviewPanel.dispose();
                    }
                    await openJar(document.uri);
                }
            },
            { supportsMultipleEditorsPerDocument: false }
        )
    );
}

export function deactivate(): void {}
