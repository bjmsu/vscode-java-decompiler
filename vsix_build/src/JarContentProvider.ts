import * as vscode from 'vscode';
import * as path from 'path';

export const JAR_LISTING_SCHEME = 'jar-listing';

export class JarContentProvider implements vscode.TextDocumentContentProvider {
    async provideTextDocumentContent(uri: vscode.Uri): Promise<string> {
        const jarPath = uri.path;

        let entries: string[] | undefined;
        try {
            entries = await vscode.commands.executeCommand<string[]>(
                'java.execute.workspaceCommand',
                'decompile.browseJar',
                jarPath
            );
        } catch (e) {
            return `Error loading ${path.basename(jarPath)}: ${e}`;
        }

        if (!entries?.length) {
            return [
                `JAR: ${jarPath}`,
                '',
                'No class entries found.',
                'Make sure the Java Language Server is running.'
            ].join('\n');
        }

        return [
            `JAR: ${jarPath}`,
            '─'.repeat(64),
            '',
            ...entries,
            '',
            `Total: ${entries.length} classes`
        ].join('\n');
    }
}
