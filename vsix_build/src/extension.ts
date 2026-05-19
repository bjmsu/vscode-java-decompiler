import * as vscode from 'vscode';
import * as os from 'os';
import { SCHEME, JarTreeProvider } from './JarTreeProvider';
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


    context.subscriptions.push(
        vscode.window.createTreeView('decompile-java.jarExplorer', {
            treeDataProvider: treeProvider,
            showCollapseAll: true
        })
    );

    const openJar = async (uri: vscode.Uri) => {
        treeProvider.addJar(normalizePath(uri.fsPath));
        await vscode.commands.executeCommand('workbench.view.explorer');
        await vscode.commands.executeCommand('decompile-java.jarExplorer.focus');
    };

    context.subscriptions.push(
        vscode.commands.registerCommand('decompile-java.openJar', openJar)
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
                    const doc = await vscode.workspace.openTextDocument(listingUri);
                    await vscode.window.showTextDocument(doc, {
                        preview: false,
                        viewColumn: webviewPanel.viewColumn
                    });
                    webviewPanel.dispose();
                    openJar(document.uri);
                }
            },
            { supportsMultipleEditorsPerDocument: false }
        )
    );
}

export function deactivate(): void {}
