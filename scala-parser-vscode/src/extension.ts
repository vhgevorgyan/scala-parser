import * as path from 'path';
import * as vscode from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext) {
    const serverJar = context.asAbsolutePath(
        path.join('server', 'scala-parser-lsp.jar')
    );

    const serverOptions: ServerOptions = {
        run: {
            command: 'java',
            args: ['-jar', serverJar],
            transport: TransportKind.stdio
        },
        debug: {
            command: 'java',
            args: ['-jar', serverJar],
            transport: TransportKind.stdio
        }
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'scala-parser' }],
        synchronize: {
            fileEvents: vscode.workspace.createFileSystemWatcher('**/*.{sp,scala}')
        }
    };

    client = new LanguageClient(
        'scala-parser',
        'Scala Parser Language Server',
        serverOptions,
        clientOptions
    );

    client.start();

    context.subscriptions.push(
        vscode.commands.registerCommand('scala-parser.restart', async () => {
            if (client) {
                await client.stop();
                client = undefined;
            }
            activate(context);
            vscode.window.showInformationMessage('Scala Parser server restarted');
        })
    );
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) return undefined;
    return client.stop();
}
