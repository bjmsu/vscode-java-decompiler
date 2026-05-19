import * as vscode from 'vscode';
import { JarTreeProvider } from './JarTreeProvider';

export class JarFileSystemProvider implements vscode.FileSystemProvider {
    private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>();
    readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event;

    constructor(private readonly tree: JarTreeProvider) {}

    async stat(uri: vscode.Uri): Promise<vscode.FileStat> {
        const id = parseInt(uri.authority, 10);
        if (!this.tree.getJarPath(id)) throw vscode.FileSystemError.FileNotFound(uri);
        return { type: vscode.FileType.File, ctime: 0, mtime: 0, size: 0 };
    }

    async readFile(uri: vscode.Uri): Promise<Uint8Array> {
        const id = parseInt(uri.authority, 10);
        const jarPath = this.tree.getJarPath(id);
        if (!jarPath) throw vscode.FileSystemError.FileNotFound(uri);

        const fullPath = uri.path.replace(/^\//, '');
        const bangIdx = fullPath.indexOf('!/');
        let source: string | undefined;

        try {
            if (bangIdx !== -1) {
                // 嵌套 JAR 内的文件
                const nestedJar = fullPath.slice(0, bangIdx);
                const entryPath = fullPath.slice(bangIdx + 2);
                const isClass = entryPath.endsWith('.java');
                const entry = isClass ? entryPath.slice(0, -5) + '.class' : entryPath;
                const command = isClass ? 'decompile.decompileNestedEntry' : 'decompile.readNestedEntry';
                source = await vscode.commands.executeCommand<string>(
                    'java.execute.workspaceCommand', command, jarPath, nestedJar, entry
                );
            } else {
                // 根归档内的文件
                const isClass = fullPath.endsWith('.java');
                const entry = isClass ? fullPath.slice(0, -5) + '.class' : fullPath;
                const command = isClass ? 'decompile.decompileEntry' : 'decompile.readEntry';
                source = await vscode.commands.executeCommand<string>(
                    'java.execute.workspaceCommand', command, jarPath, entry
                );
            }
        } catch (e) {
            throw vscode.FileSystemError.Unavailable(`Failed to read ${fullPath}: ${e}`);
        }

        if (!source) throw vscode.FileSystemError.FileNotFound(uri);
        return Buffer.from(source, 'utf-8');
    }

    watch(): vscode.Disposable { return { dispose: () => {} }; }
    readDirectory(): never { throw vscode.FileSystemError.NoPermissions(); }
    createDirectory(): never { throw vscode.FileSystemError.NoPermissions(); }
    writeFile(): never { throw vscode.FileSystemError.NoPermissions(); }
    delete(): never { throw vscode.FileSystemError.NoPermissions(); }
    rename(): never { throw vscode.FileSystemError.NoPermissions(); }
}
