import * as vscode from 'vscode';
import * as path from 'path';

export const SCHEME = 'decompiler-jar';

export interface JarNode {
    jarId: number;
    jarPath: string;
    classPath: string;
    isDirectory: boolean;
    nestedJarEntry?: string;
    isNestedArchive?: boolean;  // true = ZIP 里真实的归档文件；false = 名称带归档后缀的目录
}

function isArchive(name: string): boolean {
    return name.endsWith('.jar') || name.endsWith('.war') || name.endsWith('.zip');
}

export class JarTreeProvider implements vscode.TreeDataProvider<JarNode> {
    private readonly _onDidChangeTreeData =
        new vscode.EventEmitter<JarNode | undefined | void>();
    readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

    private nextId = 0;
    private readonly idToJarPath = new Map<number, string>();
    private readonly jarPathToId = new Map<string, number>();
    private readonly entryCache = new Map<number, string[]>();
    private readonly nestedEntryCache = new Map<string, string[]>();

    addJar(jarPath: string): void {
        if (this.jarPathToId.has(jarPath)) return;
        const id = this.nextId++;
        this.idToJarPath.set(id, jarPath);
        this.jarPathToId.set(jarPath, id);
        this._onDidChangeTreeData.fire();
    }

    removeJar(id: number): void {
        const jarPath = this.idToJarPath.get(id);
        if (!jarPath) return;
        this.idToJarPath.delete(id);
        this.jarPathToId.delete(jarPath);
        this.entryCache.delete(id);
        for (const key of this.nestedEntryCache.keys()) {
            if (key.startsWith(`${id}!`)) this.nestedEntryCache.delete(key);
        }
        this._onDidChangeTreeData.fire();
    }

    removeAllJars(): void {
        this.idToJarPath.clear();
        this.jarPathToId.clear();
        this.entryCache.clear();
        this.nestedEntryCache.clear();
        this._onDidChangeTreeData.fire();
    }

    refresh(): void {
        this.entryCache.clear();
        this.nestedEntryCache.clear();
        this._onDidChangeTreeData.fire();
    }

    getJarPath(id: number): string | undefined {
        return this.idToJarPath.get(id);
    }

    async getEntries(id: number): Promise<string[]> {
        if (this.entryCache.has(id)) return this.entryCache.get(id)!;
        const jarPath = this.idToJarPath.get(id)!;
        let entries: string[] | undefined;
        try {
            entries = await vscode.commands.executeCommand<string[]>(
                'java.execute.workspaceCommand',
                'decompile.browseJar',
                jarPath
            );
        } catch (e) {
            vscode.window.showErrorMessage(
                `Failed to browse ${path.basename(jarPath)}: ${e}`
            );
        }
        if (!entries?.length) {
            vscode.window.showWarningMessage(
                `No entries found in ${path.basename(jarPath)}. ` +
                `Make sure the Java Language Server is running.`
            );
            return [];  // 不缓存，等 LS 就绪后可自动重试
        }
        this.entryCache.set(id, entries);
        return entries;
    }

    private async getNestedEntries(id: number, jarPath: string, nestedJarEntry: string): Promise<string[]> {
        const cacheKey = `${id}!${nestedJarEntry}`;
        if (!this.nestedEntryCache.has(cacheKey)) {
            let entries: string[] | undefined;
            try {
                entries = await vscode.commands.executeCommand<string[]>(
                    'java.execute.workspaceCommand',
                    'decompile.browseNestedJar',
                    jarPath,
                    nestedJarEntry
                );
            } catch (e) {
                vscode.window.showErrorMessage(
                    `Failed to browse ${path.basename(nestedJarEntry)}: ${e}`
                );
            }
            if (!entries?.length) return [];  // 不缓存，等 LS 就绪后可重试
            this.nestedEntryCache.set(cacheKey, entries);
        }
        return this.nestedEntryCache.get(cacheKey)!;
    }

    getTreeItem(node: JarNode): vscode.TreeItem {
        if (node.isDirectory) {
            const label = node.classPath
                ? path.basename(node.classPath)
                : path.basename(node.jarPath);
            // 只有真实的嵌套归档文件才默认折叠（懒加载），普通目录展开
            const state = node.isNestedArchive
                ? vscode.TreeItemCollapsibleState.Collapsed
                : vscode.TreeItemCollapsibleState.Expanded;
            const item = new vscode.TreeItem(label, state);
            item.iconPath = node.isNestedArchive
                ? new vscode.ThemeIcon('package')
                : vscode.ThemeIcon.Folder;
            if (!node.classPath) item.contextValue = 'jarRoot';
            return item;
        }

        const isClass = node.classPath.endsWith('.class');
        const displayPath = isClass
            ? node.classPath.slice(0, -6) + '.java'
            : node.classPath;

        const uriPath = node.nestedJarEntry
            ? `/${node.nestedJarEntry}!/${displayPath}`
            : `/${displayPath}`;

        const uri = vscode.Uri.from({
            scheme: SCHEME,
            authority: String(node.jarId),
            path: uriPath
        });
        const item = new vscode.TreeItem(uri);
        item.collapsibleState = vscode.TreeItemCollapsibleState.None;
        if (!isArchive(node.classPath)) {
            item.command = { command: 'vscode.open', title: 'Open', arguments: [uri] };
        }
        return item;
    }

    async getChildren(node?: JarNode): Promise<JarNode[]> {
        if (!node) {
            return Array.from(this.idToJarPath.entries()).map(([id, jarPath]) => ({
                jarId: id, jarPath, classPath: '', isDirectory: true
            }));
        }

        // 真实嵌套归档文件展开
        if (node.isNestedArchive) {
            const entries = await this.getNestedEntries(node.jarId, node.jarPath, node.classPath);
            return this.buildChildren(entries, '', node.jarId, node.jarPath, node.classPath);
        }

        // 普通目录或嵌套归档内的目录
        const entries = node.nestedJarEntry
            ? await this.getNestedEntries(node.jarId, node.jarPath, node.nestedJarEntry)
            : await this.getEntries(node.jarId);

        return this.buildChildren(entries, node.classPath, node.jarId, node.jarPath, node.nestedJarEntry);
    }

    private buildChildren(
        entries: string[],
        dirPath: string,
        jarId: number,
        jarPath: string,
        nestedJarEntry?: string
    ): JarNode[] {
        const dirPrefix = dirPath ? dirPath + '/' : '';
        const children = new Map<string, boolean>(); // name → isDirectory（来自 ZIP 条目）

        for (const entry of entries) {
            if (!entry.startsWith(dirPrefix)) continue;
            const rest = entry.slice(dirPrefix.length);
            const slashIdx = rest.indexOf('/');
            if (slashIdx === -1) {
                if (!children.has(rest)) children.set(rest, false);
            } else {
                const dirName = rest.slice(0, slashIdx);
                if (!children.has(dirName)) children.set(dirName, true);
            }
        }

        return Array.from(children.entries())
            .sort(([aName, aIsDir], [bName, bIsDir]) => {
                if (aIsDir !== bIsDir) return aIsDir ? -1 : 1;
                return aName.localeCompare(bName);
            })
            .map(([name, isDir]) => {
                const childPath = dirPrefix + name;
                // isDir=false 且后缀是归档格式 → 真实嵌套归档文件（仅支持一层）
                const isNestedArchive = !isDir && !nestedJarEntry && isArchive(name);
                return {
                    jarId,
                    jarPath,
                    classPath: childPath,
                    isDirectory: isDir || isNestedArchive,
                    nestedJarEntry,
                    isNestedArchive
                };
            });
    }
}
